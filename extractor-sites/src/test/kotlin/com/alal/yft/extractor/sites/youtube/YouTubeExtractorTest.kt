package com.alal.yft.extractor.sites.youtube

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.FakePlayerScriptRunner
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeExtractorTest {
    @Test
    fun `embedded player streams become progressive and audio downloads`() = runTest {
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))
        val runner = FakePlayerScriptRunner()

        val result = YouTubeExtractor(http, runner).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "$MEDIA?expire=4102444800&ei=Rml4dHVyZQ&ip=203.0.113.7&itag=22&source=youtube" +
                    "&mime=video%2Fmp4&n=$SOLVED_RATE&sig=AJfixtureServerSig&lsig=AFfixture",
                "$MEDIA?expire=4102444800&ei=Rml4dHVyZQ&ip=203.0.113.7&itag=18&source=youtube" +
                    "&mime=video%2Fmp4&n=$SOLVED_RATE&sig=AJfixtureServerSig&lsig=AFfixture",
                "$MEDIA?expire=4102444800&itag=140&mime=audio%2Fmp4&xtags=acont%3Doriginal" +
                    "&n=$SOLVED_RATE",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals(
            listOf(
                "Fixture video title — 720p",
                "Fixture video title — 360p",
                "Fixture video title — Audio 129 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf("video/mp4", "video/mp4", "audio/mp4"),
            result.candidates.map(MediaCandidate::mimeType),
        )
        assertEquals(
            listOf(null, 13_370_448L, 3_434_000L),
            result.candidates.map(MediaCandidate::contentLengthBytes),
        )
        result.candidates.forEach { candidate ->
            assertEquals(CANONICAL, candidate.pageUrl)
            assertEquals(MediaKind.DIRECT, candidate.kind)
            assertEquals(setOf(CandidateSource.MANIFEST), candidate.sources)
            assertEquals(CandidateConfidence.HIGH, candidate.confidence)
            assertEquals(false, candidate.drmHint)
            assertEquals(212_000L, candidate.durationMillis)
            assertEquals(
                "https://i.ytimg.example-cdn.test/vi/Yft0Fixture/hqdefault.jpg",
                candidate.thumbnailUrl,
            )
            assertEquals(NOW + 21_540_000L, candidate.expiresAtEpochMs)
            assertEquals(NOW, candidate.observedAtEpochMs)
            assertEquals(
                BrowserRequestContext(pageUrl = CANONICAL, userAgent = USER_AGENT, cookie = null),
                candidate.requestContext,
            )
        }

        assertEquals(listOf(CANONICAL), http.requestedUrls)
        assertEquals(listOf(PLAYER_ENDPOINT), http.postedUrls)
        val solverRequest = runner.requests.single()
        assertEquals(PHONE_PLAYER, solverRequest.playerScriptUrl)
        assertEquals(CANONICAL, solverRequest.pageUrl)
        assertEquals(listOf("n-22", "n-18", "n-140"), solverRequest.challenges.map { it.key })
        solverRequest.challenges.forEach { challenge ->
            assertEquals(PlayerScriptChallengeKind.RATE_PARAM, challenge.kind)
            assertEquals(RATE_INPUT, challenge.input)
        }
    }

    @Test
    fun `the embedded player is asked without the user's cookie`() = runTest {
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))

        YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        val pageHeaders = http.requestedHeaders.single()
        assertEquals(COOKIE, pageHeaders["Cookie"])
        assertEquals(USER_AGENT, pageHeaders["User-Agent"])
        assertEquals(YouTubeExtractor.DEFAULT_MAX_PAGE_BYTES, http.requestedBodyLimits.single())

        val headers = http.postedHeaders.single()
        assertFalse(headers.containsKey("Cookie"))
        assertEquals(USER_AGENT, headers["User-Agent"])
        assertEquals("56", headers["X-YouTube-Client-Name"])
        assertEquals("2.20261002.01.00", headers["X-YouTube-Client-Version"])
        assertEquals(VISITOR_DATA, headers["X-Goog-Visitor-Id"])
        assertEquals("https://www.youtube.com/embed/Yft0Fixture", headers["Referer"])
        assertEquals("https://www.youtube.com", headers["Origin"])
        assertEquals(YouTubeExtractor.DEFAULT_MAX_PLAYER_BYTES, http.postedBodyLimits.single())

        val body = http.postedBodies.single()
        val json = BoundedJsonParser.parse(body, maxNodes = 1_000)
        assertEquals("WEB_EMBEDDED_PLAYER", json.path("context", "client", "clientName").text)
        assertEquals("2.20261002.01.00", json.path("context", "client", "clientVersion").text)
        assertEquals("en", json.path("context", "client", "hl").text)
        assertEquals(VISITOR_DATA, json.path("context", "client", "visitorData").text)
        assertEquals(
            "https://github.com/Alalkipgen/YFT",
            json.path("context", "thirdParty", "embedUrl").text,
        )
        assertEquals(VIDEO_ID, json.path("videoId").text)
        assertEquals(
            20_726L,
            json.path("playbackContext", "contentPlaybackContext", "signatureTimestamp")
                .asLongOrNull,
        )
        assertFalse(body.contains("contentCheckOk"))
        assertFalse(body.contains("racyCheckOk"))
    }

    @Test
    fun `protected streams are signed by the site's own player script`() = runTest {
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_cipher.json"))
        val runner = FakePlayerScriptRunner()

        val result = YouTubeExtractor(http, runner).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "$MEDIA?expire=4102444800&ei=Rml4dHVyZQ&itag=18&source=youtube&mime=video%2Fmp4" +
                    "&n=$SOLVED_RATE" +
                    "&sig=S%3D%3Dhgfedcba9876543210erutangiSoediVerutxiFgIARw8JQ0qOA",
                "$MEDIA?expire=4102444800&itag=140&mime=audio%2Fmp4&n=$SOLVED_RATE" +
                    "&signature=S%3D%3Dhgfedcba9876543210erutangiSoiduAerutxiFgIARw8JQ0qOA",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        val challenges = runner.requests.single().challenges
        assertEquals(listOf("sig-18", "n-18", "sig-140", "n-140"), challenges.map { it.key })
        assertEquals(
            "AOq0QJ8wRAIgFixtureVideoSignature0123456789abcdefgh==",
            challenges.first().input,
        )
        assertEquals(PlayerScriptChallengeKind.SIGNATURE, challenges.first().kind)
    }

    @Test
    fun `protected streams without a script host report the player script requirement`() =
        runTest {
            listOf("player_cipher.json", "player_ok.json").forEach { name ->
                val http = client(page = watchPage(BOT_CHECK), embedded = fixture(name))

                val result = YouTubeExtractor(http).extract(request())

                assertEquals(name, failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED), result)
            }
        }

    @Test
    fun `streams the script did not answer plausibly are dropped`() = runTest {
        val unchanged = FakePlayerScriptRunner { request ->
            PlayerScriptResult.Success(request.challenges.associate { it.key to it.input })
        }
        val marker = FakePlayerScriptRunner { request ->
            PlayerScriptResult.Success(
                request.challenges.associate { it.key to "enhanced_except_fixture" },
            )
        }
        listOf(unchanged, marker).forEach { runner ->
            val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))

            val result = YouTubeExtractor(http, runner).extract(request())

            assertEquals(failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED), result)
        }

        val partial = FakePlayerScriptRunner {
            PlayerScriptResult.Success(mapOf("n-18" to SOLVED_RATE))
        }
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))
        val result = YouTubeExtractor(http, partial).extract(request())
            as SiteExtractionResult.Success
        assertEquals(listOf("Fixture video title — 360p"), result.candidates.map { it.title })
    }

    @Test
    fun `a script host failure is reported with its own reason`() = runTest {
        val runner = FakePlayerScriptRunner {
            PlayerScriptResult.Failed(SiteExtractionFailure.NETWORK)
        }
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))

        val result = YouTubeExtractor(http, runner).extract(request())

        assertEquals(failure(SiteExtractionFailure.NETWORK), result)
    }

    @Test
    fun `an unavailable script host reports the player script requirement`() = runTest {
        val runner = FakePlayerScriptRunner(isAvailable = false)
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))

        val result = YouTubeExtractor(http, runner).extract(request())

        assertEquals(failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED), result)
        assertTrue(runner.requests.isEmpty())
    }

    @Test
    fun `streams without a rate parameter need no script`() = runTest {
        val plain = fixture("player_ok.json").replace("\\u0026n=$RATE_INPUT", "")
        val http = client(page = watchPage(BOT_CHECK), embedded = plain)

        val result = YouTubeExtractor(http).extract(request()) as SiteExtractionResult.Success

        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.none { it.mediaUrl.contains("&n=") })
    }

    @Test
    fun `a bot check with no embeddable streams asks the user to sign in`() = runTest {
        val http = client(
            page = watchPage(BOT_CHECK),
            embedded = fixture("player_unavailable.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(failure(SiteExtractionFailure.LOGIN_REQUIRED), result)
        assertEquals(1, http.postedUrls.size)
    }

    @Test
    fun `a verdict about the video ends the lookup on the watch page`() = runTest {
        mapOf(
            "player_private.json" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            "player_age_gate.json" to SiteExtractionFailure.LOGIN_REQUIRED,
            "player_geo.json" to SiteExtractionFailure.GEO_RESTRICTED,
            "player_drm.json" to SiteExtractionFailure.DRM_PROTECTED,
            "player_live.json" to SiteExtractionFailure.NO_MEDIA_FOUND,
        ).forEach { (name, reason) ->
            val http = client(page = watchPage(fixture(name)), embedded = fixture("player_ok.json"))
            val runner = FakePlayerScriptRunner()

            val result = YouTubeExtractor(http, runner).extract(request())

            assertEquals(name, failure(reason), result)
            assertTrue(name, http.postedUrls.isEmpty())
            assertTrue(name, runner.requests.isEmpty())
        }
    }

    @Test
    fun `an embed refusal falls back to the watch page's own streams`() = runTest {
        val own = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)
        val http = client(page = watchPage(own), embedded = fixture("player_embed_refused.json"))

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
        assertTrue(result.candidates.all { it.requestContext.cookie == null })
        assertEquals(1, http.postedUrls.size)
    }

    @Test
    fun `without an inline response the page client is asked with the user's session`() =
        runTest {
            val own = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)
            val http = client(
                page = watchPage(null),
                embedded = fixture("player_embed_refused.json"),
                own = own,
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
                as SiteExtractionResult.Success

            assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
            assertEquals(2, http.postedUrls.size)
            val headers = http.postedHeaders[1]
            assertEquals(COOKIE, headers["Cookie"])
            assertEquals("2", headers["X-YouTube-Client-Name"])
            assertEquals(CANONICAL, headers["Referer"])
            val body = BoundedJsonParser.parse(http.postedBodies[1], maxNodes = 1_000)
            assertEquals("MWEB", body.path("context", "client", "clientName").text)
            assertNull(body.path("context", "thirdParty"))
            assertTrue(result.candidates.all { it.requestContext.cookie == null })
        }

    @Test
    fun `the page client's verdict about the video is final`() = runTest {
        val http = client(
            page = watchPage(null),
            embedded = fixture("player_embed_refused.json"),
            own = fixture("player_private.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE), result)
    }

    @Test
    fun `an embed refusal on an unreachable page reports the page failure`() = runTest {
        val http = client(page = watchPage(null), embedded = fixture("player_embed_refused.json"))

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(failure(SiteExtractionFailure.HTTP_STATUS), result)
    }

    @Test
    fun `streaming only through YouTube's own protocol reports the player script requirement`() =
        runTest {
            val sabr = fixture("player_sabr_only.json")
            val http = client(page = watchPage(sabr), embedded = sabr)

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

            assertEquals(failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED), result)
        }

    @Test
    fun `changed markup is reported as a changed response`() = runTest {
        val unknown = fixture("player_no_status.json")
        val http = client(page = fixture("changed_markup.html"), embedded = unknown, own = unknown)

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(failure(SiteExtractionFailure.RESPONSE_CHANGED), result)
        assertEquals(
            List(2) { "https://www.youtube.com/youtubei/v1/player?prettyPrint=false" },
            http.postedUrls,
        )
        val embeddedBody = BoundedJsonParser.parse(http.postedBodies[0], maxNodes = 1_000)
        assertEquals(
            YouTubeClientProfiles.FALLBACK_CLIENT_VERSION,
            embeddedBody.path("context", "client", "clientVersion").text,
        )
        assertNull(embeddedBody.path("context", "client", "visitorData"))
        assertNull(
            embeddedBody.path("playbackContext", "contentPlaybackContext", "signatureTimestamp"),
        )
        assertEquals("1", http.postedHeaders[1]["X-YouTube-Client-Name"])
    }

    @Test
    fun `a page without a player version cannot run the script`() = runTest {
        val runner = FakePlayerScriptRunner()
        val http = client(
            page = fixture("changed_markup.html"),
            embedded = fixture("player_ok.json"),
            own = fixture("player_no_status.json"),
        )

        val result = YouTubeExtractor(http, runner).extract(request())

        assertEquals(failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED), result)
        assertTrue(runner.requests.isEmpty())
    }

    @Test
    fun `malformed player responses are reported as malformed`() = runTest {
        val broken = fixture("player_malformed.json")
        val http = client(page = watchPage(null), embedded = broken, own = broken)

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(failure(SiteExtractionFailure.MALFORMED_RESPONSE), result)
    }

    @Test
    fun `expired links are refused before any script runs`() = runTest {
        val expired = fixture("player_ok.json").replace("expire=4102444800", "expire=1700000000")
        val runner = FakePlayerScriptRunner()
        val http = client(page = watchPage(BOT_CHECK), embedded = expired)

        val result = YouTubeExtractor(http, runner).extract(request())

        assertEquals(failure(SiteExtractionFailure.EXPIRED_LINK), result)
        assertTrue(runner.requests.isEmpty())
    }

    @Test
    fun `a failed page fetch reports the transport failure`() = runTest {
        val http = client(page = null)

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(SiteExtractionResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404), result)
        assertTrue(http.postedUrls.isEmpty())
    }

    @Test
    fun `an identity with a malformed video id is unsupported`() = runTest {
        val http = client(page = watchPage(BOT_CHECK))
        val identity = SitePageIdentity(
            siteId = YouTubeUrls.SITE_ID,
            contentId = "bad id",
            canonicalPageUrl = CANONICAL,
        )

        val result = YouTubeExtractor(http).extract(request(identity))

        assertEquals(failure(SiteExtractionFailure.UNSUPPORTED_URL), result)
        assertTrue(http.requestedUrls.isEmpty())
    }

    private fun client(
        page: String?,
        embedded: String? = null,
        own: String? = null,
    ): FakeExtractorHttpClient = FakeExtractorHttpClient(
        responses = page
            ?.let { mapOf(CANONICAL to FakeExtractorHttpClient.html(it, MOBILE_PAGE)) }
            .orEmpty(),
        postResponder = { url, body ->
            val answer = if (body.contains("\"WEB_EMBEDDED_PLAYER\"")) embedded else own
            answer?.let { FakeExtractorHttpClient.json(it, url) }
        },
    )

    private fun request(
        identity: SitePageIdentity = requireNotNull(YouTubeUrls.identify(SHARED_LINK)),
    ) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = MOBILE_PAGE,
            userAgent = USER_AGENT,
            cookie = COOKIE,
        ),
        nowEpochMs = NOW,
    )

    private fun failure(reason: SiteExtractionFailure) = SiteExtractionResult.Failure(reason)

    private fun watchPage(playerResponse: String?): String =
        fixture("watch_page.html").replace("__PLAYER_RESPONSE__", playerResponse.orEmpty())

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private val com.alal.yft.extractor.api.json.JsonValue?.text: String?
        get() = asStringOrNull

    private companion object {
        const val VIDEO_ID = "Yft0Fixture"
        const val NOW = 1_791_000_000_000L
        const val SHARED_LINK = "https://youtu.be/$VIDEO_ID?si=fixture"
        const val CANONICAL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val MOBILE_PAGE = "https://m.youtube.com/watch?v=$VIDEO_ID"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Fixture) Mobile Safari/537.36"
        const val COOKIE = "PREF=fixture; SID=fixture-session"
        const val VISITOR_DATA = "CgtGaXh0dXJlVmlzaXRvcijAgICABg%3D%3D"
        const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false" +
            "&key=yft-fixture-innertube-key-not-real"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val EMBED_HOST = "rr1---sn-fixture.googlevideo.example-cdn.test"
        const val PAGE_HOST = "rr2---sn-page.googlevideo.example-cdn.test"
        const val MEDIA = "https://$EMBED_HOST/videoplayback"
        const val RATE_INPUT = "Fx7nInput0"
        const val SOLVED_RATE = "N0tupnIn7xF"
        val BOT_CHECK: String = Fixtures.read("youtube/player_bot_check.json")
    }
}
