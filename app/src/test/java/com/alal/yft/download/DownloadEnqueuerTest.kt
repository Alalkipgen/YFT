package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.SeekableDownloadOutput
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantSupport
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadEnqueuerTest {
    @Test
    fun `direct selection is probed before it reaches the queue`() = runTest {
        val harness = Harness(this)
        harness.probe.result = DirectProbeResult.Success(
            metadata(totalBytes = 4_096, contentType = "video/mp4"),
        )

        val result = harness.enqueuer.enqueue(
            asset(),
            variant(kind = MediaKind.DIRECT, label = "1080p"),
        ) as EnqueueResult.Started

        assertEquals("Fixture 1080p.mp4", result.fileName)
        assertEquals(1, harness.probe.calls)
        assertEquals(listOf("Fixture 1080p.mp4" to "video/mp4"), harness.destinations.prepared)
        val task = harness.queue.tasks.value.single()
        assertEquals(result.taskId, task.id)
        assertEquals(DownloadPlanType.DIRECT, task.planType)
        assertEquals(4_096L, task.totalBytes)
        assertEquals(1, harness.serviceStarter.starts)
    }

    @Test
    fun `probe failure keeps the queue and storage untouched`() = runTest {
        val harness = Harness(this)
        harness.probe.result = DirectProbeResult.Failure(
            DownloadFailure(DownloadFailureReason.EXPIRED_URL),
        )

        val result = harness.enqueuer.enqueue(
            asset(),
            variant(kind = MediaKind.DIRECT),
        ) as EnqueueResult.Rejected

        assertEquals(DownloadFailureReason.EXPIRED_URL, result.reason)
        assertTrue(result.message.contains("expired"))
        assertTrue(harness.destinations.prepared.isEmpty())
        assertTrue(harness.queue.tasks.value.isEmpty())
        assertEquals(0, harness.serviceStarter.starts)
    }

    @Test
    fun `stream selections skip the probe because their size is unknown`() = runTest {
        val harness = Harness(this)

        val hls = harness.enqueuer.enqueue(
            asset(),
            variant(id = "hls", kind = MediaKind.HLS, container = null),
        ) as EnqueueResult.Started
        val dash = harness.enqueuer.enqueue(
            asset(),
            variant(id = "dash", kind = MediaKind.DASH, container = null)
                .copy(manifestVariantId = "video-720"),
        ) as EnqueueResult.Started

        assertEquals(0, harness.probe.calls)
        assertEquals(2, harness.serviceStarter.starts)
        val byId = harness.queue.tasks.value.associateBy(StoredDownloadTask::id)
        assertEquals(DownloadPlanType.HLS, byId.getValue(hls.taskId).planType)
        assertEquals(DownloadPlanType.DASH, byId.getValue(dash.taskId).planType)
        assertNull(byId.getValue(hls.taskId).totalBytes)
        assertTrue(byId.values.none { it.status == DownloadTaskStatus.FAILED })
    }

    @Test
    fun `rejected plans never create a destination`() = runTest {
        val harness = Harness(this)

        val result = harness.enqueuer.enqueue(
            asset(),
            variant(support = VariantSupport.UNSUPPORTED_CODEC),
        ) as EnqueueResult.Rejected

        assertEquals(DownloadFailureReason.UNSUPPORTED_SOURCE, result.reason)
        assertTrue(harness.destinations.prepared.isEmpty())
        assertTrue(harness.queue.tasks.value.isEmpty())
        assertEquals(0, harness.serviceStarter.starts)
    }

    @Test
    fun `storage failures are reported instead of crashing the caller`() = runTest {
        val harness = Harness(this)
        harness.destinations.failure = IOException("no space")
        harness.probe.result = DirectProbeResult.Success(metadata(totalBytes = 1))

        val direct = harness.enqueuer.enqueue(
            asset(),
            variant(kind = MediaKind.DIRECT),
        ) as EnqueueResult.Rejected
        val stream = harness.enqueuer.enqueue(
            asset(),
            variant(id = "hls", kind = MediaKind.HLS),
        ) as EnqueueResult.Rejected

        assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, direct.reason)
        assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, stream.reason)
        assertTrue(harness.queue.tasks.value.isEmpty())
        assertEquals(0, harness.serviceStarter.starts)
        assertFalse(direct.message.contains("no space"))
    }

    private class Harness(scope: kotlinx.coroutines.test.TestScope) {
        val probe = FakeProbe()
        val destinations = FakeDestinations()
        val serviceStarter = CountingServiceStarter()
        val queue = DownloadQueue(
            store = InMemoryStore(),
            transferDispatcher = NeverFinishingDispatcher(),
            scope = scope.backgroundScope,
            clock = { 1_000 },
        )
        val enqueuer: DownloadEnqueuer

        init {
            var next = 0
            enqueuer = DownloadEnqueuer(
                queue = queue,
                probe = probe,
                destinations = destinations,
                serviceStarter = serviceStarter,
                taskIds = { "task-${next++}" },
                clock = { 1_000 },
            )
        }
    }

    private class FakeProbe(
        var result: DirectProbeResult = DirectProbeResult.Success(
            RemoteFileMetadata(
                finalUrl = "https://media.example.test/clip",
                totalBytes = 10,
                supportsByteRanges = true,
                entityTag = null,
                lastModified = null,
                contentType = "video/mp4",
                suggestedFileName = "clip.mp4",
            ),
        ),
    ) : DirectMetadataProbe {
        var calls = 0

        override suspend fun probe(plan: DirectDownloadPlan): DirectProbeResult {
            calls += 1
            return result
        }
    }

    private class FakeDestinations : DownloadDestinationProvider {
        val prepared = mutableListOf<Pair<String, String?>>()
        var failure: IOException? = null

        override fun prepare(fileName: String, mimeType: String?): PreparedDestination {
            failure?.let { throw it }
            prepared += fileName to mimeType
            return PreparedDestination(
                destination = NoOpDestination(),
                spec = DownloadDestinationSpec(DownloadDestinationKind.APP_PRIVATE),
            )
        }
    }

    private class CountingServiceStarter : DownloadServiceStarter {
        var starts = 0

        override fun start() {
            starts += 1
        }
    }

    private class NoOpDestination : DownloadDestination {
        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = null
        override fun open(): SeekableDownloadOutput = throw UnsupportedOperationException()
        override fun commit() = Unit
        override fun discard() = Unit
    }

    private class InMemoryStore : DownloadTaskStore {
        private val saved = linkedMapOf<String, StoredDownloadTask>()

        override suspend fun loadAll(): List<StoredDownloadTask> = saved.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            saved[task.id] = task
        }

        override suspend fun delete(id: String) {
            saved.remove(id)
        }
    }

    private class NeverFinishingDispatcher : DownloadTransferDispatcher {
        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult {
            CompletableDeferred<Unit>().await()
            return QueueTransferResult.Completed(
                0,
                DirectTransferCheckpoint(null, null, null, emptyList()),
            )
        }

        override suspend fun discard(plan: DownloadPlan) = Unit
    }

    private fun asset() = MediaAsset(
        sourcePageUrl = "https://page.example.test/watch",
        title = "Fixture",
        thumbnailUrl = null,
        durationMillis = null,
        variants = listOf(variant()),
        resolvedAtEpochMs = 1,
    )

    private fun metadata(
        totalBytes: Long?,
        contentType: String? = "video/mp4",
    ) = RemoteFileMetadata(
        finalUrl = "https://media.example.test/clip",
        totalBytes = totalBytes,
        supportsByteRanges = true,
        entityTag = null,
        lastModified = null,
        contentType = contentType,
        suggestedFileName = "clip.mp4",
    )

    private fun variant(
        id: String = "variant",
        kind: MediaKind = MediaKind.DIRECT,
        label: String? = null,
        container: String? = "mp4",
        support: VariantSupport = VariantSupport.SUPPORTED,
    ) = MediaVariant(
        id = id,
        playbackUrl = "https://media.example.test/clip",
        kind = kind,
        trackType = MediaTrackType.AUDIO_VIDEO,
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = null,
        ),
        label = label,
        mimeType = "video/mp4",
        container = container,
        support = support,
    )
}
