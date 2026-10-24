package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D0: the H check with the token step, against fake playlists; no live calls. */
class HlsCheckTest {
    private val headers = mapOf("User-Agent" to Census.UA, "Referer" to "https://www.youtube.com/watch?v=${Census.V1}")

    @Test
    fun `the token step reads the master, the highest variant and its first segment`() = runTest {
        val http = client(Census.MASTER to Census.masterPlaylist(), Census.VARIANT to Census.variantPlaylist())

        val result = HlsCheck(http).check(Census.MASTER, headers, Census.LENGTH)

        assertEquals(
            HlsCheck.Result(
                master = "2xx", variant = "2xx", segment = "2xx", variants = 2, maxHeight = 1080, audio = "muxed",
                codecs = listOf("avc1", "mp4a"), key = "NONE", segments = 43, durationSeconds = 212.0,
                durationMatch = true, requests = 3,
            ),
            result,
        )
        assertEquals(listOf(Census.MASTER, Census.VARIANT), http.requestedUrls)
        assertEquals(listOf(Census.SEGMENT), http.probedUrls)
        assertEquals(listOf(HlsCheck.MAX_MASTER_BYTES, HlsCheck.MAX_VARIANT_BYTES), http.requestedBodyLimits)
        assertTrue((http.requestedHeaders + http.probedHeaders).all { it == headers })
        assertFalse(result.toString().contains("https") || result.toString().contains("googlevideo"))
    }

    @Test
    fun `a 403 on the variant playlist or on the first segment is the token step's answer`() = runTest {
        val refusedVariant = FakeExtractorHttpClient(
            responses = mapOf(
                Census.MASTER to text(Census.masterPlaylist()),
                Census.VARIANT to ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403),
            ),
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206) },
        )
        val variant = HlsCheck(refusedVariant).check(Census.MASTER, headers, Census.LENGTH)
        assertEquals(listOf("2xx", "403", "skip", "2"), listOf(variant.master, variant.variant, variant.segment, variant.requests.toString()))
        assertTrue(refusedVariant.probedUrls.isEmpty())

        val refusedSegment = client(Census.MASTER to Census.masterPlaylist(), Census.VARIANT to Census.variantPlaylist()) {
            ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403, null)
        }
        val segment = HlsCheck(refusedSegment).check(Census.MASTER, headers, Census.LENGTH)
        assertEquals(listOf("2xx", "2xx", "403"), listOf(segment.master, segment.variant, segment.segment))

        val master = HlsCheck(FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403)))
            .check(Census.MASTER, headers, Census.LENGTH)
        assertEquals(listOf("403", "skip", "skip", "1"), listOf(master.master, master.variant, master.segment, master.requests.toString()))
    }

    @Test
    fun `separate audio, relative addresses and keys are read`() = runTest {
        val master = """
            #EXTM3U
            #EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI="skd://census",KEYFORMAT="com.apple.streamingkeydelivery"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="en",DEFAULT=YES,URI="audio/index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=900000,CODECS="avc1.4d401f",RESOLUTION=1280x720,AUDIO="aud"
            v720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS="avc1.640028,opus",RESOLUTION=1920x1080,AUDIO="aud"
            v1080/index.m3u8
        """.trimIndent()
        val base = Census.MASTER.substringBeforeLast('/')
        val variant = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://${Census.HLS_HOST}/key\"\n" +
            "#EXTINF:4.0,\nseg0.ts\n#EXTINF:4.0,\nseg1.ts\n#EXT-X-ENDLIST\n"
        val http = client(Census.MASTER to master, "$base/v1080/index.m3u8" to variant)

        val result = HlsCheck(http).check(Census.MASTER, headers, 8)

        assertEquals("separate", result.audio)
        assertEquals(listOf("avc1", "opus"), result.codecs)
        assertEquals("AES-128+SAMPLE-AES", result.key)
        assertEquals(listOf(Census.MASTER, "$base/v1080/index.m3u8"), http.requestedUrls)
        assertEquals(listOf("$base/v1080/seg0.ts"), http.probedUrls)
        assertEquals(true, result.durationMatch)
    }

    @Test
    fun `only https googlevideo addresses are asked`() = runTest {
        val http = client()
        for (address in listOf("https://example.com/hls/index.m3u8", "http://${Census.HLS_HOST}/index.m3u8", "not an address")) {
            assertEquals(address, "host", HlsCheck(http).check(address, headers, Census.LENGTH).master)
        }
        assertTrue(http.requestedUrls.isEmpty())

        val elsewhere = Census.masterPlaylist().replace(Census.VARIANT, "https://example.com/variant.m3u8")
        val outside = client(Census.MASTER to elsewhere)
        val result = HlsCheck(outside).check(Census.MASTER, headers, Census.LENGTH)
        assertEquals(listOf("2xx", "host", "1"), listOf(result.master, result.variant, result.requests.toString()))
        assertEquals(listOf(Census.MASTER), outside.requestedUrls)

        val segmentElsewhere = Census.variantPlaylist().replace(Census.SEGMENT, "https://example.com/seg.ts")
        val segment = HlsCheck(client(Census.MASTER to Census.masterPlaylist(), Census.VARIANT to segmentElsewhere))
            .check(Census.MASTER, headers, Census.LENGTH)
        assertEquals("host", segment.segment)
    }

    @Test
    fun `large, unreadable or empty playlists are classed`() = runTest {
        val large = FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE))
        assertEquals("too-large", HlsCheck(large).check(Census.MASTER, headers, null).master)
        assertEquals("not-m3u8", HlsCheck(client(Census.MASTER to "<html>")).check(Census.MASTER, headers, null).master)
        assertEquals("no-variant", HlsCheck(client(Census.MASTER to "#EXTM3U\n")).check(Census.MASTER, headers, null).variant)
        val empty = HlsCheck(client(Census.MASTER to Census.masterPlaylist(), Census.VARIANT to "#EXTM3U\n#EXT-X-ENDLIST\n"))
            .check(Census.MASTER, headers, Census.LENGTH)
        assertEquals(listOf("2xx", "no-segment"), listOf(empty.variant, empty.segment))
        assertNull(empty.durationMatch)
        val broken = FakeExtractorHttpClient(getResponder = { _, _ -> throw IOException("reset") })
        assertEquals("net", HlsCheck(broken).check(Census.MASTER, headers, null).master)
        val unsupported = HlsCheck(FakeExtractorHttpClient(responses = mapOf(Census.MASTER to text(Census.masterPlaylist()), Census.VARIANT to text(Census.variantPlaylist()))))
            .check(Census.MASTER, headers, null)
        assertEquals("unsupported", unsupported.segment)
    }

    @Test
    fun `the variant's duration is held against the video's length`() = runTest {
        val http = client(Census.MASTER to Census.masterPlaylist(), Census.VARIANT to Census.variantPlaylist(seconds = 100))

        assertEquals(false, HlsCheck(http).check(Census.MASTER, headers, Census.LENGTH).durationMatch)
        assertNull(HlsCheck(http).check(Census.MASTER, headers, null).durationMatch)
        assertEquals(true, HlsCheck(http).check(Census.MASTER, headers, 101).durationMatch)
    }

    @Test
    fun `attribute lists keep quoted commas`() {
        assertEquals(
            mapOf("BANDWIDTH" to "1", "CODECS" to "avc1.4d401f,mp4a.40.2", "RESOLUTION" to "1280x720"),
            HlsCheck.attributes("BANDWIDTH=1,CODECS=\"avc1.4d401f,mp4a.40.2\",RESOLUTION=1280x720"),
        )
        assertEquals("403", HlsCheck.statusOf(403, SiteExtractionFailure.HTTP_STATUS))
        assertEquals("5xx", HlsCheck.statusOf(503, SiteExtractionFailure.HTTP_STATUS))
        assertEquals("net", HlsCheck.statusOf(null, SiteExtractionFailure.NETWORK))
    }

    private fun text(body: String) = ExtractorHttpResult.Success(200, body, Census.MASTER, "application/vnd.apple.mpegurl")

    private fun client(
        vararg playlists: Pair<String, String>,
        probe: () -> ExtractorProbeResult = { ExtractorProbeResult.Answered(206) },
    ) = FakeExtractorHttpClient(
        responses = playlists.associate { (url, body) -> url to text(body) },
        probeResponder = { _, _ -> probe() },
    )
}
