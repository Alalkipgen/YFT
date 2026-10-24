package com.alal.yft.extractor.master.lookup

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.MasterSiteModule
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.policy.TerminalRules
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B) R3: the engine's claim rule. Flag off = P12 exactly: a
 * page Master's YouTube module claims is skipped after main's own lookup for every reason except
 * UNSUPPORTED_URL. Flag on: after main failed with a recoverable reason, in a lookup whose record
 * holds main's requests, the module answers inside a route-B run (main's identical answers
 * replayed, nothing new after a 429) and the result names it (`master: route B after <reason>;
 * replayed N; new M`). Final reasons stay final. No live calls.
 */
class RouteBClaimRuleTest {
    @Test
    fun `the recoverable set is the plan's six reasons and never a final one`() {
        assertEquals(
            setOf(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                SiteExtractionFailure.EXPIRED_LINK,
                SiteExtractionFailure.RESPONSE_CHANGED,
                SiteExtractionFailure.HTTP_STATUS,
                SiteExtractionFailure.MALFORMED_RESPONSE,
                SiteExtractionFailure.RESPONSE_TOO_LARGE,
            ),
            TerminalRules.ROUTE_B_RECOVERABLE,
        )
        for (reason in SiteExtractionFailure.entries) {
            assertEquals(reason.name, reason in TerminalRules.ROUTE_B_RECOVERABLE, TerminalRules.routeBRecoverable(reason))
        }
        for (reason in TerminalRules.ROUTE_B_RECOVERABLE) {
            assertFalse(reason.name, TerminalRules.blocksFallback(reason, PAGE))
            assertFalse(reason.name, reason in TerminalRules.NEVER_FALLBACK)
            assertFalse(reason.name, reason in TerminalRules.BROWSER_REQUIRED)
        }
        assertFalse(TerminalRules.routeBRecoverable(SiteExtractionFailure.UNSUPPORTED_URL))
    }

    @Test
    fun `flag off - a claimed page after main's lookup is skipped for every reason, as today`() = runTest {
        for (reason in SiteExtractionFailure.entries - SiteExtractionFailure.UNSUPPORTED_URL) {
            val world = World()
            val result = world.lookup(world.engine(MasterPolicy(enabled = true)), reason)
            assertEquals(reason.name, MasterResult.Skipped(reason), result)
            assertTrue(reason.name, world.module.runs.isEmpty())
            assertEquals(reason.name, 1, world.http.postedUrls.size) // main's one request
        }
        // No adapter answered: the module answers as before, with no route-B run and no line.
        val world = World()
        val answered = world.lookup(world.engine(MasterPolicy(enabled = true)), SiteExtractionFailure.UNSUPPORTED_URL)
            as MasterResult.Success
        assertEquals(listOf("master: youtube module: 1 rows"), answered.result.details)
        assertEquals(listOf<RouteBLookup?>(null), world.module.runs)
    }

    @Test
    fun `flag on - each recoverable reason lets the module answer from main's replayed answer`() = runTest {
        for (reason in TerminalRules.ROUTE_B_RECOVERABLE) {
            val world = World()
            val result = world.lookup(world.engine(ROUTE_B), reason) as MasterResult.Success
            assertEquals(reason.name, MasterStage.SITE_MODULE, result.stage)
            assertEquals(reason.name, listOf("youtube:$ID"), result.result.candidates.map(MediaCandidate::videoId))
            assertEquals(
                reason.name,
                listOf("master: route B after ${reason.name}; replayed 1; new 0", "master: youtube module: 1 rows"),
                result.result.details,
            )
            assertEquals(reason.name, 1, world.http.postedUrls.size) // main's only: Master's was replayed
            val run = checkNotNull(world.module.runs.single())
            assertSame(world.record, run.record)
            assertEquals(1, run.replayed)
            assertEquals(0, run.newRequests)
            assertEquals(0, world.record.masterAsks)
        }
    }

    @Test
    fun `flag on - a final reason stays final and the module never runs`() = runTest {
        val finals = SiteExtractionFailure.entries - TerminalRules.ROUTE_B_RECOVERABLE - SiteExtractionFailure.UNSUPPORTED_URL
        assertTrue(
            finals.containsAll(
                listOf(
                    SiteExtractionFailure.BOT_CHECK, SiteExtractionFailure.LOGIN_REQUIRED,
                    SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, SiteExtractionFailure.RATE_LIMITED,
                    SiteExtractionFailure.NETWORK,
                ),
            ),
        )
        for (reason in finals) {
            val world = World()
            assertEquals(reason.name, MasterResult.Skipped(reason), world.lookup(world.engine(ROUTE_B), reason))
            assertTrue(reason.name, world.module.runs.isEmpty())
            assertEquals(reason.name, 1, world.http.postedUrls.size)
        }
    }

    @Test
    fun `flag on - a lookup without main's requests keeps the skip`() = runTest {
        // No record (home, pasted link, another build) and a record main never asked into (a
        // lookup that joined another caller's, or a cached answer): skipped as today.
        val world = World()
        val engine = world.engine(ROUTE_B)
        val request = MasterRequest(PAGE, 1, NOW, SiteExtractionFailure.RESPONSE_CHANGED)
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.RESPONSE_CHANGED), engine.extract(request))
        assertEquals(
            MasterResult.Skipped(SiteExtractionFailure.RESPONSE_CHANGED),
            withContext(LookupRecord()) { engine.extract(request) },
        )
        assertTrue(world.module.runs.isEmpty())
        assertTrue(world.http.postedUrls.isEmpty())
    }

    @Test
    fun `flag on - UNSUPPORTED_URL is answered as today, without a route-B run`() = runTest {
        val world = World()
        val result = world.lookup(world.engine(ROUTE_B), SiteExtractionFailure.UNSUPPORTED_URL) as MasterResult.Success
        assertEquals(listOf("master: youtube module: 1 rows"), result.result.details)
        assertEquals(listOf<RouteBLookup?>(null), world.module.runs)
    }

    @Test
    fun `flag on - only the YouTube module takes route B`() = runTest {
        val world = World(siteId = "fake")
        val result = world.lookup(world.engine(ROUTE_B), SiteExtractionFailure.RESPONSE_CHANGED)
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.RESPONSE_CHANGED), result)
        assertTrue(world.module.runs.isEmpty())
    }

    @Test
    fun `flag on - after main's 429 nothing new goes out and Master's failure names route B`() = runTest {
        // Main's first client was rate limited (429) and main ended with another reason; Master's
        // request differs from main's, so it would be new: refused, never sent.
        val world = World(mainBody = OTHER_BODY, mainAnswer = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429))
        val result = world.lookup(world.engine(ROUTE_B), SiteExtractionFailure.RESPONSE_CHANGED)
        assertEquals(
            MasterResult.Failure(
                SiteExtractionFailure.RATE_LIMITED,
                listOf(
                    "master: route B after RESPONSE_CHANGED; replayed 0; new 0; refused 1",
                    "master: youtube module: RATE_LIMITED",
                ),
            ),
            result,
        )
        assertEquals(1, world.http.postedUrls.size)
        assertEquals(0, world.record.masterAsks)
    }

    @Test
    fun `flag on - a new request goes out once and is counted`() = runTest {
        val world = World(mainBody = OTHER_BODY)
        val result = world.lookup(world.engine(ROUTE_B), SiteExtractionFailure.NO_MEDIA_FOUND) as MasterResult.Success
        assertEquals(
            listOf("master: route B after NO_MEDIA_FOUND; replayed 0; new 1", "master: youtube module: 1 rows"),
            result.result.details,
        )
        assertEquals(2, world.http.postedUrls.size)
        assertEquals(1, world.record.masterAsks)
    }

    @Test
    fun `flag on - the route-B run keeps the engine's own time bound`() = runTest {
        val world = World(beforeAsk = { delay(5_000) })
        val engine = world.engine(MasterPolicy(enabled = true, timeoutMillis = 1_000, youtubeRouteB = true))
        val result = world.lookup(engine, SiteExtractionFailure.RESPONSE_CHANGED)
        assertEquals(
            MasterResult.Failure(
                SiteExtractionFailure.NETWORK,
                listOf(
                    "master: route B after RESPONSE_CHANGED; replayed 0; new 0",
                    "master: bounded lookup timed out",
                ),
            ),
            result,
        )
        assertEquals(1, world.http.postedUrls.size)
    }

    /** One lookup: main asked the player endpoint once through the recorder, into [record]. */
    private class World(
        siteId: String = "youtube",
        private val mainBody: String = BODY,
        private val mainAnswer: ExtractorHttpResult = ok(),
        beforeAsk: suspend () -> Unit = {},
    ) {
        val http = FakeExtractorHttpClient(postResponder = { _, body -> if (body == mainBody) mainAnswer else ok() })
        val record = LookupRecord()
        val module = Module(siteId, RouteBHttpClient(http), beforeAsk)

        fun engine(policy: MasterPolicy) = MasterFallbackEngine(RecordingValidator(), policy = policy, modules = listOf(module))

        suspend fun lookup(engine: MasterFallbackEngine, reason: SiteExtractionFailure): MasterResult =
            withContext(record) {
                LookupRecorder(http).postJson(PLAYER, mainBody, HEADERS, MAX)
                engine.extract(MasterRequest(PAGE, 1, NOW, reason))
            }
    }

    /** Claims the fixture's YouTube page; asks the player endpoint through route B's client. */
    private class Module(
        override val siteId: String,
        private val http: ExtractorHttpClient,
        private val beforeAsk: suspend () -> Unit,
    ) : MasterSiteModule {
        val runs = mutableListOf<RouteBLookup?>()

        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == PAGE) SitePageIdentity(siteId, ID, PAGE) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            runs += coroutineContext[RouteBLookup]
            beforeAsk()
            return when (val asked = http.postJson(PLAYER, BODY, HEADERS, MAX)) {
                is ExtractorHttpResult.Success -> SiteExtractionResult.Success(
                    listOf(
                        MediaCandidate(
                            PAGE, MEDIA, setOf(CandidateSource.MANIFEST), MediaKind.DIRECT,
                            mimeType = "video/mp4", videoId = "youtube:$ID",
                        ),
                    ),
                )
                is ExtractorHttpResult.Failure -> SiteExtractionResult.Failure(asked.reason)
            }
        }
    }

    private companion object {
        const val ID = "Yft0Fixture"
        const val PAGE = "https://www.youtube.com/watch?v=$ID"
        const val PLAYER = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val BODY = """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"1.0"}},"videoId":"Yft0Fixture"}"""
        const val OTHER_BODY = """{"context":{"client":{"clientName":"ANDROID","clientVersion":"2.0"}},"videoId":"Yft0Fixture"}"""
        const val MEDIA = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18&id=o-test"
        const val MAX = 2L * 1024 * 1024
        const val NOW = 1_800_000_000_000L
        val HEADERS = linkedMapOf("Content-Type" to "application/json", "User-Agent" to "YftTest/1.0")
        val ROUTE_B = MasterPolicy(enabled = true, youtubeRouteB = true)

        fun ok() = ExtractorHttpResult.Success(statusCode = 200, body = "{}", finalUrl = PLAYER, contentType = "application/json")
    }
}
