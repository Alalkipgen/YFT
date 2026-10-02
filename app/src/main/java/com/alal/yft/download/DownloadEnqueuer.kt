package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.policy.DownloadPolicyGate
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import java.io.IOException
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

        return when (request) {
            is DownloadRequest.Direct -> enqueueDirect(request)
            is DownloadRequest.Hls -> enqueueStream(request) { name, destination, spec ->
                queue.enqueue(request.plan.copy(suggestedFileName = name), destination, spec)
            }

            is DownloadRequest.Dash -> enqueueStream(request) { name, destination, spec ->
                queue.enqueue(request.plan.copy(suggestedFileName = name), destination, spec)
            }
        }
    }

    private suspend fun enqueueDirect(request: DownloadRequest.Direct): EnqueueResult {
        val metadata = when (val result = probe.probe(request.plan)) {
            is DirectProbeResult.Failure -> return EnqueueResult.Rejected(
                result.failure.reason,
                messageFor(result.failure.reason),
            )

            is DirectProbeResult.Success -> result.metadata
        }

        val prepared = prepare(request.fileName, request.mimeType ?: metadata.contentType)
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
        enqueue: suspend (String, DownloadDestination, DownloadDestinationSpec) -> String,
    ): EnqueueResult {
        val prepared = prepare(request.fileName, request.mimeType)
            ?: return storageUnavailable()

        policy.ensureApplied()
        val taskId = enqueue(prepared.fileName, prepared.destination, prepared.spec)
        serviceStarter.start()
        return EnqueueResult.Started(taskId, prepared.fileName)
    }

    private suspend fun prepare(fileName: String, mimeType: String?): PreparedDestination? {
        val target = location()
        return try {
            destinations.prepare(fileName, mimeType, target)
        } catch (_: IOException) {
            null
        }
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
}
