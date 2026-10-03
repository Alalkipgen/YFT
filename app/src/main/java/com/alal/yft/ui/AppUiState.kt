package com.alal.yft.ui

import com.alal.yft.core.model.ThemeMode

data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Downloads moving right now, shown as the Coral badge on the Downloads tab. */
    val activeDownloads: Int = 0,
)

sealed interface AppAction {
    data class ThemeModeChanged(val themeMode: ThemeMode) : AppAction
}

internal fun AppUiState.reduce(action: AppAction): AppUiState = when (action) {
    is AppAction.ThemeModeChanged -> copy(themeMode = action.themeMode)
}
