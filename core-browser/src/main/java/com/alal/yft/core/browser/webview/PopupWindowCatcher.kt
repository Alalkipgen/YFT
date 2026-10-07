package com.alal.yft.core.browser.webview

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * A page's new window (P32) as a WebView that is never shown and never runs a script: it tells
 * [onAddress] the first web address the window is sent to and then goes. The browser has one
 * tab, so that address opens there or is blocked. A window that gets no address in
 * [TIMEOUT_MS] goes too.
 */
internal class PopupWindowCatcher(
    context: Context,
    private val onAddress: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable(::close)
    private var answered = false
    private var closed = false

    val webView: WebView = WebView(context)

    init {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                answer(request.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                url?.let(::answer)
            }
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    private fun answer(url: String) {
        if (answered || closed || !url.isWebAddress()) return
        answered = true
        // Not inside the window's own callback.
        handler.post(::close)
        onAddress(url)
    }

    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacks(timeout)
        webView.stopLoading()
        webView.destroy()
    }

    private fun String.isWebAddress(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

    companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
