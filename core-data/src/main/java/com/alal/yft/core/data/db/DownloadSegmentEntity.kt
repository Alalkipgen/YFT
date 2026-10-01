package com.alal.yft.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "download_segments",
    primaryKeys = ["download_id", "segment_index"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["download_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["download_id"])],
)
data class DownloadSegmentEntity(
    @ColumnInfo(name = "download_id")
    val downloadId: String,
    @ColumnInfo(name = "segment_index")
    val segmentIndex: Int,
    @ColumnInfo(name = "start_byte")
    val startByte: Long,
    @ColumnInfo(name = "end_byte_inclusive")
    val endByteInclusive: Long?,
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long,
) {
    init {
        require(downloadId.isNotBlank())
        require(segmentIndex >= 0)
        require(startByte >= 0)
        require(endByteInclusive == null || endByteInclusive >= startByte)
        require(downloadedBytes >= 0)
        val length = endByteInclusive?.let { it - startByte + 1 }
        require(length == null || downloadedBytes <= length)
    }
}