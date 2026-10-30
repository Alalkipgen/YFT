package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.SaveFolder
import com.alal.yft.download.policy.DownloadPolicyGate
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import com.alal.yft.feature.library.pickedQualityLabel
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Staging destination plus the non-sensitive recovery description persisted with the task. */
data class PreparedDestination(
    val destination: DownloadDestination,
    val spec: DownloadDestinationSpec,
    /** The name the file is published under; differs from the request when it was taken. */
    val fileName: String,
    /**
     * v1.0.2 item 28: the chosen folder could not be used (deleted, access gone, card removed),
     * so the file goes to the default place instead; its row says so.
     */
    val folderUnavailable: Boolean = false,
)

/** Creates a safe staging destination that stays unpublished until the transfer is verified. */
interface DownloadDestinationProvider {
    /**
     * v1.0.2 item 28: [folder] is the folder picked for [DownloadLocation.CHOSEN_FOLDER]; the
     * other locations never use it.
     */
    @Throws(IOException::class)
    fun prepare(
        fileName: String,
        mimeType: String?,
        location: DownloadLocation = DownloadLocation.SHARED_DOWNLOADS,
        folder: SaveFolder? = null,
    ): PreparedDestination
}

/** Narrow probe boundary so enqueue logic stays testable without network access. */
interface DirectMetadataProbe {
    /**
     * [askHead] false (v1.0.2): the row's file was already got by its adapter's one-byte check
     * (`MediaCandidate.fileChecked`, TikTok), so only the range GET asks it.
     */
    suspend fun probe(plan: DirectDownloadPlan, askHead: Boolean = true): DirectProbeResult
}

/** Starts the foreground transfer service once work is actually queued. */
interface DownloadServiceStarter {
    fun start()
}

/** Narrow boundary the preview layer uses so it never depends on transfer internals. */
interface PreviewDownloadStarter {
    suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult

    /**
     * Track E: like [enqueue], with where the file came from ([origin]: the page's file, the
     * file before its conversion, who looked it up), so a link that dies after the download
     * started can be renewed; null when the screen does not know.
     */
    suspend fun enqueue(
        asset: MediaAsset,
        variant: MediaVariant,
        origin: DownloadOrigin?,
    ): EnqueueResult = enqueue(asset, variant)
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
    /** Track E: each queued download's source, for a fresh link (memory only). */
    private val sources: DownloadSources? = null,
    /** v1.0.2 item 28: the folder picked for [DownloadLocation.CHOSEN_FOLDER], if any. */
    private val folder: suspend () -> SaveFolder? = { null },
    /** Where a chosen folder's provider is asked, away from the main thread. */
    private val folderDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PreviewDownloadStarter {
    override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult =
        enqueue(asset, variant, origin = null)

    override suspend fun enqueue(
        asset: MediaAsset,
        variant: MediaVariant,
        origin: DownloadOrigin?,
    ): EnqueueResult {
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
        // v1.0.2 item 28: the chosen folder is read only when it is the target.
        val targetFolder = if (target == DownloadLocation.CHOSEN_FOLDER) folder() else null
        // A streaming size is only trusted when the manifest stated it exactly.
        val exactStreamBytes = variant.sizeBytes
            ?.takeIf { variant.sizeAccuracy == MediaSizeAccuracy.EXACT }
        // Track E: the source is remembered just before the queue gets the task, so no failure
        // of its first run comes before it; a task the queue never got leaves none.
        val remember = Remember(taskId, origin, asset, variant)
        return when (request) {
            is DownloadRequest.Direct -> enqueueDirect(
                request = request,
                target = target,
                targetFolder = targetFolder,
                remember = remember,
                // v1.0.2: a file TikTok's adapter already got skips the HEAD (its host may 404).
                askHead = origin?.candidate?.fileChecked != true,
            )
            is DownloadRequest.Hls ->
                enqueueStream(request, target, targetFolder, exactStreamBytes) { prepared ->
                    remember.around(prepared) {
                        queue.enqueue(
                            request.plan.copy(suggestedFileName = prepared.fileName),
                            prepared.destination,
                            prepared.spec,
                        )
                    }
                }

            is DownloadRequest.Dash ->
                enqueueStream(request, target, targetFolder, exactStreamBytes) { prepared ->
                    remember.around(prepared) {
                        queue.enqueue(
                            request.plan.copy(suggestedFileName = prepared.fileName),
                            prepared.destination,
                            prepared.spec,
                        )
                    }
                }

            // Both tracks and the merged file sit on the phone together before the merge ends.
            is DownloadRequest.Mux -> enqueueStream(
                request,
                target,
                targetFolder,
                variant.sizeBytes?.times(2),
            ) { prepared ->
                remember.around(prepared) {
                    queue.enqueue(
                        request.plan.copy(suggestedFileName = prepared.fileName),
                        prepared.destination,
                        prepared.spec,
                    )
                }
            }
        }
    }

    /**
     * Track E: remembers a download's source under the name it is saved as, just before the
     * queue gets the task; forgets it again when queueing fails. v1.0.2 (item 50): also the
     * quality a video was picked as, for its row, wherever it was picked; (item 28) and that the
     * chosen folder could not be used, for the row's notice.
     */
    private inner class Remember(
        private val taskId: String,
        private val origin: DownloadOrigin?,
        private val asset: MediaAsset,
        private val variant: MediaVariant,
    ) {
        suspend fun <T> around(prepared: PreparedDestination, enqueue: suspend () -> T): T {
            val sources = sources ?: return enqueue()
            origin?.let { sources.remember(taskId, it, asset, variant, prepared.fileName) }
            pickedQuality(variant)?.let { sources.picked(taskId, it) }
            if (prepared.folderUnavailable) sources.folderUnavailable(taskId)
            return try {
                enqueue()
            } catch (error: Throwable) {
                sources.forget(taskId)
                throw error
            }
        }
    }

    /** A video's quality name ("1080p"); none for sound alone, an M4A or an MP3 of a video. */
    private fun pickedQuality(variant: MediaVariant): String? {
        val sound = variant.trackType == MediaTrackType.AUDIO ||
            variant.audioFromVideo || variant.mp3 != null
        return if (sound) null else pickedQualityLabel(variant.width, variant.height)
    }

    private suspend fun enqueueDirect(
        request: DownloadRequest.Direct,
        target: DownloadLocation,
        targetFolder: SaveFolder?,
        remember: Remember,
        askHead: Boolean,
    ): EnqueueResult {
        val metadata = when (val result = probe.probe(request.plan, askHead)) {
            is DirectProbeResult.Failure -> return EnqueueResult.Rejected(
                result.failure.reason,
                messageFor(result.failure.reason),
            )

            is DirectProbeResult.Success -> result.metadata
        }
        insufficientSpace(metadata.totalBytes, target)?.let { return it }

        val prepared = prepare(
            fileName = request.fileName,
            mimeType = request.mimeType ?: metadata.contentType,
            target = target,
            targetFolder = targetFolder,
        ) ?: return storageUnavailable()

        policy.ensureApplied()
        remember.around(prepared) {
            queue.enqueue(
                plan = request.plan.copy(suggestedFileName = prepared.fileName),
                metadata = metadata,
                destination = prepared.destination,
                destinationSpec = prepared.spec,
            )
        }
        serviceStarter.start()
        return EnqueueResult.Started(request.plan.taskId, prepared.fileName)
    }

    private suspend fun enqueueStream(
        request: DownloadRequest,
        target: DownloadLocation,
        targetFolder: SaveFolder?,
        exactBytes: Long?,
        enqueue: suspend (PreparedDestination) -> String,
    ): EnqueueResult {
        insufficientSpace(exactBytes, target)?.let { return it }
        val prepared = prepare(request.fileName, request.mimeType, target, targetFolder)
            ?: return storageUnavailable()

        policy.ensureApplied()
        val taskId = enqueue(prepared)
        serviceStarter.start()
        return EnqueueResult.Started(taskId, prepared.fileName)
    }

    private suspend fun prepare(
        fileName: String,
        mimeType: String?,
        target: DownloadLocation,
        targetFolder: SaveFolder?,
    ): PreparedDestination? =
        try {
            if (targetFolder == null) {
                destinations.prepare(fileName, mimeType, target)
            } else {
                // A folder's provider may be slow (a memory card, a cloud app).
                withContext(folderDispatcher) {
                    destinations.prepare(fileName, mimeType, target, targetFolder)
                }
            }
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
        DownloadFailureReason.LIVE_NOT_READY ->
            "YouTube is still preparing this live video. Try again later."
        DownloadFailureReason.YOUTUBE_REFUSED -> "YouTube refused this download. Try again later."
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
