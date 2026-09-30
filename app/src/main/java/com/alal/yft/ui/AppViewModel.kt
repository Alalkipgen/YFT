package com.alal.yft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<AppUiState> = settingsRepository.themeMode
        .map(::AppUiState)
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
