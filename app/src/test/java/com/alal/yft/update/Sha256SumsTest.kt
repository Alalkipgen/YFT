package com.alal.yft.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Sha256SumsTest {
    private val apk = "video-downloader-1.0.1.apk"
    private val lower = "5f0c6a0a0d4c7a3b2e9f1d8c7b6a5f4e3d2c1b0a9f8e7d6c5b4a3f2e1d0c9b8a"
    private val upper = lower.uppercase()

    @Test
    fun sha256sumsLinesInEitherModeAndCase() {
        assertEquals(upper, Sha256Sums.find("$lower  $apk\n", apk))
        assertEquals(upper, Sha256Sums.find("$upper *$apk", apk))
        assertEquals(upper, Sha256Sums.find("$lower  $apk\r\n", apk))
    }

    @Test
    fun onlyTheApksOwnLineCounts() {
        val other = "1".repeat(64)
        val text = "$other  release-info.txt\n$lower  $apk\n$other  video-downloader-1.0.0.apk\n"

        assertEquals(upper, Sha256Sums.find(text, apk))
        assertNull(Sha256Sums.find("$other  release-info.txt\n", apk))
        assertNull(Sha256Sums.find("$lower  dist/$apk\n", apk))
        assertNull(Sha256Sums.find("$lower  $apk.part\n", apk))
        assertNull(Sha256Sums.find("", apk))
    }

    @Test
    fun twoLinesMustAgree() {
        assertEquals(upper, Sha256Sums.find("$lower  $apk\n$upper *$apk\n", apk))
        assertNull(Sha256Sums.find("$lower  $apk\n${"2".repeat(64)}  $apk\n", apk))
    }

    @Test
    fun badHexOrSpacingIsNoLine() {
        assertNull(Sha256Sums.find("${lower.drop(1)}  $apk", apk))
        assertNull(Sha256Sums.find("${lower.dropLast(1)}g  $apk", apk))
        assertNull(Sha256Sums.find("$lower $apk", apk))
        assertNull(Sha256Sums.find("$lower   $apk", apk))
        assertNull(Sha256Sums.find(" $lower  $apk", apk))
    }
}
