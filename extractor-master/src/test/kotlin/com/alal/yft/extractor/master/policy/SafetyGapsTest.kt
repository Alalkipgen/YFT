package com.alal.yft.extractor.master.policy

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.fixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1 R2: the engine's stops before any capture widening. */
class SafetyGapsTest {
    private val enabled = MasterPolicy(enabled = true)
    private val media = "https://cdn.example.test/main.mp4"

    @Test
    fun `a walled site's bot check makes no capture and no media check`() = runTest {
        var captures = 0
        val probe = RecordingValidator()
        val capture = PlaybackCaptureProvider {
            captures++
            CaptureResult.Available(playing(it.pageUrl))
        }
        listOf(YOUTUBE, SHORT, REDDIT, REDDIT_MEDIA).forEach { page ->
            listOf(
                SiteExtractionFailure.BOT_CHECK,
                SiteExtractionFailure.LOGIN_REQUIRED,
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            ).forEach { failure ->
                val request = MasterRequest(page, 1, NOW, failure, null, snapshot = playing(page))
                assertEquals(
                    MasterResult.Skipped(failure),
                    MasterFallbackEngine(probe, capture, enabled).extract(request),
                )
            }
        }
        assertEquals(0, captures)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `the same bot check elsewhere still reads the user's own playback`() = runTest {
        val page = "https://example.test/watch/fixture"
        val result = MasterFallbackEngine(RecordingValidator(), policy = enabled).extract(
            MasterRequest(
                page, 1, NOW, SiteExtractionFailure.BOT_CHECK, null, snapshot = playing(page),
            ),
        )
        assertTrue(result is MasterResult.Success)
    }

    @Test
    fun `a YouTube payload yields no Master rows and no media check`() = runTest {
        val probe = RecordingValidator()
        val captures = mutableListOf<String>()
        val capture = PlaybackCaptureProvider {
            captures += it.pageUrl
            CaptureResult.Available(PageSnapshot(it.pageUrl, 1))
        }
        val payload = PageSnapshot(
            YOUTUBE, 1, apiResponses = listOf(fixture("youtube-player.json")),
        )
        val result = MasterFallbackEngine(probe, capture, enabled).extract(
            MasterRequest(
                YOUTUBE, 1, NOW, SiteExtractionFailure.RESPONSE_CHANGED, "yt-fixture",
                snapshot = payload,
            ),
        )
        assertFalse(result is MasterResult.Success)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `a DRM signal returns DRM_PROTECTED before any media check`() = runTest {
        val probe = RecordingValidator()
        var captures = 0
        val capture = PlaybackCaptureProvider { captures++; CaptureResult.Unavailable }
        val page = "https://example.test/watch/fixture"
        // The browser saw EME keys or an encrypted event on the playing video.
        val eme = playing(page).copy(accessFailure = SiteExtractionFailure.DRM_PROTECTED)
        // The page's own player data states DRM.
        val stated = PageSnapshot(
            page, 1,
            apiResponses = listOf(
                """{"videoDetails":{"videoId":"a"},"streamingData":""" +
                    """{"drmFamilies":["WIDEVINE"],""" +
                    """"formats":[{"url":"$media","mimeType":"video/mp4"}]}}""",
            ),
            requests = listOf(CapturedRequest(media, mimeType = "video/mp4")),
            playingMediaUrl = media,
            authorizedPlayback = true,
        )
        listOf(eme, stated).forEach { snapshot ->
            val result = MasterFallbackEngine(probe, capture, enabled).extract(
                MasterRequest(
                    page, 1, NOW, SiteExtractionFailure.RESPONSE_CHANGED, null,
                    snapshot = snapshot,
                ),
            )
            assertEquals(
                SiteExtractionFailure.DRM_PROTECTED,
                (result as MasterResult.Failure).reason,
            )
        }
        assertEquals(0, captures)
        assertTrue(probe.seen.isEmpty())
    }

    private fun playing(page: String) = PageSnapshot(
        page, 1,
        requests = listOf(CapturedRequest(media, mimeType = "video/mp4")),
        playingMediaUrl = media,
        authorizedPlayback = true,
    )

    private companion object {
        const val YOUTUBE = "https://www.youtube.com/watch?v=yt-fixture"
        const val SHORT = "https://youtu.be/yt-fixture"
        const val REDDIT = "https://www.reddit.com/r/fixture/comments/abc/clip/"
        const val REDDIT_MEDIA = "https://v.redd.it/abc"
    }
}
