package com.alal.yft.extractor.master.android

import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.detection.FocusedVideoProbe
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Passive request/DOM/API capture of a visible tab. No JavaScriptInterface, network proxy,
 * automatic play, sign-in, settings downgrade or persistent storage.
 */
class WebViewPlaybackCapture(
    val enabled: Boolean,
    val session: MasterBrowserSession = MasterBrowserSession(),
    private val contentIdOf: (String) -> String? = { null },
) : PlaybackCaptureProvider {
    private var browser = WeakReference<WebView>(null)
    private var script: String? = null

    fun attach(view: WebView) {
        mainThread()
        if (!enabled) return
        session.clear()
        browser = WeakReference(view)
        script = view.context.assets.open("yft-master-capture.js")
            .bufferedReader().use { it.readText() }
        val userAgent = view.settings.userAgentString
        val cookies = CookieManager.getInstance()
        session.setContextProvider { url, page ->
            BrowserRequestContext(
                pageUrl = MasterBrowserSession.origin(page)?.let { "$it/" },
                userAgent = userAgent,
                cookie = cookies.getCookie(url),
            )
        }
    }

    /** Must run before WebView.destroy(). Weak ownership and generation checks cover callbacks. */
    fun detach(view: WebView) {
        mainThread()
        if (browser.get() !== view) return
        view.evaluateJavascript(
            "window.__yftMasterCaptureV1 && window.__yftMasterCaptureV1.dispose()",
            null,
        )
        browser.clear()
        script = null
        session.clear()
    }

    fun decorate(sink: BrowserObservationSink, view: WebView): BrowserObservationSink {
        if (!enabled) return sink
        val owner = WeakReference(view)
        return object : BrowserObservationSink by sink {
            override fun onPageStarted(url: String) {
                if (!owns(owner)) return
                navigate(url)
                sink.onPageStarted(url)
            }

            override fun onUrlChanged(url: String) {
                if (!owns(owner)) return
                navigate(url)
                sink.onUrlChanged(url)
            }

            override fun onPageFinished(url: String, title: String?) {
                if (!owns(owner)) return
                mainThread()
                session.currentScope()?.takeIf { it.pageUrl == url }?.let(::bind)
                sink.onPageFinished(url, title)
            }

            override fun onRequest(observation: RequestObservation) {
                if (!owns(owner)) return
                session.observe(observation)
                sink.onRequest(observation)
            }
        }
    }

    private fun navigate(url: String) {
        mainThread()
        session.navigate(url)?.let(::bind)
    }

    private fun owns(owner: WeakReference<WebView>): Boolean =
        browser.get()?.let { it === owner.get() } == true

    private fun bind(scope: BrowserCaptureScope) {
        val view = browser.get() ?: return
        val source = script ?: return
        val page = JSONObject.quote(scope.pageUrl)
        view.evaluateJavascript(
            "$source\nwindow.__yftMasterCaptureV1.bind(${scope.generation}, $page)",
            null,
        )
    }

    suspend fun request(
        failure: SiteExtractionFailure,
        nowEpochMs: Long,
        expectedContentId: String? = null,
    ): MasterRequest? = withContext(Dispatchers.Main.immediate) {
        if (!enabled || browser.get() == null) null
        else session.request(failure, nowEpochMs, expectedContentId)
    }

    override suspend fun capture(request: MasterRequest): CaptureResult =
        withContext(Dispatchers.Main.immediate) {
            val scope = session.currentScope()
            if (!enabled || scope?.generation != request.generation ||
                scope.pageUrl != request.pageUrl
            ) {
                return@withContext CaptureResult.Unavailable
            }
            // Observe progress twice; paused/preloaded media alone never authorizes playback.
            val expectedId = request.expectedContentId
            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                repeat(MAX_SAMPLES) {
                    if (expectedId != null &&
                        FocusedVideoProbe.isFeedSite(scope.pageUrl)
                    ) {
                        val focused = evaluate(scope, FocusedVideoProbe.script)
                        if (!CaptureFocusGuard.matches(
                                focused, scope.pageUrl, expectedId, contentIdOf,
                            )
                        ) {
                            // Focus changed without URL navigation: invalidate this lookup.
                            session.navigate(scope.pageUrl)?.let(::bind)
                            return@withTimeoutOrNull
                        }
                    }
                    val frame = sample(scope) ?: return@withTimeoutOrNull
                    session.accept(frame, System.nanoTime() / 1_000_000)
                    val snapshot = session.snapshot(request) as? CaptureResult.Available
                    if (snapshot?.snapshot?.authorizedPlayback == true ||
                        snapshot?.snapshot?.accessFailure != null
                    ) return@withTimeoutOrNull
                    delay(SAMPLE_DELAY_MS)
                }
            }
            session.snapshot(request)
        }

    private suspend fun sample(scope: BrowserCaptureScope): CaptureFrame? {
        val view = browser.get() ?: return null
        if (view.url != scope.pageUrl || session.currentScope() != scope) return null
        bind(scope)
        val page = JSONObject.quote(scope.pageUrl)
        val command = "window.__yftMasterCaptureV1.sample(${scope.generation}, $page)"
        return CaptureFrameReader.read(evaluate(scope, command))
    }

    private suspend fun evaluate(scope: BrowserCaptureScope, command: String): String? {
        val view = browser.get() ?: return null
        if (view.url != scope.pageUrl || session.currentScope() != scope) return null
        val result = suspendCancellableCoroutine<String?> { continuation ->
            view.evaluateJavascript(command) { response ->
                if (continuation.isActive) continuation.resume(response)
            }
        }
        if (browser.get() !== view || session.currentScope() != scope) return null
        return result
    }

    override fun toString(): String = "WebViewPlaybackCapture(enabled=$enabled, memoryOnly=true)"

    private fun mainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Master capture WebView operations require the main thread"
        }
    }

    internal companion object {
        const val CAPTURE_TIMEOUT_MS = 2_500L
        const val SAMPLE_DELAY_MS = 200L
        const val MAX_SAMPLES = 10
    }
}
