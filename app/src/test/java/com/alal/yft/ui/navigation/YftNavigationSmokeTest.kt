package com.alal.yft.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.detectedmedia.DetectedMediaScreen
import com.alal.yft.feature.library.LibraryScreen
import com.alal.yft.feature.library.LibraryUiState
import com.alal.yft.feature.settings.SettingsScreen
import com.alal.yft.feature.settings.SettingsUiState
import com.alal.yft.ui.YftAppShell
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bottomBarSwitchesTabsAndShowsWhichOneIsSelected() {
        setShell()

        composeRule.onNodeWithTag("home-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()

        composeRule.onNodeWithTag("nav-downloads").performClick()
        screenTitle(YftDestination.DOWNLOADS).assertIsDisplayed()
        composeRule.onNodeWithTag("nav-downloads").assertIsSelected()
        composeRule.onNodeWithTag("nav-home").assertIsNotSelected()

        composeRule.onNodeWithTag("nav-library").performClick()
        screenTitle(YftDestination.LIBRARY).assertIsDisplayed()
        composeRule.onNodeWithTag("nav-library").assertIsSelected()

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-settings").assertIsSelected()

        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.onNodeWithTag("home-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun systemBackFromAnyTabReturnsHome() {
        setShell()

        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("nav-downloads").performClick()
        screenTitle(YftDestination.DOWNLOADS).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag("home-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun fullScreenDestinationsHideTheBarAndBackReturnsToTheTab() {
        setShell()

        YftDestination.homeActions.forEach { destination ->
            val destinationTag = "destination-${destination.route}"
            composeRule.onNodeWithTag("home-list")
                .performScrollToNode(hasTestTag(destinationTag))
            composeRule.onNodeWithTag(destinationTag).performClick()

            composeRule.onNodeWithText(destination.title).assertIsDisplayed()
            composeRule.onNodeWithTag("nav-home").assertDoesNotExist()
            composeRule.onNodeWithTag("navigate-back").performClick()
            composeRule.onNodeWithTag("home-list").assertIsDisplayed()
            composeRule.onNodeWithTag("nav-home").assertIsSelected()
        }

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-open-about"))
        composeRule.onNodeWithTag("settings-open-about").performClick()
        composeRule.onNodeWithTag("about-content").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-settings").assertDoesNotExist()

        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("settings-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test
    fun downloadsTabAnnouncesHowManyDownloadsAreMoving() {
        var active by mutableIntStateOf(2)
        setShell(activeDownloads = { active })

        composeRule.onNodeWithTag("nav-downloads")
            .assert(stateDescription("2 active downloads"))
        composeRule.onNodeWithTag("nav-home")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))

        active = 1
        composeRule.onNodeWithTag("nav-downloads").assert(stateDescription("1 active download"))

        active = 0
        composeRule.onNodeWithTag("nav-downloads")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
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

    /** The bar label repeats each tab's title, so match the screen's own title only. */
    private fun screenTitle(destination: YftDestination) = composeRule.onNode(
        hasText(destination.title) and !hasTestTag("nav-${destination.route}"),
    )

    private fun stateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun setShell(activeDownloads: () -> Int = { 0 }) {
        composeRule.setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            YftTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                YftAppShell(navController = navController, activeDownloads = activeDownloads()) {
                    TestNavHost(
                        navController = navController,
                        themeMode = themeMode,
                        onThemeModeChanged = { mode -> themeMode = mode },
                        modifier = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun TestNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier,
) {
    YftNavHost(
        navController = navController,
        themeMode = themeMode,
        onThemeModeChanged = onThemeModeChanged,
        modifier = modifier,
        browserContent = { onNavigateBack, _, _ ->
            Placeholder(YftDestination.BROWSER, onNavigateBack)
        },
        detectedMediaContent = { onNavigateBack, _, _ ->
            DetectedMediaScreen(page = null, onNavigateBack = onNavigateBack)
        },
        previewContent = { onNavigateBack -> Placeholder(YftDestination.PREVIEW, onNavigateBack) },
        downloadsContent = { onNavigateBack ->
            Placeholder(YftDestination.DOWNLOADS, onNavigateBack)
        },
        libraryContent = { onNavigateBack ->
            LibraryScreen(
                uiState = LibraryUiState.Ready(items = emptyList()),
                onNavigateBack = onNavigateBack,
            )
        },
        settingsContent = { onNavigateBack, onOpenAbout ->
            SettingsScreen(
                state = SettingsUiState(),
                themeMode = themeMode,
                sharedDownloadsSupported = true,
                onAction = {},
                onThemeModeChanged = onThemeModeChanged,
                onNavigateBack = onNavigateBack,
                onOpenAbout = onOpenAbout,
            )
        },
    )
}

@Composable
private fun Placeholder(destination: YftDestination, onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = destination.title,
        summary = destination.summary,
        phaseNote = "Navigation-only test surface",
        onNavigateBack = onNavigateBack,
    )
}
