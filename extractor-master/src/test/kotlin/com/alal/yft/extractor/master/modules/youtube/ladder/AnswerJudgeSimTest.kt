package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfile
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubePageSignals
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D2 (MASTER_KEY_PHASE1_3_PLAN.md §4 D2): V7 in the simulator, live-backed.
 *
 * visionOS refuses one client's way — the simulator's V7 ("not available on this app") or video
 * 6's "This video is not available" as the census saw it — and [AnswerJudge] calls it `REFUSED`,
 * not a verdict about the video, so the client table's O rungs ([MasterClientTable.oRungs]:
 * Android, MWEB, provisional) are asked as the census asks them: the watch page's signals, no
 * cookie or token, S on every protected row with the world's own solver, V on every row. A world
 * is complete when one rung gives a V-checked adaptive video and audio pair; the walk stops there.
 * MCF (plan §5.1) = a wrong row, or no complete rung although the world has one. Controls: T2's
 * and T3's verdicts stop at visionOS with no O request; T3's removed video (main's unavailable
 * answer, `REFUSED` by the V7 rule) asks the O rungs, which refuse too, and stops with no row.
 * The lines go to `build/ladder-sim/v7-live-backed.txt` (never an address or value).
 */
class AnswerJudgeSimTest {
    @Test
    fun `V7 - visionOS's one-client refusal goes on to the O rungs, which complete with no wrong row`() = runTest {
        val runs = Sim.BASES.flatMap { base ->
            val v7 = SimCases.forBase(base).first { it.klass == "V7" }
            listOf(
                walk("V7/${short(base)}", v7.world(), pathExists = true),
                walk("V7/video-6-like/${short(base)}", video6Like(base), pathExists = true),
            )
        }
        write(runs)
        assertEquals(10, runs.size)
        for (run in runs) {
            assertEquals(run.id, CensusClass.REFUSED, run.vision.answerClass)
            assertFalse(run.id, run.vision.terminal)
            assertEquals(run.id, MasterClientTable.oRungs().map { it.id }, run.rungs.map(Asked::id))
            assertTrue(run.id, run.complete)
            assertEquals(run.id, "mweb", run.completeAt)
            assertEquals(run.id, 0, run.wrong.size)
            assertEquals(run.id, 0, run.mcf)
            assertEquals(run.id, 0, run.credentials)
            assertEquals(run.id, 1, run.pages)
            assertEquals(run.id, 1 + run.rungs.size, run.players)
            // Android gives its progressive row only (SABR adaptive, as live); MWEB's rows need S.
            val android = run.rungs.first()
            assertFalse(run.id, android.complete)
            assertTrue(run.id, android.rows > 0 && android.rows == android.passed)
            assertEquals(run.id, CensusClass.NEEDS_SCRIPT, run.rungs.last().answerClass)
            assertTrue(run.id, run.signatureTimestampSent)
        }
        assertEquals(0, runs.sumOf(Run::mcf))
    }

    @Test
    fun `T2 and T3's verdicts stop at visionOS, the removed video's refusal asks the O rungs and stops clean`() = runTest {
        val runs = Sim.BASES.flatMap { base ->
            SimCases.forBase(base).filter { it.klass == "T2" || it.klass == "T3" }.map { case ->
                case to walk(case.id, case.world(), pathExists = case.paths.isNotEmpty())
            }
        }
        write(runs.map { it.second }, CONTROLS)
        assertEquals(Sim.BASES.size * 5, runs.size)
        for ((case, run) in runs) {
            assertEquals(run.id, 0, run.wrong.size)
            assertEquals(run.id, 0, run.mcf)
            assertEquals(run.id, 0, run.credentials)
            assertEquals(run.id, 0, run.probes)
            if (case.variant == "removed") {
                assertEquals(run.id, CensusClass.REFUSED, run.vision.answerClass)
                assertEquals(run.id, MasterClientTable.oRungs().map { it.id }, run.rungs.map(Asked::id))
                assertTrue(run.id, run.rungs.all { it.answerClass == CensusClass.REFUSED && it.rows == 0 })
                assertFalse(run.id, run.complete)
                assertEquals(run.id, 1 + run.rungs.size, run.players)
            } else {
                assertTrue(run.id, run.vision.terminal)
                assertTrue(run.id, run.rungs.isEmpty())
                assertEquals(run.id, 1, run.players)
                assertEquals(run.id, 0, run.pages)
            }
        }
    }

    // ---- the walk: visionOS, then the O rungs as the census asks them ---------------------------

    private class Asked(
        val id: String,
        val answerClass: CensusClass,
        val rows: Int,
        val passed: Int,
        val complete: Boolean,
    ) {
        override fun toString(): String =
            "$id ${answerClass.name} rows $passed/$rows V pass" + if (complete) ", complete" else ""
    }

    private class Run(
        val id: String,
        val vision: AnswerJudge.Verdict,
        val rungs: List<Asked>,
        val wrong: List<String>,
        val pathExists: Boolean,
        val players: Int,
        val pages: Int,
        val probes: Int,
        val credentials: Int,
        /** Every script client's request carried the page's signature timestamp. */
        val signatureTimestampSent: Boolean,
    ) {
        val complete: Boolean get() = rungs.any(Asked::complete)
        val completeAt: String? get() = rungs.firstOrNull(Asked::complete)?.id
        val mcf: Int get() = if (wrong.isNotEmpty() || (pathExists && !vision.terminal && !complete)) 1 else 0

        fun line(): String = buildString {
            append(id).append(" | visionos ").append(vision.answerClass.name)
            if (vision.terminal) append(" (terminal)")
            append(" | ").append(if (rungs.isEmpty()) "no O rung asked" else rungs.joinToString(" > "))
            append(" | complete ").append(completeAt ?: "-")
            append(" | wrong ").append(wrong.size)
            if (wrong.isNotEmpty()) append(" (").append(wrong.distinct().sorted().joinToString(", ")).append(')')
            append(" | MCF ").append(mcf)
            append(" | player ").append(players).append(", page ").append(pages).append(", probe ").append(probes)
            append(" | credentials ").append(credentials)
        }
    }

    private suspend fun walk(id: String, world: SimWorld, pathExists: Boolean): Run {
        val vision = ask(world, YouTubeClientProfiles.VISION_OS, NO_PAGE)
        val rungs = mutableListOf<Asked>()
        val wrong = mutableListOf<String>()
        if (!vision.terminal) {
            val signals = pageSignals(world)
            for (entry in MasterClientTable.oRungs()) {
                val verdict = ask(world, entry.profile, signals)
                if (verdict.terminal) break
                val streams = verdict.video?.let { it.progressive + it.adaptive }.orEmpty()
                val addresses = addresses(world, streams, signals)
                val passed = addresses.filter { (_, url) ->
                    world.http.probe(url, mediaHeaders(), PROBE_TIMEOUT_MILLIS) is ExtractorProbeResult.Answered
                }
                passed.forEach { (_, url) -> world.truth(url)?.let(wrong::add) }
                val video = passed.any { (stream, _) -> stream in verdict.video?.adaptive.orEmpty() && stream.hasVideo && !stream.hasAudio }
                val audio = passed.any { (stream, _) -> stream in verdict.video?.adaptive.orEmpty() && stream.hasAudio && !stream.hasVideo }
                rungs += Asked(entry.id, verdict.answerClass, addresses.size, passed.size, video && audio)
                if (video && audio) break
            }
        }
        val log = world.http
        return Run(
            id = id,
            vision = vision,
            rungs = rungs,
            wrong = wrong,
            pathExists = pathExists,
            players = log.count(SimHttp.Kind.PLAYER),
            pages = log.count(SimHttp.Kind.PAGE),
            probes = log.count(SimHttp.Kind.PROBE),
            credentials = log.log.count(SimHttp.Entry::credentials),
            signatureTimestampSent = world.asks.filter { it.client.script }.let { asks -> asks.isNotEmpty() && asks.all(SimAsk::sts) },
        )
    }

    /** One player request as the census makes it (R6's headers, no cookie), judged. */
    private suspend fun ask(world: SimWorld, profile: YouTubeClientProfile, signals: YouTubePageSignals): AnswerJudge.Verdict {
        val result = world.http.postJson(
            url = YouTubeUrls.innerTubeUrl(signals.apiKey),
            body = profile.playerRequestBody(Sim.VIDEO_ID, signals.visitorData, signals.signatureTimestamp),
            headers = buildMap {
                put("User-Agent", profile.userAgent ?: Sim.USER_AGENT)
                put("Accept", "application/json")
                put("Origin", ORIGIN)
                if (profile.device == null) put("Referer", YouTubeUrls.canonicalUrl(Sim.VIDEO_ID))
                put("X-YouTube-Client-Name", profile.clientNameId.toString())
                put("X-YouTube-Client-Version", profile.clientVersion)
                signals.visitorData?.let { put("X-Goog-Visitor-Id", it) }
            },
            maxBodyBytes = MAX_PLAYER_BYTES,
        )
        return when (result) {
            is ExtractorHttpResult.Failure -> AnswerJudge.ofFailure(result)
            is ExtractorHttpResult.Success -> AnswerJudge.judge(profile, result.body, Sim.VIDEO_ID, Sim.NOW)
        }
    }

    private suspend fun pageSignals(world: SimWorld): YouTubePageSignals {
        val page = world.http.get(Sim.PAGE_FETCH, mapOf("User-Agent" to Sim.USER_AGENT), MAX_PAGE_BYTES)
        return (page as? ExtractorHttpResult.Success)?.let { YouTubePlayerResponseParser.pageSignals(it.body) } ?: NO_PAGE
    }

    /**
     * Each stream's address: a protected row's signature and every n through one player script
     * request (the world's solver), appended and replaced as R6 does; a row S cannot give is dropped.
     */
    private suspend fun addresses(
        world: SimWorld,
        streams: List<YouTubeStream>,
        signals: YouTubePageSignals,
    ): List<Pair<YouTubeStream, String>> {
        class Raw(val stream: YouTubeStream, val url: String, val s: String?, val sp: String, val n: String?)
        val raws = streams.mapNotNull { stream ->
            val descriptor = stream.protectedDescriptor
            if (descriptor != null) {
                val fields = Sim.query("https://x.test/?$descriptor")
                val url = fields["url"] ?: return@mapNotNull null
                Raw(stream, url, fields["s"], fields["sp"] ?: "signature", Sim.query(url)["n"])
            } else {
                stream.url?.let { Raw(stream, it, null, "signature", Sim.query(it)["n"]) }
            }
        }
        val challenges = raws.flatMapIndexed { index, raw ->
            listOfNotNull(
                raw.s?.let { PlayerScriptChallenge("s-$index", PlayerScriptChallengeKind.SIGNATURE, it) },
                raw.n?.let { PlayerScriptChallenge("n-$index", PlayerScriptChallengeKind.RATE_PARAM, it) },
            )
        }
        val solved = if (challenges.isEmpty()) {
            emptyMap()
        } else {
            val playerId = requireNotNull(signals.playerId) { "the page names its player" }
            val request = PlayerScriptRequest(YouTubeUrls.playerScriptUrl(playerId), YouTubeUrls.canonicalUrl(Sim.VIDEO_ID), challenges)
            (SimSolver(world).resolve(request) as? PlayerScriptResult.Success)?.resolved.orEmpty()
        }
        return raws.mapIndexedNotNull { index, raw ->
            var url = raw.url
            if (raw.n != null) url = YouTubeUrls.replaceQueryParam(url, "n", solved["n-$index"] ?: return@mapIndexedNotNull null)
            if (raw.s != null) url = YouTubeUrls.appendQueryParam(url, raw.sp, solved["s-$index"] ?: return@mapIndexedNotNull null)
            raw.stream to url
        }
    }

    private fun mediaHeaders(): Map<String, String> =
        mapOf("User-Agent" to Sim.USER_AGENT, "Referer" to YouTubeUrls.canonicalUrl(Sim.VIDEO_ID))

    /** Video 6 as visionOS answered it in the census (no formats, the video's details); the rest as the base. */
    private fun video6Like(base: String): SimWorld = SimWorld(base).apply {
        override = { ask -> if (ask.client == SimClient.VISION_OS) SimReply.Json(VIDEO_6_LIKE) else null }
    }

    private fun short(base: String) = base.removePrefix("player_").removeSuffix(".json")

    private fun write(runs: List<Run>, name: String = V7) {
        val out = File("build/ladder-sim").apply { mkdirs() }
        val text = runs.joinToString("\n", postfix = "\n") { it.line() }
        assertFalse("no address in the report", "://" in text)
        File(out, name).writeText(text)
    }

    private companion object {
        const val V7 = "v7-live-backed.txt"
        const val CONTROLS = "v7-live-backed-controls.txt"
        const val ORIGIN = "https://www.youtube.com"
        const val MAX_PLAYER_BYTES = 4L * 1024 * 1024
        const val MAX_PAGE_BYTES = 4L * 1024 * 1024
        const val PROBE_TIMEOUT_MILLIS = 8_000L

        val NO_PAGE = YouTubePageSignals(null, null, null, null, null, null, null)

        val VIDEO_6_LIKE = "{\"responseContext\":{},\"playabilityStatus\":{\"status\":\"UNPLAYABLE\"," +
            "\"reason\":\"This video is not available\",\"playableInEmbed\":true}," +
            "\"videoDetails\":{\"videoId\":\"${Sim.VIDEO_ID}\",\"title\":\"Fixture video title\",\"lengthSeconds\":\"212\"," +
            "\"author\":\"Fixture Channel\",\"isLiveContent\":false}}"
    }
}