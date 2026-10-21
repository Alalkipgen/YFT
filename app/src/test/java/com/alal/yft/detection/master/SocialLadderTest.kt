package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (2): the social-ladder switch's wiring. Flag off (every CI build): the shared
 * adapter client and each lookup's context are today's. Flag on: each Facebook lookup (every link
 * form) gets its own record with Facebook's labels and its own main pages, and main's real
 * Facebook adapter writes its asks (with their ways) and its pages into them; other sites as
 * before. Each case passes the switch explicitly, so it holds whatever the build's flags are.
 * Synthetic pages; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderTest {
    @Test
    fun theSwitchNeedsMasterCapture() {
        assertTrue(!SocialLadder.enabled || BuildConfig.MASTER_CAPTURE_ENABLED)
        assertTrue(SocialLadder.claims("facebook:1000000000000001"))
        assertTrue(SocialLadder.claims("facebook:AbCdEf123"))
        // S2b: TikTok's lookups too; S2c: X's; no other site's.
        assertTrue(SocialLadder.claims("tiktok:11111"))
        assertTrue(SocialLadder.claims("tiktok:ZMabcdef1"))
        assertTrue(SocialLadder.claims("x:1000000000000000001"))
        assertFalse(SocialLadder.claims("instagram:AbCdEf123"))
        assertFalse(SocialLadder.claims("youtube:Yft0Fixture"))
        assertFalse(SocialLadder.claims(null))
    }

    @Test
    fun theSharedAdapterClientKeepsMainsPagesOnlyInASocialBuild() {
        val client = OkHttpClient()
        val today = YouTubeRouteB.mainClient(client)
        val off = SocialLadder.mainClient(client, social = false)
        assertEquals(today.javaClass, off.javaClass) // flag off: route B's client as before
        val on = SocialLadder.mainClient(client, social = true)
        assertTrue(on.toString(), on is MainPageRecorder)

        val http = OkHttpExtractorClient(client)
        assertSame(http, SocialLadder.pages(http, social = false))
        assertTrue(SocialLadder.pages(http, social = true) is MainPageRecorder)
    }

    @Test
    fun aSocialBuildGivesEachFacebookLookupItsOwnLabelledRecordAndPages() {
        val social = Fixture(socialLadder = true).bridge
        for (page in listOf(WATCH, REEL, SHARE)) {
            val first = social.lookupContext(page)
            val second = social.lookupContext(page)
            assertNotNull(page, first[LookupRecord])
            assertNotNull(page, first[MainPages])
            assertNotSame(first[LookupRecord], second[LookupRecord])
            assertNotSame(first[MainPages], second[MainPages])
        }
        // S2b: a TikTok lookup its own record and pages, which name TikTok (V asks TikTok's hosts).
        val tiktok = social.lookupContext(TIKTOK)
        assertNotNull(tiktok[LookupRecord])
        assertEquals("tiktok", tiktok[MainPages]?.site)
        assertNotSame(tiktok[MainPages], social.lookupContext(TIKTOK)[MainPages])
        assertEquals("facebook", social.lookupContext(WATCH)[MainPages]?.site)
        // A page no adapter names: nothing, as before.
        assertSame(EmptyCoroutineContext, social.lookupContext("https://page.test/watch"))
        // Flag off, Master capture off, the build without Master: nothing for Facebook.
        assertSame(EmptyCoroutineContext, Fixture(socialLadder = false).bridge.lookupContext(WATCH))
        assertSame(EmptyCoroutineContext, Fixture(socialLadder = false).bridge.lookupContext(TIKTOK))
        assertSame(EmptyCoroutineContext, Fixture(socialLadder = true, enabledBuild = false).bridge.lookupContext(WATCH))
        assertSame(EmptyCoroutineContext, BrowserMasterFallback.None.lookupContext(WATCH))
    }

    @Test
    fun aSocialRecordListsMainsFacebookAsksWithTheirWaysAndKeepsMainsPages() = runTest {
        val http = FacebookPages()
        val fixture = Fixture(socialLadder = true, http = SocialLadder.pages(LookupRecorder(http), social = true))
        val context = fixture.bridge.lookupContext(REEL)
        val outcome = withContext(context) {
            fixture.sites.inspect(REEL, BrowserRequestContext(REEL, "Browser", "c_user=fixture"), 100)
        }

        // Main's answer is main's own (the page without its data), unchanged by the recorders.
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, (outcome as SiteAdapterOutcome.Failed).reason)
        val record = context[LookupRecord]!!
        assertEquals(2, http.asked.get())
        assertEquals(2, record.mainAsks)
        assertEquals(0, record.masterAsks)
        assertEquals(listOf(SiteWays.PUBLIC_PAGE, SiteWays.PAGE), record.asks().map { it.way })
        assertEquals(listOf(false, true), record.asks().map { it.authorized })
        val pages = context[MainPages]!!.pages()
        assertEquals(listOf(REEL_PAGE, REEL_PAGE), pages.map { it.url })
        assertEquals(listOf(false, true), pages.map { it.authorized })
        assertEquals(listOf(PAGE_BODY, PAGE_BODY), pages.map { it.body })

        // Without the lookup's context (flag off: no record) the same adapter only asks.
        val plain = FacebookPages()
        val off = Fixture(socialLadder = false, http = SocialLadder.pages(LookupRecorder(plain), social = false))
        withContext(off.bridge.lookupContext(REEL)) {
            off.sites.inspect(REEL, BrowserRequestContext(REEL, "Browser", "c_user=fixture"), 100)
        }
        assertEquals(2, plain.asked.get())
        assertNull(off.bridge.lookupContext(REEL)[LookupRecord])
    }

    /** Facebook's pages, answered without their data (main fails its reading). */
    private class FacebookPages : ExtractorHttpClient {
        val asked = AtomicInteger()
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked.incrementAndGet()
            return ExtractorHttpResult.Success(200, PAGE_BODY, url, "text/html")
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
    }

    private class Fixture(
        socialLadder: Boolean,
        enabledBuild: Boolean = true,
        http: ExtractorHttpClient = FacebookPages(),
    ) {
        val capture = WebViewPlaybackCapture(enabledBuild)
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(FacebookExtractor(http), TikTokExtractor(http)),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                MasterMediaValidator { found, _ -> ValidationResult.Valid(found) },
                policy = MasterPolicy(enabled = true),
            ),
            MergeSupport { true },
            socialLadder = socialLadder,
        )
    }

    private companion object {
        const val WATCH = "https://www.facebook.com/watch/?v=1000000000000002"
        const val REEL = "https://www.facebook.com/reel/1000000000000001"
        const val REEL_PAGE = "https://www.facebook.com/reel/1000000000000001"
        const val SHARE = "https://www.facebook.com/share/v/AbCdEf123/"
        const val TIKTOK = "https://www.tiktok.com/@fixture/video/11111"
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"
    }
}
