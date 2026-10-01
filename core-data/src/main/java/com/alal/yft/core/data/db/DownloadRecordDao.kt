package com.alal.yft.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DownloadRecordDao {
    @Query("SELECT * FROM download_records ORDER BY created_at_epoch_ms DESC")
    abstract fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Transaction
    @Query("SELECT * FROM download_records ORDER BY created_at_epoch_ms DESC")
    abstract fun observeAllWithSegments(): Flow<List<DownloadRecordWithSegments>>

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): DownloadRecordEntity?

    @Transaction
    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    abstract suspend fun findWithSegments(id: String): DownloadRecordWithSegments?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(record: DownloadRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSegments(segments: List<DownloadSegmentEntity>)

    @Query("DELETE FROM download_segments WHERE download_id = :downloadId")
    protected abstract suspend fun deleteSegments(downloadId: String)

    @Transaction
    open suspend fun replaceCheckpoint(
        record: DownloadRecordEntity,
        segments: List<DownloadSegmentEntity>,
    ) {
        require(segments.all { it.downloadId == record.id })
        require(segments.map(DownloadSegmentEntity::segmentIndex).distinct().size == segments.size)
        upsert(record)
        deleteSegments(record.id)
        if (segments.isNotEmpty()) upsertSegments(segments)
    }

    @Query("DELETE FROM download_records WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM download_records")
    abstract suspend fun count(): Int
}
