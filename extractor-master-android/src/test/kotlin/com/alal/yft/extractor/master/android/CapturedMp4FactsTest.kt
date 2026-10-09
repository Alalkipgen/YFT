package com.alal.yft.extractor.master.android

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class CapturedMp4FactsTest {
    @Test
    fun durationAndDimensionsAreReadFromIndependentContainerBytes() {
        val facts = CapturedMp4Facts.read(movie())!!
        assertEquals(60_000L, facts.durationMillis)
        assertEquals(1080, facts.width)
        assertEquals(1920, facts.height)
        assertFalse(facts.protected)
    }

    @Test
    fun boundedSuffixFindsOnlyACompleteMoov() {
        val facts = CapturedMp4Facts.read(ByteArray(111) + movie())!!
        assertEquals(60_000L, facts.durationMillis)
        assertNull(CapturedMp4Facts.read(movie().copyOf(20)))
    }

    @Test
    fun protectionBoxIsARefusalNotPlayableMetadata() {
        assertTrue(CapturedMp4Facts.read(box("pssh", ByteArray(32)))!!.protected)
    }

    @Test
    fun malformedBoxSizesNeverInventDuration() {
        assertNull(CapturedMp4Facts.read(byteArrayOf(-1, -1, -1, -1) + "moov".toByteArray()))
        assertNull(CapturedMp4Facts.read(ByteArray(7)))
    }

    @Test
    fun fragmentedMp4GetsDurationFromItsCompleteSidxNotThePlayer() {
        val data = ByteArray(48)
        val buffer = ByteBuffer.wrap(data)
        buffer.putInt(8, 1_000).putShort(22, 2)
        buffer.putInt(24, 30_000).putInt(28, 31_000)
        buffer.putInt(36, 30_000).putInt(40, 30_966)
        val facts = CapturedMp4Facts.read(box("sidx", data))
        assertNotNull("A complete fragment index independently states duration", facts)
        assertEquals(61_966L, facts!!.durationMillis)
    }

    @Test
    fun fragmentedMp4UsesMehdWithTheActualMovieTimescale() {
        val mvhd = ByteArray(100)
        ByteBuffer.wrap(mvhd).putInt(12, 1_000)
        val mehd = ByteArray(8)
        ByteBuffer.wrap(mehd).putInt(4, 61_966)
        val bytes = box("moov", box("mvhd", mvhd) + box("mvex", box("mehd", mehd)))
        val facts = CapturedMp4Facts.read(bytes)
        assertNotNull("Zero mvhd ticks do not erase independent mehd duration", facts)
        assertEquals(61_966L, facts!!.durationMillis)
    }

    private fun movie(): ByteArray {
        val mvhd = ByteArray(100)
        ByteBuffer.wrap(mvhd).putInt(12, 1_000).putInt(16, 60_000)
        val tkhd = ByteArray(84)
        ByteBuffer.wrap(tkhd).putInt(76, 1080 shl 16).putInt(80, 1920 shl 16)
        return box("moov", box("mvhd", mvhd) + box("trak", box("tkhd", tkhd)))
    }

    private fun box(type: String, bytes: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + bytes.size).putInt(8 + bytes.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(bytes).array()
}