package com.alal.yft.core.browser.webview

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.AdRedirectPolicy.Reason
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureBrowserChromeClientTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun fullScreenRequestsReachTheBrowserAndLeavingTellsThePage() {
        val handler = RecordingFullscreen()
        val client = SecureBrowserChromeClient(NoSink, handler)
        val playerView = View(context)
        val callback = CountingCallback()

        client.onShowCustomView(playerView, callback)
        assertSame(playerView, handler.shown)
        assertEquals(0, callback.hidden)

        handler.exit?.invoke()
        assertEquals(1, callback.hidden)

        client.onHideCustomView()
        assertEquals(1, handler.hidden)
    }

    @Test
    fun withoutAFullScreenHandlerThePageIsToldAtOnce() {
        val callback = CountingCallback()

        SecureBrowserChromeClient(NoSink).onShowCustomView(View(context), callback)

        assertEquals(1, callback.hidden)
    }

    @Test
    fun aPagesTitleReachesTheSinkWithItsAddress() {
        val titles = mutableListOf<Pair<String, String?>>()
        val sink = object : BrowserObservationSink by NoSink {
            override fun onPageTitle(url: String, title: String?) {
                titles += url to title
            }
        }
        val client = SecureBrowserChromeClient(sink)
        val webView = WebView(context)

        client.onReceivedTitle(webView, "Before any page")
        webView.loadUrl("https://example.com/news")
        client.onReceivedTitle(webView, "News")

        assertEquals(listOf("https://example.com/news" to "News"), titles)
        webView.destroy()
    }

    @Test
    fun withoutAGuardANewWindowOpensInThisTabAsBefore() {
        val page = pageAt(PAGE)
        val window = openWindow(SecureBrowserChromeClient(NoSink), page, isUserGesture = false)

        window.started("about:blank")
        assertEquals(PAGE, shadowOf(page).lastLoadedUrl)
        window.navigates(OTHER_SITE)

        assertEquals(OTHER_SITE, shadowOf(page).lastLoadedUrl)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the hidden window is gone", shadowOf(window).wasDestroyCalled())
        page.destroy()
    }

    @Test
    fun aWindowToAnotherSiteIsBlockedAndOnlyATapToTheSameSiteOpensHere() {
        val sink = BlockedSink()
        val guard = BrowserNavigationGuard()
        val client = SecureBrowserChromeClient(sink, guard = guard)
        val page = pageAt(PAGE)

        openWindow(client, page, isUserGesture = true).navigates(OTHER_SITE)
        openWindow(client, page, isUserGesture = false).started("https://m.example.test/live")
        assertEquals(PAGE, shadowOf(page).lastLoadedUrl)
        openWindow(client, page, isUserGesture = true).navigates("https://m.example.test/live")

        assertEquals("https://m.example.test/live", shadowOf(page).lastLoadedUrl)
        assertEquals(
            listOf(
                BlockedNavigation(OTHER_SITE, "win.other.test", true, Reason.NO_TAP),
                BlockedNavigation(
                    "https://m.example.test/live",
                    "m.example.test",
                    true,
                    Reason.NO_TAP,
                ),
            ),
            sink.blocked,
        )
        // Switched off in Settings: the window opens in this tab.
        guard.enabled = false
        openWindow(client, page, isUserGesture = false).navigates(OTHER_SITE)
        assertEquals(OTHER_SITE, shadowOf(page).lastLoadedUrl)
        assertEquals(2, sink.blocked.size)
        page.destroy()
    }

    @Test
    fun aWindowWithoutAWebAddressOrOnlyAnInsecureOneNeverOpensAndGoesAfterAWhile() {
        val page = pageAt(PAGE)
        val client = SecureBrowserChromeClient(NoSink)
        val silent = openWindow(client, page, isUserGesture = true)

        openWindow(client, page, isUserGesture = true).navigates("http://insecure.other.test/")
        openWindow(client, page, isUserGesture = true).navigates("market://details?id=x")
        assertEquals(PAGE, shadowOf(page).lastLoadedUrl)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PopupWindowCatcher.TIMEOUT_MS))
        assertTrue(shadowOf(silent).wasDestroyCalled())
        assertFalse(client.onCreateWindow(page, false, true, null))
        page.destroy()
    }

    private fun pageAt(url: String) = WebView(context).apply { loadUrl(url) }

    /** The page opens a window; the returned hidden WebView is where the window is sent. */
    private fun openWindow(
        client: SecureBrowserChromeClient,
        page: WebView,
        isUserGesture: Boolean,
    ): WebView {
        val message = Message.obtain(Handler(Looper.getMainLooper()))
        val transport = page.WebViewTransport()
        message.obj = transport
        assertTrue(client.onCreateWindow(page, false, isUserGesture, message))
        return checkNotNull(transport.webView)
    }

    private fun WebView.navigates(url: String) {
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        assertTrue(checkNotNull(webViewClient).shouldOverrideUrlLoading(this, request))
    }

    private fun WebView.started(url: String) {
        checkNotNull(webViewClient).onPageStarted(this, url, null)
    }

    private class BlockedSink : BrowserObservationSink by NoSink {
        val blocked = mutableListOf<BlockedNavigation>()

        override fun onNavigationBlocked(blocked: BlockedNavigation) {
            this.blocked += blocked
        }
    }

    private class RecordingFullscreen : SecureBrowserChromeClient.FullscreenHandler {
        var shown: View? = null
        var exit: (() -> Unit)? = null
        var hidden = 0

        override fun show(view: View, exit: () -> Unit) {
            shown = view
            this.exit = exit
        }

        override fun hide() {
            hidden++
        }
    }

    private class CountingCallback : WebChromeClient.CustomViewCallback {
        var hidden = 0

        override fun onCustomViewHidden() {
            hidden++
        }
    }

    private object NoSink : BrowserObservationSink {
        override fun onPageStarted(url: String) = Unit
        override fun onPageFinished(url: String, title: String?) = Unit
        override fun onUrlChanged(url: String) = Unit
        override fun onProgressChanged(progress: Int) = Unit
        override fun onRequest(observation: RequestObservation) = Unit
        override fun onDownload(observation: DownloadObservation) = Unit
        override fun onDomProbeResult(pageUrl: String, result: String?) = Unit
        override fun onMainFrameError(url: String?, description: String) = Unit
    }

    private companion object {
        const val PAGE = "https://m.example.test/watch?v=1"
        const val OTHER_SITE = "https://win.other.test/"
    }
}
