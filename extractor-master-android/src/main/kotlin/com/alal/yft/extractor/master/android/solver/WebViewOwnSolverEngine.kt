// Provenance (Master Phase 1.1 S5): adapted from main's
//   app/src/main/java/com/alal/yft/detection/script/WebViewSolverEngine.kt (main 34a41890).
// Same sandbox, own routes/assets/bridge; main's file stays unchanged and is still main's engine.
package com.alal.yft.extractor.master.android.solver

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alal.yft.extractor.master.solver.OwnSolverEngine
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1.1 S5: runs Master's own solver core in a private, offscreen WebView.
 *
 * Each run gets a fresh WebView that is destroyed afterwards. Its page and every script are
 * served by the app from the reserved asset host ([OwnSolverPageRoutes], folder
 * `yft-own-solver/`) and anything else is refused, the page's content security policy allows no
 * other origin, and the core runs in a dedicated worker. The WebView never touches the browser's
 * cookies, storage or network, and never loads main's ejs files.
 */
class WebViewOwnSolverEngine(
    context: Context,
) : OwnSolverEngine {
    private val appContext = context.applicationContext

    override val isAvailable: Boolean by lazy {
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    override suspend fun run(input: String): String? = withContext(Dispatchers.Main) {
        val reply = CompletableDeferred<String?>()
        val host = try {
            SolverHost(appContext, input.toByteArray(Charsets.UTF_8), reply)
        } catch (missing: RuntimeException) {
            // A device whose WebView provider is missing or mid-update cannot run the solver.
            return@withContext null
        }
        try {
            host.start()
            reply.await()
        } finally {
            host.destroy()
        }
    }

    private class SolverHost(
        private val context: Context,
        input: ByteArray,
        reply: CompletableDeferred<String?>,
    ) {
        private val webView = WebView(context)
        private val client = SolverClient(context, input, reply)
        private val bridge = SolverBridge(reply)

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
            webView.webViewClient = client
            webView.addJavascriptInterface(bridge, BRIDGE_NAME)
            webView.loadUrl(OwnSolverPageRoutes.PAGE_URL)
        }

        fun destroy() {
            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    /** Receives the worker's reply; the first reply wins and later ones are ignored. */
    private class SolverBridge(
        private val reply: CompletableDeferred<String?>,
    ) {
        @JavascriptInterface
        fun post(output: String?) {
            reply.complete(output?.takeIf { it.length <= MAX_OUTPUT_CHARS })
        }
    }

    private class SolverClient(
        private val context: Context,
        private val input: ByteArray,
        private val reply: CompletableDeferred<String?>,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse {
            val url = request.url
            return when (val route = OwnSolverPageRoutes.route(url.scheme, url.host, url.path)) {
                is OwnSolverPageRoutes.Route.Asset -> asset(route)
                OwnSolverPageRoutes.Route.Input -> response("application/json", input)
                OwnSolverPageRoutes.Route.Refused -> refused()
            }
        }

        /** The page never navigates; a script that tries is stopped here. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            true

        @Deprecated("Kept for WebView builds that still call the string variant")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) reply.complete(null)
        }

        /** A crashed or killed solver renderer fails the run instead of the app. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            reply.complete(null)
            return true
        }

        private fun asset(route: OwnSolverPageRoutes.Route.Asset): WebResourceResponse = try {
            val bytes = context.assets
                .open("${OwnSolverPageRoutes.ASSET_DIRECTORY}/${route.name}")
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
                "Content-Security-Policy" to OwnSolverPageRoutes.CONTENT_SECURITY_POLICY,
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

    private companion object {
        const val BRIDGE_NAME = "YftOwnSolverBridge"

        /** Upper bound for one reply, which may carry the prepared program. */
        const val MAX_OUTPUT_CHARS = 12 * 1024 * 1024
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
    }
}
