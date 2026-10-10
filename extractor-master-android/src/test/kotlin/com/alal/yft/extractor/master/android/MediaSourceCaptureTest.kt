package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7: MSE facts from `yft-master-mse.js` through the frame, the session and the engine. */
class MediaSourceCaptureTest {
    private val page = "https://page.test/watch"
    private val fed = "https://cdn.test/media/main"
    private val other = "https://cdn.test/ads/clip.mp4"

    @Test
    fun fedAddressesKeepTheirFlagAndSizeOnlyWhenFed() {
        val frame = CaptureFrameReader.read(
            """{"pageUrl":"$page","generation":1,"requests":[
              {"url":"$fed","mime":"video/mp4; codecs=\"avc1.4d401f\"","fed":true,
               "width":854,"height":480},
              {"url":"$other","mime":"video/mp4","width":1920,"height":1080},
              {"url":"$fed","fed":true,"width":0,"height":99999}]}""",
        )!!
        val (first, second, third) = frame.requests
        assertTrue(first.fed)
        assertEquals(854 to 480, first.width to first.height)
        assertFalse(second.fed)
        assertNull(second.width)
        assertNull(third.width)
        assertNull(third.height)
    }

    @Test
    fun theFedAddressIsTheFocusedVideosWithItsCodecAndSize() = runTest {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        val requests = listOf(
            FrameRequest(fed, "video/mp4; codecs=\"avc1.4d401f\"", false, true, 854, 480),
            FrameRequest(other, "video/mp4", false),
            FrameRequest("https://cdn.test/preview.mp4", "video/mp4", true, true, 320, 180),
        )
        session.accept(frame(scope, 1.0, requests), 100)
        session.accept(frame(scope, 1.2, requests), 300)
        val snapshot = (session.snapshot(request(scope)) as CaptureResult.Available).snapshot
        val recorded = snapshot.requests.distinctBy { it.url }
        assertEquals(listOf(true, false, false), recorded.map { it.fedPlayer })
        assertEquals(854 to 480, recorded[0].width to recorded[0].height)
        assertEquals(PageMediaRole.PREVIEW, recorded[2].pageRole)

        val checked = mutableListOf<String>()
        val validator = MasterMediaValidator { candidate, _ ->
            checked += candidate.mediaUrl
            ValidationResult.Valid(candidate.copy(contentLengthBytes = 4_096))
        }
        val capture = PlaybackCaptureProvider { session.snapshot(it) }
        val result = MasterFallbackEngine(validator, capture, MasterPolicy(enabled = true))
            .extract(request(scope)) as MasterResult.Success

        val main = result.result.candidates.single { it.mediaUrl == fed }
        assertEquals(PageMediaRole.MAIN, main.pageRole)
        assertEquals(listOf("avc1.4d401f"), main.codecs)
        assertEquals(854 to 480, main.width to main.height)
        assertFalse(other in result.result.candidates.map { it.mediaUrl })
    }

    @Test
    fun aLicenceRequestStopsTheCaptureWithDrmProtected() = runTest {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        val requests = listOf(FrameRequest(fed, "video/mp4", false, true, 854, 480))
        session.accept(frame(scope, 1.0, requests).copy(protected = true), 100)
        session.accept(frame(scope, 1.2, requests), 300)
        var probes = 0
        val validator = MasterMediaValidator { candidate, _ ->
            probes++
            ValidationResult.Valid(candidate)
        }
        val result = MasterFallbackEngine(
            validator, PlaybackCaptureProvider { session.snapshot(it) }, MasterPolicy(enabled = true),
        ).extract(request(scope))

        assertEquals(SiteExtractionFailure.DRM_PROTECTED, (result as MasterResult.Failure).reason)
        assertEquals(0, probes)
    }

    private fun request(scope: BrowserCaptureScope) = MasterRequest(
        scope.pageUrl, scope.generation, 1_000, SiteExtractionFailure.NO_MEDIA_FOUND,
    )

    private fun frame(scope: BrowserCaptureScope, time: Double, requests: List<FrameRequest>) =
        CaptureFrame(
            pageUrl = scope.pageUrl,
            generation = scope.generation,
            html = null,
            payloads = emptyList(),
            requests = requests,
            player = FramePlayer("video:0", "blob:https://page.test/1", time, 4, false, true),
            protected = false,
        )
}
