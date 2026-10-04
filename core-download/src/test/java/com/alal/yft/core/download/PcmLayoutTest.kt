package com.alal.yft.core.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PcmLayoutTest {
    @Test
    fun lameGetsMonoOrStereo() {
        assertEquals(1, PcmLayout.encoderChannels(1))
        assertEquals(2, PcmLayout.encoderChannels(2))
        assertEquals(2, PcmLayout.encoderChannels(6))
    }

    @Test
    fun surroundKeepsItsFrontLeftAndRight() {
        // Two frames of 5.1: L R C LFE Ls Rs.
        val pcm = shortArrayOf(1, 2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 16)

        val pair = PcmLayout.frontPair(pcm, frames = 2, channels = 6, reuse = ShortArray(0))

        assertArrayEquals(shortArrayOf(1, 2, 11, 12), pair)
        val reuse = ShortArray(8)
        assertSame(reuse, PcmLayout.frontPair(pcm, frames = 2, channels = 6, reuse = reuse))
    }
}
