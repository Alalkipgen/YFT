package com.alal.yft.core.browser.policy

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object SecureWebViewPolicy {
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun apply(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            // P32: a page's new window reaches SecureBrowserChromeClient.onCreateWindow, which
            // opens it in the current tab or blocks it; scripts still need the user's tap.
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            userAgentString?.takeIf(String::isNotBlank)?.let { own ->
                userAgentString = BrowserUserAgent.from(own)
            }
        }
        webView.setNetworkAvailable(true)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
    }
}
