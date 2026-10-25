package com.alal.yft.core.download

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * D1: the HLS remux on FFmpeg-made pieces ([HlsFixtures]). Its files were checked with FFmpeg
 * 7.0.2: they decode to the same pictures and sound as the pieces (MD5 of every decoded frame),
 * hold the very AAC frames of FFmpeg's own remux (`-c copy`) and its H.264 frames less their
 * access unit delimiters, and present the picture 46 ms after the sound, as the pieces do. The
 * MD5s below are of those sample bytes.
 */
class TsMp4RemuxerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `an MPEG-TS video becomes an MP4 with its H264 picture and AAC sound`() = runTest {
        val (file, length) = remux(listOf(piece("av-0.ts"), piece("av-1.ts")))
        val bytes = file.readBytes()
        val dump = Mp4Dump(bytes)

        assertEquals(file.length(), length)
        assertEquals(listOf("ftyp", "mdat", "moov"), dump.top.map { it.type })
        assertEquals("isom", brand(bytes))
        val (video, audio) = dump.tracks
        assertEquals("vide", video.handler)
        assertEquals("avc1", entry(video))
        assertEquals(160 to 90, (video.width shr 16) to (video.height shr 16))
        assertEquals(90_000L, video.timescale)
        assertEquals(20, video.samples.size)
        assertEquals(listOf(0, 10), video.samples.indices.filter { video.samples[it].sync })
        assertTrue(video.samples.any { it.compositionOffset > 0 })
        video.samples.forEach { sample -> assertNalUnits(bytes, sample) }
        assertEquals(VIDEO_MD5, md5(bytes, video))
        assertEquals("soun", audio.handler)
        assertEquals("mp4a", entry(audio))
        assertEquals(22_050L, audio.timescale)
        assertEquals(45, audio.samples.size)
        assertTrue(audio.samples.all { it.decodeTime % 1_024 == 0L })
        assertEquals(AUDIO_MD5, md5(bytes, audio))
        // The picture waits 46 ms in an empty edit, then starts at its first frame (18 000).
        assertEquals(listOf(46L to -1L, 2_000L to 18_000L), video.edits)
        assertTrue(audio.edits.isEmpty())
    }

    @Test
    fun `the sound alone of an MPEG-TS video becomes an M4A`() = runTest {
        val (file, _) = remux(listOf(piece("av-0.ts"), piece("av-1.ts")), audioOnly = true)
        val bytes = file.readBytes()
        val audio = Mp4Dump(bytes).tracks.single()

        assertEquals("M4A ", brand(bytes))
        assertEquals("soun", audio.handler)
        assertEquals(45, audio.samples.size)
        assertEquals(AUDIO_MD5, md5(bytes, audio))
    }

    @Test
    fun `an MPEG-TS sound becomes an M4A`() = runTest {
        val (file, _) = remux(listOf(piece("audio-0.ts"), piece("audio-1.ts")))
        val bytes = file.readBytes()
        val audio = Mp4Dump(bytes).tracks.single()

        assertEquals("M4A ", brand(bytes))
        assertEquals("mp4a", entry(audio))
        assertEquals(44, audio.samples.size)
        assertEquals(SOUND_MD5, md5(bytes, audio))
    }

    @Test
    fun `packed AAC sound becomes an M4A of the same frames`() = runTest {
        val track = Mp4TestFiles.audio(fragments = 2, perFragment = 20)
        val adts = Mp4TestFiles.adts(track, crcEvery = 3)
        // The first piece ends after 20 frames; every third frame has a CRC, 2 bytes more.
        val split = (0 until 20).sumOf { index ->
            7 + track.samples[index].bytes.size + if (index % 3 == 0) 2 else 0
        }
        // The engine hands on each piece from after its ID3 tags.
        val tag = ByteArray(PREFIX_BYTES) { 0x49 }
        val pieces = listOf(
            piece(tag + adts.copyOfRange(0, split), start = PREFIX_BYTES.toLong()),
            piece(adts.copyOfRange(split, adts.size)),
        )

        val (file, _) = remux(pieces, kind = HlsMediaKind.ADTS_AUDIO)
        val bytes = file.readBytes()
        val audio = Mp4Dump(bytes).tracks.single()

        assertEquals("M4A ", brand(bytes))
        assertEquals(44_100L, audio.timescale)
        assertEquals(track.samples.size, audio.samples.size)
        audio.samples.forEachIndexed { index, sample ->
            val at = sample.offset.toInt()
            val frame = bytes.copyOfRange(at, at + sample.size)
            assertTrue("frame $index", frame.contentEquals(track.samples[index].bytes))
            assertEquals(index * 1_024L, sample.decodeTime)
        }
    }

    @Test
    fun `pieces whose times start again follow one another`() = runTest {
        val (file, _) = remux(listOf(piece("av-0.ts"), piece("av-0.ts")))
        val (video, audio) = Mp4Dump(file.readBytes()).tracks

        assertEquals(20, video.samples.size)
        assertEquals(listOf(0, 10), video.samples.indices.filter { video.samples[it].sync })
        assertTrue(video.samples.zipWithNext().all { (a, b) -> b.decodeTime > a.decodeTime })
        assertEquals(38, audio.samples.size)
        assertTrue(audio.samples.zipWithNext().all { (a, b) -> b.decodeTime > a.decodeTime })
        // Two seconds of picture: the second piece starts where the first one ends.
        assertEquals(180_000L, video.mediaDuration)
    }

    @Test
    fun `a piece disguised as an image is remuxed from its packets`() = runTest {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47) + Random(3).nextBytes(996)
        val disguised = piece(image + HlsFixtures.bytes("av-0.ts"))

        assertEquals(HlsMediaKind.TRANSPORT_STREAM, HlsMediaSniffer.of(disguised.file, 0))
        val (file, _) = remux(listOf(disguised, piece("av-1.ts")))
        val bytes = file.readBytes()

        assertEquals(VIDEO_MD5, md5(bytes, Mp4Dump(bytes).tracks[0]))
    }

    @Test
    fun `another video codec is not remuxed`() = runTest {
        val failure = runCatching { remux(listOf(piece("mpeg2.ts"))) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is StreamCopyUnsupported)
    }

    @Test
    fun `a piece that is not MPEG-TS is not remuxed`() = runTest {
        val broken = piece(Random(4).nextBytes(4_000))
        val failure = runCatching { remux(listOf(piece("av-0.ts"), broken)) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is StreamCopyUnsupported)
        assertEquals("piece 2 is not MPEG-TS", failure?.message)
    }

    private suspend fun remux(
        pieces: List<HlsPiece>,
        kind: HlsMediaKind = HlsMediaKind.TRANSPORT_STREAM,
        audioOnly: Boolean = false,
    ): Pair<File, Long> {
        val file = folder.newFile()
        val length = FileSeekableDownloadOutput(file).use { output ->
            TsMp4Remuxer.remux(pieces, kind, audioOnly, output)
        }
        return file to length
    }

    private fun piece(name: String): HlsPiece = piece(HlsFixtures.bytes(name))

    private fun piece(bytes: ByteArray, start: Long = 0): HlsPiece =
        HlsPiece(folder.newFile().apply { writeBytes(bytes) }, start)

    private fun brand(bytes: ByteArray): String = String(bytes, 8, 4, Charsets.ISO_8859_1)

    private fun entry(track: Mp4Dump.DumpedTrack): String =
        String(track.sampleDescription, 20, 4, Charsets.ISO_8859_1)

    /** A sample of 4-byte length-prefixed NAL units, without access unit delimiters (9). */
    private fun assertNalUnits(bytes: ByteArray, sample: Mp4Dump.DumpedSample) {
        val end = sample.offset.toInt() + sample.size
        var at = sample.offset.toInt()
        val types = mutableListOf<Int>()
        while (at < end) {
            val length = ByteBuffer.wrap(bytes, at, 4).int
            assertTrue(length > 0)
            types += bytes[at + 4].toInt() and 0x1f
            at += 4 + length
        }
        assertEquals(end, at)
        assertTrue(types.toString(), 9 !in types)
        assertEquals(sample.sync, 5 in types)
    }

    private fun md5(bytes: ByteArray, track: Mp4Dump.DumpedTrack): String {
        val digest = MessageDigest.getInstance("MD5")
        track.samples.forEach { digest.update(bytes, it.offset.toInt(), it.size) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFIX_BYTES = 42
        const val VIDEO_MD5 = "469cca2aef9b568b64c7aad032e14228"
        const val AUDIO_MD5 = "45c646f3f7e0423b5d99c499e3ae6a49"
        const val SOUND_MD5 = "794275dd9215b1a1e39854f88a6f922c"
    }
}
