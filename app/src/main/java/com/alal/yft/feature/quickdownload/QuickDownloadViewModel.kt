package com.alal.yft.feature.quickdownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.download.EndedLiveSizes
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.AdRule
import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.BrowserReads
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.FreshLinks
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.core.model.media.PageVideoProof
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.SameFile
import com.alal.yft.core.model.media.SameVideo
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.media.VideoProof
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.detection.BrowserPageReader
import com.alal.yft.detection.DashSoundPairing
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.PageReread
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.DownloadOrigin
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.DownloadNetworkPolicy
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.detectedmedia.DetectedPage
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.detectedmedia.PageReload
import com.alal.yft.feature.detectedmedia.PageVideoLookup
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.thumbnail.DownloadThumbnails
import com.alal.yft.thumbnail.ThumbnailUrls
import com.alal.yft.ui.components.isAudio
import com.alal.yft.ui.components.isSavable
import com.alal.yft.ui.format.YftQualityNames
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What the sheet knows before the qualities are read: the video's title, site and length. */
data class SheetHeader(
    val title: String,
    val source: String?,
    val durationMillis: Long?,
    val audioOnly: Boolean,
    /** The video's picture; the header shows its placeholder until it loads. */
    val thumbnailUrl: String? = null,
)

/** One video item of a post in the sheet's item picker: "Video 2 of 4". */
data class PostItemChoice(val key: String, val number: Int, val label: String)

/** A queueing result is distinct from a download's later transfer result. */
sealed interface PostQueueResult {
    data object Queued : PostQueueResult
    data class Rejected(val message: String) : PostQueueResult
    /** An exception can happen after the queue accepted a task. Do not retry it blindly. */
    data object Unknown : PostQueueResult
}

data class QuickDownloadUiState(
    /** Null when there is no video to show (the sheet then says so). */
    val header: SheetHeader? = null,
    /** True while the qualities and sizes are being read. */
    val loading: Boolean = false,
    val choices: QuickChoices? = null,
    /** Why no format could be read; Try again reads them once more. */
    val failure: String? = null,
    /**
     * Page video finder: [failure]'s Details: the step, the host and the status of the request that
     * failed.
     */
    val failureDetails: List<String> = emptyList(),
    /** Page video finder: the Details of a Download the sheet could not prepare. */
    val downloadDetails: List<String> = emptyList(),
    /** False when Try again cannot help (a protected video); the sheet hides it. */
    val canRetry: Boolean = true,
    /**
     * TikTok adapter: the lookup ended on TikTok's check, which the browser can show; a quiet
     * build's sheet (Quiet details) offers its Show check.
     */
    val siteCheck: Boolean = false,
    /** The sheet waits for the browser's lookup of the page's video. */
    val findingVideo: Boolean = false,
    /**
     * Ad filter: the sheet waits a few seconds for the page's own video, because what its player
     * showed first may be an ad: it says "Finding the page's video…".
     */
    val findingPageVideo: Boolean = false,
    /** Ad filter: the video may be the ad before the page's video; the sheet says so. */
    val maybeAd: Boolean = false,
    /**
     * Fresh link renewal: the Details of the attempts before the one shown ([freshLink],
     * [adSkipped]), each with its step, host and status. The sheet never moves on to another video
     * of the page.
     */
    val attemptDetails: List<String> = emptyList(),
    /**
     * Fresh link renewal: the first link was gone, so the sheet shows the same video from a fresh
     * link (the page's newest, the player's own request, or the page read again) and says so;
     * Details in [attemptDetails].
     */
    val freshLink: Boolean = false,
    /**
     * Fresh link renewal: nothing fresh was found for the browser's page: the error offers
     * "Reload page and try again", which reloads the tab without its cache and reopens the sheet.
     */
    val canReload: Boolean = false,
    /**
     * Ad filter: what the page's player showed first was an ad, so the sheet skipped it and shows
     * the page's own video and says so; Details in [attemptDetails].
     */
    val adSkipped: Boolean = false,
    val defaultQuality: QualityPreference = DownloadPreferences().defaultQuality,
    val selectedId: String? = null,
    /**
     * [selectedId] is the user's pick. The sheet's own pick of a file of unknown quality gives way
     * when a better row comes (w3schools' MP4, measured after its OGG).
     */
    val selectionByUser: Boolean = false,
    val downloadStatus: PreviewDownloadStatus = PreviewDownloadStatus.Idle,
    /** What Download takes while the sheet waits: the Default quality's video, or audio. */
    val earlySection: OptionSection = OptionSection.VIDEO,
    /** Download was tapped before the qualities came; it starts as soon as they do. */
    val startsWhenReady: Boolean = false,
    /** The quality an early Download took ("Downloading 480p — 720p not available"). */
    val startedNote: String? = null,
    /**
     * The detail lines of the lookup that found this video (the Backup finder's: the site's ladder
     * line, the VK ladder's heal line (`vk V2` in Lookup details), the YouTube backup route's
     * counts (`route B` in Lookup details)), shown behind the sheet's Lookup details with Copy
     * details, as a failed lookup's Details; none when the lookup gave none. A quiet build shows
     * them only with Show details on (Quiet details).
     */
    val lookupDetails: List<String> = emptyList(),
    /**
     * The post's video items when it has several (an Instagram carousel, an X post with more
     * videos): the item the link names first, then the others in the post's order; empty for a
     * video of its own. [postItemKey] is the one shown.
     */
    val postItems: List<PostItemChoice> = emptyList(),
    val postItemKey: String? = null,
    val postSelection: Set<String> = emptySet(),
    val postResults: Map<String, PostQueueResult> = emptyMap(),
    val postQueueing: Boolean = false,
    /**
     * Zawgyi titles: "Use original title" is on: the sheet shows, and a Download takes, the Zawgyi
     * title the sheet's Unicode title was converted from ([QuickChoices.zawgyi]).
     */
    val useOriginalTitle: Boolean = false,
) {
    val selectedOption: SheetOption? get() = choices?.option(selectedId)

    /** Zawgyi titles: the header's title: its own, or the original ([useOriginalTitle]). */
    val shownTitle: String?
        get() = choices?.zawgyi?.takeIf { useOriginalTitle }?.original ?: header?.title

    /**
     * The title a Download (and Download as) takes once the rows came: the sheet's (Zawgyi titles:
     * Unicode for a Zawgyi title), or the original with [useOriginalTitle].
     */
    val downloadTitle: String?
        get() = choices?.let { it.zawgyi?.takeIf { useOriginalTitle }?.original ?: it.title }

    /** Zawgyi titles: the sheet offers "Use original title" (not while a Download is queued). */
    val offersOriginalTitle: Boolean get() = choices?.zawgyi != null

    /**
     * The sheet's Lookup details: the lookup's own lines ([lookupDetails]), then Zawgyi titles'
     * line "Title converted from Zawgyi (p=0.9999)" when the sheet's title was converted.
     */
    val lookupDetailLines: List<String>
        get() = lookupDetails + listOfNotNull(choices?.zawgyi?.detailLine)

    /** The sheet still says "Getting qualities…"; Download then queues [earlySection]. */
    val waitsForQualities: Boolean
        get() = choices == null && loading && failure == null && header != null

    val canDownload: Boolean
        get() = (selectedOption != null || waitsForQualities && !startsWhenReady) &&
            !postQueueing &&
            downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered
}

/**
 * The Download sheet: one video with an Audio section (M4A, MP3) and a Video section (one row per
 * resolution).
 *
 * The video is the group [DetectedMediaStore] selected (Home's lookup, the found list or the
 * browser's Download button), else the page's only video. On a site's video page the sheet may open
 * before the browser's lookup found it: it waits in its loading state for that same lookup, and a
 * failed lookup shows its message with Try again, which asks the browser again. Each of its
 * candidates is resolved like Download as does, so heights, sizes and companion audio are real; a
 * whole file whose site already stated its picture and size is shown without a request and resolved
 * on Download. Download queues the option through [PreviewDownloadStarter], which applies Wi-Fi
 * only; mobile data asks first when the user chose to be asked.
 */
@HiltViewModel
class QuickDownloadViewModel @Inject constructor(
    private val store: DetectedMediaStore,
    private val selectionStore: PreviewSelectionStore,
    private val resolver: VariantResolver,
    private val downloadStarter: PreviewDownloadStarter,
    private val downloadPreferences: DownloadPreferencesRepository,
    private val network: NetworkStatusSource,
    private val playback: VideoPlaybackSupport,
    private val downloadThumbnails: DownloadThumbnails = DownloadThumbnails.None,
    private val lookups: SiteLookupCache = SiteLookupCache(),
    private val pageReader: BrowserPageReader = BrowserPageReader.None,
    /** The merges this phone makes, for the rows of a post's other items. */
    private val merges: MergeSupport = MergeSupport { true },
    /** Ended live size sample: sizes for the rows of a recently ended live. */
    private val endedLiveSizes: EndedLiveSizes = EndedLiveSizes.None,
) : ViewModel() {
    /**
     * A sheet opened for a lookup (Home's link, a feed's video on screen) waits on it even when the
     * page under it has a video of its own.
     */
    private val opensOnLookup = store.awaitsLookup && store.lookup.value != null

    /** Ad filter: the addresses of the files this sheet skipped as ads; never offered here. */
    private val skippedAds = mutableSetOf<String>()
    private var group: MediaGroup? = (
        store.selection.value ?: store.page.value
            ?.takeUnless { opensOnLookup }
            ?.let { page ->
                val savable = page.candidates.take(DetectedMediaStore.MAX_CANDIDATES)
                    .filter { it.isSavable }
                MediaGroups.pageVideos(savable, adapterSite = page.adapterSite).singleOrNull()
            }
        )?.let(::withPageFacts)

    /**
     * The post's video items when [group]'s rows name several, the item the link names first
     * ([MediaGroups.postItems]); empty for a video of its own.
     */
    private var postItems: List<MediaGroup> = postItemsOf(group)

    /** The lookup this sheet waits on, while no video is chosen yet. */
    private var pageLookup: PageVideoLookup? = store.lookup.value.takeIf { group == null }
    private val mutableUiState = MutableStateFlow(
        pageLookup?.let(::lookupState) ?: QuickDownloadUiState(
            header = group?.header(),
            maybeAd = store.maybeAd.value && store.selection.value != null,
            // The lines of the lookup that found the selected video.
            lookupDetails = store.lookupDetailsOf(store.selection.value),
            postItems = postItemChoices(),
            postItemKey = group?.key?.takeIf { postItems.isNotEmpty() },
        ),
    )
    val uiState: StateFlow<QuickDownloadUiState> = mutableUiState.asStateFlow()
    private var loading: Job? = null
    private val sizeProbes = Semaphore(2)

    /**
     * Ended live size sample: the sizes read for this sheet's files, by address; the files being
     * read; and every file asked once, so a file is sampled once a sheet.
     */
    private val sampledSizes = mutableMapOf<String, EndedLiveSizes.Size>()
    private val sampling = mutableSetOf<String>()
    private val sampledFiles = mutableSetOf<String>()
    private var pendingPostQueue = false

    /** The user said yes to mobile data when tapping Download before the qualities came. */
    private var earlyMeteredConfirmed = false

    /**
     * Fresh link renewal: the video that failed first, once the sheet moved on to a fresh link of
     * it; Try again starts from it again.
     */
    private var firstVideo: MediaGroup? = null

    /** Fresh link renewal: which attempt the sheet shows now (the first link or a fresh one). */
    private var stage = Attempt.FIRST

    /** Fresh link renewal: the Details of each attempt that failed before, its name first. */
    private val attempts = mutableListOf<List<String>>()

    /**
     * Fresh link renewal: the addresses that answered "gone" in this sheet; never asked again here.
     */
    private val deadLinks = mutableSetOf<String>()

    /** Fresh link renewal: the quiet re-reads this failure used ([BrowserPageReader.rereads]). */
    private var rereadsUsed = 0

    /** After an ad, the page's own video behind it is looked for once. */
    private var pageVideoTried = false

    /** Fresh link renewal: the clock of the Details' "Link age"; tests set it. */
    internal var now: () -> Long = System::currentTimeMillis

    /** Ad filter: how strictly a file must prove it is the page's video; tests set it. */
    internal var adRule = PageVideoProof.DEFAULT_RULE

    /** Ad filter: the first video was an ad the sheet skipped, not a video whose link failed. */
    private var firstWasAd = false

    /** Ad filter: why the video shown was taken for the page's, for its Details. */
    private var chosenLines: List<String> = emptyList()

    /** Ad filter: the message of a link that failed on the way, for when nothing else is left. */
    private var goneMessage: String? = null

    init {
        if (pageLookup != null) awaitPageVideo() else load()
        // The waiting sheet names the Default quality an early Download takes.
        viewModelScope.launch {
            val quality = currentPreferences().defaultQuality
            mutableUiState.update { it.copy(defaultQuality = quality) }
        }
    }

    /** Closing the sheet before its video came stops the lookup it waited on. */
    override fun onCleared() {
        pageLookup?.let { store.closeLookup(it.key) }
    }

    /** Reads the formats again after a failure; a failed page lookup is asked for again. */
    fun retry() {
        val state = mutableUiState.value
        if (state.loading) return
        val lookup = pageLookup
        if (lookup != null) {
            if (!lookup.canRetry) return
            mutableUiState.update { it.copy(loading = true, failure = null) }
            store.retryLookup(lookup.key)
            return
        }
        readPageAgainThenLoad()
    }

    /**
     * Quiet details: a quiet build's Show check — the browser shows TikTok's check for the page
     * whose lookup ended on it. True when the browser took it, so the sheet closes.
     */
    fun showSiteCheck(): Boolean {
        val lookup = pageLookup?.takeIf { it.siteCheck } ?: return false
        return store.requestSiteCheck(lookup.pageUrl)
    }

    /**
     * Fresh link renewal: "Reload page and try again": the browser reloads its tab once without the
     * cache and reopens the sheet when the video comes back with a new link. True when the browser
     * took it, so the sheet closes.
     */
    fun reloadPage(): Boolean {
        if (!mutableUiState.value.canReload) return false
        val target = firstVideo ?: group ?: return false
        // Ad filter: after the reload the browser looks for the page's video, never the ad again.
        val video = (if (firstWasAd) pageVideoStandIn(target) else null) ?: target
        return store.reloadPage(PageReload(target.pageUrl, video, deadLinks + skippedAds))
    }

    /**
     * Ad filter: what the browser looks for after a reload when the first video was an ad: a
     * stand-in of the length the page states; null when it states none.
     */
    private fun pageVideoStandIn(ad: MediaGroup): MediaGroup? {
        val facts = store.page.value?.takeIf { it.pageUrl == ad.pageUrl }?.facts ?: return null
        val length = facts.durationMillis ?: return null
        val standIn = MediaCandidate(
            pageUrl = ad.pageUrl,
            mediaUrl = FreshLinks.unsigned(ad.pageUrl) + PAGE_VIDEO_FRAGMENT,
            sources = setOf(CandidateSource.DOM),
            kind = MediaKind.UNKNOWN,
            title = facts.title,
            durationMillis = length,
        )
        return MediaGroup("page-video:${ad.pageUrl}", facts.title, listOf(standIn))
    }

    fun select(selectionId: String) {
        mutableUiState.update { state ->
            if (!state.canChangeSelection) return@update state
            if (state.choices?.option(selectionId) == null) return@update state
            state.copy(
                selectedId = selectionId,
                selectionByUser = true,
                downloadStatus = PreviewDownloadStatus.Idle,
                startedNote = null,
            )
        }
    }

    /**
     * Zawgyi titles: "Use original title" switches the next Download (and Download as) to the
     * Zawgyi title the sheet's Unicode title was converted from, and back again. Like another row,
     * it clears the last Download's line; a download already queued keeps its name. Ignored while a
     * Download is being queued.
     */
    fun toggleOriginalTitle() {
        mutableUiState.update { state ->
            if (!state.canChangeSelection || !state.offersOriginalTitle) return@update state
            state.copy(
                useOriginalTitle = !state.useOriginalTitle,
                downloadStatus = PreviewDownloadStatus.Idle,
                startedNote = null,
            )
        }
    }

    /**
     * Shows another video item of the post ([QuickDownloadUiState.postItems]): its own header,
     * qualities and sizes, read as the first item's were; each item downloads as a file of its own.
     * Ignored while a Download is being queued.
     */
    fun selectPostItem(key: String) {
        val state = mutableUiState.value
        if (!state.canChangeSelection || key == state.postItemKey) return
        val next = postItems.firstOrNull { it.key == key } ?: return
        loading?.cancel()
        loading = null
        group = next
        firstVideo = null
        stage = Attempt.FIRST
        attempts.clear()
        rereadsUsed = 0
        pageVideoTried = false
        firstWasAd = false
        chosenLines = emptyList()
        goneMessage = null
        earlyMeteredConfirmed = false
        mutableUiState.update {
            it.copy(
                header = next.header(),
                loading = true,
                choices = null,
                failure = null,
                failureDetails = emptyList(),
                downloadDetails = emptyList(),
                canRetry = true,
                maybeAd = false,
                attemptDetails = emptyList(),
                freshLink = false,
                canReload = false,
                adSkipped = false,
                selectedId = null,
                selectionByUser = false,
                downloadStatus = PreviewDownloadStatus.Idle,
                startsWhenReady = false,
                startedNote = null,
                postItemKey = key,
            )
        }
        load()
    }

    /** Select only items not already queued, or whose enqueue result is uncertain. */
    fun togglePostSelection(key: String) {
        mutableUiState.update { state ->
            if (!state.canChangeSelection || state.postItems.none { it.key == key } ||
                state.postResults[key] is PostQueueResult.Queued ||
                state.postResults[key] is PostQueueResult.Unknown
            ) return@update state
            val selection = state.postSelection.toMutableSet()
            if (!selection.add(key)) selection.remove(key)
            state.copy(postSelection = selection)
        }
    }

    fun toggleAllPostSelection() {
        mutableUiState.update { state ->
            if (!state.canChangeSelection) return@update state
            val available = state.postItems.map { it.key }.filter {
                state.postResults[it] !is PostQueueResult.Queued &&
                    state.postResults[it] !is PostQueueResult.Unknown
            }.toSet()
            state.copy(
                postSelection = if (available.isNotEmpty() &&
                    state.postSelection.containsAll(available)
                ) emptySet() else available,
            )
        }
    }

    /** Queues one default-quality video per selected post item, in post order. */
    fun queuePostSelection() {
        val state = mutableUiState.value
        if (!state.canChangeSelection || state.postSelection.isEmpty()) return
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch {
            val preferences = currentPreferences()
            if (needsMeteredConfirmation(preferences)) {
                pendingPostQueue = true
                setStatus(PreviewDownloadStatus.ConfirmMetered)
            } else {
                queuePostItems(preferences)
            }
        }
    }

    private suspend fun queuePostItems(
        preferences: DownloadPreferences,
        meteredConfirmed: Boolean = false,
    ) {
        val selected = mutableUiState.value.postSelection
        val items = postItems.filter { it.key in selected }.sortedBy { item ->
            item.candidates.firstNotNullOfOrNull { it.postItem?.number } ?: Int.MAX_VALUE
        }
        mutableUiState.update {
            it.copy(postQueueing = true, downloadStatus = PreviewDownloadStatus.Idle)
        }
        try {
            for (item in items) {
                if (!meteredConfirmed && needsMeteredConfirmation(preferences)) {
                    pendingPostQueue = true
                    setStatus(PreviewDownloadStatus.ConfirmMetered)
                    return
                }
                val result = queuePostItem(item, preferences, meteredConfirmed)
                if (result == null) {
                    pendingPostQueue = true
                    setStatus(PreviewDownloadStatus.ConfirmMetered)
                    return
                }
                mutableUiState.update { state ->
                    state.copy(
                        postResults = state.postResults + (item.key to result),
                        postSelection = if (result is PostQueueResult.Rejected) {
                            state.postSelection
                        } else {
                            state.postSelection - item.key
                        },
                    )
                }
            }
        } finally {
            mutableUiState.update { it.copy(postQueueing = false) }
        }
    }

    private suspend fun queuePostItem(
        item: MediaGroup,
        preferences: DownloadPreferences,
        meteredConfirmed: Boolean,
    ): PostQueueResult? {
        val (option, prepared) = try {
            val sources = item.candidates.take(MAX_SOURCES).map { candidate ->
                val source = SheetSource(candidate, stated(candidate), resolved = false)
                if (needsSizeProbe(source)) inspectSize(source) else source
            }
            val choices = QuickDownloadChoices.of(item, sources, playback)
                ?: return PostQueueResult.Rejected("No video quality could be read.")
            val videoChoices = choices.copy(audio = emptyList())
            val option = QuickDownloadChoices.preselect(videoChoices, preferences.defaultQuality)
                ?: return PostQueueResult.Rejected("No video quality could be read.")
            val prepared = when (val result = prepare(option, choices.title)) {
                is Prepared.Failed -> return PostQueueResult.Rejected(result.message)
                is Prepared.Ready -> result
            }
            option to prepared
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return PostQueueResult.Rejected("This video could not be prepared. Try again.")
        }
        // The network can change while the item's formats are being read.
        if (!meteredConfirmed && needsMeteredConfirmation(preferences)) return null
        // The queue might have accepted a task before an exception reaches this caller.
        val result = try {
            downloadStarter.enqueue(prepared.asset, prepared.variant, origin(option, prepared.base))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return PostQueueResult.Unknown
        }
        return when (result) {
            is EnqueueResult.Rejected -> {
                if (result.reason in SiteLookupCache.LINK_FAILURES) forgetLinks(option)
                PostQueueResult.Rejected(result.message)
            }
            is EnqueueResult.Started -> {
                item.header().thumbnailUrl?.let { url ->
                    runCatching { downloadThumbnails.saveFrom(result.taskId, url) }
                }
                lookupVideoId(option.source.candidate)?.let { videoId ->
                    runCatching { lookups.rememberDownload(result.taskId, videoId) }
                }
                PostQueueResult.Queued
            }
        }
    }

    /** The Audio or the Video placeholder picked while the qualities are still coming. */
    fun pickEarly(section: OptionSection) {
        mutableUiState.update { state ->
            if (!state.waitsForQualities || !state.canChangeSelection) return@update state
            state.copy(earlySection = section)
        }
    }

    /**
     * Details: hands the selected format's candidate to Download as. Returns false when there is
     * nothing to show, so nothing navigates.
     */
    fun openDetails(): Boolean {
        val state = mutableUiState.value
        val option = state.selectedOption ?: state.choices?.options?.firstOrNull() ?: return false
        val candidate = option.source.candidate
        // Download as names a post's item as the sheet does ("Video 2 of 4"), and a title the sheet
        // converted from Zawgyi (Zawgyi titles) as it is chosen there.
        val title = state.downloadTitle
            ?.takeIf { candidate.postItem != null || state.offersOriginalTitle }
        selectionStore.select(if (title != null) candidate.copy(title = title) else candidate)
        return true
    }

    fun download() {
        val state = mutableUiState.value
        if (!state.canDownload) return
        val option = state.selectedOption
        if (option == null) {
            waitForQualities()
            return
        }
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch {
            val preferences = currentPreferences()
            if (needsMeteredConfirmation(preferences)) {
                setStatus(PreviewDownloadStatus.ConfirmMetered)
            } else {
                enqueue(option, preferences)
            }
        }
    }

    fun confirmMeteredDownload() {
        val state = mutableUiState.value
        if (state.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        if (pendingPostQueue) {
            pendingPostQueue = false
            setStatus(PreviewDownloadStatus.Enqueuing)
            viewModelScope.launch { queuePostItems(currentPreferences(), meteredConfirmed = true) }
            return
        }
        if (state.startsWhenReady) {
            // Yes to mobile data before the qualities came; their arrival starts it.
            earlyMeteredConfirmed = true
            setStatus(PreviewDownloadStatus.Idle)
            startWhenReady()
            return
        }
        val option = state.selectedOption ?: return
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch { enqueue(option, currentPreferences()) }
    }

    fun dismissMeteredDownload() {
        if (mutableUiState.value.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        pendingPostQueue = false
        // No to mobile data also drops a Download queued before the qualities came.
        mutableUiState.update {
            it.copy(
                downloadStatus = PreviewDownloadStatus.Idle,
                startsWhenReady = false,
                startedNote = null,
            )
        }
    }

    /**
     * Download before the qualities came. The mobile-data question is asked now, while the user
     * looks at the sheet; the pick starts once the rows arrive ([startWhenReady]).
     */
    private fun waitForQualities() {
        earlyMeteredConfirmed = false
        mutableUiState.update {
            it.copy(
                startsWhenReady = true,
                startedNote = null,
                // Until the question is settled the rows must not start it.
                downloadStatus = PreviewDownloadStatus.Enqueuing,
            )
        }
        viewModelScope.launch {
            if (needsMeteredConfirmation(currentPreferences())) {
                setStatus(PreviewDownloadStatus.ConfirmMetered)
            } else {
                setStatus(PreviewDownloadStatus.Idle)
                startWhenReady()
            }
        }
    }

    /**
     * The rows came, so the queued Download takes the Default quality (else the nearest lower, else
     * the nearest higher one) or the audio, and goes through today's checks.
     */
    private fun startWhenReady() {
        val state = mutableUiState.value
        if (!state.startsWhenReady || state.downloadStatus != PreviewDownloadStatus.Idle) return
        val choices = state.choices ?: return
        val pick = QuickDownloadChoices.earlyPick(choices, state.earlySection, state.defaultQuality)
        if (pick == null) {
            mutableUiState.update { it.copy(startsWhenReady = false) }
            return
        }
        mutableUiState.update {
            it.copy(
                startsWhenReady = false,
                selectedId = pick.id,
                startedNote = QuickDownloadChoices.earlyNote(
                    pick,
                    state.earlySection,
                    state.defaultQuality,
                ),
                downloadStatus = PreviewDownloadStatus.Enqueuing,
            )
        }
        viewModelScope.launch {
            val preferences = currentPreferences()
            // Wi-Fi may have gone since the tap: mobile data is asked about once.
            if (!earlyMeteredConfirmed && needsMeteredConfirmation(preferences)) {
                setStatus(PreviewDownloadStatus.ConfirmMetered)
            } else {
                enqueue(pick, preferences)
            }
        }
    }

    private fun needsMeteredConfirmation(preferences: DownloadPreferences): Boolean =
        DownloadNetworkPolicy.needsMeteredConfirmation(network.snapshot.value, preferences)

    /**
     * Shows the browser's lookup until it selects the page's video, then reads that video's
     * formats. A lookup that goes away without one (another page, the browser closed) leaves the
     * sheet's "no longer here".
     */
    private fun awaitPageVideo() {
        loading = viewModelScope.launch {
            val (selected, _) = combine(store.selection, store.lookup, ::Pair)
                .onEach { (selection, lookup) ->
                    if (selection == null && lookup != null) {
                        pageLookup = lookup
                        mutableUiState.update { lookupState(lookup).keepingEarly(it) }
                    }
                }
                .first { (selection, lookup) -> selection != null || lookup == null }
            pageLookup = null
            group = selected?.let(::withPageFacts)
            postItems = postItemsOf(group)
            mutableUiState.update {
                QuickDownloadUiState(
                    header = group?.header(),
                    maybeAd = selected != null && store.maybeAd.value,
                    // The lines of the lookup that found it, for Lookup details.
                    lookupDetails = store.lookupDetailsOf(selected),
                    postItems = postItemChoices(),
                    postItemKey = group?.key?.takeIf { postItems.isNotEmpty() },
                ).keepingEarly(it)
            }
            if (selected != null) load()
        }
    }

    /**
     * The waiting sheet's header is what is known before the lookup answers: the page's title or
     * the link itself, its site and YouTube's picture of the video ID.
     */
    private fun lookupState(lookup: PageVideoLookup) = QuickDownloadUiState(
        header = SheetHeader(
            title = lookup.title ?: QuickDownloadChoices.shownLink(lookup.pageUrl)
                ?: WAITING_TITLE,
            source = QuickDownloadChoices.host(lookup.pageUrl),
            durationMillis = null,
            audioOnly = false,
            // A YouTube video's picture follows from its ID, before the lookup ends.
            // Ad filter: another site's page names its picture.
            thumbnailUrl = ThumbnailUrls.https(lookup.thumbnailUrl)
                ?: ThumbnailUrls.youTube(lookup.key),
        ),
        loading = lookup.running,
        failure = lookup.failure,
        failureDetails = lookup.details,
        canRetry = lookup.canRetry,
        siteCheck = lookup.siteCheck,
        findingVideo = lookup.running,
        findingPageVideo = lookup.running && lookup.findingPageVideo,
    )

    /**
     * Ad filter: [group] with its page's title and picture where its files name none
     * ([MediaGroups.withPageFacts]), when the store's page is its page.
     */
    private fun withPageFacts(group: MediaGroup): MediaGroup {
        val page = store.page.value?.takeIf { it.pageUrl == group.pageUrl } ?: return group
        return MediaGroups.withPageFacts(group, page.facts)
    }

    /**
     * A new waiting state keeps the user's pick and a queued Download; a failed lookup or a video
     * that went away drops the queued Download (Try again needs a new tap).
     */
    private fun QuickDownloadUiState.keepingEarly(old: QuickDownloadUiState): QuickDownloadUiState {
        val keeps = failure == null && header != null
        return copy(
            defaultQuality = old.defaultQuality,
            earlySection = old.earlySection,
            startsWhenReady = keeps && old.startsWhenReady,
            downloadStatus = if (keeps) old.downloadStatus else PreviewDownloadStatus.Idle,
        )
    }

    private fun load() {
        val group = withPlayerAddresses(group ?: return)
        this.group = group
        loading?.cancel()
        mutableUiState.update {
            it.copy(
                loading = true,
                failure = null,
                failureDetails = emptyList(),
                canReload = false,
            )
        }
        val page = chainPage(group)
        // Ad filter: [AdRule.LENIENT] keeps the browser's first choice as it was.
        val checked = page?.takeUnless { stage == Attempt.FIRST && adRule == AdRule.LENIENT }
        chosenLines = emptyList()
        if (checked != null) {
            // Ad filter: a proven ad is skipped before a row of it is read or shown.
            val proof = proofOf(checked)
            val verdict = proof.of(group)
            if (!proof.offers(verdict) && verdict !is VideoProof.NotProven) {
                loading = viewModelScope.launch {
                    skipAd(group, checked, proof.skippedLine(verdict))
                }
                return
            }
            if (proof.adSigns) chosenLines = proof.chosenLines(verdict)
        }
        // Ad filter: a video of unknown length on a page with a length or ad signs shows its rows
        // only once its measured length proves it is not an ad.
        val held = checked != null && needsLengthCheck(group, checked)
        loading = viewModelScope.launch {
            val quality = currentPreferences().defaultQuality
            mutableUiState.update { it.copy(defaultQuality = quality) }
            val sources = group.candidates.take(MAX_SOURCES).map { candidate ->
                SheetSource(candidate, stated(candidate), resolved = false)
            }.toMutableList()
            var heldChoices: QuickChoices? = null
            val publish = { choices: QuickChoices? ->
                if (held) heldChoices = choices else showChoices(choices, sources, waiting = true)
            }
            publish(QuickDownloadChoices.of(group, sources, playback))
            coroutineScope {
                sources.toList().mapIndexed { index, initial ->
                    async {
                        if (!needsSizeProbe(initial) && !held) return@async
                        val updated = sizeProbes.withPermit { inspectSize(initial) }
                        sources[index] = updated
                        val current = if (held) heldChoices else mutableUiState.value.choices
                        val choices = if (initial.asset != null && current != null) {
                            QuickDownloadChoices.updateSizes(current, updated)
                        } else {
                            // A generic manifest has no real formats until it is resolved.
                            QuickDownloadChoices.of(group, sources, playback)
                        }
                        publish(choices)
                    }
                }.awaitAll()
            }
            if (held && checked != null) {
                val measured = sources.firstNotNullOfOrNull { source ->
                    val asset = source.asset ?: return@firstNotNullOfOrNull null
                    val variants = asset.variants.mapNotNull { it.durationMillis }
                    (asset.durationMillis ?: variants.firstOrNull())?.takeIf { it > 0 }
                }
                val proof = proofOf(checked)
                val verdict = proof.of(group, measured ?: group.durationMillis)
                // A link that could not be read goes the way of a gone link, not of an ad.
                val unread = verdict is VideoProof.NotProven && verdict.lengthMillis == null &&
                    sources.any { it.failureDetail != null }
                if (!proof.offers(verdict) && !unread) {
                    skipAd(group, checked, proof.skippedLine(verdict))
                    return@launch
                }
                chosenLines = if (proof.adSigns) proof.chosenLines(verdict) else emptyList()
                if (verdict is VideoProof.PageVideo) {
                    mutableUiState.update { it.copy(maybeAd = false) }
                }
                // The header shows the length that proved it, when its rows state none.
                val proven = heldChoices?.let { rows ->
                    if (rows.durationMillis == null) rows.copy(durationMillis = measured) else rows
                }
                showChoices(proven, sources, waiting = true)
            }
            // Fresh link renewal: on a page the sheet can look further on, a row the site stated is
            // not offered once its link answered "gone": Download would only meet the same answer.
            val gone = sources.filter { source -> source.failureDetail?.let(::isGone) == true }
                .map { it.candidate }
                .takeIf { page != null }
                .orEmpty()
                .toSet()
            if (gone.isNotEmpty()) {
                showChoices(mutableUiState.value.choices?.without(gone), sources, waiting = true)
            }
            val choices = mutableUiState.value.choices
            val failed = sources.firstOrNull { it.failureDetail != null }
            val recorded = choices == null && failed != null && page != null &&
                isGone(failed.failureDetail!!)
            if (recorded) {
                record(failed!!)
                goneMessage = goneMessage ?: QuickDownloadFailures.message(failed.failureDetail!!)
                deadLinks += group.candidates.map { it.mediaUrl }
                val target = firstVideo ?: group.also { firstVideo = it }
                nextAttempt(target, page!!)?.let { (next, attempt) ->
                    show(next, attempt)
                    return@launch
                }
            }
            showChoices(choices, sources, waiting = false)
            showAttempts(group, sources, recorded, page)
        }
    }

    /** Ad filter: [PageVideoProof]'s rule for [page] as this sheet knows it now. */
    private fun proofOf(
        page: DetectedPage,
        candidates: List<MediaCandidate> = page.candidates,
        facts: PageVideoFacts? = page.facts,
    ): PageVideoProof = PageVideoProof.forPage(
        candidates = candidates.filter { it.isSavable },
        facts = facts,
        failed = firstVideo?.takeUnless { firstWasAd },
        rule = adRule,
        sawAd = skippedAds.isNotEmpty(),
    )

    /**
     * Ad filter: whether [group]'s rows wait for its measured length: its length is unknown on a
     * page that states one or shows ad signs.
     */
    private fun needsLengthCheck(group: MediaGroup, page: DetectedPage): Boolean =
        group.durationMillis == null &&
            (page.facts?.durationMillis != null || proofOf(page).adSigns)

    /**
     * Ad filter: whether the chain may prepare [video] at [attempt]: nothing proves it an ad, or
     * its length is still to be measured; a skipped video's Details say why.
     */
    private fun offered(video: MediaGroup, attempt: Attempt, proof: PageVideoProof): Boolean {
        val verdict = proof.of(video)
        val unmeasured = verdict is VideoProof.NotProven && verdict.lengthMillis == null
        if (unmeasured || proof.offers(verdict)) return true
        attempts += listOf(attempt.label, proof.skippedLine(verdict))
        skippedAds += video.candidates.map { it.mediaUrl }
        return false
    }

    /**
     * Ad filter: [group] is an ad, not the page's video: it is skipped ([line] says why) and the
     * sheet looks for the page's video as after a gone link ([nextAttempt]). When nothing passes,
     * no ad is offered: the error says so and, on the browser's page, offers
     * "Reload page and try again".
     */
    private suspend fun skipAd(group: MediaGroup, page: DetectedPage, line: String) {
        attempts += listOf(stage.label, line)
        skippedAds += group.candidates.map { it.mediaUrl }
        val first = firstVideo ?: group.also {
            firstVideo = it
            firstWasAd = true
        }
        mutableUiState.update {
            it.copy(
                header = it.header?.copy(
                    durationMillis = first.takeUnless { firstWasAd }?.durationMillis,
                ),
                maybeAd = false,
            )
        }
        nextAttempt(first, page)?.let { (next, attempt) ->
            show(next, attempt)
            return
        }
        mutableUiState.update {
            it.copy(
                loading = false,
                choices = null,
                selectedId = null,
                selectionByUser = false,
                failure = goneMessage ?: ONLY_AD_FOUND,
                failureDetails = attemptLines(),
                canReload = page.owner == LookupOwner.BROWSER,
                freshLink = false,
                adSkipped = false,
                attemptDetails = emptyList(),
                startsWhenReady = false,
                downloadStatus = if (it.startsWhenReady) {
                    PreviewDownloadStatus.Idle
                } else {
                    it.downloadStatus
                },
            )
        }
        loading = null
    }

    /**
     * Fresh link renewal: the group with the addresses the page's player asked for before the links
     * its script names ([FreshLinks.playerFirst]), on a browser page without an adapter.
     */
    private fun withPlayerAddresses(group: MediaGroup): MediaGroup {
        if (stage == Attempt.REREAD) return group
        val page = store.page.value
            ?.takeIf { it.pageUrl == group.pageUrl && !it.adapterSite } ?: return group
        val requests = page.candidates.filter { it.isSavable && it.mediaUrl !in deadLinks }
        return FreshLinks.playerFirst(group, requests)
    }

    /** Fresh link renewal: the store's page of [group] when the sheet may look further for it. */
    private fun chainPage(group: MediaGroup): DetectedPage? {
        if (group.candidates.any { it.videoId != null }) return null
        return store.page.value?.takeIf { it.pageUrl == group.pageUrl && !it.adapterSite }
    }

    /**
     * Fresh link renewal: these choices without the rows of the [gone] links; null when no row is
     * left.
     */
    private fun QuickChoices.without(gone: Set<MediaCandidate>): QuickChoices? {
        val audio = audio.filter { it.source.candidate !in gone }
        val video = video.filter { it.source.candidate !in gone }
        return if (audio.isEmpty() && video.isEmpty()) null else copy(audio = audio, video = video)
    }

    private fun isGone(failure: VariantResolutionResult.Failure): Boolean =
        failure.httpStatusCode in LINK_DEAD_STATUSES ||
            // A refusal (412, 474) of a link the browser itself was refused too, or could not ask,
            // is as dead for this phone: the same video's newer link is tried.
            BrowserReads.isRefusal(failure.httpStatusCode) ||
            failure.reason == VariantResolutionFailure.INVALID_URL

    /**
     * Fresh link renewal: [failed]'s attempt, its name first, with where its link came from and its
     * age.
     */
    private fun record(failed: SheetSource) {
        attempts += listOf(stage.label) + QuickDownloadFailures.details(failed.failureDetail!!) +
            QuickDownloadFailures.linkLines(failed.candidate, now())
    }

    /**
     * Fresh link renewal: what to prepare after [target]'s links were gone, in this order. On the
     * browser's page: the same video's newest links the page has now (the player's first), then
     * the page read again quietly, at most [BrowserPageReader.rereads] times and one at a time (a
     * page without player data — a notice or a check — is not used). The same video is the one
     * the Same-file check ([SameFile]) proves; a similar length never is. Never another video of
     * the page: the sheet shows the page's own video or says why not, and offers
     * "Reload page and try again". After an ad, the video the player asked for comes before the
     * page read again. Null when nothing is left.
     */
    private suspend fun nextAttempt(
        target: MediaGroup,
        page: DetectedPage,
    ): Pair<MediaGroup, Attempt>? {
        if (page.owner == LookupOwner.BROWSER) {
            val videos = pageVideos(page)
            val requests = page.candidates.filter { it.isSavable && it.mediaUrl !in deadLinks }
            // Ad filter: an ad's newest link is the same ad.
            if (!firstWasAd) {
                val same = SameFile.videoIn(target, videos)
                (same as? SameVideo.Found)?.videos.orEmpty()
                    .mapNotNull { FreshLinks.playerFirst(it, requests).untried() }
                    .firstOrNull { offered(it, Attempt.NEWEST, proofOf(page)) }
                    ?.let { return it to Attempt.NEWEST }
                if (same == SameVideo.Unconfirmed) {
                    attempts += listOf(Attempt.NEWEST.label, UNCONFIRMED)
                }
            } else {
                playerVideo(target, videos, page)?.let { return it to Attempt.PLAYER }
            }
            reread(target, page)?.let { return it to Attempt.REREAD }
        }
        return pageVideoAfterAd(target, page)?.let { it to Attempt.PAGE_VIDEO }
    }

    /**
     * Fresh link renewal, Ad filter: once the video first tapped proved an ad, the page's own video
     * behind it ([MediaGroups.pageVideoAfterAd]: the page's main video, never a preview or another
     * ad), once. A video that failed is never followed by another one: only an ad gives way.
     */
    private fun pageVideoAfterAd(target: MediaGroup, page: DetectedPage): MediaGroup? {
        if (!firstWasAd || pageVideoTried) return null
        pageVideoTried = true
        val videos = pageVideos(page)
        var skipped = listOf(target) + sameVideo(target, videos) + videos.filter { video ->
            video.candidates.any { it.mediaUrl in skippedAds }
        }
        repeat(MAX_SKIPPED_ADS) {
            val video = MediaGroups.pageVideoAfterAd(videos, skipped, page.facts)?.untried()
                ?: return null
            if (offered(video, Attempt.PAGE_VIDEO, proofOf(page))) {
                return MediaGroups.withPageFacts(video, page.facts)
            }
            skipped = skipped + video
        }
        return null
    }

    /**
     * Fresh link renewal, Ad filter: after the video first tapped proved an ad, the video the
     * page's player asked for ([FreshLinks.playerVideoAfterAd]) that passes [PageVideoProof]; at
     * most [MAX_SKIPPED_ADS] ads it fetched are skipped on the way. The ad's length says nothing
     * about the page's video.
     */
    private fun playerVideo(
        target: MediaGroup,
        videos: List<MediaGroup>,
        page: DetectedPage,
    ): MediaGroup? {
        repeat(MAX_SKIPPED_ADS) {
            val video = FreshLinks.playerVideoAfterAd(
                target,
                videos,
                deadLinks + skippedAds,
                page.facts,
            )?.untried() ?: return null
            if (offered(video, Attempt.PLAYER, proofOf(page))) return video
        }
        return null
    }

    /**
     * Fresh link renewal: the page's [videos] that are [target] itself, as the Same-file check
     * ([SameFile.videoIn]) proves: one of its addresses, or one of its files under a fresh
     * signature (the script's and the player's copies of one file).
     */
    private fun sameVideo(target: MediaGroup, videos: List<MediaGroup>): List<MediaGroup> =
        (SameFile.videoIn(target, videos) as? SameVideo.Found)?.videos.orEmpty()

    /**
     * Fresh link renewal: [target] in the page read again, with a link this sheet did not try yet.
     */
    private suspend fun reread(target: MediaGroup, page: DetectedPage): MediaGroup? {
        val userAgent = store.browserUserAgent
            ?: page.candidates.firstNotNullOfOrNull { it.requestContext.userAgent }
        while (rereadsUsed < pageReader.rereads) {
            rereadsUsed++
            when (val read = pageReader.read(page.pageUrl, userAgent)) {
                is PageReread.Found -> {
                    val facts = read.facts ?: page.facts
                    val videos = MediaGroups.pageVideos(read.candidates.filter { it.isSavable })
                    // Ad filter: after an ad, the page's own video, not the ad again.
                    val same = if (firstWasAd) null else SameFile.videoIn(target, videos)
                    val again = if (firstWasAd) {
                        pageVideoIn(videos, facts)
                    } else {
                        (same as? SameVideo.Found)?.videos.orEmpty()
                            .firstNotNullOfOrNull { it.untried() }
                    }
                    if (again == null) {
                        val status = if (same == SameVideo.Unconfirmed) UNCONFIRMED else
                            "Status: no new link"
                        attempts += listOf(Attempt.REREAD.label, status)
                    } else {
                        val proof = proofOf(page, page.candidates + read.candidates, facts)
                        if (offered(again, Attempt.REREAD, proof)) {
                            return MediaGroups.withPageFacts(again, facts)
                        }
                    }
                }
                PageReread.NoPlayer -> {
                    attempts += listOf(Attempt.REREAD.label, NO_PLAYER_DATA)
                    return null
                }
                PageReread.Failed -> attempts += listOf(Attempt.REREAD.label, "Status: not read")
            }
        }
        return null
    }

    /**
     * Ad filter: the page's video in a re-read's [videos], without the links tried or skipped here.
     */
    private fun pageVideoIn(videos: List<MediaGroup>, facts: PageVideoFacts?): MediaGroup? {
        val left = videos.filter { video ->
            video.candidates.none { it.mediaUrl in deadLinks || it.mediaUrl in skippedAds }
        }
        return MediaGroups.mainVideo(left, playing = null, facts = facts)
    }

    /**
     * Fresh link renewal: a failure's Details: one attempt's lines alone, several each under its
     * name.
     */
    private fun attemptLines(): List<String> =
        attempts.singleOrNull()?.drop(1) ?: attempts.flatten()

    /**
     * The group without the addresses that already answered "gone" or were skipped as ads
     * (Ad filter); null when none is left.
     */
    private fun MediaGroup.untried(): MediaGroup? {
        val left = candidates.filter { it.mediaUrl !in deadLinks && it.mediaUrl !in skippedAds }
        return when {
            left.isEmpty() -> null
            left.size == candidates.size -> this
            else -> copy(candidates = left)
        }
    }

    /**
     * Fresh link renewal: prepares [next] by itself: a fresh link of the same video keeps the
     * sheet's header. [next] is always the page's own video, never another one.
     */
    private fun show(next: MediaGroup, attempt: Attempt) {
        stage = attempt
        // Ad filter: after an ad the page's video shows its own length, under the page's title.
        val shown = next.copy(title = next.title ?: firstVideo?.title)
        group = shown
        mutableUiState.update {
            val header = if (firstWasAd) {
                shown.header().let { own ->
                    own.copy(thumbnailUrl = own.thumbnailUrl ?: it.header?.thumbnailUrl)
                }
            } else {
                it.header ?: shown.header()
            }
            it.copy(
                header = header,
                choices = null,
                selectedId = null,
                selectionByUser = false,
                freshLink = false,
                adSkipped = false,
                attemptDetails = emptyList(),
                canReload = false,
            )
        }
        loading = null
        load()
    }

    /**
     * Fresh link renewal: Details of every attempt once a fresh link or the next video was
     * prepared; when the last one failed too, the failure's Details list them all, and on the
     * browser's page the error offers "Reload page and try again".
     */
    private fun showAttempts(
        shown: MediaGroup,
        sources: List<SheetSource>,
        recorded: Boolean,
        page: DetectedPage?,
    ) {
        val failed = sources.firstOrNull { it.failureDetail != null }
        mutableUiState.update { state ->
            if (state.choices != null) {
                if (stage == Attempt.FIRST) return@update state
                val ready = shown.candidates.first()
                val host = shown.candidates.firstNotNullOfOrNull {
                    QuickDownloadFailures.hostOf(it.mediaUrl)
                }
                val lines = listOf(stage.label) + listOfNotNull(host?.let { "Host: $it" }) +
                    "Status: ready" + chosenLines + QuickDownloadFailures.linkLines(ready, now())
                state.copy(
                    freshLink = !firstWasAd,
                    adSkipped = firstWasAd,
                    attemptDetails = attempts.flatten() + lines,
                )
            } else {
                if (failed == null || (stage == Attempt.FIRST && !recorded)) return@update state
                if (!recorded) record(failed)
                state.copy(
                    freshLink = false,
                    attemptDetails = emptyList(),
                    failureDetails = attemptLines(),
                    canReload = (deadLinks.isNotEmpty() || skippedAds.isNotEmpty()) &&
                        page?.owner == LookupOwner.BROWSER,
                    adSkipped = false,
                )
            }
        }
    }

    /**
     * Fresh link renewal: Try again asks the page's current files instead of the same dead address:
     * a page Home found is read again ([DetectedMediaStore.readPageAgain]). The video that failed
     * first is looked for there ([MediaGroups.refreshed]) and prepared again (never another video).
     * On the browser's page whose links were gone, Try again looks for a fresh link as the first
     * failure did ([nextAttempt]): the page's newest links, the player's own request, the page read
     * again; with nothing fresh the error stays and offers "Reload page and try again".
     */
    private fun readPageAgainThenLoad() {
        val current = group ?: return
        val target = firstVideo ?: current
        val page = chainPage(target)
        if (page == null) {
            load()
            return
        }
        loading?.cancel()
        val before = mutableUiState.value
        mutableUiState.update {
            it.copy(
                loading = true,
                failure = null,
                failureDetails = emptyList(),
                canReload = false,
            )
        }
        loading = viewModelScope.launch {
            val looked = deadLinks.isNotEmpty() || skippedAds.isNotEmpty()
            if (page.owner == LookupOwner.BROWSER && looked) {
                // The attempts so far stay in Details; the re-reads may run once more.
                firstVideo = target
                rereadsUsed = 0
                pageVideoTried = false
                val next = nextAttempt(target, page)
                if (next != null) {
                    show(next.first, next.second)
                    return@launch
                }
                mutableUiState.update {
                    it.copy(
                        loading = false,
                        failure = before.failure,
                        failureDetails = attemptLines(),
                        canReload = true,
                    )
                }
                loading = null
                return@launch
            }
            val fresh = if (page.owner == LookupOwner.HOME) {
                store.readPageAgain(page.pageUrl) ?: page
            } else {
                page
            }
            val again = MediaGroups.refreshed(target, pageVideos(fresh)) ?: target
            group = MediaGroups.withPageFacts(again, fresh.facts)
            firstVideo = null
            firstWasAd = false
            stage = Attempt.FIRST
            attempts.clear()
            pageVideoTried = false
            mutableUiState.update {
                it.copy(
                    header = group?.header(),
                    choices = null,
                    selectedId = null,
                    selectionByUser = false,
                    freshLink = false,
                    attemptDetails = emptyList(),
                )
            }
            loading = null
            load()
        }
    }

    /** The page's videos as the found list counts them (Page video finder). */
    private fun pageVideos(page: DetectedPage): List<MediaGroup> = MediaGroups.pageVideos(
        page.candidates.take(DetectedMediaStore.MAX_CANDIDATES).filter { it.isSavable },
        adapterSite = page.adapterSite,
    )

    private fun needsSizeProbe(source: SheetSource): Boolean =
        source.asset == null || source.candidate.contentLengthBytes?.takeIf { it > 0 } == null ||
            source.candidate.videoId?.startsWith("facebook:") == true &&
            source.candidate.bitrateBitsPerSecond != null

    private fun showChoices(rows: QuickChoices?, sources: List<SheetSource>, waiting: Boolean) {
        // Ended live size sample: a row keeps the size its pieces gave while its file states none.
        val choices = rows?.let {
            QuickDownloadChoices.withSampledSizes(it, sampledSizes, sampling)
        }
        mutableUiState.update { state ->
            val picture = state.header?.thumbnailUrl
                ?: sources.firstNotNullOfOrNull { ThumbnailUrls.https(it.asset?.thumbnailUrl) }
            // No format could be read: a Download queued before the qualities is dropped.
            val failed = choices == null && !waiting
            val failure = sources.firstNotNullOfOrNull { it.failureDetail }
            state.copy(
                header = choices?.let {
                    SheetHeader(it.title, it.source, it.durationMillis, it.isAudioOnly, picture)
                } ?: state.header,
                choices = choices,
                loading = waiting && choices == null,
                failure = if (failed) {
                    val reason = sources.firstNotNullOfOrNull { it.failure }
                    failure?.let(QuickDownloadFailures::message)
                        ?: QuickDownloadFailures.message(reason)
                } else {
                    null
                },
                failureDetails = sources.firstOrNull { it.failureDetail != null }
                    ?.takeIf { failed }
                    ?.let { source ->
                        QuickDownloadFailures.details(source.failureDetail!!) +
                            QuickDownloadFailures.linkLines(source.candidate, now())
                    }
                    .orEmpty(),
                selectedId = state.selectedId
                    ?.takeIf { id -> choices?.option(id)?.let { state.keeps(it) } == true }
                    ?: choices?.let {
                        QuickDownloadChoices.preselect(it, state.defaultQuality)?.id
                    },
                startsWhenReady = state.startsWhenReady && !failed,
                downloadStatus = if (failed && state.startsWhenReady) {
                    PreviewDownloadStatus.Idle
                } else {
                    state.downloadStatus
                },
            )
        }
        choices?.let(::sampleEndedLive)
        startWhenReady()
    }

    /**
     * Ended live size sample: the rows of a recently ended live state no size, so a sample of
     * their files' pieces is read once, by itself, and each row shows its size as it comes.
     */
    private fun sampleEndedLive(choices: QuickChoices) {
        val tracks = QuickDownloadChoices.endedLiveTracks(choices)
            .filter { sampledFiles.add(it.url) }
        if (tracks.isEmpty()) return
        val files = tracks.map { it.url }.toSet()
        sampling += files
        viewModelScope.launch {
            try {
                endedLiveSizes.estimate(tracks) { url, size ->
                    sampledSizes[url] = size
                    refreshSampledSizes()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // A sample that breaks leaves its rows "Size unknown"; the download is not touched.
            } finally {
                sampling -= files
                refreshSampledSizes()
            }
        }
        refreshSampledSizes()
    }

    private fun refreshSampledSizes() {
        mutableUiState.update { state ->
            state.copy(
                choices = state.choices?.let {
                    QuickDownloadChoices.withSampledSizes(it, sampledSizes, sampling)
                },
            )
        }
    }

    /** A failed background size check keeps the stated row, including its honest estimate. */
    private suspend fun inspectSize(initial: SheetSource): SheetSource {
        return when (val result = resolveSafely(initial.candidate)) {
            is VariantResolutionResult.Failure ->
                initial.copy(failure = result.reason, failureDetail = result)
            is VariantResolutionResult.Success -> {
                val original = initial.asset
                if (original == null) {
                    SheetSource(initial.candidate, result.asset, resolved = true)
                } else {
                    val read = result.asset.variants.firstOrNull { it.isPreviewable }
                    // Ad filter: the length the check read proves (or disproves) the page's video.
                    val length = original.durationMillis ?: result.asset.durationMillis
                    val variants = original.variants.map { variant ->
                        variant.copy(
                            sizeBytes = read?.sizeBytes ?: variant.sizeBytes,
                            sizeAccuracy = if (read?.sizeBytes != null) {
                                read.sizeAccuracy
                            } else {
                                variant.sizeAccuracy
                            },
                        ).measuredBy(read)
                    }
                    initial.copy(
                        asset = original.copy(durationMillis = length, variants = variants),
                        failure = null,
                    )
                }
            }
        }
    }

    /**
     * What the check read from the file and the site did not state: the picture of a file named
     * only "HD"/"SD" (its row is renamed in place), its sound's codec and bitrate (the M4A's
     * estimate) and its length.
     */
    private fun MediaVariant.measuredBy(read: MediaVariant?): MediaVariant {
        if (read == null) return this
        val picture = height == null && read.height != null
        return copy(
            width = if (picture) read.width else width,
            height = if (picture) read.height else height,
            framesPerSecond = if (picture) read.framesPerSecond else framesPerSecond,
            codecs = codecs.ifEmpty { read.codecs },
            durationMillis = durationMillis ?: read.durationMillis,
            audioBitrateBitsPerSecond = audioBitrateBitsPerSecond ?: read.audioBitrateBitsPerSecond,
        )
    }

    private suspend fun resolveForDownload(candidate: MediaCandidate): VariantResolutionResult {
        val waits = listOf(1_000L, 3_000L)
        for (attempt in 0..waits.size) {
            val result = resolveSafely(candidate)
            val transient = result is VariantResolutionResult.Failure && (
                result.reason == VariantResolutionFailure.NETWORK ||
                    result.httpStatusCode in setOf(502, 503, 504)
                )
            if (!transient || attempt == waits.size) return result
            delay(waits[attempt])
        }
        error("Retry budget exhausted")
    }

    /**
     * Page video finder: a failure says what went wrong. An address the phone cannot fetch
     * (`blob:`, `data:`) is not requested at all, and an exception is mapped by its type, so a list
     * of qualities the app could not read is never "could not be reached".
     */
    private suspend fun resolveSafely(candidate: MediaCandidate): VariantResolutionResult {
        val host = QuickDownloadFailures.hostOf(candidate.mediaUrl)
        if (candidate.mediaUrl.toHttpUrlOrNull() == null) {
            return VariantResolutionResult.Failure(
                VariantResolutionFailure.INVALID_URL,
                step = ResolutionStep.ADDRESS,
                host = host,
            )
        }
        return try {
            // Sound pairing: a page's one-file DASH video qualities with their manifest's sound.
            DashSoundPairing.pair(resolver.resolve(candidate), candidate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            VariantResolutionResult.Failure(
                QuickDownloadFailures.reasonOf(error),
                host = host,
                // TikTok adapter: the class name only, never the message.
                error = error.javaClass.simpleName.ifBlank { error.javaClass.name },
            )
        }
    }

    private suspend fun enqueue(option: SheetOption, preferences: DownloadPreferences) {
        var details = emptyList<String>()
        val status = try {
            when (val prepared = prepare(option)) {
                is Prepared.Failed -> {
                    details = freshLinkAfterDownload(option, prepared) ?: return
                    PreviewDownloadStatus.Rejected(prepared.message)
                }
                is Prepared.Ready -> when (
                    val result = downloadStarter.enqueue(
                        prepared.asset,
                        prepared.variant,
                        origin(option, prepared.base),
                    )
                ) {
                    is EnqueueResult.Started -> {
                        // Downloads shows the video's picture until the file has a frame.
                        mutableUiState.value.header?.thumbnailUrl?.let { url ->
                            downloadThumbnails.saveFrom(result.taskId, url)
                        }
                        // Lookup reuse: if its links stop working, the video is looked up afresh.
                        lookupVideoId(option.source.candidate)?.let { videoId ->
                            lookups.rememberDownload(result.taskId, videoId)
                        }
                        PreviewDownloadStatus.Queued(
                            fileName = result.fileName,
                            waitingForUnmetered = DownloadNetworkPolicy.stateFor(
                                network.snapshot.value,
                                preferences,
                            ) == TransferNetworkState.WAITING_FOR_UNMETERED,
                        )
                    }

                    is EnqueueResult.Rejected -> {
                        if (result.reason in SiteLookupCache.LINK_FAILURES) forgetLinks(option)
                        PreviewDownloadStatus.Rejected(result.message)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PreviewDownloadStatus.Rejected("The download could not be queued. Try again.")
        }
        // The selection is locked while queueing, so the status belongs to [option].
        setStatus(status, details)
    }

    /**
     * The asset and variant to queue. A source shown from what the site stated is resolved now,
     * and the option's conversion (MP3, or the video's sound kept as M4A) is applied to the
     * resolved file. The asset is named after the video and the variant after its quality.
     */
    private suspend fun prepare(
        option: SheetOption,
        title: String? = mutableUiState.value.downloadTitle,
    ): Prepared {
        // Zawgyi titles: the Unicode title, or the original after "Use original title".
        val resolved = when (val result = resolveForDownload(option.source.candidate)) {
            is VariantResolutionResult.Failure -> {
                if (result.httpStatusCode in LINK_GONE_STATUSES ||
                    result.reason == VariantResolutionFailure.EXPIRED_URL
                ) {
                    forgetLinks(option)
                }
                val siteVideo = option.source.candidate.videoId != null
                return Prepared.Failed(
                    when {
                        result.reason == VariantResolutionFailure.DRM_PROTECTED ||
                            result.reason == VariantResolutionFailure.UNSUPPORTED_CODEC ->
                            QuickDownloadFailures.message(result.reason)
                        // A site's lookup has its other qualities to offer.
                        siteVideo -> QUALITY_UNAVAILABLE
                        // Page video finder: another site's video says why, with the request in
                        // Details.
                        else -> QuickDownloadFailures.message(result)
                    },
                    QuickDownloadFailures.details(result) +
                        QuickDownloadFailures.linkLines(option.source.candidate, now()),
                    result,
                )
            }
            is VariantResolutionResult.Success -> result.asset
        }
        // A DASH row is its own Representation, never the first one with the manifest's address
        // (every DASH row has it) and never another track.
        val dash = option.variant.kind == MediaKind.DASH
        val wantedId = option.variant.mp3?.sourceVariantId
            ?: option.source.asset?.variants?.firstOrNull {
                it.playbackUrl == option.variant.playbackUrl && it.mp3 == null && !it.audioFromVideo
            }?.id ?: option.variant.id
        val base = if (dash) {
            DashRowFile.find(resolved.variants, option.variant)
                ?: return Prepared.Failed(
                    QUALITY_UNAVAILABLE,
                    listOf(DashRowFile.missingLine(option.variant)),
                )
        } else {
            resolved.variants.firstOrNull { it.id == wantedId && it.isPreviewable }
                ?: resolved.variants.singleOrNull { it.isPreviewable && it.mp3 == null }
                ?: return Prepared.Failed(QUALITY_UNAVAILABLE)
        }
        val stated = if (dash) {
            option.variant
        } else {
            option.source.asset?.variants?.firstOrNull { it.id == wantedId } ?: option.variant
        }
        val file = base.copy(
            width = base.width ?: stated.width,
            height = base.height ?: stated.height,
            framesPerSecond = base.framesPerSecond ?: stated.framesPerSecond,
            bitrateBitsPerSecond = base.bitrateBitsPerSecond ?: stated.bitrateBitsPerSecond,
        )
        val sound = if (option.variant.audioFromVideo) {
            AudioFromVideo.of(file, resolved.durationMillis)
                ?: return Prepared.Failed(AUDIO_UNAVAILABLE)
        } else {
            file
        }
        val variant = when (val kbps = option.variant.mp3?.bitrateKbps) {
            null -> sound
            else -> Mp3Variants.of(sound, kbps, resolved.durationMillis)
                ?: return Prepared.Failed(MP3_UNAVAILABLE)
        }
        return Prepared.Ready(
            resolved.copy(title = title ?: resolved.title),
            named(variant),
            base = file,
        )
    }

    /**
     * Fresh link renewal: where [option]'s file came from, for a fresh link if it dies after the
     * download started: the page's file, the file before its conversion ([base]), who looked the
     * video up (the page's or the lookup's owner; the browser's when neither is this page's) and
     * the agent of the browser's requests.
     */
    private fun origin(option: SheetOption, base: MediaVariant): DownloadOrigin {
        val candidate = option.source.candidate
        val owner = store.page.value?.takeIf { it.pageUrl == candidate.pageUrl }?.owner
            ?: store.lookup.value?.takeIf { it.pageUrl == candidate.pageUrl }?.owner
            ?: LookupOwner.BROWSER
        return DownloadOrigin(
            candidate = candidate,
            base = base,
            owner = owner,
            userAgent = store.browserUserAgent,
        )
    }

    /**
     * Fresh link renewal: Download met the row's link gone on a page the sheet can look further on:
     * the sheet looks for a fresh link of the video as when it opened, and the fresh link's row
     * then downloads by itself (the early Download, in the tapped row's section). Null when a fresh
     * link is being prepared; else the Details of Download's failure (every attempt when the sheet
     * looked further).
     */
    private suspend fun freshLinkAfterDownload(
        option: SheetOption,
        failed: Prepared.Failed,
    ): List<String>? {
        val failure = failed.failure?.takeIf(::isGone) ?: return failed.details
        val shown = group ?: return failed.details
        val page = chainPage(shown)
        if (page == null || loading?.isActive == true) return failed.details
        record(option.source.copy(failure = failure.reason, failureDetail = failure))
        deadLinks += shown.candidates.map { it.mediaUrl }
        val target = firstVideo ?: shown.also { firstVideo = it }
        val (next, attempt) = nextAttempt(target, page) ?: return attemptLines()
        // Mobile data was already asked about for this tap.
        earlyMeteredConfirmed = true
        mutableUiState.update {
            it.copy(
                startsWhenReady = true,
                earlySection = option.section,
                startedNote = null,
                downloadStatus = PreviewDownloadStatus.Idle,
            )
        }
        show(next, attempt)
        return null
    }

    /** Lookup reuse: the video's links stopped working; its next lookup asks the site again. */
    private fun forgetLinks(option: SheetOption) {
        lookupVideoId(option.source.candidate)?.let(lookups::forgetVideo)
    }

    /**
     * The name the lookup of [candidate]'s video is remembered under: a post's other item came with
     * the item the link names, so its lookup is that item's.
     */
    private fun lookupVideoId(candidate: MediaCandidate): String? =
        postItems.firstOrNull()?.takeIf { candidate.postItem != null }
            ?.candidates?.firstNotNullOfOrNull { it.videoId }
            ?: candidate.videoId

    /** [group]'s post's video items, when its rows name more than one. */
    private fun postItemsOf(group: MediaGroup?): List<MediaGroup> {
        group ?: return emptyList()
        val items = MediaGroups.postItems(group) { it.isSavable && merges.canMerge(it) }
        return items.takeIf { it.size > 1 }.orEmpty()
    }

    private fun postItemChoices(): List<PostItemChoice> = postItems.mapNotNull { item ->
        val post = item.candidates.firstNotNullOfOrNull { it.postItem } ?: return@mapNotNull null
        PostItemChoice(item.key, post.number, post.label)
    }

    /**
     * Video files are named after their quality ("720p"); the video's sound kept as M4A after the
     * video alone; other audio keeps its own label ("MP3 192 kbps"). The quality is the row's
     * ("720p" for a 720 × 1280 portrait video, not "1280p").
     */
    private fun named(variant: MediaVariant): MediaVariant {
        if (variant.trackType == MediaTrackType.AUDIO) {
            val plainM4a = variant.audioFromVideo && variant.mp3 == null
            return if (plainM4a) variant.copy(label = null) else variant
        }
        val height = variant.height ?: return variant.copy(label = null)
        val standard = QuickDownloadChoices.standardHeight(variant.width, height)
        return variant.copy(label = YftQualityNames.quality(standard, variant.framesPerSecond))
    }

    private suspend fun currentPreferences(): DownloadPreferences = try {
        downloadPreferences.preferences.first()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DownloadPreferences()
    }

    private fun setStatus(status: PreviewDownloadStatus, details: List<String> = emptyList()) {
        mutableUiState.update {
            it.copy(
                downloadStatus = status,
                downloadDetails = details.takeIf { status is PreviewDownloadStatus.Rejected }
                    .orEmpty(),
            )
        }
    }

    /**
     * The user's pick, a row of known quality, or a pick being downloaded stays; the sheet's own
     * pick of a file of unknown quality is made again as rows come, so the MP4 measured after the
     * page's OGG is preselected (w3schools' html5_video).
     */
    private fun QuickDownloadUiState.keeps(option: SheetOption): Boolean =
        selectionByUser || option.variant.height != null ||
            downloadStatus != PreviewDownloadStatus.Idle

    private val QuickDownloadUiState.canChangeSelection: Boolean
        get() = !postQueueing && downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered

    private fun MediaGroup.header(): SheetHeader = SheetHeader(
        title = title ?: if (candidates.all { it.isAudio() }) "Audio" else "Video",
        source = QuickDownloadChoices.host(pageUrl),
        durationMillis = durationMillis,
        audioOnly = candidates.all { it.isAudio() },
        thumbnailUrl = ThumbnailUrls.of(candidates),
    )

    /** Fresh link renewal: the attempts of one video, by the name its Details give them. */
    private enum class Attempt(val label: String) {
        FIRST("First video"),
        NEWEST("Page's newest link"),
        PLAYER("Player's link"),
        REREAD("Page read again"),
        PAGE_VIDEO("Page's video"),
    }

    private sealed interface Prepared {
        /**
         * [base]: the file before its conversion (Fresh link renewal: the quality a fresh link
         * keeps).
         */
        data class Ready(
            val asset: MediaAsset,
            val variant: MediaVariant,
            val base: MediaVariant = variant,
        ) : Prepared
        data class Failed(
            val message: String,
            val details: List<String> = emptyList(),
            /** Fresh link renewal: the resolver's answer when the file could not be read. */
            val failure: VariantResolutionResult.Failure? = null,
        ) : Prepared
    }

    internal companion object {
        /** A video rarely has more qualities; more would only cost requests. */
        const val MAX_SOURCES = 12

        /** The sheet's title while the page's lookup has no title yet. */
        const val WAITING_TITLE = "Video"
        const val MP3_UNAVAILABLE = "This audio can't be converted to MP3. Try M4A."
        const val AUDIO_UNAVAILABLE = "This video's sound can't be saved on its own."
        const val QUALITY_UNAVAILABLE = "This quality is not available now — choose another"

        /** Ad filter: nothing on the page passed as its video; no ad is offered instead. */
        const val ONLY_AD_FOUND = "Only an ad was found, not the page's video."

        /**
         * Fresh link renewal: a file of the page shares the failed one's path, but nothing
         * proves it is the same video (Same-file check), so it is not offered.
         */
        const val UNCONFIRMED = "Status: same video not confirmed"

        /** Ad filter: the ads the sheet skips in one step before it gives up on that step. */
        private const val MAX_SKIPPED_ADS = 4

        /** Ad filter: marks the stand-in of the page's video a reload looks for. */
        private const val PAGE_VIDEO_FRAGMENT = "#yft-page-video"

        /** Fresh link renewal: a page's link that answers one of these is dead for this phone. */
        private val LINK_DEAD_STATUSES = setOf(401, 403, 404, 410)
        private const val NO_PLAYER_DATA = "Status: no player data (a notice or a check)"

        /** Lookup reuse: HTTP 403 and 410 mean the lookup's links no longer work. */
        private val LINK_GONE_STATUSES = setOf(403, 410)

        /** Show a site's stated whole file even before its size check completes. */
        fun stated(candidate: MediaCandidate, now: Long = System.currentTimeMillis()): MediaAsset? =
            QuickDownloadMetadata.asset(candidate, now)
    }
}
