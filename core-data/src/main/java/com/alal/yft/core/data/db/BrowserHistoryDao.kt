package com.alal.yft.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The browser's history (P31, Room 6). SQLite on Android 7 has no UPSERT, so a visit is an
 * update that falls back to an insert inside one transaction.
 */
@Dao
abstract class BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY last_visited_at DESC, url ASC LIMIT :limit")
    abstract fun newest(limit: Int): Flow<List<BrowserHistoryEntity>>

    /** [pattern] is a LIKE pattern with `\` escaping `%`, `_` and itself. */
    @Query(
        "SELECT * FROM browser_history " +
            "WHERE title LIKE :pattern ESCAPE '\\' OR host LIKE :pattern ESCAPE '\\' " +
            "OR url LIKE :pattern ESCAPE '\\' " +
            "ORDER BY last_visited_at DESC, url ASC LIMIT :limit",
    )
    abstract fun search(pattern: String, limit: Int): Flow<List<BrowserHistoryEntity>>

    @Query("SELECT * FROM browser_history WHERE url = :url LIMIT 1")
    abstract suspend fun find(url: String): BrowserHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(entry: BrowserHistoryEntity): Long

    @Query(
        "UPDATE browser_history SET title = COALESCE(:title, title), host = :host, " +
            "last_visited_at = :visitedAtEpochMs, visit_count = visit_count + 1 WHERE url = :url",
    )
    protected abstract suspend fun touch(
        url: String,
        title: String?,
        host: String,
        visitedAtEpochMs: Long,
    ): Int

    /**
     * A visit: the known page's count and time rise (its title only when [title] is known), a
     * new page is added with a count of 1 and its host as the title until the page names itself.
     */
    @Transaction
    open suspend fun visit(url: String, title: String?, host: String, visitedAtEpochMs: Long) {
        if (touch(url, title, host, visitedAtEpochMs) == 0) {
            val entry = BrowserHistoryEntity(url, title ?: host, host, visitedAtEpochMs, 1)
            insert(entry)
        }
    }

    /** The page's own title arrived after the visit was saved; the count stays. */
    @Query("UPDATE browser_history SET title = :title WHERE url = :url")
    abstract suspend fun rename(url: String, title: String): Int

    @Query("DELETE FROM browser_history WHERE url = :url")
    abstract suspend fun delete(url: String): Int

    @Query("DELETE FROM browser_history")
    abstract suspend fun deleteAll(): Int

    @Query("DELETE FROM browser_history WHERE last_visited_at < :beforeEpochMs")
    abstract suspend fun deleteVisitedBefore(beforeEpochMs: Long): Int

    @Query(
        "DELETE FROM browser_history WHERE url NOT IN (SELECT url FROM browser_history " +
            "ORDER BY last_visited_at DESC, url ASC LIMIT :keep)",
    )
    abstract suspend fun keepNewest(keep: Int): Int

    @Query("SELECT COUNT(*) FROM browser_history")
    abstract suspend fun count(): Int
}
