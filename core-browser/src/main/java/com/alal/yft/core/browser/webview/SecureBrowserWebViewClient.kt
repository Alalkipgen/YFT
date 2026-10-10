package com.alal.yft.core.browser.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alal.yft.core.browser.detection.DomMediaProbe
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.AppLinkPolicy
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import java.io.ByteArrayInputStream

/**
 * The browser's page loads and requests. With a [guard] (P32) a navigation the page started to
 * another site or to a pop-up ad network is blocked and reported to the sink, and the scripts of
 * the listed networks get an empty answer; without one every web address loads as before.
 * P40: [pageStartScript] is a script a page gets when it starts, for a WebView that cannot add
 * scripts at document start (null: none).
 */
class SecureBrowserWebViewClient(
    private val sink: BrowserObservationSink,
    private val cookieProvider: (String) -> String?,
    private val userAgentProvider: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageUrlState: BrowserPageUrl = BrowserPageUrl(),
    private val guard: BrowserNavigationGuard? = null,
    private val pageStartScript: (url: String) -> String? = { null },
) : WebViewClient() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingDomProbe: Runnable? = null
    private var lastFallbackUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val url = request.url.toString()
        if (BrowserAddressNormalizer.isAllowedTopLevelUrl(url)) return blocks(view, url, request)
        when (val decision = AppLinkPolicy.decide(url)) {
            AppLinkPolicy.Decision.Insecure ->
                sink.onMainFrameError(url, "Blocked insecure navigation")

            is AppLinkPolicy.Decision.Fallback -> {
                val fallback = decision.url
                // A site that offers its app and falls back to the page it is already on would
                // loop; the page simply stays.
                if (fallback != view.url && fallback != lastFallbackUrl &&
                    !blocks(view, fallback, request)
                ) {
                    lastFallbackUrl = fallback
                    view.loadUrl(fallback)
                }
            }

            AppLinkPolicy.Decision.Ignore -> Unit
        }
        return true
    }

    /** P32: the guard's answer for a page's navigation; a blocked one is reported and stays. */
    private fun blocks(view: WebView, url: String, request: WebResourceRequest): Boolean {
        val blocked = guard?.blockedNavigation(
            url = url,
            pageUrl = view.url ?: pageUrlState.get(),
            hasGesture = request.hasGesture(),
            isRedirect = request.isRedirect,
        ) ?: return false
        sink.onNavigationBlocked(blocked)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        pageUrlState.update(url)
        url?.let(sink::onPageStarted)
        guard?.pageStarted()
        url?.let(pageStartScript)?.let { script -> view.evaluateJavascript(script, null) }
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        val previous = pageUrlState.get()
        pageUrlState.update(url)
        // A new document already reported its address from onPageStarted; only an address the
        // page changed by itself (single-page navigation) is new here.
        if (url == null || url == previous) return
        sink.onUrlChanged(url)
        // A single-page site renders the next video after the address changed, and no
        // onPageFinished follows, so the DOM probe runs once the new view has had time to render.
        // Only the newest address keeps its pending probe.
        pendingDomProbe?.let(mainHandler::removeCallbacks)
        val probe = Runnable {
            pendingDomProbe = null
            if (pageUrlState.get() == url) {
                view.evaluateJavascript(DomMediaProbe.script) { result ->
                    sink.onDomProbeResult(url, result)
                }
            }
        }
        pendingDomProbe = probe
        mainHandler.postDelayed(probe, IN_PAGE_DOM_PROBE_DELAY_MS)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        guard?.pageFinished()
        val pageUrl = url ?: return
        sink.onPageFinished(pageUrl, view.title)
        view.evaluateJavascript(DomMediaProbe.script) { result ->
            sink.onDomProbeResult(pageUrl, result)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val pageUrl = pageUrlState.get() ?: return null
        val requestUrl = request.url.toString()
        if (request.url.scheme !in setOf("http", "https")) return null
        sink.onRequest(
            RequestObservation(
                pageUrl = pageUrl,
                requestUrl = requestUrl,
                method = request.method,
                headers = request.requestHeaders.toMap(),
                userAgent = request.requestHeaders.entries
                    .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                    ?.value
                    ?: userAgentProvider(),
                cookie = cookieProvider(requestUrl),
                observedAtEpochMs = clock(),
            ),
        )
        // P32: a listed network's script, frame or image is empty; the page's own ads stay.
        val blocked = !request.isForMainFrame && guard?.blocksResource(request.url.host) == true
        return if (blocked) emptyAnswer() else null
    }

    private fun emptyAnswer() =
        WebResourceResponse("text/javascript", "utf-8", ByteArrayInputStream(ByteArray(0)))

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            sink.onMainFrameError(request.url?.toString(), error.description?.toString().orEmpty())
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            sink.onMainFrameError(request.url?.toString(), "HTTP ${errorResponse.statusCode}")
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        sink.onMainFrameError(error.url, "TLS certificate validation failed")
    }

    internal companion object {
        /** How long after an in-page address change the DOM probe looks for the new player. */
        const val IN_PAGE_DOM_PROBE_DELAY_MS = 1_500L
    }
}
