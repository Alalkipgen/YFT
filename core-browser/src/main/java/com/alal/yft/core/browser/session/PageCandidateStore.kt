package com.alal.yft.core.browser.session

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PageCandidateStore(
    private val scope: CoroutineScope,
    private val normalizer: CandidateNormalizer = CandidateNormalizer(),
    private val debounceMillis: Long = 250,
    private val maxRawObservations: Int = 200,
) {
    private val lock = Any()
    private val rawCandidates = ArrayDeque<MediaCandidate>()
    private var normalizeJob: Job? = null
    private var currentPageUrl: String? = null

    private val mutableCandidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val candidates: StateFlow<List<MediaCandidate>> = mutableCandidates.asStateFlow()

    fun beginPage(pageUrl: String) {
        synchronized(lock) {
            currentPageUrl = pageUrl
            rawCandidates.clear()
            normalizeJob?.cancel()
            normalizeJob = null
            mutableCandidates.value = emptyList()
        }
    }

    /**
     * Keeps the current page's candidates under a new address of the same page, for example when
     * the site adds a parameter or a fragment to the address it shows. Only the grouping key
     * changes; anything submitted for the old address afterwards is ignored.
     */
    fun movePage(pageUrl: String) {
        synchronized(lock) {
            if (currentPageUrl == null || currentPageUrl == pageUrl) return
            currentPageUrl = pageUrl
            val moved = rawCandidates.map { it.copy(pageUrl = pageUrl) }
            rawCandidates.clear()
            rawCandidates.addAll(moved)
            mutableCandidates.value = mutableCandidates.value.map { it.copy(pageUrl = pageUrl) }
            if (normalizeJob?.isActive == true) {
                normalizeJob?.cancel()
                normalizeJob = scope.launch {
                    delay(debounceMillis)
                    publishSnapshot(pageUrl)
                }
            }
        }
    }

    fun submit(candidate: MediaCandidate) {
        synchronized(lock) {
            if (candidate.pageUrl != currentPageUrl) return
            rawCandidates.addLast(candidate)
            while (rawCandidates.size > maxRawObservations) rawCandidates.removeFirst()
            normalizeJob?.cancel()
            normalizeJob = scope.launch {
                delay(debounceMillis)
                publishSnapshot(candidate.pageUrl)
            }
        }
    }

    fun submitAll(candidates: Iterable<MediaCandidate>) {
        candidates.forEach(::submit)
    }

    private fun publishSnapshot(expectedPageUrl: String) {
        val snapshot = synchronized(lock) {
            if (currentPageUrl != expectedPageUrl) return
            rawCandidates.toList()
        }
        val normalized = normalizer.normalize(expectedPageUrl, snapshot)
        synchronized(lock) {
            if (currentPageUrl == expectedPageUrl) mutableCandidates.value = normalized
        }
    }
}
