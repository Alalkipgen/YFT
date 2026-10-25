package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.master.solver.OwnSolverBreaker.End
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverBreakerTest {
    @Test
    fun `a worker timeout turns only that step off, for every player`() {
        val breaker = OwnSolverBreaker()
        breaker.begin("a", Step.WORKER_P)
        breaker.end("a", Step.WORKER_P, End.TIMEOUT)
        assertFalse(breaker.allows("a", Step.WORKER_P))
        assertFalse(breaker.allows("b", Step.WORKER_P))
        assertTrue(breaker.allows("a", Step.WORKER_B))
        assertTrue(breaker.allows("a", Step.PAGE_P))
    }

    @Test
    fun `a gone page renderer turns both page steps off, the workers stay`() {
        val breaker = OwnSolverBreaker()
        breaker.begin("a", Step.PAGE_B)
        breaker.end("a", Step.PAGE_B, End.GONE)
        assertFalse(breaker.allows("a", Step.PAGE_P))
        assertFalse(breaker.allows("a", Step.PAGE_B))
        assertTrue(breaker.allows("a", Step.WORKER_P))
        assertTrue(breaker.allows("a", Step.WORKER_B))
    }

    @Test
    fun `a finished job clears its marker, a death leaves it, two deaths in a row keep that player's step off`() {
        val markers = OwnSolverBreaker.MemoryMarkers()
        val key = OwnSolverBreaker.key("a", Step.WORKER_B)
        assertEquals("a|WORKER_B", key)

        OwnSolverBreaker(markers).run {
            begin("a", Step.WORKER_B)
            assertEquals(1, markers.count(key))
            end("a", Step.WORKER_B, End.DONE)
        }
        assertEquals(0, markers.count(key))

        // The process dies during the job: the next process sees one strike and tries once more.
        OwnSolverBreaker(markers).begin("a", Step.WORKER_B)
        val second = OwnSolverBreaker(markers)
        assertTrue(second.allows("a", Step.WORKER_B))
        second.begin("a", Step.WORKER_B)
        // It dies again: off for this player version only.
        val third = OwnSolverBreaker(markers)
        assertFalse(third.allows("a", Step.WORKER_B))
        assertTrue(third.allows("b", Step.WORKER_B))
        assertTrue(third.allows("a", Step.WORKER_P))
    }

    @Test
    fun `a failed signature turns off that player's step signatures only`() {
        val breaker = OwnSolverBreaker()
        breaker.signatureFailed("a", Step.WORKER_P)
        assertFalse(breaker.signaturesAllowed("a", Step.WORKER_P))
        assertTrue(breaker.signaturesAllowed("b", Step.WORKER_P))
        assertTrue(breaker.signaturesAllowed("a", Step.WORKER_B))
        assertTrue(breaker.allows("a", Step.WORKER_P))
    }
}
