package com.alal.yft.core.media.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebmHeaderParserTest {
    @Test
    fun theOwnersCommonsOriginalStatesItsVp8PictureLengthAndSound() {
        // The first 1024 bytes of Wikimedia Commons' File:Big_Buck_Bunny_4K.webm: VP8,
        // 4000 x 2250, 59.94 fps, Vorbis sound, 634.553 s. Its rows were "Quality unknown" before.
        val header = WebmHeaderParser.parse(commonsOriginal())!!

        assertEquals(4000, header.width)
        assertEquals(2250, header.height)
        // The file's DefaultDuration is 16666 ns, which would be 60002 fps: not a frame duration
        // any camera states, so YFT states no rate rather than a wrong one.
        assertNull(header.framesPerSecond)
        assertEquals(634_553L, header.durationMillis)
        assertEquals("vp8", header.videoCodec)
        assertEquals("vorbis", header.audioCodec)
        assertTrue(header.hasVideo)
        assertTrue(header.hasAudio)
    }

    @Test
    fun aTruncatedHeaderStillStatesWhatItHolds() {
        // The same file cut inside its video track: the picture it states is kept, and the sound
        // track it does not reach is not invented.
        val bytes = commonsOriginal().copyOf(468)

        val header = WebmHeaderParser.parse(bytes)!!

        assertEquals(4000, header.width)
        assertEquals(2250, header.height)
        assertEquals("vp8", header.videoCodec)
        assertNull(header.audioCodec)
        assertFalse(header.hasAudio)
    }

    @Test
    fun aWebmWithoutTracksStatesOnlyItsLength() {
        val header = WebmHeaderParser.parse(
            WebmFixtures.file(video = false, audio = false, durationMillis = 1_500),
        )!!

        assertNull(header.width)
        assertNull(header.height)
        assertNull(header.videoCodec)
        assertEquals(1_500L, header.durationMillis)
        assertFalse(header.hasVideo)
    }

    @Test
    fun aTrackOfTheFixturesStatesItsOwnCodecAndPicture() {
        val header = WebmHeaderParser.parse(WebmFixtures.file(framesPerSecond = 24))!!

        assertEquals(1920, header.width)
        assertEquals(1080, header.height)
        assertEquals(24.0, header.framesPerSecond!!, 0.001)
        assertEquals("vp9", header.videoCodec)
        assertEquals("opus", header.audioCodec)
        assertEquals(61_000L, header.durationMillis)
    }

    @Test
    fun aMatroskaDocTypeIsReadLikeAWebm() {
        val header = WebmHeaderParser.parse(WebmFixtures.file(docType = "matroska"))!!

        assertEquals(1920, header.width)
        assertEquals(1080, header.height)
    }

    @Test
    fun anMp4AnHtmlPageAndGarbageAreNotWebm() {
        assertNull(WebmHeaderParser.parse(Mp4Fixtures.file()))
        assertNull(WebmHeaderParser.parse("<!doctype html><html>no media</html>".toByteArray()))
        assertNull(WebmHeaderParser.parse(ByteArray(64)))
        assertNull(
            WebmHeaderParser.parse(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())),
        )
    }

    /**
     * The first 1024 bytes of the owner's file
     * (`https://upload.wikimedia.org/wikipedia/commons/c/c0/Big_Buck_Bunny_4K.webm`), kept as
     * base64 so the fixture stays a text file in the repository.
     */
    private fun commonsOriginal(): ByteArray {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/fixtures/commons-original-1024.webm.b64"),
        ) { "Missing fixture commons-original-1024.webm.b64" }
        val text = resource.use { it.readBytes().toString(Charsets.US_ASCII) }
        return java.util.Base64.getMimeDecoder().decode(text)
    }
}
