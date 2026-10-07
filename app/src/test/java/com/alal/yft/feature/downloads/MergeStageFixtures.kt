package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.StreamChunkCheckpoint

/**
 * A merged download whose two tracks are in (1 GB), at [stage] with [stepDone] of [stepTotal]
 * (P27): for the card and notification tests.
 */
internal fun mergedTask(
    id: String,
    stage: AudioVideoMuxStage,
    stepDone: Long = 0,
    stepTotal: Long? = null,
    destinationKind: DownloadDestinationKind = DownloadDestinationKind.MEDIA_STORE,
    status: DownloadTaskStatus = DownloadTaskStatus.RUNNING,
): StoredDownloadTask {
    fun track(fingerprint: Char, bytes: Long) = DashTransferCheckpoint(
        manifestFingerprint = fingerprint.toString().repeat(64),
        chunks = listOf(
            StreamChunkCheckpoint(index = 0, downloadedBytes = bytes, completed = true),
        ),
    )
    val checkpoint = AudioVideoMuxCheckpoint(
        video = track('a', VIDEO_TRACK_BYTES),
        audio = track('b', AUDIO_TRACK_BYTES),
        videoReady = true,
        audioReady = true,
        stage = stage,
        stepDone = stepDone,
        stepTotal = stepTotal,
    )
    return StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = DownloadPlanType.AUDIO_VIDEO_MUX,
        totalBytes = VIDEO_TRACK_BYTES + AUDIO_TRACK_BYTES,
        downloadedBytes = VIDEO_TRACK_BYTES + AUDIO_TRACK_BYTES,
        mimeType = "video/mp4",
        destinationKind = destinationKind,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        checkpoint = checkpoint,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )
}

private const val VIDEO_TRACK_BYTES = 900_000_000L
private const val AUDIO_TRACK_BYTES = 100_000_000L
