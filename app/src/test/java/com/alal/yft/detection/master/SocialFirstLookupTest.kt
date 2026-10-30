package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterCaptureSelection
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import com.alal.yft.feature.browser.BrowserViewModel
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan task 12 (the phone session of 2026-10-22, observations a, b and g). In the trial APK
 * (D7 `social-trial`) a social page's first lookup in the browser could find main's answer
 * remembered (P17's shared lookup: an earlier lookup of the same video, Home's or an earlier
 * page's), so main asked nothing in that lookup and the ladder had none of main's answers to read
 * (Instagram's I2, X's X1: "none kept for the post"); Try again asked main afresh and gave rows.
 * Now the trial's social lookups ask main every time (Try again's way), so the ladder reads main's
 * answer on the first lookup too; and the tab's playback's answer keeps the ladder's lines in
 * Details. Every other build (the ladder alone, the trial without the ladder, Master off): P17 as
 * before. Main's real adapters over synthetic answers with made-up IDs; no live calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialFirstLookupTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun onlyTheTrialsSocialLookupsAskMainEveryTime() {
        val pages = listOf(X_POST, REEL, FACEBOOK_WATCH, TIKTOK_VIDEO)
        val trial = Fixture(X_POST)
        pages.forEach { assertTrue(it, trial.bridge.asksMainFresh(it)) }
        assertFalse(trial.bridge.asksMainFresh("https://example.test/watch/fixture"))
        for (other in listOf(
            Fixture(X_POST, socialTrial = false),
            Fixture(X_POST, socialLadder = false),
            Fixture(X_POST, enabledBuild = false),
        )) {
            pages.forEach { assertFalse(it, other.bridge.asksMainFresh(it)) }
        }
        assertFalse(BrowserMasterFallback.None.asksMainFresh(X_POST))
    }

    @Test
    fun theTrialsFirstLookupAfterARememberedAnswerReadsMainsAnswer() = runTest {
        for ((page, file) in listOf(X_POST to X_FILE, REEL to INSTAGRAM_FILE)) {
            val fixture = Fixture(page)
            // An earlier lookup of the same video (Home's, or an earlier page's): main's rows, remembered.
            assertTrue(page, fixture.remember() is SiteAdapterOutcome.Detected)
            val store = DetectedMediaStore()
            val vm = BrowserViewModel(OkHttpClient(), fixture.sites, store, masterFallback = fixture.bridge)

            vm.onPageStarted(page)
            vm.onPageFinished(page, "Fixture")
            assertTrue(page, vm.uiState.value.pageLookupRunning)
            settle { !vm.uiState.value.pageLookupRunning }

            // Main asked again in the page's first lookup, so the ladder read main's answer.
            assertEquals(page, 2, fixture.main.asked.size)
            val notice = vm.uiState.value.siteNotice.orEmpty()
            assertTrue(notice, notice.startsWith("master: ${site(page)} ladder after RESPONSE_CHANGED"))
            assertTrue(notice, "; trial: Master's rows 1, main's 1; the same item: yes" in notice)
            assertTrue(page, vm.openPageVideo())
            assertEquals(page, listOf(file), store.selection.value!!.candidates.map { it.mediaUrl })
            assertEquals(page, listOf(file), fixture.reads.toList()) // S6 read Master's row
            assertEquals(page, 0, fixture.captures.get())
        }
    }

    @Test
    fun withoutTheTrialARememberedAnswerStaysMainsRowsAsBefore() = runTest {
        for (fixture in listOf(
            Fixture(X_POST, socialTrial = false),
            Fixture(X_POST, socialLadder = false),
            Fixture(X_POST, enabledBuild = false),
        )) {
            assertTrue(fixture.remember() is SiteAdapterOutcome.Detected)
            val store = DetectedMediaStore()
            val vm = BrowserViewModel(OkHttpClient(), fixture.sites, store, masterFallback = fixture.bridge)

            vm.onPageStarted(X_POST)
            vm.onPageFinished(X_POST, "Fixture")
            assertTrue(vm.uiState.value.pageLookupRunning)
            settle { !vm.uiState.value.pageLookupRunning }

            // P17: the remembered answer, main not asked again; Master never asked.
            assertEquals(1, fixture.main.asked.size)
            assertNull(vm.uiState.value.siteNotice)
            assertTrue(vm.openPageVideo())
            assertEquals(listOf(X_FILE), store.selection.value!!.candidates.map { it.mediaUrl })
            assertTrue(fixture.reads.isEmpty())
            assertEquals(0, fixture.captures.get())
        }
    }

    @Test
    fun theTabsPlaybackAnswerKeepsTheLaddersLinesInDetails() = runTest {
        // Main's widget answer is a 404 (X1: not proof of removal, the tab's playback next); the
        // tab plays and the app's selection asks for a fresh playback (its own line).
        for (social in listOf(true, false)) {
            val fixture = Fixture(
                X_POST,
                socialLadder = social,
                socialTrial = false,
                widget = null,
                captured = true,
                selection = { _, _, _, _ -> MasterResult.NeedsPlayback(listOf(SELECTION)) },
            )
            val failed = fixture.lookup() as SiteAdapterOutcome.Failed

            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failed.reason)
            assertTrue(failed.canRetry)
            assertEquals(SELECTION, failed.details.last())
            assertEquals(1, fixture.captures.get())
            val ladder = failed.details.filter { it.startsWith("master: x") }
            if (social) {
                // X's lines and the capture's request stay before the selection's own line.
                assertTrue(failed.details.toString(), ladder.any { it.startsWith(NOT_FOUND_LINE) })
                assertTrue(failed.details.toString(), CAPTURE in failed.details)
                assertTrue(failed.details.indexOf(CAPTURE) < failed.details.indexOf(SELECTION))
            } else {
                // No ladder: the selection's own line alone after main's, as before.
                assertTrue(failed.details.toString(), ladder.isEmpty())
                assertFalse(failed.details.toString(), CAPTURE in failed.details)
            }
        }
    }

    @Test
    fun aLaddersPhotosOnlyAnswerIsFinalWithoutTryAgain() = runTest {
        // Social plan task 15 (F3b, the phone session of 2026-10-23: Instagram slot 8, TikTok slot
        // 6): main's photos-only answer, the ladder's PHOTO_ONLY line ending in FINAL, no request:
        // main's words and no Try again. Without the ladder (every other build): Try again as before.
        for (social in listOf(true, false)) {
            val fixture = Fixture(REEL, socialLadder = social, socialTrial = social, graphQl = photoGraphQl())
            val failed = fixture.lookup() as SiteAdapterOutcome.Failed

            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failed.reason)
            if (social) {
                assertEquals(InstagramExtractor.PHOTOS_MESSAGE, failed.message)
                assertTrue(failed.details.toString(), failed.details.any { "PHOTO_ONLY" in it && "next: FINAL" in it })
                assertTrue(failed.final)
                assertFalse(failed.canRetry)
                assertTrue(fixture.master.asked.isEmpty())
                assertEquals(0, fixture.captures.get())
            } else {
                assertFalse(failed.final)
                assertTrue(failed.canRetry)
            }
        }
        // Main's own answer (Home's lookup, or no Master): not final, Try again as before.
        val mains = Fixture(REEL, graphQl = photoGraphQl()).remember() as SiteAdapterOutcome.Failed
        assertEquals(InstagramExtractor.PHOTOS_MESSAGE, mains.message)
        assertFalse(mains.final)
        assertTrue(mains.canRetry)
    }

    @Test
    fun aPrivatePostsCodeLeftUnreadKeepsThePrivateWordsAfterTheLadder() = runTest {
        // v1.0.2 item 70b: main ends unread (GraphQL: no post; the pages 404) on a private post's
        // code (longer than 11 characters). With the ladder (the release), no video in the tab:
        // main's words, now the private ones, the reason main's, Try again; an 11-character code:
        // today's words.
        val private = Fixture(PRIVATE_REEL, socialTrial = false, graphQl = NO_POST).lookup()
            as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, private.reason)
        assertEquals(private.details.toString(), InstagramExtractor.PRIVATE_MESSAGE, private.message)
        assertTrue(private.canRetry)
        assertTrue(private.details.toString(), InstagramExtractor.PRIVATE_CODE_LINE in private.details)

        val public = Fixture(REEL, socialTrial = false, graphQl = NO_POST).lookup() as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, public.reason)
        assertEquals(
            public.details.toString(),
            "Instagram's page could not be read. Tap Details to see why, or Try again.",
            public.message,
        )
        assertTrue(public.canRetry)

        // v1.0.2 fix F2 (phone test 70b): GraphQL's login answer, the wall Instagram shows for a
        // private post: on a private post's code the private words all the same, Try again; an
        // 11-character code keeps the wall's words.
        val walled = Fixture(PRIVATE_REEL, socialTrial = false, graphQl = LOGIN).lookup()
            as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, walled.reason)
        assertEquals(walled.details.toString(), InstagramExtractor.PRIVATE_MESSAGE, walled.message)
        assertTrue(walled.canRetry)
        assertTrue(walled.details.toString(), InstagramExtractor.PRIVATE_CODE_LINE in walled.details)
        val wall = Fixture(REEL, socialTrial = false, graphQl = LOGIN).lookup() as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, wall.reason)
        assertEquals(wall.details.toString(), InstagramExtractor.LOGIN_MESSAGE, wall.message)

        // Master without the ladder: its playback words for both codes, as before.
        for (page in listOf(PRIVATE_REEL, REEL)) {
            val failed = Fixture(page, socialLadder = false, socialTrial = false, graphQl = NO_POST).lookup()
                as SiteAdapterOutcome.Failed
            assertEquals(page, "Play the video on this page, then tap Try again.", failed.message)
            assertTrue(failed.canRetry)
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

    /** Synthetic answers by address; anything else a 404. */
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
        ): ExtractorHttpResult {
            asked += url
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
    }

    /**
     * One social page's lookups as the app wires them in the trial APK ([socialTrial] with
     * [socialLadder]): main's real X and Instagram adapters over [widget] (X's widget answer; null:
     * a 404) and [graphQl] (Instagram's GraphQL answer), Master's ladders over a client never
     * asked, S6's row check reading each file's start (made in code) on the site's media host, and
     * the tab's playback: nothing ([captured] false), or the page playing with the app's
     * [selection] answering.
     */
    private class Fixture(
        val page: String,
        socialLadder: Boolean = true,
        socialTrial: Boolean = true,
        enabledBuild: Boolean = true,
        widget: String? = widget(),
        graphQl: String = graphQl(),
        captured: Boolean = false,
        selection: MasterCaptureSelection? = null,
    ) {
        val main = Answers { url ->
            when {
                url.startsWith("https://cdn.syndication.twimg.com/tweet-result?") && "id=$X_POST_ID" in url ->
                    widget?.let { ExtractorHttpResult.Success(200, it, url, "application/json") }
                url.startsWith("https://www.instagram.com/graphql/query/") ->
                    ExtractorHttpResult.Success(200, graphQl, url, "application/json")
                else -> null
            }
        }
        val master = Answers { null }
        val reads = CopyOnWriteArrayList<String>()
        private val files = MediaStartReader { url, _, maxBytes ->
            reads += url
            val millis = when {
                url.startsWith("https://video.twimg.com/") -> X_MILLIS
                url.startsWith("https://scontent.cdninstagram.com/") -> INSTAGRAM_MILLIS
                else -> return@MediaStartReader MediaStart.Failed(404)
            }
            val bytes = mp4Start(millis).copyOf(maxBytes)
            MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$FILE_BYTES", bytes.size.toLong(), bytes)
        }
        val captures = AtomicInteger()
        val capture = WebViewPlaybackCapture(enabledBuild)
        private val scope = capture.session.navigate(page)!!
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(
                    XExtractor(SocialLadder.pages(LookupRecorder(main), socialLadder)),
                    InstagramExtractor(SocialLadder.pages(LookupRecorder(main), socialLadder)),
                    FacebookExtractor(main),
                    TikTokExtractor(main),
                ),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        private val cooldown = SocialCooldown()
        val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                SocialLadder.validator(
                    MasterMediaValidator { found, _ -> ValidationResult.Valid(found.copy(contentLengthBytes = 4096)) },
                    socialLadder,
                ) { files },
                PlaybackCaptureProvider {
                    captures.incrementAndGet()
                    if (captured) CaptureResult.Available(PageSnapshot(page, scope.generation)) else CaptureResult.Unavailable
                },
                MasterPolicy(enabled = true),
                captureSelection = selection,
                contracts = SiteContracts(master),
                social = SocialLadder.stage(master, sites::identity, social = socialLadder, cooldown = cooldown),
            ),
            MergeSupport { true },
            requestFactory = { reason, now, expected ->
                capture.session.currentScope()?.let { MasterRequest(page, it.generation, now, reason, expected) }
            },
            socialLadder = socialLadder,
            socialCooldown = cooldown,
            socialTrial = socialTrial,
        )

        /** An earlier lookup of the page's video outside the browser's page (Home's way): P17 keeps main's rows. */
        suspend fun remember(): SiteAdapterOutcome = sites.inspect(page, BrowserRequestContext(page, "Browser", null), NOW)

        /** The app's lookup in its own record: main's answer (asked afresh), the trial's, then Master's. */
        suspend fun lookup(): SiteAdapterOutcome = withContext(bridge.lookupContext(page)) {
            val primary = sites.inspect(page, BrowserRequestContext(page, "Browser", null), NOW, fresh = true)
            bridge.recover(page, bridge.trialPrimary(page, primary), NOW)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val X_POST_ID = "1000000000000000041"
        const val X_ITEM = "1000000000000000042"
        const val X_POST = "https://x.com/fixture_user/status/$X_POST_ID"
        const val X_FILE = "https://video.twimg.com/ext_tw_video/$X_ITEM/pu/vid/avc1/720x1280/a.mp4?tag=12"
        const val X_MILLIS = 9_000L

        /** Instagram's made-up reel (the code and media ID of the sites' fixtures). */
        const val REEL = "https://www.instagram.com/reel/C9fixtREEL1/"

        /** v1.0.2 item 70b: a made-up private post's code (longer than 11 characters). */
        const val PRIVATE_REEL = "https://www.instagram.com/reel/C9fixtREEL1AbCdEfGhIjKlMnOpQrStUv/"

        /** Instagram's GraphQL answer for a post it does not show. */
        const val NO_POST = "{\"data\":{\"xdt_shortcode_media\":null},\"status\":\"ok\"}"

        /** v1.0.2 fix F2: GraphQL's login answer (Instagram's own words). */
        const val LOGIN = "{\"message\":\"Please wait a few minutes before you try again.\"," +
            "\"require_login\":true,\"status\":\"fail\"}"
        const val INSTAGRAM_MEDIA_ID = "3310536259357233439"
        const val INSTAGRAM_FILE = "https://scontent.cdninstagram.com/o1/v/t16/f2/m86/fixture_720.mp4?oe=7A000000"
        const val INSTAGRAM_MILLIS = 15_200L
        const val FACEBOOK_WATCH = "https://www.facebook.com/watch/?v=1000000000000041"
        const val TIKTOK_VIDEO = "https://www.tiktok.com/@fixture/video/1000000000000000051"
        const val FILE_BYTES = 9_437_184L
        const val CAPTURE = "master: one browser-assisted capture requested"
        const val SELECTION = "master: fresh natural playback required"

        /** Task 0: X1's line for the widget's 404 (never final; the tab's own playback next). */
        const val NOT_FOUND_LINE =
            "master: x X1 main's answer: NEEDS_PLAYBACK (status 404: no post in the widget's answer, not proof of removal); next: PLAYBACK"

        fun site(page: String) = if (page == REEL) "instagram" else "x"

        /** X's widget answer for the post: one video, one MP4 file. */
        fun widget() = "{\"__typename\":\"Tweet\",\"id_str\":\"$X_POST_ID\",\"text\":\"fixture\"," +
            "\"user\":{\"name\":\"Fixture\",\"screen_name\":\"fixture_user\"},\"mediaDetails\":[{\"type\":\"video\"," +
            "\"media_url_https\":\"https://pbs.twimg.com/ext_tw_video_thumb/$X_ITEM/pu/img/f.jpg\"," +
            "\"original_info\":{\"height\":1280,\"width\":720},\"video_info\":{\"duration_millis\":$X_MILLIS," +
            "\"variants\":[{\"bitrate\":632000,\"content_type\":\"video/mp4\",\"url\":\"$X_FILE\"}]}}]}"

        /** Instagram's GraphQL answer for the reel: one video, one whole file. */
        fun graphQl() = "{\"data\":{\"xdt_shortcode_media\":{\"__typename\":\"XDTGraphVideo\"," +
            "\"id\":\"$INSTAGRAM_MEDIA_ID\",\"shortcode\":\"C9fixtREEL1\",\"is_video\":true," +
            "\"video_url\":\"$INSTAGRAM_FILE\",\"video_duration\":15.2,\"dimensions\":{\"height\":1280,\"width\":720}}}," +
            "\"status\":\"ok\"}"

        /** Task 15 (F3b): Instagram's GraphQL answer for a photos-only post (the reel's made-up code). */
        fun photoGraphQl() = "{\"data\":{\"xdt_shortcode_media\":{\"__typename\":\"XDTGraphImage\"," +
            "\"id\":\"$INSTAGRAM_MEDIA_ID\",\"shortcode\":\"C9fixtREEL1\",\"is_video\":false," +
            "\"display_url\":\"https://scontent.cdninstagram.com/v/fixture.jpg\",\"dimensions\":{\"height\":1080,\"width\":1080}}}," +
            "\"status\":\"ok\"}"

        /**
         * A progressive MP4 file's first 4096 bytes, made in code: the file type, then a `moov` longer
         * than the read whose movie header (timescale 1000) states [millis].
         */
        fun mp4Start(millis: Long): ByteArray {
            fun u32(value: Long) = ByteArray(4) { ((value ushr (24 - 8 * it)) and 0xFF).toByte() }
            fun box(type: String, payload: ByteArray) = u32(8L + payload.size) + type.toByteArray() + payload
            val ftyp = box("ftyp", "isom".toByteArray() + u32(512) + "isom".toByteArray() + "mp41".toByteArray())
            val mvhd = box("mvhd", u32(0) + u32(0) + u32(0) + u32(1_000) + u32(millis) + ByteArray(80))
            val moov = u32(65_536) + "moov".toByteArray() + mvhd + u32(60_000) + "trak".toByteArray()
            return (ftyp + moov).copyOf(4_096)
        }
    }
}
