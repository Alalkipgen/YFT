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
 * the reason: a variant playlist over 256 KiB read `2xx` with no body, so no segment. Fixed in
 * `7277fd8`; the next census FULL (2026-10-16 05:26 UTC) read `too-large` there, as expected.
 * A long video's variant playlist lists every segment with its full address, so it grows with the
 * video: since the owner's decision (2026-10-16, guardrail 6) the variant's cap is 1 MiB and the
 * master's stays 256 KiB. Here each segment line is about 1.2 KB (an assumption: the census never
 * prints one; with it 888 s fits 256 KiB and 1203 s does not, as on the phone, and 1 MiB holds
 * about 74 min). Offline; no live call.
 */
class HlsLongPlaylistTest {
    private val headers = mapOf("User-Agent" to Census.UA, "Referer" to "https://www.youtube.com/")

    @Test
    fun `guardrail 6's caps - the master 256 KiB, the variant 1 MiB, the leg's segment 64 KiB`() {
        assertEquals(256L * 1024, HlsCheck.MAX_MASTER_BYTES)
        assertEquals(1024L * 1024, HlsCheck.MAX_VARIANT_BYTES)
        assertEquals(64L * 1024, HlsLeg.MAX_SEGMENT_BYTES)
    }

    @Test
    fun `census video 10's variant playlist fits the variant cap - 2xx, every segment, the duration`() = runTest {
        val long = variantPlaylist(LONG_SECONDS)
        val size = long.toByteArray().size
        assertTrue("the fixture passes 256 KiB and fits 1 MiB: $size", size > 256 * 1024 && size <= HlsCheck.MAX_VARIANT_BYTES)
        val http = AppLikeClient(MASTER to masterPlaylist(), VARIANT to long)

        val result = HlsCheck(http).check(MASTER, headers, LONG_SECONDS)

        // 7277fd8 (both caps 256 KiB): variant too-large, segment skip, 2 requests; before it, the
        // census line: variant 2xx, segment no-segment.
        assertEquals(
            HlsCheck.Result(
                master = "2xx", variant = "2xx", segment = "2xx", variants = 8, maxHeight = 240,
                audio = "separate", codecs = listOf("avc1", "mp4a", "vp09"), key = "NONE", segments = 241,
                durationSeconds = 1203.0, durationMatch = true, requests = 3,
            ),
            result,
        )
        assertEquals(listOf(MASTER, VARIANT, SEGMENT), http.asked)
        assertEquals(listOf(HlsCheck.MAX_MASTER_BYTES, HlsCheck.MAX_VARIANT_BYTES), http.limits)

        val shorter = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(SHORT_SECONDS))
        val fits = HlsCheck(shorter).check(MASTER, headers, SHORT_SECONDS)
        assertEquals(listOf("2xx", "2xx", "2xx"), listOf(fits.master, fits.variant, fits.segment))
        assertEquals(listOf(178, 3), listOf(fits.segments, fits.requests))
        assertEquals(true, fits.durationMatch)
    }

    @Test
    fun `a variant playlist over the variant cap reads too-large, not 2xx without a segment`() = runTest {
        val longer = variantPlaylist(OVER_SECONDS)
        assertTrue("the fixture passes 1 MiB", longer.toByteArray().size > HlsCheck.MAX_VARIANT_BYTES)
        val http = AppLikeClient(MASTER to masterPlaylist(), VARIANT to longer)

        val result = HlsCheck(http).check(MASTER, headers, OVER_SECONDS)

        // Before 7277fd8 a playlist over the cap read as census video 10's line: 2xx, no-segment.
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
    fun `the master's cap stays 256 KiB`() = runTest {
        val padded = masterPlaylist() + "# padding\n".repeat(30_000)
        val size = padded.toByteArray().size
        assertTrue("the master passes 256 KiB and fits 1 MiB: $size", size > HlsCheck.MAX_MASTER_BYTES && size <= HlsCheck.MAX_VARIANT_BYTES)
        val http = AppLikeClient(MASTER to padded, VARIANT to variantPlaylist(SHORT_SECONDS))

        val result = HlsCheck(http).check(MASTER, headers, SHORT_SECONDS)

        assertEquals(HlsCheck.Result(master = "too-large", variant = "skip", segment = "skip", requests = 1), result)
        assertEquals(listOf(MASTER), http.asked)
    }

    @Test
    fun `the H leg gives census video 10 its row now, and unknown with no row over the variant cap`() = runTest {
        val http = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(LONG_SECONDS), SEGMENT to TS_START)

        val row = HlsLeg(http).lookup().run(MASTER, LONG_SECONDS, headers)

        assertTrue("a playlist that fits gives the row: $row", row is HlsLeg.Row)
        assertEquals("ts", (row as HlsLeg.Row).container)
        // The leg (its code unchanged) reads with HlsCheck's caps, and the segment's start with its own.
        assertEquals(listOf(HlsCheck.MAX_MASTER_BYTES, HlsCheck.MAX_VARIANT_BYTES, HlsLeg.MAX_SEGMENT_BYTES), http.limits)

        val over = AppLikeClient(MASTER to masterPlaylist(), VARIANT to variantPlaylist(OVER_SECONDS), SEGMENT to TS_START)
        val outcome = HlsLeg(over).lookup().run(MASTER, OVER_SECONDS, headers)
        assertTrue("no row: $outcome", outcome is HlsLeg.None)
        outcome as HlsLeg.None
        assertEquals(listOf("UNKNOWN", "VARIANT", "2"), listOf(outcome.verdict?.name, outcome.why.name, outcome.requests.toString()))
        assertEquals(listOf(MASTER, VARIANT), over.asked)
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
     * 206; an address it does not know is 404. Its one file check (`probe`) answers 206. It keeps
     * each read's `maxBodyBytes` in [limits].
     */
    private class AppLikeClient(vararg bodies: Pair<String, String>) : ExtractorHttpClient {
        private val bodies = bodies.toMap()
        val asked = ArrayList<String>()
        val limits = ArrayList<Long>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            limits += maxBodyBytes
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

        /** Two hours: about 1.7 MB with these lines, over the variant cap. */
        const val OVER_SECONDS = 7200L
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
