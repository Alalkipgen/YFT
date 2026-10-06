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
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

interface HlsTransferRunner {
    suspend fun transfer(
        plan: HlsDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: HlsTransferCheckpoint? = null,
        onProgress: suspend (DownloadProgress) -> Unit = {},
        onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit = {},
    ): HlsTransferResult

    suspend fun discard(plan: HlsDownloadPlan)
}

/**
 * Downloads one selected non-DRM HLS VOD media playlist.
 *
 * Chunks are downloaded into deterministic app-private files, checkpointed only after a complete
 * fsync, then assembled in manifest order. The public destination remains unpublished until its
 * final length is verified.
 */
class HlsTransferEngine(
    client: OkHttpClient,
    private val workspaceRoot: File,
    private val policy: Policy = Policy(),
    private val clock: () -> Long = System::currentTimeMillis,
) : HlsTransferRunner {
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
        }
    }

    private val http = SecureDownloadHttp(
        client = client,
        maxRedirects = policy.maxRedirects,
        callTimeoutSeconds = policy.callTimeoutSeconds,
    )

    override suspend fun transfer(
        plan: HlsDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: HlsTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit,
    ): HlsTransferResult = withContext(Dispatchers.IO) {
        val emptyCheckpoint = HlsTransferCheckpoint(null, emptyList())
        val credentialOrigin = plan.playlistUrl.toSafeDownloadUrl()
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

        var tracker: HlsCheckpointTracker? = null
        try {
            val fetched = fetchManifest(plan, credentialOrigin)
            val parsed = when (
                val result = HlsDownloadManifestParser.parse(
                    manifest = fetched.body,
                    manifestUrl = fetched.finalUrl,
                    maxChunks = policy.maxChunks,
                )
            ) {
                HlsDownloadManifestParser.Result.DrmProtected ->
                    throw HlsAbort(DownloadFailureReason.DRM_PROTECTED, stage = READ_SOURCE)
                HlsDownloadManifestParser.Result.Unsupported ->
                    throw HlsAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE)
                HlsDownloadManifestParser.Result.TooManyChunks ->
                    throw HlsAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE)
                HlsDownloadManifestParser.Result.Malformed ->
                    throw HlsAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = READ_SOURCE)
                is HlsDownloadManifestParser.Result.Parsed -> result
            }
            val initial = reconcileCheckpoint(
                workspace = workspace,
                parsed = parsed,
                saved = resumeFrom,
            )
            val activeTracker = HlsCheckpointTracker(
                initial = initial,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
            tracker = activeTracker
            activeTracker.emit()

            parsed.chunks
                .filterNot { initial.chunks[it.index].completed }
                .chunked(policy.maxConcurrentChunks)
                .forEach { batch ->
                    coroutineScope {
                        batch.map { chunk ->
                            async {
                                val bytes = downloadChunkWithRetry(
                                    plan = plan,
                                    credentialOrigin = credentialOrigin,
                                    workspace = workspace,
                                    chunk = chunk,
                                )
                                activeTracker.markCompleted(chunk.index, bytes)
                            }
                        }
                            .awaitAll()
                    }
                }

            val completedCheckpoint = activeTracker.snapshot()
            if (completedCheckpoint.completedChunkCount != parsed.chunks.size) {
                throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            val bytesWritten = assemble(
                workspace = workspace,
                chunks = parsed.chunks,
                destination = destination,
            )
            if (bytesWritten != completedCheckpoint.downloadedBytes) {
                throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            cleanupWorkspace(workspace, strict = false)
            HlsTransferResult.Completed(bytesWritten, completedCheckpoint)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                runCatching { tracker?.emit() }
            }
            throw cancellation
        } catch (abort: HlsAbort) {
            if (abort.failure.reason in NON_RESUMABLE_FAILURES) {
                cleanupWorkspace(workspace, strict = false)
            }
            HlsTransferResult.Failure(
                failure = abort.failure,
                checkpoint = tracker?.snapshot()
                    ?: emptyCheckpoint,
            )
        } catch (error: IOException) {
            // Source errors are retried and end as HlsAborts; an I/O error here comes from the
            // chunk files of the task's workspace.
            HlsTransferResult.Failure(
                failure = error.toStorageFailure(WRITE_FILE),
                checkpoint = tracker?.snapshot()
                    ?: emptyCheckpoint,
            )
        }
    }

    override suspend fun discard(plan: HlsDownloadPlan) = withContext(Dispatchers.IO) {
        val root = workspaceRoot.canonicalFile
        val workspace = workspaceFor(plan.taskId).canonicalFile
        if (workspace.parentFile != root) throw IOException("Unsafe HLS workspace")
        cleanupWorkspace(workspace, strict = true)
    }

    private suspend fun fetchManifest(
        plan: HlsDownloadPlan,
        credentialOrigin: HttpUrl,
    ): FetchedManifest {
        var lastReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return fetchManifestOnce(plan, credentialOrigin)
            } catch (retry: RetryableHlsFailure) {
                lastReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw HlsAbort(retry.failure())
                delay(backoffMillis(attempt))
            }
        }
        throw HlsAbort(lastReason, stage = CONNECT)
    }

    private suspend fun fetchManifestOnce(
        plan: HlsDownloadPlan,
        credentialOrigin: HttpUrl,
    ): FetchedManifest {
        val execution = try {
            http.execute(
                credentialOrigin = credentialOrigin,
                initialUrl = credentialOrigin,
                context = plan.requestContext,
            ) {
                get()
                header("Accept", HLS_ACCEPT)
            }
        } catch (error: IOException) {
            throw RetryableHlsFailure.network(CONNECT, error)
        }
        return when (execution) {
            is SecureDownloadHttp.Result.Failed ->
                throw HlsAbort(execution.reason, stage = CONNECT)
            is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                if (response.code in 500..599) {
                    throw RetryableHlsFailure(
                        DownloadFailureReason.SERVER_ERROR,
                        response.code,
                    )
                }
                if (response.code != 200) {
                    throw HlsAbort(response.code.toFailureReason(), response.code, CONNECT)
                }
                if (
                    response.header("Content-Encoding")
                        ?.equals("identity", ignoreCase = true) == false
                ) {
                    throw HlsAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
                }
                val body = response.body
                    ?: throw HlsAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
                if (body.contentLength() > policy.maxManifestBytes) {
                    throw HlsAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
                }
                val bytes = try {
                    body.byteStream().readAtMost(policy.maxManifestBytes + 1)
                } catch (error: IOException) {
                    throw RetryableHlsFailure.network(READ_SOURCE, error)
                }
                if (bytes.size > policy.maxManifestBytes) {
                    throw HlsAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE)
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
        parsed: HlsDownloadManifestParser.Result.Parsed,
        saved: HlsTransferCheckpoint?,
    ): HlsTransferCheckpoint {
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
                    throw IOException("Cannot reset stale HLS chunk")
                }
                StreamChunkCheckpoint(
                    index = chunk.index,
                    downloadedBytes = 0,
                    completed = false,
                )
            }
        }
        return HlsTransferCheckpoint(
            manifestFingerprint = parsed.fingerprint,
            chunks = chunks,
        )
    }

    private suspend fun downloadChunkWithRetry(
        plan: HlsDownloadPlan,
        credentialOrigin: HttpUrl,
        workspace: File,
        chunk: HlsDownloadChunk,
    ): Long {
        var lastRetryReason = DownloadFailureReason.NETWORK
        repeat(policy.maxAttempts) { attempt ->
            try {
                return downloadChunkOnce(
                    plan = plan,
                    credentialOrigin = credentialOrigin,
                    workspace = workspace,
                    chunk = chunk,
                )
            } catch (retry: RetryableHlsFailure) {
                lastRetryReason = retry.reason
                if (attempt == policy.maxAttempts - 1) throw HlsAbort(retry.failure())
                delay(backoffMillis(attempt))
            }
        }
        throw HlsAbort(lastRetryReason, stage = CONNECT)
    }

    private suspend fun downloadChunkOnce(
        plan: HlsDownloadPlan,
        credentialOrigin: HttpUrl,
        workspace: File,
        chunk: HlsDownloadChunk,
    ): Long {
        val temporary = downloadingFile(workspace, chunk.index)
        val ready = readyFile(workspace, chunk.index)
        if (temporary.exists() && !temporary.delete()) {
            throw IOException("Cannot reset temporary HLS chunk")
        }
        if (ready.exists() && !ready.delete()) {
            throw IOException("Cannot reset completed HLS chunk")
        }
        val execution = try {
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
            throw RetryableHlsFailure.network(CONNECT, error)
        }
        try {
            return when (execution) {
                is SecureDownloadHttp.Result.Failed ->
                    throw HlsAbort(execution.reason, stage = CONNECT)
                is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                    validateChunkResponse(response, chunk)
                    writeChunk(response, chunk, temporary)
                }
            }.also { bytes ->
                if (!temporary.renameTo(ready)) {
                    throw IOException("Cannot finalize temporary HLS chunk")
                }
                if (ready.length() != bytes) {
                    throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                }
            }
        } finally {
            if (temporary.exists()) runCatching { temporary.delete() }
        }
    }

    private fun validateChunkResponse(
        response: Response,
        chunk: HlsDownloadChunk,
    ) {
        if (response.code in 500..599) {
            throw RetryableHlsFailure(
                DownloadFailureReason.SERVER_ERROR,
                response.code,
            )
        }
        val range = chunk.byteRange
        if (range == null) {
            if (response.code != 200) {
                throw HlsAbort(response.code.toFailureReason(), response.code, CONNECT)
            }
        } else {
            if (response.code != 206) {
                throw HlsAbort(response.code.toFailureReason(), response.code, CONNECT)
            }
            val returned = response.header("Content-Range")
                ?.parseContentRange()
                ?: throw HlsAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
            if (returned.first != range.offset || returned.second != range.endInclusive) {
                throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = CONNECT)
            }
        }
        if (
            response.header("Content-Encoding")
                ?.equals("identity", ignoreCase = true) == false
        ) {
            throw HlsAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
        }
        val advertised = response.header("Content-Length")
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
        if (advertised != null && advertised > policy.maxChunkBytes) {
            throw HlsAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
        }
        if (range != null && advertised != null && advertised != range.length) {
            throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = CONNECT)
        }
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
        if (mimeType == "text/html" || mimeType == "application/xhtml+xml") {
            throw HlsAbort(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = CONNECT)
        }
    }

    private suspend fun writeChunk(
        response: Response,
        chunk: HlsDownloadChunk,
        destination: File,
    ): Long {
        val body = response.body
            ?: throw HlsAbort(DownloadFailureReason.MALFORMED_RESPONSE, stage = CONNECT)
        val file = try {
            FileOutputStream(destination)
        } catch (error: IOException) {
            throw HlsAbort(error.toStorageFailure(OPEN_FILE))
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
                        throw RetryableHlsFailure.network(READ_SOURCE, error)
                    }
                    if (read == -1) break
                    if (written > policy.maxChunkBytes - read) {
                        throw HlsAbort(
                            DownloadFailureReason.UNSUPPORTED_SOURCE,
                            stage = READ_SOURCE,
                        )
                    }
                    try {
                        output.write(buffer, 0, read)
                    } catch (error: IOException) {
                        throw HlsAbort(error.toStorageFailure(WRITE_FILE))
                    }
                    written += read
                }
                if (written == 0L) {
                    throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = READ_SOURCE)
                }
                chunk.byteRange?.let { range ->
                    if (written != range.length) {
                        throw HlsAbort(
                            DownloadFailureReason.INTEGRITY_MISMATCH,
                            stage = READ_SOURCE,
                        )
                    }
                }
                try {
                    output.fd.sync()
                } catch (error: IOException) {
                    throw HlsAbort(error.toStorageFailure(WRITE_FILE))
                }
                written
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (retry: RetryableHlsFailure) {
            throw retry
        } catch (abort: HlsAbort) {
            throw abort
        } catch (error: IOException) {
            throw HlsAbort(error.toStorageFailure(WRITE_FILE))
        }
    }

    private suspend fun assemble(
        workspace: File,
        chunks: List<HlsDownloadChunk>,
        destination: DownloadDestination,
    ): Long {
        val files = chunks.map { chunk ->
            readyFile(workspace, chunk.index)
                .takeIf(File::isFile)
                ?: throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
        }
        val totalBytes = files.fold(0L) { total, file ->
            val length = file.length()
            if (length <= 0 || total > Long.MAX_VALUE - length) {
                throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
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
                    throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                }
            }
            stage = VERIFY
            if (destination.temporaryLength() != totalBytes) {
                throw HlsAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            stage = PUBLISH
            destination.commit()
            return totalBytes
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (abort: HlsAbort) {
            throw abort
        } catch (error: Exception) {
            if (!error.isStorageError()) throw error
            throw HlsAbort(error.toStorageFailure(stage))
        }
    }

    private fun prepareWorkspace(plan: HlsDownloadPlan): File {
        val root = workspaceRoot.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) {
            throw IOException("Cannot create HLS workspace")
        }
        val workspace = workspaceFor(plan.taskId).canonicalFile
        if (workspace.parentFile != root) {
            throw IOException("Unsafe HLS workspace")
        }
        if (!workspace.isDirectory && !workspace.mkdirs()) {
            throw IOException("Cannot create HLS task workspace")
        }
        return workspace
    }

    private fun workspaceFor(taskId: String): File =
        File(workspaceRoot, DownloadWorkspaces.nameFor(DownloadWorkspaces.HLS_PREFIX, taskId))

    private fun clearChunkFiles(workspace: File) {
        workspace.listFiles().orEmpty().forEach { file ->
            if (file.isDirectory || !file.delete()) {
                throw IOException("Cannot reset HLS workspace")
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
        if (strict && failed) throw IOException("Cannot clean HLS workspace")
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
        checkpoint: HlsTransferCheckpoint,
    ): HlsTransferResult.Failure = HlsTransferResult.Failure(failure, checkpoint)

    private class HlsAbort(val failure: DownloadFailure) : Exception() {
        constructor(
            reason: DownloadFailureReason,
            httpStatusCode: Int? = null,
            stage: DownloadFailureStage? = null,
        ) : this(DownloadFailure(reason, httpStatusCode, stage))
    }

    /** A source failure that another attempt may fix: a network error or a 5xx answer. */
    private class RetryableHlsFailure(
        val reason: DownloadFailureReason,
        val httpStatusCode: Int? = null,
        val stage: DownloadFailureStage = CONNECT,
        val detail: String? = null,
    ) : Exception() {
        fun failure(): DownloadFailure = DownloadFailure(reason, httpStatusCode, stage, detail)

        companion object {
            fun network(stage: DownloadFailureStage, error: IOException): RetryableHlsFailure =
                RetryableHlsFailure(
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

    private class HlsCheckpointTracker(
        initial: HlsTransferCheckpoint,
        private val onProgress: suspend (DownloadProgress) -> Unit,
        private val onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit,
    ) {
        private val gate = Mutex()
        private var checkpoint = initial

        suspend fun markCompleted(index: Int, downloadedBytes: Long) = gate.withLock {
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
            emitLocked()
        }

        suspend fun emit() = gate.withLock { emitLocked() }

        suspend fun snapshot(): HlsTransferCheckpoint = gate.withLock { checkpoint }

        private suspend fun emitLocked() {
            onProgress(
                DownloadProgress(
                    downloadedBytes = checkpoint.downloadedBytes,
                    totalBytes = null,
                    activeSegmentCount = checkpoint.chunks.count { !it.completed },
                ),
            )
            onCheckpoint(checkpoint)
        }
    }

    private companion object {
        const val CHUNK_INDEX_DIGITS = 5
        const val HLS_ACCEPT =
            "application/vnd.apple.mpegurl, application/x-mpegurl, */*;q=0.1"
        val CONTENT_RANGE = Regex("""(?i)^bytes\s+(\d+)-(\d+)/(?:\d+|\*)$""")
        val NON_RESUMABLE_FAILURES = setOf(
            DownloadFailureReason.INVALID_URL,
            DownloadFailureReason.DRM_PROTECTED,
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            DownloadFailureReason.MALFORMED_RESPONSE,
            DownloadFailureReason.INTEGRITY_MISMATCH,
        )
    }
}
