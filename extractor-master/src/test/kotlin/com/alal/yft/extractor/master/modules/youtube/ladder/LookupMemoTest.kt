package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D6: the same-lookup memo (guardrail 7) on a fake client; no live calls. */
class LookupMemoTest {
    private val player = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private val page = "https://www.youtube.com/watch?v=Yft0Fixture&bpctr=9999999999&has_verified=1"
    private val body = """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"1.0"}},"videoId":"Yft0Fixture"}"""
    private val headers = linkedMapOf("Content-Type" to "application/json", "User-Agent" to "YftTest/1.0", "X-YouTube-Client-Name" to "101")

    @Test
    fun `a chain request byte-identical to the ladder's is answered from memory once`() = runTest {
        val http = counting()
        val memo = LookupMemo(http)

        val ladder = memo.ladder.postJson(player, body, headers, MAX)
        val first = memo.chain.postJson(player, body, LinkedHashMap(headers), MAX)
        val second = memo.chain.postJson(player, body, headers, MAX)

        assertSame(ladder, first)
        assertEquals(2, http.postedUrls.size) // the ladder's request and the chain's second one
        assertEquals("answer 2", (second as ExtractorHttpResult.Success).body)
        assertEquals(1, memo.keptCount)
        assertEquals(1, memo.servedCount)
    }

    @Test
    fun `the chain's page fetch the ladder already made is answered from memory once`() = runTest {
        val http = counting()
        val memo = LookupMemo(http)
        val pageHeaders = mapOf("User-Agent" to "YftTest/1.0", "Accept-Language" to "en")

        val ladder = memo.ladder.get(page, pageHeaders, MAX)
        val chain = memo.chain.get(page, pageHeaders, MAX)
        memo.chain.get(page, pageHeaders, MAX)

        assertSame(ladder, chain)
        assertEquals(listOf(page, page), http.requestedUrls)
    }

    @Test
    fun `any difference in method, address, body or headers goes out`() = runTest {
        val http = counting()
        val memo = LookupMemo(http)
        memo.ladder.postJson(player, body, headers, MAX)

        val reordered = linkedMapOf("User-Agent" to "YftTest/1.0", "Content-Type" to "application/json", "X-YouTube-Client-Name" to "101")
        memo.chain.get(player, headers, MAX) // another method
        memo.chain.postJson("$player&x=1", body, headers, MAX) // another address
        memo.chain.postJson(player, "$body ", headers, MAX) // another body
        memo.chain.postJson(player, body, headers + ("X-YouTube-Client-Name" to "102"), MAX) // another value
        memo.chain.postJson(player, body, headers + ("X-Goog-Visitor-Id" to "v"), MAX) // one more header
        memo.chain.postJson(player, body, headers - "User-Agent", MAX) // one header less
        memo.chain.postJson(player, body, reordered, MAX) // another order
        memo.chain.postJson(player, body, headers.mapKeys { it.key.lowercase() }, MAX) // other name case

        assertEquals(0, memo.servedCount)
        assertEquals(1 + 7, http.postedUrls.size)
        assertEquals(1, http.requestedUrls.size)
        // The kept answer still waits for its identical request.
        memo.chain.postJson(player, body, headers, MAX)
        assertEquals(1, memo.servedCount)
    }

    @Test
    fun `media hosts are never kept or served, and file checks always go out`() = runTest {
        val http = counting()
        val memo = LookupMemo(http)
        val media = listOf(
            "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18&id=o-test",
            "https://manifest.googlevideo.com/api/manifest/hls_variant/id/o-test/file/index.m3u8",
            "https://googlevideo.com/videoplayback?itag=18",
            "https://RR1---SN-TEST.GOOGLEVIDEO.COM./videoplayback?itag=18",
        )
        for (url in media) {
            memo.ladder.get(url, headers, MAX)
            memo.chain.get(url, headers, MAX)
            memo.ladder.probe(url, headers, 5_000)
            memo.chain.probe(url, headers, 5_000)
        }

        assertEquals(0, memo.keptCount)
        assertEquals(0, memo.servedCount)
        assertEquals(media.flatMap { listOf(it, it) }, http.requestedUrls)
        assertEquals(media.flatMap { listOf(it, it) }, http.probedUrls)
        assertTrue(media.none(LookupMemo::memoable))
        assertFalse(LookupMemo.memoable("http://www.youtube.com/watch?v=x"))
        assertTrue(LookupMemo.memoable(player))
    }

    @Test
    fun `a failed ladder answer is not kept, so the chain asks again`() = runTest {
        val refused = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429)
        var calls = 0
        val http = FakeExtractorHttpClient(postResponder = { _, _ -> calls++; if (calls == 1) refused else ok("answer $calls") })
        val memo = LookupMemo(http)

        assertSame(refused, memo.ladder.postJson(player, body, headers, MAX))
        val chain = memo.chain.postJson(player, body, headers, MAX)

        assertEquals("answer 2", (chain as ExtractorHttpResult.Success).body)
        assertEquals(0, memo.keptCount)
    }

    @Test
    fun `only the ladder's answers are kept, and the ladder itself always asks`() = runTest {
        val http = counting()
        val memo = LookupMemo(http)

        memo.chain.postJson(player, body, headers, MAX)
        memo.chain.postJson(player, body, headers, MAX)
        memo.ladder.postJson(player, body, headers, MAX)
        memo.ladder.postJson(player, body, headers, MAX)

        assertEquals(4, http.postedUrls.size)
        assertEquals(2, memo.keptCount)
        // Two kept answers serve two identical chain requests, one each, in order.
        assertEquals("answer 3", (memo.chain.postJson(player, body, headers, MAX) as ExtractorHttpResult.Success).body)
        assertEquals("answer 4", (memo.chain.postJson(player, body, headers, MAX) as ExtractorHttpResult.Success).body)
        assertEquals(4, http.postedUrls.size)
    }

    @Test
    fun `never across lookups`() = runTest {
        val http = counting()
        val first = LookupMemo(http)
        first.ladder.postJson(player, body, headers, MAX)

        val second = LookupMemo(http)
        second.chain.postJson(player, body, headers, MAX)
        first.clear()
        first.chain.postJson(player, body, headers, MAX)

        assertEquals(0, second.servedCount)
        assertEquals(0, first.servedCount)
        assertEquals(3, http.postedUrls.size)
    }

    @Test
    fun `a kept answer larger than the chain's body limit is not served`() = runTest {
        val http = counting()
        val memo = LookupMemo(http)
        val kept = memo.ladder.postJson(player, body, headers, MAX) as ExtractorHttpResult.Success

        memo.chain.postJson(player, body, headers, kept.body.length.toLong() - 1)

        assertEquals(0, memo.servedCount)
        assertEquals(2, http.postedUrls.size)
    }

    @Test
    fun `at most maxEntries answers and maxKeptChars characters are kept`() = runTest {
        val http = counting()
        val memo = LookupMemo(http, maxEntries = 2)
        repeat(3) { memo.ladder.postJson("$player&n=$it", body, headers, MAX) }
        assertEquals(2, memo.keptCount)

        val small = LookupMemo(http, maxKeptChars = 10)
        small.ladder.postJson(player, body, headers, MAX)
        small.ladder.postJson(player, body, headers, MAX)
        assertEquals(1, small.keptCount) // "answer N" is 8 or 9 characters
    }

    @Test
    fun `the memo shows counts only`() = runTest {
        val memo = LookupMemo(counting())
        memo.ladder.postJson(player, body, headers, MAX)
        memo.chain.postJson(player, body, headers, MAX)

        val text = listOf(memo, memo.ladder, memo.chain).joinToString(" ")

        assertFalse(text.contains("youtube") || text.contains("VISIONOS") || text.contains("YftTest") || text.contains("answer"))
        assertEquals("LookupMemo(kept=1, served=1, waiting=0)", memo.toString())
    }

    private fun counting(): FakeExtractorHttpClient {
        var calls = 0
        return FakeExtractorHttpClient(
            postResponder = { _, _ -> ok("answer ${++calls}") },
            getResponder = { _, _ -> ok("answer ${++calls}") },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 100L) },
        )
    }

    private fun ok(text: String) = ExtractorHttpResult.Success(statusCode = 200, body = text, finalUrl = player, contentType = "application/json")

    private companion object {
        const val MAX = 2L * 1024 * 1024
    }
}
