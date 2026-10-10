package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R3 T7: QualityLadder. */
class QualityLadderTest {
    private fun track(
        name: String,
        width: Int? = null,
        height: Int? = null,
        codec: String? = null,
        bitrate: Long? = null,
        mime: String = "video/mp4",
    ) = MediaCandidate(
        pageUrl = "https://example.test/v",
        mediaUrl = "https://cdn.example.test/$name.mp4",
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
        mimeType = mime,
        codecs = listOfNotNull(codec),
        width = width,
        height = height,
        bitrateBitsPerSecond = bitrate,
    )

    @Test
    fun `best bitrate per size, AVC first, tallest first, capped`() {
        val tracks = listOf(
            track("avc720lo", 1280, 720, "avc1.64001F", 1_000),
            track("avc720hi", 1280, 720, "avc1.64001F", 2_000),
            track("av1720", 1280, 720, "av01.0.05M.08", 9_000),
            track("av11080", 1920, 1080, "av01.0.08M.08", 4_000),
            track("avc360", 640, 360, "avc1.4d401e", 500),
            track("aac", codec = "mp4a.40.2", bitrate = 128_000, mime = "audio/mp4"),
        )
        assertEquals(
            listOf("avc720hi", "avc360"),
            QualityLadder.videos(tracks).map { it.mediaUrl.substringAfterLast('/').removeSuffix(".mp4") },
        )
        val wide = QualityLadder.videos(tracks, mergeable = { it != QualityLadder.Family.OTHER })
        assertEquals(listOf("av11080", "avc720hi", "avc360"), wide.map { it.mediaUrl.substringAfterLast('/').removeSuffix(".mp4") })
        assertEquals(1, QualityLadder.videos(tracks, cap = 1).size)
        assertEquals("aac", QualityLadder.bestAudio(tracks)?.mediaUrl?.substringAfterLast('/')?.removeSuffix(".mp4"))
        assertNull(QualityLadder.bestAudio(tracks.filter { it.mimeType == "video/mp4" }))
    }

    @Test
    fun `merged keeps each encoding once and joined adds only new size and codec slots`() {
        val first = listOf(track("a720", 720, 1280, "avc1", 2_000))
        val second = listOf(
            track("b720", 720, 1280, "avc1", 2_000), // same encoding at a new address
            track("a720", 720, 1280, "avc1", 1),     // same address
            track("b540", 540, 960, "avc1", 900),
        )
        assertEquals(2, QualityLadder.merged(first, second).size)
        val joined = QualityLadder.joined(first, listOf(track("c720", 720, 1280, "avc1", 1_500), track("c1080", 1080, 1920, "hvc1", 3_000)))
        assertEquals(listOf("c1080", "a720"), joined.map { it.mediaUrl.substringAfterLast('/').removeSuffix(".mp4") })
        assertEquals("1080p", QualityLadder.qualityName(track("x", 1660, 1078)))
        assertEquals("360p", QualityLadder.qualityName(track("x", 552, 358)))
        assertNull(QualityLadder.qualityName(track("x")))
    }
}
