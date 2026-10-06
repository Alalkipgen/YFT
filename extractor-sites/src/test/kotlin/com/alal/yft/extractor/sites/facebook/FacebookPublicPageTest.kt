package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
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

/**
 * P15: a reel or video link is first asked as Safari without the user's session; when that page
 * is the requested video, it is the whole lookup.
 */
class FacebookPublicPageTest {
    @Test
    fun `a public reel is one request as Safari without the session`() = runTest {
        val http = FakeExtractorHttpClient.serving(REEL_URL, Fixtures.read(PUBLIC_REEL))

        val result = FacebookExtractor(http).extract(request(reel()))
            as SiteExtractionResult.Success

        assertEquals(listOf(REEL_URL), http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertEquals(FacebookPageIdentity.AVC_LADDER_USER_AGENT, headers["User-Agent"])
        assertFalse(headers.keys.any { it.equals("Cookie", ignoreCase = true) })
        assertEquals("https://www.facebook.com/", headers["Referer"])
        assertEquals("navigate", headers["Sec-Fetch-Mode"])
        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { it.pageUrl == REEL_URL })
        // Media requests keep the browser's own identity, as before.
        assertTrue(result.candidates.all { it.requestContext.userAgent == BROWSER_AGENT })
        assertEquals(
            listOf(
                "public page GET 200 (${Fixtures.read(PUBLIC_REEL).length} characters)",
                "public page: the video, without the session",
            ),
            result.details.take(2),
        )
    }

    @Test
    fun `a video only the session sees is read with the session`() = runTest {
        val refusals = mapOf(
            "login wall" to FakeExtractorHttpClient.html("<html>login</html>", LOGIN_WALL),
            "unavailable" to page(Fixtures.read("facebook/content_unavailable.html")),
            "login page" to page(Fixtures.read("facebook/login_required.html")),
            "server error" to ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500),
        )
        refusals.forEach { (name, refusal) ->
            val http = twoPages(public = refusal, session = page(Fixtures.read(PUBLIC_REEL)))

            val result = FacebookExtractor(http).extract(request(reel()))

            assertTrue(name, result is SiteExtractionResult.Success)
            assertEquals(name, listOf(REEL_URL, REEL_URL), http.requestedUrls)
            val (public, session) = http.requestedHeaders
            assertNull(name, public["Cookie"])
            assertEquals(name, FacebookPageIdentity.AVC_LADDER_USER_AGENT, public["User-Agent"])
            assertEquals(name, COOKIE, session["Cookie"])
            assertEquals(name, BROWSER_AGENT, session["User-Agent"])
            assertTrue(
                name,
                (result as SiteExtractionResult.Success).details
                    .any { it.endsWith(", so the session page is read") },
            )
        }
    }

    @Test
    fun `a public page showing another video reads the session page`() = runTest {
        val watch = Fixtures.read("facebook/watch_progressive.html")
        val identity = watch()
        val http = twoPages(
            public = page(watch.replace(WATCH_ID, "1111222233334444"), identity),
            session = page(watch, identity),
            url = identity.canonicalPageUrl,
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(2, http.requestedUrls.size)
        assertEquals("Fixture watch video — Full HD", result.candidates.first().title)
        assertTrue(
            result.details.contains("public page: another video, so the session page is read"),
        )
    }

    @Test
    fun `a share link is asked once, with the session, and resolves through the redirect`() =
        runTest {
            val identity = requireNotNull(
                FacebookUrls.identify("https://www.facebook.com/share/r/fixtureCode/"),
            )
            val http = FakeExtractorHttpClient.serving(
                identity.canonicalPageUrl,
                Fixtures.read(PUBLIC_REEL),
                finalUrl = "$REEL_URL/",
            )

            val result = FacebookExtractor(http).extract(request(identity))
                as SiteExtractionResult.Success

            assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
            val headers = http.requestedHeaders.single()
            assertEquals(COOKIE, headers["Cookie"])
            assertEquals(BROWSER_AGENT, headers["User-Agent"])
            assertTrue(result.candidates.all { it.pageUrl == REEL_URL })
        }

    @Test
    fun `a login wall on both pages is login required`() = runTest {
        val wall = FakeExtractorHttpClient.html("<html>login</html>", LOGIN_WALL)
        val http = twoPages(public = wall, session = wall)

        val result = FacebookExtractor(http).extract(request(reel()))

        assertEquals(
            SiteExtractionFailure.LOGIN_REQUIRED,
            (result as SiteExtractionResult.Failure).reason,
        )
        assertEquals(listOf(REEL_URL, REEL_URL), http.requestedUrls)
        assertEquals(
            listOf(
                "public page GET 200 (18 characters)",
                "public page: login/checkpoint wall, so the session page is read",
                "page GET 200 (18 characters)",
                "page: login/checkpoint wall",
            ),
            result.details,
        )
    }

    @Test
    fun `a DRM video stays refused`() = runTest {
        val drm = page(Fixtures.read("facebook/drm_video.html"))
        val http = twoPages(public = drm, session = drm)

        val result = FacebookExtractor(http).extract(request(reel()))

        assertEquals(
            SiteExtractionFailure.DRM_PROTECTED,
            (result as SiteExtractionResult.Failure).reason,
        )
    }

    @Test
    fun `a line that fails on the public page ends the lookup there`() = runTest {
        val lineFailures = listOf(SiteExtractionFailure.NETWORK, SiteExtractionFailure.RATE_LIMITED)
        lineFailures.forEach { reason ->
            val http = twoPages(
                public = ExtractorHttpResult.Failure(reason),
                session = page(Fixtures.read(PUBLIC_REEL)),
            )

            val result = FacebookExtractor(http).extract(request(reel()))

            assertEquals(
                SiteExtractionResult.Failure(
                    reason,
                    details = listOf("public page GET failed ($reason)"),
                ),
                result,
            )
            assertEquals(listOf(REEL_URL), http.requestedUrls)
        }
    }

    /** Answers the Safari identity with [public] and every other identity with [session]. */
    private fun twoPages(
        public: ExtractorHttpResult,
        session: ExtractorHttpResult,
        url: String = REEL_URL,
    ) = FakeExtractorHttpClient(
        responses = mapOf(url to session),
        getResponder = { requested, headers ->
            public.takeIf {
                requested == url &&
                    headers["User-Agent"] == FacebookPageIdentity.AVC_LADDER_USER_AGENT
            }
        },
    )

    private fun page(body: String, identity: SitePageIdentity = reel()) =
        FakeExtractorHttpClient.html(body, identity.canonicalPageUrl)

    private fun reel(): SitePageIdentity = requireNotNull(FacebookUrls.identify(REEL_URL))

    private fun watch(): SitePageIdentity =
        requireNotNull(FacebookUrls.identify("https://www.facebook.com/watch/?v=$WATCH_ID"))

    private fun request(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(identity.canonicalPageUrl, BROWSER_AGENT, COOKIE),
        nowEpochMs = 1_791_000_000_000,
    )

    private companion object {
        const val REEL_URL = "https://www.facebook.com/reel/1603698891196107"
        const val WATCH_ID = "1234567890123456"
        const val PUBLIC_REEL = "facebook/public_reel_empty_licences.html"
        const val BROWSER_AGENT = "YFT/fixture"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val LOGIN_WALL = "https://www.facebook.com/login/?next=fixture"
    }
}
