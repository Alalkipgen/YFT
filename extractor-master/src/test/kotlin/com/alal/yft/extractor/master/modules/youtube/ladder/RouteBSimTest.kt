package com.alal.yft.extractor.master.modules.youtube.ladder

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
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.lookup.RouteBMediaStartReader
import com.alal.yft.extractor.master.lookup.RouteBPlayerScripts
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor as MainYouTubeExtractor
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 *
 * F5 (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8): with the record naming the lookup's watch page,
 * main's page GET with the tab's cookie answers Master's cookie-less GET of it once; the same sweep
 * holds (`build/routeb-sim/routeb-f5-lines.txt`), and after a bot-checked visionOS answer Master
 * completes from main's page with no new page, no new player request and nothing that carries a
 * credential or the account's identity.
 *
 * F4 (§8, a failure midway): the same lookup with Master's engine after main (its claim rule, R3):
 * after main's recoverable failure Master's rows come from main's replayed answers and the
 * engine's first line is route B's with the record's counts; when Master finds nothing its failure
 * names route B first; after main's final reason, or with the flag off, nothing of Master's goes
 * out; after a 429 midway nothing new does.
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
        /** F5: Master's requests that reached the world (address, headers, body). */
        val sent: List<Seen.Sent>,
    ) {
        val recoverable: Boolean
            get() = (mainResult as? SiteExtractionResult.Failure)?.reason in TerminalRules.ROUTE_B_RECOVERABLE

        /**
         * Whether Master answers this step in the app: after a recoverable failure (route B's gate,
         * R3) or main's found rows (the debug trial, R4, answers them as `RESPONSE_CHANGED`); after a
         * final reason (`LOGIN_REQUIRED`, `BOT_CHECK`...) route B never runs.
         */
        val answered: Boolean
            get() = recoverable || mainResult is SiteExtractionResult.Success
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
        assertSweep(runs)
    }

    /**
     * F5: the same sweep with the record naming the lookup's watch page — main's page answers
     * Master's identical cookie-less GET once; every rule above still holds, Master sends nothing
     * with a credential, and where route B answers in the app ([Step.answered]) it never ends worse
     * than route B before F5. After a final reason (route B never runs there; this sweep runs Master
     * after every main result) it may now read the page as the ladder alone does — L9's decoy
     * stops it as designed — but never ends worse than the ladder alone.
     */
    @Test
    fun `F5 - route B over every class with main's watch page kept for Master`() = runTest(timeout = 15.minutes) {
        val runs = SimCases.all().flatMap { case -> listOf(false, true).map { solver -> run(case, solver, page = true) } }
        File("build/routeb-sim").apply { mkdirs() }.resolve("routeb-f5-lines.txt")
            .writeText(runs.joinToString("\n", postfix = "\n", transform = ::line))
        assertSweep(runs)
        val before = SimCases.all().flatMap { case -> listOf(false, true).map { solver -> run(case, solver) } }
        fun worse(pick: (Run, Int) -> Boolean): List<String> = runs.zip(before).flatMap { (f5, old) ->
            f5.steps.indices
                .filter { rank(f5.steps[it].master.outcome) < rank(old.steps[it].master.outcome) && pick(f5, it) }
                .map { "${f5.case.id} ${column(f5.solver)} #${it + 1}" }
        }
        assertEquals("worse than route B before F5", emptyList<String>(), worse { run, index -> run.steps[index].answered })
        assertEquals(
            "worse than the ladder alone after a final reason",
            emptyList<String>(),
            worse { run, index -> !run.steps[index].answered && rank(run.steps[index].master.outcome) < rank(run.plain[index].outcome) },
        )
        assertEquals("credentials sent", emptyList<String>(), runs.flatMap { run -> run.steps.flatMap { it.sent } }.filter(Seen.Sent::credential).map { it.toString() })
    }

    private fun assertSweep(runs: List<Run>) {
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

    /**
     * F5 (D7 run 8: V2, V3): visionOS meets the bot check — only without visitor data (T1b) or
     * every time (a V2-like page) — so the module's next step is the watch page main read with the
     * tab's cookie. With the record naming that page Master reads main's answer (replayed, never
     * sent) and completes; before F5 the page was refused and Master had no complete rows. Signed
     * in (the page names the account, the cookie holds a session), Master's requests still carry
     * no cookie, no authorization and no account identity.
     */
    @Test
    fun `F5 - after a bot-checked visionOS answer Master completes from main's replayed watch page`() = runTest(timeout = 5.minutes) {
        val check = Fixtures.read("youtube/player_bot_check.json")
        val botChecked: SimWorld.() -> Unit = {
            override = { ask -> if (ask.client == SimClient.VISION_OS) SimReply.Json(check) else null }
        }
        val signedIn: SimWorld.() -> Unit = {
            botChecked()
            pageEdits += { it.replace("\"HL\":\"en\"", "\"HL\":\"en\",$SIGNED_IN_PAGE") }
        }
        for (base in Sim.BASES) {
            val cases = SimCases.forBase(base)
            val t1b = cases.first { it.klass == "T1b" }
            val b0 = cases.first { it.klass == "B0" }
            val worlds = listOf(
                F5World("T1b", t1b, SimMeasure.request()) {},
                F5World("V2-like", b0, SimMeasure.request(), botChecked),
                F5World("V2-like signed in", b0, signedInRequest(), signedIn),
            )
            for (world in worlds) {
                val f5 = run(world.case, solver = true, page = true, request = world.request, change = world.change)
                val before = run(world.case, solver = true, request = world.request, change = world.change)
                f5.steps.forEachIndexed { index, step ->
                    val name = "${world.name} ${world.case.id} #${index + 1}"
                    assertEquals(name, SimOutcome.COMPLETE, step.master.outcome)
                    assertTrue(name, step.master.wrong.isEmpty())
                    assertEquals(name, 0, step.master.pages)
                    assertEquals(name, 1, step.main.pages)
                    assertEquals(name, 0, step.master.players)
                    assertEquals(name, 0, step.master.credentials)
                    assertTrue(name, step.route.replayed >= 2)
                    assertEquals(name, step.route.replayed, step.record.replayedCount)
                    assertTrue(name, rank(step.master.outcome) >= rank(f5.plain[index].outcome))
                    for (sent in step.sent) {
                        assertFalse("$name: $sent", sent.credential)
                        assertFalse("$name: $sent", SECRETS.any { it in sent.url || it in sent.body.orEmpty() || sent.headers.values.any { value -> it in value } })
                    }
                    assertNotEquals("$name before F5", SimOutcome.COMPLETE, before.steps[index].master.outcome)
                }
                if (world.name.endsWith("signed in")) {
                    assertTrue(world.name, f5.steps.all { it.main.credentials > 0 }) // main asked with the session
                }
            }
        }
    }

    /**
     * F5 on a signed-in tab, over every class: the page main read names the account (its data-sync
     * ID) and the cookie holds a session main's page client asks with. Master reads that page from
     * the record and asks what its ladder asks next, never with the cookie, an authorization, the
     * session's headers or the account's identity (P22: at most the page's visitor data).
     */
    @Test
    fun `F5 - signed in, Master's requests over every class carry no credential and no account identity`() = runTest(timeout = 15.minutes) {
        val signedIn: SimWorld.() -> Unit = { pageEdits += { it.replace("\"HL\":\"en\"", "\"HL\":\"en\",$SIGNED_IN_PAGE") } }
        val runs = SimCases.all().flatMap { case ->
            listOf(false, true).map { solver -> run(case, solver, page = true, request = signedInRequest(), change = signedIn) }
        }
        val steps = runs.flatMap { run -> run.steps.mapIndexed { index, step -> "${run.case.id} ${column(run.solver)} #${index + 1}" to step } }
        fun names(pick: (Step) -> Boolean): List<String> = steps.filter { (_, step) -> pick(step) }.map { it.first }

        assertEquals("wrong rows", emptyList<String>(), names { it.master.wrong.isNotEmpty() })
        assertEquals("watch pages", emptyList<String>(), names { it.main.pages + it.master.pages > 1 })
        assertEquals("credentials", emptyList<String>(), names { step -> step.master.credentials > 0 || step.sent.any(Seen.Sent::credential) })
        assertEquals(
            "account identity",
            emptyList<String>(),
            names { step ->
                step.sent.any { sent -> SECRETS.any { it in sent.url || it in sent.body.orEmpty() || sent.headers.values.any { value -> it in value } } }
            },
        )
        // The world is signed in (main's page client asked with the session), and Master went on
        // past main's replayed page with requests of its own.
        assertTrue(steps.any { (_, step) -> step.main.credentials > 0 })
        assertTrue(steps.any { (_, step) -> step.route.replayed >= 2 && step.sent.isNotEmpty() })
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

    /**
     * F4 (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8 (1), a failure midway): main asks and then fails
     * with a recoverable reason, and Master's engine answers in the same lookup, whose record names
     * the page as the app's route-B build does. A moved shape main's parser cannot read, in every
     * identity's answer (L2, L4, L7): Master's L reads main's replayed visionOS answer. After a
     * bot-checked visionOS answer (F5): every identity asked without the tab's cookie meets the bot
     * check, main reads the page with the cookie and its own player-script step fails there (its
     * script fetch: HTTP_STATUS — streams that existed but became no download, a verdict main ranks
     * above the bot check); Master's own solver reads main's replayed page. Master's rows are
     * complete, none wrong; the engine's first line names route B with its counts (`master: route B
     * after <reason>; replayed N; new M`, the browser's notice); no new player request, no new page,
     * nothing with a credential.
     */
    @Test
    fun `F4 - main fails midway and Master's engine answers from main's replayed answers`() = runTest(timeout = 10.minutes) {
        val moved = Sim.BASES.flatMap(SimCases::forBase).filter { it.klass in MOVED_SHAPE && it.vMode == VMode.PASS }
        assertTrue(moved.size >= Sim.BASES.size * MOVED_SHAPE.size)
        for (case in moved) for (solver in listOf(false, true)) {
            val name = "${case.id} ${column(solver)}"
            val step = viaEngine(case, solver)
            assertAnswered(name, step)
            assertTrue(name, step.record.replayedCount >= 1)
        }
        val check = Fixtures.read("youtube/player_bot_check.json")
        for (base in Sim.BASES) {
            val case = SimCases.forBase(base).first { it.klass == "B0" }
            val name = "${case.id} bot-checked without the cookie"
            val step = viaEngine(case, solver = true, mainScripts = { FailedScripts(SiteExtractionFailure.HTTP_STATUS) }) {
                override = { ask -> if (ask.client != SimClient.PAGE) SimReply.Json(check) else null }
            }
            assertEquals(name, SiteExtractionFailure.HTTP_STATUS, (step.mainResult as? SiteExtractionResult.Failure)?.reason)
            assertAnswered(name, step)
            assertEquals(name, 1, step.main.pages) // main read the page with the tab's cookie
            assertTrue(name, step.record.replayedCount >= 2) // visionOS and the page
        }
    }

    /**
     * F4 (2): when Master's engine finds nothing after main's recoverable failure (F3: H's playlist
     * fails its checks; L10 and L13 when V fails or cannot answer), its failure names route B first
     * with its counts and Master's own reason last — the details the browser adds after main's while
     * main's failure stays (`AndroidBrowserMasterFallback`); no wrong row, no second page, nothing
     * with a credential.
     */
    @Test
    fun `F4 - when Master's engine finds nothing its failure names route B first`() = runTest(timeout = 5.minutes) {
        val cases = Sim.BASES.flatMap(SimCases::forBase)
            .filter { it.klass == "F3" || (it.klass in listOf("L10", "L13") && it.vMode != VMode.PASS) }
        assertTrue(cases.size >= Sim.BASES.size * 4)
        for (case in cases) for (solver in listOf(false, true)) {
            val name = "${case.id} ${column(solver)}"
            val step = viaEngine(case, solver)
            val reason = (step.mainResult as? SiteExtractionResult.Failure)?.reason
            assertTrue("$name: main failed ($reason)", reason in TerminalRules.ROUTE_B_RECOVERABLE)
            val failure = step.result as? MasterResult.Failure
            assertTrue("$name: ${step.result}", failure != null)
            assertTrue(name, step.master.wrong.isEmpty())
            assertTrue(name, step.main.pages + step.master.pages <= 1)
            assertEquals(name, 0, step.master.credentials)
            assertTrue(name, step.sent.none(Seen.Sent::credential))
            assertEquals(name, step.logged - step.sourceReads, step.record.masterAsks)
            assertLine(name, reason!!, step, failure!!.details.first())
            assertEquals(name, "master: youtube module: ${failure.reason.name}", failure.details.last())
        }
    }

    /**
     * F4 (3): after main's final reason Master's engine skips the page and nothing of Master's goes
     * out — L11 (a sign-in answer in every identity's answer and the page's), and a moved shape
     * after a bot-checked visionOS answer: main reads the page with the tab's cookie, cannot read
     * its moved answer, and its failure is the bot check (main ranks a bot check as the page's own
     * verdict, above a changed or empty answer), so route B never runs there (§2). With the
     * route-B flag off a page main failed recoverably is skipped as today (P12).
     */
    @Test
    fun `F4 - a final reason or the flag off sends nothing of Master's`() = runTest(timeout = 5.minutes) {
        val check = Fixtures.read("youtube/player_bot_check.json")
        val botChecked: SimWorld.() -> Unit = {
            override = { ask -> if (ask.client == SimClient.VISION_OS) SimReply.Json(check) else null }
        }
        val moved = Sim.BASES.flatMap(SimCases::forBase).filter { it.klass in MOVED_SHAPE && it.vMode == VMode.PASS }
        val final = Sim.BASES.flatMap(SimCases::forBase).filter { it.klass == "L11" && it.vMode == VMode.PASS }
        assertTrue(final.isNotEmpty())
        val steps = final.map { it.id to viaEngine(it, solver = true) } +
            moved.map { "${it.id} bot-checked visionOS" to viaEngine(it, solver = true, change = botChecked) }
        for ((name, step) in steps) {
            val reason = (step.mainResult as? SiteExtractionResult.Failure)?.reason
            assertTrue("$name: main failed ($reason)", reason != null && reason !in TerminalRules.ROUTE_B_RECOVERABLE)
            if ("bot-checked" in name) {
                assertEquals(name, SiteExtractionFailure.BOT_CHECK, reason)
                assertEquals(name, 1, step.main.pages) // main read the page after the bot check
            }
            assertEquals(name, MasterResult.Skipped(reason!!), step.result)
            assertEquals(name, 0, step.logged)
            assertEquals(name, 0, step.record.masterAsks)
            assertEquals(name, 0, step.record.replayedCount)
        }
        for (case in moved) {
            val step = viaEngine(case, solver = true, routeB = false)
            val reason = (step.mainResult as? SiteExtractionResult.Failure)?.reason
            assertTrue("${case.id}: main failed ($reason)", reason in TerminalRules.ROUTE_B_RECOVERABLE)
            assertEquals(case.id, MasterResult.Skipped(reason!!), step.result)
            assertEquals(case.id, 0, step.logged)
            assertEquals(case.id, 0, step.record.masterAsks)
            assertEquals(case.id, 0, step.record.replayedCount)
        }
    }

    /**
     * F4 (3): a 429 midway — Android's answer in main's lookup — while main's own failure is
     * recoverable (L2: main's verdict is the page's moved answer, which ranks above a fallback's
     * 429): Master's engine runs route B and sends nothing new; its line names the refusals.
     */
    @Test
    fun `F4 - after a 429 midway in main's lookup Master's engine sends nothing new`() = runTest(timeout = 5.minutes) {
        for (base in Sim.BASES) for (solver in listOf(false, true)) {
            val case = SimCases.forBase(base).first { it.klass == "L2" && it.vMode == VMode.PASS }
            val name = "${case.id} ${column(solver)}"
            val step = viaEngine(case, solver) {
                override = { ask -> if (ask.client == SimClient.ANDROID) SimReply.Status(SimWorld.TOO_MANY) else null }
            }
            val reason = (step.mainResult as? SiteExtractionResult.Failure)?.reason
            assertTrue("$name: main failed ($reason)", reason in TerminalRules.ROUTE_B_RECOVERABLE)
            assertTrue(name, step.record.rateLimited)
            assertEquals(name, 0, step.logged)
            assertEquals(name, 0, step.record.masterAsks)
            assertTrue(name, step.master.wrong.isEmpty())
            val details = when (val result = step.result) {
                is MasterResult.Success -> result.result.details
                is MasterResult.Failure -> result.details
                else -> emptyList()
            }
            val first = details.firstOrNull().orEmpty()
            assertTrue("$name: $first", Regex("^master: route B after ${reason!!.name}; replayed \\d+; new 0; refused [1-9]\\d*$").matches(first))
        }
    }

    /** F4: Master's engine answered after main's recoverable failure, from main's replayed answers. */
    private fun assertAnswered(name: String, step: EngineStep) {
        val reason = (step.mainResult as? SiteExtractionResult.Failure)?.reason
        assertTrue("$name: main failed ($reason)", reason in TerminalRules.ROUTE_B_RECOVERABLE)
        val found = step.result as? MasterResult.Success
        assertTrue("$name: ${step.result}", found != null)
        assertEquals(name, SimOutcome.COMPLETE, step.master.outcome)
        assertTrue(name, step.master.wrong.isEmpty())
        assertEquals(name, 0, step.master.players)
        assertEquals(name, 0, step.master.pages)
        assertEquals(name, 0, step.master.credentials)
        assertTrue(name, step.sent.none(Seen.Sent::credential))
        assertTrue(name, step.main.pages <= 1)
        assertEquals(name, step.logged - step.sourceReads, step.record.masterAsks)
        assertLine(name, reason!!, step, found!!.result.details.first())
    }

    /** F4: route B's line (`master: route B after <reason>; replayed N; new M[; refused K]`) with the record's counts. */
    private fun assertLine(name: String, reason: SiteExtractionFailure, step: EngineStep, line: String) {
        val counts = "master: route B after ${reason.name}; replayed ${step.record.replayedCount}; new ${step.record.masterAsks}"
        assertTrue("$name: $line", Regex("^${Regex.escape(counts)}(; refused \\d+)?$").matches(line))
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

    /**
     * One case: main, then Master through route B, per lookup. [page] (F5): the record names the
     * lookup's watch page, as the app's route-B build does.
     */
    private suspend fun run(
        case: SimCase,
        solver: Boolean,
        page: Boolean = false,
        request: SiteExtractionRequest = SimMeasure.request(),
        change: SimWorld.() -> Unit = {},
    ): Run {
        val plain = plain(case, solver, request, change)
        val world = case.world().apply(change)
        val main = MainYouTubeExtractor(LookupRecorder(world.http), SimSolver(world))
        val reader = ClientLadderSimTest.SimStartReader(world)
        val sources = ClientLadderSimTest.SimSourceReader(world)
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, RouteBMediaStartReader(reader), sources = sources)
        val scripts = RouteBPlayerScripts.wrap(if (solver) SimSolver(world) else NoPlayerScriptRunner)
        val seen = Seen(world.http)
        val master = MasterYouTubeModule(RouteBHttpClient(seen), scripts, ladder)
        val steps = (0 until case.lookups).map {
            val record = LookupRecord(clock = { Sim.NOW }, watchPageVideoId = Sim.VIDEO_ID.takeIf { page })
            val mainFrom = world.http.log.size
            val mainResult = withContext(record) { main.extract(request) }
            val mainJudged = SimMeasure.judge(world, case, mainResult, mainFrom)
            val from = world.http.log.size
            val sourcesFrom = sources.reads.size
            val sentFrom = seen.sent.size
            val route = RouteBLookup(record)
            val result = withContext(record + route) { master.extract(request) }
            val judged = SimMeasure.judge(world, case, result, from)
            Step(
                mainJudged, mainResult, judged, record, route, world.http.log.size - from,
                sources.reads.size - sourcesFrom, seen.sent.drop(sentFrom),
            )
        }
        return Run(case, solver, steps, plain, reader.reads, reader.segmentReads, sources.reads.size)
    }

    /** F4: main's player-script runner whose script fetch failed with [reason] (Master's own solver is another runner). */
    private class FailedScripts(private val reason: SiteExtractionFailure) : PlayerScriptRunner {
        override val isAvailable: Boolean = true
        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult = PlayerScriptResult.Failed(reason)
    }

    /** F4: one lookup through Master's engine ([viaEngine]). */
    private class EngineStep(
        val mainResult: SiteExtractionResult,
        val main: SimLookup,
        val result: MasterResult,
        val master: SimLookup,
        val record: LookupRecord,
        /** Requests in the world's log during the engine's run. */
        val logged: Int,
        val sourceReads: Int,
        val sent: List<Seen.Sent>,
    )

    /**
     * F4: one lookup as the app's route-B build runs it — main (its record names the lookup's watch
     * page), then Master's engine with the YouTube module (the ladder through route B's wrappers)
     * after main's failure; [routeB] is the engine's flag, [mainScripts] main's player-script runner
     * (Master's own solver is the world's when [solver]).
     */
    private suspend fun viaEngine(
        case: SimCase,
        solver: Boolean,
        routeB: Boolean = true,
        mainScripts: (SimWorld) -> PlayerScriptRunner = ::SimSolver,
        change: SimWorld.() -> Unit = {},
    ): EngineStep {
        val world = case.world().apply(change)
        val request = SimMeasure.request()
        val main = MainYouTubeExtractor(LookupRecorder(world.http), mainScripts(world))
        val sources = ClientLadderSimTest.SimSourceReader(world)
        val ladder = MasterClientLadder(
            ClientHealth(null) { Sim.NOW },
            RouteBMediaStartReader(ClientLadderSimTest.SimStartReader(world)),
            sources = sources,
        )
        val scripts = RouteBPlayerScripts.wrap(if (solver) SimSolver(world) else NoPlayerScriptRunner)
        val seen = Seen(world.http)
        val engine = MasterFallbackEngine(
            MasterMediaValidator { found, _ -> ValidationResult.Valid(found) },
            policy = MasterPolicy(enabled = true, youtubeRouteB = routeB),
            modules = listOf(MasterYouTubeModule(RouteBHttpClient(seen), scripts, ladder)),
        )
        val record = LookupRecord(clock = { Sim.NOW }, watchPageVideoId = Sim.VIDEO_ID)
        val mainFrom = world.http.log.size
        val mainResult = withContext(record) { main.extract(request) }
        val mainJudged = SimMeasure.judge(world, case, mainResult, mainFrom)
        val reason = (mainResult as? SiteExtractionResult.Failure)?.reason ?: SiteExtractionFailure.UNSUPPORTED_URL
        val from = world.http.log.size
        val sourcesFrom = sources.reads.size
        val result = withContext(record) {
            engine.extract(MasterRequest(Sim.MOBILE_PAGE, 1, Sim.NOW, reason, Sim.VIDEO_ID, request.requestContext))
        }
        val asSite = when (result) {
            is MasterResult.Success -> result.result
            is MasterResult.Failure -> SiteExtractionResult.Failure(result.reason, details = result.details)
            else -> SiteExtractionResult.Failure(reason)
        }
        return EngineStep(
            mainResult, mainJudged, result, SimMeasure.judge(world, case, asSite, from), record,
            world.http.log.size - from, sources.reads.size - sourcesFrom, seen.sent.toList(),
        )
    }

    /** The ladder alone in its own world ([ClientLadderSimTest]'s column): what Master would do without main. */
    private suspend fun plain(
        case: SimCase,
        solver: Boolean,
        request: SiteExtractionRequest,
        change: SimWorld.() -> Unit,
    ): List<SimLookup> {
        val world = case.world().apply(change)
        val ladder = MasterClientLadder(
            ClientHealth(null) { Sim.NOW },
            ClientLadderSimTest.SimStartReader(world),
            sources = ClientLadderSimTest.SimSourceReader(world),
        )
        val module = MasterYouTubeModule(world.http, if (solver) SimSolver(world) else NoPlayerScriptRunner, ladder)
        return (0 until case.lookups).map {
            val from = world.http.log.size
            SimMeasure.judge(world, case, module.extract(request), from)
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

    /** F5: one bot-checked world for the page check. */
    private class F5World(val name: String, val case: SimCase, val request: SiteExtractionRequest, val change: SimWorld.() -> Unit)

    /** F5: the lookup of a signed-in tab (the cookie holds a session main's page client uses). */
    private fun signedInRequest(): SiteExtractionRequest = SimMeasure.request().let { request ->
        request.copy(requestContext = request.requestContext.copy(cookie = SIGNED_IN_COOKIE))
    }

    /** F5: what Master's requests carried when they reached the world (never written out). */
    class Seen(private val http: ExtractorHttpClient) : ExtractorHttpClient {
        data class Sent(val url: String, val headers: Map<String, String>, val body: String?) {
            /** A header that names the user's session or account. */
            val credential: Boolean get() = headers.keys.any { it.trim().lowercase() in ACCOUNT_HEADERS }

            override fun toString(): String = "Sent(${headers.keys})"
        }

        val sent = mutableListOf<Sent>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            sent += Sent(url, headers, null)
            return http.get(url, headers, maxBodyBytes)
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            sent += Sent(url, headers, body)
            return http.postJson(url, body, headers, maxBodyBytes)
        }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            sent += Sent(url, headers, null)
            return http.probe(url, headers, timeoutMillis)
        }
    }

    private companion object {
        /** F5: a signed-in page's account fields, and a cookie with a session main's page client uses. */
        const val SIGNED_IN_PAGE = "\"LOGGED_IN\":true,\"DATASYNC_ID\":\"simaccount||\",\"SESSION_INDEX\":\"0\""
        const val SIGNED_IN_COOKIE = "PREF=sim; SID=sim-session; SAPISID=sim-sapisid; __Secure-3PAPISID=sim-sapisid"

        /** F5: what never leaves in Master's requests — the account's identity and the cookie's values. */
        val SECRETS = listOf("simaccount", "sim-session", "sim-sapisid")
        val ACCOUNT_HEADERS = setOf(
            "cookie", "authorization", "proxy-authorization", "x-goog-pageid", "x-goog-authuser",
            "x-youtube-bootstrap-logged-in", "x-origin",
        )

        /** Guardrail 7: the ladder's player requests per lookup. */
        const val MAX_PLAYERS = 3

        /** Layout changes main's parser cannot read and L can (main fails with a recoverable reason). */
        val MOVED_SHAPE = listOf("L2", "L4", "L7")

        /** The classes whose visionOS answer is a version rejection (A may read a source). */
        val A_CLASSES = listOf("V1", "V9", "H1") + (1..6).map { "Y$it" }
    }
}
