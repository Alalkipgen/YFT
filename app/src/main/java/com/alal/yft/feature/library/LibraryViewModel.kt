package com.alal.yft.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DownloadTaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Ready(
        val items: List<LibraryItem>,
        /** The item whose deletion waits for the user's confirmation. */
        val pendingDelete: LibraryItem? = null,
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
                else -> LibraryUiState.Ready(items = items, message = previous?.message)
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

    fun requestDelete(item: LibraryItem) = updateReady { it.copy(pendingDelete = item) }

    fun dismissDelete() = updateReady { it.copy(pendingDelete = null) }

    /** Deletes the confirmed item, then reloads so the list shows what is really on disk. */
    fun confirmDelete() {
        val ready = mutableUiState.value as? LibraryUiState.Ready ?: return
        val item = ready.pendingDelete ?: return
        mutableUiState.value = ready.copy(pendingDelete = null)
        if (item.isSameFileAs(playback.state.value?.item)) playback.stop()
        viewModelScope.launch {
            val deleted = try {
                repository.delete(item)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            val items = loadItems()
            updateReady { current ->
                current.copy(
                    items = items ?: current.items.filterNot { deleted && it.id == item.id },
                    message = if (deleted) {
                        "Deleted ${item.displayName}."
                    } else {
                        "${item.displayName} could not be deleted."
                    },
                )
            }
        }
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
