package com.alal.yft.feature.browser

import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.ui.components.isSavable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P28: on a page without an adapter that states its video's length, Download during the
 * pre-roll ad opens the page's video. When the ad is all the page has shown so far, the sheet
 * waits up to 6 s with the page's title and picture and "Finding the page's video…", then
 * switches to the page's video by itself, or shows the ad with the line that it may be one.
 * All hosts are reserved .test names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserPageVideoWaitTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val page = "https://tube.example.test/watch/77"
    private val ad = "https://cdn.adnet.test/creatives/spring-30s.mp4"
    private val master = "https://stream.example.test/v77/master.m3u8"
    private val title = "Harbour lights at dusk"
    private val poster = "https://img.example.test/v77/poster.jpg"

    @Test
    fun theSheetWaitsOnTheAdAndSwitchesToThePagesVideoWhenItsStreamComes() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(manifests(), noAdapters(), store)
        showAdOnly(viewModel)
        val ads = videos(viewModel)
        assertEquals(1, ads.size)
        assertEquals(PageMediaRole.PREVIEW, ads.single().candidates.single().pageRole)

        // Download (the round button's one video) while the ad plays.
        assertTrue(viewModel.selectForDownload(ads.single()))
        runCurrent()
        val waiting = requireNotNull(store.lookup.value)
        assertTrue(waiting.findingPageVideo && waiting.running)
        assertEquals(title, waiting.title)
        assertEquals(poster, waiting.thumbnailUrl)
        assertNull(store.selection.value)
        assertTrue(store.awaitsLookup)

        // The player asks for the page's stream; its manifest states 16:24.
        viewModel.onRequest(observation(master, at = 5_000))
        val deadline = System.currentTimeMillis() + 5_000
        while (store.selection.value == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            advanceTimeBy(50)
            runCurrent()
        }

        val chosen = requireNotNull(store.selection.value)
        assertEquals(listOf(master), chosen.candidates.map { it.mediaUrl })
        assertEquals(title, chosen.title)
        assertEquals(poster, chosen.candidates.single().thumbnailUrl)
        assertFalse(store.maybeAd.value)
        assertNull(store.lookup.value)
    }

    @Test
    fun nothingComingInSixSecondsShowsTheAdWithItsLine() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), store)
        showAdOnly(viewModel)

        assertTrue(viewModel.selectForDownload(videos(viewModel).single()))
        advanceTimeBy(5_900)
        runCurrent()
        assertNull(store.selection.value)
        assertNotNull(store.lookup.value)

        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(ad), store.selection.value?.candidates?.map { it.mediaUrl })
        assertEquals(title, store.selection.value?.title)
        assertTrue(store.maybeAd.value)
        assertNull(store.lookup.value)
    }

    @Test
    fun downloadDuringTheAdOpensThePagesVideoAtOnceWhenItIsKnown() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        viewModel.onPageStarted(page)
        viewModel.onPageFinished(page, "$title - Example Tube")
        viewModel.onDomProbeResult(
            page,
            probe(
                JSONObject().put("url", ad).put("type", "video/mp4").put("duration", 30),
                JSONObject().put("url", master).put("type", "application/x-mpegURL")
                    .put("element", "source").put("duration", 984.2),
            ),
        )
        advanceTimeBy(500)
        runCurrent()

        viewModel.mainVideoScript()
        viewModel.onPlayingVideoResult(playingAd())
        runCurrent()

        val chosen = requireNotNull(store.selection.value)
        assertEquals(listOf(master), chosen.candidates.map { it.mediaUrl })
        assertEquals(title, chosen.title)
        assertFalse(store.maybeAd.value)
        assertEquals(1, opened.size)
        // The ad is listed apart from the page's video.
        val list = MediaGroups.ofPage(videos(viewModel))
        assertEquals(listOf(ad), list.previews.flatMap { it.candidates }.map { it.mediaUrl })
    }

    @Test
    fun closingTheWaitingSheetStopsTheWait() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), store)
        showAdOnly(viewModel)
        assertTrue(viewModel.selectForDownload(videos(viewModel).single()))
        runCurrent()
        val key = requireNotNull(store.lookup.value).key

        store.closeLookup(key)
        runCurrent()
        advanceTimeBy(7_000)
        runCurrent()

        assertNull(store.lookup.value)
        assertNull(store.selection.value)
    }

    @Test
    fun aPageThatStatesNoLengthOpensItsOnlyVideoAtOnce() = runTest {
        // P24 guard: without the page's word nothing waits.
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), store)
        viewModel.onPageStarted(page)
        viewModel.onDomProbeResult(
            page,
            JSONArray().put(
                JSONObject().put("url", ad).put("type", "video/mp4").put("duration", 30),
            ).toString(),
        )
        advanceTimeBy(500)
        runCurrent()

        val only = videos(viewModel).single()
        assertTrue(viewModel.selectForDownload(only))
        assertEquals(only, store.selection.value)
        assertNull(store.lookup.value)
        assertFalse(store.maybeAd.value)
    }

    /** The page states its video; so far only the 30 s ad plays in its player. */
    private fun TestScope.showAdOnly(viewModel: BrowserViewModel) {
        viewModel.onPageStarted(page)
        viewModel.onPageFinished(page, "$title - Example Tube")
        viewModel.onDomProbeResult(
            page,
            probe(JSONObject().put("url", ad).put("type", "video/mp4").put("duration", 30)),
        )
        advanceTimeBy(500)
        runCurrent()
    }

    /** The DOM probe's answer: the page's facts first, then its media elements. */
    private fun probe(vararg media: JSONObject): String {
        val facts = JSONObject()
            .put("meta", JSONObject().put("og:title", title).put("og:image", poster))
            .put(
                "jsonLd",
                JSONArray().put(
                    """{"@type":"VideoObject","duration":"PT16M24S",""" +
                        """"embedUrl":"https://tube.example.test/embed/77"}""",
                ),
            )
            .put("documentTitle", "$title - Example Tube")
        val entries = JSONArray().put(
            JSONObject().put("element", "facts").put("facts", facts).put("scripts", JSONArray()),
        )
        media.forEach { entries.put(it.put("element", it.optString("element", "video"))) }
        return JSONObject.quote(entries.toString())
    }

    private fun playingAd(): String = JSONObject.quote(
        JSONObject().put("now", 10_000).put(
            "videos",
            JSONArray().put(
                JSONObject().put("src", ad).put("playing", true).put("duration", 30)
                    .put("time", 4).put("width", 1920).put("height", 1080).put("area", 360_000),
            ),
        ).toString(),
    )

    private fun videos(viewModel: BrowserViewModel) =
        MediaGroups.pageVideos(viewModel.uiState.value.candidates.filter { it.isSavable })

    private fun observation(url: String, at: Long) = RequestObservation(
        pageUrl = page,
        requestUrl = url,
        method = "GET",
        headers = mapOf("Referer" to page),
        userAgent = "YFT-Test",
        cookie = null,
        observedAtEpochMs = at,
    )

    /** Answers the page's HLS master with a 16:24 media playlist (164 six-second pieces). */
    private fun manifests(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val asked = chain.request()
            val body = if (asked.url.encodedPath == "/v77/master.m3u8") {
                buildString {
                    append("#EXTM3U\n#EXT-X-TARGETDURATION:6\n")
                    repeat(164) { index -> append("#EXTINF:6.0,\nseg$index.ts\n") }
                    append("#EXT-X-ENDLIST\n")
                }
            } else {
                null
            }
            Response.Builder()
                .request(asked)
                .protocol(Protocol.HTTP_1_1)
                .code(if (body == null) 404 else 200)
                .message(if (body == null) "Not Found" else "OK")
                .header("Content-Type", "application/vnd.apple.mpegurl")
                .body(body.orEmpty().toResponseBody())
                .build()
        }
        .build()

    private fun noAdapters(): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(emptyList()))
}
