package com.alal.yft.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YftFormatTest {
    @Test
    fun sizesUseOneDecimalOnlyBelowTen() {
        assertEquals("512 B", YftFormat.bytes(512))
        assertEquals("1.0 KB", YftFormat.bytes(1_024))
        assertEquals("7.4 MB", YftFormat.bytes(7_759_462))
        assertEquals("96 MB", YftFormat.bytes(96L * 1_024 * 1_024))
        assertEquals("1.2 GB", YftFormat.bytes(1_288_490_189))
    }

    @Test
    fun durationsSwitchToHoursOnlyWhenNeeded() {
        assertEquals("0:00", YftFormat.duration(-5))
        assertEquals("0:07", YftFormat.duration(6_600))
        assertEquals("4:12", YftFormat.duration(252_000))
        assertEquals("1:02:03", YftFormat.duration(3_723_000))
    }

    @Test
    fun formatComesFromTheNameOrElseTheMimeType() {
        assertEquals("MP4", YftFormat.format("Sunset.mp4", "video/mp4"))
        assertEquals("M4A", YftFormat.format("Talk.m4a", null))
        assertEquals("WEBM", YftFormat.format("clip", "video/webm"))
        assertNull(YftFormat.format("clip", "application/vnd.something-long"))
        assertNull(YftFormat.format("clip", null))
    }

    @Test
    fun titlesDropTheExtensionButNeverBecomeBlank() {
        assertEquals("Sunset timelapse", YftFormat.title("Sunset timelapse.mp4"))
        assertEquals("v1.2 release notes", YftFormat.title("v1.2 release notes"))
        assertEquals(".mp4", YftFormat.title(".mp4"))
        assertEquals("archive.tar", YftFormat.title("archive.tar.gz"))
    }
}
