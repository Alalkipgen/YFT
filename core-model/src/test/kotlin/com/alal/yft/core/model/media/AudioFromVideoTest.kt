package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFromVideoTest {
    private val context = BrowserRequestContext("https://www.facebook.com/reel/1/", null, null)

    private fun video(
        codecs: List<String> = listOf("avc1.4d401f", "mp4a.40.2"),
        mimeType: String? = "video/mp4",
        trackType: MediaTrackType = MediaTrackType.AUDIO_VIDEO,
        audioKbps: Long? = 128,
    ) = MediaVariant(
        id = "direct-0",
        playbackUrl = "https://video.example.test/sd.mp4?oh=secret",
        kind = MediaKind.DIRECT,
        trackType = trackType,
        requestContext = context,
        label = "360p",
        mimeType = mimeType,
        container = "MP4",
        codecs = codecs,
        width = 640,
        height = 360,
        durationMillis = 60_000,
        sizeBytes = 9_000_000,
        sizeAccuracy = MediaSizeAccuracy.EXACT,
        audioBitrateBitsPerSecond = audioKbps?.times(1_000),
    )

    @Test
    fun theSoundOfAnMp4WithAacBecomesAnM4a() {
        val m4a = AudioFromVideo.of(video())!!

        assertEquals("direct-0-m4a", m4a.id)
        assertEquals(MediaTrackType.AUDIO, m4a.trackType)
        assertTrue(m4a.audioFromVideo)
        assertEquals("audio/mp4", m4a.mimeType)
        assertEquals("m4a", m4a.container)
        assertEquals(listOf("mp4a.40.2"), m4a.codecs)
        assertNull(m4a.height)
        // 60 s of 128 kbps sound is about 960 kB.
        assertEquals(960_000L, m4a.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, m4a.sizeAccuracy)
        assertNull(AudioFromVideo.of(video(audioKbps = null))!!.sizeBytes)
    }

    @Test
    fun mp3IsMadeFromTheVideosSoundToo() {
        val mp3 = Mp3Variants.of(AudioFromVideo.of(video())!!, 192)

        assertNotNull(mp3)
        assertTrue(mp3!!.audioFromVideo)
        assertEquals(Mp3Conversion(192, "direct-0-m4a"), mp3.mp3)
    }

    @Test
    fun onlyAnMp4WithAacSoundOrSoundOfAnUnstatedCodecQualifies() {
        // P25: other sites' MP4s and Facebook's HD/SD state no codecs; their sound is offered
        // and the phone checks that it is AAC when it copies it.
        listOf(emptyList(), listOf("avc1.4d401f")).forEach { codecs ->
            assertTrue("$codecs", AudioFromVideo.canExtract(video(codecs = codecs)))
            val m4a = AudioFromVideo.of(video(codecs = codecs))!!
            assertEquals(emptyList<String>(), m4a.codecs)
            assertNotNull(Mp3Variants.of(m4a, 128))
        }
        listOf("opus", "ac-3", "ec-3", "fLaC", ".mp3", "vorbis").forEach { sound ->
            assertFalse(sound, AudioFromVideo.canExtract(video(codecs = listOf("avc1", sound))))
        }
        val webm = video(mimeType = "video/webm").copy(container = "WebM")
        assertFalse(AudioFromVideo.canExtract(webm))
        assertFalse(AudioFromVideo.canExtract(video(trackType = MediaTrackType.VIDEO)))
        assertNull(AudioFromVideo.of(AudioFromVideo.of(video())!!))
        assertThrows(IllegalArgumentException::class.java) {
            video().copy(audioFromVideo = true)
        }
        assertFalse(AudioFromVideo.of(video()).toString().contains("secret"))
    }
}
