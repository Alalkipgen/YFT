package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.feature.browser.BrowserViewModel
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (9): the Facebook trial APK (D7 `fb-trial`, `-Pyft.socialTrial` with
 * `-Pyft.socialLadder`, debug only) for S1a's phone proof. A Facebook page main found (a watch
 * link, a reel, a short link) is answered as main's `RESPONSE_CHANGED` after main asked, its rows
 * set aside for that lookup only, so Master's ladder answers; Facebook's line then compares
 * Master's rows with main's (the same video, the duration within 1 s). Main's failures (final ones
 * too), YouTube's and TikTok's pages and every build without the trial: as before. Main's real
 * Facebook adapter over synthetic pages with made-up IDs; each case passes the switches
 * explicitly. No live calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialTrialTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun withoutTheTrialEveryAnswerStaysAsItIs() {
        val found = mainsRows()
        // The ladder alone, the trial without the ladder, Master off, no backup: main's answer itself.
        for (fixture in listOf(
            Fixture(WATCH, socialTrial = false),
            Fixture(WATCH, socialLadder = false),
            Fixture(WATCH, enabledBuild = false),
        )) {
            assertSame(found, fixture.bridge.trialPrimary(WATCH, found))
        }
        assertSame(found, BrowserMasterFallback.None.trialPrimary(WATCH, found))
        assertFalse(SocialLadder.trial) // no unit-test build passes -Pyft.socialTrial
    }

    @Test
    fun theTrialAnswersMainsFoundFacebookPageAsMainsFailureForEveryLinkForm() = runTest {
        for (page in listOf(WATCH, REEL, SHORT)) {
            val fixture = Fixture(page)
            val primary = fixture.inspect()
            val found = primary as SiteAdapterOutcome.Detected

            val trial = fixture.bridge.trialPrimary(page, primary) as SiteAdapterOutcome.Failed

            assertEquals(page, "facebook", trial.adapterId)
            assertEquals(page, SiteExtractionFailure.RESPONSE_CHANGED, trial.reason)
            assertEquals(page, SocialLadder.TRIAL_MESSAGE, trial.message)
            assertEquals(page, "Facebook trial: main's rows were set aside and Master found none. Tap Try again.", trial.message)
            assertFalse(page, trial.allowsGenericFallback)
            assertTrue(page, trial.canRetry)
            assertEquals(page, listOf("master: facebook trial: main's ${found.candidates.size} rows set aside"), trial.details)
            assertEquals(page, 2, found.candidates.size)
            assertBare(page, trial.details)
        }
    }

    @Test
    fun mainsFailuresAndOtherSitesStayAsTheyAre() {
        val fixture = Fixture(WATCH, youtubeRouteB = true)
        val removed = SiteAdapterOutcome.Failed(
            "facebook", SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            "This Facebook post is private or no longer available.", false,
        )
        val changed = SiteAdapterOutcome.Failed("facebook", SiteExtractionFailure.RESPONSE_CHANGED, "Main's words", true)
        for (link in listOf(WATCH, REEL, SHORT)) {
            assertSame(link, removed, fixture.bridge.trialPrimary(link, removed))
            assertSame(link, changed, fixture.bridge.trialPrimary(link, changed))
        }
        assertSame(SiteAdapterOutcome.NotHandled, fixture.bridge.trialPrimary(WATCH, SiteAdapterOutcome.NotHandled))
        // YouTube's and TikTok's pages main found: as is (the Facebook trial claims no other site).
        val youtubeFound = SiteAdapterOutcome.Detected("youtube", listOf(row(YOUTUBE_PAGE, "youtube:Yft0Fixture")))
        val tiktokFound = SiteAdapterOutcome.Detected("tiktok", listOf(row(TIKTOK_PAGE, "tiktok:11111")))
        assertSame(youtubeFound, fixture.bridge.trialPrimary(YOUTUBE_PAGE, youtubeFound))
        assertSame(tiktokFound, fixture.bridge.trialPrimary(TIKTOK_PAGE, tiktokFound))
        // The route-B trial with the Facebook trial: YouTube's answer exactly as routeb's.
        val both = Fixture(WATCH, youtubeRouteB = true, routeBTrial = true)
        val routeB = both.bridge.trialPrimary(YOUTUBE_PAGE, youtubeFound) as SiteAdapterOutcome.Failed
        assertEquals(
            SiteAdapterOutcome.Failed(
                "youtube", SiteExtractionFailure.RESPONSE_CHANGED, AndroidBrowserMasterFallback.ROUTE_B_TRIAL_MESSAGE, false,
                listOf("master: route B trial: main's 1 rows set aside"),
            ),
            routeB,
        )
        assertSame(tiktokFound, both.bridge.trialPrimary(TIKTOK_PAGE, tiktokFound))
    }

    @Test
    fun aRemovedPostStaysMainsFinalAnswer() = runTest {
        val fixture = Fixture(WATCH, mainPage = errorPage(BURMESE))

        val primary = fixture.inspect() as SiteAdapterOutcome.Failed
        val answer = fixture.recover(primary)

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, primary.reason)
        assertSame(primary, answer)
        assertTrue(fixture.master.asked.isEmpty())
    }

    @Test
    fun theLadderAnswersEveryLinkFormAndItsLineComparesTheRowsWithMains() = runTest {
        for (page in listOf(WATCH, REEL, SHORT)) {
            val fixture = Fixture(page)

            val found = fixture.lookup() as SiteAdapterOutcome.Detected

            assertEquals(page, "facebook", found.adapterId)
            assertEquals(page, listOf(hd(VIDEO), sd(VIDEO)), found.candidates.map { it.mediaUrl })
            found.candidates.forEach { assertEquals(page, "facebook:$VIDEO", it.videoId) }
            assertEquals(page, listOf(embedUrl(if (page == REEL) REEL else WATCH)), fixture.master.asked)
            assertEquals(page, 0, fixture.captures.get())
            val line = SocialLadder.trialNotice(found)
            assertEquals(
                page,
                "master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; " +
                    "trial: Master's rows 2, main's 2; the same video: yes; duration within 1 s: yes",
                line,
            )
            assertEquals(page, line, found.details.first { it.startsWith("master: facebook") })
            assertBare(page, found.details)
        }
    }

    @Test
    fun theLineSaysWhenTheDurationsDifferOrAreNotKnown() = runTest {
        val later = Fixture(WATCH, plugin = embed(VIDEO, MAIN_MILLIS + 1_001)).lookup() as SiteAdapterOutcome.Detected
        val within = Fixture(WATCH, plugin = embed(VIDEO, MAIN_MILLIS - 1_000)).lookup() as SiteAdapterOutcome.Detected
        val masterNone = Fixture(WATCH, plugin = embed(VIDEO, null)).lookup() as SiteAdapterOutcome.Detected
        val mainNone = Fixture(WATCH, mainPage = mainPage(VIDEO, null)).lookup() as SiteAdapterOutcome.Detected

        assertTrue(SocialLadder.trialNotice(later)!!.endsWith("; the same video: yes; duration within 1 s: no"))
        assertTrue(SocialLadder.trialNotice(within)!!.endsWith("; the same video: yes; duration within 1 s: yes"))
        assertTrue(SocialLadder.trialNotice(masterNone)!!.endsWith("; the same video: yes; duration within 1 s: unknown"))
        assertTrue(SocialLadder.trialNotice(mainNone)!!.endsWith("; the same video: yes; duration within 1 s: unknown"))
    }

    @Test
    fun theLineSaysWhenMainsRowsNameAnotherVideo() = runTest {
        val fixture = Fixture(WATCH)
        // Main's rows (made up here) for another video than the one Master's rows name.
        val others = SiteAdapterOutcome.Detected(
            "facebook",
            listOf(row(WATCH, "facebook:$OTHER_VIDEO", MAIN_MILLIS), row(WATCH, "facebook:$OTHER_VIDEO", MAIN_MILLIS)),
        )

        val found = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.inspect() // main asks, as in every lookup
            fixture.bridge.recover(WATCH, fixture.bridge.trialPrimary(WATCH, others), NOW)
        } as SiteAdapterOutcome.Detected

        assertEquals(
            "master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; " +
                "trial: Master's rows 2, main's 2; the same video: no; duration within 1 s: yes",
            SocialLadder.trialNotice(found),
        )
    }

    @Test
    fun theComparisonReadsTheVideosAndTheDurations() {
        fun video(id: String?) = id
        val mains = listOf(row(WATCH, "facebook:$VIDEO", 60_000), row(WATCH, "facebook:$VIDEO", 60_400))
        fun segment(rows: List<MediaCandidate>, main: List<MediaCandidate> = mains) =
            SocialLadder.trialSegment(rows, main, ::video)

        assertEquals(
            "; trial: Master's rows 1, main's 2; the same video: yes; duration within 1 s: yes",
            segment(listOf(row(WATCH, "facebook:$VIDEO", 61_000))),
        )
        // Within 1 s of every one of main's; one of main's 1.4 s away: no.
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", 59_300))).endsWith("duration within 1 s: no"))
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", 61_401))).endsWith("duration within 1 s: no"))
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", null))).endsWith("duration within 1 s: unknown"))
        assertTrue(
            segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000)), listOf(row(WATCH, "facebook:$VIDEO", null)))
                .endsWith("duration within 1 s: unknown"),
        )
        // Another video, a row without a video key, main's rows of two videos, no row: not the same.
        assertTrue(segment(listOf(row(WATCH, "facebook:$OTHER_VIDEO", 60_000))).contains("the same video: no"))
        assertTrue(segment(listOf(row(WATCH, null, 60_000))).contains("the same video: no"))
        assertTrue(
            segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000)), mains + row(WATCH, "facebook:$OTHER_VIDEO", 60_000))
                .contains("the same video: no"),
        )
        assertTrue(segment(emptyList()).startsWith("; trial: Master's rows 0, main's 2; the same video: no"))
        // A link main resolves first: main's rows carry the link's key, read as the video it names.
        assertTrue(
            SocialLadder.trialSegment(
                listOf(row(WATCH, "facebook:$VIDEO", 60_000)),
                listOf(row(SHORT, "facebook:$SHORT_CODE", 60_000)),
            ) { id -> if (id == "facebook:$SHORT_CODE") "facebook:$VIDEO" else id }
                .contains("the same video: yes"),
        )
        assertBare("segment", listOf(segment(listOf(row(WATCH, "facebook:$OTHER_VIDEO", 60_000)))))
    }

    @Test
    fun noSetAsideRowsReachALaterLookup() = runTest {
        val fixture = Fixture(WATCH)
        val first = fixture.lookup() as SiteAdapterOutcome.Detected
        assertTrue(SocialLadder.trialNotice(first)!!.contains("; trial: Master's rows 2, main's 2;"))

        // The next lookup: main's own failure, nothing set aside — Master's rows, no comparison.
        fixture.mainPage = PAGE_BODY
        val second = fixture.lookup() as SiteAdapterOutcome.Detected
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), second.candidates.map { it.mediaUrl })
        assertNull(SocialLadder.trialNotice(second))
        assertTrue(second.details.isEmpty())

        // A lookup that set rows aside and never reached Master (cancelled): its rows are not
        // another answer's — neither a recover of another answer's nor the next lookup's.
        fixture.mainPage = mainPage(VIDEO, MAIN_MILLIS)
        val unfinished = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.bridge.trialPrimary(WATCH, fixture.inspect())
        }
        assertTrue(unfinished is SiteAdapterOutcome.Failed)
        val mainsOwn = SiteAdapterOutcome.Failed("facebook", SiteExtractionFailure.RESPONSE_CHANGED, "Main's words", true)
        val other = fixture.recover(mainsOwn) as SiteAdapterOutcome.Detected
        assertNull(SocialLadder.trialNotice(other))
        fixture.mainPage = PAGE_BODY
        val next = fixture.lookup() as SiteAdapterOutcome.Detected
        assertNull(SocialLadder.trialNotice(next))
        // The unfinished lookup's own answer after a later lookup started: nothing left for it either.
        val late = fixture.recover(unfinished) as SiteAdapterOutcome.Detected
        assertNull(SocialLadder.trialNotice(late))
    }

    @Test
    fun whenMasterFindsNoRowsTheTrialsLineStaysFirstInDetails() = runTest {
        // The plugin player names no file and main's page holds none to read again (main's rows
        // made up here): no rung gives rows, and the tab's playback gives none either.
        val fixture = Fixture(WATCH, mainPage = PAGE_BODY, plugin = embed(VIDEO, MASTER_MILLIS, files = false))

        val failed = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.inspect()
            fixture.bridge.recover(WATCH, fixture.bridge.trialPrimary(WATCH, mainsRows()), NOW)
        } as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, failed.reason)
        assertEquals("master: facebook trial: main's 2 rows set aside", failed.details.first())
        assertFalse(failed.allowsGenericFallback)
        assertTrue(failed.canRetry)
        assertEquals(1, fixture.captures.get())
        assertBare("no rows", failed.details)
    }

    @Test
    fun theBrowserNamesFacebooksLineWithTheTrialsComparison() = runTest {
        for (trial in listOf(true, false)) {
            val fixture = Fixture(WATCH, socialTrial = trial)
            val store = DetectedMediaStore()
            val vm = BrowserViewModel(OkHttpClient(), fixture.sites, store, masterFallback = fixture.bridge)

            vm.onPageStarted(WATCH)
            vm.onPageFinished(WATCH, "Fixture")
            settle { fixture.main.asked.isNotEmpty() && !vm.uiState.value.pageLookupRunning }

            val notice = vm.uiState.value.siteNotice
            assertTrue("trial $trial", vm.openPageVideo())
            val rows = store.selection.value!!.candidates.map { it.mediaUrl }
            if (trial) {
                assertEquals(
                    "master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; " +
                        "trial: Master's rows 2, main's 2; the same video: yes; duration within 1 s: yes",
                    notice,
                )
                assertEquals(listOf(hd(VIDEO), sd(VIDEO)).toSet(), rows.toSet())
            } else {
                // The ladder alone: main's rows, no notice, Master never asked.
                assertNull(notice)
                assertEquals(listOf(mainHd(VIDEO), mainSd(VIDEO)).toSet(), rows.toSet())
                assertTrue(fixture.master.asked.isEmpty())
            }
        }
    }

    /** The engine runs on a background thread; the test thread runs the browser's coroutines. */
    private fun TestScope.settle(done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            Thread.sleep(10)
        }
        fail("the lookup did not finish")
    }

    /** S8: no link, host, ID or page words in a line. */
    private fun assertBare(case: String, lines: List<String>) {
        lines.forEach { line ->
            listOf("://", VIDEO, OTHER_VIDEO, SHORT_CODE, BURMESE, "example-cdn").forEach { word ->
                assertFalse("$case: $line", word in line)
            }
        }
    }

    /** Synthetic answers by address (main's pages or the plugin player); anything else a 404. */
    private class Answers(private val answer: (String) -> ExtractorHttpResult?) : ExtractorHttpClient {
        val asked = CopyOnWriteArrayList<String>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            return answer(url) ?: ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
    }

    /** A stand-in adapter for another site's pages (YouTube's, TikTok's); never asked here. */
    private class OtherSite(override val id: String, private val prefix: String) : SiteExtractor {
        override val displayName = "Fixture"

        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl.startsWith(prefix)) SitePageIdentity(id, pageUrl.substringAfterLast('/'), pageUrl) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
    }

    /**
     * One Facebook lookup as the app wires it in the trial APK ([socialTrial] with [socialLadder]):
     * main's real adapter over [mainPage] (the short link resolves to the watch page), Master's
     * ladder over the plugin player [plugin] (null: a 404), capture with nothing to give.
     */
    private class Fixture(
        val page: String,
        socialLadder: Boolean = true,
        socialTrial: Boolean = true,
        enabledBuild: Boolean = true,
        youtubeRouteB: Boolean = false,
        routeBTrial: Boolean = false,
        var mainPage: String = mainPage(VIDEO, MAIN_MILLIS),
        plugin: String? = embed(VIDEO, MASTER_MILLIS),
    ) {
        val main = Answers { url ->
            when (url) {
                WATCH, REEL -> ok(mainPage, url)
                SHORT -> ok(mainPage, WATCH)
                else -> null
            }
        }
        val master = Answers { url ->
            plugin?.takeIf { url == embedUrl(WATCH) || url == embedUrl(REEL) }?.let { ok(it, url) }
        }
        val captures = AtomicInteger()
        val capture = WebViewPlaybackCapture(enabledBuild)
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(
                    FacebookExtractor(SocialLadder.pages(LookupRecorder(main), socialLadder)),
                    OtherSite("youtube", YOUTUBE_PREFIX),
                    OtherSite("tiktok", TIKTOK_PREFIX),
                ),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        private val scope = capture.session.navigate(page)!!
        private val cooldown = SocialCooldown()
        val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                MasterMediaValidator { found, _ -> ValidationResult.Valid(found.copy(contentLengthBytes = 4096)) },
                PlaybackCaptureProvider {
                    captures.incrementAndGet()
                    CaptureResult.Unavailable
                },
                MasterPolicy(enabled = true, youtubeRouteB = youtubeRouteB),
                contracts = SiteContracts(master),
                social = SocialLadder.stage(master, sites::identity, social = socialLadder, cooldown = cooldown),
            ),
            MergeSupport { true },
            requestFactory = { reason, now, expected -> MasterRequest(page, scope.generation, now, reason, expected) },
            youtubeRouteB = youtubeRouteB,
            routeBTrial = routeBTrial,
            socialLadder = socialLadder,
            socialCooldown = cooldown,
            socialTrial = socialTrial,
        )

        /** Main's answer for the page, asked afresh (Try again's way: nothing remembered). */
        suspend fun inspect(): SiteAdapterOutcome =
            sites.inspect(page, BrowserRequestContext(page, "Browser", "c_user=fixture"), NOW, fresh = true)

        /** Master's answer to [primary] in a lookup of its own (main asks first, as in the app). */
        suspend fun recover(primary: SiteAdapterOutcome): SiteAdapterOutcome = withContext(bridge.lookupContext(page)) {
            inspect()
            bridge.recover(page, primary, NOW)
        }

        /** The app's lookup: main's answer, the trial's, then Master's. */
        suspend fun lookup(): SiteAdapterOutcome = withContext(bridge.lookupContext(page)) {
            bridge.recover(page, bridge.trialPrimary(page, inspect()), NOW)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val VIDEO = "1000000000000009"
        const val OTHER_VIDEO = "1000000000000010"
        const val SHORT_CODE = "FixTrial9"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val REEL = "https://www.facebook.com/reel/$VIDEO"
        const val SHORT = "https://fb.watch/$SHORT_CODE"
        const val YOUTUBE_PREFIX = "https://www.youtube.com/watch/"
        const val YOUTUBE_PAGE = "${YOUTUBE_PREFIX}Yft0Fixture"
        const val TIKTOK_PREFIX = "https://www.tiktok.com/@fixture/video/"
        const val TIKTOK_PAGE = "${TIKTOK_PREFIX}11111"
        const val MAIN_MILLIS = 61_500L
        const val MASTER_MILLIS = 61_800L
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"

        fun ok(body: String, url: String) = ExtractorHttpResult.Success(200, body, url, "text/html")

        fun embedUrl(page: String) = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20")

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        fun mainHd(video: String) = "https://video.example-cdn.test/m/$video-hd.mp4?oe=F2A52380&oh=main"

        fun mainSd(video: String) = "https://video.example-cdn.test/m/$video-sd.mp4?oe=F2A52380&oh=main"

        fun row(page: String, videoId: String?, millis: Long? = null) = MediaCandidate(
            page, "https://video.example-cdn.test/r.mp4", setOf(CandidateSource.MANIFEST), MediaKind.DIRECT,
            mimeType = "video/mp4", durationMillis = millis, videoId = videoId,
        )

        /** Main's two rows for the watch page (as main's adapter gives them). */
        fun mainsRows() = SiteAdapterOutcome.Detected(
            "facebook",
            listOf(row(WATCH, "facebook:$VIDEO", MAIN_MILLIS), row(WATCH, "facebook:$VIDEO", MAIN_MILLIS)),
        )

        /** Main's page for [video]: one data script with the video's object, its two files and duration. */
        fun mainPage(video: String, millis: Long?): String {
            val duration = millis?.let { ",\"playable_duration_in_ms\":$it" }.orEmpty()
            val files = "\"browser_native_hd_url\":\"${mainHd(video)}\",\"browser_native_sd_url\":\"${mainSd(video)}\""
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\"$duration,\"videoDeliveryLegacyFields\":{$files}}}]]]}</script>" +
                "</body></html>"
        }

        /** The plugin player's shape (FacebookContractTest's) for [video]: its two files ([files]) and duration. */
        fun embed(video: String, millis: Long?, files: Boolean = true): String {
            val duration = millis?.let { ",\"playable_duration_in_ms\":$it" }.orEmpty()
            val hd = if (files) "\"${hd(video).replace("/", "\\/")}\"" else "null"
            val sd = if (files) "\"${sd(video).replace("/", "\\/")}\"" else "null"
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$video\",\"videoData\":[{\"is_hls\":false," +
                "\"video_id\":\"$video\"$duration,\"hd_src\":$hd,\"sd_src\":$sd,\"dash_manifest\":null," +
                "\"original_height\":720,\"original_width\":1280,\"videoLicenseUriMap\":{}," +
                "\"graphApiVideoLicenseUri\":null,\"OzDrmHelper\":null}]}],1]]});});</script></body></html>"
        }

        /** Facebook's unavailable page: the error route with its root's privacy flag ([title]: any language). */
        fun errorPage(title: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
                "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":" +
                "{\"title\":\"$title\",\"privacy\":true}},\"tracePolicy\":\"comet.test\"," +
                "\"canonicalRouteName\":\"comet.fbweb.CometErrorRoute\"}}}]]]}</script></body></html>"
    }
}
