package com.alal.yft.master

import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import com.alal.yft.extractor.master.solver.OwnSolverProbe
import com.alal.yft.master.SolverCanaryReport.Build
import com.alal.yft.master.SolverCanaryReport.Counts
import com.alal.yft.master.SolverCanaryReport.Data
import com.alal.yft.master.SolverCanaryReport.Header
import com.alal.yft.master.SolverCanaryReport.Lookup
import com.alal.yft.master.SolverCanaryReport.Part
import com.alal.yft.master.SolverCanaryReport.Report
import com.alal.yft.master.SolverCanaryReport.StepLine
import com.alal.yft.master.SolverCanaryReport.VStatus
import com.alal.yft.master.SolverCanaryReport.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 N7: the solver canary's verdicts, hit times and report text, from synthetic counts and
 * job summaries (no player, no input and no value of a real run).
 */
class SolverCanaryReportTest {
    private val pass = Counts(total = 4, ejs = 4, agree = 4, wrong = 0)
    private val miss = Counts(total = 4, ejs = 4, agree = 3, wrong = 0)
    private val oracle = Counts(total = 4, ejs = 3, agree = 3, wrong = 0)
    private val wrong = Counts(total = 4, ejs = 4, agree = 3, wrong = 1)

    @Test
    fun judgeCountsOnlyTheInputsEjsSolved() {
        val counts = SolverCanaryReport.judge(
            inputs = listOf("a", "b", "c", "d"),
            ejs = mapOf("a" to "1", "b" to "2", "c" to "3"),
            own = mapOf("a" to "1", "b" to "9", "d" to "4"),
        )
        assertEquals(Counts(total = 4, ejs = 3, agree = 1, wrong = 1), counts)
        assertEquals(1, counts.missed)
        assertFalse(counts.clean)
        assertTrue(SolverCanaryReport.judge(listOf("a"), mapOf("a" to "1"), mapOf("a" to "1")).clean)
        assertFalse(SolverCanaryReport.judge(emptyList(), emptyMap(), emptyMap()).clean)
    }

    @Test
    fun worstTakesTheWorseOfTwoRuns() {
        val second = Counts(total = 4, ejs = 3, agree = 2, wrong = 1)
        assertEquals(pass, SolverCanaryReport.worst(pass, null))
        assertEquals(second, SolverCanaryReport.worst(pass, second))
        assertEquals(second, SolverCanaryReport.worst(second, pass))
        assertEquals(Counts(total = 4, ejs = 3, agree = 3, wrong = 1), SolverCanaryReport.worst(oracle, wrong))
    }

    @Test
    fun verdictPutsWrongBeforeOracleBeforeMissBeforePass() {
        assertEquals(Verdict.FAIL, SolverCanaryReport.verdict(emptyList()))
        assertEquals(Verdict.FAIL, SolverCanaryReport.verdict(listOf(null, null)))
        assertEquals(Verdict.FAIL, SolverCanaryReport.verdict(listOf(Counts(0, 0, 0, 0))))
        assertEquals(Verdict.PASS, SolverCanaryReport.verdict(listOf(pass, pass)))
        assertEquals(Verdict.PASS, SolverCanaryReport.verdict(listOf(pass, null)))
        assertEquals(Verdict.MISS, SolverCanaryReport.verdict(listOf(pass, miss)))
        assertEquals(Verdict.ORACLE, SolverCanaryReport.verdict(listOf(miss, oracle, pass)))
        assertEquals(Verdict.WRONG, SolverCanaryReport.verdict(listOf(oracle, miss, wrong)))
    }

    @Test
    fun workerStrategiesTakeTheWorseOfTheirTwoRuns() {
        val build = buildOf(SolverCanaryReport.PHONE).copy(
            p2 = lineOf("P2", summaryOf(Step.WORKER_P), n = miss),
            b2 = null,
        )
        assertEquals(miss, SolverCanaryReport.countsOf(build, "P", N))
        assertEquals(pass, SolverCanaryReport.countsOf(build, "P", SIG))
        assertEquals(pass, SolverCanaryReport.countsOf(build, "B", N))
        assertNull(SolverCanaryReport.countsOf(build.copy(b1 = null), "B", N))
    }

    @Test
    fun pageStrategyTakesPageBOnlyForKindsPagePDidNotAnswer() {
        val fromPageB = Counts(total = 4, ejs = 4, agree = 2, wrong = 0)
        val build = buildOf(SolverCanaryReport.PHONE).copy(
            pageP = lineOf("page P", summaryOf(Step.PAGE_P, sent = emptySet(), answered = setOf(N)), n = pass, sig = miss),
            pageB = lineOf("page B", summaryOf(Step.PAGE_B, sent = emptySet()), n = fromPageB, sig = fromPageB),
        )
        assertEquals(pass, SolverCanaryReport.countsOf(build, "E", N))
        assertEquals(fromPageB, SolverCanaryReport.countsOf(build, "E", SIG))
        assertEquals(miss, SolverCanaryReport.countsOf(build.copy(pageB = null), "E", SIG))
        assertNull(SolverCanaryReport.countsOf(build.copy(pageP = null), "E", N))
    }

    @Test
    fun passNeedsEveryBuildEveryStepEveryVerdictAndV() {
        assertTrue(SolverCanaryReport.passes(reportOf()))
        assertTrue(SolverCanaryReport.passes(reportOf(lookup = lookupOf(v = VStatus.NOT_NEEDED))))
        assertTrue(SolverCanaryReport.passes(reportOf(lookup = lookupOf(v = VStatus.SKIPPED_LEDGER))))
        assertFalse(SolverCanaryReport.passes(reportOf(stoppedAt = "phone P1 (gone)")))
        assertFalse(SolverCanaryReport.passes(reportOf(builds = emptyList())))
        assertFalse(SolverCanaryReport.passes(reportOf(lookup = null)))
        listOf(VStatus.UNKNOWN, VStatus.FAIL, VStatus.NOT_RUN).forEach { v ->
            assertFalse(v.label, SolverCanaryReport.passes(reportOf(lookup = lookupOf(v = v))))
        }

        val unjudged = Build(name = SolverCanaryReport.MAIN, download = null, failure = "download NETWORK")
        assertFalse(SolverCanaryReport.passes(reportOf(builds = listOf(buildOf(SolverCanaryReport.PHONE), unjudged))))

        val missing = buildOf(SolverCanaryReport.MAIN).let { it.copy(b2 = it.b2?.copy(counts = mapOf(N to miss, SIG to pass))) }
        val oneMiss = reportOf(builds = listOf(buildOf(SolverCanaryReport.PHONE), missing))
        assertEquals(Verdict.MISS, SolverCanaryReport.verdicts(oneMiss).getValue("B").getValue(N))
        assertEquals(Verdict.PASS, SolverCanaryReport.verdicts(oneMiss).getValue("B").getValue(SIG))
        assertFalse(SolverCanaryReport.passes(oneMiss))

        val noPage = reportOf(builds = listOf(buildOf(SolverCanaryReport.PHONE).copy(pageP = null)))
        assertEquals(Verdict.FAIL, SolverCanaryReport.verdicts(noPage).getValue("E").getValue(SIG))
        assertFalse(SolverCanaryReport.passes(noPage))
    }

    @Test
    fun hitNeedsEverySentPartUsed() {
        assertTrue(SolverCanaryReport.hit(summaryOf(Step.WORKER_P)))
        assertFalse(SolverCanaryReport.hit(summaryOf(Step.WORKER_P, sent = emptySet())))
        assertFalse(SolverCanaryReport.hit(summaryOf(Step.WORKER_P, hits = setOf(N))))
        assertFalse(SolverCanaryReport.hit(summaryOf(Step.WORKER_P, outcome = OwnSolverProbe.GONE)))
    }

    @Test
    fun firstLineShowsBothHitTimesAgainstTheirTargets() {
        assertEquals(
            "YFT solver canary: PASS · P hit same process 1.5 s ✅ (≤ 2 s) · after restart 6.0 s ✅ (≤ 8 s)",
            SolverCanaryReport.firstLine(reportOf()),
        )
        val onTarget = reportOf(
            builds = listOf(buildOf(SolverCanaryReport.PHONE, p2Millis = 2_000)),
            lookup = lookupOf(workerP = summaryOf(Step.WORKER_P, totalMillis = 8_000)),
        )
        assertEquals(
            "YFT solver canary: PASS · P hit same process 2.0 s ✅ (≤ 2 s) · after restart 8.0 s ✅ (≤ 8 s)",
            SolverCanaryReport.firstLine(onTarget),
        )
        // Main has no target: only the phone build's P2 counts for the first line.
        val slow = reportOf(
            builds = listOf(buildOf(SolverCanaryReport.PHONE, p2Millis = 2_100), buildOf(SolverCanaryReport.MAIN, p2Millis = 9_000)),
            lookup = lookupOf(workerP = summaryOf(Step.WORKER_P, totalMillis = 8_100)),
        )
        assertEquals(
            "YFT solver canary: PASS · P hit same process 2.1 s ❌ (≤ 2 s) · after restart 8.1 s ❌ (≤ 8 s)",
            SolverCanaryReport.firstLine(slow),
        )
    }

    @Test
    fun firstLineSaysWhyATimeIsMissing() {
        fun afterRestart(report: Report) = SolverCanaryReport.firstLine(report).substringAfter(" · after restart ")
        fun sameProcess(report: Report) =
            SolverCanaryReport.firstLine(report).substringAfter(" · P hit same process ").substringBefore(" · after restart ")

        assertEquals("— (same process) (≤ 8 s)", afterRestart(reportOf(newProcess = false)))
        assertEquals(
            "— (no parts yet) (≤ 8 s)",
            afterRestart(reportOf(lookup = lookupOf(workerP = summaryOf(Step.WORKER_P, sent = emptySet())))),
        )
        assertEquals(
            "— ❌ (no hit) (≤ 8 s)",
            afterRestart(reportOf(lookup = lookupOf(workerP = summaryOf(Step.WORKER_P, hits = setOf(N))))),
        )
        assertEquals(
            "— ❌ (${SolverCanaryReport.safe(OwnSolverProbe.JOB_TIMEOUT)}) (≤ 8 s)",
            afterRestart(reportOf(lookup = lookupOf(workerP = summaryOf(Step.WORKER_P, outcome = OwnSolverProbe.JOB_TIMEOUT)))),
        )
        assertEquals("— (not measured) (≤ 8 s)", afterRestart(reportOf(lookup = null)))

        assertEquals("— (not measured) (≤ 2 s)", sameProcess(reportOf(builds = listOf(buildOf(SolverCanaryReport.MAIN)))))
        val noParts = buildOf(SolverCanaryReport.PHONE).copy(p2 = lineOf("P2", summaryOf(Step.WORKER_P, sent = emptySet())))
        assertEquals("— (no parts) (≤ 2 s)", sameProcess(reportOf(builds = listOf(noParts))))

        val stopped = SolverCanaryReport.firstLine(reportOf(stoppedAt = "phone page P (job-timeout)"))
        assertTrue(stopped, stopped.startsWith("YFT solver canary: FAIL · "))
        assertTrue(stopped, stopped.endsWith(" · stopped at phone page P (job-timeout)"))
    }

    @Test
    fun reportHoldsNoInputNoValueAndNoAddress() {
        val inputs = listOf("inputAq7Zx0w9", "inputBq7Zx0w9")
        val values = mapOf(inputs[0] to "valueCq7Zx0w9", inputs[1] to "valueDq7Zx0w9")
        val judged = SolverCanaryReport.judge(inputs, values, values)
        val failing = summaryOf(
            Step.WORKER_B,
            failures = listOf("TypeError", "IOException", values.getValue(inputs[0]), "a/b " + values.getValue(inputs[1])),
        )
        val phone = buildOf(SolverCanaryReport.PHONE).let {
            it.copy(
                b1 = it.b1?.copy(counts = mapOf(N to judged, SIG to judged)),
                b2 = lineOf("B2", failing, n = judged, sig = judged),
            )
        }
        val report = reportOf(builds = listOf(phone, buildOf(SolverCanaryReport.MAIN))).copy(
            breakerOff = listOf("page B"),
            signaturesOff = listOf("P"),
        )
        val text = SolverCanaryReport.render(report)

        assertEquals(SolverCanaryReport.firstLine(report), text.lines().first())
        assertFalse(text, text.contains("://"))
        assertFalse(text, text.contains("/b"))
        (inputs + values.values).forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text, text.contains("failures TypeError,IOException,<cut>,ab <cut>"))
        assertTrue(text, text.contains(" · n 2/2 · sig 2/2\n"))
        assertTrue(text, text.contains("sent n,sig · hits n,sig · given n,sig · answered n,sig"))
        assertTrue(text, text.contains("\nrun 1 · process new · Android 14 · WebView 125.0.6422.165 · app 0.1.0-debug · JS heap limit unknown\n"))
        assertTrue(text, text.contains("\nplayer fakeplayer · lookup player same\n"))
        assertTrue(text, text.contains("\nverdicts: P n pass sig pass · B n pass sig pass · E n pass sig pass · V pass\n"))
        assertTrue(
            text,
            text.contains("\nhits: P2 phone 1.5 s, main 1.5 s · B2 phone 1.5 s, main 1.5 s · lookup P 6.0 s (process new)\n"),
        )
        assertTrue(text, text.contains("\nbreaker off: page B · sig off: P\n"))
        assertTrue(text, text.contains("\ndata: app 2.5 MB · HTTP 2.4 MB in 12 calls, 3 from the tool's cache\n"))
        assertTrue(text, text.endsWith("\ntotal 42.0 s"))
    }

    @Test
    fun reportShowsUnjudgedBuildsLookupPartsAndStalls() {
        val unjudged = Build(name = SolverCanaryReport.MAIN, download = Part("download", 1_200, 300), failure = "download NETWORK")
        val text = SolverCanaryReport.render(
            reportOf(builds = listOf(buildOf(SolverCanaryReport.PHONE), unjudged), lookup = lookupOf(), newProcess = false),
        )
        assertTrue(text, text.contains("\nmain: download 1.2 s 300 B · not judged (download NETWORK)\n"))
        assertTrue(text, text.contains("\nphone: download 0.9 s 1.2 MB · ejs 2.0 s (n 4/4 sig 4/4)\n"))
        assertTrue(text, text.contains("\nlookup (process same): rows · 9.0 s · page 0.7 s 878 KB · player ×2 0.5 s 0 B (cache)\n"))
        assertTrue(text, text.contains(" · stall 0.4 s · n 4/4 · sig 4/4\n"))
        assertTrue(text, text.contains("\nbreaker off: none · sig off: none\n"))
        assertTrue(text, text.contains("lookup P 6.0 s (process same)"))
        assertTrue(SolverCanaryReport.render(reportOf(lookup = null)).contains(" · V ${VStatus.NOT_RUN.label}\n"))
    }

    @Test
    fun timesAndSizesReadPlainly() {
        assertEquals("1.5 s", SolverCanaryReport.seconds(1_500))
        assertEquals("0.0 s", SolverCanaryReport.seconds(0))
        assertEquals("512 B", SolverCanaryReport.bytes(512))
        assertEquals("2 KB", SolverCanaryReport.bytes(2_048))
        assertEquals("2.5 MB", SolverCanaryReport.bytes(2_621_440))
    }

    @Test
    fun safeKeepsWordsAndVersionsAndCutsValueShapedTokens() {
        assertEquals("SyntaxError", SolverCanaryReport.safe("SyntaxError"))
        assertEquals("SSLHandshakeException", SolverCanaryReport.safe("SSLHandshakeException"))
        assertEquals("READY_TIMEOUT", SolverCanaryReport.safe("READY_TIMEOUT"))
        assertEquals("reply-unreadable", SolverCanaryReport.safe("reply-unreadable"))
        assertEquals("125.0.6422.165", SolverCanaryReport.safe("125.0.6422.165"))
        assertEquals("short aB3dE5", SolverCanaryReport.safe("short aB3dE5"))
        assertEquals("<cut>", SolverCanaryReport.safe("aB3dE5gH7jK9"))
        assertEquals("<cut>", SolverCanaryReport.safe("1700000000"))
        assertEquals("<cut> (x)", SolverCanaryReport.safe("aB3dE5gH7jK9 (x)"))
        assertEquals("ab", SolverCanaryReport.safe("a/b"))
        assertEquals("a b", SolverCanaryReport.safe("a?=&% b"))
        assertEquals(12, SolverCanaryReport.safe("abcdefghijklmnopqrstuvwxyz", 12).length)
    }

    private fun summaryOf(
        step: Step,
        outcome: String = OwnSolverProbe.REPLY,
        sent: Set<PlayerScriptChallengeKind> = BOTH,
        hits: Set<PlayerScriptChallengeKind> = sent,
        answered: Set<PlayerScriptChallengeKind> = BOTH,
        failures: List<String> = emptyList(),
        totalMillis: Long = 1_500,
    ) = OwnSolverProbe.Summary(
        step = step,
        outcome = outcome,
        strategyB = step == Step.WORKER_B || step == Step.PAGE_B,
        located = step == Step.WORKER_P || step == Step.WORKER_B,
        partsSent = sent,
        locatorHits = hits,
        partsGiven = BOTH,
        answered = answered,
        failures = failures,
        readyMillis = 300,
        totalMillis = totalMillis,
    )

    private fun lineOf(
        name: String,
        summary: OwnSolverProbe.Summary,
        n: Counts = pass,
        sig: Counts = pass,
        stallMillis: Long? = null,
    ) = StepLine(name, summary, mapOf(N to n, SIG to sig), stallMillis)

    private fun buildOf(name: String, p2Millis: Long = 1_500) = Build(
        name = name,
        download = Part("download", 900, 1_300_000),
        ejsMillis = 2_000,
        ejsSolved = mapOf(N to 4, SIG to 4),
        total = mapOf(N to 4, SIG to 4),
        p1 = lineOf("P1", summaryOf(Step.WORKER_P, sent = emptySet(), totalMillis = 3_000)),
        p2 = lineOf("P2", summaryOf(Step.WORKER_P, totalMillis = p2Millis)),
        b1 = lineOf("B1", summaryOf(Step.WORKER_B, sent = emptySet(), totalMillis = 3_000)),
        b2 = lineOf("B2", summaryOf(Step.WORKER_B)),
        pageP = lineOf("page P", summaryOf(Step.PAGE_P, sent = emptySet()), stallMillis = 400),
    )

    private fun lookupOf(
        workerP: OwnSolverProbe.Summary? = summaryOf(Step.WORKER_P, totalMillis = 6_000),
        v: VStatus = VStatus.PASS,
    ) = Lookup(
        outcome = "rows",
        totalMillis = 9_000,
        parts = listOf(Part("page", 700, 900_000), Part("player", 300, 0, cached = true), Part("player", 200, 0, cached = true)),
        workerP = workerP,
        v = v,
        jobs = listOfNotNull(workerP),
        failures = emptyList(),
        samePlayer = true,
    )

    @Test
    fun runLineShowsThePhoneJsHeapLimit() {
        val report = reportOf()
        val unknown = SolverCanaryReport.render(report).lines()[1]
        assertTrue(unknown, unknown.startsWith("run 1 · process new · "))
        assertTrue(unknown, unknown.endsWith(" · JS heap limit unknown"))

        val known = SolverCanaryReport.render(report.copy(header = report.header.copy(jsHeapLimitBytes = 4_294_705_152)))
        assertTrue(known, known.lines()[1].endsWith(" · app 0.1.0-debug · JS heap limit 4096 MB"))

        val none = SolverCanaryReport.render(report.copy(header = report.header.copy(jsHeapLimitBytes = 0)))
        assertTrue(none, none.lines()[1].endsWith(" · JS heap limit unknown"))
    }

    private fun reportOf(
        builds: List<Build> = listOf(buildOf(SolverCanaryReport.PHONE), buildOf(SolverCanaryReport.MAIN)),
        lookup: Lookup? = lookupOf(),
        newProcess: Boolean = true,
        stoppedAt: String? = null,
    ) = Report(
        header = Header(
            run = 1,
            newProcess = newProcess,
            android = "14",
            webView = "125.0.6422.165",
            app = "0.1.0-debug",
            player = "fakeplayer",
        ),
        lookup = lookup,
        builds = builds,
        stoppedAt = stoppedAt,
        breakerOff = emptyList(),
        signaturesOff = emptyList(),
        data = Data(appBytes = 2_621_440, httpBytes = 2_516_582, calls = 12, cacheHits = 3),
        totalMillis = 42_000,
    )

    private companion object {
        val N = PlayerScriptChallengeKind.RATE_PARAM
        val SIG = PlayerScriptChallengeKind.SIGNATURE
        val BOTH = setOf(N, SIG)
    }
}
