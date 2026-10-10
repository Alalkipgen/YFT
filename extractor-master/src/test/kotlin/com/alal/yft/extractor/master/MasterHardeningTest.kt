package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.capture.InMemoryCaptureStore
import com.alal.yft.extractor.master.layers.Evidence
import com.alal.yft.extractor.master.layers.LayerStack
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterHardeningTest {
    private val enabled = MasterPolicy(enabled = true)
    private val pair = """{"videoDetails":{"videoId":"pair"},"streamingData":""" +
        """{"adaptiveFormats":[""" +
        """{"url":"https://cdn.example.test/v720.mp4","height":720,""" +
        """"mimeType":"video/mp4; codecs=\"avc1.64001f\""},""" +
        """{"url":"https://cdn.example.test/sound.m4a",""" +
        """"mimeType":"audio/mp4; codecs=\"mp4a.40.2\""},""" +
        """{"url":"https://cdn.example.test/v1080.mp4","height":1080,""" +
        """"mimeType":"video/mp4; codecs=\"avc1.640028\""}]}}"""

    @Test
    fun `audio companion is checked once and reused for both video qualities`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(
            probe, policy = enabled.copy(maxCandidates = 3),
        ).extract(request(snapshot(pair), id = "pair")) as MasterResult.Success
        assertEquals(3, probe.seen.size)
        assertEquals(1, probe.seen.count { it.mediaUrl.endsWith("sound.m4a") })
        assertEquals(2, result.result.candidates.count { it.audioCompanion != null })
    }

    @Test
    fun `failed audio does not create a merged video success`() = runTest {
        val probe = RecordingValidator {
            if (it.mediaUrl.endsWith("sound.m4a")) {
                ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS)
            } else {
                ValidationResult.Valid(it)
            }
        }
        val result = MasterFallbackEngine(probe, policy = enabled)
            .extract(request(snapshot(pair), id = "pair"))
        assertFalse(result is MasterResult.Success)
        assertTrue(probe.seen.any { it.mediaUrl.endsWith("sound.m4a") })
    }

    @Test
    fun `companion shares the same total probe budget`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(
            probe, policy = enabled.copy(maxCandidates = 1),
        ).extract(request(snapshot(pair), id = "pair"))
        assertFalse(result is MasterResult.Success)
        assertEquals(1, probe.seen.size)
    }

    @Test
    fun `failed passive URL can be checked with newly captured origin credentials`() = runTest {
        val probe = RecordingValidator {
            if (it.requestContext.cookie == null) {
                ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS)
            } else {
                ValidationResult.Valid(it)
            }
        }
        val capture = PlaybackCaptureProvider {
            CaptureResult.Available(snapshot(
                playing = MEDIA,
                requests = listOf(CapturedRequest(
                    MEDIA, context = BrowserRequestContext(PAGE, null, "session=REDACTED"),
                )),
            ))
        }
        val result = MasterFallbackEngine(probe, capture, enabled)
            .extract(request(snapshot("""{"video_url":"$MEDIA"}"""))) as MasterResult.Success
        assertEquals(MasterStage.PLAYBACK_CAPTURE, result.stage)
        assertEquals(2, probe.seen.size)
    }

    @Test
    fun `preview evidence vetoes page metadata naming the same file as main`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe, policy = enabled).extract(request(snapshot(
            """{"video_url":"$MEDIA"}""", playing = MEDIA,
            requests = listOf(CapturedRequest(MEDIA, pageRole = PageMediaRole.PREVIEW)),
        )))
        assertFalse(result is MasterResult.Success)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `one HTML player can expose two valid source formats without a guessed ID`() = runTest {
        val html = """<video><source src='/one.mp4'><source src='/one.webm'></video>"""
        val result = MasterFallbackEngine(RecordingValidator(), policy = enabled).extract(
            request(PageSnapshot(PAGE, 1, html = html)),
        ) as MasterResult.Success
        assertEquals(2, result.result.candidates.size)
        assertTrue(result.result.candidates.all { it.videoId == null })
    }

    @Test
    fun `small explicitly identified media is checked rather than guessed to be an ad`() = runTest {
        val body = """{"video_url":"$MEDIA","contentLength":4000}"""
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe, policy = enabled)
            .extract(request(snapshot(body)))
        assertTrue(result is MasterResult.Success)
        assertEquals(1, probe.seen.size)
    }

    @Test
    fun `cross origin derived referer reveals only the page origin`() {
        val page = "$PAGE?private_page_value=REDACTED"
        val context = UrlPolicy.context(
            BrowserRequestContext(page, null, "session=REDACTED"), page, MEDIA, page,
        )
        assertEquals("https://example.test/", context.replayHeaders()["Referer"])
        assertNull(context.cookie)
    }

    @Test
    fun `captured original referer is replayed only to its original media origin`() {
        val referer = "$PAGE?private_page_value=REDACTED"
        val context = UrlPolicy.context(
            BrowserRequestContext(
                PAGE, null, "session=REDACTED", mapOf("Referer" to referer),
            ),
            MEDIA, MEDIA, PAGE, captured = true,
        )
        assertEquals(referer, context.replayHeaders()["Referer"])
        assertEquals(
            "https://example.test/",
            UrlPolicy.publicHeaders(context.replayHeaders())["Referer"],
        )
    }

    @Test
    fun `a mismatched page context never supplies another site's credentials`() {
        val input = request().copy(
            requestContext = BrowserRequestContext("https://other.test/", null, "REDACTED"),
        )
        val found = LayerStack.standard().collect(
            input, snapshot("""{"video_url":"https://example.test/local.mp4"}"""),
        ).candidates.single()
        assertNull(found.requestContext.cookie)
    }

    @Test
    fun `known site grouping uses the ordinary adapter namespace`() {
        assertEquals("youtube:observed", UrlPolicy.videoKey("https://m.youtube.com/", "observed"))
        assertEquals("facebook:observed", UrlPolicy.videoKey("https://fb.watch/code", "observed"))
        assertEquals("tiktok:observed", UrlPolicy.videoKey("https://www.tiktok.com/", "observed"))
        assertEquals("instagram:observed", UrlPolicy.videoKey("https://instagram.com/", "observed"))
        assertEquals("x:observed", UrlPolicy.videoKey("https://twitter.com/", "observed"))
    }

    @Test
    fun `private and regional TikTok statuses are not ignored by a generic walk`() {
        listOf(
            10216 to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            10231 to SiteExtractionFailure.GEO_RESTRICTED,
        ).forEach { (status, reason) ->
            val html = fixture("tiktok-reflow.html")
                .replace("\"statusCode\": 0", "\"statusCode\": $status")
            val found = LayerStack.standard().collect(
                request(id = "tt-fixture"), PageSnapshot(PAGE, 1, html = html),
            )
            assertEquals(reason, found.terminalFailure)
            assertTrue(found.candidates.isEmpty())
        }
    }

    @Test
    fun `private Instagram media is not a generic extraction success`() {
        val body = """{"shortcode":"one","is_private":true,"video_url":"$MEDIA"}"""
        val found = LayerStack.standard().collect(request(id = "one"), snapshot(body))
        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, found.terminalFailure)
        assertTrue(found.candidates.isEmpty())
    }

    @Test
    fun `debug wrappers do not expose signed paths or raw result fields`() {
        val media = candidate("https://cdn.example.test/SECRET_PATH.mp4?signature=REDACTED")
        val result = MasterResult.Success(
            SiteExtractionResult.Success(listOf(media)), MasterStage.PAGE_DATA,
        )
        assertFalse(result.toString().contains("SECRET_PATH"))
        assertFalse(ValidationResult.Valid(media).toString().contains("SECRET_PATH"))
        assertFalse(Evidence(listOf(media), emptyList()).toString().contains("SECRET_PATH"))
    }

    @Test
    fun `generation cannot be reused after navigation or explicit clear`() {
        val store = InMemoryCaptureStore()
        store.navigate(PAGE, 1)
        store.clear()
        var refused = false
        try {
            store.navigate(PAGE, 1)
        } catch (_: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
        store.navigate(PAGE, 2)
    }

    @Test
    fun `oversized captured request context is rejected without truncating credentials`() {
        val store = InMemoryCaptureStore(maxRequestChars = 100)
        store.navigate(PAGE, 1)
        assertFalse(store.recordRequest(1, CapturedRequest(
            MEDIA, context = BrowserRequestContext(PAGE, null, "x".repeat(101)),
        )))
        assertFalse(store.playing(1, "x".repeat(101), true))
    }
}