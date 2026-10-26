package com.alal.yft.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.history.BrowserHistoryRepository
import com.alal.yft.core.data.preferences.BrowserPreferencesRepository
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.settings.BrowserPreferences
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.core.model.settings.SearchEngine
import com.alal.yft.detection.master.BackupFinder
import com.alal.yft.detection.master.VkTrialBreak
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A destructive action waiting for the user to confirm it. */
enum class SettingsConfirmation {
    CLEAR_BROWSING_DATA,
    CLEAR_DOWNLOAD_HISTORY,

    /** Settings › Browser › Clear browser history (P31). */
    CLEAR_BROWSER_HISTORY,
}

data class SettingsUiState(
    val download: DownloadPreferences = DownloadPreferences(),
    val finishedDownloads: Int = 0,
    val confirmation: SettingsConfirmation? = null,
    val working: Boolean = false,
    val message: String? = null,
    /** "Check copied links when YFT opens" (decision D1, on by default). */
    val checkCopiedLinks: Boolean = true,
    /** Settings › Browser (P30). */
    val browser: BrowserPreferences = BrowserPreferences(),
    /**
     * Settings › Browser › Backup finder (decision 30 (i), on by default); null in a build
     * without Master, which has no such row.
     */
    val backupFinder: Boolean? = null,
    /**
     * VK-Master wave 2 (agent D, item 7): Settings › Browser › VK value break, the choice kept and
     * the one this run uses; null but in #12 trial, which alone has the row.
     */
    val vkBreak: VkTrialBreak.Row? = null,
)

sealed interface SettingsAction {
    data class SetQuality(val quality: QualityPreference) : SettingsAction
    data class SetLocation(val location: DownloadLocation) : SettingsAction
    data class SetUnmeteredOnly(val enabled: Boolean) : SettingsAction
    data class SetConfirmMetered(val enabled: Boolean) : SettingsAction
    data class SetConcurrency(val count: Int) : SettingsAction
    data class SetCheckCopiedLinks(val enabled: Boolean) : SettingsAction
    data class SetSearchEngine(val engine: SearchEngine) : SettingsAction
    data class SetSaveHistory(val enabled: Boolean) : SettingsAction
    data class SetBlockPopups(val enabled: Boolean) : SettingsAction
    data class SetBackupFinder(val enabled: Boolean) : SettingsAction

    /** VK-Master wave 2 (item 7): the trial's VK value break, its next choice (after a restart). */
    data object NextVkBreak : SettingsAction
    data class Request(val confirmation: SettingsConfirmation) : SettingsAction
    data object Confirm : SettingsAction
    data object Dismiss : SettingsAction
    data object MessageShown : SettingsAction
}

private data class Transient(
    val confirmation: SettingsConfirmation? = null,
    val working: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: DownloadPreferencesRepository,
    private val browsingData: BrowsingDataCleaner,
    private val history: DownloadHistory,
    private val settings: SettingsRepository,
    private val browser: BrowserPreferencesRepository,
    private val browserHistory: BrowserHistoryRepository,
    // Decision 30 (i): the Backup finder switch; shown only when the build has Master.
    backupFinder: BackupFinder? = null,
    // VK-Master wave 2 (item 7): the trial's VK value break; shown only when the build has it.
    vkBreak: VkTrialBreak? = null,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val backupFinder = backupFinder?.takeIf { it.available }
    private val vkBreak = vkBreak?.takeIf { it.available }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.preferences,
        history.finishedCount,
        transient,
        settings.checkCopiedLinks,
        browser.preferences,
    ) { download, finished, local, checkCopiedLinks, browserPreferences ->
        SettingsUiState(
            download = download,
            finishedDownloads = finished,
            confirmation = local.confirmation,
            working = local.working,
            message = local.message,
            checkCopiedLinks = checkCopiedLinks,
            browser = browserPreferences,
        )
    }.combine(this.backupFinder?.on ?: flowOf(null)) { state, backupFinder ->
        state.copy(backupFinder = backupFinder)
    }.combine(this.vkBreak?.row ?: flowOf(null)) { state, vkBreak ->
        state.copy(vkBreak = vkBreak)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetQuality -> edit { it.copy(defaultQuality = action.quality) }
            is SettingsAction.SetLocation -> edit { it.copy(location = action.location) }
            is SettingsAction.SetUnmeteredOnly -> edit { it.copy(unmeteredOnly = action.enabled) }
            is SettingsAction.SetConfirmMetered ->
                edit { it.copy(confirmOnMeteredNetwork = action.enabled) }

            is SettingsAction.SetConcurrency -> {
                if (action.count in DownloadPreferences.CONCURRENT_DOWNLOAD_RANGE) {
                    edit { it.copy(maxConcurrentDownloads = action.count) }
                }
            }

            is SettingsAction.SetCheckCopiedLinks -> viewModelScope.launch {
                settings.setCheckCopiedLinks(action.enabled)
            }

            is SettingsAction.SetSearchEngine -> viewModelScope.launch {
                browser.update { it.copy(searchEngine = action.engine) }
            }

            is SettingsAction.SetSaveHistory -> viewModelScope.launch {
                browser.update { it.copy(saveHistory = action.enabled) }
            }

            is SettingsAction.SetBlockPopups -> viewModelScope.launch {
                browser.update { it.copy(blockPopups = action.enabled) }
            }

            is SettingsAction.SetBackupFinder -> backupFinder?.set(action.enabled)

            SettingsAction.NextVkBreak -> vkBreak?.next()

            is SettingsAction.Request -> transient.update {
                if (it.working) it else it.copy(confirmation = action.confirmation)
            }

            SettingsAction.Dismiss -> transient.update { it.copy(confirmation = null) }
            SettingsAction.Confirm -> runConfirmed()
            SettingsAction.MessageShown -> transient.update { it.copy(message = null) }
        }
    }

    private fun edit(transform: (DownloadPreferences) -> DownloadPreferences) {
        viewModelScope.launch { preferences.update(transform) }
    }

    private fun runConfirmed() {
        val confirmation = transient.value.confirmation ?: return
        if (transient.value.working) return
        transient.update { it.copy(confirmation = null, working = true) }
        viewModelScope.launch {
            val message = try {
                when (confirmation) {
                    SettingsConfirmation.CLEAR_BROWSING_DATA -> {
                        browsingData.clear()
                        "Browsing data cleared. Sites will ask you to sign in again."
                    }

                    SettingsConfirmation.CLEAR_BROWSER_HISTORY -> {
                        browserHistory.clear()
                        "Browser history cleared."
                    }

                    SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY -> {
                        val removed = history.clearFinished()
                        when (removed) {
                            0 -> "There was no finished download to remove."
                            1 -> "Removed 1 finished download from the list."
                            else -> "Removed $removed finished downloads from the list."
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "That could not be completed. Try again."
            }
            transient.update { it.copy(working = false, message = message) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
