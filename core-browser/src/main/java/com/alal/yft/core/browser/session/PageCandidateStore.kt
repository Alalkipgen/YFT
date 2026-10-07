package com.alal.yft.core.browser.session

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageVideoFacts
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

    private val mutableFacts = MutableStateFlow<PageVideoFacts?>(null)

    /** P28: what the current page states about its own video; null until it states anything. */
    val facts: StateFlow<PageVideoFacts?> = mutableFacts.asStateFlow()

    fun beginPage(pageUrl: String) {
        synchronized(lock) {
            currentPageUrl = pageUrl
            rawCandidates.clear()
            normalizeJob?.cancel()
            normalizeJob = null
            mutableCandidates.value = emptyList()
            mutableFacts.value = null
        }
    }

    /**
     * P28: the page's [facts] as its DOM probe read them; a newer read keeps what an older one
     * found and it lacks. Facts sent for another page are ignored.
     */
    fun submitFacts(pageUrl: String, facts: PageVideoFacts?) {
        if (facts == null || facts.isEmpty) return
        synchronized(lock) {
            if (pageUrl != currentPageUrl) return
            mutableFacts.value = facts.orElse(mutableFacts.value)
        }
    }

    /**
     * Keeps the current page's candidates under a new address of the same page, for example when
     * the site adds a parameter or a fragment to the address it shows. Only the grouping key
     * changes; anything submitted for the old address afterwards is ignored. P28: the page's
     * facts stay too.
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
