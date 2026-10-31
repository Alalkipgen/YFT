package com.alal.yft.feature.library

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.SaveFolder
import com.alal.yft.download.AndroidSaveFolders
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.testing.FakeTree
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.0.2 item 28: while new downloads go to a folder picked in Android's folder picker, the
 * library lists its top-level video and audio files with the folder's name as their place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChosenFolderLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folders = AndroidSaveFolders(context)
    private lateinit var directory: File
    private lateinit var folder: SaveFolder

    @Before
    fun setUp() {
        FakeTree.install()
        directory = FakeTree.folder(context, "Movies")
        folder = folders.take(FakeTree.treeUri("Movies").toString())!!
    }

    @Test
    fun `the folder's videos and audio are listed with its name as their place`() = runTest {
        File(directory, "Clip.mp4").apply {
            writeBytes(ByteArray(3))
            setLastModified(2_000_000L)
        }
        File(directory, "Song.m4a").apply {
            writeText("song")
            setLastModified(1_000_000L)
        }
        File(directory, ".Next.mp4.yft-t1.part").writeText("a download still being written")
        File(directory, "notes.txt").writeText("text")
        File(directory, "photo.jpg").writeText("picture")
        File(directory, "Album").mkdirs()
        File(directory, "Album/Inner.mp4").writeText("not at the top")

        val items = repository().items()

        assertEquals(listOf("Clip.mp4", "Song.m4a"), items.map(LibraryItem::displayName))
        val clip = items.first()
        assertEquals(LibraryLocation.CHOSEN_FOLDER, clip.location)
        assertEquals("Movies", clip.place)
        assertEquals("video/mp4", clip.mimeType)
        assertEquals(3L, clip.sizeBytes)
        assertEquals(2_000_000L, clip.modifiedAtEpochMs)
        assertEquals(
            DocumentsContract.buildDocumentUriUsingTree(
                FakeTree.treeUri("Movies"),
                "primary:Movies/Clip.mp4",
            ).toString(),
            clip.uri,
        )
        assertTrue(clip.isPlayable)
        assertTrue(items.last().isAudio)
    }

    @Test
    fun `a file deleted outside YFT is no longer listed`() = runTest {
        File(directory, "Keep.mp4").writeText("keep")
        val gone = File(directory, "Gone.mp4").apply { writeText("gone") }
        val repository = repository()
        assertEquals(2, repository.items().size)

        gone.delete()

        assertEquals(listOf("Keep.mp4"), repository.items().map(LibraryItem::displayName))
    }

    @Test
    fun `deleting a folder file removes only that document`() = runTest {
        File(directory, "Keep.mp4").writeText("keep")
        File(directory, "Drop.mp4").writeText("drop")
        val repository = repository()
        val drop = repository.items().single { it.displayName == "Drop.mp4" }

        assertTrue(repository.delete(drop))
        // P42: a file that is already gone counts as deleted.
        assertTrue(repository.delete(drop))
        assertEquals(listOf("Keep.mp4"), directory.list()!!.toList())
        assertEquals(listOf("Keep.mp4"), repository.items().map(LibraryItem::displayName))
    }

    @Test
    fun `a folder that can no longer be used lists nothing and the rest still shows`() = runTest {
        File(directory, "Clip.mp4").writeText("clip")
        File(AppPrivateDownloads.root(context).apply { mkdirs() }, "Own.mp4").writeText("own")

        FakeTree.revoke(context, folder.treeUri)
        assertEquals(listOf("Own.mp4"), repository().items().map(LibraryItem::displayName))

        folders.take(folder.treeUri)
        directory.deleteRecursively()
        assertEquals(listOf("Own.mp4"), repository().items().map(LibraryItem::displayName))
    }

    @Test
    fun `without a chosen folder the library lists what it did before`() = runTest {
        File(directory, "Clip.mp4").writeText("clip")
        File(AppPrivateDownloads.root(context).apply { mkdirs() }, "Own.mp4").writeText("own")
        val before = AndroidLibraryRepository(context, UnconfinedTestDispatcher(testScheduler))

        assertEquals(listOf("Own.mp4"), repository(chosen = null).items().map { it.displayName })
        assertEquals(listOf("Own.mp4"), before.items().map(LibraryItem::displayName))
    }

    @Test
    fun `the folder is listed only while new downloads go there`() = runTest {
        File(directory, "Clip.mp4").writeText("clip")
        val preferences = FakePreferences(
            DownloadPreferences(location = DownloadLocation.CHOSEN_FOLDER, folder = folder),
        )
        val repository = LibraryModule.provideLibraryRepository(
            context,
            DownloadedFileDeleter.Unavailable,
            preferences,
            folders,
        )

        assertEquals(listOf("Clip.mp4"), repository.items().map(LibraryItem::displayName))
        // N5 (v1.0.2): and named in the empty library's text.
        assertEquals("Movies", repository.chosenFolderName())

        preferences.state.value = preferences.state.value.copy(
            location = DownloadLocation.SHARED_DOWNLOADS,
        )
        assertEquals(emptyList<LibraryItem>(), repository.items())
        assertNull(repository.chosenFolderName())
    }

    private class FakePreferences(initial: DownloadPreferences) : DownloadPreferencesRepository {
        val state = MutableStateFlow(initial)
        override val preferences: Flow<DownloadPreferences> = state

        override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) {
            state.value = transform(state.value)
        }
    }

    private fun TestScope.repository(chosen: SaveFolder? = folder) = AndroidLibraryRepository(
        context = context,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        chosenFolder = { chosen },
        folders = folders,
    )
}
