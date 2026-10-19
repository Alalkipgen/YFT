package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S0a: a record given the census's labels writes each ask's way, its 2xx answer's
 * reading and shape, and counts repeated asks, asks after a stop and bot checks; a record given
 * none (every lookup of the app, route B included) is as before. No live calls.
 */
class LookupRecordLabelsTest {
    private val page = "https://www.example.test/post/1"
    private val api = "https://api.example.test/item?id=1"
    private val headers = linkedMapOf("User-Agent" to "YftTest/1.0", "Accept" to "text/html")

    /** Labels by host; a page answer is read as data unless it says "challenge". */
    private class TestLabels : AskLabels {
        var reads = 0

        override fun way(ask: LookupAsk): String? = when {
            ask.method == LookupAsk.PROBE -> "file check"
            ask.url.contains("www.example.test") -> "page"
            ask.url.contains("api.example.test") -> "api"
            else -> null
        }

        override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? {
            reads++
            if (way != "page") return null
            val mark = if (result.body.contains("challenge")) AnswerMark.BOT_CHECK else AnswerMark.DATA
            return AskReading(mark, "h0000beef")
        }
    }

    @Test
    fun `without labels the record is as before - no label, nothing more counted`() = runTest {
        val record = LookupRecord()
        val limited = FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429))
        withContext(record) {
            LookupRecorder(serving()).get(page, headers, MAX)
            LookupRecorder(serving()).get(page, headers, MAX)
            LookupRecorder(limited).get(api, headers, MAX)
            LookupRecorder(serving()).get(api, headers, MAX)
            LookupRecorder(probing(206)).probe(MEDIA, emptyMap(), 5_000)
        }
        record.unanswered(LookupAsk(LookupAsk.GET, page, null, headers), record.now())

        assertEquals(6, record.mainAsks)
        assertTrue(record.rateLimited)
        assertEquals(6, record.asks().size)
        for (ask in record.asks()) {
            assertNull(ask.way)
            assertNull(ask.mark)
            assertNull(ask.shape)
            assertFalse(ask.repeated)
            assertFalse(ask.afterStop)
        }
        assertEquals(0, record.repeatedAsks)
        assertEquals(0, record.asksAfterStop)
        assertEquals(0, record.botChecks)
        assertEquals(emptyList<String>(), record.stopHosts())
        assertEquals(
            "LookupRecord(main=6, master=0, kept=4, replayed=0, waiting=4, rateLimited=true)",
            record.toString(),
        )
    }

    @Test
    fun `a 2xx answer gets its way, reading and shape, any other ask its way alone`() = runTest {
        val labels = TestLabels()
        val record = LookupRecord(labels = labels)
        val missing = FakeExtractorHttpClient()
        withContext(record) {
            LookupRecorder(serving()).get(page, headers, MAX)
            LookupRecorder(missing).get(api, headers, MAX)
            LookupRecorder(serving()).postJson(api, "{}", headers, MAX)
            LookupRecorder(probing(206)).probe(MEDIA, emptyMap(), 5_000)
            LookupRecorder(serving()).get(OTHER, headers, MAX)
        }
        record.unanswered(LookupAsk(LookupAsk.GET, page, "body", headers), record.now())

        val asks = record.asks()
        assertEquals(listOf("page", "api", "api", "file check", null, "page"), asks.map { it.way })
        assertEquals(listOf(AnswerMark.DATA, null, null, null, null, null), asks.map { it.mark })
        assertEquals(listOf("h0000beef", null, null, null, null, null), asks.map { it.shape })
        // Read only for the three 2xx answers (the api's and the other host's say nothing).
        assertEquals(3, labels.reads)
        assertEquals(404, asks[1].statusCode)
        assertEquals(SiteExtractionFailure.HTTP_STATUS, asks[1].failure)
        assertEquals(6, record.mainAsks)
    }

    @Test
    fun `an endpoint asked again is a repeated ask, whatever its headers`() = runTest {
        val record = LookupRecord(labels = TestLabels())
        withContext(record) {
            val http = LookupRecorder(serving())
            http.get(page, headers, MAX)
            http.get(page, linkedMapOf("User-Agent" to "Other/2.0"), MAX)
            http.postJson(api, "{\"a\":1}", headers, MAX)
            http.postJson(api, "{\"a\":2}", headers, MAX)
            http.postJson(api, "{\"a\":1}", headers, MAX)
        }

        assertEquals(listOf(false, true, false, false, true), record.asks().map { it.repeated })
        assertEquals(2, record.repeatedAsks)
    }

    @Test
    fun `a 429 stops its host - a later ask there is after the stop, one already running is not`() {
        var now = 1_000L
        val record = LookupRecord(clock = { now }, labels = TestLabels())
        val limit = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429)

        now = 1_100
        record.record(LookupAsk(LookupAsk.GET, page, null, headers), limit, startedAtMillis = 1_050)
        now = 1_150
        // Sent at 1 090, before the 429 came.
        record.record(LookupAsk(LookupAsk.GET, "$page?b", null, headers), ok("b"), startedAtMillis = 1_090)
        now = 1_200
        record.record(LookupAsk(LookupAsk.GET, "$page?c", null, headers), ok("c"), startedAtMillis = 1_120)
        record.record(LookupAsk(LookupAsk.GET, api, null, headers), ok("d"), startedAtMillis = 1_130)
        record.record(LookupAsk(LookupAsk.PROBE, "https://WWW.Example.TEST./video.mp4", null, emptyMap()),
            ExtractorProbeResult.Answered(206), startedAtMillis = 1_140)

        assertEquals(listOf(false, false, true, false, true), record.asks().map { it.afterStop })
        assertEquals(2, record.asksAfterStop)
        assertEquals(listOf("www.example.test"), record.stopHosts())
        assertTrue(record.rateLimited)
        assertTrue(record.asks().first().rateLimited)
        assertEquals(50, record.asks().first().tookMillis)
    }

    @Test
    fun `a rate-limited file check stops its host too`() = runTest {
        val record = LookupRecord(labels = TestLabels())
        val refused = FakeExtractorHttpClient(
            probeResponder = { _, _ -> ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED) },
        )
        withContext(record) {
            LookupRecorder(refused).probe(MEDIA, emptyMap(), 5_000)
            LookupRecorder(probing(206)).probe(MEDIA, emptyMap(), 5_000)
        }

        assertEquals(listOf("cdn.example.test"), record.stopHosts())
        assertEquals(listOf(false, true), record.asks().map { it.afterStop })
        assertEquals(listOf(false, true), record.asks().map { it.repeated })
    }

    @Test
    fun `a bot check stops its host and is counted, but is no rate limit`() = runTest {
        val record = LookupRecord(labels = TestLabels())
        val challenge = FakeExtractorHttpClient(getResponder = { url, _ -> FakeExtractorHttpClient.html("challenge", url) })
        withContext(record) {
            LookupRecorder(challenge).get(page, headers, MAX)
            LookupRecorder(serving()).get("$page?again", headers, MAX)
            LookupRecorder(serving()).get(api, headers, MAX)
        }

        assertEquals(1, record.botChecks)
        assertEquals(AnswerMark.BOT_CHECK, record.asks().first().mark)
        assertEquals(listOf("www.example.test"), record.stopHosts())
        assertEquals(listOf(false, true, false), record.asks().map { it.afterStop })
        assertFalse(record.rateLimited)
        assertFalse(record.asks().first().rateLimited)
    }

    @Test
    fun `labels that throw are no labels, and the ask is still recorded`() = runTest {
        val throwing = object : AskLabels {
            override fun way(ask: LookupAsk): String = error("way")

            override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading =
                throw IllegalStateException("read")
        }
        val record = LookupRecord(labels = throwing)
        withContext(record) {
            LookupRecorder(serving()).get(page, headers, MAX)
            LookupRecorder(serving()).get(page, headers, MAX)
        }

        assertEquals(2, record.mainAsks)
        assertEquals(listOf<String?>(null, null), record.asks().map { it.way })
        assertEquals(listOf<AnswerMark?>(null, null), record.asks().map { it.mark })
        // Still a labelled record: the repeat is counted.
        assertEquals(1, record.repeatedAsks)
    }

    @Test
    fun `a cancellation in a label is not swallowed`() {
        val cancelling = object : AskLabels {
            override fun way(ask: LookupAsk): String = throw CancellationException("cancelled")
        }
        val record = LookupRecord(labels = cancelling)

        assertThrows(CancellationException::class.java) {
            record.record(LookupAsk(LookupAsk.GET, page, null, headers), ok("a"), record.now())
        }
    }

    @Test
    fun `labels change no keeping or replay`() = runTest {
        val labelled = LookupRecord(labels = TestLabels())
        val plain = LookupRecord()
        for (record in listOf(labelled, plain)) {
            withContext(record) { LookupRecorder(serving()).get(page, headers, MAX) }
        }
        val ask = LookupAsk(LookupAsk.GET, page, null, headers)

        assertEquals(plain.asks().single().kept, labelled.asks().single().kept)
        assertTrue(labelled.asks().single().kept)
        val replayed = labelled.replay(ask, MAX) as ExtractorHttpResult.Success
        assertEquals((plain.replay(ask, MAX) as ExtractorHttpResult.Success).body, replayed.body)
        assertNull(labelled.replay(ask, MAX))
        assertEquals(1, labelled.replayedCount)
    }

    @Test
    fun `asks over the listed cap are still counted`() = runTest {
        val record = LookupRecord(labels = TestLabels())
        withContext(record) {
            val http = LookupRecorder(serving())
            repeat(LookupRecord.MAX_LISTED + 6) { http.get(page, headers, MAX) }
        }

        assertEquals(LookupRecord.MAX_LISTED, record.asks().size)
        assertEquals(LookupRecord.MAX_LISTED + 6, record.mainAsks)
        assertEquals(LookupRecord.MAX_LISTED + 5, record.repeatedAsks)
    }

    @Test
    fun `the record's own labels instance is the one asked`() = runTest {
        val labels = TestLabels()
        val record = LookupRecord(labels = labels)
        withContext(record) { LookupRecorder(serving()).get(page, headers, MAX) }

        assertEquals(1, labels.reads)
        assertNotNull(record.asks().single().way)
        assertSame(AnswerMark.DATA, record.asks().single().mark)
    }

    private fun serving() = FakeExtractorHttpClient(getResponder = { url, _ -> ok(url) }, postResponder = { url, _ -> ok(url) })

    private fun probing(status: Int) = FakeExtractorHttpClient(probeResponder = { _, _ -> ExtractorProbeResult.Answered(status) })

    private fun ok(body: String) = FakeExtractorHttpClient.html(body, page)

    private companion object {
        const val MAX = 1_048_576L
        const val MEDIA = "https://cdn.example.test/video.mp4"
        const val OTHER = "https://elsewhere.example.test/x"
    }
}
