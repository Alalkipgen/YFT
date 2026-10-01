package com.alal.yft.core.browser.policy

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureWebViewPolicyTest {
    @Test
    @Suppress("DEPRECATION")
    fun appliesSecureDefaultsRequiredByBrowser() {
        val webView = WebView(ApplicationProvider.getApplicationContext<Context>())

        SecureWebViewPolicy.apply(webView)

        with(webView.settings) {
            assertTrue(javaScriptEnabled)
            assertTrue(domStorageEnabled)
            assertFalse(databaseEnabled)
            assertFalse(allowFileAccess)
            assertFalse(allowContentAccess)
            assertFalse(allowFileAccessFromFileURLs)
            assertFalse(allowUniversalAccessFromFileURLs)
            assertFalse(javaScriptCanOpenWindowsAutomatically)
            assertTrue(mediaPlaybackRequiresUserGesture)
            assertTrue(mixedContentMode == WebSettings.MIXED_CONTENT_NEVER_ALLOW)
        }
        webView.destroy()
    }
}
