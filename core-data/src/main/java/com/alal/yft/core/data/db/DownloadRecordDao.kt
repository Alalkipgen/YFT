package com.alal.yft.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM download_records ORDER BY created_at_epoch_ms DESC")
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DownloadRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecordEntity)

    @Query("SELECT COUNT(*) FROM download_records")
    suspend fun count(): Int
}
