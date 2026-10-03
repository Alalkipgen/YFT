package com.alal.yft.core.browser.webview

import androidx.annotation.MainThread
import java.util.concurrent.atomic.AtomicReference

/** Main-thread navigation writes; request interception reads without touching a WebView. */
class BrowserPageUrl {
    private val currentUrl = AtomicReference<String?>(null)

    @MainThread
    fun update(url: String?) {
        currentUrl.set(url)
    }

    fun get(): String? = currentUrl.get()
}