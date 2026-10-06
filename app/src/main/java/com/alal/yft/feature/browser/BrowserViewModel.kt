package com.alal.yft.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.browser.detection.BrowserObservationMapper
import com.alal.yft.core.browser.detection.DomProbeResultParser
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.FocusedVideoProbe
import com.alal.yft.core.browser.detection.MediaMetadataProbe
import com.alal.yft.core.browser.detection.PlayingVideoProbe
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import com.alal.yft.core.browser.policy.BrowserAddressResult
import com.alal.yft.core.browser.session.PageCandidateStore
import com.alal.yft.core.browser.session.PageProbeBudget
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.detection.SiteScope
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.detectedmedia.PageVideoLookup
import com.alal.yft.ui.components.isSavable
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient

@HiltViewModel
class BrowserViewModel(
    okHttpClient: OkHttpClient,
    private val siteAdapters: SiteAdapterCoordinator,
    private val detectedMediaStore: DetectedMediaStore = DetectedMediaStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val homeSitesRepository: HomeSitesRepository? = null,
) : ViewModel(), BrowserObservationSink {
    /**
     * Production entry point. Dagger has no sensible binding for the test clock lambda, so the
     * injected constructor pins it to the wall clock and only tests override it.
     */
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        siteAdapters: SiteAdapterCoordinator,
        detectedMediaStore: DetectedMediaStore,
        homeSitesRepository: HomeSitesRepository,
    ) : this(
        okHttpClient,
        siteAdapters,
        detectedMediaStore,
        System::currentTimeMillis,
        homeSitesRepository,
    )

    private val mutableUiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = mutableUiState.asStateFlow()

    private val quickDownloads = Channel<Unit>(Channel.CONFLATED)

    /** P5: one event each time the video in focus on a feed was found and its sheet may open. */
    val quickDownloadRequests: Flow<Unit> = quickDownloads.receiveAsFlow()

    /** The running lookup of the video in focus; a new tap replaces it. */
    private var focusLookup: Job? = null
    private var focusNoticeTimer: Job? = null

    private val candidateStore = PageCandidateStore(scope = viewModelScope)
    private val domParser = DomProbeResultParser()
    private val metadataProbe = MediaMetadataProbe(okHttpClient)
    private val probeBudget = PageProbeBudget()
    private val probePermits = Semaphore(permits = 2)
    private var pageProbeJob: Job = SupervisorJob(viewModelScope.coroutineContext[Job])

    private var initialLinkHandled = false

    @Volatile
    private var activePageUrl: String? = null

    @Volatile
    private var browserContext: BrowserRequestContext? = null

    /** The page's lookup waits for the site's own player to fetch media before trying again. */
    @Volatile
    private var awaitingPlayback = false

    /** Whether this page already had its one automatic retry. */
    @Volatile
    private var autoRetried = false

    /** Counts page scopes, so a lookup that finishes after the page changed is dropped. */
    @Volatile
    private var pageGeneration = 0L

    /** Whether the site adapters were already asked about the current page scope. */
    @Volatile
    private var siteLookupStarted = false

    private var focusedRetryPage: String? = null

    /**
     * P12: this page's lookups by video key ("site:contentId"), so the page's own lookup, the
     * Download button and a feed's focused link never ask the adapter twice for one video: a
     * second ask joins the running lookup or takes the one that found the video.
     */
    private val runningLookups = mutableMapOf<String, Deferred<SiteAdapterOutcome>>()
    private val foundLookups = mutableMapOf<String, SiteAdapterOutcome.Detected>()

    /** P12: completes when the page's own lookup ends, true when it found anything. */
    private var pageLookupDone = CompletableDeferred<Boolean>()

    /** P12: the page's own video once its lookup found it. */
    private var foundPageVideo: MediaGroup? = null

    /** P12: the sheet opened before the page's lookup ended and waits for its video. */
    private var sheetAwaitsPageVideo = false

    /** P12: the main-video script was evaluated and its answer has not come back yet. */
    private var mainVideoTimer: Job? = null

    init {
        homeSitesRepository?.let { repository ->
            viewModelScope.launch {
                repository.sites.collect { sites ->
                    mutableUiState.update { it.copy(sites = sites) }
                }
            }
        }
        viewModelScope.launch {
            candidateStore.candidates.collect { candidates ->
                mutableUiState.update { it.copy(candidates = candidates) }
            }
        }
        viewModelScope.launch {
            // Mirrors the current page for the Detected Media screen. A fresh browser has no page
            // yet, so the list from the previous visit stays until another page starts.
            uiState
                .map { state ->
                    PublishedPage(
                        url = state.currentUrl,
                        title = state.pageTitle,
                        candidates = state.candidates,
                        sitePage = state.sitePage,
                    )
                }
                .distinctUntilChanged()
                .collect { page ->
                    val pageUrl = page.url
                    if (pageUrl != null && pageUrl != BLANK_PAGE) {
                        detectedMediaStore.publish(
                            pageUrl,
                            page.title,
                            page.candidates,
                            adapterSite = page.sitePage,
                        )
                    }
                }
        }
        viewModelScope.launch {
            // P12: Try again in the sheet that waits on this page's own lookup.
            detectedMediaStore.lookupRetries.collect { key ->
                val pageUrl = activePageUrl ?: return@collect
                val lookup = detectedMediaStore.lookup.value
                if (lookup?.key != key || !lookup.canRetry) return@collect
                if (siteAdapters.videoKey(pageUrl) != key) return@collect
                retryLookup(pageUrl)
            }
        }
        viewModelScope.launch {
            // P12: the sheet's "Other videos on this page" opens the found list.
            detectedMediaStore.foundList.collect {
                mutableUiState.update { it.copy(foundListRequest = it.foundListRequest + 1) }
            }
        }
    }

    override fun onCleared() {
        // The sheet must not wait on a lookup of a browser that is gone.
        detectedMediaStore.showLookup(null)
        super.onCleared()
    }

    fun onAddressChanged(value: String) {
        mutableUiState.update {
            it.copy(
                address = value.take(MAX_ADDRESS_LENGTH),
                errorMessage = null,
            )
        }
    }

    /** The page to load for the address field; words become a web search (T13). */
    fun addressForLoading(): String? {
        val typed = mutableUiState.value.address
        val address = BrowserSearch.wordsOrNull(typed)?.let(BrowserSearch::webUrl) ?: typed
        return when (val result = BrowserAddressNormalizer.normalize(address)) {
            is BrowserAddressResult.Valid -> {
                mutableUiState.update {
                    it.copy(
                        address = result.url.takeUnless { url -> url == BLANK_PAGE }.orEmpty(),
                    )
                }
                result.url
            }
            is BrowserAddressResult.Invalid -> {
                mutableUiState.update { it.copy(errorMessage = result.reason) }
                null
            }
        }
    }

    /**
     * Loads the link Home handed over, once per browser session, so returning to the browser or
     * recreating its view never reloads it. Returns the address to load, or null when the link
     * was already handled or is not a valid address (the reason is shown like a typed one).
     */
    fun openInitialLink(link: String): String? {
        if (initialLinkHandled) return null
        initialLinkHandled = true
        onAddressChanged(link)
        return addressForLoading()
    }

    /**
     * Hands [video] to the download sheet. Returns false when the page no longer lists every
     * one of its candidates, or one carries a DRM hint, so nothing opens.
     */
    fun selectForDownload(video: MediaGroup): Boolean {
        val listed = mutableUiState.value.candidates
        if (video.candidates.any { it !in listed || it.drmHint == true }) return false
        detectedMediaStore.select(video)
        return true
    }

    override fun onPageStarted(url: String) {
        beginPageScope(url)
        browserContext = null
        scheduleEarlySiteLookup(url)
        mutableUiState.update {
            it.copy(
                address = url.takeUnless { value -> value == BLANK_PAGE }.orEmpty(),
                currentUrl = url,
                pageTitle = null,
                isLoading = true,
                progress = 0,
                errorMessage = null,
                candidates = emptyList(),
                siteNotice = null,
                canRetrySiteLookup = false,
            ).withFeedOf(url)
        }
    }

    override fun onPageFinished(url: String, title: String?) {
        if (url != activePageUrl) return
        mutableUiState.update {
            it.copy(
                pageTitle = title?.trim()?.take(MAX_TITLE_LENGTH)?.takeIf(String::isNotEmpty),
                isLoading = false,
                progress = 100,
            )
        }
        if (!siteLookupStarted) runSiteAdapters(url, title)
    }

    /**
     * Follows an address the page changed by itself, such as YouTube's mobile site opening a
     * video from its feed with `history.pushState` (P1).
     *
     * Another video is a new page: its candidates, notice and retry state start empty, the old
     * page's lookups are cancelled, and the site adapters are asked once the address has stayed
     * the same for [IN_PAGE_LOOKUP_DELAY_MS], so scrolling through several videos does not start
     * a lookup for each. The same page under a new address (a fragment, or the same post with
     * another parameter) keeps what was already found.
     */
    override fun onUrlChanged(url: String) {
        val previous = activePageUrl
        if (url == previous) return
        if (previous != null && isSamePage(previous, url)) {
            activePageUrl = url
            candidateStore.movePage(url)
            probeBudget.movePage(url)
            browserContext = browserContext?.copy(pageUrl = url)
            mutableUiState.update {
                it.copy(
                    address = url.takeUnless { value -> value == BLANK_PAGE }.orEmpty(),
                    currentUrl = url,
                ).withFeedOf(url, samePage = true)
            }
            return
        }
        // The site's session carries over to its next page; another site's never does.
        val siteContext = browserContext?.takeIf { context ->
            context.pageUrl?.let { previousPage -> isSameSite(url, previousPage) } == true
        }
        beginPageScope(url)
        browserContext = siteContext?.copy(pageUrl = url)
        mutableUiState.update {
            it.copy(
                address = url.takeUnless { value -> value == BLANK_PAGE }.orEmpty(),
                currentUrl = url,
                pageTitle = null,
                errorMessage = null,
                candidates = emptyList(),
                siteNotice = null,
                canRetrySiteLookup = false,
            ).withFeedOf(url)
        }
        val generation = pageGeneration
        viewModelScope.launch(pageProbeJob) {
            delay(IN_PAGE_LOOKUP_DELAY_MS)
            if (generation == pageGeneration && !siteLookupStarted) runSiteAdapters(url, null)
        }
    }

    /**
     * Asks the site adapters about a video page before it finished loading (P2): heavy pages such
     * as Facebook's keep loading long after the video is known, and the Download button should not
     * wait for them. A page that finishes first runs the lookup itself, and only once.
     */
    private fun scheduleEarlySiteLookup(url: String) {
        if (!siteAdapters.handles(url)) return
        val generation = pageGeneration
        viewModelScope.launch(pageProbeJob) {
            delay(EARLY_LOOKUP_DELAY_MS)
            if (generation == pageGeneration && !siteLookupStarted) runSiteAdapters(url, null)
        }
    }

    /**
     * P5: the script that finds the video in focus, for the Download button on a YouTube,
     * Facebook or TikTok page. Null on every other site, so no script runs there.
     */
    fun focusedVideoScript(): String? {
        val pageUrl = activePageUrl ?: return null
        if (!FocusedVideoProbe.isFeedSite(pageUrl)) return null
        focusNoticeTimer?.cancel()
        mutableUiState.update {
            it.copy(
                findingFocusedVideo = true,
                focusNotice = FINDING_NOTICE,
                canRetryFocusedLookup = false,
            )
        }
        // A page that never answers the script must not leave the button waiting.
        val generation = pageGeneration
        focusNoticeTimer = viewModelScope.launch {
            delay(FOCUS_SCRIPT_TIMEOUT_MS)
            if (generation == pageGeneration && focusLookup?.isActive != true) {
                finishFocusLookup(NO_FOCUSED_VIDEO_NOTICE)
            }
        }
        return FocusedVideoProbe.script
    }

    /**
     * P5: looks up the video the [focusedVideoScript] found with its site adapter and asks the
     * route to open its download sheet. A page with no video in focus, or a video the adapter
     * cannot read, gets a short notice instead. A result for a page the browser left is dropped.
     */
    fun onFocusedVideoResult(javascriptResult: String?) {
        if (!mutableUiState.value.findingFocusedVideo) return
        val pageUrl = activePageUrl ?: return
        focusNoticeTimer?.cancel()
        focusLookup?.cancel()
        val focused = FocusedVideoProbe.parse(javascriptResult, pageUrl)
            ?.takeIf { siteAdapters.handles(it.url) }
        if (focused == null) {
            finishFocusLookup(NO_FOCUSED_VIDEO_NOTICE)
            return
        }
        lookupFocusedVideo(focused.url)
    }

    fun retryFocusedLookup() {
        val url = focusedRetryPage ?: return
        if (!mutableUiState.value.canRetryFocusedLookup || focusLookup?.isActive == true) return
        mutableUiState.update {
            it.copy(
                findingFocusedVideo = true,
                focusNotice = FINDING_NOTICE,
                canRetryFocusedLookup = false,
            )
        }
        lookupFocusedVideo(url, fresh = true)
    }

    private fun lookupFocusedVideo(url: String, fresh: Boolean = false) {
        val generation = pageGeneration
        focusLookup = viewModelScope.launch(pageProbeJob) {
            // The site's own session reads the video's page as the feed did; another's never.
            val context = browserContext?.takeIf { context ->
                context.pageUrl?.let { page -> isSameSite(url, page) } == true
            }
            // P12: a link of a video this page already looks up, or found, joins that lookup.
            val outcome = lookUp(
                url = url,
                requestContext = context?.copy(pageUrl = url)
                    ?: BrowserRequestContext(url, null, null),
                fresh = fresh,
            )
            if (generation != pageGeneration) return@launch
            when (outcome) {
                is SiteAdapterOutcome.Detected -> {
                    val video = MediaGroups.pageVideos(outcome.candidates.filter { it.isSavable })
                        .firstOrNull()
                    if (video == null) {
                        finishFocusLookup(PROTECTED_FOCUSED_VIDEO_NOTICE)
                    } else {
                        detectedMediaStore.select(video)
                        finishFocusLookup(notice = null)
                        quickDownloads.trySend(Unit)
                    }
                }

                is SiteAdapterOutcome.Failed -> {
                    focusedRetryPage = url
                    mutableUiState.update {
                        it.copy(
                            canRetryFocusedLookup = outcome.reason == SiteExtractionFailure.NETWORK,
                        )
                    }
                    finishFocusLookup(outcome.message)
                }
                SiteAdapterOutcome.NotHandled -> finishFocusLookup(NO_FOCUSED_VIDEO_NOTICE)
            }
        }
    }

    /** Ends the focused-video lookup with [notice], which clears itself after a few seconds. */
    private fun finishFocusLookup(notice: String?) {
        focusNoticeTimer?.cancel()
        mutableUiState.update { it.copy(findingFocusedVideo = false, focusNotice = notice) }
        if (notice == null || mutableUiState.value.canRetryFocusedLookup) return
        focusNoticeTimer = viewModelScope.launch {
            delay(FOCUS_NOTICE_MS)
            mutableUiState.update { state ->
                if (state.focusNotice == notice) state.copy(focusNotice = null) else state
            }
        }
    }

    /**
     * The page's feed state (P5): whether the Download button may look for the video in focus,
     * and whether the page is a feed rather than one video's own page. A new page forgets the
     * previous page's focused-video lookup and notice; the same page under a new address keeps
     * them.
     */
    private fun BrowserUiState.withFeedOf(
        url: String,
        samePage: Boolean = false,
    ): BrowserUiState {
        val feedSite = FocusedVideoProbe.isFeedSite(url)
        val sitePage = siteAdapters.handles(url)
        return copy(
            findsFocusedVideo = feedSite,
            feedPage = feedSite && !sitePage,
            sitePage = sitePage,
            pageLookupRunning = pageLookupRunning && samePage,
            findingFocusedVideo = findingFocusedVideo && samePage,
            focusNotice = focusNotice.takeIf { samePage },
            canRetryFocusedLookup = canRetryFocusedLookup && samePage,
        )
    }

    /** Starts an empty scope for a new page and cancels everything the previous page started. */
    private fun beginPageScope(url: String) {
        pageGeneration++
        activePageUrl = url
        awaitingPlayback = false
        autoRetried = false
        siteLookupStarted = false
        focusedRetryPage = null
        pageProbeJob.cancel()
        pageProbeJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        // The focused-video lookup ran in the old page's job; its notice goes with the page.
        focusLookup = null
        focusNoticeTimer?.cancel()
        // P12: the old page's lookups and the sheet's wait for its video end with it.
        runningLookups.clear()
        foundLookups.clear()
        pageLookupDone = CompletableDeferred()
        foundPageVideo = null
        sheetAwaitsPageVideo = false
        mainVideoTimer = null
        detectedMediaStore.showLookup(null)
        candidateStore.beginPage(url)
        probeBudget.beginPage(url)
    }

    private fun isSamePage(previous: String, next: String): Boolean =
        previous.substringBefore('#') == next.substringBefore('#') ||
            siteAdapters.sameContent(previous, next)

    /**
     * Consults the site adapters once the page has settled.
     *
     * The adapter runs after load so the browser session the user already has is available, and
     * its candidates are merged into the same page store as generic observations. A failure only
     * adds a notice: generic detection keeps running so one stale adapter cannot hide media the
     * page exposes anyway.
     */
    private fun runSiteAdapters(pageUrl: String, title: String?, fresh: Boolean = false) {
        siteLookupStarted = true
        val generation = pageGeneration
        // P12: the sheet can open on this lookup and wait for it; the button shows a spinner.
        val key = siteAdapters.videoKey(pageUrl)
        if (key != null) {
            detectedMediaStore.showLookup(PageVideoLookup(key, pageUrl, lookupTitle(title)))
            mutableUiState.update { it.copy(pageLookupRunning = true) }
        }
        viewModelScope.launch(pageProbeJob) {
            val outcome = lookUp(
                url = pageUrl,
                requestContext = browserContext ?: BrowserRequestContext(pageUrl, null, null),
                fresh = fresh,
            )
            if (generation != pageGeneration) return@launch
            // The same page may have changed its address meanwhile; group under the live one.
            val livePageUrl = activePageUrl ?: return@launch
            mutableUiState.update { it.copy(pageLookupRunning = false) }
            if (outcome !is SiteAdapterOutcome.Failed) {
                awaitingPlayback = false
                mutableUiState.update { it.copy(siteNotice = null, canRetrySiteLookup = false) }
            }
            when (outcome) {
                SiteAdapterOutcome.NotHandled -> {
                    detectedMediaStore.showLookup(null)
                    pageLookupDone.complete(false)
                }

                is SiteAdapterOutcome.Detected -> {
                    // An early lookup has no title yet; the page may have one by now.
                    val pageTitle = lookupTitle(title)
                    val found = outcome.candidates.map { candidate ->
                        val titled = if (candidate.title != null) {
                            candidate
                        } else {
                            candidate.copy(title = pageTitle)
                        }
                        if (titled.pageUrl == livePageUrl) {
                            titled
                        } else {
                            titled.copy(pageUrl = livePageUrl)
                        }
                    }
                    candidateStore.submitAll(found)
                    showPageVideo(key, livePageUrl, pageTitle, found)
                }

                is SiteAdapterOutcome.Failed -> {
                    // One automatic retry per page; after that the user decides with Try again.
                    awaitingPlayback = outcome.retriesAfterPlayback && !autoRetried
                    mutableUiState.update {
                        it.copy(
                            siteNotice = outcome.message.takeUnless {
                                outcome.reason == SiteExtractionFailure.NETWORK
                            },
                            canRetrySiteLookup = outcome.canRetry,
                        )
                    }
                    // P12: the sheet shows why, with Try again, instead of the page's files.
                    key?.let {
                        detectedMediaStore.showLookup(
                            PageVideoLookup(
                                key = it,
                                pageUrl = livePageUrl,
                                title = lookupTitle(title),
                                failure = outcome.message,
                                canRetry = outcome.canRetry,
                            ),
                        )
                    }
                    pageLookupDone.complete(false)
                }
            }
        }
    }

    private fun lookupTitle(title: String?): String? =
        (title ?: mutableUiState.value.pageTitle)?.trim()?.take(MAX_TITLE_LENGTH)
            ?.takeIf(String::isNotEmpty)

    /**
     * P12: the page's lookup found its video; a sheet that waits for it shows it now. A find
     * with nothing savable (only protected media) is the sheet's failure, without Try again.
     */
    private fun showPageVideo(
        key: String?,
        pageUrl: String,
        title: String?,
        found: List<MediaCandidate>,
    ) {
        pageLookupDone.complete(found.isNotEmpty())
        if (key == null) return
        val video = MediaGroups.pageVideos(found.filter { it.isSavable }, adapterSite = true)
            .firstOrNull()
        if (video == null) {
            detectedMediaStore.showLookup(
                PageVideoLookup(key, pageUrl, title, failure = PROTECTED_FOCUSED_VIDEO_NOTICE),
            )
            return
        }
        foundPageVideo = video
        if (sheetAwaitsPageVideo) {
            sheetAwaitsPageVideo = false
            detectedMediaStore.select(video)
        }
        detectedMediaStore.showLookup(null)
    }

    /**
     * P12: one adapter lookup per video in this page scope. A second ask joins the running
     * lookup of the same video key or takes the outcome that found the video; [fresh] (Try
     * again) skips a finished result but still joins a running lookup, so one video is never
     * asked for twice at once.
     */
    private suspend fun lookUp(
        url: String,
        requestContext: BrowserRequestContext,
        fresh: Boolean,
    ): SiteAdapterOutcome {
        val key = siteAdapters.videoKey(url) ?: url
        if (!fresh) foundLookups[key]?.let { return it }
        val lookup = runningLookups[key]?.takeIf { it.isActive }
            ?: viewModelScope.async(pageProbeJob) {
                siteAdapters.inspect(
                    pageUrl = url,
                    requestContext = requestContext,
                    nowEpochMs = clock(),
                )
            }.also { runningLookups[key] = it }
        val outcome = lookup.await()
        if (runningLookups[key] === lookup) runningLookups.remove(key)
        if (outcome is SiteAdapterOutcome.Detected) {
            foundLookups[key] = outcome
        } else {
            foundLookups.remove(key)
        }
        return outcome
    }

    /**
     * P12: the Download button on a site's video page means this video. The sheet opens with
     * the video when the page's lookup found it; while the lookup runs (it starts now when the
     * page has not asked yet) the sheet waits for that same lookup, and after a failure it shows
     * the message with Try again. Returns whether the sheet opens.
     */
    fun openPageVideo(): Boolean {
        val pageUrl = activePageUrl ?: return false
        if (siteAdapters.videoKey(pageUrl) == null) return false
        val savable = mutableUiState.value.candidates.filter { it.isSavable }
        val video = MediaGroups.pageVideos(savable, adapterSite = true).firstOrNull()
            ?: foundPageVideo
        if (video != null) {
            detectedMediaStore.select(video)
            return true
        }
        detectedMediaStore.awaitPageVideo()
        sheetAwaitsPageVideo = true
        if (!siteLookupStarted) runSiteAdapters(pageUrl, mutableUiState.value.pageTitle)
        return true
    }

    /**
     * P12: on a page with several videos and no adapter, the Download button opens the main
     * one. This script asks the page which video plays; its answer goes to
     * [onPlayingVideoResult], and a page that does not answer soon gets its largest video.
     */
    fun mainVideoScript(): String {
        mainVideoTimer?.cancel()
        mainVideoTimer = viewModelScope.launch(pageProbeJob) {
            delay(MAIN_VIDEO_SCRIPT_TIMEOUT_MS)
            openMainVideo(playingUrl = null)
        }
        return PlayingVideoProbe.script
    }

    fun onPlayingVideoResult(javascriptResult: String?) {
        val timer = mainVideoTimer ?: return
        timer.cancel()
        openMainVideo(PlayingVideoProbe.parse(javascriptResult))
    }

    /** Selects the page's main video with the count of the others and opens its sheet. */
    private fun openMainVideo(playingUrl: String?) {
        mainVideoTimer = null
        val videos = MediaGroups.pageVideos(mutableUiState.value.candidates.filter { it.isSavable })
        val main = MediaGroups.mainVideo(videos, playingUrl) ?: return
        detectedMediaStore.select(main, otherVideos = videos.size - 1)
        quickDownloads.trySend(Unit)
    }

    /**
     * Asks the site adapters again for the current page, from the notice's Try again.
     *
     * The lookup carries the browser's newest session, so a site that let its own player
     * through, for example after a bot check, may now answer the lookup too.
     */
    fun retrySiteLookup() {
        val pageUrl = activePageUrl ?: return
        if (!mutableUiState.value.canRetrySiteLookup) return
        retryLookup(pageUrl)
    }

    private fun retryLookup(pageUrl: String) {
        awaitingPlayback = false
        mutableUiState.update { state ->
            state.copy(
                siteNotice = RETRY_NOTICE.takeIf { state.siteNotice != null },
                canRetrySiteLookup = false,
            )
        }
        runSiteAdapters(pageUrl, mutableUiState.value.pageTitle, fresh = true)
    }

    override fun onProgressChanged(progress: Int) {
        val bounded = progress.coerceIn(0, 100)
        mutableUiState.update {
            it.copy(
                progress = bounded,
                isLoading = it.currentUrl != null && bounded < 100,
            )
        }
    }

    override fun onRequest(observation: RequestObservation) {
        // WebView calls this concurrently off-main. Serialize with navigation and job ownership.
        viewModelScope.launch {
            if (observation.pageUrl != activePageUrl) return@launch
            rememberBrowserContext(observation)
            retryAfterPlayback(observation)
            BrowserObservationMapper.fromRequest(observation)?.let(candidateStore::submit)
            val probeCandidate = BrowserObservationMapper.forMetadataProbe(observation)
                ?: return@launch
            scheduleProbe(probeCandidate)
        }
    }

    override fun onDownload(observation: DownloadObservation) {
        viewModelScope.launch {
            if (observation.pageUrl != activePageUrl) return@launch
            BrowserObservationMapper.fromDownload(observation)?.let(candidateStore::submit)
        }
    }

    override fun onDomProbeResult(pageUrl: String, result: String?) {
        if (pageUrl != activePageUrl) return
        candidateStore.submitAll(
            domParser.parse(
                pageUrl = pageUrl,
                javascriptResult = result,
                observedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override fun onMainFrameError(url: String?, description: String) {
        if (url != null && url != activePageUrl) return
        mutableUiState.update {
            it.copy(
                isLoading = false,
                errorMessage = "Page could not be loaded. Check the address and connection.",
            )
        }
    }

    /**
     * Retries a lookup that waits for playback once the site's own player fetched media.
     *
     * Only the matched adapter can tell its site's media requests apart; the request itself is
     * never replayed.
     */
    private fun retryAfterPlayback(observation: RequestObservation) {
        if (!awaitingPlayback) return
        if (!siteAdapters.isPlayerMediaRequest(observation.pageUrl, observation.requestUrl)) {
            return
        }
        autoRetried = true
        retryLookup(observation.pageUrl)
    }

    /**
     * Keeps the newest session context the page actually used.
     *
     * The context stays in memory only and is never logged or persisted; adapters receive it so
     * they can read the same page the user is already authorized to see.
     */
    private fun rememberBrowserContext(observation: RequestObservation) {
        if (observation.userAgent == null && observation.cookie == null) return
        // Only the page's own site set the cookie a lookup may replay to it: another site's
        // cookie is never kept, and neither another site's request nor a cookieless request
        // erases the page's cookie.
        val ownSite = isSameSite(observation.requestUrl, observation.pageUrl)
        val previous = browserContext
        if (!ownSite && previous != null) return
        browserContext = BrowserRequestContext(
            pageUrl = observation.pageUrl,
            userAgent = observation.userAgent ?: previous?.userAgent,
            cookie = observation.cookie.takeIf { ownSite } ?: previous?.cookie,
        )
    }

    private fun isSameSite(requestUrl: String, pageUrl: String): Boolean {
        val request = runCatching { URI(requestUrl).host }.getOrNull() ?: return false
        val page = runCatching { URI(pageUrl).host }.getOrNull() ?: return false
        return SiteScope.sameSite(request, page)
    }

    private fun scheduleProbe(candidate: MediaCandidate) {
        if (!probeBudget.tryAcquire(candidate.pageUrl, candidate.mediaUrl)) return
        val sitePage = activePageUrl?.let(siteAdapters::handles) == true
        val lookupDone = pageLookupDone
        viewModelScope.launch(pageProbeJob) {
            // P12: on a site's video page its adapter's lookup goes first on the shared line;
            // the probes run afterwards only when it found nothing.
            if (sitePage && lookupDone.await()) return@launch
            probePermits.withPermit {
                when (val result = metadataProbe.probe(candidate)) {
                    is MediaMetadataProbe.Result.Detected -> candidateStore.submit(result.candidate)
                    is MediaMetadataProbe.Result.Failed,
                    is MediaMetadataProbe.Result.NotMedia,
                    -> Unit
                }
            }
        }
    }

    /** What the Detected Media screen mirrors of the current page. */
    private data class PublishedPage(
        val url: String?,
        val title: String?,
        val candidates: List<MediaCandidate>,
        val sitePage: Boolean,
    )

    private companion object {
        const val MAX_ADDRESS_LENGTH = 2_048
        const val MAX_TITLE_LENGTH = 200
        const val BLANK_PAGE = "about:blank"
        const val RETRY_NOTICE = "Checking this page again…"
        const val FINDING_NOTICE = "Finding the video on screen…"
        const val NO_FOCUSED_VIDEO_NOTICE =
            "No video on screen to download. Scroll to a video and tap Download again."
        const val PROTECTED_FOCUSED_VIDEO_NOTICE =
            "This video is protected, so YFT can't save it."

        /** How long a focused-video notice stays before it clears itself. */
        const val FOCUS_NOTICE_MS = 4_000L

        /** How long the page has to answer the focused-video script. */
        const val FOCUS_SCRIPT_TIMEOUT_MS = 5_000L

        /** P12: how long a page has to say which of its videos plays. */
        const val MAIN_VIDEO_SCRIPT_TIMEOUT_MS = 1_000L

        /** How long an in-page address must stay before the site adapters are asked about it. */
        const val IN_PAGE_LOOKUP_DELAY_MS = 500L

        /** How long a loading video page waits for its own finish before the lookup starts. */
        const val EARLY_LOOKUP_DELAY_MS = 1_500L
    }
}