package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import com.alal.yft.extractor.master.solver.OwnSolverLadderRule.Kind
import com.alal.yft.extractor.master.solver.OwnSolverLocator.appendPart
import com.alal.yft.extractor.master.solver.OwnSolverLocators.Located
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 N5b A.4/A.5: the locator cache and the V ledger in [OwnPlayerScriptRunner]'s ladder
 * (ownSolverNext only), on fake engines that answer like the core in locate mode: a part per kind
 * answered cleanly, a hit for a sent part that is the core's own. What is kept (only the chosen
 * step's clean part; a signature's after V), what each worker job is sent, what is dropped, and
 * that a part the answer used needs its own V pass.
 */
class OwnSolverLocatorRunnerTest {
    @Test
    fun `a miss locates - n's part is kept at once, sig's once V passed`() = runTest {
        val files = Files()
        val worker = CoreEngine { job, events -> core(job, events) }
        val runner = runner(worker, files)
        val request = request()

        val result = runner.resolve(request) as PlayerScriptResult.Success
        assertEquals(ScriptedOwnEngine.signature(SIG), result.resolved["sig-18"])
        assertEquals(OwnSolverLocator.Parts(), worker.jobs.single().parts)
        assertTrue(worker.inputs.single().contains("\"locator\":{}"))
        assertEquals(Located(Step.WORKER_P, P_PARTS.rate!!), files.locators().peek(PLAYER)?.rate)
        assertNull(files.locators().peek(PLAYER)?.signature)

        val check = requireNotNull(runner.pendingCheck(request))
        assertEquals(PendingCheck(PLAYER, Step.WORKER_P, SCRIPT, PendingCheck.NO_LOCATOR), check)
        runner.record(check, SignatureVerdicts.Verdict.PASS)
        assertEquals(Located(Step.WORKER_P, P_PARTS.signature!!), files.locators().peek(PLAYER)?.signature)
        assertTrue(files.ledger().passed(check.cacheKey))
    }

    @Test
    fun `a hit sends each kind's part to its step, and the signature it gave needs its own V`() = runTest {
        val worker = CoreEngine { job, events -> core(job, events) }
        val runner = runner(worker, Files())
        val first = request()
        runner.resolve(first)
        runner.record(requireNotNull(runner.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)

        val second = request()
        val result = runner.resolve(second) as PlayerScriptResult.Success
        assertEquals(ScriptedOwnEngine.signature(SIG), result.resolved["sig-18"])
        val job = worker.jobs.last()
        assertEquals("prepared", job.type)
        assertEquals(P_PARTS, job.parts)
        // V passed for the full run, not for an answer from the part: V again, keyed by the part.
        val check = requireNotNull(runner.pendingCheck(second))
        assertEquals(OwnSolverLocator.hashOf(P_PARTS.signature!!), check.locatorHash)
        runner.record(check, SignatureVerdicts.Verdict.PASS)

        val third = request()
        runner.resolve(third)
        assertNull(runner.pendingCheck(third))
    }

    @Test
    fun `a hit after a restart - a new runner reads both files and sends the parts with the player text`() = runTest {
        val files = Files()
        val before = runner(CoreEngine { job, events -> core(job, events) }, files)
        val first = request()
        before.resolve(first)
        before.record(requireNotNull(before.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)

        val worker = CoreEngine { job, events -> core(job, events) }
        val after = runner(worker, files)
        val second = request()
        assertTrue(after.resolve(second) is PlayerScriptResult.Success)
        val job = worker.jobs.single()
        assertEquals("player", job.type)
        assertEquals(P_PARTS, job.parts)
        val check = requireNotNull(after.pendingCheck(second))
        after.record(check, SignatureVerdicts.Verdict.PASS)

        // Another restart: the ledger file has this hit's pass, so no V.
        val again = runner(CoreEngine { job, events -> core(job, events) }, files)
        val third = request()
        again.resolve(third)
        assertNull(again.pendingCheck(third))
    }

    @Test
    fun `a poisoned entry with a V pass on file - V runs again, its fail drops the part and the next answer is checked`() =
        runTest {
            val files = Files()
            val learn = runner(CoreEngine { job, events -> core(job, events) }, files)
            val first = request()
            learn.resolve(first)
            learn.record(requireNotNull(learn.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)

            // The file now names another candidate for sig; a core that could not tell would use it.
            val poisoned = OwnSolverLocator.PPart(listOf(9), "0000beef")
            files.locatorFile.text = files.locatorFile.text!!.replace(json(P_PARTS.signature!!), json(poisoned))
            val worker = CoreEngine { job, events -> core(job, events, gullible = true) }
            val runner = runner(worker, files)
            val second = request()
            val result = runner.resolve(second) as PlayerScriptResult.Success
            assertEquals(OwnSolverLocator.Parts(P_PARTS.rate, poisoned), worker.jobs.single().parts)
            // A wrong signature came back, and it waits for V: the ledger's pass was for no part.
            assertNotEquals(ScriptedOwnEngine.signature(SIG), result.resolved["sig-18"])
            val check = requireNotNull(runner.pendingCheck(second))
            assertEquals(OwnSolverLocator.hashOf(poisoned), check.locatorHash)

            runner.record(check, SignatureVerdicts.Verdict.FAIL)
            assertNull(files.locators().peek(PLAYER)?.signature)
            assertEquals(P_PARTS.rate, files.locators().peek(PLAYER)?.rate?.part)
            // P's signatures are off now: B answers sig, and that answer is checked too.
            val third = request()
            val next = runner.resolve(third) as PlayerScriptResult.Success
            assertEquals(ScriptedOwnEngine.signature(SIG), next.resolved["sig-18"])
            assertTrue(worker.jobs.last().isB)
            assertEquals(Step.WORKER_B, requireNotNull(runner.pendingCheck(third)).step)
        }

    @Test
    fun `a sent part that comes back without one is dropped - the kind failed, the other keeps its part`() = runTest {
        val files = Files()
        var failSig = false
        val worker = CoreEngine { job, events ->
            if (job.isB) core(job, events, failed = setOf("sig")) else core(job, events, failed = if (failSig) setOf("sig") else emptySet())
        }
        val runner = runner(worker, files)
        val first = request()
        runner.resolve(first)
        runner.record(requireNotNull(runner.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)
        assertNotNull(files.locators().peek(PLAYER)?.signature)

        failSig = true
        val second = request()
        runner.resolve(second)
        assertEquals(P_PARTS, worker.jobs[1].parts)
        assertNull(files.locators().peek(PLAYER)?.signature)
        assertEquals(Located(Step.WORKER_P, P_PARTS.rate!!), files.locators().peek(PLAYER)?.rate)
        assertTrue(runner.lastFailures.contains("sig:ladder"))
    }

    @Test
    fun `other player text under the same player ID drops the entry - the job only locates`() = runTest {
        val files = Files()
        val learn = runner(CoreEngine { job, events -> core(job, events) }, files)
        learn.resolve(request())
        assertEquals(SCRIPT, files.locators().peek(PLAYER)?.scriptHash)

        val worker = CoreEngine { job, events -> core(job, events) }
        val changed = runner(worker, files, players(OTHER_TEXT))
        changed.resolve(request())
        assertEquals(OwnSolverLocator.Parts(), worker.jobs.single().parts)
        assertEquals(OwnSolverLocator.sha256Hex(OTHER_TEXT), files.locators().peek(PLAYER)?.scriptHash)
    }

    @Test
    fun `only the chosen step's clean part is kept - P with an init error gives none, B's go to B`() = runTest {
        val files = Files()
        // A core that wrongly sent parts with an init error: the runner still keeps none of them.
        val worker = CoreEngine { job, events ->
            if (job.isB) core(job, events) else core(job, events, initErrors = 1, partsAnyway = true)
        }
        val runner = runner(worker, files)
        val first = request()
        runner.resolve(first)
        assertEquals(Located(Step.WORKER_B, B_PARTS.rate!!), files.locators().peek(PLAYER)?.rate)
        runner.record(requireNotNull(runner.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)
        assertEquals(Located(Step.WORKER_B, B_PARTS.signature!!), files.locators().peek(PLAYER)?.signature)

        runner.resolve(request())
        val (p, b) = worker.jobs.takeLast(2)
        assertEquals(OwnSolverLocator.Parts(), p.parts)
        assertEquals(B_PARTS, b.parts)
        assertTrue(b.inputs.contains("\"strategy\":\"B\""))
    }

    @Test
    fun `a part of the other strategy or with an init error is not kept, and a budget gives none`() = runTest {
        val files = Files()
        val worker = CoreEngine { job, events ->
            when {
                job.isB -> core(job, events, failed = setOf("sig"), budget = true)
                else -> core(job, events, failed = setOf("sig"), budget = true, swapStrategy = true)
            }
        }
        val runner = runner(worker, files)
        runner.resolve(request())
        assertNull(files.locators().peek(PLAYER))
        assertTrue(runner.lastFailures.contains("sig:ladder"))
    }

    @Test
    fun `V fail or unknown writes no waiting part, and V fail drops a hit's part`() = runTest {
        val files = Files()
        val worker = CoreEngine { job, events -> core(job, events) }
        val runner = runner(worker, files)
        val first = request()
        runner.resolve(first)
        runner.record(requireNotNull(runner.pendingCheck(first)), SignatureVerdicts.Verdict.UNKNOWN)
        assertNull(files.locators().peek(PLAYER)?.signature)
        val second = request()
        runner.resolve(second)
        runner.record(requireNotNull(runner.pendingCheck(second)), SignatureVerdicts.Verdict.FAIL)
        assertNull(files.locators().peek(PLAYER)?.signature)
        assertEquals(P_PARTS.rate, files.locators().peek(PLAYER)?.rate?.part)

        // A part kept after a pass, then a hit on it whose V fails: the part goes.
        val again = Files()
        val learn = runner(CoreEngine { job, events -> core(job, events) }, again)
        val third = request()
        learn.resolve(third)
        learn.record(requireNotNull(learn.pendingCheck(third)), SignatureVerdicts.Verdict.PASS)
        val fourth = request()
        learn.resolve(fourth)
        val hit = requireNotNull(learn.pendingCheck(fourth))
        assertNotEquals(PendingCheck.NO_LOCATOR, hit.locatorHash)
        learn.record(hit, SignatureVerdicts.Verdict.FAIL)
        assertNull(again.locators().peek(PLAYER)?.signature)
    }

    @Test
    fun `page steps never carry a locator, and Phase 1-1 sends none`() = runTest {
        val worker = CoreEngine { job, events -> core(job, events, initErrors = 2) }
        // Page P with an init error, so page B runs too.
        val page = CoreEngine { job, events -> core(job, events, initErrors = if (job.isB) 0 else 1) }
        val files = Files()
        val runner = OwnPlayerScriptRunner(
            players(),
            worker,
            next = OwnPlayerScriptRunner.Next(page = page, locators = files.locators(), ledger = files.ledger()),
        )
        val request = request()
        assertTrue(runner.resolve(request) is PlayerScriptResult.Success)
        assertEquals(2, page.inputs.size)
        assertTrue(page.inputs.none { it.contains("locator") })
        assertTrue(worker.inputs.all { it.contains("\"locator\":{}") })
        assertEquals(Step.PAGE_B, requireNotNull(runner.pendingCheck(request)).step)
        assertNull(files.locators().peek(PLAYER))

        val plain = CoreEngine { job, events -> core(job, events) }
        OwnPlayerScriptRunner(players(), plain).resolve(request())
        assertTrue(plain.inputs.none { it.contains("locator") })
    }

    @Test
    fun `a reply that claims a hit for a part it was not sent uses no part`() = runTest {
        val worker = CoreEngine { job, events -> core(job, events, claimHits = true) }
        val runner = runner(worker, Files())
        val request = request()
        runner.resolve(request)
        assertEquals(PendingCheck.NO_LOCATOR, requireNotNull(runner.pendingCheck(request)).locatorHash)
    }

    @Test
    fun `a sent part that misses ran in full - a full run's V pass holds, and the core's own part replaces it`() = runTest {
        val files = Files()
        val learn = runner(CoreEngine { job, events -> core(job, events) }, files)
        val first = request()
        learn.resolve(first)
        learn.record(requireNotNull(learn.pendingCheck(first)), SignatureVerdicts.Verdict.PASS)
        val other = OwnSolverLocator.PPart(listOf(9), "0000beef")
        files.locatorFile.text = files.locatorFile.text!!.replace(json(P_PARTS.signature!!), json(other))

        val worker = CoreEngine { job, events -> core(job, events) }
        val runner = runner(worker, files)
        val second = request()
        runner.resolve(second)
        assertEquals(OwnSolverLocator.Parts(P_PARTS.rate, other), worker.jobs.single().parts)
        // The full run on this player text passed V before (no part): no V now, and the core's
        // own part replaces the one sent.
        assertNull(runner.pendingCheck(second))
        assertEquals(P_PARTS.signature, files.locators().peek(PLAYER)?.signature?.part)
    }

    // ---------------------------------------------------------------------------------------------

    /** The app's two files, here in memory; each store reads its file on first use. */
    private class Files {
        val locatorFile = MemoryTextFile()
        val ledgerFile = MemoryTextFile()
        private var locators: OwnSolverLocators? = null
        private var ledger: OwnSolverVLedger? = null

        /** A fresh store on the file (as after a restart) the first time; the same one after. */
        fun locators(): OwnSolverLocators = locators ?: OwnSolverLocators(locatorFile).also { locators = it }

        fun ledger(): OwnSolverVLedger = ledger ?: OwnSolverVLedger(ledgerFile).also { ledger = it }

        fun restart() {
            locators = null
            ledger = null
        }
    }

    private class MemoryTextFile(var text: String? = null) : OwnSolverTextFile {
        override fun read(): String? = text

        override fun write(text: String): Boolean {
            this.text = text
            return true
        }
    }

    /** A fake engine that reports "started" like the WebView engines and answers per job. */
    private class CoreEngine(
        private val answer: suspend (Job, OwnSolverEngine.Events) -> String?,
    ) : OwnSolverEngine {
        override val isAvailable: Boolean = true
        override val reportsReady: Boolean = true
        val inputs = mutableListOf<String>()
        val jobs = mutableListOf<Job>()

        override suspend fun run(input: String): String? = run(input, NO_EVENTS)

        override suspend fun run(input: String, events: OwnSolverEngine.Events): String? {
            inputs += input
            val job = Job(ScriptedOwnEngine.Job.parse(input), input)
            jobs += job
            return answer(job, events)
        }
    }

    private class Job(val parsed: ScriptedOwnEngine.Job, val inputs: String) {
        val isB: Boolean get() = parsed.isB
        val type: String get() = parsed.type
        val parts: OwnSolverLocator.Parts? get() = parsed.parts
    }

    /**
     * Answers like the core: every asked input of each kind not [failed] (`budget` when [budget]);
     * in locate mode a part per kind answered cleanly (no init errors; [partsAnyway]: even so) and
     * a hit for a sent part equal to the core's own. [gullible]: any sent part hits and gives a
     * wrong signature (a core that could not tell a poisoned part). [swapStrategy]: parts of the
     * other strategy. [claimHits]: a hit for every kind, sent or not.
     */
    private fun core(
        job: Job,
        events: OwnSolverEngine.Events,
        initErrors: Int = 0,
        failed: Set<String> = emptySet(),
        budget: Boolean = false,
        partsAnyway: Boolean = false,
        gullible: Boolean = false,
        swapStrategy: Boolean = false,
        claimHits: Boolean = false,
    ): String {
        events.started()
        val own = if (job.isB != swapStrategy) B_PARTS else P_PARTS
        val sent = job.parts
        val hits = mutableListOf<String>()
        var wrong = false
        if (sent != null) {
            for ((name, kind) in listOf("n" to Kind.RATE, "sig" to Kind.SIGNATURE)) {
                val part = sent.of(kind) ?: continue
                if (name in failed) continue
                if (part == own.of(kind)) {
                    hits += name
                } else if (gullible) {
                    hits += name
                    if (kind == Kind.SIGNATURE) wrong = true
                }
            }
            if (claimHits) hits.addAll(listOf("n", "sig").filter { it !in hits && it !in failed })
        }
        val locator = if (sent == null) {
            null
        } else {
            buildString {
                append("{\"v\":1")
                if (initErrors == 0 || partsAnyway) {
                    if ("n" !in failed) append(",\"n\":").append(json(own.rate!!))
                    if ("sig" !in failed) append(",\"sig\":").append(json(own.signature!!))
                }
                append('}')
            }
        }
        val reason = if (budget) "budget" else "disagree"
        return ScriptedOwnEngine.result(
            n = if ("n" in failed) emptyMap() else job.parsed.n.associateWith(ScriptedOwnEngine::rate),
            sig = if ("sig" in failed) {
                emptyMap()
            } else {
                job.parsed.sig.associateWith { if (wrong) "W" + it else ScriptedOwnEngine.signature(it) }
            },
            failed = failed.joinToString(",") { "$it:$reason" }.ifEmpty { null },
            prepared = ScriptedOwnEngine.preparedFor(job.parsed),
            initErrors = initErrors,
            locator = locator,
            locatorHits = hits,
        )
    }

    private fun runner(
        worker: OwnSolverEngine,
        files: Files,
        http: FakeExtractorHttpClient = players(),
    ): OwnPlayerScriptRunner {
        files.restart()
        return OwnPlayerScriptRunner(
            http,
            worker,
            next = OwnPlayerScriptRunner.Next(locators = files.locators(), ledger = files.ledger()),
        )
    }

    private fun players(text: String = PLAYER_TEXT) = FakeExtractorHttpClient(
        responses = mapOf(PHONE_PLAYER to FakeExtractorHttpClient.json(text, PHONE_PLAYER)),
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
        const val OTHER_TEXT = "var _yt_player = {}; (function (g) { /* other build */ })(_yt_player);"
        const val SIG = "AOq0QJ8wRAIgFixtureVideoSignature0123456789abcdefgh=="
        const val RATE = "Fx7nInput0"
        val SCRIPT: String = OwnSolverLocator.sha256Hex(PLAYER_TEXT)
        val P_PARTS = OwnSolverLocator.Parts(
            rate = OwnSolverLocator.PPart(listOf(2), "0000aaaa"),
            signature = OwnSolverLocator.PPart(listOf(5, 6), "0000bbbb"),
        )
        val B_PARTS = OwnSolverLocator.Parts(
            rate = OwnSolverLocator.BPart(listOf(OwnSolverLocator.Member(70, 0, "0000cccc", 120)), "0000dddd"),
            signature = OwnSolverLocator.BPart(listOf(OwnSolverLocator.Member(71, 1, "0000eeee", 130)), "0000ffff"),
        )
        val NO_EVENTS = object : OwnSolverEngine.Events {
            override fun started() = Unit
            override fun gone() = Unit
        }

        fun json(part: OwnSolverLocator.Part): String = buildString { appendPart(part) }
    }
}
