package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Census video 10 (tests 1–3 and run 2's census FULL, 2026-10-15/16): master and variant `2xx`,
 * segment `no-segment`, 0 segments, 2 requests, on the census's longest video (1203 s; video 9,
 * 888 s, passed). The app's client (OkHttpExtractorClient) answers a `2xx` body over the read cap
 * as `Failure(RESPONSE_TOO_LARGE, <that 2xx code>)`, and [HlsCheck.statusOf] read the code before
 * the reason: a variant playlist over 256 KiB read `2xx` with no body, so no segment. A long
 * video's variant playlist lists every segment with its full address, so it passes the cap first.
 * Here each segment line is about 1.2 KB (an assumption: the census never prints one; with it
 * 888 s fits and 1203 s does not, as on the phone). Offline; no live call.
 */
class HlsLongPlaylistTest {
    private val headers = mapOf("User-Agent" to Census.UA, "Referer" to "https://www.youtube.com/")

    @Test
    fun `a long video's variant playlist over the cap reads too-large, not 2xx without a segment`() = runTest {
        val long = variantPlaylist(LONG_SECONDS)
        assertTrue("the fixture passes 256 KiB", long.toByteArray().size > HlsCheck.MAX_PLAYLIST_BYTES)
        val http = AppLikeClient(MASTER to masterPlaylist(), VARIANT to long)

        val result = HlsCheck(http).check(MASTER, headers, LONG_SECONDS)

        // Before the fix this read as the census line: variant 2xx, segment no-segment, 0 segments.
        assertEquals(
            HlsCheck.Result(
                master = "2xx", variant = "too-large", segment = "skip", variants = 8, maxHeight = 240,
                audio = "separate", codecs = listOf("avc1", "mp4a", "vp09"), key = "NONE", requests = 2,
            ),
            result,
        )
        assertEquals(listOf(MASTER, VARIANT), http.asked)
    }

    @Test
    fun `the same playlists pass when they fit - a shorter video, or a larger cap`() = runTest {
        val shorter = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(SHORT_SECONDS))
        val fits = HlsCheck(shorter).check(MASTER, headers, SHORT_SECONDS)
        assertEquals(listOf("2xx", "2xx", "2xx"), listOf(fits.master, fits.variant, fits.segment))
        assertEquals(listOf(178, 3), listOf(fits.segments, fits.requests))
        assertEquals(true, fits.durationMatch)

        val long = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(LONG_SECONDS))
        val wide = HlsCheck(long, maxPlaylistBytes = 1024L * 1024).check(MASTER, headers, LONG_SECONDS)
        assertEquals(listOf("2xx", "2xx", "2xx"), listOf(wide.master, wide.variant, wide.segment))
        assertEquals(listOf(241, 3), listOf(wide.segments, wide.requests))
        assertEquals(true, wide.durationMatch)
    }

    @Test
    fun `the H leg gives unknown and no row for that playlist, never a wrong row`() = runTest {
        val http = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(LONG_SECONDS), SEGMENT to TS_START)

        val outcome = HlsLeg(http).lookup().run(MASTER, LONG_SECONDS, headers)

        assertTrue("no row: $outcome", outcome is HlsLeg.None)
        outcome as HlsLeg.None
        assertEquals(listOf("UNKNOWN", "VARIANT", "2"), listOf(outcome.verdict?.name, outcome.why.name, outcome.requests.toString()))
        assertEquals(listOf(MASTER, VARIANT), http.asked)

        val shorter = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(SHORT_SECONDS), SEGMENT to TS_START)
        val row = HlsLeg(shorter).lookup().run(MASTER, SHORT_SECONDS, headers)
        assertTrue("a playlist that fits gives the row: $row", row is HlsLeg.Row)
        assertEquals("ts", (row as HlsLeg.Row).container)
    }

    @Test
    fun `a first segment over the read cap is unknown too, not a start mismatch`() = runTest {
        // A server that ignores Range sends the whole segment: over the leg's 64 KiB, with 200.
        val whole = TS_START + "x".repeat(HlsLeg.MAX_SEGMENT_BYTES.toInt())
        val http = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(SHORT_SECONDS), SEGMENT to whole)

        val outcome = HlsLeg(http).lookup().run(MASTER, SHORT_SECONDS, headers)

        assertTrue("no row: $outcome", outcome is HlsLeg.None)
        outcome as HlsLeg.None
        assertEquals(listOf("UNKNOWN", "SEGMENT", "3"), listOf(outcome.verdict?.name, outcome.why.name, outcome.requests.toString()))
    }

    @Test
    fun `a failed read is never 2xx`() {
        assertEquals("too-large", HlsCheck.statusOf(200, SiteExtractionFailure.RESPONSE_TOO_LARGE))
        assertEquals("too-large", HlsCheck.statusOf(206, SiteExtractionFailure.RESPONSE_TOO_LARGE))
        assertEquals("too-large", HlsCheck.statusOf(null, SiteExtractionFailure.RESPONSE_TOO_LARGE))
        assertEquals("other", HlsCheck.statusOf(200, SiteExtractionFailure.MALFORMED_RESPONSE))
        assertEquals("403", HlsCheck.statusOf(403, SiteExtractionFailure.HTTP_STATUS))
        assertEquals("4xx", HlsCheck.statusOf(404, SiteExtractionFailure.HTTP_STATUS))
        assertEquals("5xx", HlsCheck.statusOf(503, SiteExtractionFailure.HTTP_STATUS))
        assertEquals("other", HlsCheck.statusOf(302, SiteExtractionFailure.UNSUPPORTED_URL))
        assertEquals("net", HlsCheck.statusOf(null, SiteExtractionFailure.NETWORK))
    }

    /**
     * Answers as the app's OkHttpExtractorClient does: a body over `maxBodyBytes` is
     * `Failure(RESPONSE_TOO_LARGE, 200)` (the answer's own code); a `Range` request that fits is
     * 206; an address it does not know is 404. Its one file check (`probe`) answers 206.
     */
    private class AppLikeClient(vararg bodies: Pair<String, String>) : ExtractorHttpClient {
        private val bodies = bodies.toMap()
        val asked = ArrayList<String>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            val body = bodies[url] ?: return ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
            if (body.toByteArray().size > maxBodyBytes) {
                return ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, 200)
            }
            val ranged = headers.keys.any { it.equals("Range", ignoreCase = true) }
            val type = if (url == SEGMENT) "video/mp2t" else "application/vnd.apple.mpegurl"
            return ExtractorHttpResult.Success(if (ranged) 206 else 200, body, url, type)
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            asked += url
            return ExtractorProbeResult.Answered(206)
        }
    }

    private companion object {
        const val LONG_SECONDS = 1203L
        const val SHORT_SECONDS = 888L
        const val MASTER = "https://${Census.HLS_HOST}/api/manifest/hls_variant/id/census.10/file/index.m3u8"
        const val VARIANT = "https://${Census.HLS_HOST}/api/manifest/hls_playlist/id/census.10/itag/229/index.m3u8"
        const val SEGMENT = "https://${Census.HOST}/videoplayback/id/census.10/itag/229/sq/0/file/seg.ts"
        const val TS_START = "\u0047\u0040\u0000\u0010"

        /** 8 variants, none above 240p, audio in its own groups (as census video 10's master). */
        fun masterPlaylist(): String = buildString {
            append("#EXTM3U\n#EXT-X-INDEPENDENT-SEGMENTS\n")
            for (group in listOf("a1", "a2")) {
                append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"$group\",NAME=\"Default\",DEFAULT=YES,URI=\"")
                append("https://${Census.HLS_HOST}/api/manifest/hls_playlist/id/census.10/itag/23$group/index.m3u8\"\n")
            }
            var itag = 160
            for (height in listOf(144, 240)) {
                for (codec in listOf("avc1.4d400c", "vp09.00.10.08")) {
                    for (group in listOf("a1", "a2")) {
                        val width = height * 16 / 9
                        val bandwidth = height * 1000 + itag
                        append("#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,CODECS=\"$codec,mp4a.40.2\",RESOLUTION=${width}x$height,AUDIO=\"$group\"\n")
                        val last = height == 240 && codec.startsWith("vp09") && group == "a2"
                        append(if (last) VARIANT else "https://${Census.HLS_HOST}/api/manifest/hls_playlist/id/census.10/itag/$itag/index.m3u8")
                        append('\n')
                        itag += 1
                    }
                }
            }
        }

        /** 5-second segments, each line a full address of about 1.2 KB, as a long address would be. */
        fun variantPlaylist(seconds: Long): String = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-TARGETDURATION:5\n")
            var left = seconds.toDouble()
            var index = 0
            while (left > 0) {
                val part = minOf(5.0, left)
                append("#EXTINF:").append(part).append(",\n")
                append(if (index == 0) SEGMENT else segment(index)).append('\n')
                left -= part
                index += 1
            }
            append("#EXT-X-ENDLIST\n")
        }

        private fun segment(index: Int): String =
            "https://${Census.HOST}/videoplayback/id/census.10/itag/229/source/youtube/expire/4102444800/sparams/" +
                "p".repeat(800) + "/sig/" + "s".repeat(200) + "/playlist/index.m3u8/sq/$index/file/seg.ts"
    }
}
