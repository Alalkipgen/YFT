package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadFailureStage.CONNECT
import com.alal.yft.core.model.download.DownloadFailureStage.OPEN_FILE
import com.alal.yft.core.model.download.DownloadFailureStage.PUBLISH
import com.alal.yft.core.model.download.DownloadFailureStage.READ_SOURCE
import com.alal.yft.core.model.download.DownloadFailureStage.VERIFY
import com.alal.yft.core.model.download.DownloadFailureStage.WRITE_FILE
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.download.WholeFileTrack
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

interface DashTransferRunner {
    suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint? = null,
        onProgress: suspend (DownloadProgress) -> Unit = {},
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit = {},
    ): DashTransferResult

    /**
     * [transfer] that hands its start times to [onStartTimeline] once (P41), instead of writing
     * them to the log itself: a merged download writes one line for both of its tracks. A runner
     * that does not measure them never calls it.
     */
    suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        onStartTimeline: (DownloadStartTimeline) -> Unit,
    ): DashTransferResult = transfer(plan, destination, resumeFrom, onProgress, onCheckpoint)

    suspend fun discard(plan: DashDownloadPlan)
}

/**
 * Downloads one selected non-DRM static DASH representation.
 *
 * Chunks are downloaded into deterministic app-private files, checkpointed only after a complete
 * fsync, then assembled in manifest order. The public destination remains unpublished until its
 * final length is verified. Progress counts the bytes of a chunk as they are written, at most
 * every [Policy.progressIntervalMillis] (P41).
 */
class DashTransferEngine(
    client: OkHttpClient,
    private val workspaceRoot: File,
    private val policy: Policy = Policy(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** A monotonic clock in milliseconds, for the progress pace and the start times (P41). */
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    /** One line per download with its start times (P41); no addresses. */
    private val log: (String) -> Unit = ::logStartLine,
) : DashTransferRunner {
    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 60,
        val maxAttempts: Int = 3,
        val initialRetryDelayMillis: Long = 500,
        val maxManifestBytes: Int = 1_024 * 1_024,
        val maxChunks: Int = 20_000,
        val maxChunkBytes: Long = 128L * 1_024 * 1_024,
        val maxConcurrentChunks: Int = 3,
        val bufferBytes: Int = 64 * 1_024,
        /**
         * FAST_START (P41): a whole file's first range is [firstRangeBytes], its length comes
         * from that range's answer instead of a separate request, and a worker takes the next
         * range as soon as it is free. Off: today's equal ranges, length request and batches.
         */
        val fastStart: Boolean = true,
        val firstRangeBytes: Long = 1_024L * 1_024,
        /** Ranges at once for a whole file on YouTube's media hosts (P41; others: above). */
        val mediaHostConcurrentChunks: Int = 4,
        /** The shortest time between two progress updates: 4 a second (P41). */
        val progressIntervalMillis: Long = 250,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
            require(maxAttempts in 1..5)
            require(initialRetryDelayMillis >= 0)
            require(maxManifestBytes in 1_024..4 * 1_024 * 1_024)
            require(maxChunks in 1..50_000)
            require(maxChunkBytes > 0)
            require(maxConcurrentChunks in 1..8)
            require(bufferBytes in 1_024..1024 * 1_024)
            require(firstRangeBytes > 0)
            require(mediaHostConcurrentChunks in 1..8)
            require(progressIntervalMillis >= 0)
        }
    }

    private val http = SecureDownloadHttp(
        client = client,
        maxRedirects = policy.maxRedirects,
        callTimeoutSeconds = policy.callTimeoutSeconds,
    )

    override suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
    ): DashTransferResult = transfer(
        plan = plan,
        destination = destination,
        resumeFrom = resumeFrom,
        onProgress = onProgress,
        onCheckpoint = onCheckpoint,
        onStartTimeline = { timeline -> log("DASH ${timeline.summary()}") },
    )

    override suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        onStartTimeline: (DownloadStartTimeline) -> Unit,
    ): DashTransferResult = withContext(Dispatchers.IO) {
        val start = StartTimelineRecorder(elapsedMillis)
        val startReported = AtomicBoolean(false)
        val reportStart = {
            if (startReported.compareAndSet(false, true)) onStartTimeline(start.timeline)
        }
        val emptyCheckpoint = DashTransferCheckpoint(null, emptyList())
        val credentialOrigin = plan.manifestUrl.toSafeDownloadUrl()
            ?: return@withContext failure(
                DownloadFailure(DownloadFailureReason.INVALID_URL, stage = CONNECT),
                emptyCheckpoint,
            )
        if (plan.expiresAtEpochMs?.let { it <= clock() } == true) {
            return@withContext failure(
                DownloadFailure(DownloadFailureReason.EXPIRED_URL, stage = CONNECT),
                emptyCheckpoint,
            )
        }
        val workspace = try {
            prepareWorkspace(plan)
        } catch (error: IOException) {
            return@withContext failure(error.toStorageFailure(OPEN_FILE), emptyCheckpoint)
        }

        var tracker: DashCheckpointTracker? = null
        var firstRange: OpenedRange? = null
        try {
            val wholeFile = plan.wholeFile
            val parsed = if (wholeFile != null) {
                val layout = wholeFileLayout(plan, wholeFile, credentialOrigin, resumeFrom, start)
                firstRange = layout.firstRange
                layout.parsed
            } else {
                manifestLayout(plan, credentialOrigin).also {
                    start.lengthKnown(StartLengthSource.NONE)
                }
            }
            val initial = reconcileCheckpoint(
                workspace = workspace,
                parsed = parsed,
                saved = resumeFrom,
            )
            start.planned()
            val activeTracker = DashCheckpointTracker(
                initial = initial,
                totalBytes = plan.wholeFile?.let {
                    parsed.chunks.sumOf { chunk -> chunk.byteRange?.length ?: 0L }
                },
                progressIntervalMillis = policy.progressIntervalMillis,
                elapsedMillis = elapsedMillis,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
                onFirstProgress = {
                    if (start.firstProgress()) reportStart()
                },
            )
            tracker = activeTracker
            activeTracker.emit()

            val pending = parsed.chunks.filterNot { initial.chunks[it.index].completed }
            val download: suspend (DashDownloadChunk) -> Unit = { chunk ->
                val opened = firstRange?.takeIf { chunk.index == 0 }?.take()
                val bytes = downloadChunkWithRetry(
                    plan = plan,
                    credentialOrigin = credentialOrigin,
                    workspace = workspace,
                    chunk = chunk,
                    tracker = activeTracker,
                    start = start,
                    opened = opened,
                )
                activeTracker.markCompleted(chunk.index, bytes)
            }
            if (policy.fastStart) {
                // No batch barrier (P41): each worker takes the next range as soon as it is free.
                val workers = concurrentChunksFor(plan, credentialOrigin)
                val next = AtomicInteger(0)
                coroutineScope {
                    repeat(minOf(workers, pending.size)) {
                        launch {
                            while (true) {
                                val position = next.getAndIncrement()
                                if (position >= pending.size) break
                                download(pending[position])
                            }
                        }
                    }
                }
            } else {
                pending
                    .chunked(policy.maxConcurrentChunks)
                    .forEach { batch ->
                        coroutineScope {
                            batch.map { chunk -> async { download(chunk) } }.awaitAll()
                        }
                    }
            }

            val completedCheckpoint = activeTracker.snapshot()
            if (completedCheckpoint.completedChunkCount != parsed.chunks.size) {
                throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            val bytesWritten = assemble(
                workspace = workspace,
                chunks = parsed.chunks,
                destination = destination,
            )
            if (bytesWritten != completedCheckpoint.downloadedBytes) {
                throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            cleanupWorkspace(workspace, strict = false)
            activeTracker.finish()
            reportStart()
            DashTransferResult.Completed(bytesWritten, completedCheckpoint)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                runCatching { tracker?.emit() }
                runCatching { reportStart() }
            }
            throw cancellation
        } catch (abort: DashAbort) {
            if (abort.failure.reason in NON_RESUMABLE_FAILURES) {
                cleanupWorkspace(workspace, strict = false)
            }
            reportStart()
            DashTransferResult.Failure(
                failure = abort.failure.copy(startTimeline = start.timeline.summary()),
                checkpoint = tracker?.snapshot()
                    ?: emptyCheckpoint,
            )
        } catch (error: IOException) {
            // Source errors are retried and end as DashAborts; an I/O error here comes from the
            // chunk files of the task's workspace.
            reportStart()
            DashTransferResult.Failure(
                failure = error.toStorageFailure(WRITE_FILE)
                    .copy(startTimeline = start.timeline.summary()),
                checkpoint = tracker?.snapshot()
                    ?: emptyCheckpoint,
            )
        } finally {
            firstRange?.closeUnused()
        }
    }

    /** Ranges at once: more for a whole file on YouTube's media hosts with FAST_START (P41). */
    private fun concurrentChunksFor(plan: DashDownloadPlan, fileUrl: HttpUrl): Int {
        if (plan.wholeFile == null || !policy.fastStart) return policy.maxConcurrentChunks
        val host = fileUrl.host.lowercase(Locale.US)
        val mediaHost = host == MEDIA_HOST || host.endsWith(".$MEDIA_HOST")
        return if (mediaHost) policy.mediaHostConcurrentChunks else policy.maxConcurrentChunks
    }

    private suspend fun manifestLayout(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
    ): DashDownloadManifestParser.Result.Parsed {
        val fetched = fetchManifest(plan, credentialOrigin)
        return when (
            val result = DashDownloadManifestParser.parse(
                manifest = fetched.body,
                manifestUrl = fetched.finalUrl,
                representationId = plan.representationId,
                expectedTrackType = plan.trackType,
                maxChunks = policy.maxChunks,
            )
        ) {
            DashDownloadManifestParser.Result.DrmProtected ->
                throw DashAbort(DownloadFailureReason.DRM_PROTECTED, stage = READ_SOURCE)
            DashDownloadManifestParser.Result.Unsupported ->
                throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE)
            DashDownloadManifestParser.Result.TooManyChunks ->
                throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE)
            DashDownloadManifestParser.Result.Malformed ->
                throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = READ_SOURCE)
            is DashDownloadManifestParser.Result.Parsed -> result
        }
    }

    /**
     * Splits a track served as one file into byte ranges of at most the track's request size.
     *
     * The fingerprint names the track and its length, never the address: a refreshed address
     * for the same file keeps the chunks already downloaded. With FAST_START the first range is
     * small (P41) and the layout has its own fingerprint; a checkpoint saved with the equal
     * ranges of before keeps them. A stated length needs no request; an unknown one comes from
     * the first range's answer, or from a one-byte request when finished ranges are resumed.
     */
    private suspend fun wholeFileLayout(
        plan: DashDownloadPlan,
        track: WholeFileTrack,
        fileUrl: HttpUrl,
        saved: DashTransferCheckpoint?,
        start: StartTimelineRecorder,
    ): WholeFileLayout {
        val chunkBytes = minOf(track.maxRequestBytes, policy.maxChunkBytes)
        val firstBytes = if (policy.fastStart) {
            minOf(policy.firstRangeBytes, chunkBytes)
        } else {
            chunkBytes
        }
        track.totalBytes?.let { totalBytes ->
            start.lengthKnown(StartLengthSource.KNOWN)
            return WholeFileLayout(
                wholeFileChunks(plan, fileUrl, totalBytes, chunkBytes, firstBytes, saved),
            )
        }
        val resumesRanges = saved?.chunks?.any(StreamChunkCheckpoint::completed) == true
        if (!policy.fastStart || resumesRanges) {
            val totalBytes = probeLength(plan, fileUrl)
            start.lengthKnown(StartLengthSource.PROBE)
            return WholeFileLayout(
                wholeFileChunks(plan, fileUrl, totalBytes, chunkBytes, firstBytes, saved),
            )
        }
        val opened = openFirstRange(plan, fileUrl, firstBytes)
        start.lengthKnown(StartLengthSource.FIRST_RANGE)
        return try {
            WholeFileLayout(
                parsed = wholeFileChunks(
                    plan = plan,
                    fileUrl = fileUrl,
                    totalBytes = opened.totalBytes,
                    chunkBytes = chunkBytes,
                    firstBytes = firstBytes,
                    saved = null,
                ),
                firstRange = opened,
            )
        } catch (error: Throwable) {
            opened.closeUnused()
            throw error
        }
    }

    /**
     * The ranges of a whole file of [totalBytes]: [firstBytes], then [chunkBytes] each. The
     * equal ranges of before (first = chunk) keep their fingerprint, and win when [saved] has it.
     */
    private fun wholeFileChunks(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
        totalBytes: Long,
        chunkBytes: Long,
        firstBytes: Long,
        saved: DashTransferCheckpoint?,
    ): DashDownloadManifestParser.Result.Parsed {
        val equalFingerprint = sha256(
            "whole-file|${plan.representationId}|${plan.trackType}|$totalBytes|$chunkBytes",
        )
        val first = if (saved?.manifestFingerprint == equalFingerprint) chunkBytes else firstBytes
        val fingerprint = if (first == chunkBytes) {
            equalFingerprint
        } else {
            sha256(
                "whole-file-v2|${plan.representationId}|${plan.trackType}|$totalBytes|" +
                    "$first|$chunkBytes",
            )
        }
        val firstLength = minOf(first, totalBytes)
        val chunkCount = if (totalBytes == firstLength) {
            1L
        } else {
            (totalBytes - firstLength - 1) / chunkBytes + 2
        }
        if (chunkCount > policy.maxChunks) {
            throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
        }
        val chunks = List(chunkCount.toInt()) { index ->
            val offset = if (index == 0) 0L else firstLength + (index - 1) * chunkBytes
            val length = if (index == 0) firstLength else minOf(chunkBytes, totalBytes - offset)
            DashDownloadChunk(
                index = index,
                url = fileUrl,
                byteRange = ResolvedByteRange(offset, length),
                type = DashChunkType.MEDIA,
            )
        }
        return DashDownloadManifestParser.Result.Parsed(
            chunks = chunks,
            fingerprint = fingerprint,
            trackType = plan.trackType,
            mimeType = plan.mimeType,
            codecs = plan.codecs,
        )
    }

    /**
     * Asks for a whole file's first range and reads the file's length from its Content-Range
     * (P41); the open answer is then written as the first chunk. Network errors and 5xx answers
     * are tried again, as for any range.
     */
    private suspend fun openFirstRange(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
        firstBytes: Long,
    ): OpenedRange {
        var lastReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return openFirstRangeOnce(plan, fileUrl, firstBytes)
            } catch (retry: RetryableDashFailure) {
                lastReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw DashAbort(retry.failure())
                delay(backoffMillis(attempt))
            }
        }
        throw DashAbort(lastReason, stage = CONNECT)
    }

    private suspend fun openFirstRangeOnce(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
        firstBytes: Long,
    ): OpenedRange {
        val execution = try {
            http.execute(
                credentialOrigin = fileUrl,
                initialUrl = fileUrl,
                context = plan.requestContext,
            ) {
                get()
                header("Range", "bytes=0-${firstBytes - 1}")
            }
        } catch (error: IOException) {
            throw RetryableDashFailure.network(CONNECT, error)
        }
        val response = when (execution) {
            is SecureDownloadHttp.Result.Failed ->
                throw DashAbort(execution.reason, stage = CONNECT)
            is SecureDownloadHttp.Result.Completed -> execution.response
        }
        try {
            if (response.code in 500..599) {
                throw RetryableDashFailure(DownloadFailureReason.SERVER_ERROR, response.code)
            }
            // A server that ignores ranges cannot be fetched in chunks.
            if (response.code == 200) {
                throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
            }
            if (response.code != 206) {
                throw DashAbort(response.code.toFailureReason(), response.code, CONNECT)
            }
            val totalBytes = response.header("Content-Range")
                ?.trim()
                ?.let(FIRST_RANGE::matchEntire)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
            return OpenedRange(response, totalBytes)
        } catch (error: Throwable) {
            response.close()
            throw error
        }
    }

    /** Reads a whole file's length from the answer to a one-byte ranged request. */
    private suspend fun probeLength(plan: DashDownloadPlan, fileUrl: HttpUrl): Long {
        var lastReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return probeLengthOnce(plan, fileUrl)
            } catch (retry: RetryableDashFailure) {
                lastReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw DashAbort(retry.failure())
                delay(backoffMillis(attempt))
            }
        }
        throw DashAbort(lastReason, stage = CONNECT)
    }

    private suspend fun probeLengthOnce(plan: DashDownloadPlan, fileUrl: HttpUrl): Long {
        val execution = try {
            http.execute(
                credentialOrigin = fileUrl,
                initialUrl = fileUrl,
                context = plan.requestContext,
            ) {
                get()
                header("Range", "bytes=0-0")
            }
        } catch (error: IOException) {
            throw RetryableDashFailure.network(CONNECT, error)
        }
        return when (execution) {
            is SecureDownloadHttp.Result.Failed ->
                throw DashAbort(execution.reason, stage = CONNECT)
            is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                if (response.code in 500..599) {
                    throw RetryableDashFailure(
                        DownloadFailureReason.SERVER_ERROR,
                        response.code,
                    )
                }
                // A server that ignores ranges cannot be fetched in chunks.
                if (response.code == 200) {
                    throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
                }
                if (response.code != 206) {
                    throw DashAbort(response.code.toFailureReason(), response.code, CONNECT)
                }
                response.header("Content-Range")
                    ?.trim()
                    ?.let(FIRST_BYTE_RANGE::matchEntire)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?: throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
            }
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte) }

    override suspend fun discard(plan: DashDownloadPlan) = withContext(Dispatchers.IO) {
        val root = workspaceRoot.canonicalFile
        val workspace = workspaceFor(plan.taskId).canonicalFile
        if (workspace.parentFile != root) throw IOException("Unsafe DASH workspace")
        cleanupWorkspace(workspace, strict = true)
    }

    private suspend fun fetchManifest(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
    ): FetchedManifest {
        var lastReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return fetchManifestOnce(plan, credentialOrigin)
            } catch (retry: RetryableDashFailure) {
                lastReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw DashAbort(retry.failure())
                delay(backoffMillis(attempt))
            }
        }
        throw DashAbort(lastReason, stage = CONNECT)
    }

    private suspend fun fetchManifestOnce(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
    ): FetchedManifest {
        val execution = try {
            http.execute(
                credentialOrigin = credentialOrigin,
                initialUrl = credentialOrigin,
                context = plan.requestContext,
            ) {
                get()
                header("Accept", DASH_ACCEPT)
            }
        } catch (error: IOException) {
            throw RetryableDashFailure.network(CONNECT, error)
        }
        return when (execution) {
            is SecureDownloadHttp.Result.Failed ->
                throw DashAbort(execution.reason, stage = CONNECT)
            is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                if (response.code in 500..599) {
                    throw RetryableDashFailure(
                        DownloadFailureReason.SERVER_ERROR,
                        response.code,
                    )
                }
                if (response.code != 200) {
                    throw DashAbort(response.code.toFailureReason(), response.code, CONNECT)
                }
                if (
                    response.header("Content-Encoding")
                        ?.equals("identity", ignoreCase = true) == false
                ) {
                    throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
                }
                val body = response.body
                    ?: throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
                if (body.contentLength() > policy.maxManifestBytes) {
                    throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
                }
                val bytes = try {
                    body.byteStream().readAtMost(policy.maxManifestBytes + 1)
                } catch (error: IOException) {
                    throw RetryableDashFailure.network(READ_SOURCE, error)
                }
                if (bytes.size > policy.maxManifestBytes) {
                    throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE)
                }
                FetchedManifest(
                    finalUrl = execution.finalUrl,
                    body = bytes.toString(Charsets.UTF_8),
                )
            }
        }
    }

    private fun reconcileCheckpoint(
        workspace: File,
        parsed: DashDownloadManifestParser.Result.Parsed,
        saved: DashTransferCheckpoint?,
    ): DashTransferCheckpoint {
        val reusable = saved
            ?.takeIf { it.manifestFingerprint == parsed.fingerprint }
            ?.takeIf { it.chunks.size == parsed.chunks.size }
        if (reusable == null) {
            clearChunkFiles(workspace)
        }
        val chunks = parsed.chunks.map { chunk ->
            val stored = reusable?.chunks?.getOrNull(chunk.index)
            val file = readyFile(workspace, chunk.index)
            if (
                stored?.completed == true &&
                stored.downloadedBytes in 1..policy.maxChunkBytes &&
                file.isFile &&
                file.length() == stored.downloadedBytes
            ) {
                stored
            } else {
                if (file.exists() && !file.delete()) {
                    throw IOException("Cannot reset stale DASH chunk")
                }
                StreamChunkCheckpoint(
                    index = chunk.index,
                    downloadedBytes = 0,
                    completed = false,
                )
            }
        }
        return DashTransferCheckpoint(
            manifestFingerprint = parsed.fingerprint,
            chunks = chunks,
        )
    }

    /**
     * Downloads one chunk, trying a network error or a 5xx answer again. The bytes an attempt
     * counted in the progress are taken back when it fails (P41). [opened] is the first range's
     * answer that is already open, for the first attempt.
     */
    private suspend fun downloadChunkWithRetry(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        workspace: File,
        chunk: DashDownloadChunk,
        tracker: DashCheckpointTracker,
        start: StartTimelineRecorder,
        opened: Response? = null,
    ): Long {
        var lastRetryReason = DownloadFailureReason.NETWORK
        var response = opened
        repeat(policy.maxAttempts) { attempt ->
            val counter = RangeCounter(tracker, start)
            try {
                return downloadChunkOnce(
                    plan = plan,
                    credentialOrigin = credentialOrigin,
                    workspace = workspace,
                    chunk = chunk,
                    counter = counter,
                    opened = response.also { response = null },
                )
            } catch (retry: RetryableDashFailure) {
                counter.takeBack()
                lastRetryReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw DashAbort(retry.failure())
                delay(backoffMillis(attempt))
            } catch (error: Throwable) {
                counter.takeBack()
                throw error
            }
        }
        throw DashAbort(lastRetryReason, stage = CONNECT)
    }

    private suspend fun downloadChunkOnce(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        workspace: File,
        chunk: DashDownloadChunk,
        counter: RangeCounter,
        opened: Response?,
    ): Long {
        val temporary = downloadingFile(workspace, chunk.index)
        val ready = readyFile(workspace, chunk.index)
        try {
            if (temporary.exists() && !temporary.delete()) {
                throw IOException("Cannot reset temporary DASH chunk")
            }
            if (ready.exists() && !ready.delete()) {
                throw IOException("Cannot reset completed DASH chunk")
            }
        } catch (error: IOException) {
            opened?.close()
            throw error
        }
        val execution = opened?.let { response ->
            SecureDownloadHttp.Result.Completed(response, response.request.url)
        } ?: try {
            http.execute(
                credentialOrigin = credentialOrigin,
                initialUrl = chunk.url,
                context = plan.requestContext,
            ) {
                get()
                chunk.byteRange?.let { range ->
                    header("Range", "bytes=${range.offset}-${range.endInclusive}")
                }
            }
        } catch (error: IOException) {
            throw RetryableDashFailure.network(CONNECT, error)
        }
        try {
            return when (execution) {
                is SecureDownloadHttp.Result.Failed ->
                    throw DashAbort(execution.reason, stage = CONNECT)
                is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                    validateChunkResponse(response, chunk)
                    writeChunk(response, chunk, temporary, counter)
                }
            }.also { bytes ->
                if (!temporary.renameTo(ready)) {
                    throw IOException("Cannot finalize temporary DASH chunk")
                }
                if (ready.length() != bytes) {
                    throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                }
            }
        } finally {
            if (temporary.exists()) runCatching { temporary.delete() }
        }
    }

    private fun validateChunkResponse(
        response: Response,
        chunk: DashDownloadChunk,
    ) {
        if (response.code in 500..599) {
            throw RetryableDashFailure(
                DownloadFailureReason.SERVER_ERROR,
                response.code,
            )
        }
        val range = chunk.byteRange
        if (range == null) {
            if (response.code != 200) {
                throw DashAbort(response.code.toFailureReason(), response.code, CONNECT)
            }
        } else {
            if (response.code != 206) {
                throw DashAbort(response.code.toFailureReason(), response.code, CONNECT)
            }
            val returned = response.header("Content-Range")
                ?.parseContentRange()
                ?: throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
            if (returned.first != range.offset || returned.second != range.endInclusive) {
                throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = CONNECT)
            }
        }
        if (
            response.header("Content-Encoding")
                ?.equals("identity", ignoreCase = true) == false
        ) {
            throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
        }
        val advertised = response.header("Content-Length")
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
        if (advertised != null && advertised > policy.maxChunkBytes) {
            throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
        }
        if (range != null && advertised != null && advertised != range.length) {
            throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = CONNECT)
        }
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
        if (mimeType == "text/html" || mimeType == "application/xhtml+xml") {
            throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
        }
    }

    private suspend fun writeChunk(
        response: Response,
        chunk: DashDownloadChunk,
        destination: File,
        counter: RangeCounter,
    ): Long {
        val body = response.body
            ?: throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
        val file = try {
            FileOutputStream(destination)
        } catch (error: IOException) {
            throw DashAbort(error.toStorageFailure(OPEN_FILE))
        }
        return try {
            file.use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(policy.bufferBytes)
                var written = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = try {
                        input.read(buffer)
                    } catch (error: IOException) {
                        throw RetryableDashFailure.network(READ_SOURCE, error)
                    }
                    if (read == -1) break
                    if (written > policy.maxChunkBytes - read) {
                        throw DashAbort(
                            DownloadFailureReason.UNSUPPORTED_SOURCE,
                            stage = READ_SOURCE,
                        )
                    }
                    try {
                        output.write(buffer, 0, read)
                    } catch (error: IOException) {
                        throw DashAbort(error.toStorageFailure(WRITE_FILE))
                    }
                    written += read
                    counter.add(read.toLong())
                }
                if (written == 0L) {
                    throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = READ_SOURCE)
                }
                chunk.byteRange?.let { range ->
                    if (written != range.length) {
                        throw DashAbort(
                            DownloadFailureReason.INTEGRITY_MISMATCH,
                            stage = READ_SOURCE,
                        )
                    }
                }
                try {
                    output.fd.sync()
                } catch (error: IOException) {
                    throw DashAbort(error.toStorageFailure(WRITE_FILE))
                }
                written
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (retry: RetryableDashFailure) {
            throw retry
        } catch (abort: DashAbort) {
            throw abort
        } catch (error: IOException) {
            throw DashAbort(error.toStorageFailure(WRITE_FILE))
        }
    }

    private suspend fun assemble(
        workspace: File,
        chunks: List<DashDownloadChunk>,
        destination: DownloadDestination,
    ): Long {
        val files = chunks.map { chunk ->
            readyFile(workspace, chunk.index)
                .takeIf(File::isFile)
                ?: throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
        }
        val totalBytes = files.fold(0L) { total, file ->
            val length = file.length()
            if (length <= 0 || total > Long.MAX_VALUE - length) {
                throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            total + length
        }
        // Every call below is on a file, so whatever fails is storage, at the step it reached.
        var stage = OPEN_FILE
        try {
            destination.prepare(totalBytes)
            val output = destination.open()
            stage = WRITE_FILE
            output.use {
                val buffer = ByteArray(policy.bufferBytes)
                var position = 0L
                files.forEach { file ->
                    file.inputStream().use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(position, buffer, 0, read)
                            position += read
                        }
                    }
                }
                output.sync()
                if (position != totalBytes) {
                    throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                }
            }
            stage = VERIFY
            if (destination.temporaryLength() != totalBytes) {
                throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            stage = PUBLISH
            destination.commit()
            return totalBytes
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (abort: DashAbort) {
            throw abort
        } catch (error: Exception) {
            if (!error.isStorageError()) throw error
            throw DashAbort(error.toStorageFailure(stage))
        }
    }

    private fun prepareWorkspace(plan: DashDownloadPlan): File {
        val root = workspaceRoot.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) {
            throw IOException("Cannot create DASH workspace")
        }
        val workspace = workspaceFor(plan.taskId).canonicalFile
        if (workspace.parentFile != root) {
            throw IOException("Unsafe DASH workspace")
        }
        if (!workspace.isDirectory && !workspace.mkdirs()) {
            throw IOException("Cannot create DASH task workspace")
        }
        return workspace
    }

    private fun workspaceFor(taskId: String): File =
        File(workspaceRoot, DownloadWorkspaces.nameFor(DownloadWorkspaces.DASH_PREFIX, taskId))

    private fun clearChunkFiles(workspace: File) {
        workspace.listFiles().orEmpty().forEach { file ->
            if (file.isDirectory || !file.delete()) {
                throw IOException("Cannot reset DASH workspace")
            }
        }
    }

    private fun cleanupWorkspace(
        workspace: File,
        strict: Boolean,
    ) {
        if (!workspace.exists()) return
        var failed = false
        workspace.walkBottomUp().forEach { file ->
            if (!file.delete()) failed = true
        }
        if (strict && failed) throw IOException("Cannot clean DASH workspace")
    }

    private fun readyFile(workspace: File, index: Int): File =
        File(workspace, "chunk-${index.toString().padStart(CHUNK_INDEX_DIGITS, '0')}.ready")

    private fun downloadingFile(workspace: File, index: Int): File =
        File(workspace, "chunk-${index.toString().padStart(CHUNK_INDEX_DIGITS, '0')}.downloading")

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, policy.bufferBytes))
        val buffer = ByteArray(policy.bufferBytes)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun String.parseContentRange(): Pair<Long, Long>? {
        val match = CONTENT_RANGE.matchEntire(trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        return if (start >= 0 && end >= start) start to end else null
    }

    private fun Int.toFailureReason(): DownloadFailureReason = when (this) {
        401 -> DownloadFailureReason.AUTHENTICATION_REQUIRED
        403 -> DownloadFailureReason.ACCESS_DENIED
        404 -> DownloadFailureReason.NOT_FOUND
        410 -> DownloadFailureReason.GONE
        416 -> DownloadFailureReason.RANGE_NOT_SATISFIABLE
        in 500..599 -> DownloadFailureReason.SERVER_ERROR
        else -> DownloadFailureReason.HTTP_STATUS
    }

    private fun backoffMillis(attempt: Int): Long {
        if (policy.initialRetryDelayMillis == 0L) return 0
        return policy.initialRetryDelayMillis * (1L shl attempt.coerceAtMost(4))
    }

    private fun failure(
        failure: DownloadFailure,
        checkpoint: DashTransferCheckpoint,
    ): DashTransferResult.Failure = DashTransferResult.Failure(failure, checkpoint)

    private class DashAbort(val failure: DownloadFailure) : Exception() {
        constructor(
            reason: DownloadFailureReason,
            httpStatusCode: Int? = null,
            stage: DownloadFailureStage? = null,
        ) : this(DownloadFailure(reason, httpStatusCode, stage))
    }

    /** A source failure that another attempt may fix: a network error or a 5xx answer. */
    private class RetryableDashFailure(
        val reason: DownloadFailureReason,
        val httpStatusCode: Int? = null,
        val stage: DownloadFailureStage = CONNECT,
        val detail: String? = null,
    ) : Exception() {
        fun failure(): DownloadFailure = DownloadFailure(reason, httpStatusCode, stage, detail)

        companion object {
            fun network(stage: DownloadFailureStage, error: IOException): RetryableDashFailure =
                RetryableDashFailure(
                    reason = DownloadFailureReason.NETWORK,
                    stage = stage,
                    detail = DownloadFailureDetails.of(error),
                )
        }
    }

    private data class FetchedManifest(
        val finalUrl: HttpUrl,
        val body: String,
    )

    /** A whole file's ranges, with the first range's answer when it gave the length. */
    private class WholeFileLayout(
        val parsed: DashDownloadManifestParser.Result.Parsed,
        val firstRange: OpenedRange? = null,
    )

    /** The open answer to a whole file's first range (P41), written by the first worker. */
    private class OpenedRange(
        private val response: Response,
        val totalBytes: Long,
    ) {
        private val taken = AtomicBoolean(false)

        fun take(): Response? = response.takeIf { taken.compareAndSet(false, true) }

        fun closeUnused() {
            if (taken.compareAndSet(false, true)) runCatching { response.close() }
        }
    }

    /** The bytes one attempt at a chunk wrote, counted in the progress as they come (P41). */
    private class RangeCounter(
        private val tracker: DashCheckpointTracker,
        private val start: StartTimelineRecorder,
    ) {
        private var counted = 0L

        suspend fun add(bytes: Long) {
            if (bytes <= 0) return
            if (counted == 0L) start.firstByte()
            counted += bytes
            tracker.advance(bytes)
        }

        /** A failed attempt takes back what it counted; the progress shown stays (P41). */
        fun takeBack() {
            if (counted > 0) tracker.takeBack(counted)
            counted = 0
        }
    }

    /**
     * Keeps the checkpoint and the progress of one transfer.
     *
     * The checkpoint marks a chunk done only after its sync. Progress adds the bytes of the
     * chunks still downloading (P41), never goes back (a failed attempt's bytes are taken back
     * without lowering what was shown), never passes the total, and comes at most every
     * [progressIntervalMillis]; the first bytes and the end are reported at once.
     */
    private class DashCheckpointTracker(
        initial: DashTransferCheckpoint,
        private val totalBytes: Long?,
        private val progressIntervalMillis: Long,
        private val elapsedMillis: () -> Long,
        private val onProgress: suspend (DownloadProgress) -> Unit,
        private val onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        private val onFirstProgress: () -> Unit,
    ) {
        private val gate = Mutex()
        private var checkpoint = initial
        private val inFlight = AtomicLong(0)
        private val lastProgressAt = AtomicLong(NEVER)
        private var shownBytes = initial.downloadedBytes
        private var reportedBytes = -1L

        /** Counts [bytes] of a chunk still downloading; reports them when it is time. */
        suspend fun advance(bytes: Long) {
            inFlight.addAndGet(bytes)
            val now = elapsedMillis()
            val last = lastProgressAt.get()
            if (last != NEVER && now - last < progressIntervalMillis) return
            if (!lastProgressAt.compareAndSet(last, now)) return
            gate.withLock { emitProgressLocked() }
        }

        fun takeBack(bytes: Long) {
            inFlight.addAndGet(-bytes)
        }

        suspend fun markCompleted(index: Int, downloadedBytes: Long) = gate.withLock {
            inFlight.addAndGet(-downloadedBytes)
            checkpoint = checkpoint.copy(
                chunks = checkpoint.chunks.map { chunk ->
                    if (chunk.index == index) {
                        chunk.copy(
                            downloadedBytes = downloadedBytes,
                            completed = true,
                        )
                    } else {
                        chunk
                    }
                },
            )
            val now = elapsedMillis()
            val last = lastProgressAt.get()
            val due = last == NEVER || now - last >= progressIntervalMillis
            if (due && lastProgressAt.compareAndSet(last, now)) emitProgressLocked()
            onCheckpoint(checkpoint)
        }

        /** Progress and checkpoint now, as at the start and at a stop. */
        suspend fun emit() = gate.withLock {
            emitProgressLocked(force = true)
            onCheckpoint(checkpoint)
        }

        /** The last progress, when the pace held it back. */
        suspend fun finish() = gate.withLock { emitProgressLocked() }

        suspend fun snapshot(): DashTransferCheckpoint = gate.withLock { checkpoint }

        private suspend fun emitProgressLocked(force: Boolean = false) {
            val counted = checkpoint.downloadedBytes + inFlight.get().coerceAtLeast(0)
            val capped = totalBytes?.let { counted.coerceAtMost(it) } ?: counted
            val moved = capped > shownBytes
            shownBytes = maxOf(shownBytes, capped)
            if (!force && shownBytes == reportedBytes) return
            reportedBytes = shownBytes
            onProgress(
                DownloadProgress(
                    downloadedBytes = shownBytes,
                    totalBytes = totalBytes?.takeIf { it >= shownBytes },
                    activeSegmentCount = checkpoint.chunks.count { !it.completed },
                ),
            )
            if (moved) onFirstProgress()
        }

        private companion object {
            const val NEVER = Long.MIN_VALUE
        }
    }

    private companion object {
        const val CHUNK_INDEX_DIGITS = 5
        const val DASH_ACCEPT =
            "application/dash+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.1"
        val CONTENT_RANGE = Regex("""(?i)^bytes\s+(\d+)-(\d+)/(?:\d+|\*)$""")
        val FIRST_BYTE_RANGE = Regex("""(?i)^bytes\s+0-0/(\d+)$""")
        val FIRST_RANGE = Regex("""(?i)^bytes\s+0-\d+/(\d+)$""")
        const val MEDIA_HOST = "googlevideo.com"
        val NON_RESUMABLE_FAILURES = setOf(
            DownloadFailureReason.INVALID_URL,
            DownloadFailureReason.DRM_PROTECTED,
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            DownloadFailureReason.MALFORMED_RESPONSE,
            DownloadFailureReason.INTEGRITY_MISMATCH,
        )
    }
}

private fun logStartLine(message: String) {
    runCatching { android.util.Log.i("YftDownloads", message) }
}
