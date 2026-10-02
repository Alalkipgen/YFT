package com.alal.yft.feature.browser

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractorRegistry
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
            result = """[{"url":"https://cdn.test/video.mp4","type":"video/mp4","title":"Fixture"}]""",
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

    private fun noAdapters(): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(emptyList()))
}
