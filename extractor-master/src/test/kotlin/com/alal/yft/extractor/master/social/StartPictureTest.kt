package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan task 16 (F6): a file's picture size from the first bytes V read of it
 * ([StartPicture]), for the rows that state none (the plugin player's answer names no size, so on
 * the phone of 2026-10-23 Facebook link 1's Master rows read "Quality unknown"). The picture
 * track's header (`tkhd`, 16.16), else its visual sample entry (`stsd`); a turned track's swapped;
 * each side 16 to 8192; nothing else is a size. MP4 starts made in code ([PictureBytes]).
 */
class StartPictureTest {
    @Test
    fun `a progressive file's picture track gives its size, though the read cuts its moov`() {
        // The picture track first, its sample tables longer than the read; the sound track after it.
        val start = PictureBytes.progressive(
            PictureBytes.video(1_280, 720, tables = PictureBytes.tables(20_000)),
            PictureBytes.sound(),
        )
        assertEquals(SocialRowCheck.START_BYTES, start.size)
        assertEquals(StartPicture.Size(1_280, 720), StartPicture.of(start))
        // The sound track first (0 × 0, handler soun), then the picture track.
        assertEquals(
            StartPicture.Size(640, 360),
            StartPicture.of(PictureBytes.progressive(PictureBytes.sound(), PictureBytes.video(640, 360))),
        )
        // A portrait file as its header states it; a header of version 1; an HEVC file.
        assertEquals(StartPicture.Size(718, 1_276), StartPicture.of(PictureBytes.progressive(PictureBytes.video(718, 1_276))))
        assertEquals(StartPicture.Size(1_920, 1_080), StartPicture.of(PictureBytes.progressive(PictureBytes.video(1_920, 1_080, version = 1))))
        assertEquals(
            StartPicture.Size(3_840, 2_160),
            StartPicture.of(PictureBytes.progressive(PictureBytes.video(3_840, 2_160, entry = PictureBytes.visual("hvc1", 3_840, 2_160)))),
        )
        // No handler in the bytes (the read cuts the track after its header): a non-zero size is a picture's.
        val cut = PictureBytes.progressive(PictureBytes.video(854, 480))
        val header = cut.indexOf("tkhd") + 4 + 84
        assertEquals(StartPicture.Size(854, 480), StartPicture.of(cut.copyOf(header)))
    }

    @Test
    fun `a fragmented file's moov gives its size`() {
        // A DASH start: no samples in the moov, its fragments in mvex, an index and a fragment after it.
        assertEquals(StartPicture.Size(1_280, 720), StartPicture.of(PictureBytes.fragmented(PictureBytes.video(1_280, 720))))
        assertEquals(
            StartPicture.Size(426, 240),
            StartPicture.of(PictureBytes.fragmented(PictureBytes.video(426, 240, entry = PictureBytes.visual("vp09", 426, 240)))),
        )
        assertEquals(
            StartPicture.Size(1_080, 1_920),
            StartPicture.of(PictureBytes.fragmented(PictureBytes.video(1_080, 1_920, entry = PictureBytes.visual("av01", 1_080, 1_920)))),
        )
    }

    @Test
    fun `a track turned 90 or 270 degrees shows its size swapped, 180 degrees as it is`() {
        assertEquals(
            StartPicture.Size(1_080, 1_920),
            StartPicture.of(PictureBytes.progressive(PictureBytes.video(1_920, 1_080, matrix = PictureBytes.TURN_90))),
        )
        assertEquals(
            StartPicture.Size(720, 1_280),
            StartPicture.of(PictureBytes.fragmented(PictureBytes.video(1_280, 720, matrix = PictureBytes.TURN_270))),
        )
        assertEquals(
            StartPicture.Size(1_280, 720),
            StartPicture.of(PictureBytes.progressive(PictureBytes.video(1_280, 720, matrix = PictureBytes.TURN_180))),
        )
        // Turned with its size only in the sample entry: swapped too.
        val entryOnly = PictureBytes.video(0, 0, matrix = PictureBytes.TURN_90, entry = PictureBytes.visual("avc1", 1_280, 720))
        assertEquals(StartPicture.Size(720, 1_280), StartPicture.of(PictureBytes.progressive(entryOnly)))
    }

    @Test
    fun `a picture track whose header states 0 x 0 takes its sample entry's size`() {
        for (type in listOf("avc1", "avc3", "hvc1", "hev1", "vp09", "av01")) {
            val track = PictureBytes.video(0, 0, entry = PictureBytes.visual(type, 1_280, 720))
            assertEquals(type, StartPicture.Size(1_280, 720), StartPicture.of(PictureBytes.progressive(track)))
            assertEquals(type, StartPicture.Size(1_280, 720), StartPicture.of(PictureBytes.fragmented(track)))
        }
        // The header's own size comes first.
        val both = PictureBytes.video(1_280, 720, entry = PictureBytes.visual("avc1", 1_920, 1_088))
        assertEquals(StartPicture.Size(1_280, 720), StartPicture.of(PictureBytes.progressive(both)))
        // An entry the reader does not know (encrypted, or another codec) gives none.
        assertNull(StartPicture.of(PictureBytes.progressive(PictureBytes.video(0, 0, entry = PictureBytes.visual("encv", 1_280, 720)))))
        assertNull(StartPicture.of(PictureBytes.progressive(PictureBytes.video(0, 0, entry = PictureBytes.visual("mp4v", 1_280, 720)))))
    }

    @Test
    fun `bytes without a picture size give none`() {
        val none = mapOf(
            "a sound-only file" to PictureBytes.progressive(PictureBytes.sound()),
            "a sound-only DASH file" to PictureBytes.fragmented(PictureBytes.sound()),
            "a sound track whose header states a size" to PictureBytes.progressive(PictureBytes.sound(width = 1_280, height = 720)),
            "a subtitle track's size" to PictureBytes.progressive(PictureBytes.track(PictureBytes.tkhd(1_280, 720), "sbtl", PictureBytes.sound().copyOf(0))),
            "a moov past the bytes read (after the samples)" to
                (InitBytes.ftyp() + InitBytes.u32(1_000_000) + "mdat".toByteArray() + ByteArray(5_000) +
                    InitBytes.box("moov", PictureBytes.video(1_280, 720))).copyOf(SocialRowCheck.START_BYTES),
            "a moov after a box longer than the read" to
                (InitBytes.ftyp() + InitBytes.box("free", ByteArray(5_000)) + PictureBytes.progressive(PictureBytes.video(1_280, 720)))
                    .copyOf(SocialRowCheck.START_BYTES),
            "a track header outside a moov" to InitBytes.ftyp() + PictureBytes.video(1_280, 720),
            "a picture track of 0 × 0 without a sample entry" to PictureBytes.progressive(PictureBytes.video(0, 0, entry = ByteArray(0))),
            "the init check's start (headers of zeros)" to InitBytes.mp4(),
            "a side under 16" to PictureBytes.progressive(PictureBytes.video(8, 8)),
            "a side over 8192" to PictureBytes.progressive(PictureBytes.video(9_000, 5_000)),
            "a page" to "<html><body>fixture</body></html>".toByteArray(),
            "zeros" to ByteArray(4_096),
            "nothing" to ByteArray(0),
            "more boxes than the bound before the moov" to
                (1..300).fold(ByteArray(0)) { bytes, _ -> bytes + InitBytes.box("free", ByteArray(0)) } +
                PictureBytes.progressive(PictureBytes.video(1_280, 720)),
        )
        none.forEach { (case, bytes) -> assertNull(case, StartPicture.of(bytes)) }
        // The bounds themselves count.
        assertEquals(StartPicture.Size(16, 16), StartPicture.of(PictureBytes.progressive(PictureBytes.video(16, 16))))
        assertEquals(StartPicture.Size(8_192, 4_320), StartPicture.of(PictureBytes.progressive(PictureBytes.video(8_192, 4_320))))
    }

    @Test
    fun `any cut of a start, and any bytes, give the size or none and never fail`() {
        val starts = listOf(
            PictureBytes.progressive(PictureBytes.video(1_280, 720), PictureBytes.sound()) to StartPicture.Size(1_280, 720),
            PictureBytes.fragmented(PictureBytes.video(0, 0, matrix = PictureBytes.TURN_90, entry = PictureBytes.visual("avc1", 1_280, 720))) to
                StartPicture.Size(720, 1_280),
        )
        for ((start, size) in starts) {
            for (cut in 0..start.size) {
                val read = StartPicture.of(start.copyOf(cut))
                assertTrue("$cut: $read", read == null || read == size)
            }
        }
        val random = Random(16)
        repeat(500) {
            val bytes = random.nextBytes(random.nextInt(0, 800))
            // Some made-up boxes inside: a moov, a track, its header of any size.
            if (bytes.size > 16 && it % 2 == 0) "moov".toByteArray().copyInto(bytes, 4)
            if (bytes.size > 32 && it % 3 == 0) "trak".toByteArray().copyInto(bytes, 12)
            if (bytes.size > 64 && it % 5 == 0) "tkhd".toByteArray().copyInto(bytes, 20)
            val read = StartPicture.of(bytes)
            assertTrue("$read", read == null || read.width in 16..8_192 && read.height in 16..8_192)
        }
    }

    private fun ByteArray.indexOf(type: String): Int {
        val marker = type.toByteArray()
        return (0..size - marker.size).first { at -> marker.indices.all { this[at + it] == marker[it] } }
    }
}

/** MP4 file starts with a picture size, made in code, for task 16's (F6) tests (the init check's boxes). */
internal object PictureBytes {
    private fun u32(value: Long): ByteArray = InitBytes.u32(value)

    private fun u16(value: Int): ByteArray = byteArrayOf(((value shr 8) and 255).toByte(), (value and 255).toByte())

    private fun box(type: String, payload: ByteArray): ByteArray = InitBytes.box(type, payload)

    private fun matrix(vararg values: Long): ByteArray = values.fold(ByteArray(0)) { bytes, value -> bytes + u32(value) }

    /** The identity matrix, a quarter turn (90°: b = 1, c = -1), its reverse (270°) and a half turn. */
    val IDENTITY = longArrayOf(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000)
    val TURN_90 = longArrayOf(0, 0x10000, 0, 0xFFFF0000, 0, 0, 0, 0, 0x40000000)
    val TURN_270 = longArrayOf(0, 0xFFFF0000, 0, 0x10000, 0, 0, 0, 0, 0x40000000)
    val TURN_180 = longArrayOf(0xFFFF0000, 0, 0, 0, 0xFFFF0000, 0, 0, 0, 0x40000000)

    /** A track header ([version] 0 or 1) of [width] × [height] (16.16) with [turn] as its matrix. */
    fun tkhd(width: Int, height: Int, turn: LongArray = IDENTITY, version: Int = 0): ByteArray {
        val times = if (version == 1) ByteArray(16) + u32(1) + u32(0) + ByteArray(8) else u32(0) + u32(0) + u32(1) + u32(0) + u32(0)
        return box(
            "tkhd",
            u32((version.toLong() shl 24) or 3) + times + ByteArray(8) + ByteArray(8) + matrix(*turn) +
                u32(width.toLong() shl 16) + u32(height.toLong() shl 16),
        )
    }

    /** A visual sample entry [type] of [width] × [height], with a short decoder configuration. */
    fun visual(type: String, width: Int, height: Int): ByteArray = box(
        type,
        ByteArray(6) + u16(1) + ByteArray(16) + u16(width) + u16(height) + u32(0x00480000) + u32(0x00480000) + u32(0) + u16(1) +
            ByteArray(32) + u16(0x18) + u16(0xFFFF) + box("avcC", ByteArray(12)),
    )

    /** A sound sample entry (AAC). */
    private fun mp4a(): ByteArray =
        box("mp4a", ByteArray(6) + u16(1) + ByteArray(8) + u16(2) + u16(16) + ByteArray(4) + u32(48_000L shl 16) + box("esds", ByteArray(24)))

    /** Sample tables of about [bytes] bytes (a long file's sizes table). */
    fun tables(bytes: Int): ByteArray = box("stts", u32(0) + u32(0)) + box("stsz", u32(0) + u32(0) + u32(bytes / 4L) + ByteArray(bytes))

    /** A track: [header], then mdia (mdhd, the [handler], minf (a media header, dinf, stbl (stsd with [entry], [tables]))). */
    fun track(header: ByteArray, handler: String, entry: ByteArray, tables: ByteArray = ByteArray(0)): ByteArray {
        val media = if (handler == "vide") box("vmhd", u32(1) + ByteArray(8)) else box("smhd", ByteArray(8))
        val stsd = if (entry.isEmpty()) ByteArray(0) else box("stsd", u32(0) + u32(1) + entry)
        val minf = box("minf", media + box("dinf", box("dref", u32(0) + u32(1) + box("url ", u32(1)))) + box("stbl", stsd + tables))
        val hdlr = box("hdlr", u32(0) + u32(0) + handler.toByteArray() + ByteArray(12) + "fixture".toByteArray() + ByteArray(1))
        return box("trak", header + box("edts", box("elst", u32(0) + u32(1) + ByteArray(12))) + box("mdia", box("mdhd", ByteArray(24)) + hdlr + minf))
    }

    /** A picture track of [width] × [height] (its header's), [entry] its sample entry. */
    fun video(
        width: Int,
        height: Int,
        matrix: LongArray = IDENTITY,
        entry: ByteArray = visual("avc1", width, height),
        tables: ByteArray = ByteArray(0),
        version: Int = 0,
    ): ByteArray = track(tkhd(width, height, matrix, version), "vide", entry, tables)

    /** A sound track (its header 0 × 0 unless named). */
    fun sound(width: Int = 0, height: Int = 0): ByteArray = track(tkhd(width, height), "soun", mp4a())

    /**
     * A progressive (faststart) file's first [read] bytes: the file type, then a `moov` (the movie
     * header, [tracks]) and the samples after it, as long as the read.
     */
    fun progressive(vararg tracks: ByteArray, read: Int = SocialRowCheck.START_BYTES): ByteArray {
        val moov = box("moov", LengthBytes.mvhd(1_000, 23_891) + tracks.fold(ByteArray(0)) { bytes, track -> bytes + track })
        return (InitBytes.ftyp() + moov + u32(1_000_000) + "mdat".toByteArray() + ByteArray(read)).copyOf(read)
    }

    /**
     * A DASH file's first bytes: the file type, a whole `moov` (the movie header, [tracks], their
     * fragments in `mvex`), an index, then the start of its first fragment.
     */
    fun fragmented(vararg tracks: ByteArray): ByteArray {
        val extends = box("mvex", LengthBytes.mehd(709_000) + box("trex", ByteArray(24)))
        return InitBytes.ftyp() + box("moov", LengthBytes.mvhd(29_857, 0) + tracks.fold(ByteArray(0)) { bytes, track -> bytes + track } + extends) +
            LengthBytes.sidx(1_000, 23_746) + u32(2_048) + "moof".toByteArray() + ByteArray(64)
    }
}
