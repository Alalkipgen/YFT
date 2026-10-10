package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.SearchEngine
import com.alal.yft.ui.components.ALLOWED_MEDIA_NOTE
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Native graphics: the sheet is clipped to a shape with only its top corners rounded, and the
 * legacy Robolectric canvas cannot hit-test such a path, so taps on it would never arrive.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BrowserScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sheetPeeksWithSavableCountExpandsAndPreviewsTheTappedMedia() {
        var selected: MediaGroup? = null
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = listOf(
                    stream(),
                    stream().copy(title = "Protected clip", drmHint = true),
                ),
            ),
            onDownloadGroup = { selected = it },
        )

        // Collapsed: only the peek header, counting the one savable item.
        composeRule.onNodeWithTag("media-found-button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Found on this page, 1 item").assertIsDisplayed()
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)

        composeRule.onNodeWithTag("media-found-button").performClick()
        composeRule.onNodeWithText("Fixture stream").assertIsDisplayed()
        composeRule.onNodeWithText("HLS").assertIsDisplayed()
        composeRule.onNodeWithText("Auto quality").assertIsDisplayed()
        composeRule.onNodeWithText(ALLOWED_MEDIA_NOTE).assertIsDisplayed()
        composeRule.onNodeWithTag("found-protected-note").assertIsDisplayed()
        composeRule.onAllNodesWithText("Protected clip").assertCountEquals(0)
        composeRule.onAllNodesWithText("token", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("found-preview-0")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals("Fixture stream", selected?.title) }
        composeRule.runOnIdle { assertEquals(1, selected?.candidates?.size) }
    }

    @Test
    fun tappingTheScrimCollapsesTheSheet() {
        setScreen(
            uiState = BrowserUiState(currentUrl = PAGE, candidates = listOf(stream())),
            initialSheetExpanded = true,
        )

        composeRule.onNodeWithTag("found-list").assertIsDisplayed()
        composeRule.onNodeWithTag("found-sheet-scrim").performClick()
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)
        composeRule.onNodeWithTag("media-found-button").assertIsDisplayed()
    }

    @Test
    fun draggingTheHeaderOpensAndClosesTheSheet() {
        setScreen(uiState = BrowserUiState(currentUrl = PAGE, candidates = listOf(stream())))

        composeRule.onNodeWithTag("media-found-button").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("found-list").assertIsDisplayed()
        composeRule.onNodeWithTag("media-found-button").performTouchInput { swipeDown() }
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)
    }

    @Test
    fun withoutAPageStartPageDoesNotCreateBrowserSurface() {
        setScreen(uiState = BrowserUiState())

        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)

        composeRule.onAllNodesWithTag("browser-surface").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-start").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-address").assertIsDisplayed()
        composeRule.onAllNodesWithTag("media-found-button").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-history-back").assertIsNotEnabled()
        composeRule.onNodeWithTag("browser-reload-stop").assertIsNotEnabled()
    }

    @Test
    fun copiedLinkIsAnExplicitActionAndNeverAPreviewOrAutomaticNavigation() {
        var pasted = 0
        setScreen(
            uiState = BrowserUiState(),
            copiedLinkHint = true,
            onUseCopiedLink = { pasted++ },
        )

        composeRule.onNodeWithText("Link you copied").assertIsDisplayed()
        composeRule.onNodeWithText("Use copied link").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, pasted) }
        composeRule.onAllNodesWithTag("browser-surface").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-use-copied-link").performClick()
        composeRule.runOnIdle { assertEquals(1, pasted) }
    }

    @Test
    fun viewSitesOffersThePopularSitesAndViewAllTheSavedOnes() {
        val savedSite = HomeSite("My site", "https://example.test")
        var selected: HomeSite? = null
        var home = 0
        setScreen(
            uiState = BrowserUiState(sites = listOf(savedSite)),
            onOpenSite = { selected = it },
            onGoHome = { home++ },
        )

        composeRule.onNodeWithText("Search to download").assertIsDisplayed()
        composeRule.onNodeWithText("View sites").performScrollTo().assertIsDisplayed()
        VIEW_SITES.forEach { site ->
            composeRule.onNodeWithTag("browser-site-${site.url}").performScrollTo()
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag("browser-site-https://x.com").performClick()
        composeRule.runOnIdle { assertEquals(VIEW_SITES.last(), selected) }
        composeRule.onAllNodesWithTag("browser-site-${savedSite.url}").assertCountEquals(0)

        composeRule.onNodeWithTag("browser-sites-all").performScrollTo().performClick()
        composeRule.onNodeWithText("Your sites").assertIsDisplayed()
        composeRule.onAllNodesWithText("Archive").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-site-${savedSite.url}").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(savedSite, selected) }
        composeRule.onNodeWithTag("browser-edit-sites").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, home) }
    }

    @Test
    fun downloadButtonOpensTheSheetForOneVideoAndHidesWhileTheSheetIsOpen() {
        val opened = mutableListOf<MediaGroup>()
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = listOf(clip()),
            ),
            onDownloadGroup = { opened += it },
        )

        // P13: one video gets the wide button under the page instead of the round one.
        composeRule.onNodeWithContentDescription("Download this video").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
        composeRule.onAllNodesWithTag("browser-download-fab-badge").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-download-wide").performClick()
        composeRule.runOnIdle { assertEquals(listOf(listOf(clip())), opened.map { it.candidates }) }

        composeRule.onNodeWithTag("media-found-button").performClick()
        composeRule.onNodeWithTag("found-list").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
        composeRule.onAllNodesWithTag("browser-download-wide").assertCountEquals(0)
    }

    @Test
    fun theQualitiesOfOneVideoAreOneRowAndTheButtonOpensThemTogether() {
        // Facebook's HD, SD and DASH items of one reel (P3 regression: three rows).
        val id = "facebook:1603698891196107"
        val items = listOf(
            clip().copy(title = "Reel — HD", videoId = id),
            clip().copy(mediaUrl = "https://cdn.test/sd.mp4", title = "Reel — SD", videoId = id),
            stream().copy(title = "Reel — DASH", kind = MediaKind.DASH, videoId = id),
        )
        val opened = mutableListOf<MediaGroup>()
        setScreen(
            uiState = BrowserUiState(address = PAGE, currentUrl = PAGE, candidates = items),
            onDownloadGroup = { opened += it },
        )

        composeRule.onNodeWithContentDescription("Download this video").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-download-wide").performClick()
        composeRule.runOnIdle { assertEquals(items, opened.single().candidates) }

        composeRule.onNodeWithTag("media-found-button").performClick()
        composeRule.onNodeWithContentDescription("Found on this page, 1 item").assertExists()
        composeRule.onNodeWithTag("found-item-0").assertIsDisplayed()
        composeRule.onAllNodesWithTag("found-item-1").assertCountEquals(0)
        composeRule.onNodeWithText("Reel").assertIsDisplayed()
        composeRule.onNodeWithText("Several qualities").assertIsDisplayed()
        composeRule.onNodeWithTag("found-preview-0")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(2, opened.size) }
    }

    @Test
    fun aFacebookPostsVideoOpensTheSheetAndThePlayersOwnFilesAreNotMoreVideos() {
        // P3-FIX regression: story.php listed the player's track files as 26 "Video file" rows
        // and the button opened that list instead of the video's sheet.
        val id = "facebook:post:1234567890123456"
        val video = listOf(
            clip().copy(title = "Post — HD", videoId = id),
            clip().copy(mediaUrl = "https://cdn.test/sd.mp4", title = "Post — SD", videoId = id),
        )
        val played = listOf(
            clip().copy(mediaUrl = "https://cdn.test/v/track-1.mp4", title = null),
            clip().copy(mediaUrl = "https://cdn.test/v/track-2.mp4", title = null),
        )
        val opened = mutableListOf<MediaGroup>()
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = played + video,
            ),
            onDownloadGroup = { opened += it },
        )

        composeRule.onNodeWithContentDescription("Download this video").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-download-wide").performClick()
        composeRule.runOnIdle { assertEquals(video, opened.single().candidates) }
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)
    }

    @Test
    fun severalItemsShowACountBadgeAndOpenTheMainVideoWithTheOthersOneRowAway() {
        var quick = 0
        var main = 0
        var state by mutableStateOf(
            BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = listOf(clip(), stream()),
            ),
        )
        setScreen(
            uiStateProvider = { state },
            onDownloadGroup = { quick++ },
            onDownloadMain = { main++ },
        )

        composeRule.onNodeWithContentDescription("Download video, 2 found").assertIsDisplayed()
        // Visual only: the count is already in the button's label.
        composeRule.onNodeWithTag("browser-download-fab-badge", useUnmergedTree = true)
            .assertExists()
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)
        // P12: the main video's sheet, not the found list.
        composeRule.onNodeWithTag("browser-download-fab").performClick()
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, main)
            assertEquals(0, quick)
        }
    }

    @Test
    fun aPagesPreviewsAreNotCountedAndFollowItsVideoUnderOtherVideos() {
        // P24: one stream and three preview clips: the button and the count say one video.
        var main = 0
        val previews = (1..3).map { number ->
            clip().copy(
                mediaUrl = "https://cdn.test/previews/$number.mp4",
                title = "Preview $number",
                pageRole = PageMediaRole.PREVIEW,
            )
        }
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = previews.take(2) + stream() + previews.drop(2),
            ),
            onDownloadMain = { main++ },
        )

        composeRule.onNodeWithContentDescription("Download video, 1 found").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-download-fab-badge", useUnmergedTree = true)
            .assertCountEquals(0)
        // Every entry still counts for the tap: the main video's sheet, the others behind it.
        composeRule.onNodeWithTag("browser-download-fab").performClick()
        composeRule.runOnIdle { assertEquals(1, main) }

        composeRule.onNodeWithTag("media-found-button").performClick()
        rowShows("found-item-0", "Fixture stream")
        composeRule.onNodeWithTag("found-list")
            .performScrollToNode(hasTestTag("found-other-videos"))
        composeRule.onNodeWithTag("found-other-videos")
            .assert(hasText("Other videos on this page (3)"))
        rowShows("found-item-1", "Preview 1")
        composeRule.onNodeWithTag("found-list").performScrollToNode(hasTestTag("found-item-3"))
        rowShows("found-item-3", "Preview 3")
    }

    private fun rowShows(tag: String, text: String) {
        composeRule.onNode(
            hasAnyAncestor(hasTestTag(tag)) and hasText(text),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun aSiteVideoPageButtonMeansThisVideoAndSpinsWhileItsLookupRuns() {
        var page = 0
        var main = 0
        var state by mutableStateOf(
            BrowserUiState(
                address = WATCH,
                currentUrl = WATCH,
                findsFocusedVideo = true,
                sitePage = true,
                pageLookupRunning = true,
            ),
        )
        setScreen(
            uiStateProvider = { state },
            onDownloadPage = { page++ },
            onDownloadMain = { main++ },
        )

        // Nothing found yet: the (wide, P13) button is there, says "this video" and spins.
        composeRule.onNodeWithContentDescription("Download this video").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-download-wide-spinner", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag("browser-download-wide").performClick()
        composeRule.runOnIdle { assertEquals(1, page) }
        // The player's own files are not more videos of this page: no badge, no list.
        state = state.copy(pageLookupRunning = false, candidates = listOf(clip(), stream()))
        composeRule.onAllNodesWithTag("browser-download-wide-spinner", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("browser-download-fab-badge", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("browser-download-wide").performClick()
        composeRule.onAllNodesWithTag("found-list").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(2, page)
            assertEquals(0, main)
        }
    }

    @Test
    fun theWideButtonSitsUnderThePageAndOnlyOneButtonShowsAtATime() {
        var page = 0
        var main = 0
        val opened = mutableListOf<MediaGroup>()
        var fullScreen by mutableStateOf(false)
        var sheetOpen by mutableStateOf(false)
        var state by mutableStateOf(
            BrowserUiState(
                address = WATCH,
                currentUrl = WATCH,
                findsFocusedVideo = true,
                sitePage = true,
            ),
        )
        setScreen(
            uiStateProvider = { state },
            onDownloadPage = { page++ },
            onDownloadMain = { main++ },
            onDownloadGroup = { opened += it },
            fullScreenProvider = { fullScreen },
            downloadSheetOpenProvider = { sheetOpen },
        )

        // A site's video page: the wide button, which does what the round one did (P12).
        composeRule.onNodeWithTag("browser-download-wide")
            .assertIsDisplayed()
            .assert(hasContentDescription("Download this video"))
            .performClick()
        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, page) }
        // The page ends where the button starts, so the site's own controls stay reachable.
        val pageBottom = composeRule.onNodeWithTag("test-page").getBoundsInRoot().bottom
        val wideTop = composeRule.onNodeWithTag("browser-download-wide").getBoundsInRoot().top
        assertTrue("page $pageBottom, button $wideTop", pageBottom <= wideTop)

        // Full screen and a download sheet over the browser hide it.
        fullScreen = true
        composeRule.onAllNodesWithTag("browser-download-wide").assertCountEquals(0)
        fullScreen = false
        sheetOpen = true
        composeRule.onAllNodesWithTag("browser-download-wide").assertCountEquals(0)
        sheetOpen = false
        composeRule.onNodeWithTag("browser-download-wide").assertIsDisplayed()

        // A feed keeps the round button.
        state = BrowserUiState(
            address = FEED,
            currentUrl = FEED,
            findsFocusedVideo = true,
            feedPage = true,
        )
        composeRule.onNodeWithTag("browser-download-fab").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-download-wide").assertCountEquals(0)
        // A page with one video: the wide button opens it.
        state = BrowserUiState(address = PAGE, currentUrl = PAGE, candidates = listOf(clip()))
        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-download-wide").performClick()
        composeRule.runOnIdle { assertEquals(listOf(clip()), opened.single().candidates) }
        // Several videos: the round button with its count.
        state = state.copy(candidates = listOf(clip(), stream()))
        composeRule.onAllNodesWithTag("browser-download-wide").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-download-fab").performClick()
        composeRule.runOnIdle { assertEquals(1, main) }
        // An open found list hides both.
        state = state.copy(candidates = listOf(clip()))
        composeRule.onNodeWithTag("media-found-button").performClick()
        composeRule.onAllNodesWithTag("browser-download-wide").assertCountEquals(0)
        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
    }

    @Test
    fun aFeedShowsTheButtonWithNothingFoundAndATapLooksForTheVideoOnScreen() {
        var focused = 0
        val opened = mutableListOf<MediaGroup>()
        var state by mutableStateOf(
            BrowserUiState(
                address = FEED,
                currentUrl = FEED,
                findsFocusedVideo = true,
                feedPage = true,
            ),
        )
        setScreen(
            uiStateProvider = { state },
            onDownloadGroup = { opened += it },
            onDownloadFocused = { focused += 1 },
        )

        composeRule.onNodeWithContentDescription("Download the video on screen").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-download-fab").performClick()
        composeRule.runOnIdle { assertEquals(1, focused) }

        // A feed's own finds may be any video's: the tap still looks for the one on screen.
        state = state.copy(candidates = listOf(clip(), stream()))
        composeRule.onAllNodesWithTag("browser-download-fab-badge", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("browser-download-fab").performClick()
        composeRule.runOnIdle {
            assertEquals(2, focused)
            assertTrue(opened.isEmpty())
        }

        // While it looks, another tap waits; the notice says what is happening.
        state = state.copy(findingFocusedVideo = true, focusNotice = "Finding the video on screen…")
        composeRule.onNodeWithTag("browser-focus-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-download-fab").performClick()
        composeRule.runOnIdle { assertEquals(2, focused) }
    }

    @Test
    fun noFocusedVideoNoticeShowsWithoutAButtonOnOtherSites() {
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                focusNotice = "No video on screen to download.",
            ),
        )

        composeRule.onNodeWithTag("browser-focus-notice").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
    }

    @Test
    fun noDownloadButtonForDrmOnlyPagesOrTheStartPage() {
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = listOf(clip().copy(drmHint = true)),
            ),
        )

        composeRule.onAllNodesWithTag("browser-download-fab").assertCountEquals(0)
    }

    @Test
    fun wordsOfferYouTubeAndGoogleSearchRows() {
        val searched = mutableListOf<String>()
        setScreen(uiState = BrowserUiState(address = "cat videos"), onSearch = { searched += it })

        composeRule.onNodeWithText("Search YouTube for “cat videos”").assertIsDisplayed()
        // P30: the web search row names its engine, Google unless Settings › Browser says else.
        composeRule.onNodeWithText("Search Google for “cat videos”").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-search-youtube").performClick()
        composeRule.onNodeWithTag("browser-search-web").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "https://m.youtube.com/results?search_query=cat+videos",
                    "https://www.google.com/search?q=cat+videos",
                ),
                searched,
            )
        }
    }

    @Test
    fun theWebSearchRowNamesAndSearchesTheChosenEngine() {
        val searched = mutableListOf<String>()
        setScreen(
            uiState = BrowserUiState(address = "cats & dogs"),
            onSearch = { searched += it },
            searchEngine = SearchEngine.DUCKDUCKGO,
        )

        composeRule.onNodeWithText("Search DuckDuckGo for “cats & dogs”").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-search-web").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("https://duckduckgo.com/?q=cats+%26+dogs"), searched)
        }
    }

    @Test
    fun aTypedLinkOffersNoSearchRows() {
        setScreen(uiState = BrowserUiState(address = "m.youtube.com/watch?v=1"))

        composeRule.onNodeWithTag("browser-start").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-search-rows").assertCountEquals(0)
    }

    @Test
    fun searchModeFocusesTheFieldAndDownloadReadsTheCopiedLinkOnlyOnTap() {
        var downloads = 0
        setScreen(
            uiState = BrowserUiState(),
            copiedLinkHint = true,
            searchMode = true,
            onDownloadCopiedLink = { downloads++ },
        )

        composeRule.onNodeWithTag("browser-address").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, downloads) }
        composeRule.onNodeWithTag("browser-copied-download").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, downloads) }
    }

    @Test
    fun withoutSearchModeTheAddressFieldWaitsForATap() {
        setScreen(uiState = BrowserUiState())

        composeRule.onNodeWithTag("browser-address").assertIsNotFocused()
    }

    @Test
    fun loadedPageKeepsAddressAndCloseAboveTheBrowserSurface() {
        setScreen(uiState = BrowserUiState(address = PAGE, currentUrl = PAGE))

        composeRule.onAllNodesWithTag("browser-start").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        val address = composeRule.onNodeWithTag("browser-address").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val surface = composeRule.onNodeWithTag("browser-surface").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The page must stay below the address bar", address.bottom <= surface.top)
    }

    @Test
    fun largestTextKeepsTheCountBadgeInsideTheSheet() {
        setScreen(
            uiState = BrowserUiState(currentUrl = PAGE, candidates = listOf(stream())),
            initialSheetExpanded = true,
            fontScale = 2f,
        )

        val badge = composeRule.onNodeWithTag("found-count", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val sheet = composeRule.onNodeWithTag("found-sheet").fetchSemanticsNode().boundsInRoot
        val minimum = with(composeRule.density) { 34.dp.toPx() }
        assertTrue("Count badge needs its circle plus gap, got $badge", badge.width >= minimum)
        assertTrue("Count must stay inside the sheet", badge.right <= sheet.right)
    }

    @Test
    fun protectedOnlyPagesExplainInsteadOfOfferingMedia() {
        setScreen(
            uiState = BrowserUiState(
                currentUrl = PAGE,
                candidates = listOf(stream().copy(drmHint = true)),
            ),
        )

        composeRule.onAllNodesWithTag("media-found-button").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-protected-notice").assertIsDisplayed()
    }

    @Test
    fun typingAnAddressShowsGoAndSubmits() {
        val typed = mutableListOf<String>()
        var went = false
        setScreen(
            uiState = BrowserUiState(),
            onAddressChanged = { typed += it },
            onGo = { went = true },
        )

        composeRule.onNodeWithTag("browser-address").performTextInput("archive.org")
        composeRule.runOnIdle { assertEquals("archive.org", typed.last()) }
        composeRule.onNodeWithTag("browser-go").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(went) }
    }

    @Test
    fun toolbarAndCloseReachTheirActions() {
        var back = 0
        var forward = 0
        var home = 0
        var closed = 0
        var reloaded = 0
        var stopped = 0
        setScreen(
            uiState = BrowserUiState(address = PAGE, currentUrl = PAGE),
            canGoBack = true,
            onBrowserBack = { back++ },
            onBrowserForward = { forward++ },
            onGoHome = { home++ },
            onNavigateBack = { closed++ },
            onReload = { reloaded++ },
            onStop = { stopped++ },
        )

        composeRule.onNodeWithTag("browser-history-back").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("browser-history-forward").assertIsNotEnabled()
        composeRule.onNodeWithTag("browser-toolbar-reload").performClick()
        composeRule.onNodeWithTag("browser-reload-stop").performClick()
        composeRule.onNodeWithContentDescription("YFT Home").performClick()
        composeRule.onNodeWithContentDescription("Close browser").performClick()
        composeRule.onNodeWithTag("browser-address").assertTextEquals(PAGE)
        composeRule.runOnIdle {
            assertEquals(1, back)
            assertEquals(0, forward)
            assertEquals(2, reloaded)
            assertEquals(0, stopped)
            assertEquals(1, home)
            assertEquals(1, closed)
        }
    }

    @Test
    fun siteNoticeOffersTryAgainOnlyWhenAskingAgainCanHelp() {
        var retried = 0
        var state by mutableStateOf(
            BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                siteNotice = "Fixture wants to check that this is not a bot.",
                canRetrySiteLookup = true,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserScreen(
                    uiState = state,
                    canGoBack = false,
                    canGoForward = false,
                    onAddressChanged = {},
                    onGo = {},
                    onBrowserBack = {},
                    onBrowserForward = {},
                    onReload = {},
                    onStop = {},
                    onDownloadGroup = {},
                    onNavigateBack = {},
                    onRetrySiteLookup = { retried += 1 },
                    browserSurface = { Box(modifier = it) },
                )
            }
        }

        composeRule.onNodeWithTag("browser-site-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-site-retry").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }

        state = state.copy(
            siteNotice = "This Fixture post is protected.",
            canRetrySiteLookup = false,
        )
        composeRule.onNodeWithTag("browser-site-notice").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-site-retry").assertCountEquals(0)
    }

    @Test
    fun tikToksCheckOffersShowCheckInsteadOfTryAgain() {
        var shown = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserScreen(
                    uiState = BrowserUiState(
                        address = PAGE,
                        currentUrl = PAGE,
                        siteNotice = SiteCheck.NOTICE,
                        canRetrySiteLookup = true,
                        siteCheckUrl = "https://www.tiktok.com/@fixture/video/7311234567890123456",
                    ),
                    canGoBack = false,
                    canGoForward = false,
                    onAddressChanged = {},
                    onGo = {},
                    onBrowserBack = {},
                    onBrowserForward = {},
                    onReload = {},
                    onStop = {},
                    onDownloadGroup = {},
                    onNavigateBack = {},
                    onShowSiteCheck = { shown += 1 },
                    browserSurface = { Box(modifier = it) },
                )
            }
        }

        composeRule.onNodeWithTag("browser-site-check").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, shown) }
        composeRule.onAllNodesWithTag("browser-site-retry").assertCountEquals(0)
    }

    @Test
    fun addressDisplayShowsHostAndPathButNeverTheQuery() {
        val display =
            addressDisplay("https://www.archive.org/details/ocean-waves?token=secret#t=1")!!
        assertEquals("archive.org", display.host)
        assertEquals("/details/ocean-waves", display.path)
        assertTrue(display.secure)

        assertEquals("", addressDisplay("https://example.test/")!!.path)
        assertEquals("example.test:8443", addressDisplay("https://example.test:8443/a")!!.host)
        assertNull(addressDisplay("archive.org"))
        assertNull(addressDisplay("about:blank"))
        assertNull(addressDisplay(""))
    }

    @Test
    fun aFocusedNetworkErrorHasRetryBesideDownloadButNoTopBanner() {
        var retries = 0
        setScreen(
            uiState = BrowserUiState(
                currentUrl = "https://m.youtube.com/",
                findsFocusedVideo = true,
                feedPage = true,
                focusNotice = "Couldn't reach YouTube.",
                canRetryFocusedLookup = true,
            ),
            onRetryFocusedLookup = { retries += 1 },
        )
        composeRule.onAllNodesWithTag("browser-site-notice").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-focus-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-focus-retry").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    private fun setScreen(
        uiState: BrowserUiState = BrowserUiState(),
        uiStateProvider: (() -> BrowserUiState)? = null,
        onDownloadFocused: () -> Unit = {},
        onRetryFocusedLookup: () -> Unit = {},
        canGoBack: Boolean = false,
        onAddressChanged: (String) -> Unit = {},
        onGo: () -> Unit = {},
        onBrowserBack: () -> Unit = {},
        onBrowserForward: () -> Unit = {},
        onReload: () -> Unit = {},
        onStop: () -> Unit = {},
        onDownloadGroup: (MediaGroup) -> Unit = {},
        onNavigateBack: () -> Unit = {},
        onGoHome: () -> Unit = {},
        initialSheetExpanded: Boolean = false,
        copiedLinkHint: Boolean = false,
        onUseCopiedLink: () -> Unit = {},
        onOpenSite: (HomeSite) -> Unit = {},
        fontScale: Float = 1f,
        searchMode: Boolean = false,
        onSearch: (String) -> Unit = {},
        onDownloadCopiedLink: () -> Unit = {},
        onDownloadPage: () -> Unit = {},
        onDownloadMain: () -> Unit = {},
        fullScreenProvider: () -> Boolean = { false },
        downloadSheetOpenProvider: () -> Boolean = { false },
        searchEngine: SearchEngine = SearchEngine.GOOGLE,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                YftTheme(themeMode = ThemeMode.LIGHT) {
                    BrowserScreen(
                        uiState = uiStateProvider?.invoke() ?: uiState,
                        canGoBack = canGoBack,
                        canGoForward = false,
                        onAddressChanged = onAddressChanged,
                        onGo = onGo,
                        onBrowserBack = onBrowserBack,
                        onBrowserForward = onBrowserForward,
                        onReload = onReload,
                        onStop = onStop,
                        onDownloadGroup = onDownloadGroup,
                        onNavigateBack = onNavigateBack,
                        onGoHome = onGoHome,
                        initialSheetExpanded = initialSheetExpanded,
                        copiedLinkHint = copiedLinkHint,
                        onUseCopiedLink = onUseCopiedLink,
                        onOpenSite = onOpenSite,
                        searchMode = searchMode,
                        onSearch = onSearch,
                        onDownloadCopiedLink = onDownloadCopiedLink,
                        onDownloadFocused = onDownloadFocused,
                        onRetryFocusedLookup = onRetryFocusedLookup,
                        onDownloadPage = onDownloadPage,
                        onDownloadMain = onDownloadMain,
                        fullScreen = fullScreenProvider(),
                        downloadSheetOpen = downloadSheetOpenProvider(),
                        searchEngine = searchEngine,
                        browserSurface = { Box(modifier = it.testTag("test-page")) },
                    )
                }
            }
        }
    }

    private fun clip() = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://cdn.test/clip.mp4",
        sources = setOf(CandidateSource.DOM),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        title = "Fixture clip",
        confidence = CandidateConfidence.HIGH,
        observedAtEpochMs = 1,
    )

    private fun stream() = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://cdn.test/master.m3u8?token=test-only",
        sources = setOf(CandidateSource.REQUEST, CandidateSource.MANIFEST),
        kind = MediaKind.HLS,
        mimeType = "application/vnd.apple.mpegurl",
        title = "Fixture stream",
        confidence = CandidateConfidence.HIGH,
        observedAtEpochMs = 1,
    )

    private companion object {
        const val PAGE = "https://example.test/watch"
        const val FEED = "https://m.youtube.com/"
        const val WATCH = "https://m.youtube.com/watch?v=abcdefghijk"
    }
}
