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

    private fun quick(page: OwnSolverEngine) =
        OwnPlayerScriptRunner.Next(page = page, readyTimeoutMillis = 1_000, jobTimeoutMillis = 1_000)

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
        val NO_EVENTS = object : OwnSolverEngine.Events {
            override fun started() = Unit
            override fun gone() = Unit
        }
    }
}
