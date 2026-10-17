package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B) R2: Master's HTTP wrapper for the YouTube module. Main's
 * identical unauthenticated answers are replayed from the lookup's record, never asked again;
 * authorized requests are refused; after a 429 nothing new goes out; replayed / new / refused are
 * counted. No live calls.
 */
class RouteBHttpClientTest {
    private val player = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private val page = "https://www.youtube.com/watch?v=Yft0Fixture&bpctr=9999999999&has_verified=1"
    private val body = """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"1.0"}},"videoId":"Yft0Fixture"}"""
    private val other = """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"2.0"}},"videoId":"Yft0Fixture"}"""
    private val headers = linkedMapOf("Content-Type" to "application/json", "User-Agent" to "YftTest/1.0")

    @Test
    fun `without a route-B lookup every request passes through, also inside a lookup's record`() = runTest {
        val http = counting()
        val master = RouteBHttpClient(http)
        val record = LookupRecord()

        master.postJson(player, body, headers, MAX)
        withContext(record) {
            master.get(page, headers + ("Cookie" to "SID=fixture"), MAX)
            master.probe(MEDIA, emptyMap(), 1_000)
        }

        assertEquals(1, http.postedUrls.size)
        assertEquals(1, http.requestedUrls.size)
        assertEquals(1, http.probedUrls.size)
        // Only route B lists Master's requests; the record stays main's.
        assertEquals(0, record.masterAsks)
        assertEquals(0, record.mainAsks)
        assertEquals("RouteBHttpClient", master.toString())
    }

    @Test
    fun `main's identical unauthenticated answer is replayed once, then not asked again`() = runTest {
        val http = counting()
        val record = LookupRecord()
        withContext(record) { LookupRecorder(http).postJson(player, body, headers, MAX) }
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        val (first, second) = withContext(record + route) {
            master.postJson(player, body, LinkedHashMap(headers), MAX) to master.postJson(player, body, headers, MAX)
        }

        assertEquals("answer 1", (first as ExtractorHttpResult.Success).body)
        assertEquals(SiteExtractionFailure.NETWORK, (second as ExtractorHttpResult.Failure).reason)
        assertEquals(null, second.statusCode)
        assertEquals(1, http.postedUrls.size) // main's one request only
        assertEquals(1, route.replayed)
        assertEquals(0, route.newRequests)
        assertEquals(1, route.refused)
        assertEquals(0, record.masterAsks)
        assertEquals(1, record.replayedCount)
    }

    @Test
    fun `a kept failure is replayed as it came`() = runTest {
        val http = FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500))
        val record = LookupRecord()
        withContext(record) { LookupRecorder(http).get(page, headers, MAX) }
        val route = RouteBLookup(record)

        val replayed = withContext(record + route) { RouteBHttpClient(http).get(page, headers, MAX) }

        assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500), replayed)
        assertEquals(1, http.requestedUrls.size)
        assertEquals(1, route.replayed)
    }

    @Test
    fun `an endpoint main asked is not asked again when its answer cannot be replayed`() = runTest {
        val http = counting()
        val record = LookupRecord()
        withContext(record) {
            val main = LookupRecorder(http)
            // Main's page with the user's session: listed, never kept.
            main.get(page, headers + ("Cookie" to "SID=fixture"), MAX)
            // Main's player request with one more header than Master's.
            main.postJson(player, body, headers + ("X-Goog-Visitor-Id" to "v"), MAX)
        }
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        val results = withContext(record + route) {
            listOf(master.get(page, headers, MAX), master.postJson(player, body, headers, MAX))
        }

        for (result in results) {
            assertEquals(SiteExtractionFailure.NETWORK, (result as ExtractorHttpResult.Failure).reason)
        }
        assertEquals(1, http.requestedUrls.size)
        assertEquals(1, http.postedUrls.size)
        assertEquals(0, route.replayed)
        assertEquals(0, route.newRequests)
        assertEquals(2, route.refused)
        assertEquals(0, record.masterAsks)
    }

    @Test
    fun `an authorized request is never sent`() = runTest {
        val http = counting()
        val record = LookupRecord()
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)
        val signedIn = listOf(
            headers + ("Cookie" to "SID=fixture"),
            headers + ("authorization" to "SAPISIDHASH fixture"),
            headers + ("Proxy-Authorization" to "Basic fixture"),
        )

        withContext(record + route) {
            for (each in signedIn) {
                assertEquals(SiteExtractionFailure.NETWORK, (master.get(page, each, MAX) as ExtractorHttpResult.Failure).reason)
                assertEquals(SiteExtractionFailure.NETWORK, (master.postJson(player, other, each, MAX) as ExtractorHttpResult.Failure).reason)
                assertEquals(SiteExtractionFailure.NETWORK, (master.probe(MEDIA, each, 1_000) as ExtractorProbeResult.Refused).reason)
            }
        }

        assertTrue(http.requestedUrls.isEmpty() && http.postedUrls.isEmpty() && http.probedUrls.isEmpty())
        assertEquals(9, route.refused)
        assertEquals(0, route.newRequests)
        assertEquals(0, record.masterAsks)
    }

    @Test
    fun `a new endpoint goes out once and is listed as Master's, never kept`() = runTest {
        val http = counting()
        val record = LookupRecord()
        withContext(record) { LookupRecorder(http).postJson(player, body, headers, MAX) }
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        val (sent, again) = withContext(record + route) {
            master.postJson(player, other, headers, MAX) to master.postJson(player, other, headers, MAX)
        }

        assertEquals("answer 2", (sent as ExtractorHttpResult.Success).body)
        assertEquals(SiteExtractionFailure.NETWORK, (again as ExtractorHttpResult.Failure).reason)
        assertEquals(2, http.postedUrls.size)
        assertEquals(1, route.newRequests)
        assertEquals(1, route.refused)
        assertEquals(1, record.masterAsks)
        assertEquals(1, record.keptCount) // main's answer only
        assertEquals(LookupRecord.Asker.MASTER, record.asks().last().asker)
    }

    @Test
    fun `two identical new requests at once - only one goes out`() = runTest {
        val release = CompletableDeferred<Unit>()
        var posted = 0
        val http = object : ExtractorHttpClient {
            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long) = ok("page")

            override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
                posted++
                release.await()
                return ok("answer")
            }
        }
        val record = LookupRecord()
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        withContext(record + route) {
            val first = async { master.postJson(player, other, headers, MAX) }
            yield()
            val second = master.postJson(player, other, headers, MAX)
            assertEquals(SiteExtractionFailure.NETWORK, (second as ExtractorHttpResult.Failure).reason)
            release.complete(Unit)
            assertTrue(first.await() is ExtractorHttpResult.Success)
        }

        assertEquals(1, posted)
        assertEquals(1, route.newRequests)
        assertEquals(1, route.refused)
    }

    @Test
    fun `after main's 429 nothing new goes out, but main's answers are still replayed`() = runTest {
        val http = FakeExtractorHttpClient(
            postResponder = { _, sent -> if (sent == body) ok("answer") else ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 100L) },
        )
        val record = LookupRecord()
        withContext(record) {
            val main = LookupRecorder(http)
            main.postJson(player, body, headers, MAX)
            main.postJson(player, "$body ", headers, MAX)
        }
        assertTrue(record.rateLimited)
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        withContext(record + route) {
            assertEquals("answer", (master.postJson(player, body, headers, MAX) as ExtractorHttpResult.Success).body)
            val refused = master.postJson(player, other, headers, MAX) as ExtractorHttpResult.Failure
            assertEquals(SiteExtractionFailure.RATE_LIMITED, refused.reason)
            assertEquals(null, refused.statusCode)
            assertEquals(SiteExtractionFailure.RATE_LIMITED, (master.get(page, headers, MAX) as ExtractorHttpResult.Failure).reason)
            assertEquals(SiteExtractionFailure.RATE_LIMITED, (master.probe(MEDIA, emptyMap(), 1_000) as ExtractorProbeResult.Refused).reason)
        }

        assertEquals(2, http.postedUrls.size) // main's two
        assertTrue(http.requestedUrls.isEmpty() && http.probedUrls.isEmpty())
        assertEquals(1, route.replayed)
        assertEquals(0, route.newRequests)
        assertEquals(3, route.refused)
    }

    @Test
    fun `after Master's own 429 nothing new goes out`() = runTest {
        val http = FakeExtractorHttpClient(
            postResponder = { _, _ -> ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) },
            getResponder = { _, _ -> ok("page") },
            probeResponder = { _, _ -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 429) },
        )
        val record = LookupRecord()
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        withContext(record + route) {
            assertEquals(429, (master.postJson(player, other, headers, MAX) as ExtractorHttpResult.Failure).statusCode)
            assertEquals(SiteExtractionFailure.RATE_LIMITED, (master.get(page, headers, MAX) as ExtractorHttpResult.Failure).reason)
        }
        assertTrue(http.requestedUrls.isEmpty())

        // A file check's 429 stops new requests too.
        val second = LookupRecord()
        val checks = RouteBLookup(second)
        withContext(second + checks) {
            assertEquals(429, (master.probe(MEDIA, emptyMap(), 1_000) as ExtractorProbeResult.Refused).statusCode)
            assertEquals(SiteExtractionFailure.RATE_LIMITED, (master.probe("$MEDIA&n=2", emptyMap(), 1_000) as ExtractorProbeResult.Refused).reason)
        }
        assertEquals(1, http.probedUrls.size)
        assertEquals(1, checks.newRequests)
        assertEquals(1, checks.refused)
    }

    @Test
    fun `file checks and media reads are never replayed and may repeat until a 429`() = runTest {
        val http = counting()
        val record = LookupRecord()
        withContext(record) { LookupRecorder(http).probe(MEDIA, emptyMap(), 1_000) }
        val route = RouteBLookup(record)
        val master = RouteBHttpClient(http)

        withContext(record + route) {
            assertTrue(master.probe(MEDIA, emptyMap(), 1_000) is ExtractorProbeResult.Answered)
            assertTrue(master.probe(MEDIA, emptyMap(), 1_000) is ExtractorProbeResult.Answered)
            // Media hosts are outside the record's replay (as in the ladder's memo): a playlist or
            // a file's first bytes go out each time the ladder asks within its own bounds.
            assertTrue(master.get(MEDIA, emptyMap(), MAX) is ExtractorHttpResult.Success)
            assertTrue(master.get(MEDIA, emptyMap(), MAX) is ExtractorHttpResult.Success)
        }

        assertEquals(3, http.probedUrls.size)
        assertEquals(2, http.requestedUrls.size)
        assertEquals(4, route.newRequests)
        assertEquals(0, route.replayed)
        assertEquals(4, record.masterAsks)
        assertEquals(0, record.keptCount)
    }

    @Test
    fun `counts and descriptions show no address, body or header`() = runTest {
        val http = counting()
        val record = LookupRecord()
        withContext(record) { LookupRecorder(http).postJson(player, body, headers, MAX) }
        val route = RouteBLookup(record)
        withContext(record + route) {
            val master = RouteBHttpClient(http)
            master.postJson(player, body, headers, MAX)
            master.postJson(player, other, headers, MAX)
            master.get(page, headers + ("Cookie" to "SID=fixture"), MAX)
        }

        assertEquals("replayed 1; new 1; refused 1", route.summary())
        assertEquals("RouteBLookup(replayed=1, new=1, refused=1)", route.toString())
        for (text in listOf(route.summary(), route.toString(), record.toString())) {
            assertFalse(text, listOf("youtube", "Yft0Fixture", "VISIONOS", "SID", "YftTest").any { it in text })
        }
        // Without a refusal the summary says only what was replayed and what was new.
        assertEquals("replayed 0; new 0", RouteBLookup(LookupRecord()).summary())
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
