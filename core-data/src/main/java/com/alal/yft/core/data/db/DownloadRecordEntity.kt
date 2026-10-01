package com.alal.yft.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_records")
data class DownloadRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "progress_percent")
    val progressPercent: Int,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String? = null,
    @ColumnInfo(name = "plan_type", defaultValue = "'DIRECT'")
    val planType: String = "DIRECT",
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long? = null,
    @ColumnInfo(name = "downloaded_bytes", defaultValue = "0")
    val downloadedBytes: Long = 0,
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,
    @ColumnInfo(name = "destination_kind", defaultValue = "'APP_PRIVATE'")
    val destinationKind: String = "APP_PRIVATE",
    @ColumnInfo(name = "destination_uri")
    val destinationUri: String? = null,
    @ColumnInfo(name = "entity_tag")
    val entityTag: String? = null,
    @ColumnInfo(name = "last_modified")
    val lastModified: String? = null,
    @ColumnInfo(name = "preferred_segment_count", defaultValue = "4")
    val preferredSegmentCount: Int = 4,
    @ColumnInfo(name = "requires_link_refresh", defaultValue = "1")
    val requiresLinkRefresh: Boolean = true,
    @ColumnInfo(name = "updated_at_epoch_ms", defaultValue = "0")
    val updatedAtEpochMs: Long = createdAtEpochMs,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(progressPercent in 0..100)
        require(totalBytes == null || totalBytes >= 0)
        require(downloadedBytes >= 0)
        require(totalBytes == null || downloadedBytes <= totalBytes)
        require(preferredSegmentCount in 1..32)
    }

    override fun toString(): String = buildString {
        append("DownloadRecordEntity(id=")
        append(id)
        append(", displayName=")
        append(displayName)
        append(", status=")
        append(status)
        append(", progressPercent=")
        append(progressPercent)
        append(", planType=")
        append(planType)
        append(", totalBytes=")
        append(totalBytes)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(", destinationKind=")
        append(destinationKind)
        append(", destinationUriPresent=")
        append(!destinationUri.isNullOrBlank())
        append(", validatorPresent=")
        append(!entityTag.isNullOrBlank() || !lastModified.isNullOrBlank())
        append(", requiresLinkRefresh=")
        append(requiresLinkRefresh)
        append(')')
    }
}
