package com.alal.yft.core.download

import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.data.db.DownloadRecordEntity
import com.alal.yft.core.data.db.DownloadRecordWithSegments
import com.alal.yft.core.data.db.DownloadSegmentEntity
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P21: the failure details of a task are kept in `last_error_detail` across restarts. */
class RoomDownloadTaskStoreTest {
    private val dao = FakeDao()

    @Test
    fun aFailureKeepsItsStageAndDetailAfterARestart() = runBlocking {
        val failure = DownloadFailure(
            reason = DownloadFailureReason.STORAGE_UNAVAILABLE,
            stage = DownloadFailureStage.WRITE_FILE,
            detail = "IOException: EIO (I/O error)",
        )
        val task = task(failureReason = failure.reason, failure = failure)

        RoomDownloadTaskStore(dao).save(task)

        assertEquals(
            "WRITE_FILE||IOException: EIO (I/O error)",
            dao.records.getValue(ID).lastErrorDetail,
        )
        val restored = RoomDownloadTaskStore(dao).loadAll().single()
        assertEquals(failure, restored.failure)
        assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, restored.failureReason)
        assertEquals(task.checkpoint, restored.checkpoint)
    }

    @Test
    fun aTaskWithoutAFailureStoresNoDetail() = runBlocking {
        RoomDownloadTaskStore(dao).save(task(failureReason = null, failure = null))

        assertNull(dao.records.getValue(ID).lastErrorDetail)
        assertNull(RoomDownloadTaskStore(dao).loadAll().single().failure)
    }

    @Test
    fun detailsOfAnotherReasonAreNotStored() = runBlocking {
        val stale = DownloadFailure(
            reason = DownloadFailureReason.NETWORK,
            stage = DownloadFailureStage.READ_SOURCE,
        )

        RoomDownloadTaskStore(dao).save(
            task(failureReason = DownloadFailureReason.EXPIRED_URL, failure = stale),
        )

        assertNull(dao.records.getValue(ID).lastErrorDetail)
        val restored = RoomDownloadTaskStore(dao).loadAll().single()
        assertEquals(DownloadFailureReason.EXPIRED_URL, restored.failureReason)
        assertNull(restored.failure)
    }

    private fun task(
        failureReason: DownloadFailureReason?,
        failure: DownloadFailure?,
    ) = StoredDownloadTask(
        id = ID,
        displayName = "clip.mp4",
        status = DownloadTaskStatus.FAILED,
        totalBytes = 10,
        downloadedBytes = 4,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = "content://media/pending/1",
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = failureReason,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = 10,
            entityTag = null,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, 9, downloadedBytes = 4)),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
        failure = failure,
    )

    /** Keeps rows in memory, like the Room DAO does in its database. */
    private class FakeDao : DownloadRecordDao() {
        val records = linkedMapOf<String, DownloadRecordEntity>()
        private val segments = mutableMapOf<String, List<DownloadSegmentEntity>>()

        override fun observeAll(): Flow<List<DownloadRecordEntity>> =
            flowOf(records.values.toList())

        override fun observeAllWithSegments(): Flow<List<DownloadRecordWithSegments>> =
            flowOf(records.values.map(::withSegments))

        override suspend fun loadAllWithSegments(): List<DownloadRecordWithSegments> =
            records.values.map(::withSegments)

        override suspend fun findById(id: String): DownloadRecordEntity? = records[id]

        override suspend fun findWithSegments(id: String): DownloadRecordWithSegments? =
            records[id]?.let(::withSegments)

        override suspend fun upsert(record: DownloadRecordEntity) {
            records[record.id] = record
        }

        override suspend fun upsertSegments(segments: List<DownloadSegmentEntity>) {
            segments.groupBy(DownloadSegmentEntity::downloadId).forEach { (id, rows) ->
                this.segments[id] = rows
            }
        }

        override suspend fun deleteSegments(downloadId: String) {
            segments.remove(downloadId)
        }

        override suspend fun deleteById(id: String) {
            records.remove(id)
            segments.remove(id)
        }

        override suspend fun count(): Int = records.size

        private fun withSegments(record: DownloadRecordEntity) =
            DownloadRecordWithSegments(record, segments[record.id].orEmpty())
    }

    private companion object {
        const val ID = "task-1"
    }
}
