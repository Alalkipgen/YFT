package com.alal.yft.feature.browser

import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.FocusedVideoProbe
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.ui.components.isSavable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.detectedmedia.PageReload
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

        assertEquals("https://www.google.com/search?q=cat+videos", viewModel.addressForLoading())
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
        val detected = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), detected)
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
        val video = MediaGroups.of(viewModel.uiState.value.candidates).single()
        assertTrue(viewModel.selectForDownload(video))
        assertEquals(video, detected.selection.value)

        viewModel.onPageStarted("https://example.test/two")
        runCurrent()

        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        // The new page also drops the video chosen on the old one.
        assertEquals(null, detected.selection.value)
        assertFalse(viewModel.selectForDownload(video))
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

        BrowserViewModel(OkHttpClient(), noAdapters(), detected)
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

    @Test
    fun inPageNavigationFromAFeedLooksUpEachVideoAsANewPage() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_A, "a.mp4"))),
            SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_B, "b.mp4"))),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FEED_PAGE)
        viewModel.onRequest(request("https://fixture.test/api/feed", "SID=own", FEED_PAGE))
        viewModel.onDomProbeResult(
            pageUrl = FEED_PAGE,
            result = """[{"url":"https://cdn.test/feed-preview.mp4","type":"video/mp4"}]""",
        )
        viewModel.onPageFinished(FEED_PAGE, "Feed")
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("https://cdn.test/feed-preview.mp4"), mediaUrls(viewModel))
        assertTrue(extractor.requests.isEmpty())

        // YouTube's mobile site opens a video with history.pushState: no page load follows.
        viewModel.onUrlChanged(VIDEO_A)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertEquals(VIDEO_A, viewModel.uiState.value.address)
        assertEquals(VIDEO_A, viewModel.uiState.value.currentUrl)
        advanceTimeBy(499)
        runCurrent()
        assertTrue(extractor.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("1"), extractor.requests.map { it.identity.contentId })
        // The site's own session carries over to its next page.
        assertEquals(listOf("SID=own"), extractor.cookies())
        assertEquals(listOf("https://cdn.fixture.test/a.mp4"), mediaUrls(viewModel))
        assertEquals(VIDEO_A, viewModel.uiState.value.candidates.single().pageUrl)
        assertTrue(downloadButtonVisible(viewModel))

        viewModel.onUrlChanged(VIDEO_B)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertFalse(downloadButtonVisible(viewModel))
        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("1", "2"), extractor.requests.map { it.identity.contentId })
        assertEquals(listOf("https://cdn.fixture.test/b.mp4"), mediaUrls(viewModel))
        assertEquals(VIDEO_B, viewModel.uiState.value.candidates.single().pageUrl)
    }

    @Test
    fun observationsAfterAnInPageChangeBelongToTheNewVideo() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_A, "a.mp4"))),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FEED_PAGE)
        viewModel.onPageFinished(FEED_PAGE, "Feed")
        runCurrent()

        viewModel.onUrlChanged(VIDEO_A)
        viewModel.onRequest(request("https://fixture.test/api/player", "SID=new", VIDEO_A))
        // Anything still arriving for the feed is the old page's.
        viewModel.onDomProbeResult(
            pageUrl = FEED_PAGE,
            result = """[{"url":"https://cdn.test/feed-preview.mp4","type":"video/mp4"}]""",
        )
        viewModel.onDomProbeResult(
            pageUrl = VIDEO_A,
            result = """[{"url":"https://cdn.test/player.mp4","type":"video/mp4"}]""",
        )
        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf("SID=new"), extractor.cookies())
        assertEquals(
            listOf("https://cdn.fixture.test/a.mp4", "https://cdn.test/player.mp4"),
            mediaUrls(viewModel).sorted(),
        )
    }

    @Test
    fun aLookupForAnEarlierVideoNeverShowsOnTheNextOne() = runTest {
        val gate = CompletableDeferred<SiteExtractionResult>()
        val extractor = GatedExtractor(
            gates = mapOf("1" to gate),
            results = mapOf(
                "2" to SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_B, "b.mp4"))),
            ),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FEED_PAGE)
        viewModel.onPageFinished(FEED_PAGE, "Feed")
        viewModel.onUrlChanged(VIDEO_A)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf("1"), extractor.asked)

        viewModel.onUrlChanged(VIDEO_B)
        // The first video's answer arrives after the user moved on to the next one.
        gate.complete(SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_A, "a.mp4"))))
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf("1", "2"), extractor.asked)
        assertEquals(listOf("https://cdn.fixture.test/b.mp4"), mediaUrls(viewModel))
    }

    @Test
    fun scrollingPastVideosLooksUpOnlyTheOneThatStays() = runTest {
        val extractor = ScriptedExtractor()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FEED_PAGE)
        viewModel.onPageFinished(FEED_PAGE, "Feed")

        viewModel.onUrlChanged(VIDEO_A)
        advanceTimeBy(300)
        viewModel.onUrlChanged(VIDEO_B)
        advanceTimeBy(300)
        viewModel.onUrlChanged(VIDEO_C)
        advanceTimeBy(500)
        runCurrent()

        assertEquals(listOf("3"), extractor.requests.map { it.identity.contentId })
    }

    @Test
    fun theSamePageUnderANewAddressKeepsWhatWasFound() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_A, "a.mp4"))),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(VIDEO_A)
        viewModel.onPageFinished(VIDEO_A, "Clip")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("https://cdn.fixture.test/a.mp4"), mediaUrls(viewModel))

        // The site adds a parameter, then a fragment, to the address of the same video.
        val shared = "$VIDEO_A?pp=share"
        viewModel.onUrlChanged(shared)
        viewModel.onUrlChanged("$shared#t=10")
        viewModel.onPageFinished("$shared#t=10", "Clip")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, extractor.requests.size)
        assertEquals("$shared#t=10", viewModel.uiState.value.address)
        assertEquals(listOf("https://cdn.fixture.test/a.mp4"), mediaUrls(viewModel))
        assertEquals("$shared#t=10", viewModel.uiState.value.candidates.single().pageUrl)
        assertEquals("Clip", viewModel.uiState.value.pageTitle)
    }

    @Test
    fun aFragmentChangeOnAnyPageKeepsItsMedia() = runTest {
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters())
        val page = "https://example.test/article"
        viewModel.onPageStarted(page)
        viewModel.onDomProbeResult(
            pageUrl = page,
            result = """[{"url":"https://cdn.test/clip.mp4","type":"video/mp4"}]""",
        )
        advanceTimeBy(250)
        runCurrent()

        viewModel.onUrlChanged("$page#comments")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("https://cdn.test/clip.mp4"), mediaUrls(viewModel))
        assertEquals("$page#comments", viewModel.uiState.value.currentUrl)
    }

    @Test
    fun aLoadingVideoPageIsLookedUpBeforeItFinishesAndOnlyOnce() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Success(listOf(fixtureCandidate())),
            SiteExtractionResult.Success(listOf(fixtureCandidate(VIDEO_B, "b.mp4"))),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))

        // A page no adapter handles waits for its own finish.
        viewModel.onPageStarted(FEED_PAGE)
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(extractor.requests.isEmpty())

        // Facebook keeps loading long after its video is known.
        viewModel.onPageStarted(FIXTURE_PAGE)
        advanceTimeBy(1_499)
        runCurrent()
        assertTrue(extractor.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("42"), extractor.requests.map { it.identity.contentId })
        assertEquals(listOf("https://cdn.fixture.test/42.mp4"), mediaUrls(viewModel))
        assertTrue(downloadButtonVisible(viewModel))

        // Finishing later does not ask again, and keeps what was found.
        viewModel.onPageFinished(FIXTURE_PAGE, "Video")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, extractor.requests.size)
        assertEquals(listOf("https://cdn.fixture.test/42.mp4"), mediaUrls(viewModel))

        // A page left before the wait ends is never asked about.
        viewModel.onPageStarted(VIDEO_A)
        advanceTimeBy(750)
        viewModel.onPageStarted(VIDEO_B)
        advanceTimeBy(1_500)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("42", "2"), extractor.requests.map { it.identity.contentId })
        assertEquals(listOf("https://cdn.fixture.test/b.mp4"), mediaUrls(viewModel))
    }

    @Test
    fun aFeedTapLooksUpTheVideoOnScreenAndOpensItsDownloadSheet() = runTest {
        val extractor = FeedVideoExtractor()
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store, { 7_000L })
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onRequest(request(FEED_API, "SID=own", YOUTUBE_FEED))
        viewModel.onPageFinished(YOUTUBE_FEED, "YouTube")
        runCurrent()

        // Nothing was found, yet the button shows and looks for the video on screen.
        val state = viewModel.uiState.value
        assertTrue(state.findsFocusedVideo)
        assertTrue(state.feedPage)
        assertTrue(downloadButtonVisible(viewModel))
        assertEquals(
            BrowserDownloadFab.Action.FIND_VIDEO_ON_SCREEN,
            BrowserDownloadFab.action(0, state.findsFocusedVideo, state.feedPage),
        )
        assertTrue(extractor.requests.isEmpty())

        assertEquals(FocusedVideoProbe.script, viewModel.focusedVideoScript())
        assertTrue(viewModel.uiState.value.findingFocusedVideo)
        assertEquals("Finding the video on screen…", viewModel.uiState.value.focusNotice)
        viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=BBBBBBBBBB2&pp=x"))
        runCurrent()

        val asked = extractor.requests.single()
        assertEquals("BBBBBBBBBB2", asked.identity.contentId)
        assertEquals(FOCUSED_VIDEO, asked.requestContext.pageUrl)
        // The site's own session reads the video as the feed did.
        assertEquals("SID=own", asked.requestContext.cookie)
        assertEquals(7_000L, asked.nowEpochMs)
        val selected = store.selection.value!!
        assertEquals(listOf("https://cdn.fixture.test/BBBBBBBBBB2.mp4"), selected.candidates.map {
            it.mediaUrl
        })
        assertEquals(listOf(FOCUSED_VIDEO), selected.candidates.map { it.pageUrl })
        assertEquals(1, opened.size)
        assertFalse(viewModel.uiState.value.findingFocusedVideo)
        assertNull(viewModel.uiState.value.focusNotice)
        // The feed itself still lists only what it found.
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertEquals(YOUTUBE_FEED, viewModel.uiState.value.currentUrl)
    }

    @Test
    fun noVideoOnScreenOrAnUnreadableOneShowsAShortNoticeThatClearsItself() = runTest {
        val extractor = FeedVideoExtractor(
            failing = mapOf("FFFFFFFFFF7" to SiteExtractionFailure.NO_MEDIA_FOUND),
        )
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onPageFinished(YOUTUBE_FEED, "YouTube")
        runCurrent()

        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(JSONObject.quote("""{"url":null,"source":"none"}"""))
        runCurrent()
        assertTrue(extractor.requests.isEmpty())
        assertEquals(
            "No video on screen to download. Scroll to a video and tap Download again.",
            viewModel.uiState.value.focusNotice,
        )
        assertFalse(viewModel.uiState.value.findingFocusedVideo)
        advanceTimeBy(3_999)
        runCurrent()
        assertNotNull(viewModel.uiState.value.focusNotice)
        advanceTimeBy(1)
        runCurrent()
        assertNull(viewModel.uiState.value.focusNotice)

        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=FFFFFFFFFF7", "page"))
        runCurrent()
        assertEquals(listOf("FFFFFFFFFF7"), extractor.requests.map { it.identity.contentId })
        // P16: the sheet opened at once for that video and says why, instead of a notice.
        assertEquals(1, opened.size)
        assertEquals(
            "This YouTube post has no downloadable video.",
            store.lookup.value?.failure,
        )
        assertNull(viewModel.uiState.value.focusNotice)
        assertFalse(viewModel.uiState.value.findingFocusedVideo)
        assertNull(store.selection.value)

        // A page that never answers the script does not leave the button waiting.
        advanceTimeBy(4_000)
        runCurrent()
        viewModel.focusedVideoScript()
        advanceTimeBy(5_000)
        runCurrent()
        assertFalse(viewModel.uiState.value.findingFocusedVideo)
        assertEquals(
            "No video on screen to download. Scroll to a video and tap Download again.",
            viewModel.uiState.value.focusNotice,
        )
    }

    @Test
    fun noScriptRunsOnOtherSitesAndTheirButtonWaitsForAFind() = runTest {
        val extractor = FeedVideoExtractor()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted("https://example.org/videos")
        viewModel.onPageFinished("https://example.org/videos", "Videos")
        runCurrent()

        assertFalse(viewModel.uiState.value.findsFocusedVideo)
        assertFalse(viewModel.uiState.value.feedPage)
        assertFalse(downloadButtonVisible(viewModel))
        assertNull(viewModel.focusedVideoScript())
        viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=BBBBBBBBBB2", "page"))
        runCurrent()
        assertTrue(extractor.requests.isEmpty())
        assertNull(viewModel.uiState.value.focusNotice)
    }

    @Test
    fun aVideoPageOfAFeedSiteIsNoFeedAndALeftFeedDropsItsLookup() = runTest {
        val gate = CompletableDeferred<Unit>()
        val extractor = FeedVideoExtractor(gate = gate)
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onPageFinished(YOUTUBE_FEED, "YouTube")
        runCurrent()
        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=BBBBBBBBBB2"))
        runCurrent()
        assertEquals(1, extractor.requests.size)

        // P16: the sheet opened at once and waits on that lookup.
        assertEquals(1, opened.size)
        assertEquals("youtube:BBBBBBBBBB2", store.lookup.value?.key)

        // The page opened a video before the lookup answered: the sheet no longer waits.
        viewModel.onUrlChanged("https://m.youtube.com/watch?v=AAAAAAAAAA1")
        assertTrue(viewModel.uiState.value.findsFocusedVideo)
        assertFalse(viewModel.uiState.value.feedPage)
        assertFalse(viewModel.uiState.value.findingFocusedVideo)
        assertNull(viewModel.uiState.value.focusNotice)
        assertNotEquals("youtube:BBBBBBBBBB2", store.lookup.value?.key)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, opened.size)
        assertNull(store.selection.value)
        // A late answer of the old page's script is dropped too.
        viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=BBBBBBBBBB2"))
        runCurrent()
        assertEquals(listOf("BBBBBBBBBB2"), extractor.requests.map { it.identity.contentId }
            .filter { it == "BBBBBBBBBB2" })
        assertEquals(1, opened.size)
    }

    @Test
    fun aBackgroundNetworkFailureHasNoTopNoticeAndRetryRunsAgain() = runTest {
        val extractor = ScriptedExtractor(
            SiteExtractionResult.Failure(SiteExtractionFailure.NETWORK),
            SiteExtractionResult.Success(listOf(fixtureCandidate())),
        )
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        viewModel.onPageStarted(FIXTURE_PAGE)
        viewModel.onPageFinished(FIXTURE_PAGE, "Clip")
        runCurrent()
        assertNull(viewModel.uiState.value.siteNotice)
        assertTrue(viewModel.uiState.value.canRetrySiteLookup)
        viewModel.retrySiteLookup()
        runCurrent()
        assertEquals(2, extractor.requests.size)
        assertFalse(viewModel.uiState.value.canRetrySiteLookup)
        assertNull(viewModel.uiState.value.siteNotice)
    }

    @Test
    fun aFocusedNetworkFailureShowsInTheSheetWithTryAgainForTheSameVideo() = runTest {
        val failing = mutableMapOf("BBBBBBBBBB2" to SiteExtractionFailure.NETWORK)
        val extractor = FeedVideoExtractor(failing = failing)
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onPageFinished(YOUTUBE_FEED, "Feed")
        runCurrent()
        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(answer(FOCUSED_VIDEO))
        runCurrent()
        // P16: the open sheet says why, with Try again; the page keeps its Download button.
        val failed = store.lookup.value!!
        assertEquals("Couldn't reach YouTube.", failed.failure)
        assertTrue(failed.canRetry)
        assertNull(viewModel.uiState.value.focusNotice)
        assertTrue(downloadButtonVisible(viewModel))
        assertNull(viewModel.uiState.value.siteNotice)

        // The sheet's Try again asks once more for the same video, which fills the sheet.
        failing.clear()
        store.retryLookup(failed.key)
        runCurrent()
        assertEquals(2, extractor.requests.size)
        assertEquals("BBBBBBBBBB2", extractor.requests.last().identity.contentId)
        assertEquals(
            listOf("https://cdn.fixture.test/BBBBBBBBBB2.mp4"),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
        assertNull(store.lookup.value)
        viewModel.onPageStarted("https://example.test/next")
        runCurrent()
        assertFalse(viewModel.uiState.value.canRetryFocusedLookup)
        assertNull(viewModel.uiState.value.focusNotice)
    }

    @Test
    fun aFeedTapOpensTheSheetBeforeTheLookupEndsAndClosingItStopsThatLookup() = runTest {
        val gate = CompletableDeferred<Unit>()
        val extractor = FeedVideoExtractor(gate = gate, cancellable = true)
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onPageFinished(YOUTUBE_FEED, "YouTube")
        runCurrent()

        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(answer(FOCUSED_VIDEO))
        runCurrent()
        // P16: the sheet is open while the adapter still answers, and waits on that lookup.
        assertEquals(1, opened.size)
        assertEquals(1, extractor.requests.size)
        val waiting = store.lookup.value!!
        assertEquals("youtube:BBBBBBBBBB2", waiting.key)
        assertTrue(waiting.running)
        assertTrue(store.awaitsLookup)

        // Closing the sheet before the video came stops its lookup.
        store.closeLookup(waiting.key)
        runCurrent()
        assertEquals(1, extractor.cancelled)
        assertNull(store.lookup.value)
        assertFalse(viewModel.uiState.value.findingFocusedVideo)
        assertNull(viewModel.uiState.value.focusNotice)

        // The next tap opens the sheet again and the lookup fills it: one open, no second ask.
        gate.complete(Unit)
        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(answer(FOCUSED_VIDEO))
        runCurrent()
        assertEquals(2, opened.size)
        assertEquals(2, extractor.requests.size)
        assertEquals(
            listOf("https://cdn.fixture.test/BBBBBBBBBB2.mp4"),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
        assertNull(store.lookup.value)
        // The sheet of a found video closes without stopping anything.
        store.closeLookup(waiting.key)
        runCurrent()
        assertNotNull(store.selection.value)
    }

    @Test
    fun aWatchPageAsksItsAdapterOnceAndTheSheetWaitsForThatSameLookup() = runTest {
        val gate = CompletableDeferred<Unit>()
        val extractor = FeedVideoExtractor(gate = gate)
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        viewModel.onPageStarted(WATCH_A)
        viewModel.onPageFinished(WATCH_A, "Video A")
        runCurrent()

        // P12: the button means this page's video and spins while its lookup runs.
        val state = viewModel.uiState.value
        assertTrue(state.sitePage)
        assertFalse(state.feedPage)
        assertTrue(state.pageLookupRunning)
        assertEquals(
            BrowserDownloadFab.Action.OPEN_PAGE_VIDEO,
            BrowserDownloadFab.action(0, state.findsFocusedVideo, state.feedPage, state.sitePage),
        )
        // Two taps during the lookup: the sheet opens and waits; nobody asks again.
        assertTrue(viewModel.openPageVideo())
        assertTrue(viewModel.openPageVideo())
        runCurrent()
        assertEquals(1, extractor.requests.size)
        assertNull(store.selection.value)
        val waiting = store.lookup.value!!
        assertEquals("youtube:AAAAAAAAAA1", waiting.key)
        assertTrue(waiting.running)
        assertEquals("Video A", waiting.title)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1, extractor.requests.size)
        assertEquals(
            listOf("https://cdn.fixture.test/AAAAAAAAAA1.mp4"),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
        assertNull(store.lookup.value)
        assertFalse(viewModel.uiState.value.pageLookupRunning)
        // Later taps open the video it found, still without another lookup.
        assertTrue(viewModel.openPageVideo())
        runCurrent()
        assertEquals(1, extractor.requests.size)
    }

    @Test
    fun aFailedPageLookupShowsInTheSheetAndItsTryAgainAsksOnceMore() = runTest {
        val failing = mutableMapOf("AAAAAAAAAA1" to SiteExtractionFailure.NETWORK)
        val extractor = FeedVideoExtractor(failing = failing)
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        viewModel.onPageStarted(WATCH_A)
        viewModel.onPageFinished(WATCH_A, "Video A")
        runCurrent()

        assertTrue(viewModel.openPageVideo())
        val failed = store.lookup.value!!
        assertEquals("Couldn't reach YouTube.", failed.failure)
        assertTrue(failed.canRetry)
        assertNull(store.selection.value)
        assertEquals(1, extractor.requests.size)

        // The sheet's Try again: one more lookup of the same video, which fills the sheet.
        failing.clear()
        store.retryLookup(failed.key)
        runCurrent()
        assertEquals(2, extractor.requests.size)
        assertEquals(
            listOf("https://cdn.fixture.test/AAAAAAAAAA1.mp4"),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
        assertNull(store.lookup.value)
        // Another video's key is not this page's: nothing more is asked.
        store.retryLookup("youtube:BBBBBBBBBB2")
        runCurrent()
        assertEquals(2, extractor.requests.size)
    }

    @Test
    fun aFeedLinkToAVideoThatWasAlreadyFoundTakesThatLookup() = runTest {
        val extractor = FeedVideoExtractor()
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onPageFinished(YOUTUBE_FEED, "YouTube")
        runCurrent()

        repeat(2) {
            viewModel.focusedVideoScript()
            viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=BBBBBBBBBB2"))
            runCurrent()
        }
        // P12: the same video ID in this page asks the adapter once; both taps open its sheet.
        assertEquals(1, extractor.requests.size)
        assertEquals(2, opened.size)
        assertEquals(
            listOf("https://cdn.fixture.test/BBBBBBBBBB2.mp4"),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
        // Nothing failed, so there is no Try again. Plan adapted (P17): a new page of the same
        // video takes the answer remembered for a few minutes instead of asking the site again.
        viewModel.retryFocusedLookup()
        viewModel.onPageStarted(YOUTUBE_FEED)
        viewModel.onPageFinished(YOUTUBE_FEED, "YouTube")
        runCurrent()
        viewModel.focusedVideoScript()
        viewModel.onFocusedVideoResult(answer("https://m.youtube.com/watch?v=BBBBBBBBBB2"))
        runCurrent()
        assertEquals(1, extractor.requests.size)
        assertEquals(
            listOf("https://cdn.fixture.test/BBBBBBBBBB2.mp4"),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
    }

    @Test
    fun genericProbesWaitForTheSiteLookupAndRunOnlyWhenItFoundNothing() = runTest {
        val probes = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                probes.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val gate = CompletableDeferred<Unit>()
        val failing = mutableMapOf<String, SiteExtractionFailure>()
        val extractor = FeedVideoExtractor(failing = failing, gate = gate)
        val viewModel = BrowserViewModel(client, adapters(extractor))
        viewModel.onPageStarted(WATCH_A)
        viewModel.onPageFinished(WATCH_A, "Video A")
        viewModel.onRequest(request(PLAYER_FILE, null, WATCH_A))
        runCurrent()
        Thread.sleep(PROBE_SETTLE_MS)
        runCurrent()
        // P12: the site's lookup goes first; the player's file is not probed meanwhile.
        assertEquals(0, probes.get())
        gate.complete(Unit)
        runCurrent()
        Thread.sleep(PROBE_SETTLE_MS)
        runCurrent()
        // It found the video, so the file is never probed.
        assertEquals(0, probes.get())

        // A video it could not read: the page's own files are probed after it.
        failing["AAAAAAAAAA1"] = SiteExtractionFailure.NO_MEDIA_FOUND
        viewModel.onPageStarted(WATCH_B)
        viewModel.onPageFinished(WATCH_B, "Video B")
        failing["BBBBBBBBBB2"] = SiteExtractionFailure.NO_MEDIA_FOUND
        viewModel.onRequest(request(PLAYER_FILE, null, WATCH_B))
        runCurrent()
        val deadline = System.currentTimeMillis() + 5_000
        while (probes.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            runCurrent()
        }
        assertTrue(probes.get() > 0)
    }

    @Test
    fun reloadPageAndTryAgainReloadsWithoutCacheAndReopensTheSheetForANewLink() = runTest {
        // P37: the sheet's "Reload page and try again" on a page whose links were gone.
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        val page = "https://example.test/watch/5"
        val dead = "https://cdn.test/v5.mp4?token=old"
        val fresh = "https://cdn.test/v5.mp4?token=new"
        viewModel.onPageStarted(page)
        viewModel.onPageFinished(page, "Clip")
        viewModel.onDomProbeResult(page, oneVideo(dead))
        advanceTimeBy(2_000)
        runCurrent()
        val video = MediaGroups.pageVideos(viewModel.uiState.value.candidates).single()

        assertTrue(store.reloadPage(PageReload(page, video, setOf(dead))))
        runCurrent()
        assertEquals(1, viewModel.uiState.value.reloadRequest)
        assertTrue(viewModel.uiState.value.noCacheLoad)

        // The tab reloads; the page first names the same dead link, then a new one.
        viewModel.onPageStarted(page)
        viewModel.onDomProbeResult(page, oneVideo(dead))
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(opened.isEmpty())
        viewModel.onDomProbeResult(page, oneVideo(fresh))
        advanceTimeBy(2_000)
        runCurrent()
        viewModel.onPageFinished(page, "Clip")
        assertEquals(1, opened.size)
        assertEquals(listOf(fresh), store.selection.value?.candidates?.map { it.mediaUrl })
        assertFalse(viewModel.uiState.value.noCacheLoad)

        // No new link within the wait: a notice that clears itself; nothing opens.
        assertTrue(store.reloadPage(PageReload(page, video, setOf(dead, fresh))))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.reloadRequest)
        viewModel.onPageStarted(page)
        viewModel.onDomProbeResult(page, oneVideo(fresh))
        viewModel.onPageFinished(page, "Clip")
        advanceTimeBy(15_001)
        runCurrent()
        assertEquals(
            "The site gave no new link. Play the video for a moment, then tap Download again.",
            viewModel.uiState.value.focusNotice,
        )
        assertEquals(1, opened.size)
        advanceTimeBy(4_001)
        runCurrent()
        assertNull(viewModel.uiState.value.focusNotice)

        // Another page's reload is not this tab's.
        store.reloadPage(PageReload("https://example.test/other", video, setOf(dead)))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.reloadRequest)
    }

    @Test
    fun severalVideosOpenThePlayingOneElseTheLargestWithTheOthersCounted() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), noAdapters(), store)
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.quickDownloadRequests.collect { opened += it }
        }
        val page = "https://example.test/gallery"
        viewModel.onPageStarted(page)
        viewModel.onPageFinished(page, "Gallery")
        // No sizes or pictures were stated: the longest is the largest.
        val small = "https://cdn.test/small.mp4"
        val large = "https://cdn.test/large.mp4"
        val playing = "https://cdn.test/playing.mp4"
        val videos = JSONArray()
        listOf(small to 10, large to 300, playing to 60).forEach { (url, seconds) ->
            videos.put(
                JSONObject().put("url", url).put("type", "video/mp4").put("duration", seconds),
            )
        }
        viewModel.onDomProbeResult(page, videos.toString())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, MediaGroups.pageVideos(viewModel.uiState.value.candidates).size)

        // The page says which video plays.
        viewModel.mainVideoScript()
        viewModel.onPlayingVideoResult(JSONObject.quote(playing))
        runCurrent()
        assertEquals(listOf(playing), store.selection.value?.candidates?.map { it.mediaUrl })
        assertEquals(2, store.otherVideos.value)
        assertEquals(1, opened.size)

        // A page that does not answer gets its largest video.
        viewModel.mainVideoScript()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(large), store.selection.value?.candidates?.map { it.mediaUrl })
        assertEquals(2, opened.size)
        // Its late answer opens nothing more.
        viewModel.onPlayingVideoResult(JSONObject.quote(playing))
        runCurrent()
        assertEquals(2, opened.size)
    }

    @Test
    fun aVideoPageWithPreviewsAndAnAdOpensItsStreamByItsLengthWithTheRestCountedAsOthers() =
        runTest {
            // P24 (plan step 8): the owner's case. The page plays one HLS stream through Media
            // Source Extensions (a blob: address); 40 muted looping previews and an ad's clip
            // play around it. All hosts are reserved .test names.
            val page = "https://videos.example.test/watch/42"
            val master = "https://stream.example.test/v42/master.m3u8?token=fixture"
            val playlist = "https://stream.example.test/v42/720p/index.m3u8"
            val fetched = CopyOnWriteArrayList<Request>()
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val asked = chain.request()
                    fetched += asked
                    val body = when (asked.url.encodedPath) {
                        "/v42/master.m3u8" -> FIXTURE_MASTER
                        "/v42/720p/index.m3u8" -> fixturePlaylist()
                        else -> null
                    }
                    Response.Builder()
                        .request(asked)
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (body == null) 404 else 200)
                        .message(if (body == null) "Not Found" else "OK")
                        .header("Content-Type", "application/vnd.apple.mpegurl")
                        .body(body.orEmpty().toResponseBody())
                        .build()
                }
                .build()
            val store = DetectedMediaStore()
            val viewModel = BrowserViewModel(client, noAdapters(), store)
            viewModel.onPageStarted(page)
            viewModel.onPageFinished(page, "Long walk by the river")
            val fromPage = mapOf("Referer" to page)
            viewModel.onRequest(request(master, null, page).copy(headers = fromPage))
            viewModel.onRequest(request(playlist, null, page).copy(headers = fromPage))
            val adFrame = mapOf("Referer" to "https://ads.adnet.example.test/frame?slot=right")
            viewModel.onRequest(
                request("https://cdn.adnet.example.test/creative/v.mp4", null, page)
                    .copy(headers = adFrame),
            )
            val previews = JSONArray()
            repeat(40) { index ->
                previews.put(
                    JSONObject()
                        .put("url", "https://media.example.test/previews/$index.mp4")
                        .put("type", "video/mp4")
                        .put("duration", 5 + index % 26)
                        .put("muted", true)
                        .put("loop", true)
                        .put("thumbnail", true),
                )
            }
            viewModel.onDomProbeResult(page, previews.toString())
            // The stream states its length only in its manifest, read off the main thread.
            fun streamsLengthsKnown(): Boolean {
                val streams = viewModel.uiState.value.candidates.filter { it.kind == MediaKind.HLS }
                return streams.any { it.mediaUrl == master } &&
                    streams.all { it.durationMillis != null }
            }
            val deadline = System.currentTimeMillis() + 5_000
            while (!streamsLengthsKnown() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
                advanceTimeBy(100)
                runCurrent()
            }
            advanceTimeBy(2_000)
            runCurrent()

            // The page plays a preview and its own player; the player's length names the stream.
            val now = 1_000_000L
            val answer = JSONObject().put("now", now).put(
                "videos",
                JSONArray()
                    .put(
                        JSONObject().put("src", "https://media.example.test/previews/3.mp4")
                            .put("playing", true).put("duration", 8).put("time", 2)
                            .put("width", 320).put("height", 180).put("area", 57_600)
                            .put("muted", true).put("loop", true).put("thumbnail", true),
                    )
                    .put(
                        JSONObject().put("src", "blob:https://videos.example.test/3f2a")
                            .put("playing", true).put("duration", 754.5).put("time", 31)
                            .put("width", 1920).put("height", 1080).put("area", 360_000)
                            .put("muted", false).put("loop", false).put("thumbnail", false),
                    ),
            )
            viewModel.mainVideoScript()
            viewModel.onPlayingVideoResult(JSONObject.quote(answer.toString()))
            runCurrent()
            val selected = store.selection.value?.candidates.orEmpty()
            val urls = selected.map { it.mediaUrl }
            assertTrue(urls.toString(), master in urls)
            assertTrue(selected.none { "/previews/" in it.mediaUrl || "adnet" in it.mediaUrl })
            assertEquals(754_500L, selected.first { it.mediaUrl == master }.durationMillis)
            assertEquals(41, store.otherVideos.value)

            // A page that does not answer gets its stream too, never a preview.
            viewModel.mainVideoScript()
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(urls, store.selection.value?.candidates?.map { it.mediaUrl })
            // The manifest was read the way the page's player reads it.
            val read = fetched.first { asked ->
                asked.method == "GET" && asked.url.encodedPath == "/v42/master.m3u8"
            }
            assertEquals(page, read.header("Referer"))
            assertEquals("https://videos.example.test", read.header("Origin"))
        }

    private fun downloadButtonVisible(viewModel: BrowserViewModel): Boolean =
        BrowserDownloadFab.isVisible(
            hasPage = viewModel.uiState.value.currentUrl != null,
            savableCount = viewModel.uiState.value.candidates.count { it.isSavable },
            sheetExpanded = false,
            editingAddress = false,
            findsFocusedVideo = viewModel.uiState.value.findsFocusedVideo,
        )

    /** The focused-video script's answer as `WebView.evaluateJavascript` hands it back. */
    private fun answer(url: String, source: String = "centre"): String =
        JSONObject.quote(JSONObject().put("url", url).put("source", source).toString())

    /** P24: a 720p playlist of 125 six-second pieces and a last one of 4.5 s: 754.5 s. */
    private fun fixturePlaylist(): String = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:6\n")
        repeat(125) { index -> append("#EXTINF:6.0,\nseg$index.ts\n") }
        append("#EXTINF:4.5,\nseg125.ts\n#EXT-X-ENDLIST\n")
    }

    private fun mediaUrls(viewModel: BrowserViewModel): List<String> =
        viewModel.uiState.value.candidates.map { it.mediaUrl }

    private fun noAdapters(): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(emptyList()))

    private fun adapters(extractor: SiteExtractor): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)))

    /** P37: the page's script names one 10-minute video at [url]. */
    private fun oneVideo(url: String): String = JSONArray()
        .put(JSONObject().put("url", url).put("type", "video/mp4").put("duration", 600))
        .toString()

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

    private fun fixtureCandidate(
        page: String = FIXTURE_PAGE,
        file: String = "42.mp4",
    ): MediaCandidate = MediaCandidate(
        pageUrl = page,
        mediaUrl = "https://cdn.fixture.test/$file",
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
    )

    /** Holds the answer for some videos until the test releases it, even after cancellation. */
    private class GatedExtractor(
        private val gates: Map<String, CompletableDeferred<SiteExtractionResult>>,
        private val results: Map<String, SiteExtractionResult>,
    ) : SiteExtractor {
        override val id: String = "fixture"
        override val displayName: String = "Fixture Site"
        val asked = mutableListOf<String>()

        override fun identify(pageUrl: String): SitePageIdentity? {
            if (!pageUrl.startsWith(FIXTURE_PREFIX)) return null
            val contentId = pageUrl.removePrefix(FIXTURE_PREFIX).substringBefore('?')
            return SitePageIdentity("fixture", contentId, "$FIXTURE_PREFIX$contentId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean = false

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            val contentId = request.identity.contentId
            asked += contentId
            val gate = gates[contentId]
                ?: return results[contentId]
                    ?: SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
            return withContext(NonCancellable) { gate.await() }
        }
    }

    /** A YouTube-shaped adapter for the feed tests: watch pages only, one file per video. */
    private class FeedVideoExtractor(
        private val failing: Map<String, SiteExtractionFailure> = emptyMap(),
        private val gate: CompletableDeferred<Unit>? = null,
        /** P16: whether a stopped lookup stops here too; [cancelled] counts those. */
        private val cancellable: Boolean = false,
    ) : SiteExtractor {
        override val id: String = "youtube"
        override val displayName: String = "YouTube"
        val requests = mutableListOf<SiteExtractionRequest>()
        var cancelled = 0

        override fun identify(pageUrl: String): SitePageIdentity? {
            val videoId = WATCH.matchEntire(pageUrl)?.groupValues?.get(1) ?: return null
            return SitePageIdentity("youtube", videoId, "https://www.youtube.com/watch?v=$videoId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean = false

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            if (cancellable) {
                try {
                    gate?.await()
                } catch (cancellation: CancellationException) {
                    cancelled += 1
                    throw cancellation
                }
            } else {
                gate?.let { withContext(NonCancellable) { it.await() } }
            }
            val videoId = request.identity.contentId
            failing[videoId]?.let { return SiteExtractionResult.Failure(it) }
            val candidate = MediaCandidate(
                pageUrl = request.identity.canonicalPageUrl,
                mediaUrl = "https://cdn.fixture.test/$videoId.mp4",
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = "video/mp4",
            )
            return SiteExtractionResult.Success(listOf(candidate))
        }

        private companion object {
            val WATCH =
                Regex("https://(?:www|m)\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})(?:&.*)?")
        }
    }

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
        const val FEED_PAGE = "https://fixture.test/feed"
        const val VIDEO_A = "${FIXTURE_PREFIX}1"
        const val VIDEO_B = "${FIXTURE_PREFIX}2"
        const val VIDEO_C = "${FIXTURE_PREFIX}3"
        const val YOUTUBE_FEED = "https://m.youtube.com/"
        const val FEED_API = "https://m.youtube.com/youtubei/v1/browse"
        const val FOCUSED_VIDEO = "https://www.youtube.com/watch?v=BBBBBBBBBB2"
        const val WATCH_A = "https://m.youtube.com/watch?v=AAAAAAAAAA1"
        const val WATCH_B = "https://m.youtube.com/watch?v=BBBBBBBBBB2"
        const val PLAYER_FILE = "https://cdn.player.test/part/clip.mp4"
        const val PROBE_SETTLE_MS = 200L

        /** P24: an HLS master of two qualities, the first one 720p. */
        const val FIXTURE_MASTER = "#EXTM3U\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720\n720p/index.m3u8\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080\n1080p/index.m3u8\n"
    }
}
