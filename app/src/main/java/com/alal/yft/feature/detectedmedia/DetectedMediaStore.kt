package com.alal.yft.feature.detectedmedia

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Media found on the page the browser showed last, kept for the Detected Media screen. */
data class DetectedPage(
    val pageUrl: String,
    val pageTitle: String?,
    val candidates: List<MediaCandidate>,
)

/**
 * Memory-only copy of the browser's current-page candidates.
 *
 * Candidate URLs can carry signed tokens, so like the preview selection this never reaches a
 * route, saved state, database or log; it disappears with the process or when the user clears
 * browsing data. Only one page is kept: opening another page replaces it.
 */
@Singleton
class DetectedMediaStore @Inject constructor() {
    private val mutablePage = MutableStateFlow<DetectedPage?>(null)
    val page: StateFlow<DetectedPage?> = mutablePage.asStateFlow()
    private val mutableSelection = MutableStateFlow<MediaGroup?>(null)

    /** The video the download sheet shows (P3): a snapshot, so new finds do not move it. */
    val selection: StateFlow<MediaGroup?> = mutableSelection.asStateFlow()

    fun publish(pageUrl: String, pageTitle: String?, candidates: List<MediaCandidate>) {
        // A video chosen on another page is not this page's: the sheet must not show it.
        if (mutablePage.value?.pageUrl != pageUrl) mutableSelection.value = null
        mutablePage.value = DetectedPage(
            pageUrl = pageUrl,
            pageTitle = pageTitle,
            candidates = candidates.take(MAX_CANDIDATES),
        )
    }

    fun select(group: MediaGroup) {
        mutableSelection.value = group
    }

    fun clear() {
        mutablePage.value = null
        mutableSelection.value = null
    }

    companion object {
        const val MAX_CANDIDATES = 50
    }
}
