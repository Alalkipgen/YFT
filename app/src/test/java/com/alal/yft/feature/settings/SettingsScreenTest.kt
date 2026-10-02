package com.alal.yft.feature.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `choices report the action the user picked`() {
        val actions = mutableListOf<SettingsAction>()
        var theme = ThemeMode.SYSTEM
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                themeMode = ThemeMode.SYSTEM,
                sharedDownloadsSupported = true,
                onAction = { actions += it },
                onThemeModeChanged = { theme = it },
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithTag("quality-HIGHEST").assertIsSelected()
        composeRule.onNodeWithTag("quality-UP_TO_720P").performClick()
        composeRule.onNodeWithTag("location-APP_STORAGE").performScrollTo().performClick()
        composeRule.onNodeWithTag("unmetered-only").performScrollTo().performClick()
        composeRule.onNodeWithTag("concurrency-3").performScrollTo().performClick()
        composeRule.onNodeWithTag("theme-DARK").performScrollTo().performClick()

        assertEquals(
            listOf(
                SettingsAction.SetQuality(QualityPreference.UP_TO_720P),
                SettingsAction.SetLocation(DownloadLocation.APP_STORAGE),
                SettingsAction.SetUnmeteredOnly(true),
                SettingsAction.SetConcurrency(3),
            ),
            actions,
        )
        assertEquals(ThemeMode.DARK, theme)
    }

    @Test
    fun `wifi only makes the mobile data question moot`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(download = DownloadPreferences(unmeteredOnly = true)),
                themeMode = ThemeMode.SYSTEM,
                sharedDownloadsSupported = true,
                onAction = {},
                onThemeModeChanged = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithTag("confirm-metered").performScrollTo()
            .assertIsNotEnabled()
            .assertIsOff()
    }

    @Test
    fun `older releases can only save to app storage`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                themeMode = ThemeMode.SYSTEM,
                sharedDownloadsSupported = false,
                onAction = {},
                onThemeModeChanged = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithTag("location-SHARED_DOWNLOADS").performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("location-APP_STORAGE").assertIsSelected()
    }

    @Test
    fun `clearing asks for confirmation first`() {
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    finishedDownloads = 2,
                    confirmation = SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY,
                ),
                themeMode = ThemeMode.SYSTEM,
                sharedDownloadsSupported = true,
                onAction = { actions += it },
                onThemeModeChanged = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithText("Clear download history?").assertExists()
        composeRule.onNodeWithText(
            "Removes 2 finished entries from Downloads. " +
                "Files you already saved stay on the device.",
        ).assertExists()
        composeRule.onNodeWithTag("confirm-action").performClick()

        assertTrue(actions.contains(SettingsAction.Confirm))
    }

    @Test
    fun `history cannot be cleared when nothing finished`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(finishedDownloads = 0),
                themeMode = ThemeMode.SYSTEM,
                sharedDownloadsSupported = true,
                onAction = {},
                onThemeModeChanged = {},
                onNavigateBack = {},
            )
        }

        composeRule.onNodeWithTag("clear-download-history").performScrollTo()
            .assertIsNotEnabled()
    }
}
