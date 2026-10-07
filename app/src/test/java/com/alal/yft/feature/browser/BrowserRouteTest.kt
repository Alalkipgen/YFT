package com.alal.yft.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.BrowserPreferences
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.core.model.settings.SearchEngine
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.alal.yft.ui.theme.YftTheme
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses a shadow WebView to verify creation/identity, not Chromium network behaviour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BrowserRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: BrowserViewModel
    private val browserPreferences = FakeBrowserPreferencesRepository()
    private val browserHistory = FakeBrowserHistoryRepository()

    @Before
    fun createViewModel() {
        viewModel = BrowserViewModel(
            OkHttpClient(),
            SiteAdapterCoordinator(SiteExtractorRegistry(emptyList())),
        )
        viewModelStore.put("browser", viewModel)
    }

    @After
    fun clearViewModel() {
        composeRule.runOnIdle { viewModelStore.clear() }
        BrowserSearch.engine = SearchEngine.GOOGLE
    }

    @Test
    fun typedWordsSearchGoogleByDefault() {
        showRoute()
        navigate("cat videos")

        composeRule.runOnIdle {
            assertEquals(
                "https://www.google.com/search?q=cat+videos",
                Shadows.shadowOf(webViews().single()).lastLoadedUrl,
            )
        }
    }

    @Test
    fun typedWordsAndTheStartPageSearchTheEngineSettingsChose() {
        browserPreferences.set(BrowserPreferences(searchEngine = SearchEngine.BING))
        showRoute()
        composeRule.onNodeWithTag("browser-address")
            .performClick()
            .performTextReplacement("cat videos")

        composeRule.onNodeWithText("Search Bing for “cat videos”").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-go").performClick()
        composeRule.runOnIdle {
            assertEquals(
                "https://www.bing.com/search?q=cat+videos",
                Shadows.shadowOf(webViews().single()).lastLoadedUrl,
            )
        }
    }

    @Test
    fun recentPagesAndTheHistoryListOpenTheirPageInTheBrowser() {
        browserHistory.pages.value = listOf(
            BrowserHistoryEntry(FIRST_PAGE, "First", "example.test", 2_000, 1),
            BrowserHistoryEntry(SECOND_PAGE, "Second", "example.test", 1_000, 3),
        )
        showRoute()

        composeRule.onNodeWithTag("browser-recent").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-recent-1").performClick()
        composeRule.runOnIdle {
            assertEquals(SECOND_PAGE, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }

        composeRule.onNodeWithTag("browser-menu").performClick()
        composeRule.onNodeWithTag("browser-menu-history").performClick()
        composeRule.onNodeWithTag("browser-history").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-history-page-$FIRST_PAGE").performClick()

        composeRule.onAllNodesWithTag("browser-history").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(FIRST_PAGE, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    @Test
    fun backClosesTheHistoryBeforeItLeavesThePage() {
        showRoute(initialLink = FIRST_PAGE)
        composeRule.onNodeWithTag("browser-menu").performClick()
        composeRule.onNodeWithTag("browser-menu-history").performClick()
        composeRule.onNodeWithTag("browser-history-empty").assertIsDisplayed()

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onAllNodesWithTag("browser-history").assertCountEquals(0)
        assertBrowserPage()
    }

    @Test
    fun aPageTheWebViewFinishedIsSavedUnlessSavingIsTurnedOff() {
        showRoute(initialLink = FIRST_PAGE)
        val client = composeRule.runOnIdle {
            Shadows.shadowOf(webViews().single()).webViewClient
        }

        composeRule.runOnIdle {
            val view = webViews().single()
            client.onPageStarted(view, "$FIRST_PAGE?utm_source=feed", null)
            client.onPageFinished(view, "$FIRST_PAGE?utm_source=feed")
        }
        composeRule.waitForIdle()
        assertEquals(listOf(FIRST_PAGE), browserHistory.pages.value.map { it.url })

        browserPreferences.set(BrowserPreferences(saveHistory = false))
        composeRule.runOnIdle {
            val view = webViews().single()
            client.onPageStarted(view, SECOND_PAGE, null)
            client.onPageFinished(view, SECOND_PAGE)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(FIRST_PAGE), browserHistory.pages.value.map { it.url })
        // The detection still saw both pages.
        assertEquals(SECOND_PAGE, viewModel.uiState.value.currentUrl)
    }

    @Test
    fun aBlockedPopUpShowsANoticeWhoseOpenLoadsItHereAndTheSwitchLetsItThrough() {
        showRoute(initialLink = FIRST_PAGE)
        val page = composeRule.runOnIdle { webViews().single() }
        val chrome = composeRule.runOnIdle { checkNotNull(page.webChromeClient) }

        composeRule.runOnIdle { openWindow(chrome, page, POP_UP) }

        composeRule.onNodeWithTag("browser-blocked-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-blocked-text", useUnmergedTree = true)
            .assertTextEquals("Pop-up blocked")
        composeRule.runOnIdle { assertEquals(FIRST_PAGE, Shadows.shadowOf(page).lastLoadedUrl) }
        composeRule.onNodeWithTag("browser-blocked-open").performClick()
        composeRule.onAllNodesWithTag("browser-blocked-notice").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(POP_UP, Shadows.shadowOf(page).lastLoadedUrl) }

        // Settings › Browser › Block pop-ups and ad redirects off: the window opens here.
        browserPreferences.set(BrowserPreferences(blockPopups = false))
        composeRule.waitForIdle()
        composeRule.runOnIdle { openWindow(chrome, page, SECOND_POP_UP) }
        composeRule.onAllNodesWithTag("browser-blocked-notice").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(SECOND_POP_UP, Shadows.shadowOf(page).lastLoadedUrl)
        }
    }

    @Test
    fun emptyRouteDoesNotCreateEvenAShadowWebView() {
        showRoute()

        assertStartPage()
        composeRule.runOnIdle { assertTrue(webViews().isEmpty()) }
    }

    @Test
    fun firstGoCreatesOneWebViewAndLaterNavigationAndRecompositionKeepIt() {
        showRoute()
        navigate("example.test/one")
        lateinit var first: WebView
        composeRule.runOnIdle {
            first = webViews().single()
            assertEquals(FIRST_PAGE, Shadows.shadowOf(first).lastLoadedUrl)
            viewModel.onPageStarted(FIRST_PAGE)
            viewModel.onProgressChanged(40)
            viewModel.onPageFinished(FIRST_PAGE, "Fixture page")
            viewModel.onMainFrameError(FIRST_PAGE, "ignored upstream detail")
        }
        assertBrowserPage()
        composeRule.runOnIdle { assertSame(first, webViews().single()) }

        navigate("example.test/two")
        assertBrowserPage()
        composeRule.runOnIdle {
            assertSame(first, webViews().single())
            assertEquals(SECOND_PAGE, Shadows.shadowOf(first).lastLoadedUrl)
        }
    }

    @Test
    fun theBrowserWebViewFillsItsBoxInsteadOfWrappingItsContent() {
        showRoute()
        navigate("example.test/one")

        // With wrap-content parameters a real WebView lays pages out with a zero viewport
        // height: Facebook's reel video got a 320x0 box (P2 emulator diagnostics).
        composeRule.runOnIdle {
            val params = webViews().single().layoutParams
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
        }
    }

    @Test
    fun homeLinkCanCreateAndLoadTheFirstWebViewWithoutAReadinessDeadlock() {
        showRoute(initialLink = "example.test/one")

        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(FIRST_PAGE, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    @Test
    fun recreatedRouteLoadsTheRetainedPageEvenWhenTheInitialLinkWasHandled() {
        composeRule.runOnIdle {
            viewModel.openInitialLink(FIRST_PAGE)
            viewModel.onPageStarted(FIRST_PAGE)
            viewModel.onPageFinished(FIRST_PAGE, "Retained page")
        }
        showRoute(initialLink = FIRST_PAGE)

        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(FIRST_PAGE, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    @Test
    fun invalidAddressShowsAnErrorWithoutCreatingWebView() {
        showRoute()
        navigate("http://example.test")

        assertStartPage()
        composeRule.onNodeWithTag("browser-error").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(webViews().isEmpty()) }
    }

    @Test
    fun initialBlankPageDoesNotCreateWebView() {
        showRoute(initialLink = "about:blank")

        assertStartPage()
        composeRule.runOnIdle { assertTrue(webViews().isEmpty()) }
    }

    @Test
    fun clipboardDescriptionOnlyChangesHintAndTapOpensTheFirstCopiedLink() {
        showRoute()
        composeRule.runOnIdle {
            val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText("copied", "Watch this: https://example.test/copied."),
            )
        }
        composeRule.onNodeWithText("Use copied link").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(webViews().isEmpty())
            assertEquals("", viewModel.uiState.value.address)
        }

        composeRule.onNodeWithTag("browser-use-copied-link").performClick()
        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(
                "https://example.test/copied",
                Shadows.shadowOf(webViews().single()).lastLoadedUrl,
            )
        }
    }

    @Test
    fun siteShortcutCreatesTheFirstWebView() {
        showRoute()
        val site = HomeSites.DEFAULTS.first().url
        composeRule.onNodeWithTag("browser-site-$site").performClick()

        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(site, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    @Test
    fun aPlayersFullScreenViewCoversTheBrowserUntilBackOrThePageLeavesIt() {
        showRoute()
        navigate("example.test/one")
        lateinit var chrome: WebChromeClient
        composeRule.runOnIdle { chrome = checkNotNull(webViews().single().webChromeClient) }
        var hidden = 0
        val player = View(composeRule.activity)

        composeRule.runOnIdle {
            chrome.onShowCustomView(player, WebChromeClient.CustomViewCallback { hidden++ })
        }
        composeRule.onNodeWithTag("browser-fullscreen").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(player.isAttachedToWindow) }

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onAllNodesWithTag("browser-fullscreen").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, hidden)
            assertTrue(!player.isAttachedToWindow)
        }
        assertBrowserPage()

        composeRule.runOnIdle {
            chrome.onShowCustomView(
                View(composeRule.activity),
                WebChromeClient.CustomViewCallback { hidden++ },
            )
        }
        composeRule.onNodeWithTag("browser-fullscreen").assertIsDisplayed()
        composeRule.runOnIdle { chrome.onHideCustomView() }
        composeRule.onAllNodesWithTag("browser-fullscreen").assertCountEquals(0)
        // The page left full screen itself, so it is not told again.
        composeRule.runOnIdle { assertEquals(1, hidden) }
    }

    @Test
    fun twoDownloadTapsDuringAWatchPagesLookupAskItsAdapterOnce() {
        val extractor = GatedWatchExtractor()
        viewModel = BrowserViewModel(
            OkHttpClient(),
            SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor))),
        )
        viewModelStore.put("browser", viewModel)
        var opened = 0
        showRoute(onOpenQuickDownload = { opened++ })
        navigate("m.youtube.com/watch?v=AAAAAAAAAA1")
        composeRule.runOnIdle {
            viewModel.onPageStarted(WATCH_PAGE)
            viewModel.onPageFinished(WATCH_PAGE, "Video A")
        }
        composeRule.waitUntil(5_000) { extractor.calls.get() == 1 }

        // P12: two taps while the page's own lookup runs. Whatever script a tap runs, the page
        // answers with this very video, as the player on screen would.
        var answered: ValueCallback<String>? = null
        repeat(2) {
            composeRule.onNodeWithTag("browser-download-wide").performClick()
            composeRule.runOnIdle {
                val shadow = Shadows.shadowOf(webViews().single())
                val callback = shadow.lastEvaluatedJavascriptCallback
                if (callback != null && callback !== answered) {
                    answered = callback
                    callback.onReceiveValue(focusedAnswer(WATCH_PAGE))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, extractor.calls.get())
            assertEquals(2, opened)
        }

        // The one lookup ends: the page has its video, and nobody asked twice.
        extractor.gate.complete(Unit)
        composeRule.waitUntil(5_000) {
            // The found list settles after a short pause on the main looper's clock.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            viewModel.uiState.value.candidates.isNotEmpty()
        }
        composeRule.runOnIdle { assertEquals(1, extractor.calls.get()) }
    }

    private fun focusedAnswer(url: String): String =
        JSONObject.quote(JSONObject().put("url", url).put("source", "centre").toString())

    /** A YouTube-shaped adapter whose answer waits for the test, counting its lookups. */
    private class GatedWatchExtractor : SiteExtractor {
        override val id: String = "youtube"
        override val displayName: String = "YouTube"
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()

        override fun identify(pageUrl: String): SitePageIdentity? {
            val videoId = WATCH.matchEntire(pageUrl)?.groupValues?.get(1) ?: return null
            return SitePageIdentity("youtube", videoId, "https://www.youtube.com/watch?v=$videoId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean = false

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            calls.incrementAndGet()
            withContext(NonCancellable) { gate.await() }
            val videoId = request.identity.contentId
            return SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = request.identity.canonicalPageUrl,
                        mediaUrl = "https://cdn.fixture.test/$videoId.mp4",
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                        mimeType = "video/mp4",
                    ),
                ),
            )
        }

        private companion object {
            val WATCH =
                Regex("https://(?:www|m)\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})(?:&.*)?")
        }
    }

    private fun showRoute(initialLink: String? = null, onOpenQuickDownload: () -> Unit = {}) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserRoute(
                    onNavigateBack = {},
                    initialLink = initialLink,
                    onOpenQuickDownload = onOpenQuickDownload,
                    viewModel = viewModel,
                    browserSettings = browserSettings(),
                    history = historyViewModel(),
                )
            }
        }
    }

    private fun browserSettings(): BrowserSettingsViewModel {
        val settings = BrowserSettingsViewModel(browserPreferences)
        viewModelStore.put("browser-settings", settings)
        return settings
    }

    private fun historyViewModel(): BrowserHistoryViewModel {
        val history = BrowserHistoryViewModel(browserHistory, browserPreferences)
        viewModelStore.put("browser-history", history)
        return history
    }

    private fun navigate(address: String) {
        composeRule.onNodeWithTag("browser-address")
            .performClick()
            .performTextReplacement(address)
        composeRule.onNodeWithTag("browser-go").performClick()
    }

    private fun assertStartPage() {
        composeRule.onNodeWithTag("browser-start").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-surface").assertCountEquals(0)
        assertTopControls()
    }

    private fun assertBrowserPage() {
        composeRule.onAllNodesWithTag("browser-start").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-surface").assertIsDisplayed()
        assertTopControls()
    }

    private fun assertTopControls() {
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-address").assertIsDisplayed()
    }

    /** The page opens a window without a tap and the window is sent to [url]. */
    private fun openWindow(chrome: WebChromeClient, page: WebView, url: String) {
        val message = Message.obtain(Handler(Looper.getMainLooper()))
        val transport = page.WebViewTransport()
        message.obj = transport
        assertTrue(chrome.onCreateWindow(page, false, false, message))
        val window = checkNotNull(transport.webView)
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        checkNotNull(window.webViewClient).shouldOverrideUrlLoading(window, request)
    }

    private fun webViews(): List<WebView> {
        val result = mutableListOf<WebView>()
        fun visit(view: View) {
            if (view is WebView) result += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(composeRule.activity.window.decorView)
        return result
    }

    private companion object {
        const val FIRST_PAGE = "https://example.test/one"
        const val SECOND_PAGE = "https://example.test/two"
        const val WATCH_PAGE = "https://m.youtube.com/watch?v=AAAAAAAAAA1"
        const val POP_UP = "https://pop.other.test/win"
        const val SECOND_POP_UP = "https://pop.other.test/again"
    }
}