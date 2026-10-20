package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
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
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (4): F2, main's pages. When F1 (the plugin player) leaves nothing to read, the
 * pages main's real Facebook adapter read in the lookup are read again, without a request: rows
 * bound to the video on a page whose keys main no longer knows go to the media checks (the page
 * data); the page's own route on main's page (private or removed, in any language: slot 13) is
 * final when every page main read for the link says the same; DRM is final. Pages that disagree,
 * the plugin player's "needs playback", a page over the bound, another video's page and a
 * related video's files are not final and give no rows. Synthetic pages; no live calls.
 */
class FacebookLadderF2Test {
    @Test
    fun `renamed keys on main's page - F2's rows bound to the video are checked, nothing new asked`() = runTest {
        val share = identify(SHARE)
        val cases = listOf(
            // A watch link: main's page is the video's; a share link: main's page redirected to the reel.
            Case(WATCH, VIDEO, identify(WATCH).canonicalPageUrl, WATCH, identify(WATCH)),
            Case(SHARE, REEL_VIDEO, share.canonicalPageUrl, REEL, identify(REEL)),
        )
        for (case in cases) {
            val main = FakeExtractorHttpClient(
                responses = mapOf(case.mainUrl to ExtractorHttpResult.Success(200, movedKeyPage(case.video), case.finalUrl, HTML)),
            )
            val plugin = answering(embedUrl(case.asked) to NO_DATA, contentType = HTML)
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val record = record()

            val lookup = lookup(case.link, main, plugin, record = record, validator = validator, capture = capture)

            assertEquals("main no longer reads the page", SiteExtractionFailure.RESPONSE_CHANGED, lookup.mainFailure)
            val success = lookup.result as MasterResult.Success
            assertEquals(MasterStage.PAGE_DATA, success.stage)
            val rows = success.result.candidates
            assertEquals(setOf(hd(case.video), sd(case.video)), rows.map { it.mediaUrl }.toSet())
            rows.forEach { assertEquals("facebook:${case.video}", it.videoId) }
            assertEquals(2, validator.seen.size)
            assertEquals(0, capture.calls)
            // Main's one page and F1's one ask; F2 asked nothing.
            assertEquals(listOf(case.mainUrl), main.requestedUrls)
            assertEquals(listOf(embedUrl(case.asked)), plugin.requestedUrls)
            assertEquals(1, record.masterAsks)
            val details = success.result.details
            assertTrue(details.toString(), "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG" in details)
            assertTrue(details.toString(), "$F2: page 1: DIRECT (rows 2, bound to the video ID)" in details)
            assertTrue(details.toString(), "$F2: next: CHECK_ROWS" in details)
            assertBare(details)
        }
    }

    @Test
    fun `the page's own route on main's page is final in any language, never captured`() = runTest {
        val post = identify(POST)
        val share = identify(SHARE)
        val cases = listOf(
            // Main reads these RESPONSE_CHANGED (its markers are English words, not these), a post
            // NO_MEDIA_FOUND (S0d's slot 13); Master reads the page's route.
            Route(WATCH, routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH), SiteExtractionFailure.RESPONSE_CHANGED, "PRIVATE (route: error (privacy))"),
            Route(WATCH, routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE), SiteExtractionFailure.RESPONSE_CHANGED, "PRIVATE (route: error (privacy))"),
            Route(POST, routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE), SiteExtractionFailure.NO_MEDIA_FOUND, "PRIVATE (route: error (privacy))"),
            Route(SHARE, routePage(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = BURMESE), SiteExtractionFailure.RESPONSE_CHANGED, "REMOVED (route: video not found)"),
        )
        for (case in cases) {
            val identity = identify(case.link)
            // The page stays on the link's own address: a post or share link main did not resolve.
            val main = answering(identity.canonicalPageUrl to case.page, contentType = HTML)
            val plugin = answering(embedUrl(identity) to NO_DATA, contentType = HTML)
            val capture = CountingCapture()

            val lookup = lookup(case.link, main, plugin, capture = capture)

            assertEquals(case.link, case.mainFailure, lookup.mainFailure)
            val failure = lookup.result as MasterResult.Failure
            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
            assertEquals(0, capture.calls)
            assertEquals(listOf(identity.canonicalPageUrl), main.requestedUrls)
            val details = failure.details
            assertTrue(details.toString(), "$F2: page 1: ${case.line}" in details)
            assertTrue(details.toString(), "$F2: next: FINAL" in details)
            if (case.link == WATCH) {
                assertEquals(listOf(embedUrl(identity)), plugin.requestedUrls)
            } else {
                // Unresolved (no video in main's answer): judged for the route only, nothing asked.
                assertTrue(plugin.requestedUrls.isEmpty())
                assertTrue(details.toString(), "$LADDER: a link main resolves first, no video in main's answer; not asked" in details)
            }
            assertBare(details)
        }
        assertTrue(post.requiresCanonicalResolution && share.requiresCanonicalResolution)
    }

    @Test
    fun `pages that disagree, the plugin player's needs playback and a page over the bound are not final`() = runTest {
        val reel = identify(REEL)
        val watch = identify(WATCH)
        val private = routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE)
        // A reel: main's public page (no session) shows the route, its session page does not.
        val disagreeing = FakeExtractorHttpClient(
            getResponder = { url, headers ->
                if (url != reel.canonicalPageUrl) {
                    null
                } else {
                    ExtractorHttpResult.Success(200, if ("Cookie" in headers) NO_DATA else private, url, HTML)
                }
            },
        )
        val cases = listOf(
            NotFinal(
                REEL, disagreeing, answering(embedUrl(reel) to NO_DATA, contentType = HTML), MainPages(),
                listOf("$F2: page 1: PRIVATE (route: error (privacy))", "$F2: page 2: SHAPE_CHANGED (no rows; no route)", "$F2: next: PLAYBACK"),
            ),
            NotFinal(
                WATCH, answering(watch.canonicalPageUrl to private, contentType = HTML),
                answering(embedUrl(watch) to embed(VIDEO), contentType = HTML), MainPages(),
                listOf(
                    "$F1: NEEDS_PLAYBACK (the plugin player: needs playback); next: PLAYBACK",
                    "$F2: page 1: PRIVATE (route: error (privacy))",
                    "$F2: the plugin player names the video; next: PLAYBACK",
                ),
            ),
            NotFinal(
                WATCH, answering(watch.canonicalPageUrl to private, contentType = HTML),
                answering(embedUrl(watch) to NO_DATA, contentType = HTML), MainPages(maxChars = 64),
                listOf("$F2: page 1: not kept (over the bound)", "$F2: next: PLAYBACK"),
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val lookup = lookup(case.link, case.main, case.plugin, pages = case.pages, validator = validator, capture = capture)

            // The tab's own playback follows (here it has none): no rows, no final answer.
            val details = (lookup.result as MasterResult.NeedsPlayback).details
            case.lines.forEach { assertTrue(details.toString(), it in details) }
            assertEquals(1, capture.calls)
            assertTrue(validator.seen.isEmpty())
            assertBare(details)
        }
        assertEquals(listOf(reel.canonicalPageUrl, reel.canonicalPageUrl), disagreeing.requestedUrls)
    }

    @Test
    fun `another video's page and a related video's files give no rows`() = runTest {
        val watch = identify(WATCH)
        val cases = listOf(
            // Main's page for the video answered from another video's address: not read.
            ExtractorHttpResult.Success(200, movedKeyPage(OTHER_VIDEO), "https://www.facebook.com/watch/?v=$OTHER_VIDEO", HTML) to
                "$F2: page 1: another video's page; not read",
            // The video's node without files, a related video's files beside it: none is the video's.
            ExtractorHttpResult.Success(200, movedKeyPage(VIDEO, files = false), watch.canonicalPageUrl, HTML) to
                "$F2: page 1: SHAPE_CHANGED (no rows; no route)",
        )
        for ((page, line) in cases) {
            val main = FakeExtractorHttpClient(responses = mapOf(watch.canonicalPageUrl to page))
            val plugin = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val lookup = lookup(WATCH, main, plugin, validator = validator, capture = capture)

            val details = (lookup.result as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), line in details)
            assertTrue(details.toString(), "$F2: next: PLAYBACK" in details)
            assertTrue(validator.seen.isEmpty())
            assertEquals(1, capture.calls)
            assertBare(details)
        }
    }

    @Test
    fun `DRM on main's page is final, and after F1's 429 F2 still reads without a request`() = runTest {
        val watch = identify(WATCH)
        val protectedMain = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO, drm = true), contentType = HTML)
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val drm = lookup(
            WATCH, protectedMain, answering(embedUrl(watch) to NO_DATA, contentType = HTML),
            validator = validator, capture = capture,
        )
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, drm.mainFailure)
        val failure = drm.result as MasterResult.Failure
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, failure.reason)
        assertTrue(failure.details.toString(), "$F2: page 1: DRM (the reader: DRM)" in failure.details)
        assertTrue(failure.details.toString(), "$F2: next: FINAL" in failure.details)
        assertTrue(validator.seen.isEmpty())
        assertEquals(0, capture.calls)
        assertBare(failure.details)

        // F1's 429 stops the site's asks; main's pages are already here, so F2 reads them.
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)
        val limited = FakeExtractorHttpClient(
            responses = mapOf(embedUrl(watch) to ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)),
        )
        val record = record()
        val rowsCapture = CountingCapture()
        val after = lookup(WATCH, main, limited, record = record, capture = rowsCapture)
        val success = after.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), success.result.candidates.map { it.mediaUrl }.toSet())
        assertEquals(listOf(embedUrl(watch)), limited.requestedUrls)
        assertEquals(listOf(watch.canonicalPageUrl), main.requestedUrls)
        assertTrue(record.rateLimited)
        assertEquals(0, rowsCapture.calls)
        val details = success.result.details
        assertTrue(details.toString(), "$F1: RATE_LIMITED (status 429); next: STOP_SITE" in details)
        assertTrue(details.toString(), "$F2: page 1: DIRECT (rows 2, bound to the video ID)" in details)
        assertBare(details)
    }

    @Test
    fun `without main's pages F2 reads nothing, and F1's rows still go first`() = runTest {
        val watch = identify(WATCH)
        // No page main read for the video in this lookup: nothing to read.
        val empty = withContext(record() + MainPages()) {
            engine(answering(embedUrl(watch) to NO_DATA, contentType = HTML)).extract(contractRequest(WATCH, watch))
        }
        val emptyDetails = (empty as MasterResult.NeedsPlayback).details
        assertTrue(emptyDetails.toString(), "$F2: no page main read for the video; nothing to read" in emptyDetails)

        // F1's rows: checked as before; main's pages are not read.
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)
        val rows = lookup(WATCH, main, answering(embedUrl(watch) to embed(VIDEO, files = true), contentType = HTML))
        val success = rows.result as MasterResult.Success
        assertEquals(MasterStage.CONTRACT, success.stage)
        assertFalse(success.result.details.any { it.startsWith(F2) })
    }

    /** One lookup: main's real adapter over [main] (its pages kept in the lookup), then Master's engine. */
    private suspend fun lookup(
        link: String,
        main: FakeExtractorHttpClient,
        plugin: FakeExtractorHttpClient,
        pages: MainPages = MainPages(),
        record: LookupRecord = record(),
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
    ): Lookup {
        val identity = identify(link)
        return withContext(record + pages) {
            val answer = FacebookExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identity, COOKIE))
            val failure = (answer as SiteExtractionResult.Failure).reason
            val result = engine(plugin, validator, capture).extract(contractRequest(link, identity, failure, COOKIE))
            Lookup(failure, result)
        }
    }

    private class Lookup(val mainFailure: SiteExtractionFailure, val result: MasterResult)

    private class Case(
        val link: String,
        val video: String,
        val mainUrl: String,
        val finalUrl: String,
        val asked: SitePageIdentity,
    )

    private class Route(val link: String, val page: String, val mainFailure: SiteExtractionFailure, val line: String)

    private class NotFinal(
        val link: String,
        val main: FakeExtractorHttpClient,
        val plugin: FakeExtractorHttpClient,
        val pages: MainPages,
        val lines: List<String>,
    )

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
            listOf(VIDEO, REEL_VIDEO, OTHER_VIDEO, POST_ID, SHARE_CODE, "facebook.com", "example-cdn", ENGLISH, BURMESE).forEach {
                assertFalse("$line has $it", line.contains(it))
            }
        }
    }

    private companion object {
        const val VIDEO = "1000000000000003"
        const val REEL_VIDEO = "1000000000000004"
        const val OTHER_VIDEO = "1000000000000009"
        const val POST_ID = "1000000000000006"
        const val SHARE_CODE = "FixShare7"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val REEL = "https://www.facebook.com/reel/$REEL_VIDEO"
        const val SHARE = "https://www.facebook.com/share/v/$SHARE_CODE/"
        const val POST = "https://www.facebook.com/fixturepage/posts/$POST_ID"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val HTML = "text/html"
        const val LADDER = "master: facebook ladder"
        const val F1 = "master: facebook F1 plugin player"
        const val F2 = "master: facebook F2 main's pages"
        const val ENGLISH = "This content is not here for you now"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"
        const val NO_DATA = "<html><head><title>Facebook</title></head><body>no data</body></html>"

        fun identifyOrNull(url: String): SitePageIdentity? = FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url))

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /**
         * Main's page with the video's files under keys main's parser does not know (renamed: main
         * reads it RESPONSE_CHANGED), a related video's files beside it; [drm]: a licence for it.
         */
        fun movedKeyPage(video: String, files: Boolean = true, drm: Boolean = false): String {
            val own = buildList {
                add("\"video_id\":\"$video\"")
                if (files) {
                    add("\"browser_native_hd_url_v2\":\"${hd(video)}\"")
                    add("\"browser_native_sd_url_v2\":\"${sd(video)}\"")
                }
                if (drm) add("\"license_url\":\"https://licence.example.test/fixture\"")
            }.joinToString(",")
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{$own},\"related\":[{\"video_id\":\"$OTHER_VIDEO\"," +
                "\"browser_native_hd_url_v2\":\"${hd(OTHER_VIDEO)}\"}]}]]]}</script></body></html>"
        }

        /** The plugin player's shape (FacebookContractTest's) for [video]; without [files]: needs playback. */
        fun embed(video: String, files: Boolean = false): String {
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
