package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.lookup.RouteBMediaStartReader
import com.alal.yft.extractor.master.lookup.RouteBPlayerScripts
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor as MainYouTubeExtractor
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B) R2, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §4 "Simulator":
 * one lookup = main, then Master's YouTube module with the ladder through route B's wrappers, in
 * the same simulated world. Main (`extractor-sites`, called by this test only) asks first and its
 * asks go into the lookup's [LookupRecord]; Master then runs inside a [RouteBLookup] over that
 * record: main's identical unauthenticated answers are replayed, what main asked is not asked
 * again, nothing new goes out after a 429. Master runs after every main result here, as route B
 * R4's debug trial makes it (the engine's claim rule, R3, lets it answer only after a recoverable
 * failure). The lines are written to `build/routeb-sim/routeb-lines.txt` (counts, never an
 * address or value).
 */
class RouteBSimTest {
    private class Step(
        val main: SimLookup,
        val mainResult: SiteExtractionResult,
        val master: SimLookup,
        val record: LookupRecord,
        val route: RouteBLookup,
        /** Requests in the world's log during Master's run. */
        val logged: Int,
        /** A's version-source reads during Master's run (D5a's own reader, not YouTube). */
        val sourceReads: Int,
    ) {
        val recoverable: Boolean
            get() = (mainResult as? SiteExtractionResult.Failure)?.reason in TerminalRules.ROUTE_B_RECOVERABLE
    }

    private class Run(
        val case: SimCase,
        val solver: Boolean,
        val steps: List<Step>,
        val plain: List<SimLookup>,
        val reads: Int,
        val segmentReads: Int,
        val sourceReads: Int,
    )

    @Test
    fun `route B after main over every class in both own-solver columns`() = runTest(timeout = 15.minutes) {
        val runs = SimCases.all().flatMap { case -> listOf(false, true).map { solver -> run(case, solver) } }
        File("build/routeb-sim").apply { mkdirs() }.resolve("routeb-lines.txt")
            .writeText(runs.joinToString("\n", postfix = "\n", transform = ::line))

        val steps = runs.flatMap { run -> run.steps.mapIndexed { index, step -> Triple(run, index, step) } }
        fun names(pick: (Run, Int, Step) -> Boolean): List<String> =
            steps.filter { (run, index, step) -> pick(run, index, step) }
                .map { (run, index, _) -> "${run.case.id} ${column(run.solver)} #${index + 1}" }

        assertEquals("wrong rows", emptyList<String>(), names { _, _, step -> step.master.wrong.isNotEmpty() })
        assertEquals("credentials", emptyList<String>(), names { _, _, step -> step.master.credentials > 0 })
        // Guardrail 7: one watch page per lookup (main's and Master's together), at most three
        // player requests of Master's own, and never more than the ladder alone would send.
        assertEquals("watch pages", emptyList<String>(), names { _, _, step -> step.main.pages + step.master.pages > 1 })
        assertEquals("player requests", emptyList<String>(), names { _, _, step -> step.master.players > MAX_PLAYERS })
        val morePlayers = runs.filter { run -> run.steps.sumOf { it.master.players } > run.plain.sumOf(SimLookup::players) }
            .map { "${it.case.id} ${column(it.solver)}" }
        assertEquals("more player requests than the ladder alone", emptyList<String>(), morePlayers)
        // Every request Master's module sent is a new one of the record (replays and refusals send
        // nothing); A's version source (D5a, not YouTube) is read through its own reader, outside it.
        assertEquals(
            "unlisted requests",
            emptyList<String>(),
            names { _, _, step -> step.logged - step.sourceReads != step.route.newRequests },
        )
        assertEquals("unlisted record", emptyList<String>(), names { _, _, step -> step.record.masterAsks != step.route.newRequests })
        assertTrue(runs.all { it.reads <= ShapeNormalizer.MAX_OFFER * it.case.lookups })
        assertTrue(runs.all { it.segmentReads <= it.case.lookups })
        val sourceReads = runs.filter { it.sourceReads > (if (it.case.klass in A_CLASSES) 1 else 0) }
            .map { "${it.case.id} ${column(it.solver)}" }
        assertEquals("version source reads", emptyList<String>(), sourceReads)
        // After a recoverable failure (route B's gate, R3) Master ends no worse than the ladder alone.
        val worse = names { run, index, step ->
            step.recoverable && rank(step.master.outcome) < rank(run.plain[index].outcome)
        }
        assertEquals("worse than the ladder alone", emptyList<String>(), worse)
        assertTrue("no recoverable failure in the simulator", steps.any { it.third.recoverable })
    }

    @Test
    fun `a moved shape - L reads main's replayed answer with no new player request`() = runTest(timeout = 5.minutes) {
        val cases = Sim.BASES.flatMap(SimCases::forBase)
            .filter { it.klass in MOVED_SHAPE && it.vMode == VMode.PASS }
        assertTrue(cases.size >= Sim.BASES.size * MOVED_SHAPE.size)
        for (case in cases) for (solver in listOf(false, true)) {
            val step = run(case, solver).steps.single()
            val name = "${case.id} ${column(solver)}"
            val failure = step.mainResult as? SiteExtractionResult.Failure
            assertTrue("$name: main failed (${failure?.reason})", step.recoverable)
            assertEquals(name, SimOutcome.COMPLETE, step.master.outcome)
            assertEquals(name, 0, step.master.players)
            assertEquals(name, 0, step.master.pages)
            assertEquals(name, 0, step.master.credentials)
            assertTrue(name, step.route.replayed >= 1)
            assertEquals(name, step.route.replayed, step.record.replayedCount)
            assertTrue(name, step.route.summary().startsWith("replayed ${step.route.replayed}; new ${step.route.newRequests}"))
        }
    }

    @Test
    fun `after a 429 in main's lookup Master sends nothing new`() = runTest(timeout = 5.minutes) {
        for (base in Sim.BASES) for (solver in listOf(false, true)) {
            val case = SimCases.forBase(base).first { it.klass == "L2" && it.vMode == VMode.PASS }
            val step = run(case, solver) {
                override = { ask -> if (ask.client == SimClient.ANDROID) SimReply.Status(SimWorld.TOO_MANY) else null }
            }.steps.single()
            val name = "${case.id} ${column(solver)}"
            assertTrue(name, step.record.rateLimited)
            assertEquals(name, 0, step.logged)
            assertEquals(name, 0, step.route.newRequests)
            assertEquals(name, 0, step.record.masterAsks)
            assertTrue(name, step.route.refused >= 1)
            assertTrue(name, step.master.wrong.isEmpty())
            assertEquals(name, 0, step.master.afterTerminal)
        }
    }

    private suspend fun run(case: SimCase, solver: Boolean, change: SimWorld.() -> Unit = {}): Run {
        val plain = plain(case, solver, change)
        val world = case.world().apply(change)
        val main = MainYouTubeExtractor(LookupRecorder(world.http), SimSolver(world))
        val reader = ClientLadderSimTest.SimStartReader(world)
        val sources = ClientLadderSimTest.SimSourceReader(world)
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, RouteBMediaStartReader(reader), sources = sources)
        val scripts = RouteBPlayerScripts.wrap(if (solver) SimSolver(world) else NoPlayerScriptRunner)
        val master = MasterYouTubeModule(RouteBHttpClient(world.http), scripts, ladder)
        val steps = (0 until case.lookups).map {
            val record = LookupRecord(clock = { Sim.NOW })
            val mainFrom = world.http.log.size
            val mainResult = withContext(record) { main.extract(SimMeasure.request()) }
            val mainJudged = SimMeasure.judge(world, case, mainResult, mainFrom)
            val from = world.http.log.size
            val sourcesFrom = sources.reads.size
            val route = RouteBLookup(record)
            val result = withContext(record + route) { master.extract(SimMeasure.request()) }
            val judged = SimMeasure.judge(world, case, result, from)
            Step(mainJudged, mainResult, judged, record, route, world.http.log.size - from, sources.reads.size - sourcesFrom)
        }
        return Run(case, solver, steps, plain, reader.reads, reader.segmentReads, sources.reads.size)
    }

    /** The ladder alone in its own world ([ClientLadderSimTest]'s column): what Master would do without main. */
    private suspend fun plain(case: SimCase, solver: Boolean, change: SimWorld.() -> Unit): List<SimLookup> {
        val world = case.world().apply(change)
        val ladder = MasterClientLadder(
            ClientHealth(null) { Sim.NOW },
            ClientLadderSimTest.SimStartReader(world),
            sources = ClientLadderSimTest.SimSourceReader(world),
        )
        val module = MasterYouTubeModule(world.http, if (solver) SimSolver(world) else NoPlayerScriptRunner, ladder)
        return (0 until case.lookups).map {
            val from = world.http.log.size
            SimMeasure.judge(world, case, module.extract(SimMeasure.request()), from)
        }
    }

    private fun line(run: Run): String = buildString {
        append(run.case.id).append(" | ").append(column(run.solver))
        run.steps.forEachIndexed { index, step ->
            append(" | #").append(index + 1).append(' ')
            append("main ").append(step.main.outcome.name).append('/').append(step.main.failure?.name ?: "-")
            append(if (step.recoverable) " > route B " else " > trial ")
            append(step.master.outcome.name).append(" wrong ").append(step.master.wrong.size)
            append(", ").append(step.route.summary())
            append(", player ").append(step.master.players).append(", page ").append(step.master.pages)
            append(", probe ").append(step.master.probes).append(", hls ").append(step.master.hls)
            append(", media ").append(step.master.media)
            append(" (ladder alone ").append(run.plain[index].outcome.name).append(')')
        }
    }

    private fun column(solver: Boolean) = if (solver) "ROUTE_B_ON" else "ROUTE_B_OFF"

    private fun rank(outcome: SimOutcome): Int = when (outcome) {
        SimOutcome.COMPLETE -> 4
        SimOutcome.UNSIZED -> 3
        SimOutcome.PARTIAL -> 2
        SimOutcome.STOP -> 1
        SimOutcome.WRONG -> 0
    }

    private companion object {
        /** Guardrail 7: the ladder's player requests per lookup. */
        const val MAX_PLAYERS = 3

        /** Layout changes main's parser cannot read and L can (main fails with a recoverable reason). */
        val MOVED_SHAPE = listOf("L2", "L4", "L7")

        /** The classes whose visionOS answer is a version rejection (A may read a source). */
        val A_CLASSES = listOf("V1", "V9", "H1") + (1..6).map { "Y$it" }
    }
}
