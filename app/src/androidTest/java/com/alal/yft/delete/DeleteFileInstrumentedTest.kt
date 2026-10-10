package com.alal.yft.delete

import android.annotation.TargetApi
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.download.AndroidDownloadedFileDeleter
import com.alal.yft.download.AndroidFileDeleteSystem
import com.alal.yft.feature.downloads.DeleteFileQuestion
import com.alal.yft.feature.downloads.DownloadsViewModel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P42 on the CI emulator (API 34) with the real MediaStore: a small file saved through the
 * Download/YFT path, then Downloads → Delete file → Delete. MediaStore no longer finds it and
 * the record is gone. The JVM twin is `DownloadsViewModelDeleteFileTest`.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@TargetApi(Build.VERSION_CODES.Q)
class DeleteFileInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver: ContentResolver = context.contentResolver

    @Test
    fun deleteFileRemovesTheSavedItemFromDownloadYftAndItsRecord() = runBlocking {
        val name = "yft-p42-${UUID.randomUUID()}.mp4"
        val destination = MediaStoreDownloadDestination.create(resolver, name, VIDEO_MP4)
        val uri = Uri.parse(destination.publishedUri)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            save(destination, ByteArray(4_096) { (it % 251).toByte() })
            assertTrue("the saved item is listed", exists(uri))
            val store = Store(completed("p42", name, uri.toString()))
            val queue = DownloadQueue(
                store = store,
                transferDispatcher = NeverFinishing,
                scope = scope,
                clock = System::currentTimeMillis,
                ioDispatcher = Dispatchers.IO,
            )
            val viewModel = withContext(Dispatchers.Main) {
                DownloadsViewModel(
                    queue = queue,
                    fileDeleter = AndroidDownloadedFileDeleter(AndroidFileDeleteSystem(context)),
                )
            }
            waitFor("the row") { queue.tasks.value.any { it.id == "p42" } }

            withContext(Dispatchers.Main) { viewModel.onDeleteFile("p42") }
            assertEquals(DeleteFileQuestion("p42", name), viewModel.deleteFileQuestion.value)
            withContext(Dispatchers.Main) { viewModel.confirmDeleteFile() }

            waitFor("the record to go") { store.tasks.isEmpty() }
            assertFalse("MediaStore still finds the file", exists(uri))
            assertTrue(queue.tasks.value.none { it.id == "p42" })
        } finally {
            scope.cancel()
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private fun save(destination: DownloadDestination, bytes: ByteArray) {
        destination.prepare(bytes.size.toLong())
        destination.open().use { output ->
            output.write(0, bytes, 0, bytes.size)
            output.sync()
        }
        destination.commit()
    }

    private fun exists(uri: Uri): Boolean = resolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns._ID),
        null,
        null,
        null,
    )?.use { it.moveToFirst() } ?: false

    private suspend fun waitFor(what: String, done: () -> Boolean) {
        runCatching {
            withTimeout(10_000) {
                while (!done()) delay(50)
            }
        }.onFailure { throw AssertionError("timed out waiting for $what", it) }
    }

    private fun completed(id: String, name: String, uri: String) = StoredDownloadTask(
        id = id,
        displayName = name,
        status = DownloadTaskStatus.COMPLETED,
        planType = DownloadPlanType.DIRECT,
        totalBytes = 4_096,
        downloadedBytes = 4_096,
        mimeType = VIDEO_MP4,
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = uri,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = 4_096,
            entityTag = null,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, 4_095, 4_096)),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private class Store(initial: StoredDownloadTask) : DownloadTaskStore {
        val tasks = ConcurrentHashMap(mapOf(initial.id to initial))

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
        const val VIDEO_MP4 = "video/mp4"
    }
}
