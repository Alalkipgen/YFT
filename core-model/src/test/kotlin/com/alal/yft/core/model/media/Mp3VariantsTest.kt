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

    private fun asset(
        vararg variants: MediaVariant,
        duration: Long? = null,
        page: String = YOUTUBE_PAGES.first(),
    ) = MediaAsset(
        sourcePageUrl = page,
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
        // 320 kbps is offered too (P3): 60 s is 2.4 MB.
        assertEquals(2_400_000L, Mp3Variants.of(audio(), 320)!!.sizeBytes)
        assertNull(Mp3Variants.of(audio(durationMillis = null), 128)!!.sizeBytes)
        val fromAsset = Mp3Variants.of(audio(durationMillis = null), 128, 60_000)
        assertEquals(960_000L, fromAsset!!.sizeBytes)
    }

    @Test
    fun youTubeOffersEveryBitrateAndEverySiteThatIsNotYouTubeOnly192() {
        // D2 (the owner): the rule is "not YouTube", so VK gets it with no VK code.
        YOUTUBE_PAGES.forEach { page ->
            assertEquals(page, listOf(320, 192, 128), Mp3Variants.bitratesFor(page))
        }
        OTHER_PAGES.forEach { page ->
            assertEquals(page, listOf(192), Mp3Variants.bitratesFor(page))
        }
        // Neither a look-alike host nor an unreadable address is YouTube.
        listOf(
            "https://notyoutube.com/watch?v=1",
            "https://youtube.com.example.test/watch?v=1",
            "not a page",
        ).forEach { assertEquals(it, listOf(192), Mp3Variants.bitratesFor(it)) }
        // A chosen row is still made at its own bitrate, whatever its page.
        Mp3Variants.BITRATES_KBPS.forEach {
            assertEquals(it, Mp3Variants.of(audio(), it)?.mp3?.bitrateKbps)
        }
    }

    @Test
    fun addToPutsOnly192AfterTheBestAacFileOnEverySiteThatIsNotYouTube() {
        val best = audio(id = "best", kbps = 128)
        OTHER_PAGES.forEach { page ->
            val added = Mp3Variants.addTo(asset(video(), best, page = page))
            assertEquals(page, listOf("720p", "best", "best-mp3-192"), added.variants.map { it.id })
            assertSame(page, added, Mp3Variants.addTo(added))
        }
    }

    @Test
    fun addToPutsEveryBitrateAfterTheBestAacFile() {
        val low = audio(id = "low", kbps = 48)
        val best = audio(id = "best", kbps = 128)
        val opus = audio(id = "opus", mimeType = "audio/webm", codecs = listOf("opus"), kbps = 160)

        val added = Mp3Variants.addTo(asset(video(), low, best, opus))

        assertEquals(
            listOf(
                "720p", "low", "best", "best-mp3-320", "best-mp3-192", "best-mp3-128", "opus",
            ),
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

    private companion object {
        val YOUTUBE_PAGES = listOf(
            "https://m.youtube.com/watch?v=fixture0001",
            "https://www.youtube.com/watch?v=fixture0001",
            "https://music.youtube.com/watch?v=fixture0001",
            "https://youtu.be/fixture0001",
            "https://www.youtube-nocookie.com/embed/fixture0001",
        )
        val OTHER_PAGES = listOf(
            "https://www.facebook.com/watch/?v=25",
            "https://fb.watch/fixture25/",
            "https://www.instagram.com/reel/fixture25/",
            "https://x.com/someone/status/25",
            "https://twitter.com/someone/status/25",
            "https://www.tiktok.com/@someone/video/25",
            "https://vk.com/video-25_25",
            "https://vkvideo.ru/video-25_25",
            "https://example.test/watch",
        )
    }
}
