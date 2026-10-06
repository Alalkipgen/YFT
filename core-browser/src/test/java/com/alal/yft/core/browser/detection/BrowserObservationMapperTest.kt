package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserObservationMapperTest {
    private val pageUrl = "https://example.test/watch"

    @Test
    fun mapsGetRequestAndMarksManifest() {
        val candidate = BrowserObservationMapper.fromRequest(
            RequestObservation(
                pageUrl = pageUrl,
                requestUrl = "https://cdn.test/master.m3u8?token=fake",
                method = "GET",
                headers = mapOf("Accept" to "application/vnd.apple.mpegurl"),
                userAgent = "YFT-Test",
                cookie = "session=fake",
                observedAtEpochMs = 10,
            ),
        )!!

        assertEquals(MediaKind.HLS, candidate.kind)
        assertTrue(candidate.sources.containsAll(setOf(CandidateSource.REQUEST, CandidateSource.MANIFEST)))
        assertEquals("session=fake", candidate.requestContext.cookie)
    }

    @Test
    fun ignoresPostUnhintedAndLiteralBlobRequests() {
        val post = request("https://cdn.test/video.mp4", method = "POST")
        val unhinted = request("https://cdn.test/generated", method = "GET")
        val blob = request("blob:https://example.test/id", method = "GET")

        assertNull(BrowserObservationMapper.fromRequest(post))
        assertNull(BrowserObservationMapper.fromRequest(unhinted))
        assertNull(BrowserObservationMapper.fromRequest(blob))
    }

    @Test
    fun aByteRangeRequestIsTheWholeFileAndItsProbeAsksForTheWholeFile() {
        // P3-FIX: one candidate and one size per file, not one per piece the player fetched.
        val piece = request(
            "https://video.cdn.test/v/t42/abc_n.mp4?_nc_cat=1&bytestart=812&byteend=1907&oh=x",
            method = "GET",
        )

        val whole = "https://video.cdn.test/v/t42/abc_n.mp4?_nc_cat=1&oh=x"
        assertEquals(whole, BrowserObservationMapper.fromRequest(piece)?.mediaUrl)
        assertEquals(whole, BrowserObservationMapper.forMetadataProbe(piece)?.mediaUrl)
    }

    @Test
    fun metadataProbeMappingAllowsStrongOpaqueHintsButRejectsOrdinaryAssets() {
        val opaqueStream = request("https://cdn.test/api/video/stream?id=7", method = "GET")
        val acceptHint = opaqueStream.copy(
            requestUrl = "https://cdn.test/api/content?id=7",
            headers = mapOf("Accept" to "video/*"),
        )
        val script = request("https://cdn.test/video/player.js", method = "GET")
        val analytics = request("https://cdn.test/analytics/video.gif", method = "GET")

        assertEquals(MediaKind.UNKNOWN, BrowserObservationMapper.forMetadataProbe(opaqueStream)?.kind)
        assertEquals(MediaKind.UNKNOWN, BrowserObservationMapper.forMetadataProbe(acceptHint)?.kind)
        assertNull(BrowserObservationMapper.forMetadataProbe(script))
        assertNull(BrowserObservationMapper.forMetadataProbe(analytics))
    }

    @Test
    fun downloadListenerUsesMimeAndSafeFilenameHint() {
        val candidate = BrowserObservationMapper.fromDownload(
            DownloadObservation(
                pageUrl = pageUrl,
                mediaUrl = "https://cdn.test/generated",
                userAgent = "YFT-Test",
                contentDisposition = "attachment; filename=fixture.webm",
                mimeType = "video/webm",
                contentLengthBytes = 50_000,
                cookie = null,
                observedAtEpochMs = 20,
            ),
        )!!

        assertEquals(MediaKind.DIRECT, candidate.kind)
        assertEquals("fixture.webm", candidate.title)
        assertEquals(setOf(CandidateSource.DOWNLOAD_LISTENER), candidate.sources)
    }

    @Test
    fun domLiteralBlobIsIgnoredSoUnderlyingRequestCanWin() {
        val blob = BrowserObservationMapper.fromDom(
            DomMediaObservation(pageUrl, "blob:https://example.test/id", null, null, null, null, 1),
        )
        val manifest = BrowserObservationMapper.fromRequest(
            request("https://cdn.test/underlying.mpd", method = "GET"),
        )

        assertNull(blob)
        assertEquals(MediaKind.DASH, manifest?.kind)
    }

    @Test
    fun anAdsFileIsAPreviewByItsServerItsFolderOrTheFrameThatAskedForIt() {
        // P24: the page's player asks with the page as its Referer, so its files keep no role.
        fun role(url: String, referer: String? = null) = BrowserObservationMapper.fromRequest(
            request(url, method = "GET").copy(
                headers = referer?.let { mapOf("Referer" to it) } ?: emptyMap(),
            ),
        )?.pageRole

        assertEquals(
            PageMediaRole.PREVIEW,
            role("https://cdn.adnet.test/creative/v.mp4", "https://ads.adnet.test/frame?slot=1"),
        )
        assertEquals(PageMediaRole.PREVIEW, role("https://cdn.test/ads/clip.mp4"))
        assertEquals(PageMediaRole.PREVIEW, role("https://pubads.g.doubleclick.net/v/1.mp4"))
        assertNull(role("https://cdn.test/v/master.m3u8", referer = pageUrl))
        // Whole folders only: a video about the vast ocean is no ad.
        assertNull(role("https://cdn.test/the-vast-ocean/1.mp4"))
        assertNull(role("https://cdn.test/downloads/1.mp4"))
    }

    private fun request(url: String, method: String) = RequestObservation(
        pageUrl = pageUrl,
        requestUrl = url,
        method = method,
        headers = emptyMap(),
        userAgent = null,
        cookie = null,
        observedAtEpochMs = 1,
    )
}
