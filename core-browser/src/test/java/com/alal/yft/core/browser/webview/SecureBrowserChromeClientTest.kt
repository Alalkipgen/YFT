package com.alal.yft.core.browser.webview

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
