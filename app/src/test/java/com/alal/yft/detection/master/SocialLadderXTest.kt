package com.alal.yft.detection.master

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
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.lookup.LookupRecord
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
 * lines (a photo post in main's words, the widget's 404 as no such post); the trial compares the
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
    fun anXPhotoPostAndNoSuchPostEndInTheirWordsOnlyOnXsFinalLines() {
        val photo = "master: x X1 main's answer: PHOTO_ONLY (photos only, no video); next: FINAL"
        val removed = "master: x X1 main's answer: REMOVED (status 404: no such post); next: FINAL"
        assertEquals(
            "This post has photos only, no video.", // main's own for a photo post
            SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf("master: x ladder after NO_MEDIA_FOUND", photo)),
        )
        assertEquals(
            "This X post is no longer available.",
            SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, KEY, listOf(removed)),
        )
        // Not X's final line for that reason: null (the engine's own words, as before).
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, KEY))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(removed)))
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
        assertNull(SocialLadder.trialNotice(detected(listOf(ladder))))
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

    private companion object {
        const val POST = "https://x.com/fixture_user/status/1000000000000000001"
        const val KEY = "x:1000000000000000001"
        const val FILE_720 = "https://video.twimg.com/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4?tag=16"
        const val FILE_360 = "https://video.twimg.com/amplify_video/1000000000000000002/vid/avc1/640x360/b.mp4?tag=16"
        const val OTHER_ITEM = "https://video.twimg.com/amplify_video/1000000000000000003/vid/avc1/1280x720/c.mp4?tag=16"
        const val OFF_HOST = "https://cdn.example.test/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4"
    }
}
