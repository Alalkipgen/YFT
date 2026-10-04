package com.alal.yft.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.feature.detectedmedia.DetectedMediaScreen
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.home.ClipPeek
import com.alal.yft.feature.home.ClipboardAccess
import com.alal.yft.feature.home.CopiedLinkWatcher
import com.alal.yft.feature.home.HomeRoute
import com.alal.yft.feature.home.HomeViewModel
import com.alal.yft.feature.home.LinkInspection
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.feature.library.LibraryRepository
import com.alal.yft.feature.library.LibraryScreen
import com.alal.yft.feature.library.LibraryUiState
import com.alal.yft.feature.quickdownload.MoreFormatsTarget
import com.alal.yft.feature.settings.SettingsScreen
import com.alal.yft.feature.settings.SettingsUiState
import com.alal.yft.ui.YftAppShell
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YftNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var shellNavController: NavHostController

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

        composeRule.onNodeWithTag("home-open-browser").performClick()
        composeRule.onNodeWithText(YftDestination.BROWSER.title).assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertDoesNotExist()
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("home-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()

        composeRule.onNodeWithTag("home-link").performTextInput("https://a.test/found")
        composeRule.onNodeWithTag("home-open-link").performClick()
        composeRule.onNodeWithTag("home-view-media").performClick()
        composeRule.onNodeWithText(YftDestination.DETECTED_MEDIA.title).assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertDoesNotExist()
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("home-found").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()

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
    fun licensesOpenFromSettingsAndFromAboutAndBackRetracesTheWay() {
        setShell()
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-open-licenses"))
        composeRule.onNodeWithTag("settings-open-licenses").performClick()
        composeRule.onNodeWithTag("licenses-content").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-settings").assertDoesNotExist()
        assertEquals(YftDestination.LICENSES.route, shellNavController.currentDestination?.route)
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("settings-list").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-open-about"))
        composeRule.onNodeWithTag("settings-open-about").performClick()
        composeRule.onNodeWithTag("about-content")
            .performScrollToNode(hasTestTag("about-open-licenses"))
        composeRule.onNodeWithTag("about-open-licenses").performClick()
        composeRule.onNodeWithTag("licenses-content").assertIsDisplayed()

        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("about-content").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("settings-list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test
    fun aLibraryVideoPlaysFullScreenAndBackReturnsToTheLibrary() {
        setShell()
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("library-item-${SAVED_VIDEO.id}").performClick()

        composeRule.onNodeWithText(YftDestination.PLAYER.summary).assertIsDisplayed()
        composeRule.onNodeWithTag("nav-library").assertDoesNotExist()
        assertEquals(YftDestination.PLAYER.route, shellNavController.currentDestination?.route)

        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("library-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-library").assertIsSelected()
    }

    @Test
    fun theMiniPlayerSitsAboveTheBarOnEveryTabOnly() {
        setShell(miniPlayer = { Text("Now playing", Modifier.testTag("mini")) })

        composeRule.onNodeWithTag("mini").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-library").performClick()
        composeRule.onNodeWithTag("mini").assertIsDisplayed()
        val mini = composeRule.onNodeWithTag("mini").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag("nav-library").getUnclippedBoundsInRoot()
        assertTrue(mini.bottom <= bar.top)

        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.onNodeWithTag("home-open-browser").performClick()
        composeRule.onNodeWithTag("mini").assertDoesNotExist()
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("mini").assertIsDisplayed()
    }

    @Test
    fun searchToDownloadOpensTheBrowserStartPageInSearchMode() {
        setShell()
        composeRule.onNodeWithTag("home-search").performScrollTo().performClick()

        composeRule.onNodeWithText("search mode").assertIsDisplayed()
        assertEquals(BROWSER_ROUTE_PATTERN, shellNavController.currentDestination?.route)
    }

    @Test
    fun browserDownloadOfTheCopiedLinkIsLookedUpOnHomeAndOpensVideoYouCopied() {
        setShell()
        composeRule.onNodeWithTag("home-open-browser").performClick()
        composeRule.onNodeWithText("search mode").assertDoesNotExist()

        composeRule.onNodeWithTag("browser-copied-download").performClick()

        composeRule.onNodeWithTag("modal-sheet").assertExists()
        composeRule.onNodeWithText(YftDestination.QUICK_DOWNLOAD.summary).assertIsDisplayed()
        assertEquals(
            YftDestination.QUICK_DOWNLOAD.route,
            shellNavController.currentDestination?.route,
        )
    }

    // Native graphics hit-tests the sheet's top-rounded shape, so taps inside it land.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun oneCopiedVideoOpensVideoYouCopiedOverHomeAndMoreFormatsOpensTheList() {
        setShell()
        composeRule.onNodeWithTag("home-link").performTextInput("https://a.test/copied")
        composeRule.onNodeWithTag("home-open-link").performClick()

        composeRule.onNodeWithTag("modal-sheet").assertExists()
        composeRule.onNodeWithText(YftDestination.QUICK_DOWNLOAD.summary).assertIsDisplayed()
        assertEquals(
            YftDestination.QUICK_DOWNLOAD.route,
            shellNavController.currentDestination?.route,
        )
        composeRule.onNodeWithTag("sheet-close").performClick()
        composeRule.onNodeWithTag("modal-sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("home-found").assertIsDisplayed()

        // View reopens the sheet; More formats swaps it for the Found list.
        composeRule.onNodeWithTag("home-view-media").performClick()
        composeRule.onNodeWithTag("sheet-more-formats").performClick()
        composeRule.onNodeWithTag("modal-sheet").assertDoesNotExist()
        composeRule.onNodeWithText(YftDestination.DETECTED_MEDIA.title).assertIsDisplayed()
        assertEquals(
            YftDestination.DETECTED_MEDIA.route,
            shellNavController.currentDestination?.route,
        )
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun downloadAsRisesAsASheetOverThePageThatOpenedIt() {
        setShell()
        composeRule.onNodeWithTag("home-link").performTextInput("https://a.test/found")
        composeRule.onNodeWithTag("home-open-link").performClick()
        composeRule.onNodeWithTag("home-view-media").performClick()

        composeRule.runOnUiThread { shellNavController.navigate(YftDestination.PREVIEW.route) }

        composeRule.onNodeWithTag("modal-sheet").assertExists()
        composeRule.onNodeWithText(YftDestination.PREVIEW.summary).assertIsDisplayed()
        // The page stays composed under the sheet and the bar stays hidden.
        composeRule.onNodeWithText(YftDestination.DETECTED_MEDIA.title).assertExists()
        composeRule.onNodeWithTag("nav-home").assertDoesNotExist()

        composeRule.onNodeWithTag("sheet-close").performClick()

        composeRule.onNodeWithTag("modal-sheet").assertDoesNotExist()
        composeRule.onNodeWithText(YftDestination.DETECTED_MEDIA.title).assertIsDisplayed()
        assertEquals(
            YftDestination.DETECTED_MEDIA.route,
            shellNavController.currentDestination?.route,
        )

        composeRule.runOnUiThread { shellNavController.navigate(YftDestination.PREVIEW.route) }
        composeRule.onNodeWithTag("sheet-open-downloads").performClick()

        composeRule.onNodeWithTag("modal-sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-downloads").assertIsSelected()
        assertEquals(YftDestination.DOWNLOADS.route, shellNavController.currentDestination?.route)
    }

    @Test
    fun recentSeeAllOpensTheLibraryTab() {
        setShell()

        composeRule.onNodeWithTag("home-list")
            .performScrollToNode(hasTestTag("home-recent-all"))
        composeRule.onNodeWithTag("home-recent-all").performClick()

        screenTitle(YftDestination.LIBRARY).assertIsDisplayed()
        composeRule.onNodeWithTag("nav-library").assertIsSelected()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("nav-home").assertIsSelected()
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
        val home = homeViewModel()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                YftNavHost(
                    navController = rememberNavController(),
                    themeMode = ThemeMode.LIGHT,
                    onThemeModeChanged = {},
                    homeContent = { onOpenBrowser, onOpenDetectedMedia, onOpenLibrary, _, _ ->
                        HomeRoute(
                            onOpenBrowser = onOpenBrowser,
                            onOpenDetectedMedia = onOpenDetectedMedia,
                            onOpenLibrary = onOpenLibrary,
                            viewModel = home,
                        )
                    },
                    browserContent = { onNavigateBack, _, link, _, _, _ ->
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
        composeRule.onNodeWithTag("home-open-in-browser").performClick()

        composeRule.onNodeWithText("link=$link").assertIsDisplayed()
        assertEquals(link, received.last())
        composeRule.onNodeWithTag("navigate-back").performClick()
        composeRule.onNodeWithTag("home-not-found").performClick()
        composeRule.onNodeWithTag("home-link-clear").performClick()
        composeRule.onNodeWithTag("home-open-browser").performClick()
        composeRule.onNodeWithText("link=null").assertIsDisplayed()
    }

    /** The bar label repeats each tab's title, so match the screen's own title only. */
    private fun screenTitle(destination: YftDestination) = composeRule.onNode(
        hasText(destination.title) and !hasTestTag("nav-${destination.route}"),
    )

    private fun stateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun setShell(
        activeDownloads: () -> Int = { 0 },
        miniPlayer: @Composable () -> Unit = {},
    ) {
        val home = homeViewModel()
        composeRule.setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            YftTheme(themeMode = themeMode) {
                val navController = rememberNavController().also { shellNavController = it }
                YftAppShell(
                    navController = navController,
                    activeDownloads = activeDownloads(),
                    miniPlayer = miniPlayer,
                ) {
                    TestNavHost(
                        navController = navController,
                        home = home,
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
    home: HomeViewModel,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier,
) {
    YftNavHost(
        navController = navController,
        themeMode = themeMode,
        onThemeModeChanged = onThemeModeChanged,
        modifier = modifier,
        homeContent = { onOpenBrowser, onOpenDetectedMedia, onOpenLibrary, onOpenQuick, onSearch ->
            HomeRoute(
                onOpenBrowser = onOpenBrowser,
                onOpenDetectedMedia = onOpenDetectedMedia,
                onOpenLibrary = onOpenLibrary,
                onOpenQuickDownload = onOpenQuick,
                onOpenSearch = onSearch,
                viewModel = home,
            )
        },
        browserContent = { onNavigateBack, _, _, _, searchMode, onDownloadLink ->
            Column {
                if (searchMode) Text(text = "search mode")
                TextButton(
                    onClick = { onDownloadLink("https://a.test/copied") },
                    modifier = Modifier.testTag("browser-copied-download"),
                ) { Text(text = "Download") }
                Placeholder(YftDestination.BROWSER, onNavigateBack)
            }
        },
        quickDownloadContent = { onNavigateBack, _, onMoreFormats ->
            Column {
                Text(text = YftDestination.QUICK_DOWNLOAD.summary)
                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("sheet-close"),
                ) { Text(text = "Close") }
                TextButton(
                    onClick = { onMoreFormats(MoreFormatsTarget.FOUND_LIST) },
                    modifier = Modifier.testTag("sheet-more-formats"),
                ) { Text(text = "More formats") }
            }
        },
        detectedMediaContent = { onNavigateBack, _, _ ->
            DetectedMediaScreen(page = null, onNavigateBack = onNavigateBack)
        },
        previewContent = { onNavigateBack, onOpenDownloads ->
            Column {
                Text(text = YftDestination.PREVIEW.summary)
                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("sheet-close"),
                ) { Text(text = "Close") }
                TextButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier.testTag("sheet-open-downloads"),
                ) { Text(text = "View downloads") }
            }
        },
        downloadsContent = { _, _ ->
            Placeholder(YftDestination.DOWNLOADS, onNavigateBack = {})
        },
        libraryContent = { onOpenPlayer ->
            LibraryScreen(
                uiState = LibraryUiState.Ready(items = listOf(SAVED_VIDEO)),
                onPlay = { onOpenPlayer() },
            )
        },
        playerContent = { onClose -> Placeholder(YftDestination.PLAYER, onClose) },
        settingsContent = { onOpenAbout, onOpenLicenses ->
            SettingsScreen(
                state = SettingsUiState(),
                themeMode = themeMode,
                sharedDownloadsSupported = true,
                onAction = {},
                onThemeModeChanged = onThemeModeChanged,
                onOpenAbout = onOpenAbout,
                onOpenLicenses = onOpenLicenses,
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

/**
 * Home with fakes: links containing "found" have two files, "copied" one video (which opens
 * Video you copied), anything else has none, the
 * library is empty and the default sites are shown.
 */
private fun homeViewModel(): HomeViewModel = HomeViewModel(
    inspector = { link ->
        if ("found" in link || "copied" in link) {
            val files = if ("copied" in link) listOf("clip") else listOf("clip", "other")
            LinkInspection.Found(
                pageUrl = link,
                pageTitle = "Fixture page",
                candidates = files.map { name ->
                    MediaCandidate(
                        pageUrl = link,
                        mediaUrl = "https://cdn.a.test/$name.mp4",
                        sources = setOf(CandidateSource.DOM),
                        kind = MediaKind.DIRECT,
                    )
                },
            )
        } else {
            LinkInspection.NotFound(PromptboxStatus.NO_MEDIA_MESSAGE)
        }
    },
    sitesRepository = object : HomeSitesRepository {
        override val sites = MutableStateFlow(HomeSites.DEFAULTS)

        override suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>) {
            sites.value = transform(sites.value)
        }
    },
    library = object : LibraryRepository {
        override suspend fun items(): List<LibraryItem> = emptyList()

        override suspend fun delete(item: LibraryItem): Boolean = false
    },
    detectedMediaStore = DetectedMediaStore(),
    settings = object : SettingsRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val checkCopiedLinks = MutableStateFlow(false)

        override suspend fun setThemeMode(themeMode: ThemeMode) = Unit

        override suspend fun setCheckCopiedLinks(enabled: Boolean) = Unit
    },
    copiedLinks = CopiedLinkWatcher(
        object : ClipboardAccess {
            override fun peek(): ClipPeek? = null

            override fun readText(): CharSequence? = null
        },
    ),
)

private val SAVED_VIDEO = LibraryItem(
    id = "media:1",
    displayName = "Clip.mp4",
    uri = "content://media/external_primary/downloads/1",
    mimeType = "video/mp4",
    sizeBytes = 2_097_152,
    modifiedAtEpochMs = 1L,
    location = LibraryLocation.SHARED_DOWNLOADS,
)
