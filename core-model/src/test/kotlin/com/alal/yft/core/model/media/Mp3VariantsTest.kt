package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3VariantsTest {
    private val context = BrowserRequestContext("https://example.test/watch", null, null)

    private fun audio(
        id: String = "m4a",
        mimeType: String? = "audio/mp4",
        codecs: List<String> = listOf("mp4a.40.2"),
        kbps: Long? = 128,
        kind: MediaKind = MediaKind.DIRECT,
        durationMillis: Long? = 60_000,
    ) = MediaVariant(
        id = id,
        playbackUrl = "https://rr1.example.test/$id?sig=secret",
        kind = kind,
        trackType = MediaTrackType.AUDIO,
        requestContext = context,
        mimeType = mimeType,
        codecs = codecs,
        bitrateBitsPerSecond = kbps?.times(1_000),
        durationMillis = durationMillis,
        sizeBytes = 960_000,
        sizeAccuracy = MediaSizeAccuracy.EXACT,
    )

    private fun video(id: String = "720p") = MediaVariant(
        id = id,
        playbackUrl = "https://rr1.example.test/$id",
        kind = MediaKind.DIRECT,
        trackType = MediaTrackType.AUDIO_VIDEO,
        requestContext = context,
        mimeType = "video/mp4",
        height = 720,
    )

    private fun asset(vararg variants: MediaVariant, duration: Long? = null) = MediaAsset(
        sourcePageUrl = "https://example.test/watch",
        title = "Song",
        thumbnailUrl = null,
        durationMillis = duration,
        variants = variants.toList(),
        resolvedAtEpochMs = 0,
    )

    @Test
    fun onlyWholeAacAudioFilesCanBeConverted() {
        assertTrue(Mp3Variants.isAacSource(audio()))
        assertTrue(Mp3Variants.isAacSource(audio(codecs = emptyList())))
        assertTrue(Mp3Variants.isAacSource(audio(mimeType = "audio/aac", codecs = emptyList())))
        val opus = audio(mimeType = "audio/webm", codecs = listOf("opus"))
        assertFalse(Mp3Variants.isAacSource(opus))
        assertFalse(Mp3Variants.isAacSource(audio(codecs = listOf("ec-3"))))
        assertFalse(Mp3Variants.isAacSource(audio(mimeType = null, codecs = emptyList())))
        assertFalse(Mp3Variants.isAacSource(audio(kind = MediaKind.HLS)))
        assertFalse(Mp3Variants.isAacSource(video()))
        assertFalse(Mp3Variants.isAacSource(Mp3Variants.of(audio(), 192)!!))
    }

    @Test
    fun theMp3VariantDownloadsItsSourceAndEstimatesItsSize() {
        val mp3 = Mp3Variants.of(audio(), 192)!!

        assertEquals("m4a-mp3-192", mp3.id)
        assertEquals("https://rr1.example.test/m4a?sig=secret", mp3.playbackUrl)
        assertEquals("MP3 192 kbps", mp3.label)
        assertEquals("audio/mpeg", mp3.mimeType)
        assertEquals("mp3", mp3.container)
        assertEquals(listOf("mp3"), mp3.codecs)
        assertEquals(192_000L, mp3.bitrateBitsPerSecond)
        // 60 s at 192 kbps is 1.44 MB, an estimate.
        assertEquals(1_440_000L, mp3.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, mp3.sizeAccuracy)
        assertEquals(Mp3Conversion(192, "m4a"), mp3.mp3)
        assertNull(Mp3Variants.of(audio(), 160))
        assertNull(Mp3Variants.of(audio(durationMillis = null), 128)!!.sizeBytes)
        val fromAsset = Mp3Variants.of(audio(durationMillis = null), 128, 60_000)
        assertEquals(960_000L, fromAsset!!.sizeBytes)
    }

    @Test
    fun addToPutsBothBitratesAfterTheBestAacFile() {
        val low = audio(id = "low", kbps = 48)
        val best = audio(id = "best", kbps = 128)
        val opus = audio(id = "opus", mimeType = "audio/webm", codecs = listOf("opus"), kbps = 160)

        val added = Mp3Variants.addTo(asset(video(), low, best, opus))

        assertEquals(
            listOf("720p", "low", "best", "best-mp3-192", "best-mp3-128", "opus"),
            added.variants.map(MediaVariant::id),
        )
        assertSame(added, Mp3Variants.addTo(added))
    }

    @Test
    fun assetsWithoutAacAudioAreUnchanged() {
        val opus = audio(id = "opus", mimeType = "audio/webm", codecs = listOf("opus"))
        val original = asset(video(), opus)

        assertSame(original, Mp3Variants.addTo(original))
    }

    @Test
    fun onlyWholeAudioFilesCarryAnMp3Conversion() {
        assertThrows(IllegalArgumentException::class.java) {
            video().copy(mp3 = Mp3Conversion(192, "m4a"))
        }
        assertThrows(IllegalArgumentException::class.java) { Mp3Conversion(160, "m4a") }
        assertFalse(Mp3Variants.of(audio(), 128).toString().contains("secret"))
    }
}
