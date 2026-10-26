package com.alal.yft.detection.master

import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
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
import com.alal.yft.extractor.master.social.VkClientValues
import com.alal.yft.extractor.sites.vk.VkExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VK-Master wave 2 (agent D): the app's VK wiring. Flag off: no stage, and V2's values are never
 * asked for. Flag on: Master's social rung has VK's ladder too, claiming only a VK lookup with its
 * record (VK's labels, a MainPages naming VK) and asking nothing to claim it; VK's Details lines
 * and trial words; VK's final answers in main's words (the video removed) and the app's (the
 * region limit); the process's healing values made once. Each case passes the switch explicitly,
 * so it holds whatever the build's flags are. Made-up IDs; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderVkTest {
    @Test
    fun theStageClaimsAVkLookupOnlyWithItsRecordAndAsksNothing() = runTest {
        val asks = NoAsks()
        val sites = sites(asks)
        val made = AtomicInteger()
        val values = { made.incrementAndGet(); VkClientValues.bundled() }
        assertNull(SocialLadder.stage(asks, sites::identity, social = false, cooldown = SocialCooldown(), vkValues = values))
        assertEquals("no ladder build: V2's values never made", 0, made.get())
        val stage = checkNotNull(
            SocialLadder.stage(asks, sites::identity, social = true, cooldown = SocialCooldown(), vkValues = values),
        )
        assertEquals(1, made.get())
        assertTrue(stage.toString(), stage is SocialStages)
        assertTrue(stage.toString(), "VkLadder" in stage.toString())

        val identity = sites.identity(VIDEO)
        assertEquals("vk", identity?.siteId)
        assertEquals(KEY, sites.videoKey(VIDEO))
        assertTrue(SocialLadder.claims(KEY))
        withContext(SocialLadder.lookupContext(KEY)) {
            assertTrue(stage.claims(request(VIDEO, identity)))
            // VK's lookup: VK's labels and a MainPages naming VK.
            assertEquals("vk", coroutineContext[MainPages]?.site)
            assertNotNull(coroutineContext[LookupRecord])
        }
        // No record (flag off, or no lookup context): not claimed, the engine as before.
        assertFalse(stage.claims(request(VIDEO, identity)))
        assertEquals(0, asks.asked.get()) // claiming asks nothing
    }

    @Test
    fun vksLinesAndTrialWords() {
        assertEquals("master: vk", SocialLadder.line(KEY))
        assertEquals(
            "VK trial: main's rows were set aside and Master found none. Tap Try again.",
            SocialLadder.trialMessage(KEY),
        )
        assertEquals("master: vk trial: main's", SocialLadder.trialDetail(KEY))
        assertEquals("master: vk: Master's answer not used:", SocialLadder.notUsed(KEY))
        assertEquals("vk", SocialLadder.siteOf(KEY))
        assertTrue(SocialLadder.VK_SITE_ID in SocialLadder.SITES)
        // The others' as before.
        assertEquals(SocialLadder.TRIAL_MESSAGE, SocialLadder.trialMessage("facebook:1000000000000001"))
        assertEquals("master: x", SocialLadder.line(X_KEY))
    }

    @Test
    fun vksFinalAnswersInMainsWordsAndNeverPhotosOnly() {
        assertEquals(
            VkExtractor.NOT_FOUND_MESSAGE, // main's own for a video VK no longer has
            SocialLadder.finalMessage(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, KEY),
        )
        assertEquals(
            SocialLadder.VK_REGION_MESSAGE,
            SocialLadder.finalMessage(SiteExtractionFailure.GEO_RESTRICTED, KEY),
        )
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.RESPONSE_CHANGED, KEY))
        val final = "master: vk V1: REMOVED (restriction: can_play 0, VK's deleted icon); next: FINAL"
        assertFalse(SocialLadder.photosOnly(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(final)))
        // Another site's words as before.
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.GEO_RESTRICTED, X_KEY))
    }

    @Test
    fun theProcesssHealingValuesAreMadeOnce() {
        val client = OkHttpClient()
        assertSame(SocialLadder.vkValues(client, null), SocialLadder.vkValues(client, null))
        assertEquals("master/site-health.json", SocialLadder.VK_HEALTH_FILE)
    }

    private fun request(page: String, identity: SitePageIdentity?) = MasterRequest(
        pageUrl = page,
        generation = 1,
        nowEpochMs = 1,
        primaryFailure = SiteExtractionFailure.NO_MEDIA_FOUND,
        identity = identity,
    )

    private fun sites(http: ExtractorHttpClient) = SiteAdapterCoordinator(
        SiteExtractorRegistry(listOf(VkExtractor(http), XExtractor(http)), SiteAdapterFlags { true }),
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
        const val VIDEO = "https://vkvideo.ru/video-100000001_456000001"
        const val KEY = "vk:-100000001_456000001"
        const val X_KEY = "x:1000000000000000001"
    }
}
