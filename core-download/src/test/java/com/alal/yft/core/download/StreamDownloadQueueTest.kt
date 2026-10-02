package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamDownloadQueueTest {
    @Test
    fun `restored HLS checkpoint survives link refresh and completes`() = runTest {
        val savedCheckpoint = HlsTransferCheckpoint(
            manifestFingerprint = "a".repeat(64),
            chunks = listOf(StreamChunkCheckpoint(0, 4, completed = true)),
        )
        val store = InMemoryStore(
            listOf(
                storedTask(
                    id = "hls-refresh",
                    type = DownloadPlanType.HLS,
                    checkpoint = savedCheckpoint,
                    status = DownloadTaskStatus.RUNNING,
                ),
            ),
        )
        val dispatcher = ControlledDispatcher()
        val queue = DownloadQueue(
            store = store,
            transferDispatcher = dispatcher,
            scope = backgroundScope,
            maxConcurrentDownloads = 1,
        )

        queue.restore()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, queue.tasks.value.single().status)
        assertEquals(savedCheckpoint, queue.tasks.value.single().checkpoint)

        queue.refresh(
            id = "hls-refresh",
            plan = hlsPlan("hls-refresh"),
            destination = RecordingDestination(),
        )
        runCurrent()

        assertEquals(savedCheckpoint, dispatcher.resumeCheckpoints["hls-refresh"])
        dispatcher.complete("hls-refresh")
        runCurrent()

        val completed = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.COMPLETED, completed.status)
        assertEquals(DownloadPlanType.HLS, completed.planType)
        assertEquals(10L, completed.downloadedBytes)
        assertEquals(10L, completed.totalBytes)
    }

    @Test
    fun `mixed stream and mux plans share the queue concurrency bound`() = runTest {
        val dispatcher = ControlledDispatcher()
        val queue = DownloadQueue(
            store = InMemoryStore(),
            transferDispatcher = dispatcher,
            scope = backgroundScope,
            maxConcurrentDownloads = 2,
        )

        queue.enqueue(hlsPlan("hls"), RecordingDestination())
        queue.enqueue(dashPlan("dash", MediaTrackType.VIDEO), RecordingDestination())
        queue.enqueue(muxPlan("mux"), RecordingDestination())
        runCurrent()

        assertEquals(2, dispatcher.active.get())
        assertEquals(2, queue.tasks.value.count { it.status == DownloadTaskStatus.RUNNING })
        assertEquals(1, queue.tasks.value.count { it.status == DownloadTaskStatus.QUEUED })
        assertEquals(
            setOf(
                DownloadPlanType.HLS,
                DownloadPlanType.DASH,
                DownloadPlanType.AUDIO_VIDEO_MUX,
            ),
            queue.tasks.value.map(StoredDownloadTask::planType).toSet(),
        )

        dispatcher.complete("hls")
        runCurrent()
        assertEquals(2, dispatcher.active.get())
        dispatcher.complete("dash")
        dispatcher.complete("mux")
        runCurrent()

        assertTrue(queue.tasks.value.all { it.status == DownloadTaskStatus.COMPLETED })
    }

    @Test
    fun `cancelling pending stream discards engine workspace and destination`() = runTest {
        val dispatcher = ControlledDispatcher()
        val destination = RecordingDestination()
        val queue = DownloadQueue(
            store = InMemoryStore(),
            transferDispatcher = dispatcher,
            scope = backgroundScope,
        )
        queue.setNetworkAvailable(false)
        queue.enqueue(hlsPlan("cancel-hls"), destination)

        queue.cancel("cancel-hls")

        assertEquals(DownloadTaskStatus.CANCELLED, queue.tasks.value.single().status)
        assertEquals(listOf("cancel-hls"), dispatcher.discarded)
        assertTrue(destination.discarded.get())
    }

    private fun hlsPlan(id: String): HlsDownloadPlan = HlsDownloadPlan(
        taskId = id,
        playlistUrl = "https://media.example.test/track.m3u8?token=private",
        suggestedFileName = "$id.mp4",
        requestContext = context(),
        mimeType = "video/mp4",
    )

    private fun dashPlan(id: String, type: MediaTrackType): DashDownloadPlan =
        DashDownloadPlan(
            taskId = id,
            manifestUrl = "https://media.example.test/manifest.mpd?token=private",
            representationId = "$id-private",
            trackType = type,
            suggestedFileName = "$id.mp4",
            requestContext = context(),
            mimeType = if (type == MediaTrackType.AUDIO) "audio/mp4" else "video/mp4",
            codecs = if (type == MediaTrackType.AUDIO) {
                listOf("mp4a.40.2")
            } else {
                listOf("avc1.4d401f")
            },
        )

    private fun muxPlan(id: String): AudioVideoMuxDownloadPlan =
        AudioVideoMuxDownloadPlan(
            taskId = id,
            video = dashPlan("$id-video", MediaTrackType.VIDEO),
            audio = dashPlan("$id-audio", MediaTrackType.AUDIO),
            suggestedFileName = "$id.mp4",
        )

    private fun context(): BrowserRequestContext = BrowserRequestContext(
        pageUrl = "https://page.example.test/watch",
        userAgent = "fixture",
        cookie = "session=private",
    )

    private fun storedTask(
        id: String,
        type: DownloadPlanType,
        checkpoint: TransferCheckpoint,
        status: DownloadTaskStatus,
    ): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = type,
        totalBytes = null,
        downloadedBytes = checkpoint.downloadedBytes,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = true,
        failureReason = null,
        checkpoint = checkpoint,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private class InMemoryStore(
        initial: List<StoredDownloadTask> = emptyList(),
    ) : DownloadTaskStore {
        private val gate = Mutex()
        private val records = LinkedHashMap(initial.associateBy(StoredDownloadTask::id))

        override suspend fun loadAll(): List<StoredDownloadTask> =
            gate.withLock { records.values.toList() }

        override suspend fun save(task: StoredDownloadTask) {
            gate.withLock { records[task.id] = task }
        }

        override suspend fun delete(id: String) {
            gate.withLock { records.remove(id) }
        }
    }

    private class ControlledDispatcher : DownloadTransferDispatcher {
        val active = AtomicInteger()
        val resumeCheckpoints = ConcurrentHashMap<String, TransferCheckpoint>()
        val discarded = CopyOnWriteArrayList<String>()
        private val completions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult {
            active.incrementAndGet()
            if (resumeFrom != null) resumeCheckpoints[plan.taskId] = resumeFrom
            val partial = resumeFrom ?: checkpoint(plan, bytes = 1, complete = false)
            onCheckpoint(partial)
            return try {
                completions.computeIfAbsent(plan.taskId) { CompletableDeferred() }.await()
                val completed = checkpoint(plan, bytes = 10, complete = true)
                onCheckpoint(completed)
                QueueTransferResult.Completed(bytesWritten = 10, checkpoint = completed)
            } finally {
                active.decrementAndGet()
            }
        }

        override suspend fun discard(plan: DownloadPlan) {
            discarded += plan.taskId
        }

        fun complete(id: String) {
            completions.computeIfAbsent(id) { CompletableDeferred() }.complete(Unit)
        }

        private fun checkpoint(
            plan: DownloadPlan,
            bytes: Long,
            complete: Boolean,
        ): TransferCheckpoint = when (plan) {
            is HlsDownloadPlan -> HlsTransferCheckpoint(
                manifestFingerprint = "a".repeat(64),
                chunks = listOf(StreamChunkCheckpoint(0, bytes, complete)),
            )
            is DashDownloadPlan -> DashTransferCheckpoint(
                manifestFingerprint = "b".repeat(64),
                chunks = listOf(StreamChunkCheckpoint(0, bytes, complete)),
            )
            is AudioVideoMuxDownloadPlan -> {
                if (!complete) {
                    AudioVideoMuxCheckpoint()
                } else {
                    val track = DashTransferCheckpoint(
                        manifestFingerprint = "c".repeat(64),
                        chunks = listOf(StreamChunkCheckpoint(0, bytes / 2, true)),
                    )
                    AudioVideoMuxCheckpoint(
                        video = track,
                        audio = track.copy(manifestFingerprint = "d".repeat(64)),
                        videoReady = true,
                        audioReady = true,
                        stage = AudioVideoMuxStage.COMPLETED,
                    )
                }
            }
            else -> error("Unexpected direct plan")
        }
    }

    private class RecordingDestination : DownloadDestination {
        val discarded = AtomicBoolean(false)

        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = null
        override fun open(): SeekableDownloadOutput = error("Dispatcher owns no output")
        override fun commit() = Unit
        override fun discard() {
            discarded.set(true)
        }
    }
}