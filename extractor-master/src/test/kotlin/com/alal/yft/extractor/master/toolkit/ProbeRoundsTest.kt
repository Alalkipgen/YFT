package com.alal.yft.extractor.master.toolkit

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** R3 T8: ProbeRounds. */
class ProbeRoundsTest {
    @Test
    fun `first addresses first, then the next address of refused qualities, within the cap`() = runTest {
        val order = mutableListOf<String>()
        val rounds = ProbeRounds(maxChecks = 4)
        val outcome = rounds.run(
            listOf(
                ProbeRounds.Quality(720, "720p", listOf("a1", "a2", "a3")),
                ProbeRounds.Quality(540, "540p", listOf("b1", "b2")),
                ProbeRounds.Quality(360, "360p", listOf("c1")),
            ),
        ) { address ->
            synchronized(order) { order += address }
            if (address == "b1" || address == "c1") {
                ProbeRounds.Answer.Answered(206, 10)
            } else {
                ProbeRounds.Answer.Refused("403")
            }
        }
        assertEquals(listOf("a1", "b1", "c1"), order.take(3).sorted())
        assertEquals("a2", order[3])
        assertEquals(0, rounds.checksLeft)
        assertNull(outcome[0].found)
        assertEquals(listOf("403", "403"), outcome[0].trail)
        assertEquals("b1", outcome[1].found)
        assertEquals(10L, outcome[2].totalBytes)
    }

    @Test
    fun `a reserve is kept, parallelism is bounded and unsupported stops a quality`() = runTest {
        val running = AtomicInteger()
        var peak = 0
        val rounds = ProbeRounds(maxChecks = 6, maxParallel = 2)
        val qualities = (1..5).map { ProbeRounds.Quality(it, "$it", listOf("q$it")) }
        rounds.run(qualities, reserve = 1) {
            val now = running.incrementAndGet()
            synchronized(this@ProbeRoundsTest) { peak = maxOf(peak, now) }
            delay(10)
            running.decrementAndGet()
            ProbeRounds.Answer.Answered(200, null)
        }
        assertTrue(peak <= 2)
        assertEquals(1, rounds.checksLeft)
        val unsupported = ProbeRounds().run(listOf(ProbeRounds.Quality(1, "x", listOf("u1", "u2")))) {
            ProbeRounds.Answer.Unsupported
        }.single()
        assertTrue(unsupported.unsupported)
        assertEquals(emptyList<String>(), unsupported.trail)
    }
}
