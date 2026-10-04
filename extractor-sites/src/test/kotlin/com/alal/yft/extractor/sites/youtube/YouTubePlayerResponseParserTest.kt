package com.alal.yft.extractor.sites.youtube

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.sites.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlayerResponseParserTest {
    @Test
    fun `page signals are read from the watch page without running it`() {
        val signals = YouTubePlayerResponseParser.pageSignals(
            fixture("watch_page.html").replace("__PLAYER_RESPONSE__", fixture("player_ok.json")),
        )

        assertEquals("f1x7ure0", signals.playerId)
        assertEquals("yft-fixture-innertube-key-not-real", signals.apiKey)
        assertEquals("MWEB", signals.clientName)
        assertEquals("2.20261002.01.00", signals.clientVersion)
        assertEquals("CgtGaXh0dXJlVmlzaXRvcijAgICABg%3D%3D", signals.visitorData)
        assertEquals(20_726, signals.signatureTimestamp)
        val inline = requireNotNull(signals.playerResponseJson)
        assertTrue(inline.startsWith("{") && inline.endsWith("}"))
        assertTrue(inline.contains("\"Yft0Fixture\""))
        assertFalse(signals.toString().contains("CgtGaXh0"))
        assertNull(signals.loggedIn)
        assertNull(signals.dataSyncId)
        assertNull(signals.sessionIndex)
        assertTrue(signals.contentBoundPoToken)
    }

    @Test
    fun `the content binding experiment is read in either escaping and only when on`() {
        val page = fixture("watch_page.html")
        val flag = "html5_generate_content_po_token\\u003dtrue"
        assertTrue(page.contains(flag))
        val plain = page.replace(flag, "html5_generate_content_po_token=true")
        val off = page.replace(flag, "html5_generate_content_po_token\\u003dfalse")
        val absent = page.replace(flag, "html5_fixture\\u003d1")

        assertTrue(YouTubePlayerResponseParser.pageSignals(plain).contentBoundPoToken)
        assertFalse(YouTubePlayerResponseParser.pageSignals(off).contentBoundPoToken)
        assertFalse(YouTubePlayerResponseParser.pageSignals(absent).contentBoundPoToken)
    }

    @Test
    fun `a signed-in page names its session without the session printing`() {
        val signals = YouTubePlayerResponseParser.pageSignals(
            fixture("watch_page.html").replace(
                "\"HL\":\"en\",",
                "\"HL\":\"en\",\"LOGGED_IN\":true," +
                    "\"DATASYNC_ID\":\"Fixture0Delegated||Fixture0User\",\"SESSION_INDEX\":\"1\",",
            ),
        )

        assertEquals(true, signals.loggedIn)
        assertEquals("Fixture0Delegated||Fixture0User", signals.dataSyncId)
        assertEquals(1, signals.sessionIndex)
        assertFalse(signals.toString().contains("Fixture0"))

        val anonymous = YouTubePlayerResponseParser.pageSignals(
            fixture("watch_page.html").replace(
                "\"HL\":\"en\",",
                "\"HL\":\"en\",\"LOGGED_IN\":false,\"DATASYNC_ID\":\"Fixture0Visitor||\",",
            ),
        )
        assertEquals(false, anonymous.loggedIn)
        assertNull(anonymous.sessionIndex)
    }

    @Test
    fun `the null placeholder alone is not an inline response`() {
        val signals = YouTubePlayerResponseParser.pageSignals(
            fixture("watch_page.html").replace("__PLAYER_RESPONSE__", ""),
        )

        assertNull(signals.playerResponseJson)
        assertEquals("f1x7ure0", signals.playerId)
    }

    @Test
    fun `a changed page yields no signals`() {
        val signals = YouTubePlayerResponseParser.pageSignals(fixture("changed_markup.html"))

        assertNull(signals.playerResponseJson)
        assertNull(signals.playerId)
        assertNull(signals.apiKey)
        assertNull(signals.clientVersion)
        assertNull(signals.visitorData)
        assertNull(signals.signatureTimestamp)
    }

    @Test
    fun `a playable response lists progressive and adaptive streams`() {
        val video = (parse("player_ok.json") as YouTubeParseResult.Success).video

        assertEquals("Yft0Fixture", video.videoId)
        assertEquals("Fixture video title", video.title)
        assertEquals("Fixture Channel", video.author)
        assertEquals(212_000L, video.durationMillis)
        assertEquals(NOW + 21_540_000L, video.expiresAtEpochMs)
        assertEquals(listOf(18, 22), video.progressive.map(YouTubeStream::itag))
        assertEquals(listOf(137, 140, 140, 140, 251), video.adaptive.map(YouTubeStream::itag))

        val progressive = video.progressive.first()
        assertEquals("video/mp4", progressive.mimeType)
        assertEquals(listOf("avc1.42001E", "mp4a.40.2"), progressive.codecs)
        assertTrue(progressive.hasVideo && progressive.hasAudio)
        assertEquals(360, progressive.height)
        assertFalse(progressive.isProtected)

        val videoOnly = video.adaptive.first()
        assertTrue(videoOnly.hasVideo)
        assertFalse(videoOnly.hasAudio)

        val (drc, dub, original) = video.adaptive.filter { it.itag == 140 }
        assertTrue(drc.isDrc)
        assertEquals(false, dub.isDefaultAudio)
        assertEquals(true, original.isDefaultAudio)
        assertEquals(129_478L, original.bitrate)
        assertFalse(original.toString().contains("googlevideo"))
    }

    @Test
    fun `protected streams keep their descriptor opaque`() {
        val video = (parse("player_cipher.json") as YouTubeParseResult.Success).video

        val stream = video.progressive.first()
        assertTrue(stream.isProtected)
        assertNull(stream.url)
        assertTrue(stream.protectedDescriptor!!.startsWith("s=AOq0QJ8w"))
        assertFalse(stream.toString().contains("AOq0QJ8w"))
    }

    @Test
    fun `playability verdicts map onto structured failures`() {
        mapOf(
            "player_bot_check.json" to (SiteExtractionFailure.BOT_CHECK to false),
            "player_private.json" to (SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to true),
            "player_geo.json" to (SiteExtractionFailure.GEO_RESTRICTED to true),
            "player_embed_refused.json" to (SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to false),
            "player_unavailable.json" to (SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to true),
            "player_drm.json" to (SiteExtractionFailure.DRM_PROTECTED to true),
            "player_live.json" to (SiteExtractionFailure.NO_MEDIA_FOUND to true),
            "player_sabr_only.json" to (SiteExtractionFailure.NO_MEDIA_FOUND to false),
            "player_no_status.json" to (SiteExtractionFailure.RESPONSE_CHANGED to false),
            "player_malformed.json" to (SiteExtractionFailure.MALFORMED_RESPONSE to false),
        ).forEach { (name, expected) ->
            val (reason, definite) = expected
            assertEquals(name, YouTubeParseResult.Failure(reason, definite), parse(name))
        }
        assertEquals(
            YouTubeParseResult.Failure(
                SiteExtractionFailure.LOGIN_REQUIRED,
                definite = true,
                ageCheck = true,
            ),
            parse("player_age_gate.json"),
        )
    }

    @Test
    fun `only YouTube's age checks are marked as age checks`() {
        listOf("AGE_VERIFICATION_REQUIRED", "AGE_CHECK_REQUIRED").forEach { name ->
            assertEquals(
                name,
                YouTubeParseResult.Failure(
                    SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                    definite = true,
                    ageCheck = true,
                ),
                YouTubePlayerResponseParser.parse(
                    "{\"playabilityStatus\":{\"status\":\"$name\"}}",
                    NOW,
                ),
            )
        }
        assertEquals(
            YouTubeParseResult.Failure(
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                definite = true,
            ),
            YouTubePlayerResponseParser.parse(
                "{\"playabilityStatus\":{\"status\":\"CONTENT_CHECK_REQUIRED\"}}",
                NOW,
            ),
        )
        listOf("player_private.json", "player_geo.json", "player_bot_check.json").forEach {
            val failure = parse(it) as YouTubeParseResult.Failure
            assertFalse(it, failure.ageCheck)
        }
    }

    @Test
    fun `bot checks are recognised in the reason or on the error screen`() {
        listOf(
            status("\"reason\":\"Sign in to confirm you’re not a bot\""),
            status("\"reason\":\"Sign in to confirm you're not a bot\""),
            status("\"reason\":\"SIGN IN TO CONFIRM YOU'RE NOT A BOT\""),
            status(
                "\"errorScreen\":{\"playerErrorMessageRenderer\":{\"reason\":" +
                    "{\"runs\":[{\"text\":\"Sign in to confirm \"}," +
                    "{\"text\":\"you’re not a bot\"}]}}}",
            ),
            status(
                "\"errorScreen\":{\"playerErrorMessageRenderer\":{\"reason\":" +
                    "{\"simpleText\":\"Sign in to confirm you're not a bot\"}}}",
            ),
        ).forEach { json ->
            assertEquals(
                json,
                YouTubeParseResult.Failure(SiteExtractionFailure.BOT_CHECK, definite = false),
                YouTubePlayerResponseParser.parse(json, NOW),
            )
        }
    }

    @Test
    fun `real sign-in prompts and age gates keep their own verdicts`() {
        assertEquals(
            YouTubeParseResult.Failure(SiteExtractionFailure.LOGIN_REQUIRED, definite = false),
            YouTubePlayerResponseParser.parse(status("\"reason\":\"Please sign in\""), NOW),
        )
        assertEquals(
            YouTubeParseResult.Failure(
                SiteExtractionFailure.LOGIN_REQUIRED,
                definite = true,
                ageCheck = true,
            ),
            YouTubePlayerResponseParser.parse(
                status("\"reason\":\"Sign in to confirm your age\""),
                NOW,
            ),
        )
        assertEquals(
            YouTubeParseResult.Failure(
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                definite = true,
            ),
            YouTubePlayerResponseParser.parse(status("\"reason\":\"Private video\""), NOW),
        )
    }

    @Test
    fun `summaries count addressed formats and flag SABR without keeping an address`() {
        val ok = inspect("player_ok.json").summary
        assertEquals(
            listOf("client WEB: OK; with URLs: 2 progressive, 5 adaptive; SABR no"),
            ok.lines("client WEB"),
        )
        assertEquals(
            listOf("client WEB: OK; with URLs: 0 progressive, 0 adaptive; protected: 3; SABR no"),
            inspect("player_cipher.json").summary.lines("client WEB"),
        )
        val sabr = inspect("player_sabr_only.json").summary
        assertTrue(sabr.sabr)
        assertTrue(sabr.sabrOnly)
        assertEquals(
            listOf("client WEB: OK; with URLs: 0 progressive, 0 adaptive; SABR only"),
            sabr.lines("client WEB"),
        )
        assertEquals(
            listOf("client WEB: unreadable response"),
            inspect("player_malformed.json").summary.lines("client WEB"),
        )
        val bot = inspect("player_bot_check.json").summary
        assertEquals("LOGIN_REQUIRED", bot.status)
        assertEquals(
            "Sign in to confirm you’re not a bot This helps protect our community. Learn more",
            bot.reason,
        )
        listOf(ok, sabr, bot).map(YouTubeResponseSummary::toString).forEach { text ->
            assertFalse(text, text.contains("https"))
            assertFalse(text, text.contains("bot"))
        }
    }

    @Test
    fun `summary reasons keep the words of markup but not its tags`() {
        val json = "{\"playabilityStatus\":{\"status\":\"ERROR\",\"reason\":" +
            "\"This video is unavailable <a href='https://www.youtube.com/watch?v=x' " +
            "target='_blank'>Watch video on YouTube</a>\"}}"

        assertEquals(
            "This video is unavailable Watch video on YouTube",
            YouTubePlayerResponseParser.inspect(json, NOW).summary.reason,
        )
    }

    @Test
    fun `summaries name only well-formed statuses`() {
        assertEquals(
            "no status",
            YouTubePlayerResponseParser.inspect("{\"playabilityStatus\":{}}", NOW).summary.status,
        )
        assertEquals(
            "unrecognized status",
            YouTubePlayerResponseParser.inspect(
                "{\"playabilityStatus\":{\"status\":\"<b>odd</b>\"}}",
                NOW,
            ).summary.status,
        )
    }

    @Test
    fun `statuses without streams or with unknown names are not definite`() {
        assertEquals(
            YouTubeParseResult.Failure(SiteExtractionFailure.LOGIN_REQUIRED, definite = false),
            YouTubePlayerResponseParser.parse("{\"playabilityStatus\":{\"status\":\"OK\"}}", NOW),
        )
        assertEquals(
            YouTubeParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, definite = false),
            YouTubePlayerResponseParser.parse(
                "{\"playabilityStatus\":{\"status\":\"SOMETHING_NEW\"}}",
                NOW,
            ),
        )
        assertEquals(
            YouTubeParseResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, definite = true),
            YouTubePlayerResponseParser.parse(
                "{\"playabilityStatus\":{\"status\":\"LIVE_STREAM_OFFLINE\"}}",
                NOW,
            ),
        )
    }

    private fun parse(name: String): YouTubeParseResult =
        YouTubePlayerResponseParser.parse(fixture(name), NOW)

    private fun inspect(name: String): YouTubePlayerResponse =
        YouTubePlayerResponseParser.inspect(fixture(name), NOW)

    /** A sign-in verdict with the given extra fields, the shape YouTube uses for its gates. */
    private fun status(fields: String): String =
        "{\"playabilityStatus\":{\"status\":\"LOGIN_REQUIRED\",$fields}}"

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private companion object {
        const val NOW = 1_791_000_000_000L
    }
}
