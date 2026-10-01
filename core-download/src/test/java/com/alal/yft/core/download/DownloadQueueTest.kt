package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {
    @Test
    fun `restore keeps checkpoint but requires fresh in-memory link context`() = runTest {
        val stored = storedTask("restored", DownloadTaskStatus.RUNNING, downloaded = 4)
        val store = InMemoryStore(listOf(stored))
        val queue = DownloadQueue(store, ControlledRunner(), backgroundScope)

        queue.restore()

        val restored = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, restored.status)
        assertTrue(restored.requiresLinkRefresh)
        assertEquals(4L, restored.checkpoint.downloadedBytes)
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, store.snapshot().single().status)
    }

    @Test
    fun `duplicate URLs remain independent queue records`() = runTest {
        val store = InMemoryStore()
        val queue = DownloadQueue(store, ControlledRunner(), backgroundScope)
        queue.setNetworkAvailable(false)

        queue.enqueue(plan("one"), metadata(), RecordingDestination())
        queue.enqueue(plan("two"), metadata(), RecordingDestination())

        assertEquals(setOf("one", "two"), queue.tasks.value.map { it.id }.toSet())
        assertTrue(queue.tasks.value.all { it.status == DownloadTaskStatus.WAITING_FOR_NETWORK })
    }

    @Test
    fun `bounded concurrency drains queue when capacity becomes available`() = runTest {
        val runner = ControlledRunner()
        val queue = DownloadQueue(
            store = InMemoryStore(),
            transferRunner = runner,
            scope = backgroundScope,
            maxConcurrentDownloads = 2,
        )

        repeat(3) { index ->
            queue.enqueue(
                plan("task-$index"),
                metadata(),
                RecordingDestination(),
            )
        }
        runCurrent()

        assertEquals(2, runner.active.get())
        assertEquals(2, queue.tasks.value.count { it.status == DownloadTaskStatus.RUNNING })
        assertEquals(1, queue.tasks.value.count { it.status == DownloadTaskStatus.QUEUED })

        runner.complete("task-0")
        runCurrent()

        assertEquals(2, runner.active.get())
        assertEquals(1, queue.tasks.value.count { it.status == DownloadTaskStatus.COMPLETED })
        assertEquals(2, queue.tasks.value.count { it.status == DownloadTaskStatus.RUNNING })
        runner.complete("task-1")
        runner.complete("task-2")
        runCurrent()
        assertTrue(queue.tasks.value.all { it.status == DownloadTaskStatus.COMPLETED })
    }

    @Test
    fun `network loss checkpoints and resumes runtime task`() = runTest {
        val runner = ControlledRunner()
        val store = InMemoryStore()
        val queue = DownloadQueue(store, runner, backgroundScope, maxConcurrentDownloads = 1)
        queue.enqueue(plan("network"), metadata(), RecordingDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.RUNNING, queue.tasks.value.single().status)

        queue.setNetworkAvailable(false)

        val waiting = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.WAITING_FOR_NETWORK, waiting.status)
        assertTrue(waiting.downloadedBytes > 0)

        queue.setNetworkAvailable(true)
        runCurrent()
        assertEquals(DownloadTaskStatus.RUNNING, queue.tasks.value.single().status)
        runner.complete("network")
        runCurrent()
        assertEquals(DownloadTaskStatus.COMPLETED, queue.tasks.value.single().status)
    }

    @Test
    fun `cancelled pending task discards partial destination`() = runTest {
        val destination = RecordingDestination()
        val queue = DownloadQueue(InMemoryStore(), ControlledRunner(), backgroundScope)
        queue.setNetworkAvailable(false)
        queue.enqueue(plan("cancel"), metadata(), destination)

        queue.cancel("cancel")

        assertEquals(DownloadTaskStatus.CANCELLED, queue.tasks.value.single().status)
        assertTrue(destination.discarded.get())
    }

    @Test
    fun `refresh after restore reuses checkpoint and can complete`() = runTest {
        val saved = storedTask(
            id = "refresh",
            status = DownloadTaskStatus.NEEDS_REFRESH,
            downloaded = 4,
        )
        val runner = ControlledRunner()
        val queue = DownloadQueue(
            InMemoryStore(listOf(saved)),
            runner,
            backgroundScope,
            maxConcurrentDownloads = 1,
        )
        queue.restore()

        queue.refresh(
            id = "refresh",
            plan = plan("refresh"),
            metadata = metadata(),
            destination = RecordingDestination(),
        )
        runCurrent()
        assertEquals(4L, runner.resumeCheckpoints["refresh"]?.downloadedBytes)
        runner.complete("refresh")
        runCurrent()

        assertEquals(DownloadTaskStatus.COMPLETED, queue.tasks.value.single().status)
    }

    private fun plan(id: String): DirectDownloadPlan = DirectDownloadPlan(
        taskId = id,
        sourceUrl = "http://localhost/file.bin",
        suggestedFileName = "$id.bin",
        requestContext = BrowserRequestContext(
            pageUrl = "https://example.test/watch",
            userAgent = "fixture",
            cookie = "session=fixture",
        ),
        expectedBytes = TOTAL_BYTES,
        preferredSegmentCount = 1,
    )

    private fun metadata(): RemoteFileMetadata = RemoteFileMetadata(
        finalUrl = "http://localhost/file.bin",
        totalBytes = TOTAL_BYTES,
        supportsByteRanges = true,
        entityTag = ETAG,
        lastModified = null,
        contentType = "application/octet-stream",
        suggestedFileName = "file.bin",
    )

    private fun storedTask(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
    ): StoredDownloadTask {
        val segment = DownloadSegment(0, 0, TOTAL_BYTES - 1, downloaded)
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = TOTAL_BYTES,
            entityTag = ETAG,
            lastModified = null,
            segments = listOf(segment),
        )
        return StoredDownloadTask(
            id = id,
            displayName = "$id.bin",
            status = status,
            totalBytes = TOTAL_BYTES,
            downloadedBytes = downloaded,
            mimeType = "application/octet-stream",
            destinationKind = DownloadDestinationKind.APP_PRIVATE,
            destinationUri = null,
            preferredSegmentCount = 1,
            requiresLinkRefresh = true,
            failureReason = null,
            checkpoint = checkpoint,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }

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

        suspend fun snapshot(): List<StoredDownloadTask> = loadAll()
    }

    private class ControlledRunner : DirectTransferRunner {
        val active = AtomicInteger()
        val resumeCheckpoints = ConcurrentHashMap<String, DirectTransferCheckpoint>()
        private val completions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

        override suspend fun transfer(
            plan: DirectDownloadPlan,
            metadata: RemoteFileMetadata,
            destination: DownloadDestination,
            resumeFrom: DirectTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
        ): DirectTransferResult {
            active.incrementAndGet()
            if (resumeFrom != null) resumeCheckpoints[plan.taskId] = resumeFrom
            val partial = DirectTransferCheckpoint(
                totalBytes = TOTAL_BYTES,
                entityTag = ETAG,
                lastModified = null,
                segments = listOf(DownloadSegment(0, 0, TOTAL_BYTES - 1, 1)),
            )
            onCheckpoint(partial)
            return try {
                completions.computeIfAbsent(plan.taskId) { CompletableDeferred() }.await()
                val completed = partial.copy(
                    segments = listOf(
                        DownloadSegment(
                            index = 0,
                            startByte = 0,
                            endByteInclusive = TOTAL_BYTES - 1,
                            downloadedBytes = TOTAL_BYTES,
                        ),
                    ),
                )
                onCheckpoint(completed)
                DirectTransferResult.Completed(TOTAL_BYTES, completed)
            } finally {
                active.decrementAndGet()
            }
        }

        fun complete(id: String) {
            completions.computeIfAbsent(id) { CompletableDeferred() }.complete(Unit)
        }
    }

    private class RecordingDestination : DownloadDestination {
        val discarded = AtomicBoolean(false)

        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = 0
        override fun open(): SeekableDownloadOutput = error("Runner owns no real output")
        override fun commit() = Unit
        override fun discard() {
            discarded.set(true)
        }
    }

    private companion object {
        const val TOTAL_BYTES = 10L
        const val ETAG = "\"fixture\""
    }
}