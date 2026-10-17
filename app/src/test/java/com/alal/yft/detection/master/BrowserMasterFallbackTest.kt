package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.CodecSteering
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.modules.MasterSiteModule
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.LookupRecord
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserMasterFallbackTest {
    private val generic = "https://page.test/watch"
    private val tiktok = "https://www.tiktok.com/@fixture/video/11111"
    private val media = "https://cdn.test/main.mp4"

    @Test
    fun codecSteeringFollowsTheDeviceMergeRules() {
        // R5: VP9/Opus WebM merges from Android 10; AV1 merges are off everywhere.
        assertEquals(
            CodecSteering(vp9 = false, av1 = false),
            AndroidBrowserMasterFallback.codecSteering(28),
        )
        assertEquals(
            CodecSteering(vp9 = true, av1 = false),
            AndroidBrowserMasterFallback.codecSteering(29),
        )
        assertFalse(AndroidBrowserMasterFallback.codecSteering(35).av1)
    }

    @Test
    fun successAndOrdinaryGenericPageNeverCallCapture() = runTest {
        val fixture = Fixture(generic)
        val success = SiteAdapterOutcome.Detected("generic", listOf(candidate()))
        assertSame(success, fixture.bridge.recover(generic, success, 100))
        assertSame(
            SiteAdapterOutcome.NotHandled,
            fixture.bridge.recover(generic, SiteAdapterOutcome.NotHandled, 100),
        )
        assertEquals(0, fixture.factories.get())
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun explicitDisabledMatchDoesNotBecomeAGenericBackup() = runTest {
        val fixture = Fixture(tiktok, enabledSite = false)
        assertSame(
            SiteAdapterOutcome.NotHandled,
            fixture.bridge.recover(tiktok, SiteAdapterOutcome.NotHandled, 100, true),
        )
        assertEquals(0, fixture.factories.get())
    }

    @Test
    fun terminalAccessAndNetworkFailuresNeverSample() = runTest {
        val fixture = Fixture(tiktok)
        for (reason in listOf(
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.DRM_PROTECTED,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.RATE_LIMITED,
        )) {
            val primary = failed(reason)
            assertSame(primary, fixture.bridge.recover(tiktok, primary, 100))
        }
        assertEquals(0, fixture.factories.get())
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun walledSitesBotLoginAndPlayerFailuresNeverSample() = runTest {
        for (page in listOf(
            "https://www.youtube.com/watch?v=fixture",
            "https://youtu.be/fixture",
            "https://www.reddit.com/r/fixture/comments/abc/clip/",
        )) {
            val fixture = Fixture(page)
            for (reason in listOf(
                SiteExtractionFailure.BOT_CHECK,
                SiteExtractionFailure.LOGIN_REQUIRED,
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            )) {
                val primary = failed(reason)
                assertSame(primary, fixture.bridge.recover(page, primary, 100))
            }
            assertEquals(0, fixture.factories.get())
            assertEquals(0, fixture.captures.get())
            assertEquals(0, fixture.probes.get())
        }
    }

    /** Phase 1.4 item 2: the h-only APK's site still goes through Master when its page is found. */
    @Test
    fun aFoundPageOfTheMasterFirstSiteGoesThroughMaster() = runTest {
        val fixture = Fixture(tiktok, masterFirstSite = "tiktok")
        val primary = SiteAdapterOutcome.Detected("tiktok", listOf(candidate()))
        val result = fixture.bridge.recover(tiktok, primary, 100) as SiteAdapterOutcome.Detected
        assertNotSame(primary, result)
        assertEquals("master", result.adapterId)
        assertEquals("tiktok:11111", result.candidates.single().videoId)
        assertEquals(1, fixture.factories.get())
        assertEquals(1, fixture.captures.get())
    }

    /**
     * Item 2, run 4: the trial's found page is answered by the site's own module (YouTube: the
     * ladder, so H's row), not by capture. Run 4 showed the adapter's rows: the engine skips a
     * claimed page unless no adapter answered, so a found page must reach it as unanswered.
     */
    @Test
    fun aFoundPageOfTheMasterFirstSiteIsAnsweredByItsModule() = runTest {
        val module = FixtureModule()
        val fixture = Fixture(tiktok, masterFirstSite = "tiktok", modules = listOf(module))
        val primary = SiteAdapterOutcome.Detected("tiktok", listOf(candidate()))
        val result = fixture.bridge.recover(tiktok, primary, 100) as SiteAdapterOutcome.Detected
        assertEquals("master", result.adapterId)
        assertEquals(MediaKind.HLS, result.candidates.single().kind)
        assertEquals(FixtureModule.ROW_URL, result.candidates.single().mediaUrl)
        assertEquals(1, module.calls.get())
        assertEquals(0, fixture.captures.get())
        // A failed adapter's answer still stands for a claimed page (the engine's rule, unchanged).
        val failedPrimary = failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        val normal = Fixture(tiktok, modules = listOf(module))
        assertSame(failedPrimary, normal.bridge.recover(tiktok, failedPrimary, 100))
        assertEquals(1, module.calls.get())
    }

    /**
     * Item 2, run 4: the h-only trial never gives the adapter's rows back. Master cannot ask (no
     * page), or the page went to another video meanwhile: the lookup fails and names the step;
     * the same video reloaded meanwhile keeps the module's answer.
     */
    @Test
    fun theMasterFirstSiteNeverGetsTheAdapterRowsBack() = runTest {
        val primary = SiteAdapterOutcome.Detected("tiktok", listOf(candidate()))
        val noPage = Fixture(tiktok, masterFirstSite = "tiktok", modules = listOf(FixtureModule()), noRequest = true)
        val failed = noPage.bridge.recover(tiktok, primary, 100) as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failed.reason)
        assertTrue(failed.details.toString(), failed.details.any { "h-only: no page to ask" in it })

        lateinit var reloaded: Fixture
        reloaded = Fixture(
            tiktok, masterFirstSite = "tiktok",
            modules = listOf(FixtureModule { reloaded.capture.session.navigate(tiktok) }),
        )
        val kept = reloaded.bridge.recover(tiktok, primary, 100) as SiteAdapterOutcome.Detected
        assertEquals(MediaKind.HLS, kept.candidates.single().kind)

        lateinit var moved: Fixture
        moved = Fixture(
            tiktok, masterFirstSite = "tiktok",
            modules = listOf(FixtureModule { moved.capture.session.navigate("https://www.tiktok.com/@fixture/video/22222") }),
        )
        val gone = moved.bridge.recover(tiktok, primary, 100) as SiteAdapterOutcome.Failed
        assertTrue(gone.details.toString(), gone.details.any { "h-only: the page changed" in it })
    }

    /** Item 2: every other build (no master-first site) and every other site return at once. */
    @Test
    fun aFoundPageReturnsAtOnceWithoutTheTrialOrOnAnotherSite() = runTest {
        val primary = SiteAdapterOutcome.Detected("tiktok", listOf(candidate()))
        val normal = Fixture(tiktok)
        assertSame(primary, normal.bridge.recover(tiktok, primary, 100))
        val otherSite = Fixture(tiktok, masterFirstSite = AndroidBrowserMasterFallback.YOUTUBE_SITE_ID)
        assertSame(primary, otherSite.bridge.recover(tiktok, primary, 100))
        val genericPage = Fixture(generic, masterFirstSite = "tiktok")
        val found = SiteAdapterOutcome.Detected("generic", listOf(candidate()))
        assertSame(found, genericPage.bridge.recover(generic, found, 100))
        for (fixture in listOf(normal, otherSite, genericPage)) {
            assertEquals(0, fixture.factories.get())
            assertEquals(0, fixture.captures.get())
        }
    }

    /**
     * Phase 1.4 item 7 (YouTube routing B) R1: a route-B build gives each YouTube lookup a new
     * record; another site, a page no enabled adapter claims, Master off in the build and every
     * other build (the switch off) get nothing, so their lookups run as before.
     */
    @Test
    fun aRouteBBuildGivesEachYouTubeLookupItsOwnRecordAndNothingElseOne() {
        val routeB = Fixture(YOUTUBE_PAGE, extractor = youTubeExtractor(), youtubeRouteB = true)
        val first = routeB.bridge.lookupContext(YOUTUBE_PAGE)[LookupRecord]
        val second = routeB.bridge.lookupContext(YOUTUBE_PAGE)[LookupRecord]
        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
        assertEquals(0, first!!.mainAsks)
        assertEquals(0, first.keptCount)

        val none = EmptyCoroutineContext
        assertSame(none, routeB.bridge.lookupContext(generic))
        assertSame(none, Fixture(YOUTUBE_PAGE, extractor = youTubeExtractor()).bridge.lookupContext(YOUTUBE_PAGE))
        assertSame(none, Fixture(tiktok, youtubeRouteB = true).bridge.lookupContext(tiktok))
        val adapterOff = Fixture(YOUTUBE_PAGE, enabledSite = false, extractor = youTubeExtractor(), youtubeRouteB = true)
        assertSame(none, adapterOff.bridge.lookupContext(YOUTUBE_PAGE))
        val masterOff = Fixture(YOUTUBE_PAGE, enabledBuild = false, extractor = youTubeExtractor(), youtubeRouteB = true)
        assertSame(none, masterOff.bridge.lookupContext(YOUTUBE_PAGE))
        assertSame(none, BrowserMasterFallback.None.lookupContext(YOUTUBE_PAGE))
    }

    /** Item 7 R1: the record changes nothing yet — a found YouTube page still returns at once. */
    @Test
    fun aRouteBRecordLeavesTheLookupAsBefore() = runTest {
        val fixture = Fixture(YOUTUBE_PAGE, extractor = youTubeExtractor(), youtubeRouteB = true)
        val record = fixture.bridge.lookupContext(YOUTUBE_PAGE)
        val found = SiteAdapterOutcome.Detected("youtube", listOf(candidate()))
        assertSame(found, withContext(record) { fixture.bridge.recover(YOUTUBE_PAGE, found, 100) })
        assertEquals(0, fixture.factories.get())
        assertEquals(0, fixture.captures.get())
        assertEquals(0, record[LookupRecord]!!.mainAsks)
    }

    @Test
    fun recoverableSiteFailureUsesOneCaptureAndKnownFocusedIdentity() = runTest {
        val fixture = Fixture(tiktok)
        val result = fixture.bridge.recover(
            tiktok, failed(SiteExtractionFailure.BOT_CHECK), 100,
        ) as SiteAdapterOutcome.Detected
        assertEquals("tiktok:11111", result.candidates.single().videoId)
        assertEquals(1, fixture.captures.get())
        assertEquals(1, fixture.probes.get())
    }

    @Test
    fun genericBackupOnlyRunsOnDemandAndDoesNotInventAnId() = runTest {
        val fixture = Fixture(generic)
        val result = fixture.bridge.recover(
            generic, SiteAdapterOutcome.NotHandled, 100, true,
        ) as SiteAdapterOutcome.Detected
        assertNull(result.candidates.single().videoId)
        assertEquals(1, fixture.captures.get())
    }

    @Test
    fun pausedBrowserCannotRecoverABotOrLoginFailure() = runTest {
        val fixture = Fixture(tiktok, authorized = false)
        val result = fixture.bridge.recover(
            tiktok, failed(SiteExtractionFailure.BOT_CHECK), 100,
        ) as SiteAdapterOutcome.Failed
        assertTrue(result.message.startsWith("Play the video"))
        assertEquals(0, fixture.probes.get())
    }

    @Test
    fun differentNamedPostAndUnrelatedTabAreNotUsed() = runTest {
        val fixture = Fixture(tiktok)
        val other = tiktok.replace("11111", "22222")
        val primary = failed(SiteExtractionFailure.RESPONSE_CHANGED)
        assertSame(primary, fixture.bridge.recover(other, primary, 100))
        assertSame(primary, fixture.bridge.recover(generic, primary, 100))
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun deviceMergeGateStillAppliesToMasterResults() = runTest {
        val fixture = Fixture(generic, canMerge = false)
        val result = fixture.bridge.recover(
            generic, SiteAdapterOutcome.NotHandled, 100, true,
        ) as SiteAdapterOutcome.Failed
        assertTrue(result.message.contains("cannot merge"))
    }

    @Test
    fun detachWhileCaptureRunsCannotReturnAStaleSuccess() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val fixture = Fixture(generic, beforeCapture = {
            entered.complete(Unit)
            finish.await()
        })
        val work = async {
            fixture.bridge.recover(generic, SiteAdapterOutcome.NotHandled, 100, true)
        }
        entered.await()
        fixture.capture.session.clear()
        finish.complete(Unit)
        assertSame(SiteAdapterOutcome.NotHandled, work.await())
    }

    @Test
    fun disabledBuildKeepsPrimaryAndNoCaptureFactory() = runTest {
        val fixture = Fixture(tiktok, enabledBuild = false)
        val primary = failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        assertSame(primary, fixture.bridge.recover(tiktok, primary, 100))
        assertEquals(0, fixture.factories.get())
    }

    @Test
    fun contractRowsReachTheAppUnderTheAdaptersKey() = runTest {
        // R8: the hook hands Master the adapter's identity; the site's own answer is read and
        // its rows carry the app's key, so no capture is needed.
        val page = "https://vimeo.com/123456789"
        val asked = mutableListOf<String>()
        val http = object : ExtractorHttpClient {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
                maxBodyBytes: Long,
            ): ExtractorHttpResult {
                asked += url
                return ExtractorHttpResult.Success(200, VIMEO_CONFIG, url, "application/json")
            }

            override suspend fun postJson(
                url: String,
                body: String,
                headers: Map<String, String>,
                maxBodyBytes: Long,
            ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
        val fixture = Fixture(
            page, extractor = FixtureExtractor("vimeo", "https://vimeo.com/"),
            contracts = SiteContracts(http),
        )
        val primary = SiteAdapterOutcome.Failed(
            "vimeo", SiteExtractionFailure.NO_MEDIA_FOUND, "Fixture failure", true,
        )

        val outcome = fixture.bridge.recover(page, primary, 100) as SiteAdapterOutcome.Detected

        assertEquals("vimeo", outcome.adapterId)
        assertEquals(listOf("https://cdn.test/v720.mp4"), outcome.candidates.map { it.mediaUrl })
        assertEquals(listOf("https://player.vimeo.com/video/123456789/config"), asked)
        assertEquals(0, fixture.captures.get())
        assertEquals(1, fixture.probes.get())
    }

    private fun youTubeExtractor() = FixtureExtractor("youtube", YOUTUBE_PREFIX)

    private fun failed(reason: SiteExtractionFailure) =
        SiteAdapterOutcome.Failed("tiktok", reason, "Fixture failure", true)

    private fun candidate() = MediaCandidate(
        generic, media, setOf(CandidateSource.REQUEST), MediaKind.DIRECT, mimeType = "video/mp4",
    )

    private inner class Fixture(
        page: String,
        enabledSite: Boolean = true,
        enabledBuild: Boolean = true,
        authorized: Boolean = true,
        canMerge: Boolean = true,
        beforeCapture: suspend () -> Unit = {},
        extractor: SiteExtractor = FixtureExtractor(),
        contracts: SiteContracts? = null,
        masterFirstSite: String? = null,
        modules: List<MasterSiteModule> = emptyList(),
        noRequest: Boolean = false,
        youtubeRouteB: Boolean = false,
    ) {
        val capture = WebViewPlaybackCapture(enabledBuild)
        val factories = AtomicInteger()
        val captures = AtomicInteger()
        val probes = AtomicInteger()
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(extractor), SiteAdapterFlags { enabledSite }),
            MergeSupport { true },
        )
        val scope = capture.session.navigate(page)!!
        val engine = MasterFallbackEngine(
            validator = MasterMediaValidator { found, _ ->
                probes.incrementAndGet()
                ValidationResult.Valid(found.copy(contentLengthBytes = 4096))
            },
            capture = PlaybackCaptureProvider { request ->
                captures.incrementAndGet()
                beforeCapture()
                CaptureResult.Available(
                    PageSnapshot(
                        request.pageUrl, request.generation,
                        requests = listOf(
                            CapturedRequest(
                                media, mimeType = "video/mp4",
                                context = BrowserRequestContext(page, null, null),
                            ),
                        ),
                        playingMediaUrl = media,
                        authorizedPlayback = authorized,
                    ),
                )
            },
            policy = MasterPolicy(enabled = true),
            modules = modules,
            contracts = contracts,
        )
        val bridge = AndroidBrowserMasterFallback(
            capture, sites, engine, MergeSupport { canMerge },
            requestFactory = { reason, now, expected ->
                factories.incrementAndGet()
                if (noRequest) null else MasterRequest(page, scope.generation, now, reason, expected)
            },
            masterFirstSite = masterFirstSite,
            youtubeRouteB = youtubeRouteB,
        )
    }

    /** A site module that claims the fixture's TikTok pages and answers one HLS row. */
    private class FixtureModule(private val onExtract: () -> Unit = {}) : MasterSiteModule {
        val calls = AtomicInteger()
        override val siteId = "tiktok"
        override fun identify(pageUrl: String): SitePageIdentity? {
            if (!pageUrl.startsWith("https://www.tiktok.com/@fixture/video/")) return null
            return SitePageIdentity(siteId, pageUrl.substringAfterLast('/'), pageUrl)
        }
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            calls.incrementAndGet()
            onExtract()
            return SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        request.identity.canonicalPageUrl, ROW_URL, setOf(CandidateSource.REQUEST),
                        MediaKind.HLS, mimeType = "application/vnd.apple.mpegurl",
                        videoId = "tiktok:${request.identity.contentId}",
                    ),
                ),
            )
        }
        companion object {
            const val ROW_URL = "https://cdn.test/h/index.m3u8"
        }
    }

    private class FixtureExtractor(
        override val id: String = "tiktok",
        private val prefix: String = "https://www.tiktok.com/@fixture/video/",
    ) : SiteExtractor {
        override val displayName = "Fixture"
        override fun identify(pageUrl: String): SitePageIdentity? {
            if (!pageUrl.startsWith(prefix)) return null
            return SitePageIdentity(id, pageUrl.substringAfterLast('/'), pageUrl)
        }
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
    }

    private companion object {
        const val YOUTUBE_PREFIX = "https://www.youtube.com/watch/"
        const val YOUTUBE_PAGE = "${YOUTUBE_PREFIX}Yft0Fixture"
        const val VIMEO_CONFIG = "{\"video\":{\"id\":123456789,\"title\":\"Fixture\"}," +
            "\"request\":{\"files\":{\"progressive\":[{\"url\":\"https://cdn.test/v720.mp4\"," +
            "\"mime\":\"video/mp4\",\"width\":1280,\"height\":720}]}}}"
    }
}
