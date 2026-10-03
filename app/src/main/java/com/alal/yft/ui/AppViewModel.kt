package com.alal.yft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.downloads.activeDownloadCount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    activeDownloads: Flow<Int>,
) : ViewModel() {
    /**
     * The badge reads the persisted queue; restoring it is idempotent and is also what the
     * Downloads screen does first, so opening the app shows the right count straight away.
     */
    @Inject
    constructor(settingsRepository: SettingsRepository, queue: DownloadQueue) : this(
        settingsRepository = settingsRepository,
        activeDownloads = flow {
            queue.restore()
            emitAll(queue.tasks.map { it.activeDownloadCount() })
        },
    )

    /** Without a download queue, for tests that only exercise the theme. */
    constructor(settingsRepository: SettingsRepository) : this(settingsRepository, flowOf(0))

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.themeMode,
        activeDownloads.distinctUntilChanged(),
    ) { themeMode, active -> AppUiState(themeMode = themeMode, activeDownloads = active) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppUiState(),
        )

    fun onAction(action: AppAction) {
        when (action) {
            is AppAction.ThemeModeChanged -> viewModelScope.launch {
                settingsRepository.setThemeMode(action.themeMode)
            }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        onAction(AppAction.ThemeModeChanged(themeMode))
    }
}
