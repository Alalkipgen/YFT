package com.alal.yft.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One page the in-app browser opened (P31). The address is already cleaned (HTTPS, no fragment,
 * no tracking parameters) and is the key, so a page opened again raises its count and time.
 */
@Entity(
    tableName = "browser_history",
    indices = [Index(value = ["last_visited_at"])],
)
data class BrowserHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "last_visited_at")
    val lastVisitedAtEpochMs: Long,
    @ColumnInfo(name = "visit_count")
    val visitCount: Int,
)
