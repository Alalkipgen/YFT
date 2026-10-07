package com.alal.yft.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.data.history.BrowserHistoryRepository
import com.alal.yft.core.data.preferences.BrowserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The browser's History list and the start page's Recent pages (P31). */
data class BrowserHistoryUiState(
    val query: String = "",
    /** Newest first; the words in [query] filter them. */
    val pages: List<BrowserHistoryEntry> = emptyList(),
    /** The start page's "Recent": the last six pages. */
    val recent: List<BrowserHistoryEntry> = emptyList(),
    /** Settings › Browser › Save browser history. */
    val saving: Boolean = true,
)

/**
 * Keeps the pages the browser opens (when Settings › Browser › Save browser history is on) and
 * gives the History list and Recent their pages. The switch is read from the store at each
 * visit, so a page that finishes just after the browser opens is not kept when it is off.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserHistoryViewModel internal constructor(
    private val history: BrowserHistoryRepository,
    private val preferences: BrowserPreferencesRepository,
    private val clock: () -> Long,
) : ViewModel() {
    @Inject
    constructor(
        history: BrowserHistoryRepository,
        preferences: BrowserPreferencesRepository,
    ) : this(history, preferences, System::currentTimeMillis)

    private val query = MutableStateFlow("")

    /** Visits, titles, deletes and clears run one after another, in the order they came. */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A page that could not be saved is not worth stopping the browser for.
                }
            }
        }
    }

    val uiState: StateFlow<BrowserHistoryUiState> = combine(
        query,
        query.flatMapLatest { words -> history.search(words, LIST_LIMIT) },
        history.newest(RECENT_COUNT),
        preferences.preferences.map { it.saveHistory },
    ) { words, pages, recent, saving ->
        BrowserHistoryUiState(query = words, pages = pages, recent = recent, saving = saving)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowserHistoryUiState())

    fun onQueryChanged(words: String) {
        query.value = words
    }

    fun recordVisit(url: String, title: String?) {
        val visitedAt = clock()
        write {
            if (preferences.preferences.first().saveHistory) {
                history.record(url, title, visitedAt)
            }
        }
    }

    fun renamePage(url: String, title: String) {
        write {
            if (preferences.preferences.first().saveHistory) history.rename(url, title)
        }
    }

    fun delete(url: String) {
        write { history.delete(url) }
    }

    fun clear() {
        write { history.clear() }
    }

    private fun write(block: suspend () -> Unit) {
        writes.trySend(block)
    }

    companion object {
        const val RECENT_COUNT = 6

        /** The list shows this many pages; the search reaches older ones. */
        const val LIST_LIMIT = 500
    }
}
