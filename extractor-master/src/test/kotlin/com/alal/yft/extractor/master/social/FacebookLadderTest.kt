package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (3): Facebook's ladder in Master's engine, F1 judged. Inside a Facebook
 * lookup's own record the plugin player is asked once (route B's rules: no session, nothing new
 * after a 429), read for the video and judged: rows bound to the video go to the media checks; a
 * final answer only on the page's own route (private or removed, in every language) or DRM;
 * "needs playback", a sign-in wall, a 429, no evidence and another video's page leave it to the
 * tab's own playback. A share link is read through main's resolved link. Without the lookup's
 * record R8's path is unchanged. Synthetic pages; no live calls.
 */
class FacebookLadderTest {
    @Test
    fun `F1's rows bound to the video are checked, one Master ask by the plugin player`() = runTest {
        val identity = identify(WATCH)
        val http = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val record = record()

        val result = withContext(record + MainPages()) {
            engine(http, validator, capture).extract(contractRequest(WATCH, identity, cookie = COOKIE))
        }

        val success = result as MasterResult.Success
        assertEquals(MasterStage.CONTRACT, success.stage)
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), success.result.candidates.map { it.mediaUrl })
        success.result.candidates.forEach { assertEquals("facebook:$VIDEO", it.videoId) }
        assertEquals(2, validator.seen.size)
        assertEquals(0, capture.calls)
        assertEquals(listOf(embedUrl(identity)), http.requestedUrls)
        assertNull("never the user's session", http.requestedHeaders.single()["Cookie"])
        assertEquals(1, record.masterAsks)
        assertEquals(listOf(SiteWays.PLUGIN_PLAYER), record.asks().map { it.way })
        val details = success.result.details
        assertTrue(details.toString(), "$LADDER after RESPONSE_CHANGED; replayed 0; new 1" in details)
        assertTrue(details.toString(), "$F1: DIRECT (rows 2, bound to the video ID); next: CHECK_ROWS" in details)
        assertBare(details)
    }

    @Test
    fun `the page's own route is final in English and Burmese, never captured`() = runTest {
        val identity = identify(WATCH)
        val pages = listOf(
            routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH) to
                "$F1: PRIVATE (route: error (privacy)); next: FINAL",
            routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE) to
                "$F1: PRIVATE (route: error (privacy)); next: FINAL",
            routePage(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = BURMESE) to
                "$F1: REMOVED (route: video not found); next: FINAL",
        )
        for ((page, line) in pages) {
            val capture = CountingCapture()
            val result = withContext(record() + MainPages()) {
                engine(answering(embedUrl(identity) to page, contentType = HTML), capture = capture)
                    .extract(contractRequest(WATCH, identity))
            }

            val failure = result as MasterResult.Failure
            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
            assertEquals(0, capture.calls)
            assertTrue(failure.details.toString(), line in failure.details)
            assertBare(failure.details)
        }
    }

    @Test
    fun `needs playback, no evidence and another video's page are not final`() = runTest {
        val identity = identify(WATCH)
        val pages = listOf(
            embed(VIDEO, files = false) to "$F1: NEEDS_PLAYBACK (the plugin player: needs playback); next: PLAYBACK",
            routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = false, title = ENGLISH) to
                "$F1: SHAPE_CHANGED (no rows; route: error (no privacy)); next: NEXT_RUNG",
            routePage(FacebookAnswerJudge.ERROR_ROUTE.replace("Error", "Errors"), privacy = true, title = ENGLISH) to
                "$F1: SHAPE_CHANGED (no rows; route: another); next: NEXT_RUNG",
            "<html><body><h1>$ENGLISH</h1></body></html>" to "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG",
            embed(OTHER_VIDEO) to "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG",
        )
        for ((page, line) in pages) {
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val result = withContext(record() + MainPages()) {
                engine(answering(embedUrl(identity) to page, contentType = HTML), validator, capture)
                    .extract(contractRequest(WATCH, identity))
            }

            // The tab's own playback follows (here it has none): no rows, no final answer.
            assertTrue(line, result is MasterResult.NeedsPlayback)
            assertEquals(line, 1, capture.calls)
            assertTrue(line, validator.seen.isEmpty())
            val details = (result as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), line in details)
            assertBare(details)
        }
    }

    @Test
    fun `a share link is read through main's resolved link, else not asked`() = runTest {
        val share = identify(SHARE)
        val reel = identify(REEL)
        val pages = MainPages()
        pages.note(share.canonicalPageUrl, BROWSER, page(share.canonicalPageUrl)) // the share page itself
        pages.note(share.canonicalPageUrl, BROWSER, page(REEL))
        val http = answering(embedUrl(reel) to embed(REEL_VIDEO), contentType = HTML)
        val capture = CountingCapture()

        val result = withContext(record() + pages) {
            engine(http, capture = capture).extract(contractRequest(SHARE, share))
        }

        val rows = (result as MasterResult.Success).result.candidates
        assertEquals(listOf(hd(REEL_VIDEO), sd(REEL_VIDEO)), rows.map { it.mediaUrl })
        rows.forEach { assertEquals("facebook:$REEL_VIDEO", it.videoId) }
        assertEquals(listOf(embedUrl(reel)), http.requestedUrls)
        assertEquals(0, capture.calls)
        assertBare(result.result.details)

        // Main read no page for it, or its pages name two videos: nothing is asked, no guess.
        val twoVideos = MainPages().apply {
            note(share.canonicalPageUrl, BROWSER, page(REEL))
            note(share.canonicalPageUrl, BROWSER, page(WATCH))
        }
        for (main in listOf(MainPages(), twoVideos)) {
            val unasked = answering(embedUrl(reel) to embed(REEL_VIDEO), contentType = HTML)
            val playback = CountingCapture()
            val answer = withContext(record() + main) {
                engine(unasked, capture = playback).extract(contractRequest(SHARE, share))
            }
            assertTrue(unasked.requestedUrls.isEmpty())
            assertEquals(1, playback.calls)
            val details = (answer as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), "$LADDER: a link main resolves first, no video in main's answer; not asked" in details)
        }
    }

    @Test
    fun `a 429 stops the site, the plugin player's or main's, nothing new goes out`() = runTest {
        val identity = identify(WATCH)
        val limited = FakeExtractorHttpClient(
            responses = mapOf(embedUrl(identity) to ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)),
        )
        val record = record()
        val capture = CountingCapture()
        val lookup = record + MainPages()

        val first = withContext(lookup) { engine(limited, capture = capture).extract(contractRequest(WATCH, identity)) }
        val again = withContext(lookup) { engine(limited, capture = capture).extract(contractRequest(WATCH, identity)) }

        assertEquals(listOf(embedUrl(identity)), limited.requestedUrls)
        assertTrue(record.rateLimited)
        assertEquals(2, capture.calls)
        val firstDetails = (first as MasterResult.NeedsPlayback).details
        assertTrue(firstDetails.toString(), "$F1: RATE_LIMITED (status 429); next: STOP_SITE" in firstDetails)
        val againDetails = (again as MasterResult.NeedsPlayback).details
        assertTrue(againDetails.toString(), "$LADDER after RESPONSE_CHANGED; replayed 0; new 0; refused 1" in againDetails)
        assertTrue(againDetails.toString(), "$F1: RATE_LIMITED (status 429); next: STOP_SITE" in againDetails)

        // Main's 429 earlier in the lookup: the plugin player is never asked.
        val untouched = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val mains = record()
        mains.record(
            LookupAsk(LookupAsk.GET, identity.canonicalPageUrl, null, BROWSER),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            mains.now(),
            LookupRecord.Asker.MAIN,
        )
        val after = withContext(mains + MainPages()) { engine(untouched).extract(contractRequest(WATCH, identity)) }
        assertTrue(untouched.requestedUrls.isEmpty())
        assertEquals(0, mains.masterAsks)
        assertTrue(after.toString(), after is MasterResult.NeedsPlayback)
    }

    @Test
    fun `a sign-in wall goes to the tab's playback, DRM is final`() = runTest {
        val identity = identify(WATCH)
        val wall = FakeExtractorHttpClient(
            responses = mapOf(
                embedUrl(identity) to ExtractorHttpResult.Success(200, "<html>sign in</html>", LOGIN, HTML),
            ),
        )
        val capture = CountingCapture()
        val walled = withContext(record() + MainPages()) {
            engine(wall, capture = capture).extract(contractRequest(WATCH, identity))
        }
        assertEquals(1, capture.calls)
        val details = (walled as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), "$F1: LOGIN_WALL (final address: login); next: BROWSER" in details)

        val drm = embed(VIDEO).replace(
            "\"videoLicenseUriMap\":{}",
            "\"videoLicenseUriMap\":{\"widevine\":\"https://licence.example.test\"}",
        )
        val protectedCapture = CountingCapture()
        val result = withContext(record() + MainPages()) {
            engine(answering(embedUrl(identity) to drm, contentType = HTML), capture = protectedCapture)
                .extract(contractRequest(WATCH, identity))
        }
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, (result as MasterResult.Failure).reason)
        assertEquals(0, protectedCapture.calls)
        assertTrue(result.details.toString(), "$F1: DRM (the reader: DRM); next: FINAL" in result.details)
    }

    @Test
    fun `without the lookup's record or after a sign-in failure the ladder asks nothing`() = runTest {
        val identity = identify(WATCH)
        // No record (every build but the social-ladder one): R8's ask, exactly as without a ladder.
        val r8Http = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val r8 = MasterFallbackEngine(
            RecordingValidator(), CountingCapture(), MasterPolicy(enabled = true), contracts = SiteContracts(r8Http),
        ).extract(contractRequest(WATCH, identity)) as MasterResult.Success
        val http = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val plain = engine(http).extract(contractRequest(WATCH, identity)) as MasterResult.Success
        assertEquals(r8.result.candidates, plain.result.candidates)
        assertEquals(r8.result.details, plain.result.details)
        assertEquals(r8Http.requestedUrls, http.requestedUrls)
        assertFalse(plain.result.details.any { it.startsWith(LADDER) })

        // Main's sign-in failure: the user's own playback, never an ask (as R8).
        val signIn = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val record = record()
        val capture = CountingCapture()
        val result = withContext(record + MainPages()) {
            engine(signIn, capture = capture)
                .extract(contractRequest(WATCH, identity, SiteExtractionFailure.LOGIN_REQUIRED))
        }
        assertTrue(signIn.requestedUrls.isEmpty())
        assertEquals(0, record.masterAsks)
        assertEquals(1, capture.calls)
        assertTrue(result.toString(), result is MasterResult.NeedsPlayback)
    }

    private fun engine(
        http: FakeExtractorHttpClient,
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
    ) = MasterFallbackEngine(
        validator, capture, MasterPolicy(enabled = true),
        contracts = SiteContracts(http),
        social = FacebookLadder(http, ::identifyOrNull),
    )

    private fun record() = LookupRecord(labels = SiteWays.FACEBOOK.labels())

    /** No ID, link or page words in the lines. */
    private fun assertBare(details: List<String>) {
        details.forEach { line ->
            listOf(VIDEO, REEL_VIDEO, SHARE_CODE, "facebook.com", "example-cdn", ENGLISH, BURMESE).forEach {
                assertFalse("$line has $it", line.contains(it))
            }
        }
    }

    private companion object {
        const val VIDEO = "1000000000000003"
        const val REEL_VIDEO = "1000000000000004"
        const val OTHER_VIDEO = "1000000000000009"
        const val SHARE_CODE = "FixShare7"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val REEL = "https://www.facebook.com/reel/$REEL_VIDEO"
        const val SHARE = "https://www.facebook.com/share/v/$SHARE_CODE/"
        const val LOGIN = "https://www.facebook.com/login/?next=fixture"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val HTML = "text/html"
        const val LADDER = "master: facebook ladder"
        const val F1 = "master: facebook F1 plugin player"
        const val ENGLISH = "This content is not here for you now"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"
        val BROWSER = mapOf("User-Agent" to "Browser", "Cookie" to "c_user=fixture")

        fun identifyOrNull(url: String): SitePageIdentity? = FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url))

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** Main's page answer for a link, its final address [finalUrl]. */
        fun page(finalUrl: String) = ExtractorHttpResult.Success(200, "<html>main's page</html>", finalUrl, HTML)

        /** The plugin player's shape (FacebookContractTest's, live 2026-10-10) for [video]. */
        fun embed(video: String, files: Boolean = true): String {
            val hd = if (files) "\"${hd(video).replace("/", "\\/")}\"" else "null"
            val sd = if (files) "\"${sd(video).replace("/", "\\/")}\"" else "null"
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$video\",\"videoData\":[{\"is_hls\":false," +
                "\"video_id\":\"$video\",\"hd_src\":$hd,\"sd_src\":$sd,\"dash_manifest\":null," +
                "\"original_height\":720,\"original_width\":1280,\"videoLicenseUriMap\":{}," +
                "\"graphApiVideoLicenseUri\":null,\"OzDrmHelper\":null}]}],1]]});});</script></body></html>"
        }

        /** A page of the route's structure (SiteAnswerJudgeTest's): the route's name, its root's props. */
        fun routePage(route: String, privacy: Boolean? = null, title: String): String {
            val props = buildList {
                add("\"title\":\"$title\"")
                privacy?.let { add("\"privacy\":$it") }
            }.joinToString(",")
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
                "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":{$props}}," +
                "\"tracePolicy\":\"comet.test\",\"canonicalRouteName\":\"$route\"}}}]]]}</script></body></html>"
        }
    }
}
