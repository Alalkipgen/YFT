package com.alal.yft.core.browser.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alal.yft.core.browser.detection.DomMediaProbe
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer

class SecureBrowserWebViewClient(
    private val sink: BrowserObservationSink,
    private val cookieProvider: (String) -> String?,
    private val userAgentProvider: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val blocked = !BrowserAddressNormalizer.isAllowedTopLevelUrl(request.url.toString())
        if (blocked) {
            sink.onMainFrameError(request.url.toString(), "Blocked insecure navigation")
        }
        return blocked
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        url?.let(sink::onPageStarted)
    }

    override fun onPageFinished(view: WebView, url: String?) {
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
        val pageUrl = view.url ?: return null
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
        return null
    }

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
}
