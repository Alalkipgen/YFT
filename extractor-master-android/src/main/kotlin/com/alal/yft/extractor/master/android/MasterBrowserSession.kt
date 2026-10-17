package com.alal.yft.extractor.master.android

import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.capture.InMemoryCaptureStore
import com.alal.yft.extractor.master.MasterRequest
import java.net.URI
import java.net.URLDecoder

data class BrowserCaptureScope(val pageUrl: String, val generation: Long) {
    override fun toString(): String = "BrowserCaptureScope(generation=$generation)"
}

/** One tab, memory only. WebView callbacks and delayed JavaScript never share another scope. */
class MasterBrowserSession {
    private val store = InMemoryCaptureStore()
    private var generation = 0L
    private var scope: BrowserCaptureScope? = null
    private var contextForUrl: (String, String) -> BrowserRequestContext = { _, page ->
        BrowserRequestContext(origin(page)?.let { "$it/" }, null, null)
    }
    private val nativeContexts = linkedMapOf<String, BrowserRequestContext>()
    private val payloadHashes = mutableSetOf<Int>()
    private var previousPlayer: FramePlayer? = null
    private var previousSampleAt = 0L
    private var protected = false

    @Synchronized
    fun navigate(pageUrl: String): BrowserCaptureScope? {
        clear()
        generation++
        if (!secure(pageUrl) || pageUrl.length > CaptureFrameReader.MAX_URL_CHARS) return null
        return BrowserCaptureScope(pageUrl, generation).also {
            scope = it
            store.navigate(pageUrl, generation)
        }
    }

    @Synchronized
    fun currentScope(): BrowserCaptureScope? = scope

    @Synchronized
    internal fun focusedPlayer(request: MasterRequest): FramePlayer? {
        val current = scope ?: return null
        if (current.generation != request.generation || current.pageUrl != request.pageUrl ||
            protected
        ) return null
        return previousPlayer?.takeIf { it.visible && !it.paused && it.ready >= 2 }
    }

    @Synchronized
    internal fun setContextProvider(provider: (String, String) -> BrowserRequestContext) {
        contextForUrl = provider
    }

    @Synchronized
    fun observe(observation: RequestObservation, preview: Boolean = false) {
        val current = scope ?: return
        if (observation.pageUrl != current.pageUrl || !secure(observation.requestUrl)) return
        if (!observation.method.equals("GET", true)) return
        val mime = queryMime(observation.requestUrl)
        if (MediaUrlClassifier.classify(observation.requestUrl, mime) == null) return
        val context = BrowserRequestContext(
            pageUrl = origin(current.pageUrl)?.let { "$it/" },
            userAgent = observation.userAgent,
            cookie = observation.cookie,
            observedHeaders = observation.headers,
        )
        val accepted = store.recordRequest(
            current.generation,
            CapturedRequest(
                url = observation.requestUrl,
                mimeType = mime,
                context = context,
                observedAtEpochMs = observation.observedAtEpochMs,
                pageRole = PageMediaRole.PREVIEW.takeIf { preview },
            ),
        )
        if (accepted) {
            nativeContexts[observation.requestUrl] = context
            while (nativeContexts.size > 200) nativeContexts.remove(nativeContexts.keys.first())
        }
    }

    @Synchronized
    internal fun accept(frame: CaptureFrame, sampleAtMillis: Long): Boolean {
        val current = scope ?: return false
        if (frame.pageUrl != current.pageUrl || frame.generation != current.generation) return false
        protected = protected || frame.protected
        frame.html?.let { store.recordHtml(current.generation, it) }
        frame.payloads.forEach { body ->
            if (body.hashCode() !in payloadHashes &&
                store.recordPayload(current.generation, body)
            ) {
                payloadHashes += body.hashCode()
            }
        }
        frame.requests.forEach { observed ->
            if (!secure(observed.url)) return@forEach
            if (MediaUrlClassifier.classify(observed.url, observed.mime) == null) return@forEach
            // Only the host can supply credentials. JSON headers/cookies are never interpreted.
            val context = nativeContexts[observed.url]
                ?: contextForUrl(observed.url, current.pageUrl)
            store.recordRequest(
                current.generation,
                CapturedRequest(
                    url = observed.url,
                    mimeType = observed.mime,
                    context = context,
                    pageRole = PageMediaRole.PREVIEW.takeIf { observed.preview },
                    fedPlayer = observed.fed && !observed.preview,
                    width = observed.width,
                    height = observed.height,
                ),
            )
        }
        val playing = frame.player?.takeIf { it.visible && !it.paused && it.ready >= 2 }
        val old = previousPlayer
        val elapsed = sampleAtMillis - previousSampleAt
        val authorized = playing != null && old != null && old.key == playing.key &&
            old.url == playing.url && elapsed in 50..2_000 &&
            playing.time - old.time in 0.02..5.0 && !protected
        store.playing(current.generation, playing?.url, authorized)
        previousPlayer = playing
        previousSampleAt = sampleAtMillis
        return true
    }

    suspend fun request(
        failure: SiteExtractionFailure,
        nowEpochMs: Long,
        expectedContentId: String? = null,
    ): MasterRequest? {
        val current = currentScope() ?: return null
        val context = synchronized(this) { contextForUrl(current.pageUrl, current.pageUrl) }
        val request = MasterRequest(
            current.pageUrl, current.generation, nowEpochMs, failure, expectedContentId, context,
        )
        val snapshot = snapshot(request) as? CaptureResult.Available ?: return null
        return request.copy(snapshot = snapshot.snapshot)
    }

    internal suspend fun snapshot(request: MasterRequest): CaptureResult {
        val result = store.capture(request)
        return if (result is CaptureResult.Available) {
            result.copy(
                snapshot = result.snapshot.copy(
                    accessFailure = SiteExtractionFailure.DRM_PROTECTED.takeIf {
                        synchronized(this) { protected }
                    },
                ),
            )
        } else result
    }

    @Synchronized
    fun clear() {
        scope = null
        store.clear()
        nativeContexts.clear()
        payloadHashes.clear()
        previousPlayer = null
        previousSampleAt = 0
        protected = false
    }

    override fun toString(): String = "MasterBrowserSession(memoryOnly=true)"

    internal companion object {
        fun secure(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null
        }.getOrDefault(false)

        fun origin(url: String): String? = runCatching {
            if (!secure(url)) return null
            val uri = URI(url)
            val port = uri.port.takeIf { it != -1 && it != 443 }?.let { ":$it" }.orEmpty()
            "https://${uri.host.lowercase()}$port"
        }.getOrNull()

        private fun queryMime(url: String): String? = runCatching {
            URI(url).rawQuery?.split('&')?.firstOrNull {
                it.substringBefore('=').equals("mime", true)
            }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.length <= 128 }
        }.getOrNull()
    }
}