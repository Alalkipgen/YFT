package com.alal.yft.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.ui.theme.YftTheme
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

    private val shown = mutableStateOf(SettingsUiState())
    private val actions = mutableListOf<SettingsAction>()
    private var theme = ThemeMode.SYSTEM
    private var aboutOpened = 0
    private var licensesOpened = 0

    @Test
    fun `theme, switches and the stepper report what the user picked`() {
        setContent()

        composeRule.onNodeWithTag("theme-SYSTEM").assertIsSelected()
        composeRule.onNodeWithTag("theme-DARK").performClick()
        composeRule.onNodeWithTag("unmetered-only").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm-metered").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("concurrency-value", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("${DownloadPreferences.DEFAULT_CONCURRENT_DOWNLOADS}"))
        composeRule.onNodeWithTag("concurrency-increase").performClick()
        composeRule.onNodeWithTag("concurrency-decrease").performClick()

        assertEquals(ThemeMode.DARK, theme)
        assertEquals(
            listOf(
                SettingsAction.SetUnmeteredOnly(true),
                SettingsAction.SetConfirmMetered(false),
                SettingsAction.SetConcurrency(DownloadPreferences.DEFAULT_CONCURRENT_DOWNLOADS + 1),
                SettingsAction.SetConcurrency(DownloadPreferences.DEFAULT_CONCURRENT_DOWNLOADS - 1),
            ),
            actions,
        )
    }

    @Test
    fun `the copied link switch is on by default and reports a toggle`() {
        setContent()

        composeRule.onNodeWithTag("check-copied-links").performScrollTo().assertIsOn()
            .performClick()
        composeRule.onNodeWithText(
            "Android shows a short \"pasted\" message when YFT reads a copied link.",
        ).assertExists()

        assertEquals(listOf<SettingsAction>(SettingsAction.SetCheckCopiedLinks(false)), actions)
    }

    @Test
    fun `quality and location open a dialog that applies the tapped choice`() {
        setContent()

        composeRule.onNodeWithTag("settings-quality").performScrollTo()
            .assert(hasText("Highest available"))
            .performClick()
        composeRule.onNodeWithTag("quality-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Download as starts on this quality when a page offers it.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("quality-HIGHEST").assertIsSelected()
        composeRule.onNodeWithTag("quality-UP_TO_720P").performClick()
        composeRule.onAllNodesWithTag("quality-dialog").assertCountEquals(0)

        composeRule.onNodeWithTag("settings-location").performScrollTo()
            .assert(hasText("Download/YFT"))
            .performClick()
        composeRule.onNodeWithTag("location-SHARED_DOWNLOADS").assertIsSelected()
        composeRule.onNodeWithTag("location-APP_STORAGE").performClick()
        composeRule.onAllNodesWithTag("location-dialog").assertCountEquals(0)

        composeRule.onNodeWithTag("settings-quality").performClick()
        composeRule.onNodeWithTag("choice-cancel").performClick()
        composeRule.onAllNodesWithTag("quality-dialog").assertCountEquals(0)

        assertEquals(
            listOf(
                SettingsAction.SetQuality(QualityPreference.UP_TO_720P),
                SettingsAction.SetLocation(DownloadLocation.APP_STORAGE),
            ),
            actions,
        )
    }

    @Test
    fun `wifi only makes the mobile data question moot`() {
        setContent(
            state = SettingsUiState(
                download = DownloadPreferences(
                    unmeteredOnly = true,
                    confirmOnMeteredNetwork = true,
                ),
            ),
        )

        composeRule.onNodeWithTag("unmetered-only").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("confirm-metered").performScrollTo()
            .assertIsNotEnabled()
            .assertIsOn()
            .assert(hasText("Not needed while Wi-Fi only is on"))
    }

    @Test
    fun `older releases can only save to app storage`() {
        setContent(sharedDownloadsSupported = false)

        composeRule.onNodeWithTag("settings-location").performScrollTo()
            .assert(hasText("App storage"))
            .performClick()
        composeRule.onNodeWithTag("location-SHARED_DOWNLOADS")
            .assertIsNotEnabled()
            .assert(hasText("Needs Android 10 or newer. This device saves to app storage."))
        composeRule.onNodeWithTag("location-APP_STORAGE").assertIsSelected()
    }

    @Test
    fun `clearing asks for confirmation first`() {
        setContent(
            state = SettingsUiState(
                finishedDownloads = 2,
                confirmation = SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY,
            ),
        )

        composeRule.onNodeWithText("Clear download history?").assertExists()
        composeRule.onNodeWithText(
            "Removes 2 finished entries from Downloads. " +
                "Files you already saved stay on the device.",
        ).assertExists()
        composeRule.onNodeWithTag("confirm-action").performClick()

        assertTrue(actions.contains(SettingsAction.Confirm))
    }

    @Test
    fun `clearing browsing data says what goes`() {
        setContent()

        composeRule.onNodeWithTag("clear-browsing-data").performScrollTo().performClick()
        assertEquals(
            listOf(SettingsAction.Request(SettingsConfirmation.CLEAR_BROWSING_DATA)),
            actions,
        )

        shown.value = SettingsUiState(confirmation = SettingsConfirmation.CLEAR_BROWSING_DATA)
        composeRule.onNodeWithText("Clear browsing data?").assertExists()
        composeRule.onNodeWithText(
            "Removes cookies, site storage, the cache, saved sign-ins and the found media " +
                "list. You will be signed out of every site opened in YFT. Downloads and " +
                "settings are not affected.",
        ).assertExists()
        composeRule.onNodeWithTag("dismiss-action").performClick()
        assertEquals(SettingsAction.Dismiss, actions.last())
    }

    @Test
    fun `history cannot be cleared when nothing finished`() {
        setContent(state = SettingsUiState(finishedDownloads = 0))

        composeRule.onNodeWithTag("clear-download-history").performScrollTo()
            .assertIsNotEnabled()
            .assert(hasText("No finished downloads in the list"))
        composeRule.onNodeWithTag("clear-browsing-data").assertIsEnabled()
    }

    @Test
    fun `version and licenses open their own pages`() {
        setContent()

        composeRule.onNodeWithTag("settings-open-about").performScrollTo()
            .assert(hasText("Version"))
            .assert(hasText("9.8.7"))
            .performClick()
        composeRule.onNodeWithTag("settings-open-licenses").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-footer").performScrollTo()
            .assert(hasText("No ads · No tracking · No account"))
        // Settings is a tab: there is no way back, only the bottom bar.
        composeRule.onAllNodesWithTag("navigate-back").assertCountEquals(0)

        assertEquals(1, aboutOpened)
        assertEquals(1, licensesOpened)
    }

    @Test
    fun `group labels read in normal case`() {
        setContent()

        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Appearance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Privacy").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `theme choices stay on screen with the largest text`() {
        setContent(fontScale = 2f)

        ThemeMode.entries.forEach { mode ->
            composeRule.onNodeWithTag("theme-${mode.name}").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("concurrency-increase").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(
        state: SettingsUiState = SettingsUiState(),
        sharedDownloadsSupported: Boolean = true,
        fontScale: Float = 1f,
    ) {
        shown.value = state
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                YftTheme(themeMode = ThemeMode.LIGHT) {
                    SettingsScreen(
                        state = shown.value,
                        themeMode = theme,
                        sharedDownloadsSupported = sharedDownloadsSupported,
                        onAction = { actions += it },
                        onThemeModeChanged = { theme = it },
                        versionName = "9.8.7",
                        onOpenAbout = { aboutOpened++ },
                        onOpenLicenses = { licensesOpened++ },
                    )
                }
            }
        }
    }
}
