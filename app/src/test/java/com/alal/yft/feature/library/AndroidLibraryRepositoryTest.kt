package com.alal.yft.feature.library

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidLibraryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun listsPublishedAppStorageFilesNewestFirstWithProviderUris() = runTest {
        val root = AppPrivateDownloads.root(context).apply { mkdirs() }
        File(root, "Old.mp4").apply {
            writeText("old")
            setLastModified(1_000_000L)
        }
        File(root, "New.m4a").apply {
            writeText("newer")
            setLastModified(2_000_000L)
        }
        File(root, "New (1).m4a.ab12.part").writeText("partial")
        val repository = AndroidLibraryRepository(context, UnconfinedTestDispatcher(testScheduler))

        val items = repository.items()

        assertEquals(listOf("New.m4a", "Old.mp4"), items.map(LibraryItem::displayName))
        val audio = items.first()
        assertEquals(LibraryLocation.APP_STORAGE, audio.location)
        assertEquals("audio/mp4", audio.mimeType)
        assertEquals(5L, audio.sizeBytes)
        assertEquals(2_000_000L, audio.modifiedAtEpochMs)
        assertEquals(AppPrivateDownloadProvider.uriFor(context, "New.m4a").toString(), audio.uri)
        assertTrue(audio.isAudio)
        assertTrue(audio.isPlayable)
    }

    @Test
    fun deletingAnAppStorageItemRemovesOnlyThatFile() = runTest {
        val root = AppPrivateDownloads.root(context).apply { mkdirs() }
        File(root, "Keep.mp4").writeText("keep")
        File(root, "Drop.mp4").writeText("drop")
        val repository = AndroidLibraryRepository(context, UnconfinedTestDispatcher(testScheduler))
        val drop = repository.items().single { it.displayName == "Drop.mp4" }

        assertTrue(repository.delete(drop))
        // P42: a file that is already gone counts as deleted, as in Downloads' "Delete file".
        assertTrue(repository.delete(drop))
        assertEquals(listOf("Keep.mp4"), repository.items().map(LibraryItem::displayName))
    }

    @Test
    fun mediaStoreRowsBecomeSharedItemsWithHonestMetadata() {
        val collection = Uri.parse("content://media/external_primary/downloads")
        val cursor = MatrixCursor(AndroidLibraryRepository.SHARED_PROJECTION).apply {
            addRow(arrayOf<Any?>(42L, "Clip.mp4", "video/mp4", 2_048L, 1_700_000_000L))
            addRow(arrayOf<Any?>(43L, "Song.m4a", null, null, null))
            addRow(arrayOf<Any?>(44L, " ", "video/mp4", 1L, 1L))
        }

        val items = cursor.toSharedLibraryItems(collection)

        assertEquals(2, items.size)
        val video = items[0]
        assertEquals("media:42", video.id)
        assertEquals("content://media/external_primary/downloads/42", video.uri)
        assertEquals("video/mp4", video.mimeType)
        assertEquals(2_048L, video.sizeBytes)
        assertEquals(1_700_000_000_000L, video.modifiedAtEpochMs)
        assertEquals(LibraryLocation.SHARED_DOWNLOADS, video.location)
        val audio = items[1]
        assertEquals("audio/mp4", audio.mimeType)
        assertEquals(null, audio.sizeBytes)
        assertEquals(null, audio.modifiedAtEpochMs)
    }

    @Test
    fun unknownFileTypesAreNotOfferedToTheInAppPlayer() {
        assertEquals(null, LibraryMimeTypes.forFileName("archive"))
        val item = LibraryItem(
            id = "app:notes.bin",
            displayName = "notes.bin",
            uri = "content://example/notes.bin",
            mimeType = null,
            sizeBytes = 1,
            modifiedAtEpochMs = null,
            location = LibraryLocation.APP_STORAGE,
        )
        assertFalse(item.isPlayable)
        assertTrue(item.copy(mimeType = "video/webm").isPlayable)
    }
}
