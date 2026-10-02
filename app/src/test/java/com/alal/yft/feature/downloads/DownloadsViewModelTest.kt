package com.alal.yft.feature.downloads

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
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun restoredIncompleteTaskSurfacesAsNeedingRefreshedLink() = runTest {
        val store = InMemoryDownloadTaskStore(
            listOf(storedTask("a", DownloadTaskStatus.RUNNING, downloaded = 10, total = 100)),
        )
        val viewModel = DownloadsViewModel(queue(store))

        runCurrent()

        val row = viewModel.uiState.value.rows.single()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, row.status)
        assertTrue(row.requiresLinkRefresh)
        assertEquals(setOf(DownloadAction.DELETE), row.availableActions)
    }

    @Test
    fun restoredCompletedTaskStaysCompletedAndRemovable() = runTest {
        val store = InMemoryDownloadTaskStore(
            listOf(storedTask("done", DownloadTaskStatus.COMPLETED, downloaded = 100, total = 100)),
        )
        val viewModel = DownloadsViewModel(queue(store))

        runCurrent()

        val row = viewModel.uiState.value.rows.single()
        assertEquals(DownloadTaskStatus.COMPLETED, row.status)
        assertEquals(1f, row.progressFraction)

        viewModel.onAction(DownloadAction.DELETE, "done")
        runCurrent()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun enqueuedTaskRunsThenPausesAndResumesThroughTheViewModel() = runTest {
        val store = InMemoryDownloadTaskStore()
        val downloadQueue = queue(store)
        val viewModel = DownloadsViewModel(downloadQueue)
        runCurrent()

        downloadQueue.enqueue(
            plan = directPlan("task-1"),
            metadata = metadata(totalBytes = 2_048),
            destination = RecordingDestination(),
            destinationSpec = DownloadDestinationSpec(DownloadDestinationKind.APP_PRIVATE),
        )
        runCurrent()

        val running = viewModel.uiState.value.rows.single()
        assertEquals(DownloadTaskStatus.RUNNING, running.status)
        assertEquals(DownloadPlanType.DIRECT, running.planType)
        assertEquals(setOf(DownloadAction.PAUSE, DownloadAction.CANCEL), running.availableActions)
        assertTrue(viewModel.uiState.value.canPauseAll)
        assertEquals(1, viewModel.uiState.value.occupyingCount)

        viewModel.onAction(DownloadAction.PAUSE, "task-1")
        runCurrent()

        val paused = viewModel.uiState.value.rows.single()
        assertEquals(DownloadTaskStatus.PAUSED, paused.status)
        assertEquals(setOf(DownloadAction.RESUME, DownloadAction.CANCEL), paused.availableActions)

        viewModel.onAction(DownloadAction.RESUME, "task-1")
        runCurrent()

        assertEquals(DownloadTaskStatus.RUNNING, viewModel.uiState.value.rows.single().status)
    }

    @Test
    fun cancelDiscardsPartialWorkAndKeepsRecordRemovable() = runTest {
        val store = InMemoryDownloadTaskStore()
        val downloadQueue = queue(store)
        val viewModel = DownloadsViewModel(downloadQueue)
        val destination = RecordingDestination()
        runCurrent()

        downloadQueue.enqueue(
            plan = directPlan("task-1"),
            metadata = metadata(totalBytes = 2_048),
            destination = destination,
        )
        runCurrent()

        viewModel.onAction(DownloadAction.CANCEL, "task-1")
        runCurrent()

        val cancelled = viewModel.uiState.value.rows.single()
        assertEquals(DownloadTaskStatus.CANCELLED, cancelled.status)
        assertEquals(setOf(DownloadAction.DELETE), cancelled.availableActions)
        assertTrue(destination.discarded)
        assertNull(cancelled.failureReason)
    }

    @Test
    fun pauseAllStopsEveryUnfinishedTask() = runTest {
        val store = InMemoryDownloadTaskStore()
        val downloadQueue = queue(store, maxConcurrent = 2)
        val viewModel = DownloadsViewModel(downloadQueue)
        runCurrent()

        downloadQueue.enqueue(directPlan("task-1"), metadata(1_024), RecordingDestination())
        downloadQueue.enqueue(directPlan("task-2"), metadata(1_024), RecordingDestination())
        runCurrent()

        assertEquals(2, viewModel.uiState.value.occupyingCount)

        viewModel.pauseAll()
        runCurrent()

        assertTrue(
            viewModel.uiState.value.rows.all { it.status == DownloadTaskStatus.PAUSED },
        )
        assertEquals(0, viewModel.uiState.value.occupyingCount)
    }

    private fun queue(
        store: DownloadTaskStore,
        maxConcurrent: Int = 1,
    ): DownloadQueue = DownloadQueue(
        store = store,
        transferDispatcher = NeverFinishingDispatcher(),
        scope = CoroutineScope(mainDispatcherRule.dispatcher),
        maxConcurrentDownloads = maxConcurrent,
        clock = { 1_000 },
    )

    private fun directPlan(taskId: String): DirectDownloadPlan = DirectDownloadPlan(
        taskId = taskId,
        sourceUrl = "https://cdn.test/$taskId.mp4",
        suggestedFileName = "$taskId.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://example.test/watch",
            userAgent = "test-agent",
            cookie = null,
        ),
        mimeType = "video/mp4",
        preferredSegmentCount = 1,
    )

    private fun metadata(totalBytes: Long?): RemoteFileMetadata = RemoteFileMetadata(
        finalUrl = "https://cdn.test/final.mp4",
        totalBytes = totalBytes,
        supportsByteRanges = true,
        entityTag = null,
        lastModified = null,
        contentType = "video/mp4",
        suggestedFileName = "final.mp4",
    )

    private fun storedTask(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
        total: Long?,
        failureReason: DownloadFailureReason? = null,
    ): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        totalBytes = total,
        downloadedBytes = downloaded,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = failureReason,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = total,
            entityTag = null,
            lastModified = null,
            segments = listOf(
                DownloadSegment(
                    index = 0,
                    startByte = 0,
                    endByteInclusive = total?.minus(1),
                    downloadedBytes = downloaded,
                ),
            ),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private class InMemoryDownloadTaskStore(
        initial: List<StoredDownloadTask> = emptyList(),
    ) : DownloadTaskStore {
        private val tasks = LinkedHashMap<String, StoredDownloadTask>().apply {
            initial.forEach { put(it.id, it) }
        }

        override suspend fun loadAll(): List<StoredDownloadTask> = tasks.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            tasks[task.id] = task
        }

        override suspend fun delete(id: String) {
            tasks.remove(id)
        }
    }

    /** Keeps every transfer in flight so queue state stays observable without real I/O. */
    private class NeverFinishingDispatcher : DownloadTransferDispatcher {
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

    private class RecordingDestination : DownloadDestination {
        var discarded = false
            private set

        override fun prepare(expectedLength: Long?) = Unit

        override fun temporaryLength(): Long? = null

        override fun open(): SeekableDownloadOutput = error("Not used in this test")

        override fun commit() = Unit

        override fun discard() {
            discarded = true
        }
    }
}
