package com.alal.yft.core.browser.detection

import com.alal.yft.core.browser.detection.FocusedVideo.Source
import com.alal.yft.core.browser.detection.FocusedVideoProbe.FeedSite
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P5: what the feed script may hand back and how it becomes one link the site adapter reads.
 * The script itself runs on the sanitized feed fixtures in `FocusedVideoProbeInstrumentedTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusedVideoProbeTest {
    @Test
    fun theScriptOnlyReadsThePageAndReturnsOneLink() {
        val script = FocusedVideoProbe.script

        assertTrue(script.contains("querySelectorAll('video')"))
        assertTrue(script.contains("getBoundingClientRect"))
        assertTrue(script.contains("JSON.stringify({ url, source })"))
        listOf(
            "document.cookie",
            "localStorage",
            "fetch(",
            "XMLHttpRequest",
            "innerText",
            "textContent",
            "appendChild",
            "document.write",
            ".click(",
            ".play(",
        ).forEach { forbidden -> assertFalse(forbidden, script.contains(forbidden)) }
    }

    @Test
    fun theAnswerOfEvaluateJavascriptIsReadAsTheQuotedStringItIs() {
        // WebView hands back the script's string as a JSON string literal.
        val answer = JSONObject.quote(
            """{"url":"https://m.youtube.com/watch?v=BBBBBBBBBB2&pp=tracking","source":"centre"}""",
        )

        assertEquals(
            FocusedVideo("https://www.youtube.com/watch?v=BBBBBBBBBB2", Source.CENTRE),
            FocusedVideoProbe.parse(answer, YOUTUBE_HOME),
        )
        assertEquals(
            FocusedVideo("https://www.youtube.com/watch?v=BBBBBBBBBB2", Source.PLAYING),
            FocusedVideoProbe.parse(
                """{"url":"https://m.youtube.com/watch?v=BBBBBBBBBB2","source":"playing"}""",
                YOUTUBE_HOME,
            ),
        )
    }

    @Test
    fun noVideoInFocusAndBrokenAnswersGiveNothing() {
        listOf(
            null,
            "",
            "null",
            "undefined",
            JSONObject.quote("""{"url":null,"source":"none"}"""),
            JSONObject.quote("""{"url":"","source":"centre"}"""),
            JSONObject.quote("""{"url":"https://m.youtube.com/watch?v=BBBBBBBBBB2"}"""),
            JSONObject.quote("""{"url":"https://m.youtube.com/watch?v=BBBBBBBBBB2","source":1}"""),
            JSONObject.quote("""{"url":42,"source":"centre"}"""),
            JSONObject.quote("""[{"url":"https://m.youtube.com/watch?v=BBBBBBBBBB2"}]"""),
            "\"{\\\"url\\\":",
            "{not json",
            JSONObject.quote(
                """{"url":"https://m.youtube.com/watch?v=BBBBBBBBBB2&x=${"a".repeat(5_000)}",""" +
                    """"source":"centre"}""",
            ),
        ).forEach { answer -> assertNull(answer, FocusedVideoProbe.parse(answer, YOUTUBE_HOME)) }
    }

    @Test
    fun youTubeWatchPagesAndShortsKeepOnlyTheVideoId() {
        mapOf(
            "https://m.youtube.com/watch?v=AAAAAAAAAA1&pp=tracking" to
                "https://www.youtube.com/watch?v=AAAAAAAAAA1",
            "https://www.youtube.com/watch?feature=share&v=AAAAAAAAAA1#t=3" to
                "https://www.youtube.com/watch?v=AAAAAAAAAA1",
            "https://youtube.com/shorts/SSSSSSSSSS6?feature=share" to
                "https://www.youtube.com/shorts/SSSSSSSSSS6",
            "https://m.youtube.com/shorts/SSSSSSSSSS6/" to
                "https://www.youtube.com/shorts/SSSSSSSSSS6",
        ).forEach { (link, canonical) ->
            val found = FocusedVideoProbe.canonicalVideoUrl(link, FeedSite.YOUTUBE)
            assertEquals(link, canonical, found)
        }
        listOf(
            "https://m.youtube.com/",
            "https://m.youtube.com/feed/subscriptions",
            "https://m.youtube.com/@channel",
            "https://m.youtube.com/channel/UCfixture",
            "https://m.youtube.com/watch?v=short",
            "https://m.youtube.com/watch?list=PLfixture",
            "https://m.youtube.com/shorts/",
            "https://m.youtube.com/playlist?list=PLfixture",
        ).forEach { link ->
            assertNull(link, FocusedVideoProbe.canonicalVideoUrl(link, FeedSite.YOUTUBE))
        }
    }

    @Test
    fun facebookVideosReelsSharesAndPostsLoseTheirTrackingParameters() {
        mapOf(
            "https://m.facebook.com/reel/1000000000000003/?s=tracking" to
                "https://www.facebook.com/reel/1000000000000003/",
            "https://www.facebook.com/reels/1000000000000003" to
                "https://www.facebook.com/reel/1000000000000003/",
            "https://m.facebook.com/watch/?v=1000000000000004&ref=tracking" to
                "https://www.facebook.com/watch/?v=1000000000000004",
            "https://m.facebook.com/video.php?v=1000000000000004" to
                "https://www.facebook.com/watch/?v=1000000000000004",
            "https://www.facebook.com/watch/live/?ref=x&v=1000000000000004" to
                "https://www.facebook.com/watch/?v=1000000000000004",
            "https://m.facebook.com/fixture.page/videos/1000000000000005/?__tn__=x" to
                "https://www.facebook.com/fixture.page/videos/1000000000000005/",
            "https://www.facebook.com/fixture.page/videos/a-title/1000000000000005" to
                "https://www.facebook.com/fixture.page/videos/1000000000000005/",
            "https://www.facebook.com/share/v/AbCd1234/?mibextid=x" to
                "https://www.facebook.com/share/v/AbCd1234/",
            "https://www.facebook.com/share/r/AbCd1234" to
                "https://www.facebook.com/share/r/AbCd1234/",
            // Feed posts name their video only on the post's own page; the adapter reads it.
            "https://m.facebook.com/story.php?story_fbid=pfbid0Fixture2D&id=100000000000002" +
                "&__cft__[0]=x" to
                "https://www.facebook.com/story.php?story_fbid=pfbid0Fixture2D&id=100000000000002",
            "https://m.facebook.com/permalink.php?id=100000000000002&story_fbid=10000000006" to
                "https://www.facebook.com/permalink.php?story_fbid=10000000006&id=100000000000002",
            "https://m.facebook.com/fixture.page/posts/pfbid0Fixture3Ghi?__cft__[0]=x" to
                "https://www.facebook.com/fixture.page/posts/pfbid0Fixture3Ghi",
            "https://www.facebook.com/groups/fixture.group/permalink/1000000000000007/" to
                "https://www.facebook.com/groups/fixture.group/permalink/1000000000000007/",
            "https://www.facebook.com/share/p/AbCd1234/" to
                "https://www.facebook.com/share/p/AbCd1234/",
        ).forEach { (link, canonical) ->
            val found = FocusedVideoProbe.canonicalVideoUrl(link, FeedSite.FACEBOOK)
            assertEquals(link, canonical, found)
        }
        listOf(
            "https://m.facebook.com/",
            "https://m.facebook.com/fixture.page",
            "https://m.facebook.com/fixture.page/photos/1",
            "https://m.facebook.com/watch/",
            "https://m.facebook.com/watch/?v=video",
            "https://m.facebook.com/reel/",
            "https://m.facebook.com/story.php?story_fbid=1000000000000006",
            "https://m.facebook.com/share/x/AbCd1234/",
            "https://m.facebook.com/groups/fixture.group/",
        ).forEach { link ->
            assertNull(link, FocusedVideoProbe.canonicalVideoUrl(link, FeedSite.FACEBOOK))
        }
    }

    @Test
    fun tikTokVideosKeepTheirUserAndId() {
        assertEquals(
            "https://www.tiktok.com/@fixture.user/video/7300000000000000001",
            FocusedVideoProbe.canonicalVideoUrl(
                "https://m.tiktok.com/@fixture.user/video/7300000000000000001?is_from_webapp=1",
                FeedSite.TIKTOK,
            ),
        )
        // P36: a video whose author the page did not show.
        assertEquals(
            "https://www.tiktok.com/@/video/7300000000000000001",
            FocusedVideoProbe.canonicalVideoUrl(
                "https://www.tiktok.com/@/video/7300000000000000001",
                FeedSite.TIKTOK,
            ),
        )
        listOf(
            "https://www.tiktok.com/foryou",
            "https://www.tiktok.com/@fixture.user",
            "https://www.tiktok.com/@fixture.user/photo/7300000000000000001",
            "https://www.tiktok.com/@fixture.user/video/abc",
            "https://www.tiktok.com/video/7300000000000000001",
        ).forEach { link ->
            assertNull(link, FocusedVideoProbe.canonicalVideoUrl(link, FeedSite.TIKTOK))
        }
    }

    @Test
    fun aTikTokFeedCardWithoutAVideoLinkGivesTheVideoOfItsPlayer() {
        // P36 (R19): what the script hands back on the card of today's For You feed: no /video/
        // link, the video id in the player's wrapper id, the author's /@ link in the card.
        val card = Regex("<article[\\s\\S]*?</article>")
            .find(fixture("focused-video/tiktok-foryou-card.html"))!!.value
        assertFalse(card.contains("/video/"))
        val wrapper = Regex("id=\"(xgwrapper-[^\"]+)\"").find(card)!!.groupValues[1]
        val handle = Regex("href=\"(/@[^\"]+)\"").find(card)!!.groupValues[1]

        assertEquals(
            FocusedVideo(
                "https://www.tiktok.com/@fixture.user/video/7300000000000000011",
                Source.PLAYING,
            ),
            FocusedVideoProbe.parse(cardAnswer(wrapper = wrapper, handle = handle), TIKTOK_FEED),
        )
        // Without the author's link the address TikTok answers is /@/video/<id>.
        assertEquals(
            FocusedVideo("https://www.tiktok.com/@/video/7300000000000000011", Source.CENTRE),
            FocusedVideoProbe.parse(
                cardAnswer(wrapper = wrapper, source = "centre"),
                "https://m.tiktok.com/following",
            ),
        )
        // The phone layout keeps the id in the page's slide.
        assertEquals(
            FocusedVideo(
                "https://www.tiktok.com/@phone.user/video/7300000000000000021",
                Source.PLAYING,
            ),
            FocusedVideoProbe.parse(
                cardAnswer(item = "7300000000000000021", handle = "/@phone.user/"),
                TIKTOK_FEED,
            ),
        )
    }

    @Test
    fun onlyAWellFormedTikTokCardOnATikTokPageGivesALink() {
        listOf(
            cardAnswer(wrapper = "xgwrapper-0-1234567"),
            cardAnswer(wrapper = "xgwrapper-0-7300000000000000011x"),
            cardAnswer(wrapper = "player-0-7300000000000000011"),
            cardAnswer(wrapper = "xgwrapper-0-73000000000000000110000"),
            cardAnswer(item = "730000000000"),
            cardAnswer(item = "abc"),
            cardAnswer(),
            JSONObject.quote(
                """{"url":null,"source":"playing","card":"xgwrapper-0-7300000000000000011"}""",
            ),
        ).forEach { answer -> assertNull(answer, FocusedVideoProbe.parse(answer, TIKTOK_FEED)) }
        // Another site's page never takes a TikTok card.
        listOf(YOUTUBE_HOME, "https://m.facebook.com/").forEach { page ->
            assertNull(
                FocusedVideoProbe.parse(
                    cardAnswer(wrapper = "xgwrapper-0-7300000000000000011"),
                    page,
                ),
            )
        }
        // A link the script found is used as it is, never replaced by the card.
        val both = JSONObject()
            .put("url", "https://www.tiktok.com/@fixture.user/photo/7300000000000000011")
            .put("source", "playing")
            .put("card", JSONObject().put("wrapper", "xgwrapper-0-7300000000000000011"))
        assertNull(FocusedVideoProbe.parse(both.toString(), TIKTOK_FEED))
        // A handle in another shape is left out, the video stays.
        listOf("/@fixture.user/video/1", "/@", "/@${"a".repeat(41)}", "/@bad handle").forEach {
            assertEquals(
                it,
                "https://www.tiktok.com/@/video/7300000000000000011",
                FocusedVideoProbe.parse(
                    cardAnswer(wrapper = "xgwrapper-0-7300000000000000011", handle = it),
                    TIKTOK_FEED,
                )?.url,
            )
        }
    }

    @Test
    fun theScriptReadsTikTokCardsOnlyOnTikTok() {
        val script = FocusedVideoProbe.script

        assertTrue(script.contains("const tiktok = site === 'tiktok.com';"))
        assertTrue(script.contains("if (!tiktok) return null;"))
        assertTrue(script.contains("xgwrapper-"))
        assertTrue(script.contains("[data-e2e=\"recommend-list-item-container\"]"))
        assertTrue(script.contains("[data-e2e=\"feed-video\"]"))
        assertTrue(script.contains("[data-e2e^=\"video-slide\"]"))
    }

    @Test
    fun onlyAnHttpsLinkOfThePagesOwnSiteIsAccepted() {
        listOf(
            // Another site's link on a YouTube page, or a look-alike host.
            "https://www.facebook.com/reel/1000000000000003/",
            "https://youtube.com.fixture.test/watch?v=AAAAAAAAAA1",
            "https://notyoutube.com/watch?v=AAAAAAAAAA1",
            "http://m.youtube.com/watch?v=AAAAAAAAAA1",
            "https://user@m.youtube.com/watch?v=AAAAAAAAAA1",
            "javascript:alert(1)",
            "/watch?v=AAAAAAAAAA1",
        ).forEach { link ->
            val answer = JSONObject().put("url", link).put("source", "centre").toString()
            assertNull(link, FocusedVideoProbe.parse(answer, YOUTUBE_HOME))
        }
        // A YouTube link is no Facebook page's video either.
        val youTube = JSONObject()
            .put("url", "https://m.youtube.com/watch?v=AAAAAAAAAA1")
            .put("source", "playing")
            .toString()
        assertNull(FocusedVideoProbe.parse(youTube, "https://m.facebook.com/"))
        assertNull(FocusedVideoProbe.parse(youTube, "https://example.org/"))
        assertNull(FocusedVideoProbe.parse(youTube, null))
    }

    @Test
    fun onlyYouTubeFacebookAndTikTokPagesAreFeeds() {
        listOf(
            YOUTUBE_HOME,
            "https://www.youtube.com/watch?v=AAAAAAAAAA1",
            "https://m.facebook.com/",
            "https://web.facebook.com/watch/",
            "https://www.tiktok.com/foryou",
        ).forEach { page -> assertTrue(page, FocusedVideoProbe.isFeedSite(page)) }
        listOf(
            null,
            "about:blank",
            "http://m.youtube.com/",
            "https://example.org/",
            "https://vimeo.com/",
            "https://youtube.com.fixture.test/",
            "https://fb.watch/AbCd1234/",
        ).forEach { page -> assertFalse(page.toString(), FocusedVideoProbe.isFeedSite(page)) }
    }

    @Test
    fun theFoundLinkIsNeverPrinted() {
        val video = FocusedVideo("https://www.youtube.com/watch?v=AAAAAAAAAA1", Source.PAGE)

        assertFalse(video.toString().contains("youtube"))
    }

    /** The script's answer for a TikTok card, as `evaluateJavascript` hands it back. */
    private fun cardAnswer(
        wrapper: String? = null,
        item: String? = null,
        handle: String? = null,
        source: String = "playing",
    ): String = JSONObject.quote(
        JSONObject()
            .put("url", JSONObject.NULL)
            .put("source", source)
            .put(
                "card",
                JSONObject()
                    .put("wrapper", wrapper ?: JSONObject.NULL)
                    .put("item", item ?: JSONObject.NULL)
                    .put("handle", handle ?: JSONObject.NULL),
            )
            .toString(),
    )

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$path")) { path }
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val YOUTUBE_HOME = "https://m.youtube.com/"
        const val TIKTOK_FEED = "https://www.tiktok.com/foryou"
    }
}
