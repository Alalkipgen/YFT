package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.HomeSite
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
        var selected: MediaCandidate? = null
        setScreen(
            uiState = BrowserUiState(
                address = PAGE,
                currentUrl = PAGE,
                candidates = listOf(
                    stream(),
                    stream().copy(title = "Protected clip", drmHint = true),
                ),
            ),
            onPreviewCandidate = { selected = it },
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
    fun startPageUsesSavedSitesInsteadOfASeparateHardcodedList() {
        val savedSite = HomeSite("My site", "https://example.test")
        var selected: HomeSite? = null
        setScreen(
            uiState = BrowserUiState(sites = listOf(savedSite)),
            onOpenSite = { selected = it },
        )

        composeRule.onAllNodesWithText("Archive").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-site-${savedSite.url}").performClick()
        composeRule.runOnIdle { assertEquals(savedSite, selected) }
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

    private fun setScreen(
        uiState: BrowserUiState,
        canGoBack: Boolean = false,
        onAddressChanged: (String) -> Unit = {},
        onGo: () -> Unit = {},
        onBrowserBack: () -> Unit = {},
        onBrowserForward: () -> Unit = {},
        onReload: () -> Unit = {},
        onStop: () -> Unit = {},
        onPreviewCandidate: (MediaCandidate) -> Unit = {},
        onNavigateBack: () -> Unit = {},
        onGoHome: () -> Unit = {},
        initialSheetExpanded: Boolean = false,
        copiedLinkHint: Boolean = false,
        onUseCopiedLink: () -> Unit = {},
        onOpenSite: (HomeSite) -> Unit = {},
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                YftTheme(themeMode = ThemeMode.LIGHT) {
                    BrowserScreen(
                        uiState = uiState,
                        canGoBack = canGoBack,
                        canGoForward = false,
                        onAddressChanged = onAddressChanged,
                        onGo = onGo,
                        onBrowserBack = onBrowserBack,
                        onBrowserForward = onBrowserForward,
                        onReload = onReload,
                        onStop = onStop,
                        onPreviewCandidate = onPreviewCandidate,
                        onNavigateBack = onNavigateBack,
                        onGoHome = onGoHome,
                        initialSheetExpanded = initialSheetExpanded,
                        copiedLinkHint = copiedLinkHint,
                        onUseCopiedLink = onUseCopiedLink,
                        onOpenSite = onOpenSite,
                        browserSurface = { Box(modifier = it) },
                    )
                }
            }
        }
    }

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
    }
}
