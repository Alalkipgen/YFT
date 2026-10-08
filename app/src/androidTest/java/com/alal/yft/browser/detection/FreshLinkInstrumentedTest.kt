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
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
 * P37 on a real WebView: a fixture page of a site without an adapter whose player setup names
 * a link that answers HTTP 410 for YFT while its player asks for the same file under a newer
 * signature. The DOM probe reads the setup and the request the page's player really made is
 * caught as the browser's hook catches it. The sheet prepares the player's address without a
 * manual reload. Once that link is gone too, the page read again (a notice the first time, a
 * fresh copy on Try again) gives the next working link. Offline: the test answers every
 * request, the page included; the files are checked by a fake resolver.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FreshLinkInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var webView: WebView? = null
    private val sheets = ViewModelStore()
    private val asked = CopyOnWriteArrayList<WebResourceRequest>()
    private val playerAsked = CountDownLatch(1)
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
    fun thePlayersLinkIsPreparedAndTheReadPageGivesTheNextOne() {
        val agent = load()
        assertTrue("the player asked for its file", playerAsked.await(TIMEOUT_S, TimeUnit.SECONDS))

        // What the browser lists: the setup's link from the DOM probe, the player's request.
        val now = System.currentTimeMillis()
        val fromDom = DomProbeResultParser().parse(PAGE, run(DomMediaProbe.script), now)
        assertTrue(fromDom.any { it.mediaUrl == SCRIPT_LINK })
        val request = asked.first { it.url.toString() == PLAYER_LINK }
        val fromPlayer = BrowserObservationMapper.fromRequest(
            RequestObservation(
                pageUrl = PAGE,
                requestUrl = request.url.toString(),
                method = request.method,
                headers = request.requestHeaders.orEmpty(),
                userAgent = agent,
                cookie = null,
                observedAtEpochMs = now + 1,
            ),
        )
        assertNotNull(fromPlayer)
        val store = DetectedMediaStore()
        store.browserUserAgent = agent
        store.publish(PAGE, "Harbour lights at dusk", fromDom + fromPlayer!!)
        val script = fromDom.first { it.mediaUrl == SCRIPT_LINK }
        store.select(MediaGroups.of(listOf(script)).single())

        // The sheet opened on the script's link prepares the player's address.
        val resolver = FakeResolver(dead = setOf(SCRIPT_LINK))
        val first = settle(sheet(store, resolver, reader { _, _ -> error("not read") }))
        assertNull(first.failure)
        assertNotNull(first.choices)
        assertEquals(listOf(PLAYER_LINK), resolver.requested)

        // Later the player's link is gone too: the page is read again quietly (a notice the
        // first time: not used), then Try again reads it once more and prepares its new link.
        val reads = AtomicInteger()
        val sessionAgent = AtomicReference<String?>()
        val reader = reader { url, session ->
            sessionAgent.set(session.userAgent)
            val page = if (reads.incrementAndGet() == 1) NOTICE else html.replace(SCRIPT, AGAIN)
            HeadlessPageFetcher.Result.Page(url, page)
        }
        val gone = FakeResolver(dead = setOf(SCRIPT_LINK, PLAYER_LINK))
        val sheet = sheet(store, gone, reader)
        val failed = settle(sheet)
        assertEquals("The site no longer has this video (HTTP 410).", failed.failure)
        assertTrue(failed.canReload)
        assertEquals(1, reads.get())
        assertEquals(agent, sessionAgent.get())
        assertEquals(listOf(PLAYER_LINK, SCRIPT_LINK), gone.requested)
        assertTrue("Page's newest link" in failed.failureDetails)
        assertTrue("Status: no player data (a notice or a check)" in failed.failureDetails)

        instrumentation.runOnMainSync { sheet.retry() }
        val again = settle(sheet)

        assertNull(again.failure)
        assertTrue(again.freshLink)
        assertEquals(2, reads.get())
        assertEquals(AGAIN_LINK, gone.requested.last())
        assertTrue("Link from: page read again" in again.attemptDetails)
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

    private fun reader(
        fetch: suspend (String, HeadlessPageFetcher.TabSession) -> HeadlessPageFetcher.Result,
    ) = TabPageReader(fetch = fetch, cookies = { null })

    /** Waits until the sheet shows its qualities or its error. */
    private fun settle(sheet: QuickDownloadViewModel): QuickDownloadUiState {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_S)
        while (SystemClock.uptimeMillis() < deadline) {
            val state = sheet.uiState.value
            if (!state.loading && (state.choices != null || state.failure != null)) return state
            Thread.sleep(POLL_MS)
        }
        fail("the sheet did not settle")
        error("unreachable")
    }

    /** Loads the fixture; returns the WebView's user agent. */
    private fun load(): String {
        html = instrumentation.context.assets.open("browser-detection/p37-fresh-link.html")
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

    /** Answers the page's own requests without the network; the player's file is noted. */
    private fun answer(request: WebResourceRequest): WebResourceResponse {
        val url = request.url.toString()
        asked += request
        val (type, body) = when {
            url.substringBefore('#') == PAGE -> "text/html" to html
            url == PLAYER_LINK -> {
                playerAsked.countDown()
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

    /** Checks a file like the real resolver, but HTTP 410 for the [dead] links. */
    private class FakeResolver(private val dead: Set<String>) : VariantResolver {
        val requested = CopyOnWriteArrayList<String>()

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            requested += candidate.mediaUrl
            if (candidate.mediaUrl in dead) {
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
                    durationMillis = candidate.durationMillis,
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
        const val PAGE = "https://clips.example.test/watch/5"
        const val SCRIPT = "hash=script"
        const val AGAIN = "hash=again"
        const val SCRIPT_LINK = "https://media.example.test/v5/720.mp4?$SCRIPT"
        const val PLAYER_LINK = "https://media.example.test/v5/720.mp4?hash=player"
        const val AGAIN_LINK = "https://media.example.test/v5/720.mp4?$AGAIN"
        const val TIMEOUT_S = 20L
        const val POLL_MS = 50L

        /** An age notice without player data, as a site may answer a page read without it. */
        const val NOTICE = "<html><head><title>Are you 18?</title></head>" +
            "<body><p>This site is for adults.</p><a href=\"/enter\">Enter</a></body></html>"
    }
}
