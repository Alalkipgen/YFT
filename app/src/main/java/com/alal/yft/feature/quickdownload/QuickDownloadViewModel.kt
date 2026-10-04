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
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.DownloadNetworkPolicy
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.ui.components.isAudio
import com.alal.yft.ui.components.isSavable
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sheet knows before the qualities are read: the video's title, site and length. */
data class SheetHeader(
    val title: String,
    val source: String?,
    val durationMillis: Long?,
    val audioOnly: Boolean,
)

data class QuickDownloadUiState(
    /** Null when there is no video to show (the sheet then says so). */
    val header: SheetHeader? = null,
    /** True while the qualities and sizes are being read. */
    val loading: Boolean = false,
    val choices: QuickChoices? = null,
    /** Why no format could be read; Try again reads them once more. */
    val failure: String? = null,
    val selectedId: String? = null,
    val moreFormatsExpanded: Boolean = false,
    val downloadStatus: PreviewDownloadStatus = PreviewDownloadStatus.Idle,
) {
    val selectedOption: SheetOption? get() = choices?.option(selectedId)

    val canDownload: Boolean
        get() = selectedOption != null &&
            downloadStatus != PreviewDownloadStatus.Enqueuing &&
            downloadStatus != PreviewDownloadStatus.ConfirmMetered
}

/**
 * The download sheet (P3): one video, its Music and Video rows and every other format inside the
 * same sheet.
 *
 * The video is the group [DetectedMediaStore] selected (Home's lookup, the found list or the
 * browser's Download button), else the page's only video. Each of its candidates is resolved
 * like Download as does, so heights, sizes and companion audio are real; a whole file whose site
 * already stated its picture and size is shown without a request and resolved on Download.
 * Download queues the option through [PreviewDownloadStarter], which applies Wi-Fi only; mobile
 * data asks first when the user chose to be asked.
 */
@HiltViewModel
class QuickDownloadViewModel @Inject constructor(
    private val store: DetectedMediaStore,
    private val selectionStore: PreviewSelectionStore,
    private val resolver: VariantResolver,
    private val downloadStarter: PreviewDownloadStarter,
    private val downloadPreferences: DownloadPreferencesRepository,
    private val network: NetworkStatusSource,
) : ViewModel() {
    private val group: MediaGroup? = store.selection.value ?: store.page.value?.candidates
        ?.take(DetectedMediaStore.MAX_CANDIDATES)
        ?.filter { it.isSavable }
        ?.let(MediaGroups::of)
        ?.singleOrNull()
    private val mutableUiState = MutableStateFlow(QuickDownloadUiState(header = group?.header()))
    val uiState: StateFlow<QuickDownloadUiState> = mutableUiState.asStateFlow()
    private var loading: Job? = null

    init {
        load()
    }

    /** Reads the formats again after a failure. */
    fun retry() {
        if (mutableUiState.value.loading) return
        load()
    }

    fun select(selectionId: String) {
        mutableUiState.update { state ->
            if (!state.canChangeSelection) return@update state
            if (state.choices?.option(selectionId) == null) return@update state
            state.copy(selectedId = selectionId, downloadStatus = PreviewDownloadStatus.Idle)
        }
    }

    /** More formats opens and closes inside the sheet. */
    fun toggleMoreFormats() {
        mutableUiState.update { it.copy(moreFormatsExpanded = !it.moreFormatsExpanded) }
    }

    /**
     * More formats › Details: hands the selected format's candidate to Download as. Returns false
     * when there is nothing to show, so nothing navigates.
     */
    fun openDetails(): Boolean {
        val state = mutableUiState.value
        val option = state.selectedOption ?: state.choices?.more?.firstOrNull() ?: return false
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

    private fun load() {
        val group = group ?: return
        loading?.cancel()
        mutableUiState.update { it.copy(loading = true, failure = null) }
        loading = viewModelScope.launch {
            val quality = currentPreferences().defaultQuality
            val sources = coroutineScope {
                group.candidates.take(MAX_SOURCES).map { candidate ->
                    async { inspect(candidate) }
                }.awaitAll()
            }
            val choices = QuickDownloadChoices.of(group, sources)
            mutableUiState.update { state ->
                if (choices == null) {
                    state.copy(
                        loading = false,
                        choices = null,
                        failure = messageFor(sources.firstNotNullOfOrNull { it.failure }),
                    )
                } else {
                    state.copy(
                        header = SheetHeader(
                            title = choices.title,
                            source = choices.source,
                            durationMillis = choices.durationMillis,
                            audioOnly = choices.isAudioOnly,
                        ),
                        loading = false,
                        choices = choices,
                        failure = null,
                        selectedId = state.selectedId?.takeIf { choices.option(it) != null }
                            ?: QuickDownloadChoices.preselect(choices, quality)?.id,
                    )
                }
            }
        }
    }

    /**
     * A whole file whose site stated its type, size and picture needs no request to be shown;
     * anything else is resolved now, so the sheet only shows what the server confirmed.
     */
    private suspend fun inspect(candidate: MediaCandidate): SheetSource {
        stated(candidate)?.let { return SheetSource(candidate, it, resolved = false) }
        return when (val result = resolveSafely(candidate)) {
            is VariantResolutionResult.Success -> SheetSource(candidate, result.asset, true)
            is VariantResolutionResult.Failure ->
                SheetSource(candidate, null, resolved = false, failure = result.reason)
        }
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
                    is EnqueueResult.Started -> PreviewDownloadStatus.Queued(
                        fileName = result.fileName,
                        waitingForUnmetered = DownloadNetworkPolicy.stateFor(
                            network.snapshot.value,
                            preferences,
                        ) == TransferNetworkState.WAITING_FOR_UNMETERED,
                    )

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
        if (option.source.resolved) {
            val asset = option.source.asset ?: return Prepared.Failed(messageFor(null))
            return Prepared.Ready(asset.copy(title = title ?: asset.title), named(option.variant))
        }
        val resolved = when (val result = resolveSafely(option.source.candidate)) {
            is VariantResolutionResult.Failure -> return Prepared.Failed(messageFor(result.reason))
            is VariantResolutionResult.Success -> result.asset
        }
        val base = resolved.variants.firstOrNull { it.isPreviewable }
            ?: return Prepared.Failed(messageFor(null))
        val stated = option.source.asset?.variants?.firstOrNull()
        val file = base.copy(
            width = base.width ?: stated?.width,
            height = base.height ?: stated?.height,
            framesPerSecond = base.framesPerSecond ?: stated?.framesPerSecond,
            bitrateBitsPerSecond = base.bitrateBitsPerSecond ?: stated?.bitrateBitsPerSecond,
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
        const val MP3_UNAVAILABLE = "This audio can't be converted to MP3. Try M4A."
        const val AUDIO_UNAVAILABLE = "This video's sound can't be saved on its own."
        private const val HIGH_FRAME_RATE = 31.0

        /**
         * The variant a whole file's own statements describe, when they are enough to show it:
         * HTTPS, not expired, its type and size known, and a video's height known.
         */
        fun stated(candidate: MediaCandidate, now: Long = System.currentTimeMillis()): MediaAsset? {
            if (candidate.kind != MediaKind.DIRECT || candidate.drmHint == true) return null
            if (!candidate.mediaUrl.startsWith("https://", ignoreCase = true)) return null
            if (candidate.expiresAtEpochMs?.let { it <= now } == true) return null
            val mime = candidate.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
                ?: return null
            val size = candidate.contentLengthBytes?.takeIf { it > 0 } ?: return null
            val audio = candidate.audioCompanion == null && mime.startsWith("audio/")
            if (!audio && candidate.height == null) return null
            val variant = MediaVariant(
                id = "direct-0",
                playbackUrl = candidate.mediaUrl,
                kind = MediaKind.DIRECT,
                trackType = if (audio) MediaTrackType.AUDIO else MediaTrackType.AUDIO_VIDEO,
                requestContext = candidate.requestContext,
                mimeType = mime,
                container = containerOf(mime),
                codecs = candidate.codecs,
                width = candidate.width,
                height = candidate.height,
                framesPerSecond = candidate.framesPerSecond,
                bitrateBitsPerSecond = candidate.bitrateBitsPerSecond,
                durationMillis = candidate.durationMillis,
                sizeBytes = size,
                sizeAccuracy = if (candidate.audioCompanion != null) {
                    MediaSizeAccuracy.ESTIMATED
                } else {
                    MediaSizeAccuracy.EXACT
                },
                expiresAtEpochMs = candidate.expiresAtEpochMs,
                audioCompanion = candidate.audioCompanion,
            )
            return MediaAsset(
                sourcePageUrl = candidate.pageUrl,
                title = candidate.title,
                thumbnailUrl = candidate.thumbnailUrl,
                durationMillis = candidate.durationMillis,
                variants = listOf(variant),
                resolvedAtEpochMs = now,
            )
        }

        private fun containerOf(mime: String): String? = when (mime) {
            "video/mp4", "audio/mp4" -> "MP4"
            "video/webm", "audio/webm" -> "WebM"
            "audio/mpeg" -> "MP3"
            else -> null
        }
    }
}
