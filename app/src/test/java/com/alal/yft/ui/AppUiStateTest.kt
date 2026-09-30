package com.alal.yft.ui

import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUiStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun themeActionProducesNewState() {
        val initial = AppUiState()
        val updated = initial.reduce(AppAction.ThemeModeChanged(ThemeMode.DARK))

        assertEquals(ThemeMode.SYSTEM, initial.themeMode)
        assertEquals(ThemeMode.DARK, updated.themeMode)
    }

    @Test
    fun viewModelPersistsAndExposesThemeState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, repository.themeMode.value)
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

        override suspend fun setThemeMode(themeMode: ThemeMode) {
            this.themeMode.value = themeMode
        }
    }
}
