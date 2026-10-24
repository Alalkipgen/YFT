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
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.StreamChunkCheckpoint
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

    /** D1 follow-up 3: a remuxed HLS download keeps its MP4's size after a restart. */
    @Test
    fun aCompletedHlsTaskKeepsThePublishedFileSizeAfterARestart() = runBlocking {
        val task = hlsTask(DownloadTaskStatus.COMPLETED, shownBytes = 8)

        RoomDownloadTaskStore(dao).save(task)

        assertEquals(8L, dao.records.getValue(ID).totalBytes)
        assertEquals(8L, dao.records.getValue(ID).downloadedBytes)
        val restored = RoomDownloadTaskStore(dao).loadAll().single()
        assertEquals(task, restored)
        assertEquals(10L, restored.checkpoint.downloadedBytes)
    }

    /** D1 follow-up 3: a task that is not a completed HLS one shows its checkpoint's bytes. */
    @Test
    fun anUnfinishedHlsTaskStillShowsItsCheckpointBytesAfterARestart() = runBlocking {
        RoomDownloadTaskStore(dao).save(hlsTask(DownloadTaskStatus.COMPLETED, shownBytes = 8))
        dao.upsert(dao.records.getValue(ID).copy(status = DownloadTaskStatus.FAILED.name))

        val restored = RoomDownloadTaskStore(dao).loadAll().single()

        assertEquals(DownloadTaskStatus.FAILED, restored.status)
        assertEquals(10L, restored.downloadedBytes)
        assertNull(restored.totalBytes)
    }

    /** D1 follow-up 3: a completed HLS task saved before keeps the pieces' size it showed. */
    @Test
    fun aCompletedHlsTaskSavedBeforeKeepsItsSize() = runBlocking {
        RoomDownloadTaskStore(dao).save(hlsTask(DownloadTaskStatus.COMPLETED, shownBytes = 10))

        val restored = RoomDownloadTaskStore(dao).loadAll().single()

        assertEquals(10L, restored.downloadedBytes)
        assertEquals(10L, restored.totalBytes)
    }

    private fun hlsTask(status: DownloadTaskStatus, shownBytes: Long) = StoredDownloadTask(
        id = ID,
        displayName = "clip.mp4",
        status = status,
        planType = DownloadPlanType.HLS,
        totalBytes = shownBytes,
        downloadedBytes = shownBytes,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = "content://media/external/video/media/1",
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        // The pieces: 10 bytes.
        checkpoint = HlsTransferCheckpoint(
            manifestFingerprint = "a".repeat(64),
            chunks = listOf(
                StreamChunkCheckpoint(0, 6, completed = true),
                StreamChunkCheckpoint(1, 4, completed = true),
            ),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
    )

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
