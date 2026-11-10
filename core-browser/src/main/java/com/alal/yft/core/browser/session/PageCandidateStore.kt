package com.alal.yft.core.browser.session

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.core.model.media.StreamPieces
import com.alal.yft.extractor.generic.classifier.MediaFileUrls
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The current page's files, from every observation of them: the player's requests, the page's
 * markup, probes and manifest reads, at most [maxRawObservations] of them.
 *
 * Stream piece filter: a stream's pieces never crowd out the page's files. A piece of a stream
 * whose playlist was read ([StreamPieces]) is not kept, the same request asked again (a live
 * playlist's refresh) replaces its earlier record, and when the records are full a numbered
 * stream's piece goes before anything else.
 */
class PageCandidateStore(
    private val scope: CoroutineScope,
    private val normalizer: CandidateNormalizer = CandidateNormalizer(),
    private val debounceMillis: Long = 250,
    private val maxRawObservations: Int = 200,
) {
    private val lock = Any()
    private val rawCandidates = ArrayDeque<MediaCandidate>()
    private val streams = mutableListOf<StreamPieces>()
    private var normalizeJob: Job? = null
    private var currentPageUrl: String? = null

    private val mutableCandidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val candidates: StateFlow<List<MediaCandidate>> = mutableCandidates.asStateFlow()

    private val mutableFacts = MutableStateFlow<PageVideoFacts?>(null)

    /** What the current page states about its own video; null until it states anything. */
    val facts: StateFlow<PageVideoFacts?> = mutableFacts.asStateFlow()

    fun beginPage(pageUrl: String) {
        synchronized(lock) {
            currentPageUrl = pageUrl
            rawCandidates.clear()
            streams.clear()
            normalizeJob?.cancel()
            normalizeJob = null
            mutableCandidates.value = emptyList()
            mutableFacts.value = null
        }
    }

    /**
     * The page's [facts] as its DOM probe read them; a newer read keeps what an older one
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
     * changes; anything submitted for the old address afterwards is ignored. The page's
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
            candidate.streamPieces?.takeIf { it !in streams }?.let(streams::add)
            if (StreamPieces.isPiece(candidate, streams)) return
            val same = rawCandidates.indexOfLast { it.sameRecordAs(candidate) }
            if (same >= 0) {
                rawCandidates[same] = candidate
            } else {
                rawCandidates.addLast(candidate)
                while (rawCandidates.size > maxRawObservations) evictOne()
            }
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

    /**
     * Stream piece filter: whether the player's request [candidate] is a stream's piece, so it
     * needs no probe of its own: a piece of a stream whose playlist was read, or one more
     * numbered piece of a series the page already has.
     */
    fun isStreamPiece(candidate: MediaCandidate): Boolean = synchronized(lock) {
        if (candidate.pageUrl != currentPageUrl) return false
        if (StreamPieces.isPiece(candidate, streams)) return true
        if (candidate.kind != MediaKind.DIRECT) return false
        val family = MediaFileUrls.segmentFamily(candidate.mediaUrl) ?: return false
        val others = rawCandidates.count { other ->
            other.mediaUrl != candidate.mediaUrl &&
                MediaFileUrls.segmentFamily(other.mediaUrl) == family
        }
        others >= normalizer.policy.minStreamPieces - 1
    }

    /** The oldest piece of a stream when the records hold one, else the oldest record. */
    private fun evictOne() {
        val families = rawCandidates.mapNotNull { candidate ->
            candidate.takeIf { it.kind == MediaKind.DIRECT }
                ?.let { MediaFileUrls.segmentFamily(it.mediaUrl) }
        }.groupingBy { it }.eachCount()
        val piece = rawCandidates.indexOfFirst { candidate ->
            val family = candidate.takeIf { it.kind == MediaKind.DIRECT }
                ?.let { MediaFileUrls.segmentFamily(it.mediaUrl) }
            StreamPieces.isPiece(candidate, streams) ||
                family != null && (families[family] ?: 0) >= normalizer.policy.minStreamPieces
        }
        if (piece >= 0) rawCandidates.removeAt(piece) else rawCandidates.removeFirst()
    }

    /** The same observation again: only the time it was made differs. */
    private fun MediaCandidate.sameRecordAs(other: MediaCandidate): Boolean =
        mediaUrl == other.mediaUrl && copy(observedAtEpochMs = other.observedAtEpochMs) == other

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
