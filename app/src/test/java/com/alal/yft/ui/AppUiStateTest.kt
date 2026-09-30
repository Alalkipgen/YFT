package com.alal.yft.ui

import com.alal.yft.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiStateTest {
    @Test
    fun themeActionProducesNewState() {
        val initial = AppUiState()
        val updated = initial.reduce(AppAction.ThemeModeChanged(ThemeMode.DARK))

        assertEquals(ThemeMode.SYSTEM, initial.themeMode)
        assertEquals(ThemeMode.DARK, updated.themeMode)
    }

    @Test
    fun viewModelExposesUnidirectionalThemeState() {
        val viewModel = AppViewModel()

        viewModel.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }
}
