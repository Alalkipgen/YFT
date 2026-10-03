package com.alal.yft.feature.about

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithText("The clipboard is read only when you tap Paste.")
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

    private fun setContent(
        themeMode: ThemeMode = ThemeMode.LIGHT,
        onOpenLicenses: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = themeMode) {
                AboutScreen(
                    onNavigateBack = {},
                    onOpenLicenses = onOpenLicenses,
                    versionName = "9.8.7",
                    versionCode = 42,
                )
            }
        }
    }
}
