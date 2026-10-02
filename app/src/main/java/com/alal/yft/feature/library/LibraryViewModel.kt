package com.alal.yft.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.alal.yft.core.media.player.MediaPlayerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Ready(
        val items: List<LibraryItem>,
        /** The item whose deletion waits for the user's confirmation. */
        val pendingDelete: LibraryItem? = null,
        /** The item open in the in-app player. */
        val playing: LibraryItem? = null,
        /** A one-line outcome of the user's last action. */
        val message: String? = null,
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val mediaPlayerFactory: MediaPlayerFactory,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    /** Reloads the list, keeping the open player and message when the item still exists. */
    fun refresh() {
        viewModelScope.launch {
            val previous = mutableUiState.value as? LibraryUiState.Ready
            mutableUiState.value = when (val items = loadItems()) {
                null -> previous?.copy(message = READ_FAILED) ?: LibraryUiState.Error(READ_FAILED)
                else -> LibraryUiState.Ready(
                    items = items,
                    playing = previous?.playing?.takeIf { open -> items.any { it.id == open.id } },
                    message = previous?.message,
                )
            }
        }
    }

    fun play(item: LibraryItem) = updateReady { it.copy(playing = item, message = null) }

    fun stopPlayback() = updateReady { it.copy(playing = null) }

    fun onPlaybackError(item: LibraryItem) = updateReady { ready ->
        if (ready.playing?.id != item.id) return@updateReady ready
        ready.copy(
            playing = null,
            message = "${item.displayName} could not be played here. Try Open instead.",
        )
    }

    fun requestDelete(item: LibraryItem) = updateReady { it.copy(pendingDelete = item) }

    fun dismissDelete() = updateReady { it.copy(pendingDelete = null) }

    /** Deletes the confirmed item, then reloads so the list shows what is really on disk. */
    fun confirmDelete() {
        val ready = mutableUiState.value as? LibraryUiState.Ready ?: return
        val item = ready.pendingDelete ?: return
        mutableUiState.value = ready.copy(
            pendingDelete = null,
            playing = ready.playing?.takeUnless { it.id == item.id },
        )
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

    fun createPlayer(): ExoPlayer = mediaPlayerFactory.create()

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
