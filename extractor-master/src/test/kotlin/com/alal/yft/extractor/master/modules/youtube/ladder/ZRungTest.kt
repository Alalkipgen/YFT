package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.ladder.ZRung.Outcome
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D4: rung Z, sizes from the one-byte probe (guardrail 6), on fake probes; no live calls. */
class ZRungTest {
    private val headers = mapOf("User-Agent" to "YftTest/1.0", "Referer" to "https://www.youtube.com/")

    @Test
    fun `a missing size comes from the probe's total and a known size is never asked`() = runTest {
        val http = probing { url -> ExtractorProbeResult.Answered(206, totalOf(url)) }
        val rows = listOf(row(137, size = null), row(140, size = 4_000L), row(251, size = 0L))

        val result = ZRung(http).lookup().fill(rows, headers)

        assertEquals(listOf(Outcome.FILLED, Outcome.KNOWN, Outcome.FILLED), result.outcomes)
        assertEquals(listOf(totalOf(address(137)), 4_000L, totalOf(address(251))), result.rows.map(YouTubeStream::contentLengthBytes))
        assertEquals(listOf(address(137), address(251)), http.probedUrls)
        assertEquals(2, result.probes)
        assertEquals(2, result.filled)
        assertEquals(0, result.missing)
        // Only the size changes.
        assertEquals(rows[0].copy(contentLengthBytes = totalOf(address(137))), result.rows[0])
        assertEquals(rows[1], result.rows[1])
    }

    @Test
    fun `at most 8 probes per lookup over all calls, and a new lookup has its own budget`() = runTest {
        val http = probing { url -> ExtractorProbeResult.Answered(206, totalOf(url)) }
        val z = ZRung(http)
        val lookup = z.lookup()

        val first = lookup.fill((1..5).map { row(it) }, headers)
        val second = lookup.fill((6..10).map { row(it) }, headers)

        assertEquals(5, first.probes)
        assertEquals(3, second.probes)
        assertEquals(listOf(Outcome.FILLED, Outcome.FILLED, Outcome.FILLED, Outcome.BUDGET_SPENT, Outcome.BUDGET_SPENT), second.outcomes)
        assertEquals(2, second.missing)
        assertEquals(ZRung.MAX_PROBES_PER_LOOKUP, lookup.probesUsed)
        assertEquals(8, http.probedUrls.size)

        val next = z.lookup().fill(listOf(row(9), row(10)), headers)
        assertEquals(listOf(Outcome.FILLED, Outcome.FILLED), next.outcomes)
        assertEquals(10, http.probedUrls.size)
    }

    @Test
    fun `only YouTube media-server addresses are asked`() = runTest {
        val http = probing { ExtractorProbeResult.Answered(206, 1_000L) }
        val rows = listOf(
            row(18).copy(url = null, protectedDescriptor = "s=x&sp=sig&url=x"),
            row(22).copy(url = "http://rr1---sn-test.googlevideo.com/videoplayback?itag=22"),
            row(43).copy(url = "https://media.example-cdn.test/videoplayback?itag=43"),
            row(44).copy(url = "https://rr1---sn-test.googlevideo.com/api/manifest/hls_variant/x"),
            row(45).copy(url = "https://user@rr1---sn-test.googlevideo.com/videoplayback?itag=45"),
            row(46).copy(url = "https://googlevideo.com.example-cdn.test/videoplayback?itag=46"),
        )

        val result = ZRung(http).lookup().fill(rows, headers)

        assertEquals(
            listOf(Outcome.NO_ADDRESS) + List(5) { Outcome.NOT_MEDIA_HOST },
            result.outcomes,
        )
        assertTrue(http.probedUrls.isEmpty())
        assertEquals(rows, result.rows)
    }

    @Test
    fun `the probe carries the download's headers without cookies, credentials or a range`() = runTest {
        val http = probing { ExtractorProbeResult.Answered(206, 1_000L) }
        val sent = headers + mapOf(
            "Cookie" to "SID=secret",
            "cookie2" to "x",
            " Authorization " to "Bearer secret",
            "Proxy-Authorization" to "Basic secret",
            "X-Goog-AuthUser" to "0",
            "Range" to "bytes=0-99",
        )

        ZRung(http).lookup().fill(listOf(row(137)), sent)

        assertEquals(listOf(headers), http.probedHeaders)
        assertEquals(listOf(ExtractorHttpClient.DEFAULT_PROBE_TIMEOUT_MILLIS), http.probeTimeouts)
    }

    @Test
    fun `a refusal, no total, a zero total or an error leave the size missing`() = runTest {
        val answers = mapOf(
            address(1) to ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403),
            address(2) to ExtractorProbeResult.Answered(206, null),
            address(3) to ExtractorProbeResult.Answered(200, 0L),
            address(4) to ExtractorProbeResult.Refused(SiteExtractionFailure.NETWORK, null, "IOException"),
        )
        val http = FakeExtractorHttpClient(
            probeResponder = { url, _ -> if (url == address(5)) throw IOException("down") else answers.getValue(url) },
        )

        val result = ZRung(http).lookup().fill((1..5).map { row(it) }, headers)

        assertEquals(listOf(Outcome.REFUSED, Outcome.NO_TOTAL, Outcome.NO_TOTAL, Outcome.NETWORK, Outcome.NETWORK), result.outcomes)
        assertTrue(result.rows.all { it.contentLengthBytes == null })
        assertEquals(5, result.probes)
        assertEquals(5, result.missing)
    }

    @Test
    fun `a probe that does not answer in time leaves the size missing`() = runTest {
        val http = object : ExtractorHttpClient {
            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
                error("no GET")

            override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
                error("no POST")

            override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
                delay(60_000)
                return ExtractorProbeResult.Answered(206, 1_000L)
            }
        }

        val result = ZRung(http, timeoutMillis = 2_000L).lookup().fill(listOf(row(137)), headers)

        assertEquals(listOf(Outcome.NETWORK), result.outcomes)
        assertEquals(1, result.probes)
    }

    @Test
    fun `a client that cannot check files is asked once and costs no probe`() = runTest {
        val http = FakeExtractorHttpClient()

        val result = ZRung(http).lookup().fill(listOf(row(137), row(140)), headers)

        assertEquals(listOf(Outcome.UNSUPPORTED, Outcome.UNSUPPORTED), result.outcomes)
        assertEquals(0, result.probes)
    }

    @Test
    fun `the same address is asked once per lookup`() = runTest {
        val http = probing { url -> ExtractorProbeResult.Answered(206, totalOf(url)) }
        val lookup = ZRung(http).lookup()

        val first = lookup.fill(listOf(row(140), row(140)), headers)
        val again = lookup.fill(listOf(row(140)), headers)

        assertEquals(listOf(Outcome.FILLED, Outcome.FILLED), first.outcomes)
        assertEquals(listOf(Outcome.FILLED), again.outcomes)
        assertEquals(totalOf(address(140)), again.rows.single().contentLengthBytes)
        assertEquals(listOf(address(140)), http.probedUrls)
        assertEquals(0, again.probes)
    }

    @Test
    fun `a result shows no address`() = runTest {
        val http = probing { ExtractorProbeResult.Answered(206, 1_000L) }

        val text = ZRung(http).lookup().fill(listOf(row(137), row(140, size = 5L)), headers).toString()

        assertFalse(text.contains("https") || text.contains("googlevideo") || text.contains("videoplayback"))
    }

    private fun probing(answer: (String) -> ExtractorProbeResult) =
        FakeExtractorHttpClient(probeResponder = { url, _ -> answer(url) })

    private fun address(itag: Int) =
        "https://rr1---sn-test.googlevideo.com/videoplayback?expire=1&itag=$itag&id=o-test"

    private fun totalOf(url: String): Long = 1_000_000L + url.hashCode().toLong().and(0xFFFF)

    private fun row(itag: Int, size: Long? = null) = YouTubeStream(
        itag = itag,
        url = address(itag),
        protectedDescriptor = null,
        mimeType = "video/mp4; codecs=\"avc1.640028\"",
        codecs = listOf("avc1.640028"),
        qualityLabel = "1080p",
        bitrate = 4_000_000L,
        width = 1920,
        height = 1080,
        contentLengthBytes = size,
        hasVideo = true,
        hasAudio = false,
        isDefaultAudio = null,
        isDrc = false,
    )
}
