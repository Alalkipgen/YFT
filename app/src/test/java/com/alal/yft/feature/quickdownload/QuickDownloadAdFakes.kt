package com.alal.yft.feature.quickdownload

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.BrowserPageReader
import com.alal.yft.detection.PageReread
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * P43: the item-7 page of FIX_ADD_PLAN without an adapter: it states a 10:05 video, its player
 * setup names an HLS and an MP4 of it, and its player fetches a 30-second pre-roll first. The
 * sheet under test, with a resolver whose answers the test sets by address.
 */
internal class AdSheetHarness {
    val store = DetectedMediaStore()
    val resolver = AnsweringResolver()
    val starter = QueueingStarter()

    fun sheet(reader: BrowserPageReader = BrowserPageReader.None) = QuickDownloadViewModel(
        store = store,
        selectionStore = PreviewSelectionStore(),
        resolver = resolver,
        downloadStarter = starter,
        downloadPreferences = object : DownloadPreferencesRepository {
            override val preferences: Flow<DownloadPreferences> =
                MutableStateFlow(DownloadPreferences(confirmOnMeteredNetwork = false))

            override suspend fun update(
                transform: (DownloadPreferences) -> DownloadPreferences,
            ) = Unit
        },
        network = object : NetworkStatusSource {
            override val snapshot: StateFlow<NetworkSnapshot> =
                MutableStateFlow(
                    NetworkSnapshot(connected = true, validated = true, unmetered = true),
                )
        },
        playback = VideoPlaybackSupport.ANY,
        pageReader = reader,
    )

    companion object {
        const val PAGE = "https://clips.example.test/watch/43"
        const val TITLE = "Harbour lights at dusk"
        const val PAGE_LENGTH = 605_000L
        const val MAIN_LENGTH = 603_000L
        const val AD_LENGTH = 30_000L

        /** A file the page's player setup names as its video (P28's key and role). */
        fun named(url: String, kind: MediaKind = MediaKind.DIRECT, at: Long = 1) = MediaCandidate(
            pageUrl = PAGE,
            mediaUrl = url,
            sources = setOf(CandidateSource.DOM),
            kind = kind,
            mimeType = if (kind == MediaKind.DIRECT) "video/mp4" else null,
            observedAtEpochMs = at,
            height = if (kind == MediaKind.DIRECT) 720 else null,
            pageRole = PageMediaRole.MAIN,
            pageVideoKey = "player:0",
        )

        /** A file the page's player asked for, of a length nothing stated. */
        fun requested(url: String, at: Long = 5) = MediaCandidate(
            pageUrl = PAGE,
            mediaUrl = url,
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DIRECT,
            mimeType = "video/mp4",
            observedAtEpochMs = at,
        )

        /** The file check read [lengthMillis] from [candidate]'s file. */
        fun read(candidate: MediaCandidate, lengthMillis: Long?) = VariantResolutionResult.Success(
            MediaAsset(
                sourcePageUrl = candidate.pageUrl,
                title = candidate.title,
                thumbnailUrl = null,
                durationMillis = lengthMillis,
                variants = listOf(
                    MediaVariant(
                        id = "direct-0",
                        playbackUrl = candidate.mediaUrl,
                        kind = MediaKind.DIRECT,
                        trackType = MediaTrackType.AUDIO_VIDEO,
                        requestContext = BrowserRequestContext(candidate.pageUrl, null, null),
                        mimeType = "video/mp4",
                        height = 720,
                        sizeBytes = 20L * 1024 * 1024,
                        sizeAccuracy = MediaSizeAccuracy.EXACT,
                    ),
                ),
                resolvedAtEpochMs = 1,
            ),
        )

        /** The file check answered [status]: the link is gone. */
        fun gone(status: Int) = VariantResolutionResult.Failure(
            VariantResolutionFailure.HTTP_STATUS,
            httpStatusCode = status,
            step = ResolutionStep.FILE_CHECK,
            host = "media.example.test",
        )
    }
}

/** Answers each address as the test set it ([answers]); else reads the length it stated. */
internal class AnsweringResolver : VariantResolver {
    val requested = mutableListOf<MediaCandidate>()
    val answers = mutableMapOf<String, VariantResolutionResult>()

    override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
        requested += candidate
        return answers[candidate.mediaUrl]
            ?: AdSheetHarness.read(candidate, candidate.durationMillis)
    }
}

/** Answers the sheet's quiet re-reads in turn; then "not read". */
internal class QueuedPageReader(vararg answers: PageReread) : BrowserPageReader {
    val asked = mutableListOf<String>()
    private val queue = ArrayDeque(answers.toList())

    override suspend fun read(pageUrl: String, userAgent: String?): PageReread {
        asked += pageUrl
        return queue.removeFirstOrNull() ?: PageReread.Failed
    }
}

internal class QueueingStarter : PreviewDownloadStarter {
    val variants = mutableListOf<MediaVariant>()

    override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
        variants += variant
        return EnqueueResult.Started("task-${variants.size}", "Harbour lights at dusk.mp4")
    }
}
