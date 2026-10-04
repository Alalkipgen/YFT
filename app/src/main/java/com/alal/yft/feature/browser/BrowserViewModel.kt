package com.alal.yft.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.browser.detection.BrowserObservationMapper
import com.alal.yft.core.browser.detection.DomProbeResultParser
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.MediaMetadataProbe
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import com.alal.yft.core.browser.policy.BrowserAddressResult
import com.alal.yft.core.browser.session.PageCandidateStore
import com.alal.yft.core.browser.session.PageProbeBudget
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteScope
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient

@HiltViewModel
class BrowserViewModel(
    okHttpClient: OkHttpClient,
    private val siteAdapters: SiteAdapterCoordinator,
    private val previewSelectionStore: PreviewSelectionStore = PreviewSelectionStore(),
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
        previewSelectionStore: PreviewSelectionStore,
        detectedMediaStore: DetectedMediaStore,
        homeSitesRepository: HomeSitesRepository,
    ) : this(
        okHttpClient,
        siteAdapters,
        previewSelectionStore,
        detectedMediaStore,
        System::currentTimeMillis,
        homeSitesRepository,
    )

    private val mutableUiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = mutableUiState.asStateFlow()

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
                .map { state -> Triple(state.currentUrl, state.pageTitle, state.candidates) }
                .distinctUntilChanged()
                .collect { (pageUrl, title, candidates) ->
                    if (pageUrl != null && pageUrl != BLANK_PAGE) {
                        detectedMediaStore.publish(pageUrl, title, candidates)
                    }
                }
        }
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

    fun selectForPreview(candidate: MediaCandidate): Boolean {
        if (candidate !in mutableUiState.value.candidates) return false
        previewSelectionStore.select(candidate)
        return true
    }

    override fun onPageStarted(url: String) {
        activePageUrl = url
        browserContext = null
        awaitingPlayback = false
        autoRetried = false
        pageProbeJob.cancel()
        pageProbeJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        candidateStore.beginPage(url)
        probeBudget.beginPage(url)
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
            )
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
        runSiteAdapters(url, title)
    }

    /**
     * Consults the site adapters once the page has settled.
     *
     * The adapter runs after load so the browser session the user already has is available, and
     * its candidates are merged into the same page store as generic observations. A failure only
     * adds a notice: generic detection keeps running so one stale adapter cannot hide media the
     * page exposes anyway.
     */
    private fun runSiteAdapters(pageUrl: String, title: String?) {
        viewModelScope.launch(pageProbeJob) {
            val outcome = siteAdapters.inspect(
                pageUrl = pageUrl,
                requestContext = browserContext ?: BrowserRequestContext(pageUrl, null, null),
                nowEpochMs = clock(),
            )
            if (pageUrl != activePageUrl) return@launch
            if (outcome !is SiteAdapterOutcome.Failed) {
                awaitingPlayback = false
                mutableUiState.update { it.copy(siteNotice = null, canRetrySiteLookup = false) }
            }
            when (outcome) {
                SiteAdapterOutcome.NotHandled -> Unit

                is SiteAdapterOutcome.Detected -> candidateStore.submitAll(
                    outcome.candidates.map { candidate ->
                        if (candidate.title != null) {
                            candidate
                        } else {
                            candidate.copy(title = title?.trim()?.take(MAX_TITLE_LENGTH))
                        }
                    },
                )

                is SiteAdapterOutcome.Failed -> {
                    // One automatic retry per page; after that the user decides with Try again.
                    awaitingPlayback = outcome.retriesAfterPlayback && !autoRetried
                    mutableUiState.update {
                        it.copy(
                            siteNotice = outcome.message,
                            canRetrySiteLookup = outcome.canRetry,
                        )
                    }
                }
            }
        }
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
        mutableUiState.update {
            it.copy(siteNotice = RETRY_NOTICE, canRetrySiteLookup = false)
        }
        runSiteAdapters(pageUrl, mutableUiState.value.pageTitle)
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
        viewModelScope.launch(pageProbeJob) {
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

    private companion object {
        const val MAX_ADDRESS_LENGTH = 2_048
        const val MAX_TITLE_LENGTH = 200
        const val BLANK_PAGE = "about:blank"
        const val RETRY_NOTICE = "Checking this page again…"
    }
}