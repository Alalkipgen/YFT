package com.alal.yft.core.download

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5 (v1.0.2): the hand-off between the MP3 decoder's thread and LAME's. */
class PcmHandoffTest {
    @Test
    fun everySoundComesOutInOrderBeforeTheEndThroughASmallHandoff() {
        val handoff = PcmHandoff(capacity = 4)
        val producer = thread(name = "test-decoder") {
            for (index in 0 until SOUNDS) {
                val pcm = handoff.array(2).also { it[0] = index.toShort() }
                check(handoff.put(PcmItem.Sound(pcm, index, sampleRate = 44_100, channels = 2)))
            }
            check(handoff.put(PcmItem.End))
        }

        val counts = mutableListOf<Int>()
        while (true) {
            val item = checkNotNull(handoff.take(timeoutMs = 2_000)) {
                "nothing after ${counts.size}"
            }
            if (item == PcmItem.End) break
            val sound = item as PcmItem.Sound
            assertEquals(sound.count.toShort(), sound.pcm[0])
            counts += sound.count
            handoff.recycle(sound.pcm)
        }
        producer.join(2_000)

        assertEquals((0 until SOUNDS).toList(), counts)
        assertNull(handoff.take(timeoutMs = 10))
    }

    @Test
    fun aFullHandoffMakesTheDecoderWaitUntilLameTakesOne() {
        val handoff = PcmHandoff(capacity = 2)
        assertTrue(handoff.put(sound(1)))
        assertTrue(handoff.put(sound(2)))
        val handed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        thread(name = "test-decoder") {
            result.set(handoff.put(sound(3)))
            handed.countDown()
        }

        assertFalse("waits while full", handed.await(150, TimeUnit.MILLISECONDS))
        assertEquals(1, (handoff.take(timeoutMs = 100) as PcmItem.Sound).count)
        assertTrue("goes on once there is room", handed.await(2, TimeUnit.SECONDS))
        assertEquals(true, result.get())
        assertEquals(2, (handoff.take(timeoutMs = 100) as PcmItem.Sound).count)
        assertEquals(3, (handoff.take(timeoutMs = 100) as PcmItem.Sound).count)
    }

    @Test
    fun closingReleasesAWaitingDecoderAndRefusesEveryLaterSound() {
        val handoff = PcmHandoff(capacity = 1)
        assertTrue(handoff.put(sound(1)))
        val handed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        thread(name = "test-decoder") {
            result.set(handoff.put(sound(2)))
            handed.countDown()
        }
        assertFalse(handed.await(100, TimeUnit.MILLISECONDS))

        handoff.close()

        assertTrue("released by close", handed.await(2, TimeUnit.SECONDS))
        assertEquals(false, result.get())
        assertFalse(handoff.put(PcmItem.End))
        assertNull("nothing waits after close", handoff.take(timeoutMs = 10))
    }

    @Test
    fun takeGivesNothingWhenNoSoundComesInTime() {
        val handoff = PcmHandoff(capacity = 4)
        val started = System.nanoTime()

        assertNull(handoff.take(timeoutMs = 40))

        assertTrue(System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(30))
    }

    @Test
    fun anEncodedArrayIsUsedAgainWhenItIsLargeEnough() {
        val handoff = PcmHandoff(capacity = 4)
        val first = handoff.array(2_048)
        handoff.recycle(first)
        assertSame(first, handoff.array(1_024))

        handoff.recycle(first)
        val larger = handoff.array(4_096)
        assertNotSame(first, larger)
        assertEquals(4_096, larger.size)

        handoff.close()
        handoff.recycle(larger)
        assertNotSame(larger, handoff.array(16))
    }

    private fun sound(count: Int) = PcmItem.Sound(ShortArray(count), count, 44_100, 2)

    private companion object {
        const val SOUNDS = 2_000
    }
}
