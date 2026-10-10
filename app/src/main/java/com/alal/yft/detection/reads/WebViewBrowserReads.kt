package com.alal.yft.detection.reads

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alal.yft.core.model.media.BrowserReadAnswer
import com.alal.yft.core.model.media.BrowserReadRequest
import com.alal.yft.core.model.media.BrowserReads
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P45: [BrowserReads] by Android's WebView, the engine of YFT's browser: one offscreen WebView,
 * never attached to the screen, that opens a blank page of the link's page origin and asks the
 * link with the page's own `fetch` ([BrowserReadScript]). So the request goes out through
 * Chromium's network stack, with its TLS handshake, the tab's agent and the browser's cookie
 * store, as the page's player would send it.
 *
 * One read at a time, at most [READ_TIMEOUT_MILLIS] each; the WebView is destroyed after
 * [IDLE_MILLIS] without reads. Nothing loads in it but the blank page: no navigation, no
 * address from a site. Null when the phone has no WebView.
 */
@Singleton
class WebViewBrowserReads @Inject constructor(
    @ApplicationContext context: Context,
) : BrowserReads {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: WebView? = null
    private var pageReady: CompletableDeferred<Boolean>? = null
    private var pageOrigin: String? = null
    private var pageAgent: String? = null
    private var idle: Job? = null
    private var nextKey = 0

    override suspend fun read(request: BrowserReadRequest): BrowserReadAnswer? {
        val origin = BrowserReadScript.originOf(request.pageUrl) ?: return null
        if (!BrowserReadScript.isHttps(request.url)) return null
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                idle?.cancel()
                try {
                    val webView = view ?: createView() ?: return@withContext null
                    withTimeoutOrNull(READ_TIMEOUT_MILLIS) { readOnMain(webView, request, origin) }
                        ?: BrowserReadAnswer(status = 0, error = "Timeout")
                } finally {
                    idle = scope.launch {
                        delay(IDLE_MILLIS)
                        mutex.withLock { destroyView() }
                    }
                }
            }
        }
    }

    private suspend fun readOnMain(
        webView: WebView,
        request: BrowserReadRequest,
        origin: String,
    ): BrowserReadAnswer {
        if (!openBlankPage(webView, origin, request.userAgent)) {
            return BrowserReadAnswer(status = 0, error = "BlankPage")
        }
        val key = ++nextKey
        webView.evaluateJavascript(
            BrowserReadScript.start(key, request, FETCH_TIMEOUT_MILLIS),
            null,
        )
        try {
            while (true) {
                delay(POLL_MILLIS)
                val reply = CompletableDeferred<String?>()
                webView.evaluateJavascript(BrowserReadScript.poll(key)) { reply.complete(it) }
                val json = withTimeoutOrNull(POLL_TIMEOUT_MILLIS) { reply.await() }
                BrowserReadScript.answer(json, request.maxBytes)?.let { return it }
                if (view !== webView) return BrowserReadAnswer(status = 0, error = "PageGone")
            }
        } finally {
            if (view === webView) webView.evaluateJavascript(BrowserReadScript.forget(key), null)
        }
    }

    /** The blank page of [origin] with [agent], loaded once and kept while the two stay. */
    private suspend fun openBlankPage(webView: WebView, origin: String, agent: String?): Boolean {
        val wanted = agent?.takeIf(String::isNotBlank) ?: defaultAgent()
        val ready = pageReady
        if (ready != null && pageOrigin == origin && pageAgent == wanted && ready.isCompleted) {
            return ready.await()
        }
        wanted?.let { webView.settings.userAgentString = it }
        val loaded = CompletableDeferred<Boolean>()
        pageReady = loaded
        pageOrigin = origin
        pageAgent = wanted
        webView.loadDataWithBaseURL("$origin/", BLANK_PAGE, "text/html", "utf-8", null)
        return withTimeoutOrNull(PAGE_TIMEOUT_MILLIS) { loaded.await() } ?: false
    }

    private fun defaultAgent(): String? =
        runCatching { WebSettings.getDefaultUserAgent(appContext) }.getOrNull()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(): WebView? {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
            return null
        }
        val webView = try {
            WebView(appContext)
        } catch (missing: RuntimeException) {
            // A WebView provider that is missing or mid-update cannot ask anything.
            return null
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkImage = true
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        webView.webViewClient = Client()
        view = webView
        return webView
    }

    private fun destroyView() {
        val webView = view ?: return
        view = null
        pageReady = null
        pageOrigin = null
        pageAgent = null
        scope.launch(NonCancellable) {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    private inner class Client : WebViewClient() {
        /** Only the blank page shows here: any navigation stays closed. */
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = true

        @Deprecated("Kept for WebView builds that still call the string variant")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

        override fun onPageFinished(view: WebView, url: String?) {
            if (view === this@WebViewBrowserReads.view) pageReady?.complete(true)
        }

        /** A crashed or killed renderer ends this WebView instead of the app. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === this@WebViewBrowserReads.view) {
                this@WebViewBrowserReads.view = null
                pageReady?.complete(false)
                pageReady = null
            }
            view.destroy()
            return true
        }
    }

    private companion object {
        const val READ_TIMEOUT_MILLIS = 12_000L
        const val FETCH_TIMEOUT_MILLIS = 10_000L
        const val PAGE_TIMEOUT_MILLIS = 3_000L
        const val POLL_MILLIS = 100L
        const val POLL_TIMEOUT_MILLIS = 2_000L
        const val IDLE_MILLIS = 60_000L
        const val BLANK_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"></head>" +
            "<body></body></html>"
    }
}

/** P45: YFT's resolver and stream downloads ask the browser's engine through this. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BrowserReadsModule {
    @Binds
    abstract fun bindBrowserReads(reads: WebViewBrowserReads): BrowserReads
}
