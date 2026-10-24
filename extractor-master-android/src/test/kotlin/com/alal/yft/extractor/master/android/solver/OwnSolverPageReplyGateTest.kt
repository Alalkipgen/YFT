package com.alal.yft.extractor.master.android.solver

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnSolverPageReplyGateTest {
    private val origin = "https://appassets.androidplatform.net"

    @Test
    fun `the host page's first string reply ends the run and later ones are ignored`() {
        val gate = OwnSolverPageReplyGate()
        assertEquals(OwnSolverPageReplyGate.Verdict.Reply("{\"type\":\"result\"}"), gate.offer(origin, true, "{\"type\":\"result\"}"))
        assertEquals(OwnSolverPageReplyGate.Verdict.Ignored, gate.offer(origin, true, "{\"type\":\"result\",\"n\":{}}"))
    }

    @Test
    fun `replies from a sub frame or another origin are never accepted`() {
        val gate = OwnSolverPageReplyGate()
        listOf(
            Triple(origin, false, "{}"),
            Triple("null", true, "{}"),
            Triple(null, true, "{}"),
            Triple("https://www.youtube.com", true, "{}"),
            Triple("http://appassets.androidplatform.net", true, "{}"),
            Triple("https://appassets.androidplatform.net.evil.test", true, "{}"),
            Triple("https://appassets.androidplatform.net:8443", true, "{}"),
        ).forEach { (source, mainFrame, text) ->
            assertEquals("$source $mainFrame", OwnSolverPageReplyGate.Verdict.Ignored, gate.offer(source, mainFrame, text))
        }
        // Still open: the host page's reply is accepted afterwards (the origin may end in "/").
        assertEquals(OwnSolverPageReplyGate.Verdict.Reply("{}"), gate.offer("$origin/", true, "{}"))
    }

    @Test
    fun `a non-string or oversized reply from the host page fails the run`() {
        assertEquals(OwnSolverPageReplyGate.Verdict.Failed, OwnSolverPageReplyGate().offer(origin, true, null))
        val small = OwnSolverPageReplyGate(maxChars = 4)
        assertEquals(OwnSolverPageReplyGate.Verdict.Failed, small.offer(origin, true, "12345"))
        assertEquals(OwnSolverPageReplyGate.Verdict.Ignored, small.offer(origin, true, "1234"))
        assertEquals(OwnSolverPageReplyGate.Verdict.Reply("1234"), OwnSolverPageReplyGate(maxChars = 4).offer(origin, true, "1234"))
        assertEquals(12 * 1024 * 1024, OwnSolverPageReplyGate.MAX_REPLY_CHARS)
    }
}
