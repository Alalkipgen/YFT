package com.alal.yft.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P6: 2K and 4K rows are checked against the phone's decoders before they are downloaded. */
class VideoPlaybackSupportTest {
    @Test
    fun codecsNameTheDecoderTheyNeed() {
        fun type(vararg codecs: String) =
            DeviceVideoPlaybackSupport.decoderMimeTypeFor(codecs.toList())

        assertEquals(VP9, type("vp9"))
        assertEquals(VP9, type("vp09.00.51.08"))
        assertEquals(AV1, type("av01.0.12M.08"))
        assertEquals("video/avc", type("avc1.640028", "mp4a.40.2"))
        assertEquals("video/hevc", type("hvc1.1.6.L150.90"))
        assertNull(type("mp4a.40.2"))
        assertNull(type("vp8"))
        assertNull(type("vp99"))
    }

    @Test
    fun aVideoPlaysOnlyWhereADecoderOfItsCodecTakesItsSize() {
        val upTo1080 = decoder(VP9, maxLong = 1_920, maxShort = 1_080)
        val upTo4k = decoder(AV1, maxLong = 3_840, maxShort = 2_160)
        val support = DeviceVideoPlaybackSupport { listOf(upTo1080, upTo4k) }

        assertFalse(support.canPlay(variant("vp9", 3_840, 2_160)))
        assertTrue(support.canPlay(variant("vp9", 1_920, 1_080)))
        // A portrait video fits a decoder the other way up.
        assertTrue(support.canPlay(variant("vp9", 1_080, 1_920)))
        assertTrue(support.canPlay(variant("av01.0.12M.08", 3_840, 2_160)))
        assertTrue(support.canPlay(variant("av01.0.12M.08", 2_160, 3_840)))
        // No AVC or HEVC decoder is listed at all.
        assertFalse(support.canPlay(variant("avc1.640033", 1_280, 720)))
        // Without a width the height alone decides.
        assertFalse(support.canPlay(variant("vp9", null, 2_160)))
        assertTrue(support.canPlay(variant("vp9", null, 1_080)))
    }

    @Test
    fun whatCannotBeCheckedCountsAsPlayable() {
        var reads = 0
        val none = DeviceVideoPlaybackSupport { reads += 1; emptyList() }
        val some = DeviceVideoPlaybackSupport { listOf(decoder(VP9, 1_920, 1_080)) }

        // The phone's list could not be read: no warning anywhere.
        repeat(2) { assertTrue(none.canPlay(variant("vp9", 3_840, 2_160))) }
        assertEquals(1, reads)
        // An unknown codec, no codec or no size is never warned about.
        assertTrue(some.canPlay(variant("dvh1.05.06", 3_840, 2_160)))
        assertTrue(some.canPlay(variant(null, 3_840, 2_160)))
        assertTrue(some.canPlay(variant("vp9", 3_840, null)))
        assertTrue(VideoPlaybackSupport.ANY.canPlay(variant("vp9", 7_680, 4_320)))
    }

    private fun decoder(mimeType: String, maxLong: Int, maxShort: Int) = VideoDecoder(
        mimeType = mimeType,
        playsSize = { width, height -> width <= maxLong && height <= maxShort },
        playsHeight = { height -> height <= maxLong },
    )

    private fun variant(codec: String?, width: Int?, height: Int?) = MediaVariant(
        id = "video",
        playbackUrl = "https://media.example.test/video.webm",
        kind = MediaKind.DIRECT,
        trackType = MediaTrackType.AUDIO_VIDEO,
        requestContext = BrowserRequestContext("https://page.example.test/", null, null),
        mimeType = "video/webm",
        codecs = listOfNotNull(codec),
        width = width,
        height = height,
    )

    private companion object {
        const val VP9 = "video/x-vnd.on2.vp9"
        const val AV1 = "video/av01"
    }
}
