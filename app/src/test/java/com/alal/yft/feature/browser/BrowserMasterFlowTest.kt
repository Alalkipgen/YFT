package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
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
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
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

    /** Answers every request (no live call). */
    private class Answering : ExtractorHttpClient {
        var asked = 0
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked++
            return ExtractorHttpResult.Success(200, "{}", url, "application/json")
        }
        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = get(url, headers, maxBodyBytes)
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
}
