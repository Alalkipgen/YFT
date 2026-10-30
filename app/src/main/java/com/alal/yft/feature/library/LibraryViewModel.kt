package com.alal.yft.feature.library

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DownloadTaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Ready(
        val items: List<LibraryItem>,
        /**
         * The files whose deletion waits for the user's confirmation: one from its ⋯ menu, or
         * (v1.0.2 item 5b) those picked in select mode, for one "Delete 3 files?" question.
         */
        val pendingDelete: List<LibraryItem> = emptyList(),
        /** A one-line outcome of the user's last action. */
        val message: String? = null,
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel internal constructor(
    private val repository: LibraryRepository,
    private val playback: LibraryPlayback,
    finishedDownloads: Flow<Int>,
) : ViewModel() {
    /** The list is re-read whenever the number of finished downloads changes. */
    @Inject
    constructor(
        repository: LibraryRepository,
        playback: LibraryPlayback,
        queue: DownloadQueue,
    ) : this(
        repository = repository,
        playback = playback,
        finishedDownloads = queue.tasks.map { tasks ->
            tasks.count { it.status == DownloadTaskStatus.COMPLETED }
        },
    )

    /** Without a download queue, for tests of the list and playback. */
    constructor(repository: LibraryRepository, playback: LibraryPlayback) :
        this(repository, playback, emptyFlow())

    private val mutableUiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    /** What the app-wide player has loaded, so the screen can mark it. */
    val playing: StateFlow<PlaybackState?> = playback.state

    private val consentRequests = Channel<IntentSender>(Channel.BUFFERED)

    /**
     * v1.0.2 item 5b: Android's own "Allow YFT to delete?" requests, for the screen to launch;
     * one covers all the confirmed files that need it (one at a time on Android 10).
     */
    val deleteConsentRequests: Flow<IntentSender> = consentRequests.receiveAsFlow()

    /** The confirmed files still being deleted, or null. */
    private var deleteRun: DeleteRun? = null

    init {
        viewModelScope.launch {
            finishedDownloads.distinctUntilChanged().drop(1).collect { refresh() }
        }
    }

    /**
     * Reloads the list, keeping the message. Playback of a listed file that has gone is stopped;
     * a file started from Downloads is left alone until the list shows it.
     */
    fun refresh() {
        viewModelScope.launch {
            val previous = mutableUiState.value as? LibraryUiState.Ready
            val items = loadItems()
            mutableUiState.value = when (items) {
                null -> previous?.copy(message = READ_FAILED) ?: LibraryUiState.Error(READ_FAILED)
                else -> LibraryUiState.Ready(
                    items = items,
                    // An open question stays for the files that are still listed.
                    pendingDelete = previous?.pendingDelete.orEmpty().filter { pending ->
                        items.any { it.id == pending.id }
                    },
                    message = previous?.message,
                )
            }
            val loaded = playback.state.value?.item
            val wasListed = previous?.items.orEmpty().any { it.isSameFileAs(loaded) }
            if (items != null && wasListed && items.none { it.isSameFileAs(loaded) }) {
                playback.stop()
            }
        }
    }

    /** Plays [item] in the app-wide player; videos are shown by the full-screen player. */
    fun play(item: LibraryItem) {
        if (!item.isPlayable) return
        updateReady { it.copy(message = null) }
        playback.play(item)
    }

    fun requestDelete(item: LibraryItem) = requestDeletePicked(listOf(item))

    /** v1.0.2 item 5b: one question ("Delete 3 files?") for the files picked in select mode. */
    fun requestDeletePicked(items: List<LibraryItem>) {
        val picked = items.distinctBy(LibraryItem::id)
        if (picked.isEmpty()) return
        updateReady { it.copy(pendingDelete = picked) }
    }

    fun dismissDelete() = updateReady { it.copy(pendingDelete = emptyList()) }

    /**
     * Deletes the confirmed files together, then reloads so the list shows what is really on
     * disk. Playback of one of them stops first. Where Android asks the user first, one request
     * covers them all ([deleteConsentRequests]).
     */
    fun confirmDelete() {
        val ready = mutableUiState.value as? LibraryUiState.Ready ?: return
        val items = ready.pendingDelete.takeIf { it.isNotEmpty() } ?: return
        mutableUiState.value = ready.copy(pendingDelete = emptyList())
        val loaded = playback.state.value?.item
        if (items.any { it.isSameFileAs(loaded) }) playback.stop()
        val running = deleteRun
        if (running != null) {
            // Deleting goes on (or waits for Android's answer): these follow.
            running.left += items.filterNot { item -> running.has(item) }
            return
        }
        val run = DeleteRun(items)
        deleteRun = run
        viewModelScope.launch { continueDeleting(run) }
    }

    /**
     * The answer to Android's request: once allowed, the files it asked about are deleted again,
     * and those still there count as not deleted; either way the files left follow.
     */
    fun onDeleteConsentResult(allowed: Boolean) {
        val run = deleteRun ?: return
        val asked = run.asking.takeIf { it.isNotEmpty() } ?: return
        run.asking = emptyList()
        viewModelScope.launch {
            if (allowed) {
                val again = deleteAll(asked)
                run.deleted += again.deleted
                run.failed += again.failed + again.asking + again.waiting
            }
            continueDeleting(run)
        }
    }

    private suspend fun continueDeleting(run: DeleteRun) {
        while (run.left.isNotEmpty()) {
            val batch = run.left.toList()
            run.left.clear()
            val result = deleteAll(batch)
            run.deleted += result.deleted
            run.failed += result.failed
            // Android 10 asks about one file at a time: the rest come first after its answer.
            run.left.addAll(0, result.waiting)
            val request = result.request
            if (request != null && result.asking.isNotEmpty()) {
                run.asking = result.asking
                consentRequests.send(request)
                return
            }
            run.failed += result.asking
        }
        deleteRun = null
        val items = loadItems()
        val gone = run.deleted.map(LibraryItem::id).toSet()
        updateReady { current ->
            current.copy(
                items = items ?: current.items.filterNot { it.id in gone },
                message = deletedMessage(run.deleted, run.failed) ?: current.message,
            )
        }
    }

    private suspend fun deleteAll(items: List<LibraryItem>): LibraryDeletion = try {
        repository.deleteAll(items)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        LibraryDeletion(failed = items)
    }

    fun showMessage(message: String) = updateReady { it.copy(message = message) }

    fun dismissMessage() = updateReady { it.copy(message = null) }

    private suspend fun loadItems(): List<LibraryItem>? = try {
        repository.items()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun updateReady(transform: (LibraryUiState.Ready) -> LibraryUiState.Ready) {
        mutableUiState.update { current ->
            if (current is LibraryUiState.Ready) transform(current) else current
        }
    }

    private companion object {
        const val READ_FAILED = "The library could not be read. Try again."
    }
}

/** v1.0.2 item 5b: the confirmed files a delete is still working through. */
private class DeleteRun(items: List<LibraryItem>) {
    /** Not tried yet, or waiting for Android's answer about another file (Android 10). */
    val left = items.toMutableList()

    /** The files Android's open request asks about. */
    var asking: List<LibraryItem> = emptyList()
    val deleted = mutableListOf<LibraryItem>()
    val failed = mutableListOf<LibraryItem>()

    fun has(item: LibraryItem): Boolean =
        (left + asking + deleted + failed).any { it.id == item.id }
}

/**
 * The line after a delete: "Deleted a.mp4.", "Deleted 3 files.", "a.mp4 could not be deleted.",
 * "Deleted 2 files; b.mp4 could not be deleted."; null when nothing was deleted or tried (the
 * user declined Android's request).
 */
internal fun deletedMessage(deleted: List<LibraryItem>, failed: List<LibraryItem>): String? {
    fun files(items: List<LibraryItem>) =
        items.singleOrNull()?.displayName ?: "${items.size} files"
    return when {
        deleted.isEmpty() && failed.isEmpty() -> null
        failed.isEmpty() -> "Deleted ${files(deleted)}."
        deleted.isEmpty() -> "${files(failed)} could not be deleted."
        else -> "Deleted ${files(deleted)}; ${files(failed)} could not be deleted."
    }
}
