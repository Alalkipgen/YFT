package com.alal.yft.extractor.sites.youtube

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PoTokenResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.FakePlayerScriptRunner
import com.alal.yft.extractor.sites.testing.FakePoTokenProvider
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

        assertEquals(listOf(PAGE_FETCH), http.requestedUrls)
        assertEquals(List(3) { PLAYER_ENDPOINT }, http.postedUrls)
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
        assertEquals(PageNavigationHeaders.ACCEPT, pageHeaders["Accept"])
        assertEquals("navigate", pageHeaders["Sec-Fetch-Mode"])
        assertEquals(USER_AGENT, pageHeaders["User-Agent"])
        assertEquals(YouTubeExtractor.DEFAULT_MAX_PAGE_BYTES, http.requestedBodyLimits.single())

        val embedded = http.postedClients().indexOf("WEB_EMBEDDED_PLAYER")
        val headers = http.postedHeaders[embedded]
        assertFalse(headers.containsKey("Cookie"))
        assertNull(headers["Authorization"])
        assertEquals("application/json", headers["Accept"])
        assertNull(headers["Sec-Fetch-Mode"])
        assertEquals(USER_AGENT, headers["User-Agent"])
        assertEquals("56", headers["X-YouTube-Client-Name"])
        assertEquals("2.20261002.01.00", headers["X-YouTube-Client-Version"])
        assertEquals(VISITOR_DATA, headers["X-Goog-Visitor-Id"])
        assertEquals("https://www.youtube.com/embed/Yft0Fixture", headers["Referer"])
        assertEquals("https://www.youtube.com", headers["Origin"])
        assertEquals(List(3) { YouTubeExtractor.DEFAULT_MAX_PLAYER_BYTES }, http.postedBodyLimits)

        val body = http.postedBodies[embedded]
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
        assertEquals(true, json["contentCheckOk"].asBooleanOrNull)
        assertEquals(true, json["racyCheckOk"].asBooleanOrNull)
        assertNull(json.path("context", "client", "userAgent"))
        assertNull(json["serviceIntegrityDimensions"])
    }

    @Test
    fun `device clients are asked first and their direct streams need no script`() = runTest {
        val http = client(
            page = watchPage(fixture("player_sabr_only.json")),
            visionOs = fixture("player_visionos.json"),
            android = fixture("player_android.json"),
        )
        val runner = FakePlayerScriptRunner()
        val tokens = FakePoTokenProvider()

        val result = YouTubeExtractor(http, runner, tokens).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "https://$ANDROID_HOST/videoplayback?expire=4102444800&ei=QW5kcm9pZA&itag=18" +
                    "&source=youtube&mime=video%2Fmp4&ratebypass=yes&c=ANDROID" +
                    "&sig=AJfixtureAndroidSig&lsig=AFandroid",
                "https://$DEVICE_HOST/videoplayback?expire=4102444800&ei=RGV2aWNl&itag=140" +
                    "&source=youtube&mime=audio%2Fmp4&c=VISIONOS&sig=AJfixtureDeviceSig" +
                    "&lsig=AFdevice",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals(
            listOf("Fixture video title — 360p", "Fixture video title — Audio 131 kbps"),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf(null, 3_449_447L),
            result.candidates.map(MediaCandidate::contentLengthBytes),
        )
        result.candidates.forEach { candidate ->
            assertEquals(
                BrowserRequestContext(pageUrl = CANONICAL, userAgent = USER_AGENT, cookie = null),
                candidate.requestContext,
            )
            assertEquals(NOW + 21_540_000L, candidate.expiresAtEpochMs)
        }
        // The lookup has a video with sound and an audio track, so nothing else is asked.
        assertEquals(listOf("VISIONOS", "ANDROID"), http.postedClients())
        assertTrue(runner.requests.isEmpty())
        assertTrue(tokens.requests.isEmpty())
        assertEquals(
            listOf(
                "client VISIONOS: OK; with URLs: 0 progressive, 5 adaptive; SABR yes",
                "client VISIONOS: 1 download offered",
                "client ANDROID: OK; with URLs: 1 progressive, 0 adaptive; SABR yes",
                "client ANDROID: 1 download offered",
            ),
            result.details.filter { it.contains("VISIONOS") || it.contains("ANDROID") },
        )
    }

    @Test
    fun `device clients are asked as their own apps, without the user's session`() = runTest {
        val http = client(
            page = watchPage(fixture("player_sabr_only.json")),
            visionOs = fixture("player_visionos.json"),
            android = fixture("player_android.json"),
        )

        YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request(cookie = SIGNED_IN_COOKIE))

        YouTubeClientProfiles.DEVICE_CLIENTS.forEachIndexed { index, profile ->
            val headers = http.postedHeaders[index]
            assertEquals(profile.id, profile.userAgent, headers["User-Agent"])
            listOf("Cookie", "Authorization", "X-Origin", "Referer", "Sec-Fetch-Mode").forEach {
                assertNull("${profile.id} $it", headers[it])
            }
            assertEquals(profile.id, "https://www.youtube.com", headers["Origin"])
            assertEquals(profile.id, "application/json", headers["Accept"])
            assertEquals(
                profile.id,
                profile.clientNameId.toString(),
                headers["X-YouTube-Client-Name"],
            )
            assertEquals(profile.id, profile.clientVersion, headers["X-YouTube-Client-Version"])
            assertEquals(profile.id, VISITOR_DATA, headers["X-Goog-Visitor-Id"])

            val json = BoundedJsonParser.parse(http.postedBodies[index], maxNodes = 1_000)
            assertEquals(profile.id, profile.clientName, clientOf(http.postedBodies[index]))
            assertEquals(
                profile.id,
                VISITOR_DATA,
                json.path("context", "client", "visitorData").text,
            )
            assertNull(
                profile.id,
                json.path("playbackContext", "contentPlaybackContext", "signatureTimestamp"),
            )
            assertNull(profile.id, json["serviceIntegrityDimensions"])
        }
    }

    @Test
    fun `an audio track alone does not end the lookup`() = runTest {
        val http = client(
            page = watchPage(BOT_CHECK),
            visionOs = fixture("player_visionos.json"),
            embedded = fixture("player_ok.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "Fixture video title — 720p",
                "Fixture video title — 360p",
                "Fixture video title — Audio 131 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        // The first audio track found is the one offered.
        assertEquals(
            listOf(EMBED_HOST, EMBED_HOST, DEVICE_HOST),
            result.candidates.map { it.mediaUrl.removePrefix("https://").substringBefore('/') },
        )
        assertEquals(
            listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
            http.postedClients(),
        )
        assertTrue(result.details.contains("client ANDROID: HTTP_STATUS (HTTP 404)"))
        assertTrue(result.details.contains("client WEB_EMBEDDED_PLAYER: 2 downloads offered"))
    }

    @Test
    fun `audio is still offered when no client has a video with sound`() = runTest {
        val sabr = fixture("player_sabr_only.json")
        val http = client(
            page = watchPage(sabr),
            visionOs = fixture("player_visionos.json"),
            android = sabr,
            embedded = fixture("player_embed_refused.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        val audio = result.candidates.single()
        assertEquals("Fixture video title — Audio 131 kbps", audio.title)
        assertEquals("audio/mp4", audio.mimeType)
        assertTrue(audio.mediaUrl.startsWith("https://$DEVICE_HOST/"))
        assertEquals(
            listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
            http.postedClients(),
        )
    }

    @Test
    fun `a device client's refusal does not end the lookup`() = runTest {
        val http = client(
            page = watchPage(BOT_CHECK),
            visionOs = fixture("player_private.json"),
            android = fixture("player_android.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf("Fixture video title — 360p"),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
            http.postedClients(),
        )
    }

    @Test
    fun `an age check from any client leaves the answer to the user's own session`() = runTest {
        val gated = client(
            page = watchPage(BOT_CHECK),
            visionOs = fixture("player_age_gate.json"),
            android = fixture("player_android.json"),
            embedded = fixture("player_ok.json"),
        )

        val refused = YouTubeExtractor(gated, FakePlayerScriptRunner()).extract(request())

        // The page's own answer was a bot check, which the user can act on in the browser.
        assertFailure(SiteExtractionFailure.BOT_CHECK, refused)
        assertEquals(listOf("VISIONOS"), gated.postedClients())

        val watchable = client(
            page = watchPage(ownStreams()),
            visionOs = fixture("player_visionos.json"),
            android = fixture("player_age_gate.json"),
            embedded = fixture("player_ok.json"),
        )

        val result = YouTubeExtractor(watchable, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        // What the device client offered before the age check is dropped, too.
        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
        assertEquals(listOf("VISIONOS", "ANDROID"), watchable.postedClients())
        assertTrue(
            result.details.contains(
                "client ANDROID: age check, so only the user's session is asked",
            ),
        )
    }

    @Test
    fun `clients are asked in the order the owner chose`() = runTest {
        val inline = client(page = watchPage(BOT_CHECK))
        YouTubeExtractor(inline).extract(request())
        assertEquals(
            listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
            inline.postedClients(),
        )

        // Without an answer in the page, the page's own client is asked first.
        val asked = client(page = watchPage(null), own = BOT_CHECK)
        YouTubeExtractor(asked).extract(request())
        assertEquals(
            listOf("MWEB", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
            asked.postedClients(),
        )

        // A desktop page is followed by YouTube's mobile site.
        val desktop = client(page = desktopPage(null), own = BOT_CHECK)
        YouTubeExtractor(desktop).extract(request())
        assertEquals(
            listOf("WEB", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", MOBILE_SITE),
            desktop.postedClients(),
        )
    }

    @Test
    fun `a desktop page offered only through SABR falls back to YouTube's mobile site`() =
        runTest {
            val http = client(
                page = desktopPage(fixture("player_sabr_only.json")),
                embedded = fixture("player_embed_refused.json"),
                mobile = ownStreams(),
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
                as SiteExtractionResult.Success

            assertEquals(3, result.candidates.size)
            assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
            assertTrue(result.candidates.all { it.requestContext.cookie == null })
            assertEquals(
                listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", MOBILE_SITE),
                http.postedClients(),
            )
            val profile = YouTubeClientProfiles.mobileWeb()
            val headers = http.postedHeaders.last()
            assertEquals(profile.userAgent, headers["User-Agent"])
            assertEquals(COOKIE, headers["Cookie"])
            assertEquals(CANONICAL, headers["Referer"])
            assertEquals("2", headers["X-YouTube-Client-Name"])
            assertEquals(
                YouTubeClientProfiles.MOBILE_CLIENT_VERSION,
                headers["X-YouTube-Client-Version"],
            )
            val body = BoundedJsonParser.parse(http.postedBodies.last(), maxNodes = 1_000)
            assertEquals(
                20_726L,
                body.path("playbackContext", "contentPlaybackContext", "signatureTimestamp")
                    .asLongOrNull,
            )
            assertTrue(
                result.details.contains(
                    "client WEB (watch page response): OK; with URLs: 0 progressive, " +
                        "0 adaptive; SABR only",
                ),
            )
            assertTrue(result.details.contains("client MWEB: 3 downloads offered"))
        }

    @Test
    fun `the page's own streams carry one proof-of-origin token`() = runTest {
        val http = client(
            page = watchPage(null),
            embedded = fixture("player_embed_refused.json"),
            own = ownStreams(),
        )
        val tokens = FakePoTokenProvider()

        val result = YouTubeExtractor(http, FakePlayerScriptRunner(), tokens).extract(request())
            as SiteExtractionResult.Success

        assertEquals(3, result.candidates.size)
        result.candidates.forEach { candidate ->
            assertTrue(candidate.mediaUrl.endsWith("&pot=${FakePoTokenProvider.TOKEN}"))
        }
        // One token per lookup, bound to the video, for the request and every address.
        val minted = tokens.requests.single()
        assertEquals(VIDEO_ID, minted.contentBinding)
        assertEquals(PHONE_PLAYER, minted.playerScriptUrl)
        assertEquals(CANONICAL, minted.pageUrl)
        val own = BoundedJsonParser.parse(http.postedBodies[0], maxNodes = 1_000)
        assertEquals(
            FakePoTokenProvider.TOKEN,
            own.path("serviceIntegrityDimensions", "poToken").text,
        )
        http.postedBodies.drop(1).forEach { body ->
            assertFalse(body.contains(FakePoTokenProvider.TOKEN))
        }
        assertTrue(result.details.contains("proof of origin (video): minted"))
        assertTrue(result.details.none { it.contains(FakePoTokenProvider.TOKEN) })
    }

    @Test
    fun `only the page's own clients ever carry a token`() = runTest {
        val tokens = FakePoTokenProvider()
        val inline = client(
            page = watchPage(ownStreams()),
            embedded = fixture("player_embed_refused.json"),
        )

        val own = YouTubeExtractor(inline, FakePlayerScriptRunner(), tokens).extract(request())
            as SiteExtractionResult.Success

        // The page's answer came inline, so the token is minted only for its addresses.
        assertTrue(own.candidates.all { it.mediaUrl.endsWith("&pot=${FakePoTokenProvider.TOKEN}") })
        assertEquals(1, tokens.requests.size)
        assertTrue(inline.postedBodies.none { it.contains("serviceIntegrityDimensions") })

        val unused = FakePoTokenProvider()
        val embedded = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))
        val result = YouTubeExtractor(embedded, FakePlayerScriptRunner(), unused)
            .extract(request()) as SiteExtractionResult.Success
        assertTrue(result.candidates.none { it.mediaUrl.contains("pot=") })
        assertTrue(unused.requests.isEmpty())
    }

    @Test
    fun `a token that cannot be had leaves the addresses without one`() = runTest {
        mapOf(
            FakePoTokenProvider(isAvailable = false) to "proof of origin (video): no host",
            FakePoTokenProvider { PoTokenResult.Unavailable } to
                "proof of origin (video): unavailable",
            FakePoTokenProvider { PoTokenResult.Failed(SiteExtractionFailure.NETWORK) } to
                "proof of origin (video): failed (NETWORK)",
            FakePoTokenProvider { PoTokenResult.Minted("not a token!") } to
                "proof of origin (video): unusable",
        ).forEach { (tokens, detail) ->
            val http = client(
                page = watchPage(null),
                embedded = fixture("player_embed_refused.json"),
                own = ownStreams(),
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner(), tokens)
                .extract(request()) as SiteExtractionResult.Success

            assertEquals(detail, 3, result.candidates.size)
            assertTrue(detail, result.candidates.none { it.mediaUrl.contains("pot=") })
            assertFalse(detail, http.postedBodies[0].contains("serviceIntegrityDimensions"))
            assertTrue(detail, result.details.contains(detail))
            assertTrue(detail, tokens.requests.size <= 1)
        }
    }

    @Test
    fun `media tokens follow the page's binding rule while the player token binds the video`() =
        runTest {
            val unbound = watchPage(null).replace(CONTENT_BOUND_FLAG, "html5_fixture\\u003d0")
            val signedIn = unbound.replace(
                "\"HL\":\"en\",",
                "\"HL\":\"en\",\"LOGGED_IN\":true,\"DATASYNC_ID\":\"Fixture0User||\",",
            )
            val noVisitor = unbound.replace(VISITOR_DATA, "")
            mapOf(
                unbound to VISITOR_DATA,
                signedIn to "Fixture0User||",
                noVisitor to null,
            ).forEach { (page, binding) ->
                val tokens = FakePoTokenProvider { request ->
                    PoTokenResult.Minted(
                        if (request.contentBinding == VIDEO_ID) PLAYER_TOKEN else MEDIA_TOKEN,
                    )
                }
                val http = client(
                    page = page,
                    embedded = fixture("player_embed_refused.json"),
                    own = ownStreams(),
                )

                val result = YouTubeExtractor(http, FakePlayerScriptRunner(), tokens)
                    .extract(request()) as SiteExtractionResult.Success

                assertEquals(
                    "$binding",
                    listOfNotNull(VIDEO_ID, binding),
                    tokens.requests.map { it.contentBinding },
                )
                val own = BoundedJsonParser.parse(http.postedBodies[0], maxNodes = 1_000)
                assertEquals(PLAYER_TOKEN, own.path("serviceIntegrityDimensions", "poToken").text)
                result.candidates.forEach { candidate ->
                    assertFalse(candidate.mediaUrl.contains(PLAYER_TOKEN))
                    assertEquals(
                        "$binding",
                        binding != null,
                        candidate.mediaUrl.endsWith("&pot=$MEDIA_TOKEN"),
                    )
                }
                val label = if (binding == VISITOR_DATA || binding == null) "visitor" else "account"
                val detail = if (binding == null) "nothing to bind to" else "minted"
                assertTrue(result.details.contains("proof of origin (video): minted"))
                assertTrue(result.details.contains("proof of origin ($label): $detail"))
            }
        }

    @Test
    fun `a signed-in session is authorized only for the page's own clients`() = runTest {
        val page = watchPage(null).replace(
            "\"HL\":\"en\",",
            "\"HL\":\"en\",\"LOGGED_IN\":true,\"DATASYNC_ID\":\"Fixture0User||\"," +
                "\"SESSION_INDEX\":\"0\",",
        )
        val http = client(
            page = page,
            embedded = fixture("player_embed_refused.json"),
            own = ownStreams(),
        )

        YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request(cookie = SIGNED_IN_COOKIE))
            as SiteExtractionResult.Success

        assertEquals(
            listOf("MWEB", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
            http.postedClients(),
        )
        val signedIn = http.postedHeaders[0]
        assertEquals(SIGNED_IN_COOKIE, signedIn["Cookie"])
        val authorization = requireNotNull(signedIn["Authorization"])
        val proof = "1791000000_[0-9a-f]{40}_u"
        assertTrue(
            authorization,
            Regex("^SAPISIDHASH $proof SAPISID1PHASH $proof SAPISID3PHASH $proof$")
                .matches(authorization),
        )
        assertEquals("https://www.youtube.com", signedIn["X-Origin"])
        assertEquals("0", signedIn["X-Goog-AuthUser"])
        assertEquals("true", signedIn["X-Youtube-Bootstrap-Logged-In"])
        assertNull(signedIn["X-Goog-PageId"])
        http.postedHeaders.drop(1).forEach { headers ->
            listOf(
                "Cookie", "Authorization", "X-Origin", "X-Goog-AuthUser",
                "X-Youtube-Bootstrap-Logged-In",
            ).forEach { name -> assertNull(name, headers[name]) }
        }
    }

    @Test
    fun `a bot check from the device clients outranks a page offered only through SABR`() =
        runTest {
            val http = client(
                page = watchPage(fixture("player_sabr_only.json")),
                visionOs = BOT_CHECK,
                android = BOT_CHECK,
                embedded = fixture("player_unavailable.json"),
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

            // Playing the video in YFT's browser and trying again is what the user can do.
            assertFailure(SiteExtractionFailure.BOT_CHECK, result)
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

                assertFailure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, result, name)
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

            assertFailure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, result)
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

        assertFailure(SiteExtractionFailure.NETWORK, result)
    }

    @Test
    fun `an unavailable script host reports the player script requirement`() = runTest {
        val runner = FakePlayerScriptRunner(isAvailable = false)
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))

        val result = YouTubeExtractor(http, runner).extract(request())

        assertFailure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, result)
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
    fun `a bot check with no embeddable streams is reported as a bot check`() = runTest {
        val http = client(
            page = watchPage(BOT_CHECK),
            embedded = fixture("player_unavailable.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.BOT_CHECK, result)
        assertEquals(listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"), http.postedClients())
    }

    @Test
    fun `a bot check worded with a straight apostrophe is still a bot check`() = runTest {
        val straight = BOT_CHECK.replace("you’re", "you're")
        val http = client(
            page = watchPage(straight),
            embedded = fixture("player_unavailable.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.BOT_CHECK, result)
    }

    @Test
    fun `lookup details name every client asked and its verdict`() = runTest {
        val page = watchPage(BOT_CHECK)
        val http = client(
            page = page,
            embedded = fixture("player_unavailable.json"),
            visionOs = BOT_CHECK,
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Failure

        assertEquals(
            listOf(
                "watch page GET 200 (${page.length} characters)",
                "client MWEB (watch page response): LOGIN_REQUIRED; with URLs: " +
                    "0 progressive, 0 adaptive; SABR no",
                "client MWEB (watch page response) reason: Sign in to confirm you’re not a bot " +
                    "This helps protect our community. Learn more",
                "client VISIONOS: LOGIN_REQUIRED; with URLs: 0 progressive, 0 adaptive; SABR no",
                "client VISIONOS reason: Sign in to confirm you’re not a bot " +
                    "This helps protect our community. Learn more",
                "client ANDROID: HTTP_STATUS (HTTP 404)",
                "client WEB_EMBEDDED_PLAYER: ERROR; with URLs: 0 progressive, 0 adaptive; SABR no",
                "client WEB_EMBEDDED_PLAYER reason: This video is unavailable",
            ),
            result.details,
        )
    }

    @Test
    fun `lookup details count the formats each client offered`() = runTest {
        val own = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)
        val http = client(
            page = watchPage(null),
            embedded = fixture("player_embed_refused.json"),
            own = own,
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertTrue(result.details.contains("watch page: no inline player response"))
        assertTrue(
            result.details.contains(
                "client WEB_EMBEDDED_PLAYER: UNPLAYABLE; with URLs: 0 progressive, " +
                    "0 adaptive; SABR no",
            ),
        )
        assertTrue(
            result.details.contains(
                "client MWEB: OK; with URLs: 2 progressive, 5 adaptive; SABR no",
            ),
        )
        assertTrue(result.details.contains("client MWEB: 3 downloads offered"))
    }

    @Test
    fun `lookup details never carry session values or media addresses`() = runTest {
        val reason = "Sign in to confirm you’re not a bot. Verify at " +
            "https://www.youtube.com/verify?visitor=$VISITOR_DATA&session=fixture-session"
        val inline = BOT_CHECK.replace("Sign in to confirm you’re not a bot", reason)
        val sabr = fixture("player_sabr_only.json")
        val http = client(page = watchPage(inline), embedded = sabr)

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Failure

        val copied = result.details.joinToString("\n")
        assertTrue(copied, copied.contains("https://www.youtube.com"))
        listOf(
            "?", "visitor=", VISITOR_DATA, "fixture-session", COOKIE, "SID=", "googlevideo",
            "videoplayback", "sabr=1", "yft-fixture-innertube-key-not-real",
        ).forEach { secret ->
            assertFalse(secret, copied.contains(secret))
        }
        assertEquals(result.details, result.details.filter { it.length <= 240 })
    }

    @Test
    fun `Home lookups use the desktop identity for the page and every player request`() =
        runTest {
            val own = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)
            val http = client(
                page = watchPage(null),
                embedded = fixture("player_embed_refused.json"),
                own = own,
            )
            val home = SiteExtractionRequest(
                identity = requireNotNull(YouTubeUrls.identify(SHARED_LINK)),
                requestContext = BrowserRequestContext(SHARED_LINK, HOME_USER_AGENT, null),
                nowEpochMs = NOW,
            )

            YouTubeExtractor(http, FakePlayerScriptRunner()).extract(home)
                as SiteExtractionResult.Success

            val page = http.requestedHeaders.single()
            assertEquals(HOME_USER_AGENT, page["User-Agent"])
            PageNavigationHeaders.DEFAULTS.forEach { (name, value) ->
                assertEquals(name, value, page[name])
            }
            assertNull(page["Cookie"])
            assertEquals(
                listOf("MWEB", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
                http.postedClients(),
            )
            // Device clients send their own app's agent; every other request sends Home's.
            val apps = YouTubeClientProfiles.DEVICE_CLIENTS.associateBy { it.clientName }
            http.postedHeaders.zip(http.postedClients()).forEach { (headers, name) ->
                assertEquals(name, apps[name]?.userAgent ?: HOME_USER_AGENT, headers["User-Agent"])
                assertEquals(name, "application/json", headers["Accept"])
                assertNull(name, headers["Cookie"])
                assertNull(name, headers["Sec-Fetch-Mode"])
            }
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

            assertFailure(reason, result, name)
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
        assertEquals(listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"), http.postedClients())
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
            assertEquals(
                listOf("MWEB", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER"),
                http.postedClients(),
            )
            val headers = http.postedHeaders[0]
            assertEquals(COOKIE, headers["Cookie"])
            assertEquals(USER_AGENT, headers["User-Agent"])
            assertEquals("2", headers["X-YouTube-Client-Name"])
            assertEquals("2.20261002.01.00", headers["X-YouTube-Client-Version"])
            assertEquals(CANONICAL, headers["Referer"])
            // The fixture cookie holds no signed-in session, so there is nothing to authorize.
            assertNull(headers["Authorization"])
            val body = BoundedJsonParser.parse(http.postedBodies[0], maxNodes = 1_000)
            assertEquals("MWEB", body.path("context", "client", "clientName").text)
            assertNull(body.path("context", "client", "userAgent"))
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

        assertFailure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, result)
        assertEquals(listOf("MWEB"), http.postedClients())
    }

    @Test
    fun `an embed refusal on an unreachable page reports the page failure`() = runTest {
        val http = client(page = watchPage(null), embedded = fixture("player_embed_refused.json"))

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.HTTP_STATUS, result)
    }

    @Test
    fun `streaming only through YouTube's SABR protocol is no media with a SABR detail`() =
        runTest {
            val sabr = fixture("player_sabr_only.json")
            val http = client(
                page = watchPage(sabr),
                embedded = sabr,
                visionOs = sabr,
                android = sabr,
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
                as SiteExtractionResult.Failure

            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
            assertEquals(
                listOf(
                    "client MWEB (watch page response): OK; with URLs: 0 progressive, " +
                        "0 adaptive; SABR only",
                    "client VISIONOS: OK; with URLs: 0 progressive, 0 adaptive; SABR only",
                    "client ANDROID: OK; with URLs: 0 progressive, 0 adaptive; SABR only",
                    "client WEB_EMBEDDED_PLAYER: OK; with URLs: 0 progressive, 0 adaptive; " +
                        "SABR only",
                ),
                result.details.filter { it.startsWith("client ") },
            )
            assertTrue(result.allowsGenericFallback)
        }

    @Test
    fun `changed markup is reported as a changed response`() = runTest {
        val unknown = fixture("player_no_status.json")
        val http = client(
            page = fixture("changed_markup.html"),
            embedded = unknown,
            own = unknown,
            mobile = unknown,
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.RESPONSE_CHANGED, result)
        assertEquals(
            List(5) { "https://www.youtube.com/youtubei/v1/player?prettyPrint=false" },
            http.postedUrls,
        )
        // A page that names no client of its own is treated as the desktop site.
        assertEquals(
            listOf("WEB", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", MOBILE_SITE),
            http.postedClients(),
        )
        assertNull(http.postedHeaders[1]["X-Goog-Visitor-Id"])
        val embeddedBody = BoundedJsonParser.parse(http.postedBodies[3], maxNodes = 1_000)
        assertEquals(
            YouTubeClientProfiles.FALLBACK_CLIENT_VERSION,
            embeddedBody.path("context", "client", "clientVersion").text,
        )
        assertNull(embeddedBody.path("context", "client", "visitorData"))
        assertNull(
            embeddedBody.path("playbackContext", "contentPlaybackContext", "signatureTimestamp"),
        )
        assertEquals("1", http.postedHeaders[0]["X-YouTube-Client-Name"])
    }

    @Test
    fun `a page without a player version cannot run the script or mint a token`() = runTest {
        val runner = FakePlayerScriptRunner()
        val tokens = FakePoTokenProvider()
        val http = client(
            page = fixture("changed_markup.html"),
            embedded = fixture("player_ok.json"),
            own = fixture("player_no_status.json"),
        )

        val result = YouTubeExtractor(http, runner, tokens).extract(request())

        assertFailure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, result)
        assertTrue(runner.requests.isEmpty())
        assertTrue(tokens.requests.isEmpty())
        assertTrue(
            (result as SiteExtractionResult.Failure).details
                .contains("proof of origin (video): no player version"),
        )
    }

    @Test
    fun `malformed player responses are reported as malformed`() = runTest {
        val broken = fixture("player_malformed.json")
        val http = client(page = watchPage(null), embedded = broken, own = broken)

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.MALFORMED_RESPONSE, result)
    }

    @Test
    fun `expired links are refused before any script runs`() = runTest {
        val expired = fixture("player_ok.json").replace("expire=4102444800", "expire=1700000000")
        val runner = FakePlayerScriptRunner()
        val http = client(page = watchPage(BOT_CHECK), embedded = expired)

        val result = YouTubeExtractor(http, runner).extract(request())

        assertFailure(SiteExtractionFailure.EXPIRED_LINK, result)
        assertTrue(runner.requests.isEmpty())
    }

    @Test
    fun `a failed page fetch reports the transport failure`() = runTest {
        val http = client(page = null)

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertEquals(
            SiteExtractionResult.Failure(
                SiteExtractionFailure.HTTP_STATUS,
                404,
                details = listOf("watch page GET failed (HTTP_STATUS)"),
            ),
            result,
        )
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

        assertFailure(SiteExtractionFailure.UNSUPPORTED_URL, result)
        assertTrue(http.requestedUrls.isEmpty())
    }

    /**
     * Serves [page] for the watch page and answers each client with its own document.
     *
     * A client left null is answered with an HTTP 404, the fake's fallback.
     */
    private fun client(
        page: String?,
        embedded: String? = null,
        own: String? = null,
        visionOs: String? = null,
        android: String? = null,
        mobile: String? = null,
    ): FakeExtractorHttpClient = FakeExtractorHttpClient(
        responses = page
            ?.let { mapOf(PAGE_FETCH to FakeExtractorHttpClient.html(it, MOBILE_PAGE)) }
            .orEmpty(),
        postResponder = { url, body ->
            val answer = when (clientOf(body)) {
                "WEB_EMBEDDED_PLAYER" -> embedded
                "VISIONOS" -> visionOs
                "ANDROID" -> android
                MOBILE_SITE -> mobile
                else -> own
            }
            answer?.let { FakeExtractorHttpClient.json(it, url) }
        },
    )

    /** The clients asked, in order. */
    private fun FakeExtractorHttpClient.postedClients(): List<String> =
        postedBodies.map(::clientOf)

    /** The client a request names; the mobile site sends its own agent, unlike an MWEB page. */
    private fun clientOf(body: String): String {
        val client = BoundedJsonParser.parse(body, maxNodes = 1_000).path("context", "client")
        val name = client["clientName"].text.orEmpty()
        return if (name == "MWEB" && client["userAgent"] != null) MOBILE_SITE else name
    }

    private fun request(
        identity: SitePageIdentity = requireNotNull(YouTubeUrls.identify(SHARED_LINK)),
        cookie: String? = COOKIE,
    ) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = MOBILE_PAGE,
            userAgent = USER_AGENT,
            cookie = cookie,
        ),
        nowEpochMs = NOW,
    )

    /** Compares the reason only; the lookup details have their own tests. */
    private fun assertFailure(
        expected: SiteExtractionFailure,
        result: SiteExtractionResult,
        message: String? = null,
    ) {
        val failure = result as? SiteExtractionResult.Failure
            ?: throw AssertionError("${message.orEmpty()} expected $expected but was $result")
        assertEquals(message, expected, failure.reason)
    }

    private fun watchPage(playerResponse: String?): String =
        fixture("watch_page.html").replace("__PLAYER_RESPONSE__", playerResponse.orEmpty())

    /** The same page as the desktop site serves it, whose own client is YouTube's WEB client. */
    private fun desktopPage(playerResponse: String?): String = watchPage(playerResponse)
        .replace("\"INNERTUBE_CLIENT_NAME\":\"MWEB\"", "\"INNERTUBE_CLIENT_NAME\":\"WEB\"")
        .replace("\"INNERTUBE_CONTEXT_CLIENT_NAME\":2", "\"INNERTUBE_CONTEXT_CLIENT_NAME\":1")

    /** The page's own streams, served from the page's media host. */
    private fun ownStreams(): String = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private val JsonValue?.text: String?
        get() = asStringOrNull

    private companion object {
        const val VIDEO_ID = "Yft0Fixture"
        const val NOW = 1_791_000_000_000L
        const val SHARED_LINK = "https://youtu.be/$VIDEO_ID?si=fixture"
        const val CANONICAL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val PAGE_FETCH = "$CANONICAL&bpctr=9999999999&has_verified=1"
        const val MOBILE_PAGE = "https://m.youtube.com/watch?v=$VIDEO_ID"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Fixture) Mobile Safari/537.36"
        const val COOKIE = "PREF=fixture; SID=fixture-session"
        const val VISITOR_DATA = "CgtGaXh0dXJlVmlzaXRvcijAgICABg%3D%3D"
        const val CONTENT_BOUND_FLAG = "html5_generate_content_po_token\\u003dtrue"
        const val PLAYER_TOKEN = "MnFixturePlayerBound-0123456789_abcdefghij"
        const val MEDIA_TOKEN = "MnFixtureSessionBound-0123456789_abcdefghij"
        const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false" +
            "&key=yft-fixture-innertube-key-not-real"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val EMBED_HOST = "rr1---sn-fixture.googlevideo.example-cdn.test"
        const val PAGE_HOST = "rr2---sn-page.googlevideo.example-cdn.test"
        const val DEVICE_HOST = "rr3---sn-device.googlevideo.example-cdn.test"
        const val ANDROID_HOST = "rr4---sn-android.googlevideo.example-cdn.test"
        const val MEDIA = "https://$EMBED_HOST/videoplayback"
        const val RATE_INPUT = "Fx7nInput0"
        const val SOLVED_RATE = "N0tupnIn7xF"
        const val HOME_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) YFT/fixture"
        const val MOBILE_SITE = "MWEB (mobile site)"
        const val SIGNED_IN_COOKIE = "SID=fixture-session; SAPISID=FixtureSapisid/0123456789; " +
            "__Secure-1PAPISID=Fixture1PSapisid/0123456789; " +
            "__Secure-3PAPISID=Fixture3PSapisid/0123456789"
        val BOT_CHECK: String = Fixtures.read("youtube/player_bot_check.json")
    }
}
