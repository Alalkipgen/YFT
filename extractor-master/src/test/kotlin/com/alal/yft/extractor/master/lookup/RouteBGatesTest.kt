package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.SignatureGate
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B) R2: the two paths of Master's YouTube module that do not
 * go through its HTTP client — the ladder's media-start reader (L rows' V and init check, H's
 * first segment) and the player-script runner (the own solver) — under route B. No live calls.
 */
class RouteBGatesTest {
    @Test
    fun `the media-start reader passes through without route B`() = runTest {
        val reader = CountingReader(MediaStart.Read(206, "video/mp4", "bytes 0-9/100", 10, ByteArray(10)))
        val gated = RouteBMediaStartReader(reader)
        val record = LookupRecord()

        withContext(record) { assertTrue(gated.read(MEDIA, emptyMap(), 10) is MediaStart.Read) }
        gated.read(MEDIA, emptyMap(), 10)

        assertEquals(2, reader.reads)
        assertEquals(0, record.masterAsks)
    }

    @Test
    fun `under route B a media read is new and listed as Master's until a 429`() = runTest {
        val reader = CountingReader(MediaStart.Read(206, "video/mp4", "bytes 0-9/100", 10, ByteArray(10)))
        val gated = RouteBMediaStartReader(reader)
        val record = LookupRecord()
        val route = RouteBLookup(record)

        withContext(record + route) {
            assertTrue(gated.read(MEDIA, emptyMap(), 10) is MediaStart.Read)
            // The same file again (an L row and H's segment may share a host): it goes out.
            assertTrue(gated.read(MEDIA, emptyMap(), 10) is MediaStart.Read)
            // The user's session never goes to a media host.
            assertNull((gated.read(MEDIA, mapOf("Cookie" to "SID=fixture"), 10) as MediaStart.Failed).statusCode)
        }

        assertEquals(2, reader.reads)
        assertEquals(2, route.newRequests)
        assertEquals(1, route.refused)
        assertEquals(2, record.masterAsks)
        assertEquals(LookupRecord.Asker.MASTER, record.asks().last().asker)
        assertEquals(0, record.keptCount)
    }

    @Test
    fun `a media read's 429 stops new requests, and after main's 429 nothing is read`() = runTest {
        val limited = CountingReader(MediaStart.Failed(429))
        val record = LookupRecord()
        val route = RouteBLookup(record)
        withContext(record + route) {
            assertEquals(429, (RouteBMediaStartReader(limited).read(MEDIA, emptyMap(), 10) as MediaStart.Failed).statusCode)
            assertNull((RouteBMediaStartReader(limited).read(MEDIA, emptyMap(), 10) as MediaStart.Failed).statusCode)
        }
        assertTrue(record.rateLimited)
        assertEquals(1, limited.reads)
        assertEquals(1, route.refused)

        val afterMain = LookupRecord()
        withContext(afterMain) {
            LookupRecorder(FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)))
                .postJson(PLAYER, "{}", emptyMap(), 1_000)
        }
        val reader = CountingReader(MediaStart.Read(206, "video/mp4", null, 10, ByteArray(10)))
        val gated = RouteBLookup(afterMain)
        withContext(afterMain + gated) { assertTrue(RouteBMediaStartReader(reader).read(MEDIA, emptyMap(), 10) is MediaStart.Failed) }
        assertEquals(0, reader.reads)
        assertEquals(1, gated.refused)
        assertEquals(0, afterMain.masterAsks)
    }

    @Test
    fun `wrapping keeps the runner's kind, so the signature gate (V) stays exactly as it was`() {
        val http = FakeExtractorHttpClient()
        assertSame(NoPlayerScriptRunner, RouteBPlayerScripts.wrap(NoPlayerScriptRunner))

        val own = OwnRunner()
        val wrappedOwn = RouteBPlayerScripts.wrap(own)
        assertTrue(wrappedOwn is SignatureVerdicts)
        assertNotNull(SignatureGate.of(wrappedOwn, http))
        val check = (wrappedOwn as SignatureVerdicts).pendingCheck(REQUEST)
        assertEquals(OwnRunner.CHECK, check)
        wrappedOwn.record(OwnRunner.CHECK, SignatureVerdicts.Verdict.PASS)
        assertEquals(listOf(SignatureVerdicts.Verdict.PASS), own.verdicts)

        val plain = PlainRunner()
        val wrappedPlain = RouteBPlayerScripts.wrap(plain)
        assertFalse(wrappedPlain is SignatureVerdicts)
        assertNull(SignatureGate.of(wrappedPlain, http))
        assertTrue(wrappedPlain.isAvailable)
        plain.available = false
        assertFalse(wrappedPlain.isAvailable)
        assertEquals("RouteBPlayerScripts", wrappedPlain.toString())
    }

    @Test
    fun `the runner answers as before, and after a 429 in the lookup it is not asked`() = runTest {
        val own = OwnRunner()
        val runner = RouteBPlayerScripts.wrap(own)
        assertTrue(runner.resolve(REQUEST) is PlayerScriptResult.Success)

        val record = LookupRecord()
        val route = RouteBLookup(record)
        withContext(record + route) { assertTrue(runner.resolve(REQUEST) is PlayerScriptResult.Success) }
        assertEquals(2, own.resolved)
        assertEquals(0, route.refused)

        withContext(record) {
            LookupRecorder(FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)))
                .get(PLAYER, emptyMap(), 1_000)
        }
        withContext(record + route) {
            assertEquals(PlayerScriptResult.Failed(SiteExtractionFailure.RATE_LIMITED), runner.resolve(REQUEST))
        }
        // Without route B (main's own lookups, the census) the runner is asked as before.
        withContext(record) { assertTrue(runner.resolve(REQUEST) is PlayerScriptResult.Success) }
        assertEquals(3, own.resolved)
        assertEquals(1, route.refused)
    }

    private class CountingReader(private val answer: MediaStart) : MediaStartReader {
        var reads = 0

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            reads++
            return answer
        }
    }

    private open class PlainRunner : PlayerScriptRunner {
        var available = true
        var resolved = 0
        override val isAvailable: Boolean get() = available

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
            resolved++
            return PlayerScriptResult.Success(request.challenges.associate { it.key to it.input.reversed() })
        }
    }

    private class OwnRunner : PlainRunner(), SignatureVerdicts {
        val verdicts = mutableListOf<SignatureVerdicts.Verdict>()

        override fun pendingCheck(request: PlayerScriptRequest): PendingCheck = CHECK

        override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
            verdicts += verdict
        }

        companion object {
            val CHECK = PendingCheck("f1x7ure0", OwnSolverBreaker.Step.WORKER_P)
        }
    }

    private companion object {
        const val MEDIA = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18&id=o-test"
        const val PLAYER = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        val REQUEST = PlayerScriptRequest(
            playerScriptUrl = "https://www.youtube.com/s/player/f1x7ure0/player_ias.vflset/en_US/base.js",
            pageUrl = "https://www.youtube.com/watch?v=Yft0Fixture",
            challenges = listOf(PlayerScriptChallenge("n0", PlayerScriptChallengeKind.RATE_PARAM, "abc")),
        )
    }
}
