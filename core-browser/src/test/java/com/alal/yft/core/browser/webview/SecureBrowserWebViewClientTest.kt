package com.alal.yft.core.browser.webview

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureBrowserWebViewClientTest {
    private lateinit var webView: WebView
    private lateinit var sink: RecordingSink
    private lateinit var client: SecureBrowserWebViewClient

    @Before
    fun setUp() {
        webView = WebView(ApplicationProvider.getApplicationContext<Context>())
        sink = RecordingSink()
        client = SecureBrowserWebViewClient(
            sink = sink,
            cookieProvider = { null },
            userAgentProvider = { "YFT-Test" },
        )
    }

    @Test
    fun allowsHttpsAndBlocksInsecureTopLevelNavigationWithSafeError() {
        assertFalse(client.shouldOverrideUrlLoading(webView, request("https://example.test")))
        assertTrue(client.shouldOverrideUrlLoading(webView, request("http://example.test")))

        assertEquals(1, sink.errors.size)
        assertEquals("Blocked insecure navigation", sink.errors.single().second)
    }

    @Test
    fun reportsMainFrameHttpErrorsButIgnoresSubresourceErrors() {
        val notFound = WebResourceResponse(
            "text/html",
            "UTF-8",
            404,
            "Not Found",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )

        client.onReceivedHttpError(webView, request("https://example.test/missing"), notFound)
        client.onReceivedHttpError(
            webView,
            request("https://example.test/image.png", isForMainFrame = false),
            notFound,
        )

        assertEquals(listOf("HTTP 404"), sink.errors.map { it.second })
    }

    private fun request(url: String, isForMainFrame: Boolean = true) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = isForMainFrame
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }

    private class RecordingSink : BrowserObservationSink {
        val errors = mutableListOf<Pair<String?, String>>()

        override fun onPageStarted(url: String) = Unit
        override fun onPageFinished(url: String, title: String?) = Unit
        override fun onProgressChanged(progress: Int) = Unit
        override fun onRequest(observation: RequestObservation) = Unit
        override fun onDownload(observation: DownloadObservation) = Unit
        override fun onDomProbeResult(pageUrl: String, result: String?) = Unit

        override fun onMainFrameError(url: String?, description: String) {
            errors += url to description
        }
    }
}