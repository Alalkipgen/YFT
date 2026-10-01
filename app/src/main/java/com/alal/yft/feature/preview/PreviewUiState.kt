package com.alal.yft.feature.preview

import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure

enum class PreviewTab {
    VIDEO,
    AUDIO,
}

sealed interface PreviewUiState {
    data object Empty : PreviewUiState
    data object Loading : PreviewUiState

    data class Ready(
        val asset: MediaAsset,
        val selectedTab: PreviewTab,
        val selectedVariantId: String,
        val playbackError: String? = null,
    ) : PreviewUiState {
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