package com.alal.yft.core.browser.detection

import com.alal.yft.core.browser.session.PageCandidateStore
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GenericDetectionPipelineFixtureTest {
    private val pageUrl = "https://fixture.test/generic-media.html"

    @Test
    fun committedFixtureContainsEveryGenericPageShape() {
        val html = fixtureText("generic-media.html")

        listOf(
            "<video",
            "<audio",
            "<source",
            ".mp4",
            ".webm",
            ".m3u8",
            ".mpd",
            "blob:https://",
        ).forEach { marker ->
            assertTrue("Missing fixture marker: $marker", html.contains(marker))
        }
    }

    @Test
    fun fixtureSignalsProduceMergedBoundedPageScopedCandidates() = runTest {
        val store = PageCandidateStore(
            scope = this,
            debounceMillis = 0,
        )
        store.beginPage(pageUrl)
        store.submitAll(
            DomProbeResultParser().parse(
                pageUrl = pageUrl,
                javascriptResult = fixtureText("generic-media-dom-result.json"),
                observedAtEpochMs = 10,
            ),
        )
        store.submit(
            BrowserObservationMapper.fromDownload(
                DownloadObservation(
                    pageUrl = pageUrl,
                    mediaUrl = "https://media.fixture.test/direct/movie.webm",
                    userAgent = "YFT-Fixture",
                    contentDisposition = "attachment; filename=movie.webm",
                    mimeType = "video/webm",
                    contentLengthBytes = 500_000,
                    cookie = "fixture-session=test-only",
                    observedAtEpochMs = 20,
                ),
            )!!,
        )
        store.submit(
            BrowserObservationMapper.fromRedirect(
                RedirectObservation(
                    pageUrl = pageUrl,
                    fromUrl = "https://fixture.test/redirect",
                    toUrl = "https://media.fixture.test/direct/movie.mp4",
                    requestHeaders = mapOf("Accept" to "video/*"),
                    userAgent = "YFT-Fixture",
                    cookie = "fixture-session=test-only",
                    observedAtEpochMs = 21,
                ),
            )!!,
        )
        store.submit(
            BrowserObservationMapper.fromRequest(
                request(
                    "https://media.fixture.test/hls/master.m3u8?token=test-only-one",
                    observedAt = 30,
                ),
            )!!,
        )
        store.submit(
            BrowserObservationMapper.fromRequest(
                request(
                    "https://media.fixture.test/hls/master.m3u8?token=test-only-two",
                    observedAt = 31,
                ),
            )!!,
        )
        store.submit(
            BrowserObservationMapper.fromRequest(
                request(
                    "https://media.fixture.test/dash/manifest.mpd",
                    observedAt = 32,
                ),
            )!!,
        )
        runCurrent()

        val candidates = store.candidates.value
        assertEquals(5, candidates.size)
        assertEquals(3, candidates.count { it.kind == MediaKind.DIRECT })
        assertEquals(1, candidates.count { it.kind == MediaKind.HLS })
        assertEquals(1, candidates.count { it.kind == MediaKind.DASH })
        assertFalse(candidates.any { it.mediaUrl.startsWith("blob:") })

        val hls = candidates.single { it.kind == MediaKind.HLS }
        assertTrue(hls.mediaUrl.contains("token=test-only-two"))
        assertTrue(
            hls.sources.containsAll(
                setOf(CandidateSource.DOM, CandidateSource.REQUEST, CandidateSource.MANIFEST),
            ),
        )
        assertEquals("fixture-session=test-only", hls.requestContext.cookie)

        val webm = candidates.single { it.mediaUrl.contains("movie.webm") }
        assertTrue(
            webm.sources.containsAll(
                setOf(CandidateSource.DOM, CandidateSource.DOWNLOAD_LISTENER),
            ),
        )
        assertEquals(500_000L, webm.contentLengthBytes)

        store.beginPage("https://fixture.test/next")
        assertTrue(store.candidates.value.isEmpty())
    }

    private fun request(url: String, observedAt: Long) = RequestObservation(
        pageUrl = pageUrl,
        requestUrl = url,
        method = "GET",
        headers = mapOf(
            "Accept" to "*/*",
            "X-Fixture-Context" to "test-only",
        ),
        userAgent = "YFT-Fixture",
        cookie = "fixture-session=test-only",
        observedAtEpochMs = observedAt,
    )

    private fun fixtureText(name: String): String {
        val resource = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing fixture resource: $name"
        }
        return resource.bufferedReader().use { it.readText() }
    }
}