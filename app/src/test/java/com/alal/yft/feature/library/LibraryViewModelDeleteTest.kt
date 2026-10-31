package com.alal.yft.feature.library

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
 * v1.0.2 item 5b: the files picked in the Library's select mode are deleted after one question
 * ("Delete 3 files?"), together, with one Android request for those that need the user's
 * consent (one at a time on Android 10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryViewModelDeleteTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakeLibraryPlayback()
    private val a = libraryItem("a")
    private val b = libraryItem("b")
    private val c = libraryItem("c")
    private val d = libraryItem("d")

    @Test
    fun onePromptForThePickedFilesThenTheyAreDeletedTogether() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c, d))
        val viewModel = loaded(repository)

        viewModel.requestDeletePicked(listOf(a, b, c, a))
        assertEquals(listOf(a, b, c), ready(viewModel).pendingDelete)
        assertTrue(repository.calls.isEmpty())

        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf(listOf(a, b, c)), repository.calls)
        val state = ready(viewModel)
        assertEquals(emptyList<LibraryItem>(), state.pendingDelete)
        assertEquals(listOf(d), state.items)
        assertEquals("Deleted 3 files.", state.message)
    }

    @Test
    fun oneAndroidRequestCoversTheFilesThatNeedItAndAllowedDeletesThem() = runTest {
        val request = intentSender(1)
        val repository = FakeRepository(mutableListOf(a, b, c, d)) { asked ->
            if (calls.size == 1) {
                deleteNow(listOf(a))
                LibraryDeletion(deleted = listOf(a), request = request, asking = listOf(b, c))
            } else {
                deleteNow(asked)
            }
        }
        val viewModel = loaded(repository)
        val requests = consentRequests(viewModel)

        viewModel.requestDeletePicked(listOf(a, b, c))
        viewModel.confirmDelete()
        runCurrent()
        assertEquals(listOf(request), requests)
        assertNull(ready(viewModel).message)

        viewModel.onDeleteConsentResult(allowed = true)
        runCurrent()

        assertEquals(listOf(listOf(a, b, c), listOf(b, c)), repository.calls)
        assertEquals(listOf(request), requests)
        assertEquals(listOf(d), ready(viewModel).items)
        assertEquals("Deleted 3 files.", ready(viewModel).message)
        // A late answer changes nothing.
        viewModel.onDeleteConsentResult(allowed = true)
        runCurrent()
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun declinedKeepsTheFilesAndTheMessageSaysWhatWent() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c)) {
            deleteNow(listOf(a))
            LibraryDeletion(deleted = listOf(a), request = intentSender(1), asking = listOf(b, c))
        }
        val viewModel = loaded(repository)
        consentRequests(viewModel)

        viewModel.requestDeletePicked(listOf(a, b, c))
        viewModel.confirmDelete()
        runCurrent()
        viewModel.onDeleteConsentResult(allowed = false)
        runCurrent()

        assertEquals(1, repository.calls.size)
        assertEquals(listOf(b, c), ready(viewModel).items)
        assertEquals("Deleted 1 file.", ready(viewModel).message)
    }

    @Test
    fun androidTenAsksAboutOneFileAtATime() = runTest {
        val first = intentSender(1)
        val second = intentSender(2)
        val repository = FakeRepository(mutableListOf(a, b, c)) { asked ->
            when (calls.size) {
                1 -> LibraryDeletion(request = first, asking = listOf(a), waiting = listOf(b, c))
                3 -> LibraryDeletion(request = second, asking = listOf(b), waiting = listOf(c))
                else -> deleteNow(asked)
            }
        }
        val viewModel = loaded(repository)
        val requests = consentRequests(viewModel)

        viewModel.requestDeletePicked(listOf(a, b, c))
        viewModel.confirmDelete()
        runCurrent()
        viewModel.onDeleteConsentResult(allowed = true)
        runCurrent()
        assertEquals(listOf(first, second), requests)
        viewModel.onDeleteConsentResult(allowed = false)
        runCurrent()

        assertEquals(
            listOf(listOf(a, b, c), listOf(a), listOf(b, c), listOf(c)),
            repository.calls,
        )
        assertEquals(listOf(b), ready(viewModel).items)
        assertEquals("Deleted 2 files.", ready(viewModel).message)
    }

    @Test
    fun aFileStillThereAfterItWasAllowedCountsAsNotDeleted() = runTest {
        val repository = FakeRepository(mutableListOf(a, b)) {
            LibraryDeletion(request = intentSender(1), asking = listOf(a, b))
        }
        val viewModel = loaded(repository)
        consentRequests(viewModel)

        viewModel.requestDeletePicked(listOf(a, b))
        viewModel.confirmDelete()
        runCurrent()
        viewModel.onDeleteConsentResult(allowed = true)
        runCurrent()

        assertEquals(2, repository.calls.size)
        assertEquals("2 files could not be deleted.", ready(viewModel).message)
    }

    @Test
    fun filesConfirmedWhileAndroidAsksFollowTheFirstOnes() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c)) { asked ->
            if (calls.size == 1) {
                LibraryDeletion(request = intentSender(1), asking = asked)
            } else {
                deleteNow(asked)
            }
        }
        val viewModel = loaded(repository)
        consentRequests(viewModel)
        viewModel.requestDeletePicked(listOf(a, b))
        viewModel.confirmDelete()
        runCurrent()

        viewModel.requestDelete(c)
        viewModel.confirmDelete()
        runCurrent()
        assertEquals(1, repository.calls.size)

        viewModel.onDeleteConsentResult(allowed = true)
        runCurrent()
        assertEquals(listOf(listOf(a, b), listOf(a, b), listOf(c)), repository.calls)
        assertEquals(emptyList<LibraryItem>(), ready(viewModel).items)
        assertEquals("Deleted 3 files.", ready(viewModel).message)
    }

    @Test
    fun somePickedFilesStayAndTheMessageNamesThem() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c)) {
            deleteNow(listOf(a, b))
            LibraryDeletion(deleted = listOf(a, b), failed = listOf(c))
        }
        val viewModel = loaded(repository)

        viewModel.requestDeletePicked(listOf(a, b, c))
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf(c), ready(viewModel).items)
        assertEquals("Deleted 2 files; c.mp4 could not be deleted.", ready(viewModel).message)
    }

    @Test
    fun aDeleteThatThrowsCountsEveryPickedFileAsNotDeleted() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c)) { throw IOException("gone") }
        val viewModel = loaded(repository)

        viewModel.requestDeletePicked(listOf(a, b, c))
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf(a, b, c), ready(viewModel).items)
        assertEquals("3 files could not be deleted.", ready(viewModel).message)
    }

    @Test
    fun playbackOfAPickedFileStopsButOfAnotherDoesNot() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c, d))
        val viewModel = loaded(repository)

        viewModel.play(d)
        viewModel.requestDeletePicked(listOf(a, b))
        viewModel.confirmDelete()
        runCurrent()
        assertEquals("d", playback.state.value?.item?.id)

        viewModel.play(c)
        viewModel.requestDeletePicked(listOf(c, d))
        viewModel.confirmDelete()
        assertNull(playback.state.value)
    }

    @Test
    fun aReloadKeepsTheQuestionForTheFilesStillListed() = runTest {
        val repository = FakeRepository(mutableListOf(a, b, c))
        val viewModel = loaded(repository)

        viewModel.requestDeletePicked(listOf(a, b))
        repository.items.remove(b)
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(a), ready(viewModel).pendingDelete)

        viewModel.dismissDelete()
        assertEquals(emptyList<LibraryItem>(), ready(viewModel).pendingDelete)
        viewModel.requestDeletePicked(emptyList())
        assertEquals(emptyList<LibraryItem>(), ready(viewModel).pendingDelete)
    }

    @Test
    fun theMessageCountsTheDeletedFilesAndNamesOneThatStayed() {
        assertNull(deletedMessage(emptyList(), emptyList()))
        assertEquals("Deleted 1 file.", deletedMessage(listOf(a), emptyList()))
        assertEquals("Deleted 3 files.", deletedMessage(listOf(a, b, c), emptyList()))
        assertEquals("a.mp4 could not be deleted.", deletedMessage(emptyList(), listOf(a)))
        assertEquals("2 files could not be deleted.", deletedMessage(emptyList(), listOf(a, b)))
        assertEquals(
            "Deleted 1 file; 2 files could not be deleted.",
            deletedMessage(listOf(a), listOf(b, c)),
        )
    }

    private fun TestScope.loaded(repository: FakeRepository): LibraryViewModel =
        LibraryViewModel(repository, playback).also {
            it.refresh()
            runCurrent()
        }

    private fun TestScope.consentRequests(viewModel: LibraryViewModel): List<IntentSender> {
        val requests = mutableListOf<IntentSender>()
        backgroundScope.launch { viewModel.deleteConsentRequests.collect { requests += it } }
        runCurrent()
        return requests
    }

    private fun ready(viewModel: LibraryViewModel) =
        viewModel.uiState.value as LibraryUiState.Ready

    private fun intentSender(code: Int): IntentSender = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(),
        code,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    /** Deletes what it is asked to, unless [answer] says otherwise; records every call. */
    private class FakeRepository(
        val items: MutableList<LibraryItem>,
        val answer: FakeRepository.(List<LibraryItem>) -> LibraryDeletion = { deleteNow(it) },
    ) : LibraryRepository {
        val calls = mutableListOf<List<LibraryItem>>()

        override suspend fun items(): List<LibraryItem> = items.toList()

        override suspend fun delete(item: LibraryItem): Boolean = error("deleteAll only")

        override suspend fun deleteAll(items: List<LibraryItem>): LibraryDeletion {
            calls += items
            return answer(items)
        }

        fun deleteNow(gone: List<LibraryItem>): LibraryDeletion {
            items.removeAll { item -> gone.any { it.id == item.id } }
            return LibraryDeletion(deleted = gone)
        }
    }
}
