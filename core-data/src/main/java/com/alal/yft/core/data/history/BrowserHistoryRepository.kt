package com.alal.yft.core.data.history

import com.alal.yft.core.data.db.BrowserHistoryDao
import com.alal.yft.core.data.db.BrowserHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One remembered page as the history list shows it. */
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val host: String,
    val lastVisitedAtEpochMs: Long,
    val visitCount: Int,
)

/** The in-app browser's history (P31): pages the user opened, newest first. */
interface BrowserHistoryRepository {
    fun newest(limit: Int): Flow<List<BrowserHistoryEntry>>

    /** Pages whose title, host or address contains [words], newest first. */
    fun search(words: String, limit: Int): Flow<List<BrowserHistoryEntry>>

    /** Saves a visit; false when the address is not one the history keeps. */
    suspend fun record(url: String, title: String?, visitedAtEpochMs: Long): Boolean

    /** The page's title arrived after its visit was saved. */
    suspend fun rename(url: String, title: String?)

    suspend fun delete(url: String)

    suspend fun clear()
}

@Singleton
class RoomBrowserHistoryRepository @Inject constructor(
    private val dao: BrowserHistoryDao,
) : BrowserHistoryRepository {
    override fun newest(limit: Int): Flow<List<BrowserHistoryEntry>> =
        dao.newest(limit).map { rows -> rows.map(BrowserHistoryEntity::toEntry) }

    override fun search(words: String, limit: Int): Flow<List<BrowserHistoryEntry>> {
        val trimmed = words.trim()
        if (trimmed.isEmpty()) return newest(limit)
        return dao.search("%${escapeLike(trimmed)}%", limit)
            .map { rows -> rows.map(BrowserHistoryEntity::toEntry) }
    }

    override suspend fun record(url: String, title: String?, visitedAtEpochMs: Long): Boolean {
        val address = BrowserHistoryAddress.clean(url) ?: return false
        val host = BrowserHistoryAddress.host(address)
        dao.visit(address, titleOf(title, address), host, visitedAtEpochMs)
        dao.deleteVisitedBefore(visitedAtEpochMs - KEEP_MS)
        dao.keepNewest(MAX_PAGES)
        return true
    }

    override suspend fun rename(url: String, title: String?) {
        val address = BrowserHistoryAddress.clean(url) ?: return
        titleOf(title, address)?.let { dao.rename(address, it) }
    }

    override suspend fun delete(url: String) {
        dao.delete(url)
    }

    override suspend fun clear() {
        dao.deleteAll()
    }

    companion object {
        /** Pages older than this are dropped (P31: 90 days). */
        const val KEEP_DAYS = 90
        const val KEEP_MS = KEEP_DAYS * 24L * 60 * 60 * 1_000

        /** At most this many pages are kept; the oldest go first. */
        const val MAX_PAGES = 5_000
        private const val MAX_TITLE = 300

        /** A real title; a blank one or one that is only the address (no title) is none. */
        internal fun titleOf(title: String?, address: String): String? {
            val text = title?.trim()?.replace(WHITESPACE, " ")?.take(MAX_TITLE) ?: return null
            if (text.isEmpty()) return null
            val bare = address.removePrefix("https://")
            if (text == address || text == bare || text == bare.removeSuffix("/")) return null
            if (text.startsWith("https://") || text.startsWith("http://")) return null
            return text
        }

        internal fun escapeLike(words: String): String =
            words.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        private val WHITESPACE = Regex("\\s+")
    }
}

private fun BrowserHistoryEntity.toEntry() = BrowserHistoryEntry(
    url = url,
    title = title,
    host = host,
    lastVisitedAtEpochMs = lastVisitedAtEpochMs,
    visitCount = visitCount,
)
