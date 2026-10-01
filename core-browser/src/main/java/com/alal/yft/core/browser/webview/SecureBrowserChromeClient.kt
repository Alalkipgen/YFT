package com.alal.yft.core.browser.webview

import android.webkit.WebChromeClient
import android.webkit.WebView

class SecureBrowserChromeClient(
    private val sink: BrowserObservationSink,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        sink.onProgressChanged(newProgress.coerceIn(0, 100))
    }
}
