package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.policy.DownloadPolicyGate
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** Staging destination plus the non-sensitive recovery description persisted with the task. */
data class PreparedDestination(
    val destination: DownloadDestination,
    val spec: DownloadDestinationSpec,
    /** The name the file is published under; differs from the request when it was taken. */
    val fileName: String,
)

/** Creates a safe staging destination that stays unpublished until the transfer is verified. */
interface DownloadDestinationProvider {
    @Throws(IOException::class)
    fun prepare(
        fileName: String,
        mimeType: String?,
        location: DownloadLocation = DownloadLocation.SHARED_DOWNLOADS,
    ): PreparedDestination
}

/** Narrow probe boundary so enqueue logic stays testable without network access. */
interface DirectMetadataProbe {
    suspend fun probe(plan: DirectDownloadPlan): DirectProbeResult
}

/** Starts the foreground transfer service once work is actually queued. */
interface DownloadServiceStarter {
    fun start()
}

/** Narrow boundary the preview layer uses so it never depends on transfer internals. */
interface PreviewDownloadStarter {
    suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult
}

sealed interface EnqueueResult {
    data class Started(val taskId: String, val fileName: String) : EnqueueResult

    data class Rejected(
        val reason: DownloadFailureReason,
        val message: String,
    ) : EnqueueResult
}

/**
 * Turns a preview selection into queued work.
 *
 * Direct sources are probed first so the queue records a real size and range capability instead of
 * guessing. Streaming sources skip the probe because their size is only known after parsing.
 * Every failure is reported explicitly and no destination is created for rejected work.
 *
 * The network policy is applied before anything is queued, so a download requested on mobile data
 * while the user allows only Wi-Fi waits instead of starting for a moment.
 */
class DownloadEnqueuer(
    private val queue: DownloadQueue,
    private val probe: DirectMetadataProbe,
    private val destinations: DownloadDestinationProvider,
    private val serviceStarter: DownloadServiceStarter,
    private val policy: DownloadPolicyGate = DownloadPolicyGate.None,
    private val location: suspend () -> DownloadLocation = { DownloadLocation.SHARED_DOWNLOADS },
    private val taskIds: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val storage: StorageSpace = StorageSpace.Unknown,
) : PreviewDownloadStarter {
    override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
        val taskId = taskIds()
        val planned = DownloadPlanFactory.create(
            asset = asset,
            variant = variant,
            taskId = taskId,
            nowEpochMs = clock(),
        )
        val request = when (planned) {
            is DownloadPlanResult.Rejected ->
                return EnqueueResult.Rejected(planned.reason, planned.message)

            is DownloadPlanResult.Ready -> planned.request
        }

        val target = location()
        // A streaming size is only trusted when the manifest stated it exactly.
        val exactStreamBytes = variant.sizeBytes
            ?.takeIf { variant.sizeAccuracy == MediaSizeAccuracy.EXACT }
        return when (request) {
            is DownloadRequest.Direct -> enqueueDirect(request, target)
            is DownloadRequest.Hls ->
                enqueueStream(request, target, exactStreamBytes) { name, destination, spec ->
                    queue.enqueue(request.plan.copy(suggestedFileName = name), destination, spec)
                }

            is DownloadRequest.Dash ->
                enqueueStream(request, target, exactStreamBytes) { name, destination, spec ->
                    queue.enqueue(request.plan.copy(suggestedFileName = name), destination, spec)
                }

            // Both tracks and the merged file sit on the phone together before the merge ends.
            is DownloadRequest.Mux ->
                enqueueStream(request, target, variant.sizeBytes?.times(2)) { name, dest, spec ->
                    queue.enqueue(request.plan.copy(suggestedFileName = name), dest, spec)
                }
        }
    }

    private suspend fun enqueueDirect(
        request: DownloadRequest.Direct,
        target: DownloadLocation,
    ): EnqueueResult {
        val metadata = when (val result = probe.probe(request.plan)) {
            is DirectProbeResult.Failure -> return EnqueueResult.Rejected(
                result.failure.reason,
                messageFor(result.failure.reason),
            )

            is DirectProbeResult.Success -> result.metadata
        }
        insufficientSpace(metadata.totalBytes, target)?.let { return it }

        val prepared = prepare(request.fileName, request.mimeType ?: metadata.contentType, target)
            ?: return storageUnavailable()

        policy.ensureApplied()
        queue.enqueue(
            plan = request.plan.copy(suggestedFileName = prepared.fileName),
            metadata = metadata,
            destination = prepared.destination,
            destinationSpec = prepared.spec,
        )
        serviceStarter.start()
        return EnqueueResult.Started(request.plan.taskId, prepared.fileName)
    }

    private suspend fun enqueueStream(
        request: DownloadRequest,
        target: DownloadLocation,
        exactBytes: Long?,
        enqueue: suspend (String, DownloadDestination, DownloadDestinationSpec) -> String,
    ): EnqueueResult {
        insufficientSpace(exactBytes, target)?.let { return it }
        val prepared = prepare(request.fileName, request.mimeType, target)
            ?: return storageUnavailable()

        policy.ensureApplied()
        val taskId = enqueue(prepared.fileName, prepared.destination, prepared.spec)
        serviceStarter.start()
        return EnqueueResult.Started(taskId, prepared.fileName)
    }

    private fun prepare(
        fileName: String,
        mimeType: String?,
        target: DownloadLocation,
    ): PreparedDestination? =
        try {
            destinations.prepare(fileName, mimeType, target)
        } catch (_: IOException) {
            null
        }

    /**
     * Rejects work of a known size that cannot fit, before any destination exists (R3). Unknown
     * sizes and unmeasurable volumes pass; the engines still report a full disk if a write fails.
     */
    private fun insufficientSpace(requiredBytes: Long?, target: DownloadLocation): EnqueueResult? {
        val required = requiredBytes?.takeIf { it > 0 } ?: return null
        val available = storage.availableBytes(target) ?: return null
        if (required + STORAGE_HEADROOM_BYTES <= available) return null
        return EnqueueResult.Rejected(
            DownloadFailureReason.INSUFFICIENT_STORAGE,
            "There is not enough free storage. This download needs ${formatSize(required)} " +
                "and ${formatSize(available.coerceAtLeast(0))} is free.",
        )
    }

    private fun storageUnavailable(): EnqueueResult = EnqueueResult.Rejected(
        DownloadFailureReason.STORAGE_UNAVAILABLE,
        messageFor(DownloadFailureReason.STORAGE_UNAVAILABLE),
    )

    private fun messageFor(reason: DownloadFailureReason): String = when (reason) {
        DownloadFailureReason.INVALID_URL -> "This media link is not valid."
        DownloadFailureReason.EXPIRED_URL ->
            "This media link already expired. Reload the page and try again."

        DownloadFailureReason.DRM_PROTECTED -> "This media is protected and cannot be downloaded."
        DownloadFailureReason.UNSUPPORTED_SOURCE -> "YFT cannot download this media source."
        DownloadFailureReason.INCOMPATIBLE_TRACKS ->
            "The selected audio and video tracks cannot be combined on this device."

        DownloadFailureReason.UNSAFE_REDIRECT -> "The source redirected to an unsafe location."
        DownloadFailureReason.TOO_MANY_REDIRECTS -> "The source redirected too many times."
        DownloadFailureReason.AUTHENTICATION_REQUIRED ->
            "This media requires signing in on the page first."

        DownloadFailureReason.ACCESS_DENIED -> "The server denied access to this media."
        DownloadFailureReason.NOT_FOUND -> "The media is no longer at this address."
        DownloadFailureReason.GONE -> "The media is no longer available."
        DownloadFailureReason.RANGE_NOT_SATISFIABLE ->
            "The server rejected the requested byte range."

        DownloadFailureReason.SERVER_ERROR -> "The server reported an error. Try again later."
        DownloadFailureReason.HTTP_STATUS -> "The server returned an unexpected response."
        DownloadFailureReason.MALFORMED_RESPONSE -> "The server response could not be read."
        DownloadFailureReason.NETWORK -> "Network error while contacting the server."
        DownloadFailureReason.STORAGE_UNAVAILABLE -> "Download storage is unavailable."
        DownloadFailureReason.INSUFFICIENT_STORAGE -> "There is not enough free storage."
        DownloadFailureReason.INTEGRITY_MISMATCH -> "The downloaded data failed verification."
    }

    companion object {
        /** Room kept free for the database, checkpoints and the rest of the system. */
        const val STORAGE_HEADROOM_BYTES: Long = 32L * 1_024 * 1_024

        internal fun formatSize(bytes: Long): String {
            if (bytes < 1_024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = -1
            while (value >= 1_024 && unit < units.lastIndex) {
                value /= 1_024
                unit += 1
            }
            return String.format(Locale.US, "%.1f %s", value, units[unit])
        }
    }
}
