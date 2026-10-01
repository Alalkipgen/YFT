package com.alal.yft.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.theme.YftTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YftNavigationSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startsAtHomeAndEveryDestinationNavigatesBack() {
        composeRule.setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            YftTheme(themeMode = themeMode) {
                YftNavHost(
                    navController = rememberNavController(),
                    themeMode = themeMode,
                    onThemeModeChanged = { themeMode = it },
                    browserContent = { onNavigateBack ->
                        PhasePlaceholderScreen(
                            title = YftDestination.BROWSER.title,
                            summary = YftDestination.BROWSER.summary,
                            phaseNote = "Navigation-only test surface",
                            onNavigateBack = onNavigateBack,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("home-list").assertIsDisplayed()
        composeRule.onNodeWithText("Video Downloader").assertIsDisplayed()

        YftDestination.homeActions.forEach { destination ->
            val destinationTag = "destination-${destination.route}"
            composeRule.onNodeWithTag("home-list")
                .performScrollToNode(hasTestTag(destinationTag))
            composeRule.onNodeWithTag(destinationTag).performClick()

            composeRule.onNodeWithText(destination.title).assertIsDisplayed()
            composeRule.onNodeWithTag("navigate-back").performClick()
            composeRule.onNodeWithTag("home-list").assertIsDisplayed()
        }
    }
}