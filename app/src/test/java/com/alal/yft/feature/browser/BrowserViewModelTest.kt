package com.alal.yft.feature.browser

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
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
    fun wordsInTheAddressFieldSearchTheWebInsteadOfFailing() {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())
        viewModel.onAddressChanged("cat videos")

        assertEquals("https://duckduckgo.com/?q=cat+videos", viewModel.addressForLoading())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun startPageFollowsSavedSitesIncludingAnIntentionallyEmptyList() = runTest {
        val saved = listOf(HomeSite("Saved site", "https://example.test"))
        val siteFlow = MutableStateFlow(saved)
        val repository = object : HomeSitesRepository {
            override val sites = siteFlow
            var writes = 0

            override suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>) {
                writes++
                siteFlow.value = transform(siteFlow.value)
            }
        }
        val viewModel = BrowserViewModel(
            OkHttpClient(),
            noAdapters(),
            homeSitesRepository = repository,
        )
        runCurrent()
        assertEquals(saved, viewModel.uiState.value.sites)

        siteFlow.value = emptyList()
        runCurrent()
        assertTrue(viewModel.uiState.value.sites.isEmpty())
        assertEquals(0, repository.writes)
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

    @Test
    fun botCheckRetriesOnceAfterThePlayerFetchesMediaWithTheSitesOwnCookie() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Failure(SiteExtractionFailure.BOT_CHECK),
            SiteExtractionResult.Failure(SiteExtractionFailure.BOT_CHECK),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FIXTURE_PAGE)
        runCurrent()
        viewModel.onRequest(request("https://fixture.test/api/player", cookie = "SID=own"))
        viewModel.onRequest(request("https://ads.other.test/pixel", cookie = "tracker=1"))
        viewModel.onRequest(request("https://fixture.test/api/log", cookie = null))
        runCurrent()
        viewModel.onPageFinished(FIXTURE_PAGE, "Clip")
        runCurrent()

        assertEquals(listOf("SID=own"), extractor.cookies())
        assertTrue(viewModel.uiState.value.siteNotice!!.contains("not a bot"))
        assertTrue(viewModel.uiState.value.canRetrySiteLookup)

        viewModel.onRequest(request("https://cdn.fixture.test/thumb.jpg", cookie = null))
        runCurrent()
        assertEquals(1, extractor.requests.size)

        viewModel.onRequest(request(MEDIA_REQUEST, cookie = null))
        runCurrent()
        assertEquals(listOf("SID=own", "SID=own"), extractor.cookies())

        // Only one automatic retry per page: later media requests leave it to Try again.
        viewModel.onRequest(request(MEDIA_REQUEST, cookie = null))
        runCurrent()
        assertEquals(2, extractor.requests.size)
        assertTrue(viewModel.uiState.value.canRetrySiteLookup)
    }

    @Test
    fun tryAgainAsksTheAdapterAgainAndASuccessClearsTheNotice() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Failure(SiteExtractionFailure.RATE_LIMITED),
            SiteExtractionResult.Success(listOf(fixtureCandidate())),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FIXTURE_PAGE)
        viewModel.onPageFinished(FIXTURE_PAGE, "Clip")
        runCurrent()
        assertTrue(viewModel.uiState.value.canRetrySiteLookup)

        // Rate limits are not answered by playback, so media requests never retry by themselves.
        viewModel.onRequest(request(MEDIA_REQUEST, cookie = null))
        runCurrent()
        assertEquals(1, extractor.requests.size)

        viewModel.retrySiteLookup()
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(2, extractor.requests.size)
        assertNull(viewModel.uiState.value.siteNotice)
        assertFalse(viewModel.uiState.value.canRetrySiteLookup)
        assertEquals(
            "https://cdn.fixture.test/42.mp4",
            viewModel.uiState.value.candidates.single().mediaUrl,
        )

        viewModel.retrySiteLookup()
        runCurrent()
        assertEquals(2, extractor.requests.size)
    }

    @Test
    fun finalFailuresOfferNoRetryAndANewPageForgetsTheOldOne() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Failure(SiteExtractionFailure.DRM_PROTECTED),
            SiteExtractionResult.Failure(SiteExtractionFailure.BOT_CHECK),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FIXTURE_PAGE)
        viewModel.onPageFinished(FIXTURE_PAGE, "Clip")
        runCurrent()

        assertTrue(viewModel.uiState.value.siteNotice!!.isNotBlank())
        assertFalse(viewModel.uiState.value.canRetrySiteLookup)
        viewModel.retrySiteLookup()
        runCurrent()
        assertEquals(1, extractor.requests.size)

        viewModel.onPageStarted("$FIXTURE_PAGE?t=1")
        runCurrent()
        assertNull(viewModel.uiState.value.siteNotice)
        assertFalse(viewModel.uiState.value.canRetrySiteLookup)
        val nextPage = "$FIXTURE_PAGE?t=1"
        viewModel.onRequest(request("https://ads.other.test/pixel", "tracker=1", nextPage))
        runCurrent()
        viewModel.onPageFinished("$FIXTURE_PAGE?t=1", "Clip")
        runCurrent()

        assertEquals(listOf(null, null), extractor.cookies())
        assertTrue(viewModel.uiState.value.canRetrySiteLookup)
    }

    private fun noAdapters(): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(emptyList()))

    private fun adapters(extractor: SiteExtractor): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)))

    private fun request(
        url: String,
        cookie: String?,
        page: String = FIXTURE_PAGE,
    ): RequestObservation = RequestObservation(
        pageUrl = page,
        requestUrl = url,
        method = "GET",
        headers = emptyMap(),
        userAgent = "YFT-Test",
        cookie = cookie,
        observedAtEpochMs = 1_000,
    )

    private fun fixtureCandidate(): MediaCandidate = MediaCandidate(
        pageUrl = FIXTURE_PAGE,
        mediaUrl = "https://cdn.fixture.test/42.mp4",
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
    )

    /** Answers each lookup with the next scripted result and records what it was asked. */
    private class ScriptedExtractor(vararg results: SiteExtractionResult) : SiteExtractor {
        private val script = ArrayDeque(results.toList())
        override val id: String = "fixture"
        override val displayName: String = "Fixture Site"
        val requests = mutableListOf<SiteExtractionRequest>()

        fun cookies(): List<String?> = requests.map { it.requestContext.cookie }

        override fun identify(pageUrl: String): SitePageIdentity? {
            if (!pageUrl.startsWith(FIXTURE_PREFIX)) return null
            val contentId = pageUrl.removePrefix(FIXTURE_PREFIX).substringBefore('?')
            return SitePageIdentity("fixture", contentId, "$FIXTURE_PREFIX$contentId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean =
            requestUrl.startsWith("https://media.fixture.test/")

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            return script.removeFirstOrNull()
                ?: SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }
    }

    private companion object {
        const val FIXTURE_PREFIX = "https://fixture.test/video/"
        const val FIXTURE_PAGE = "${FIXTURE_PREFIX}42"
        const val MEDIA_REQUEST = "https://media.fixture.test/stream?part=1"
    }
}
