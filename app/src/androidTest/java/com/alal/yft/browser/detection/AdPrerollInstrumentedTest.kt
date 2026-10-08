package com.alal.yft.browser.detection

import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.MainActivity
import com.alal.yft.core.browser.detection.BrowserObservationMapper
import com.alal.yft.core.browser.detection.DomMediaProbe
import com.alal.yft.core.browser.detection.DomProbeResultParser
import com.alal.yft.core.browser.detection.HeadlessPageFetcher
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.detection.VastAdTracker
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.AdSign
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.TabPageReader
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.quickdownload.QuickDownloadUiState
import com.alal.yft.feature.quickdownload.QuickDownloadViewModel
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P43 on a real WebView (FIX_ADD_PLAN item 7): a fixture page of a site without an adapter
 * states a 10:05 video and its player setup names an HLS and an MP4 of it whose links answer
 * HTTP 410. Its player asks for an ad break by a query (`output=vast`) and fetches the
 * 30-second file the VAST answer names, from another site. The requests the page really made
 * go through the browser's ad-break tracker as its hook sends them (the request and its
 * answer); the 0:30 file is marked as the ad break's, is never listed and never offered: the
 * sheet reads the page again and offers the page's own video with its 10:03 length. Offline:
 * the test answers every request, the page included; the files are checked by a fake resolver.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AdPrerollInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var webView: WebView? = null
    private val sheets = ViewModelStore()
    private val asked = CopyOnWriteArrayList<WebResourceRequest>()
    private val askedAt = ConcurrentHashMap<String, Long>()
    private val adAsked = CountDownLatch(1)
    @Volatile private var html = ""

    @Before
    fun openWindow() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeWindow() {
        instrumentation.runOnMainSync {
            sheets.clear()
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
        scenario.close()
    }

    @Test
    fun theAdBreaksFileIsSkippedAndThePageReadAgainGivesThePagesVideo() {
        val agent = load()
        assertTrue("the player fetched its ad", adAsked.await(TIMEOUT_S, TimeUnit.SECONDS))

        // What the browser lists: the setup's links and facts from the DOM probe; the player's
        // requests through the ad-break tracker, as the browser's hook sends them.
        val now = System.currentTimeMillis()
        val parser = DomProbeResultParser()
        val dom = run(DomMediaProbe.script)
        val facts = parser.facts(PAGE, dom)
        assertEquals(PAGE_LENGTH, facts?.durationMillis)
        val fromDom = parser.parse(PAGE, dom, now)
        assertTrue(fromDom.any { it.mediaUrl == SCRIPT_MP4 })
        val tracker = VastAdTracker().apply { beginPage(PAGE) }
        val breakRequest = observation(AD_REQUEST, agent)
        tracker.onRequest(breakRequest)
        tracker.onAnswer(breakRequest, "text/xml", VAST)
        val adObservation = observation(AD_FILE, agent)
        val ad = BrowserObservationMapper.fromRequest(adObservation)
            ?.let { tracker.marked(it, adObservation) }
        assertEquals(AdSign.AD_BREAK, ad?.adSign)

        val store = DetectedMediaStore()
        store.browserUserAgent = agent
        val candidates = fromDom + ad!!
        store.publish(PAGE, "Harbour lights at dusk", candidates, facts = facts)
        val videos = MediaGroups.pageVideos(candidates)
        assertTrue(
            "the ad is never listed",
            MediaGroups.ofPage(videos, facts, hideAds = true).all
                .none { group -> group.candidates.any { it.mediaUrl == AD_FILE } },
        )
        val main = MediaGroups.mainVideo(videos, null, facts)
        assertTrue(main!!.candidates.any { it.mediaUrl == SCRIPT_MP4 })
        store.select(main, otherVideos = videos.size - 1)

        // The setup's links are gone; the page read again names new ones.
        val reader = TabPageReader(
            fetch = { url, _ -> HeadlessPageFetcher.Result.Page(url, html.replace(SCRIPT, AGAIN)) },
            cookies = { null },
        )
        val resolver = FakeResolver()
        val state = settle(sheet(store, resolver, reader)) { it.choices != null }

        assertNull(state.failure)
        val offered = state.choices!!.options.map { it.source.candidate.mediaUrl }
        assertTrue(offered.toString(), offered.isNotEmpty() && offered.all { AGAIN in it })
        assertEquals(MAIN_LENGTH, state.header?.durationMillis)
        assertTrue(state.freshLink)
        assertTrue(
            state.attemptDetails.toString(),
            "length 10:03 matches the page (10:05)" in state.attemptDetails,
        )
        assertTrue("the ad's file is never checked", AD_FILE !in resolver.requested)
    }

    /** The page's request for [url] as the browser's hook reports it. */
    private fun observation(url: String, agent: String): RequestObservation {
        val request = asked.first { it.url.toString() == url }
        return RequestObservation(
            pageUrl = PAGE,
            requestUrl = url,
            method = request.method,
            headers = request.requestHeaders.orEmpty(),
            userAgent = agent,
            cookie = null,
            observedAtEpochMs = askedAt.getValue(url),
        )
    }

    private fun sheet(
        store: DetectedMediaStore,
        resolver: VariantResolver,
        reader: TabPageReader,
    ): QuickDownloadViewModel {
        val created = AtomicReference<QuickDownloadViewModel>()
        instrumentation.runOnMainSync {
            val factory = viewModelFactory {
                initializer {
                    QuickDownloadViewModel(
                        store = store,
                        selectionStore = PreviewSelectionStore(),
                        resolver = resolver,
                        downloadStarter = NoDownloads,
                        downloadPreferences = Preferences,
                        network = WiFi,
                        playback = VideoPlaybackSupport.ANY,
                        pageReader = reader,
                    )
                }
            }
            sheets.clear()
            created.set(ViewModelProvider(sheets, factory)[QuickDownloadViewModel::class.java])
        }
        return created.get()
    }

    /** Waits until the sheet is [done] and its state stays the same for [STABLE_MS]. */
    private fun settle(
        sheet: QuickDownloadViewModel,
        done: (QuickDownloadUiState) -> Boolean,
    ): QuickDownloadUiState {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_S)
        var last: QuickDownloadUiState? = null
        var since = 0L
        while (SystemClock.uptimeMillis() < deadline) {
            val state = sheet.uiState.value
            val now = SystemClock.uptimeMillis()
            if (state != last) {
                last = state
                since = now
            }
            if (!state.loading && done(state) && now - since >= STABLE_MS) return state
            Thread.sleep(POLL_MS)
        }
        fail("the sheet did not settle")
        error("unreachable")
    }

    /** Loads the fixture; returns the WebView's user agent. */
    private fun load(): String {
        html = instrumentation.context.assets.open("ads/p43-preroll.html")
            .bufferedReader()
            .use { it.readText() }
        val loaded = CountDownLatch(1)
        val agent = AtomicReference<String>()
        scenario.onActivity { activity ->
            val view = WebView(activity).apply {
                @Suppress("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) = loaded.countDown()

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse = answer(request)
                }
            }
            activity.addContentView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            webView = view
            agent.set(view.settings.userAgentString)
            view.loadUrl(PAGE)
        }
        assertTrue("fixture loaded", loaded.await(TIMEOUT_S, TimeUnit.SECONDS))
        assertEquals(
            "the fixture page, not the WebView's error page",
            "\"Harbour lights at dusk - Example Clips\"",
            run("document.title"),
        )
        return agent.get()
    }

    /** Answers the page's requests without the network: the page, the ad break, the ad. */
    private fun answer(request: WebResourceRequest): WebResourceResponse {
        val url = request.url.toString()
        asked += request
        askedAt.putIfAbsent(url, System.currentTimeMillis())
        val (type, body) = when {
            url.substringBefore('#') == PAGE -> "text/html" to html
            url == AD_REQUEST -> "text/xml" to VAST
            url == AD_FILE -> {
                adAsked.countDown()
                "video/mp4" to ""
            }
            else -> return WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(ByteArray(0)),
            )
        }
        return WebResourceResponse(
            type,
            "UTF-8",
            200,
            "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(body.toByteArray()),
        )
    }

    private fun run(script: String): String? {
        val answered = CountDownLatch(1)
        val answer = AtomicReference<String?>()
        instrumentation.runOnMainSync {
            webView!!.evaluateJavascript(script) { result ->
                answer.set(result)
                answered.countDown()
            }
        }
        assertTrue("script answered", answered.await(TIMEOUT_S, TimeUnit.SECONDS))
        return answer.get()
    }

    /**
     * Checks a file like the real resolver: the setup's links answer HTTP 410, the ad's file
     * is 0:30 and the page's video 10:03 long.
     */
    private class FakeResolver : VariantResolver {
        val requested = CopyOnWriteArrayList<String>()

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            requested += candidate.mediaUrl
            if (SCRIPT in candidate.mediaUrl) {
                return VariantResolutionResult.Failure(
                    VariantResolutionFailure.HTTP_STATUS,
                    httpStatusCode = 410,
                    step = ResolutionStep.FILE_CHECK,
                    host = "media.example.test",
                )
            }
            val variant = MediaVariant(
                id = "direct-0",
                playbackUrl = candidate.mediaUrl,
                kind = MediaKind.DIRECT,
                trackType = MediaTrackType.AUDIO_VIDEO,
                requestContext = BrowserRequestContext(candidate.pageUrl, null, null),
                mimeType = "video/mp4",
                height = 720,
            )
            return VariantResolutionResult.Success(
                MediaAsset(
                    sourcePageUrl = candidate.pageUrl,
                    title = candidate.title,
                    thumbnailUrl = null,
                    durationMillis = if (candidate.mediaUrl == AD_FILE) AD_LENGTH else MAIN_LENGTH,
                    variants = listOf(variant),
                    resolvedAtEpochMs = 1,
                ),
            )
        }
    }

    private object NoDownloads : PreviewDownloadStarter {
        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult =
            EnqueueResult.Started("task-1", "Harbour lights at dusk.mp4")
    }

    private object Preferences : DownloadPreferencesRepository {
        override val preferences: Flow<DownloadPreferences> =
            MutableStateFlow(DownloadPreferences(confirmOnMeteredNetwork = false))

        override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) =
            Unit
    }

    private object WiFi : NetworkStatusSource {
        override val snapshot: StateFlow<NetworkSnapshot> = MutableStateFlow(
            NetworkSnapshot(connected = true, validated = true, unmetered = true),
        )
    }

    private companion object {
        const val PAGE = "https://clips.example.test/watch/43"
        const val SCRIPT = "hash=script"
        const val AGAIN = "hash=again"
        const val SCRIPT_MP4 = "https://media.example.test/v43/720.mp4?$SCRIPT"
        const val AD_REQUEST = "https://player.example.test/api/serve?zone=7&output=vast"
        const val AD_FILE = "https://cdn.clipnet.test/c/30s.mp4"
        const val PAGE_LENGTH = 605_000L
        const val MAIN_LENGTH = 603_000L
        const val AD_LENGTH = 30_000L
        const val TIMEOUT_S = 20L
        const val POLL_MS = 50L
        const val STABLE_MS = 500L

        /** The ad break's answer: one 30-second linear ad and its file. */
        const val VAST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<VAST version=\"4.0\"><Ad id=\"1\"><InLine><AdTitle>Clip</AdTitle><Creatives>" +
            "<Creative><Linear><Duration>00:00:30</Duration><MediaFiles>" +
            "<MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"640\" height=\"360\">" +
            "<![CDATA[$AD_FILE]]></MediaFile></MediaFiles></Linear></Creative></Creatives>" +
            "</InLine></Ad></VAST>"
    }
}
