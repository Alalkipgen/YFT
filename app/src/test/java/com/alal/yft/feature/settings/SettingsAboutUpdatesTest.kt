package com.alal.yft.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.update.UpdatesRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → About: Version (the version on its second line), Updates, Licenses (§4.5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class SettingsAboutUpdatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val updates = mutableStateOf<UpdatesRow?>(UpdatesRow("Not checked yet", false))
    private var updatesOpened = 0
    private var aboutOpened = 0

    private fun setContent() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    state = SettingsUiState(),
                    themeMode = ThemeMode.SYSTEM,
                    sharedDownloadsSupported = true,
                    onAction = {},
                    onThemeModeChanged = {},
                    versionName = "1.0.0-beta.3-debug",
                    onOpenAbout = { aboutOpened += 1 },
                    updates = updates.value,
                    onOpenUpdates = { updatesOpened += 1 },
                )
            }
        }
    }

    private fun top(tag: String, unmerged: Boolean = false): Float =
        composeRule.onNodeWithTag(tag, useUnmergedTree = unmerged).getBoundsInRoot().top.value

    @Test
    fun aboutRowsReadVersionUpdatesLicensesInOrder() {
        setContent()

        composeRule.onNodeWithTag("settings-open-licenses").performScrollTo()
        assertTrue(top("settings-open-about") < top("settings-open-updates"))
        assertTrue(top("settings-open-updates") < top("settings-open-licenses"))
        // The version sits under the title now, not beside it.
        val title = composeRule.onNodeWithText("Version", useUnmergedTree = true).getBoundsInRoot()
        val version = composeRule.onNodeWithText("1.0.0-beta.3-debug", useUnmergedTree = true)
            .getBoundsInRoot()
        assertTrue(version.top >= title.bottom)
        assertEquals(title.left, version.left)
        composeRule.onNodeWithTag("settings-open-about")
            .assert(hasText("Version"))
            .assert(hasText("1.0.0-beta.3-debug"))
            .performClick()
        assertEquals(1, aboutOpened)
    }

    @Test
    fun theUpdatesRowsThreeStatesAndTheNewBadge() {
        setContent()

        composeRule.onNodeWithTag("settings-open-updates").performScrollTo()
            .assert(hasText("Updates"))
            .assert(hasText("Not checked yet"))
        composeRule.onAllNodesWithTag("settings-row-badge", useUnmergedTree = true)
            .assertCountEquals(0)

        updates.value = UpdatesRow("Up to date", isNew = false)
        composeRule.onNodeWithTag("settings-open-updates").assert(hasText("Up to date"))
        composeRule.onAllNodesWithTag("settings-row-badge", useUnmergedTree = true)
            .assertCountEquals(0)

        updates.value = UpdatesRow("v1.0.1 available", isNew = true)
        composeRule.onNodeWithTag("settings-open-updates")
            .assert(hasText("v1.0.1 available"))
            .assert(hasText("New"))
        composeRule.onAllNodesWithTag("settings-row-badge", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun noRowWhenTheBuildNeverChecks() {
        updates.value = null
        setContent()

        composeRule.onNodeWithTag("settings-open-licenses").performScrollTo()
        composeRule.onAllNodesWithTag("settings-open-updates").assertCountEquals(0)
    }

    @Test
    fun aTapOpensUpdates() {
        setContent()

        composeRule.onNodeWithTag("settings-open-updates").performScrollTo().performClick()

        assertEquals(1, updatesOpened)
    }
}
