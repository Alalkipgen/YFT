package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.DownloadLinkRenewal
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.SeekableDownloadOutput
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.SaveFolder
import com.alal.yft.download.RenewalFixtures.MIB
import com.alal.yft.download.RenewalFixtures.NOW
import com.alal.yft.download.RenewalFixtures.TOTAL
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.feature.detectedmedia.LookupOwner
import java.io.IOException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope

/** Track E (E2): a task store in memory; with [failure] every save fails, as a full disk. */
internal class MemoryTaskStore : DownloadTaskStore {
    private val saved = linkedMapOf<String, StoredDownloadTask>()
    var failure: IOException? = null

    override suspend fun loadAll(): List<StoredDownloadTask> = saved.values.toList()

    override suspend fun save(task: StoredDownloadTask) {
        failure?.let { throw it }
        saved[task.id] = task
    }

    override suspend fun delete(id: String) {
        saved.remove(id)
    }
}

/** A file probe that answers each plan with its own link's facts, unless told otherwise. */
internal class RecordingProbe : DirectMetadataProbe {
    val plans = CopyOnWriteArrayList<DirectDownloadPlan>()

    /** Whether each probe asked the HEAD first (v1.0.2: not for a file its adapter checked). */
    val askedHead = CopyOnWriteArrayList<Boolean>()
    var answer: (DirectDownloadPlan) -> DirectProbeResult = { plan ->
        DirectProbeResult.Success(RenewalFixtures.metadata(plan.sourceUrl))
    }
    var thrown: Exception? = null

    override suspend fun probe(plan: DirectDownloadPlan, askHead: Boolean): DirectProbeResult {
        plans += plan
        askedHead += askHead
        thrown?.let { throw it }
        return answer(plan)
    }
}

/** Destinations that write nothing; [rename] stands for a name already taken. */
internal class NamingDestinations : DownloadDestinationProvider {
    var rename: (String) -> String = { it }
    var failure: IOException? = null

    override fun prepare(
        fileName: String,
        mimeType: String?,
        location: DownloadLocation,
        folder: SaveFolder?,
    ): PreparedDestination {
        failure?.let { throw it }
        return PreparedDestination(
            destination = NoFileDestination(),
            spec = DownloadDestinationSpec(DownloadDestinationKind.APP_PRIVATE),
            fileName = rename(fileName),
        )
    }
}

internal class NoFileDestination : DownloadDestination {
    override fun prepare(expectedLength: Long?) = Unit
    override fun temporaryLength(): Long? = null
    override fun open(): SeekableDownloadOutput = throw UnsupportedOperationException()
    override fun commit() = Unit
    override fun discard() = Unit
}

/** An engine whose runs never end, so a queued download stays where it is. */
internal class EndlessTransfers : DownloadTransferDispatcher {
    override suspend fun transfer(
        plan: DownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: TransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult = awaitCancellation()

    override suspend fun discard(plan: DownloadPlan) = Unit
}

/** The app's real enqueue and queue, with [sources] and [renewal], on the test's scope. */
internal class EnqueueHarness(
    scope: TestScope,
    val sources: DownloadSources = DownloadSources(),
    val probe: RecordingProbe = RecordingProbe(),
    transfers: DownloadTransferDispatcher = EndlessTransfers(),
    renewal: DownloadLinkRenewal = DownloadLinkRenewal.None,
) {
    val store = MemoryTaskStore()
    val destinations = NamingDestinations()
    val queue = DownloadQueue(
        store = store,
        transferDispatcher = transfers,
        scope = scope.backgroundScope,
        clock = { RenewalFixtures.NOW },
        linkRenewal = renewal,
    )
    private var next = 0
    val enqueuer = DownloadEnqueuer(
        queue = queue,
        probe = probe,
        destinations = destinations,
        serviceStarter = object : DownloadServiceStarter {
            override fun start() = Unit
        },
        taskIds = { "task-${next++}" },
        clock = { RenewalFixtures.NOW },
        sources = sources,
    )
}

/** The sign-in each owner's lookup would send now; none until a test sets one. */
internal class FakeSessions : LookupSessions {
    val cookies = mutableMapOf<LookupOwner, String>()

    override fun cookieFor(owner: LookupOwner, pageUrl: String): String? = cookies[owner]
}

internal object RenewalFixtures {
    const val NOW = 1_000L
    const val MIB = 1_048_576L
    const val TOTAL = 100 * MIB

    fun metadata(url: String, totalBytes: Long? = TOTAL) = RemoteFileMetadata(
        finalUrl = url,
        totalBytes = totalBytes,
        supportsByteRanges = true,
        entityTag = "\"fixture\"",
        lastModified = null,
        contentType = "video/mp4",
        suggestedFileName = "clip.mp4",
    )

    val DENIED = DownloadFailure(
        reason = DownloadFailureReason.ACCESS_DENIED,
        httpStatusCode = 403,
        stage = DownloadFailureStage.CONNECT,
        detail = "403 text/plain len 0",
    )

    /** A listed task of [status] that holds nothing yet, for [DownloadSources.prune]. */
    fun task(id: String, status: DownloadTaskStatus) = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        totalBytes = null,
        downloadedBytes = 0,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        checkpoint = DirectTransferCheckpoint(null, null, null, emptyList()),
        createdAtEpochMs = NOW,
        updatedAtEpochMs = NOW,
    )
}

/** A site adapter for pages `https://<id>.test/video/<id>`; lookup n answers [answer] (n). */
internal class FakeSite(override val id: String) : SiteExtractor {
    override val displayName: String = id
    val host = "$id.test"
    val requests = CopyOnWriteArrayList<SiteExtractionRequest>()
    var answer: (lookup: Int) -> SiteExtractionResult = {
        SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
    }

    fun page() = "https://$host/video/v1"

    override fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        val path = uri.path.orEmpty()
        if (uri.host != host || !path.startsWith(VIDEO_PATH)) return null
        val video = path.removePrefix(VIDEO_PATH).takeIf(String::isNotBlank) ?: return null
        return SitePageIdentity(id, video, "https://$host$VIDEO_PATH$video")
    }

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        val lookup = requests.size
        requests += request
        return answer(lookup)
    }

    private companion object {
        const val VIDEO_PATH = "/video/"
    }
}

/**
 * Resolves like the real resolver: a whole file is one variant keeping its companion and
 * codecs, with the height its MP4 header gives ([measured], by the file's name) when the
 * site states none; a playlist or manifest lists 1080p, 720p and 360p.
 */
internal class FakeResolver : VariantResolver {
    var measured: Map<String, Int> = emptyMap()
    var failing: (MediaCandidate) -> Boolean = { false }

    override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
        if (failing(candidate)) {
            return VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK)
        }
        val variants = when (candidate.kind) {
            MediaKind.DIRECT -> listOf(whole(candidate))
            else -> listOf(1080, 720, 360).map { rendition(candidate, it) }
        }
        return VariantResolutionResult.Success(
            MediaAsset(
                sourcePageUrl = candidate.pageUrl,
                title = MediaGroups.baseTitle(candidate.title),
                thumbnailUrl = null,
                durationMillis = candidate.durationMillis,
                variants = variants,
                resolvedAtEpochMs = NOW,
            ),
        )
    }

    private fun whole(candidate: MediaCandidate): MediaVariant {
        val audio = candidate.mimeType?.startsWith("audio/") == true
        val name = candidate.mediaUrl.substringBefore('?').substringAfterLast('/')
        return MediaVariant(
            id = "direct-0",
            playbackUrl = candidate.mediaUrl,
            kind = MediaKind.DIRECT,
            trackType = if (audio) MediaTrackType.AUDIO else MediaTrackType.AUDIO_VIDEO,
            requestContext = candidate.requestContext,
            mimeType = candidate.mimeType,
            container = if (audio) "m4a" else "mp4",
            codecs = candidate.codecs,
            height = candidate.height ?: measured[name],
            durationMillis = candidate.durationMillis,
            audioCompanion = candidate.audioCompanion,
        )
    }

    private fun rendition(candidate: MediaCandidate, height: Int): MediaVariant {
        val playlist = candidate.kind == MediaKind.HLS
        return MediaVariant(
            id = "${candidate.kind.name.lowercase()}-$height",
            playbackUrl = if (playlist) {
                candidate.mediaUrl.replace("?", "/$height.m3u8?")
            } else {
                candidate.mediaUrl
            },
            kind = candidate.kind,
            trackType = MediaTrackType.AUDIO_VIDEO,
            requestContext = candidate.requestContext,
            manifestVariantId = "video-$height",
            mimeType = "video/mp4",
            codecs = listOf("avc1.64001F", "mp4a.40.2"),
            width = height * 16 / 9,
            height = height,
            framesPerSecond = 30.0,
            bitrateBitsPerSecond = height * 4_000L,
            durationMillis = candidate.durationMillis,
        )
    }
}

/**
 * A direct file's engine: run n fails with [failure] (n), a 403 unless told otherwise, after
 * [failAt] (n) bytes, else completes.
 */
internal class ScriptedTransfers(
    private val failure: (run: Int) -> DownloadFailure = { RenewalFixtures.DENIED },
    private val failAt: (run: Int) -> Long?,
) : DownloadTransferDispatcher {
    val plans = CopyOnWriteArrayList<DownloadPlan>()
    val resumedFrom = CopyOnWriteArrayList<Long>()

    override suspend fun transfer(
        plan: DownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: TransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult {
        val run = plans.size
        plans += plan
        val start = resumeFrom?.downloadedBytes ?: 0L
        resumedFrom += start
        failAt(run)?.let { bytes ->
            val written = checkpoint(bytes)
            onCheckpoint(written)
            return QueueTransferResult.Failure(failure(run), written)
        }
        onCheckpoint(checkpoint(start + MIB))
        val done = checkpoint(TOTAL)
        onCheckpoint(done)
        return QueueTransferResult.Completed(TOTAL, done)
    }

    override suspend fun discard(plan: DownloadPlan) = Unit

    private fun checkpoint(bytes: Long) = DirectTransferCheckpoint(
        totalBytes = TOTAL,
        entityTag = "\"fixture\"",
        lastModified = null,
        segments = listOf(DownloadSegment(0, 0, TOTAL - 1, bytes)),
    )
}
