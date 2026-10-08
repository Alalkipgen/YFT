package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ResponseCookie
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

class TikTokExtractorTest {
    @Test
    fun `a standard post yields every quality with honest metadata`() = runTest {
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
        )

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "https://v16-webapp.example-cdn.test/video/play/fixture-1080.mp4?expire=4102444800",
                "https://v16-webapp.example-cdn.test/video/play/fixture-720.mp4?expire=4102444800",
                "https://v16-webapp.example-cdn.test/video/play/fixture-540.mp4?expire=4102444800",
                "https://v16-webapp.example-cdn.test/video/play/fixture-standard.mp4?expire=4102444800",
                "https://v16-webapp.example-cdn.test/video/download/fixture-download.mp4?expire=4102444800",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )

        val best = result.candidates.first()
        assertEquals(MediaKind.DIRECT, best.kind)
        assertEquals("video/mp4", best.mimeType)
        assertEquals(setOf(CandidateSource.MANIFEST), best.sources)
        assertEquals(CandidateConfidence.HIGH, best.confidence)
        assertEquals(false, best.drmHint)
        assertEquals(5_308_416L, best.contentLengthBytes)
        assertEquals(17_000L, best.durationMillis)
        assertEquals(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            best.pageUrl,
        )
        assertEquals("Sunrise over the harbour #fixture — Standard 1080p", best.title)
        assertEquals("https://p16-sign.example-cdn.test/obj/cover-fixture", best.thumbnailUrl)
        assertEquals("Sunrise over the harbour #fixture — Low 540p", result.candidates[2].title)
        assertEquals("Sunrise over the harbour #fixture", result.candidates[3].title)
        assertNull(result.candidates[3].contentLengthBytes)
    }

    @Test
    fun `the page request replays the browser session only to tiktok`() = runTest {
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
        )

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertEquals("fixture-agent", headers["User-Agent"])
        assertEquals("sessionid=fixture-cookie", headers["Cookie"])
        assertEquals("https://www.tiktok.com/", headers["Referer"])
        assertEquals(listOf(TikTokExtractor.DEFAULT_MAX_PAGE_BYTES), http.requestedBodyLimits)

        val context = result.candidates.first().requestContext
        assertEquals("fixture-agent", context.userAgent)
        assertEquals("sessionid=fixture-cookie", context.cookie)
        assertEquals(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            context.pageUrl,
        )
    }

    @Test
    fun `a short link resolves through the redirect and keeps the real page identity`() = runTest {
        val shortUrl = "https://vm.tiktok.com/ZMabc123x"
        val identity = TikTokUrls.identify(shortUrl)!!
        val http = FakeExtractorHttpClient.serving(
            url = shortUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
            finalUrl = "https://www.tiktok.com/@fixture_user/video/7311234567890123456?lang=en",
        )

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            result.candidates.first().pageUrl,
        )
    }

    @Test
    fun `a home lookup gives media requests only the tiktok cookies its page set`() = runTest {
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = tiktokHostedPage(),
            cookies = PAGE_COOKIES,
        )

        val result = TikTokExtractor(http).extract(homeRequest(identity))
            as SiteExtractionResult.Success

        assertNull(http.requestedHeaders.single()["Cookie"])
        assertEquals(5, result.candidates.size)
        result.candidates.forEach { candidate ->
            assertTrue(candidate.mediaUrl.startsWith("https://$TIKTOK_MEDIA_HOST/"))
            assertEquals(
                "tt_chain_token=chain-fixture; ttwid=wid-fixture; tt_csrf_token=csrf-fixture",
                candidate.requestContext.cookie,
            )
            assertEquals("fixture-home-agent", candidate.requestContext.userAgent)
        }
        val printed = listOf(
            result.toString(),
            result.candidates.toString(),
            result.candidates.map { it.requestContext }.toString(),
            PAGE_COOKIES.toString(),
        ).joinToString()
        listOf("chain-fixture", "wid-fixture", "csrf-fixture", "www-fixture", "cdn-fixture")
            .forEach { value -> assertFalse(value, printed.contains(value)) }
    }

    @Test
    fun `a home lookup carries no cookie when the page set none or media leaves tiktok`() =
        runTest {
            val identity = identity("7311234567890123456")
            val noCookies = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = tiktokHostedPage(),
            )
            val otherHost = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("tiktok/universal_video.html"),
                cookies = PAGE_COOKIES,
            )

            listOf(noCookies, otherHost).forEach { http ->
                val result = TikTokExtractor(http).extract(homeRequest(identity))
                    as SiteExtractionResult.Success
                result.candidates.forEach { candidate ->
                    assertNull(candidate.mediaUrl, candidate.requestContext.cookie)
                }
            }
        }

    @Test
    fun `the browser path keeps the webview cookie and adds the page answer's cookies`() =
        runTest {
            val identity = identity("7311234567890123456")
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = tiktokHostedPage(),
                cookies = PAGE_COOKIES,
            )

            val result = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Success

            result.candidates.forEach { candidate ->
                assertEquals(
                    "sessionid=fixture-cookie; tt_chain_token=chain-fixture; ttwid=wid-fixture; " +
                        "tt_csrf_token=csrf-fixture",
                    candidate.requestContext.cookie,
                )
            }
        }

    @Test
    fun `a stale same-named webview cookie loses to the page answer's cookie`() = runTest {
        // P36 (R20): the media host answers 403 to an old tt_chain_token, 206 to the page's.
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = tiktokHostedPage(),
            cookies = PAGE_COOKIES,
        )
        val request = request(identity).let {
            it.copy(
                requestContext = it.requestContext.copy(
                    cookie = "tt_chain_token=stale-chain; sessionid=fixture-cookie",
                ),
            )
        }

        val result = TikTokExtractor(http).extract(request) as SiteExtractionResult.Success

        result.candidates.forEach { candidate ->
            assertEquals(
                "tt_chain_token=chain-fixture; sessionid=fixture-cookie; ttwid=wid-fixture; " +
                    "tt_csrf_token=csrf-fixture",
                candidate.requestContext.cookie,
            )
            assertFalse(candidate.requestContext.cookie!!.contains("stale-chain"))
        }
    }

    @Test
    fun `the phone page's post is read from its reflow data`() = runTest {
        // P36 (R20): the page TikTok gives the WebView's user agent has no webapp.video-detail.
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_reflow_video.html"),
        )

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        assertEquals(
            listOf(
                "https://v16-webapp.example-cdn.test/video/reflow/fixture-play.mp4" +
                    "?expire=REDACTED&signature=REDACTED",
                "https://v16-webapp.example-cdn.test/video/reflow/fixture-download.mp4" +
                    "?expire=REDACTED&signature=REDACTED",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        val play = result.candidates.first()
        assertEquals("Sunrise over the harbour #fixture", play.title)
        assertEquals(17_000L, play.durationMillis)
        assertEquals(
            "https://p16-sign.example-cdn.test/obj/cover-fixture?x-expires=REDACTED" +
                "&x-signature=REDACTED",
            play.thumbnailUrl,
        )
        assertEquals(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            play.pageUrl,
        )
        assertEquals("fixture-agent", play.requestContext.userAgent)
    }

    @Test
    fun `a phone page without qualities takes the desktop page's qualities and cookies`() =
        runTest {
            val identity = identity("7311234567890123456")
            val http = phoneAndDesktop(identity, desktop = FakeExtractorHttpClient.html(
                body = tiktokHostedPage(),
                finalUrl = identity.canonicalPageUrl,
                cookies = DESKTOP_COOKIES,
            ))

            val result = TikTokExtractor(http, desktopUserAgent = DESKTOP_AGENT)
                .extract(request(identity)) as SiteExtractionResult.Success

            assertEquals(
                listOf(identity.canonicalPageUrl, identity.canonicalPageUrl),
                http.requestedUrls,
            )
            assertEquals("fixture-agent", http.requestedHeaders[0]["User-Agent"])
            assertEquals(DESKTOP_AGENT, http.requestedHeaders[1]["User-Agent"])
            assertEquals("sessionid=fixture-cookie", http.requestedHeaders[1]["Cookie"])
            assertEquals(5, result.candidates.size)
            assertEquals(
                "Sunrise over the harbour #fixture — Standard 1080p",
                result.candidates.first().title,
            )
            assertEquals(5_308_416L, result.candidates.first().contentLengthBytes)
            result.candidates.forEach { candidate ->
                assertTrue(candidate.mediaUrl.startsWith("https://$TIKTOK_MEDIA_HOST/"))
                // The media request goes out as the page that gave its address was asked.
                assertEquals(DESKTOP_AGENT, candidate.requestContext.userAgent)
                assertEquals(
                    "sessionid=fixture-cookie; tt_chain_token=desktop-chain-fixture",
                    candidate.requestContext.cookie,
                )
            }
        }

    @Test
    fun `a failed desktop page keeps the phone page's address, its cookies and agent`() =
        runTest {
            val identity = identity("7311234567890123456")
            listOf(
                ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403),
                FakeExtractorHttpClient.html(
                    body = Fixtures.read("tiktok/changed_markup.html"),
                    finalUrl = identity.canonicalPageUrl,
                ),
                FakeExtractorHttpClient.html(
                    body = tiktokHostedPhonePage(),
                    finalUrl = identity.canonicalPageUrl,
                ),
            ).forEach { desktop ->
                val http = phoneAndDesktop(identity, desktop)

                val result = TikTokExtractor(http, desktopUserAgent = DESKTOP_AGENT)
                    .extract(request(identity)) as SiteExtractionResult.Success

                // Asked once, never twice.
                assertEquals(2, http.requestedUrls.size)
                assertEquals(2, result.candidates.size)
                result.candidates.forEach { candidate ->
                    assertEquals("fixture-agent", candidate.requestContext.userAgent)
                    assertEquals(
                        "sessionid=fixture-cookie; tt_chain_token=chain-fixture; " +
                            "ttwid=wid-fixture; tt_csrf_token=csrf-fixture",
                        candidate.requestContext.cookie,
                    )
                }
            }
        }

    @Test
    fun `no second page without the desktop agent, with it or for a page with qualities`() =
        runTest {
            val identity = identity("7311234567890123456")
            // PAGE (TIKTOK_QUALITIES=PAGE): no desktop user agent, so no second request.
            val pageOnly = phoneAndDesktop(identity, desktop = null)
            TikTokExtractor(pageOnly).extract(request(identity))
            assertEquals(1, pageOnly.requestedUrls.size)

            // Home already reads the page with the desktop user agent.
            val home = phoneAndDesktop(identity, desktop = null)
            val homeRequest = homeRequest(identity).let {
                it.copy(requestContext = it.requestContext.copy(userAgent = DESKTOP_AGENT))
            }
            TikTokExtractor(home, desktopUserAgent = DESKTOP_AGENT).extract(homeRequest)
            assertEquals(1, home.requestedUrls.size)

            // A page that lists its qualities is enough.
            val desktopPage = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("tiktok/universal_video.html"),
            )
            TikTokExtractor(desktopPage, desktopUserAgent = DESKTOP_AGENT)
                .extract(request(identity))
            assertEquals(1, desktopPage.requestedUrls.size)
        }

    @Test
    fun `cookie pairs merge by name and keep their order`() {
        val answer = listOf(
            ResponseCookie("b", "new", "tiktok.com", hostOnly = false),
            ResponseCookie("c", "3", "tiktok.com", hostOnly = false),
        )

        assertEquals("a=1; b=new; c=3", TikTokExtractor.mergedCookie("a=1; b=old", answer))
        assertEquals("b=new; c=3", TikTokExtractor.mergedCookie(null, answer))
        assertEquals("a=1", TikTokExtractor.mergedCookie(" a=1 ;; ", emptyList()))
        assertNull(TikTokExtractor.mergedCookie("", emptyList()))
    }

    @Test
    fun `the legacy payload is still understood`() = runTest {
        val identity = identity("7311234567890123458")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/sigi_video.html"),
        )

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(2, result.candidates.size)
        assertEquals(
            "https://v16-webapp.example-cdn.test/video/play/legacy.mp4?expire=4102444800",
            result.candidates.first().mediaUrl,
        )
        assertEquals("Legacy payload fixture", result.candidates.first().title)
        assertEquals(
            "https://www.tiktok.com/@legacy_user/video/7311234567890123458",
            result.candidates.first().pageUrl,
        )
        assertEquals(9_000L, result.candidates.first().durationMillis)
    }

    @Test
    fun `photo posts, private posts, login walls and regions fail with their own reason`() = runTest {
        val cases = mapOf(
            "tiktok/universal_photo.html" to SiteExtractionFailure.NO_MEDIA_FOUND,
            "tiktok/universal_private.html" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            "tiktok/universal_login_required.html" to SiteExtractionFailure.LOGIN_REQUIRED,
            "tiktok/universal_geo_restricted.html" to SiteExtractionFailure.GEO_RESTRICTED,
            "tiktok/drm_video.html" to SiteExtractionFailure.DRM_PROTECTED,
            "tiktok/insecure_renditions.html" to SiteExtractionFailure.NO_MEDIA_FOUND,
            "tiktok/changed_markup.html" to SiteExtractionFailure.RESPONSE_CHANGED,
            "tiktok/malformed_payload.html" to SiteExtractionFailure.RESPONSE_CHANGED,
        )

        cases.forEach { (fixture, expected) ->
            val identity = identity("7311234567890123456")
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read(fixture),
            )

            val result = TikTokExtractor(http).extract(request(identity))

            assertEquals(fixture, expected, (result as SiteExtractionResult.Failure).reason)
        }
    }

    @Test
    fun `access failures never fall back to the generic detector`() = runTest {
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_private.html"),
        )

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertFalse(failure.allowsGenericFallback)
    }

    @Test
    fun `a changed page still allows the generic detector to try`() = runTest {
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/changed_markup.html"),
        )

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertTrue(failure.allowsGenericFallback)
    }

    @Test
    fun `a photo page url fails before the payload is even inspected`() = runTest {
        val identity = TikTokUrls.identify(
            "https://www.tiktok.com/@fixture_user/photo/7311234567890123457",
        )!!
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
        )

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
    }

    @Test
    fun `transport failures are surfaced with their status code`() = runTest {
        val identity = identity("7311234567890123456")
        val http = FakeExtractorHttpClient(
            responses = emptyMap(),
            fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
        )

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RATE_LIMITED, failure.reason)
        assertEquals(429, failure.httpStatusCode)
    }

    private fun identity(videoId: String) = SitePageIdentity(
        siteId = "tiktok",
        contentId = videoId,
        canonicalPageUrl = "https://www.tiktok.com/@fixture_user/video/$videoId",
    )

    private fun homeRequest(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = identity.canonicalPageUrl,
            userAgent = "fixture-home-agent",
            cookie = null,
        ),
        nowEpochMs = 1_700_000_000_000,
    )

    /**
     * The page as TikTok serves it to a phone ([request]'s agent, with [PAGE_COOKIES]) and, to
     * [DESKTOP_AGENT], [desktop] (the 404 fallback when null).
     */
    private fun phoneAndDesktop(
        identity: SitePageIdentity,
        desktop: ExtractorHttpResult?,
    ) = FakeExtractorHttpClient(
        getResponder = { url, headers ->
            when {
                url != identity.canonicalPageUrl -> null
                headers["User-Agent"] == DESKTOP_AGENT -> desktop
                else -> FakeExtractorHttpClient.html(
                    body = tiktokHostedPhonePage(),
                    finalUrl = url,
                    cookies = PAGE_COOKIES,
                )
            }
        },
    )

    private fun tiktokHostedPhonePage(): String =
        Fixtures.read("tiktok/universal_reflow_video.html")
            .replace("v16-webapp.example-cdn.test", TIKTOK_MEDIA_HOST)

    /** The committed fixture with its media on TikTok's own media host, as live pages serve. */
    private fun tiktokHostedPage(): String = Fixtures.read("tiktok/universal_video.html")
        .replace("v16-webapp.example-cdn.test", TIKTOK_MEDIA_HOST)

    private fun request(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = identity.canonicalPageUrl,
            userAgent = "fixture-agent",
            cookie = "sessionid=fixture-cookie",
        ),
        nowEpochMs = 1_700_000_000_000,
    )

    private companion object {
        const val TIKTOK_MEDIA_HOST = "v16-webapp-prime.us.tiktok.com"
        const val DESKTOP_AGENT = "fixture-desktop-agent"

        val DESKTOP_COOKIES = listOf(
            ResponseCookie("tt_chain_token", "desktop-chain-fixture", "tiktok.com", false),
        )

        /** TikTok's own cookies, a host-only page cookie and another site's cookie. */
        val PAGE_COOKIES = listOf(
            ResponseCookie("tt_chain_token", "chain-fixture", "tiktok.com", hostOnly = false),
            ResponseCookie("ttwid", "wid-fixture", "tiktok.com", hostOnly = false),
            ResponseCookie("tt_csrf_token", "csrf-fixture", "tiktok.com", hostOnly = false),
            ResponseCookie("www_only", "www-fixture", "www.tiktok.com", hostOnly = true),
            ResponseCookie("cdn_cookie", "cdn-fixture", "example-cdn.test", hostOnly = false),
        )
    }
}
