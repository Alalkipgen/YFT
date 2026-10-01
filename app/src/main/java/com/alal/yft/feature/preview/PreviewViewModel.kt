package com.alal.yft.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.alal.yft.core.media.player.MediaPlayerFactory
import com.alal.yft.core.media.player.PreviewSourceFactory
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val resolver: VariantResolver,
    private val selectionStore: PreviewSelectionStore,
    private val mediaPlayerFactory: MediaPlayerFactory,
    private val previewSourceFactory: PreviewSourceFactory,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Empty)
    val uiState: StateFlow<PreviewUiState> = mutableUiState.asStateFlow()
    private val retryEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            merge(
                selectionStore.selection,
                retryEvents.map { selectionStore.selection.value },
            ).collectLatest(::resolve)
        }
    }

    fun retry() {
        retryEvents.tryEmit(Unit)
    }

    fun selectTab(tab: PreviewTab) {
        mutableUiState.update { current ->
            val ready = current as? PreviewUiState.Ready ?: return@update current
            val selected = ready.asset.variants.firstOrNull {
                it.belongsTo(tab) && it.isPreviewable
            } ?: return@update current
            ready.copy(
                selectedTab = tab,
                selectedVariantId = selected.id,
                playbackError = null,
            )
        }
    }

    fun selectVariant(variantId: String) {
        mutableUiState.update { current ->
            val ready = current as? PreviewUiState.Ready ?: return@update current
            val selected = ready.asset.variants.firstOrNull {
                it.id == variantId &&
                    it.belongsTo(ready.selectedTab) &&
                    it.isPreviewable
            } ?: return@update current
            ready.copy(
                selectedVariantId = selected.id,
                playbackError = null,
            )
        }
    }

    fun onPlaybackError(variantId: String) {
        mutableUiState.update { current ->
            val ready = current as? PreviewUiState.Ready ?: return@update current
            if (ready.selectedVariantId != variantId) return@update current
            ready.copy(
                playbackError = "This variant could not be played. Try another variant.",
            )
        }
    }

    fun createPlayer(): ExoPlayer = mediaPlayerFactory.create()

    fun createMediaSource(): MediaSource {
        val ready = mutableUiState.value as? PreviewUiState.Ready
            ?: throw IllegalStateException("No preview variant is selected")
        return previewSourceFactory.create(ready.selectedVariant)
    }

    private suspend fun resolve(candidate: MediaCandidate?) {
        if (candidate == null) {
            mutableUiState.value = PreviewUiState.Empty
            return
        }
        mutableUiState.value = PreviewUiState.Loading
        val result = try {
            resolver.resolve(candidate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK)
        }
        mutableUiState.value = when (result) {
            is VariantResolutionResult.Success -> result.asset.toReadyState()
            is VariantResolutionResult.Failure -> result.toErrorState()
        }
    }

    private fun MediaAsset.toReadyState(): PreviewUiState {
        val video = variants.firstOrNull {
            it.belongsTo(PreviewTab.VIDEO) && it.isPreviewable
        }
        val audio = variants.firstOrNull {
            it.belongsTo(PreviewTab.AUDIO) && it.isPreviewable
        }
        val initial = video ?: audio ?: return PreviewUiState.Error(
            reason = VariantResolutionFailure.UNSUPPORTED_CODEC,
            message = messageFor(VariantResolutionFailure.UNSUPPORTED_CODEC),
            retryable = false,
        )
        return PreviewUiState.Ready(
            asset = this,
            selectedTab = if (video != null) PreviewTab.VIDEO else PreviewTab.AUDIO,
            selectedVariantId = initial.id,
        )
    }

    private fun VariantResolutionResult.Failure.toErrorState(): PreviewUiState.Error =
        PreviewUiState.Error(
            reason = reason,
            message = messageFor(reason),
            retryable = reason == VariantResolutionFailure.NETWORK ||
                reason == VariantResolutionFailure.HTTP_STATUS,
        )

    private fun messageFor(reason: VariantResolutionFailure): String = when (reason) {
        VariantResolutionFailure.EXPIRED_URL ->
            "This media URL has expired. Return to the browser and detect it again."
        VariantResolutionFailure.DRM_PROTECTED ->
            "DRM-protected media cannot be previewed."
        VariantResolutionFailure.UNSUPPORTED_CODEC ->
            "No variant uses a codec supported by this preview."
        VariantResolutionFailure.MALFORMED_MANIFEST ->
            "The media manifest is malformed and cannot be previewed."
        VariantResolutionFailure.MANIFEST_TOO_LARGE ->
            "The media manifest is too large to inspect safely."
        VariantResolutionFailure.UNSAFE_REDIRECT ->
            "Preview stopped because the media redirected to an unsafe address."
        VariantResolutionFailure.TOO_MANY_REDIRECTS ->
            "Preview stopped after too many media redirects."
        VariantResolutionFailure.HTTP_STATUS ->
            "The media server rejected the preview request."
        VariantResolutionFailure.NETWORK ->
            "The media could not be reached. Check the connection and try again."
        VariantResolutionFailure.INVALID_URL ->
            "The detected media address is invalid or insecure."
        VariantResolutionFailure.NO_VARIANTS ->
            "No playable media variants were found."
    }
}