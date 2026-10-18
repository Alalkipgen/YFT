package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.lookup.RouteBMediaStartReader
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.4 item 7 (YouTube routing B) R1–R2: the app's wiring of the recorder and of route B's Master wrappers. */
class YouTubeRouteBTest {
    /**
     * Item 7 R4: the trial acts only with route B on (debug, `-Pyft.routeBTrial`); the browser's
     * notice is route B's line of rows Master found, nothing for any other answer.
     */
    @Test
    fun theTrialNeedsRouteBAndTheNoticeIsRouteBsLine() {
        assertTrue(!YouTubeRouteB.trial || YouTubeRouteB.enabled)
        val line = "master: route B after RESPONSE_CHANGED; replayed 1; new 0"
        assertEquals(line, YouTubeRouteB.notice(SiteAdapterOutcome.Detected("youtube", emptyList(), listOf(line, "h: 1 rows"))))
        assertNull(YouTubeRouteB.notice(SiteAdapterOutcome.Detected("youtube", emptyList())))
        assertNull(YouTubeRouteB.notice(SiteAdapterOutcome.Detected("youtube", emptyList(), listOf("master: h-only: 1 rows"))))
        assertNull(
            YouTubeRouteB.notice(
                SiteAdapterOutcome.Failed("youtube", SiteExtractionFailure.RESPONSE_CHANGED, "m", true, listOf(line)),
            ),
        )
        assertNull(YouTubeRouteB.notice(SiteAdapterOutcome.NotHandled))
    }

    @Test
    fun theSharedAdapterClientRecordsOnlyInARouteBBuild() {
        val client = OkHttpClient()
        // Every other build: today's client, exactly.
        val plain = YouTubeRouteB.mainClient(client, routeB = false)
        assertTrue(plain.toString(), plain is OkHttpExtractorClient)
        // A route-B build: the same client inside the recorder (it passes through without a record).
        val recording = YouTubeRouteB.mainClient(client, routeB = true)
        assertTrue(recording.toString(), recording is LookupRecorder)
    }

    @Test
    fun mastersPiecesAreRouteBsOnlyInARouteBBuild() {
        val http = CountingHttp()
        val reader = object : MediaStartReader {
            override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart = MediaStart.Failed(null)
        }
        val runner = object : PlayerScriptRunner {
            override val isAvailable: Boolean = true
            override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
                PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        val ownSolver = VerdictRunner()
        // Every other build: each piece is the very same object as before.
        assertSame(http, YouTubeRouteB.masterClient(http, routeB = false))
        assertSame(runner, YouTubeRouteB.masterScripts(runner, routeB = false))
        assertSame(ownSolver, YouTubeRouteB.masterScripts(ownSolver, routeB = false))
        assertSame(reader, YouTubeRouteB.masterReader(reader, routeB = false))
        // A route-B build: route B's wrappers; the own solver keeps handing V its checks; no runner stays none.
        assertTrue(YouTubeRouteB.masterClient(http, routeB = true) is RouteBHttpClient)
        assertTrue(YouTubeRouteB.masterReader(reader, routeB = true) is RouteBMediaStartReader)
        val wrapped = YouTubeRouteB.masterScripts(ownSolver, routeB = true)
        assertTrue(wrapped !== ownSolver && wrapped is SignatureVerdicts)
        assertFalse(YouTubeRouteB.masterScripts(runner, routeB = true) is SignatureVerdicts)
        assertSame(NoPlayerScriptRunner, YouTubeRouteB.masterScripts(NoPlayerScriptRunner, routeB = true))
    }

    @Test
    fun aRouteBModuleSendsNothingNewAfterMainsRateLimit() = runTest {
        val record = LookupRecord()
        record.record(
            LookupAsk(LookupAsk.POST, PLAYER_URL, "{}"),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            record.now(),
        )
        val http = CountingHttp()
        val module = MasterLadderSwitch.youTube(http, OkHttpClient(), null, enabled = false, ownSolver = false, routeB = true)
        val route = RouteBLookup(record)
        val result = withContext(record + route) { module.extract(request(module.identify(WATCH_URL)!!)) }

        assertTrue(result is SiteExtractionResult.Failure)
        assertEquals(0, http.sent.get())
        assertEquals(0, record.masterAsks)
        assertTrue(route.toString(), route.refused >= 1)
    }

    @Test
    fun aRouteBModuleOnlyPassesThroughOutsideARouteBRun() = runTest {
        // Without route B's lookup in the context (the census, every other run) its requests go out.
        val http = CountingHttp()
        val module = MasterLadderSwitch.youTube(http, OkHttpClient(), null, enabled = false, ownSolver = false, routeB = true)
        module.extract(request(module.identify(WATCH_URL)!!))
        assertTrue(http.sent.get() >= 1)
        // Flag off: today's module, even inside a route-B run's context.
        val record = LookupRecord()
        record.record(
            LookupAsk(LookupAsk.POST, PLAYER_URL, "{}"),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            record.now(),
        )
        val plainHttp = CountingHttp()
        val plain = MasterLadderSwitch.youTube(plainHttp, OkHttpClient(), null, enabled = false, ownSolver = false, routeB = false)
        withContext(record + RouteBLookup(record)) { plain.extract(request(plain.identify(WATCH_URL)!!)) }
        assertTrue(plainHttp.sent.get() >= 1)
    }

    private fun request(identity: com.alal.yft.extractor.api.SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(pageUrl = WATCH_URL, userAgent = "Mozilla/5.0 (Linux; Android 14) Mobile", cookie = "PREF=f1"),
        nowEpochMs = 1_791_000_000_000L,
    )

    /** Answers every request with a 404 and counts what went out. */
    private class CountingHttp : ExtractorHttpClient {
        val sent = AtomicInteger()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            sent.incrementAndGet()
            return ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            sent.incrementAndGet()
            return ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            sent.incrementAndGet()
            return ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 404)
        }
    }

    /** An own solver as the module sees it: a runner that hands V its checks. */
    private class VerdictRunner : PlayerScriptRunner, SignatureVerdicts {
        override val isAvailable: Boolean = true

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
            PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)

        override fun pendingCheck(request: PlayerScriptRequest): PendingCheck = PendingCheck("f1x7ure0", OwnSolverBreaker.Step.WORKER_P)

        override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) = Unit
    }

    private companion object {
        const val WATCH_URL = "https://m.youtube.com/watch?v=Yft0Fixture"
        const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    }
}
