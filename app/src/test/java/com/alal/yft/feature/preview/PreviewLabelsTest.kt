package com.alal.yft.feature.preview

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewLabelsTest {
    @Test
    fun videoQualityNamesFollowTheDesign() {
        assertEquals("2160p · 4K", video(height = 2_160).qualityLabel())
        assertEquals("1440p · 2K", video(height = 1_440).qualityLabel())
        assertEquals("1080p · Full HD", video(height = 1_080).qualityLabel())
        assertEquals("720p60 · HD", video(height = 720, fps = 59.94).qualityLabel())
        assertEquals("720p · HD", video(height = 720, fps = 30.0).qualityLabel())
        assertEquals("480p", video(height = 480).qualityLabel())
        assertEquals("360p · Data saver", video(height = 360).qualityLabel())
        assertEquals("Auto", video(label = " Auto ").qualityLabel())
        assertEquals("Quality unknown", video().qualityLabel())
    }

    @Test
    fun audioNamesShowBitrateAndLanguageWhenKnown() {
        assertEquals(
            "128 kbps · English",
            audio(label = "English", bitrate = 128_000).qualityLabel(),
        )
        assertEquals("en", audio(language = "en").qualityLabel())
        assertEquals(
            "AAC 128 kbps",
            audio(label = "AAC 128 kbps", bitrate = 128_000).qualityLabel(),
        )
        assertEquals("96 kbps", audio(bitrate = 96_000).qualityLabel())
        assertEquals("Audio track", audio().qualityLabel())
    }

    @Test
    fun rowsThatWouldReadTheSameAddTheirBitrate() {
        val high = video(id = "high", height = 720, bitrate = 2_500_000)
        val low = video(id = "low", height = 720, bitrate = 1_200_000)
        val other = video(id = "other", height = 480, bitrate = 900_000)

        val labels = listOf(high, low, other).qualityLabels()

        assertEquals("720p · HD · 2.50 Mbps", labels["high"])
        assertEquals("720p · HD · 1.20 Mbps", labels["low"])
        assertEquals("480p", labels["other"])
    }

    @Test
    fun sizesAreExactEstimatedOrLeftOut() {
        val exact = video().copy(sizeBytes = 96L * MIB, sizeAccuracy = MediaSizeAccuracy.EXACT)
        val estimated = exact.copy(sizeAccuracy = MediaSizeAccuracy.ESTIMATED)

        assertEquals("96 MB", exact.sizeText())
        assertEquals("~96 MB", estimated.sizeText())
        assertNull(video().sizeText())
        assertEquals("Download · 96 MB", exact.downloadLabel())
        assertEquals("Download · ~96 MB", estimated.downloadLabel())
        assertEquals("Download", video().downloadLabel())
    }

    @Test
    fun sourceLineNamesTheSiteAndWhatTheFileHolds() {
        val both = video().copy(trackType = MediaTrackType.AUDIO_VIDEO)

        assertEquals(
            "archive.org · Video + audio",
            sourceLine(asset("https://www.archive.org/details/mountain-lake"), both),
        )
        assertEquals("example.test · Audio", sourceLine(asset("https://example.test/a"), audio()))
        assertEquals("Video", sourceLine(asset("not a url"), video()))
    }

    @Test
    fun timeLabelShowsLengthFirstThenElapsedOfTotal() {
        assertEquals("4:12", timeLabel(started = false, positionMs = 0, durationMs = 252_000))
        assertEquals(
            "0:12 / 4:12",
            timeLabel(started = true, positionMs = 12_000, durationMs = 252_000),
        )
        assertEquals("0:05", timeLabel(started = true, positionMs = 5_000, durationMs = 0))
        assertNull(timeLabel(started = false, positionMs = 0, durationMs = 0))
    }

    private fun video(
        id: String = "video",
        height: Int? = null,
        fps: Double? = null,
        bitrate: Long? = null,
        label: String? = null,
    ) = variant(id, MediaTrackType.VIDEO).copy(
        height = height,
        framesPerSecond = fps,
        bitrateBitsPerSecond = bitrate,
        label = label,
    )

    private fun audio(label: String? = null, bitrate: Long? = null, language: String? = null) =
        variant("audio", MediaTrackType.AUDIO).copy(
            label = label,
            bitrateBitsPerSecond = bitrate,
            language = language,
        )

    private fun variant(id: String, trackType: MediaTrackType) = MediaVariant(
        id = id,
        playbackUrl = "https://media.example.test/$id.mp4",
        kind = MediaKind.DIRECT,
        trackType = trackType,
        requestContext = BrowserRequestContext(null, "fixture-agent", null),
    )

    private fun asset(pageUrl: String) = MediaAsset(
        sourcePageUrl = pageUrl,
        title = "Mountain Lake 4K",
        thumbnailUrl = null,
        durationMillis = 252_000,
        variants = listOf(variant("video", MediaTrackType.AUDIO_VIDEO)),
        resolvedAtEpochMs = 1,
    )

    private companion object {
        const val MIB = 1_024L * 1_024
    }
}
