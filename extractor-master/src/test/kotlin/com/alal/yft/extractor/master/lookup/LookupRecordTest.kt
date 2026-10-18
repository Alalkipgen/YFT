package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.4 item 7 (YouTube routing B) R1: the per-lookup request record; no live calls. */
class LookupRecordTest {
    private val player = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private val page = "https://www.youtube.com/watch?v=Yft0Fixture&bpctr=9999999999&has_verified=1"
    private val body = """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"1.0"}},"videoId":"Yft0Fixture"}"""
    private val headers = linkedMapOf("Content-Type" to "application/json", "User-Agent" to "YftTest/1.0")

    @Test
    fun `without a record the recorder only passes through`() = runTest {
        val http = counting()
        val recorder = LookupRecorder(http)
        val record = LookupRecord()

        val result = recorder.postJson(player, body, headers, MAX)

        assertEquals("answer 1", (result as ExtractorHttpResult.Success).body)
        assertEquals(1, http.postedUrls.size)
        assertEquals(0, record.mainAsks)
        assertNull(record.replay(LookupAsk(LookupAsk.POST, player, body, headers), MAX))
        assertEquals("LookupRecorder", recorder.toString())
    }

    @Test
    fun `each lookup's record sees only its own asks, also when two run at once`() = runTest {
        val bothAsked = CompletableDeferred<Unit>()
        var waiting = 0
        val http = object : ExtractorHttpClient {
            var calls = 0
            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
                if (++waiting == 2) bothAsked.complete(Unit)
                bothAsked.await()
                return ok("answer ${++calls}")
            }

            override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long) =
                ok("answer ${++calls}")
        }
        val recorder = LookupRecorder(http)
        val first = LookupRecord()
        val second = LookupRecord()

        coroutineScope {
            listOf(
                async(first) {
                    recorder.get(page, headers, MAX)
                    recorder.postJson(player, body, headers, MAX)
                },
                async(second) { recorder.get("$page&n=2", headers, MAX) },
            ).awaitAll()
        }

        assertEquals(2, first.mainAsks)
        assertEquals(1, second.mainAsks)
        assertTrue(first.asked(LookupAsk(LookupAsk.GET, page, null, headers)))
        assertFalse(second.asked(LookupAsk(LookupAsk.GET, page, null, headers)))
        assertNull(second.replay(LookupAsk(LookupAsk.POST, player, body, headers), MAX))
        assertNotNull(first.replay(LookupAsk(LookupAsk.POST, player, body, headers), MAX))
        assertNotNull(second.replay(LookupAsk(LookupAsk.GET, "$page&n=2", null, headers), MAX))
    }

    @Test
    fun `only an identical unauthenticated request gets main's answer, once`() = runTest {
        val record = LookupRecord()
        withContext(record) { LookupRecorder(counting()).postJson(player, body, headers, MAX) }

        val reordered = linkedMapOf("User-Agent" to "YftTest/1.0", "Content-Type" to "application/json")
        val others = listOf(
            LookupAsk(LookupAsk.GET, player, null, headers), // another method
            LookupAsk(LookupAsk.POST, "$player&x=1", body, headers), // another address
            LookupAsk(LookupAsk.POST, player, "$body ", headers), // another body
            LookupAsk(LookupAsk.POST, player, body, headers + ("User-Agent" to "YftTest/2.0")), // another value
            LookupAsk(LookupAsk.POST, player, body, headers + ("X-Goog-Visitor-Id" to "v")), // one more header
            LookupAsk(LookupAsk.POST, player, body, headers - "User-Agent"), // one header less
            LookupAsk(LookupAsk.POST, player, body, reordered), // another order
            LookupAsk(LookupAsk.POST, player, body, headers.mapKeys { it.key.lowercase() }), // other name case
        )
        for (other in others) assertNull(other.toString(), record.replay(other, MAX))

        val identical = LookupAsk(LookupAsk.POST, player, body, LinkedHashMap(headers))
        assertEquals("answer 1", (record.replay(identical, MAX) as ExtractorHttpResult.Success).body)
        assertNull(record.replay(identical, MAX))
        assertEquals(1, record.keptCount)
        assertEquals(1, record.replayedCount)
    }

    @Test
    fun `an authorized request is recorded and counts as asked, but is never kept or replayed`() = runTest {
        val record = LookupRecord()
        val recorder = LookupRecorder(counting())
        val signedIn = listOf(
            headers + ("Cookie" to "SID=fixture"),
            headers + ("authorization" to "SAPISIDHASH fixture"),
            headers + ("Proxy-Authorization" to "Basic fixture"),
        )
        withContext(record) { for (each in signedIn) recorder.get(page, each, MAX) }

        for (each in signedIn) {
            val ask = LookupAsk(LookupAsk.GET, page, null, each)
            assertTrue(ask.authorized)
            assertNull(record.replay(ask, MAX))
            assertTrue(record.asked(ask))
        }
        assertEquals(3, record.mainAsks)
        assertEquals(0, record.keptCount)
        assertTrue(record.asks().all { it.authorized && !it.kept })
        // A blank cookie carries no session: that answer is kept like any other.
        val blank = headers + ("Cookie" to " ")
        assertFalse(LookupAsk(LookupAsk.GET, page, null, blank).authorized)
        withContext(record) { recorder.get(page, blank, MAX) }
        assertNotNull(record.replay(LookupAsk(LookupAsk.GET, page, null, blank), MAX))
    }

    @Test
    fun `a kept success drops the cookies its answer set`() = runTest {
        val cookie = ResponseCookie("YSC", "fixture", "youtube.com", hostOnly = false)
        val http = FakeExtractorHttpClient(postResponder = { _, _ -> ok("answer").copy(cookies = listOf(cookie)) })
        val record = LookupRecord()
        val asked = withContext(record) { LookupRecorder(http).postJson(player, body, headers, MAX) }

        val replayed = record.replay(LookupAsk(LookupAsk.POST, player, body, headers), MAX) as ExtractorHttpResult.Success

        assertEquals(listOf(cookie), (asked as ExtractorHttpResult.Success).cookies) // main got its answer as it came
        assertEquals("answer", replayed.body)
        assertTrue(replayed.cookies.isEmpty())
    }

    @Test
    fun `main's failure is kept without a body and replayed once`() = runTest {
        val failure = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500)
        val record = LookupRecord()
        withContext(record) { LookupRecorder(FakeExtractorHttpClient(fallback = failure)).postJson(player, body, headers, MAX) }

        val ask = LookupAsk(LookupAsk.POST, player, body, headers)
        assertSame(failure, record.replay(ask, MAX))
        assertNull(record.replay(ask, MAX))
        assertFalse(record.rateLimited)
        assertEquals(SiteExtractionFailure.HTTP_STATUS, record.asks().single().failure)
        assertEquals(500, record.asks().single().statusCode)
    }

    @Test
    fun `a 429 is seen by status or by reason, from an answer or a file check`() = runTest {
        val limits = listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED),
        )
        for (limit in limits) {
            val record = LookupRecord()
            withContext(record) { LookupRecorder(FakeExtractorHttpClient(fallback = limit)).get(page, headers, MAX) }
            assertTrue(limit.toString(), record.rateLimited)
            assertTrue(record.asks().single().rateLimited)
        }
        val probed = LookupRecord()
        val refused = FakeExtractorHttpClient(
            probeResponder = { _, _ -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 429) },
        )
        withContext(probed) { LookupRecorder(refused).probe(MEDIA, emptyMap(), 5_000) }
        assertTrue(probed.rateLimited)

        val other = LookupRecord()
        withContext(other) {
            LookupRecorder(FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 503)))
                .get(page, headers, MAX)
            LookupRecorder(counting()).get(page, headers, MAX)
        }
        assertFalse(other.rateLimited)
    }

    @Test
    fun `media hosts, plain http and file checks are never kept`() = runTest {
        val record = LookupRecord()
        val recorder = LookupRecorder(counting())
        withContext(record) {
            recorder.get(MEDIA, headers, MAX)
            recorder.get("http://www.youtube.com/watch?v=Yft0Fixture", headers, MAX)
            recorder.probe(MEDIA, headers, 5_000)
            recorder.probe(page, headers, 5_000)
        }

        assertEquals(4, record.mainAsks)
        assertEquals(0, record.keptCount)
        assertNull(record.replay(LookupAsk(LookupAsk.GET, MEDIA, null, headers), MAX))
        assertNull(record.replay(LookupAsk(LookupAsk.PROBE, page, null, headers), MAX))
        assertEquals(listOf("GET", "GET", "PROBE", "PROBE"), record.asks().map { it.method })
        // A file check and a GET of the same address are different endpoints.
        assertTrue(record.asked(LookupAsk(LookupAsk.PROBE, page, null, emptyMap())))
        assertFalse(record.asked(LookupAsk(LookupAsk.GET, page, null, emptyMap())))
    }

    @Test
    fun `asked names every endpoint asked whatever its headers`() = runTest {
        val record = LookupRecord()
        withContext(record) { LookupRecorder(counting()).postJson(player, body, headers, MAX) }

        assertTrue(record.asked(LookupAsk(LookupAsk.POST, player, body, emptyMap())))
        assertTrue(record.asked(LookupAsk(LookupAsk.POST, player, body, headers + ("Cookie" to "SID=fixture"))))
        assertFalse(record.asked(LookupAsk(LookupAsk.POST, player, "$body ", headers)))
        assertFalse(record.asked(LookupAsk(LookupAsk.GET, player, null, headers)))
        // Master's own asks count as asked too.
        record.record(LookupAsk(LookupAsk.GET, page, null, emptyMap()), ok("master"), record.now(), LookupRecord.Asker.MASTER)
        assertTrue(record.asked(LookupAsk(LookupAsk.GET, page, null, headers)))
        assertEquals(1, record.mainAsks)
        assertEquals(1, record.masterAsks)
        // Master's answers are never kept for a replay.
        assertNull(record.replay(LookupAsk(LookupAsk.GET, page, null, emptyMap()), MAX))
    }

    @Test
    fun `a kept answer over the asker's body limit replays as too large`() = runTest {
        val record = LookupRecord()
        val kept = withContext(record) { LookupRecorder(counting()).postJson(player, body, headers, MAX) } as ExtractorHttpResult.Success

        val replayed = record.replay(LookupAsk(LookupAsk.POST, player, body, headers), kept.body.length.toLong() - 1)

        assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, 200), replayed)
        assertEquals(1, record.replayedCount)
    }

    @Test
    fun `bounded - at most maxKept answers and maxKeptChars characters, at most MAX_LISTED asks listed`() = runTest {
        val few = LookupRecord(maxKept = 2)
        withContext(few) { repeat(3) { LookupRecorder(counting()).postJson("$player&n=$it", body, headers, MAX) } }
        assertEquals(2, few.keptCount)
        assertNull(few.replay(LookupAsk(LookupAsk.POST, "$player&n=2", body, headers), MAX))

        val small = LookupRecord(maxKeptChars = 10)
        val recorder = LookupRecorder(counting())
        withContext(small) { repeat(2) { recorder.postJson(player, body, headers, MAX) } }
        assertEquals(1, small.keptCount) // "answer N" is 8 characters

        val many = LookupRecord()
        withContext(many) { repeat(LookupRecord.MAX_LISTED + 5) { LookupRecorder(counting()).get(MEDIA, headers, MAX) } }
        assertEquals(LookupRecord.MAX_LISTED + 5, many.mainAsks)
        assertEquals(LookupRecord.MAX_LISTED, many.asks().size)
    }

    @Test
    fun `when each ask started and how long it took, the time so far and the time left`() = runTest {
        var now = 1_000L
        val record = LookupRecord(clock = { now })
        val slow = FakeExtractorHttpClient(getResponder = { _, _ -> now += 30; ok("answer") })
        now += 5
        withContext(record) { LookupRecorder(slow).get(page, headers, MAX) }

        val ask = record.asks().single()
        assertEquals(5, ask.atMillis)
        assertEquals(30, ask.tookMillis)
        assertEquals(35, record.elapsedMillis())
        assertEquals(65, record.timeLeftMillis(100))
        assertEquals(0, record.timeLeftMillis(10))
    }

    @Test
    fun `an ask that never answered still counts as asked`() = runTest {
        val hanging = object : ExtractorHttpClient {
            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
                awaitCancellation()

            override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long) =
                awaitCancellation()
        }
        val record = LookupRecord()
        val job = launch(record) { LookupRecorder(hanging).get(page, headers, MAX) }
        yield()
        job.cancel()
        job.join()

        assertTrue(record.asked(LookupAsk(LookupAsk.GET, page, null, headers)))
        assertFalse(record.asks().single().answered)
        assertEquals(0, record.keptCount)
    }

    @Test
    fun `F2 - a reused answer is noted, the latest note stays, nothing else changes`() {
        val record = LookupRecord()
        assertNull(record.reuse)

        record.noteReuse(LookupRecord.Reuse.REMEMBERED)
        assertEquals(LookupRecord.Reuse.REMEMBERED, record.reuse)
        record.noteReuse(LookupRecord.Reuse.JOINED)
        assertEquals(LookupRecord.Reuse.JOINED, record.reuse)

        assertEquals(0, record.mainAsks)
        assertTrue(record.asks().isEmpty())
        assertEquals("LookupRecord(main=0, master=0, kept=0, replayed=0, waiting=0, rateLimited=false)", record.toString())
        assertEquals("its remembered answer (P17)", LookupRecord.Reuse.REMEMBERED.label)
        assertEquals("another caller's running lookup (P17)", LookupRecord.Reuse.JOINED.label)
    }

    @Test
    fun `clear drops the kept answers, the counts stay`() = runTest {
        val record = LookupRecord()
        withContext(record) { LookupRecorder(counting()).postJson(player, body, headers, MAX) }

        record.clear()

        assertNull(record.replay(LookupAsk(LookupAsk.POST, player, body, headers), MAX))
        assertEquals(1, record.mainAsks)
        assertEquals(1, record.keptCount)
        assertTrue(record.asked(LookupAsk(LookupAsk.POST, player, body, headers)))
    }

    @Test
    fun `nothing shows an address, a body or a header`() = runTest {
        val record = LookupRecord()
        val signedIn = headers + ("Cookie" to "SID=fixture")
        withContext(record) {
            LookupRecorder(counting()).postJson(player, body, headers, MAX)
            LookupRecorder(counting()).get(page, signedIn, MAX)
        }
        val ask = LookupAsk(LookupAsk.GET, page, null, signedIn)

        val text = listOf(record, ask, record.asks()).joinToString(" ")

        // The bodies main got read "answer 1" (the listed field "answered" is no body).
        for (secret in listOf("youtube", "Yft0Fixture", "VISIONOS", "YftTest", "SID", "fixture", "answer 1", "json")) {
            assertFalse(secret, text.contains(secret, ignoreCase = true))
        }
        assertEquals("LookupRecord(main=2, master=0, kept=1, replayed=0, waiting=1, rateLimited=false)", record.toString())
        assertEquals("LookupAsk(GET #${ask.hash}, authorized)", ask.toString())
        assertEquals(LookupAsk.HASH_CHARS, ask.hash.length)
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
        const val MEDIA = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18&id=o-test"
    }
}
