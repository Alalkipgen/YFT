package com.alal.yft.feature.browser

import com.alal.yft.core.data.history.BrowserHistoryAddress
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.data.history.BrowserHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The browser's history held in memory, with the real address rules (P31). */
internal class FakeBrowserHistoryRepository(
    initial: List<BrowserHistoryEntry> = emptyList(),
) : BrowserHistoryRepository {
    val pages = MutableStateFlow(initial)
    var clears = 0
        private set

    override fun newest(limit: Int): Flow<List<BrowserHistoryEntry>> =
        pages.map { it.sortedByDescending(BrowserHistoryEntry::lastVisitedAtEpochMs).take(limit) }

    override fun search(words: String, limit: Int): Flow<List<BrowserHistoryEntry>> =
        newest(Int.MAX_VALUE).map { all ->
            val needle = words.trim()
            all.filter {
                needle.isEmpty() || it.title.contains(needle, ignoreCase = true) ||
                    it.host.contains(needle, ignoreCase = true) ||
                    it.url.contains(needle, ignoreCase = true)
            }.take(limit)
        }

    override suspend fun record(url: String, title: String?, visitedAtEpochMs: Long): Boolean {
        val address = BrowserHistoryAddress.clean(url) ?: return false
        val host = BrowserHistoryAddress.host(address)
        pages.update { list ->
            val old = list.firstOrNull { it.url == address }
            val entry = BrowserHistoryEntry(
                url = address,
                title = title ?: old?.title ?: host,
                host = host,
                lastVisitedAtEpochMs = visitedAtEpochMs,
                visitCount = (old?.visitCount ?: 0) + 1,
            )
            list.filterNot { it.url == address } + entry
        }
        return true
    }

    override suspend fun rename(url: String, title: String?) {
        val address = BrowserHistoryAddress.clean(url) ?: return
        val named = title ?: return
        pages.update { list -> list.map { if (it.url == address) it.copy(title = named) else it } }
    }

    override suspend fun delete(url: String) {
        pages.update { list -> list.filterNot { it.url == url } }
    }

    override suspend fun clear() {
        clears++
        pages.value = emptyList()
    }
}
