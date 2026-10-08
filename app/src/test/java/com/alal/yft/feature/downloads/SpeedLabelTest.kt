package com.alal.yft.feature.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/** P34 (G8): one speed format on the cards and in the notification. */
class SpeedLabelTest {
    @Test
    fun wholeKilobytesUpTo1023ThenOneDecimal() {
        assertEquals("0 B/s", speedLabel(0))
        assertEquals("1023 B/s", speedLabel(1_023))
        assertEquals("1 KB/s", speedLabel(1_024))
        assertEquals("850 KB/s", speedLabel(870_400))
        assertEquals("1023 KB/s", speedLabel(1_048_575))
        assertEquals("1.0 MB/s", speedLabel(1_048_576))
        assertEquals("1.2 MB/s", speedLabel(1_258_291))
        assertEquals("12.3 MB/s", speedLabel(12_897_485))
        assertEquals("1.0 GB/s", speedLabel(1_073_741_824))
    }

    @Test
    fun timeLeftTexts() {
        assertEquals("15 s left", timeLeftLabel(15))
        assertEquals("1 min left", timeLeftLabel(60))
        assertEquals("3 min left", timeLeftLabel(150))
        assertEquals("1 h 5 min left", timeLeftLabel(3_900))
    }
}
