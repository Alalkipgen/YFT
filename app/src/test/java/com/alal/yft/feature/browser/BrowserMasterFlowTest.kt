package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.master.AndroidBrowserMasterFallback
import com.alal.yft.detection.master.BrowserMasterFallback
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.modules.MasterSiteModule
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserMasterFlowTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val page = "https://page.test/watch"
    private val site = "https://www.tiktok.com/@fixture/video/11111"
    private val media = "https://cdn.test/main.mp4"
    private val youtube = "https://www.youtube.com/watch?v=$YOUTUBE_ID"

    @Test
    fun primarySuccessNeverCallsTheBackup() = runTest {
        val adapter = FixtureExtractor(succeeds = true)
        val backup = FakeBackup()
        val vm = viewModel(backup, adapter)
        vm.onPageStarted(site)
        vm.onPageFinished(site, "Fixture")
        runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(0, backup.calls)
    }

    /** Phase 1.4 item 2: the h-only APK's site goes through the backup even when it was found. */
    @Test
    fun aFoundPageOfTheMasterFirstSiteRunsTheBackupOnce() = runTest {
        val adapter = FixtureExtractor(succeeds = true)
        val backup = FakeBackup(masterFirstSite = site)
        val vm = viewModel(backup, adapter)
        vm.onPageStarted(site)
        vm.onPageFinished(site, "Fixture")
        runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(1, backup.calls)
        assertFalse(backup.onDemand)
    }

    @Test
    fun recoverablePrimaryFailureRunsTheBackupOnceWithoutRecursiveExtraction() = runTest {
        val adapter = FixtureExtractor()
        val backup = FakeBackup()
        val vm = viewModel(backup, adapter)
        vm.onPageStarted(site)
        vm.onPageFinished(site, "Fixture")
        runCurrent()
        assertEquals(1, adapter.calls)
        assertEquals(1, backup.calls)
    }

    /**
     * Phase 1.4 item 7 (YouTube routing B) R1: a route-B lookup runs main's adapter and the backup
     * in one record of its own — main's request lands there — and Try again gets a new record.
     */
    @Test
    fun aRouteBLookupRunsMainAndTheBackupInARecordOfItsOwn() = runTest {
        val adapter = FixtureExtractor(http = LookupRecorder(Answering()))
        val backup = FakeBackup(routeB = true, answer = { failedOutcome() })
        val vm = viewModel(backup, adapter)
        vm.onPageStarted(site)
        vm.onPageFinished(site, "Fixture")
        runCurrent()
        val first = backup.records.single()
        assertEquals(listOf<LookupRecord?>(first), adapter.records)
        assertEquals(listOf<LookupRecord?>(first), backup.seen)
        assertEquals(1, first.mainAsks)

        vm.retrySiteLookup()
        runCurrent()
        assertEquals(2, adapter.calls)
        assertEquals(2, backup.records.size)
        val second = backup.records.last()
        assertNotSame(first, second)
        assertEquals(listOf<LookupRecord?>(first, second), adapter.records)
        assertEquals(listOf<LookupRecord?>(first, second), backup.seen)
        assertEquals(1, first.mainAsks)
        assertEquals(1, second.mainAsks)
    }

    /** Item 7 R1: every other build's lookup carries no record; the recorder only passes through. */
    @Test
    fun withoutRouteBNoLookupCarriesARecord() = runTest {
        val http = Answering()
        val adapter = FixtureExtractor(http = LookupRecorder(http))
        val backup = FakeBackup(answer = { failedOutcome() })
        val vm = viewModel(backup, adapter)
        vm.onPageStarted(site)
        vm.onPageFinished(site, "Fixture")
        runCurrent()
        assertEquals(listOf<LookupRecord?>(null), adapter.records)
        assertEquals(listOf<LookupRecord?>(null), backup.seen)
        assertEquals(1, http.asked)
    }

    /**
     * Item 7 R3: a route-B page lookup is main, then Master — after main's RESPONSE_CHANGED the
     * module answers inside the lookup's record (main's request replayed, not asked again) and
     * its row is the page's video; after a bot check main's failure stays and Master never runs.
     */
    @Test
    fun aRouteBYouTubeLookupShowsMastersRowOnlyAfterARecoverableFailure() = runTest {
        for (reason in listOf(SiteExtractionFailure.RESPONSE_CHANGED, SiteExtractionFailure.BOT_CHECK)) {
            val http = Answering()
            val adapter = YouTubeAdapter(LookupRecorder(http), reason)
            val sites = SiteAdapterCoordinator(SiteExtractorRegistry(listOf(adapter)), MergeSupport { true })
            val capture = WebViewPlaybackCapture(true)
            val scope = capture.session.navigate(youtube)!!
            val module = YouTubeModule(RouteBHttpClient(http))
            val engine = MasterFallbackEngine(
                MasterMediaValidator { found, _ -> ValidationResult.Valid(found) },
                policy = MasterPolicy(enabled = true, youtubeRouteB = true),
                modules = listOf(module),
            )
            val bridge = AndroidBrowserMasterFallback(
                capture, sites, engine, MergeSupport { true },
                requestFactory = { failure, now, expected ->
                    MasterRequest(youtube, scope.generation, now, failure, expected)
                },
                youtubeRouteB = true,
            )
            val store = DetectedMediaStore()
            val vm = BrowserViewModel(OkHttpClient(), sites, store, masterFallback = bridge)
            vm.onPageStarted(youtube)
            vm.onPageFinished(youtube, "Fixture")
            settle { adapter.calls == 1 && !vm.uiState.value.pageLookupRunning }

            assertEquals(reason.name, 1, http.asked) // main's request; Master's was replayed
            if (reason == SiteExtractionFailure.RESPONSE_CHANGED) {
                assertEquals(1, module.calls)
                assertEquals(1, module.runs.single()!!.replayed)
                assertNull(store.lookup.value)
                assertTrue(vm.openPageVideo())
                assertEquals(listOf(ROW), store.selection.value!!.candidates.map { it.mediaUrl })
            } else {
                assertEquals(0, module.calls)
                assertNotNull(store.lookup.value!!.failure)
                assertNull(store.selection.value)
            }
        }
    }

    /**
     * Item 7 R4: the route-B trial APK — main finds the YouTube page, its rows are set aside as
     * main's RESPONSE_CHANGED after main asked, Master's module answers from the lookup's record
     * (main's request replayed, nothing new) and the browser names route B with its counts;
     * Master's row is the page's video. Route B without the trial keeps main's row, no notice.
     */
    @Test
    fun theRouteBTrialShowsMastersRowAndRouteBsCounts() = runTest {
        for (trial in listOf(true, false)) {
            val http = Answering()
            val adapter = YouTubeAdapter(LookupRecorder(http), reason = null)
            val sites = SiteAdapterCoordinator(SiteExtractorRegistry(listOf(adapter)), MergeSupport { true })
            val capture = WebViewPlaybackCapture(true)
            val scope = capture.session.navigate(youtube)!!
            val module = YouTubeModule(RouteBHttpClient(http))
            val engine = MasterFallbackEngine(
                MasterMediaValidator { found, _ -> ValidationResult.Valid(found) },
                policy = MasterPolicy(enabled = true, youtubeRouteB = true),
                modules = listOf(module),
            )
            val bridge = AndroidBrowserMasterFallback(
                capture, sites, engine, MergeSupport { true },
                requestFactory = { failure, now, expected ->
                    MasterRequest(youtube, scope.generation, now, failure, expected)
                },
                youtubeRouteB = true,
                routeBTrial = trial,
            )
            val store = DetectedMediaStore()
            val vm = BrowserViewModel(OkHttpClient(), sites, store, masterFallback = bridge)
            vm.onPageStarted(youtube)
            vm.onPageFinished(youtube, "Fixture")
            settle { adapter.calls == 1 && !vm.uiState.value.pageLookupRunning }

            assertEquals("trial $trial", 1, http.asked) // main's request; Master's was replayed
            val notice = vm.uiState.value.siteNotice
            assertTrue("trial $trial", vm.openPageVideo())
            val rows = store.selection.value!!.candidates.map { it.mediaUrl }
            if (trial) {
                assertEquals(1, module.calls)
                assertEquals(1, module.runs.single()!!.replayed)
                assertEquals(listOf(ROW), rows)
                assertEquals("master: route B after RESPONSE_CHANGED; replayed 1; new 0", notice)
            } else {
                assertEquals(0, module.calls)
                assertEquals(listOf(MAIN_ROW), rows)
                assertNull(notice)
            }
        }
    }

    /**
     * Item 7 F5 (D7 run 8: V2, V3): the route-B trial on a page whose visionOS answer is a bot
     * check. Main asks the player endpoint, then reads the watch page with the tab's cookie and
     * finds its row (set aside by the trial); Master's module asks the same player request and the
     * same page without the cookie — both answered from the lookup's record (replayed 2, new 0),
     * nothing goes out again and only main's page carried the cookie. Master read main's page, and
     * its row reaches the sheet with route B's counts.
     */
    @Test
    fun theRouteBTrialOnAV2LikePageShowsMastersRowFromMainsWatchPage() = runTest {
        val http = Answering()
        val adapter = YouTubeAdapter(LookupRecorder(http), reason = null, pageHeaders = MAIN_PAGE_HEADERS)
        val sites = SiteAdapterCoordinator(SiteExtractorRegistry(listOf(adapter)), MergeSupport { true })
        val capture = WebViewPlaybackCapture(true)
        val scope = capture.session.navigate(youtube)!!
        val module = YouTubeModule(RouteBHttpClient(http), pageHeaders = MASTER_PAGE_HEADERS)
        val engine = MasterFallbackEngine(
            MasterMediaValidator { found, _ -> ValidationResult.Valid(found) },
            policy = MasterPolicy(enabled = true, youtubeRouteB = true),
            modules = listOf(module),
        )
        val bridge = AndroidBrowserMasterFallback(
            capture, sites, engine, MergeSupport { true },
            requestFactory = { failure, now, expected ->
                MasterRequest(youtube, scope.generation, now, failure, expected)
            },
            youtubeRouteB = true,
            routeBTrial = true,
        )
        val store = DetectedMediaStore()
        val vm = BrowserViewModel(OkHttpClient(), sites, store, masterFallback = bridge)
        vm.onPageStarted(youtube)
        vm.onPageFinished(youtube, "Fixture")
        settle { adapter.calls == 1 && !vm.uiState.value.pageLookupRunning }

        assertEquals(2, http.asked) // main's player request and page; Master's were replayed
        assertEquals(1, http.withCookie) // main's page only
        assertEquals(1, module.calls)
        assertEquals(2, module.runs.single()!!.replayed)
        assertEquals(0, module.runs.single()!!.newRequests)
        assertEquals(listOf<String?>(PAGE_BODY), module.pages) // Master read main's page
        val notice = vm.uiState.value.siteNotice
        assertTrue(vm.openPageVideo())
        assertEquals(listOf(ROW), store.selection.value!!.candidates.map { it.mediaUrl })
        assertEquals("master: route B after RESPONSE_CHANGED; replayed 2; new 0", notice)
    }

    /**
     * Item 7 F4 (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8 (1)): a real failure midway, the trial
     * off. Main asks the player endpoint — and, as after a bot-checked visionOS answer, reads the
     * watch page with the tab's cookie — then fails with each recoverable reason (§2). Master's
     * module asks the same requests without the cookie: answered from the lookup's record (replayed
     * 1 or 2, new 0), nothing goes out again and only main's page carried the cookie. Master's row
     * reaches the sheet with route B's notice and counts.
     */
    @Test
    fun aRecoverableFailureMidwayShowsMastersRowFromMainsReplayedAnswers() = runTest {
        for (reason in TerminalRules.ROUTE_B_RECOVERABLE) for (page in listOf(false, true)) {
            val name = "$reason, page $page"
            val http = Answering()
            val browser = RouteBBrowser(
                YouTubeAdapter(LookupRecorder(http), reason, pageHeaders = MAIN_PAGE_HEADERS.takeIf { page }),
                YouTubeModule(RouteBHttpClient(http), pageHeaders = MASTER_PAGE_HEADERS.takeIf { page }),
            )
            browser.open()
            settle { browser.adapter.calls == 1 && !browser.vm.uiState.value.pageLookupRunning }

            val asked = if (page) 2 else 1
            assertEquals(name, asked, http.asked) // main's requests; Master's were replayed
            assertEquals(name, if (page) 1 else 0, http.withCookie) // main's page only
            assertEquals(name, reason, (browser.bridge.primaries.single() as SiteAdapterOutcome.Failed).reason)
            val run = browser.module.runs.single()!!
            assertSame(name, browser.adapter.records.single(), run.record)
            assertEquals(name, asked, run.replayed)
            assertEquals(name, 0, run.newRequests)
            assertEquals(name, if (page) listOf<String?>(PAGE_BODY) else emptyList<String?>(), browser.module.pages)
            assertNull(name, browser.store.lookup.value)
            assertEquals(
                name,
                "master: route B after ${reason.name}; replayed $asked; new 0",
                browser.vm.uiState.value.siteNotice,
            )
            assertTrue(name, browser.vm.openPageVideo())
            assertEquals(name, listOf(ROW), browser.store.selection.value!!.candidates.map { it.mediaUrl })
        }
    }

    /**
     * Item 7 F4 (2): when Master finds nothing after main's failure midway, main's own failure
     * stays — its message, notice, Try again and Details — with Master's details after main's
     * (route B's line first, Master's own reason last), compared with the same lookup in a build
     * without route B (P12: main's answer itself, no record, no line, Master never asked). Try
     * again asks afresh in a new record and then shows Master's row.
     */
    @Test
    fun whenMasterFindsNothingMainsFailureStaysWithMastersDetailsAndTryAgain() = runTest {
        val nothing = SiteExtractionResult.Failure(
            SiteExtractionFailure.BOT_CHECK, details = listOf("client VISIONOS: LOGIN_REQUIRED"),
        )
        fun browser(routeB: Boolean, http: Answering) = RouteBBrowser(
            YouTubeAdapter(LookupRecorder(http), SiteExtractionFailure.RESPONSE_CHANGED, pageHeaders = MAIN_PAGE_HEADERS),
            YouTubeModule(
                RouteBHttpClient(http), pageHeaders = MASTER_PAGE_HEADERS,
                answer = { call -> nothing.takeIf { call == 1 } },
            ),
            routeB = routeB,
        )
        val offHttp = Answering()
        val off = browser(routeB = false, offHttp)
        off.open()
        settle { off.adapter.calls == 1 && !off.vm.uiState.value.pageLookupRunning }
        val today = off.store.lookup.value!!
        assertSame(off.bridge.primaries.single(), off.bridge.answers.single())
        assertEquals(listOf<LookupRecord?>(null), off.adapter.records)
        assertEquals(0, off.module.calls)
        assertEquals(2, offHttp.asked)
        assertTrue(today.canRetry)
        assertTrue(today.details.none { it.startsWith("master: route B") })

        val http = Answering()
        val on = browser(routeB = true, http)
        on.open()
        settle { on.adapter.calls == 1 && !on.vm.uiState.value.pageLookupRunning }
        val masters = listOf(
            "master: route B after RESPONSE_CHANGED; replayed 2; new 0",
            "client VISIONOS: LOGIN_REQUIRED",
            "master: youtube module: BOT_CHECK",
        )
        val primary = on.bridge.primaries.single() as SiteAdapterOutcome.Failed
        assertEquals(primary.copy(details = primary.details + masters), on.bridge.answers.single())
        val lookup = on.store.lookup.value!!
        assertEquals(today.failure, lookup.failure)
        assertEquals(today.canRetry, lookup.canRetry)
        assertEquals(today.details + masters, lookup.details)
        assertNotNull(on.vm.uiState.value.siteNotice)
        assertEquals(off.vm.uiState.value.siteNotice, on.vm.uiState.value.siteNotice)
        assertNull(on.store.selection.value)
        assertEquals(1, on.module.calls)
        assertEquals(2, http.asked) // nothing of Master's went out

        on.vm.retrySiteLookup()
        settle { on.adapter.calls == 2 && !on.vm.uiState.value.pageLookupRunning }
        assertEquals(2, on.module.calls)
        assertEquals(2, on.adapter.records.size)
        assertNotSame(on.adapter.records[0], on.adapter.records[1])
        assertSame(on.adapter.records[1], on.module.runs[1]!!.record)
        assertEquals(4, http.asked) // main asked afresh; Master's were replayed again
        assertEquals("master: route B after RESPONSE_CHANGED; replayed 2; new 0", on.vm.uiState.value.siteNotice)
        assertTrue(on.vm.openPageVideo())
        assertEquals(listOf(ROW), on.store.selection.value!!.candidates.map { it.mediaUrl })
    }

    /**
     * Item 7 F4 (3): main's final reasons never reach Master — main's failure stays with route B's
     * line saying why (`master: route B not run: <reason> is final`), Master's module never runs
     * and nothing of Master's goes out (main's 429 answers RATE_LIMITED, final). A 429 midway in a
     * lookup main then failed recoverably: Master's identical request is answered with main's 429
     * (replayed), its other request is refused, nothing new goes out and main's failure stays.
     * Main's success: untouched (main's row, no notice, the backup never asked).
     */
    @Test
    fun noRouteBOnMainsFinalReasonsNothingNewAfterMains429AndMainsSuccessUntouched() = runTest {
        val final = SiteExtractionFailure.entries - TerminalRules.ROUTE_B_RECOVERABLE - SiteExtractionFailure.UNSUPPORTED_URL
        assertEquals(TerminalRules.NEVER_FALLBACK + TerminalRules.BROWSER_REQUIRED, final.toSet())
        for (reason in final) {
            val http = Answering(tooMany = reason == SiteExtractionFailure.RATE_LIMITED)
            val browser = RouteBBrowser(
                YouTubeAdapter(LookupRecorder(http), reason, pageHeaders = MAIN_PAGE_HEADERS),
                YouTubeModule(RouteBHttpClient(http), pageHeaders = MASTER_PAGE_HEADERS),
            )
            browser.open()
            settle { browser.adapter.calls == 1 && !browser.vm.uiState.value.pageLookupRunning }

            val line = "master: route B not run: ${reason.name} is final"
            val primary = browser.bridge.primaries.single() as SiteAdapterOutcome.Failed
            assertEquals(reason.name, primary.copy(details = primary.details + line), browser.bridge.answers.single())
            assertEquals(reason.name, line, browser.store.lookup.value!!.details.last())
            assertEquals(reason.name, 0, browser.module.calls)
            assertEquals(reason.name, 2, http.asked) // main's only
            assertNull(reason.name, browser.store.selection.value)
        }

        val http = Answering(tooMany = true)
        val midway = RouteBBrowser(
            YouTubeAdapter(LookupRecorder(http), SiteExtractionFailure.RESPONSE_CHANGED),
            YouTubeModule(
                RouteBHttpClient(http), otherClient = true,
                answer = { SiteExtractionResult.Failure(SiteExtractionFailure.RATE_LIMITED) },
            ),
        )
        midway.open()
        settle { midway.adapter.calls == 1 && !midway.vm.uiState.value.pageLookupRunning }
        assertTrue(midway.adapter.records.single()!!.rateLimited)
        val run = midway.module.runs.single()!!
        assertEquals(1, run.replayed) // main's 429
        assertEquals(1, run.refused) // the other request
        assertEquals(0, run.newRequests)
        assertEquals(1, http.asked) // main's request only
        val lines = listOf("master: route B after RESPONSE_CHANGED; replayed 1; new 0; refused 1", "master: youtube module: RATE_LIMITED")
        val primary = midway.bridge.primaries.single() as SiteAdapterOutcome.Failed
        assertEquals(primary.copy(details = primary.details + lines), midway.bridge.answers.single())
        assertEquals(primary.details + lines, midway.store.lookup.value!!.details)
        assertTrue(midway.store.lookup.value!!.canRetry)
        assertNull(midway.store.selection.value)

        val foundHttp = Answering()
        val found = RouteBBrowser(
            YouTubeAdapter(LookupRecorder(foundHttp), reason = null, pageHeaders = MAIN_PAGE_HEADERS),
            YouTubeModule(RouteBHttpClient(foundHttp), pageHeaders = MASTER_PAGE_HEADERS),
        )
        found.open()
        settle { found.adapter.calls == 1 && !found.vm.uiState.value.pageLookupRunning }
        assertTrue(found.bridge.primaries.isEmpty())
        assertEquals(0, found.module.calls)
        assertEquals(2, foundHttp.asked)
        assertNull(found.vm.uiState.value.siteNotice)
        assertTrue(found.vm.openPageVideo())
        assertEquals(listOf(MAIN_ROW), found.store.selection.value!!.candidates.map { it.mediaUrl })
    }

    @Test
    fun ordinaryGenericPageDoesNotStartTheBackupAutomatically() = runTest {
        val backup = FakeBackup()
        val vm = viewModel(backup)
        vm.onPageStarted(page)
        vm.onPageFinished(page, "Fixture")
        runCurrent()
        assertEquals(0, backup.calls)
    }

    @Test
    fun missingGenericVideoUsesOnDemandCaptureAndOpensItsSheet() = runTest {
        val backup = FakeBackup()
        val store = DetectedMediaStore()
        val vm = viewModel(backup, store = store)
        vm.onPageStarted(page)
        vm.onPageFinished(page, "Fixture")
        runCurrent()
        vm.mainVideoScript()
        vm.onPlayingVideoResult("null")
        runCurrent()
        assertEquals(1, backup.calls)
        assertTrue(backup.onDemand)
        assertEquals(media, store.selection.value!!.candidates.single().mediaUrl)
        assertNull(store.lookup.value)
        assertFalse(vm.uiState.value.pageLookupRunning)
    }

    @Test
    fun existingGenericVideoIsLeftUntouched() = runTest {
        val backup = FakeBackup()
        val store = DetectedMediaStore()
        val vm = viewModel(backup, store = store)
        vm.onPageStarted(page)
        vm.onPageFinished(page, "Fixture")
        vm.onDomProbeResult(
            page,
            """[{"url":"$media","type":"video/mp4","title":"Fixture"}]""",
        )
        advanceTimeBy(600)
        runCurrent()
        vm.mainVideoScript()
        vm.onPlayingVideoResult("null")
        runCurrent()
        assertEquals(0, backup.calls)
        assertNotNull(store.selection.value)
    }

    @Test
    fun navigationCancelsAnOldGenericCaptureAndClearsTheSpinner() = runTest {
        val pending = CompletableDeferred<SiteAdapterOutcome>()
        val backup = FakeBackup { pending.await() }
        val store = DetectedMediaStore()
        val vm = viewModel(backup, store = store)
        vm.onPageStarted(page)
        vm.mainVideoScript()
        vm.onPlayingVideoResult("null")
        runCurrent()
        assertTrue(vm.uiState.value.pageLookupRunning)
        vm.onPageStarted("$page/next")
        runCurrent()
        assertEquals(1, backup.cancelled)
        assertNull(store.selection.value)
        assertNull(store.lookup.value)
        assertFalse(vm.uiState.value.pageLookupRunning)
    }

    @Test
    fun closingTheWaitingSheetCancelsCaptureAndClearsItsLookup() = runTest {
        val pending = CompletableDeferred<SiteAdapterOutcome>()
        val backup = FakeBackup { pending.await() }
        val store = DetectedMediaStore()
        val vm = viewModel(backup, store = store)
        vm.onPageStarted(page)
        vm.mainVideoScript()
        vm.onPlayingVideoResult("null")
        runCurrent()
        store.closeLookup(store.lookup.value!!.key)
        runCurrent()
        assertEquals(1, backup.cancelled)
        assertNull(store.lookup.value)
        assertFalse(vm.uiState.value.pageLookupRunning)
    }

    @Test
    fun tryAgainInGenericCaptureSheetUsesOneNewOnDemandCall() = runTest {
        var attempt = 0
        val backup = FakeBackup {
            if (++attempt == 1) SiteAdapterOutcome.Failed(
                "master", SiteExtractionFailure.NO_MEDIA_FOUND, "Play first", true,
            ) else detected()
        }
        val store = DetectedMediaStore()
        val vm = viewModel(backup, store = store)
        vm.onPageStarted(page)
        vm.mainVideoScript()
        vm.onPlayingVideoResult("null")
        runCurrent()
        store.retryLookup(store.lookup.value!!.key)
        runCurrent()
        assertEquals(2, backup.calls)
        assertNotNull(store.selection.value)
        assertNull(store.lookup.value)
    }

    @Test
    fun explicitlyDisabledSiteCannotStartOnDemandBackup() = runTest {
        val backup = FakeBackup()
        val registry = SiteExtractorRegistry(
            listOf(FixtureExtractor()), SiteAdapterFlags { false },
        )
        val vm = BrowserViewModel(
            OkHttpClient(), SiteAdapterCoordinator(registry, MergeSupport { true }),
            masterFallback = backup,
        )
        vm.onPageStarted(site)
        vm.mainVideoScript()
        vm.onPlayingVideoResult("null")
        runCurrent()
        assertEquals(0, backup.calls)
    }

    /**
     * Item 7 R3: the backup's engine runs on a background thread (Dispatchers.Default); the test
     * thread runs the browser's coroutines until [done], for at most a few seconds.
     */
    private fun TestScope.settle(done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            Thread.sleep(10)
        }
        fail("the lookup did not finish")
    }

    private fun viewModel(
        backup: FakeBackup,
        adapter: FixtureExtractor? = null,
        store: DetectedMediaStore = DetectedMediaStore(),
    ) = BrowserViewModel(
        OkHttpClient(),
        SiteAdapterCoordinator(
            SiteExtractorRegistry(listOfNotNull(adapter)), MergeSupport { true },
        ),
        store,
        masterFallback = backup,
    )

    private fun detected() = SiteAdapterOutcome.Detected(
        "master",
        listOf(
            MediaCandidate(
                page, media, setOf(CandidateSource.REQUEST), MediaKind.DIRECT,
                mimeType = "video/mp4", confidence = CandidateConfidence.HIGH,
                contentLengthBytes = 100_000,
            ),
        ),
    )

    private fun failedOutcome() = SiteAdapterOutcome.Failed(
        "tiktok", SiteExtractionFailure.NO_MEDIA_FOUND, "Fixture failure", true,
    )

    private inner class FakeBackup(
        private val masterFirstSite: String? = null,
        private val routeB: Boolean = false,
        private val answer: suspend () -> SiteAdapterOutcome = { detected() },
    ) : BrowserMasterFallback {
        override val enabled = true
        override fun masterFirst(lookupUrl: String): Boolean = lookupUrl == masterFirstSite
        var calls = 0
        var cancelled = 0
        var onDemand = false
        val records = mutableListOf<LookupRecord>()
        val seen = mutableListOf<LookupRecord?>()
        override fun lookupContext(lookupUrl: String): CoroutineContext =
            if (routeB) LookupRecord().also { records += it } else EmptyCoroutineContext
        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome {
            calls++
            onDemand = genericOnDemand
            seen += coroutineContext[LookupRecord]
            return try { answer() } catch (failure: CancellationException) {
                cancelled++
                throw failure
            }
        }
    }

    /**
     * Item 7 F4: one route-B YouTube browser as the app builds it — main's adapter in the
     * coordinator, Master's engine with the module, the bridge (route B as [routeB], the trial
     * off) as the browser holds it ([Watching]), the store and the view model.
     */
    private inner class RouteBBrowser(val adapter: YouTubeAdapter, val module: YouTubeModule, routeB: Boolean = true) {
        val sites = SiteAdapterCoordinator(SiteExtractorRegistry(listOf(adapter)), MergeSupport { true })
        val capture = WebViewPlaybackCapture(true)
        val scope = capture.session.navigate(youtube)!!
        val engine = MasterFallbackEngine(
            MasterMediaValidator { found, _ -> ValidationResult.Valid(found) },
            policy = MasterPolicy(enabled = true, youtubeRouteB = routeB),
            modules = listOf(module),
        )
        val bridge = Watching(
            AndroidBrowserMasterFallback(
                capture, sites, engine, MergeSupport { true },
                requestFactory = { failure, now, expected ->
                    MasterRequest(youtube, scope.generation, now, failure, expected)
                },
                youtubeRouteB = routeB,
            ),
        )
        val store = DetectedMediaStore()
        val vm = BrowserViewModel(OkHttpClient(), sites, store, masterFallback = bridge)

        fun open() {
            vm.onPageStarted(youtube)
            vm.onPageFinished(youtube, "Fixture")
        }
    }

    /** Item 7 F4: the bridge, noting main's answer it was given and its own answer. */
    private class Watching(private val inner: BrowserMasterFallback) : BrowserMasterFallback by inner {
        val primaries = mutableListOf<SiteAdapterOutcome>()
        val answers = mutableListOf<SiteAdapterOutcome>()
        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome {
            primaries += primary
            return inner.recover(lookupUrl, primary, nowEpochMs, genericOnDemand).also { answers += it }
        }
    }

    /**
     * Answers every request (no live call); F5: the watch page answers [PAGE_BODY]. F4: with
     * [tooMany] the player endpoint answers 429.
     */
    private class Answering(private val tooMany: Boolean = false) : ExtractorHttpClient {
        var asked = 0
        var withCookie = 0
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked++
            if (headers.keys.any { it.equals("Cookie", ignoreCase = true) }) withCookie++
            if (url == WATCH_PAGE) return ExtractorHttpResult.Success(200, PAGE_BODY, url, "text/html")
            if (tooMany && url == PLAYER) return ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
            return ExtractorHttpResult.Success(200, "{}", url, "application/json")
        }
        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = get(url, headers, maxBodyBytes)
    }

    /**
     * Item 7 R3: main's YouTube adapter stand-in — asks the player endpoint once, then fails with
     * [reason] (R4: null = finds its own row, [MAIN_ROW]). F5: with [pageHeaders] it then reads
     * the watch page with them (the tab's cookie), as after a bot-checked visionOS answer. F4: it
     * notes the lookup record it ran in ([records]).
     */
    private inner class YouTubeAdapter(
        private val http: ExtractorHttpClient,
        private val reason: SiteExtractionFailure?,
        private val pageHeaders: Map<String, String>? = null,
    ) : SiteExtractor {
        override val id = "youtube"
        override val displayName = "Fixture YouTube"
        var calls = 0
        val records = mutableListOf<LookupRecord?>()
        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == youtube) SitePageIdentity(id, YOUTUBE_ID, pageUrl) else null
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            calls++
            records += coroutineContext[LookupRecord]
            http.postJson(PLAYER, PLAYER_BODY, emptyMap(), MAX_BODY)
            pageHeaders?.let { http.get(WATCH_PAGE, it, MAX_BODY) }
            return reason?.let { SiteExtractionResult.Failure(it) } ?: SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        youtube, MAIN_ROW, setOf(CandidateSource.REQUEST), MediaKind.DIRECT,
                        mimeType = "video/mp4", confidence = CandidateConfidence.HIGH,
                        contentLengthBytes = 100_000, videoId = "youtube:$YOUTUBE_ID",
                    ),
                ),
            )
        }
    }

    /**
     * Item 7 R3: Master's YouTube module stand-in — main's request again, through route B. F5: with
     * [pageHeaders] it then reads the watch page with them (no cookie) and keeps what it got. F4:
     * with [otherClient] it also asks another client's player request; [answer] (by call number)
     * replaces its row, as when Master finds nothing.
     */
    private inner class YouTubeModule(
        private val http: ExtractorHttpClient,
        private val pageHeaders: Map<String, String>? = null,
        private val otherClient: Boolean = false,
        private val answer: (Int) -> SiteExtractionResult? = { null },
    ) : MasterSiteModule {
        var calls = 0
        val runs = mutableListOf<RouteBLookup?>()
        val pages = mutableListOf<String?>()
        override val siteId = "youtube"
        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == youtube) SitePageIdentity(siteId, YOUTUBE_ID, pageUrl) else null
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            calls++
            runs += coroutineContext[RouteBLookup]
            http.postJson(PLAYER, PLAYER_BODY, emptyMap(), MAX_BODY)
            pageHeaders?.let { pages += (http.get(WATCH_PAGE, it, MAX_BODY) as? ExtractorHttpResult.Success)?.body }
            if (otherClient) http.postJson(PLAYER, OTHER_BODY, emptyMap(), MAX_BODY)
            answer(calls)?.let { return it }
            return SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        youtube, ROW, setOf(CandidateSource.REQUEST), MediaKind.DIRECT,
                        mimeType = "video/mp4", confidence = CandidateConfidence.HIGH,
                        contentLengthBytes = 100_000, videoId = "youtube:$YOUTUBE_ID",
                    ),
                ),
            )
        }
    }

    private inner class FixtureExtractor(
        private val succeeds: Boolean = false,
        private val http: ExtractorHttpClient? = null,
    ) : SiteExtractor {
        override val id = "tiktok"
        override val displayName = "Fixture"
        var calls = 0
        val records = mutableListOf<LookupRecord?>()
        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == site) SitePageIdentity(id, "11111", pageUrl) else null
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            calls++
            records += coroutineContext[LookupRecord]
            http?.get(request.identity.canonicalPageUrl)
            return if (succeeds) {
                SiteExtractionResult.Success(
                    detected().candidates.map { it.copy(videoId = "tiktok:11111") },
                )
            } else SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }
    }

    private companion object {
        const val YOUTUBE_ID = "Yft0Fixture"
        const val PLAYER = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val PLAYER_BODY = "{\"videoId\":\"Yft0Fixture\"}"

        /** F4: another client's player request for the same video (not one of main's). */
        const val OTHER_BODY = "{\"videoId\":\"Yft0Fixture\",\"context\":{\"client\":{\"clientName\":\"OTHER\"}}}"
        const val MAX_BODY = 2L * 1024 * 1024
        const val ROW = "https://cdn.test/yt/master.mp4"
        const val MAIN_ROW = "https://cdn.test/yt/main.mp4"

        /** F5: the looked-up video's watch page as main's adapter and Master's module read it. */
        const val WATCH_PAGE = "https://www.youtube.com/watch?v=Yft0Fixture&bpctr=9999999999&has_verified=1"
        const val PAGE_BODY = "<html>main's watch page</html>"
        val MAIN_PAGE_HEADERS = linkedMapOf("User-Agent" to "YftTest/1.0", "Cookie" to "SID=fixture", "Accept-Language" to "en-US,en;q=0.9")
        val MASTER_PAGE_HEADERS = linkedMapOf("User-Agent" to "YftTest/1.0", "Accept-Language" to "en-US,en;q=0.9")
    }
}
