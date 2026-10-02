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
            "player_bot_check.json" to (SiteExtractionFailure.LOGIN_REQUIRED to false),
            "player_private.json" to (SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to true),
            "player_age_gate.json" to (SiteExtractionFailure.LOGIN_REQUIRED to true),
            "player_geo.json" to (SiteExtractionFailure.GEO_RESTRICTED to true),
            "player_embed_refused.json" to (SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to false),
            "player_unavailable.json" to (SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to true),
            "player_drm.json" to (SiteExtractionFailure.DRM_PROTECTED to true),
            "player_live.json" to (SiteExtractionFailure.NO_MEDIA_FOUND to true),
            "player_sabr_only.json" to (SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED to false),
            "player_no_status.json" to (SiteExtractionFailure.RESPONSE_CHANGED to false),
            "player_malformed.json" to (SiteExtractionFailure.MALFORMED_RESPONSE to false),
        ).forEach { (name, expected) ->
            val (reason, definite) = expected
            assertEquals(name, YouTubeParseResult.Failure(reason, definite), parse(name))
        }
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

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private companion object {
        const val NOW = 1_791_000_000_000L
    }
}
