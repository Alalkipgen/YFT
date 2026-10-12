// Master Phase 1.2 N4 (strategy E). The WebView settings and the request filter follow
// WebViewOwnSolverEngine (Phase 1.1 S5, unchanged); the reply path differs (see below).
package com.alal.yft.extractor.master.android.solver

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.alal.yft.extractor.master.solver.OwnSolverEngine
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1.2 N4: strategy E, Master's own solver core in a real page (a real DOM), for players
 * whose code needs browser objects the worker lacks (plan section 1 rule 4).
 *
 * Each run gets a fresh, offscreen WebView that is destroyed afterwards. The host page
 * (`page-env.html`) runs only the app's script; the player's code runs in its iframe
 * (`page-frame.html`, `sandbox="allow-scripts"`: an opaque origin, no cookies, no storage, no
 * access to the host page). Every address is served by the app from the reserved asset host
 * ([OwnSolverPageEnvRoutes]) or refused, the frame's content security policy allows no
 * connection, and navigations and new windows are refused.
 *
 * No Java object is exposed to the page's scripts (Android would put such an object into every
 * frame, the sandboxed one included). The reply comes back through androidx.webkit's
 * `addWebMessageListener`, whose object exists only in frames of the app's origin, and
 * [OwnSolverPageReplyGate] accepts it only from the main frame of that origin. Without that
 * WebView feature the engine is unavailable and E is skipped.
 */
class WebViewPageSolverEngine(
    context: Context,
) : OwnSolverEngine {
    private val appContext = context.applicationContext

    override val isAvailable: Boolean by lazy {
        try {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        } catch (missing: RuntimeException) {
            // A device whose WebView provider is missing or mid-update cannot run the page.
            false
        }
    }

    /** N5b: the job is "started" once the host page read it (the input route). */
    override val reportsReady: Boolean get() = true

    override suspend fun run(input: String): String? = run(input, NO_EVENTS)

    override suspend fun run(input: String, events: OwnSolverEngine.Events): String? {
        if (!isAvailable) return null
        return withContext(Dispatchers.Main) {
            val reply = CompletableDeferred<String?>()
            val host = try {
                PageHost(appContext, input.toByteArray(Charsets.UTF_8), reply, events)
            } catch (missing: RuntimeException) {
                return@withContext null
            }
            try {
                if (!host.start()) return@withContext null
                reply.await()
            } finally {
                host.destroy()
            }
        }
    }

    private class PageHost(
        context: Context,
        input: ByteArray,
        private val reply: CompletableDeferred<String?>,
        events: OwnSolverEngine.Events,
    ) {
        private val webView = WebView(context)
        private val client = PageClient(context, input, reply, events)
        private val gate = OwnSolverPageReplyGate()
        private var listening = false

        private val listener = WebViewCompat.WebMessageListener {
                _: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean,
                _: JavaScriptReplyProxy,
            ->
            when (val verdict = gate.offer(sourceOrigin.toString(), isMainFrame, textOf(message))) {
                is OwnSolverPageReplyGate.Verdict.Reply -> reply.complete(verdict.text)
                OwnSolverPageReplyGate.Verdict.Failed -> reply.complete(null)
                OwnSolverPageReplyGate.Verdict.Ignored -> Unit
            }
        }

        @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
        fun start(): Boolean {
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
            webView.webChromeClient = PageChrome()
            try {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
                WebViewCompat.addWebMessageListener(
                    webView,
                    OwnSolverPageEnvRoutes.BRIDGE_NAME,
                    OwnSolverPageEnvRoutes.ALLOWED_ORIGIN_RULES,
                    listener,
                )
                listening = true
            } catch (unsupported: RuntimeException) {
                return false
            }
            webView.loadUrl(OwnSolverPageEnvRoutes.PAGE_URL)
            return true
        }

        @SuppressLint("RequiresFeature")
        fun destroy() {
            webView.stopLoading()
            if (listening) {
                try {
                    WebViewCompat.removeWebMessageListener(webView, OwnSolverPageEnvRoutes.BRIDGE_NAME)
                } catch (ignored: RuntimeException) {
                    // The WebView is destroyed next either way.
                }
            }
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }

        private fun textOf(message: WebMessageCompat): String? = try {
            if (message.type == WebMessageCompat.TYPE_STRING) message.data else null
        } catch (unexpected: RuntimeException) {
            null
        }
    }

    /** No dialogs, windows, permissions or console output (it could quote player code). */
    private class PageChrome : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message,
        ): Boolean = false

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            callback.invoke(origin, false, false)
        }
    }

    private class PageClient(
        private val context: Context,
        private val input: ByteArray,
        private val reply: CompletableDeferred<String?>,
        private val events: OwnSolverEngine.Events,
    ) : WebViewClient() {
        /** The job is read once, by the host page. */
        private val inputServed = AtomicBoolean(false)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse {
            val url = request.url
            return when (
                val route = OwnSolverPageEnvRoutes.route(url.scheme, url.host, url.path, request.isForMainFrame)
            ) {
                is OwnSolverPageEnvRoutes.Route.Asset -> asset(route)
                OwnSolverPageEnvRoutes.Route.Input ->
                    if (inputServed.compareAndSet(false, true)) {
                        events.started()
                        response("application/json", OwnSolverPageEnvRoutes.FILE_CSP, input)
                    } else {
                        refused()
                    }
                OwnSolverPageEnvRoutes.Route.Refused -> refused()
            }
        }

        /** Only the frame's own page may load in the frame; nothing else navigates. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !OwnSolverPageEnvRoutes.allowsNavigation(request.url.toString(), request.isForMainFrame)

        @Deprecated("Kept for WebView builds that still call the string variant")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            url != OwnSolverPageEnvRoutes.FRAME_URL

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) reply.complete(null)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame) reply.complete(null)
        }

        /** A crashed or killed renderer fails the run instead of the app. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            events.gone()
            reply.complete(null)
            return true
        }

        private fun asset(route: OwnSolverPageEnvRoutes.Route.Asset): WebResourceResponse = try {
            val bytes = context.assets
                .open("${OwnSolverPageEnvRoutes.ASSET_DIRECTORY}/${route.name}")
                .use { it.readBytes() }
            response(route.mimeType, route.policy, bytes)
        } catch (missing: IOException) {
            refused()
        }

        private fun response(mimeType: String, policy: String, bytes: ByteArray) = WebResourceResponse(
            mimeType,
            "utf-8",
            HTTP_OK,
            "OK",
            mapOf(
                "Content-Security-Policy" to policy,
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
                "Referrer-Policy" to "no-referrer",
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
        val NO_EVENTS = object : OwnSolverEngine.Events {
            override fun started() = Unit
            override fun gone() = Unit
        }
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
    }
}
