package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.recipes.ContractRecipes
import com.alal.yft.extractor.master.recipes.RemoteRecipes
import com.alal.yft.extractor.master.recipes.SignedRecipes
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.io.File
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

    @Test
    fun `S4 - F1 and F2 off in the signed config - their lines, nothing asked, the tab's own playback next`() = runTest {
        val identity = identify(WATCH)
        // F1 off: the plugin player is never asked; F2 still reads main's pages.
        val http = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val record = record()
        val capture = CountingCapture()
        val result = withContext(record + MainPages()) {
            engine(http, capture = capture, contractsOf = SignedRecipes.rungsOff("F1"))
                .extract(contractRequest(WATCH, identity, cookie = COOKIE))
        }
        val details = (result as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), "$F1: off (config); not run" in details)
        assertTrue(details.toString(), details.any { it.startsWith("$F2: ") && "off (config)" !in it })
        assertTrue(http.requestedUrls.isEmpty())
        assertEquals(0, record.masterAsks)
        assertEquals(1, capture.calls)
        assertTrue(details.toString(), CAPTURE in details)
        assertBare(details)

        // F1 and F2 off: both lines, nothing read; the tab's own playback as before.
        val both = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val bothCapture = CountingCapture()
        val bothResult = withContext(record() + MainPages()) {
            engine(both, capture = bothCapture, contractsOf = SignedRecipes.rungsOff("F1", "F2"))
                .extract(contractRequest(WATCH, identity, cookie = COOKIE))
        }
        val bothDetails = (bothResult as MasterResult.NeedsPlayback).details
        assertEquals(
            bothDetails.toString(),
            listOf("$F1: off (config); not run", "$F2: off (config); not run"),
            bothDetails.filter { it.startsWith(F1) || it.startsWith(F2) },
        )
        assertTrue(both.requestedUrls.isEmpty())
        assertEquals(1, bothCapture.calls)
        // F2 off alone: F1's rows answer as before.
        val rows = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val rowsResult = withContext(record() + MainPages()) {
            engine(rows, contractsOf = SignedRecipes.rungsOff("F2")).extract(contractRequest(WATCH, identity, cookie = COOKIE))
        }
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), (rowsResult as MasterResult.Success).result.candidates.map { it.mediaUrl })
        assertFalse(rowsResult.result.details.any { "off (config)" in it })
    }

    @Test
    fun `S4 - F3 off in the signed config - no capture asked, the ladder's lines end the answer`() = runTest {
        val identity = identify(WATCH)
        val http = answering(embedUrl(identity) to embed(VIDEO, files = false), contentType = HTML)
        val capture = CountingCapture()

        val result = withContext(record() + MainPages()) {
            engine(http, capture = capture, contractsOf = SignedRecipes.rungsOff("F3"))
                .extract(contractRequest(WATCH, identity, cookie = COOKIE))
        }

        val details = (result as MasterResult.NeedsPlayback).details
        assertEquals(0, capture.calls)
        assertEquals("master: facebook F3: off (config); not run", details.last())
        assertFalse(details.toString(), CAPTURE in details)
        assertFalse(details.toString(), details.any { it.startsWith("Open the page") })
        assertTrue(details.toString(), details.any { it.startsWith("$F1: NEEDS_PLAYBACK") })
        assertEquals(listOf(embedUrl(identity)), http.requestedUrls)
        assertTrue(RungsOff.captureOff(details))
        assertBare(details)

        // Without the lookup's record (R8's path: no ladder) the config's F3 is never read.
        val plainCapture = CountingCapture()
        val plain = engine(answering(embedUrl(identity) to embed(VIDEO, files = false), contentType = HTML), capture = plainCapture,
            contractsOf = SignedRecipes.rungsOff("F3"))
            .extract(contractRequest(WATCH, identity))
        assertTrue(plain.toString(), plain is MasterResult.NeedsPlayback)
        assertEquals(1, plainCapture.calls)
    }

    @Test
    fun `S4 - the config is asked as the stage starts, on the ladder's client but outside the lookup`() = runTest {
        val identity = identify(WATCH)
        val envelope = SignedRecipes.sign(SignedRecipes.keys, SignedRecipes.payload(rungsOff = listOf("F1")))
        val http = answering(SignedRecipes.CONFIG_URL to envelope, embedUrl(identity) to embed(VIDEO), contentType = HTML)
        val remotes = mutableListOf<RemoteRecipes>()
        val contractsOf = { client: ExtractorHttpClient ->
            val remote = RemoteRecipes(client, SignedRecipes.CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }
            remotes += remote
            SiteContracts(client, remote)
        }
        val record = record()

        val result = withContext(record + MainPages()) {
            engine(http, contractsOf = contractsOf).extract(contractRequest(WATCH, identity, cookie = COOKIE))
        }

        // The config came in this lookup, through the ladder's own client, and turned F1 off at once.
        val details = (result as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), "$F1: off (config); not run" in details)
        assertEquals(listOf(SignedRecipes.CONFIG_URL), http.requestedUrls)
        assertNull(http.requestedHeaders.single()["Cookie"])
        assertTrue(remotes.any { it.version == 1L })
        // Never one of the lookup's asks: the record has none, and F1 asked nothing.
        assertTrue(record.asks().toString(), record.asks().isEmpty())
        assertEquals(0, record.masterAsks)
        assertBare(details)
    }

    @Test
    fun `S4 - the drill's renamed media key - the key table reads none, the open read finds F1's files`() = runTest {
        val identity = identify(WATCH)
        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        suspend fun lookup(config: String): MasterResult.Success {
            val text = File(docs, "configs/$config.json").readText()
            val remote = SignedRecipes.applied(text.trim().removeSuffix("}").trimEnd() + ""","version":1,"expires":"2027-12-31"}""")
            val http = answering(embedUrl(identity) to embed(VIDEO), contentType = HTML)
            return withContext(record() + MainPages()) {
                engine(http, contractsOf = { client -> SiteContracts(client, remote) })
                    .extract(contractRequest(WATCH, identity, cookie = COOKIE))
            } as MasterResult.Success
        }

        val changed = lookup("drill-key-change")
        fun files(result: MasterResult.Success) =
            result.result.details.filter { it.startsWith("master: facebook contract: ") && " files " in it }
        val changedLines = files(changed)
        assertEquals(
            changed.result.details.toString(),
            listOf("master: facebook contract: 0 files from the key table", "master: facebook contract: 2 files by shape (key table changed)"),
            changedLines,
        )
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), changed.result.candidates.map { it.mediaUrl }.toSet())
        assertBare(changed.result.details)

        val defaults = lookup("defaults")
        assertEquals(listOf("master: facebook contract: 2 files from the key table"), files(defaults))
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), defaults.result.candidates.map { it.mediaUrl })
    }

    private fun engine(
        http: FakeExtractorHttpClient,
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
    ) = MasterFallbackEngine(
        validator, capture, MasterPolicy(enabled = true),
        contracts = contractsOf(http),
        social = FacebookLadder(http, ::identifyOrNull, contractsOf),
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
        const val F2 = "master: facebook F2 main's pages"
        const val CAPTURE = "master: one browser-assisted capture requested"
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

        /**
         * The plugin player's shape (FacebookContractTest's, live 2026-10-10 and 2026-10-23: the
         * video's page link under `video_url` and the player's stand-in file under `abortDummy`)
         * for [video].
         */
        fun embed(video: String, files: Boolean = true): String {
            val hd = if (files) "\"${hd(video).replace("/", "\\/")}\"" else "null"
            val sd = if (files) "\"${sd(video).replace("/", "\\/")}\"" else "null"
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$video\",\"video_url\":\"\\/fixture\\/videos\\/$video\\/\"," +
                "\"abortDummy\":\"https:\\/\\/static.example-cdn.test\\/rsrc.php\\/v\\/stand-in.mp4\"," +
                "\"videoData\":[{\"is_hls\":false,\"video_id\":\"$video\"," +
                "\"video_url\":\"https:\\/\\/www.facebook.com\\/fixture\\/videos\\/$video\\/\"," +
                "\"hd_src\":$hd,\"sd_src\":$sd,\"dash_manifest\":null," +
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
