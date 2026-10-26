package com.alal.yft.extractor.master.android

import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.detection.FocusedVideoProbe
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.extractor.master.verify.InspectedMedia
import java.lang.ref.WeakReference
import java.net.URI
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
    metadata: suspend (MediaCandidate) -> MediaCandidate = { it },
    /** R4: the bounded metadata read with the file's fingerprint; wins over [metadata]. */
    inspect: (suspend (MediaCandidate) -> InspectedMedia)? = null,
    /** R5: codec steering at document start; null (default) installs nothing. */
    private val codecs: CodecSteering? = null,
    /** R7: MSE/EME metadata hooks at document start (`yft-master-mse.js`). */
    private val mediaHooks: Boolean = true,
) : PlaybackCaptureProvider {
    private val mainSelection = MasterMainSelection(session, enabled, metadata, inspect)
    private var browser = WeakReference<WebView>(null)
    private var script: String? = null
    private var steering: ScriptHandler? = null
    private var hooks: ScriptHandler? = null
    private var early: ScriptHandler? = null

    fun attach(view: WebView) {
        mainThread()
        if (!enabled) return
        mainSelection.clear()
        session.clear()
        browser = WeakReference(view)
        script = view.context.assets.open("yft-master-capture.js")
            .bufferedReader().use { it.readText() }
        steering?.remove()
        steering = codecs?.let { steer(view, it) }
        hooks?.remove()
        hooks = if (mediaHooks) documentStart(view, readAsset(view, MEDIA_HOOKS_ASSET)) else null
        early?.remove()
        early = documentStart(view, script, EARLY_ORIGINS)
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
            "window.__yftMasterCaptureV1 && window.__yftMasterCaptureV1.dispose();" +
                "window.__yftMasterMseV1 && window.__yftMasterMseV1.dispose()",
            null,
        )
        browser.clear()
        mainSelection.clear()
        script = null
        steering?.remove()
        steering = null
        hooks?.remove()
        hooks = null
        early?.remove()
        early = null
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
        mainSelection.clear()
        session.navigate(url)?.let(::bind)
    }

    suspend fun selectMain(
        request: MasterRequest,
        snapshot: PageSnapshot,
        candidates: List<MediaCandidate>,
        probe: suspend (MediaCandidate) -> ValidationResult,
    ): MasterResult? = mainSelection.select(request, snapshot, candidates, probe)

    fun appCandidates(
        candidates: List<MediaCandidate>,
        expectedKey: String?,
    ): List<MediaCandidate>? = mainSelection.appCandidates(candidates, expectedKey)

    fun mainAndMore(candidates: List<MediaCandidate>): MasterMainPresentation? =
        mainSelection.presentation(candidates)

    /** Document-start only (a later script would miss the player's first codec query). */
    private fun steer(view: WebView, policy: CodecSteering): ScriptHandler? =
        documentStart(view, readAsset(view, CodecSteering.ASSET)?.let(policy::script))

    /** Nothing is installed on WebViews without document-start scripts; capture still works. */
    private fun documentStart(
        view: WebView,
        source: String?,
        origins: Set<String> = CodecSteering.ORIGINS,
    ): ScriptHandler? = runCatching {
        if (source == null) return null
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return null
        WebViewCompat.addDocumentStartJavaScript(view, source, origins)
    }.getOrNull()

    private fun readAsset(view: WebView, name: String): String? = runCatching {
        view.context.assets.open(name).bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun owns(owner: WeakReference<WebView>): Boolean =
        browser.get()?.let { it === owner.get() } == true

    private fun bind(scope: BrowserCaptureScope) {
        val view = browser.get() ?: return
        val source = script ?: return
        val page = JSONObject.quote(scope.pageUrl)
        // V3's retest (2026-10-26): on VK's pages the bind carries the page's manifests (see
        // [carriesManifests]); every other site's bind is the one of before.
        val carry = if (carriesManifests(scope.pageUrl)) ", true" else ""
        view.evaluateJavascript(
            "$source\nwindow.__yftMasterCaptureV1.bind(${scope.generation}, $page$carry)",
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
            // Install/bind once per lookup, not the entire collector before every sample.
            bind(scope)
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
        val page = JSONObject.quote(scope.pageUrl)
        val command = "(function(){var c=window.__yftMasterCaptureV1;" +
            "return c ? c.sample(${scope.generation}, $page) : null;})()"
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
        const val MEDIA_HOOKS_ASSET = "yft-master-mse.js"

        /**
         * VK's pack proof (2026-10-26): VK's player keeps the page's first `fetch` and asks for
         * its manifest as it starts, both before [bind]'s script runs, so on VK's pages the
         * capture script is also in place from document start (top frame only, like every
         * bind; nothing leaves the page before a bind, which takes its own page's requests
         * only). Every other site keeps the capture of before.
         */
        val EARLY_ORIGINS = setOf(
            "https://vk.com", "https://*.vk.com",
            "https://vk.ru", "https://*.vk.ru",
            "https://vkvideo.ru", "https://*.vkvideo.ru",
        )

        /**
         * V3's retest (2026-10-26): whether a bind on [pageUrl] (HTTPS on one of [EARLY_ORIGINS],
         * the default port) carries the page's manifests: VK's player fetches its manifest once a
         * document, so the capture keeps the manifests the document fetched (before its first
         * bind and during any bind, at most 16, by the address they were fetched on) and offers
         * them to every later bind of the same address (Try again's lookup). Nothing else is
         * carried; a reload is a new document. Every other site: false (its capture of before).
         */
        fun carriesManifests(pageUrl: String): Boolean {
            val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null) return false
            if (uri.port != -1 && uri.port != 443) return false
            val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf(String::isNotEmpty) ?: return false
            return EARLY_ORIGINS.any { origin ->
                val pattern = origin.removePrefix("https://")
                if (pattern.startsWith("*.")) host.endsWith(pattern.substring(1)) else host == pattern
            }
        }
    }
}
