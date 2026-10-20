package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure.GEO_RESTRICTED
import com.alal.yft.extractor.api.SiteExtractionFailure.LOGIN_REQUIRED
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.api.SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.master.lookup.AnswerMark.BOT_CHECK
import com.alal.yft.extractor.master.lookup.AnswerMark.DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.LOGIN_PAGE
import com.alal.yft.extractor.master.lookup.AnswerMark.NOT_JSON
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_POST
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.social.SiteWays.APP_API
import com.alal.yft.extractor.master.social.SiteWays.AVC_LADDER
import com.alal.yft.extractor.master.social.SiteWays.DESKTOP_PAGE
import com.alal.yft.extractor.master.social.SiteWays.DOWNLOAD_SERVICE
import com.alal.yft.extractor.master.social.SiteWays.EMBED_PAGE
import com.alal.yft.extractor.master.social.SiteWays.EMBED_PLAYER
import com.alal.yft.extractor.master.social.SiteWays.FILE_CHECK
import com.alal.yft.extractor.master.social.SiteWays.GRAPHQL
import com.alal.yft.extractor.master.social.SiteWays.HIDDEN_PAGE
import com.alal.yft.extractor.master.social.SiteWays.PAGE
import com.alal.yft.extractor.master.social.SiteWays.PHONE_PAGE
import com.alal.yft.extractor.master.social.SiteWays.PLUGIN_PLAYER
import com.alal.yft.extractor.master.social.SiteWays.PUBLIC_PAGE
import com.alal.yft.extractor.master.social.SiteWays.TWEET_RESULT
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S0a: the census's site table ([SiteWays]): the four sites with main's IDs, their 41
 * slots and what each kind expects, the hosts the one-row check may ask, each site's way labels,
 * the readings of main's committed fixtures and the shape fingerprints. The labels of the asks
 * main's adapters and Master's contracts really send are [SiteWaysDriftTest]'s. No live calls.
 */
class SiteWaysTest {
    private val none = FakeExtractorHttpClient()

    @Test
    fun `the table holds the four sites with main's IDs, names and ways`() {
        val main: List<SiteExtractor> =
            listOf(FacebookExtractor(none), TikTokExtractor(none), InstagramExtractor(none), XExtractor(none))

        assertEquals(main.map { it.id }, SiteWays.ALL.map { it.id })
        assertEquals(main.map { it.displayName }, SiteWays.ALL.map { it.name })
        assertEquals(listOf(PUBLIC_PAGE, PAGE, AVC_LADDER), SiteWays.FACEBOOK.mainWays)
        assertEquals(listOf(PHONE_PAGE, DESKTOP_PAGE, HIDDEN_PAGE, DOWNLOAD_SERVICE), SiteWays.TIKTOK.mainWays)
        assertEquals(listOf(APP_API, GRAPHQL, PAGE, EMBED_PAGE), SiteWays.INSTAGRAM.mainWays)
        assertEquals(listOf(TWEET_RESULT), SiteWays.X.mainWays)
        assertEquals(
            listOf(PLUGIN_PLAYER, EMBED_PLAYER, EMBED_PAGE, TWEET_RESULT),
            SiteWays.ALL.map { it.masterWay },
        )
        SiteWays.ALL.forEach { assertSame(it, SiteWays.site(it.id)) }
        assertNull(SiteWays.site("youtube"))
        assertNull(SiteWays.site("Facebook"))
        assertEquals("Site(facebook, 13 slots)", SiteWays.FACEBOOK.toString())
    }

    @Test
    fun `the census lists 41 slots, each with its kind`() {
        assertEquals(listOf(13, 10, 10, 8), SiteWays.ALL.map { it.slots.size })
        assertEquals(41, SiteWays.ALL.sumOf { it.slots.size })
        assertEquals(
            List(3) { "video" } + List(3) { "reel" } + listOf(
                "watch link", "share link", "post with a video", "group post", "live replay", "sign-in-only",
                "removed",
            ),
            labels(SiteWays.FACEBOOK),
        )
        assertEquals(
            List(4) { "single video" } + listOf(
                "long video", "photo post", "private", "region-limited", "removed", "desktop-only HD",
            ),
            labels(SiteWays.TIKTOK),
        )
        assertEquals(
            List(3) { "single video" } + List(3) { "reel" } +
                listOf("carousel with videos", "photo-only", "sign-in-only", "removed"),
            labels(SiteWays.INSTAGRAM),
        )
        assertEquals(
            List(3) { "video" } + listOf("GIF", "quoted post's video", "several videos", "age-limited", "removed"),
            labels(SiteWays.X),
        )
        // Slots are numbered from 1, as on the screen.
        assertEquals("video", SiteWays.FACEBOOK.kind(1)?.label)
        assertEquals("removed", SiteWays.FACEBOOK.kind(13)?.label)
        assertNull(SiteWays.FACEBOOK.kind(0))
        assertNull(SiteWays.FACEBOOK.kind(14))
        assertNull(SiteWays.X.kind(9))
        assertNull(SiteWays.X.kind(-1))
    }

    @Test
    fun `each kind expects rows or the site's final answer`() {
        // Video kinds expect rows, and no reason is right for them.
        val videoKinds = SiteWays.ALL.flatMap { site -> site.slots.filter { it.expect.rows } }
        assertEquals(30, videoKinds.size)
        assertTrue(videoKinds.all { it.expect.reasons.isEmpty() && it.expect.wantsRows(session = false) })
        assertEquals(listOf(11, 6, 7, 6), SiteWays.ALL.map { site -> site.slots.count { it.expect.wantsRows(false) } })
        assertEquals(listOf(12, 6, 8, 6), SiteWays.ALL.map { site -> site.slots.count { it.expect.wantsRows(true) } })

        for (site in listOf(SiteWays.FACEBOOK, SiteWays.INSTAGRAM)) {
            val signIn = site.slots.single { it.label == "sign-in-only" }.expect
            assertFalse(signIn.rows)
            assertEquals(setOf(LOGIN_REQUIRED), signIn.reasons)
            assertFalse("without the session sign-in is the answer", signIn.wantsRows(session = false))
            assertTrue("with the session it downloads", signIn.wantsRows(session = true))
        }
        val removed = SiteWays.FACEBOOK.kind(13)!!.expect
        assertEquals(setOf(PRIVATE_OR_UNAVAILABLE), removed.reasons)
        assertFalse(removed.wantsRows(session = true))
        assertEquals(removed, SiteWays.INSTAGRAM.kind(10)!!.expect)
        assertEquals(removed, SiteWays.TIKTOK.kind(9)!!.expect)

        assertEquals(setOf(NO_MEDIA_FOUND), SiteWays.TIKTOK.kind(6)!!.expect.reasons)
        assertEquals(setOf(PRIVATE_OR_UNAVAILABLE, LOGIN_REQUIRED), SiteWays.TIKTOK.kind(7)!!.expect.reasons)
        assertEquals(setOf(GEO_RESTRICTED, PRIVATE_OR_UNAVAILABLE), SiteWays.TIKTOK.kind(8)!!.expect.reasons)
        val hd = SiteWays.TIKTOK.kind(10)!!.expect
        assertTrue(hd.rows)
        assertEquals(720, hd.minHeight)
        assertTrue(SiteWays.ALL.flatMap { it.slots }.filter { it !== SiteWays.TIKTOK.kind(10) }.all { it.expect.minHeight == null })
        assertEquals(setOf(NO_MEDIA_FOUND), SiteWays.INSTAGRAM.kind(8)!!.expect.reasons)
        // X's main answers a post its widget does not show with no media (P48).
        assertEquals(setOf(LOGIN_REQUIRED, PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND), SiteWays.X.kind(7)!!.expect.reasons)
        assertEquals(setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND), SiteWays.X.kind(8)!!.expect.reasons)
    }

    @Test
    fun `the one-row check asks only the site's media hosts over HTTPS`() {
        val facebook = SiteWays.FACEBOOK
        assertTrue(facebook.mediaHost("https://video.fxyz1-1.fna.fbcdn.net/v/t42.1790-2/a.mp4?oh=fixture"))
        assertTrue(facebook.mediaHost(" https://VIDEO.FBCDN.NET./v/a.mp4 "))
        assertFalse(facebook.mediaHost("http://video.fbcdn.net/v/a.mp4"))
        assertFalse(facebook.mediaHost("https://user@video.fbcdn.net/v/a.mp4"))
        assertFalse(facebook.mediaHost("https://fbcdn.net.example.test/v/a.mp4"))
        assertFalse(facebook.mediaHost("https://notfbcdn.net/v/a.mp4"))
        assertFalse(facebook.mediaHost("https://www.facebook.com/video.mp4"))
        assertFalse(facebook.mediaHost("not a link"))
        assertFalse(facebook.mediaHost(""))

        // TikTok: main's player-media rule, so a cover or a page of the site is never checked.
        val tiktok = SiteWays.TIKTOK
        assertTrue(tiktok.mediaHost("https://v16m.tiktokcdn.com/fixture/video/tos/useast2a/a.mp4"))
        assertTrue(tiktok.mediaHost("https://www.tiktok.com/aweme/v1/play/?video_id=fixture"))
        assertTrue(tiktok.mediaHost("https://v19.tiktokcdn-us.com/fixture/a?mime_type=video_mp4"))
        assertFalse(tiktok.mediaHost("https://www.tiktok.com/@fixture_user/video/7311234567890123456"))
        assertFalse(tiktok.mediaHost("https://p16-sign.tiktokcdn.com/obj/cover.jpeg"))
        assertFalse("the download service's host is not TikTok's", tiktok.mediaHost("https://www.tikwm.com/video/media/hdplay/1.mp4"))

        val instagram = SiteWays.INSTAGRAM
        assertTrue(instagram.mediaHost("https://scontent-xyz1-1.cdninstagram.com/o1/v/t16/a.mp4"))
        assertTrue(instagram.mediaHost("https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/a.mp4"))
        assertFalse(instagram.mediaHost("https://www.instagram.com/reel/C9fixtREEL1/"))

        val x = SiteWays.X
        assertTrue(x.mediaHost("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/720x1280/a.mp4"))
        assertFalse(x.mediaHost("https://pbs.twimg.com/media/a.jpg"))
        assertFalse(x.mediaHost("https://twimg.com/a.mp4"))
    }

    @Test
    fun `Facebook's labels tell the public page from the AVC ladder by order`() {
        val labels = SiteWays.FACEBOOK.labels()

        assertEquals(PUBLIC_PAGE, labels.way(get(FB_REEL, SAFARI)))
        assertEquals(PUBLIC_PAGE, labels.way(get(FB_REEL, SAFARI, cookie = " ")))
        assertEquals(PAGE, labels.way(get(FB_REEL, BROWSER_AGENT, FB_COOKIE)))
        assertEquals(PAGE, labels.way(get(FB_REEL, SAFARI, FB_COOKIE)))
        assertEquals(AVC_LADDER, labels.way(get("https://www.facebook.com/watch/?v=1234567890123456", SAFARI)))
        assertEquals(PAGE, labels.way(get("https://m.facebook.com/story.php?story_fbid=1&id=2", BROWSER_AGENT)))
        assertEquals(PAGE, labels.way(get("https://fb.watch/fixture/", BROWSER_AGENT)))
        assertEquals(PLUGIN_PLAYER, labels.way(get("https://www.facebook.com/plugins/video.php?href=x", SAFARI)))
        assertEquals(FILE_CHECK, labels.way(probe("https://video.fbcdn.net/v/a.mp4")))
        assertNull(labels.way(get("https://www.example.test/reel/1", BROWSER_AGENT)))
        assertNull(labels.way(get("http://www.facebook.com/reel/1", BROWSER_AGENT)))
        assertNull(labels.way(get("https://user@www.facebook.com/reel/1", BROWSER_AGENT)))
        // Each census row gets its own labels: a new set starts again with the public page.
        val next = SiteWays.FACEBOOK.labels()
        assertNotSame(labels, next)
        assertEquals(PUBLIC_PAGE, next.way(get(FB_REEL, SAFARI)))
        // Only the pages are read.
        val answer = page(Fixtures.read("facebook/watch_progressive.html"), FB_REEL)
        assertEquals(DATA, labels.read(get(FB_REEL, SAFARI), PUBLIC_PAGE, answer)?.mark)
        assertEquals(DATA, labels.read(get(FB_REEL, SAFARI), PLUGIN_PLAYER, answer)?.mark)
        assertNull(labels.read(get(FB_REEL, SAFARI), null, answer))
        assertNull(labels.read(probe(FB_REEL), FILE_CHECK, answer))
    }

    @Test
    fun `TikTok's labels name the service, the embed player and the two pages`() {
        val labels = SiteWays.TIKTOK.labels()

        assertEquals(PHONE_PAGE, labels.way(get(TT_POST, PHONE_AGENT)))
        assertEquals(PHONE_PAGE, labels.way(get(TT_POST, null)))
        assertEquals(PHONE_PAGE, labels.way(get("https://vm.tiktok.com/ZMfixture/", PHONE_AGENT)))
        assertEquals(DESKTOP_PAGE, labels.way(get(TT_POST, DESKTOP_AGENT)))
        assertEquals(EMBED_PLAYER, labels.way(get("https://www.tiktok.com/embed/v2/7311234567890123456", PHONE_AGENT)))
        assertEquals(DOWNLOAD_SERVICE, labels.way(get("https://www.tikwm.com/api/?url=x&hd=1", PHONE_AGENT)))
        assertEquals(FILE_CHECK, labels.way(probe("https://v16m.tiktokcdn.com/fixture/video/tos/a.mp4")))
        assertNull(labels.way(get("https://www.example.test/embed/v2/1", PHONE_AGENT)))
        assertNull(labels.way(get("https://tiktok.com.example.test/@u/video/1", PHONE_AGENT)))
        assertNull(labels.read(get(TT_POST, PHONE_AGENT), FILE_CHECK, page("{}", TT_POST)))
    }

    @Test
    fun `Instagram's and X's labels name their ways by path`() {
        val instagram = SiteWays.INSTAGRAM.labels()
        assertEquals(APP_API, instagram.way(get("https://www.instagram.com/api/v1/media/1/info/", BROWSER_AGENT)))
        assertEquals(GRAPHQL, instagram.way(get("https://www.instagram.com/graphql/query/?doc_id=1", BROWSER_AGENT)))
        assertEquals(EMBED_PAGE, instagram.way(get("https://www.instagram.com/p/C9fixtREEL1/embed/captioned/", null)))
        assertEquals(PAGE, instagram.way(get(IG_REEL, BROWSER_AGENT)))
        assertEquals(FILE_CHECK, instagram.way(probe("https://scontent.cdninstagram.com/v/a.mp4")))
        assertNull(instagram.way(get("https://www.example.test/api/v1/media/1/info/", BROWSER_AGENT)))

        val x = SiteWays.X.labels()
        assertEquals(TWEET_RESULT, x.way(get("https://cdn.syndication.twimg.com/tweet-result?id=1&token=a", null)))
        assertEquals(FILE_CHECK, x.way(probe("https://video.twimg.com/a.mp4")))
        assertNull(x.way(get("https://cdn.syndication.twimg.com/timeline/profile", null)))
        assertNull("only the widget's own host", x.way(get("https://a.cdn.syndication.twimg.com/tweet-result?id=1", null)))
        assertNull(x.way(get("https://x.com/fixture_user/status/1785999999999999999", BROWSER_AGENT)))
    }

    @Test
    fun `Facebook's pages read as main's parser sees them`() {
        val expected = mapOf(
            "content_unavailable" to NO_POST,
            "login_required" to LOGIN_PAGE,
            "geo_restricted" to NO_POST,
            "no_media" to NO_DATA,
            "changed_markup" to NO_DATA,
            "insecure_renditions" to NO_DATA,
            "reel_avc360_av1" to DATA,
            "reel_hd_sd_only" to DATA,
            "reel_inline_dash" to DATA,
            "reel_inline_dash_safari" to DATA,
            "reel_legacy_fields" to DATA,
            "reel_safari_ladder" to DATA,
            "public_reel_empty_licences" to DATA,
            "watch_progressive" to DATA,
            "expired_links" to DATA,
            "drm_video" to DATA,
            "malformed_payload" to DATA,
        )
        assertEquals(fixtures("facebook"), expected.keys)
        for ((name, mark) in expected) {
            val reading = SiteReadings.facebookPage(page(Fixtures.read("facebook/$name.html"), FB_REEL))
            assertEquals(name, mark, reading?.mark)
            assertTrue(name, PAGE_SHAPE.matches(reading?.shape.orEmpty()))
        }
        val video = Fixtures.read("facebook/watch_progressive.html")
        // The login wall by its address, whatever the page holds.
        assertEquals(LOGIN_PAGE, SiteReadings.facebookPage(page(video, "https://www.facebook.com/login/?next=x"))?.mark)
        assertEquals(LOGIN_PAGE, SiteReadings.facebookPage(page(video, "https://m.facebook.com/checkpoint/1/"))?.mark)
        assertEquals(DATA, SiteReadings.facebookPage(page(video, "https://www.example.test/login/"))?.mark)
        // Master's plugin player: its own files (escaped slashes), or an inline manifest.
        assertEquals(DATA, SiteReadings.facebookPage(page(FB_EMBED, FB_REEL))?.mark)
        assertEquals(
            DATA,
            SiteReadings.facebookPage(page("{\"dash_manifest\":\"\\u003CMPD>\"}", FB_REEL))?.mark,
        )
        assertEquals(NO_DATA, SiteReadings.facebookPage(page("<html></html>", FB_REEL))?.mark)
        // Any 2xx answer is read (an answer that is not 2xx is a failure, never read).
        assertEquals(DATA, SiteReadings.facebookPage(page(video, FB_REEL, status = 203))?.mark)
    }

    @Test
    fun `TikTok's pages, embed player and download service read as main sees them`() {
        val expected = mapOf(
            "universal_video" to DATA,
            "sigi_video" to DATA,
            "live_desktop_video_detail" to DATA,
            "live_phone_reflow" to DATA,
            "universal_reflow_video" to DATA,
            "drm_video" to DATA,
            "universal_photo" to NO_DATA,
            "insecure_renditions" to NO_DATA,
            "universal_private" to NO_POST,
            "universal_login_required" to NO_POST,
            "universal_geo_restricted" to NO_POST,
            "malformed_payload" to NO_DATA,
            "changed_markup" to NO_DATA,
        )
        assertEquals(fixtures("tiktok", ".html") - "live_home_page", expected.keys)
        for ((name, mark) in expected) {
            val reading = SiteReadings.tiktokPage(page(Fixtures.read("tiktok/$name.html"), TT_POST))
            assertEquals(name, mark, reading?.mark)
            assertTrue(name, PAGE_SHAPE.matches(reading?.shape.orEmpty()))
        }
        val home = Fixtures.read("tiktok/live_home_page.html")
        assertEquals(NO_POST, SiteReadings.tiktokPage(page(home, "https://www.tiktok.com/"))?.mark)
        assertEquals(NO_POST, SiteReadings.tiktokPage(page(home, "https://www.tiktok.com/foryou?lang=en"))?.mark)
        val video = Fixtures.read("tiktok/universal_video.html")
        assertEquals(LOGIN_PAGE, SiteReadings.tiktokPage(page(video, "https://www.tiktok.com/login?redirect_url=x"))?.mark)
        // A challenge page holds none of TikTok's data scripts; the same words inside them are not one.
        assertEquals(BOT_CHECK, SiteReadings.tiktokPage(page(TT_CHALLENGE, TT_POST))?.mark)
        assertEquals(
            NO_DATA,
            SiteReadings.tiktokPage(
                page("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{\"captcha\":\"verify\"}</script>", TT_POST),
            )?.mark,
        )

        // The embed player: its file, else no data (never a bot check).
        assertEquals(DATA, SiteReadings.tiktokEmbed(page(TT_EMBED, TT_EMBED_URL))?.mark)
        assertEquals(NO_DATA, SiteReadings.tiktokEmbed(page(TT_CHALLENGE, TT_EMBED_URL))?.mark)
        assertEquals(NO_DATA, SiteReadings.tiktokEmbed(page(Fixtures.read("tiktok/universal_photo.html"), TT_EMBED_URL))?.mark)

        // The download service: code 0 with a file; a limit is no data; another code no post.
        val service = SiteReadings.downloadService(json(SERVICE_ANSWER, TT_SERVICE))
        assertEquals(DATA, service?.mark)
        assertTrue(JSON_SHAPE.matches(service?.shape.orEmpty()))
        assertEquals(NO_DATA, SiteReadings.downloadService(json("{\"code\":0,\"data\":{\"play\":\"\"}}", TT_SERVICE))?.mark)
        assertEquals(
            NO_DATA,
            SiteReadings.downloadService(json("{\"code\":-1,\"msg\":\"Free Api Limit: 1 request/second.\"}", TT_SERVICE))?.mark,
        )
        assertEquals(
            NO_POST,
            SiteReadings.downloadService(json("{\"code\":-1,\"msg\":\"Url parsing is failed!\"}", TT_SERVICE))?.mark,
        )
        assertEquals(NOT_JSON, SiteReadings.downloadService(page("<html>busy</html>", TT_SERVICE))?.mark)
    }

    @Test
    fun `Instagram's sources and X's widget read as main sees them`() {
        val graphQl = mapOf(
            "graphql_login" to LOGIN_PAGE,
            "graphql_null" to NO_POST,
            "graphql_photo" to NO_DATA,
            "graphql_reel" to DATA,
            "graphql_carousel" to DATA,
        )
        for ((name, mark) in graphQl) {
            val reading = SiteReadings.instagramJson(json(Fixtures.read("instagram/$name.json"), IG_GRAPHQL), appApi = false)
            assertEquals(name, mark, reading?.mark)
            assertTrue(name, JSON_SHAPE.matches(reading?.shape.orEmpty()))
        }
        val api = Fixtures.read("instagram/api_info_reel.json")
        assertEquals(DATA, SiteReadings.instagramJson(json(api, IG_API), appApi = true)?.mark)
        assertEquals(NO_POST, SiteReadings.instagramJson(json("{\"items\":[]}", IG_API), appApi = true)?.mark)
        assertEquals(
            "GraphQL's answer is not the app API's post",
            NO_POST,
            SiteReadings.instagramJson(json(Fixtures.read("instagram/graphql_reel.json"), IG_API), appApi = true)?.mark,
        )
        val wall = SiteReadings.instagramJson(json(api, "https://www.instagram.com/accounts/login/?next=x"), appApi = true)
        assertEquals(LOGIN_PAGE, wall?.mark)
        assertNull("a wall has no shape", wall?.shape)
        assertEquals(NOT_JSON, SiteReadings.instagramJson(page("<html></html>", IG_GRAPHQL), appApi = false)?.mark)

        val pages = mapOf("embed_captioned" to DATA, "page_signed_in" to DATA, "embed_no_video" to NO_POST)
        for ((name, mark) in pages) {
            assertEquals(name, mark, SiteReadings.instagramPage(page(Fixtures.read("instagram/$name.html"), IG_REEL))?.mark)
        }
        assertEquals(NO_DATA, SiteReadings.instagramPage(page("<html></html>", IG_REEL))?.mark)
        assertEquals(
            LOGIN_PAGE,
            SiteReadings.instagramPage(page(Fixtures.read("instagram/embed_captioned.html"), "https://www.instagram.com/challenge/x/"))?.mark,
        )
        assertEquals(fixtures("instagram"), graphQl.keys + pages.keys + "api_info_reel")

        val tweets = mapOf(
            "tweet_video" to DATA,
            "tweet_gif" to DATA,
            "tweet_hls_only" to DATA,
            "tweet_mixed" to DATA,
            "tweet_quoted" to DATA,
            "tweet_photos" to NO_DATA,
            "tweet_tombstone" to NO_POST,
            // 0.1: the post's own card is read, the quoted post's card is not; like the post's
            // media, a card's file counts as data before main's host guard.
            "tweet_card_video" to DATA,
            "tweet_card_carousel" to DATA,
            "tweet_card_off_host" to DATA,
            "tweet_card_no_video" to NO_DATA,
            "tweet_quoted_card" to NO_DATA,
        )
        assertEquals(fixtures("x"), tweets.keys)
        for ((name, mark) in tweets) {
            val reading = SiteReadings.tweetResult(json(Fixtures.read("x/$name.json"), X_WIDGET))
            assertEquals(name, mark, reading?.mark)
            assertTrue(name, JSON_SHAPE.matches(reading?.shape.orEmpty()))
        }
        assertEquals("an empty answer is no post", NO_POST, SiteReadings.tweetResult(json("{}", X_WIDGET))?.mark)
        assertEquals(NO_DATA, SiteReadings.tweetResult(json("[]", X_WIDGET))?.mark)
        assertEquals(NOT_JSON, SiteReadings.tweetResult(json("{\"a\":1} more", X_WIDGET))?.mark)
        assertEquals(NOT_JSON, SiteReadings.tweetResult(json("<html>", X_WIDGET))?.mark)
    }

    @Test
    fun `JSON too deep or too big is not read`() {
        assertNull(SiteReadings.tweetResult(json("[".repeat(100) + "]".repeat(100), X_WIDGET)))
        val big = "{\"a\":\"" + "x".repeat(SiteReadings.MAX_JSON_CHARS) + "\"}"
        assertNull(SiteReadings.tweetResult(json(big, X_WIDGET)))
        assertEquals("ten deep is read", NO_DATA, SiteReadings.tweetResult(json("[".repeat(10) + "]".repeat(10), X_WIDGET))?.mark)
    }

    @Test
    fun `a JSON shape keeps its structure and changes with a renamed key`() {
        val one = shape("""{"data":{"id":"1","video":{"url":"https://a.example.test/1.mp4"}},"status":"ok"}""")
        assertTrue(JSON_SHAPE.matches(one))
        assertEquals(one, shape("""{"status":"fail","data":{"video":{"url":"https://b.example.test/2.mp4"},"id":"2"}}"""))
        assertNotEquals(one, shape("""{"data":{"id":"1","video":{"src":"https://a.example.test/1.mp4"}},"status":"ok"}"""))
        assertNotEquals(one, shape("""{"data":{"id":"1","video":{"url":"x"}}}"""))
        // A key that is a number (5 digits or more) or very long counts as one key.
        assertEquals(shape("""{"12345":{"a":1}}"""), shape("""{"678901":{"a":2}}"""))
        assertNotEquals(shape("""{"1234":{"a":1}}"""), shape("""{"5678":{"a":1}}"""))
        assertEquals(shape("""{"${"a".repeat(65)}":1}"""), shape("""{"${"b".repeat(70)}":1}"""))
        assertNotEquals(shape("""{"${"a".repeat(64)}":1}"""), shape("""{"${"b".repeat(64)}":1}"""))
        // At most four keys deep; an array by its first item.
        assertEquals(shape("""{"a":{"b":{"c":{"d":{"e":1}}}}}"""), shape("""{"a":{"b":{"c":{"d":{"f":1}}}}}"""))
        assertNotEquals(shape("""{"a":{"b":{"c":{"e":1}}}}"""), shape("""{"a":{"b":{"c":{"f":1}}}}"""))
        assertEquals(shape("""{"x":[{"a":1},{"b":2}]}"""), shape("""{"x":[{"a":3}]}"""))
        assertNotEquals(shape("""{"x":[{"a":1}]}"""), shape("""{"x":[{"b":1}]}"""))
        // At most 400 paths: what lies past them does not count.
        val keys = (0 until 400).joinToString(",") { "\"k%03d\":1".format(it) }
        assertEquals(shape("{$keys,\"z1\":1}"), shape("{$keys,\"z2\":{\"y\":1}}"))
        assertEquals(shape("[1]"), shape("\"text\""))
    }

    @Test
    fun `a page shape keeps its markers whatever their values`() {
        val video = Fixtures.read("facebook/watch_progressive.html")
        val first = SiteReadings.facebookPage(page(video, FB_REEL))?.shape
        assertEquals(first, SiteReadings.facebookPage(page(video.replace("1234567890123456", "6543210987654321"), FB_REEL))?.shape)
        assertNotEquals(first, SiteReadings.facebookPage(page(Fixtures.read("facebook/content_unavailable.html"), FB_REEL))?.shape)
        val markers = listOf("\"a\"", "\"b\"")
        assertEquals(SiteReadings.pageShape("x\"a\":1", markers), SiteReadings.pageShape("\"a\":\"other\" y", markers))
        assertNotEquals(SiteReadings.pageShape("\"a\":1", markers), SiteReadings.pageShape("\"a\":1,\"b\":2", markers))
        assertTrue(PAGE_SHAPE.matches(SiteReadings.pageShape("", markers)))
    }

    private fun labels(site: Site) = site.slots.map { it.label }

    /** The names of main's committed fixtures of [site] ending in [suffix], without it. */
    private fun fixtures(site: String, suffix: String = ""): Set<String> {
        val root = java.io.File(requireNotNull(System.getProperty("yft.siteFixtures")), site)
        return root.list().orEmpty().filter { it.endsWith(suffix) }.map { it.substringBeforeLast('.') }.toSet()
    }

    private fun shape(text: String): String {
        val read = BoundedJsonParser.read(text) as JsonReadResult.Read
        return SiteReadings.jsonShape(read.value)
    }

    companion object {
        const val BROWSER_AGENT = "YFT/fixture"
        const val SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"
        const val PHONE_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0.6668.100 Mobile Safari/537.36"
        const val DESKTOP_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        const val FB_COOKIE = "c_user=0; xs=fixture-cookie"
        const val FB_REEL = "https://www.facebook.com/reel/1603698891196107"
        const val TT_POST = "https://www.tiktok.com/@fixture_user/video/7311234567890123456"
        const val TT_EMBED_URL = "https://www.tiktok.com/embed/v2/7311234567890123456"
        const val TT_SERVICE = "https://www.tikwm.com/api/?url=fixture&hd=1"
        const val IG_REEL = "https://www.instagram.com/reel/C9fixtREEL1/"
        const val IG_API = "https://www.instagram.com/api/v1/media/1/info/"
        const val IG_GRAPHQL = "https://www.instagram.com/graphql/query/?doc_id=1"
        const val X_WIDGET = "https://cdn.syndication.twimg.com/tweet-result?id=1785999999999999999&token=a"

        val PAGE_SHAPE = Regex("h[0-9a-f]{8}")
        val JSON_SHAPE = Regex("j[0-9a-f]{8}")

        /** Master's plugin player's shape (as the R8 contract test's), with fixture addresses. */
        const val FB_EMBED = "<html><body><script>s.handle({\"instances\":[[\"__inst\",[\"VideoConfig\"]," +
            "[{\"video_id\":\"1234567890123456\",\"videoData\":[{\"is_hls\":false," +
            "\"hd_src\":\"https:\\/\\/video.example-cdn.test\\/v\\/embed-hd.mp4?oh=fixture\"," +
            "\"sd_src\":\"https:\\/\\/video.example-cdn.test\\/v\\/embed-sd.mp4?oh=fixture\"}]}],1]]});" +
            "</script></body></html>"

        /** Master's embed player's shape (as the R8 contract test's), with a fixture address. */
        const val TT_EMBED = "<html><body><script id=\"__FRONTITY_CONNECT_STATE__\" type=\"application/json\">" +
            "{\"source\":{\"data\":{\"/embed/v2/7311234567890123456\":{\"videoData\":{\"itemInfos\":" +
            "{\"id\":\"7311234567890123456\",\"video\":{\"urls\":" +
            "[\"https://v45.example-cdn.test/video/tos/useast5/embed.mp4?mime_type=video_mp4\"]}}}}}}}" +
            "</script></body></html>"

        /** A bot check as TikTok shows one (no data scripts). */
        const val TT_CHALLENGE = "<!DOCTYPE html><html><head><title>Security Check</title></head>" +
            "<body><div id=\"captcha-verify-container\">Please wait...</div></body></html>"

        const val SERVICE_ANSWER = "{\"code\":0,\"msg\":\"success\",\"data\":{\"id\":\"7311234567890123456\"," +
            "\"title\":\"Fixture caption\",\"duration\":12," +
            "\"play\":\"https://v16m.example-cdn.test/sd/7311234567890123456.mp4\"," +
            "\"hdplay\":\"https://v16m.example-cdn.test/hd/7311234567890123456.mp4\"," +
            "\"wmplay\":\"https://v16m.example-cdn.test/wm/7311234567890123456.mp4\"," +
            "\"size\":2000000,\"hd_size\":5000000}}"

        fun get(url: String, agent: String?, cookie: String? = null): LookupAsk = LookupAsk(
            LookupAsk.GET,
            url,
            null,
            linkedMapOf<String, String>().apply {
                agent?.let { put("User-Agent", it) }
                put("Accept", "text/html")
                cookie?.let { put("Cookie", it) }
            },
        )

        fun probe(url: String): LookupAsk = LookupAsk(LookupAsk.PROBE, url, null, mapOf("Range" to "bytes=0-0"))

        fun page(body: String, finalUrl: String, status: Int = 200) =
            ExtractorHttpResult.Success(status, body, finalUrl, "text/html; charset=utf-8")

        fun json(body: String, finalUrl: String) = ExtractorHttpResult.Success(200, body, finalUrl, "application/json")
    }
}
