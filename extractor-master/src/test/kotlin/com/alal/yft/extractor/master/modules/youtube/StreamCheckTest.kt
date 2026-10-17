package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.StreamCheck.Kind
import com.alal.yft.extractor.master.modules.youtube.StreamCheck.LookupState
import com.alal.yft.extractor.master.modules.youtube.StreamCheck.NRule
import com.alal.yft.extractor.master.modules.youtube.StreamCheck.Reason
import com.alal.yft.extractor.master.modules.youtube.StreamCheck.StatusClass
import com.alal.yft.extractor.master.modules.youtube.StreamCheck.Verdict
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.2 N2: live check V against a fake HTTP client; no live calls. */
class StreamCheckTest {
    @Test
    fun `a stream that opens passes the signature and leaves n unknown until measured`() = runTest {
        val http = client { _, _ -> ok() }
        val result = StreamCheck(http).check(request())

        assertEquals(Verdict.PASS, result.verdict)
        assertEquals(Reason.CHECKED, result.reason)
        assertEquals(1, result.requests)
        assertEquals(listOf(TARGET), http.probedUrls)
        assertEquals(listOf(ExtractorHttpClient.DEFAULT_PROBE_TIMEOUT_MILLIS), http.probeTimeouts)
        assertEquals(Verdict.PASS, result.verdictFor(Kind.SIG))
        assertEquals(Verdict.UNKNOWN, result.verdictFor(Kind.N))
        assertFalse(result.marksFailed(Kind.SIG))
    }

    @Test
    fun `a 403 fails the strategy only when the control opens`() = runTest {
        val http = client { url, _ -> if (url == TARGET) refused(403) else ok() }
        val result = StreamCheck(http).check(request())

        assertEquals(Verdict.FAIL, result.verdict)
        assertEquals(Reason.CHECKED, result.reason)
        assertTrue(result.strategyFailed)
        assertEquals(2, result.requests)
        assertEquals(listOf(TARGET, CONTROL), http.probedUrls)
        assertEquals(StatusClass.FORBIDDEN_403, result.target)
        assertEquals(StatusClass.OK_2XX, result.control)
        assertTrue(result.marksFailed(Kind.SIG))
        // Until the phone measurement, a refusal still fails n: dropping is safe, passing is not.
        assertEquals(Verdict.FAIL, result.verdictFor(Kind.N))
    }

    @Test
    fun `a 403 without a control fails this lookup but not the strategy`() = runTest {
        val http = client { _, _ -> refused(403) }
        val check = StreamCheck(http)
        val result = check.check(request(control = null))

        assertEquals(Verdict.FAIL, result.verdict)
        assertEquals(Reason.NO_CONTROL, result.reason)
        assertFalse(result.strategyFailed)
        assertFalse(result.marksFailed(Kind.SIG))
        assertEquals(1, result.requests)
        // Not remembered: the next lookup may check again.
        check.check(request(control = null))
        assertEquals(2, http.probedUrls.size)
    }

    @Test
    fun `a control that fails too makes the verdict unknown`() = runTest {
        for (control in listOf(refused(403), refused(503), refused(null))) {
            val http = client { url, _ -> if (url == TARGET) refused(403) else control }
            val result = StreamCheck(http).check(request())

            assertEquals(Verdict.UNKNOWN, result.verdict)
            assertEquals(Reason.CONTROL_FAILED, result.reason)
            assertFalse(result.strategyFailed)
            assertEquals(2, result.requests)
            assertEquals(Verdict.UNKNOWN, result.verdictFor(Kind.SIG))
        }
    }

    @Test
    fun `a timeout or a network error is unknown and never pass`() = runTest {
        val timedOut = client { _, _ ->
            ExtractorProbeResult.Refused(SiteExtractionFailure.NETWORK, error = "SocketTimeoutException")
        }
        val first = StreamCheck(timedOut).check(request())
        assertEquals(Verdict.UNKNOWN, first.verdict)
        assertEquals(Reason.NETWORK, first.reason)
        assertEquals(StatusClass.NETWORK, first.target)
        assertEquals(listOf(TARGET), timedOut.probedUrls)

        // A client that never answers is cut off after the check's own time limit.
        val hanging = HangingClient()
        val second = StreamCheck(hanging).check(request())
        assertEquals(Verdict.UNKNOWN, second.verdict)
        assertEquals(Reason.NETWORK, second.reason)
        assertEquals(1, hanging.calls)

        val thrown = StreamCheck(HangingClient(throwInstead = true)).check(request())
        assertEquals(Verdict.UNKNOWN, thrown.verdict)
        assertEquals(Reason.NETWORK, thrown.reason)
    }

    @Test
    fun `other statuses and a client without file checks are unknown`() = runTest {
        for ((status, statusClass) in listOf(
            404 to StatusClass.OTHER_4XX,
            410 to StatusClass.OTHER_4XX,
            429 to StatusClass.OTHER_4XX,
            500 to StatusClass.SERVER_5XX,
            503 to StatusClass.SERVER_5XX,
            304 to StatusClass.OTHER_STATUS,
        )) {
            val http = client { _, _ -> refused(status) }
            val result = StreamCheck(http).check(request())
            assertEquals(Verdict.UNKNOWN, result.verdict)
            assertEquals(Reason.OTHER_STATUS, result.reason)
            assertEquals(statusClass, result.target)
            assertEquals(listOf(TARGET), http.probedUrls)
        }

        val unsupported = FakeExtractorHttpClient()
        val result = StreamCheck(unsupported).check(request())
        assertEquals(Verdict.UNKNOWN, result.verdict)
        assertEquals(Reason.UNSUPPORTED, result.reason)
        assertEquals(0, result.requests)
    }

    @Test
    fun `at most four checks per player version per day`() = runTest {
        var now = DAY_START
        val http = client { _, _ -> refused(404) }
        val check = StreamCheck(http, clock = { now })

        repeat(4) { index ->
            assertEquals(Reason.OTHER_STATUS, check.check(request(cacheKey = "answer-$index")).reason)
        }
        val spent = check.check(request(cacheKey = "answer-4"))
        assertEquals(Verdict.UNKNOWN, spent.verdict)
        assertEquals(Reason.BUDGET_SPENT, spent.reason)
        assertEquals(0, spent.requests)
        assertEquals(4, http.probedUrls.size)

        // Another player version has its own budget.
        assertEquals(Reason.OTHER_STATUS, check.check(request(budgetKey = "player-b")).reason)
        assertEquals(5, http.probedUrls.size)

        // The next UTC day starts a new budget.
        now = DAY_START + StreamCheck.DAY_MILLIS
        assertEquals(Reason.OTHER_STATUS, check.check(request(cacheKey = "answer-5")).reason)
        assertEquals(6, http.probedUrls.size)

        assertThrows(IllegalArgumentException::class.java) {
            StreamCheck.Policy(maxChecksPerKeyPerDay = 5)
        }
    }

    @Test
    fun `proven verdicts are reused per player version and answer without a request`() = runTest {
        var now = DAY_START
        val http = client { _, _ -> ok() }
        val check = StreamCheck(http, clock = { now })

        assertEquals(Verdict.PASS, check.check(request()).verdict)
        val again = check.check(request())
        assertEquals(Verdict.PASS, again.verdict)
        assertEquals(Reason.CACHED, again.reason)
        assertTrue(again.cached)
        assertEquals(0, again.requests)
        assertEquals(1, http.probedUrls.size)

        // A different answer for the same player version is checked on its own.
        check.check(request(cacheKey = "other-answer"))
        assertEquals(2, http.probedUrls.size)

        // A cached pass does not outlive the cache time.
        now = DAY_START + StreamCheck.DAY_MILLIS
        assertEquals(Reason.CHECKED, check.check(request()).reason)
        assertEquals(3, http.probedUrls.size)

        // A proven fail is reused too.
        val failing = client { url, _ -> if (url == TARGET) refused(403) else ok() }
        val failCheck = StreamCheck(failing, clock = { now })
        failCheck.check(request())
        val failAgain = failCheck.check(request())
        assertEquals(Verdict.FAIL, failAgain.verdict)
        assertTrue(failAgain.cached)
        assertTrue(failAgain.marksFailed(Kind.SIG))
        assertEquals(2, failing.probedUrls.size)
    }

    @Test
    fun `no request outside a user-started lookup or on a terminal state`() = runTest {
        val http = client { _, _ -> ok() }
        val check = StreamCheck(http)

        assertEquals(Reason.NOT_USER_STARTED, check.check(request(userStarted = false)).reason)
        for (state in LookupState.entries - LookupState.OK) {
            val result = check.check(request(state = state))
            assertEquals(Verdict.UNKNOWN, result.verdict)
            assertEquals(Reason.TERMINAL_STATE, result.reason)
            assertEquals(0, result.requests)
        }
        assertEquals(Reason.NOTHING_TO_JUDGE, check.check(request(kinds = emptySet())).reason)
        assertTrue(http.probedUrls.isEmpty())
    }

    @Test
    fun `only the stream host YouTube returned is asked`() = runTest {
        val http = client { _, _ -> ok() }
        val check = StreamCheck(http)

        val notReturned = listOf(
            request(hosts = setOf("rr5---sn-other.googlevideo.com")),
            request(target = "https://example.com/videoplayback?sig=SOLVEDSIG"),
            request(target = "http://$HOST/videoplayback?sig=SOLVEDSIG", hosts = setOf(HOST)),
            request(target = "https://$HOST/other?sig=SOLVEDSIG"),
            request(target = "https://user@$HOST/videoplayback?sig=SOLVEDSIG"),
        )
        for (candidate in notReturned) {
            val result = check.check(candidate)
            assertEquals(Verdict.UNKNOWN, result.verdict)
            assertEquals(Reason.HOST_NOT_RETURNED, result.reason)
        }
        assertTrue(http.probedUrls.isEmpty())

        // A control on a host YouTube did not return is ignored, never asked.
        val refusing = client { _, _ -> refused(403) }
        val result = StreamCheck(refusing).check(
            request(control = "https://rr9---sn-foreign.googlevideo.com/videoplayback?itag=18"),
        )
        assertEquals(Reason.NO_CONTROL, result.reason)
        assertEquals(listOf(TARGET), refusing.probedUrls)
    }

    @Test
    fun `the download headers go along without cookies or credentials`() = runTest {
        val http = client { url, _ -> if (url == TARGET) refused(403) else ok() }
        StreamCheck(http).check(
            request(
                headers = mapOf(
                    "User-Agent" to "UA",
                    "Origin" to "https://www.youtube.com",
                    "Cookie" to "SID=secret",
                    "cookie2" to "x=1",
                    "Authorization" to "Bearer secret",
                    "Proxy-Authorization" to "Basic secret",
                    "X-Goog-AuthUser" to "0",
                    "Range" to "bytes=0-",
                ),
            ),
        )

        val expected = mapOf("User-Agent" to "UA", "Origin" to "https://www.youtube.com")
        assertEquals(listOf(expected, expected), http.probedHeaders)
    }

    @Test
    fun `the n rule from the phone measurement decides what a 2xx says about n`() = runTest {
        val passing = client { _, _ -> ok() }
        val judges = StreamCheck(passing, StreamCheck.Policy(nRule = NRule.JUDGES_N)).check(request())
        assertEquals(Verdict.PASS, judges.verdictFor(Kind.N))

        val sigOnly = StreamCheck(passing, StreamCheck.Policy(nRule = NRule.SIG_ONLY)).check(request())
        assertEquals(Verdict.PASS, sigOnly.verdictFor(Kind.SIG))
        assertEquals(Verdict.UNKNOWN, sigOnly.verdictFor(Kind.N))

        val refusing = client { url, _ -> if (url == TARGET) refused(403) else ok() }
        val sigOnlyFail =
            StreamCheck(refusing, StreamCheck.Policy(nRule = NRule.SIG_ONLY)).check(request())
        assertEquals(Verdict.FAIL, sigOnlyFail.verdictFor(Kind.SIG))
        assertEquals(Verdict.UNKNOWN, sigOnlyFail.verdictFor(Kind.N))
        assertFalse(sigOnlyFail.marksFailed(Kind.N))

        // A kind the URL does not carry is never judged.
        val nOnly = StreamCheck(passing, StreamCheck.Policy(nRule = NRule.JUDGES_N))
            .check(request(kinds = setOf(Kind.N)))
        assertEquals(Verdict.UNKNOWN, nOnly.verdictFor(Kind.SIG))
    }

    @Test
    fun `nothing is logged and no result shows an address or a value`() = runTest {
        val records = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }
        val root = Logger.getLogger("")
        val out = System.out
        val err = System.err
        val printed = ByteArrayOutputStream()
        root.addHandler(handler)
        System.setOut(PrintStream(printed, true))
        System.setErr(PrintStream(printed, true))
        val shown = try {
            val outcomes = listOf(
                client { _, _ -> ok() },
                client { url, _ -> if (url == TARGET) refused(403) else ok() },
                client { _, _ -> refused(403) },
                client { _, _ -> refused(null) },
            ).map { http ->
                val req = request(headers = mapOf("Cookie" to "SID=secret", "User-Agent" to "UA"))
                req.toString() + StreamCheck(http).check(req).toString()
            }
            outcomes.joinToString("\n")
        } finally {
            System.setOut(out)
            System.setErr(err)
            root.removeHandler(handler)
        }

        assertTrue(records.isEmpty())
        assertEquals("", printed.toString())
        for (secret in listOf("googlevideo", "videoplayback", "SOLVED", "secret", "UA", "player-a", "answer")) {
            assertFalse("result shows $secret", shown.contains(secret))
        }
    }

    private fun request(
        target: String = TARGET,
        control: String? = CONTROL,
        hosts: Set<String> = setOf(HOST),
        headers: Map<String, String> = mapOf("User-Agent" to "UA"),
        kinds: Set<Kind> = setOf(Kind.SIG, Kind.N),
        budgetKey: String = "player-a",
        cacheKey: String = "player-a|P|answer",
        userStarted: Boolean = true,
        state: LookupState = LookupState.OK,
    ) = StreamCheck.Request(
        target = target,
        control = control,
        returnedHosts = hosts,
        headers = headers,
        kinds = kinds,
        budgetKey = budgetKey,
        cacheKey = cacheKey,
        userStarted = userStarted,
        state = state,
    )

    private fun client(
        answer: (url: String, headers: Map<String, String>) -> ExtractorProbeResult,
    ) = FakeExtractorHttpClient(probeResponder = answer)

    private fun ok() = ExtractorProbeResult.Answered(206, 1_000_000L)

    private fun refused(status: Int?) = ExtractorProbeResult.Refused(
        if (status == null) SiteExtractionFailure.NETWORK else SiteExtractionFailure.HTTP_STATUS,
        status,
        if (status == null) "ConnectException" else null,
    )

    /** Never answers in time, or throws, like a stuck socket. */
    private class HangingClient(private val throwInstead: Boolean = false) : ExtractorHttpClient {
        var calls = 0

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long) =
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ) = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        override suspend fun probe(
            url: String,
            headers: Map<String, String>,
            timeoutMillis: Long,
        ): ExtractorProbeResult {
            calls += 1
            if (throwInstead) throw IllegalStateException("socket")
            delay(60_000)
            return ExtractorProbeResult.Answered(206)
        }
    }

    private companion object {
        const val HOST = "rr3---sn-abc.googlevideo.com"
        const val TARGET = "https://$HOST/videoplayback?expire=1&n=SOLVEDN&sig=SOLVEDSIG"
        const val CONTROL = "https://$HOST/videoplayback?expire=1&itag=18"
        const val DAY_START = 20_000L * 24 * 60 * 60 * 1000
    }
}
