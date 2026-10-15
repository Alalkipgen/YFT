package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D6 (MASTER_KEY_PHASE1_3_PLAN.md §4 D6, §5): the ladder columns of the change
 * simulator. Every case of [SimCases] runs through R6 ([SimMeasure.run], the pinned baseline) and
 * through the same reader with the ladder ([MasterClientLadder]), own solver off and on, each in
 * its own world. The ladder never offers a wrong row, never ends a lookup worse than R6
 * (complete > without sizes > partial > clean stop), sends no player request R6 would not and
 * stays inside its per-lookup bounds; the cells for plan §5 are written to
 * `build/ladder-sim/ladder-summary.md` (never an address or value).
 */
class ClientLadderSimTest {
    private class Pair2(val case: SimCase, val solver: Boolean, val r6: SimRun, val ladder: SimRun, val reads: Int, val notes: List<String>)

    @Test
    fun `the ladder over every class in both own-solver columns`() = runTest(timeout = 15.minutes) {
        val pairs = SimCases.all().flatMap { case -> listOf(false, true).map { solver -> pair(case, solver) } }
        val out = File("build/ladder-sim").apply { mkdirs() }
        File(out, "ladder-lines.txt").writeText(pairs.joinToString("\n", postfix = "\n") { line(it) })
        File(out, "ladder-summary.md").writeText(summary(pairs))
        File(out, "ladder-notes.txt").writeText(
            pairs.joinToString("\n", postfix = "\n") { "${it.case.id} | ${column(it.solver)} | ${it.notes.joinToString("; ")}" },
        )

        val worse = pairs.flatMap { pair ->
            pair.r6.lookups.zip(pair.ladder.lookups).mapIndexedNotNull { index, (r6, ladder) ->
                "${pair.case.id} ${column(pair.solver)} #${index + 1}: ${r6.outcome} -> ${ladder.outcome}"
                    .takeIf { rank(ladder.outcome) < rank(r6.outcome) }
            }
        }
        assertEquals("worse than R6: $worse", emptyList<String>(), worse)
        val newWrong = pairs.filter { it.ladder.wrong > it.r6.wrong }.map { "${it.case.id} ${column(it.solver)}" }
        assertEquals("new wrong rows: $newWrong", emptyList<String>(), newWrong)
        // Plan §5.2 budget: V1–V5 and L1–L15 no more player requests than R6; T1–T3, T1b–T1d no
        // InnerTube request (player or page) R6 would not send.
        val morePlayers = pairs.filter { pair ->
            val klass = pair.case.klass
            val budget = klass in BUDGET_CLASSES || klass.startsWith("T")
            val pages = klass.startsWith("T") && pair.ladder.lookups.sumOf(SimLookup::pages) > pair.r6.lookups.sumOf(SimLookup::pages)
            budget && (pages || pair.ladder.lookups.sumOf(SimLookup::players) > pair.r6.lookups.sumOf(SimLookup::players))
        }.map { "${it.case.id} ${column(it.solver)}" }
        assertEquals("requests over the budget: $morePlayers", emptyList<String>(), morePlayers)
        assertTrue(pairs.all { pair -> pair.ladder.lookups.all { it.credentials == 0 } })
        assertTrue(pairs.all { it.reads <= ShapeNormalizer.MAX_OFFER * it.case.lookups })
    }

    private suspend fun pair(case: SimCase, solver: Boolean): Pair2 {
        val r6 = SimMeasure.run(case, if (solver) SimColumn.R6_ON else SimColumn.R6_OFF)
        val world = case.world()
        val reader = SimStartReader(world)
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, reader)
        val module = if (solver) {
            MasterYouTubeModule(world.http, SimSolver(world), ladder)
        } else {
            MasterYouTubeModule(world.http, ladder = ladder)
        }
        val notes = mutableListOf<String>()
        val lookups = (0 until case.lookups).map { index ->
            val from = world.http.log.size
            val result = module.extract(SimMeasure.request())
            val details = when (result) {
                is SiteExtractionResult.Success -> result.details
                is SiteExtractionResult.Failure -> result.details
            }
            notes += details.filter { it.startsWith("ladder ") }.map { "#${index + 1} ${it.removePrefix("ladder ")}" }
            SimMeasure.judge(world, case, result, from)
        }
        return Pair2(case, solver, r6, SimRun(case, r6.column, lookups), reader.reads, notes)
    }

    private companion object {
        val BUDGET_CLASSES = (1..5).map { "V$it" } + (1..15).map { "L$it" }
    }

    private fun column(solver: Boolean) = if (solver) "LADDER_ON" else "LADDER_OFF"

    private fun line(pair: Pair2): String {
        val r6 = pair.r6.line().substringAfter(" | ").substringAfter(" | ")
        val ladder = pair.ladder.line().substringAfter(" | ").substringAfter(" | ")
        val mark = if (r6 == ladder) "same" else "CHANGED"
        return "${pair.case.id} | ${column(pair.solver)} | $mark | $ladder | reads ${pair.reads}" +
            if (mark == "same") "" else "\n    R6: $r6"
    }

    private fun rank(outcome: SimOutcome): Int = when (outcome) {
        SimOutcome.COMPLETE -> 4
        SimOutcome.UNSIZED -> 3
        SimOutcome.PARTIAL -> 2
        SimOutcome.STOP -> 1
        SimOutcome.WRONG -> 0
    }

    private fun summary(pairs: List<Pair2>): String = buildString {
        append("# Phase 1.3 D6 — ladder columns (simulator)\n\n")
        append("| Class | R6, own solver off | ladder, own solver off | R6, own solver on | ladder, own solver on |\n")
        append("|---|---|---|---|---|\n")
        for (klass in SimCases.CLASSES) {
            val of = pairs.filter { it.case.klass == klass }
            fun cells(solver: Boolean): String {
                val group = of.filter { it.solver == solver }
                return SimMeasure.cell(group.map { it.r6 }) + " | " + SimMeasure.cell(group.map { it.ladder })
            }
            append("| ").append(klass).append(" | ").append(cells(false)).append(" | ").append(cells(true)).append(" |\n")
        }
        fun totals(solver: Boolean, pick: (Pair2) -> SimRun): String {
            val group = pairs.filter { it.solver == solver }.map(pick)
            return "wrong ${group.sumOf(SimRun::wrong)}, MCF ${group.sumOf(SimRun::mcf)}"
        }
        append("| total | ").append(totals(false) { it.r6 }).append(" | ").append(totals(false) { it.ladder })
        append(" | ").append(totals(true) { it.r6 }).append(" | ").append(totals(true) { it.ladder }).append(" |\n")
    }

    /** The media host's first bytes through the world's HTTP (logged as media), as the app's reader returns them. */
    private class SimStartReader(private val world: SimWorld) : MediaStartReader {
        var reads = 0

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            reads += 1
            return when (val got = world.http.get(url, headers, maxBytes.toLong())) {
                is ExtractorHttpResult.Success -> {
                    val bytes = got.body.toByteArray(Charsets.ISO_8859_1).take(maxBytes).toByteArray()
                    val size = Sim.query(url)["yftsim"]?.let { world.media[it]?.size }
                    MediaStart.Read(
                        statusCode = got.statusCode,
                        contentType = got.contentType,
                        contentRange = size?.let { "bytes 0-${bytes.size - 1}/$it" },
                        contentLength = bytes.size.toLong(),
                        bytes = bytes,
                    )
                }
                is ExtractorHttpResult.Failure -> MediaStart.Failed(got.statusCode)
            }
        }
    }
}
