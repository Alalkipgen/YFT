package com.alal.yft.core.download

import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult

/**
 * P50: the track downloads of a merge. A track that is an HLS playlist
 * ([DashDownloadPlan.hlsPlaylist]: a video quality of a master playlist and its audio
 * rendition) is fetched by [hls], segment by segment like any HLS download, and resumes from
 * the same chunk checkpoint; every other track goes to [dash] as before.
 */
class PlaylistTrackTransferRunner(
    private val dash: DashTransferRunner,
    private val hls: HlsTransferRunner,
) : DashTransferRunner {
    override suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
    ): DashTransferResult = if (plan.hlsPlaylist) {
        playlist(plan, destination, resumeFrom, onProgress, onCheckpoint)
    } else {
        dash.transfer(plan, destination, resumeFrom, onProgress, onCheckpoint)
    }

    override suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        onStartTimeline: (DownloadStartTimeline) -> Unit,
    ): DashTransferResult = if (plan.hlsPlaylist) {
        playlist(plan, destination, resumeFrom, onProgress, onCheckpoint)
    } else {
        dash.transfer(plan, destination, resumeFrom, onProgress, onCheckpoint, onStartTimeline)
    }

    override suspend fun discard(plan: DashDownloadPlan) {
        if (plan.hlsPlaylist) hls.discard(playlistPlan(plan)) else dash.discard(plan)
    }

    private suspend fun playlist(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
    ): DashTransferResult = when (
        val result = hls.transfer(
            plan = playlistPlan(plan),
            destination = destination,
            resumeFrom = resumeFrom?.let { saved ->
                HlsTransferCheckpoint(saved.manifestFingerprint, saved.chunks)
            },
            onProgress = onProgress,
            onCheckpoint = { checkpoint -> onCheckpoint(checkpoint.asTrack()) },
        )
    ) {
        is HlsTransferResult.Completed ->
            DashTransferResult.Completed(result.bytesWritten, result.checkpoint.asTrack())
        is HlsTransferResult.Failure ->
            DashTransferResult.Failure(result.failure, result.checkpoint.asTrack())
    }

    private fun playlistPlan(plan: DashDownloadPlan) = HlsDownloadPlan(
        taskId = plan.taskId,
        playlistUrl = plan.manifestUrl,
        suggestedFileName = plan.suggestedFileName,
        requestContext = plan.requestContext,
        mimeType = plan.mimeType,
        expiresAtEpochMs = plan.expiresAtEpochMs,
    )

    private fun HlsTransferCheckpoint.asTrack() =
        DashTransferCheckpoint(manifestFingerprint, chunks)
}
