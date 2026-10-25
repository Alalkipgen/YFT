package com.alal.yft.core.download

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8: the turns, waits and Detail of one whole file's 403 answers on YouTube's media hosts. */
class MediaHostRefusalsTest {
    private class Refused : Exception()

    private fun TestScope.refusals(
        limitMillis: Long = 10_000,
        firstWaitMillis: Long = 1_000,
        maxWaitMillis: Long = 4_000,
    ) = MediaHostRefusals(
        limitMillis = limitMillis,
        firstWaitMillis = firstWaitMillis,
        maxWaitMillis = maxWaitMillis,
        elapsedMillis = { testScheduler.currentTime },
        isRefusal = { it is Refused },
    )

    @Test
    fun `ranges are asked at once until the file is refused`() = runTest {
        val refusals = refusals()
        val now = AtomicInteger(0)
        val most = AtomicInteger(0)

        List(3) { index ->
            launch {
                refusals.turn(index * 1_000L) {
                    most.accumulateAndGet(now.incrementAndGet(), ::maxOf)
                    delay(500)
                    now.decrementAndGet()
                }
            }
        }.forEach { it.join() }

        assertEquals(3, most.get())
        assertEquals(500L, testScheduler.currentTime)
    }

    @Test
    fun `after a refusal ranges are asked one at a time and the next one waits`() = runTest {
        val refusals = refusals()
        val now = AtomicInteger(0)
        val most = AtomicInteger(0)
        val starts = Collections.synchronizedList(mutableListOf<Long>())
        val refused = runCatching { refusals.turn(MIB.toLong()) { throw Refused() } }

        List(2) {
            launch {
                refusals.turn(2L * MIB) {
                    starts += testScheduler.currentTime
                    most.accumulateAndGet(now.incrementAndGet(), ::maxOf)
                    delay(500)
                    now.decrementAndGet()
                }
            }
        }.forEach { it.join() }

        assertTrue(refused.exceptionOrNull() is Refused)
        assertTrue(refusals.mayAskAgain())
        assertEquals(1, most.get())
        // The first waits a second after the refusal; its answer ends the row, so the next
        // asks as soon as its turn comes.
        assertEquals(listOf(1_000L, 1_500L), starts.toList())
    }

    @Test
    fun `a range refused in a row waits twice as long each time and stops at the limit`() =
        runTest {
            val refusals = refusals()
            val asks = mutableListOf<Long>()

            while (true) {
                val refused = runCatching {
                    refusals.turn(MIB.toLong()) {
                        asks += testScheduler.currentTime
                        throw Refused()
                    }
                }
                assertTrue(refused.exceptionOrNull() is Refused)
                if (!refusals.mayAskAgain()) break
            }

            // Waits of 1, 2 and 4 s (the most); the next would start 11 s after the first.
            assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), asks)
            assertEquals("403×4 at 1 MiB, 1 at once, 7 s", refusals.describe())
        }

    @Test
    fun `an answer after the row began starts the next row from the first wait`() = runTest {
        val refusals = refusals()
        val asks = mutableListOf<Long>()
        suspend fun ask(refuse: Boolean) = runCatching {
            refusals.turn(0L) {
                asks += testScheduler.currentTime
                if (refuse) throw Refused()
            }
        }

        ask(refuse = true)
        ask(refuse = true)
        ask(refuse = false)
        ask(refuse = true)
        ask(refuse = false)

        // 0 and 1 s refused (the second wait would be 2 s), 3 s answered: the row is over, so
        // the refusal at 3 s waits the first second again.
        assertEquals(listOf(0L, 1_000L, 3_000L, 3_000L, 4_000L), asks)
        assertTrue(refusals.mayAskAgain())
        assertEquals("403×3 at 0 MiB, 1 at once, 3 s", refusals.describe())
    }

    @Test
    fun `the download's own times ask a refused range again once, 3 s after the refusal`() =
        runTest {
            val policy = DashTransferEngine.Policy()
            val refusals = refusals(
                limitMillis = policy.mediaHostRefusalWaitMillis,
                firstWaitMillis = policy.mediaHostRefusalFirstWaitMillis,
                maxWaitMillis = policy.mediaHostRefusalMaxWaitMillis,
            )
            val asks = Collections.synchronizedList(mutableListOf<Long>())

            val failed = runCatching {
                coroutineScope {
                    // Three ranges in flight are refused, as V3's were (D7 run 13: "3 at once").
                    List(3) { index ->
                        launch {
                            while (true) {
                                val refused = runCatching {
                                    refusals.turn((1L + 10 * index) * MIB) {
                                        asks += testScheduler.currentTime
                                        delay(100)
                                        throw Refused()
                                    }
                                }
                                assertTrue(refused.exceptionOrNull() is Refused)
                                if (!refusals.mayAskAgain()) {
                                    throw IllegalStateException(refusals.describe())
                                }
                            }
                        }
                    }
                }
            }

            // F8 trim (owner, 2026-10-19): one range is asked again alone 3 s after the last
            // refusal; refused again, the file fails (a second ask would wait 6 s more).
            assertEquals(listOf(0L, 0L, 0L, 3_100L), asks.toList())
            assertEquals("403×4 at 1 MiB, 3 at once, 3 s", failed.exceptionOrNull()?.message)
        }

    @Test
    fun `the download's own times go on once the range asked again is answered`() = runTest {
        val policy = DashTransferEngine.Policy()
        val refusals = refusals(
            limitMillis = policy.mediaHostRefusalWaitMillis,
            firstWaitMillis = policy.mediaHostRefusalFirstWaitMillis,
            maxWaitMillis = policy.mediaHostRefusalMaxWaitMillis,
        )
        val asks = mutableListOf<Long>()
        var refuse = true

        while (true) {
            val answer = runCatching {
                refusals.turn(MIB.toLong()) {
                    asks += testScheduler.currentTime
                    if (refuse) throw Refused()
                }
            }
            if (answer.isSuccess) break
            assertTrue(refusals.mayAskAgain())
            refuse = false
        }
        refusals.turn(2L * MIB) { asks += testScheduler.currentTime }

        // Asked again 3 s after the refusal and answered: the row is over, the next range asks
        // at once (still one at a time).
        assertEquals(listOf(0L, 3_000L, 3_000L), asks)
    }

    @Test
    fun `other failures do not make the file wait`() = runTest {
        val refusals = refusals()

        val failed = runCatching { refusals.turn(0L) { throw IllegalStateException() } }
        refusals.turn(MIB.toLong()) { delay(100) }

        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun `a file refused many times stops whatever the time`() = runTest {
        val refusals = refusals(limitMillis = Long.MAX_VALUE / 4, maxWaitMillis = 1_000)
        var asked = 0

        while (true) {
            runCatching { refusals.turn(0L) { asked += 1; throw Refused() } }
            if (!refusals.mayAskAgain()) break
        }

        assertEquals(MediaHostRefusals.MAX_REFUSALS, asked)
        assertFalse(refusals.mayAskAgain())
    }
}
