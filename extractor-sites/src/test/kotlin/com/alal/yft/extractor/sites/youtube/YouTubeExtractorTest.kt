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
    fun `embedded player streams become progressive, merged and audio downloads`() = runTest {
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_ok.json"))
        val runner = FakePlayerScriptRunner()

        val result = YouTubeExtractor(http, runner).extract(request())
            as SiteExtractionResult.Success

        val audioUrl = "$MEDIA?expire=4102444800&itag=140&mime=audio%2Fmp4" +
            "&xtags=acont%3Doriginal&n=$SOLVED_RATE"
        assertEquals(
            listOf(
                "$MEDIA?expire=4102444800&itag=137&mime=video%2Fmp4&n=$SOLVED_RATE",
                "$MEDIA?expire=4102444800&ei=Rml4dHVyZQ&ip=203.0.113.7&itag=22&source=youtube" +
                    "&mime=video%2Fmp4&n=$SOLVED_RATE&sig=AJfixtureServerSig&lsig=AFfixture",
                "$MEDIA?expire=4102444800&ei=Rml4dHVyZQ&ip=203.0.113.7&itag=18&source=youtube" +
                    "&mime=video%2Fmp4&n=$SOLVED_RATE&sig=AJfixtureServerSig&lsig=AFfixture",
                audioUrl,
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals(
            listOf(
                "Fixture video title — 1080p",
                "Fixture video title — 720p",
                "Fixture video title — 360p",
                "Fixture video title — Audio 129 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf("video/mp4", "video/mp4", "video/mp4", "audio/mp4"),
            result.candidates.map(MediaCandidate::mimeType),
        )
        // The picture YouTube states reaches the download sheet without another request (P3).
        assertEquals(
            listOf(1920, 1280, 640, null),
            result.candidates.map(MediaCandidate::width),
        )
        assertEquals(
            listOf(1080, 720, 360, null),
            result.candidates.map(MediaCandidate::height),
        )
        assertEquals(
            listOf(30.0, null, null, null),
            result.candidates.map(MediaCandidate::framesPerSecond),
        )
        assertEquals(
            listOf(4_400_000L, 1_210_000L, 503_814L, 129_478L),
            result.candidates.map(MediaCandidate::bitrateBitsPerSecond),
        )
        // The merged row is as large as its video and audio files together.
        assertEquals(
            listOf(91_434_000L, null, 13_370_448L, 3_434_000L),
            result.candidates.map(MediaCandidate::contentLengthBytes),
        )
        assertEquals(
            listOf(
                listOf("avc1.640028"),
                listOf("avc1.64001F", "mp4a.40.2"),
                listOf("avc1.42001E", "mp4a.40.2"),
                listOf("mp4a.40.2"),
            ),
            result.candidates.map(MediaCandidate::codecs),
        )
        // Only the video-only stream is paired with the audio track it is merged with.
        val companion = result.candidates.first().audioCompanion
        assertEquals(audioUrl, companion?.mediaUrl)
        assertEquals("audio/mp4", companion?.mimeType)
        assertEquals(listOf("mp4a.40.2"), companion?.codecs)
        assertEquals(3_434_000L, companion?.contentLengthBytes)
        assertEquals(NOW + 21_540_000L, companion?.expiresAtEpochMs)
        assertEquals(
            BrowserRequestContext(pageUrl = CANONICAL, userAgent = USER_AGENT, cookie = null),
            companion?.requestContext,
        )
        assertTrue(result.candidates.drop(1).all { it.audioCompanion == null })
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
        // visionOS is asked before the watch page (P14), so without the page's key. Its failed
        // request is not asked again, and the embedded player's answer is complete (P22).
        assertEquals(listOf(PLAYER_URL_WITHOUT_PAGE, PLAYER_ENDPOINT), http.postedUrls)
        val solverRequest = runner.requests.single()
        assertEquals(PHONE_PLAYER, solverRequest.playerScriptUrl)
        assertEquals(CANONICAL, solverRequest.pageUrl)
        assertEquals(
            listOf("n-22", "n-18", "n-137", "n-140"),
            solverRequest.challenges.map { it.key },
        )
        solverRequest.challenges.forEach { challenge ->
            assertEquals(PlayerScriptChallengeKind.RATE_PARAM, challenge.kind)
            assertEquals(RATE_INPUT, challenge.input)
        }
    }

    @Test
    fun `2K and 4K rows are 8-bit VP9 merged with the Opus track into WebM`() = runTest {
        val http = client(page = watchPage(BOT_CHECK), embedded = fixture("player_4k.json"))
        val runner = FakePlayerScriptRunner()

        val result = YouTubeExtractor(http, runner).extract(request())
            as SiteExtractionResult.Success

        val candidates = result.candidates
        // 1080p stays AVC; above it YouTube has no AVC, so 2K and 4K are VP9 (P6). The brighter
        // HDR VP9 and the AV1 copies of the same qualities are not offered beside them.
        assertEquals(listOf(313, 271, 137, 22, 18, 140), candidates.map(::itagOf))
        assertEquals(
            listOf("2160p", "1440p", "1080p", "720p", "360p", "Audio 129 kbps").map {
                "Fixture video title — $it"
            },
            candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf("video/webm", "video/webm", "video/mp4", "video/mp4", "video/mp4", "audio/mp4"),
            candidates.map(MediaCandidate::mimeType),
        )
        assertEquals(listOf(3840, 2560, 1920, 1280, 640, null), candidates.map { it.width })
        assertEquals(listOf(2160, 1440, 1080, 720, 360, null), candidates.map { it.height })
        assertEquals(listOf("vp9"), candidates[0].codecs)
        // WebM video is merged with the Opus WebM track, MP4 video with the AAC track.
        candidates.take(2).forEach { candidate ->
            val companion = candidate.audioCompanion
            assertEquals("audio/webm", companion?.mimeType)
            assertEquals(listOf("opus"), companion?.codecs)
            assertEquals(251, companion?.mediaUrl?.let(::itagOf))
            assertEquals(3_800_000L, companion?.contentLengthBytes)
            assertTrue(companion?.mediaUrl.orEmpty().endsWith("&n=$SOLVED_RATE"))
        }
        assertEquals(403_800_000L, candidates[0].contentLengthBytes)
        assertEquals("audio/mp4", candidates[2].audioCompanion?.mimeType)
        assertEquals(140, candidates[2].audioCompanion?.mediaUrl?.let(::itagOf))
        assertTrue(candidates.drop(3).all { it.audioCompanion == null })
        // The Opus track is only merged; the audio-only row stays the AAC one.
        assertTrue(candidates.none { it.mimeType == "audio/webm" })
        val asked = runner.requests.single().challenges.map { it.key }.toSet()
        assertTrue("n-251" in asked)
        assertTrue(asked.none { it in setOf("n-337", "n-401", "n-400", "n-248") })
    }

    @Test
    fun `without 8-bit VP9 2K and 4K rows are AV1 merged with the AAC track`() = runTest {
        val http = client(
            page = watchPage(BOT_CHECK),
            embedded = fixture("player_4k_av1_only.json"),
        )
        val runner = FakePlayerScriptRunner()

        val result = YouTubeExtractor(http, runner).extract(request())
            as SiteExtractionResult.Success

        val candidates = result.candidates
        assertEquals(listOf(401, 400, 137, 22, 18, 140), candidates.map(::itagOf))
        assertEquals(
            listOf("video/mp4", "video/mp4", "video/mp4", "video/mp4", "video/mp4", "audio/mp4"),
            candidates.map(MediaCandidate::mimeType),
        )
        assertEquals(listOf("av01.0.12M.08"), candidates[0].codecs)
        candidates.take(3).forEach { candidate ->
            assertEquals("audio/mp4", candidate.audioCompanion?.mimeType)
            assertEquals(140, candidate.audioCompanion?.mediaUrl?.let(::itagOf))
        }
        // Nothing is merged with the Opus track, so its address is never worked out.
        val asked = runner.requests.single().challenges.map { it.key }.toSet()
        assertTrue(asked.none { it in setOf("n-251", "n-337") })
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
        assertEquals(List(2) { YouTubeExtractor.DEFAULT_MAX_PLAYER_BYTES }, http.postedBodyLimits)

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
            visionOs = chainVisionOs(),
            android = fixture("player_android.json"),
        )
        val runner = FakePlayerScriptRunner()
        val tokens = FakePoTokenProvider()

        val result = YouTubeExtractor(http, runner, tokens).extract(request())
            as SiteExtractionResult.Success

        val audioUrl = "https://$DEVICE_HOST/videoplayback?expire=4102444800&ei=RGV2aWNl" +
            "&itag=140&source=youtube&mime=audio%2Fmp4&c=VISIONOS&sig=AJfixtureDeviceSig" +
            "&lsig=AFdevice"
        assertEquals(
            listOf(
                "https://$DEVICE_HOST/videoplayback?expire=4102444800&ei=RGV2aWNl&itag=137" +
                    "&source=youtube&mime=video%2Fmp4&c=VISIONOS&sig=AJfixtureDeviceSig" +
                    "&lsig=AFdevice",
                "https://$DEVICE_HOST/videoplayback?expire=4102444800&ei=RGV2aWNl&itag=136" +
                    "&source=youtube&mime=video%2Fmp4&c=VISIONOS&sig=AJfixtureDeviceSig" +
                    "&lsig=AFdevice",
                audioUrl,
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        assertEquals(
            listOf(
                "Fixture video title — 1080p",
                "Fixture video title — 720p",
                "Fixture video title — Audio 131 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf(84_361_446L, 29_905_327L, 3_449_447L),
            result.candidates.map(MediaCandidate::contentLengthBytes),
        )
        // The merged rows are paired with the audio track of the answer they came from.
        assertEquals(
            listOf(audioUrl, audioUrl, null),
            result.candidates.map { it.audioCompanion?.mediaUrl },
        )
        result.candidates.forEach { candidate ->
            assertEquals(
                BrowserRequestContext(pageUrl = CANONICAL, userAgent = USER_AGENT, cookie = null),
                candidate.requestContext,
            )
            assertEquals(NOW + 21_540_000L, candidate.expiresAtEpochMs)
        }
        // Merged rows with their audio track are complete (P22), so nothing else is asked: not
        // even the Android app, whose only download is a progressive 360p file.
        assertEquals(listOf("VISIONOS"), http.postedClients())
        assertTrue(runner.requests.isEmpty())
        assertTrue(tokens.requests.isEmpty())
        assertEquals(
            listOf(
                "client VISIONOS: OK; with URLs: 0 progressive, 5 adaptive; SABR yes",
                "client VISIONOS: 3 downloads offered",
            ),
            result.details.filter { it.contains("VISIONOS") || it.contains("ANDROID") },
        )
    }

    @Test
    fun `device clients are asked as their own apps, without the user's session`() = runTest {
        // visionOS's verdict is not asked for again and nothing else answers, so the Android
        // app is asked last.
        val http = client(
            page = watchPage(fixture("player_sabr_only.json")),
            visionOs = fixture("player_private.json"),
            android = fixture("player_android.json"),
        )

        YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request(cookie = SIGNED_IN_COOKIE))

        assertEquals(listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"), http.postedClients())
        YouTubeClientProfiles.DEVICE_CLIENTS.forEach { profile ->
            val index = http.postedClients().indexOf(profile.clientName)
            // visionOS is asked before the watch page (P14), so it has no visitor data to send.
            val visitor = VISITOR_DATA.takeUnless { profile == YouTubeClientProfiles.VISION_OS }
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
            assertEquals(profile.id, visitor, headers["X-Goog-Visitor-Id"])

            val json = BoundedJsonParser.parse(http.postedBodies[index], maxNodes = 1_000)
            assertEquals(profile.id, profile.clientName, clientOf(http.postedBodies[index]))
            assertEquals(
                profile.id,
                visitor,
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
    fun `merged rows with their audio track end the lookup before any other client`() = runTest {
        val http = client(
            page = watchPage(BOT_CHECK),
            visionOs = chainVisionOs(),
            embedded = fixture("player_ok.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "Fixture video title — 1080p",
                "Fixture video title — 720p",
                "Fixture video title — Audio 131 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(List(3) { DEVICE_HOST }, result.candidates.map(::hostOf))
        assertEquals(
            listOf(true, true, false),
            result.candidates.map { it.audioCompanion != null },
        )
        // A separate video merged with its audio track and the audio track itself are complete
        // (P22): the embedded player could add only progressive files of the same qualities.
        assertEquals(listOf("VISIONOS"), http.postedClients())
        assertTrue(result.details.contains("client VISIONOS: 3 downloads offered"))
    }

    @Test
    fun `a merged row takes a progressive file's place, never the other way round`() = runTest {
        // The embedded player offers only the progressive 360p file, which has no size.
        val fileOnly = fixture("player_android.json").replace(ANDROID_HOST, EMBED_HOST)
        val ladder = client(
            page = watchPage(fixture("player_cipher_ladder.json")),
            embedded = fileOnly,
        )

        val result = YouTubeExtractor(ladder, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(LADDER_ROWS, result.candidates.map { it.title?.substringAfterLast("— ") })
        // The page's merged 360p row, which has a size, takes the embedded player's file's place.
        val p360 = result.candidates.single { it.height == 360 }
        assertEquals(134, itagOf(p360))
        assertEquals(41_503_321L + AAC_BYTES, p360.contentLengthBytes)
        assertTrue(result.candidates.all { it.mediaUrl.startsWith(PAGE_MEDIA) })
        assertTrue(result.details.contains("client WEB_EMBEDDED_PLAYER: 1 download offered"))
        assertTrue(
            result.details.contains("client MWEB (watch page response): 9 downloads offered"),
        )

        // A progressive file never takes a row's place: the Android app's file comes too late.
        val files = client(
            page = watchPage(fixture("player_sabr_only.json")),
            embedded = fileOnly,
            android = fixture("player_android.json"),
        )

        val first = YouTubeExtractor(files, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(listOf(EMBED_HOST), first.candidates.map(::hostOf))
        assertTrue(first.details.contains("client ANDROID: 0 downloads offered"))
        assertEquals(listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"), files.postedClients())
    }

    @Test
    fun `merged rows and audio are offered when no client has a video with sound`() = runTest {
        val sabr = fixture("player_sabr_only.json")
        val http = client(
            page = watchPage(sabr),
            visionOs = chainVisionOs(),
            android = sabr,
            embedded = fixture("player_embed_refused.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "Fixture video title — 1080p",
                "Fixture video title — 720p",
                "Fixture video title — Audio 131 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        val audio = result.candidates.last()
        assertEquals("audio/mp4", audio.mimeType)
        assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$DEVICE_HOST/") })
        result.candidates.dropLast(1).forEach { merged ->
            assertEquals(audio.mediaUrl, merged.audioCompanion?.mediaUrl)
        }
        // They are complete (P22), so the clients that would answer through SABR are not asked.
        assertEquals(listOf("VISIONOS"), http.postedClients())
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
            listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"),
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

        // visionOS asked again meets the age check, so the page's own answer is what is left.
        val watchable = client(
            page = watchPage(ownStreams()),
            visionOs = BOT_CHECK,
            visionOsAgain = fixture("player_age_gate.json"),
            android = fixture("player_android.json"),
            embedded = fixture("player_ok.json"),
        )

        val result = YouTubeExtractor(watchable, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(4, result.candidates.size)
        assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
        assertEquals(listOf("VISIONOS", "VISIONOS"), watchable.postedClients())
        assertTrue(
            result.details.contains(
                "client VISIONOS again, with visitor data: age check, so only the user's " +
                    "session is asked",
            ),
        )

        // An age check from the Android app, asked last, drops what the embedded player offered
        // before it, and the page's own download of the same quality takes its place again.
        val late = client(
            page = watchPage(fixture("player_cipher.json").replace(EMBED_HOST, PAGE_HOST)),
            embedded = fixture("player_android.json").replace(ANDROID_HOST, EMBED_HOST),
            android = fixture("player_age_gate.json"),
        )

        val kept = YouTubeExtractor(late, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf("360p", "Audio 129 kbps"),
            kept.candidates.map { it.title?.substringAfterLast("— ") },
        )
        assertEquals(List(2) { PAGE_HOST }, kept.candidates.map(::hostOf))
        assertEquals(listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"), late.postedClients())
        assertTrue(
            kept.details.contains(
                "client ANDROID: age check, so only the user's session is kept",
            ),
        )
    }

    @Test
    fun `clients are asked in the order the owner chose`() = runTest {
        // A visionOS request that failed is not asked again; the Android app comes last (P22).
        val inline = client(page = watchPage(BOT_CHECK))
        YouTubeExtractor(inline).extract(request())
        assertEquals(
            listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"),
            inline.postedClients(),
        )

        // visionOS's refusal is asked again, with the page's visitor data (P22).
        val refused = client(page = watchPage(BOT_CHECK), visionOs = BOT_CHECK)
        YouTubeExtractor(refused).extract(request())
        assertEquals(
            listOf("VISIONOS", "VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"),
            refused.postedClients(),
        )

        // visionOS is asked before the page; without an answer in the page, the page's own
        // client is asked next.
        val asked = client(page = watchPage(null), own = BOT_CHECK)
        YouTubeExtractor(asked).extract(request())
        assertEquals(
            listOf("VISIONOS", "MWEB", "WEB_EMBEDDED_PLAYER", "ANDROID"),
            asked.postedClients(),
        )

        // A desktop page is followed by YouTube's mobile site, still before the Android app.
        val desktop = client(page = desktopPage(null), own = BOT_CHECK)
        YouTubeExtractor(desktop).extract(request())
        assertEquals(
            listOf("VISIONOS", "WEB", "WEB_EMBEDDED_PLAYER", MOBILE_SITE, "ANDROID"),
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

            assertEquals(4, result.candidates.size)
            assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
            assertTrue(result.candidates.all { it.requestContext.cookie == null })
            assertEquals(
                listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", MOBILE_SITE),
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
            assertTrue(result.details.contains("client MWEB: 4 downloads offered"))
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

        assertEquals(4, result.candidates.size)
        result.candidates.forEach { candidate ->
            assertTrue(candidate.mediaUrl.endsWith("&pot=${FakePoTokenProvider.TOKEN}"))
        }
        // The audio track a merged row is downloaded with carries the same token.
        val companion = result.candidates.mapNotNull { it.audioCompanion }.single()
        assertTrue(companion.mediaUrl.endsWith("&pot=${FakePoTokenProvider.TOKEN}"))
        // One token per lookup, bound to the video, for the request and every address.
        val minted = tokens.requests.single()
        assertEquals(VIDEO_ID, minted.contentBinding)
        assertEquals(PHONE_PLAYER, minted.playerScriptUrl)
        assertEquals(CANONICAL, minted.pageUrl)
        val own = BoundedJsonParser.parse(http.bodyOf("MWEB"), maxNodes = 1_000)
        assertEquals(
            FakePoTokenProvider.TOKEN,
            own.path("serviceIntegrityDimensions", "poToken").text,
        )
        http.postedBodies.filter { clientOf(it) != "MWEB" }.forEach { body ->
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

            assertEquals(detail, 4, result.candidates.size)
            assertTrue(detail, result.candidates.none { it.mediaUrl.contains("pot=") })
            assertTrue(
                detail,
                result.candidates.none { it.audioCompanion?.mediaUrl?.contains("pot=") == true },
            )
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
                val own = BoundedJsonParser.parse(http.bodyOf("MWEB"), maxNodes = 1_000)
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
            listOf("VISIONOS", "MWEB", "WEB_EMBEDDED_PLAYER"),
            http.postedClients(),
        )
        val pageClient = http.postedClients().indexOf("MWEB")
        val signedIn = http.postedHeaders[pageClient]
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
        http.postedHeaders.filterIndexed { index, _ -> index != pageClient }.forEach { headers ->
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

        assertEquals(4, result.candidates.size)
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
        assertEquals(listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "ANDROID"), http.postedClients())
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
                "client VISIONOS: LOGIN_REQUIRED; with URLs: 0 progressive, 0 adaptive; SABR no",
                "client VISIONOS reason: Sign in to confirm you’re not a bot " +
                    "This helps protect our community. Learn more",
                "visionOS first: no playable answer, so the watch page is read",
                "watch page GET 200 (${page.length} characters)",
                "client MWEB (watch page response): LOGIN_REQUIRED; with URLs: " +
                    "0 progressive, 0 adaptive; SABR no",
                "client MWEB (watch page response) reason: Sign in to confirm you’re not a bot " +
                    "This helps protect our community. Learn more",
                "client VISIONOS again, with visitor data: LOGIN_REQUIRED; with URLs: " +
                    "0 progressive, 0 adaptive; SABR no",
                "client VISIONOS again, with visitor data reason: Sign in to confirm you’re " +
                    "not a bot This helps protect our community. Learn more",
                "client WEB_EMBEDDED_PLAYER: ERROR; with URLs: 0 progressive, 0 adaptive; SABR no",
                "client WEB_EMBEDDED_PLAYER reason: This video is unavailable",
                "client ANDROID: HTTP_STATUS (HTTP 404)",
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
        assertTrue(result.details.contains("client MWEB: 4 downloads offered"))
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
                listOf("VISIONOS", "MWEB", "WEB_EMBEDDED_PLAYER"),
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
            // Only visionOS, asked before the page and without the page's key, was posted to.
            assertEquals(name, listOf(PLAYER_URL_WITHOUT_PAGE), http.postedUrls)
            assertTrue(name, runner.requests.isEmpty())
        }
    }

    @Test
    fun `an embed refusal falls back to the watch page's own streams`() = runTest {
        val own = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)
        val http = client(page = watchPage(own), embedded = fixture("player_embed_refused.json"))

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
            as SiteExtractionResult.Success

        assertEquals(4, result.candidates.size)
        assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$PAGE_HOST/") })
        assertTrue(result.candidates.all { it.requestContext.cookie == null })
        assertEquals(listOf("VISIONOS", "WEB_EMBEDDED_PLAYER"), http.postedClients())
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
                listOf("VISIONOS", "MWEB", "WEB_EMBEDDED_PLAYER"),
                http.postedClients(),
            )
            val headers = http.postedHeaders[http.postedClients().indexOf("MWEB")]
            assertEquals(COOKIE, headers["Cookie"])
            assertEquals(USER_AGENT, headers["User-Agent"])
            assertEquals("2", headers["X-YouTube-Client-Name"])
            assertEquals("2.20261002.01.00", headers["X-YouTube-Client-Version"])
            assertEquals(CANONICAL, headers["Referer"])
            // The fixture cookie holds no signed-in session, so there is nothing to authorize.
            assertNull(headers["Authorization"])
            val body = BoundedJsonParser.parse(http.bodyOf("MWEB"), maxNodes = 1_000)
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
        assertEquals(listOf("VISIONOS", "MWEB"), http.postedClients())
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
                    "client VISIONOS: OK; with URLs: 0 progressive, 0 adaptive; SABR only",
                    "client MWEB (watch page response): OK; with URLs: 0 progressive, " +
                        "0 adaptive; SABR only",
                    "client VISIONOS again, with visitor data: OK; with URLs: 0 progressive, " +
                        "0 adaptive; SABR only",
                    "client WEB_EMBEDDED_PLAYER: OK; with URLs: 0 progressive, 0 adaptive; " +
                        "SABR only",
                    "client ANDROID: OK; with URLs: 0 progressive, 0 adaptive; SABR only",
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
            visionOs = unknown,
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.RESPONSE_CHANGED, result)
        assertEquals(List(5) { PLAYER_URL_WITHOUT_PAGE }, http.postedUrls)
        // A page that names no client of its own is treated as the desktop site. It gives no
        // visitor data either, so visionOS's refusal is not asked again (P22).
        assertEquals(
            listOf("VISIONOS", "WEB", "WEB_EMBEDDED_PLAYER", MOBILE_SITE, "ANDROID"),
            http.postedClients(),
        )
        assertTrue(
            (result as SiteExtractionResult.Failure).details.contains(
                "visionOS again: the watch page gave no visitor data, so not asked",
            ),
        )
        assertNull(http.postedHeaders[0]["X-Goog-Visitor-Id"])
        val embedded = http.bodyOf("WEB_EMBEDDED_PLAYER")
        val embeddedBody = BoundedJsonParser.parse(embedded, maxNodes = 1_000)
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
                details = listOf(
                    "client VISIONOS: HTTP_STATUS (HTTP 404)",
                    "visionOS first: no playable answer, so the watch page is read",
                    "watch page GET failed (HTTP_STATUS)",
                ),
            ),
            result,
        )
        assertEquals(listOf(PLAYER_URL_WITHOUT_PAGE), http.postedUrls)
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

    @Test
    fun `a complete visionOS answer is the whole lookup, without the watch page`() = runTest {
        val http = client(
            page = watchPage(ownStreams()),
            visionOs = fixture("player_visionos.json"),
            android = fixture("player_android.json"),
            embedded = fixture("player_ok.json"),
        )
        val runner = FakePlayerScriptRunner()
        val tokens = FakePoTokenProvider()

        val result = YouTubeExtractor(http, runner, tokens).extract(request())
            as SiteExtractionResult.Success

        // One small request (P14): no watch page, no other client, no script and no token.
        assertTrue(http.requestedUrls.isEmpty())
        assertEquals(listOf("VISIONOS"), http.postedClients())
        assertEquals(listOf(PLAYER_URL_WITHOUT_PAGE), http.postedUrls)
        assertTrue(runner.requests.isEmpty())
        assertTrue(tokens.requests.isEmpty())
        // Asked as the app it names: no cookie, no session and no page values.
        val headers = http.postedHeaders.single()
        assertFalse(headers.keys.any { it.equals("Cookie", ignoreCase = true) })
        assertFalse(headers.containsKey("Authorization"))
        assertFalse(headers.containsKey("X-Goog-Visitor-Id"))
        assertFalse(http.postedBodies.single().contains("visitorData"))
        assertEquals(
            listOf(
                "Fixture video title — 1080p",
                "Fixture video title — 720p",
                "Fixture video title — Audio 131 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf(84_361_446L, 29_905_327L, 3_449_447L),
            result.candidates.map(MediaCandidate::contentLengthBytes),
        )
        assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$DEVICE_HOST/") })
        assertTrue(result.candidates.all { it.requestContext.cookie == null })
        assertEquals(
            listOf(
                "client VISIONOS: OK; with URLs: 0 progressive, 5 adaptive; SABR yes",
                "client VISIONOS: 3 downloads offered",
                "visionOS first: complete, no watch page",
            ),
            result.details,
        )
    }

    @Test
    fun `any other visionOS answer reads the watch page, whose own streams then count`() =
        runTest {
            val answers = mapOf(
                "no status" to fixture("player_no_status.json"),
                "bot check" to BOT_CHECK,
                "age check" to fixture("player_age_gate.json"),
                "unplayable" to fixture("player_unavailable.json"),
                "private" to fixture("player_private.json"),
                "live" to fixture("player_live.json"),
                "SABR only" to fixture("player_sabr_only.json"),
                "no answer" to null,
            )
            for ((name, answer) in answers) {
                val http = client(page = watchPage(ownStreams()), visionOs = answer)

                val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

                assertTrue(name, result is SiteExtractionResult.Success)
                result as SiteExtractionResult.Success
                assertEquals(name, listOf(PAGE_FETCH), http.requestedUrls)
                assertEquals(name, "VISIONOS", http.postedClients().first())
                assertTrue(name, result.candidates.isNotEmpty())
                assertTrue(name, result.candidates.all { it.mediaUrl.startsWith(PAGE_MEDIA) })
                assertTrue(name, result.details.any { it.startsWith("visionOS first: ") })
            }
        }

    @Test
    fun `an age check from visionOS first leaves the verdict to the watch page`() = runTest {
        val http = client(
            page = watchPage(fixture("player_age_gate.json")),
            visionOs = fixture("player_age_gate.json"),
            android = fixture("player_android.json"),
            embedded = fixture("player_ok.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        // The page's own age check is final: no other client is asked to get around it.
        assertFailure(SiteExtractionFailure.LOGIN_REQUIRED, result)
        assertEquals(listOf(PAGE_FETCH), http.requestedUrls)
        assertEquals(listOf("VISIONOS"), http.postedClients())
    }

    @Test
    fun `a visionOS answer without a size or with a protected format reads the watch page`() =
        runTest {
            val complete = fixture("player_visionos.json")
            val answers = listOf(
                "a format without a size" to complete.replace(LOW_AUDIO_LENGTH, ""),
                "a format without a direct address" to complete.replace(
                    LOW_AUDIO_URL,
                    "\"signatureCipher\": \"s=AJfixtureCipher&sp=sig&url=https%3A%2F%2F" +
                        "$DEVICE_HOST%2Fvideoplayback%3Fitag%3D139\"",
                ),
                "a format without a direct address" to complete.replace(
                    LOW_AUDIO_URL,
                    "\"signingInfo\": {}",
                ),
            )
            for ((gap, answer) in answers) {
                assertTrue(gap, answer != complete)
                val http = client(page = watchPage(ownStreams()), visionOs = answer)

                val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
                    as SiteExtractionResult.Success

                assertEquals(gap, listOf(PAGE_FETCH), http.requestedUrls)
                assertTrue(
                    gap,
                    result.details.contains("visionOS first: $gap, so the watch page is read"),
                )
            }
        }

    @Test
    fun `DRM stays refused when visionOS is asked first`() = runTest {
        val http = client(
            page = watchPage(fixture("player_drm.json")),
            visionOs = fixture("player_drm.json"),
            android = fixture("player_android.json"),
            embedded = fixture("player_ok.json"),
        )

        val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

        assertFailure(SiteExtractionFailure.DRM_PROTECTED, result)
        assertEquals(listOf(PAGE_FETCH), http.requestedUrls)
        assertEquals(listOf("VISIONOS"), http.postedClients())
    }

    @Test
    fun `visionOS asked again with the page's visitor data gives every quality with its size`() =
        runTest {
            val http = client(
                page = watchPage(BOT_CHECK),
                visionOs = BOT_CHECK,
                visionOsAgain = fixture("player_visionos_ladder.json"),
                android = fixture("player_android.json"),
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())
                as SiteExtractionResult.Success

            assertEquals(LADDER_ROWS, result.candidates.map { it.title?.substringAfterLast("— ") })
            assertEquals(
                listOf(313, 271, 137, 136, 135, 134, 133, 160, 140),
                result.candidates.map(::itagOf),
            )
            assertTrue(result.candidates.all { it.mediaUrl.startsWith("https://$DEVICE_HOST/") })
            // Each row is as large as its video and audio files together, as YouTube states.
            assertEquals(LADDER_BYTES, result.candidates.map(MediaCandidate::contentLengthBytes))
            assertEquals(
                listOf(2160, 1440, 1080, 720, 480, 360, 240, 144, null),
                result.candidates.map(MediaCandidate::height),
            )
            assertEquals(
                List(2) { "audio/webm" } + List(6) { "audio/mp4" } + listOf(null),
                result.candidates.map { it.audioCompanion?.mimeType },
            )
            // The M4A row is the AAC track itself, with its size; MP3 is converted from it.
            val audio = result.candidates.last()
            assertEquals("audio/mp4", audio.mimeType)
            assertEquals(listOf("mp4a.40.2"), audio.codecs)
            assertEquals(AAC_BYTES, audio.contentLengthBytes)

            // Asked again as before the page, with only the page's visitor data added, in the
            // request and its header, and still without the page's key or the user's cookie.
            assertEquals(listOf("VISIONOS", "VISIONOS"), http.postedClients())
            assertEquals(List(2) { PLAYER_URL_WITHOUT_PAGE }, http.postedUrls)
            val (before, again) = http.postedBodies.map { body ->
                BoundedJsonParser.parse(body, maxNodes = 1_000)
            }
            assertNull(before.path("context", "client", "visitorData"))
            assertEquals(VISITOR_DATA, again.path("context", "client", "visitorData").text)
            assertNull(
                again.path("playbackContext", "contentPlaybackContext", "signatureTimestamp"),
            )
            val (first, second) = http.postedHeaders
            assertNull(first["X-Goog-Visitor-Id"])
            assertEquals(VISITOR_DATA, second["X-Goog-Visitor-Id"])
            assertEquals(first, second - "X-Goog-Visitor-Id")
            assertTrue(
                result.details.contains(
                    "client VISIONOS again, with visitor data: OK; with URLs: 0 progressive, " +
                        "11 adaptive; SABR yes",
                ),
            )
            assertTrue(
                result.details.contains(
                    "client VISIONOS again, with visitor data: 9 downloads offered",
                ),
            )
            assertTrue(result.details.none { it.contains(VISITOR_DATA) })
        }

    @Test
    fun `refused twice, visionOS leaves the ladder to the page client's script-signed formats`() =
        runTest {
            val runner = FakePlayerScriptRunner()
            val http = client(
                page = watchPage(fixture("player_cipher_ladder.json")),
                visionOs = BOT_CHECK,
                embedded = BOT_CHECK,
                android = fixture("player_android.json"),
            )

            val result = YouTubeExtractor(http, runner, FakePoTokenProvider()).extract(request())
                as SiteExtractionResult.Success

            assertEquals(LADDER_ROWS, result.candidates.map { it.title?.substringAfterLast("— ") })
            assertEquals(LADDER_BYTES, result.candidates.map(MediaCandidate::contentLengthBytes))
            assertTrue(result.candidates.all { it.mediaUrl.startsWith(PAGE_MEDIA) })
            // Signed by YouTube's own player script and carrying the page's token.
            assertEquals(
                "${PAGE_MEDIA}videoplayback?expire=4102444800&ei=UGFnZQ&itag=137&source=youtube" +
                    "&mime=video%2Fmp4&n=$SOLVED_RATE&sig=Sgis-731-DETCADER" +
                    "&pot=${FakePoTokenProvider.TOKEN}",
                result.candidates.single { it.height == 1080 }.mediaUrl,
            )
            val challenges = runner.requests.single().challenges
            assertEquals(10, challenges.count { it.kind == PlayerScriptChallengeKind.SIGNATURE })
            // The Android app, whose only download is its 360p file, is never asked.
            assertEquals(
                listOf("VISIONOS", "VISIONOS", "WEB_EMBEDDED_PLAYER"),
                http.postedClients(),
            )
            listOf(
                "client MWEB (watch page response): 10 formats via player script",
                "client MWEB (watch page response): 9 downloads offered",
            ).forEach { line -> assertTrue(line, result.details.contains(line)) }
        }

    @Test
    fun `an age-restricted video still asks for a sign-in, and visionOS is not asked again`() =
        runTest {
            val http = client(
                page = watchPage(fixture("player_age_gate.json")),
                visionOs = BOT_CHECK,
                visionOsAgain = fixture("player_visionos_ladder.json"),
                embedded = fixture("player_ok.json"),
                android = fixture("player_android.json"),
            )

            val result = YouTubeExtractor(http, FakePlayerScriptRunner()).extract(request())

            assertFailure(SiteExtractionFailure.LOGIN_REQUIRED, result)
            // The page's own verdict about the video is final: nothing is asked to get around it.
            assertEquals(listOf("VISIONOS"), http.postedClients())
        }

    @Test
    fun `the Android app's 360p file is a row only when no client streams 360p separately`() =
        runTest {
            val sabr = client(
                page = watchPage(fixture("player_sabr_only.json")),
                visionOs = fixture("player_private.json"),
                android = fixture("player_android.json"),
            )

            val file = YouTubeExtractor(sabr, FakePlayerScriptRunner()).extract(request())
                as SiteExtractionResult.Success

            // Nothing else streams separately, so the file, without a size, is the one row.
            assertEquals(listOf(18), file.candidates.map(::itagOf))
            assertNull(file.candidates.single().contentLengthBytes)
            assertEquals(
                listOf(
                    "client ANDROID: OK; with URLs: 1 progressive, 0 adaptive; SABR yes",
                    "client ANDROID: 2 adaptive formats only through SABR",
                    "client ANDROID: 1 download offered",
                ),
                file.details.filter { it.startsWith("client ANDROID") },
            )

            val ladder = client(
                page = watchPage(fixture("player_sabr_only.json")),
                visionOs = BOT_CHECK,
                visionOsAgain = fixture("player_visionos_ladder.json"),
                android = fixture("player_android.json"),
            )

            val merged = YouTubeExtractor(ladder, FakePlayerScriptRunner()).extract(request())
                as SiteExtractionResult.Success

            // The separate 360p video, which has a size, is the row; the app is not asked.
            assertEquals(134, itagOf(merged.candidates.single { it.height == 360 }))
            assertFalse(ladder.postedClients().contains("ANDROID"))
        }

    private fun hostOf(candidate: MediaCandidate): String =
        candidate.mediaUrl.removePrefix("https://").substringBefore('/')

    private fun itagOf(candidate: MediaCandidate): Int = itagOf(candidate.mediaUrl)

    private fun itagOf(url: String): Int =
        Regex("[?&]itag=(\\d+)").find(url)!!.groupValues[1].toInt()

    /**
     * Serves [page] for the watch page and answers each client with its own document.
     *
     * A client left null is answered with an HTTP 404, the fake's fallback. [visionOsAgain],
     * when given, answers visionOS asked with visitor data, which only the watch page gives.
     */
    private fun client(
        page: String?,
        embedded: String? = null,
        own: String? = null,
        visionOs: String? = null,
        android: String? = null,
        mobile: String? = null,
        visionOsAgain: String? = null,
    ): FakeExtractorHttpClient = FakeExtractorHttpClient(
        responses = page
            ?.let { mapOf(PAGE_FETCH to FakeExtractorHttpClient.html(it, MOBILE_PAGE)) }
            .orEmpty(),
        postResponder = { url, body ->
            val answer = when (clientOf(body)) {
                "WEB_EMBEDDED_PLAYER" -> embedded
                "VISIONOS" -> visionOsAgain?.takeIf { "\"visitorData\"" in body } ?: visionOs
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

    /** The one request body [client] was asked with. */
    private fun FakeExtractorHttpClient.bodyOf(client: String): String =
        postedBodies.single { clientOf(it) == client }

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

    /**
     * visionOS's answer with one unused format (the low-bitrate audio) without a size, so it is
     * not a lookup on its own (P14) and the chain runs with the same rows.
     */
    private fun chainVisionOs(): String =
        fixture("player_visionos.json").replace(LOW_AUDIO_LENGTH, "")

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
        const val PAGE_MEDIA = "https://$PAGE_HOST/"
        const val PLAYER_URL_WITHOUT_PAGE =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        val BOT_CHECK: String = Fixtures.read("youtube/player_bot_check.json")

        /** The rows of `player_visionos_ladder.json` and `player_cipher_ladder.json` (P22). */
        val LADDER_ROWS = listOf(
            "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p", "Audio 131 kbps",
        )
        const val AAC_BYTES = 12_722_101L
        const val OPUS_BYTES = 12_010_444L

        /** Each ladder row's size: its video's and its audio track's together. */
        val LADDER_BYTES = listOf(1_402_997_331L, 702_334_112L).map { it + OPUS_BYTES } +
            listOf(367_301_118L, 111_303_552L, 68_012_007L, 41_503_321L, 9_871_230L, 4_812_344L)
                .map { it + AAC_BYTES } + AAC_BYTES

        /** The low-bitrate audio format's size and address in `player_visionos.json`. */
        val LOW_AUDIO_LENGTH = Regex(""""contentLength":\s*"1300631",\s*""")
        val LOW_AUDIO_URL = Regex(""""url":\s*"https://[^"]*itag=139[^"]*"""")
    }
}
