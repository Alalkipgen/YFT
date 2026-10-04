package com.alal.yft.feature.settings

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferences()
    private val cleaner = FakeCleaner()
    private val history = FakeHistory()
    private val settings = FakeSettings()

    @Test
    fun theCopiedLinkSwitchWritesTheSetting() = runTest {
        val viewModel = subscribed()
        assertTrue(viewModel.uiState.value.checkCopiedLinks)

        viewModel.onAction(SettingsAction.SetCheckCopiedLinks(false))
        runCurrent()

        assertFalse(settings.checkCopiedLinks.value)
        assertFalse(viewModel.uiState.value.checkCopiedLinks)
    }

    @Test
    fun `download preferences are saved as they change`() = runTest {
        val viewModel = subscribed()

        viewModel.onAction(SettingsAction.SetQuality(QualityPreference.UP_TO_720P))
        viewModel.onAction(SettingsAction.SetLocation(DownloadLocation.APP_STORAGE))
        viewModel.onAction(SettingsAction.SetUnmeteredOnly(true))
        viewModel.onAction(SettingsAction.SetConfirmMetered(false))
        viewModel.onAction(SettingsAction.SetConcurrency(3))
        viewModel.onAction(SettingsAction.SetConcurrency(9))
        runCurrent()

        val expected = DownloadPreferences(
            defaultQuality = QualityPreference.UP_TO_720P,
            location = DownloadLocation.APP_STORAGE,
            unmeteredOnly = true,
            maxConcurrentDownloads = 3,
            confirmOnMeteredNetwork = false,
        )
        assertEquals(expected, preferences.state.value)
        assertEquals(expected, viewModel.uiState.value.download)
    }

    @Test
    fun `browsing data is cleared only after confirmation`() = runTest {
        val viewModel = subscribed()

        viewModel.onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_BROWSING_DATA))
        runCurrent()
        assertEquals(
            SettingsConfirmation.CLEAR_BROWSING_DATA,
            viewModel.uiState.value.confirmation,
        )
        assertEquals(0, cleaner.calls)

        viewModel.onAction(SettingsAction.Confirm)
        runCurrent()

        assertEquals(1, cleaner.calls)
        assertNull(viewModel.uiState.value.confirmation)
        assertFalse(viewModel.uiState.value.working)
        assertTrue(viewModel.uiState.value.message!!.startsWith("Browsing data cleared"))

        viewModel.onAction(SettingsAction.MessageShown)
        runCurrent()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `dismissing the confirmation leaves everything in place`() = runTest {
        val viewModel = subscribed()

        viewModel.onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY))
        viewModel.onAction(SettingsAction.Dismiss)
        viewModel.onAction(SettingsAction.Confirm)
        runCurrent()

        assertEquals(0, history.clears)
        assertNull(viewModel.uiState.value.confirmation)
    }

    @Test
    fun `clearing download history reports how many entries went away`() = runTest {
        history.count.value = 3
        val viewModel = subscribed()
        assertEquals(3, viewModel.uiState.value.finishedDownloads)

        viewModel.onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY))
        viewModel.onAction(SettingsAction.Confirm)
        runCurrent()

        assertEquals(1, history.clears)
        assertEquals(0, viewModel.uiState.value.finishedDownloads)
        assertEquals(
            "Removed 3 finished downloads from the list.",
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `a failed clean up is reported instead of crashing`() = runTest {
        cleaner.failure = IOException("webview gone")
        val viewModel = subscribed()

        viewModel.onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_BROWSING_DATA))
        viewModel.onAction(SettingsAction.Confirm)
        runCurrent()

        assertEquals("That could not be completed. Try again.", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.working)
    }

    private fun TestScope.subscribed(): SettingsViewModel {
        val viewModel = SettingsViewModel(preferences, cleaner, history, settings)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private class FakeSettings : SettingsRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val checkCopiedLinks = MutableStateFlow(true)

        override suspend fun setThemeMode(themeMode: ThemeMode) = Unit

        override suspend fun setCheckCopiedLinks(enabled: Boolean) {
            checkCopiedLinks.value = enabled
        }
    }

    private class FakePreferences : DownloadPreferencesRepository {
        val state = MutableStateFlow(DownloadPreferences())
        override val preferences: Flow<DownloadPreferences> = state

        override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) {
            state.value = transform(state.value)
        }
    }

    private class FakeCleaner : BrowsingDataCleaner {
        var calls = 0
        var failure: Exception? = null

        override suspend fun clear() {
            calls += 1
            failure?.let { throw it }
        }
    }

    private class FakeHistory : DownloadHistory {
        val count = MutableStateFlow(0)
        var clears = 0
        override val finishedCount: Flow<Int> = count

        override suspend fun clearFinished(): Int {
            clears += 1
            val removed = count.value
            count.value = 0
            return removed
        }
    }
}
