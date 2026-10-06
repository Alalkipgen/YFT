package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.IOException
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
import org.junit.Assert.assertNull
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
    fun `an mp3 conversion is recorded as audio mpeg, not the source type`() = runTest {
        val queue = DownloadQueue(InMemoryStore(), ControlledRunner(), backgroundScope)
        queue.setNetworkAvailable(false)

        queue.enqueue(plan("aac"), metadata(), RecordingDestination())
        queue.enqueue(
            plan("mp3").copy(mp3 = Mp3Encoding(192, "Song")),
            metadata(),
            RecordingDestination(),
        )

        val types = queue.tasks.value.associate { it.id to it.mimeType }
        assertEquals("application/octet-stream", types["aac"])
        assertEquals("audio/mpeg", types["mp3"])
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

    @Test
    fun `public destination persists recovery URI then published URI`() = runTest {
        val store = InMemoryStore()
        val runner = ControlledRunner()
        val destination = RecordingDestination(
            recoveryUri = "content://media/pending/1",
            publishedUri = "content://media/completed/1",
        )
        val queue = DownloadQueue(store, runner, backgroundScope, maxConcurrentDownloads = 1)

        queue.enqueue(
            plan = plan("public"),
            metadata = metadata(),
            destination = destination,
            destinationSpec = DownloadDestinationSpec(DownloadDestinationKind.MEDIA_STORE),
        )
        runCurrent()

        assertEquals(
            "content://media/pending/1",
            queue.tasks.value.single().destinationUri,
        )

        runner.complete("public")
        runCurrent()

        assertEquals(DownloadTaskStatus.COMPLETED, queue.tasks.value.single().status)
        assertEquals(
            "content://media/completed/1",
            queue.tasks.value.single().destinationUri,
        )
        assertEquals(
            "content://media/completed/1",
            store.snapshot().single().destinationUri,
        )
    }

    /**
     * P21: a Retry after the file itself failed starts over at byte 0 in a new destination of
     * the same name. The old queue resumed the checkpoint in the broken one and failed again.
     */
    @Test
    fun `retry after a storage failure starts over in a new destination and completes`() =
        runTest {
            val healthy = TestDestination(recoveryUri = "content://media/pending/2")
            val broken = TestDestination(
                recoveryUri = "content://media/pending/1",
                renewed = healthy,
            )
            val runner = ScriptedRunner { _, destination ->
                if (destination === broken) STORAGE_FAILURE else null
            }
            val store = InMemoryStore()
            val queue = DownloadQueue(store, runner, backgroundScope, maxConcurrentDownloads = 1)
            queue.enqueue(
                plan = plan("storage"),
                metadata = metadata(),
                destination = broken,
                destinationSpec = DownloadDestinationSpec(DownloadDestinationKind.MEDIA_STORE),
            )
            runCurrent()
            val failed = queue.tasks.value.single()
            assertEquals(DownloadTaskStatus.FAILED, failed.status)
            assertEquals(STORAGE_FAILURE, failed.failure)
            assertEquals(STORAGE_FAILURE, store.snapshot().single().failure)
            assertEquals(PARTIAL_BYTES, failed.downloadedBytes)

            queue.resume("storage")
            runCurrent()

            val done = queue.tasks.value.single()
            assertEquals(DownloadTaskStatus.COMPLETED, done.status)
            assertNull(done.failureReason)
            assertNull(done.failure)
            assertEquals(listOf<DownloadDestination>(broken, healthy), runner.destinations)
            assertEquals("The retry starts at byte 0", listOf(0L, 0L), runner.resumedFrom)
            assertEquals(1, broken.renewals.get())
            assertEquals("content://media/pending/2", done.destinationUri)
            assertEquals("content://media/pending/2", store.snapshot().single().destinationUri)
        }

    /** P21: a dropped connection keeps its partial file, so the Retry resumes it. */
    @Test
    fun `retry after a network failure resumes its checkpoint in the same destination`() =
        runTest {
            val destination = TestDestination(length = TOTAL_BYTES)
            val runner = ScriptedRunner { call, _ -> NETWORK_FAILURE.takeIf { call == 0 } }
            val queue = DownloadQueue(
                InMemoryStore(),
                runner,
                backgroundScope,
                maxConcurrentDownloads = 1,
            )
            queue.enqueue(plan("network-retry"), metadata(), destination)
            runCurrent()
            assertEquals(NETWORK_FAILURE, queue.tasks.value.single().failure)

            queue.resume("network-retry")
            runCurrent()

            assertEquals(DownloadTaskStatus.COMPLETED, queue.tasks.value.single().status)
            assertNull(queue.tasks.value.single().failure)
            assertEquals(
                listOf<DownloadDestination>(destination, destination),
                runner.destinations,
            )
            assertEquals(listOf(0L, PARTIAL_BYTES), runner.resumedFrom)
            assertEquals(0, destination.renewals.get())
        }

    /** P21: when the partial file is gone, resuming its checkpoint cannot work; start over. */
    @Test
    fun `retry when the partial file is gone starts over at byte 0`() = runTest {
        val replacement = TestDestination(length = 0)
        val destination = TestDestination(length = null, renewed = replacement)
        val runner = ScriptedRunner { call, _ -> NETWORK_FAILURE.takeIf { call == 0 } }
        val queue = DownloadQueue(InMemoryStore(), runner, backgroundScope)
        queue.enqueue(plan("gone"), metadata(), destination)
        runCurrent()

        queue.resume("gone")
        runCurrent()

        assertEquals(DownloadTaskStatus.COMPLETED, queue.tasks.value.single().status)
        assertEquals(listOf<DownloadDestination>(destination, replacement), runner.destinations)
        assertEquals(listOf(0L, 0L), runner.resumedFrom)
    }

    /** P21: a Retry that cannot make a new file stays failed, with the new error's details. */
    @Test
    fun `retry that cannot make a new destination stays failed with the new details`() =
        runTest {
            val denied = IOException("EACCES (Permission denied)")
            val destination = TestDestination(renewError = denied)
            val runner = ScriptedRunner { _, _ -> STORAGE_FAILURE }
            val queue = DownloadQueue(InMemoryStore(), runner, backgroundScope)
            queue.enqueue(plan("no-new-file"), metadata(), destination)
            runCurrent()

            queue.resume("no-new-file")
            runCurrent()

            val task = queue.tasks.value.single()
            assertEquals(DownloadTaskStatus.FAILED, task.status)
            assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, task.failureReason)
            assertEquals(DownloadFailureStage.OPEN_FILE, task.failure?.stage)
            assertEquals("IOException: EACCES (Permission denied)", task.failure?.detail)
            assertEquals("No second transfer", 1, runner.destinations.size)
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

    private class RecordingDestination(
        override val recoveryUri: String? = null,
        override val publishedUri: String? = recoveryUri,
    ) : DownloadDestination {
        val discarded = AtomicBoolean(false)

        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = 0
        override fun open(): SeekableDownloadOutput = error("Runner owns no real output")
        override fun commit() = Unit
        override fun discard() {
            discarded.set(true)
        }
    }

    /**
     * Records each transfer. A call fails with what [outcome] returns for it after writing
     * [PARTIAL_BYTES] more bytes, or completes the file when it returns null.
     */
    private class ScriptedRunner(
        private val outcome: (call: Int, destination: DownloadDestination) -> DownloadFailure?,
    ) : DirectTransferRunner {
        val destinations = mutableListOf<DownloadDestination>()
        val resumedFrom = mutableListOf<Long>()

        override suspend fun transfer(
            plan: DirectDownloadPlan,
            metadata: RemoteFileMetadata,
            destination: DownloadDestination,
            resumeFrom: DirectTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
        ): DirectTransferResult {
            val call = destinations.size
            destinations += destination
            val start = resumeFrom?.downloadedBytes ?: 0L
            resumedFrom += start
            val failure = outcome(call, destination)
            if (failure != null) {
                val partial = checkpointAt(start + PARTIAL_BYTES)
                onCheckpoint(partial)
                return DirectTransferResult.Failure(failure, partial)
            }
            val completed = checkpointAt(TOTAL_BYTES)
            onCheckpoint(completed)
            return DirectTransferResult.Completed(TOTAL_BYTES, completed)
        }
    }

    /** A destination whose partial file has [length] bytes and that renews into [renewed]. */
    private class TestDestination(
        override val recoveryUri: String? = null,
        private val length: Long? = 0,
        private val renewed: DownloadDestination? = null,
        private val renewError: IOException? = null,
    ) : DownloadDestination {
        val renewals = AtomicInteger()

        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = length
        override fun open(): SeekableDownloadOutput = error("Runner owns no real output")
        override fun commit() = Unit
        override fun discard() = Unit
        override fun renew(): DownloadDestination? {
            renewals.incrementAndGet()
            renewError?.let { throw it }
            return renewed
        }
    }

    private companion object {
        const val TOTAL_BYTES = 10L
        const val PARTIAL_BYTES = 4L
        const val ETAG = "\"fixture\""
        val STORAGE_FAILURE = DownloadFailure(
            reason = DownloadFailureReason.STORAGE_UNAVAILABLE,
            stage = DownloadFailureStage.WRITE_FILE,
            detail = "IOException: EIO (I/O error)",
        )
        val NETWORK_FAILURE = DownloadFailure(
            reason = DownloadFailureReason.NETWORK,
            stage = DownloadFailureStage.READ_SOURCE,
            detail = "SocketException: Connection reset",
        )

        fun checkpointAt(downloaded: Long) = DirectTransferCheckpoint(
            totalBytes = TOTAL_BYTES,
            entityTag = ETAG,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, TOTAL_BYTES - 1, downloaded)),
        )
    }
}
