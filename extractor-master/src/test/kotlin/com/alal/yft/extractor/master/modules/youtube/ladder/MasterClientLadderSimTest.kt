package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.testing.FakePlayerScriptRunner
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 1.3 D1 (MASTER_KEY_PHASE1_3_PLAN.md §4 D1): the change simulator and the R6 baseline.
 *
 * Every class of the plan's table is made in code on main's 5 base answers (no YouTube data
 * captured or committed) and run through R6 with the own solver off and on. A row is judged by
 * what the simulated media host would do with its address ([SimWorld.truth]); the result per
 * case is pinned in `src/test/resources/ladder/r6-baseline.txt`, so a change to R6 shows here
 * (flag off must stay R6), and the per-class cells of plan §5's baseline table are written to
 * `build/ladder-sim/`. The ladder columns, the parity gate and the L round-trip need D6's seam
 * and D5b's parser and come with those tasks.
 */
class MasterClientLadderSimTest {
    @Test
    fun `every case has its own id and every class of the plan is made`() {
        val cases = SimCases.all()
        assertEquals(cases.size, cases.map(SimCase::id).toSet().size)
        assertEquals(SimCases.CLASSES.toSet(), cases.map(SimCase::klass).toSet())
        for (base in Sim.BASES) {
            assertEquals(SimCases.CLASSES.toSet(), cases.filter { it.base == base }.map(SimCase::klass).toSet())
        }
        val modes = cases.filter { it.klass.startsWith("L") }.groupBy { it.klass + "/" + it.variant + "/" + it.base }
        assertTrue(modes.values.all { group -> group.map(SimCase::vMode).toSet() == VMode.entries.toSet() })
    }

    @Test
    fun `every case changes the world it is made on`() {
        val plain = Sim.BASES.associateWith { fingerprint(SimWorld(it)) }
        for (case in SimCases.all().filter { it.klass != "B0" }) {
            assertNotEquals("$case changes nothing", plain.getValue(case.base), fingerprint(case.world()))
        }
    }

    @Test
    fun `the base worlds give main's answers with addresses the world opens`() {
        for (base in Sim.BASES) {
            val world = SimWorld(base)
            val vision = parse(world, SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false))
            val streams = vision.progressive + vision.adaptive
            assertTrue(base, streams.isNotEmpty())
            assertTrue(base, streams.all { it.url != null && world.truth(it.url) == null })
            assertEquals(base, world.lengthSeconds * 1_000, vision.durationMillis)

            val android = parse(world, SimAsk(SimClient.ANDROID, "", visitor = false, sts = false))
            assertTrue(base, android.progressive.all { world.truth(it.url) == null })
            assertEquals(base, world.lengthSeconds * 1_000, android.durationMillis)

            val mweb = parse(world, SimAsk(SimClient.MWEB, "", visitor = true, sts = true))
            val unsolved = (mweb.progressive + mweb.adaptive).map { requireNotNull(it.protectedDescriptor) }
            assertTrue(base, unsolved.isNotEmpty())
            for (descriptor in unsolved) {
                val fields = Sim.query("https://x.test/?$descriptor")
                val raw = fields.getValue("url")
                assertEquals("n", world.truth(raw))
                val n = solve(PlayerScriptChallengeKind.RATE_PARAM, requireNotNull(Sim.query(raw)["n"]))
                val withN = YouTubeUrls.replaceQueryParam(raw, "n", n)
                assertEquals("sig", world.truth(withN))
                val sig = solve(PlayerScriptChallengeKind.SIGNATURE, fields.getValue("s"))
                assertNull(world.truth(YouTubeUrls.appendQueryParam(withN, "sig", sig)))
            }
        }
    }

    @Test
    fun `a script client asked without the page's timestamp gets addresses the media host refuses`() {
        val world = SimWorld(Sim.BASES.first())
        val mweb = parse(world, SimAsk(SimClient.MWEB, "", visitor = true, sts = false))
        for (descriptor in (mweb.progressive + mweb.adaptive).map { requireNotNull(it.protectedDescriptor) }) {
            val fields = Sim.query("https://x.test/?$descriptor")
            val raw = fields.getValue("url")
            val solved = YouTubeUrls.appendQueryParam(
                YouTubeUrls.replaceQueryParam(raw, "n", solve(PlayerScriptChallengeKind.RATE_PARAM, Sim.query(raw).getValue("n"))),
                "sig",
                solve(PlayerScriptChallengeKind.SIGNATURE, fields.getValue("s")),
            )
            assertEquals("403", world.truth(solved))
        }
    }

    @Test
    fun `the world's HLS passes the H check and every F3 fault is a broken HLS row`() = runTest {
        for (base in Sim.BASES) {
            val world = SimWorld(base)
            assertTrue(world.hls.good)
            val result = HlsCheck(world.http).check(world.hls.masterUrl, emptyMap(), world.lengthSeconds)
            assertEquals(base, HlsCheck.OK, result.master)
            assertEquals(base, HlsCheck.OK, result.variant)
            assertEquals(base, HlsCheck.OK, result.segment)
            assertEquals(base, true, result.durationMatch)
            assertEquals(base, "NONE", result.key)
        }
        val faults = SimCases.all().filter { it.klass == "F3" }
        assertEquals(5 * Sim.BASES.size, faults.size)
        for (case in faults) {
            val world = case.world()
            assertFalse(case.id, world.hls.good)
            val result = HlsCheck(world.http).check(world.hls.masterUrl, emptyMap(), world.lengthSeconds)
            when (case.variant) {
                "segment-403" -> assertEquals(case.id, "403", result.segment)
                "host" -> assertEquals(case.id, HlsCheck.HOST, result.segment)
                "duration" -> assertEquals(case.id, false, result.durationMatch)
                // The content type and start bytes are D4's checks on top of D0's.
                else -> assertEquals(case.id, HlsCheck.OK, result.segment)
            }
        }
    }

    @Test
    fun `L14 moves every page signal the reader reads`() {
        val plain = YouTubePlayerResponseParser.pageSignals(SimWorld(Sim.BASES.first()).pageHtml())
        assertNotNull(plain.playerId)
        assertNotNull(plain.signatureTimestamp)
        assertNotNull(plain.visitorData)
        assertNotNull(plain.playerResponseJson)
        for (case in SimCases.all().filter { it.klass == "L14" }) {
            val moved = YouTubePlayerResponseParser.pageSignals(case.world().pageHtml())
            assertNull(case.id, moved.playerId)
            assertNull(case.id, moved.signatureTimestamp)
            assertNull(case.id, moved.visitorData)
            assertNotNull(case.id, moved.playerResponseJson)
        }
    }

    @Test
    fun `V fail and unknown act on the L rows only`() {
        fun world(klass: String, mode: VMode) = SimCases.all()
            .first { it.klass == klass && it.base == Sim.BASES.first() && it.vMode == mode && it.variant.isEmpty() }
            .world()
            .apply {
                answer(SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false))
                answer(SimAsk(SimClient.MWEB, "", visitor = true, sts = true))
                answer(SimAsk(SimClient.ANDROID, "", visitor = false, sts = false))
            }
        fun SimWorld.files(tag: String) = media.values.filter { it.key.startsWith(tag) }.also { assertTrue(tag, it.isNotEmpty()) }
        fun SimWorld.address(file: SimMedia) = mediaUrl(file.host, file.itag, file.key, file.mime, file.n, lengthSeconds)

        // L3: visionOS's and Android's renamed addresses are L rows; the script clients' ciphers are not.
        val fail = world("L3", VMode.FAIL)
        assertTrue((fail.files("vo") + fail.files("an")).all { it.status == SimWorld.FORBIDDEN })
        assertTrue(fail.files("mws").all { it.status == SimWorld.OK })
        val unknown = world("L3", VMode.UNKNOWN)
        val renamed = unknown.files("vo").first()
        assertTrue(unknown.probe(unknown.address(renamed)) is ExtractorProbeResult.Refused)
        assertEquals("unchecked", unknown.truth(unknown.address(renamed)))
        assertTrue(unknown.files("vo").all { it.status == SimWorld.OK })
        assertTrue(unknown.hls.good)

        // L6: the copied parser reads every drifted format, so no file is an L row.
        assertTrue(world("L6", VMode.FAIL).media.values.all { it.status == SimWorld.OK })

        // L13 and L1: the moved HLS address is an L row; L2 keeps it in place.
        assertEquals(SimWorld.FORBIDDEN, world("L13", VMode.FAIL).hls.master)
        assertFalse(world("L13", VMode.UNKNOWN).hls.good)
        assertFalse(world("L1", VMode.FAIL).hls.good)
        assertTrue(world("L2", VMode.FAIL).hls.good)

        // L14: the page signals moved, so every file that needs the player script is an L row.
        val page = world("L14", VMode.FAIL)
        assertTrue((page.files("vos") + page.files("mws")).all { it.status == SimWorld.FORBIDDEN })
        assertTrue(page.files("an").all { it.status == SimWorld.OK })
    }

    @Test
    fun `decoys and protected files are wrong rows`() {
        val world = SimWorld(Sim.BASES.first())
        assertEquals("other video", world.truth(world.extraFile("d-137", 137, "video/mp4", Sim.OTHER_VIDEO, 30)))
        world.encrypted = true
        val vision = parse(world, SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false))
        assertTrue((vision.progressive + vision.adaptive).all { world.truth(it.url) == "encrypted" })
        assertEquals("unknown", world.truth("https://${Sim.DEVICE_HOST}/videoplayback?itag=18"))
    }

    /**
     * Plan §5's baseline, R6 columns: every case through R6 with the own solver off and on, pinned
     * line by line. R6 never sends the user's cookie or authorization.
     */
    @Test
    fun `R6 baseline over every class in both own-solver columns`() = runTest(timeout = 10.minutes) {
        val runs = SimCases.all().flatMap { case -> SimColumn.entries.map { column -> SimMeasure.run(case, column) } }
        assertEquals(0, runs.sumOf { run -> run.lookups.sumOf(SimLookup::credentials) })

        val text = runs.joinToString("\n", postfix = "\n") { it.line() }
        val out = File("build/ladder-sim").apply { mkdirs() }
        File(out, "r6-baseline.txt").writeText(text)
        File(out, "r6-baseline-summary.md").writeText(summary(runs))

        val golden = javaClass.getResource(GOLDEN)?.readText()
            ?: return@runTest fail("no $GOLDEN: review build/ladder-sim/r6-baseline.txt and pin it there")
        val expected = golden.lines()
        val actual = text.lines()
        val changed = actual.indices.filter { it >= expected.size || expected[it] != actual[it] }
        if (changed.isNotEmpty() || expected.size != actual.size) {
            fail(
                "R6 baseline changed in ${changed.size} lines (expected ${expected.size}, got ${actual.size}):\n" +
                    changed.take(MAX_SHOWN).joinToString("\n") { "- ${expected.getOrNull(it)}\n+ ${actual[it]}" },
            )
        }
    }

    private fun summary(runs: List<SimRun>): String = buildString {
        append("| Class | ").append(SimColumn.entries.joinToString(" | ") { it.label }).append(" |\n")
        append("|---|").append(SimColumn.entries.joinToString("") { "---|" }).append('\n')
        for (klass in SimCases.CLASSES) {
            append("| ").append(klass).append(" | ")
            append(
                SimColumn.entries.joinToString(" | ") { column ->
                    SimMeasure.cell(runs.filter { it.case.klass == klass && it.column == column })
                },
            )
            append(" |\n")
        }
        val total = SimColumn.entries.joinToString(" | ") { column ->
            val inColumn = runs.filter { it.column == column }
            val lookups = inColumn.sumOf { it.lookups.size }
            "wrong ${inColumn.sumOf(SimRun::wrong)}, MCF ${inColumn.sumOf(SimRun::mcf)} (${inColumn.size} cases, $lookups lookups)"
        }
        append("| all | ").append(total).append(" |\n")
    }

    private fun parse(world: SimWorld, ask: SimAsk) =
        (YouTubePlayerResponseParser.parse(SimJson.write(world.answer(ask)), Sim.NOW) as YouTubeParseResult.Success).video

    private fun solve(kind: PlayerScriptChallengeKind, input: String) = FakePlayerScriptRunner.transform(kind, input)

    /** What a reader could see of a world: its answers, page, versions, sources, HLS and files. */
    private fun fingerprint(world: SimWorld): String = buildString {
        for (client in SimClient.entries) {
            val ask = SimAsk(client, Sim.BASE_VERSION, visitor = true, sts = true)
            append(client).append('=').append(
                when (val reply = world.override(ask) ?: world.defaultReply(ask)) {
                    is SimReply.Json -> reply.body
                    is SimReply.Status -> "status ${reply.code}"
                    SimReply.Network -> "network"
                },
            ).append('\n')
        }
        append(world.pageHtml()).append('\n')
        append(world.acceptedVersions).append(world.sources).append(world.solver).append(world.probesDown)
        append(world.hls.good).append(world.hls.masterText()).append(world.hls.variantText())
        world.media.values.forEach { append(it.status).append(it.encrypted).append(it.videoId) }
    }

    private companion object {
        const val GOLDEN = "/ladder/r6-baseline.txt"
        const val MAX_SHOWN = 40
    }
}
