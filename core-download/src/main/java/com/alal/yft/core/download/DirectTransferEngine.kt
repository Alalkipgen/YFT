package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.RemoteFileMetadata
import java.io.IOException
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
import okhttp3.OkHttpClient
import okhttp3.Response

interface DirectTransferRunner {
    suspend fun transfer(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        destination: DownloadDestination,
        resumeFrom: DirectTransferCheckpoint? = null,
        onProgress: suspend (DownloadProgress) -> Unit = {},
        onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit = {},
    ): DirectTransferResult
}

/** Streams direct media into temporary seekable storage and publishes only verified output. */
class DirectTransferEngine(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
    private val clock: () -> Long = System::currentTimeMillis,
) : DirectTransferRunner {
    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 60,
        val maxAttempts: Int = 5,
        val initialRetryDelayMillis: Long = 500,
        val bufferBytes: Int = 64 * 1_024,
        val checkpointIntervalBytes: Long = 256 * 1_024,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
            require(maxAttempts in 1..5)
            require(initialRetryDelayMillis >= 0)
            require(bufferBytes in 1_024..1024 * 1_024)
            require(checkpointIntervalBytes > 0)
        }
    }

    private val http = SecureDownloadHttp(
        client = client,
        maxRedirects = policy.maxRedirects,
        callTimeoutSeconds = policy.callTimeoutSeconds,
    )

    override suspend fun transfer(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        destination: DownloadDestination,
        resumeFrom: DirectTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
    ): DirectTransferResult = withContext(Dispatchers.IO) {
        val emptyCheckpoint = metadata.checkpoint(emptyList())
        val credentialOrigin = plan.sourceUrl.toSafeDownloadUrl()
            ?: return@withContext DirectTransferResult.Failure(
                DownloadFailure(DownloadFailureReason.INVALID_URL),
                emptyCheckpoint,
            )
        val transferUrl = metadata.finalUrl.toSafeDownloadUrl()
            ?: return@withContext DirectTransferResult.Failure(
                DownloadFailure(DownloadFailureReason.INVALID_URL),
                emptyCheckpoint,
            )
        if (plan.expiresAtEpochMs?.let { it <= clock() } == true) {
            return@withContext DirectTransferResult.Failure(
                DownloadFailure(DownloadFailureReason.EXPIRED_URL),
                emptyCheckpoint,
            )
        }
        if (
            plan.expectedBytes != null &&
            metadata.totalBytes != null &&
            plan.expectedBytes != metadata.totalBytes
        ) {
            return@withContext DirectTransferResult.Failure(
                DownloadFailure(DownloadFailureReason.INTEGRITY_MISMATCH),
                emptyCheckpoint,
            )
        }

        val probedTotalBytes = metadata.totalBytes
        val canUseRanges = metadata.supportsByteRanges &&
            probedTotalBytes != null &&
            probedTotalBytes > 0
        val plannedSegments = SegmentPlanner.plan(
            totalBytes = metadata.totalBytes,
            supportsByteRanges = canUseRanges,
            preferredSegmentCount = plan.preferredSegmentCount,
        )
        val existingLength = runCatching(destination::temporaryLength)
            .getOrElse {
                return@withContext DirectTransferResult.Failure(
                    DownloadFailure(DownloadFailureReason.STORAGE_UNAVAILABLE),
                    emptyCheckpoint,
                )
            }
        val initialSegments = reconcileCheckpoint(
            planned = plannedSegments,
            saved = resumeFrom,
            metadata = metadata,
            existingLength = existingLength,
            resumable = canUseRanges,
        )
        val tracker = ProgressTracker(
            metadata = metadata,
            initialSegments = initialSegments,
            checkpointIntervalBytes = policy.checkpointIntervalBytes,
            onProgress = onProgress,
            onCheckpoint = onCheckpoint,
        )

        try {
            destination.prepare(metadata.totalBytes)
            if (initialSegments != plannedSegments) {
                tracker.forceCheckpoint()
            }

            if (canUseRanges) {
                try {
                    transferRanges(
                        plan = plan,
                        metadata = metadata,
                        credentialOrigin = credentialOrigin,
                        transferUrl = transferUrl,
                        destination = destination,
                        tracker = tracker,
                    )
                } catch (_: RangeIgnoredException) {
                    val fallback = SegmentPlanner.plan(
                        totalBytes = metadata.totalBytes,
                        supportsByteRanges = false,
                        preferredSegmentCount = 1,
                    )
                    resetDestination(destination, metadata.totalBytes)
                    tracker.replace(fallback)
                    transferSegment(
                        plan = plan,
                        metadata = metadata,
                        credentialOrigin = credentialOrigin,
                        transferUrl = transferUrl,
                        destination = destination,
                        tracker = tracker,
                        initialSegment = fallback.single(),
                        ranged = false,
                    )
                }
            } else if (plannedSegments.isNotEmpty()) {
                resetDestination(destination, metadata.totalBytes)
                tracker.replace(plannedSegments.map { it.copy(downloadedBytes = 0) })
                transferSegment(
                    plan = plan,
                    metadata = metadata,
                    credentialOrigin = credentialOrigin,
                    transferUrl = transferUrl,
                    destination = destination,
                    tracker = tracker,
                    initialSegment = tracker.snapshot().segments.single(),
                    ranged = false,
                )
            }

            tracker.forceCheckpoint()
            val checkpoint = tracker.snapshot()
            val bytesWritten = checkpoint.downloadedBytes
            val expectedBytes = metadata.totalBytes
            if (expectedBytes != null && bytesWritten != expectedBytes) {
                throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
            val temporaryLength = destination.temporaryLength()
            val verifiedLength = expectedBytes ?: bytesWritten
            if (temporaryLength != verifiedLength) {
                throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
            destination.commit()
            DirectTransferResult.Completed(
                bytesWritten = bytesWritten,
                checkpoint = checkpoint.copy(totalBytes = verifiedLength),
            )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                runCatching { tracker.forceCheckpoint() }
            }
            throw cancellation
        } catch (failure: TransferAbort) {
            DirectTransferResult.Failure(
                failure = DownloadFailure(failure.reason, failure.httpStatusCode),
                checkpoint = tracker.snapshot(),
            )
        } catch (failure: IOException) {
            DirectTransferResult.Failure(
                failure = DownloadFailure(failure.storageFailureReason()),
                checkpoint = tracker.snapshot(),
            )
        } catch (_: IllegalStateException) {
            DirectTransferResult.Failure(
                failure = DownloadFailure(DownloadFailureReason.STORAGE_UNAVAILABLE),
                checkpoint = tracker.snapshot(),
            )
        }
    }

    private suspend fun transferRanges(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        credentialOrigin: okhttp3.HttpUrl,
        transferUrl: okhttp3.HttpUrl,
        destination: DownloadDestination,
        tracker: ProgressTracker,
    ) = coroutineScope {
        tracker.snapshot().segments
            .filter { it.downloadedBytes != it.lengthBytes }
            .map { segment ->
                async {
                    transferSegment(
                        plan = plan,
                        metadata = metadata,
                        credentialOrigin = credentialOrigin,
                        transferUrl = transferUrl,
                        destination = destination,
                        tracker = tracker,
                        initialSegment = segment,
                        ranged = true,
                    )
                }
            }
            .awaitAll()
    }

    private suspend fun transferSegment(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        credentialOrigin: okhttp3.HttpUrl,
        transferUrl: okhttp3.HttpUrl,
        destination: DownloadDestination,
        tracker: ProgressTracker,
        initialSegment: DownloadSegment,
        ranged: Boolean,
    ) {
        var downloaded = initialSegment.downloadedBytes
        var lastRetryFailure: RetryableTransferException? = null
        repeat(policy.maxAttempts) { attempt ->
            currentCoroutineContext().ensureActive()
            if (initialSegment.lengthBytes == downloaded) return
            if (!ranged && downloaded > 0) {
                resetDestination(destination, metadata.totalBytes)
                downloaded = 0
                tracker.update(initialSegment.index, downloaded, forceCheckpoint = true)
            }
            try {
                transferAttempt(
                    plan = plan,
                    metadata = metadata,
                    credentialOrigin = credentialOrigin,
                    transferUrl = transferUrl,
                    destination = destination,
                    tracker = tracker,
                    segment = initialSegment,
                    alreadyDownloaded = downloaded,
                    ranged = ranged,
                )
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (rangeIgnored: RangeIgnoredException) {
                throw rangeIgnored
            } catch (failure: RetryableTransferException) {
                lastRetryFailure = failure
                downloaded = tracker.downloadedFor(initialSegment.index)
                if (attempt == policy.maxAttempts - 1) {
                    throw TransferAbort(failure.reason, failure.httpStatusCode)
                }
                delay(backoffMillis(attempt))
            } catch (_: IOException) {
                downloaded = tracker.downloadedFor(initialSegment.index)
                if (attempt == policy.maxAttempts - 1) {
                    throw TransferAbort(DownloadFailureReason.NETWORK)
                }
                delay(backoffMillis(attempt))
            }
        }
        throw TransferAbort(
            lastRetryFailure?.reason ?: DownloadFailureReason.NETWORK,
            lastRetryFailure?.httpStatusCode,
        )
    }

    private suspend fun transferAttempt(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        credentialOrigin: okhttp3.HttpUrl,
        transferUrl: okhttp3.HttpUrl,
        destination: DownloadDestination,
        tracker: ProgressTracker,
        segment: DownloadSegment,
        alreadyDownloaded: Long,
        ranged: Boolean,
    ) {
        val startByte = segment.startByte + alreadyDownloaded
        val endByte = segment.endByteInclusive
        val execution = http.execute(
            credentialOrigin = credentialOrigin,
            initialUrl = transferUrl,
            context = plan.requestContext,
        ) {
            get()
            if (ranged) {
                header("Range", "bytes=$startByte-$endByte")
                (metadata.entityTag ?: metadata.lastModified)
                    ?.let { header("If-Range", it) }
            }
        }
        when (execution) {
            is SecureDownloadHttp.Result.Failed -> throw TransferAbort(execution.reason)
            is SecureDownloadHttp.Result.Completed -> execution.response.use { response ->
                validateResponse(
                    response = response,
                    metadata = metadata,
                    requestedStart = startByte,
                    requestedEnd = endByte,
                    ranged = ranged,
                )
                val body = response.body
                    ?: throw TransferAbort(DownloadFailureReason.MALFORMED_RESPONSE)
                val expectedRemaining = segment.lengthBytes?.minus(alreadyDownloaded)
                val advertised = response.header("Content-Length")
                    ?.toLongOrNull()
                    ?.takeIf { it >= 0 }
                if (
                    expectedRemaining != null &&
                    advertised != null &&
                    advertised != expectedRemaining
                ) {
                    throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
                }

                val openedOutput = try {
                    destination.open()
                } catch (error: IOException) {
                    throw TransferAbort(error.storageFailureReason())
                }
                openedOutput.use { output ->
                    var downloaded = alreadyDownloaded
                    try {
                        body.byteStream().use { input ->
                            val buffer = ByteArray(policy.bufferBytes)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                val remaining = segment.lengthBytes?.minus(downloaded)
                                if (remaining != null && read.toLong() > remaining) {
                                    throw TransferAbort(
                                        DownloadFailureReason.INTEGRITY_MISMATCH,
                                    )
                                }
                                try {
                                    output.write(
                                        position = segment.startByte + downloaded,
                                        buffer = buffer,
                                        offset = 0,
                                        byteCount = read,
                                    )
                                } catch (error: IOException) {
                                    throw TransferAbort(error.storageFailureReason())
                                }
                                downloaded += read
                                tracker.update(segment.index, downloaded)
                            }
                        }
                        if (
                            segment.lengthBytes != null &&
                            downloaded != segment.lengthBytes
                        ) {
                            throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
                        }
                        try {
                            output.sync()
                        } catch (error: IOException) {
                            throw TransferAbort(error.storageFailureReason())
                        }
                        tracker.update(
                            index = segment.index,
                            downloadedBytes = downloaded,
                            forceCheckpoint = true,
                        )
                    } catch (cancellation: CancellationException) {
                        withContext(NonCancellable) {
                            runCatching { output.sync() }
                            tracker.update(
                                index = segment.index,
                                downloadedBytes = downloaded,
                                forceCheckpoint = true,
                            )
                        }
                        throw cancellation
                    } catch (failure: Exception) {
                        withContext(NonCancellable) {
                            runCatching { output.sync() }
                            tracker.update(
                                index = segment.index,
                                downloadedBytes = downloaded,
                                forceCheckpoint = true,
                            )
                        }
                        throw failure
                    }
                }
            }
        }
    }

    private fun validateResponse(
        response: Response,
        metadata: RemoteFileMetadata,
        requestedStart: Long,
        requestedEnd: Long?,
        ranged: Boolean,
    ) {
        if (ranged && response.code == 200) throw RangeIgnoredException()
        if (response.code in 500..599) {
            throw RetryableTransferException(
                reason = DownloadFailureReason.SERVER_ERROR,
                httpStatusCode = response.code,
            )
        }
        if (ranged) {
            if (response.code != 206 || requestedEnd == null || metadata.totalBytes == null) {
                throw TransferAbort(response.code.toFailureReason(), response.code)
            }
            val returned = response.header("Content-Range")
                ?.parseContentRange()
                ?: throw TransferAbort(DownloadFailureReason.MALFORMED_RESPONSE)
            if (
                returned.startByte != requestedStart ||
                returned.endByteInclusive != requestedEnd ||
                returned.totalBytes != metadata.totalBytes
            ) {
                throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
        } else if (response.code != 200) {
            throw TransferAbort(response.code.toFailureReason(), response.code)
        }
        if (
            response.header("Content-Encoding")
                ?.equals("identity", ignoreCase = true) == false
        ) {
            throw TransferAbort(DownloadFailureReason.MALFORMED_RESPONSE)
        }
        val contentType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
        if (contentType == "text/html" || contentType == "application/xhtml+xml") {
            throw TransferAbort(DownloadFailureReason.UNSUPPORTED_SOURCE)
        }
        metadata.entityTag?.let { expected ->
            response.header("ETag")
                ?.takeIf { it != expected }
                ?.let { throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH) }
        }
        metadata.lastModified?.let { expected ->
            if (metadata.entityTag == null) {
                response.header("Last-Modified")
                    ?.takeIf { it != expected }
                    ?.let { throw TransferAbort(DownloadFailureReason.INTEGRITY_MISMATCH) }
            }
        }
    }

    private suspend fun resetDestination(
        destination: DownloadDestination,
        expectedLength: Long?,
    ) {
        destination.open().use { output ->
            output.setLength(0)
            if (expectedLength != null) output.setLength(expectedLength)
            output.sync()
        }
    }

    private fun reconcileCheckpoint(
        planned: List<DownloadSegment>,
        saved: DirectTransferCheckpoint?,
        metadata: RemoteFileMetadata,
        existingLength: Long?,
        resumable: Boolean,
    ): List<DownloadSegment> {
        if (!resumable || saved == null || existingLength == null) return planned
        if (saved.entityTag == null && saved.lastModified == null) return planned
        if (
            saved.totalBytes != metadata.totalBytes ||
            saved.entityTag != metadata.entityTag ||
            saved.lastModified != metadata.lastModified ||
            saved.segments.size != planned.size
        ) {
            return planned
        }
        return planned.zip(saved.segments)
            .map { (expected, stored) ->
                if (
                    expected.index != stored.index ||
                    expected.startByte != stored.startByte ||
                    expected.endByteInclusive != stored.endByteInclusive ||
                    stored.downloadedBytes > expected.lengthBytes!! ||
                    stored.startByte > existingLength ||
                    stored.downloadedBytes > existingLength - stored.startByte
                ) {
                    return planned
                }
                expected.copy(downloadedBytes = stored.downloadedBytes)
            }
    }

    private fun RemoteFileMetadata.checkpoint(
        segments: List<DownloadSegment>,
    ): DirectTransferCheckpoint = DirectTransferCheckpoint(
        totalBytes = totalBytes,
        entityTag = entityTag,
        lastModified = lastModified,
        segments = segments,
    )

    private fun Int.toFailureReason(): DownloadFailureReason = when (this) {
        401 -> DownloadFailureReason.AUTHENTICATION_REQUIRED
        403 -> DownloadFailureReason.ACCESS_DENIED
        404 -> DownloadFailureReason.NOT_FOUND
        410 -> DownloadFailureReason.GONE
        416 -> DownloadFailureReason.RANGE_NOT_SATISFIABLE
        in 500..599 -> DownloadFailureReason.SERVER_ERROR
        else -> DownloadFailureReason.HTTP_STATUS
    }

    private fun String.parseContentRange(): ParsedContentRange? {
        val match = CONTENT_RANGE.matchEntire(trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (end < start || total <= end) return null
        return ParsedContentRange(start, end, total)
    }

    private fun IOException.storageFailureReason(): DownloadFailureReason {
        val text = message.orEmpty().lowercase(Locale.US)
        return if (
            "enospc" in text ||
            "no space left" in text ||
            "disk full" in text
        ) {
            DownloadFailureReason.INSUFFICIENT_STORAGE
        } else {
            DownloadFailureReason.STORAGE_UNAVAILABLE
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        if (policy.initialRetryDelayMillis == 0L) return 0
        val multiplier = 1L shl attempt.coerceAtMost(4)
        return policy.initialRetryDelayMillis * multiplier
    }

    private inner class ProgressTracker(
        private val metadata: RemoteFileMetadata,
        initialSegments: List<DownloadSegment>,
        private val checkpointIntervalBytes: Long,
        private val onProgress: suspend (DownloadProgress) -> Unit,
        private val onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
    ) {
        private val gate = Mutex()
        private var segments = initialSegments
        private var lastCheckpointBytes = initialSegments.sumOf { it.downloadedBytes }

        suspend fun update(
            index: Int,
            downloadedBytes: Long,
            forceCheckpoint: Boolean = false,
        ) = gate.withLock {
            segments = segments.map { segment ->
                if (segment.index == index) {
                    segment.copy(downloadedBytes = downloadedBytes)
                } else {
                    segment
                }
            }
            val checkpoint = metadata.checkpoint(segments)
            onProgress(
                DownloadProgress(
                    downloadedBytes = checkpoint.downloadedBytes,
                    totalBytes = metadata.totalBytes,
                    activeSegmentCount = segments.count {
                        val length = it.lengthBytes
                        length == null || it.downloadedBytes < length
                    },
                ),
            )
            if (
                forceCheckpoint ||
                checkpoint.downloadedBytes - lastCheckpointBytes >= checkpointIntervalBytes
            ) {
                onCheckpoint(checkpoint)
                lastCheckpointBytes = checkpoint.downloadedBytes
            }
        }

        suspend fun replace(replacement: List<DownloadSegment>) = gate.withLock {
            segments = replacement
            val checkpoint = metadata.checkpoint(segments)
            lastCheckpointBytes = checkpoint.downloadedBytes
            onProgress(
                DownloadProgress(
                    downloadedBytes = checkpoint.downloadedBytes,
                    totalBytes = metadata.totalBytes,
                    activeSegmentCount = replacement.size,
                ),
            )
            onCheckpoint(checkpoint)
        }

        suspend fun forceCheckpoint() = gate.withLock {
            val checkpoint = metadata.checkpoint(segments)
            onCheckpoint(checkpoint)
            lastCheckpointBytes = checkpoint.downloadedBytes
        }

        suspend fun downloadedFor(index: Int): Long = gate.withLock {
            segments.single { it.index == index }.downloadedBytes
        }

        suspend fun snapshot(): DirectTransferCheckpoint = gate.withLock {
            metadata.checkpoint(segments.toList())
        }
    }

    private data class ParsedContentRange(
        val startByte: Long,
        val endByteInclusive: Long,
        val totalBytes: Long,
    )

    private class TransferAbort(
        val reason: DownloadFailureReason,
        val httpStatusCode: Int? = null,
    ) : Exception()

    private class RetryableTransferException(
        val reason: DownloadFailureReason,
        val httpStatusCode: Int? = null,
    ) : Exception()

    private class RangeIgnoredException : Exception()

    private companion object {
        val CONTENT_RANGE = Regex("""(?i)^bytes\s+(\d+)-(\d+)/(\d+)$""")
    }
}
