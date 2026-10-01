package com.alal.yft.core.download

import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.data.db.DownloadRecordEntity
import com.alal.yft.core.data.db.DownloadRecordWithSegments
import com.alal.yft.core.data.db.DownloadSegmentEntity
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus

enum class DownloadDestinationKind {
    APP_PRIVATE,
    MEDIA_STORE,
    SAF_DOCUMENT,
}

data class StoredDownloadTask(
    val id: String,
    val displayName: String,
    val status: DownloadTaskStatus,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val mimeType: String?,
    val destinationKind: DownloadDestinationKind,
    val destinationUri: String?,
    val preferredSegmentCount: Int,
    val requiresLinkRefresh: Boolean,
    val failureReason: DownloadFailureReason?,
    val checkpoint: DirectTransferCheckpoint,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(totalBytes == null || totalBytes >= 0)
        require(downloadedBytes >= 0)
        require(totalBytes == null || downloadedBytes <= totalBytes)
        require(preferredSegmentCount in 1..32)
        require(checkpoint.downloadedBytes == downloadedBytes)
    }

    val progressPercent: Int
        get() = when {
            status == DownloadTaskStatus.COMPLETED -> 100
            totalBytes == null || totalBytes == 0L -> 0
            else -> ((downloadedBytes * 100.0) / totalBytes)
                .toInt()
                .coerceIn(0, 99)
        }

    override fun toString(): String = buildString {
        append("StoredDownloadTask(id=")
        append(id)
        append(", displayName=")
        append(displayName)
        append(", status=")
        append(status)
        append(", totalBytes=")
        append(totalBytes)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(", destinationKind=")
        append(destinationKind)
        append(", destinationUriPresent=")
        append(!destinationUri.isNullOrBlank())
        append(", requiresLinkRefresh=")
        append(requiresLinkRefresh)
        append(", failureReason=")
        append(failureReason)
        append(')')
    }
}

interface DownloadTaskStore {
    suspend fun loadAll(): List<StoredDownloadTask>
    suspend fun save(task: StoredDownloadTask)
    suspend fun delete(id: String)
}

class RoomDownloadTaskStore(
    private val dao: DownloadRecordDao,
) : DownloadTaskStore {
    override suspend fun loadAll(): List<StoredDownloadTask> =
        dao.loadAllWithSegments().map { it.toStoredTask() }

    override suspend fun save(task: StoredDownloadTask) {
        dao.replaceCheckpoint(
            record = task.toEntity(),
            segments = task.checkpoint.segments.map { segment ->
                DownloadSegmentEntity(
                    downloadId = task.id,
                    segmentIndex = segment.index,
                    startByte = segment.startByte,
                    endByteInclusive = segment.endByteInclusive,
                    downloadedBytes = segment.downloadedBytes,
                )
            },
        )
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private fun DownloadRecordWithSegments.toStoredTask(): StoredDownloadTask {
        val orderedSegments = segments
            .sortedBy(DownloadSegmentEntity::segmentIndex)
            .map { segment ->
                DownloadSegment(
                    index = segment.segmentIndex,
                    startByte = segment.startByte,
                    endByteInclusive = segment.endByteInclusive,
                    downloadedBytes = segment.downloadedBytes,
                )
            }
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = record.totalBytes,
            entityTag = record.entityTag,
            lastModified = record.lastModified,
            segments = orderedSegments,
        )
        return StoredDownloadTask(
            id = record.id,
            displayName = record.displayName,
            status = enumValueOrDefault(record.status, DownloadTaskStatus.FAILED),
            totalBytes = record.totalBytes,
            downloadedBytes = checkpoint.downloadedBytes,
            mimeType = record.mimeType,
            destinationKind = enumValueOrDefault(
                record.destinationKind,
                DownloadDestinationKind.APP_PRIVATE,
            ),
            destinationUri = record.destinationUri,
            preferredSegmentCount = record.preferredSegmentCount.coerceIn(1, 32),
            requiresLinkRefresh = record.requiresLinkRefresh,
            failureReason = record.lastErrorCode?.let {
                enumValueOrDefault(it, DownloadFailureReason.HTTP_STATUS)
            },
            checkpoint = checkpoint,
            createdAtEpochMs = record.createdAtEpochMs,
            updatedAtEpochMs = record.updatedAtEpochMs,
        )
    }

    private fun StoredDownloadTask.toEntity(): DownloadRecordEntity =
        DownloadRecordEntity(
            id = id,
            displayName = displayName,
            status = status.name,
            progressPercent = progressPercent,
            createdAtEpochMs = createdAtEpochMs,
            lastErrorCode = failureReason?.name,
            planType = "DIRECT",
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            mimeType = mimeType,
            destinationKind = destinationKind.name,
            destinationUri = destinationUri,
            entityTag = checkpoint.entityTag,
            lastModified = checkpoint.lastModified,
            preferredSegmentCount = preferredSegmentCount,
            requiresLinkRefresh = requiresLinkRefresh,
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback
}