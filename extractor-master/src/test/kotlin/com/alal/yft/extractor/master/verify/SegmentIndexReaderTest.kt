package com.alal.yft.extractor.master.verify

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4: offline sidx bytes and HLS fixture playlists; nothing is fetched or decoded. */
class SegmentIndexReaderTest {
    @Test
    fun `a version 0 sidx gives the length and every later segment start`() {
        val timeline = SegmentIndexReader.sidx(ftyp() + sidx(listOf(2_000, 3_000, 1_500)))
        assertEquals(6_500L, timeline!!.durationMillis)
        assertEquals(listOf(2_000L, 5_000L), timeline.cuesMillis)
    }

    @Test
    fun `a version 1 sidx with a 90 kHz timescale reads the same`() {
        val ticks = listOf(180_000, 270_000, 90_000)
        val timeline = SegmentIndexReader.sidx(sidx(ticks, scale = 90_000, version = 1))
        assertEquals(6_000L, timeline!!.durationMillis)
        assertEquals(listOf(2_000L, 5_000L), timeline.cuesMillis)
    }

    @Test
    fun `a hierarchical or truncated index states no timeline`() {
        assertNull(SegmentIndexReader.sidx(sidx(listOf(2_000, 2_000), hierarchical = true)))
        val whole = sidx(listOf(2_000, 2_000, 2_000))
        assertNull(SegmentIndexReader.sidx(whole.copyOf(whole.size - 6)))
        assertNull(SegmentIndexReader.sidx(ftyp()))
    }

    @Test
    fun `CapturedMp4Facts keeps the sidx cues and its old duration rule`() {
        val facts = CapturedMp4Facts.read(ftyp() + sidx(listOf(4_000, 4_000, 2_000)))!!
        assertEquals(10_000L, facts.durationMillis)
        assertEquals(listOf(4_000L, 8_000L), facts.cuesMillis)
    }

    @Test
    fun `an ended HLS media playlist gives its length and piece starts`() {
        val timeline = SegmentIndexReader.extinf(fixture("ladder-1080.m3u8"))!!
        assertEquals(60_021L, timeline.durationMillis)
        assertEquals(15, timeline.cuesMillis.size)
        assertEquals(4_004L, timeline.cuesMillis.first())
        assertFalse(timeline.protected)
    }

    @Test
    fun `a master, a live playlist or other text states no timeline`() {
        assertNull(SegmentIndexReader.extinf(fixture("master.m3u8")))
        assertNull(SegmentIndexReader.extinf(fixture("live.m3u8")))
        assertNull(SegmentIndexReader.extinf("<html></html>"))
    }

    @Test
    fun `a SAMPLE-AES or FairPlay key marks the playlist protected`() {
        val timeline = SegmentIndexReader.extinf(fixture("drm.m3u8"))
        assertNotNull(timeline)
        assertTrue(timeline!!.protected)
    }

    companion object {
        fun fixture(name: String): String =
            checkNotNull(SegmentIndexReaderTest::class.java.getResource("/fingerprint/$name"))
                .readText()

        fun ftyp(): ByteArray = box("ftyp", "dash".toByteArray() + ByteArray(4))

        /** A sidx box whose [durations] are subsegment ticks at [scale]. */
        fun sidx(
            durations: List<Int>,
            scale: Int = 1_000,
            version: Int = 0,
            hierarchical: Boolean = false,
        ): ByteArray {
            val head = if (version == 1) 32 else 24
            val body = ByteBuffer.allocate(head + durations.size * 12)
            body.put(version.toByte()).put(ByteArray(3)).putInt(1).putInt(scale)
            if (version == 1) body.putLong(0).putLong(0) else body.putInt(0).putInt(0)
            body.putShort(0).putShort(durations.size.toShort())
            durations.forEach {
                body.putInt(if (hierarchical) 0x80000100.toInt() else 0x100)
                body.putInt(it).putInt(0x90000000.toInt())
            }
            return box("sidx", body.array())
        }

        fun box(type: String, bytes: ByteArray): ByteArray =
            ByteBuffer.allocate(8 + bytes.size).putInt(8 + bytes.size)
                .put(type.toByteArray(Charsets.US_ASCII)).put(bytes).array()
    }
}
