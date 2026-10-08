package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus

internal const val MB = 1_048_576L

/** 1.2 MB/s, as the tracker would measure it. */
internal const val SPEED_1_2_MB = 1_258_291L

/** A direct download for P34's background tests: 100 MB by default, saved to Download/YFT. */
internal fun backgroundTask(
    id: String,
    status: DownloadTaskStatus = DownloadTaskStatus.RUNNING,
    downloaded: Long = 0,
    total: Long? = 100 * MB,
    mimeType: String = "video/mp4",
    displayName: String = "$id.mp4",
    failureReason: DownloadFailureReason? = null,
): StoredDownloadTask = StoredDownloadTask(
    id = id,
    displayName = displayName,
    status = status,
    totalBytes = total,
    downloadedBytes = downloaded,
    mimeType = mimeType,
    destinationKind = DownloadDestinationKind.MEDIA_STORE,
    destinationUri = null,
    preferredSegmentCount = 1,
    requiresLinkRefresh = false,
    failureReason = failureReason,
    checkpoint = DirectTransferCheckpoint(
        totalBytes = total,
        entityTag = null,
        lastModified = null,
        segments = listOf(DownloadSegment(0, 0, total?.minus(1), downloaded)),
    ),
    createdAtEpochMs = 1,
    updatedAtEpochMs = 1,
)
