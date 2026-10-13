package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 N5b: the ladder in [OwnPlayerScriptRunner] behind ownSolverNext ([OwnPlayerScriptRunner.Next]),
 * on fake engines: which step answers, the job fields, ready and job timeouts, the breaker and
 * its markers, and the V hand-over ([SignatureVerdicts]).
 */
class OwnSolverLadderRunnerTest {
    @Test
    fun `ownSolverNext off is Phase 1-1 - one worker job, no tripwire, nothing waits for V`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events) }
        val runner = OwnPlayerScriptRunner(players(), worker)
        val request = request()

        assertTrue(runner.resolve(request) is PlayerScriptResult.Success)
        assertEquals(1, worker.inputs.size)
        assertTrue("tripwire" !in worker.inputs.single())
        assertNull(worker.jobs.single().deadlineMillis)
        assertNull(runner.pendingCheck(request))
    }

    @Test
    fun `worker P answers cleanly - its values, the tripwire asked, the signature waits for V`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events) }
        val page = StepEngine { job, events -> clean(job, events) }
        val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next(page = page))
        val request = request()

        val result = runner.resolve(request) as PlayerScriptResult.Success
        assertEquals(ScriptedOwnEngine.signature(SIG), result.resolved["sig-18"])
        assertEquals(ScriptedOwnEngine.rate(RATE), result.resolved["n-18"])
        assertEquals(1, worker.inputs.size)
        assertTrue(worker.inputs.single().contains("\"tripwire\":true"))
        assertTrue(page.inputs.isEmpty())
        assertEquals(PendingCheck(PLAYER, Step.WORKER_P), runner.pendingCheck(request))
        assertNull(runner.pendingCheck(request))

        // A pass is kept per player version and step: the next answer needs no V, from the kept program.
        runner.record(PendingCheck(PLAYER, Step.WORKER_P), SignatureVerdicts.Verdict.PASS)
        val again = request()
        assertTrue(runner.resolve(again) is PlayerScriptResult.Success)
        assertNull(runner.pendingCheck(again))
        assertEquals("prepared", worker.jobs.last().type)
    }

    @Test
    fun `init errors in P send the kinds to worker B, from the player text`() = runTest {
        val worker = StepEngine { job, events ->
            if (job.isB) clean(job, events) else clean(job, events, initErrors = 1)
        }
        val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next())
        val request = request()

        val result = runner.resolve(request) as PlayerScriptResult.Success
        assertEquals(ScriptedOwnEngine.signature(SIG), result.resolved["sig-18"])
        assertEquals(2, worker.inputs.size)
        val b = worker.inputs[1]
        assertTrue(b.contains("\"strategy\":\"B\",\"hint\":false"))
        assertTrue(b.contains("\"tripwire\":true"))
        assertEquals("player", worker.jobs[1].type)
        assertEquals(PendingCheck(PLAYER, Step.WORKER_B), runner.pendingCheck(request))
    }

    @Test
    fun `workers with init errors send the run to the page - page P, then page B where P was not clean`() =
        runTest {
            val worker = StepEngine { job, events -> clean(job, events, initErrors = 2) }
            val page = StepEngine { job, events -> clean(job, events, initErrors = if (job.isB) 0 else 1) }
            val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next(page = page))
            val request = request()

            assertTrue(runner.resolve(request) is PlayerScriptResult.Success)
            assertEquals(2, worker.inputs.size)
            assertEquals(2, page.inputs.size)
            assertTrue(page.inputs.none { it.contains("tripwire") })
            assertTrue(page.inputs[1].contains("\"strategy\":\"B\""))
            assertEquals(PendingCheck(PLAYER, Step.PAGE_B), runner.pendingCheck(request))
        }

    @Test
    fun `without a clean step nothing is answered`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events, initErrors = 1) }
        val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next())

        assertTrue(runner.resolve(request()) is PlayerScriptResult.Failed)
        assertTrue(runner.lastFailures.contains("n:ladder"))
        assertTrue(runner.lastFailures.contains("sig:ladder"))
    }

    @Test
    fun `a page that never takes the job is a ready timeout, with no strike`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events, initErrors = 1) }
        val page = StepEngine { _, _ -> awaitCancellation() }
        val runner = OwnPlayerScriptRunner(players(), worker, next = quick(page))

        assertTrue(runner.resolve(request()) is PlayerScriptResult.Failed)
        assertTrue(runner.lastFailures.contains("pP ready timeout"))
        val pageJobs = page.inputs.size
        runner.resolve(request())
        assertTrue(page.inputs.size > pageJobs)
    }

    @Test
    fun `a job timeout on the page turns the page off until the process restarts`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events, initErrors = 1) }
        val page = StepEngine { _, events ->
            events.started()
            awaitCancellation()
        }
        val runner = OwnPlayerScriptRunner(players(), worker, next = quick(page))

        runner.resolve(request())
        assertTrue(runner.lastFailures.contains("pP job timeout"))
        assertEquals(1, page.inputs.size)
        runner.resolve(request())
        assertEquals(1, page.inputs.size)
    }

    @Test
    fun `a gone renderer turns that worker step off, and B takes over`() = runTest {
        var gone = true
        val worker = StepEngine { job, events ->
            events.started()
            if (!job.isB && gone) {
                gone = false
                events.gone()
                null
            } else {
                clean(job, events)
            }
        }
        val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next())
        val first = request()

        runner.resolve(first)
        assertTrue(runner.lastFailures.contains("P gone"))
        val second = request()
        runner.resolve(second)
        assertTrue(worker.jobs.drop(2).all { it.isB })
        assertEquals(PendingCheck(PLAYER, Step.WORKER_B), runner.pendingCheck(second))
    }

    @Test
    fun `a marker left behind twice keeps the step off for that player, once is retried`() = runTest {
        val markers = OwnSolverBreaker.MemoryMarkers()
        markers.set(OwnSolverBreaker.key(PLAYER, Step.WORKER_P), 2)
        val worker = StepEngine { job, events -> clean(job, events) }
        val runner = OwnPlayerScriptRunner(
            players(),
            worker,
            next = OwnPlayerScriptRunner.Next(breaker = OwnSolverBreaker(markers)),
        )
        runner.resolve(request())
        assertTrue(worker.jobs.all { it.isB })

        val retried = OwnSolverBreaker.MemoryMarkers()
        retried.set(OwnSolverBreaker.key(PLAYER, Step.WORKER_P), 1)
        val during = mutableListOf<Int>()
        val again = StepEngine { job, events ->
            during += retried.count(OwnSolverBreaker.key(PLAYER, Step.WORKER_P))
            clean(job, events)
        }
        OwnPlayerScriptRunner(players(), again, next = OwnPlayerScriptRunner.Next(breaker = OwnSolverBreaker(retried)))
            .resolve(request())
        assertEquals(listOf(2), during)
        assertEquals(0, retried.count(OwnSolverBreaker.key(PLAYER, Step.WORKER_P)))
    }

    @Test
    fun `V = fail turns that step's signatures off for the player, and the ladder looks further`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events) }
        val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next())
        val first = request()
        runner.resolve(first)
        val check = requireNotNull(runner.pendingCheck(first))

        runner.record(check, SignatureVerdicts.Verdict.FAIL)
        val second = request()
        val result = runner.resolve(second) as PlayerScriptResult.Success
        assertEquals(ScriptedOwnEngine.rate(RATE), result.resolved["n-18"])
        assertTrue(worker.jobs.last().isB)
        assertEquals(PendingCheck(PLAYER, Step.WORKER_B), runner.pendingCheck(second))
    }

    @Test
    fun `an unknown verdict keeps nothing - the next answer waits for V again`() = runTest {
        val worker = StepEngine { job, events -> clean(job, events) }
        val runner = OwnPlayerScriptRunner(players(), worker, next = OwnPlayerScriptRunner.Next())
        val first = request()
        runner.resolve(first)
        runner.record(requireNotNull(runner.pendingCheck(first)), SignatureVerdicts.Verdict.UNKNOWN)
        val second = request()
        runner.resolve(second)
        assertEquals(PendingCheck(PLAYER, Step.WORKER_P), runner.pendingCheck(second))
    }

    @Test
    fun `each ladder job carries its step's cap as its deadline`() = runTest {
        val clock = FakeClock()
        val worker = StepEngine { job, events -> clean(job, events, initErrors = 1) }
        val page = StepEngine { job, events -> clean(job, events, initErrors = 1) }
        OwnPlayerScriptRunner(players(), worker, next = timed(clock, page)).resolve(request())

        assertEquals(listOf<Long?>(40_000, 40_000), deadlines(worker))
        assertEquals(listOf<Long?>(35_000, 35_000), deadlines(page))
        assertTrue(worker.inputs.all { it.contains("\"deadline_ms\":40000") })
    }

    @Test
    fun `a step's cap ends before its job timeout and the lookup has a cap`() {
        listOf(
            { OwnPlayerScriptRunner.Next(workerCapMillis = OwnPlayerScriptRunner.DEFAULT_TIMEOUT_MILLIS) },
            { OwnPlayerScriptRunner.Next(pageCapMillis = 0) },
            { OwnPlayerScriptRunner.Next(lookupCapMillis = 0) },
        ).forEach { next -> assertTrue(runCatching(next).exceptionOrNull() is IllegalArgumentException) }
    }

    @Test
    fun `all four steps slow - each lookup ends inside its cap and the next lookup goes on to the page`() =
        runTest {
            val clock = FakeClock()
            val worker = StepEngine { job, events -> takes(clock, job, events, SLOW) }
            val page = StepEngine { job, events -> takes(clock, job, events, SLOW) }
            val breaker = OwnSolverBreaker()
            val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock, page, breaker))

            suspend fun lookup(): Long {
                val start = clock.now
                assertTrue(runner.resolve(request()) is PlayerScriptResult.Failed)
                return clock.now - start
            }

            // P has its full cap (kept: off); B gets what is left (short: not kept); no time for the page.
            assertEquals(60_000L, lookup())
            assertEquals(listOf<Long?>(40_000, 20_000), deadlines(worker))
            assertTrue(page.jobs.isEmpty())
            assertTrue(runner.lastFailures.containsAll(listOf("pP lookup cap", "pB lookup cap")))

            // P is skipped; B has its full cap now (off); page P gets what is left.
            assertEquals(60_000L, lookup())
            assertTrue(runner.lastFailures.containsAll(listOf("P budget off", "pB lookup cap")))
            assertEquals(listOf<Long?>(40_000, 20_000, 40_000), deadlines(worker))
            assertEquals(listOf<Long?>(20_000), deadlines(page))

            // Both workers skipped: page P with its full cap (off), page B with what is left.
            assertEquals(60_000L, lookup())
            assertEquals(3, worker.jobs.size)
            assertEquals(listOf<Long?>(20_000, 35_000, 25_000), deadlines(page))

            // Page B with its full cap; then every step is off for this player, and nothing runs.
            assertEquals(35_000L, lookup())
            assertEquals(listOf<Long?>(20_000, 35_000, 25_000, 35_000), deadlines(page))
            assertEquals(0L, lookup())
            assertEquals(3, worker.jobs.size)
            assertEquals(4, page.jobs.size)
            val off = listOf("P budget off", "B budget off", "pP budget off", "pB budget off")
            assertTrue(runner.lastFailures.containsAll(off))
            // A budget is no strike: the breaker still allows every step.
            assertTrue(Step.entries.all { breaker.allows(PLAYER, it) })
        }

    @Test
    fun `slow P leaves B a short deadline - its budget is not kept and the next lookup runs B again`() = runTest {
        val clock = FakeClock()
        val worker = StepEngine { job, events ->
            if (job.isB) takes(clock, job, events, 30_000) else takes(clock, job, events, SLOW)
        }
        val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock))

        assertTrue(runner.resolve(request()) is PlayerScriptResult.Failed)
        assertEquals(listOf<Long?>(40_000, 20_000), deadlines(worker))

        val second = request()
        val result = runner.resolve(second) as PlayerScriptResult.Success
        assertEquals(ScriptedOwnEngine.signature(SIG), result.resolved["sig-18"])
        assertTrue(runner.lastFailures.contains("P budget off"))
        assertEquals(listOf<Long?>(40_000, 20_000, 40_000), deadlines(worker))
        assertTrue(worker.jobs.last().isB)
        assertEquals(PendingCheck(PLAYER, Step.WORKER_B), runner.pendingCheck(second))
    }

    @Test
    fun `a budget with the full cap is kept - the next lookup skips that step, with no strike`() = runTest {
        val clock = FakeClock()
        val worker = StepEngine { job, events ->
            if (job.isB) takes(clock, job, events, SLOW) else clean(job, events, initErrors = 1)
        }
        val breaker = OwnSolverBreaker()
        val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock, breaker = breaker))

        runner.resolve(request())
        assertEquals(listOf<Long?>(40_000, 40_000), deadlines(worker))
        runner.resolve(request())
        assertEquals(listOf(false, true, false), worker.jobs.map { it.isB })
        assertTrue(runner.lastFailures.contains("B budget off"))
        assertTrue(breaker.allows(PLAYER, Step.WORKER_B))
    }

    @Test
    fun `two lookups in a row where B has only a short deadline and runs out turn B off - the third skips it`() =
        runTest {
            val clock = FakeClock()
            val worker = StepEngine { job, events ->
                if (job.isB) takes(clock, job, events, SLOW) else takes(clock, job, events, 30_000, initErrors = 1)
            }
            val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock))

            runner.resolve(request())
            runner.resolve(request())
            assertEquals(listOf<Long?>(40_000, 30_000, 40_000, 30_000), deadlines(worker))
            runner.resolve(request())
            assertEquals(listOf(false, true, false, true, false), worker.jobs.map { it.isB })
            assertTrue(runner.lastFailures.contains("B budget off"))
        }

    @Test
    fun `short budget, then an answer, then a short budget again is not kept - the answer starts the count again`() =
        runTest {
            val clock = FakeClock()
            val needs = listOf(SLOW, 0L, SLOW, SLOW)
            var runs = 0
            val worker = StepEngine { job, events ->
                if (job.isB) {
                    takes(clock, job, events, needs[runs++])
                } else {
                    takes(clock, job, events, 30_000, initErrors = 1)
                }
            }
            val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock))

            repeat(4) { runner.resolve(request()) }
            // B ran in all four lookups; the fourth is its second short budget in a row.
            assertEquals(4, runs)
            assertTrue("B budget off" !in runner.lastFailures)
            runner.resolve(request())
            assertEquals(4, runs)
            assertTrue(runner.lastFailures.contains("B budget off"))
        }

    @Test
    fun `a B job that never starts is not a run - the short budgets around it are still in a row`() = runTest {
        val clock = FakeClock()
        var runs = 0
        val worker = StepEngine { job, events ->
            when {
                !job.isB -> takes(clock, job, events, 30_000, initErrors = 1)
                runs++ == 1 -> awaitCancellation()
                else -> takes(clock, job, events, SLOW)
            }
        }
        val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock))

        runner.resolve(request())
        runner.resolve(request())
        assertTrue(runner.lastFailures.contains("B ready timeout"))
        runner.resolve(request())
        runner.resolve(request())
        assertEquals(3, runs)
        assertTrue(runner.lastFailures.contains("B budget off"))
    }

    @Test
    fun `a budget from the kept program does not break it - no rebuild from the player text`() = runTest {
        val clock = FakeClock()
        val worker = StepEngine { job, events ->
            if (job.type == "prepared") takes(clock, job, events, SLOW) else clean(job, events)
        }
        val runner = OwnPlayerScriptRunner(players(), worker, next = timed(clock))

        runner.resolve(request())
        runner.resolve(request())
        // P from the player (kept), P from the kept program (budget), then B from the player text.
        assertEquals(listOf("player", "prepared", "player"), worker.jobs.map { it.type })
        assertEquals(listOf(false, false, true), worker.jobs.map { it.isB })
        assertEquals(listOf<Long?>(40_000, 40_000, 20_000), deadlines(worker))
    }

    /** A fake engine that reports "started" like the WebView engines and answers per job. */
    private class StepEngine(
        override val reportsReady: Boolean = true,
        private val answer: suspend (ScriptedOwnEngine.Job, OwnSolverEngine.Events) -> String?,
    ) : OwnSolverEngine {
        override val isAvailable: Boolean = true
        val inputs = mutableListOf<String>()
        val jobs = mutableListOf<ScriptedOwnEngine.Job>()

        override suspend fun run(input: String): String? = run(input, NO_EVENTS)

        override suspend fun run(input: String, events: OwnSolverEngine.Events): String? {
            inputs += input
            val job = ScriptedOwnEngine.Job.parse(input)
            jobs += job
            return answer(job, events)
        }
    }

    private fun clean(job: ScriptedOwnEngine.Job, events: OwnSolverEngine.Events, initErrors: Int = 0): String {
        events.started()
        return ScriptedOwnEngine.result(
            n = job.n.associateWith(ScriptedOwnEngine::rate),
            sig = job.sig.associateWith(ScriptedOwnEngine::signature),
            prepared = ScriptedOwnEngine.preparedFor(job),
            initErrors = initErrors,
        )
    }

    private fun quick(page: OwnSolverEngine) = OwnPlayerScriptRunner.Next(
        page = page,
        readyTimeoutMillis = 1_000,
        jobTimeoutMillis = 1_000,
        workerCapMillis = 500,
        pageCapMillis = 500,
    )

    /** N5b A.2: the ladder's clock in these tests; a fake job moves it on by the time it takes. */
    private class FakeClock(var now: Long = 0)

    /** The default caps (worker 40 s, page 35 s, lookup 60 s), on [clock]. */
    private fun timed(
        clock: FakeClock,
        page: OwnSolverEngine? = null,
        breaker: OwnSolverBreaker = OwnSolverBreaker(),
    ) = OwnPlayerScriptRunner.Next(page = page, breaker = breaker, clock = { clock.now })

    /**
     * A job that needs [millis]: within its deadline it answers like [clean], else it stops at the
     * deadline with `budget` for both kinds, as the core does when the search runs out after an
     * init with an error (the init error sends the run on to the page).
     */
    private fun takes(
        clock: FakeClock,
        job: ScriptedOwnEngine.Job,
        events: OwnSolverEngine.Events,
        millis: Long,
        initErrors: Int = 0,
    ): String {
        val deadline = requireNotNull(job.deadlineMillis) { "A ladder job carries its deadline" }
        if (millis <= deadline) {
            clock.now += millis
            return clean(job, events, initErrors)
        }
        events.started()
        clock.now += deadline
        return ScriptedOwnEngine.result(failed = "n:budget,sig:budget", initErrors = 1)
    }

    private fun deadlines(engine: StepEngine): List<Long?> = engine.jobs.map { it.deadlineMillis }

    private fun players() = FakeExtractorHttpClient(
        responses = mapOf(PHONE_PLAYER to FakeExtractorHttpClient.json(PLAYER_TEXT, PHONE_PLAYER)),
    )

    private fun request() = PlayerScriptRequest(
        playerScriptUrl = PHONE_PLAYER,
        pageUrl = PAGE,
        challenges = listOf(
            PlayerScriptChallenge("sig-18", PlayerScriptChallengeKind.SIGNATURE, SIG),
            PlayerScriptChallenge("n-18", PlayerScriptChallengeKind.RATE_PARAM, RATE),
        ),
    )

    private companion object {
        const val PLAYER = "f1x7ure0"
        const val PAGE = "https://www.youtube.com/watch?v=Yft0Fixture"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val PLAYER_TEXT = "var _yt_player = {}; (function (g) { /* fixture */ })(_yt_player);"
        const val SIG = "AOq0QJ8wRAIgFixtureVideoSignature0123456789abcdefgh=="
        const val RATE = "Fx7nInput0"
        /** N5b A.2: more than any step's cap. */
        const val SLOW = 100_000L
        val NO_EVENTS = object : OwnSolverEngine.Events {
            override fun started() = Unit
            override fun gone() = Unit
        }
    }
}
