package com.alal.yft.feature.downloads

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.download.FileDeletion
import com.alal.yft.download.SavedDownloadFile
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P42: "Delete file" deletes the saved file and then the row. Before P42 the only delete was
 * "Remove from list", which kept the file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsViewModelDeleteFileTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun confirmDeletesTheFileThenTheRecord() = runTest {
        val store = Store(listOf(completed("done")))
        val deleter = FakeDeleter(FileDeletion.Deleted)
        val viewModel = DownloadsViewModel(queue(store), deleter)
        val messages = messagesOf(viewModel)
        runCurrent()

        viewModel.onDeleteFile("done")
        runCurrent()

        assertEquals(DeleteFileQuestion("done", "done.mp4"), viewModel.deleteFileQuestion.value)
        assertTrue("nothing is deleted before Delete", deleter.files.isEmpty())

        viewModel.confirmDeleteFile()
        runCurrent()

        assertNull(viewModel.deleteFileQuestion.value)
        assertEquals(
            listOf(SavedDownloadFile(DownloadDestinationKind.MEDIA_STORE, MEDIA_URI, "done.mp4")),
            deleter.files,
        )
        assertTrue(store.tasks.isEmpty())
        assertTrue(viewModel.uiState.value.rows.isEmpty())
        assertEquals(listOf("File deleted"), messages)
    }

    @Test
    fun cancelKeepsTheFileAndTheRow() = runTest {
        val store = Store(listOf(completed("done")))
        val deleter = FakeDeleter(FileDeletion.Deleted)
        val viewModel = DownloadsViewModel(queue(store), deleter)
        runCurrent()

        viewModel.onDeleteFile("done")
        viewModel.cancelDeleteFile()
        runCurrent()

        assertNull(viewModel.deleteFileQuestion.value)
        assertTrue(deleter.files.isEmpty())
        assertEquals(listOf("done"), store.tasks.keys.toList())
        assertEquals(1, viewModel.uiState.value.rows.size)
    }

    @Test
    fun withoutTheQuestionDeleteFileDeletesAtOnce() = runTest {
        val store = Store(listOf(completed("done")))
        val deleter = FakeDeleter(FileDeletion.Deleted)
        val viewModel = DownloadsViewModel(queue(store), deleter, asksBeforeDeletingFile = false)
        runCurrent()

        viewModel.onDeleteFile("done")
        runCurrent()

        assertNull(viewModel.deleteFileQuestion.value)
        assertEquals(1, deleter.files.size)
        assertTrue(store.tasks.isEmpty())
    }

    @Test
    fun aFileThatStaysKeepsItsRow() = runTest {
        val store = Store(listOf(completed("done")))
        val viewModel = DownloadsViewModel(queue(store), FakeDeleter(FileDeletion.Failed))
        val messages = messagesOf(viewModel)
        runCurrent()

        viewModel.onDeleteFile("done")
        viewModel.confirmDeleteFile()
        runCurrent()

        assertEquals(listOf("done"), store.tasks.keys.toList())
        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals(listOf("Could not delete the file"), messages)
    }

    @Test
    fun androidsRequestComesFirstAndAnAllowedDeleteRemovesTheRow() = runTest {
        val store = Store(listOf(completed("done")))
        val request = intentSender()
        val deleter = FakeDeleter(FileDeletion.NeedsConsent(request), FileDeletion.Deleted)
        val viewModel = DownloadsViewModel(queue(store), deleter, asksBeforeDeletingFile = false)
        val requests = mutableListOf<IntentSender>()
        backgroundScope.launch { viewModel.deleteConsentRequests.collect { requests += it } }
        runCurrent()

        viewModel.onDeleteFile("done")
        runCurrent()

        assertEquals(listOf(request), requests)
        assertEquals(listOf("done"), store.tasks.keys.toList())

        viewModel.onDeleteConsentResult(allowed = true)
        runCurrent()

        assertEquals(2, deleter.files.size)
        assertTrue(store.tasks.isEmpty())
    }

    @Test
    fun aRefusedRequestKeepsTheFileAndTheRow() = runTest {
        val store = Store(listOf(completed("done")))
        val deleter = FakeDeleter(FileDeletion.NeedsConsent(intentSender()))
        val viewModel = DownloadsViewModel(queue(store), deleter, asksBeforeDeletingFile = false)
        backgroundScope.launch { viewModel.deleteConsentRequests.collect {} }
        runCurrent()

        viewModel.onDeleteFile("done")
        runCurrent()
        viewModel.onDeleteConsentResult(allowed = false)
        runCurrent()

        assertEquals(1, deleter.files.size)
        assertEquals(listOf("done"), store.tasks.keys.toList())
    }

    @Test
    fun anUnfinishedDownloadHasNoDeleteFile() = runTest {
        val store = Store(listOf(completed("done").copy(status = DownloadTaskStatus.PAUSED)))
        val deleter = FakeDeleter(FileDeletion.Deleted)
        val viewModel = DownloadsViewModel(queue(store), deleter, asksBeforeDeletingFile = false)
        runCurrent()

        viewModel.onDeleteFile("done")
        runCurrent()

        assertNull(viewModel.deleteFileQuestion.value)
        assertTrue(deleter.files.isEmpty())
        assertEquals(1, store.tasks.size)
    }

    private fun kotlinx.coroutines.test.TestScope.messagesOf(
        viewModel: DownloadsViewModel,
    ): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch { viewModel.messages.collect { messages += it } }
        return messages
    }

    private fun intentSender(): IntentSender = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    private fun queue(store: DownloadTaskStore): DownloadQueue = DownloadQueue(
        store = store,
        transferDispatcher = NeverFinishing,
        scope = CoroutineScope(mainDispatcherRule.dispatcher),
        maxConcurrentDownloads = 1,
        clock = { 1_000 },
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    private fun completed(id: String): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = DownloadTaskStatus.COMPLETED,
        planType = DownloadPlanType.DIRECT,
        totalBytes = 100,
        downloadedBytes = 100,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = MEDIA_URI,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = 100,
            entityTag = null,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, 99, 100)),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    /** Answers with [results] in turn, then with the last one. */
    private class FakeDeleter(private vararg val results: FileDeletion) : DownloadedFileDeleter {
        val files = mutableListOf<SavedDownloadFile>()

        override suspend fun delete(file: SavedDownloadFile): FileDeletion {
            files += file
            return results[minOf(files.size, results.size) - 1]
        }
    }

    private class Store(initial: List<StoredDownloadTask>) : DownloadTaskStore {
        val tasks = LinkedHashMap<String, StoredDownloadTask>().apply {
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

    private object NeverFinishing : DownloadTransferDispatcher {
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

    private companion object {
        const val MEDIA_URI = "content://media/external/downloads/42"
    }
}
