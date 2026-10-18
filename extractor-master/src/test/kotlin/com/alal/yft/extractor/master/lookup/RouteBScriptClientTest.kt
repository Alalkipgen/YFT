package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import com.alal.yft.extractor.master.solver.OwnSolverEngine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B): the player-script client ([RouteBScriptClient]). The
 * runners fetch the player script with their own client, outside [LookupRecorder] and
 * [RouteBHttpClient]; a 429 there stops route B like any other 429 in the lookup, and Master's
 * fetches are counted. No live calls.
 */
class RouteBScriptClientTest {
    @Test
    fun `a 429 on Master's script fetch stops the runner's other address and every new request after it`() = runTest {
        val http = ScriptHttp(script = LIMITED)
        val runner = RouteBPlayerScripts.wrap(OwnPlayerScriptRunner(RouteBScriptClient(http), NoEngine))
        val record = LookupRecord()
        withContext(record) { LookupRecorder(http).get(MAIN_ASK, emptyMap(), MAX) }
        val route = RouteBLookup(record)

        withContext(record + route) {
            assertEquals(PlayerScriptResult.Failed(SiteExtractionFailure.RATE_LIMITED), runner.resolve(REQUEST))
            assertEquals(RATE_LIMITED, RouteBHttpClient(http).get(FOLLOWING, emptyMap(), MAX))
        }

        // One script fetch went out (the page's player); the desktop build and the module's next request did not.
        assertEquals(listOf(MAIN_ASK, PHONE_PLAYER), http.urls)
        assertTrue(record.rateLimited)
        assertEquals(1, record.masterAsks)
        assertEquals("replayed 0; new 1; refused 2", route.summary())
    }

    @Test
    fun `main's script fetches go out unchanged, and their 429 stops route B`() = runTest {
        val plainHttp = ScriptHttp(script = LIMITED)
        val plain = OwnPlayerScriptRunner(plainHttp, NoEngine).resolve(REQUEST)

        val http = ScriptHttp(script = LIMITED)
        val runner = OwnPlayerScriptRunner(RouteBScriptClient(http), NoEngine)
        val record = LookupRecord()
        val result = withContext(record) {
            LookupRecorder(http).get(MAIN_ASK, emptyMap(), MAX)
            runner.resolve(REQUEST)
        }

        // Main's runner: the same answer and the same fetches (both addresses, same headers) as with the plain client.
        assertEquals(plain, result)
        assertEquals(listOf(PHONE_PLAYER, DESKTOP_PLAYER), plainHttp.urls)
        assertEquals(plainHttp.urls, http.urls.drop(1))
        assertEquals(plainHttp.headers, http.headers.drop(1))
        // Not listed or counted as main's asks; the 429 is the lookup's.
        assertEquals(1, record.mainAsks)
        assertEquals(1, record.asks().size)
        assertTrue(record.rateLimited)

        val route = RouteBLookup(record)
        withContext(record + route) {
            assertEquals(RATE_LIMITED, RouteBHttpClient(http).get(FOLLOWING, emptyMap(), MAX))
            assertEquals(PlayerScriptResult.Failed(SiteExtractionFailure.RATE_LIMITED), RouteBPlayerScripts.wrap(runner).resolve(REQUEST))
        }
        assertEquals(3, http.urls.size)
        assertEquals(0, record.masterAsks)
        assertEquals("replayed 0; new 0; refused 2", route.summary())
    }

    @Test
    fun `without route B it only passes through, and nothing of it is listed`() = runTest {
        val http = ScriptHttp(script = OK)
        val client = RouteBScriptClient(http)
        // No lookup at all (the canary, the census): the inner client's answers, exactly.
        assertEquals(OK, client.get(PHONE_PLAYER, REFERER, MAX))
        assertEquals(OTHER, client.postJson(FOLLOWING, "{}", emptyMap(), MAX))
        assertEquals(ANSWERED, client.probe(MEDIA, emptyMap(), 1_000))

        // Main's lookup: the same, and its record lists none of it.
        val record = LookupRecord()
        withContext(record) {
            assertEquals(OK, client.get(PHONE_PLAYER, REFERER, MAX))
            assertEquals(OTHER, client.postJson(FOLLOWING, "{}", emptyMap(), MAX))
            assertEquals(ANSWERED, client.probe(MEDIA, emptyMap(), 1_000))
        }
        assertEquals(listOf(PHONE_PLAYER, FOLLOWING, MEDIA, PHONE_PLAYER, FOLLOWING, MEDIA), http.urls)
        assertEquals(listOf(REFERER, emptyMap(), emptyMap(), REFERER, emptyMap(), emptyMap()), http.headers)
        assertFalse(record.rateLimited)
        assertEquals(0, record.mainAsks)
        assertEquals(0, record.asks().size)
        assertEquals("RouteBScriptClient", client.toString())
    }

    @Test
    fun `an unlisted answer marks only a 429 or a rate limit, and nothing is listed, counted or kept`() {
        val quiet = listOf<Any>(
            OK,
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404),
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK),
            ANSWERED,
            ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403),
            ExtractorProbeResult.Unsupported,
        )
        val record = LookupRecord()
        quiet.forEach { mark(record, it) }
        assertFalse(record.rateLimited)

        val limited = listOf<Any>(
            LIMITED,
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED),
            ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 429),
            ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED),
        )
        for (answer in limited) {
            val marked = LookupRecord()
            mark(marked, answer)
            assertTrue(answer.toString(), marked.rateLimited)
            assertEquals(0, marked.asks().size)
            assertEquals("LookupRecord(main=0, master=0, kept=0, replayed=0, waiting=0, rateLimited=true)", marked.toString())
        }
    }

    @Test
    fun `in Master's run each script fetch is new and Master's - never replayed, never refused as asked before`() = runTest {
        val http = ScriptHttp(script = OK)
        val client = RouteBScriptClient(http)
        val record = LookupRecord()
        // Main's identical fetch through the adapters' client is kept; a script fetch still goes out.
        withContext(record) { LookupRecorder(http).get(PHONE_PLAYER, REFERER, MAX) }
        val route = RouteBLookup(record)

        withContext(record + route) {
            // The ladder's next run fetches the same script again: it goes out too.
            assertEquals(OK, client.get(PHONE_PLAYER, REFERER, MAX))
            assertEquals(OK, client.get(PHONE_PLAYER, REFERER, MAX))
            // The user's session never goes out.
            assertEquals(NOT_SENT, client.get(DESKTOP_PLAYER, mapOf("Cookie" to "SID=fixture"), MAX))
            assertEquals(ANSWERED, client.probe(MEDIA, emptyMap(), 1_000))
        }
        assertEquals(listOf(PHONE_PLAYER, PHONE_PLAYER, PHONE_PLAYER, MEDIA), http.urls)
        assertEquals("replayed 0; new 3; refused 1", route.summary())
        assertEquals(3, record.masterAsks)
        assertEquals(1, record.keptCount)
        assertEquals(0, record.replayedCount)
        assertTrue(record.asks().drop(1).all { it.asker == LookupRecord.Asker.MASTER && !it.kept })

        // After a 429 in the lookup nothing new goes out, a file check included.
        record.unlisted(LIMITED)
        withContext(record + route) {
            assertEquals(RATE_LIMITED, client.get(PHONE_PLAYER, REFERER, MAX))
            assertEquals(RATE_LIMITED, client.postJson(FOLLOWING, "{}", emptyMap(), MAX))
            assertEquals(ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED), client.probe(MEDIA, emptyMap(), 1_000))
        }
        assertEquals(4, http.urls.size)
        assertEquals("replayed 0; new 3; refused 4", route.summary())
    }

    private fun mark(record: LookupRecord, answer: Any) = when (answer) {
        is ExtractorHttpResult -> record.unlisted(answer)
        is ExtractorProbeResult -> record.unlisted(answer)
        else -> error("not an answer")
    }

    /** Answers a player-script address with [script], any other request with [OTHER], a file check with [ANSWERED]. */
    private class ScriptHttp(private val script: ExtractorHttpResult) : ExtractorHttpClient {
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            urls += url
            this.headers += headers
            return if (url.contains("/s/player/")) script else OTHER
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            urls += url
            this.headers += headers
            return OTHER
        }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            urls += url
            this.headers += headers
            return ANSWERED
        }
    }

    /** The solver itself: a failed download never runs it. */
    private object NoEngine : OwnSolverEngine {
        override val isAvailable: Boolean = true

        override suspend fun run(input: String): String? = error("a failed download never runs the solver")
    }

    private companion object {
        const val MAX = 4L * 1024 * 1024
        const val MAIN_ASK = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val FOLLOWING = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"
        const val PAGE = "https://www.youtube.com/watch?v=Yft0Fixture"
        const val PHONE_PLAYER = "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val DESKTOP_PLAYER = "https://www.youtube.com/s/player/f1x7ure0/player_ias.vflset/en_US/base.js"
        const val MEDIA = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18&id=o-test"
        val REFERER = mapOf("Referer" to PAGE)
        val OK = ExtractorHttpResult.Success(statusCode = 200, body = "var player = 1;", finalUrl = PHONE_PLAYER)
        val OTHER = ExtractorHttpResult.Success(statusCode = 200, body = "{}", finalUrl = MAIN_ASK)
        val LIMITED = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
        val RATE_LIMITED = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED)
        val NOT_SENT = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        val ANSWERED = ExtractorProbeResult.Answered(206, 100L)
        val REQUEST = PlayerScriptRequest(
            playerScriptUrl = PHONE_PLAYER,
            pageUrl = PAGE,
            challenges = listOf(PlayerScriptChallenge("n0", PlayerScriptChallengeKind.RATE_PARAM, "abc")),
        )
    }
}
