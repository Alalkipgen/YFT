package com.alal.yft.core.media.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4HeaderParserTest {
    @Test
    fun aFastStartFileStatesItsPictureAndSoundInOneRead() = runTest {
        val bytes = Mp4Fixtures.file()
        val reader = Mp4Fixtures.Reader(bytes)

        val header = Mp4HeaderParser.parse(reader, bytes.size.toLong())!!

        assertEquals(1280, header.width)
        assertEquals(720, header.height)
        assertEquals(29.97, header.framesPerSecond!!, 0.001)
        assertEquals(61_000L, header.durationMillis)
        assertEquals("avc1", header.videoCodec)
        assertEquals("mp4a.40.2", header.audioCodec)
        assertEquals(128_000L, header.audioBitrateBitsPerSecond)
        assertTrue(header.hasVideo)
        assertTrue(header.hasAudio)
        assertEquals(1, reader.reads)
    }

    @Test
    fun aMoovAfterTheMediaCostsOneMoreRead() = runTest {
        val bytes = Mp4Fixtures.file(
            width = 640,
            height = 360,
            moovAtEnd = true,
            mediaBytes = 400_000,
        )
        val reader = Mp4Fixtures.Reader(bytes)

        val header = Mp4HeaderParser.parse(reader, bytes.size.toLong())!!

        assertEquals(360, header.height)
        assertEquals(2, reader.reads)
        assertTrue(reader.offsets.last() > Mp4HeaderParser.FIRST_READ_BYTES)
    }

    @Test
    fun aSilentVideoHasNoSound() = runTest {
        val bytes = Mp4Fixtures.file(audio = false)

        val header = Mp4HeaderParser.parse(Mp4Fixtures.Reader(bytes), bytes.size.toLong())!!

        assertTrue(header.hasVideo)
        assertFalse(header.hasAudio)
        assertNull(header.audioCodec)
    }

    @Test
    fun anythingElseIsNotRead() = runTest {
        val html = "<!doctype html><html></html>".toByteArray()
        assertNull(Mp4HeaderParser.parse(Mp4Fixtures.Reader(html), html.size.toLong()))

        // A box that claims to be larger than the file ends the walk.
        val broken = Mp4Fixtures.box("ftyp", ByteArray(8)) +
            byteArrayOf(0x7F, 0, 0, 0) + "mdat".toByteArray()
        assertNull(Mp4HeaderParser.parse(Mp4Fixtures.Reader(broken), broken.size.toLong()))

        // A server that answers nothing costs one read.
        val silent = Mp4Fixtures.Reader(ByteArray(0))
        assertNull(Mp4HeaderParser.parse(silent, null))
        assertEquals(1, silent.reads)
    }

    @Test
    fun realFfmpegFilesAreReadEitherWay() = runTest {
        listOf("mp4/clip-video-aac.mp4", "mp4/clip-moov-end.mp4").forEach { name ->
            val bytes = javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

            val header = Mp4HeaderParser.parse(Mp4Fixtures.Reader(bytes), bytes.size.toLong())!!

            assertEquals(name, 160, header.width)
            assertEquals(name, 120, header.height)
            assertEquals(name, 15.0, header.framesPerSecond!!, 0.01)
            assertEquals(name, "mp4v", header.videoCodec)
            assertEquals(name, "mp4a.40.2", header.audioCodec)
            assertTrue(name, header.audioBitrateBitsPerSecond!! in 30_000L..80_000L)
            assertTrue(name, header.durationMillis!! in 900L..1_100L)
        }
    }
}
