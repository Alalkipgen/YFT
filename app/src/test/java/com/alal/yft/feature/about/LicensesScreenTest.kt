package com.alal.yft.feature.about

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LicensesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyNoticeIsListedUnderItsGroup() {
        setContent()

        composeRule.onNodeWithText(YftDestination.LICENSES.title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Bundled code").assertIsDisplayed()
        OpenSourceNotices.all.forEach { notice ->
            composeRule.onNodeWithTag("license-${notice.id}")
                .performScrollTo()
                .assertIsDisplayed()
                .assert(hasText(notice.usage))
                .assert(hasText(notice.license))
        }
        composeRule.onNodeWithContentDescription("Libraries").performScrollTo()
        composeRule.onNodeWithText("meriyah 6.1.4").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "The solver files keep their original license headers. Each library remains " +
                "under its own license.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aLicenseTextOpensOnDemandAndClosesAgain() {
        setContent(themeMode = ThemeMode.DARK)

        composeRule.onAllNodesWithTag("license-text-meriyah").assertCountEquals(0)
        composeRule.onNodeWithTag("license-meriyah")
            .performScrollTo()
            .assert(stateDescription("License hidden"))
            .performClick()
        composeRule.onNodeWithTag("license-text-meriyah", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(hasText("KFlash", substring = true))
        composeRule.onNodeWithTag("license-meriyah").assert(stateDescription("License shown"))

        composeRule.onNodeWithTag("license-meriyah").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("license-text-meriyah", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun lameShowsItsLgplNoticeSourceAndTheFullLicense() {
        setContent()

        composeRule.onNodeWithTag("license-lame").performScrollTo().performClick()
        val text = composeRule.onNodeWithTag("license-text-lame", useUnmergedTree = true)
        text.assert(hasText("core-download/src/main/cpp/lame-3.100", substring = true))
        text.assert(hasText("GNU LIBRARY GENERAL PUBLIC LICENSE", substring = true))
        text.assert(hasText("NO WARRANTY", substring = true))
    }

    @Test
    fun backLeavesThePage() {
        var back = 0
        setContent(onNavigateBack = { back++ })

        composeRule.onNodeWithTag("navigate-back").performClick()

        assertEquals(1, back)
    }

    private fun stateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun setContent(
        themeMode: ThemeMode = ThemeMode.LIGHT,
        onNavigateBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = themeMode) {
                LicensesScreen(onNavigateBack = onNavigateBack)
            }
        }
    }
}
