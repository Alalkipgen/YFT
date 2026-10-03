package com.alal.yft.feature.browser

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addressInputIsHttpsNormalizedAndInvalidSchemesStayLocal() {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())
        viewModel.onAddressChanged("example.test/watch")

        assertEquals("https://example.test/watch", viewModel.addressForLoading())

        viewModel.onAddressChanged("http://example.test")
        assertNull(viewModel.addressForLoading())
        assertEquals("Only HTTPS pages are supported", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun domCandidatesAppearAfterDebounceAndClearImmediatelyOnNavigation() = runTest {
        val selectionStore = PreviewSelectionStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), selectionStore)
        val firstPage = "https://example.test/one"
        viewModel.onPageStarted(firstPage)
        viewModel.onDomProbeResult(
            pageUrl = firstPage,
            result = """[{"url":"https://cdn.test/video.mp4","type":"video/mp4",""" +
                """"title":"Fixture"}]""",
        )

        advanceTimeBy(250)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.candidates.size)
        assertEquals("Fixture", viewModel.uiState.value.candidates.single().title)
        val candidate = viewModel.uiState.value.candidates.single()
        assertTrue(viewModel.selectForPreview(candidate))
        assertEquals(candidate, selectionStore.selection.value)

        viewModel.onPageStarted("https://example.test/two")

        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertFalse(viewModel.selectForPreview(candidate))
    }

    @Test
    fun mainFrameErrorsDoNotExposeWebViewDescriptions() {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())
        val page = "https://example.test/watch"
        viewModel.onPageStarted(page)

        viewModel.onMainFrameError(page, "sensitive upstream diagnostic")

        assertEquals(
            "Page could not be loaded. Check the address and connection.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun linkFromHomeLoadsOnceAndInvalidLinksAreExplained() {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())

        assertEquals(
            "https://example.com/watch?v=1",
            viewModel.openInitialLink("example.com/watch?v=1"),
        )
        assertEquals("https://example.com/watch?v=1", viewModel.uiState.value.address)
        assertEquals(null, viewModel.openInitialLink("https://example.com/again"))

        val insecure = BrowserViewModel(OkHttpClient(), noAdapters())
        assertEquals(null, insecure.openInitialLink("http://example.com/plain"))
        assertEquals("Only HTTPS pages are supported", insecure.uiState.value.errorMessage)
    }

    @Test
    fun currentPageIsPublishedForDetectedMediaAndOutlivesTheBrowser() = runTest {
        val detected = DetectedMediaStore()
        val viewModel = BrowserViewModel(
            OkHttpClient(),
            noAdapters(),
            PreviewSelectionStore(),
            detected,
        )
        val page = "https://example.test/one"

        viewModel.onPageStarted(page)
        runCurrent()
        assertEquals(page, detected.page.value?.pageUrl)
        assertTrue(detected.page.value!!.candidates.isEmpty())

        viewModel.onDomProbeResult(
            pageUrl = page,
            result = """[{"url":"https://cdn.test/a.mp4","type":"video/mp4","title":"Clip"}]""",
        )
        advanceTimeBy(250)
        runCurrent()
        viewModel.onPageFinished(page, "Fixture page")
        runCurrent()

        val published = detected.page.value!!
        assertEquals("Fixture page", published.pageTitle)
        assertEquals("Clip", published.candidates.single().title)

        BrowserViewModel(OkHttpClient(), noAdapters(), PreviewSelectionStore(), detected)
        runCurrent()
        assertEquals(published, detected.page.value)
    }

    @Test
    fun parallelDownloadCallbacksKeepEveryCurrentPageObservation() = runTest {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())
        val page = "https://example.test/watch"
        viewModel.onPageStarted(page)
        runCurrent()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val callbacks = (0 until 32).map { index ->
                executor.submit {
                    viewModel.onDownload(
                        DownloadObservation(
                            pageUrl = page,
                            mediaUrl = "https://cdn.test/clip-$index.mp4",
                            userAgent = "YFT-Test",
                            contentDisposition = null,
                            mimeType = "video/mp4",
                            contentLengthBytes = 1_048_576,
                            cookie = null,
                            observedAtEpochMs = 1_000,
                        ),
                    )
                }
            }
            callbacks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(32, viewModel.uiState.value.candidates.size)
        assertTrue(viewModel.uiState.value.candidates.all { it.pageUrl == page })
    }

    @Test
    fun backgroundRequestQueuedBeforeNavigationIsDiscardedOnMain() = runTest {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())
        val firstPage = "https://example.test/one"
        viewModel.onPageStarted(firstPage)
        runCurrent()
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                viewModel.onRequest(
                    RequestObservation(
                        pageUrl = firstPage,
                        requestUrl = "https://cdn.test/old.mp4",
                        method = "GET",
                        headers = emptyMap(),
                        userAgent = "YFT-Test",
                        cookie = null,
                        observedAtEpochMs = 1_000,
                    ),
                )
            }.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
        viewModel.onPageStarted("https://example.test/two")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals("https://example.test/two", viewModel.uiState.value.currentUrl)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
    }

    private fun noAdapters(): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(emptyList()))
}
