package com.alal.yft.detection.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs YouTube's BotGuard challenge in a private, offscreen WebView.
 *
 * The page and its script are served by the app from the reserved asset host, the challenge
 * comes from memory, and anything else is refused; the page's content security policy allows
 * no other origin. The page's origin is not YouTube's, so the user's YouTube cookies are never
 * visible to it. One page lives as long as its minter is in use and is destroyed afterwards.
 */
internal class WebViewBotGuardEngine(
    context: Context,
    private val stepTimeoutMillis: Long = DEFAULT_STEP_TIMEOUT_MILLIS,
) : BotGuardEngine {
    private val appContext = context.applicationContext

    override val isAvailable: Boolean by lazy {
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    override suspend fun userAgent(): String? = withContext(Dispatchers.Main) {
        // A device whose WebView provider is missing or mid-update has no identity to offer.
        runCatching { WebSettings.getDefaultUserAgent(appContext) }.getOrNull()
    }

    override suspend fun open(challenge: BotGuardProtocol.Challenge): BotGuardSession? {
        val document = BotGuardProtocol.challengeDocument(challenge).toByteArray(Charsets.UTF_8)
        val page = withContext(Dispatchers.Main) {
            try {
                PageHost(appContext, document, stepTimeoutMillis).also(PageHost::start)
            } catch (missing: RuntimeException) {
                null
            }
        } ?: return null
        if (!page.awaitLoaded()) {
            page.close()
            return null
        }
        return page
    }

    private class PageHost(
        private val context: Context,
        private val challenge: ByteArray,
        private val stepTimeoutMillis: Long,
    ) : BotGuardSession {
        private val webView = WebView(context)
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<BotGuardProtocol.Reply?>>()
        private val nextId = AtomicInteger(BotGuardProtocol.LOADED_ID + 1)
        private val loaded = CompletableDeferred<BotGuardProtocol.Reply?>()
        private val mainThread = Handler(Looper.getMainLooper())

        @Volatile
        private var closed = false

        init {
            pending[BotGuardProtocol.LOADED_ID] = loaded
        }

        @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
        fun start() {
            webView.settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                blockNetworkImage = true
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // The page loads nothing from the web, so there is nothing to look up.
                    safeBrowsingEnabled = false
                }
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
            webView.webViewClient = PageClient()
            webView.addJavascriptInterface(Bridge(), BRIDGE_NAME)
            webView.loadUrl(PoTokenPageRoutes.PAGE_URL)
        }

        suspend fun awaitLoaded(): Boolean =
            withTimeoutOrNull(stepTimeoutMillis) { loaded.await() }?.value == LOADED_VALUE

        override suspend fun snapshot(): String? = call { id -> BotGuardProtocol.snapshotCall(id) }

        override suspend fun createMinter(integrityToken: String): Boolean =
            call { id -> BotGuardProtocol.createMinterCall(id, integrityToken) } == MINTER_READY

        override suspend fun mint(binding: String): String? =
            call { id -> BotGuardProtocol.mintCall(id, binding) }

        private suspend fun call(script: (Int) -> String): String? {
            if (closed) return null
            val id = nextId.getAndIncrement()
            val reply = CompletableDeferred<BotGuardProtocol.Reply?>()
            pending[id] = reply
            return try {
                val code = script(id)
                withContext(Dispatchers.Main) {
                    if (!closed) webView.evaluateJavascript(code, null)
                }
                withTimeoutOrNull(stepTimeoutMillis) { reply.await() }?.value
            } finally {
                pending.remove(id)
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            pending.values.forEach { it.complete(null) }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                destroy()
            } else {
                mainThread.post(::destroy)
            }
        }

        private fun destroy() {
            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }

        /** Receives page replies; each call's first reply wins and later ones are ignored. */
        private inner class Bridge {
            @JavascriptInterface
            fun post(text: String?) {
                val reply = BotGuardProtocol.parseReply(text) ?: return
                pending[reply.id]?.complete(reply)
            }
        }

        private inner class PageClient : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse {
                val url = request.url
                return when (val route = PoTokenPageRoutes.route(url.scheme, url.host, url.path)) {
                    is PoTokenPageRoutes.Route.Asset -> asset(route)
                    PoTokenPageRoutes.Route.Challenge -> response("application/json", challenge)
                    PoTokenPageRoutes.Route.Refused -> refused()
                }
            }

            /** The page never navigates; a script that tries is stopped here. */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = true

            @Deprecated("Kept for WebView builds that still call the string variant")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) loaded.complete(null)
            }

            /** A crashed or killed renderer ends the session instead of the app. */
            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                close()
                return true
            }

            private fun asset(route: PoTokenPageRoutes.Route.Asset): WebResourceResponse = try {
                val bytes = context.assets
                    .open("${PoTokenPageRoutes.ASSET_DIRECTORY}/${route.name}")
                    .use { it.readBytes() }
                response(route.mimeType, bytes)
            } catch (missing: IOException) {
                refused()
            }

            private fun response(mimeType: String, bytes: ByteArray) = WebResourceResponse(
                mimeType,
                "utf-8",
                HTTP_OK,
                "OK",
                mapOf(
                    "Content-Security-Policy" to PoTokenPageRoutes.CONTENT_SECURITY_POLICY,
                    "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff",
                ),
                ByteArrayInputStream(bytes),
            )

            private fun refused() = WebResourceResponse(
                "text/plain",
                "utf-8",
                HTTP_FORBIDDEN,
                "Forbidden",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(ByteArray(0)),
            )
        }
    }

    private companion object {
        const val BRIDGE_NAME = "YftPoTokenBridge"
        const val LOADED_VALUE = "loaded"
        const val MINTER_READY = "ready"
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403

        /** BotGuard answers in well under a second; this bounds a stuck or throttled page. */
        const val DEFAULT_STEP_TIMEOUT_MILLIS = 30_000L
    }
}
