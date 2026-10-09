package com.alal.yft.feature.detectedmedia

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.PageVideoFacts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** Media found on the page the browser showed last, kept for the Detected Media screen. */
data class DetectedPage(
    val pageUrl: String,
    val pageTitle: String?,
    val candidates: List<MediaCandidate>,
    /** P12: an adapter reads this page's video, so only its named video counts. */
    val adapterSite: Boolean = false,
    /** P28: what the page states about its own video: its length, title and picture. */
    val facts: PageVideoFacts? = null,
    /** P29: who found this page's files: the browser, or Home's check of a pasted link. */
    val owner: LookupOwner = LookupOwner.BROWSER,
)

/**
 * P37: the sheet's "Reload page and try again": the browser reloads [pageUrl] once without its
 * cache and reopens the sheet when [video] is found again with a link not in [deadLinks].
 */
data class PageReload(
    val pageUrl: String,
    val video: MediaGroup,
    val deadLinks: Set<String>,
) {
    override fun toString(): String = "PageReload(deadLinks=${deadLinks.size})"
}

/** P16: who runs a [PageVideoLookup]; only its owner answers, retries, stops or clears it. */
enum class LookupOwner { BROWSER, HOME }

/**
 * P12: the lookup of a site page's own video, while it runs or after it failed, so the download
 * sheet can open for that video before the lookup ends. [key] is the video's "site:contentId".
 * P16: Home's lookup of a pasted link and the browser's of a feed's video on screen are shown
 * the same way.
 */
data class PageVideoLookup(
    val key: String,
    val pageUrl: String,
    val title: String?,
    /** Why the lookup failed; null while it runs. */
    val failure: String? = null,
    /** Whether asking again can change the answer, so the sheet offers Try again. */
    val canRetry: Boolean = false,
    val owner: LookupOwner = LookupOwner.BROWSER,
    /** P28: the page's picture of its video, shown while the sheet waits. */
    val thumbnailUrl: String? = null,
    /**
     * P28: the browser waits a few seconds for the page's own video, because what its player
     * shows first is far shorter than the length the page states (an ad).
     */
    val findingPageVideo: Boolean = false,
    /**
     * P39 (R25): the failed lookup's non-sensitive steps (page kinds, data keys, file checks),
     * shown as the sheet's Details. Hosts only, never an address, query value or cookie.
     */
    val details: List<String> = emptyList(),
) {
    val running: Boolean get() = failure == null

    override fun toString(): String =
        "PageVideoLookup(running=$running, canRetry=$canRetry, owner=$owner, " +
            "findingPageVideo=$findingPageVideo)"
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

    private val mutableMaybeAd = MutableStateFlow(false)

    /**
     * P28: the selected video may be the ad the page's player shows first: the page states a
     * far longer video and it did not come in time. The sheet says so.
     */
    val maybeAd: StateFlow<Boolean> = mutableMaybeAd.asStateFlow()

    private val mutableLookup = MutableStateFlow<PageVideoLookup?>(null)

    /** P12: the current page's own video lookup, while it runs or after it failed. */
    val lookup: StateFlow<PageVideoLookup?> = mutableLookup.asStateFlow()

    private val retryRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** P12: the sheet's Try again for [lookup], by video key; its owner asks again. */
    val lookupRetries: SharedFlow<String> = retryRequests.asSharedFlow()

    private val closeRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** P16: the sheet that waited on [lookup] closed, by video key; its owner stops it. */
    val lookupCloses: SharedFlow<String> = closeRequests.asSharedFlow()

    /** P16: a sheet opens for [lookup]; nothing the page itself shows stands in for it. */
    private var awaiting = false

    /** P16: whether an opening sheet waits on [lookup] rather than taking the page's video. */
    val awaitsLookup: Boolean get() = awaiting && mutableSelection.value == null

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
        facts: PageVideoFacts? = null,
        owner: LookupOwner = LookupOwner.BROWSER,
    ) {
        // A video chosen on another page is not this page's: the sheet must not show it.
        if (mutablePage.value?.pageUrl != pageUrl) {
            mutableSelection.value = null
            mutableOtherVideos.value = 0
            mutableMaybeAd.value = false
            awaiting = false
        }
        mutablePage.value = DetectedPage(
            pageUrl = pageUrl,
            pageTitle = pageTitle,
            candidates = candidates.take(MAX_CANDIDATES),
            adapterSite = adapterSite,
            facts = facts,
            owner = owner,
        )
    }

    /**
     * [group] is the video the sheet shows; [otherVideos] more videos are on its page. P28:
     * [maybeAd] when it may be the ad before the page's video ([maybeAd]).
     */
    fun select(group: MediaGroup, otherVideos: Int = 0, maybeAd: Boolean = false) {
        awaiting = false
        mutableMaybeAd.value = maybeAd
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
        mutableMaybeAd.value = false
        awaiting = true
    }

    /** P12: the browser's page lookup started, failed or ended (null). */
    fun showLookup(lookup: PageVideoLookup?) {
        mutableLookup.value = lookup
    }

    /** P16: [owner]'s lookup ended; another owner's lookup stays. */
    fun clearLookup(owner: LookupOwner) {
        mutableLookup.update { current -> current?.takeUnless { it.owner == owner } }
    }

    fun retryLookup(key: String) {
        retryRequests.tryEmit(key)
    }

    /** P16: the sheet that waited on the lookup of [key] closed before its video came. */
    fun closeLookup(key: String) {
        awaiting = false
        closeRequests.tryEmit(key)
    }

    fun showFoundList() {
        foundListRequests.tryEmit(Unit)
    }

    private val pageReadRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * P29: the sheet's Try again on a page Home found, by page address: Home reads the page
     * again, quietly, for its current files ([pageReadDone] when it answered).
     */
    val pageReads: SharedFlow<String> = pageReadRequests.asSharedFlow()

    private val pageReadAnswers = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * P29: asks Home to read [pageUrl] again and waits up to [timeoutMillis] for it; then the
     * page as the store has it, null when it holds another page. Without Home listening, the
     * page as it is.
     */
    suspend fun readPageAgain(
        pageUrl: String,
        timeoutMillis: Long = PAGE_READ_TIMEOUT_MILLIS,
    ): DetectedPage? {
        if (pageReadRequests.subscriptionCount.value > 0) {
            coroutineScope {
                val answered = async(start = CoroutineStart.UNDISPATCHED) {
                    pageReadAnswers.first { it == pageUrl }
                }
                pageReadRequests.tryEmit(pageUrl)
                if (withTimeoutOrNull(timeoutMillis) { answered.await() } == null) {
                    answered.cancel()
                }
            }
        }
        return mutablePage.value?.takeIf { it.pageUrl == pageUrl }
    }

    /** P29: Home read [pageUrl] again, with or without new files. */
    fun pageReadDone(pageUrl: String) {
        pageReadAnswers.tryEmit(pageUrl)
    }

    /**
     * P37: the browser's own user agent, which its requests carry: the sheet's quiet re-read of
     * the browser's page introduces itself the same way. Not a secret; memory only.
     */
    @Volatile
    var browserUserAgent: String? = null

    private val reloadRequests = MutableSharedFlow<PageReload>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** P37: the sheet's "Reload page and try again"; the browser reloads its page. */
    val pageReloads: SharedFlow<PageReload> = reloadRequests.asSharedFlow()

    /** P37: asks the browser to reload; false when no browser listens (the sheet stays). */
    fun reloadPage(request: PageReload): Boolean =
        reloadRequests.subscriptionCount.value > 0 && reloadRequests.tryEmit(request)

    fun clear() {
        mutablePage.value = null
        mutableSelection.value = null
        mutableOtherVideos.value = 0
        mutableMaybeAd.value = false
        mutableLookup.value = null
        awaiting = false
    }

    companion object {
        const val MAX_CANDIDATES = 50

        /** P29: how long Try again waits for Home's new read of a page. */
        const val PAGE_READ_TIMEOUT_MILLIS = 20_000L
    }
}
