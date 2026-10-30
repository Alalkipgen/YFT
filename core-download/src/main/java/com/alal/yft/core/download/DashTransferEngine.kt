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
import com.alal.yft.core.model.media.MediaTrackType
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
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
        /** Attempts for one piece of a live stream ([LivePieces]): thousands are asked. */
        val livePieceAttempts: Int = 5,
        /**
         * F8 (D7 run 12): a whole file on YouTube's media hosts whose range is refused with 403
         * asks its ranges one at a time and the refused range again ([MediaHostRefusals]), the
         * first time after [mediaHostRefusalFirstWaitMillis], then twice as long each time up to
         * [mediaHostRefusalMaxWaitMillis], while it is within this time of its first refusal in
         * a row; 0: a 403 fails at once, as before. F8 trim (owner, 2026-10-19, after D7 runs 13
         * and 13b, where 45 s of asking again never got an answer): 5 s, so a refused range is
         * asked again once, 3 s after the refusal (the next wait, 6 s, would end past it).
         */
        val mediaHostRefusalWaitMillis: Long = 5_000,
        val mediaHostRefusalFirstWaitMillis: Long = 3_000,
        val mediaHostRefusalMaxWaitMillis: Long = 8_000,
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
            require(livePieceAttempts in 1..8)
            require(mediaHostRefusalWaitMillis >= 0)
            require(mediaHostRefusalFirstWaitMillis > 0)
            require(mediaHostRefusalMaxWaitMillis >= mediaHostRefusalFirstWaitMillis)
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
            // A live stream's file is asked piece by piece, sq 0 to the newest (run 4f).
            val live = wholeFile != null && LiveOriginFile.matches(credentialOrigin)
            // A whole file's length is known before its ranges; so is a SegmentBase file's (59).
            var lengthKnown = wholeFile != null && !live
            val parsed = if (live) {
                start.lengthKnown(StartLengthSource.UNTIL_END)
                liveLayout(plan, credentialOrigin)
            } else if (wholeFile != null) {
                val layout = wholeFileLayout(plan, wholeFile, credentialOrigin, resumeFrom, start)
                firstRange = layout.firstRange
                layout.parsed
            } else {
                val layout = manifestLayout(plan, credentialOrigin, resumeFrom, start)
                firstRange = layout.firstRange
                lengthKnown = layout.oneFile
                layout.parsed
            }
            val initial = reconcileCheckpoint(
                workspace = workspace,
                parsed = parsed,
                saved = resumeFrom,
            )
            start.planned()
            val activeTracker = DashCheckpointTracker(
                initial = initial,
                totalBytes = if (lengthKnown) {
                    parsed.chunks.sumOf { chunk -> chunk.byteRange?.length ?: 0L }
                } else {
                    null
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

            var pending = parsed.chunks.filterNot { initial.chunks[it.index].completed }
            val pieces = parsed.chunks.size.takeIf { live }
            val refusals = refusalsFor(plan, credentialOrigin, live)
            val download: suspend (DashDownloadChunk) -> Unit = { chunk ->
                val opened = firstRange?.takeIf { chunk.index == 0 }?.take()
                val bytes = try {
                    downloadChunkWithRetry(
                        plan = plan,
                        credentialOrigin = credentialOrigin,
                        workspace = workspace,
                        chunk = chunk,
                        tracker = activeTracker,
                        start = start,
                        opened = opened,
                        livePieces = pieces,
                        refusals = refusals,
                    )
                } catch (abort: DashAbort) {
                    // The newest pieces may not be served: a missing one ends the file there.
                    val tail = pieces != null && chunk.index > 0 &&
                        chunk.index >= pieces - LivePieces.TAIL_PIECES
                    if (!tail) throw abort
                    0L
                }
                activeTracker.markCompleted(chunk.index, bytes)
            }
            if (live) {
                // Piece 0 first: when it does not start a fragmented MP4, nothing more is asked.
                pending.firstOrNull { it.index == 0 }?.let { first ->
                    try {
                        download(first)
                    } catch (abort: DashAbort) {
                        throw abort.notReadyWhenMissing()
                    }
                }
                checkFirstPiece(workspace, plan)
                pending = pending.filter { it.index != 0 }
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
            val bytesWritten = if (live) {
                assembleLive(workspace, parsed.chunks, destination, plan)
            } else {
                assemble(
                    workspace = workspace,
                    chunks = parsed.chunks,
                    destination = destination,
                )
            }
            if (!live && bytesWritten != completedCheckpoint.downloadedBytes) {
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
        return if (isMediaHost(fileUrl)) {
            policy.mediaHostConcurrentChunks
        } else {
            policy.maxConcurrentChunks
        }
    }

    /**
     * F8: the refusals of a whole file on YouTube's media hosts, which asks a range refused with
     * 403 again ([Policy.mediaHostRefusalWaitMillis]); null: a 403 fails at once.
     */
    private fun refusalsFor(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
        live: Boolean,
    ): MediaHostRefusals? {
        if (plan.wholeFile == null || live || policy.mediaHostRefusalWaitMillis == 0L) return null
        if (!isMediaHost(fileUrl)) return null
        return MediaHostRefusals(
            limitMillis = policy.mediaHostRefusalWaitMillis,
            firstWaitMillis = policy.mediaHostRefusalFirstWaitMillis,
            maxWaitMillis = policy.mediaHostRefusalMaxWaitMillis,
            elapsedMillis = elapsedMillis,
            isRefusal = { error ->
                error is DashAbort &&
                    error.failure.httpStatusCode == HTTP_FORBIDDEN &&
                    error.failure.stage == CONNECT
            },
        )
    }

    private fun isMediaHost(fileUrl: HttpUrl): Boolean {
        val host = fileUrl.host.lowercase(Locale.US)
        return host == MEDIA_HOST || host.endsWith(".$MEDIA_HOST")
    }

    private suspend fun manifestLayout(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        saved: DashTransferCheckpoint?,
        start: StartTimelineRecorder,
    ): WholeFileLayout {
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
            is DashDownloadManifestParser.Result.Parsed -> {
                start.lengthKnown(StartLengthSource.NONE)
                WholeFileLayout(result)
            }
            is DashDownloadManifestParser.Result.OneFile ->
                oneFileLayout(plan, result, credentialOrigin, saved, start)
        }
    }

    /**
     * v1.0.2 item 59: a `SegmentBase` representation is one file
     * ([DashDownloadManifestParser.Result.OneFile]), asked in byte ranges as a whole file is
     * ([wholeFileLayout]: its length from the first range's answer, a resume's from a one-byte
     * request; the checkpoint's ranges kept while the file's length and its manifest's byte
     * layout stay the same), each request sent as from the manifest ([credentialOrigin]). Its
     * index (`sidx`) is not read: the ranges need none. A file that ends before the
     * initialization or the index its manifest places in it is not the manifest's file:
     * INTEGRITY_MISMATCH before any range is kept.
     */
    private suspend fun oneFileLayout(
        plan: DashDownloadPlan,
        file: DashDownloadManifestParser.Result.OneFile,
        credentialOrigin: HttpUrl,
        saved: DashTransferCheckpoint?,
        start: StartTimelineRecorder,
    ): WholeFileLayout {
        val layout = wholeFileLayout(
            plan = plan,
            track = WholeFileTrack(),
            fileUrl = file.url,
            saved = saved,
            start = start,
            credentialOrigin = credentialOrigin,
            proof = file.layoutProof,
        )
        val totalBytes = layout.parsed.chunks.sumOf { it.byteRange?.length ?: 0L }
        val statedEnd = file.statedEndInclusive
        if (statedEnd != null && statedEnd >= totalBytes) {
            layout.firstRange?.closeUnused()
            val part = if (file.index?.endInclusive == statedEnd) "index" else "initialization"
            throw DashAbort(
                DownloadFailure(
                    reason = DownloadFailureReason.INTEGRITY_MISMATCH,
                    stage = VERIFY,
                    detail = "the file is $totalBytes bytes; its manifest's $part ends at " +
                        "byte $statedEnd",
                ),
            )
        }
        return WholeFileLayout(layout.parsed, layout.firstRange, oneFile = true)
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
        /** Whose request a range is: the file's own, or a SegmentBase file's manifest (59). */
        credentialOrigin: HttpUrl = fileUrl,
        /** More of the file's facts for its fingerprint (59: its manifest's byte layout). */
        proof: String? = null,
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
                wholeFileChunks(plan, fileUrl, totalBytes, chunkBytes, firstBytes, saved, proof),
            )
        }
        val resumesRanges = saved?.chunks?.any(StreamChunkCheckpoint::completed) == true
        if (!policy.fastStart || resumesRanges) {
            val totalBytes = probeLength(plan, fileUrl, credentialOrigin)
            start.lengthKnown(StartLengthSource.PROBE)
            return WholeFileLayout(
                wholeFileChunks(plan, fileUrl, totalBytes, chunkBytes, firstBytes, saved, proof),
            )
        }
        val opened = openFirstRange(plan, fileUrl, firstBytes, credentialOrigin)
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
                    proof = proof,
                ),
                firstRange = opened,
            )
        } catch (error: Throwable) {
            opened.closeUnused()
            throw error
        }
    }

    /**
     * A live stream's file as pieces (run 4f): the newest piece's number from a HEAD of its
     * address ([liveHead]), then one chunk per piece, `sq` 0 up to it, as yt-dlp asks them. WebM
     * pieces are not joined. The fingerprint names the track and the newest piece, never the
     * address: a resume keeps the pieces while the stream has not grown.
     */
    private suspend fun liveLayout(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
    ): DashDownloadManifestParser.Result.Parsed {
        if (LivePieces.isWebm(plan.mimeType)) {
            throw DashAbort(
                DownloadFailure(
                    reason = DownloadFailureReason.UNSUPPORTED_SOURCE,
                    stage = CONNECT,
                    detail = "live WebM pieces are not joined; pick an MP4 quality",
                ),
            )
        }
        trackKind(plan)
        val head = liveHead(plan, fileUrl)
        if (head >= policy.maxChunks) {
            throw DashAbort(
                DownloadFailure(
                    reason = DownloadFailureReason.UNSUPPORTED_SOURCE,
                    stage = CONNECT,
                    detail = "live: ${head + 1} pieces, more than ${policy.maxChunks}",
                ),
            )
        }
        val chunks = List(head + 1) { sq ->
            DashDownloadChunk(
                index = sq,
                url = LivePieces.pieceUrl(fileUrl, sq),
                byteRange = null,
                type = DashChunkType.MEDIA,
            )
        }
        return DashDownloadManifestParser.Result.Parsed(
            chunks = chunks,
            fingerprint = sha256("live-pieces|${plan.representationId}|${plan.trackType}|$head"),
            trackType = plan.trackType,
            mimeType = plan.mimeType,
            codecs = plan.codecs,
        )
    }

    /**
     * The newest piece's number: the `X-Head-Seqnum` of a HEAD of the live file's address. A
     * network error or a 5xx answer is tried again; an answer without it: not ready yet.
     */
    private suspend fun liveHead(plan: DashDownloadPlan, fileUrl: HttpUrl): Int {
        repeat(policy.maxAttempts) { attempt ->
            try {
                return liveHeadOnce(plan, fileUrl)
            } catch (retry: RetryableDashFailure) {
                if (attempt == policy.maxAttempts - 1) {
                    throw DashAbort(retry.noted("live head").failure())
                }
                delay(retry.retryAfterMillis ?: backoffMillis(attempt))
            }
        }
        throw DashAbort(DownloadFailureReason.NETWORK, stage = CONNECT)
    }

    private suspend fun liveHeadOnce(plan: DashDownloadPlan, fileUrl: HttpUrl): Int {
        val execution = try {
            http.execute(
                credentialOrigin = fileUrl,
                initialUrl = fileUrl,
                context = plan.requestContext,
            ) {
                head()
            }
        } catch (error: IOException) {
            throw RetryableDashFailure.network(CONNECT, error)
        }
        val response = when (execution) {
            is SecureDownloadHttp.Result.Failed ->
                throw DashAbort(execution.reason, stage = CONNECT)
            is SecureDownloadHttp.Result.Completed -> execution.response
        }
        return response.use {
            val said = "live head: ${response.code} ${pieceNumbers(response)}"
            if (response.code in 500..599) {
                throw RetryableDashFailure(
                    reason = DownloadFailureReason.SERVER_ERROR,
                    httpStatusCode = response.code,
                    detail = said,
                )
            }
            if (response.code !in 200..299) {
                throw DashAbort(
                    DownloadFailure(response.code.toFailureReason(), response.code, CONNECT, said),
                )
            }
            LivePieces.head(response) ?: throw DashAbort(
                DownloadFailure(
                    reason = DownloadFailureReason.LIVE_NOT_READY,
                    httpStatusCode = response.code,
                    stage = CONNECT,
                    detail = "live head: ${response.code}, no X-Head-Seqnum",
                ),
            )
        }
    }

    /** The merge's track kind for a live file's plan; a live file is one track. */
    private fun trackKind(plan: DashDownloadPlan): StreamCopyTrackKind = when (plan.trackType) {
        MediaTrackType.VIDEO -> StreamCopyTrackKind.VIDEO
        MediaTrackType.AUDIO -> StreamCopyTrackKind.AUDIO
        MediaTrackType.AUDIO_VIDEO -> throw DashAbort(
            DownloadFailure(
                reason = DownloadFailureReason.UNSUPPORTED_SOURCE,
                stage = CONNECT,
                detail = "live pieces with sound and picture are not joined",
            ),
        )
    }

    /** Piece 0 must start a fragmented MP4 of the plan's track; else YouTube is not ready. */
    private fun checkFirstPiece(workspace: File, plan: DashDownloadPlan) {
        val kind = trackKind(plan)
        try {
            LivePieceJoin.join(listOf(readyFile(workspace, 0)), tailFrom = 1).use { join ->
                Mp4TrackReader(join, kind).read()
            }
        } catch (problem: LivePieceProblem) {
            throw DashAbort(liveFailure(DownloadFailureReason.LIVE_NOT_READY, problem.message))
        } catch (problem: StreamCopyUnsupported) {
            throw DashAbort(
                liveFailure(DownloadFailureReason.LIVE_NOT_READY, "sq 0: ${problem.message}"),
            )
        } catch (problem: EOFException) {
            throw DashAbort(
                liveFailure(DownloadFailureReason.LIVE_NOT_READY, "sq 0: ${problem.message}"),
            )
        }
    }

    private fun joinFailure(what: String?): DownloadFailure =
        liveFailure(DownloadFailureReason.INTEGRITY_MISMATCH, "join: $what")

    private fun liveFailure(reason: DownloadFailureReason, what: String?): DownloadFailure =
        DownloadFailure(
            reason = reason,
            stage = VERIFY,
            detail = DownloadFailureDetails.sanitize("live: $what"),
        )

    /**
     * Joins the downloaded pieces ([LivePieceJoin]), checks the joined track with the merge's
     * own reader and its length against the video's, and writes it to [destination] (run 4f).
     */
    private suspend fun assembleLive(
        workspace: File,
        chunks: List<DashDownloadChunk>,
        destination: DownloadDestination,
        plan: DashDownloadPlan,
    ): Long {
        val kind = trackKind(plan)
        val join = try {
            LivePieceJoin.join(
                files = chunks.map { chunk -> readyFile(workspace, chunk.index) },
                tailFrom = chunks.size - LivePieces.TAIL_PIECES,
            )
        } catch (problem: LivePieceProblem) {
            throw DashAbort(joinFailure(problem.message))
        }
        join.use {
            val track = try {
                Mp4TrackReader(join, kind).read()
            } catch (problem: StreamCopyUnsupported) {
                throw DashAbort(joinFailure(problem.message))
            } catch (problem: EOFException) {
                throw DashAbort(joinFailure(problem.message))
            }
            val durationMillis = if (track.timescale > 0) {
                track.mediaDuration * 1_000 / track.timescale
            } else {
                0L
            }
            val summary = "${LivePieces.clock(durationMillis)}" +
                (plan.expectedDurationMillis?.let { " of ${LivePieces.clock(it)}" } ?: "") +
                " · ${join.pieces}/${chunks.size} pieces"
            plan.expectedDurationMillis?.let { expected ->
                // A stream not yet whole on YouTube's side: some pieces are not served yet.
                val averagePiece = durationMillis / join.pieces
                if (durationMillis + 2 * averagePiece + 1_000 < expected) {
                    throw DashAbort(
                        DownloadFailure(
                            reason = DownloadFailureReason.LIVE_NOT_READY,
                            stage = VERIFY,
                            detail = DownloadFailureDetails.sanitize("live $summary"),
                        ),
                    )
                }
            }
            val totalBytes = join.size
            // Every call below is on a file, so whatever fails is storage, at the step it reached.
            var stage = OPEN_FILE
            try {
                destination.prepare(totalBytes)
                val output = destination.open()
                stage = WRITE_FILE
                output.use {
                    val buffer = ByteArray(policy.bufferBytes)
                    var position = 0L
                    while (position < totalBytes) {
                        currentCoroutineContext().ensureActive()
                        val length = minOf(buffer.size.toLong(), totalBytes - position).toInt()
                        join.readFully(ByteBuffer.wrap(buffer, 0, length), position)
                        output.write(position, buffer, 0, length)
                        position += length
                    }
                    output.sync()
                }
                stage = VERIFY
                if (destination.temporaryLength() != totalBytes) {
                    throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                }
                stage = PUBLISH
                destination.commit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (abort: DashAbort) {
                throw abort
            } catch (error: Exception) {
                if (!error.isStorageError()) throw error
                throw DashAbort(error.toStorageFailure(stage))
            }
            log("DASH live ${plan.trackType}: $summary · $totalBytes bytes")
            return totalBytes
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
        proof: String? = null,
    ): DashDownloadManifestParser.Result.Parsed {
        val equalFingerprint = sha256(
            "whole-file|${plan.representationId}|${plan.trackType}|$totalBytes|$chunkBytes" +
                proof.orEmpty(),
        )
        val first = if (saved?.manifestFingerprint == equalFingerprint) chunkBytes else firstBytes
        val fingerprint = if (first == chunkBytes) {
            equalFingerprint
        } else {
            sha256(
                "whole-file-v2|${plan.representationId}|${plan.trackType}|$totalBytes|" +
                    "$first|$chunkBytes" + proof.orEmpty(),
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
        credentialOrigin: HttpUrl = fileUrl,
    ): OpenedRange {
        var lastReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return openFirstRangeOnce(plan, fileUrl, firstBytes, credentialOrigin)
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
        credentialOrigin: HttpUrl,
    ): OpenedRange {
        val execution = try {
            http.execute(
                credentialOrigin = credentialOrigin,
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
    private suspend fun probeLength(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
        credentialOrigin: HttpUrl = fileUrl,
    ): Long {
        var lastReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return probeLengthOnce(plan, fileUrl, credentialOrigin)
            } catch (retry: RetryableDashFailure) {
                lastReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw DashAbort(retry.failure())
                delay(backoffMillis(attempt))
            }
        }
        throw DashAbort(lastReason, stage = CONNECT)
    }

    private suspend fun probeLengthOnce(
        plan: DashDownloadPlan,
        fileUrl: HttpUrl,
        credentialOrigin: HttpUrl,
    ): Long {
        val execution = try {
            http.execute(
                credentialOrigin = credentialOrigin,
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
     * answer that is already open, for the first attempt. With [refusals] (F8: a whole file on
     * YouTube's media hosts) the chunk is asked in the file's turn and a 403 is asked again
     * while the file may wait; then it fails with the refusals' Detail.
     */
    private suspend fun downloadChunkWithRetry(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        workspace: File,
        chunk: DashDownloadChunk,
        tracker: DashCheckpointTracker,
        start: StartTimelineRecorder,
        opened: Response? = null,
        livePieces: Int? = null,
        refusals: MediaHostRefusals? = null,
    ): Long {
        var response = opened
        val attempts = if (livePieces != null) policy.livePieceAttempts else policy.maxAttempts
        var attempt = 0
        while (true) {
            val counter = RangeCounter(tracker, start)
            val once: suspend () -> Long = {
                downloadChunkOnce(
                    plan = plan,
                    credentialOrigin = credentialOrigin,
                    workspace = workspace,
                    chunk = chunk,
                    counter = counter,
                    opened = response.also { response = null },
                    livePieces = livePieces,
                )
            }
            try {
                return if (refusals != null) {
                    refusals.turn(chunk.byteRange?.offset ?: 0L, once)
                } else {
                    once()
                }
            } catch (retry: RetryableDashFailure) {
                counter.takeBack()
                if (attempt == attempts - 1) throw DashAbort(retry.failure())
                delay(retry.retryAfterMillis ?: backoffMillis(attempt))
                attempt += 1
            } catch (abort: DashAbort) {
                counter.takeBack()
                if (refusals == null || !refusals.isRefusal(abort)) throw abort
                if (!refusals.mayAskAgain()) {
                    throw DashAbort(abort.failure.copy(detail = refusals.describe()))
                }
            } catch (error: Throwable) {
                counter.takeBack()
                throw error
            }
        }
    }

    private suspend fun downloadChunkOnce(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        workspace: File,
        chunk: DashDownloadChunk,
        counter: RangeCounter,
        opened: Response?,
        livePieces: Int? = null,
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
        try {
            val written = if (livePieces != null) {
                opened?.close()
                receivePiece(plan, credentialOrigin, chunk, livePieces, temporary, counter)
            } else {
                receive(
                    plan = plan,
                    credentialOrigin = credentialOrigin,
                    url = chunk.url,
                    range = chunk.byteRange,
                    temporary = temporary,
                    counter = counter,
                    opened = opened,
                )
            }
            if (!temporary.renameTo(ready)) {
                throw IOException("Cannot finalize temporary DASH chunk")
            }
            if (ready.length() != written) {
                throw DashAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            return written
        } finally {
            if (temporary.exists()) runCatching { temporary.delete() }
        }
    }

    /**
     * Asks for [range] ([opened] when the first range is already open) and writes the answer,
     * the whole range, to [temporary]; returns the bytes written.
     */
    private suspend fun receive(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        url: HttpUrl,
        range: ResolvedByteRange?,
        temporary: File,
        counter: RangeCounter,
        opened: Response?,
    ): Long {
        val execution = opened?.let { response ->
            SecureDownloadHttp.Result.Completed(response, response.request.url)
        } ?: try {
            http.execute(
                credentialOrigin = credentialOrigin,
                initialUrl = url,
                context = plan.requestContext,
            ) {
                get()
                range?.let { header("Range", "bytes=${it.offset}-${it.endInclusive}") }
            }
        } catch (error: IOException) {
            throw RetryableDashFailure.network(CONNECT, error)
        }
        return when (execution) {
            is SecureDownloadHttp.Result.Failed ->
                throw DashAbort(execution.reason, stage = CONNECT)
            is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                val answered = validateChunkResponse(response, range)
                writeChunk(response, answered, temporary, counter)
            }
        }
    }

    /**
     * Checks the answer to [range] and returns how many bytes it carries: the whole range (null:
     * no range asked, the whole body). A mismatch names what was asked and what came (Detail).
     */
    private fun validateChunkResponse(
        response: Response,
        range: ResolvedByteRange?,
    ): Long? {
        if (response.code in 500..599) {
            throw RetryableDashFailure(
                DownloadFailureReason.SERVER_ERROR,
                response.code,
            )
        }
        var answered: Long? = null
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
                throw rangeMismatch(range, response)
            }
            answered = range.length
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
        if (range != null && advertised != null && advertised != answered) {
            throw rangeMismatch(range, response)
        }
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
        if (mimeType == "text/html" || mimeType == "application/xhtml+xml") {
            throw DashAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
        }
        return answered
    }

    private fun rangeMismatch(asked: ResolvedByteRange, response: Response): DashAbort =
        DashAbort(
            DownloadFailure(
                reason = DownloadFailureReason.INTEGRITY_MISMATCH,
                stage = CONNECT,
                detail = LiveOriginFile.describe(asked.offset, asked.endInclusive, response),
            ),
        )

    /**
     * Asks for one live piece, the chunk's address with its `sq`, and writes its body, the
     * whole piece, to [temporary]. A 5xx, 403 or 429 answer is tried again, a 429 after its
     * Retry-After (at most a minute); a failure's Detail names the piece ("live sq 12/2021 ·
     * 404 text/plain len 0").
     */
    private suspend fun receivePiece(
        plan: DashDownloadPlan,
        credentialOrigin: HttpUrl,
        chunk: DashDownloadChunk,
        pieces: Int,
        temporary: File,
        counter: RangeCounter,
    ): Long {
        val note = "live sq ${chunk.index}/${pieces - 1}"
        try {
            val execution = try {
                http.execute(
                    credentialOrigin = credentialOrigin,
                    initialUrl = chunk.url,
                    context = plan.requestContext,
                ) {
                    get()
                }
            } catch (error: IOException) {
                throw RetryableDashFailure.network(CONNECT, error)
            }
            return when (execution) {
                is SecureDownloadHttp.Result.Failed ->
                    throw DashAbort(execution.reason, stage = CONNECT)
                is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                    val expected = checkPieceResponse(response)
                    writeChunk(response, expected, temporary, counter)
                }
            }
        } catch (retry: RetryableDashFailure) {
            throw retry.noted(note)
        } catch (abort: DashAbort) {
            throw DashAbort(abort.failure.copy(detail = notedDetail(note, abort.failure.detail)))
        }
    }

    /**
     * Checks a live piece's answer: a 200, or a 206 that holds the whole piece. Returns the
     * piece's length when the answer names it.
     */
    private fun checkPieceResponse(response: Response): Long? {
        val code = response.code
        val advertised = response.header("Content-Length")?.trim()?.toLongOrNull()
            ?.takeIf { it >= 0 }
        val type = response.header("Content-Type")?.substringBefore(';')?.trim() ?: "no type"
        val said = "$code $type len ${advertised ?: "none"} ${pieceNumbers(response)}"
        if (code in 500..599 || code == 403) {
            throw RetryableDashFailure(code.toFailureReason(), code, detail = said)
        }
        if (code == 429) {
            val wait = response.header("Retry-After")?.trim()?.toLongOrNull()
                ?.coerceIn(0, MAX_RETRY_AFTER_SECONDS)
                ?.times(1_000)
            throw RetryableDashFailure(
                reason = DownloadFailureReason.HTTP_STATUS,
                httpStatusCode = code,
                detail = said,
                retryAfterMillis = wait,
            )
        }
        val whole = code == 200 || code == 206 && response.header("Content-Range")
            ?.trim()
            ?.let(WHOLE_RANGE::matchEntire)
            ?.let { match ->
                match.groupValues[1].toLongOrNull()?.plus(1) == match.groupValues[2].toLongOrNull()
            } == true
        if (!whole) {
            throw DashAbort(DownloadFailure(code.toFailureReason(), code, CONNECT, said))
        }
        if (
            response.header("Content-Encoding")
                ?.equals("identity", ignoreCase = true) == false
        ) {
            throw DashAbort(
                DownloadFailure(DownloadFailureReason.MALFORMED_RESPONSE, code, CONNECT, said),
            )
        }
        if (advertised != null && advertised > policy.maxChunkBytes) {
            throw DashAbort(
                DownloadFailure(DownloadFailureReason.UNSUPPORTED_SOURCE, code, CONNECT, said),
            )
        }
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
        if (mimeType == "text/html" || mimeType == "application/xhtml+xml") {
            throw DashAbort(
                DownloadFailure(DownloadFailureReason.UNSUPPORTED_SOURCE, code, CONNECT, said),
            )
        }
        return advertised
    }

    /** "sq 12/2021": the piece the live server sent and its newest, as its headers name them. */
    private fun pieceNumbers(response: Response): String =
        "sq ${response.header("X-Sequence-Num")?.trim() ?: "-"}/" +
            (response.header("X-Head-Seqnum")?.trim() ?: "-")

    /**
     * Appends [response]'s body to [destination]; it must be [expectedBytes] when known.
     */
    private suspend fun writeChunk(
        response: Response,
        expectedBytes: Long?,
        destination: File,
        counter: RangeCounter,
    ): Long {
        val body = response.body
            ?: throw DashAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
        val file = try {
            FileOutputStream(destination, true)
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
                expectedBytes?.let { expected ->
                    if (written != expected) {
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

        /** A live stream's first piece that is not served: YouTube is not ready yet (run 4f). */
        fun notReadyWhenMissing(): DashAbort =
            if (failure.reason in MISSING_FIRST_PIECE) {
                DashAbort(failure.copy(reason = DownloadFailureReason.LIVE_NOT_READY))
            } else {
                this
            }
    }

    /**
     * A source failure that another attempt may fix: a network error or a 5xx answer (for a live
     * piece also 403 and 429, after [retryAfterMillis] when the server names it).
     */
    private class RetryableDashFailure(
        val reason: DownloadFailureReason,
        val httpStatusCode: Int? = null,
        val stage: DownloadFailureStage = CONNECT,
        val detail: String? = null,
        val retryAfterMillis: Long? = null,
    ) : Exception() {
        fun failure(): DownloadFailure = DownloadFailure(reason, httpStatusCode, stage, detail)

        /** The same failure, its Detail led by [note] ("live sq 12/2021"). */
        fun noted(note: String): RetryableDashFailure = RetryableDashFailure(
            reason = reason,
            httpStatusCode = httpStatusCode,
            stage = stage,
            detail = notedDetail(note, detail),
            retryAfterMillis = retryAfterMillis,
        )

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

    /**
     * A whole file's ranges, with the first range's answer when it gave the length; [oneFile]:
     * a manifest's `SegmentBase` file (59), whose length is then known too.
     */
    private class WholeFileLayout(
        val parsed: DashDownloadManifestParser.Result.Parsed,
        val firstRange: OpenedRange? = null,
        val oneFile: Boolean = false,
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
        const val HTTP_FORBIDDEN = 403
        const val MAX_RETRY_AFTER_SECONDS = 60L
        val WHOLE_RANGE = Regex("""(?i)^bytes\s+0-(\d+)/(\d+)$""")
        val MISSING_FIRST_PIECE = setOf(
            DownloadFailureReason.NOT_FOUND,
            DownloadFailureReason.GONE,
            DownloadFailureReason.RANGE_NOT_SATISFIABLE,
            DownloadFailureReason.HTTP_STATUS,
            DownloadFailureReason.INTEGRITY_MISMATCH,
        )
        val NON_RESUMABLE_FAILURES = setOf(
            DownloadFailureReason.INVALID_URL,
            DownloadFailureReason.DRM_PROTECTED,
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            DownloadFailureReason.MALFORMED_RESPONSE,
            DownloadFailureReason.INTEGRITY_MISMATCH,
        )
    }
}

/** [detail] led by [note] ("live sq 12/2021 · 404 …"), as one clean Detail. */
private fun notedDetail(note: String, detail: String?): String? =
    DownloadFailureDetails.sanitize(if (detail.isNullOrBlank()) note else "$note · $detail")

private fun logStartLine(message: String) {
    runCatching { android.util.Log.i("YftDownloads", message) }
}
