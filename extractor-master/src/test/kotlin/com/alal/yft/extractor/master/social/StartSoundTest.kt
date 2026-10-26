package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The owner's retest of 7e101aa4 (VK, 2026-10-26): a sound file's codec from the first bytes V
 * read of it ([StartSound]), for the pieces' sound row that states none, so the download's gate
 * (`mp4a.40.…` only) takes the merge the sheet offered. MP4 starts made in code ([SoundBytes]).
 */
class StartSoundTest {
    @Test
    fun `an mp4a entry's esds names the audio object type`() {
        assertEquals("mp4a.40.2", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2))))))
        assertEquals("mp4a.40.5", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(5))))))
        assertEquals("mp4a.40.29", StartSound.codec(PictureBytes.progressive(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(29))))))
        // The escape (31, then six bits): 32 + 10 = 42 (USAC).
        assertEquals("mp4a.40.42", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(42))))))
        // An ES descriptor with its optional fields (a dependence, a URL, an OCR).
        assertEquals(
            "mp4a.40.2",
            StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2, flags = 0xE0))))),
        )
        // Another object type (MPEG-1 layer 3 in MP4): named as is, so the gate refuses it.
        assertEquals("mp4a.6b", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2, objectType = 0x6B))))))
    }

    @Test
    fun `an mp4a entry without a readable esds is plain AAC, another entry its own type`() {
        assertEquals("mp4a.40.2", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(ByteArray(0))))))
        assertEquals("mp4a.40.2", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(InitBytes.box("esds", ByteArray(3)))))))
        assertEquals("mp4a.40.2", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(InitBytes.box("btrt", ByteArray(12)))))))
        assertEquals("opus", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.entry("Opus", InitBytes.box("dOps", ByteArray(11)))))))
        assertEquals("ac-3", StartSound.codec(PictureBytes.fragmented(SoundBytes.sound(SoundBytes.entry("ac-3", InitBytes.box("dac3", ByteArray(3)))))))
    }

    @Test
    fun `the sound track is found after a picture track, and a cut moov or picture-only file names none`() {
        val both = PictureBytes.progressive(PictureBytes.video(1_080, 1_920), SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2))))
        assertEquals("mp4a.40.2", StartSound.codec(both))
        assertNull(StartSound.codec(PictureBytes.fragmented(PictureBytes.video(1_080, 1_920))))
        // The read cuts the picture track's tables before the sound track.
        val cut = PictureBytes.progressive(PictureBytes.video(1_280, 720, tables = PictureBytes.tables(20_000)), SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2))))
        assertNull(StartSound.codec(cut))
        assertNull(StartSound.codec(ByteArray(0)))
        assertNull(StartSound.codec(InitBytes.ftyp()))
        assertNull(StartSound.codec("not an mp4 at all, only text".toByteArray()))
        // Each whole box only: a moov the bytes hold in part names none.
        val whole = PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2))))
        assertNull(StartSound.codec(whole.copyOf(whole.indexOf("esds") + 10)))
    }

    private fun ByteArray.indexOf(type: String): Int {
        val needle = type.toByteArray(Charsets.US_ASCII)
        for (at in 0..size - needle.size) {
            if ((needle.indices).all { this[at + it] == needle[it] }) return at
        }
        error("no $type")
    }
}

/** Sound tracks' bytes: an `mp4a` entry with its `esds`, or another entry, in a `soun` track. */
internal object SoundBytes {
    private fun u16(value: Int): ByteArray = byteArrayOf(((value shr 8) and 255).toByte(), (value and 255).toByte())

    private fun u32(value: Long): ByteArray = InitBytes.u32(value)

    /** A sound sample entry [type] (2 channels, 16 bits, 48 kHz) with [children] after its fields. */
    fun entry(type: String, children: ByteArray): ByteArray =
        InitBytes.box(type, ByteArray(6) + u16(1) + ByteArray(8) + u16(2) + u16(16) + ByteArray(4) + u32(48_000L shl 16) + children)

    fun mp4a(children: ByteArray): ByteArray = entry("mp4a", children)

    /** A descriptor [tag] of [payload] (a two-byte size, the first byte a continuation, as muxers write them). */
    private fun descriptor(tag: Int, payload: ByteArray): ByteArray =
        byteArrayOf(tag.toByte(), (0x80 or (payload.size shr 7)).toByte(), (payload.size and 0x7f).toByte()) + payload

    /**
     * An `esds` box: the ES descriptor ([flags]: a dependence 0x80, a URL 0x40, an OCR 0x20), its
     * decoder configuration ([objectType]) and the audio specific configuration of
     * [audioObjectType] (the escape past 31), then an SL configuration.
     */
    fun esds(audioObjectType: Int, objectType: Int = 0x40, flags: Int = 0): ByteArray {
        val config = if (audioObjectType < 31) {
            byteArrayOf(((audioObjectType shl 3) or 0x03).toByte(), 0x90.toByte())
        } else {
            val extra = audioObjectType - 32
            byteArrayOf((0xF8 or (extra shr 3)).toByte(), (((extra and 0x07) shl 5) or 0x0C).toByte(), 0x80.toByte())
        }
        val decoder = descriptor(0x04, byteArrayOf(objectType.toByte(), 0x15, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0) + descriptor(0x05, config))
        var es = u16(1) + byteArrayOf(flags.toByte())
        if (flags and 0x80 != 0) es += u16(2)
        if (flags and 0x40 != 0) es += byteArrayOf(3) + "url".toByteArray()
        if (flags and 0x20 != 0) es += u16(3)
        return InitBytes.box("esds", u32(0) + descriptor(0x03, es + decoder + descriptor(0x06, byteArrayOf(2))))
    }

    /** A sound track of [entry] (its header 0 × 0, handler `soun`). */
    fun sound(entry: ByteArray): ByteArray = PictureBytes.track(PictureBytes.tkhd(0, 0), "soun", entry)
}
