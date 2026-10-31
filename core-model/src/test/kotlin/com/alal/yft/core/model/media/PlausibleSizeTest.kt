package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlausibleSizeTest {
    private val replay = (1 * 3_600 + 47 * 60 + 11) * 1_000L

    @Test
    fun aSizeUnder1KbForEachSecondOfTheLengthIsUnknown() {
        // G1 (v1.0.2): the 1:47:11 YouTube M4A that once read "1 B"; its re-lookup read ~110 MB.
        assertNull(PlausibleSize.of(1, replay))
        assertNull(PlausibleSize.of(6_430_999, replay))
        assertEquals(6_431_000L, PlausibleSize.of(6_431_000, replay))
        assertEquals(110_000_000L, PlausibleSize.of(110_000_000, replay))
    }

    @Test
    fun aShortClipKeepsItsSmallSizeAndASizeWithoutALengthStays() {
        assertEquals(6_000L, PlausibleSize.of(6_000, 5_000))
        assertEquals(1_000L, PlausibleSize.of(1_000, 1_000))
        assertNull(PlausibleSize.of(999, 1_000))
        assertEquals(1L, PlausibleSize.of(1, null))
        assertEquals(1L, PlausibleSize.of(1, 0))
        assertNull(PlausibleSize.of(null, replay))
    }
}
