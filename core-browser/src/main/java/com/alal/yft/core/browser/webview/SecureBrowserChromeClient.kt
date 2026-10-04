package com.alal.yft.core.browser.webview

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Page progress and full-screen video for the in-app browser.
 *
 * A site's player asks for full screen with [onShowCustomView]; the browser screen shows that view
 * over everything until the page or the user leaves full screen. Without a handler the request is
 * declined at once, so the page never waits for a full-screen view that does not come.
 */
class SecureBrowserChromeClient(
    private val sink: BrowserObservationSink,
    private val fullscreen: FullscreenHandler? = null,
) : WebChromeClient() {
    /** Shows and hides the player's full-screen view; both calls arrive on the main thread. */
    interface FullscreenHandler {
        fun show(view: View, exit: () -> Unit)

        fun hide()
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        sink.onProgressChanged(newProgress.coerceIn(0, 100))
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
}
