package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.api.SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
import com.alal.yft.extractor.api.SiteExtractionFailure.RESPONSE_CHANGED
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan 0.3 fix (A1): main reads Facebook's unavailable page by the page's own route, in any
 * language: the error route with its root's `privacy: true`, or the video's not-found route →
 * `PRIVATE_OR_UNAVAILABLE`, only on a page without playable media and after the English markers,
 * on both of the parser's paths. Anything less keeps today's reason; the lookup's flow is
 * unchanged (the public page alone never ends it). Synthetic pages, made-up IDs, no network.
 */
class FacebookPageRouteTest {
    @Test
    fun `the unavailable page's route is final in any language, on both of the parser's paths`() {
        for (title in listOf(ENGLISH, BURMESE)) {
            val error = routePage(ERROR_ROUTE, privacy = true, title = title)
            // Without the route these pages read RESPONSE_CHANGED (no video node) and
            // NO_MEDIA_FOUND (a video node without a playable file).
            assertEquals(title, RESPONSE_CHANGED, reason(wordsPage(title)))
            assertEquals(title, NO_MEDIA_FOUND, reason(wordsPage(title) + unplayableNode(VIDEO)))
            assertEquals(title, PRIVATE_OR_UNAVAILABLE, reason(error))
            assertEquals(title, PRIVATE_OR_UNAVAILABLE, reason(error + unplayableNode(VIDEO)))
            assertEquals("the same route twice is one", PRIVATE_OR_UNAVAILABLE, reason(error + error))
        }
        val removed = routePage(NOT_FOUND_ROUTE, title = BURMESE)
        assertEquals(PRIVATE_OR_UNAVAILABLE, reason(removed))
        assertEquals(PRIVATE_OR_UNAVAILABLE, reason(removed + unplayableNode(VIDEO)))
        // The not-found route needs no flag.
        assertEquals(PRIVATE_OR_UNAVAILABLE, reason(routePage(NOT_FOUND_ROUTE, privacy = false, title = "")))
    }

    @Test
    fun `the English markers come first, and a playable video wins over any route`() {
        val error = routePage(ERROR_ROUTE, privacy = true, title = BURMESE)
        assertEquals(PRIVATE_OR_UNAVAILABLE, reason(wordsPage("This content isn't available right now")))
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, reason(error + wordsPage("You must log in to continue")))
        assertEquals(SiteExtractionFailure.GEO_RESTRICTED, reason(error + wordsPage("not available in your country")))
        for (page in listOf(error, routePage(NOT_FOUND_ROUTE, title = BURMESE))) {
            for (body in listOf(page + mediaPage(VIDEO), mediaPage(VIDEO) + page)) {
                val parsed = FacebookPageParser.parse(body, VIDEO) as FacebookParseResult.Success
                assertEquals(VIDEO, parsed.post.videoId)
                assertEquals(listOf(hd(VIDEO), sd(VIDEO)), parsed.post.renditions.map(FacebookRendition::url))
            }
        }
    }

    @Test
    fun `no flag, another value, a route elsewhere, two routes and renamed keys keep today's reason`() {
        val error = routePage(ERROR_ROUTE, privacy = true, title = BURMESE)
        val cases = mapOf(
            "the words alone" to wordsPage(BURMESE),
            "no flag" to routePage(ERROR_ROUTE, title = BURMESE),
            "the flag false" to routePage(ERROR_ROUTE, privacy = false, title = BURMESE),
            "the flag a string" to error.replace("\"privacy\":true", "\"privacy\":\"true\""),
            "the route's names only in another script" to
                "<html><body><script>{\"modules\":[\"$NOT_FOUND_ROUTE\",\"$ERROR_ROUTE\"]}</script>" +
                "<h1>$BURMESE</h1></body></html>",
            "two routes that disagree" to error + routePage(REELS_ROUTE, title = ""),
            "the not-found route beside another" to routePage(NOT_FOUND_ROUTE, title = BURMESE) + routePage(REELS_ROUTE, title = ""),
            "the route's key renamed" to error.replace("canonicalRouteName", "routeName"),
            "the flag's key renamed" to error.replace("\"privacy\":true", "\"private\":true"),
            "the root renamed" to error.replace("\"rootView\"", "\"view\""),
            "the route renamed" to error.replace("\"route\":{", "\"routes\":{"),
            "the route info renamed" to error.replace("initialRouteInfo", "initialRoute"),
            "another route's name" to error.replace(ERROR_ROUTE, "comet.fbweb.CometErrorRouteV2"),
            "the name in other case" to error.replace(ERROR_ROUTE, ERROR_ROUTE.lowercase()),
            "a broken route info" to "<script>{\"initialRouteInfo\":{\"route\":{\"canonicalRouteName\":</script>",
            // A reel's own route with its query's `video: null` stays recoverable (A2, closed).
            "the reel's route, video null" to routePage(REELS_ROUTE, title = "") +
                "<script type=\"application/json\">{\"data\":{\"video\":null},\"extensions\":{\"is_final\":true}}</script>",
        )
        for ((name, page) in cases) {
            assertEquals(name, RESPONSE_CHANGED, reason(page))
            assertEquals(name, NO_MEDIA_FOUND, reason(page + unplayableNode(VIDEO)))
        }
    }

    @Test
    fun `the route scan reads at most four route infos, each at most 256 KiB`() {
        val error = routePage(ERROR_ROUTE, privacy = true, title = BURMESE)
        fun broken(count: Int) = "<script>{\"x\":[" + List(count) { "{\"initialRouteInfo\":null}" }.joinToString(",") + "]}</script>"
        assertEquals("the fourth is read", PRIVATE_OR_UNAVAILABLE, reason(broken(3) + error))
        assertEquals("the fifth is not", RESPONSE_CHANGED, reason(broken(4) + error))
        // The route info's own object: 256 KiB is read, a character more is not.
        val bare = routeInfo(ERROR_ROUTE, privacy = true, title = "").length
        val within = routePageOf(routeInfo(ERROR_ROUTE, privacy = true, title = "x".repeat(MAX_ROUTE_CHARS - bare)))
        val over = routePageOf(routeInfo(ERROR_ROUTE, privacy = true, title = "x".repeat(MAX_ROUTE_CHARS - bare + 1)))
        assertEquals(PRIVATE_OR_UNAVAILABLE, reason(within))
        assertEquals(RESPONSE_CHANGED, reason(over))
    }

    @Test
    fun `main's lookup ends on the page's route - a watch link and a post, never NO_MEDIA_FOUND`() = runTest {
        val watch = identify(WATCH)
        val http = FakeExtractorHttpClient.serving(watch.canonicalPageUrl, routePage(ERROR_ROUTE, privacy = true, title = BURMESE))
        val failure = FacebookExtractor(http).extract(request(watch)) as SiteExtractionResult.Failure
        assertEquals(PRIVATE_OR_UNAVAILABLE, failure.reason)
        assertFalse(failure.allowsGenericFallback)
        assertEquals(listOf(watch.canonicalPageUrl), http.requestedUrls)

        val post = identify(POST)
        assertTrue(post.requiresCanonicalResolution)
        val pages = listOf(
            routePage(ERROR_ROUTE, privacy = true, title = BURMESE) to PRIVATE_OR_UNAVAILABLE,
            routePage(ERROR_ROUTE, privacy = true, title = ENGLISH) to PRIVATE_OR_UNAVAILABLE,
            routePage(NOT_FOUND_ROUTE, title = BURMESE) to PRIVATE_OR_UNAVAILABLE,
            // Without the flag: today's reason for a post whose page led to no video.
            routePage(ERROR_ROUTE, title = BURMESE) to NO_MEDIA_FOUND,
        )
        for ((page, expected) in pages) {
            val posts = FakeExtractorHttpClient.serving(post.canonicalPageUrl, page)
            val answer = FacebookExtractor(posts).extract(request(post)) as SiteExtractionResult.Failure
            assertEquals(expected, answer.reason)
            assertEquals(listOf(post.canonicalPageUrl), posts.requestedUrls)
        }
    }

    @Test
    fun `a reel - the public page alone never ends the lookup, and a video on either page wins`() = runTest {
        val reel = identify(REEL)
        val error = routePage(ERROR_ROUTE, privacy = true, title = BURMESE)
        // The public page (no session) shows the error route; the session page has the video.
        val sessionVideo = reelPages(reel, public = error, session = mediaPage(REEL_VIDEO))
        val rows = FacebookExtractor(sessionVideo).extract(request(reel)) as SiteExtractionResult.Success
        assertEquals(listOf(hd(REEL_VIDEO), sd(REEL_VIDEO)), rows.candidates.map(MediaCandidate::mediaUrl))
        assertEquals(listOf(false, true), sessionVideo.requestedHeaders.map { "Cookie" in it })

        // The public page has the video (its files only, so the session page is read), the
        // session page the error route: the public page's files.
        val publicVideo = reelPages(reel, public = mediaPage(REEL_VIDEO), session = error)
        val kept = FacebookExtractor(publicVideo).extract(request(reel)) as SiteExtractionResult.Success
        assertEquals(listOf(hd(REEL_VIDEO), sd(REEL_VIDEO)), kept.candidates.map(MediaCandidate::mediaUrl))
        assertEquals(listOf(false, true), publicVideo.requestedHeaders.map { "Cookie" in it })

        // Both pages the error route: the session page's answer, final.
        val both = reelPages(reel, public = error, session = error)
        val failure = FacebookExtractor(both).extract(request(reel)) as SiteExtractionResult.Failure
        assertEquals(PRIVATE_OR_UNAVAILABLE, failure.reason)
        assertEquals(2, both.requestedUrls.size)

        // A reel's own route with `video: null` on both pages: today's reason, recoverable.
        val nullVideo = routePage(REELS_ROUTE, title = "") +
            "<script type=\"application/json\">{\"data\":{\"video\":null},\"extensions\":{\"is_final\":true}}</script>"
        val empty = reelPages(reel, public = nullVideo, session = nullVideo)
        val changed = FacebookExtractor(empty).extract(request(reel)) as SiteExtractionResult.Failure
        assertEquals(RESPONSE_CHANGED, changed.reason)
        assertTrue(changed.allowsGenericFallback)
    }

    private fun reason(html: String): SiteExtractionFailure =
        (FacebookPageParser.parse(html, VIDEO) as FacebookParseResult.Failure).reason

    /** The reel's page: [public] without the session (no cookie), [session] with it. */
    private fun reelPages(reel: SitePageIdentity, public: String, session: String) = FakeExtractorHttpClient(
        getResponder = { url, headers ->
            if (url != reel.canonicalPageUrl) {
                null
            } else {
                FakeExtractorHttpClient.html(if ("Cookie" in headers) session else public, url)
            }
        },
    )

    private fun identify(link: String): SitePageIdentity = checkNotNull(FacebookUrls.identify(link))

    private fun request(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(identity.canonicalPageUrl, "fixture-agent", cookie = "c_user=0; xs=fixture-cookie"),
        nowEpochMs = NOW,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val VIDEO = "1000000000000021"
        const val REEL_VIDEO = "1000000000000022"
        const val POST_ID = "1000000000000023"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val REEL = "https://www.facebook.com/reel/$REEL_VIDEO"
        const val POST = "https://www.facebook.com/fixturepage/posts/$POST_ID"
        const val ERROR_ROUTE = "comet.fbweb.CometErrorRoute"
        const val NOT_FOUND_ROUTE = "comet.fbweb.CometVideoHomeVideoNotFoundRoute"
        const val REELS_ROUTE = "comet.fbweb.CometFBReelsRoute"
        const val MAX_ROUTE_CHARS = 256 * 1024
        const val ENGLISH = "This content is not here for you now"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** The route info's object: the route's name and its root's props ([privacy] when set). */
        fun routeInfo(route: String, privacy: Boolean? = null, title: String): String {
            val props = buildList {
                add("\"title\":\"$title\"")
                privacy?.let { add("\"privacy\":$it") }
            }.joinToString(",")
            return "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":{$props}}," +
                "\"tracePolicy\":\"comet.test\",\"canonicalRouteName\":\"$route\"}}"
        }

        fun routePageOf(info: String): String = "<html><head><title>Facebook</title></head><body>" +
            "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
            "$info}]]]}</script></body></html>"

        /** A page of the route's structure (Master's judge tests'): the route's name, its root's props. */
        fun routePage(route: String, privacy: Boolean? = null, title: String): String =
            routePageOf(routeInfo(route, privacy, title))

        /** A page with only [words], no data. */
        fun wordsPage(words: String) = "<html><head><title>Facebook</title></head><body><h1>$words</h1></body></html>"

        /** The video's node with its two files. */
        fun mediaPage(video: String) = "<script type=\"application/json\">{\"video_id\":\"$video\"," +
            "\"playable_url_quality_hd\":\"${hd(video)}\",\"playable_url\":\"${sd(video)}\"}</script>"

        /** A video node whose only file is not HTTPS: no playable media (the parser's second path). */
        fun unplayableNode(video: String) = "<script type=\"application/json\">{\"video_id\":\"$video\"," +
            "\"playable_url\":\"http://video.example-cdn.test/v/$video-sd.mp4\"}</script>"
    }
}
