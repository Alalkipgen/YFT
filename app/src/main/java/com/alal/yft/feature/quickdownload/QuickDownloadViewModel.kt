package com.alal.yft.feature.quickdownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.AudioFromVideo
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
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.detection.BrowserPageReader
import com.alal.yft.detection.PageReread
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.VideoPlaybackSupport
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
    /** P19: the video's picture; the header shows its placeholder until it loads. */
    val thumbnailUrl: String? = null,
)

data class QuickDownloadUiState(
    /** Null when there is no video to show (the sheet then says so). */
    val header: SheetHeader? = null,
    /** True while the qualities and sizes are being read. */
    val loading: Boolean = false,
    val choices: QuickChoices? = null,
    /** Why no format could be read; Try again reads them once more. */
    val failure: String? = null,
    /** P24: [failure]'s Details: the step, the host and the status of the request that failed. */
    val failureDetails: List<String> = emptyList(),
    /** P24: the Details of a Download the sheet could not prepare. */
    val downloadDetails: List<String> = emptyList(),
    /** P12: false when Try again cannot help (a protected video); the sheet hides it. */
    val canRetry: Boolean = true,
    /** P12: the sheet waits for the browser's lookup of the page's video. */
    val findingVideo: Boolean = false,
    /**
     * P28: the sheet waits a few seconds for the page's own video, because what its player
     * showed first may be an ad: it says "Finding the page's video…".
     */
    val findingPageVideo: Boolean = false,
    /** P28: the video may be the ad before the page's video; the sheet says so. */
    val maybeAd: Boolean = false,
    /**
     * P29: the first video's file is gone (HTTP 403, 404, 410 or an address YFT can't fetch),
     * so the sheet shows the page's next video and says so.
     */
    val nextVideo: Boolean = false,
    /** P29: [nextVideo]'s Details: both attempts, each with its step, host and status. */
    val attemptDetails: List<String> = emptyList(),
    /**
     * P37: the first link was gone, so the sheet shows the same video from a fresh link (the
     * page's newest, the player's own request, or the page read again) and says so; Details in
     * [attemptDetails].
     */
    val freshLink: Boolean = false,
    /**
     * P37: nothing fresh was found for the browser's page: the error offers "Reload page and try
     * again", which reloads the tab without its cache and reopens the sheet.
     */
    val canReload: Boolean = false,
    /** P12: how many more videos the page has; a row opens their list. */
    val otherVideos: Int = 0,
    val defaultQuality: QualityPreference = DownloadPreferences().defaultQuality,
    val selectedId: String? = null,
    val downloadStatus: PreviewDownloadStatus = PreviewDownloadStatus.Idle,
    /** P18: what Download takes while the sheet waits: the Default quality's video, or audio. */
    val earlySection: OptionSection = OptionSection.VIDEO,
    /** P18: Download was tapped before the qualities came; it starts as soon as they do. */
    val startsWhenReady: Boolean = false,
    /** P18: the quality an early Download took ("Downloading 480p — 720p not available"). */
    val startedNote: String? = null,
) {
    val selectedOption: SheetOption? get() = choices?.option(selectedId)

    /** P18: the sheet still says "Getting qualities…"; Download then queues [earlySection]. */
    val waitsForQualities: Boolean
        get() = choices == null && loading && failure == null && header != null

    val canDownload: Boolean
        get() = (selectedOption != null || waitsForQualities && !startsWhenReady) &&
            downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered
}

/**
 * The download sheet (P3, P3-FIX): one video with an Audio section (M4A, MP3) and a Video section
 * (one row per resolution).
 *
 * The video is the group [DetectedMediaStore] selected (Home's lookup, the found list or the
 * browser's Download button), else the page's only video. On a site's video page the sheet may
 * open before the browser's lookup found it (P12): it waits in its loading state for that same
 * lookup, and a failed lookup shows its message with Try again, which asks the browser again.
 * Each of its candidates is resolved like Download as does, so heights, sizes and companion audio
 * are real; a whole file whose site already stated its picture and size is shown without a request
 * and resolved on Download. Download queues the option through [PreviewDownloadStarter], which
 * applies Wi-Fi only; mobile data asks first when the user chose to be asked.
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
) : ViewModel() {
    /**
     * P16: a sheet opened for a lookup (Home's link, a feed's video on screen) waits on it even
     * when the page under it has a video of its own.
     */
    private val opensOnLookup = store.awaitsLookup && store.lookup.value != null
    private var group: MediaGroup? = (
        store.selection.value ?: store.page.value
            ?.takeUnless { opensOnLookup }
            ?.let { page ->
                val savable = page.candidates.take(DetectedMediaStore.MAX_CANDIDATES)
                    .filter { it.isSavable }
                MediaGroups.pageVideos(savable, adapterSite = page.adapterSite).singleOrNull()
            }
        )?.let(::withPageFacts)

    /** P12: the lookup this sheet waits on, while no video is chosen yet. */
    private var pageLookup: PageVideoLookup? = store.lookup.value.takeIf { group == null }
    private val mutableUiState = MutableStateFlow(
        pageLookup?.let(::lookupState) ?: QuickDownloadUiState(
            header = group?.header(),
            otherVideos = store.otherVideos.value.takeIf { store.selection.value != null } ?: 0,
            maybeAd = store.maybeAd.value && store.selection.value != null,
        ),
    )
    val uiState: StateFlow<QuickDownloadUiState> = mutableUiState.asStateFlow()
    private var loading: Job? = null
    private val sizeProbes = Semaphore(2)

    /** P18: the user said yes to mobile data when tapping Download before the qualities came. */
    private var earlyMeteredConfirmed = false

    /**
     * P29: the video that failed first, once the sheet moved on to a fresh link or the page's
     * next video; Try again starts from it again.
     */
    private var firstVideo: MediaGroup? = null

    /** P37: which attempt the sheet shows now (the first link, a fresh one, the next video). */
    private var stage = Attempt.FIRST

    /** P37: the Details of each attempt that failed before, its name first. */
    private val attempts = mutableListOf<List<String>>()

    /** P37: the addresses that answered "gone" in this sheet; never asked again here. */
    private val deadLinks = mutableSetOf<String>()

    /** P37: the quiet re-reads this failure used ([BrowserPageReader.rereads]). */
    private var rereadsUsed = 0

    /** P29: the page's next video is tried once. */
    private var nextTried = false

    /** P29: the first video's failure allows the next video (HTTP 403, 404, 410, an address). */
    private var nextAllowed = false

    /** P37: the clock of the Details' "Link age"; tests set it. */
    internal var now: () -> Long = System::currentTimeMillis

    init {
        if (pageLookup != null) awaitPageVideo() else load()
        // P18: the waiting sheet names the Default quality an early Download takes.
        viewModelScope.launch {
            val quality = currentPreferences().defaultQuality
            mutableUiState.update { it.copy(defaultQuality = quality) }
        }
    }

    /** P16: closing the sheet before its video came stops the lookup it waited on. */
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
     * P37: "Reload page and try again": the browser reloads its tab once without the cache and
     * reopens the sheet when the video comes back with a new link. True when the browser took
     * it, so the sheet closes.
     */
    fun reloadPage(): Boolean {
        if (!mutableUiState.value.canReload) return false
        val target = firstVideo ?: group ?: return false
        return store.reloadPage(PageReload(target.pageUrl, target, deadLinks.toSet()))
    }

    /** P12: "Other videos on this page" asks the browser to open its found list. */
    fun openOtherVideos(): Boolean {
        if (mutableUiState.value.otherVideos <= 0) return false
        store.showFoundList()
        return true
    }

    fun select(selectionId: String) {
        mutableUiState.update { state ->
            if (!state.canChangeSelection) return@update state
            if (state.choices?.option(selectionId) == null) return@update state
            state.copy(
                selectedId = selectionId,
                downloadStatus = PreviewDownloadStatus.Idle,
                startedNote = null,
            )
        }
    }

    /** P18: the Audio or the Video placeholder picked while the qualities are still coming. */
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
        selectionStore.select(option.source.candidate)
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
        if (state.startsWhenReady) {
            // P18: yes to mobile data before the qualities came; their arrival starts it.
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
        // P18: no to mobile data also drops a Download queued before the qualities came.
        mutableUiState.update {
            it.copy(
                downloadStatus = PreviewDownloadStatus.Idle,
                startsWhenReady = false,
                startedNote = null,
            )
        }
    }

    /**
     * P18: Download before the qualities came. The mobile-data question is asked now, while the
     * user looks at the sheet; the pick starts once the rows arrive ([startWhenReady]).
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
     * P18: the rows came, so the queued Download takes the Default quality (else the nearest
     * lower, else the nearest higher one) or the audio, and goes through today's checks.
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
     * P12: shows the browser's lookup until it selects the page's video, then reads that
     * video's formats. A lookup that goes away without one (another page, the browser closed)
     * leaves the sheet's "no longer here".
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
            mutableUiState.update {
                QuickDownloadUiState(
                    header = group?.header(),
                    otherVideos = if (selected != null) store.otherVideos.value else 0,
                    maybeAd = selected != null && store.maybeAd.value,
                ).keepingEarly(it)
            }
            if (selected != null) load()
        }
    }

    /**
     * P16: the waiting sheet's header is what is known before the lookup answers: the page's
     * title or the link itself, its site and YouTube's picture of the video ID.
     */
    private fun lookupState(lookup: PageVideoLookup) = QuickDownloadUiState(
        header = SheetHeader(
            title = lookup.title ?: QuickDownloadChoices.shownLink(lookup.pageUrl)
                ?: WAITING_TITLE,
            source = QuickDownloadChoices.host(lookup.pageUrl),
            durationMillis = null,
            audioOnly = false,
            // P19: a YouTube video's picture follows from its ID, before the lookup ends.
            // P28: another site's page names its picture.
            thumbnailUrl = ThumbnailUrls.https(lookup.thumbnailUrl)
                ?: ThumbnailUrls.youTube(lookup.key),
        ),
        loading = lookup.running,
        failure = lookup.failure,
        canRetry = lookup.canRetry,
        findingVideo = lookup.running,
        findingPageVideo = lookup.running && lookup.findingPageVideo,
    )

    /**
     * P28: [group] with its page's title and picture where its files name none
     * ([MediaGroups.withPageFacts]), when the store's page is its page.
     */
    private fun withPageFacts(group: MediaGroup): MediaGroup {
        val page = store.page.value?.takeIf { it.pageUrl == group.pageUrl } ?: return group
        return MediaGroups.withPageFacts(group, page.facts)
    }

    /**
     * P18: a new waiting state keeps the user's pick and a queued Download; a failed lookup or a
     * video that went away drops the queued Download (Try again needs a new tap).
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
        loading = viewModelScope.launch {
            val quality = currentPreferences().defaultQuality
            mutableUiState.update { it.copy(defaultQuality = quality) }
            val sources = group.candidates.take(MAX_SOURCES).map { candidate ->
                SheetSource(candidate, stated(candidate), resolved = false)
            }.toMutableList()
            showChoices(QuickDownloadChoices.of(group, sources, playback), sources, waiting = true)
            coroutineScope {
                sources.toList().mapIndexed { index, initial ->
                    async {
                        if (!needsSizeProbe(initial)) return@async
                        val updated = sizeProbes.withPermit { inspectSize(initial) }
                        sources[index] = updated
                        val current = mutableUiState.value.choices
                        val choices = if (initial.asset != null && current != null) {
                            QuickDownloadChoices.updateSizes(current, updated)
                        } else {
                            // A generic manifest has no real formats until it is resolved.
                            QuickDownloadChoices.of(group, sources, playback)
                        }
                        showChoices(choices, sources, waiting = true)
                    }
                }.awaitAll()
            }
            val page = chainPage(group)
            // P37: on a page the sheet can look further on, a row the site stated is not
            // offered once its link answered "gone": Download would only meet the same answer.
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
                deadLinks += group.candidates.map { it.mediaUrl }
                val target = firstVideo ?: group.also {
                    firstVideo = it
                    nextAllowed = isGoneForNext(failed.failureDetail!!)
                }
                nextAttempt(target, page!!)?.let { (next, attempt) ->
                    show(next, attempt)
                    return@launch
                }
            }
            showChoices(choices, sources, waiting = false)
            showAttempts(group, sources, recorded, page)
        }
    }

    /**
     * P37 (R18): the group with the addresses the page's player asked for before the links its
     * script names ([FreshLinks.playerFirst]), on a browser page without an adapter.
     */
    private fun withPlayerAddresses(group: MediaGroup): MediaGroup {
        if (stage == Attempt.REREAD) return group
        val page = store.page.value
            ?.takeIf { it.pageUrl == group.pageUrl && !it.adapterSite } ?: return group
        val requests = page.candidates.filter { it.isSavable && it.mediaUrl !in deadLinks }
        return FreshLinks.playerFirst(group, requests)
    }

    /** P29/P37: the store's page of [group] when the sheet may look further for it. */
    private fun chainPage(group: MediaGroup): DetectedPage? {
        if (group.candidates.any { it.videoId != null }) return null
        return store.page.value?.takeIf { it.pageUrl == group.pageUrl && !it.adapterSite }
    }

    /** P37: these choices without the rows of the [gone] links; null when no row is left. */
    private fun QuickChoices.without(gone: Set<MediaCandidate>): QuickChoices? {
        val audio = audio.filter { it.source.candidate !in gone }
        val video = video.filter { it.source.candidate !in gone }
        return if (audio.isEmpty() && video.isEmpty()) null else copy(audio = audio, video = video)
    }

    private fun isGone(failure: VariantResolutionResult.Failure): Boolean =
        failure.httpStatusCode in LINK_DEAD_STATUSES ||
            failure.reason == VariantResolutionFailure.INVALID_URL

    private fun isGoneForNext(failure: VariantResolutionResult.Failure): Boolean =
        failure.httpStatusCode in GONE_STATUSES ||
            failure.reason == VariantResolutionFailure.INVALID_URL

    /** P37: [failed]'s attempt, its name first, with where its link came from and its age. */
    private fun record(failed: SheetSource) {
        attempts += listOf(stage.label) + QuickDownloadFailures.details(failed.failureDetail!!) +
            QuickDownloadFailures.linkLines(failed.candidate, now())
    }

    /**
     * P37: what to prepare after [target]'s links were gone, in FIX_ADD_PLAN's order. On the
     * browser's page: the same video's newest links the page has now (the player's first), the
     * video the player itself asked for, then the page read again quietly, at most
     * [BrowserPageReader.rereads] times and one at a time (a page without player data — a
     * notice or a check — is not used). Then, once, P29's next video. Null when nothing is left.
     */
    private suspend fun nextAttempt(
        target: MediaGroup,
        page: DetectedPage,
    ): Pair<MediaGroup, Attempt>? {
        if (page.owner == LookupOwner.BROWSER) {
            val videos = pageVideos(page)
            val requests = page.candidates.filter { it.isSavable && it.mediaUrl !in deadLinks }
            sameVideo(target, videos)
                .firstNotNullOfOrNull { FreshLinks.playerFirst(it, requests).untried() }
                ?.let { return it to Attempt.NEWEST }
            FreshLinks.playerVideo(target, videos, deadLinks, page.facts)
                ?.untried()
                ?.let { return it to Attempt.PLAYER }
            reread(target, page)?.let { return it to Attempt.REREAD }
        }
        if (nextTried || !nextAllowed) return null
        nextTried = true
        val videos = pageVideos(page)
        val failed = listOf(target) + sameVideo(target, videos)
        return MediaGroups.nextVideo(videos, failed, page.facts)
            ?.untried()
            ?.let { MediaGroups.withPageFacts(it, page.facts) to Attempt.NEXT }
    }

    /**
     * P37: the page's [videos] that are [target] itself: those with one of its addresses or the
     * same address under another signature (the script's and the player's copies of one file);
     * else the one [MediaGroups.refreshed] finds by its length.
     */
    private fun sameVideo(target: MediaGroup, videos: List<MediaGroup>): List<MediaGroup> {
        val urls = target.candidates.map { it.mediaUrl }.toSet()
        val unsigned = FreshLinks::unsigned
        val paths = target.candidates.map { unsigned(it.mediaUrl) }.toSet()
        return videos.filter { video ->
            video.candidates.any { it.mediaUrl in urls || unsigned(it.mediaUrl) in paths }
        }.ifEmpty { listOfNotNull(MediaGroups.refreshed(target, videos)) }
    }

    /** P37: [target] in the page read again, with a link this sheet did not try yet. */
    private suspend fun reread(target: MediaGroup, page: DetectedPage): MediaGroup? {
        val userAgent = store.browserUserAgent
            ?: page.candidates.firstNotNullOfOrNull { it.requestContext.userAgent }
        while (rereadsUsed < pageReader.rereads) {
            rereadsUsed++
            when (val read = pageReader.read(page.pageUrl, userAgent)) {
                is PageReread.Found -> {
                    val videos = MediaGroups.pageVideos(read.candidates.filter { it.isSavable })
                    MediaGroups.refreshed(target, videos)?.untried()?.let { again ->
                        return MediaGroups.withPageFacts(again, read.facts ?: page.facts)
                    }
                    attempts += listOf(Attempt.REREAD.label, "Status: no new link")
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

    /** P37: a failure's Details: one attempt's lines alone, several each under its name. */
    private fun attemptLines(): List<String> =
        attempts.singleOrNull()?.drop(1) ?: attempts.flatten()

    /** The group without the addresses that already answered "gone"; null when none is left. */
    private fun MediaGroup.untried(): MediaGroup? {
        val left = candidates.filter { it.mediaUrl !in deadLinks }
        return when {
            left.isEmpty() -> null
            left.size == candidates.size -> this
            else -> copy(candidates = left)
        }
    }

    /**
     * P29/P37: prepares [next] by itself: a fresh link of the same video keeps the sheet's
     * header; the next video shows its own.
     */
    private fun show(next: MediaGroup, attempt: Attempt) {
        stage = attempt
        val sameVideo = attempt != Attempt.NEXT
        val shown = if (sameVideo) next.copy(title = next.title ?: firstVideo?.title) else next
        group = shown
        mutableUiState.update {
            it.copy(
                header = if (sameVideo) it.header ?: shown.header() else shown.header(),
                choices = null,
                selectedId = null,
                nextVideo = attempt == Attempt.NEXT,
                freshLink = false,
                attemptDetails = emptyList(),
                canReload = false,
            )
        }
        loading = null
        load()
    }

    /**
     * P29/P37: Details of every attempt once a fresh link or the next video was prepared; when
     * the last one failed too, the failure's Details list them all, and on the browser's page
     * the error offers "Reload page and try again".
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
                    "Status: ready" + QuickDownloadFailures.linkLines(ready, now())
                state.copy(
                    nextVideo = stage == Attempt.NEXT,
                    freshLink = stage != Attempt.NEXT,
                    attemptDetails = attempts.flatten() + lines,
                )
            } else {
                if (failed == null || (stage == Attempt.FIRST && !recorded)) return@update state
                if (!recorded) record(failed)
                state.copy(
                    nextVideo = false,
                    freshLink = false,
                    attemptDetails = emptyList(),
                    failureDetails = attemptLines(),
                    canReload = deadLinks.isNotEmpty() && page?.owner == LookupOwner.BROWSER,
                )
            }
        }
    }

    /**
     * P29: Try again asks the page's current files instead of the same dead address: a page Home
     * found is read again ([DetectedMediaStore.readPageAgain]). The video that failed first is
     * looked for there ([MediaGroups.refreshed]) and prepared again, and may move on to the next
     * video once more. P37 (R16): on the browser's page whose links were gone, Try again looks
     * for a fresh link as the first failure did ([nextAttempt]): the page's newest links, the
     * player's own request, the page read again; with nothing fresh the error stays and offers
     * "Reload page and try again".
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
            if (page.owner == LookupOwner.BROWSER && deadLinks.isNotEmpty()) {
                // The attempts so far stay in Details; the re-reads and the next video may
                // run once more.
                firstVideo = target
                rereadsUsed = 0
                nextTried = false
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
            stage = Attempt.FIRST
            attempts.clear()
            nextTried = false
            mutableUiState.update {
                it.copy(
                    header = group?.header(),
                    choices = null,
                    selectedId = null,
                    nextVideo = false,
                    freshLink = false,
                    attemptDetails = emptyList(),
                )
            }
            loading = null
            load()
        }
    }

    /** The page's videos as the found list counts them (P24). */
    private fun pageVideos(page: DetectedPage): List<MediaGroup> = MediaGroups.pageVideos(
        page.candidates.take(DetectedMediaStore.MAX_CANDIDATES).filter { it.isSavable },
        adapterSite = page.adapterSite,
    )

    private fun needsSizeProbe(source: SheetSource): Boolean =
        source.asset == null || source.candidate.contentLengthBytes?.takeIf { it > 0 } == null ||
            source.candidate.videoId?.startsWith("facebook:") == true &&
            source.candidate.bitrateBitsPerSecond != null

    private fun showChoices(choices: QuickChoices?, sources: List<SheetSource>, waiting: Boolean) {
        mutableUiState.update { state ->
            val picture = state.header?.thumbnailUrl
                ?: sources.firstNotNullOfOrNull { ThumbnailUrls.https(it.asset?.thumbnailUrl) }
            // P18: no format could be read: a Download queued before the qualities is dropped.
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
                selectedId = state.selectedId?.takeIf { choices?.option(it) != null }
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
        startWhenReady()
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
                    initial.copy(asset = original.copy(variants = original.variants.map { variant ->
                        variant.copy(
                            sizeBytes = read?.sizeBytes ?: variant.sizeBytes,
                            sizeAccuracy = if (read?.sizeBytes != null) {
                                read.sizeAccuracy
                            } else {
                                variant.sizeAccuracy
                            },
                        ).measuredBy(read)
                    }), failure = null)
                }
            }
        }
    }

    /**
     * P25: what the check read from the file and the site did not state: the picture of a file
     * named only "HD"/"SD" (its row is renamed in place), its sound's codec and bitrate (the
     * M4A's estimate) and its length.
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
     * P24: a failure says what went wrong. An address the phone cannot fetch (`blob:`, `data:`)
     * is not requested at all, and an exception is mapped by its type, so a list of qualities
     * the app could not read is never "could not be reached".
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
            resolver.resolve(candidate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            VariantResolutionResult.Failure(QuickDownloadFailures.reasonOf(error), host = host)
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
                    val result = downloadStarter.enqueue(prepared.asset, prepared.variant)
                ) {
                    is EnqueueResult.Started -> {
                        // P19: Downloads shows the video's picture until the file has a frame.
                        mutableUiState.value.header?.thumbnailUrl?.let { url ->
                            downloadThumbnails.saveFrom(result.taskId, url)
                        }
                        // P17: if its links stop working, the video is looked up afresh.
                        option.source.candidate.videoId?.let { videoId ->
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
    private suspend fun prepare(option: SheetOption): Prepared {
        val title = mutableUiState.value.choices?.title
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
                        // P24: another site's video says why, with the request in Details.
                        else -> QuickDownloadFailures.message(result)
                    },
                    QuickDownloadFailures.details(result) +
                        QuickDownloadFailures.linkLines(option.source.candidate, now()),
                    result,
                )
            }
            is VariantResolutionResult.Success -> result.asset
        }
        val wantedId = option.variant.mp3?.sourceVariantId
            ?: option.source.asset?.variants?.firstOrNull {
                it.playbackUrl == option.variant.playbackUrl && it.mp3 == null && !it.audioFromVideo
            }?.id ?: option.variant.id
        val base = resolved.variants.firstOrNull { it.id == wantedId && it.isPreviewable }
            ?: resolved.variants.singleOrNull { it.isPreviewable && it.mp3 == null }
            ?: return Prepared.Failed(QUALITY_UNAVAILABLE)
        val stated = option.source.asset?.variants?.firstOrNull { it.id == wantedId }
            ?: option.variant
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
        return Prepared.Ready(resolved.copy(title = title ?: resolved.title), named(variant))
    }

    /**
     * P37: Download met the row's link gone on a page the sheet can look further on: the sheet
     * looks for a fresh link of the video as when it opened, and the fresh link's row then
     * downloads by itself (P18's early Download, in the tapped row's section). Null when a fresh
     * link is being prepared; else the Details of Download's failure (every attempt when the
     * sheet looked further).
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
        val target = firstVideo ?: shown.also {
            firstVideo = it
            nextAllowed = isGoneForNext(failure)
        }
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

    /** P17: the video's links stopped working; its next lookup asks the site again. */
    private fun forgetLinks(option: SheetOption) {
        option.source.candidate.videoId?.let(lookups::forgetVideo)
    }

    /**
     * Video files are named after their quality ("720p"); the video's sound kept as M4A after
     * the video alone; other audio keeps its own label ("MP3 192 kbps").
     */
    private fun named(variant: MediaVariant): MediaVariant {
        if (variant.trackType == MediaTrackType.AUDIO) {
            val plainM4a = variant.audioFromVideo && variant.mp3 == null
            return if (plainM4a) variant.copy(label = null) else variant
        }
        val height = variant.height ?: return variant.copy(label = null)
        val rate = variant.framesPerSecond?.takeIf { it > HIGH_FRAME_RATE }
            ?.let { Math.round(it).toString() }.orEmpty()
        return variant.copy(label = "${height}p$rate")
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

    private val QuickDownloadUiState.canChangeSelection: Boolean
        get() = downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered

    private fun MediaGroup.header(): SheetHeader = SheetHeader(
        title = title ?: if (candidates.all { it.isAudio() }) "Audio" else "Video",
        source = QuickDownloadChoices.host(pageUrl),
        durationMillis = durationMillis,
        audioOnly = candidates.all { it.isAudio() },
        thumbnailUrl = ThumbnailUrls.of(candidates),
    )

    /** P29/P37: the attempts of one video, by the name its Details give them. */
    private enum class Attempt(val label: String) {
        FIRST("First video"),
        NEWEST("Page's newest link"),
        PLAYER("Player's link"),
        REREAD("Page read again"),
        NEXT("Next video"),
    }

    private sealed interface Prepared {
        data class Ready(val asset: MediaAsset, val variant: MediaVariant) : Prepared
        data class Failed(
            val message: String,
            val details: List<String> = emptyList(),
            /** P37: the resolver's answer when the file could not be read. */
            val failure: VariantResolutionResult.Failure? = null,
        ) : Prepared
    }

    internal companion object {
        /** A video rarely has more qualities; more would only cost requests. */
        const val MAX_SOURCES = 12

        /** P12: the sheet's title while the page's lookup has no title yet. */
        const val WAITING_TITLE = "Video"
        const val MP3_UNAVAILABLE = "This audio can't be converted to MP3. Try M4A."
        const val AUDIO_UNAVAILABLE = "This video's sound can't be saved on its own."
        const val QUALITY_UNAVAILABLE = "This quality is not available now — choose another"

        /** P29: the first file of a page without an adapter is gone; its next video is shown. */
        private val GONE_STATUSES = setOf(403, 404, 410)

        /** P37: a page's link that answers one of these is dead for this phone. */
        private val LINK_DEAD_STATUSES = setOf(401, 403, 404, 410)
        private const val NO_PLAYER_DATA = "Status: no player data (a notice or a check)"

        /** P17: HTTP 403 and 410 mean the lookup's links no longer work. */
        private val LINK_GONE_STATUSES = setOf(403, 410)
        private const val HIGH_FRAME_RATE = 31.0

        /** P11: show a site's stated whole file even before its size check completes. */
        fun stated(candidate: MediaCandidate, now: Long = System.currentTimeMillis()): MediaAsset? =
            QuickDownloadMetadata.asset(candidate, now)
    }
}
