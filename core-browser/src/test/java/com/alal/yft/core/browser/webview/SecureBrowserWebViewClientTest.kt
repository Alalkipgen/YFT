package com.alal.yft.core.browser.webview

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.browser.detection.DomMediaProbe
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.AdRedirectPolicy.Reason
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureBrowserWebViewClientTest {
    private lateinit var webView: WebView
    private lateinit var sink: RecordingSink
    private lateinit var client: SecureBrowserWebViewClient
    private lateinit var pageUrlState: BrowserPageUrl
    private var now = 0L

    @Before
    fun setUp() {
        webView = ThreadCheckingWebView(ApplicationProvider.getApplicationContext<Context>())
        webView.settings.userAgentString = "YFT-Test"
        val cachedUserAgent = webView.settings.userAgentString
        sink = RecordingSink()
        pageUrlState = BrowserPageUrl()
        client = SecureBrowserWebViewClient(
            sink = sink,
            cookieProvider = { null },
            userAgentProvider = { cachedUserAgent },
            pageUrlState = pageUrlState,
        )
    }

    @Test
    fun interceptsRequestsOffMainWithoutTouchingWebViewOrSettings() {
        val pageUrl = "https://example.test/watch"
        webView.loadUrl(pageUrl)
        client.onPageStarted(webView, pageUrl, null)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val response = executor.submit<WebResourceResponse?> {
                client.shouldInterceptRequest(
                    webView,
                    request("https://cdn.test/media.mp4", isForMainFrame = false),
                )
            }.get(5, TimeUnit.SECONDS)
            assertNull(response)
        } finally {
            executor.shutdownNow()
        }

        val observation = sink.requests.single()
        assertEquals(pageUrl, observation.pageUrl)
        assertEquals("YFT-Test", observation.userAgent)
    }

    @Test
    fun inPageAddressChangesAreReportedOnceAndRequestsFollowTheNewAddress() {
        val feed = "https://m.example.test/"
        val video = "https://m.example.test/watch?v=abc"
        client.onPageStarted(webView, feed, null)
        // A new document commits the address onPageStarted already reported.
        client.doUpdateVisitedHistory(webView, feed, false)
        assertTrue(sink.urlChanges.isEmpty())

        client.doUpdateVisitedHistory(webView, video, false)
        client.doUpdateVisitedHistory(webView, video, true)

        assertEquals(listOf(video), sink.urlChanges)
        client.shouldInterceptRequest(
            webView,
            request("https://cdn.test/next.mp4", isForMainFrame = false),
        )
        assertEquals(video, sink.requests.single().pageUrl)
    }

    @Test
    fun domProbeRunsForTheNewAddressOnlyWhileThePageStaysThere() {
        val feed = "https://m.example.test/"
        val first = "https://m.example.test/watch?v=first"
        val second = "https://m.example.test/watch?v=second"
        client.onPageStarted(webView, feed, null)
        client.doUpdateVisitedHistory(webView, first, false)
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        client.doUpdateVisitedHistory(webView, second, false)
        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)
        // The first address was left before its probe was due.
        assertNull(shadowOf(webView).lastEvaluatedJavascript)

        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        assertEquals(DomMediaProbe.script, shadowOf(webView).lastEvaluatedJavascript)
        shadowOf(webView).lastEvaluatedJavascriptCallback.onReceiveValue("[]")
        assertEquals(listOf(second to "[]"), sink.domResults)
    }

    @Test
    fun loadStartSnapshotIsAvailableBeforePageCallbacks() {
        val page = "https://example.test/initial"
        pageUrlState.update(page)

        assertNull(interceptOnWorker(request("https://cdn.test/initial.mp4")))

        assertEquals(page, sink.requests.single().pageUrl)
    }

    @Test
    fun cachedPageFollowsPageStartsHistoryUpdatesAndRedirects() {
        client.onPageStarted(webView, "https://example.test/one", null)
        interceptOnWorker(request("https://cdn.test/one.mp4"))
        client.doUpdateVisitedHistory(webView, "https://example.test/two", false)
        interceptOnWorker(request("https://cdn.test/two.mp4"))
        client.onPageStarted(webView, "https://example.test/redirected", null)
        interceptOnWorker(request("https://cdn.test/redirected.mp4"))

        assertEquals(
            listOf(
                "https://example.test/one",
                "https://example.test/two",
                "https://example.test/redirected",
            ),
            sink.requests.map { it.pageUrl },
        )
    }

    @Test
    fun clearingHistorySnapshotDoesNotReuseThePreviousPage() {
        client.onPageStarted(webView, "https://example.test/one", null)
        client.doUpdateVisitedHistory(webView, null, false)

        assertNull(interceptOnWorker(request("https://cdn.test/stale.mp4")))

        assertTrue(sink.requests.isEmpty())
    }

    @Test
    fun requestUserAgentOverridesTheCachedDefaultCaseInsensitively() {
        client.onPageStarted(webView, "https://example.test/watch", null)
        interceptOnWorker(
            request("https://cdn.test/media.mp4", headers = mapOf("uSeR-aGeNt" to "Request-UA")),
        )

        assertEquals("Request-UA", sink.requests.single().userAgent)
        assertEquals(mapOf("uSeR-aGeNt" to "Request-UA"), sink.requests.single().headers)
    }

    @Test
    fun parallelInterceptionReadsTheSameCachedPageWithoutWebViewCalls() {
        val page = "https://example.test/watch"
        pageUrlState.update(page)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val requests = (0 until 32).map { index ->
                executor.submit<WebResourceResponse?> {
                    client.shouldInterceptRequest(
                        webView,
                        request("https://cdn.test/clip-$index.mp4"),
                    )
                }
            }
            requests.forEach { assertNull(it.get(5, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(32, sink.requests.size)
        assertTrue(sink.requests.all { it.pageUrl == page && it.userAgent == "YFT-Test" })
    }

    @Test
    fun requestsBeforeAnyLoadDoNotReadTheWebView() {
        assertNull(interceptOnWorker(request("https://cdn.test/media.mp4")))

        assertTrue(sink.requests.isEmpty())
    }

    @Test
    fun allowsHttpsAndBlocksInsecureTopLevelNavigationWithSafeError() {
        assertFalse(client.shouldOverrideUrlLoading(webView, request("https://example.test")))
        assertTrue(client.shouldOverrideUrlLoading(webView, request("http://example.test")))

        assertEquals(1, sink.errors.size)
        assertEquals("Blocked insecure navigation", sink.errors.single().second)
    }

    @Test
    fun appLinksKeepThePageAndIntentFallbacksLoadTheirHttpsPageOnce() {
        val fallback = "https://m.example.test/share/v/abc/"
        val intent = "intent://share/v/abc/#Intent;scheme=fb;" +
            "S.browser_fallback_url=https%3A%2F%2Fm.example.test%2Fshare%2Fv%2Fabc%2F;end"
        webView.loadUrl("https://m.example.test/start")

        assertTrue(client.shouldOverrideUrlLoading(webView, request("fb://watch/?v=1")))
        assertTrue(client.shouldOverrideUrlLoading(webView, request("market://details?id=x")))
        assertEquals("https://m.example.test/start", shadowOf(webView).lastLoadedUrl)

        assertTrue(client.shouldOverrideUrlLoading(webView, request(intent)))
        assertEquals(fallback, shadowOf(webView).lastLoadedUrl)

        // The page asks for its app again: the fallback is not loaded in a loop.
        webView.loadUrl("https://m.example.test/other")
        assertTrue(client.shouldOverrideUrlLoading(webView, request(intent)))
        assertEquals("https://m.example.test/other", shadowOf(webView).lastLoadedUrl)
        assertTrue(sink.errors.isEmpty())
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

    @Test
    fun withoutAGuardAPagesOwnRedirectToAnotherSiteLoadsAsBefore() {
        openPage(SecureBrowserWebViewClient(sink, { null }, { null }, pageUrlState = pageUrlState))

        assertFalse(client.shouldOverrideUrlLoading(webView, request(OTHER_SITE)))
        assertTrue(sink.blocked.isEmpty())
    }

    @Test
    fun aRedirectThePageStartedByItselfIsBlockedAndThePageStays() {
        val guard = openGuardedPage()
        now += 3_000

        assertTrue(client.shouldOverrideUrlLoading(webView, request(OTHER_SITE)))

        assertEquals(
            listOf(BlockedNavigation(OTHER_SITE, "win.other.test", false, Reason.NO_TAP)),
            sink.blocked,
        )
        assertEquals(GUARDED_PAGE, shadowOf(webView).lastLoadedUrl)
        // Settings › Browser › Block pop-ups and ad redirects off: it loads.
        guard.enabled = false
        assertFalse(client.shouldOverrideUrlLoading(webView, request(OTHER_SITE)))
        assertEquals(1, sink.blocked.size)
    }

    @Test
    fun tapsTheSameSiteServerRedirectsAndForwardingPagesStillLoad() {
        openGuardedPage()
        now += 3_000
        val tap = request("https://news.other.test/story", hasGesture = true)
        assertFalse(client.shouldOverrideUrlLoading(webView, tap))
        assertFalse(client.shouldOverrideUrlLoading(webView, request("https://m.example.test/b")))
        val redirect = request("https://login.other.test/", isRedirect = true)
        assertFalse(client.shouldOverrideUrlLoading(webView, redirect))

        // A page that forwards while it opens, or right after it finished, is a redirect page.
        client.onPageStarted(webView, "https://t.example.test/l?u=1", null)
        now += 1_000
        assertFalse(client.shouldOverrideUrlLoading(webView, request(OTHER_SITE)))
        client.onPageFinished(webView, "https://t.example.test/l?u=1")
        now += 1_000
        assertFalse(client.shouldOverrideUrlLoading(webView, request(OTHER_SITE)))
        assertTrue(sink.blocked.isEmpty())
    }

    @Test
    fun listedNetworksAreBlockedAfterATapAsARedirectHopAndAsAnAppFallback() {
        openGuardedPage()
        now += 3_000
        val tap = request("https://www.popads.net/click", hasGesture = true)
        assertTrue(client.shouldOverrideUrlLoading(webView, tap))
        val hop = request("https://c2.onclkds.com/l", isRedirect = true)
        assertTrue(client.shouldOverrideUrlLoading(webView, hop))
        val intent = "intent://x/#Intent;scheme=x;" +
            "S.browser_fallback_url=https%3A%2F%2Fadsterra.com%2Fl;end"
        assertTrue(client.shouldOverrideUrlLoading(webView, request(intent, hasGesture = true)))

        assertEquals(
            listOf("www.popads.net", "c2.onclkds.com", "adsterra.com"),
            sink.blocked.map { it.host },
        )
        assertTrue(sink.blocked.all { it.reason == Reason.AD_NETWORK && !it.window })
        assertEquals(GUARDED_PAGE, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun listedScriptsGetAnEmptyAnswerOffMainAndTheSinkStillSeesThem() {
        val guard = openGuardedPage()
        val script = request("https://c1.popads.net/pop.js", isForMainFrame = false)

        val answer = interceptOnWorker(script)
        assertNotNull(answer)
        assertEquals(-1, answer!!.data.read())
        assertNull(interceptOnWorker(request("https://cdn.example.test/player.js", false)))
        assertNull(interceptOnWorker(request("https://a.exoclick.com/ad.js", false)))
        assertNull("the main frame is left to navigation", interceptOnWorker(request(LISTED)))
        guard.enabled = false
        assertNull(interceptOnWorker(script))

        assertEquals(
            listOf(
                "https://c1.popads.net/pop.js",
                "https://cdn.example.test/player.js",
                "https://a.exoclick.com/ad.js",
                LISTED,
                "https://c1.popads.net/pop.js",
            ),
            sink.requests.map { it.requestUrl },
        )
    }

    private fun openGuardedPage(): BrowserNavigationGuard {
        val guard = BrowserNavigationGuard(clock = { now })
        openPage(
            SecureBrowserWebViewClient(
                sink = sink,
                cookieProvider = { null },
                userAgentProvider = { null },
                pageUrlState = pageUrlState,
                guard = guard,
            ),
        )
        return guard
    }

    private fun openPage(next: SecureBrowserWebViewClient) {
        client = next
        webView.loadUrl(GUARDED_PAGE)
        client.onPageStarted(webView, GUARDED_PAGE, null)
        now += 500
        client.onPageFinished(webView, GUARDED_PAGE)
    }

    private fun interceptOnWorker(request: WebResourceRequest): WebResourceResponse? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit<WebResourceResponse?> {
                client.shouldInterceptRequest(webView, request)
            }.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun request(
        url: String,
        isForMainFrame: Boolean = true,
        headers: Map<String, String> = emptyMap(),
        hasGesture: Boolean = false,
        isRedirect: Boolean = false,
    ) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = isForMainFrame
            override fun isRedirect(): Boolean = isRedirect
            override fun hasGesture(): Boolean = hasGesture
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = headers.toMutableMap()
        }

    private class ThreadCheckingWebView(context: Context) : WebView(context) {
        override fun getUrl(): String? {
            requireMainLooper()
            return super.getUrl()
        }

        override fun getSettings(): WebSettings {
            requireMainLooper()
            return super.getSettings()
        }

        private fun requireMainLooper() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "WebView accessed outside its main looper"
            }
        }
    }

    private class RecordingSink : BrowserObservationSink {
        val errors = mutableListOf<Pair<String?, String>>()
        val requests = CopyOnWriteArrayList<RequestObservation>()
        val urlChanges = mutableListOf<String>()
        val domResults = mutableListOf<Pair<String, String?>>()
        val blocked = mutableListOf<BlockedNavigation>()

        override fun onPageStarted(url: String) = Unit
        override fun onPageFinished(url: String, title: String?) = Unit
        override fun onUrlChanged(url: String) {
            urlChanges += url
        }
        override fun onProgressChanged(progress: Int) = Unit
        override fun onRequest(observation: RequestObservation) {
            requests += observation
        }
        override fun onDownload(observation: DownloadObservation) = Unit
        override fun onDomProbeResult(pageUrl: String, result: String?) {
            domResults += pageUrl to result
        }

        override fun onMainFrameError(url: String?, description: String) {
            errors += url to description
        }

        override fun onNavigationBlocked(blocked: BlockedNavigation) {
            this.blocked += blocked
        }
    }

    private companion object {
        const val GUARDED_PAGE = "https://m.example.test/watch?v=1"
        const val OTHER_SITE = "https://win.other.test/"
        const val LISTED = "https://www.popads.net/"
    }
}