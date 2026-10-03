package com.alal.yft.extractor.sites.vimeo

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VimeoExtractorTest {
    @Test
    fun `a clip page follows its own player configuration`() = runTest {
        val http = client(
            CLIP_URL to Fixtures.read("vimeo/clip_page.html"),
            CONFIG_URL to Fixtures.read("vimeo/player_config.json"),
        )

        val result = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Success

        assertEquals(listOf(CLIP_URL, CONFIG_URL), http.requestedUrls)
        assertEquals(
            listOf(
                "https://vod-progressive.example-cdn.test/v/fixture-1080.mp4?token=fixture",
                "https://vod-progressive.example-cdn.test/v/fixture-720.mp4?token=fixture",
                "https://vod-progressive.example-cdn.test/v/fixture-540.mp4?token=fixture",
                "https://akfire.example-cdn.test/v/fixture.m3u8?token=fixture",
                "https://akfire.example-cdn.test/v/fixture.mpd?token=fixture",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals(
            listOf(
                "Fixture clip title — 1080p",
                "Fixture clip title — 720p",
                "Fixture clip title — 540p",
                "Fixture clip title — Adaptive HLS",
                "Fixture clip title — Adaptive DASH",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf(
                MediaKind.DIRECT,
                MediaKind.DIRECT,
                MediaKind.DIRECT,
                MediaKind.HLS,
                MediaKind.DASH,
            ),
            result.candidates.map(MediaCandidate::kind),
        )
        assertEquals(
            listOf(
                "video/mp4",
                "video/mp4",
                "video/mp4",
                "application/x-mpegURL",
                "application/dash+xml",
            ),
            result.candidates.map(MediaCandidate::mimeType),
        )

        val best = result.candidates.first()
        assertEquals(CLIP_URL, best.pageUrl)
        assertEquals(48_234_496L, best.contentLengthBytes)
        assertEquals("https://i.vimeocdn.test/video/fixture_1280.jpg", best.thumbnailUrl)
        assertEquals(65_400L, best.durationMillis)
        assertEquals(4_070_908_800_000L, best.expiresAtEpochMs)
        assertEquals(setOf(CandidateSource.MANIFEST), best.sources)
        assertEquals(CandidateConfidence.HIGH, best.confidence)
        assertEquals(false, best.drmHint)
        assertEquals(NOW_EPOCH_MS, best.observedAtEpochMs)
    }

    @Test
    fun `an inline player configuration needs no second request`() = runTest {
        val clipUrl = "https://vimeo.com/987654321"
        val http = client(clipUrl to Fixtures.read("vimeo/player_page_inline.html"))

        val result = VimeoExtractor(http).extract(request(identity("987654321")))
            as SiteExtractionResult.Success

        assertEquals(listOf(clipUrl), http.requestedUrls)
        assertEquals(
            listOf(
                "https://vod-progressive.example-cdn.test/v/inline-360.mp4?token=fixture",
                "https://skyfire.example-cdn.test/v/inline.m3u8?token=fixture",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals("Inline player fixture — 360p", result.candidates.first().title)
        assertEquals(12_000L, result.candidates.first().durationMillis)
        assertEquals(
            "https://i.vimeocdn.test/video/inline",
            result.candidates.first().thumbnailUrl,
        )
    }

    @Test
    fun `the configuration request is anchored to the clip page and replays the session`() =
        runTest {
            val http = client(
                CLIP_URL to Fixtures.read("vimeo/clip_page.html"),
                CONFIG_URL to Fixtures.read("vimeo/player_config.json"),
            )

            val result = VimeoExtractor(http).extract(request(identity()))
                as SiteExtractionResult.Success

            val pageHeaders = http.requestedHeaders.first()
            assertEquals("fixture-agent", pageHeaders["User-Agent"])
            assertEquals("vimeo=fixture-cookie", pageHeaders["Cookie"])
            assertEquals("https://vimeo.com/", pageHeaders["Referer"])
            assertEquals(PageNavigationHeaders.ACCEPT, pageHeaders["Accept"])
            assertEquals("navigate", pageHeaders["Sec-Fetch-Mode"])

            val configHeaders = http.requestedHeaders.last()
            assertEquals("fixture-agent", configHeaders["User-Agent"])
            assertEquals("vimeo=fixture-cookie", configHeaders["Cookie"])
            assertEquals(CLIP_URL, configHeaders["Referer"])
            assertTrue(configHeaders["Accept"].orEmpty().contains("application/json"))
            assertNull(configHeaders["Sec-Fetch-Mode"])

            assertEquals(
                listOf(
                    VimeoExtractor.DEFAULT_MAX_PAGE_BYTES,
                    VimeoExtractor.DEFAULT_MAX_CONFIG_BYTES,
                ),
                http.requestedBodyLimits,
            )

            val context = result.candidates.first().requestContext
            assertEquals(CLIP_URL, context.pageUrl)
            assertEquals("fixture-agent", context.userAgent)
            assertEquals("vimeo=fixture-cookie", context.cookie)
            assertTrue(context.observedHeaders.isEmpty())
        }

    @Test
    fun `an unlisted clip keeps its hash in the page and the configuration address`() = runTest {
        val identity = VimeoUrls.identify("https://vimeo.com/123456789/abcdef1234")!!
        val hashedConfigUrl = "https://player.vimeo.com/video/123456789/config?h=abcdef1234"
        val http = client(
            identity.canonicalPageUrl to Fixtures.read("vimeo/changed_markup.html"),
            hashedConfigUrl to Fixtures.read("vimeo/player_config.json"),
        )

        val result = VimeoExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            listOf("https://vimeo.com/123456789/abcdef1234", hashedConfigUrl),
            http.requestedUrls,
        )
        assertEquals(
            "https://vimeo.com/123456789/abcdef1234",
            result.candidates.first().pageUrl,
        )
    }

    @Test
    fun `a changed page falls back to the player configuration`() = runTest {
        val http = client(
            CLIP_URL to Fixtures.read("vimeo/changed_markup.html"),
            FALLBACK_CONFIG_URL to Fixtures.read("vimeo/player_config.json"),
        )

        val result = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Success

        assertEquals(listOf(CLIP_URL, FALLBACK_CONFIG_URL), http.requestedUrls)
        assertEquals(5, result.candidates.size)
    }

    @Test
    fun `a configuration address outside vimeo is never followed`() = runTest {
        val http = client(
            CLIP_URL to Fixtures.read("vimeo/clip_page_foreign_config.html"),
            FALLBACK_CONFIG_URL to Fixtures.read("vimeo/player_config.json"),
        )

        val result = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Success

        assertTrue(http.requestedUrls.none { it.contains("evil.example") })
        assertEquals(listOf(CLIP_URL, FALLBACK_CONFIG_URL), http.requestedUrls)
        assertEquals(5, result.candidates.size)
    }

    @Test
    fun `drm, private, password, region and empty configurations fail with their own reason`() =
        runTest {
            val cases = mapOf(
                "vimeo/config_drm.json" to SiteExtractionFailure.DRM_PROTECTED,
                "vimeo/config_private.json" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                "vimeo/config_password.json" to SiteExtractionFailure.LOGIN_REQUIRED,
                "vimeo/config_geo.json" to SiteExtractionFailure.GEO_RESTRICTED,
                "vimeo/config_no_files.json" to SiteExtractionFailure.NO_MEDIA_FOUND,
                "vimeo/config_insecure.json" to SiteExtractionFailure.NO_MEDIA_FOUND,
                "vimeo/config_malformed.json" to SiteExtractionFailure.MALFORMED_RESPONSE,
            )

            cases.forEach { (fixture, expected) ->
                val http = client(
                    CLIP_URL to Fixtures.read("vimeo/clip_page.html"),
                    CONFIG_URL to Fixtures.read(fixture),
                )

                val result = VimeoExtractor(http).extract(request(identity()))

                assertEquals(fixture, expected, (result as SiteExtractionResult.Failure).reason)
            }
        }

    @Test
    fun `a private clip page fails before any configuration is requested`() = runTest {
        val http = client(CLIP_URL to Fixtures.read("vimeo/private_page.html"))

        val failure = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
        assertEquals(listOf(CLIP_URL), http.requestedUrls)
        assertFalse(failure.allowsGenericFallback)
    }

    @Test
    fun `access failures never fall back to the generic detector`() = runTest {
        listOf(
            "vimeo/config_drm.json",
            "vimeo/config_private.json",
            "vimeo/config_password.json",
            "vimeo/config_geo.json",
        ).forEach { fixture ->
            val http = client(
                CLIP_URL to Fixtures.read("vimeo/clip_page.html"),
                CONFIG_URL to Fixtures.read(fixture),
            )

            val failure = VimeoExtractor(http).extract(request(identity()))
                as SiteExtractionResult.Failure

            assertFalse(fixture, failure.allowsGenericFallback)
        }
    }

    @Test
    fun `an expired configuration is reported instead of being queued`() = runTest {
        val http = client(
            CLIP_URL to Fixtures.read("vimeo/clip_page.html"),
            CONFIG_URL to Fixtures.read("vimeo/config_expired.json"),
        )

        val failure = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.EXPIRED_LINK, failure.reason)
        assertTrue(failure.allowsGenericFallback)
    }

    @Test
    fun `a failing configuration request surfaces its status code`() = runTest {
        val http = FakeExtractorHttpClient(
            responses = mapOf(
                CLIP_URL to ExtractorHttpResult.Success(
                    statusCode = 200,
                    body = Fixtures.read("vimeo/clip_page.html"),
                    finalUrl = CLIP_URL,
                    contentType = "text/html; charset=utf-8",
                ),
            ),
            fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403),
        )

        val failure = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.HTTP_STATUS, failure.reason)
        assertEquals(403, failure.httpStatusCode)
    }

    @Test
    fun `transport failures are surfaced with their status code`() = runTest {
        val http = FakeExtractorHttpClient(
            responses = emptyMap(),
            fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
        )

        val failure = VimeoExtractor(http).extract(request(identity()))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RATE_LIMITED, failure.reason)
        assertEquals(429, failure.httpStatusCode)
        assertEquals(listOf(CLIP_URL), http.requestedUrls)
    }

    private fun client(vararg responses: Pair<String, String>) = FakeExtractorHttpClient(
        responses = responses.associate { (url, body) ->
            url to ExtractorHttpResult.Success(
                statusCode = 200,
                body = body,
                finalUrl = url,
                contentType = "text/html; charset=utf-8",
            )
        },
    )

    private fun identity(videoId: String = "123456789") = SitePageIdentity(
        siteId = "vimeo",
        contentId = videoId,
        canonicalPageUrl = "https://vimeo.com/$videoId",
    )

    private fun request(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = identity.canonicalPageUrl,
            userAgent = "fixture-agent",
            cookie = "vimeo=fixture-cookie",
        ),
        nowEpochMs = NOW_EPOCH_MS,
    )

    private companion object {
        const val NOW_EPOCH_MS = 1_700_000_000_000
        const val CLIP_URL = "https://vimeo.com/123456789"
        const val CONFIG_URL =
            "https://player.vimeo.com/video/123456789/config?h=abcdef1234&s=fixture-signature"
        const val FALLBACK_CONFIG_URL = "https://player.vimeo.com/video/123456789/config"
    }
}
