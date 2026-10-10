package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.verify.FocusSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureLayerMseTest {
    private val page = "https://page.test/watch"

    @Test
    fun anAddressFedToTheFocusedPlayerIsItsVideoWithTheStatedSize() {
        val snapshot = PageSnapshot(
            page, 1,
            requests = listOf(
                CapturedRequest(
                    "https://cdn.test/v/720.mp4", mimeType = "video/mp4; codecs=\"avc1.64001f\"",
                    fedPlayer = true, width = 1280, height = 720,
                ),
                CapturedRequest("https://cdn.test/ad.mp4", mimeType = "video/mp4"),
            ),
            playingMediaUrl = "blob:https://page.test/1",
        )
        val request = MasterRequest(page, 1, 1_000, SiteExtractionFailure.NO_MEDIA_FOUND, "abc")

        val (fed, other) = CaptureLayer().collect(request, snapshot).candidates

        assertEquals(PageMediaRole.MAIN, fed.pageRole)
        assertEquals(listOf("avc1.64001f"), fed.codecs)
        assertEquals(1280 to 720, fed.width to fed.height)
        assertEquals(true, fed.videoId?.endsWith("abc"))
        assertEquals(CaptureLayer.FED_PLAYER_KEY, fed.pageVideoKey)
        assertNull(other.pageRole)
        assertNull(other.videoId)
        assertNull(other.width)
    }

    @Test
    fun aPlayersFedTracksAndQualitiesAreOneFocusedVideo() {
        val fed = listOf("v/480.mp4", "v/720.mp4", "a/audio.m4a").map {
            CapturedRequest("https://cdn.test/$it", fedPlayer = true)
        }
        val snapshot = PageSnapshot(page, 1, requests = fed, playingMediaUrl = "blob:x")
        val request = MasterRequest(page, 1, 1_000, SiteExtractionFailure.NO_MEDIA_FOUND)
        val found = CaptureLayer().collect(request, snapshot).candidates

        assertEquals(3, FocusSelection.select(found, snapshot).size)
    }
}
