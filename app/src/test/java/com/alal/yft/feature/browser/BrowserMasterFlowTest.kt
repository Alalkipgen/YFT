package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.master.BrowserMasterFallback
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
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

    private inner class FakeBackup(
        private val answer: suspend () -> SiteAdapterOutcome = { detected() },
    ) : BrowserMasterFallback {
        override val enabled = true
        var calls = 0
        var cancelled = 0
        var onDemand = false
        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome {
            calls++
            onDemand = genericOnDemand
            return try { answer() } catch (failure: CancellationException) {
                cancelled++
                throw failure
            }
        }
    }

    private inner class FixtureExtractor(private val succeeds: Boolean = false) : SiteExtractor {
        override val id = "tiktok"
        override val displayName = "Fixture"
        var calls = 0
        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == site) SitePageIdentity(id, "11111", pageUrl) else null
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            calls++
            return if (succeeds) {
                SiteExtractionResult.Success(
                    detected().candidates.map { it.copy(videoId = "tiktok:11111") },
                )
            } else SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }
    }
}
