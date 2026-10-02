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
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.detectedmedia.DetectedMediaScreen
import com.alal.yft.feature.library.LibraryScreen
import com.alal.yft.feature.library.LibraryUiState
import com.alal.yft.feature.settings.SettingsScreen
import com.alal.yft.feature.settings.SettingsUiState
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
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
                    browserContent = { onNavigateBack, _, _ ->
                        PhasePlaceholderScreen(
                            title = YftDestination.BROWSER.title,
                            summary = YftDestination.BROWSER.summary,
                            phaseNote = "Navigation-only test surface",
                            onNavigateBack = onNavigateBack,
                        )
                    },
                    detectedMediaContent = { onNavigateBack, _, _ ->
                        DetectedMediaScreen(page = null, onNavigateBack = onNavigateBack)
                    },
                    previewContent = { onNavigateBack ->
                        PhasePlaceholderScreen(
                            title = YftDestination.PREVIEW.title,
                            summary = YftDestination.PREVIEW.summary,
                            phaseNote = "Navigation-only test surface",
                            onNavigateBack = onNavigateBack,
                        )
                    },
                    downloadsContent = { onNavigateBack ->
                        PhasePlaceholderScreen(
                            title = YftDestination.DOWNLOADS.title,
                            summary = YftDestination.DOWNLOADS.summary,
                            phaseNote = "Navigation-only test surface",
                            onNavigateBack = onNavigateBack,
                        )
                    },
                    libraryContent = { onNavigateBack ->
                        LibraryScreen(
                            uiState = LibraryUiState.Ready(items = emptyList()),
                            onNavigateBack = onNavigateBack,
                        )
                    },
                    settingsContent = { onNavigateBack ->
                        SettingsScreen(
                            state = SettingsUiState(),
                            themeMode = themeMode,
                            sharedDownloadsSupported = true,
                            onAction = {},
                            onThemeModeChanged = { themeMode = it },
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

    @Test
    fun homeLinkReachesTheBrowserIntactAndPlainBrowserOpensEmpty() {
        val received = mutableListOf<String?>()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                YftNavHost(
                    navController = rememberNavController(),
                    themeMode = ThemeMode.LIGHT,
                    onThemeModeChanged = {},
                    browserContent = { onNavigateBack, _, link ->
                        received += link
                        PhasePlaceholderScreen(
                            title = YftDestination.BROWSER.title,
                            summary = "link=$link",
                            phaseNote = "Navigation-only test surface",
                            onNavigateBack = onNavigateBack,
                        )
                    },
                )
            }
        }
        val link = "https://example.com/a b?c=1&d=é#part"

        composeRule.onNodeWithTag("home-link").performTextInput(link)
        composeRule.onNodeWithTag("home-open-link").performClick()

        composeRule.onNodeWithText("link=$link").assertIsDisplayed()
        assertEquals(link, received.last())
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("home-open-browser").performClick()
        composeRule.onNodeWithText("link=null").assertIsDisplayed()
    }
}
