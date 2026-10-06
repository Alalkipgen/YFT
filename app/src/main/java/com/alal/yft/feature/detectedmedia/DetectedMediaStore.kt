package com.alal.yft.feature.detectedmedia

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Media found on the page the browser showed last, kept for the Detected Media screen. */
data class DetectedPage(
    val pageUrl: String,
    val pageTitle: String?,
    val candidates: List<MediaCandidate>,
    /** P12: an adapter reads this page's video, so only its named video counts. */
    val adapterSite: Boolean = false,
)

/**
 * P12: the lookup of a site page's own video, while it runs or after it failed, so the download
 * sheet can open for that video before the lookup ends. [key] is the video's "site:contentId".
 */
data class PageVideoLookup(
    val key: String,
    val pageUrl: String,
    val title: String?,
    /** Why the lookup failed; null while it runs. */
    val failure: String? = null,
    /** Whether asking again can change the answer, so the sheet offers Try again. */
    val canRetry: Boolean = false,
) {
    val running: Boolean get() = failure == null

    override fun toString(): String = "PageVideoLookup(running=$running, canRetry=$canRetry)"
}

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

    private val mutableOtherVideos = MutableStateFlow(0)

    /** P12: how many more videos the page of the selected main video has; the sheet lists them. */
    val otherVideos: StateFlow<Int> = mutableOtherVideos.asStateFlow()

    private val mutableLookup = MutableStateFlow<PageVideoLookup?>(null)

    /** P12: the current page's own video lookup, while it runs or after it failed. */
    val lookup: StateFlow<PageVideoLookup?> = mutableLookup.asStateFlow()

    private val retryRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** P12: the sheet's Try again for [lookup], by video key; the browser asks again. */
    val lookupRetries: SharedFlow<String> = retryRequests.asSharedFlow()

    private val foundListRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** P12: the sheet's "Other videos on this page" row; the browser opens its found list. */
    val foundList: SharedFlow<Unit> = foundListRequests.asSharedFlow()

    fun publish(
        pageUrl: String,
        pageTitle: String?,
        candidates: List<MediaCandidate>,
        adapterSite: Boolean = false,
    ) {
        // A video chosen on another page is not this page's: the sheet must not show it.
        if (mutablePage.value?.pageUrl != pageUrl) {
            mutableSelection.value = null
            mutableOtherVideos.value = 0
        }
        mutablePage.value = DetectedPage(
            pageUrl = pageUrl,
            pageTitle = pageTitle,
            candidates = candidates.take(MAX_CANDIDATES),
            adapterSite = adapterSite,
        )
    }

    /** [group] is the video the sheet shows; [otherVideos] more videos are on its page. */
    fun select(group: MediaGroup, otherVideos: Int = 0) {
        mutableSelection.value = group
        mutableOtherVideos.value = otherVideos.coerceAtLeast(0)
    }

    /**
     * P12: the sheet opens for the page's own video before its lookup found it. Nothing is
     * selected until the browser's lookup selects the video, so the sheet waits on [lookup].
     */
    fun awaitPageVideo() {
        mutableSelection.value = null
        mutableOtherVideos.value = 0
    }

    /** P12: the browser's page lookup started, failed or ended (null). */
    fun showLookup(lookup: PageVideoLookup?) {
        mutableLookup.value = lookup
    }

    fun retryLookup(key: String) {
        retryRequests.tryEmit(key)
    }

    fun showFoundList() {
        foundListRequests.tryEmit(Unit)
    }

    fun clear() {
        mutablePage.value = null
        mutableSelection.value = null
        mutableOtherVideos.value = 0
        mutableLookup.value = null
    }

    companion object {
        const val MAX_CANDIDATES = 50
    }
}
