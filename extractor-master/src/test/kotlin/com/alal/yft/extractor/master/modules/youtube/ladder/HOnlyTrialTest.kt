package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5c (MASTER_KEY_PHASE1_3_PLAN.md §4 D5c; the owner's order of 2026-10-16): the
 * `h-only` trial, for the owner's phone download through H. Over every simulator class, own solver
 * off and on, each lookup's result is H's one HLS row alone (the chain's rows left out) or no row
 * with H's reason: never a wrong row or a credential, at most one H check (3 requests), and every
 * class with an H path ends complete. The H row carries the chain's video key in both modes (the
 * app's browser hook keeps only rows with the page's key); both audio forms give the row.
 */
class HOnlyTrialTest {
    private val trial = LadderTrial.of(LadderTrial.H_ONLY, "")
    private val key = SimMeasure.request().identity.let { "${it.siteId}:${it.contentId}" }

    private class Lookups(val judged: List<SimLookup>, val results: List<SiteExtractionResult>, val notes: List<String>)

    @Test
    fun `every class - the H row alone or no row with H's reason, never a wrong row`() = runTest(timeout = 10.minutes) {
        val lines = mutableListOf<String>()
        val missed = mutableListOf<String>()
        for (case in SimCases.all()) {
            for (solver in listOf(false, true)) {
                val run = run(case.world(), case, solver, trial)
                val where = "${case.id} solver=$solver"
                lines += "$where | " + run.judged.joinToString { "${it.outcome} rows ${it.rows} hls ${it.hls} wrong ${it.wrong.size}" } +
                    " | " + run.notes.joinToString("; ")
                assertTrue("wrong rows: $where", run.judged.all { it.wrong.isEmpty() && it.credentials == 0 })
                assertTrue("one H check: $where", run.judged.all { it.hls <= 3 })
                for (result in run.results) {
                    val details = details(result)
                    assertTrue(where, details.any { it.startsWith("ladder trial h-only: the chain's ") })
                    val rows = (result as? SiteExtractionResult.Success)?.candidates.orEmpty()
                    if (rows.isEmpty()) {
                        assertTrue("no row without H's reason: $where", details.any { it.startsWith("ladder H: ") })
                        continue
                    }
                    val row = rows.single()
                    assertEquals(where, MediaKind.HLS, row.kind)
                    assertEquals(where, key, row.videoId)
                    assertTrue(where, details.any { it.startsWith("ladder H: one HLS row (") })
                }
                if (Rung.H in case.paths && !case.terminal && run.judged.any { it.outcome != SimOutcome.COMPLETE }) missed += where
            }
        }
        File("build/ladder-sim").apply { mkdirs() }.resolve("ladder-h-only.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        assertEquals("an H path without the H row", emptyList<String>(), missed)
    }

    @Test
    fun `both audio forms give the one row - muxed and separate`() = runTest {
        val case = SimCases.all().first { it.klass == "B0" }
        for (separate in listOf(false, true)) {
            val world = case.world().apply { hls.separateAudio = separate }
            val run = run(world, case, solver = false, trial)
            val result = run.results.single() as SiteExtractionResult.Success
            val row = result.candidates.single()
            assertEquals(world.hls.masterUrl, row.mediaUrl)
            val form = if (separate) "audio separate" else "audio muxed"
            assertTrue(form, result.details.any { it.startsWith("ladder H: one HLS row (") && form in it })
            assertTrue(form, result.details.any { it.startsWith("ladder trial h-only: the chain's ") && !it.endsWith(" 0 rows left out") })
        }
    }

    @Test
    fun `no H row - no row at all, the reason in the notes`() = runTest {
        val case = SimCases.all().first { it.klass == "B0" }
        val worlds = listOf<Pair<String, SimWorld.() -> Unit>>(
            "ladder H: no HLS address" to { hls.present = false },
            "ladder H: KEY" to { hls.key = "AES-128" },
            "ladder H: SEGMENT_START" to { hls.startText = "X" },
        )
        for ((reason, change) in worlds) {
            val run = run(case.world().apply(change), case, solver = false, trial)
            val result = run.results.single() as SiteExtractionResult.Failure
            assertEquals(reason, SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
            assertTrue("$reason: ${result.details}", reason in result.details)
        }
        // visionOS closed (HTTP 400; no A in a trial) and an age check: no H, never the chain's rows.
        for ((klass, reason) in listOf("V9" to "H: no HLS address", "L11" to "H: visionOS's answer AGE_CHECK")) {
            val closed = SimCases.all().first { it.klass == klass && (klass != "L11" || it.variant == "age") }
            val run = run(closed.world(), closed, solver = false, trial)
            assertTrue(klass, run.results.all { it is SiteExtractionResult.Failure })
            assertTrue("$klass: ${run.notes}", reason in run.notes)
        }
    }

    @Test
    fun `the normal ladder's H row carries the chain's video key too`() = runTest(timeout = 5.minutes) {
        var seen = 0
        for (case in SimCases.all().filter { Rung.H in it.paths && !it.terminal }) {
            val run = run(case.world(), case, solver = false, LadderTrial.NONE)
            for (result in run.results) {
                val rows = (result as? SiteExtractionResult.Success)?.candidates.orEmpty()
                if (rows.none { it.kind == MediaKind.HLS }) continue
                seen += 1
                assertEquals(case.id, setOf(key), rows.map { it.videoId }.toSet())
            }
        }
        assertTrue("an H row in the normal ladder", seen > 0)
    }

    private suspend fun run(world: SimWorld, case: SimCase, solver: Boolean, trial: LadderTrial): Lookups {
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, ClientLadderSimTest.SimStartReader(world), trial)
        val module = if (solver) MasterYouTubeModule(world.http, SimSolver(world), ladder) else MasterYouTubeModule(world.http, ladder = ladder)
        val judged = mutableListOf<SimLookup>()
        val results = mutableListOf<SiteExtractionResult>()
        repeat(case.lookups) {
            val from = world.http.log.size
            val result = module.extract(SimMeasure.request())
            results += result
            judged += SimMeasure.judge(world, case, result, from)
        }
        val notes = results.flatMap(::details).filter { it.startsWith("ladder ") }.map { it.removePrefix("ladder ") }
        return Lookups(judged, results, notes)
    }

    private fun details(result: SiteExtractionResult): List<String> = when (result) {
        is SiteExtractionResult.Success -> result.details
        is SiteExtractionResult.Failure -> result.details
    }
}
