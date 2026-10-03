package com.alal.yft.feature.preview

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.alal.yft.core.media.player.MediaPlayerFactory
import com.alal.yft.core.media.player.PreviewSourceFactory
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.core.model.settings.pick
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.DownloadNetworkPolicy
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.download.policy.TransferNetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val resolver: VariantResolver,
    private val selectionStore: PreviewSelectionStore,
    private val mediaPlayerFactory: MediaPlayerFactory,
    private val previewSourceFactory: PreviewSourceFactory,
    private val downloadStarter: PreviewDownloadStarter,
    private val downloadPreferences: DownloadPreferencesRepository,
    private val network: NetworkStatusSource,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Empty)
    private var quality = QualityPreference.HIGHEST
    val uiState: StateFlow<PreviewUiState> = mutableUiState.asStateFlow()
    private val retryEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val sharedDownloadsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val defaultOptions = PreviewDownloadOptions(
        savesToSharedDownloads = sharedDownloadsSupported,
    )

    /** Wi-Fi only and the save location, shown next to the Download button. */
    val downloadOptions: StateFlow<PreviewDownloadOptions> = downloadPreferences.preferences
        .map { preferences ->
            PreviewDownloadOptions(
                wifiOnly = preferences.unmeteredOnly,
                savesToSharedDownloads = sharedDownloadsSupported &&
                    preferences.location == DownloadLocation.SHARED_DOWNLOADS,
            )
        }
        .catch { emit(defaultOptions) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(OPTIONS_STOP_TIMEOUT_MS),
            initialValue = defaultOptions,
        )

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

    /** The sheet's Wi-Fi only switch writes the global download preference. */
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            try {
                downloadPreferences.update { it.copy(unmeteredOnly = enabled) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The switch keeps showing the stored value, so a failed write is visible.
            }
        }
    }

    fun selectTab(tab: PreviewTab) {
        mutableUiState.update { current ->
            val ready = current as? PreviewUiState.Ready ?: return@update current
            val selected = ready.asset.preferredVariant(tab) ?: return@update current
            ready.copy(
                selectedTab = tab,
                selectedVariantId = selected.id,
                playbackError = null,
                downloadStatus = PreviewDownloadStatus.Idle,
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
                downloadStatus = PreviewDownloadStatus.Idle,
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

    /**
     * Queues the selected variant.
     *
     * Re-entrant taps are ignored while a request is in flight so one selection cannot create
     * duplicate queue entries, and every rejection surfaces its own reason instead of a generic
     * failure. On mobile data the user is asked first when they chose to be.
     */
    fun download() {
        val ready = mutableUiState.value as? PreviewUiState.Ready ?: return
        if (!ready.canDownload) return
        val asset = ready.asset
        val variant = ready.selectedVariant
        updateDownloadStatus(variant.id, PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch {
            val preferences = currentPreferences()
            val snapshot = network.snapshot.value
            if (DownloadNetworkPolicy.needsMeteredConfirmation(snapshot, preferences)) {
                updateDownloadStatus(variant.id, PreviewDownloadStatus.ConfirmMetered)
            } else {
                enqueue(asset, variant, preferences)
            }
        }
    }

    /** Starts the download the user confirmed for mobile data. */
    fun confirmMeteredDownload() {
        val ready = mutableUiState.value as? PreviewUiState.Ready ?: return
        if (ready.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        val variant = ready.selectedVariant
        updateDownloadStatus(variant.id, PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch { enqueue(ready.asset, variant, currentPreferences()) }
    }

    fun dismissMeteredDownload() {
        val ready = mutableUiState.value as? PreviewUiState.Ready ?: return
        if (ready.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        updateDownloadStatus(ready.selectedVariantId, PreviewDownloadStatus.Idle)
    }

    private suspend fun enqueue(
        asset: MediaAsset,
        variant: MediaVariant,
        preferences: DownloadPreferences,
    ) {
        val status = try {
            when (val result = downloadStarter.enqueue(asset, variant)) {
                is EnqueueResult.Started -> PreviewDownloadStatus.Queued(
                    fileName = result.fileName,
                    waitingForUnmetered = DownloadNetworkPolicy.stateFor(
                        network.snapshot.value,
                        preferences,
                    ) == TransferNetworkState.WAITING_FOR_UNMETERED,
                )

                is EnqueueResult.Rejected -> PreviewDownloadStatus.Rejected(result.message)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PreviewDownloadStatus.Rejected("The download could not be queued. Try again.")
        }
        updateDownloadStatus(variant.id, status)
    }

    private suspend fun currentPreferences(): DownloadPreferences = try {
        downloadPreferences.preferences.first()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DownloadPreferences()
    }

    private fun updateDownloadStatus(variantId: String, status: PreviewDownloadStatus) {
        mutableUiState.update { current ->
            val ready = current as? PreviewUiState.Ready ?: return@update current
            if (ready.selectedVariantId != variantId) return@update current
            ready.copy(downloadStatus = status)
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
        quality = currentPreferences().defaultQuality
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

    /** The variant the user's quality preference points at within one tab. */
    private fun MediaAsset.preferredVariant(tab: PreviewTab): MediaVariant? {
        val choices = variants.filter { it.belongsTo(tab) && it.isPreviewable }
        return when (tab) {
            PreviewTab.VIDEO -> quality.pick(
                choices,
                MediaVariant::height,
                MediaVariant::bitrateBitsPerSecond,
            )

            PreviewTab.AUDIO -> {
                val rated = choices.filter { it.bitrateBitsPerSecond != null }
                when {
                    rated.isEmpty() -> choices.firstOrNull()
                    quality == QualityPreference.LOWEST ->
                        rated.minByOrNull { it.bitrateBitsPerSecond ?: 0L }
                    else -> rated.maxByOrNull { it.bitrateBitsPerSecond ?: 0L }
                }
            }
        }
    }

    private fun MediaAsset.toReadyState(): PreviewUiState {
        val video = preferredVariant(PreviewTab.VIDEO)
        val audio = preferredVariant(PreviewTab.AUDIO)
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

    private companion object {
        const val OPTIONS_STOP_TIMEOUT_MS = 5_000L
    }
}
