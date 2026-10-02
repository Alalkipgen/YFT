package com.alal.yft.extractor.sites.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlsTest {
    @Test
    fun `single-video shapes collapse onto the canonical watch page`() {
        listOf(
            "https://www.youtube.com/watch?v=Yft0Fixture",
            "https://www.youtube.com/watch?feature=share&v=Yft0Fixture&t=30s",
            "https://youtube.com/watch?v=Yft0Fixture",
            "https://m.youtube.com/watch?v=Yft0Fixture",
            "https://music.youtube.com/watch?v=Yft0Fixture&list=RDfixture",
            "https://youtu.be/Yft0Fixture?si=fixture",
            "https://www.youtube.com/shorts/Yft0Fixture",
            "https://www.youtube.com/embed/Yft0Fixture?start=5",
            "https://www.youtube-nocookie.com/embed/Yft0Fixture",
            "https://www.youtube.com/live/Yft0Fixture?feature=share",
            "https://www.youtube.com/v/Yft0Fixture",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=Yft0Fixture",
        ).forEach { url ->
            val identity = YouTubeUrls.identify(url)
            assertEquals(url, "youtube", identity?.siteId)
            assertEquals(url, "Yft0Fixture", identity?.contentId)
            assertEquals(
                url,
                "https://www.youtube.com/watch?v=Yft0Fixture",
                identity?.canonicalPageUrl,
            )
        }
    }

    @Test
    fun `channels, playlists, searches and malformed addresses are not claimed`() {
        listOf(
            "http://www.youtube.com/watch?v=Yft0Fixture",
            "https://www.youtube.com/@FixtureChannel",
            "https://www.youtube.com/channel/UCfixture0000000000000000",
            "https://www.youtube.com/playlist?list=PLfixture",
            "https://www.youtube.com/results?search_query=fixture",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=Yft0Fixture1",
            "https://www.youtube.com/watch?v=Yft0Fixtur%21",
            "https://www.youtube.com/watch",
            "https://youtu.be/",
            "https://user@www.youtube.com/watch?v=Yft0Fixture",
            "https://www.youtube.com.example.test/watch?v=Yft0Fixture",
            "https://notyoutube.com/watch?v=Yft0Fixture",
            "not a url",
        ).forEach { url ->
            assertNull(url, YouTubeUrls.identify(url))
        }
    }

    @Test
    fun `the player version is read only from YouTube's own script path`() {
        mapOf(
            "\\/s\\/player\\/f1x7ure0\\/player-plasma-es6-en_US.vflset\\/base.js" to "f1x7ure0",
            "/s/player/f1x7ure0/player_ias.vflset/en_US/base.js" to "f1x7ure0",
            "https://www.youtube.com/s/player/f1x7ure0/player_ias.vflset/en_US/base.js" to
                "f1x7ure0",
            "https://evil.example.test/s/player/f1x7ure0/player_ias.vflset/en_US/base.js" to null,
            "/s/player/../../evil/base.js" to null,
            "/s/player/f1x7ure0/player.css" to null,
            "/s/player/bad!id/base.js" to null,
            "//www.youtube.com/s/player/f1x7ure0/base.js" to null,
            "" to null,
        ).forEach { (raw, expected) ->
            assertEquals(raw, expected, YouTubeUrls.playerId(raw))
        }
        assertNull(YouTubeUrls.playerId(null))
    }

    @Test
    fun `player script addresses name the phone build by default`() {
        assertEquals(
            "https://www.youtube.com/s/player/f1x7ure0/" +
                "player-plasma-ias-phone-en_US.vflset/base.js",
            YouTubeUrls.playerScriptUrl("f1x7ure0"),
        )
        assertEquals(
            "https://www.youtube.com/s/player/f1x7ure0/player_ias.vflset/en_US/base.js",
            YouTubeUrls.playerScriptUrl("f1x7ure0", YouTubeUrls.MAIN_PLAYER_VARIANT),
        )
    }

    @Test
    fun `the player endpoint carries the page's key only when it looks like one`() {
        val base = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        assertEquals(base, YouTubeUrls.innerTubeUrl(null))
        assertEquals(base, YouTubeUrls.innerTubeUrl("bad key&x=1"))
        assertEquals("$base&key=yftFixtureKey0000", YouTubeUrls.innerTubeUrl("yftFixtureKey0000"))
    }

    @Test
    fun `query parameters are replaced and appended with encoding`() {
        val url = "https://media.example-cdn.test/videoplayback?a=1&n=old&b=2"
        assertEquals(
            "https://media.example-cdn.test/videoplayback?a=1&n=new%2B%2F%3D&b=2",
            YouTubeUrls.replaceQueryParam(url, "n", "new+/="),
        )
        assertEquals("$url&sig=AB%3D%3D", YouTubeUrls.appendQueryParam(url, "sig", "AB=="))
        assertEquals("old", YouTubeUrls.queryParamOf(url, "n"))
        assertNull(YouTubeUrls.queryParamOf(url, "missing"))
        assertNull(YouTubeUrls.queryParamOf("https://media.example-cdn.test/p", "n"))
    }
}
