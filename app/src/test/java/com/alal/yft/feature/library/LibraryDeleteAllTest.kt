package com.alal.yft.feature.library

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.download.FileDeletion
import com.alal.yft.download.FilesDeletion
import com.alal.yft.download.SavedDownloadFile
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.0.2 item 5b: the Library's select mode deletes the picked files together, through the
 * same deleter as Downloads' "Delete file", so Android asks once for all that need it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryDeleteAllTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun thePickedFilesGoToTheDeleterTogetherAndTheOutcomeNamesTheItems() = runTest {
        val request = PendingIntent.getActivity(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
        val handed = mutableListOf<List<SavedDownloadFile>>()
        val deleter = object : DownloadedFileDeleter {
            override suspend fun delete(file: SavedDownloadFile): FileDeletion =
                error("one at a time")

            override suspend fun deleteAll(files: List<SavedDownloadFile>): FilesDeletion {
                handed += files
                return FilesDeletion(
                    deleted = listOf(files[2]),
                    failed = listOf(files[1]),
                    request = request,
                    asking = listOf(files[0]),
                )
            }
        }
        val shared = libraryItem("media:7", "a.mp4", location = LibraryLocation.SHARED_DOWNLOADS)
        val chosen = libraryItem("saf:doc", "b.mp4", location = LibraryLocation.CHOSEN_FOLDER)
        val own = libraryItem("app:c.mp4", "c.mp4", location = LibraryLocation.APP_STORAGE)
        val repository = AndroidLibraryRepository(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            fileDeleter = deleter,
        )

        val result = repository.deleteAll(listOf(shared, chosen, own, shared))

        assertEquals(
            listOf(
                listOf(
                    SavedDownloadFile(DownloadDestinationKind.MEDIA_STORE, shared.uri, "a.mp4"),
                    SavedDownloadFile(DownloadDestinationKind.SAF_DOCUMENT, chosen.uri, "b.mp4"),
                    SavedDownloadFile(DownloadDestinationKind.APP_PRIVATE, null, "c.mp4"),
                ),
            ),
            handed,
        )
        assertEquals(
            LibraryDeletion(
                deleted = listOf(own),
                failed = listOf(chosen),
                request = request,
                asking = listOf(shared),
            ),
            result,
        )
    }

    @Test
    fun aRepositoryWithoutItsOwnDeleteAllDeletesOneAtATime() = runTest {
        val tried = mutableListOf<String>()
        val repository = object : LibraryRepository {
            override suspend fun items(): List<LibraryItem> = emptyList()

            override suspend fun delete(item: LibraryItem): Boolean {
                tried += item.id
                return when (item.id) {
                    "a" -> true
                    "b" -> false
                    else -> throw IOException("unreadable")
                }
            }
        }
        val items = listOf(libraryItem("a"), libraryItem("b"), libraryItem("c"))

        val result = repository.deleteAll(items)

        assertEquals(listOf("a", "b", "c"), tried)
        assertEquals(LibraryDeletion(deleted = items.take(1), failed = items.drop(1)), result)
    }
}
