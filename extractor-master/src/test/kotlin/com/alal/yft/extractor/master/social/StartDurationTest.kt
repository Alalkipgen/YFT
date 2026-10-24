package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (10): a file's length from the first bytes V read of it ([StartDuration]), for
 * the rows that state none (the plugin player's answer names no length, so on step 6's first run
 * every trial line said "duration within 1 s: unknown"). The movie header of a progressive file
 * whose `moov` the read cuts, a fragmented file's `mehd` over the movie's timescale, else its
 * index (`sidx`); nothing else is a length. MP4 starts made in code ([LengthBytes]).
 */
class StartDurationTest {
    @Test
    fun `a progressive file's movie header gives its length, though the read cuts its moov`() {
        assertEquals(23_891L, StartDuration.millis(LengthBytes.progressive(LengthBytes.mvhd(1_000, 23_891))))
        // Version 1 (64-bit times): 1:02:05.5 at 90 kHz.
        assertEquals(3_725_500L, StartDuration.millis(LengthBytes.progressive(LengthBytes.mvhd1(90_000, 335_295_000))))
        // Exactly 48 hours is the longest length.
        assertEquals(172_800_000L, StartDuration.millis(LengthBytes.progressive(LengthBytes.mvhd(1_000, 172_800_000))))
    }

    @Test
    fun `a fragmented file's length comes from its fragments' header, else from its index`() {
        val index = LengthBytes.sidx(1_000, 10_000, 5_500)
        // The movie header states 0 (a DASH file): mehd over the movie's timescale.
        assertEquals(
            23_746L,
            StartDuration.millis(LengthBytes.fragmented(LengthBytes.mvhd(29_857, 0), LengthBytes.mehd(709_000), index)),
        )
        assertEquals(
            23_746L,
            StartDuration.millis(LengthBytes.fragmented(LengthBytes.mvhd(29_857, 0), LengthBytes.mehd(709_000, version = 1), index)),
        )
        // No mehd: the index's subsegments; an index alone after the file type too.
        assertEquals(15_500L, StartDuration.millis(LengthBytes.fragmented(LengthBytes.mvhd(29_857, 0), null, index)))
        assertEquals(4_000L, StartDuration.millis(InitBytes.ftyp() + LengthBytes.sidx(1_000, 4_000)))
        // A movie header that states a length comes first.
        assertEquals(
            20_000L,
            StartDuration.millis(LengthBytes.fragmented(LengthBytes.mvhd(1_000, 20_000), LengthBytes.mehd(709_000), index)),
        )
    }

    @Test
    fun `bytes without a length give none`() {
        val none = mapOf(
            "the init check's start (headers of zeros)" to InitBytes.mp4(),
            "a moov the read cuts inside its movie header" to
                LengthBytes.progressive(LengthBytes.mvhd(1_000, 23_891)).copyOf(InitBytes.ftyp().size + 8 + 50),
            "a movie header outside a moov" to InitBytes.ftyp() + LengthBytes.mvhd(1_000, 5_000),
            "a length over 48 hours" to LengthBytes.progressive(LengthBytes.mvhd(1_000, 172_800_001)),
            "fragments without the movie's timescale" to
                LengthBytes.fragmented(LengthBytes.mvhd(0, 0), LengthBytes.mehd(709_000), null),
            "an index the read cuts" to (InitBytes.ftyp() + LengthBytes.sidx(1_000, 4_000)).let { it.copyOf(it.size - 4) },
            "a page" to "<html><body>fixture</body></html>".toByteArray(),
            "zeros" to ByteArray(4_096),
            "nothing" to ByteArray(0),
            "more boxes than the bound before the moov" to
                (1..300).fold(ByteArray(0)) { bytes, _ -> bytes + InitBytes.box("free", ByteArray(0)) } +
                LengthBytes.progressive(LengthBytes.mvhd(1_000, 23_891)),
        )
        none.forEach { (case, bytes) -> assertNull(case, StartDuration.millis(bytes)) }
    }

    @Test
    fun `any cut of a start, and any bytes, give a bounded length or none and never fail`() {
        val start = LengthBytes.progressive(LengthBytes.mvhd(1_000, 23_891))
        for (size in 0..start.size) {
            val millis = StartDuration.millis(start.copyOf(size))
            assertTrue("$size", millis == null || millis == 23_891L)
        }
        val random = Random(10)
        repeat(500) {
            val bytes = random.nextBytes(random.nextInt(0, 600))
            // Some made-up boxes inside: a moov or an index of any size.
            if (bytes.size > 16 && it % 2 == 0) "moov".toByteArray().copyInto(bytes, 4)
            if (bytes.size > 64 && it % 3 == 0) "mvhd".toByteArray().copyInto(bytes, 12)
            val millis = StartDuration.millis(bytes)
            assertTrue(millis == null || millis in 1..172_800_000L)
        }
    }
}

/** MP4 file starts with a length, made in code, for S1a (10)'s tests (the init check's boxes). */
internal object LengthBytes {
    private fun u32(value: Long): ByteArray = InitBytes.u32(value)

    private fun u64(value: Long): ByteArray = u32(value ushr 32) + u32(value and 0xFFFFFFFFL)

    private fun box(type: String, payload: ByteArray): ByteArray = InitBytes.box(type, payload)

    /** A movie header, version 0: [scale] ticks a second, [ticks] long (the rest zeros). */
    fun mvhd(scale: Long, ticks: Long): ByteArray =
        box("mvhd", u32(0) + u32(0) + u32(0) + u32(scale) + u32(ticks) + ByteArray(80))

    /** A movie header, version 1 (64-bit times and duration). */
    fun mvhd1(scale: Long, ticks: Long): ByteArray =
        box("mvhd", u32(0x01000000) + u64(0) + u64(0) + u32(scale) + u64(ticks) + ByteArray(80))

    /** A fragmented file's length, [ticks] of the movie's timescale. */
    fun mehd(ticks: Long, version: Int = 0): ByteArray =
        box("mehd", if (version == 1) u32(0x01000000) + u64(ticks) else u32(0) + u32(ticks))

    /** A whole index of [scale] ticks a second, one media subsegment per [ticks]. */
    fun sidx(scale: Long, vararg ticks: Long): ByteArray {
        val references = ticks.fold(ByteArray(0)) { bytes, each -> bytes + u32(1_000) + u32(each) + u32(0x90000000) }
        val count = byteArrayOf(((ticks.size shr 8) and 255).toByte(), (ticks.size and 255).toByte())
        return box("sidx", u32(0) + u32(1) + u32(scale) + u32(0) + u32(0) + byteArrayOf(0, 0) + count + references)
    }

    /** A picture track's boxes (zeros). */
    private fun trak(): ByteArray = box(
        "trak",
        box("tkhd", ByteArray(84)) + box("mdia", box("mdhd", ByteArray(24)) + box("hdlr", u32(0) + u32(0) + "vide".toByteArray() + ByteArray(13))),
    )

    /**
     * A progressive (faststart) file's first [read] bytes: the file type, then a `moov` far longer
     * than the read, [header] first and a long track after it.
     */
    fun progressive(header: ByteArray, read: Int = 4_096): ByteArray {
        val track = u32(20_008) + "trak".toByteArray() + ByteArray(20_000)
        val moov = u32(8L + header.size + track.size) + "moov".toByteArray() + header + track
        return (InitBytes.ftyp() + moov).copyOf(read)
    }

    /**
     * A DASH file's first bytes: the file type, a whole `moov` ([movie] header, a track, [fragments]
     * in `mvex`), [index] when named, then the start of its first fragment.
     */
    fun fragmented(movie: ByteArray, fragments: ByteArray?, index: ByteArray?): ByteArray {
        val extends = fragments?.let { box("mvex", it + box("trex", ByteArray(24))) } ?: ByteArray(0)
        return InitBytes.ftyp() + box("moov", movie + trak() + extends) + (index ?: ByteArray(0)) +
            u32(2_048) + "moof".toByteArray() + ByteArray(64)
    }
}
