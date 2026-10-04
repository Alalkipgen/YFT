package com.alal.yft.feature.quickdownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.DownloadNetworkPolicy
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.preview.PreviewDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickDownloadUiState(
    /** Null when the last lookup no longer holds one video (the sheet then says so). */
    val choices: QuickChoices? = null,
    val selectedId: String? = null,
    val downloadStatus: PreviewDownloadStatus = PreviewDownloadStatus.Idle,
) {
    val selectedRow: QuickRow? get() = choices?.rows?.firstOrNull { it.id == selectedId }

    val canDownload: Boolean
        get() = selectedRow != null &&
            downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered
}

/** Where More formats goes: the single file's Download as, or the list of every format. */
enum class MoreFormatsTarget { DOWNLOAD_AS, FOUND_LIST }

/**
 * "Video you copied": the quick rows for the media Home's lookup put in [DetectedMediaStore].
 *
 * Download resolves the selected candidate the same way Download as does, so a merged
 * YouTube row keeps its `audioCompanion`, then queues it through [PreviewDownloadStarter],
 * which applies Wi-Fi only; mobile data asks first when the user chose to be asked.
 */
@HiltViewModel
class QuickDownloadViewModel @Inject constructor(
    store: DetectedMediaStore,
    private val selectionStore: PreviewSelectionStore,
    private val resolver: VariantResolver,
    private val downloadStarter: PreviewDownloadStarter,
    private val downloadPreferences: DownloadPreferencesRepository,
    private val network: NetworkStatusSource,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        QuickDownloadUiState(
            choices = store.page.value?.candidates?.let(QuickDownloadChoices::of),
        ),
    )
    val uiState: StateFlow<QuickDownloadUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val quality = currentPreferences().defaultQuality
            mutableUiState.update { state ->
                val choices = state.choices ?: return@update state
                if (state.selectedId != null) return@update state
                state.copy(selectedId = QuickDownloadChoices.preselect(choices, quality)?.id)
            }
        }
    }

    fun select(rowId: String) {
        mutableUiState.update { state ->
            if (!state.canChangeSelection) return@update state
            if (state.choices?.rows?.none { it.id == rowId } != false) return@update state
            state.copy(selectedId = rowId, downloadStatus = PreviewDownloadStatus.Idle)
        }
    }

    fun download() {
        val state = mutableUiState.value
        val row = state.selectedRow ?: return
        if (!state.canDownload) return
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch {
            val preferences = currentPreferences()
            val metered = DownloadNetworkPolicy.needsMeteredConfirmation(
                network.snapshot.value,
                preferences,
            )
            if (metered) {
                setStatus(PreviewDownloadStatus.ConfirmMetered)
            } else {
                enqueue(row, preferences)
            }
        }
    }

    fun confirmMeteredDownload() {
        val state = mutableUiState.value
        val row = state.selectedRow ?: return
        if (state.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch { enqueue(row, currentPreferences()) }
    }

    fun dismissMeteredDownload() {
        if (mutableUiState.value.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        setStatus(PreviewDownloadStatus.Idle)
    }

    /** One file opens its Download as; several open the Found list with every format. */
    fun moreFormats(): MoreFormatsTarget {
        val state = mutableUiState.value
        val choices = state.choices ?: return MoreFormatsTarget.FOUND_LIST
        if (choices.candidateCount != 1) return MoreFormatsTarget.FOUND_LIST
        val candidate = (state.selectedRow ?: choices.rows.first()).candidate
        selectionStore.select(candidate)
        return MoreFormatsTarget.DOWNLOAD_AS
    }

    private suspend fun enqueue(row: QuickRow, preferences: DownloadPreferences) {
        val status = try {
            when (val resolved = resolver.resolve(row.candidate)) {
                is VariantResolutionResult.Failure ->
                    PreviewDownloadStatus.Rejected(messageFor(resolved.reason))

                is VariantResolutionResult.Success -> {
                    val variant = resolved.asset.variants.firstOrNull { it.isPreviewable }
                    if (variant == null) {
                        PreviewDownloadStatus.Rejected(messageFor(null))
                    } else {
                        when (val result = downloadStarter.enqueue(resolved.asset, variant)) {
                            is EnqueueResult.Started -> PreviewDownloadStatus.Queued(
                                fileName = result.fileName,
                                waitingForUnmetered = DownloadNetworkPolicy.stateFor(
                                    network.snapshot.value,
                                    preferences,
                                ) == TransferNetworkState.WAITING_FOR_UNMETERED,
                            )

                            is EnqueueResult.Rejected ->
                                PreviewDownloadStatus.Rejected(result.message)
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PreviewDownloadStatus.Rejected("The download could not be queued. Try again.")
        }
        // The selection is locked while queueing, so the status belongs to [row].
        setStatus(status)
    }

    private suspend fun currentPreferences(): DownloadPreferences = try {
        downloadPreferences.preferences.first()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DownloadPreferences()
    }

    private fun setStatus(status: PreviewDownloadStatus) {
        mutableUiState.update { it.copy(downloadStatus = status) }
    }

    private val QuickDownloadUiState.canChangeSelection: Boolean
        get() = downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered

    private fun messageFor(reason: VariantResolutionFailure?): String = when (reason) {
        VariantResolutionFailure.EXPIRED_URL ->
            "This link has expired. Paste it on Home again."
        VariantResolutionFailure.DRM_PROTECTED -> "Protected media (DRM) can't be saved."
        VariantResolutionFailure.NETWORK ->
            "The media could not be reached. Check the connection and try again."
        VariantResolutionFailure.UNSUPPORTED_CODEC, null ->
            "This version can't be saved. Try More formats."
        else -> "This version could not be prepared. Try again or use More formats."
    }
}
