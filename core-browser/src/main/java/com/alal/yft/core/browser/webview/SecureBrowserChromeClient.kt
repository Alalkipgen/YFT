package com.alal.yft.core.browser.webview

import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer

/**
 * Page progress and full-screen video for the in-app browser.
 *
 * A site's player asks for full screen with [onShowCustomView]; the browser screen shows that view
 * over everything until the page or the user leaves full screen. Without a handler the request is
 * declined at once, so the page never waits for a full-screen view that does not come.
 *
 * A new window (`window.open`, `target="_blank"`; P32) never opens beside the page: it opens in
 * the current tab, or the [guard] blocks it and the sink hears of it.
 */
class SecureBrowserChromeClient(
    private val sink: BrowserObservationSink,
    private val fullscreen: FullscreenHandler? = null,
    private val guard: BrowserNavigationGuard? = null,
) : WebChromeClient() {
    /** Shows and hides the player's full-screen view; both calls arrive on the main thread. */
    interface FullscreenHandler {
        fun show(view: View, exit: () -> Unit)

        fun hide()
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        sink.onProgressChanged(newProgress.coerceIn(0, 100))
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        val url = view.url ?: return
        sink.onPageTitle(url, title)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val handler = fullscreen
        if (handler == null) {
            callback.onCustomViewHidden()
            return
        }
        handler.show(view) { callback.onCustomViewHidden() }
    }

    override fun onHideCustomView() {
        fullscreen?.hide()
    }

    /** The window's address is not known yet: a hidden WebView waits for it. */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val pageUrl = view.url
        val catcher = PopupWindowCatcher(view.context) { url ->
            onWindowAddress(view, url, pageUrl, isUserGesture)
        }
        transport.webView = catcher.webView
        resultMsg.sendToTarget()
        return true
    }

    /** Where the page's new window went: it opens in the current tab unless it is blocked. */
    internal fun onWindowAddress(
        view: WebView,
        url: String,
        pageUrl: String?,
        isUserGesture: Boolean,
    ) {
        if (!BrowserAddressNormalizer.isAllowedTopLevelUrl(url)) return
        val blocked = guard?.blockedWindow(url, pageUrl, isUserGesture)
        if (blocked != null) sink.onNavigationBlocked(blocked) else view.loadUrl(url)
    }
}
