package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus.Mode
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.FakePlayerScriptRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5a's A check (owner, 2026-10-16, option 2) against a scripted YouTube; no live calls:
 * video 2 only, visionOS 1.02 as R6 asks it, 1.04 once only when 1.02 is DIRECT, a stop at once
 * on a bot check or HTTP 429, and a run that is reported but not kept for the diff.
 */
class ClientCensusACheckTest {
    private var now = START

    @Test
    fun `the A check asks visionOS 1_02 as R6 asks it on video 2, then 1_04 once with only the version changed`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2)
        world.answers[Census.V2 to "VISIONOS"] = { body ->
            val json = Census.visionOs(Census.V2, cver = CensusWorld.versionOf(body))
            ExtractorHttpResult.Success(200, json, Census.PLAYER_URL, "application/json")
        }
        val http = world.http()

        val outcome = census(http).run(Mode.A_CHECK, VIDEOS, null)

        assertEquals(
            "A CHECK — visionOS 1.02 DIRECT; visionOS 1.04 DIRECT, V PASS: 1.04 can be visionOS's bundled alternate (NewPipe, 2026-08-06)",
            outcome.firstLine,
        )
        assertNull(outcome.stopped)
        assertEquals(listOf("VISIONOS", "VISIONOS"), http.postedBodies.map(CensusWorld::clientOf))
        assertEquals(listOf("1.02", "1.04"), http.postedBodies.map(CensusWorld::versionOf))
        assertTrue(http.postedBodies.all { Census.V2 in it && Census.V1 !in it })
        assertTrue("no watch page, no source", http.requestedUrls.isEmpty())
        assertEquals("V on 1.04's AVC row only", 1, http.probedUrls.size)
        assertTrue(calls(http) <= ClientCensus.A_CHECK_MAX_REQUESTS)

        // 1.02 is the full census's own R0 request (CensusR6ParityTest: R6's), body and headers.
        val full = CensusWorld().standard(Census.V2).sources().http()
        census(full).run(Mode.FULL, mapOf(2 to Census.V2), null)
        assertEquals(full.postedBodies.first(), http.postedBodies[0])
        assertEquals(full.postedHeaders.first(), http.postedHeaders[0])
        assertFalse(http.postedHeaders.any { it.keys.any { name -> name.equals("X-Goog-Visitor-Id", ignoreCase = true) } })
        assertFalse(http.postedBodies.any { Census.VISITOR in it })
        // 1.04 is that request with only the version changed, as the ladder's A rung changes it.
        val edited = VersionSource.withVersion(http.postedBodies[0], http.postedHeaders[0], "VISIONOS", "1.02", "1.04")!!
        assertEquals(edited.body, http.postedBodies[1])
        assertEquals(edited.headers, http.postedHeaders[1])
        assertEquals(http.postedUrls[0], http.postedUrls[1])

        val run = runOf(outcome)
        assertEquals("A_CHECK", run.mode)
        assertEquals(listOf("R0", ClientCensus.A_CHECK_STEP), run.asks.map(CensusAsk::step))
        assertTrue(run.asks.all { it.video == 2 && it.client == "visionos" })
        val (bundled, alternate) = run.asks
        assertEquals(listOf("1.02", "DIRECT", null), listOf(bundled.version, bundled.finalClass.name, bundled.v))
        assertEquals(listOf("1.04", "DIRECT", "PASS"), listOf(alternate.version, alternate.finalClass.name, alternate.v))
        assertTrue(outcome.report.lines()[2].startsWith("A check rule (owner, 2026-10-16): video 2 only"))
    }

    @Test
    fun `a bot check on visionOS 1_02 stops the A check at once and no census runs for 30 minutes`() = runTest {
        val world = CensusWorld().standard(Census.V2).sources().visionOsBotCheck(Census.V2)
        val http = world.http()
        val census = census(http)

        val outcome = census.run(Mode.A_CHECK, VIDEOS, null)

        assertEquals("A CHECK — stopped: bot check on visionOS 1.02, so 1.04 was not asked; no census for 30 min", outcome.firstLine)
        assertEquals("bot-check", outcome.stopped)
        assertEquals("one request, no watch page and no R0+vd", 1, http.postedBodies.size)
        assertTrue(http.requestedUrls.isEmpty() && http.probedUrls.isEmpty())
        assertEquals(START + 30 * MINUTE, CensusJson.readState(outcome.stateJson).cooldownUntil)
        assertEquals(CensusClass.BOT_CHECK, runOf(outcome).asks.single().answerClass)

        now += 10 * MINUTE
        val waiting = census.run(Mode.FULL, VIDEOS, outcome.stateJson)
        assertEquals("cooldown", waiting.stopped)
        assertEquals("cooldown", census.run(Mode.A_CHECK, VIDEOS, outcome.stateJson).stopped)
        assertEquals(1, http.postedBodies.size)
    }

    @Test
    fun `HTTP 429 on visionOS 1_02 stops the A check at once`() = runTest {
        val world = CensusWorld().standard(Census.V2)
        world.answers[Census.V2 to "VISIONOS"] = { _ -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429) }
        val http = world.http()

        val outcome = census(http).run(Mode.A_CHECK, VIDEOS, null)

        assertEquals("A CHECK — stopped: HTTP 429 on visionOS 1.02, so 1.04 was not asked; no census for 30 min", outcome.firstLine)
        assertEquals("rate-limited", outcome.stopped)
        assertEquals(1, calls(http))
        assertEquals(START + 30 * MINUTE, CensusJson.readState(outcome.stateJson).cooldownUntil)
    }

    @Test
    fun `1_04 is not asked when visionOS 1_02 does not answer DIRECT`() = runTest {
        val world = CensusWorld().standard(Census.V2)
        world.answer(Census.V2, "VISIONOS", Census.visionOsCiphered(Census.V2))
        val http = world.http()

        val outcome = census(http).run(Mode.A_CHECK, VIDEOS, null)

        assertEquals("A CHECK — visionOS 1.02 NEEDS_SCRIPT, so 1.04 was not asked: nothing decided", outcome.firstLine)
        assertNull(outcome.stopped)
        assertEquals(1, calls(http))
        assertTrue(outcome.report.contains("A check: visionOS 1.02 answered NEEDS_SCRIPT, so 1.04 was not asked"))
        assertEquals(0L, CensusJson.readState(outcome.stateJson).cooldownUntil)
    }

    @Test
    fun `a bot check or HTTP 429 on 1_04 stops the A check with no census for 30 minutes`() = runTest {
        for ((refusal, line) in listOf(
            Census.botCheck(Census.V2) to "bot check",
            null to "HTTP 429",
        )) {
            val world = CensusWorld().standard(Census.V2)
            world.answers[Census.V2 to "VISIONOS"] = { body ->
                when {
                    CensusWorld.versionOf(body) != "1.04" ->
                        ExtractorHttpResult.Success(200, Census.visionOs(Census.V2), Census.PLAYER_URL, "application/json")
                    refusal == null -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429)
                    else -> ExtractorHttpResult.Success(200, refusal, Census.PLAYER_URL, "application/json")
                }
            }
            val http = world.http()

            val outcome = census(http).run(Mode.A_CHECK, VIDEOS, null)

            assertEquals(
                "A CHECK — stopped: $line on visionOS 1.04 (visionOS 1.02 DIRECT); no census for 30 min",
                outcome.firstLine,
            )
            assertEquals(if (refusal == null) "rate-limited" else "bot-check", outcome.stopped)
            assertEquals(2, http.postedBodies.size)
            assertTrue("no V after the stop", http.probedUrls.isEmpty() && http.requestedUrls.isEmpty())
            assertEquals(START + 30 * MINUTE, CensusJson.readState(outcome.stateJson).cooldownUntil)
        }
    }

    @Test
    fun `1_04 refused, needing a token or without a V pass is not an alternate`() = runTest {
        val cases = listOf(
            "rejected" to "A CHECK — visionOS 1.02 DIRECT; visionOS 1.04 VERSION_REJECTED (http 400): not an alternate",
            "token" to "A CHECK — visionOS 1.02 DIRECT; visionOS 1.04 TOKEN_NEEDED: not an alternate",
            "unknown" to "A CHECK — visionOS 1.02 DIRECT; visionOS 1.04 DIRECT, V UNKNOWN: nothing decided (no V pass)",
            "update" to "A CHECK — visionOS 1.02 DIRECT; visionOS 1.04 VERSION_REJECTED: not an alternate",
        )
        for ((case, line) in cases) {
            val world = CensusWorld().standard(Census.V2)
            world.answers[Census.V2 to "VISIONOS"] = { body ->
                val newer = CensusWorld.versionOf(body) == "1.04"
                when {
                    newer && case == "rejected" -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 400)
                    newer && case == "update" -> ExtractorHttpResult.Success(
                        200,
                        Census.answer(Census.V2, status = "UNPLAYABLE", reason = "Please update to the latest version of the app"),
                        Census.PLAYER_URL,
                        "application/json",
                    )
                    else -> ExtractorHttpResult.Success(200, Census.visionOs(Census.V2), Census.PLAYER_URL, "application/json")
                }
            }
            world.probe = { url ->
                when {
                    "itag=136" !in url -> ExtractorProbeResult.Answered(206, 1_000_000)
                    case == "token" -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403, null)
                    case == "unknown" -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 404, null)
                    else -> ExtractorProbeResult.Answered(206, 1_000_000)
                }
            }
            val http = world.http()

            val outcome = census(http).run(Mode.A_CHECK, VIDEOS, null)

            assertEquals(case, line, outcome.firstLine)
            assertNull(case, outcome.stopped)
            assertTrue(case, calls(http) <= ClientCensus.A_CHECK_MAX_REQUESTS)
            assertEquals(case, if (case == "rejected" || case == "update") 0 else 1, http.probedUrls.count { "itag=136" in it })
        }
    }

    @Test
    fun `the A check is not kept for the diff and leaves the stored runs as they were`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
        val census = census(world.http())
        val first = census.run(Mode.FULL, VIDEOS, null)
        now += HOUR
        val checked = census.run(Mode.A_CHECK, VIDEOS, first.stateJson)
        now += HOUR
        val after = census.run(Mode.FULL, VIDEOS, checked.stateJson)
        val direct = census(CensusWorld().standard(Census.V1).standard(Census.V2).sources().http())
            .run(Mode.FULL, VIDEOS, first.stateJson)

        assertEquals(CensusJson.readState(first.stateJson), CensusJson.readState(checked.stateJson))
        assertTrue(after.firstLine.startsWith("NO CHANGE"))
        assertEquals(direct.firstLine, after.firstLine)
        assertTrue(ClientCensus.statusOf(checked.stateJson, now).contains("(full)"))
    }

    @Test
    fun `the A check needs video 2 and asks nothing else`() = runTest {
        val http = CensusWorld().standard(Census.V1).http()

        val outcome = census(http).run(Mode.A_CHECK, mapOf(1 to Census.V1), null)

        assertEquals("A CHECK — video 2 is not set, so nothing was asked", outcome.firstLine)
        assertEquals(0, calls(http))
    }

    @Test
    fun `the A check's report holds no address, video ID or visitor data`() = runTest {
        val world = CensusWorld().standard(Census.V2)
        world.answers[Census.V2 to "VISIONOS"] = { body ->
            ExtractorHttpResult.Success(200, Census.visionOs(Census.V2, CensusWorld.versionOf(body)), Census.PLAYER_URL, "application/json")
        }
        val steps = ArrayList<String>()

        val outcome = census(world.http()).run(Mode.A_CHECK, VIDEOS, null) { steps += it }

        assertEquals(listOf("video 2 · visionOS 1.02", "video 2 · visionOS 1.04"), steps)
        for (text in listOf(outcome.report, outcome.stateJson, outcome.firstLine)) {
            for (secret in listOf(Census.V1, Census.V2, Census.VISITOR, Census.HOST, "googlevideo", "videoplayback", Census.API_KEY)) {
                assertFalse(secret, text.contains(secret))
            }
        }
    }

    @Test
    fun `the A check's version is NewPipe's 1_04 - valid for visionOS, newer than the bundled one, with source and date`() {
        val alternate = ClientCensus.A_CHECK_ALTERNATE
        val vision = MasterClientTable.entry("visionos")!!
        assertEquals("1.04", alternate.version)
        assertTrue(vision.versionPattern.matches(alternate.version))
        assertTrue(ClientHealth.VERSION.matches(alternate.version))
        assertNotEquals(YouTubeClientProfiles.VISION_OS.clientVersion, alternate.version)
        assertTrue(alternate.version.toDouble() > YouTubeClientProfiles.VISION_OS.clientVersion.toDouble())
        assertTrue(alternate.source.startsWith("NewPipe"))
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(alternate.date))
        assertEquals("not bundled before the owner's A check", emptyList<MasterClientTable.Alternate>(), vision.alternates)
        assertEquals("R0 stays 1.02", "1.02", YouTubeClientProfiles.VISION_OS.clientVersion)
    }

    private fun census(
        http: FakeExtractorHttpClient,
        scripts: FakePlayerScriptRunner = FakePlayerScriptRunner(),
    ) = ClientCensus(http, scripts, Census.UA, clock = { now })

    private fun calls(http: FakeExtractorHttpClient) = http.postedUrls.size + http.requestedUrls.size + http.probedUrls.size

    /** The A check's run, from its report (it is not kept in the state). */
    private fun runOf(outcome: ClientCensus.Outcome): CensusRun {
        val json = outcome.report.substring(outcome.report.indexOf("{\"tool\""))
        return CensusJson.readRun(BoundedJsonParser.parse(json, maxNodes = 100_000)!!)!!
    }

    private companion object {
        const val START = 1_791_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        val VIDEOS = mapOf(1 to Census.V1, 2 to Census.V2)
    }
}
