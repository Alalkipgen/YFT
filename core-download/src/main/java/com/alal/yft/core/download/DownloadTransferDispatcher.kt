package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint

sealed interface QueueTransferResult {
    val checkpoint: TransferCheckpoint

    data class Completed(
        val bytesWritten: Long,
        override val checkpoint: TransferCheckpoint,
    ) : QueueTransferResult

    data class Failure(
        val failure: DownloadFailure,
        override val checkpoint: TransferCheckpoint,
    ) : QueueTransferResult
}

interface DownloadTransferDispatcher {
    suspend fun transfer(
        plan: DownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: TransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit = {},
        onCheckpoint: suspend (TransferCheckpoint) -> Unit = {},
    ): QueueTransferResult

    suspend fun discard(plan: DownloadPlan)
}

class DefaultDownloadTransferDispatcher(
    private val direct: DirectTransferRunner,
    private val hls: HlsTransferRunner,
    private val dash: DashTransferRunner,
    private val mux: AudioVideoMuxRunner,
) : DownloadTransferDispatcher {
    override suspend fun transfer(
        plan: DownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: TransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult = when (plan) {
        is DirectDownloadPlan -> transferDirect(
            plan,
            metadata,
            destination,
            resumeFrom as? DirectTransferCheckpoint,
            onProgress,
            onCheckpoint,
        )
        is HlsDownloadPlan -> when (
            val result = hls.transfer(
                plan = plan,
                destination = destination,
                resumeFrom = resumeFrom as? HlsTransferCheckpoint,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
        ) {
            is HlsTransferResult.Completed ->
                QueueTransferResult.Completed(result.bytesWritten, result.checkpoint)
            is HlsTransferResult.Failure ->
                QueueTransferResult.Failure(result.failure, result.checkpoint)
        }
        is DashDownloadPlan -> when (
            val result = dash.transfer(
                plan = plan,
                destination = destination,
                resumeFrom = resumeFrom as? DashTransferCheckpoint,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
        ) {
            is DashTransferResult.Completed ->
                QueueTransferResult.Completed(result.bytesWritten, result.checkpoint)
            is DashTransferResult.Failure ->
                QueueTransferResult.Failure(result.failure, result.checkpoint)
        }
        is AudioVideoMuxDownloadPlan -> when (
            val result = mux.transfer(
                plan = plan,
                destination = destination,
                resumeFrom = resumeFrom as? AudioVideoMuxCheckpoint,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
        ) {
            is AudioVideoMuxResult.Completed ->
                QueueTransferResult.Completed(result.bytesWritten, result.checkpoint)
            is AudioVideoMuxResult.Failure ->
                QueueTransferResult.Failure(result.failure, result.checkpoint)
        }
    }

    override suspend fun discard(plan: DownloadPlan) {
        when (plan) {
            is DirectDownloadPlan -> Unit
            is HlsDownloadPlan -> hls.discard(plan)
            is DashDownloadPlan -> dash.discard(plan)
            is AudioVideoMuxDownloadPlan -> mux.discard(plan)
        }
    }

    private suspend fun transferDirect(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: DirectTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult {
        val safeMetadata = metadata ?: return QueueTransferResult.Failure(
            failure = DownloadFailure(
                DownloadFailureReason.MALFORMED_RESPONSE,
                stage = DownloadFailureStage.CONNECT,
            ),
            checkpoint = resumeFrom ?: DirectTransferCheckpoint(
                totalBytes = plan.expectedBytes,
                entityTag = null,
                lastModified = null,
                segments = emptyList(),
            ),
        )
        return when (
            val result = direct.transfer(
                plan = plan,
                metadata = safeMetadata,
                destination = destination,
                resumeFrom = resumeFrom,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
        ) {
            is DirectTransferResult.Completed ->
                QueueTransferResult.Completed(result.bytesWritten, result.checkpoint)
            is DirectTransferResult.Failure ->
                QueueTransferResult.Failure(result.failure, result.checkpoint)
        }
    }
}

internal class DirectOnlyDownloadTransferDispatcher(
    private val direct: DirectTransferRunner,
) : DownloadTransferDispatcher {
    override suspend fun transfer(
        plan: DownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: TransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult {
        require(plan is DirectDownloadPlan)
        val safeMetadata = requireNotNull(metadata)
        return when (
            val result = direct.transfer(
                plan = plan,
                metadata = safeMetadata,
                destination = destination,
                resumeFrom = resumeFrom as? DirectTransferCheckpoint,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
        ) {
            is DirectTransferResult.Completed ->
                QueueTransferResult.Completed(result.bytesWritten, result.checkpoint)
            is DirectTransferResult.Failure ->
                QueueTransferResult.Failure(result.failure, result.checkpoint)
        }
    }

    override suspend fun discard(plan: DownloadPlan) = Unit
}