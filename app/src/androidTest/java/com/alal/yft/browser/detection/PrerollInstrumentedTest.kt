package com.alal.yft.browser.detection

import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.MainActivity
import com.alal.yft.core.browser.detection.BrowserObservationMapper
import com.alal.yft.core.browser.detection.DomMediaProbe
import com.alal.yft.core.browser.detection.DomProbeResultParser
import com.alal.yft.core.browser.detection.PageFactsReader
import com.alal.yft.core.browser.detection.PlayingVideoProbe
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.detection.VastAdTracker
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.extractor.generic.manifest.ManifestReader
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P28 on a real WebView: a fixture page of a site without an adapter states its video (16:24)
 * and its player plays a 30 s ad first, then the page's stream. Download during the ad picks
 * the page's video with its title and picture; the ad stays under Other videos. The DOM and
 * playing-video probes run on the page; the requests the browser's hook reports (the ad break,
 * the ad, the stream) are given as it reports them, because a page script's own fetches are not
 * reported in time on the CI emulator. Offline: the test answers every request the page makes.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PrerollInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var webView: WebView? = null

    @Before
    fun openWindow() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeWindow() {
        instrumentation.runOnMainSync {
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
        scenario.close()
    }

    @Test
    fun downloadDuringTheAdPicksThePagesVideo() {
        load()

        val dom = run(DomMediaProbe.script)
        val parser = DomProbeResultParser()
        val facts = parser.facts(PAGE, dom)
        diagnose(dom)
        assertNotNull(facts)
        assertEquals(984_000L, facts?.durationMillis)
        assertEquals("Harbour lights at dusk", facts?.title)
        assertEquals(POSTER, facts?.thumbnailUrl)
        val playing = PlayingVideoProbe.playing(run(PlayingVideoProbe.script))
        assertEquals(AD, playing?.url)
        assertEquals(30_000L, playing?.durationMillis)

        val videos = videos(parser.parse(PAGE, dom, System.currentTimeMillis()), facts)
        val main = MediaGroups.mainVideo(videos, playing, facts)
            ?.let { MediaGroups.withPageFacts(it, facts) }

        assertEquals(MASTER, main?.candidates?.single()?.mediaUrl)
        assertEquals("Harbour lights at dusk", main?.title)
        assertEquals(POSTER, main?.candidates?.single()?.thumbnailUrl)
        val list = MediaGroups.ofPage(videos, facts)
        assertTrue(list.previews.flatMap { it.candidates }.any { it.mediaUrl == AD })
        assertTrue(list.videos.flatMap { it.candidates }.none { it.mediaUrl == AD })

        // After the ad the same element plays the page's stream: still the page's video.
        run("window.endAd(); 'ok'")
        val after = PlayingVideoProbe.playing(run(PlayingVideoProbe.script))
        assertTrue(after?.pageBuilt == true)
        assertEquals(main, MediaGroups.mainVideo(videos, after, facts)?.let {
            MediaGroups.withPageFacts(it, facts)
        })
    }

    /** What the browser lists: the DOM probe's files and the page's requests, with lengths. */
    private fun videos(
        fromDom: List<MediaCandidate>,
        facts: PageVideoFacts?,
    ): List<MediaGroup> {
        val tracker = VastAdTracker().apply { beginPage(PAGE) }
        val fromRequests = hookRequests(System.currentTimeMillis()).mapNotNull { observation ->
            tracker.onRequest(observation)
            val candidate = BrowserObservationMapper.fromRequest(observation)
                ?.let { tracker.marked(it, observation) }
                ?: return@mapNotNull null
            // The browser reads a stream's manifest for its length (MediaMetadataProbe).
            if (candidate.kind == MediaKind.HLS) {
                val length = ManifestReader.hls(playlist(), candidate.mediaUrl)?.durationMillis
                candidate.copy(durationMillis = length)
            } else {
                candidate.copy(durationMillis = 30_000)
            }
        }
        val candidates = CandidateNormalizer().normalize(PAGE, fromDom + fromRequests)
        return MediaGroups.pageVideos(MediaGroups.withPageRoles(candidates, facts))
    }

    /**
     * What the probe read on this WebView and how this device reads it, as `YFT-DIAG` lines
     * the CI run shows (no page text: lengths, names, and the JSON-LD block as hex).
     */
    private fun diagnose(dom: String?) {
        val entries = runCatching { JSONArray(JSONTokener(dom).nextValue() as String) }.getOrNull()
        val parts = entries?.optJSONObject(0)?.optJSONObject("facts")
        val blocks = parts?.optJSONArray("jsonLd")
        val block = blocks?.optString(0, "").orEmpty()
        val keys = parts?.optJSONObject("meta")?.keys()?.asSequence()?.joinToString("/")
            ?.replace('_', '.')
        diag(
            "probe",
            "len=${dom?.length} entries=${entries?.length()} keys=$keys " +
                "ld=${blocks?.length()} ldlen=${block.length}",
        )
        val hex = block.toByteArray().joinToString("") { "%02x".format(it) }
        diag("ldhex", hex.chunked(HEX_CHUNK).take(HEX_PARTS).zip(HEX_KEYS).joinToString(" ") {
            "${it.second}=${it.first}"
        })
        val fromHtml = runCatching { PageFactsReader.fromHtml(fixture(), PAGE).durationMillis }
        val fromBlock = runCatching {
            PageFactsReader.fromParts(emptyMap(), listOf(block), null, PAGE).durationMillis
        }
        val iso = runCatching { ManifestReader.isoDurationMillis("PT16M24S") }
        diag(
            "device",
            listOf("html" to fromHtml, "block" to fromBlock, "iso" to iso).joinToString(" ") {
                "${it.first}=" + it.second.fold({ value -> "$value" }) { error ->
                    error.javaClass.simpleName
                }
            },
        )
        val state = run(PAGE_STATE)?.let { runCatching { JSONTokener(it).nextValue() }.getOrNull() }
        diag("page", state as? String ?: "state=none")
    }

    private fun diag(phase: String, text: String) = Log.i(TAG, "YFT-DIAG p28-preroll $phase $text")

    private fun fixture(): String =
        instrumentation.context.assets.open("browser-detection/p28-preroll.html")
            .bufferedReader()
            .use { it.readText() }

    private fun load() {
        val html = fixture()
        val loaded = CountDownLatch(1)
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
            view.loadDataWithBaseURL(PAGE, html, "text/html", "UTF-8", null)
        }
        assertTrue("fixture loaded", loaded.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
    }

    /**
     * What the browser's request hook reports while the page plays: its player asks another
     * site for the ad break, fetches the ad, and after it the page's HLS master.
     */
    private fun hookRequests(now: Long): List<RequestObservation> = listOf(
        "https://ads.adnet.test/serve/vast.xml?zone=7" to now,
        AD to now + AD_AFTER_BREAK_MS,
        MASTER to now + STREAM_AFTER_BREAK_MS,
    ).map { (url, at) ->
        RequestObservation(
            pageUrl = PAGE,
            requestUrl = url,
            method = "GET",
            headers = mapOf("Referer" to PAGE),
            userAgent = null,
            cookie = null,
            observedAtEpochMs = at,
        )
    }

    /** Answers the page's own requests without the network. */
    private fun answer(request: WebResourceRequest): WebResourceResponse {
        val url = request.url.toString()
        val (type, body) = when {
            MASTER in url -> "application/vnd.apple.mpegurl" to playlist()
            "vast.xml" in url -> "application/xml" to "<VAST version=\"3.0\"></VAST>"
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

    /** 164 six-second pieces: 16:24. */
    private fun playlist(): String = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:6\n")
        repeat(164) { index -> append("#EXTINF:6.0,\nseg$index.ts\n") }
        append("#EXT-X-ENDLIST\n")
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
        assertTrue("script answered", answered.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
        return answer.get()
    }

    private companion object {
        const val PAGE = "https://tube.example.test/watch/77"
        const val AD = "https://cdn.adnet.test/creatives/spring-30s.mp4"
        const val MASTER = "https://stream.example.test/v77/master.m3u8"
        const val POSTER = "https://img.example.test/v77/poster.jpg"
        const val LOAD_TIMEOUT_S = 20L
        const val AD_AFTER_BREAK_MS = 300L
        const val STREAM_AFTER_BREAK_MS = 4_000L
        const val TAG = "YFTPreroll"
        const val HEX_CHUNK = 200
        const val HEX_PARTS = 6
        val HEX_KEYS = listOf("a", "b", "c", "d", "e", "f")
        val PAGE_STATE = """
            (() => {
              const ld = document.querySelector('script[type="application/ld+json"]');
              return ['ready=' + document.readyState, 'scripts=' + document.scripts.length,
                'endAd=' + typeof window.endAd, 'ldtext=' + (ld ? ld.textContent.length : -1),
                'metas=' + document.querySelectorAll('meta').length,
                'html=' + document.documentElement.outerHTML.length].join(' ');
            })()
        """.trimIndent()
    }
}
