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
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val clock: () -> Long = System::currentTimeMillis,
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
    ) : this(okHttpClient, siteAdapters, previewSelectionStore, System::currentTimeMillis)

    private val mutableUiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = mutableUiState.asStateFlow()

    private val candidateStore = PageCandidateStore(scope = viewModelScope)
    private val domParser = DomProbeResultParser()
    private val metadataProbe = MediaMetadataProbe(okHttpClient)
    private val probeBudget = PageProbeBudget()
    private val probePermits = Semaphore(permits = 2)
    private var pageProbeJob: Job = SupervisorJob(viewModelScope.coroutineContext[Job])

    @Volatile
    private var activePageUrl: String? = null

    @Volatile
    private var browserContext: BrowserRequestContext? = null

    init {
        viewModelScope.launch {
            candidateStore.candidates.collect { candidates ->
                mutableUiState.update { it.copy(candidates = candidates) }
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

    fun addressForLoading(): String? {
        return when (val result = BrowserAddressNormalizer.normalize(mutableUiState.value.address)) {
            is BrowserAddressResult.Valid -> {
                mutableUiState.update {
                    it.copy(address = result.url.takeUnless { url -> url == "about:blank" }.orEmpty())
                }
                result.url
            }
            is BrowserAddressResult.Invalid -> {
                mutableUiState.update { it.copy(errorMessage = result.reason) }
                null
            }
        }
    }

    fun selectForPreview(candidate: MediaCandidate): Boolean {
        if (candidate !in mutableUiState.value.candidates) return false
        previewSelectionStore.select(candidate)
        return true
    }

    override fun onPageStarted(url: String) {
        activePageUrl = url
        browserContext = null
        pageProbeJob.cancel()
        pageProbeJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        candidateStore.beginPage(url)
        probeBudget.beginPage(url)
        mutableUiState.update {
            it.copy(
                address = url.takeUnless { value -> value == "about:blank" }.orEmpty(),
                currentUrl = url,
                pageTitle = null,
                isLoading = true,
                progress = 0,
                errorMessage = null,
                candidates = emptyList(),
                siteNotice = null,
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

                is SiteAdapterOutcome.Failed -> mutableUiState.update {
                    it.copy(siteNotice = outcome.message)
                }
            }
        }
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
        if (observation.pageUrl != activePageUrl) return
        rememberBrowserContext(observation)
        BrowserObservationMapper.fromRequest(observation)?.let(candidateStore::submit)
        val probeCandidate = BrowserObservationMapper.forMetadataProbe(observation) ?: return
        scheduleProbe(probeCandidate)
    }

    override fun onDownload(observation: DownloadObservation) {
        if (observation.pageUrl != activePageUrl) return
        BrowserObservationMapper.fromDownload(observation)?.let(candidateStore::submit)
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
     * Keeps the newest session context the page actually used.
     *
     * The context stays in memory only and is never logged or persisted; adapters receive it so
     * they can read the same page the user is already authorized to see.
     */
    private fun rememberBrowserContext(observation: RequestObservation) {
        if (observation.userAgent == null && observation.cookie == null) return
        browserContext = BrowserRequestContext(
            pageUrl = observation.pageUrl,
            userAgent = observation.userAgent,
            cookie = observation.cookie,
        )
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
    }
}