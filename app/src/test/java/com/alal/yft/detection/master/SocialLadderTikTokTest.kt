package com.alal.yft.detection.master

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialStages
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
 * Social plan S2b: the app's TikTok wiring. Flag off (every CI build): main's TikTok client is
 * today's (its cookies sent back), no stage, Facebook's words as before. Flag on: main's TikTok
 * client keeps main's pages; Master's social rung is both ladders, each claiming only its own
 * site's lookup with a record; each site's Details lines, trial words and final words. Each case
 * passes the switch explicitly, so it holds whatever the build's flags are. Synthetic pages and
 * made-up IDs; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderTikTokTest {
    @Test
    fun mainsTikTokClientIsTodaysWithTheFlagOffAndKeepsMainsPagesWithItOn() {
        val client = OkHttpClient()
        val off = SocialLadder.tiktokClient(client, social = false)
        assertTrue(off.toString(), off is OkHttpExtractorClient) // today's client, as before
        val on = SocialLadder.tiktokClient(client, social = true)
        assertTrue(on.toString(), on is MainPageRecorder)
    }

    @Test
    fun theStageIsBothLaddersAndEachClaimsOnlyItsOwnSitesLookupWithARecord() = runTest {
        val asks = NoAsks()
        val sites = sites(asks)
        assertNull(SocialLadder.stage(asks, sites::identity, social = false, cooldown = SocialCooldown()))
        val stage = checkNotNull(SocialLadder.stage(asks, sites::identity, social = true, cooldown = SocialCooldown()))
        assertTrue(stage.toString(), stage is SocialStages)

        for ((page, key) in listOf(TIKTOK to "tiktok:11111", WATCH to "facebook:1000000000000002")) {
            val identity = sites.identity(page)
            assertNotNull(page, identity)
            // A lookup with its record (its site's): claimed.
            withContext(SocialLadder.lookupContext(key)) {
                assertTrue(page, stage.claims(request(page, identity)))
            }
            // No record (flag off, or no lookup context): not claimed, the engine as before.
            assertFalse(page, stage.claims(request(page, identity)))
        }
        // A page no ladder names: not claimed, even with a record.
        withContext(SocialLadder.lookupContext("tiktok:11111")) {
            assertFalse(stage.claims(request("https://page.test/watch", null)))
        }
        assertEquals(0, asks.asked.get()) // claiming asks nothing
    }

    @Test
    fun eachSitesLinesAndTrialWordsFacebooksAsBefore() {
        for (key in listOf("facebook:1000000000000001", "facebook:AbCdEf123", null)) {
            assertEquals(SocialLadder.LINE, SocialLadder.line(key))
            assertEquals(SocialLadder.TRIAL_MESSAGE, SocialLadder.trialMessage(key))
            assertEquals(SocialLadder.TRIAL_DETAIL, SocialLadder.trialDetail(key))
            assertEquals(SocialLadder.NOT_USED, SocialLadder.notUsed(key))
        }
        val key = "tiktok:11111"
        assertEquals("master: tiktok", SocialLadder.line(key))
        assertEquals(
            "TikTok trial: main's rows were set aside and Master found none. Tap Try again.",
            SocialLadder.trialMessage(key),
        )
        assertEquals("master: tiktok trial: main's", SocialLadder.trialDetail(key))
        assertEquals("master: tiktok: Master's answer not used:", SocialLadder.notUsed(key))
        assertEquals("tiktok", SocialLadder.siteOf(key))
        assertNull(SocialLadder.siteOf("instagram:AbCdEf123"))
    }

    @Test
    fun aTikTokPhotoPostEndsInMainsWordsOnlyOnTheLaddersFinalPhotoLine() {
        val tiktok = "tiktok:11111"
        val photo = "master: tiktok ladder: PHOTO_ONLY (the link: a photo address); next: FINAL; not asked"
        val t1 = "master: tiktok T1 embed player: PHOTO_ONLY (the reader: a photo post); next: FINAL"
        val words = "This TikTok post has no downloadable video." // main's own for a photo post
        assertEquals(words, SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, tiktok, listOf(photo)))
        assertEquals(words, SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, tiktok, listOf("x", t1)))
        // Not the ladder's photo stop: null (the engine's own words, as before).
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, tiktok))
        assertNull(
            SocialLadder.finalMessage(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                tiktok,
                listOf("master: tiktok T1 embed player: PHOTO_ONLY (the reader: a photo post); next: T2"),
            ),
        )
        assertNull(
            SocialLadder.finalMessage(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                tiktok,
                listOf("master: facebook F1 plugin player: PHOTO_ONLY (x); next: FINAL"),
            ),
        )
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, tiktok, listOf(photo)))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.LOGIN_REQUIRED, tiktok, listOf(photo)))
        // Facebook's as before: private or removed in main's words; nothing else.
        val facebook = "This Facebook post is private or no longer available."
        assertEquals(facebook, SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE))
        assertEquals(
            facebook,
            SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, "facebook:1000000000000001"),
        )
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, "facebook:1", listOf(photo)))
    }

    @Test
    fun theTrialsNoticeReadsTikToksLineToo() {
        val segment = "; trial: Master's rows 1, main's 1; the same video: yes; duration within 1 s: yes"
        val tiktok = detected(listOf("master: tiktok ladder after RESPONSE_CHANGED", "master: tiktok$segment"))
        assertEquals("master: tiktok$segment", SocialLadder.trialNotice(tiktok))
        val facebook = detected(listOf("master: facebook$segment"))
        assertEquals("master: facebook$segment", SocialLadder.trialNotice(facebook))
        // No ladder site's line, or no trial segment: null.
        assertNull(SocialLadder.trialNotice(detected(listOf("master: instagram$segment"))))
        assertNull(SocialLadder.trialNotice(detected(listOf("master: tiktok ladder after RESPONSE_CHANGED"))))
    }

    private fun detected(details: List<String>): SiteAdapterOutcome.Detected = SiteAdapterOutcome.Detected(
        "tiktok",
        listOf(
            MediaCandidate(
                TIKTOK, "https://video.example-cdn.test/r.mp4", setOf(CandidateSource.MANIFEST), MediaKind.DIRECT,
                mimeType = "video/mp4", videoId = "tiktok:11111",
            ),
        ),
        details,
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
            listOf(FacebookExtractor(http), TikTokExtractor(http)),
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
        const val WATCH = "https://www.facebook.com/watch/?v=1000000000000002"
        const val TIKTOK = "https://www.tiktok.com/@fixture/video/11111"
    }
}
