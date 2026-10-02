package com.alal.yft.feature.about

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
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

        composeRule.onNodeWithTag("about-version")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Version 9.8.7 (42)").assertIsDisplayed()
        composeRule.onNodeWithText("What YFT does not do").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("• The clipboard is read only when you tap Paste.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("• Logs never contain cookies, tokens or signed links.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun everyNoticeIsListedAndItsLicenseTextOpensOnDemand() {
        setContent(themeMode = ThemeMode.DARK)

        OpenSourceNotices.all.forEach { notice ->
            composeRule.onNodeWithTag("about-license-${notice.id}").performScrollTo()
            composeRule.onNodeWithText(notice.usage).assertIsDisplayed()
        }
        composeRule.onNodeWithText("meriyah 6.1.4").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("about-license-text-meriyah").assertCountEquals(0)

        composeRule.onNodeWithTag("about-license-meriyah").performScrollTo().performClick()
        composeRule.onNodeWithTag("about-license-text-meriyah")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(hasText("KFlash", substring = true))

        composeRule.onNodeWithTag("about-license-meriyah").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("about-license-text-meriyah").assertCountEquals(0)
    }

    private fun setContent(themeMode: ThemeMode = ThemeMode.LIGHT) {
        composeRule.setContent {
            YftTheme(themeMode = themeMode) {
                AboutScreen(onNavigateBack = {}, versionName = "9.8.7", versionCode = 42)
            }
        }
    }
}
