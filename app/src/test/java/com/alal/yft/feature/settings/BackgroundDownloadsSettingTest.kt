package com.alal.yft.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.feature.downloads.BATTERY_CARD_TEXT
import com.alal.yft.feature.downloads.XIAOMI_STEPS
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P34: Settings › Downloads › Background downloads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundDownloadsSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(background: BackgroundSystemState?) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    state = SettingsUiState(),
                    themeMode = ThemeMode.SYSTEM,
                    sharedDownloadsSupported = true,
                    onAction = {},
                    onThemeModeChanged = {},
                    background = background,
                    onAllowBackground = { calls += "allow" },
                    onOpenAppSettings = { calls += "app-settings" },
                )
            }
        }
    }

    @Test
    fun limitedShowsTheBatteryTextAllowAndXiaomiSteps() {
        setContent(BackgroundSystemState(unrestricted = false, xiaomi = true))

        composeRule.onNodeWithTag("settings-background-downloads")
            .performScrollTo()
            .assertTextContains("Background downloads")
            .assertTextContains("Limited")
            .performClick()
        composeRule.onNodeWithTag("settings-background-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-background-text", useUnmergedTree = true)
            .assertTextEquals(BATTERY_CARD_TEXT)
        composeRule.onNodeWithTag("settings-background-xiaomi", useUnmergedTree = true)
            .assertTextEquals(XIAOMI_STEPS)
        composeRule.onNodeWithTag("settings-background-allow").performClick()

        assertEquals(listOf("allow"), calls)
        composeRule.onAllNodesWithTag("settings-background-dialog").assertCountEquals(0)
    }

    @Test
    fun allowedSaysSoWithoutXiaomiStepsElsewhere() {
        setContent(BackgroundSystemState.Unlimited)

        composeRule.onNodeWithTag("settings-background-downloads")
            .performScrollTo()
            .assertTextContains("Allowed")
            .performClick()
        composeRule.onNodeWithTag("settings-background-text", useUnmergedTree = true)
            .assertTextEquals(BACKGROUND_ALLOWED_TEXT)
        composeRule.onAllNodesWithTag("settings-background-xiaomi").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-background-app-settings").performClick()

        assertEquals(listOf("app-settings"), calls)
    }

    @Test
    fun withoutAStateTheRowIsNotShown() {
        setContent(background = null)

        composeRule.onAllNodesWithTag("settings-background-downloads").assertCountEquals(0)
    }
}
