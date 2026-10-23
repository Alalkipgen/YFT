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
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialStages
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S2c: the app's X wiring. Flag off: no stage. Flag on: Master's social rung has X's
 * ladder too, claiming only an X lookup with its record (X's labels, a MainPages naming X, so V
 * asks X's media host); X's Details lines and trial words; X's final words only on X's final
 * line (a photo post in main's words); task 0: the widget's 404 is never final, so after the tab's
 * playback finds nothing the lookup keeps main's reason (Try again stays); the trial compares the
 * item (one video of a post with several); the trial's notice reads X's line. Each case passes the
 * switch explicitly, so it holds whatever the build's flags are. Synthetic rows and made-up IDs;
 * no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderXTest {
    @Test
    fun theStageClaimsAnXLookupOnlyWithItsRecordAndAsksNothing() = runTest {
        val asks = NoAsks()
        val sites = sites(asks)
        assertNull(SocialLadder.stage(asks, sites::identity, social = false, cooldown = SocialCooldown()))
        val stage = checkNotNull(SocialLadder.stage(asks, sites::identity, social = true, cooldown = SocialCooldown()))
        assertTrue(stage.toString(), stage is SocialStages)
        assertTrue(stage.toString(), "XLadder" in stage.toString())

        val identity = sites.identity(POST)
        assertEquals("x", identity?.siteId)
        assertTrue(SocialLadder.claims("x:${identity?.contentId}"))
        withContext(SocialLadder.lookupContext(KEY)) {
            assertTrue(stage.claims(request(POST, identity)))
            // X's lookup: X's labels and a MainPages naming X (V asks X's media host).
            assertEquals("x", coroutineContext[MainPages]?.site)
            assertNotNull(coroutineContext[LookupRecord])
        }
        // No record (flag off, or no lookup context): not claimed, the engine as before.
        assertFalse(stage.claims(request(POST, identity)))
        assertEquals(0, asks.asked.get()) // claiming asks nothing
    }

    @Test
    fun xsLinesAndTrialWords() {
        assertEquals("master: x", SocialLadder.line(KEY))
        assertEquals("X trial: main's rows were set aside and Master found none. Tap Try again.", SocialLadder.trialMessage(KEY))
        assertEquals("master: x trial: main's", SocialLadder.trialDetail(KEY))
        assertEquals("master: x: Master's answer not used:", SocialLadder.notUsed(KEY))
        assertEquals("x", SocialLadder.siteOf(KEY))
        // Facebook's and TikTok's as before.
        assertEquals(SocialLadder.TRIAL_MESSAGE, SocialLadder.trialMessage("facebook:1000000000000001"))
        assertEquals("master: tiktok", SocialLadder.line("tiktok:11111"))
    }

    @Test
    fun anXPhotoPostEndsInMainsWordsOnlyOnXsFinalLineAndTheWidgets404NeverDoes() {
        val photo = "master: x X1 main's answer: PHOTO_ONLY (photos only, no video); next: FINAL"
        assertEquals(
            "This post has photos only, no video.", // main's own for a photo post
            SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf("master: x ladder after NO_MEDIA_FOUND", photo)),
        )
        // Task 15 (F3b): that photo answer is final (no Try again); the 404's is not.
        assertTrue(SocialLadder.photosOnly(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(photo)))
        assertFalse(SocialLadder.photosOnly(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(NOT_FOUND_LINE)))
        // Task 0: the widget's 404 is never final (X1 names no reason), and no X line ends a lookup
        // as removed, not even an earlier build's REMOVED line.
        val removed = "master: x X1 main's answer: REMOVED (status 404: no such post); next: FINAL"
        for (reason in SiteExtractionFailure.entries) {
            assertNull("$reason", SocialLadder.finalMessage(reason, KEY, listOf(NOT_FOUND_LINE)))
            assertNull("$reason", SocialLadder.finalMessage(reason, KEY, listOf(removed)))
        }
        // Not X's final line for that reason: null (the engine's own words, as before).
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, KEY))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, KEY, listOf(photo)))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.LOGIN_REQUIRED, KEY, listOf(photo, removed)))
        assertNull(
            SocialLadder.finalMessage(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                KEY,
                listOf("master: x X1 main's answer: AGE_LIMITED (an empty tombstone); next: BROWSER"),
            ),
        )
        assertNull(
            SocialLadder.finalMessage(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                KEY,
                listOf("master: tiktok ladder: PHOTO_ONLY (the link: a photo address); next: FINAL; not asked"),
            ),
        )
        // X's lines never give another site's words.
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, "tiktok:11111", listOf(photo)))
        assertEquals(
            "This Facebook post is private or no longer available.",
            SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, "facebook:1000000000000001", listOf(removed)),
        )
    }

    @Test
    fun theWidgets404AfterTheTabsPlaybackFindsNothingKeepsMainsReason() = runTest {
        // Task 0 (c): main's widget answer is a 404; X1 asks nothing, the tab's own playback finds
        // nothing, and the lookup keeps main's reason (not final: Try again stays), never "removed".
        val fixture = NotFoundFixture()

        val (primary, recovered) = fixture.lookup()

        val main = primary as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, main.reason)
        val failed = recovered as SiteAdapterOutcome.Failed
        assertEquals(main.reason, failed.reason)
        assertTrue(failed.canRetry)
        // Task 14 (observation e): no video in the tab (X's "post not found" page), so main's own
        // words (with Try again), not the playback words; its line says which, before X1's.
        assertEquals(XExtractor.HIDDEN_MESSAGE, failed.message)
        assertEquals(main.message, failed.message)
        val words = failed.details.indexOf("master: x words: main's (no video in the tab)")
        assertTrue(failed.details.toString(), words >= 0)
        assertTrue(failed.details.toString(), failed.details.any { it.startsWith(NOT_FOUND_LINE) })
        assertTrue(words < failed.details.indexOfFirst { it.startsWith(NOT_FOUND_LINE) })
        assertTrue(failed.details.toString(), failed.details.none { "REMOVED" in it || "next: FINAL" in it })
        // No new widget request (no X2, no token check): Master asks nothing; main asked once.
        assertEquals(0, fixture.master.asked.get())
        assertEquals(1, fixture.main.asked.get())
        assertEquals(1, fixture.captures.get())
        assertFalse(fixture.cooldown.cooling(WIDGET_HOST))
    }

    @Test
    fun anAgeWallGetsTheSignInWordsAndAVideoInTheTabThePlaybackWords() = runTest {
        // Task 14 (observation e): X's empty tombstone (X1: AGE_LIMITED, the browser next), the
        // tab without a video: the sign-in words, main's reason kept (Try again); the same answer
        // with a video in the tab, and the widget's 404 so too: the playback words.
        val wall = NotFoundFixture(widget = "{\"__typename\":\"TweetTombstone\",\"tombstone\":{}}")

        val (primary, recovered) = wall.lookup()

        val main = primary as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, main.reason)
        val failed = recovered as SiteAdapterOutcome.Failed
        assertEquals(main.reason, failed.reason)
        assertTrue(failed.canRetry)
        assertEquals("Sign in to X on this page first, then try again.", failed.message)
        assertEquals(SocialLadder.signInMessage(KEY), failed.message)
        assertTrue(failed.details.toString(), "master: x words: sign-in (AGE_LIMITED; no video in the tab)" in failed.details)
        assertTrue(failed.details.toString(), failed.details.any { it.startsWith("master: x X1 main's answer: AGE_LIMITED (") })
        assertEquals(1, wall.captures.get())

        for (fixture in listOf(
            NotFoundFixture(widget = "{\"__typename\":\"TweetTombstone\",\"tombstone\":{}}", tabVideo = true),
            NotFoundFixture(tabVideo = true),
        )) {
            val played = fixture.lookup().second as SiteAdapterOutcome.Failed
            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, played.reason)
            assertTrue(played.canRetry)
            assertEquals("Play the video on this page, then tap Try again.", played.message)
            assertTrue(played.details.toString(), "master: x words: playback (the tab shows a video)" in played.details)
        }
    }

    @Test
    fun xsTrialComparesTheItemAndFacebooksTheVideoAsBefore() {
        val key: (String?) -> String? = { it }
        val mains = listOf(row(FILE_720, KEY, 9_000), row(FILE_360, KEY, 9_000))
        assertEquals(
            "; trial: Master's rows 1, main's 2; the same item: yes; duration within 1 s: yes",
            SocialLadder.trialSegment(listOf(row(FILE_720, KEY, 9_400)), mains, key),
        )
        // Another video of the same post: the same post's key, not the same item.
        assertEquals(
            "; trial: Master's rows 1, main's 2; the same item: no; duration within 1 s: no",
            SocialLadder.trialSegment(listOf(row(OTHER_ITEM, KEY, 12_000)), mains, key),
        )
        // A row off X's video host names no item; another post's key is not the same either.
        assertTrue(SocialLadder.trialSegment(listOf(row(OFF_HOST, KEY, 9_000)), mains, key).contains("the same item: no"))
        assertTrue(SocialLadder.trialSegment(listOf(row(FILE_720, "x:2", 9_000)), mains, key).contains("the same item: no"))
        assertTrue(SocialLadder.trialSegment(emptyList(), mains, key).contains("the same item: no"))
        // Facebook's: the same video, as before (no item).
        val facebook = "facebook:1000000000000001"
        assertEquals(
            "; trial: Master's rows 1, main's 1; the same video: yes; duration within 1 s: yes",
            SocialLadder.trialSegment(
                listOf(row("https://video.fixture.fbcdn.net/v/a.mp4", facebook, 9_000)),
                listOf(row("https://video.fixture.fbcdn.net/v/b.mp4", facebook, 9_000)),
                key,
            ),
        )
    }

    @Test
    fun theTrialsNoticeReadsXsLineToo() {
        val segment = "; trial: Master's rows 1, main's 1; the same item: yes; duration within 1 s: yes"
        val ladder = "master: x ladder after RESPONSE_CHANGED; main's answer replayed, no new ask"
        assertEquals("master: x$segment", SocialLadder.trialNotice(detected(listOf(ladder, "master: x$segment"))))
        // Task 15 (F1): without the trial's comparison, X's ladder line alone.
        assertEquals(ladder, SocialLadder.trialNotice(detected(listOf(ladder))))
        assertNull(SocialLadder.trialNotice(detected(listOf("master: vimeo$segment"))))
    }

    private fun detected(details: List<String>): SiteAdapterOutcome.Detected =
        SiteAdapterOutcome.Detected("x", listOf(row(FILE_720, KEY, 9_000)), details)

    private fun row(url: String, key: String, millis: Long) = MediaCandidate(
        POST, url, setOf(CandidateSource.MANIFEST), MediaKind.DIRECT,
        mimeType = "video/mp4", videoId = key, durationMillis = millis,
    )

    private fun request(page: String, identity: SitePageIdentity?) = MasterRequest(
        pageUrl = page,
        generation = 1,
        nowEpochMs = 1,
        primaryFailure = SiteExtractionFailure.RESPONSE_CHANGED,
        identity = identity,
    )

    private fun sites(http: ExtractorHttpClient) = SiteAdapterCoordinator(
        SiteExtractorRegistry(
            listOf(FacebookExtractor(http), TikTokExtractor(http), XExtractor(http)),
            SiteAdapterFlags { true },
        ),
        MergeSupport { true },
    )

    /** A client the stage never asks while it only claims. */
    private class NoAsks : ExtractorHttpClient {
        val asked = AtomicInteger()
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked.incrementAndGet()
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            asked.incrementAndGet()
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
    }

    /** Main's client: every GET a 404 (the widget's answer for the post), or [widget] (task 14). */
    private class NotFound(private val widget: String? = null) : ExtractorHttpClient {
        val asked = AtomicInteger()
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked.incrementAndGet()
            return widget?.let { ExtractorHttpResult.Success(200, it, url, "application/json") }
                ?: ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
    }

    /**
     * One X lookup as the app wires it in a social-ladder build: main's real X adapter over
     * [main] (the widget's 404), Master's engine with X's ladder over [master] (never asked) and a
     * tab playback that finds nothing; task 14: main's [widget] answer instead of the 404, and
     * whether the tab showed a video ([tabVideo]).
     */
    private class NotFoundFixture(widget: String? = null, tabVideo: Boolean = false) {
        val cooldown = SocialCooldown()
        val main = NotFound(widget)
        val master = NoAsks()
        val captures = AtomicInteger()
        private val capture = WebViewPlaybackCapture(true)
        private val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(XExtractor(SocialLadder.pages(LookupRecorder(main), true))),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        private val scope = capture.session.navigate(POST)!!
        private val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                SocialLadder.validator(
                    MasterMediaValidator { found, _ -> ValidationResult.Valid(found.copy(contentLengthBytes = 4096)) },
                    social = true,
                ) { MediaStartReader { _, _, _ -> MediaStart.Failed(404) } },
                PlaybackCaptureProvider {
                    captures.incrementAndGet()
                    CaptureResult.Unavailable
                },
                MasterPolicy(enabled = true),
                contracts = SiteContracts(master),
                social = SocialLadder.stage(master, sites::identity, social = true, cooldown = cooldown),
            ),
            MergeSupport { true },
            requestFactory = { reason, at, expected -> MasterRequest(POST, scope.generation, at, reason, expected) },
            socialLadder = true,
            socialCooldown = cooldown,
            tabVideo = { tabVideo },
        )

        /** Main's answer, then Master's, in one lookup. */
        suspend fun lookup(): Pair<SiteAdapterOutcome, SiteAdapterOutcome> = withContext(bridge.lookupContext(POST)) {
            val primary = sites.inspect(POST, BrowserRequestContext(POST, "Browser", null), NOW)
            primary to bridge.recover(POST, primary, NOW)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val WIDGET_HOST = "cdn.syndication.twimg.com"

        /** Task 0: X1's line for the widget's 404 (never final; the tab's own playback next). */
        const val NOT_FOUND_LINE =
            "master: x X1 main's answer: NEEDS_PLAYBACK (status 404: no post in the widget's answer, not proof of removal); next: PLAYBACK"
        const val POST = "https://x.com/fixture_user/status/1000000000000000001"
        const val KEY = "x:1000000000000000001"
        const val FILE_720 = "https://video.twimg.com/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4?tag=16"
        const val FILE_360 = "https://video.twimg.com/amplify_video/1000000000000000002/vid/avc1/640x360/b.mp4?tag=16"
        const val OTHER_ITEM = "https://video.twimg.com/amplify_video/1000000000000000003/vid/avc1/1280x720/c.mp4?tag=16"
        const val OFF_HOST = "https://cdn.example.test/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4"
    }
}
