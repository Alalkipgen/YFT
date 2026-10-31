package com.alal.yft.core.download

import org.junit.Assert.assertEquals
import org.junit.Test

class Mp3TimesTest {
    @Test
    fun theDetailsLineNamesTheSoundsLengthAndTheDecodeAndEncodeTimes() {
        // G2 (v1.0.2): the owner's example line.
        assertEquals(
            "MP3: 4:12 of sound, decode 1.2 s, encode 6.8 s",
            Mp3Times.line(soundMillis = 252_400, decodeMillis = 1_200, encodeMillis = 6_800),
        )
        assertEquals(
            "MP3: 1:00:00 of sound, decode 40 ms, encode 2 min 5 s",
            Mp3Times.line(soundMillis = 3_600_000, decodeMillis = 40, encodeMillis = 125_000),
        )
    }

    @Test
    fun theLengthReadsAsOtherSitesLengthsDo() {
        assertEquals("0:08", Mp3Times.length(8_900))
        assertEquals("3:19", Mp3Times.length(199_000))
        assertEquals("1:02:05", Mp3Times.length(3_725_000))
        assertEquals("0:00", Mp3Times.length(-5))
    }
}
