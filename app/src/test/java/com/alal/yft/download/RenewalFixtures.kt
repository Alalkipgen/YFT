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
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.feature.detectedmedia.LookupOwner
import java.io.IOException
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
    var answer: (DirectDownloadPlan) -> DirectProbeResult = { plan ->
        DirectProbeResult.Success(RenewalFixtures.metadata(plan.sourceUrl))
    }
    var thrown: Exception? = null

    override suspend fun probe(plan: DirectDownloadPlan): DirectProbeResult {
        plans += plan
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
