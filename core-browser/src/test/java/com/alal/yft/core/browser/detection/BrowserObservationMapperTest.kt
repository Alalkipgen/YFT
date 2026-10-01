package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaKind
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
