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
import com.alal.yft.core.model.media.MediaCandidate
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
class BrowserViewModel @Inject constructor(
    okHttpClient: OkHttpClient,
) : ViewModel(), BrowserObservationSink {
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

    override fun onPageStarted(url: String) {
        activePageUrl = url
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