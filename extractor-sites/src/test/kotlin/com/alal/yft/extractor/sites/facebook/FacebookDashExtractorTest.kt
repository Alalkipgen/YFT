package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
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

/**
 * P4: a Facebook video's inline DASH manifest becomes one merged MP4 per picture size plus an
 * audio file, next to the progressive SD/HD files.
 */
class FacebookDashExtractorTest {
    @Test
    fun `every picture size is a merged MP4 with the AAC track, and the AAC track is audio`() =
        runTest {
            val http = FakeExtractorHttpClient(
                responses = mapOf(PAGE_URL to page("facebook/reel_inline_dash.html")),
            )

            val result = FacebookExtractor(http).extract(request()) as SiteExtractionResult.Success

            assertEquals(
                listOf(
                    "$CDN/r/fixture-dash-reel-hd.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/r/fixture-dash-reel-sd.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-av1-1080.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-av1-720-q80.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-audio-heaac.mp4?oe=F2A52380&oh=fixture",
                ),
                result.candidates.map(MediaCandidate::mediaUrl),
            )
            assertEquals(
                listOf(
                    "Fixture DASH reel — HD",
                    "Fixture DASH reel — SD",
                    "Fixture DASH reel — 1080p",
                    "Fixture DASH reel — 720p",
                    "Fixture DASH reel — Audio",
                ),
                result.candidates.map(MediaCandidate::title),
            )
            assertTrue(result.candidates.all { it.kind == MediaKind.DIRECT })
            assertTrue(result.candidates.none { it.mediaUrl.contains("dash_mpd_debug") })

            val fullHd = result.candidates[2]
            assertEquals("video/mp4", fullHd.mimeType)
            assertEquals(listOf("av01.0.08M.08"), fullHd.codecs)
            assertEquals(1660, fullHd.width)
            assertEquals(1078, fullHd.height)
            assertEquals(30.0, fullHd.framesPerSecond!!, 0.001)
            assertEquals(1_340_000L, fullHd.bitrateBitsPerSecond)
            assertEquals(31_500L, fullHd.durationMillis)
            // No size: the video bandwidth can be a peak, so the CDN states the file's length.
            assertNull(fullHd.contentLengthBytes)
            assertEquals(4_070_908_800_000L, fullHd.expiresAtEpochMs)
            val companion = fullHd.audioCompanion!!
            assertEquals(
                "$CDN/o1/v/fixture-audio-heaac.mp4?oe=F2A52380&oh=fixture",
                companion.mediaUrl,
            )
            assertEquals("audio/mp4", companion.mimeType)
            assertEquals(listOf("mp4a.40.5"), companion.codecs)
            assertEquals(57_372L, companion.bitrateBitsPerSecond)
            // The sound's bitrate over 31.5 s: 57 372 bit/s × 31.5 s / 8.
            assertEquals(225_902L, companion.contentLengthBytes)
            assertEquals(fullHd.requestContext, companion.requestContext)

            val sound = result.candidates.last()
            assertEquals("audio/mp4", sound.mimeType)
            assertEquals(listOf("mp4a.40.5"), sound.codecs)
            assertEquals(57_372L, sound.bitrateBitsPerSecond)
            assertNull(sound.audioCompanion)
            assertNull(sound.height)
            // The exact size comes from the file's own lookup, never from the bitrate.
            assertNull(sound.contentLengthBytes)

            val progressive = result.candidates.first()
            assertNull(progressive.audioCompanion)
            assertEquals(31_500L, progressive.durationMillis)
        }

    @Test
    fun `a page without AVC video asks Safari's page for the AVC ladder without the session`() =
        runTest {
            // A share link skips the public page (P15), so its session page comes first.
            val http = FakeExtractorHttpClient(
                responses = mapOf(SHARE_URL to page("facebook/reel_inline_dash.html")),
                getResponder = { url, headers ->
                    if (url == PAGE_URL &&
                        headers["User-Agent"] == FacebookPageIdentity.AVC_LADDER_USER_AGENT
                    ) {
                        page("facebook/reel_inline_dash_safari.html")
                    } else {
                        null
                    }
                },
            )

            val result = FacebookExtractor(http).extract(request(share()))
                as SiteExtractionResult.Success

            assertEquals(listOf(SHARE_URL, PAGE_URL), http.requestedUrls)
            val second = http.requestedHeaders[1]
            assertEquals(FacebookPageIdentity.AVC_LADDER_USER_AGENT, second["User-Agent"])
            assertFalse(second.keys.any { it.equals("Cookie", ignoreCase = true) })
            assertEquals("c_user=0; xs=fixture-cookie", http.requestedHeaders[0]["Cookie"])
            assertEquals(
                listOf(
                    "$CDN/r/fixture-dash-reel-hd.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/r/fixture-dash-reel-sd.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-av1-1080.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-avc-720.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-avc-360.mp4?oe=F2A52380&oh=fixture",
                    "$CDN/o1/v/fixture-audio-heaac.mp4?oe=F2A52380&oh=fixture",
                ),
                result.candidates.map(MediaCandidate::mediaUrl),
            )
            assertEquals(
                listOf(listOf("avc1.64001f"), listOf("avc1.4d001e")),
                result.candidates.subList(3, 5).map(MediaCandidate::codecs),
            )
            assertEquals(
                "Fixture DASH reel — 360p",
                result.candidates[4].title,
            )
            assertTrue(result.details.any { it.startsWith("AVC page GET 200: 2 AVC tracks") })
            // Media requests keep the browser's own identity and session.
            assertEquals("c_user=0; xs=fixture-cookie", result.candidates[3].requestContext.cookie)
        }

    @Test
    fun `a page that already lists AVC video needs no second request`() = runTest {
        val http = FakeExtractorHttpClient(
            responses = mapOf(PAGE_URL to page("facebook/reel_inline_dash_safari.html")),
        )

        val result = FacebookExtractor(http).extract(request()) as SiteExtractionResult.Success

        assertEquals(listOf(PAGE_URL), http.requestedUrls)
        assertEquals(
            listOf("720p", "360p"),
            result.candidates.filter { it.audioCompanion != null }
                .map { it.title!!.substringAfterLast("— ") },
        )
    }

    @Test
    fun `a failed AVC page keeps the first page's tracks`() = runTest {
        val http = FakeExtractorHttpClient(
            responses = mapOf(SHARE_URL to page("facebook/reel_inline_dash.html")),
            getResponder = { _, headers -> loginWall().takeIf { isSafari(headers) } },
        )

        val result = FacebookExtractor(http).extract(request(share()))
            as SiteExtractionResult.Success

        assertEquals(listOf(SHARE_URL, PAGE_URL), http.requestedUrls)
        assertEquals(5, result.candidates.size)
        assertTrue(result.details.contains("AVC page: login/checkpoint wall"))
    }

    @Test
    fun `the public page already asked as Safari is not asked again for the AVC ladder`() =
        runTest {
            val http = FakeExtractorHttpClient(
                responses = mapOf(PAGE_URL to page("facebook/reel_inline_dash.html")),
                getResponder = { _, headers -> loginWall().takeIf { isSafari(headers) } },
            )

            val result = FacebookExtractor(http).extract(request())
                as SiteExtractionResult.Success

            // The public page (Safari, no session), then the session page: no third request.
            assertEquals(listOf(PAGE_URL, PAGE_URL), http.requestedUrls)
            assertTrue(isSafari(http.requestedHeaders[0]))
            assertEquals("c_user=0; xs=fixture-cookie", http.requestedHeaders[1]["Cookie"])
            assertEquals(5, result.candidates.size)
            assertTrue(
                result.details.toString(),
                result.details.containsAll(
                    listOf(
                        "public page: login/checkpoint wall, so the session page is read",
                        "AVC page: the public page's 0 AVC tracks",
                    ),
                ),
            )
        }

    @Test
    fun `an expired audio track leaves only the progressive files`() = runTest {
        val expired = Fixtures.read("facebook/reel_inline_dash.html")
            .replace("fixture-audio-heaac.mp4?oe=F2A52380", "fixture-audio-heaac.mp4?oe=5F5E1000")
        val http = FakeExtractorHttpClient(
            responses = mapOf(PAGE_URL to FakeExtractorHttpClient.html(expired, PAGE_URL)),
        )

        val result = FacebookExtractor(http).extract(request()) as SiteExtractionResult.Success

        assertEquals(
            listOf("Fixture DASH reel — HD", "Fixture DASH reel — SD"),
            result.candidates.map(MediaCandidate::title),
        )
    }

    private fun page(fixture: String): ExtractorHttpResult.Success =
        FakeExtractorHttpClient.html(Fixtures.read(fixture), PAGE_URL)

    private fun isSafari(headers: Map<String, String>): Boolean =
        headers["User-Agent"] == FacebookPageIdentity.AVC_LADDER_USER_AGENT

    private fun loginWall(): ExtractorHttpResult.Success = FakeExtractorHttpClient.html(
        body = "<html>login</html>",
        finalUrl = "https://www.facebook.com/login/?next=fixture",
    )

    private fun share(): SitePageIdentity = requireNotNull(FacebookUrls.identify("$SHARE_URL/"))

    private fun request(
        identity: SitePageIdentity = SitePageIdentity(
            siteId = "facebook",
            contentId = VIDEO_ID,
            canonicalPageUrl = PAGE_URL,
        ),
    ) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = PAGE_URL,
            userAgent = "fixture-agent",
            cookie = "c_user=0; xs=fixture-cookie",
        ),
        nowEpochMs = NOW_EPOCH_MS,
    )

    private companion object {
        const val VIDEO_ID = "7180001112223340"
        const val PAGE_URL = "https://www.facebook.com/watch/?v=$VIDEO_ID"
        const val SHARE_URL = "https://www.facebook.com/share/v/aBc123dEf"
        const val CDN = "https://video.example-cdn.test"
        const val NOW_EPOCH_MS = 1_700_000_000_000
    }
}
