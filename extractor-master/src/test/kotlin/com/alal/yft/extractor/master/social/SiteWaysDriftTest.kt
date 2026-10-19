package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.lookup.AnswerMark.BOT_CHECK
import com.alal.yft.extractor.master.lookup.AnswerMark.DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_POST
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.social.SiteWays.APP_API
import com.alal.yft.extractor.master.social.SiteWays.AVC_LADDER
import com.alal.yft.extractor.master.social.SiteWays.DESKTOP_PAGE
import com.alal.yft.extractor.master.social.SiteWays.DOWNLOAD_SERVICE
import com.alal.yft.extractor.master.social.SiteWays.EMBED_PAGE
import com.alal.yft.extractor.master.social.SiteWays.FILE_CHECK
import com.alal.yft.extractor.master.social.SiteWays.GRAPHQL
import com.alal.yft.extractor.master.social.SiteWays.PAGE
import com.alal.yft.extractor.master.social.SiteWays.PHONE_PAGE
import com.alal.yft.extractor.master.social.SiteWays.PUBLIC_PAGE
import com.alal.yft.extractor.master.social.SiteWays.TWEET_RESULT
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.BROWSER_AGENT
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.FB_COOKIE
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.FB_EMBED
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.FB_REEL
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.IG_REEL
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.SERVICE_ANSWER
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_CHALLENGE
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_EMBED
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_POST
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S0a, drift: the labels name the asks main's four adapters (`extractor-sites`, as the
 * app builds them) and Master's R8 contracts really send, through the recorder the census uses. A
 * change in main's order of ways, its identities or its addresses fails here, not on the phone.
 */
class SiteWaysDriftTest {
    @Test
    fun `main's Facebook reel is its public page, then its page`() = runTest {
        val session = Fixtures.read("facebook/reel_inline_dash_safari.html")
            .replace("7180001112223340", FB_REEL.substringAfterLast('/'))
        val public = Fixtures.read("facebook/public_reel_empty_licences.html")
        // The public page is whatever main asks without the session; the labels must know it by its agent.
        val http = FakeExtractorHttpClient(
            getResponder = { url, headers ->
                val withSession = headers.keys.any { it.equals("Cookie", ignoreCase = true) }
                FakeExtractorHttpClient.html(if (withSession) session else public, url).takeIf { url == FB_REEL }
            },
        )
        val record = LookupRecord(labels = SiteWays.FACEBOOK.labels())
        val extractor = FacebookExtractor(LookupRecorder(http))

        val result = withContext(record) { extractor.extract(request(extractor, FB_REEL, FB_COOKIE)) }

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        val asks = gets(record)
        assertEquals(listOf(PUBLIC_PAGE, PAGE), asks.map { it.way })
        assertEquals(listOf(DATA, DATA), asks.map { it.mark })
        assertEquals("the same page with another identity", listOf(false, true), asks.map { it.repeated })
        assertTrue(asks.all { it.asker == LookupRecord.Asker.MAIN && it.shape != null && !it.afterStop })
    }

    @Test
    fun `main's Facebook story is its page, then the AVC ladder on the video's page`() = runTest {
        val story = "https://m.facebook.com/story.php?story_fbid=9876543210987654&id=100012345678901"
        val http = FakeExtractorHttpClient.serving(
            url = "https://www.facebook.com/100012345678901/posts/9876543210987654",
            body = Fixtures.read("facebook/watch_progressive.html"),
            finalUrl = "https://www.facebook.com/100012345678901/videos/fixture/1234567890123456/",
        )
        val record = LookupRecord(labels = SiteWays.FACEBOOK.labels())
        val extractor = FacebookExtractor(LookupRecorder(http))

        val result = withContext(record) { extractor.extract(request(extractor, story, FB_COOKIE)) }

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        val asks = gets(record)
        assertEquals(listOf(PAGE, AVC_LADDER), asks.map { it.way })
        assertEquals("the ladder's 404 is not read", listOf(DATA, null), asks.map { it.mark })
        assertEquals(listOf(200, 404), asks.map { it.statusCode })
        assertEquals(listOf(false, false), asks.map { it.repeated })
    }

    @Test
    fun `main's TikTok asks its two pages, then the download service, then the files`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                if (url.startsWith(SERVICE_ENDPOINT)) {
                    FakeExtractorHttpClient.json(SERVICE_ANSWER, url)
                } else {
                    FakeExtractorHttpClient.html(Fixtures.read("tiktok/universal_private.html"), url)
                }
            },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 5_000_000L) },
        )
        val record = LookupRecord(labels = SiteWays.TIKTOK.labels())
        val extractor = TikTokExtractor(LookupRecorder(http), askDownloadService = true)

        val result = withContext(record) { extractor.extract(request(extractor, TT_POST, "sessionid=fixture-cookie")) }

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        val asks = gets(record)
        assertEquals(listOf(PHONE_PAGE, DESKTOP_PAGE, DOWNLOAD_SERVICE), asks.map { it.way })
        assertEquals(listOf(NO_POST, NO_POST, DATA), asks.map { it.mark })
        assertEquals(listOf(false, true, false), asks.map { it.repeated })
        val checks = record.asks().filter { it.method == LookupAsk.PROBE }
        assertTrue("the files are checked", checks.isNotEmpty())
        assertTrue(checks.all { it.way == FILE_CHECK && it.mark == null && it.shape == null })
        assertEquals(0, record.botChecks)
        assertEquals(emptyList<String>(), record.stopHosts())
    }

    @Test
    fun `a TikTok bot check stops its host, and main's desktop page after it is counted`() = runTest {
        val http = FakeExtractorHttpClient(getResponder = { url, _ -> FakeExtractorHttpClient.html(TT_CHALLENGE, url) })
        val record = LookupRecord(labels = SiteWays.TIKTOK.labels())
        val extractor = TikTokExtractor(LookupRecorder(http))

        val result = withContext(record) { extractor.extract(request(extractor, TT_POST, null)) }

        assertEquals(SiteExtractionFailure.BOT_CHECK, (result as SiteExtractionResult.Failure).reason)
        val asks = gets(record)
        assertEquals(listOf(PHONE_PAGE, DESKTOP_PAGE), asks.map { it.way })
        assertEquals(listOf(BOT_CHECK, BOT_CHECK), asks.map { it.mark })
        assertEquals(listOf(false, true), asks.map { it.afterStop })
        assertEquals(2, record.botChecks)
        assertEquals(1, record.asksAfterStop)
        assertEquals(listOf("www.tiktok.com"), record.stopHosts())
        assertFalse("a bot check is not a rate limit", record.rateLimited)
    }

    @Test
    fun `main's Instagram with a session asks its four sources in order`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                when {
                    "/api/v1/" in url -> null
                    "/graphql/" in url -> FakeExtractorHttpClient.json(Fixtures.read("instagram/graphql_null.json"), url)
                    "/embed" in url -> FakeExtractorHttpClient.html(Fixtures.read("instagram/embed_captioned.html"), url)
                    else -> FakeExtractorHttpClient.html(Fixtures.read("instagram/embed_no_video.html"), url)
                }
            },
        )
        val record = LookupRecord(labels = SiteWays.INSTAGRAM.labels())
        val extractor = InstagramExtractor(LookupRecorder(http))
        val session = "csrftoken=csrfFixture; sessionid=555%3Asecret; ds_user_id=555"

        val result = withContext(record) { extractor.extract(request(extractor, IG_REEL, session)) }

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        val asks = gets(record)
        assertEquals(listOf(APP_API, GRAPHQL, PAGE, EMBED_PAGE), asks.map { it.way })
        assertEquals("the app API's 404 is not read", listOf(null, NO_POST, NO_POST, DATA), asks.map { it.mark })
        assertEquals(listOf(404, 200, 200, 200), asks.map { it.statusCode })
    }

    @Test
    fun `main's X asks the widget's tweet-result`() = runTest {
        val tweet = Fixtures.read("x/tweet_video.json")
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.json(tweet, url).takeIf { url.startsWith(WIDGET) } },
        )
        val record = LookupRecord(labels = SiteWays.X.labels())
        val extractor = XExtractor(LookupRecorder(http))

        val result = withContext(record) { extractor.extract(request(extractor, X_POST, "auth_token=secret")) }

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        val asks = gets(record)
        assertEquals(listOf(TWEET_RESULT), asks.map { it.way })
        assertEquals(listOf(DATA), asks.map { it.mark })
    }

    @Test
    fun `Master's contract asks, under route B, are each site's Master way`() = runTest {
        val cases = listOf(
            Triple(SiteWays.FACEBOOK, "https://www.facebook.com/watch/?v=1234567890123456", FB_EMBED),
            Triple(SiteWays.TIKTOK, TT_POST, TT_EMBED),
            Triple(SiteWays.INSTAGRAM, IG_REEL, Fixtures.read("instagram/embed_captioned.html")),
            Triple(SiteWays.X, X_POST, Fixtures.read("x/tweet_video.json")),
        )
        for ((site, page, body) in cases) {
            val http = FakeExtractorHttpClient(
                getResponder = { url, _ -> ExtractorHttpResult.Success(200, body, url, "text/html") },
            )
            val identity = checkNotNull(extractorOf(site.id).identify(page)) { page }
            val record = LookupRecord(labels = site.labels())

            withContext(RouteBLookup(record)) { SiteContracts(RouteBHttpClient(http)).ask(contractRequest(page, identity)) }

            val ask = record.asks().single()
            assertEquals(site.id, LookupRecord.Asker.MASTER, ask.asker)
            assertEquals(site.id, site.masterWay, ask.way)
            assertEquals(site.id, DATA, ask.mark)
            assertFalse(site.id, ask.repeated)
        }
    }

    private fun gets(record: LookupRecord) = record.asks().filter { it.method != LookupAsk.PROBE }

    private fun extractorOf(id: String): SiteExtractor {
        val none = FakeExtractorHttpClient()
        return listOf(FacebookExtractor(none), TikTokExtractor(none), InstagramExtractor(none), XExtractor(none))
            .single { it.id == id }
    }

    private fun request(extractor: SiteExtractor, link: String, cookie: String?): SiteExtractionRequest {
        val identity = checkNotNull(extractor.identify(link)) { link }
        return SiteExtractionRequest(
            identity,
            BrowserRequestContext(identity.canonicalPageUrl, BROWSER_AGENT, cookie),
            NOW_MS,
        )
    }

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val SERVICE_ENDPOINT = "https://www.tikwm.com/api/"
        const val WIDGET = "https://cdn.syndication.twimg.com/tweet-result"
        const val X_POST = "https://x.com/fixture_user/status/1785999999999999999"
    }
}
