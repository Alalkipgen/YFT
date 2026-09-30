package com.alal.yft.ui

import com.alal.yft.core.model.ThemeMode

data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

sealed interface AppAction {
    data class ThemeModeChanged(val themeMode: ThemeMode) : AppAction
}

internal fun AppUiState.reduce(action: AppAction): AppUiState = when (action) {
    is AppAction.ThemeModeChanged -> copy(themeMode = action.themeMode)
}
