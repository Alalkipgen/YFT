package com.alal.yft.feature.downloads

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P34: the notifications and battery cards on Downloads and what their buttons do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BackgroundCardsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val background = mutableStateOf(BackgroundCardsUiState.None)

    private fun setContent() {
        val actions = BackgroundCardActions(
            onTurnOnNotifications = { calls += "notifications" },
            onDismissNotifications = { calls += "dismiss" },
            onAllow = { calls += "allow" },
            onOpenAppSettings = { calls += "app-settings" },
            onNotNow = { calls += "not-now" },
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                DownloadsScreen(
                    uiState = DownloadsUiState.Empty.copy(background = background.value),
                    onAction = { _, _ -> },
                    onPauseAll = {},
                    backgroundActions = actions,
                )
            }
        }
    }

    @Test
    fun noCardsWithoutAReason() {
        setContent()

        composeRule.onAllNodesWithTag(NOTIFICATIONS_CARD_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(BATTERY_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun batteryCardOffersAllowAndNotNowWithXiaomiStepsAfterAFreeze() {
        background.value = BackgroundCardsUiState(
            batteryCard = BatteryCardUiState(
                freezeMessage = "Your phone paused YFT in the background for 1 min 40 s.",
                canAllow = true,
                xiaomiSteps = true,
            ),
        )
        setContent()

        composeRule.onNodeWithTag(BATTERY_CARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("$BATTERY_CARD_TAG-freeze", useUnmergedTree = true)
            .assertTextEquals("Your phone paused YFT in the background for 1 min 40 s.")
        composeRule.onNodeWithText(BATTERY_CARD_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag("$BATTERY_CARD_TAG-xiaomi", useUnmergedTree = true)
            .assertTextEquals(XIAOMI_STEPS)
        composeRule.onNodeWithTag("$BATTERY_CARD_TAG-allow").performClick()
        composeRule.onNodeWithTag("$BATTERY_CARD_TAG-not-now").performClick()

        assertEquals(listOf("allow", "not-now"), calls)
    }

    @Test
    fun batteryCardWithoutLimitsOpensTheAppSettingsAndHasNoXiaomiStepsElsewhere() {
        background.value = BackgroundCardsUiState(
            batteryCard = BatteryCardUiState(
                freezeMessage = "Your phone paused YFT in the background for 45 s.",
                canAllow = false,
                xiaomiSteps = false,
            ),
        )
        setContent()

        composeRule.onAllNodesWithTag("$BATTERY_CARD_TAG-xiaomi", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("$BATTERY_CARD_TAG-allow").assertCountEquals(0)
        composeRule.onNodeWithTag("$BATTERY_CARD_TAG-app-settings").performClick()

        assertEquals(listOf("app-settings"), calls)
    }

    @Test
    fun notificationsCardTurnsOnOrDismisses() {
        background.value = BackgroundCardsUiState(notificationsCard = true)
        setContent()

        composeRule.onNodeWithText(NOTIFICATIONS_CARD_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag("$NOTIFICATIONS_CARD_TAG-open").performClick()
        composeRule.onNodeWithTag("$NOTIFICATIONS_CARD_TAG-dismiss").performClick()

        assertEquals(listOf("notifications", "dismiss"), calls)
    }
}
