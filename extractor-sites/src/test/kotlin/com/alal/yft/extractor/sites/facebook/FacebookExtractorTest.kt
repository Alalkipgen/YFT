package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookExtractorTest {
    @Test
    fun `a watch page yields every quality plus the dash manifest`() = runTest {
        val identity = identity("1234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/watch_progressive.html"),
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "https://video.example-cdn.test/v/fixture-1080.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/v/fixture-720.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/v/fixture-480.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/v/fixture-legacy-sd.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/v/fixture-dash.mpd?oe=F2A52380&oh=fixture",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals(
            listOf(
                "Fixture watch video — Full HD",
                "Fixture watch video — HD",
                "Fixture watch video — SD",
                "Fixture watch video — SD",
                "Fixture watch video — Adaptive",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf(
                MediaKind.DIRECT,
                MediaKind.DIRECT,
                MediaKind.DIRECT,
                MediaKind.DIRECT,
                MediaKind.DASH,
            ),
            result.candidates.map(MediaCandidate::kind),
        )

        val best = result.candidates.first()
        assertEquals("https://www.facebook.com/watch/?v=1234567890123456", best.pageUrl)
        assertEquals("video/mp4", best.mimeType)
        assertEquals("https://scontent.example-cdn.test/thumb-1280.jpg", best.thumbnailUrl)
        assertEquals(128_000L, best.durationMillis)
        assertEquals(4_070_908_800_000L, best.expiresAtEpochMs)
        assertEquals(setOf(CandidateSource.MANIFEST), best.sources)
        assertEquals(CandidateConfidence.HIGH, best.confidence)
        assertEquals(false, best.drmHint)
        assertEquals(NOW_EPOCH_MS, best.observedAtEpochMs)
        assertEquals("application/dash+xml", result.candidates.last().mimeType)
    }

    @Test
    fun `a suggested video on the same page is never returned instead of the requested one`() =
        runTest {
            val identity = identity("1234567890123456")
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("facebook/watch_progressive.html"),
            )

            val result = FacebookExtractor(http).extract(request(identity))
                as SiteExtractionResult.Success

            assertTrue(result.candidates.none { it.mediaUrl.contains("suggested") })
        }

    @Test
    fun `the page request replays the browser session only to facebook`() = runTest {
        val identity = identity("1234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/watch_progressive.html"),
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertEquals("fixture-agent", headers["User-Agent"])
        assertEquals("c_user=0; xs=fixture-cookie", headers["Cookie"])
        assertEquals("https://www.facebook.com/", headers["Referer"])
        assertEquals(
            FacebookExtractor.DEFAULT_MAX_PAGE_BYTES,
            http.requestedBodyLimits.single(),
        )

        val context = result.candidates.first().requestContext
        assertEquals("https://www.facebook.com/watch/?v=1234567890123456", context.pageUrl)
        assertEquals("fixture-agent", context.userAgent)
        assertEquals("c_user=0; xs=fixture-cookie", context.cookie)
        assertTrue(context.observedHeaders.isEmpty())
    }

    @Test
    fun `a phone identity asks for the desktop page and media keep the phone identity`() =
        runTest {
            val identity = identity("1234567890123456")
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("facebook/watch_progressive.html"),
            )

            val result = FacebookExtractor(http).extract(request(identity, PHONE_AGENT))
                as SiteExtractionResult.Success

            assertEquals(DESKTOP_AGENT, http.requestedHeaders.single()["User-Agent"])
            assertTrue(result.candidates.all { it.requestContext.userAgent == PHONE_AGENT })
        }

    @Test
    fun `the page identity keeps desktop and unknown agents and has a version fallback`() {
        assertEquals("fixture-agent", FacebookPageIdentity.forPageRequest("fixture-agent"))
        assertEquals(null, FacebookPageIdentity.forPageRequest(null))
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Safari/537.36",
            FacebookPageIdentity.forPageRequest("Mozilla/5.0 (iPhone) Mobile/15E148"),
        )
    }

    @Test
    fun `a share link resolves through the redirect and keeps the real page identity`() = runTest {
        val shareUrl = "https://www.facebook.com/share/v/aBc123dEf/"
        val identity = FacebookUrls.identify(shareUrl)!!
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/watch_progressive.html"),
            finalUrl = "https://www.facebook.com/watch/?v=1234567890123456&ref=share",
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            "https://www.facebook.com/watch/?v=1234567890123456",
            result.candidates.first().pageUrl,
        )
        assertEquals("Fixture watch video — Full HD", result.candidates.first().title)
    }

    @Test
    fun `a story page is asked on the owner's posts path and resolves to its video`() = runTest {
        // P3-FIX: Facebook's desktop story.php is a login wall; /{owner}/posts/{id} redirects to
        // the video's own page, whose ID then picks the right video node.
        val story =
            "https://m.facebook.com/story.php?story_fbid=9876543210987654&id=100012345678901"
        val identity = FacebookUrls.identify(story)!!
        val http = FakeExtractorHttpClient.serving(
            url = "https://www.facebook.com/100012345678901/posts/9876543210987654",
            body = Fixtures.read("facebook/watch_progressive.html"),
            finalUrl = "https://www.facebook.com/100012345678901/videos/fixture/1234567890123456/",
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        assertEquals(
            "https://www.facebook.com/watch/?v=1234567890123456",
            result.candidates.first().pageUrl,
        )
        assertEquals("Fixture watch video — Full HD", result.candidates.first().title)
    }

    @Test
    fun `a post without a video says so instead of a changed page format`() = runTest {
        val identity = FacebookUrls.identify(
            "https://www.facebook.com/FixturePage/posts/9876543210987654",
        )!!
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/changed_markup.html"),
        )

        val failure = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertTrue(failure.allowsGenericFallback)
    }

    @Test
    fun `a reel falls back to the legacy delivery fields`() = runTest {
        val identity = FacebookUrls.identify("https://www.facebook.com/reel/7180001112223334")!!
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/reel_legacy_fields.html"),
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "https://video.example-cdn.test/r/fixture-reel-hd.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/r/fixture-reel-sd.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/r/fixture-reel.mpd?oe=F2A52380&oh=fixture",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        val best = result.candidates.first()
        assertEquals("https://www.facebook.com/reel/7180001112223334", best.pageUrl)
        assertEquals("Fixture reel caption — HD", best.title)
        assertEquals("https://scontent.example-cdn.test/reel-thumb.jpg", best.thumbnailUrl)
        assertEquals(31_000L, best.durationMillis)
        assertEquals(MediaKind.DASH, result.candidates.last().kind)
    }

    @Test
    fun `drm, login walls, private pages, regions and empty posts fail with their own reason`() =
        runTest {
            val cases = mapOf(
                "facebook/drm_video.html" to SiteExtractionFailure.DRM_PROTECTED,
                "facebook/login_required.html" to SiteExtractionFailure.LOGIN_REQUIRED,
                "facebook/content_unavailable.html" to
                    SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                "facebook/geo_restricted.html" to SiteExtractionFailure.GEO_RESTRICTED,
                "facebook/no_media.html" to SiteExtractionFailure.NO_MEDIA_FOUND,
                "facebook/insecure_renditions.html" to SiteExtractionFailure.NO_MEDIA_FOUND,
                "facebook/changed_markup.html" to SiteExtractionFailure.RESPONSE_CHANGED,
                "facebook/malformed_payload.html" to SiteExtractionFailure.RESPONSE_CHANGED,
            )

            cases.forEach { (fixture, expected) ->
                val identity = identity("1234567890123456")
                val http = FakeExtractorHttpClient.serving(
                    url = identity.canonicalPageUrl,
                    body = Fixtures.read(fixture),
                )

                val result = FacebookExtractor(http).extract(request(identity))

                assertEquals(fixture, expected, (result as SiteExtractionResult.Failure).reason)
            }
        }

    @Test
    fun `access failures never fall back to the generic detector`() = runTest {
        listOf(
            "facebook/drm_video.html",
            "facebook/login_required.html",
            "facebook/content_unavailable.html",
            "facebook/geo_restricted.html",
        ).forEach { fixture ->
            val identity = identity("1234567890123456")
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read(fixture),
            )

            val failure = FacebookExtractor(http).extract(request(identity))
                as SiteExtractionResult.Failure

            assertFalse(fixture, failure.allowsGenericFallback)
        }
    }

    @Test
    fun `a changed page still allows the generic detector to try`() = runTest {
        val identity = identity("1234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/changed_markup.html"),
        )

        val failure = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, failure.reason)
        assertTrue(failure.allowsGenericFallback)
    }

    @Test
    fun `links the page already expired are reported instead of being queued`() = runTest {
        val identity = identity("5556667778889990")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/expired_links.html"),
        )

        val failure = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.EXPIRED_LINK, failure.reason)
        assertTrue(failure.allowsGenericFallback)
    }

    @Test
    fun `a login redirect fails before the payload is parsed`() = runTest {
        val identity = identity("1234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("facebook/watch_progressive.html"),
            finalUrl = "https://www.facebook.com/login/?next=%2Fwatch%2F",
        )

        val failure = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, failure.reason)
        assertFalse(failure.allowsGenericFallback)
    }

    @Test
    fun `transport failures are surfaced with their status code`() = runTest {
        val identity = identity("1234567890123456")
        val http = FakeExtractorHttpClient(
            responses = emptyMap(),
            fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
        )

        val failure = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RATE_LIMITED, failure.reason)
        assertEquals(429, failure.httpStatusCode)
    }

    private fun identity(videoId: String) = SitePageIdentity(
        siteId = "facebook",
        contentId = videoId,
        canonicalPageUrl = "https://www.facebook.com/watch/?v=$videoId",
    )

    private fun request(
        identity: SitePageIdentity,
        userAgent: String = "fixture-agent",
    ) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = identity.canonicalPageUrl,
            userAgent = userAgent,
            cookie = "c_user=0; xs=fixture-cookie",
        ),
        nowEpochMs = NOW_EPOCH_MS,
    )

    private companion object {
        const val NOW_EPOCH_MS = 1_700_000_000_000

        /** The production browser's identity: the WebView's without `; wv` and `Version/4.0`. */
        const val PHONE_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0.6668.100 Mobile Safari/537.36"
        const val DESKTOP_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0.6668.100 Safari/537.36"
    }
}
