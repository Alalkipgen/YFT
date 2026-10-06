package com.alal.yft.feature.quickdownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.DownloadNetworkPolicy
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
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
    /** P12: false when Try again cannot help (a protected video); the sheet hides it. */
    val canRetry: Boolean = true,
    /** P12: the sheet waits for the browser's lookup of the page's video. */
    val findingVideo: Boolean = false,
    /** P12: how many more videos the page has; a row opens their list. */
    val otherVideos: Int = 0,
    val defaultQuality: QualityPreference = DownloadPreferences().defaultQuality,
    val selectedId: String? = null,
    val downloadStatus: PreviewDownloadStatus = PreviewDownloadStatus.Idle,
) {
    val selectedOption: SheetOption? get() = choices?.option(selectedId)

    val canDownload: Boolean
        get() = selectedOption != null &&
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
) : ViewModel() {
    /**
     * P16: a sheet opened for a lookup (Home's link, a feed's video on screen) waits on it even
     * when the page under it has a video of its own.
     */
    private val opensOnLookup = store.awaitsLookup && store.lookup.value != null
    private var group: MediaGroup? = store.selection.value ?: store.page.value
        ?.takeUnless { opensOnLookup }
        ?.let { page ->
            val savable = page.candidates.take(DetectedMediaStore.MAX_CANDIDATES)
                .filter { it.isSavable }
            MediaGroups.pageVideos(savable, adapterSite = page.adapterSite).singleOrNull()
        }

    /** P12: the lookup this sheet waits on, while no video is chosen yet. */
    private var pageLookup: PageVideoLookup? = store.lookup.value.takeIf { group == null }
    private val mutableUiState = MutableStateFlow(
        pageLookup?.let(::lookupState) ?: QuickDownloadUiState(
            header = group?.header(),
            otherVideos = store.otherVideos.value.takeIf { store.selection.value != null } ?: 0,
        ),
    )
    val uiState: StateFlow<QuickDownloadUiState> = mutableUiState.asStateFlow()
    private var loading: Job? = null
    private val sizeProbes = Semaphore(2)

    init {
        if (pageLookup != null) awaitPageVideo() else load()
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
        load()
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
            state.copy(selectedId = selectionId, downloadStatus = PreviewDownloadStatus.Idle)
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
        val option = state.selectedOption ?: return
        if (!state.canDownload) return
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch {
            val preferences = currentPreferences()
            val metered = DownloadNetworkPolicy.needsMeteredConfirmation(
                network.snapshot.value,
                preferences,
            )
            if (metered) {
                setStatus(PreviewDownloadStatus.ConfirmMetered)
            } else {
                enqueue(option, preferences)
            }
        }
    }

    fun confirmMeteredDownload() {
        val state = mutableUiState.value
        val option = state.selectedOption ?: return
        if (state.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        setStatus(PreviewDownloadStatus.Enqueuing)
        viewModelScope.launch { enqueue(option, currentPreferences()) }
    }

    fun dismissMeteredDownload() {
        if (mutableUiState.value.downloadStatus != PreviewDownloadStatus.ConfirmMetered) return
        setStatus(PreviewDownloadStatus.Idle)
    }

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
                        mutableUiState.update { lookupState(lookup) }
                    }
                }
                .first { (selection, lookup) -> selection != null || lookup == null }
            pageLookup = null
            group = selected
            mutableUiState.update {
                QuickDownloadUiState(
                    header = selected?.header(),
                    otherVideos = if (selected != null) store.otherVideos.value else 0,
                )
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
            thumbnailUrl = ThumbnailUrls.youTube(lookup.key),
        ),
        loading = lookup.running,
        failure = lookup.failure,
        canRetry = lookup.canRetry,
        findingVideo = lookup.running,
    )

    private fun load() {
        val group = group ?: return
        loading?.cancel()
        mutableUiState.update { it.copy(loading = true, failure = null) }
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
            showChoices(mutableUiState.value.choices, sources, waiting = false)
        }
    }

    private fun needsSizeProbe(source: SheetSource): Boolean =
        source.asset == null || source.candidate.contentLengthBytes?.takeIf { it > 0 } == null ||
            source.candidate.videoId?.startsWith("facebook:") == true &&
            source.candidate.bitrateBitsPerSecond != null

    private fun showChoices(choices: QuickChoices?, sources: List<SheetSource>, waiting: Boolean) {
        mutableUiState.update { state ->
            val picture = state.header?.thumbnailUrl
                ?: sources.firstNotNullOfOrNull { ThumbnailUrls.https(it.asset?.thumbnailUrl) }
            state.copy(
                header = choices?.let {
                    SheetHeader(it.title, it.source, it.durationMillis, it.isAudioOnly, picture)
                } ?: state.header,
                choices = choices,
                loading = waiting && choices == null,
                failure = if (choices == null && !waiting) {
                    messageFor(sources.firstNotNullOfOrNull { it.failure })
                } else {
                    null
                },
                selectedId = state.selectedId?.takeIf { choices?.option(it) != null }
                    ?: choices?.let {
                        QuickDownloadChoices.preselect(it, state.defaultQuality)?.id
                    },
            )
        }
    }

    /** A failed background size check keeps the stated row, including its honest estimate. */
    private suspend fun inspectSize(initial: SheetSource): SheetSource {
        return when (val result = resolveSafely(initial.candidate)) {
            is VariantResolutionResult.Failure -> initial.copy(failure = result.reason)
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
                        )
                    }), failure = null)
                }
            }
        }
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

    private suspend fun resolveSafely(candidate: MediaCandidate): VariantResolutionResult = try {
        resolver.resolve(candidate)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK)
    }

    private suspend fun enqueue(option: SheetOption, preferences: DownloadPreferences) {
        val status = try {
            when (val prepared = prepare(option)) {
                is Prepared.Failed -> PreviewDownloadStatus.Rejected(prepared.message)
                is Prepared.Ready -> when (
                    val result = downloadStarter.enqueue(prepared.asset, prepared.variant)
                ) {
                    is EnqueueResult.Started -> {
                        // P19: Downloads shows the video's picture until the file has a frame.
                        mutableUiState.value.header?.thumbnailUrl?.let { url ->
                            downloadThumbnails.saveFrom(result.taskId, url)
                        }
                        PreviewDownloadStatus.Queued(
                            fileName = result.fileName,
                            waitingForUnmetered = DownloadNetworkPolicy.stateFor(
                                network.snapshot.value,
                                preferences,
                            ) == TransferNetworkState.WAITING_FOR_UNMETERED,
                        )
                    }

                    is EnqueueResult.Rejected -> PreviewDownloadStatus.Rejected(result.message)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PreviewDownloadStatus.Rejected("The download could not be queued. Try again.")
        }
        // The selection is locked while queueing, so the status belongs to [option].
        setStatus(status)
    }

    /**
     * The asset and variant to queue. A source shown from what the site stated is resolved now,
     * and the option's conversion (MP3, or the video's sound kept as M4A) is applied to the
     * resolved file. The asset is named after the video and the variant after its quality.
     */
    private suspend fun prepare(option: SheetOption): Prepared {
        val title = mutableUiState.value.choices?.title
        val resolved = when (val result = resolveForDownload(option.source.candidate)) {
            is VariantResolutionResult.Failure -> return Prepared.Failed(
                if (result.reason == VariantResolutionFailure.DRM_PROTECTED ||
                    result.reason == VariantResolutionFailure.UNSUPPORTED_CODEC
                ) messageFor(result.reason) else QUALITY_UNAVAILABLE,
            )
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

    private fun setStatus(status: PreviewDownloadStatus) {
        mutableUiState.update { it.copy(downloadStatus = status) }
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

    private fun messageFor(reason: VariantResolutionFailure?): String = when (reason) {
        VariantResolutionFailure.EXPIRED_URL ->
            "This link has expired. Open the page again."
        VariantResolutionFailure.DRM_PROTECTED -> "Protected media (DRM) can't be saved."
        VariantResolutionFailure.NETWORK ->
            "The media could not be reached. Check the connection and try again."
        VariantResolutionFailure.UNSUPPORTED_CODEC, null ->
            "This version can't be saved. Try another format."
        else -> "This version could not be prepared. Try again or pick another format."
    }

    private sealed interface Prepared {
        data class Ready(val asset: MediaAsset, val variant: MediaVariant) : Prepared
        data class Failed(val message: String) : Prepared
    }

    internal companion object {
        /** A video rarely has more qualities; more would only cost requests. */
        const val MAX_SOURCES = 12

        /** P12: the sheet's title while the page's lookup has no title yet. */
        const val WAITING_TITLE = "Video"
        const val MP3_UNAVAILABLE = "This audio can't be converted to MP3. Try M4A."
        const val AUDIO_UNAVAILABLE = "This video's sound can't be saved on its own."
        const val QUALITY_UNAVAILABLE = "This quality is not available now — choose another"
        private const val HIGH_FRAME_RATE = 31.0

        /** P11: show a site's stated whole file even before its size check completes. */
        fun stated(candidate: MediaCandidate, now: Long = System.currentTimeMillis()): MediaAsset? =
            QuickDownloadMetadata.asset(candidate, now)
    }
}
