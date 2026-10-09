package com.alal.yft.detection.tiktok

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.alal.yft.extractor.sites.tiktok.TikTokAgents
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P40 step 3: puts the page script's store into a WebView for TikTok's pages at document start
 * (androidx.webkit `addDocumentStartJavaScript`, for [origins] only). Returns false when this
 * WebView cannot run document-start scripts: the caller then runs the store when a page of
 * [origins] starts (`onPageStarted`), and answers TikTok's code got before that are missed.
 */
object TikTokApiCapture {
    fun install(
        webView: WebView,
        storeScript: String,
        origins: Set<String> = TikTokPageScript.ORIGINS,
    ): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        return runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, storeScript, origins)
        }.isSuccess
    }
}

/**
 * P40 step 4: the hidden pages of [TikTokPageEngine], offscreen WebViews on the main thread that
 * are never attached to the screen. Only `https` pages of [allowedHosts] (and their
 * subdomains) load in them; [isMedia] requests (TikTok's video and audio files) and media file
 * types get an empty answer. [scriptOrigins], [fixtures] and [onDestroyed] are for the
 * instrumented tests: the fixture page's origin, its answers, and a sign that a page is gone.
 */
class WebViewHiddenPages(
    context: Context,
    private val isMedia: (String) -> Boolean = { false },
    private val scriptOrigins: Set<String> = TikTokPageScript.ORIGINS,
    private val allowedHosts: Set<String> = setOf(TIKTOK_HOST),
    private val fixtures: ((WebResourceRequest) -> WebResourceResponse?)? = null,
    private val onDestroyed: () -> Unit = {},
) : HiddenPageWindows {
    private val appContext = context.applicationContext

    override suspend fun open(storeScript: String): HiddenPageWindow? =
        withContext(Dispatchers.Main) {
            if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
                return@withContext null
            }
            try {
                Window().also { it.start(storeScript) }
            } catch (missing: RuntimeException) {
                // A WebView provider that is missing or mid-update has no page to offer.
                null
            }
        }

    private inner class Window : HiddenPageWindow {
        private val webView = WebView(appContext)
        private var fallbackStore: String? = null

        @Volatile
        private var destroyed = false

        @Volatile
        override var userAgent: String? = null
            private set

        @Volatile
        override var currentUrl: String? = null
            private set

        @Volatile
        override var error: String? = null
            private set

        @SuppressLint("SetJavaScriptEnabled")
        fun start(storeScript: String) {
            val own = runCatching { WebSettings.getDefaultUserAgent(appContext) }.getOrNull()
            // TikTok sends every quality to desktop Chrome (P39), with the WebView's version.
            val agent = TikTokAgents().desktop(own)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkImage = true
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
                userAgentString = agent
            }
            userAgent = agent
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, false)
            }
            if (!TikTokApiCapture.install(webView, storeScript, scriptOrigins)) {
                fallbackStore = storeScript
            }
            webView.webViewClient = Client()
            // A desktop-sized page that is never attached to the screen.
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, WIDTH, HEIGHT)
        }

        override suspend fun load(url: String) = withContext(Dispatchers.Main) {
            if (destroyed) return@withContext
            if (!allowed(url)) {
                error = "address not allowed"
                return@withContext
            }
            currentUrl = url
            webView.loadUrl(url)
        }

        override suspend fun evaluate(script: String): String? = withContext(Dispatchers.Main) {
            if (destroyed) return@withContext null
            val reply = CompletableDeferred<String?>()
            webView.evaluateJavascript(script) { reply.complete(it) }
            withTimeoutOrNull(EVALUATE_TIMEOUT_MILLIS) { reply.await() }
        }

        override suspend fun destroy() = withContext(Dispatchers.Main + NonCancellable) {
            if (destroyed) return@withContext
            destroyed = true
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
            onDestroyed()
        }

        private fun allowed(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase(Locale.US)?.removeSuffix(".") ?: return false
            return allowedHosts.any { host == it || host.endsWith(".$it") }
        }

        private inner class Client : WebViewClient() {
            /** Only TikTok's own pages open; an app link or another site stays closed. */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = request.isForMainFrame && !allowed(request.url.toString())

            @Deprecated("Kept for WebView builds that still call the string variant")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !allowed(url)

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                fixtures?.invoke(request)?.let { return it }
                val url = request.url.toString()
                if (request.isForMainFrame) return null
                return if (isMediaFile(url)) emptyAnswer() else null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                currentUrl = url
                val store = fallbackStore ?: return
                if (TikTokPageScript.isScriptOrigin(url, scriptOrigins)) {
                    view.evaluateJavascript(store, null)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null) currentUrl = url
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                this@Window.error = error.description?.toString()
                    ?.takeIf { it.startsWith("net::") }
                    ?: "error ${error.errorCode}"
            }

            /** A crashed or killed renderer ends this page instead of the app. */
            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                this@Window.error = "page renderer gone"
                if (!destroyed) {
                    destroyed = true
                    view.destroy()
                    onDestroyed()
                }
                return true
            }
        }
    }

    private fun isMediaFile(url: String): Boolean {
        if (runCatching { isMedia(url) }.getOrDefault(false)) return true
        val path = runCatching { URI(url).rawPath }.getOrNull()?.lowercase(Locale.US)
            ?: return false
        return MEDIA_EXTENSIONS.any(path::endsWith)
    }

    private fun emptyAnswer() = WebResourceResponse(
        "application/octet-stream",
        null,
        HTTP_NO_CONTENT,
        "No Content",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

    private companion object {
        const val TIKTOK_HOST = "tiktok.com"
        const val WIDTH = 1280
        const val HEIGHT = 800
        const val HTTP_NO_CONTENT = 204
        const val EVALUATE_TIMEOUT_MILLIS = 2_000L
        val MEDIA_EXTENSIONS = listOf(".mp4", ".m4a", ".m4s", ".mp3", ".aac", ".webm", ".ts")
    }
}

/** The browser's cookie store for TikTok's pages (values never logged). */
object CookieManagerTikTokCookies : TikTokCookies {
    private val PAGES = listOf("https://www.tiktok.com/", "https://m.tiktok.com/")

    override fun header(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            ?.takeIf(String::isNotBlank)

    override fun clear(names: Set<String>) {
        val manager = CookieManager.getInstance()
        names.forEach { name ->
            PAGES.forEach { page ->
                // The domain cookie and the page's own one, whichever TikTok set.
                manager.setCookie(page, "$name=; Max-Age=0; Path=/; Domain=.tiktok.com")
                manager.setCookie(page, "$name=; Max-Age=0; Path=/")
            }
        }
        manager.flush()
    }
}