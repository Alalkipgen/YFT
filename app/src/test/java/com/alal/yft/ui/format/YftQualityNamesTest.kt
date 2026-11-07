package com.alal.yft.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Download sheet's size for a file whose length the site does not give is its advertised
 * bitrates over the length ([YftQualityNames.estimatedBytes]): the video's, plus the sound's for
 * a merge. A download row's number comes from the pieces served instead, so the two differ on a
 * live that has ended.
 */
class YftQualityNamesTest {
    /** The owner's ended live: 419 kbps video and 144 kbps sound over 2:07:50. */
    @Test
    fun anEstimateIsTheBitratesOverTheLength() {
        assertEquals(401_716_250L, YftQualityNames.estimatedBytes(419_000L, 7_670_000L))
        assertEquals(138_060_000L, YftQualityNames.estimatedBytes(144_000L, 7_670_000L))
        // The sheet's row for the merge: both files, about 515 MB.
        assertEquals(
            539_776_250L,
            YftQualityNames.estimatedBytes(419_000L, 7_670_000L) +
                YftQualityNames.estimatedBytes(144_000L, 7_670_000L),
        )
        assertEquals("515 MB", YftFormat.bytes(539_776_250L))
    }

    @Test
    fun anEstimateNeedsBothTheBitrateAndTheLength() {
        assertNull(YftQualityNames.estimatedBytes(null, 7_670_000L))
        assertNull(YftQualityNames.estimatedBytes(419_000L, null))
        assertNull(YftQualityNames.estimatedBytes(0L, 7_670_000L))
        assertNull(YftQualityNames.estimatedBytes(419_000L, 0L))
    }
}
