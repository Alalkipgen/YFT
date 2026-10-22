package com.alal.yft.feature.browser

import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.master.BrowserMasterFallback
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.social.InstagramTabProbe
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S2d part 1 (decision 19 (v)): the browser's side of Instagram's tab-fetch probe.
 * Every other build (no switch): no lookup carries a probe. A social-ladder build: an Instagram
 * lookup's context carries it (its own switch read each time), another site's none; without a
 * screen collecting the asks there is no tab; with one, the ask names the post and the screen's
 * answer goes back to the ladder. Fixture adapters and a fake backup; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserInstagramProbeTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun everyOtherBuildsLookupsCarryNoProbe() = runTest {
        val backup = ProbingBackup()
        val vm = viewModel(backup, probeOn = null)
        open(vm, REEL)
        settle { backup.calls == 1 }
        assertEquals(listOf<InstagramTabProbe?>(null), backup.probes)
    }

    @Test
    fun anInstagramLookupCarriesTheProbeAndWithoutAScreenThereIsNoTab() = runTest {
        var on = false
        val backup = ProbingBackup()
        val vm = viewModel(backup, probeOn = { on })
        open(vm, REEL)
        settle { backup.calls == 1 }
        val probe = checkNotNull(backup.probes.single())
        assertFalse(probe.on()) // off by default
        on = true
        assertTrue(probe.on()) // read each time
        assertEquals(InstagramTabProbe.NO_TAB, (backup.replies.single() as InstagramTabProbe.Reply.NotAsked).why)
    }

    @Test
    fun theScreensAnswerGoesBackToTheLadder() = runTest {
        val backup = ProbingBackup()
        val vm = viewModel(backup, probeOn = { true })
        val asked = mutableListOf<InstagramTabProbe.Request>()
        val screen = backgroundScope.launch {
            vm.instagramProbes.collect { request ->
                asked += request.probe
                assertTrue(request.isWaiting)
                request.answer(InstagramTabProbe.Reply.Answered(200, 2, "{}", null, null, wall = false))
            }
        }
        runCurrent()
        open(vm, REEL)
        settle { backup.calls == 1 && backup.replies.isNotEmpty() }
        assertEquals(CODE, asked.single().code)
        assertEquals(MEDIA_ID, asked.single().mediaId)
        val reply = backup.replies.single() as InstagramTabProbe.Reply.Answered
        assertEquals(200, reply.status)
        assertEquals("{}", reply.body)
        screen.cancel()
    }

    @Test
    fun anotherSitesLookupCarriesNoProbe() = runTest {
        val backup = ProbingBackup()
        val vm = viewModel(backup, probeOn = { true })
        open(vm, TIKTOK)
        settle { backup.calls == 1 }
        assertEquals(listOf<InstagramTabProbe?>(null), backup.probes)
    }

    private fun open(vm: BrowserViewModel, url: String) {
        vm.onPageStarted(url)
        vm.onPageFinished(url, "Fixture")
    }

    private fun TestScope.settle(done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            Thread.sleep(10)
        }
        fail("the lookup did not finish")
    }

    private fun viewModel(backup: ProbingBackup, probeOn: (() -> Boolean)?) = BrowserViewModel(
        OkHttpClient(),
        SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(Failing("instagram", REEL, CODE), Failing("tiktok", TIKTOK, "11111"))),
            MergeSupport { true },
        ),
        DetectedMediaStore(),
        masterFallback = backup,
        instagramProbeOn = probeOn,
    )

    /** A backup that notes the probe its lookup's context carries and asks it once. */
    private class ProbingBackup : BrowserMasterFallback {
        override val enabled = true
        var calls = 0
        val probes = mutableListOf<InstagramTabProbe?>()
        val replies = mutableListOf<InstagramTabProbe.Reply>()

        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome {
            val probe = coroutineContext[InstagramTabProbe]
            probes += probe
            probe?.let { replies += it.ask(InstagramTabProbe.Request(CODE, MEDIA_ID, lookupUrl)) }
            calls++
            return primary
        }
    }

    /** One site's adapter that identifies only [page] and fails its lookup. */
    private class Failing(override val id: String, private val page: String, private val content: String) : SiteExtractor {
        override val displayName = "Fixture"

        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == page) SitePageIdentity(id, content, pageUrl) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
    }

    private companion object {
        const val CODE = "C9fixtREEL1"
        const val MEDIA_ID = "3413599992010523381"
        const val REEL = "https://www.instagram.com/reel/$CODE/"
        const val TIKTOK = "https://www.tiktok.com/@fixture/video/11111"
    }
}
