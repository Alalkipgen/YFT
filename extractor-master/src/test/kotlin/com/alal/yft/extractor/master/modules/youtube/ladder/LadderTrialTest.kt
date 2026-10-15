package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D7 prep: the owner's live-check switches ([LadderTrial]) — the build's values, each
 * layout change on an answer, and every mode over the simulator's B0 videos (own solver off and
 * on): no wrong row ever; L1, L2, L3 and L7 give the normal mode's outcome through L (its offer set);
 * L5 leaves a cipherless answer as it came; skipping visionOS or its adaptive formats is noted.
 */
class LadderTrialTest {
    @Test
    fun `the build's values - blank is none, one mode at a time, unknown refused`() {
        assertSame(LadderTrial.NONE, LadderTrial.of("", " "))
        assertEquals("visionos", LadderTrial.of("visionos", "").label)
        assertEquals("visionos-adaptive", LadderTrial.of("visionos-adaptive", "").label)
        LadderTrial.MUTATIONS.forEach { assertEquals(it, LadderTrial.of("", it).label) }
        assertThrows(IllegalArgumentException::class.java) { LadderTrial.of("android", "") }
        assertThrows(IllegalArgumentException::class.java) { LadderTrial.of("", "L4") }
        assertThrows(IllegalArgumentException::class.java) { LadderTrial.of("visionos", "L1") }
    }

    @Test
    fun `each layout change on an answer, and none where it has nothing to change`() {
        val root = ShapeLocator.parse(ANSWER) as JsonValue.Object
        fun text(value: JsonValue?) = value?.let(LadderJson::text)

        assertEquals(
            """{"videoDetails":{"videoId":"x"},"playback":{"streamingData":{"formats":[{"itag":18,"url":"u18"}],"adaptiveFormats":[{"itag":137,"url":"u137"},{"itag":137,"url":"u137b"}]}}}""",
            text(LadderTrial.mutate("L1", root)),
        )
        assertEquals(
            """{"videoDetails":{"videoId":"x"},"streamingData":{"muxedFormats":[{"itag":18,"url":"u18"}],"separateFormats":[{"itag":137,"url":"u137"},{"itag":137,"url":"u137b"}]}}""",
            text(LadderTrial.mutate("L2", root)),
        )
        assertEquals(
            """{"videoDetails":{"videoId":"x"},"streamingData":{"formats":[{"itag":18,"streamUrl":"u18"}],"adaptiveFormats":[{"itag":137,"streamUrl":"u137"},{"itag":137,"streamUrl":"u137b"}]}}""",
            text(LadderTrial.mutate("L3", root)),
        )
        assertNull("no cipher: L5 changes nothing", LadderTrial.mutate("L5", root))
        val ciphered = ShapeLocator.parse("""{"streamingData":{"formats":[{"itag":18,"signatureCipher":"s"}]}}""") as JsonValue.Object
        assertEquals("""{"streamingData":{"formats":[{"itag":18,"streamCipher":"s"}]}}""", text(LadderTrial.mutate("L5", ciphered)))
        assertEquals(
            """{"videoDetails":{"videoId":"x"},"streamingData":{"formats":{"18":{"itag":18,"url":"u18"}},"adaptiveFormats":{"137":{"itag":137,"url":"u137"},"137-1":{"itag":137,"url":"u137b"}}}}""",
            text(LadderTrial.mutate("L7", root)),
        )
        assertEquals(
            """{"videoDetails":{"videoId":"x"},"streamingData":{"formats":[{"itag":18,"url":"u18"}]}}""",
            text(LadderTrial.of("visionos-adaptive", "").apply(root)),
        )
        val bare = ShapeLocator.parse("""{"videoDetails":{"videoId":"x"}}""") as JsonValue.Object
        (LadderTrial.MUTATIONS).forEach { assertNull(it, LadderTrial.mutate(it, bare)) }
        assertNull(LadderTrial.of("visionos-adaptive", "").apply(bare))
        assertNull(LadderTrial.NONE.apply(root))
    }

    @Test
    fun `a trial never reads or writes the health file, the normal ladder does (D7)`() {
        class CountingFile : OwnSolverTextFile {
            var reads = 0
            var writes = 0

            override fun read(): String? = null.also { reads += 1 }

            override fun write(text: String): Boolean = true.also { writes += 1 }
        }
        for (trial in LadderTrial.SKIPS.map { LadderTrial.of(it, "") } + LadderTrial.MUTATIONS.map { LadderTrial.of("", it) }) {
            val file = CountingFile()
            val ladder = MasterClientLadder(healthFile = file, reader = null, trial = trial)
            ladder.health.record("visionos", CensusClass.SHAPE_CHANGED, repairedBy = ClientHealth.Rung.L)
            ladder.health.record("android", CensusClass.DIRECT)
            assertEquals(trial.label, listOf(0, 0), listOf(file.reads, file.writes))
        }
        val file = CountingFile()
        MasterClientLadder(healthFile = file, reader = null).health.record("visionos", CensusClass.DIRECT)
        assertTrue(file.reads > 0 && file.writes > 0)
    }

    @Test
    fun `every mode over the B0 videos - no wrong row, L finds the normal rows again`() = runTest(timeout = 10.minutes) {
        val modes = listOf(LadderTrial.NONE) + LadderTrial.SKIPS.map { LadderTrial.of(it, "") } +
            LadderTrial.MUTATIONS.map { LadderTrial.of("", it) }
        val lines = mutableListOf<String>()
        for (case in SimCases.all().filter { it.klass == "B0" }) {
            for (solver in listOf(false, true)) {
                val normal = run(case, solver, LadderTrial.NONE)
                for (trial in modes) {
                    val (lookups, notes) = if (trial === LadderTrial.NONE) normal else run(case, solver, trial)
                    val where = "${case.id} solver=$solver ${trial.label}"
                    lines += "$where | " + lookups.joinToString { "${it.outcome} rows ${it.rows} wrong ${it.wrong.size}" } + " | " + notes.joinToString("; ")
                    assertTrue("wrong rows: $where", lookups.all { it.wrong.isEmpty() && it.credentials == 0 })
                    when (trial.label) {
                        "none" -> Unit
                        "L1", "L2", "L3", "L7" -> {
                            // L hands the chain the offer set (the app's selection, ≤ 8 rows) that passed V.
                            assertEquals(where, normal.first.map { it.outcome }, lookups.map { it.outcome })
                            assertTrue(where, normal.first.zip(lookups).all { (a, b) -> b.rows in 1..a.rows })
                            assertTrue(where, notes.any { it.startsWith("trial ${trial.label}: visionOS's answer changed") })
                            assertTrue(where, notes.any { it.startsWith("L: ") && "passed V" in it })
                        }
                        "L5" -> {
                            assertEquals(where, normal.first.map { it.outcome to it.rows }, lookups.map { it.outcome to it.rows })
                            assertTrue(where, notes.any { it.startsWith("trial L5: visionOS's answer unchanged") })
                        }
                        "visionos" -> assertTrue(where, notes.any { it.startsWith("trial visionos: visionOS's answer not used") })
                        else -> assertTrue(where, notes.any { it.startsWith("trial visionos-adaptive: visionOS's answer changed") })
                    }
                }
            }
        }
        File("build/ladder-sim").apply { mkdirs() }.resolve("ladder-trials.txt").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private suspend fun run(case: SimCase, solver: Boolean, trial: LadderTrial): Pair<List<SimLookup>, List<String>> {
        val world = case.world()
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, ClientLadderSimTest.SimStartReader(world), trial)
        val module = if (solver) MasterYouTubeModule(world.http, SimSolver(world), ladder) else MasterYouTubeModule(world.http, ladder = ladder)
        val notes = mutableListOf<String>()
        val lookups = (0 until case.lookups).map {
            val from = world.http.log.size
            val result = module.extract(SimMeasure.request())
            val details = when (result) {
                is SiteExtractionResult.Success -> result.details
                is SiteExtractionResult.Failure -> result.details
            }
            notes += details.filter { it.startsWith("ladder ") }.map { it.removePrefix("ladder ") }
            SimMeasure.judge(world, case, result, from)
        }
        return lookups to notes
    }

    private companion object {
        const val ANSWER =
            """{"videoDetails":{"videoId":"x"},"streamingData":{"formats":[{"itag":18,"url":"u18"}],"adaptiveFormats":[{"itag":137,"url":"u137"},{"itag":137,"url":"u137b"}]}}"""
    }
}
