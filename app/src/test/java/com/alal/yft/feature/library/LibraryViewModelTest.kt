package com.alal.yft.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.media.player.MediaPlayerFactory
import com.alal.yft.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startsLoadingThenShowsItemsOrAnEmptyLibrary() = runTest {
        val repository = FakeRepository(mutableListOf())
        val viewModel = viewModel(repository)
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)

        viewModel.refresh()
        runCurrent()
        assertEquals(LibraryUiState.Ready(items = emptyList()), viewModel.uiState.value)

        repository.items += item("a")
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf("a"), ready(viewModel).items.map(LibraryItem::id))
    }

    @Test
    fun readFailureIsExplainedAndRetryRecovers() = runTest {
        val repository = FakeRepository(mutableListOf(item("a")), failReads = true)
        val viewModel = viewModel(repository)

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
    fun deleteWaitsForConfirmationAndDismissKeepsTheFile() = runTest {
        val repository = FakeRepository(mutableListOf(item("a")))
        val viewModel = loaded(repository)

        viewModel.requestDelete(item("a"))
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
        val repository = FakeRepository(mutableListOf(item("a"), item("b")))
        val viewModel = loaded(repository)
        viewModel.play(item("a"))
        viewModel.requestDelete(item("a"))

        viewModel.confirmDelete()
        assertNull(ready(viewModel).pendingDelete)
        assertNull(ready(viewModel).playing)
        runCurrent()

        assertEquals(listOf("a"), repository.deleted)
        val state = ready(viewModel)
        assertEquals(listOf("b"), state.items.map(LibraryItem::id))
        assertEquals("Deleted a.mp4.", state.message)
    }

    @Test
    fun failedDeleteKeepsTheItemAndSaysSo() = runTest {
        val repository = FakeRepository(mutableListOf(item("a")), deleteResult = false)
        val viewModel = loaded(repository)
        viewModel.requestDelete(item("a"))

        viewModel.confirmDelete()
        runCurrent()

        val state = ready(viewModel)
        assertEquals(listOf("a"), state.items.map(LibraryItem::id))
        assertEquals("a.mp4 could not be deleted.", state.message)
    }

    @Test
    fun playbackErrorClosesThePlayerWithAHint() = runTest {
        val repository = FakeRepository(mutableListOf(item("a"), item("b")))
        val viewModel = loaded(repository)
        viewModel.play(item("a"))

        viewModel.onPlaybackError(item("b"))
        assertEquals("a", ready(viewModel).playing?.id)

        viewModel.onPlaybackError(item("a"))
        val state = ready(viewModel)
        assertNull(state.playing)
        assertTrue(state.message!!.contains("Try Open"))

        viewModel.refresh()
        runCurrent()
        assertTrue(ready(viewModel).message!!.contains("Try Open"))
    }

    private fun kotlinx.coroutines.test.TestScope.loaded(
        repository: FakeRepository,
    ): LibraryViewModel = viewModel(repository).also {
        it.refresh()
        runCurrent()
    }

    private fun viewModel(repository: LibraryRepository) = LibraryViewModel(
        repository = repository,
        mediaPlayerFactory = MediaPlayerFactory(
            ApplicationProvider.getApplicationContext<Context>(),
        ),
    )

    private fun ready(viewModel: LibraryViewModel) =
        viewModel.uiState.value as LibraryUiState.Ready

    private fun item(id: String) = LibraryItem(
        id = id,
        displayName = "$id.mp4",
        uri = "content://example/$id.mp4",
        mimeType = "video/mp4",
        sizeBytes = 1,
        modifiedAtEpochMs = 1,
        location = LibraryLocation.APP_STORAGE,
    )

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
