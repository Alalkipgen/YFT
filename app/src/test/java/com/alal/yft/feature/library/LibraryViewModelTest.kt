package com.alal.yft.feature.library

import com.alal.yft.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakeLibraryPlayback()

    @Test
    fun startsLoadingThenShowsItemsOrAnEmptyLibrary() = runTest {
        val repository = FakeRepository(mutableListOf())
        val viewModel = LibraryViewModel(repository, playback)
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)

        viewModel.refresh()
        runCurrent()
        assertEquals(LibraryUiState.Ready(items = emptyList()), viewModel.uiState.value)

        repository.items += libraryItem("a")
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf("a"), ready(viewModel).items.map(LibraryItem::id))
    }

    @Test
    fun readFailureIsExplainedAndRetryRecovers() = runTest {
        val repository = FakeRepository(mutableListOf(libraryItem("a")), failReads = true)
        val viewModel = LibraryViewModel(repository, playback)

        viewModel.refresh()
        runCurrent()
        val error = viewModel.uiState.value as LibraryUiState.Error
        assertTrue(error.message.contains("could not be read"))

        repository.failReads = false
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf("a"), ready(viewModel).items.map(LibraryItem::id))
    }

    @Test
    fun aFinishedDownloadRereadsTheList() = runTest {
        val repository = FakeRepository(mutableListOf(libraryItem("a")))
        val finished = MutableStateFlow(3)
        val viewModel = LibraryViewModel(repository, playback, finished)
        runCurrent()
        // The count it starts with is not news.
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)

        viewModel.refresh()
        runCurrent()
        repository.items += libraryItem("b")
        finished.value = 4
        runCurrent()

        assertEquals(listOf("a", "b"), ready(viewModel).items.map(LibraryItem::id))
    }

    @Test
    fun playStartsOnlyPlayableFilesAndClearsTheLastMessage() = runTest {
        val viewModel = loaded(FakeRepository(mutableListOf(libraryItem("a"))))
        viewModel.showMessage("Deleted b.mp4.")

        viewModel.play(libraryItem("notes", name = "notes.bin"))
        assertEquals(emptyList<String>(), playback.calls)
        assertEquals("Deleted b.mp4.", ready(viewModel).message)

        viewModel.play(libraryItem("a"))
        assertEquals(listOf("play:a"), playback.calls)
        assertNull(ready(viewModel).message)
        assertSame(playback.state, viewModel.playing)
    }

    @Test
    fun aListedFileThatHasGoneStopsPlayingButOneFromDownloadsDoesNot() = runTest {
        val repository = FakeRepository(mutableListOf(libraryItem("a"), libraryItem("b")))
        val viewModel = loaded(repository)

        playback.play(libraryItem("download-7", name = "elsewhere.mp4"))
        viewModel.refresh()
        runCurrent()
        assertEquals("download-7", playback.state.value?.item?.id)

        viewModel.play(libraryItem("a"))
        repository.items.removeAll { it.id == "a" }
        viewModel.refresh()
        runCurrent()
        assertNull(playback.state.value)
        assertEquals(listOf("b"), ready(viewModel).items.map(LibraryItem::id))
    }

    @Test
    fun deleteWaitsForConfirmationAndDismissKeepsTheFile() = runTest {
        val repository = FakeRepository(mutableListOf(libraryItem("a")))
        val viewModel = loaded(repository)

        viewModel.requestDelete(libraryItem("a"))
        assertEquals("a", ready(viewModel).pendingDelete?.id)
        assertEquals(0, repository.deleted.size)

        viewModel.dismissDelete()
        runCurrent()
        assertNull(ready(viewModel).pendingDelete)
        assertEquals(0, repository.deleted.size)
        assertEquals(1, ready(viewModel).items.size)
    }

    @Test
    fun confirmedDeleteRemovesTheFileStopsItsPlaybackAndReportsIt() = runTest {
        val repository = FakeRepository(mutableListOf(libraryItem("a"), libraryItem("b")))
        val viewModel = loaded(repository)
        viewModel.play(libraryItem("a"))
        viewModel.requestDelete(libraryItem("a"))

        viewModel.confirmDelete()
        assertNull(ready(viewModel).pendingDelete)
        assertNull(playback.state.value)
        runCurrent()

        assertEquals(listOf("a"), repository.deleted)
        val state = ready(viewModel)
        assertEquals(listOf("b"), state.items.map(LibraryItem::id))
        assertEquals("Deleted a.mp4.", state.message)
    }

    @Test
    fun deletingAnotherFileKeepsPlaybackAndAFailedDeleteSaysSo() = runTest {
        val repository = FakeRepository(
            mutableListOf(libraryItem("a"), libraryItem("b")),
            deleteResult = false,
        )
        val viewModel = loaded(repository)
        viewModel.play(libraryItem("b"))
        viewModel.requestDelete(libraryItem("a"))

        viewModel.confirmDelete()
        runCurrent()

        val state = ready(viewModel)
        assertEquals(listOf("a", "b"), state.items.map(LibraryItem::id))
        assertEquals("a.mp4 could not be deleted.", state.message)
        assertEquals("b", playback.state.value?.item?.id)

        viewModel.dismissMessage()
        assertNull(ready(viewModel).message)
    }

    private fun TestScope.loaded(repository: FakeRepository): LibraryViewModel =
        LibraryViewModel(repository, playback).also {
            it.refresh()
            runCurrent()
        }

    private fun ready(viewModel: LibraryViewModel) =
        viewModel.uiState.value as LibraryUiState.Ready

    private class FakeRepository(
        val items: MutableList<LibraryItem>,
        var failReads: Boolean = false,
        val deleteResult: Boolean = true,
    ) : LibraryRepository {
        val deleted = mutableListOf<String>()

        override suspend fun items(): List<LibraryItem> {
            if (failReads) throw IOException("unreadable")
            return items.toList()
        }

        override suspend fun delete(item: LibraryItem): Boolean {
            if (!deleteResult) return false
            deleted += item.id
            items.removeAll { it.id == item.id }
            return true
        }
    }
}
