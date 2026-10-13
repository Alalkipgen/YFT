package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 N7: [OwnSolverProbe], the phone canary's solver step, on fake engines. Its jobs are the
 * runner's, its timeouts and breaker are the runner's, its time runs from sending the job to its
 * reply, and it keeps locator parts only for kinds found right, per build and worker step.
 */
class OwnSolverProbeTest {
    @Test
    fun `worker P from the text sends the runner's first job`() = runTest {
        val runnerJobs = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        runner(runnerJobs).resolve(request())
        val probeJobs = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        val run = OwnSolverProbe(OwnSolverBreaker()).run(probeJobs, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(runnerJobs.inputs.single(), probeJobs.inputs.single())
        assertEquals(OwnSolverProbe.REPLY, run.summary.outcome)
        assertFalse(run.stopped)
        assertEquals(BOTH, run.summary.answered)
        assertEquals(RATES.size, run.rate.size)
        assertEquals(SIGNATURES.size, run.signature.size)
    }

    @Test
    fun `worker P from the kept program with the learned parts sends the runner's second job`() = runTest {
        val runnerJobs = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        val runner = runner(runnerJobs)
        val first = request()
        runner.resolve(first)
        runner.record(requireNotNull(runner.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)
        runner.resolve(request())

        val probeJobs = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        val probe = OwnSolverProbe(OwnSolverBreaker())
        val raw = probe.run(probeJobs, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        probe.learn(raw, BOTH)
        val kept = probe.run(probeJobs, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES, fromPrepared = true)

        assertEquals(2, runnerJobs.inputs.size)
        assertEquals(runnerJobs.inputs, probeJobs.inputs)
        assertTrue(kept.summary.fromPrepared)
        assertEquals(BOTH, kept.summary.partsSent)
        assertEquals(BOTH, kept.summary.locatorHits)
    }

    @Test
    fun `worker B after a P that gave nothing sends the runner's B job`() = runTest {
        val refusedByP: suspend (ScriptedOwnEngine.Job, OwnSolverEngine.Events) -> String? = { job, events ->
            if (job.isB) {
                answer(job, events)
            } else {
                events.started()
                ScriptedOwnEngine.result(failed = "n:refused,sig:refused", initErrors = 0)
            }
        }
        val runnerJobs = RecordingEngine(reply = refusedByP)
        runner(runnerJobs).resolve(request())
        val probeJobs = RecordingEngine(reply = refusedByP)
        val run = OwnSolverProbe(OwnSolverBreaker()).run(probeJobs, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(2, runnerJobs.inputs.size)
        assertEquals(runnerJobs.inputs[1], probeJobs.inputs.single())
        assertTrue(run.summary.strategyB)
        assertTrue(run.summary.located)
    }

    @Test
    fun `page P and page B after workers with init errors send the runner's page jobs`() = runTest {
        val workers = RecordingEngine { job, events -> answer(job, events, initErrors = 1) }
        val pages = RecordingEngine { job, events -> answer(job, events, initErrors = 1) }
        runner(workers, pages).resolve(request())
        val probePages = RecordingEngine { job, events -> answer(job, events, initErrors = 1) }
        val probe = OwnSolverProbe(OwnSolverBreaker())
        val pageP = probe.run(probePages, Step.PAGE_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        val pageB = probe.run(probePages, Step.PAGE_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(2, pages.inputs.size)
        assertEquals(pages.inputs, probePages.inputs)
        assertFalse(pageP.summary.located)
        assertTrue(pageB.summary.strategyB)
        assertEquals(1, pageB.summary.initErrors)
    }

    @Test
    fun `the time runs from sending the job to its reply - ready apart, the marker writes outside it`() = runTest {
        val clock = FakeClock(1_000)
        val markers = SlowMarkers(clock)
        val probe = OwnSolverProbe(OwnSolverBreaker(markers), clock = { clock.now })
        val engine = RecordingEngine { job, events ->
            clock.now += 300
            events.started()
            clock.now += 1_200
            answer(job, NO_EVENTS)
        }
        val run = probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(OwnSolverProbe.REPLY, run.summary.outcome)
        assertEquals(300L, run.summary.readyMillis)
        assertEquals(1_500L, run.summary.totalMillis)
        assertEquals(listOf(1, 0), markers.writes)

        val plain = RecordingEngine(reportsReady = false) { job, _ ->
            clock.now += 700
            answer(job, NO_EVENTS)
        }
        val unready = probe.run(plain, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertNull(unready.summary.readyMillis)
        assertEquals(700L, unready.summary.totalMillis)
    }

    @Test
    fun `a page that never takes the job is a ready timeout - no strike, the canary stops`() = runTest {
        val breaker = OwnSolverBreaker()
        val engine = RecordingEngine { _, _ -> awaitCancellation() }
        val run = OwnSolverProbe(breaker).run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(OwnSolverProbe.READY_TIMEOUT, run.summary.outcome)
        assertTrue(run.stopped)
        assertNull(run.summary.totalMillis)
        assertTrue(breaker.allows(PLAYER, Step.WORKER_P))
    }

    @Test
    fun `a job timeout turns the step off in the app's breaker - the next run is breaker off and sends nothing`() =
        runTest {
            val breaker = OwnSolverBreaker()
            val engine = RecordingEngine { _, events ->
                events.started()
                awaitCancellation()
            }
            val probe = OwnSolverProbe(breaker)
            val run = probe.run(engine, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

            assertEquals(OwnSolverProbe.JOB_TIMEOUT, run.summary.outcome)
            assertTrue(run.stopped)
            assertFalse(breaker.allows(PLAYER, Step.WORKER_B))
            assertTrue(breaker.allows(PLAYER, Step.WORKER_P))
            val next = probe.run(engine, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
            assertEquals(OwnSolverProbe.BREAKER_OFF, next.summary.outcome)
            assertTrue(next.stopped)
            assertEquals(1, engine.inputs.size)
        }

    @Test
    fun `a gone renderer on the page turns both page steps off`() = runTest {
        val breaker = OwnSolverBreaker()
        val engine = RecordingEngine { _, events ->
            events.started()
            events.gone()
            null
        }
        val run = OwnSolverProbe(breaker).run(engine, Step.PAGE_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(OwnSolverProbe.GONE, run.summary.outcome)
        assertFalse(breaker.allows(PLAYER, Step.PAGE_P))
        assertFalse(breaker.allows(PLAYER, Step.PAGE_B))
        assertTrue(breaker.allows(PLAYER, Step.WORKER_P))
    }

    @Test
    fun `a step the app's breaker has off does not run`() = runTest {
        val markers = OwnSolverBreaker.MemoryMarkers()
        markers.set(OwnSolverBreaker.key(PLAYER, Step.WORKER_P), OwnSolverBreaker.STRIKES)
        val engine = RecordingEngine { job, events -> answer(job, events) }
        val run = OwnSolverProbe(OwnSolverBreaker(markers)).run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(OwnSolverProbe.BREAKER_OFF, run.summary.outcome)
        assertTrue(run.stopped)
        assertTrue(engine.inputs.isEmpty())
    }

    @Test
    fun `the marker is kept during the job and cleared after it`() = runTest {
        val markers = OwnSolverBreaker.MemoryMarkers()
        val key = OwnSolverBreaker.key(PLAYER, Step.PAGE_P)
        var during = -1
        val engine = RecordingEngine { job, events ->
            during = markers.count(key)
            answer(job, events)
        }
        OwnSolverProbe(OwnSolverBreaker(markers)).run(engine, Step.PAGE_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(1, during)
        assertEquals(0, markers.count(key))
    }

    @Test
    fun `a step whose signatures V failed gives no signature`() = runTest {
        val breaker = OwnSolverBreaker()
        breaker.signatureFailed(PLAYER, Step.WORKER_P)
        val engine = RecordingEngine { job, events -> answer(job, events) }
        val run = OwnSolverProbe(breaker).run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertTrue(run.summary.signaturesOff)
        assertTrue(run.signature.isEmpty())
        assertEquals(setOf(PlayerScriptChallengeKind.RATE_PARAM), run.summary.answered)
        assertEquals(RATES.size, run.rate.size)
    }

    @Test
    fun `a run that ran out of time or broke stops the canary, a refused kind does not`() = runTest {
        val probe = OwnSolverProbe(OwnSolverBreaker())
        suspend fun outcome(reply: String): OwnSolverProbe.Run {
            val engine = RecordingEngine { _, events ->
                events.started()
                reply
            }
            return probe.run(engine, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        }

        val budget = outcome(ScriptedOwnEngine.result(failed = "sig:budget", initErrors = 0))
        assertTrue(budget.summary.budget)
        assertTrue(budget.stopped)
        assertEquals("error TypeError", outcome(ScriptedOwnEngine.error("TypeError")).summary.outcome)
        assertTrue(outcome(ScriptedOwnEngine.error("TypeError")).stopped)
        assertEquals(OwnSolverProbe.REPLY_UNREADABLE, outcome("not a reply").summary.outcome)
        val rateOnly = ScriptedOwnEngine.result(
            n = RATES.associateWith(ScriptedOwnEngine::rate),
            failed = "sig:refused",
            initErrors = 0,
        )
        val partly = outcome(rateOnly)
        assertFalse(partly.stopped)
        assertEquals(setOf(PlayerScriptChallengeKind.RATE_PARAM), partly.summary.answered)
        assertEquals(listOf("sig:refused"), partly.summary.failures)
    }

    @Test
    fun `parts are kept only for kinds found right - the kept program's job carries them and hits`() = runTest {
        val probe = OwnSolverProbe(OwnSolverBreaker())
        val engine = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        val raw = probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertTrue(raw.summary.located)
        assertTrue(raw.summary.partsSent.isEmpty())
        assertEquals(BOTH, raw.summary.partsGiven)
        assertTrue(probe.hasPrepared(PLAYER, BUILD))

        probe.learn(raw, setOf(PlayerScriptChallengeKind.RATE_PARAM))
        val kept = probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES, fromPrepared = true)
        assertEquals(OwnSolverLocator.Parts(rate = partOf(P_PART_N)), engine.jobs.last().parts)
        assertTrue(kept.summary.fromPrepared)
        assertEquals(setOf(PlayerScriptChallengeKind.RATE_PARAM), kept.summary.partsSent)
        assertEquals(setOf(PlayerScriptChallengeKind.RATE_PARAM), kept.summary.locatorHits)

        // A kind found wrong loses its part.
        probe.learn(kept, emptySet())
        probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(), engine.jobs.last().parts)
    }

    @Test
    fun `a run with init errors keeps no part, and a run that never went out changes nothing`() = runTest {
        val breaker = OwnSolverBreaker()
        val store = OwnSolverLocators()
        val probe = OwnSolverProbe(breaker, workerP = store)
        val clean = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        probe.learn(probe.run(clean, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES), BOTH)
        val withErrors = RecordingEngine { job, events -> answer(job, events, initErrors = 1, locator = P_LOCATOR) }
        val failed = probe.run(withErrors, Step.WORKER_P, PLAYER, "main", SOURCE, RATES, SIGNATURES)
        probe.learn(failed, BOTH)
        probe.run(clean, Step.WORKER_P, PLAYER, "main", SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(), clean.jobs.last().parts)

        breaker.end(PLAYER, Step.WORKER_P, OwnSolverBreaker.End.TIMEOUT)
        val off = probe.run(clean, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverProbe.BREAKER_OFF, off.summary.outcome)
        probe.learn(off, emptySet())
        val sameStore = OwnSolverProbe(OwnSolverBreaker(), workerP = store)
        sameStore.run(clean, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(partOf(P_PART_N), partOf(P_PART_SIG)), clean.jobs.last().parts)
    }

    @Test
    fun `P's and B's parts and the builds of one player are kept apart`() = runTest {
        val engine = RecordingEngine { job, events ->
            val located = job.locator != null
            answer(job, events, locator = if (!located) null else if (job.isB) B_LOCATOR else P_LOCATOR)
        }
        val probe = OwnSolverProbe(OwnSolverBreaker())
        probe.learn(probe.run(engine, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES), BOTH)

        probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(), engine.jobs.last().parts)
        probe.run(engine, Step.WORKER_B, PLAYER, "main", SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(), engine.jobs.last().parts)
        val hit = probe.run(engine, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(partOf(B_PART_N), partOf(B_PART_SIG)), engine.jobs.last().parts)
        assertEquals(BOTH, hit.summary.locatorHits)
    }

    @Test
    fun `parts outlive the probe in their files, prepared programs do not`() = runTest {
        val workerP = MemoryFile()
        val workerB = MemoryFile()
        val breaker = OwnSolverBreaker()
        val engine = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        val first = OwnSolverProbe(breaker, OwnSolverLocators(workerP), OwnSolverLocators(workerB))
        first.learn(first.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES), BOTH)
        assertTrue(first.hasPrepared(PLAYER, BUILD))

        val second = OwnSolverProbe(breaker, OwnSolverLocators(workerP), OwnSolverLocators(workerB))
        assertFalse(second.hasPrepared(PLAYER, BUILD))
        val none = second.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES, fromPrepared = true)
        assertEquals(OwnSolverProbe.NO_PREPARED, none.summary.outcome)
        assertEquals(1, engine.inputs.size)
        second.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        assertEquals(OwnSolverLocator.Parts(partOf(P_PART_N), partOf(P_PART_SIG)), engine.jobs.last().parts)
        assertNull(workerB.text)
    }

    @Test
    fun `describe reads each job and its reply as the probe's summary, without times`() = runTest {
        val replies = mutableListOf<String>()
        val engine = RecordingEngine { job, events ->
            val located = job.locator != null
            answer(job, events, locator = if (!located) null else if (job.isB) B_LOCATOR else P_LOCATOR)
                .also { replies += it }
        }
        val probe = OwnSolverProbe(OwnSolverBreaker())
        val runs = mutableListOf<OwnSolverProbe.Run>()
        val raw = probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        probe.learn(raw, BOTH)
        runs += raw
        runs += probe.run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES, fromPrepared = true)
        runs += probe.run(engine, Step.WORKER_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        runs += probe.run(engine, Step.PAGE_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        runs += probe.run(engine, Step.PAGE_B, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)

        assertEquals(runs.size, engine.inputs.size)
        runs.forEachIndexed { index, run ->
            val described = OwnSolverProbe.describe(engine.inputs[index], replies[index])
            assertEquals(run.summary.copy(readyMillis = null, totalMillis = null), described)
        }
    }

    @Test
    fun `describe - no reply, an unreadable reply, a worker error, not a job`() {
        val input = OwnSolverProtocol.encode(
            OwnSolverProtocol.Player.Raw(SOURCE),
            OwnSolverProtocol.Batch(RATES, SIGNATURES),
            true,
            OwnSolverProtocol.Options(tripwire = true, deadlineMillis = 40_000, locator = OwnSolverLocator.Parts()),
        )

        assertEquals(OwnSolverProbe.ENGINE, OwnSolverProbe.describe(input, null)?.outcome)
        assertEquals(OwnSolverProbe.REPLY_UNREADABLE, OwnSolverProbe.describe(input, "not a reply")?.outcome)
        assertEquals("error TypeError", OwnSolverProbe.describe(input, ScriptedOwnEngine.error("TypeError"))?.outcome)
        assertEquals(Step.WORKER_P, OwnSolverProbe.describe(input, null)?.step)
        assertNull(OwnSolverProbe.describe("not a job", null))
    }

    @Test
    fun `a run and a summary print kinds, counts, classes and times only`() = runTest {
        val engine = RecordingEngine { job, events -> answer(job, events, locator = P_LOCATOR) }
        val run = OwnSolverProbe(OwnSolverBreaker()).run(engine, Step.WORKER_P, PLAYER, BUILD, SOURCE, RATES, SIGNATURES)
        val printed = run.toString() + OwnSolverProbe.describe(engine.inputs.single(), null)
        val hidden = RATES + SIGNATURES + RATES.map(ScriptedOwnEngine::rate) +
            SIGNATURES.map(ScriptedOwnEngine::signature) + listOf(SOURCE, ScriptedOwnEngine.programOf(SOURCE)) +
            HASHES
        assertTrue("a value, input, text or hash is printed", hidden.none { printed.contains(it) })
    }

    @Test
    fun `a store key stays within the locator file's player key form`() {
        val long = "a".repeat(32)
        assertEquals(32, OwnSolverProbe.keyOf(long, "phone").length)
        assertTrue(OwnSolverProbe.keyOf(long, "phone").endsWith("-phone"))
        assertEquals("$PLAYER-main", OwnSolverProbe.keyOf(PLAYER, "main"))
    }

    private class RecordingEngine(
        override val reportsReady: Boolean = true,
        private val reply: suspend (ScriptedOwnEngine.Job, OwnSolverEngine.Events) -> String?,
    ) : OwnSolverEngine {
        override val isAvailable: Boolean = true
        val inputs = mutableListOf<String>()
        val jobs = mutableListOf<ScriptedOwnEngine.Job>()

        override suspend fun run(input: String): String? = run(input, NO_EVENTS)

        override suspend fun run(input: String, events: OwnSolverEngine.Events): String? {
            inputs += input
            val job = ScriptedOwnEngine.Job.parse(input)
            jobs += job
            return reply(job, events)
        }
    }

    private class FakeClock(var now: Long)

    /** Markers whose every write takes five seconds on [clock]. */
    private class SlowMarkers(private val clock: FakeClock) : OwnSolverBreaker.Markers {
        private val counts = HashMap<String, Int>()
        val writes = mutableListOf<Int>()

        override fun count(key: String): Int = counts[key] ?: 0

        override fun set(key: String, count: Int) {
            clock.now += 5_000
            writes += count
            if (count <= 0) counts.remove(key) else counts[key] = count
        }
    }

    private class MemoryFile : OwnSolverTextFile {
        var text: String? = null

        override fun read(): String? = text

        override fun write(text: String): Boolean {
            this.text = text
            return true
        }
    }

    /** Every input answered, the hits for the parts the job carried; [locator] as the core writes it. */
    private fun answer(
        job: ScriptedOwnEngine.Job,
        events: OwnSolverEngine.Events,
        initErrors: Int = 0,
        locator: String? = null,
    ): String {
        events.started()
        val parts = job.parts
        return ScriptedOwnEngine.result(
            n = job.n.associateWith(ScriptedOwnEngine::rate),
            sig = job.sig.associateWith(ScriptedOwnEngine::signature),
            prepared = ScriptedOwnEngine.preparedFor(job),
            initErrors = initErrors,
            locator = locator,
            locatorHits = listOfNotNull(
                "n".takeIf { parts?.rate != null },
                "sig".takeIf { parts?.signature != null },
            ),
        )
    }

    private fun runner(workers: OwnSolverEngine, pages: OwnSolverEngine? = null) = OwnPlayerScriptRunner(
        http = FakeExtractorHttpClient(
            responses = mapOf(PHONE_PLAYER to FakeExtractorHttpClient.json(SOURCE, PHONE_PLAYER)),
        ),
        engine = workers,
        next = OwnPlayerScriptRunner.Next(page = pages, clock = { 0L }),
    )

    private fun request() = PlayerScriptRequest(
        playerScriptUrl = PHONE_PLAYER,
        pageUrl = PAGE,
        challenges = RATES.mapIndexed { index, input ->
            PlayerScriptChallenge("n$index", PlayerScriptChallengeKind.RATE_PARAM, input)
        } + SIGNATURES.mapIndexed { index, input ->
            PlayerScriptChallenge("s$index", PlayerScriptChallengeKind.SIGNATURE, input)
        },
    )

    private fun partOf(json: String): OwnSolverLocator.Part =
        requireNotNull(OwnSolverLocator.decodePart(BoundedJsonParser.parse(json)))

    private companion object {
        const val PLAYER = "probe0test"
        const val BUILD = "phone"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/probe0test/player-plasma-ias-phone-en_US.vflset/base.js"
        const val PAGE = "https://www.youtube.com/"
        const val SOURCE = "var synthetic = 'probe test player';"
        val RATES = listOf("probe-n-one", "probe-n-two")
        val SIGNATURES = listOf("probe-sig-one")
        val BOTH = setOf(PlayerScriptChallengeKind.RATE_PARAM, PlayerScriptChallengeKind.SIGNATURE)
        val HASHES = listOf("0a0b0c0d", "1a1b1c1d", "2a2b2c2d", "3a3b3c3d", "4a4b4c4d", "5a5b5c5d")
        val P_PART_N = """{"s":"P","c":[0,2],"o":"${HASHES[0]}"}"""
        val P_PART_SIG = """{"s":"P","c":[1],"o":"${HASHES[1]}"}"""
        val B_PART_N = """{"s":"B","m":[[7,1,"${HASHES[2]}",120]],"o":"${HASHES[3]}"}"""
        val B_PART_SIG = """{"s":"B","m":[[9,0,"${HASHES[4]}",64]],"o":"${HASHES[5]}"}"""
        val P_LOCATOR = """{"v":1,"n":$P_PART_N,"sig":$P_PART_SIG}"""
        val B_LOCATOR = """{"v":1,"n":$B_PART_N,"sig":$B_PART_SIG}"""
        val NO_EVENTS = object : OwnSolverEngine.Events {
            override fun started() = Unit

            override fun gone() = Unit
        }
    }
}