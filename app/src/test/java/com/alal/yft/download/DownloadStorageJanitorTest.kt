package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.DownloadWorkspaces
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadStorageJanitorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cutoff = 1_000_000_000_000L
    private val before = cutoff - 60_000
    private val after = cutoff + 60_000

    @Test
    fun onlyStaleStagingFilesAndOrphanWorkspacesAreRemoved() = runTest {
        val store = MemoryStore(
            task("live", DownloadTaskStatus.NEEDS_REFRESH),
            task("done", DownloadTaskStatus.COMPLETED),
        )
        val downloads = folder.newFolder("downloads")
        val stalePart = file(downloads, "old.mp4.abc.part", before)
        val freshPart = file(downloads, "new.mp4.def.part", after)
        val published = file(downloads, "finished.mp4", before)
        val hls = folder.newFolder("download-hls")
        val dash = folder.newFolder("download-dash")
        val liveWorkspace = workspace(hls, "hls", "live", before)
        val doneWorkspace = workspace(hls, "hls", "done", before)
        val goneWorkspace = workspace(hls, "hls", "gone", before)
        val freshWorkspace = workspace(hls, "hls", "started-now", after)
        val unrelated = File(hls, "notes").apply { mkdirs(); setLastModified(before) }
        val goneDash = workspace(dash, "dash", "gone", before)

        val report = janitor(this, store, downloads, hls, dash).clean()

        assertEquals(JanitorReport(partialFiles = 1, workspaces = 3, records = 0), report)
        assertFalse(stalePart.exists())
        assertTrue(freshPart.exists())
        assertTrue(published.exists())
        assertTrue(liveWorkspace.exists())
        assertFalse(doneWorkspace.exists())
        assertFalse(goneWorkspace.exists())
        assertTrue(freshWorkspace.exists())
        assertTrue(unrelated.exists())
        assertFalse(goneDash.exists())
    }

    @Test
    fun onlyTheOldestFinishedRecordsBeyondTheCapArePruned() = runTest {
        val store = MemoryStore(
            task("c1", DownloadTaskStatus.COMPLETED, updatedAt = 1),
            task("f2", DownloadTaskStatus.FAILED, updatedAt = 2),
            task("x3", DownloadTaskStatus.CANCELLED, updatedAt = 3),
            task("c4", DownloadTaskStatus.COMPLETED, updatedAt = 4),
            task("refresh", DownloadTaskStatus.NEEDS_REFRESH, updatedAt = 0),
        )

        val report = janitor(this, store, keepFinished = 2).clean()

        assertEquals(2, report.records)
        assertEquals(setOf("x3", "c4", "refresh"), store.ids())
    }

    @Test
    fun launchOnceRunsASinglePassPerProcess() = runTest {
        val downloads = folder.newFolder("downloads")
        val first = file(downloads, "a.mp4.1.part", before)
        val janitor = janitor(this, MemoryStore(), downloads)

        janitor.launchOnce()
        runCurrent()
        assertFalse(first.exists())

        val second = file(downloads, "b.mp4.2.part", before)
        janitor.launchOnce()
        runCurrent()
        assertTrue(second.exists())
    }

    private fun janitor(
        scope: TestScope,
        store: MemoryStore,
        downloads: File = File(folder.root, "missing-downloads"),
        hls: File = File(folder.root, "missing-hls"),
        dash: File = File(folder.root, "missing-dash"),
        keepFinished: Int = DownloadStorageJanitor.DEFAULT_KEEP_FINISHED_RECORDS,
    ): DownloadStorageJanitor {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val queue = DownloadQueue(
            store = store,
            transferDispatcher = IdleDispatcher(),
            scope = scope.backgroundScope,
            clock = { cutoff },
            ioDispatcher = dispatcher,
        )
        return DownloadStorageJanitor(
            queue = queue,
            appDownloadsRoot = downloads,
            workspaceRoots = listOf(
                WorkspaceRoot(hls, DownloadWorkspaces.HLS_PREFIX),
                WorkspaceRoot(dash, DownloadWorkspaces.DASH_PREFIX),
            ),
            processStartEpochMs = { cutoff },
            scope = scope.backgroundScope,
            keepFinishedRecords = keepFinished,
            ioDispatcher = dispatcher,
        )
    }

    private fun file(directory: File, name: String, modified: Long): File =
        File(directory, name).apply {
            writeText("x")
            setLastModified(modified)
        }

    private fun workspace(root: File, prefix: String, taskId: String, modified: Long): File =
        File(root, DownloadWorkspaces.nameFor(prefix, taskId)).apply {
            mkdirs()
            File(this, "chunk-00001.bin").writeText("segment")
            setLastModified(modified)
        }

    private fun task(
        id: String,
        status: DownloadTaskStatus,
        updatedAt: Long = 1,
    ): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        totalBytes = 10,
        downloadedBytes = 0,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = status == DownloadTaskStatus.NEEDS_REFRESH,
        failureReason = null,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = 10,
            entityTag = null,
            lastModified = null,
            segments = listOf(
                DownloadSegment(
                    index = 0,
                    startByte = 0,
                    endByteInclusive = 9,
                    downloadedBytes = 0,
                ),
            ),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = updatedAt,
    )

    private class MemoryStore(vararg initial: StoredDownloadTask) : DownloadTaskStore {
        private val tasks = LinkedHashMap<String, StoredDownloadTask>().apply {
            initial.forEach { put(it.id, it) }
        }

        fun ids(): Set<String> = tasks.keys.toSet()

        override suspend fun loadAll(): List<StoredDownloadTask> = tasks.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            tasks[task.id] = task
        }

        override suspend fun delete(id: String) {
            tasks.remove(id)
        }
    }

    private class IdleDispatcher : DownloadTransferDispatcher {
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
}
