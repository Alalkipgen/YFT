package com.alal.yft.browser.navigation

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.AdRedirectPolicy.Reason
import com.alal.yft.core.browser.policy.SecureWebViewPolicy
import com.alal.yft.core.browser.webview.BlockedNavigation
import com.alal.yft.core.browser.webview.BrowserNavigationGuard
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.browser.webview.SecureBrowserChromeClient
import com.alal.yft.core.browser.webview.SecureBrowserWebViewClient
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.browser.BrowserBlockedNotice
import com.alal.yft.ui.theme.YftTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P32 on a real WebView: local fixture pages (inline HTML under an invented address, nothing
 * fetched for them) with the browser's own policy, clients, guard and notice. A page's timer
 * that sends the tab to another site and a tap that opens a window are blocked and the page
 * stays; a tapped link to another site still opens.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PopupAndRedirectInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val sink = RecordingSink()
    private var notice by mutableStateOf<BlockedNavigation?>(null)
    private var webView: WebView? = null

    @Test
    fun aTimerThatSendsTheTabToAnotherSiteIsBlockedAndThePageStays() {
        show(TIMER_PAGE)

        composeRule.waitUntil(WAIT_MS) { sink.blocked.isNotEmpty() }

        assertEquals(
            BlockedNavigation(AD_LANDING, "ads.other.test", window = false, reason = Reason.NO_TAP),
            sink.blocked.single(),
        )
        composeRule.onNodeWithTag("browser-blocked-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-blocked-text", useUnmergedTree = true)
            .assertTextEquals("Blocked a redirect to ads.other.test")
        assertThePageStayed("ads.other.test")
    }

    @Test
    fun aTapThatOpensAWindowToAnotherSiteIsBlockedAndThePageStays() {
        show(WINDOW_PAGE)

        tapTheMiddleOfThePage()
        composeRule.waitUntil(WAIT_MS) { sink.blocked.isNotEmpty() }

        val blocked = sink.blocked.single()
        assertEquals(POP_UP, blocked.url)
        assertTrue(blocked.window)
        composeRule.onNodeWithTag("browser-blocked-text", useUnmergedTree = true)
            .assertTextEquals("Pop-up blocked")
        assertThePageStayed("pop.other.test")
    }

    @Test
    fun aTappedLinkToAnotherSiteStillOpens() {
        show(LINK_PAGE)

        tapTheMiddleOfThePage()
        // The invented site never answers; the browser asking for it is enough.
        composeRule.waitUntil(WAIT_MS) {
            sink.requests.any { it.startsWith(STORY) } || sink.started.contains(STORY) ||
                sink.errors.any { it.orEmpty().startsWith(STORY) }
        }

        assertTrue(sink.blocked.isEmpty())
    }

    private fun show(html: String) {
        val guard = BrowserNavigationGuard()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                SecureWebViewPolicy.apply(this)
                                webViewClient = SecureBrowserWebViewClient(
                                    sink = sink,
                                    cookieProvider = { null },
                                    userAgentProvider = { null },
                                    guard = guard,
                                )
                                webChromeClient = SecureBrowserChromeClient(sink, guard = guard)
                                webView = this
                                loadDataWithBaseURL(PAGE, html, "text/html", "UTF-8", PAGE)
                            }
                        },
                        onRelease = { view -> view.destroy() },
                    )
                    notice?.let { blocked ->
                        BrowserBlockedNotice(
                            blocked = blocked,
                            onOpen = {},
                            onDismiss = {},
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(WAIT_MS) { sink.finished.isNotEmpty() }
        Thread.sleep(SETTLE_MS)
    }

    private fun tapTheMiddleOfThePage() {
        val centre = IntArray(2)
        instrumentation.runOnMainSync {
            val view = checkNotNull(webView)
            view.getLocationOnScreen(centre)
            centre[0] += view.width / 2
            centre[1] += view.height / 2
        }
        check(UiDevice.getInstance(instrumentation).click(centre[0], centre[1])) { "tap failed" }
    }

    private fun assertThePageStayed(blockedHost: String) {
        Thread.sleep(SETTLE_MS)
        var address: String? = null
        instrumentation.runOnMainSync { address = webView?.url }
        assertEquals(PAGE, address)
        assertTrue(sink.started.none { it.contains(blockedHost) })
        assertTrue(sink.requests.none { it.contains(blockedHost) })
    }

    private inner class RecordingSink : BrowserObservationSink {
        val started = CopyOnWriteArrayList<String>()
        val finished = CopyOnWriteArrayList<String>()
        val requests = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String?>()
        val blocked = CopyOnWriteArrayList<BlockedNavigation>()

        override fun onPageStarted(url: String) {
            started += url
        }

        override fun onPageFinished(url: String, title: String?) {
            finished += url
        }

        override fun onUrlChanged(url: String) = Unit
        override fun onProgressChanged(progress: Int) = Unit
        override fun onRequest(observation: RequestObservation) {
            requests += observation.requestUrl
        }

        override fun onDownload(observation: DownloadObservation) = Unit
        override fun onDomProbeResult(pageUrl: String, result: String?) = Unit
        override fun onMainFrameError(url: String?, description: String) {
            errors += url
        }

        override fun onNavigationBlocked(blocked: BlockedNavigation) {
            this.blocked += blocked
            notice = blocked
        }
    }

    private companion object {
        const val PAGE = "https://fixture.yft.test/watch"
        const val AD_LANDING = "https://ads.other.test/landing"
        const val POP_UP = "https://pop.other.test/prize"
        const val STORY = "https://news.other.test/story"
        const val WAIT_MS = 20_000L
        const val SETTLE_MS = 1_000L
        const val HEAD = "<!doctype html><html><head><meta name=\"viewport\" " +
            "content=\"width=device-width\"></head><body style=\"margin:0\">"
        const val FULL = "position:fixed;left:0;top:0;width:100vw;height:100vh;font-size:32px"
        const val TIMER_PAGE = HEAD + "<h1>A page</h1><script>" +
            "setTimeout(function () { location.href = '$AD_LANDING'; }, 4000);" +
            "</script></body></html>"
        const val WINDOW_PAGE = HEAD + "<button style=\"$FULL\" " +
            "onclick=\"window.open('$POP_UP')\">Play</button></body></html>"
        const val LINK_PAGE = HEAD + "<a style=\"display:block;$FULL\" href=\"$STORY\">" +
            "Read the story</a></body></html>"
    }
}
