package com.alal.yft.feature.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.alal.yft.BuildConfig
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsVersionScopeAndPrivacyPromises() {
        setContent()

        composeRule.onNodeWithTag("about-version").assertIsDisplayed()
        composeRule.onNodeWithText("Version 9.8.7 (42)").assertIsDisplayed()
        composeRule.onNodeWithText("YFT · Video Downloader").assertIsDisplayed()
        // Group labels are drawn in capitals but read out in normal case.
        composeRule.onNodeWithContentDescription("What YFT does not do")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "The clipboard is read on Paste or Use, or once per new clip if Check copied " +
                "links is on.",
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Logs never contain cookies, tokens or signed links.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("•", substring = true).assertCountEquals(0)
    }

    @Test
    fun licensesHaveTheirOwnPage() {
        var opened = 0
        setContent(themeMode = ThemeMode.DARK, onOpenLicenses = { opened++ })

        composeRule.onAllNodesWithTag("license-meriyah").assertCountEquals(0)
        composeRule.onNodeWithTag("about-open-licenses")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(hasText("Licenses"))
            .performClick()

        assertEquals(1, opened)
    }

    @Test
    fun reportIsHiddenUntilOneExists() {
        setContent()
        composeRule.onNodeWithTag("about-crash-report").assertDoesNotExist()
        composeRule.onNodeWithTag("about-crash-dialog").assertDoesNotExist()
    }

    @Test
    fun reportActionsAreExplicitAndViewIsDismissible() {
        var copies = 0
        var shares = 0
        var deletes = 0
        setContent(
            crashReport = "local crash fixture",
            onCopyCrash = { copies++ },
            onShareCrash = { shares++ },
            onDeleteCrash = { deletes++ },
        )
        assertEquals(0, copies + shares + deletes)
        composeRule.onNodeWithTag("about-crash-view").performScrollTo().performClick()
        composeRule.onNodeWithTag("about-crash-text").assertIsDisplayed()
        composeRule.onNodeWithText("local crash fixture").assertIsDisplayed()
        assertEquals(0, copies + shares + deletes)
        composeRule.onNodeWithTag("about-crash-close").performClick()
        composeRule.onNodeWithTag("about-crash-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("about-crash-copy").performScrollTo().performClick()
        composeRule.onNodeWithTag("about-crash-share").performClick()
        composeRule.onNodeWithTag("about-crash-delete").performClick()
        assertEquals(1, copies)
        assertEquals(1, shares)
        assertEquals(1, deletes)
    }

    @Test
    fun debugCrashRequiresLongPressAndConfirmation() {
        assumeTrue(BuildConfig.DEBUG)
        setContent()
        composeRule.onNodeWithText("Crash now").assertDoesNotExist()
        composeRule.onNodeWithTag("about-version").performTouchInput { longClick() }
        composeRule.onNodeWithText("Crash now?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Crash now?").assertDoesNotExist()
        assertTrue(BuildConfig.DEBUG)
    }

    @Test
    fun privacyNamesGitHubFirst() {
        // Decision 30 (ii): the Backup finder's GitHub raw; the update check's api.github.com.
        setContent()

        composeRule.onNodeWithText(PRIVACY_CONTACTS).performScrollTo().assertIsDisplayed()
        assertTrue(PRIVACY_CONTACTS.contains("raw.githubusercontent.com"))
        assertTrue(PRIVACY_CONTACTS.contains("api.github.com"))
        composeRule.onAllNodesWithText(
            "YFT only contacts the sites you open and the media servers they point to.",
        ).assertCountEquals(0)
    }

    @Test
    fun aQuietBuildsVersionLineTappedSevenTimesTurnsShowDetailsOnAndOff() {
        // Decision 30 (v): hidden, off by default, not in Settings.
        var on by mutableStateOf(false)
        var toggles = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                AboutScreen(
                    onNavigateBack = {},
                    onOpenLicenses = {},
                    versionName = "9.8.7",
                    versionCode = 42,
                    showDetails = on,
                    onToggleShowDetails = {
                        toggles++
                        on = !on
                    },
                )
            }
        }

        composeRule.onAllNodesWithTag("about-show-details").assertCountEquals(0)
        repeat(SevenTaps.TAPS - 1) { composeRule.onNodeWithTag("about-version").performClick() }
        assertEquals(0, toggles)
        composeRule.onNodeWithTag("about-version").performClick()
        assertEquals(1, toggles)
        composeRule.onNodeWithTag("about-show-details").assert(hasText(SHOW_DETAILS_ON))

        repeat(SevenTaps.TAPS) { composeRule.onNodeWithTag("about-version").performClick() }
        assertEquals(2, toggles)
        composeRule.onAllNodesWithTag("about-show-details").assertCountEquals(0)
    }

    @Test
    fun anotherBuildsVersionLineHasNoShowDetails() {
        setContent()
        repeat(SevenTaps.TAPS) { composeRule.onNodeWithTag("about-version").performClick() }
        composeRule.onAllNodesWithTag("about-show-details").assertCountEquals(0)
    }

    private fun setContent(
        themeMode: ThemeMode = ThemeMode.LIGHT,
        onOpenLicenses: () -> Unit = {},
        crashReport: String? = null,
        onCopyCrash: () -> Unit = {},
        onShareCrash: () -> Unit = {},
        onDeleteCrash: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = themeMode) {
                AboutScreen(
                    onNavigateBack = {},
                    onOpenLicenses = onOpenLicenses,
                    versionName = "9.8.7",
                    versionCode = 42,
                    crashReport = crashReport,
                    onCopyCrash = onCopyCrash,
                    onShareCrash = onShareCrash,
                    onDeleteCrash = onDeleteCrash,
                )
            }
        }
    }
}
