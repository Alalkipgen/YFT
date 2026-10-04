package com.alal.yft.ui

import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Test
    fun viewModelFollowsTheActiveDownloadCountForTheTabBadge() = runTest {
        val active = MutableStateFlow(0)
        val viewModel = AppViewModel(FakeSettingsRepository(), active)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.activeDownloads)

        active.value = 3
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.activeDownloads)
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

        override suspend fun setThemeMode(themeMode: ThemeMode) {
            this.themeMode.value = themeMode
        }

        override val checkCopiedLinks = MutableStateFlow(true)

        override suspend fun setCheckCopiedLinks(enabled: Boolean) {
            checkCopiedLinks.value = enabled
        }
    }
}
