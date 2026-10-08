package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
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

        // P39: one row per height and codec; the play address is the 540p file's last address
        // and the watermarked download address is kept back while another file opens.
        assertEquals(
            listOf(
                "https://v16-webapp.example-cdn.test/video/play/fixture-1080.mp4?expire=4102444800",
                "https://v16-webapp.example-cdn.test/video/play/fixture-720.mp4?expire=4102444800",
                "https://v16-webapp.example-cdn.test/video/play/fixture-540.mp4?expire=4102444800",
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
        assertEquals("Sunrise over the harbour #fixture — 1080p", best.title)
        assertEquals("https://p16-sign.example-cdn.test/obj/cover-fixture", best.thumbnailUrl)
        assertEquals(listOf("avc1"), best.codecs)
        assertEquals(2_496_000L, best.bitrateBitsPerSecond)
        assertEquals("Sunrise over the harbour #fixture — 540p", result.candidates[2].title)
        assertEquals(1_122_304L, result.candidates[2].contentLengthBytes)
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
        assertEquals(3, result.candidates.size)
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

        // P39: one listed quality, so the desktop page is asked too (here it is the same page).
        assertEquals(
            listOf(identity.canonicalPageUrl, identity.canonicalPageUrl),
            http.requestedUrls,
        )
        // The watermarked download address stays back while the play address works.
        assertEquals(
            listOf(
                "https://v16-webapp.example-cdn.test/video/reflow/fixture-play.mp4" +
                    "?expire=REDACTED&signature=REDACTED",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        val play = result.candidates.first()
        // 576 × 1024 is TikTok's 540p.
        assertEquals("Sunrise over the harbour #fixture — 540p", play.title)
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

            val result = TikTokExtractor(http)
                .extract(request(identity)) as SiteExtractionResult.Success

            assertEquals(
                listOf(identity.canonicalPageUrl, identity.canonicalPageUrl),
                http.requestedUrls,
            )
            assertEquals("fixture-agent", http.requestedHeaders[0]["User-Agent"])
            assertEquals(DESKTOP_AGENT, http.requestedHeaders[1]["User-Agent"])
            // P39 (R29): the lookup's cookie jar carries the phone answer's cookies to the
            // desktop page, as a browser would.
            assertEquals(
                "sessionid=fixture-cookie; tt_chain_token=chain-fixture; ttwid=wid-fixture; " +
                    "tt_csrf_token=csrf-fixture; www_only=www-fixture",
                http.requestedHeaders[1]["Cookie"],
            )
            assertEquals(3, result.candidates.size)
            assertEquals(
                "Sunrise over the harbour #fixture — 1080p",
                result.candidates.first().title,
            )
            assertEquals(5_308_416L, result.candidates.first().contentLengthBytes)
            result.candidates.forEach { candidate ->
                assertTrue(candidate.mediaUrl.startsWith("https://$TIKTOK_MEDIA_HOST/"))
                // The media request goes out as the page that gave its address was asked.
                assertEquals(DESKTOP_AGENT, candidate.requestContext.userAgent)
                // The desktop answer's newer tt_chain_token replaces the phone answer's.
                assertEquals(
                    "sessionid=fixture-cookie; tt_chain_token=desktop-chain-fixture; " +
                        "ttwid=wid-fixture; tt_csrf_token=csrf-fixture",
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

                val result = TikTokExtractor(http)
                    .extract(request(identity)) as SiteExtractionResult.Success

                // Asked once, never twice.
                assertEquals(2, http.requestedUrls.size)
                assertEquals(1, result.candidates.size)
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
            // The desktop page switched off: no second request.
            val pageOnly = phoneAndDesktop(identity, desktop = null)
            TikTokExtractor(pageOnly, askDesktopPage = false).extract(request(identity))
            assertEquals(1, pageOnly.requestedUrls.size)

            // A lookup already asking as desktop Chrome of the same version asks once.
            val home = phoneAndDesktop(identity, desktop = null)
            val homeRequest = homeRequest(identity).let {
                it.copy(requestContext = it.requestContext.copy(userAgent = DESKTOP_AGENT))
            }
            TikTokExtractor(home).extract(homeRequest)
            assertEquals(1, home.requestedUrls.size)

            // A page that lists its qualities is enough.
            val desktopPage = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("tiktok/universal_video.html"),
            )
            TikTokExtractor(desktopPage)
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

        assertEquals(1, result.candidates.size)
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
    fun `photo posts, private posts, login walls and regions fail with their own reason`() =
        runTest {
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
                // The photo fixture is post ...457; P39 skips a post whose id is not the link's.
                val identity = identity(
                    if (fixture.endsWith("photo.html")) PHOTO_POST_ID else "7311234567890123456",
                )
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

    @Test
    fun `the live phone page lists one address, so the desktop page's qualities win`() =
        runTest {
            val identity = identity("7311234567890123456")
            val http = livePages(identity, probe = ::cdnAnswers)

            val result = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Success

            assertEquals(
                listOf("fixture-agent", DESKTOP_AGENT),
                http.requestedHeaders.map { it["User-Agent"] },
            )
            assertFalse(DESKTOP_AGENT.contains("YFT"))
            assertEquals(
                listOf(
                    "Sunrise over the harbour #fixture — 720p H.265",
                    "Sunrise over the harbour #fixture — 540p",
                    "Sunrise over the harbour #fixture — 540p H.265",
                ),
                result.candidates.map { it.title },
            )
            // Exact sizes from the file checks; codecs and picture sizes from the page.
            assertEquals(
                listOf(2_004_627L, 2_953_029L, 1_728_265L),
                result.candidates.map { it.contentLengthBytes },
            )
            assertEquals(
                listOf(listOf("hvc1"), listOf("avc1"), listOf("hvc1")),
                result.candidates.map { it.codecs },
            )
            assertEquals(listOf(720, 576, 576), result.candidates.map { it.width })
            assertEquals(listOf(1280, 1024, 1024), result.candidates.map { it.height })
            result.candidates.forEach { candidate ->
                assertTrue(candidate.mediaUrl.startsWith("https://$TIKTOK_MEDIA_HOST/video/tos/"))
                assertEquals(DESKTOP_AGENT, candidate.requestContext.userAgent)
            }
            // One check per quality of the better answer; the phone answer needs none.
            assertEquals(3, http.probedUrls.size)
            http.probedHeaders.forEach { headers ->
                assertEquals("https://www.tiktok.com/", headers["Referer"])
                assertEquals(DESKTOP_AGENT, headers["User-Agent"])
                assertTrue(headers.getValue("Cookie").contains("tt_chain_token=chain-fixture"))
            }
            assertEquals(List(3) { TikTokExtractor.PROBE_TIMEOUT_MILLIS }, http.probeTimeouts)
            assertEquals(
                listOf(
                    "page: phone · HTTP 200 · ${kb(livePhone())} KB · landed on: video page",
                    "data: universal webapp.reflow.video.detail · JSON: read",
                    "post id: matches",
                    "qualities: 1 (540p) · play address: yes · download address: no",
                    "page: desktop · HTTP 200 · ${kb(liveDesktop())} KB · landed on: video page",
                    "data: universal webapp.video-detail · JSON: read",
                    "post id: matches",
                    "qualities: 3 (720p H.265, 540p, 540p H.265) · play address: yes · " +
                        "download address: no",
                    "file check (desktop): 720p H.265 206 · 540p 206 · 540p H.265 206",
                    "answer: desktop · 3 working qualities",
                    "hosts: $TIKTOK_MEDIA_HOST",
                ),
                result.details,
            )
        }

    @Test
    fun `an unreadable phone page makes the adapter ask the desktop page`() = runTest {
        val identity = identity("7311234567890123456")
        val http = livePages(identity, phone = Fixtures.read("tiktok/malformed_payload.html"))

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(2, http.requestedUrls.size)
        assertEquals(3, result.candidates.size)
        assertTrue(
            result.details[1],
            result.details[1].startsWith("data: none · JSON: error MalformedJson at char "),
        )
        assertEquals("post id: missing", result.details[2])
        assertTrue(result.details.contains("file check: not available"))
    }

    @Test
    fun `a short link that lands on TikTok's home page says the link does not open a video`() =
        runTest {
            val shortUrl = "https://vt.tiktok.com/ZSfixture1/"
            val identity = TikTokUrls.identify(shortUrl)!!
            val http = FakeExtractorHttpClient.serving(
                url = shortUrl,
                body = Fixtures.read("tiktok/live_home_page.html"),
                finalUrl = "https://www.tiktok.com/",
            )

            val failure = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Failure

            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
            assertEquals(
                "This TikTok link does not open a video. It may be removed or private — open " +
                    "it in YFT's browser to check.",
                failure.message,
            )
            assertFalse(failure.allowsGenericFallback)
            // The desktop page would land on the same home page.
            assertEquals(listOf(shortUrl), http.requestedUrls)
            assertTrue(failure.details.first().endsWith("landed on: home page"))
        }

    @Test
    fun `a link that lands on a page without a post id says the link does not open a video`() =
        runTest {
            val shortUrl = "https://vm.tiktok.com/ZMfixture2/"
            val identity = TikTokUrls.identify(shortUrl)!!
            val http = FakeExtractorHttpClient.serving(
                url = shortUrl,
                body = Fixtures.read("tiktok/live_home_page.html")
                    .replace("\"webapp.a-b\":{}", "\"webapp.user-detail\":{\"statusCode\":0}"),
                finalUrl = "https://www.tiktok.com/@fixture_user",
            )

            val failure = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Failure

            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
            assertEquals(TikTokPageParser.NOT_A_VIDEO_MESSAGE, failure.message)
            assertTrue(failure.details.first().endsWith("landed on: other page"))
            assertEquals(1, http.requestedUrls.size)
        }

    @Test
    fun `TikTok's own status for the post is final, so the desktop page is not asked`() =
        runTest {
            val identity = identity("7311234567890123456")
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("tiktok/universal_private.html"),
            )

            val failure = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Failure

            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
            assertEquals(1, http.requestedUrls.size)
            assertNull(failure.message)
        }

    @Test
    fun `a post under an unknown data key is still read`() = runTest {
        val identity = identity("7311234567890123456")
        val page = liveDesktop().replace("\"webapp.video-detail\"", "\"webapp.video-detail-v2\"")
        val http = livePages(identity, phone = page, desktop = page)

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(3, result.candidates.size)
        assertTrue(result.details.contains("data: universal webapp.video-detail-v2 · JSON: read"))
        // Three qualities are enough: one page request.
        assertEquals(1, http.requestedUrls.size)
    }

    @Test
    fun `entity-encoded data with trailing text is read after one cleanup`() = runTest {
        val identity = identity("7311234567890123456")
        val page = entityEncoded(liveDesktop())
        val http = livePages(identity, phone = page, desktop = page)

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(3, result.candidates.size)
        assertTrue(
            result.details.contains(
                "data: universal webapp.video-detail · JSON: read after cleanup",
            ),
        )
    }

    @Test
    fun `a challenge page is a bot check, not a changed page`() = runTest {
        val identity = identity("7311234567890123456")
        val challenge = "<!DOCTYPE html><html><head><title>Please wait...</title></head>" +
            "<body><div id=\"captcha-verify-container\"></div>" +
            "<script src=\"https://sf16-website-login.neutral.ttwstatic.com/obj/captcha.js\">" +
            "</script></body></html>"
        val http = livePages(identity, phone = challenge, desktop = challenge)

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.BOT_CHECK, failure.reason)
        assertFalse(failure.allowsGenericFallback)
        // The desktop page may be let through, so it is asked too.
        assertEquals(2, http.requestedUrls.size)
        assertTrue(failure.details.first().endsWith("landed on: challenge page"))
    }

    @Test
    fun `another post's data is skipped for the next shape`() = runTest {
        // The universal data holds post ...456; SIGI_STATE holds the link's post ...458.
        val identity = identity("7311234567890123458")
        val sigi = Fixtures.read("tiktok/sigi_video.html").let { html ->
            val start = html.indexOf("<script id=\"SIGI_STATE\"")
            html.substring(start, html.indexOf("</script>", start) + "</script>".length)
        }
        val page = liveDesktop().replace("</body>", "$sigi\n</body>")
        val http = livePages(identity, phone = page, desktop = page)

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            "https://v16-webapp.example-cdn.test/video/play/legacy.mp4?expire=4102444800",
            result.candidates.single().mediaUrl,
        )
        assertTrue(result.details.contains("data: SIGI_STATE ItemModule · JSON: read"))

        // No shape holds the link's post: an unknown page, Details say why.
        val otherIdentity = identity("7311234567890123499")
        val other = livePages(otherIdentity, phone = liveDesktop(), desktop = liveDesktop())
        val failure = TikTokExtractor(other).extract(request(otherIdentity))
            as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, failure.reason)
        assertTrue(failure.details.contains("post id: other id"))
        assertTrue(failure.allowsGenericFallback)
    }

    @Test
    fun `a refused file address moves to the next one, and a quality none opens is left out`() =
        runTest {
            val identity = identity("7311234567890123456")
            val pages = liveDesktop()
            val http = livePages(identity, phone = pages, desktop = pages) { url, _ ->
                when {
                    url.contains("v16-webapp-prime") && url.contains(HEVC_720) -> refused(403)
                    url.contains(HEVC_540) -> refused(403)
                    else -> cdnAnswers(url)
                }
            }

            val result = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Success

            assertEquals(
                listOf(
                    "Sunrise over the harbour #fixture — 720p H.265",
                    "Sunrise over the harbour #fixture — 540p",
                ),
                result.candidates.map { it.title },
            )
            assertTrue(
                result.candidates[0].mediaUrl.startsWith("https://v19-webapp-prime.us.tiktok.com/"),
            )
            assertEquals(2_004_627L, result.candidates[0].contentLengthBytes)
            assertEquals(6, http.probedUrls.size)
            assertTrue(
                result.details.toString(),
                result.details.contains(
                    "file check (phone): 720p H.265 403 → 206 · 540p 206 · " +
                        "540p H.265 403 → 403 → 403 (left out)",
                ),
            )
        }

    @Test
    fun `a lookup checks at most eight files and says when none opened`() = runTest {
        val identity = identity("7311234567890123456")
        val page = sixQualityPage()
        val http = livePages(identity, phone = page, desktop = page) { _, _ -> refused(403) }

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(TikTokExtractor.MAX_FILE_CHECKS, http.probedUrls.size)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertEquals(TikTokExtractor.FILES_REFUSED_MESSAGE, failure.message)
        assertTrue(failure.allowsGenericFallback)
        assertTrue(failure.details.contains("answer: none · no file opened"))
    }

    @Test
    fun `the watermarked download address stays back while another file opens`() = runTest {
        val identity = identity("7311234567890123456")
        val page = Fixtures.read("tiktok/universal_reflow_video.html")
        val http = livePages(identity, phone = page, desktop = page) { _, _ ->
            ExtractorProbeResult.Answered(206, 1_000L)
        }

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertTrue(result.candidates.single().mediaUrl.contains("fixture-play.mp4"))
        assertFalse(http.probedUrls.any { it.contains("fixture-download") })
        assertFalse(result.candidates.single().title!!.contains("watermark"))
    }

    @Test
    fun `the watermarked file is offered only when no other file opens`() = runTest {
        val identity = identity("7311234567890123456")
        val page = Fixtures.read("tiktok/universal_reflow_video.html")
        val http = livePages(identity, phone = page, desktop = page) { url, _ ->
            if (url.contains("fixture-download")) {
                ExtractorProbeResult.Answered(206, 2_000L)
            } else {
                refused(403)
            }
        }

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        val row = result.candidates.single()
        assertEquals("Sunrise over the harbour #fixture — With TikTok watermark", row.title)
        assertTrue(row.mediaUrl.contains("fixture-download.mp4"))
        assertEquals(2_000L, row.contentLengthBytes)
        assertTrue(result.details.contains("answer: phone · watermarked file only"))
    }

    @Test
    fun `a post with only a download address offers it with the watermark label`() = runTest {
        val identity = identity("7311234567890123456")
        val page = Fixtures.read("tiktok/universal_reflow_video.html")
            .replace(Regex("\"playAddr\":\"[^\"]*\","), "")
        val http = livePages(identity, phone = page, desktop = page)

        val result = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        assertEquals(
            "Sunrise over the harbour #fixture — With TikTok watermark",
            result.candidates.single().title,
        )
    }

    @Test
    fun `no Details line names an address, a query value or a cookie`() = runTest {
        val identity = identity("7311234567890123456")
        val lookups = listOf(
            livePages(identity, probe = ::cdnAnswers),
            livePages(identity, phone = Fixtures.read("tiktok/malformed_payload.html")),
            livePages(identity, phone = sixQualityPage(), desktop = sixQualityPage()) { _, _ ->
                refused(403)
            },
            FakeExtractorHttpClient(
                fallback = ExtractorHttpResult.Failure(
                    SiteExtractionFailure.RATE_LIMITED,
                    429,
                    details = listOf("session pairs left out: 1"),
                ),
            ),
        )

        lookups.forEach { http ->
            val details = when (val result = TikTokExtractor(http).extract(request(identity))) {
                is SiteExtractionResult.Success -> result.details
                is SiteExtractionResult.Failure -> result.details
            }
            assertTrue(details.isNotEmpty())
            details.forEach { line ->
                listOf(
                    "http", "://", "?", "=", "REDACTED", "fixture-cookie", "chain-fixture",
                    "wid-fixture", "csrf-fixture", "www-fixture",
                ).forEach { word -> assertFalse("$word in: $line", line.contains(word)) }
            }
        }
    }

    @Test
    fun `an unexpected error is named by its class and step, never a changed page`() = runTest {
        val identity = identity("7311234567890123456")
        val http = livePages(identity, probe = { _, _ -> error("fixture failure") })

        val failure = TikTokExtractor(http).extract(request(identity))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.MALFORMED_RESPONSE, failure.reason)
        assertEquals("error: IllegalStateException at step file check", failure.details.last())
    }

    @Test
    fun `the player's own file requests are recognised for the browser's retry`() {
        val extractor = TikTokExtractor(FakeExtractorHttpClient())
        assertTrue(
            extractor.isPlayerMediaRequest(
                "https://$TIKTOK_MEDIA_HOST/video/tos/useast5/fixture/abc/?mime_type=video_mp4",
            ),
        )
        val pageUrl = identity("7311234567890123456").canonicalPageUrl
        assertFalse(extractor.isPlayerMediaRequest(pageUrl))
    }

    private fun livePhone(): String = Fixtures.read("tiktok/live_phone_reflow.html")

    private fun liveDesktop(): String = Fixtures.read("tiktok/live_desktop_video_detail.html")

    private fun kb(page: String): Int = page.encodeToByteArray().size / 1_024

    /**
     * TikTok as the 2026-10-09 live answers looked: [phone] to the request's own agent with
     * [PAGE_COOKIES], [desktop] to [DESKTOP_AGENT]; file checks answered by [probe].
     */
    private fun livePages(
        identity: SitePageIdentity,
        phone: String = livePhone(),
        desktop: String = liveDesktop(),
        probe: ((String, Map<String, String>) -> ExtractorProbeResult)? = null,
    ) = FakeExtractorHttpClient(
        getResponder = { url, headers ->
            when {
                url != identity.canonicalPageUrl -> null
                headers["User-Agent"] == DESKTOP_AGENT -> FakeExtractorHttpClient.html(desktop, url)
                else -> FakeExtractorHttpClient.html(phone, url, cookies = PAGE_COOKIES)
            }
        },
        probeResponder = probe,
    )

    /** The media host's answer: 206 with the file's size, 403 for an unknown address. */
    private fun cdnAnswers(url: String, headers: Map<String, String> = emptyMap()) =
        FILE_SIZES.entries.firstOrNull { url.contains(it.key) }
            ?.let { ExtractorProbeResult.Answered(206, it.value) }
            ?: refused(403)

    private fun refused(status: Int) =
        ExtractorProbeResult.Refused(SiteExtractionFailure.LOGIN_REQUIRED, status)

    /** The data script's body entity-encoded, with script text after the object. */
    private fun entityEncoded(html: String): String {
        val open = html.indexOf('>', html.indexOf("id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"")) + 1
        val close = html.indexOf("</script>", open)
        val body = html.substring(open, close).replace("&", "&amp;").replace("\"", "&quot;")
        return html.substring(0, open) + body + ";window.__ready=1" + html.substring(close)
    }

    /** Six qualities (1080p, 720p and 540p, each H.264 and H.265), three addresses each. */
    private fun sixQualityPage(): String {
        val gears = listOf(1080, 720, 540).flatMap { height ->
            listOf("h264", "h265_hvc1").map { codec -> height to codec }
        }.joinToString(",") { (height, codec) ->
            val objectId = "fixture$height$codec"
            val urls = listOf("v16-webapp-prime", "v19-webapp-prime").joinToString(",") { host ->
                "\"https://$host.us.tiktok.com/video/tos/useast5/fixture/$objectId/\""
            } + ",\"https://www.tiktok.com/aweme/v1/play/\""
            "{\"GearName\":\"normal_${height}_0\",\"Bitrate\":1000000,\"CodecType\":\"$codec\"," +
                "\"PlayAddr\":{\"Width\":$height,\"Height\":${height * 16 / 9}," +
                "\"DataSize\":1000,\"UrlList\":[$urls]}}"
        }
        return "<html><body><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">" +
            "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"statusCode\":0,\"itemInfo\":" +
            "{\"itemStruct\":{\"id\":\"7311234567890123456\",\"desc\":\"Fixture\"," +
            "\"author\":{\"uniqueId\":\"fixture_user\"},\"video\":{\"duration\":10," +
            "\"bitrateInfo\":[$gears]}}}}}}</script></body></html>"
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
        const val HEVC_720 = "c9f7a85bfdd7452aaffabc48131961fc"
        const val HEVC_540 = "9755a2b079e549d08c4a2c1d3279922d"
        const val PHOTO_POST_ID = "7311234567890123457"

        /** The live answer's files (object id in the address) and their sizes. */
        val FILE_SIZES = mapOf(
            "a9d18e19b850455597ce9421ea84a228" to 2_953_029L,
            HEVC_720 to 2_004_627L,
            "dfeb817321c449c9891675ba4552c6b8" to 1_814_281L,
            HEVC_540 to 1_728_265L,
        )
        val DESKTOP_AGENT = TikTokAgents().desktop("fixture-agent")

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
