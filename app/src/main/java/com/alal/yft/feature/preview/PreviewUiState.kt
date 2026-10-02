package com.alal.yft.feature.preview

import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure

enum class PreviewTab {
    VIDEO,
    AUDIO,
}

/**
 * Outcome of the preview download action.
 *
 * [Queued] only means the task reached the persisted queue; transfer progress lives on the
 * downloads screen so this state never claims a file is finished.
 */
sealed interface PreviewDownloadStatus {
    data object Idle : PreviewDownloadStatus
    data object Enqueuing : PreviewDownloadStatus

    /** The device is on mobile data and the user asked to confirm such downloads. */
    data object ConfirmMetered : PreviewDownloadStatus

    data class Queued(
        val fileName: String,
        /** The download waits because the user allows transfers on Wi-Fi only. */
        val waitingForUnmetered: Boolean = false,
    ) : PreviewDownloadStatus

    data class Rejected(val message: String) : PreviewDownloadStatus
}

sealed interface PreviewUiState {
    data object Empty : PreviewUiState
    data object Loading : PreviewUiState

    data class Ready(
        val asset: MediaAsset,
        val selectedTab: PreviewTab,
        val selectedVariantId: String,
        val playbackError: String? = null,
        val downloadStatus: PreviewDownloadStatus = PreviewDownloadStatus.Idle,
    ) : PreviewUiState {
        val canDownload: Boolean
            get() = downloadStatus != PreviewDownloadStatus.Enqueuing &&
                downloadStatus != PreviewDownloadStatus.ConfirmMetered

        val visibleVariants: List<MediaVariant>
            get() = asset.variants.filter { it.belongsTo(selectedTab) }

        val selectedVariant: MediaVariant
            get() = asset.variants.first { it.id == selectedVariantId }

        fun hasPreviewableVariant(tab: PreviewTab): Boolean =
            asset.variants.any { it.belongsTo(tab) && it.isPreviewable }
    }

    data class Error(
        val reason: VariantResolutionFailure,
        val message: String,
        val retryable: Boolean,
    ) : PreviewUiState
}

internal fun MediaVariant.belongsTo(tab: PreviewTab): Boolean = when (tab) {
    PreviewTab.VIDEO ->
        trackType == MediaTrackType.VIDEO || trackType == MediaTrackType.AUDIO_VIDEO
    PreviewTab.AUDIO -> trackType == MediaTrackType.AUDIO
}