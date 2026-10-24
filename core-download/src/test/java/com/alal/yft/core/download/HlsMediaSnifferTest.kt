package com.alal.yft.core.download

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** D1: what the first piece of an HLS download holds, and so how it is published. */
class HlsMediaSnifferTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `MPEG-TS pieces are told from their sync bytes`() {
        val file = file(HlsFixtures.bytes("av-0.ts"))

        assertEquals(HlsMediaKind.TRANSPORT_STREAM, HlsMediaSniffer.of(file, 0))
        assertEquals(0L, HlsMediaSniffer.transportStreamStart(file, 0))
        assertEquals("ts" to "video/mp2t", HlsMediaKind.TRANSPORT_STREAM.kept())
    }

    @Test
    fun `MPEG-TS packets after an image prefix are found`() {
        val prefix = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47) + Random(5).nextBytes(2_000)
        val file = file(ByteArray(7) + prefix + HlsFixtures.bytes("audio-0.ts"))

        assertEquals(HlsMediaKind.TRANSPORT_STREAM, HlsMediaSniffer.of(file, 7))
        assertEquals(7L + prefix.size, HlsMediaSniffer.transportStreamStart(file, 7))
        assertNull(HlsMediaSniffer.transportStreamStart(file(prefix), 0))
    }

    @Test
    fun `fragmented MP4 pieces are an MP4 already`() {
        val init = Mp4TestFiles.fragmented(Mp4TestFiles.video(fragments = 1, perFragment = 3))
        val segment = Mp4TestFiles.box("styp", "msdh".toByteArray()) + ByteArray(64)

        assertEquals(HlsMediaKind.ISO_MP4, HlsMediaSniffer.of(file(init), 0))
        assertEquals(HlsMediaKind.ISO_MP4, HlsMediaSniffer.of(file(segment), 0))
        assertEquals(null to null, HlsMediaKind.ISO_MP4.kept())
    }

    @Test
    fun `packed sound is AAC or MP3, after its ID3 tags`() {
        val aac = Mp4TestFiles.adts(Mp4TestFiles.audio(fragments = 1, perFragment = 4))
        val mp3 = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64) + ByteArray(413)
        val tag = id3Tag(body = 30)

        assertEquals(HlsMediaKind.ADTS_AUDIO, HlsMediaSniffer.of(file(aac), 0))
        assertEquals(HlsMediaKind.ADTS_AUDIO, HlsMediaSniffer.of(file(tag + aac), 0))
        assertEquals(HlsMediaKind.MPEG_AUDIO, HlsMediaSniffer.of(file(tag + mp3), 0))
        assertEquals("aac" to "audio/aac", HlsMediaKind.ADTS_AUDIO.kept())
        assertEquals("mp3" to "audio/mpeg", HlsMediaKind.MPEG_AUDIO.kept())
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(HlsMediaKind.UNKNOWN, HlsMediaSniffer.of(file("#EXTM3U\n".toByteArray()), 0))
        assertEquals(HlsMediaKind.UNKNOWN, HlsMediaSniffer.of(file(ByteArray(0)), 0))
        assertEquals(HlsMediaKind.UNKNOWN, HlsMediaSniffer.of(file(Random(6).nextBytes(900)), 0))
        assertEquals(HlsMediaKind.UNKNOWN, HlsMediaSniffer.of(folder.root.resolve("gone"), 0))
    }

    private fun HlsMediaKind.kept(): Pair<String?, String?> = keptExtension to keptMimeType

    private fun file(bytes: ByteArray) = folder.newFile().apply { writeBytes(bytes) }

    private fun id3Tag(body: Int): ByteArray =
        byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0, 0, 0, 0, body.toByte()) + ByteArray(body)
}
