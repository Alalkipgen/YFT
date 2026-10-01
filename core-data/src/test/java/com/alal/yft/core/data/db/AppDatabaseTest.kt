package com.alal.yft.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun baselineDaoPersistsAndReadsRecord() = runTest {
        val record = DownloadRecordEntity(
            id = "record-1",
            displayName = "Sample",
            status = "QUEUED",
            progressPercent = 0,
            createdAtEpochMs = 1000,
        )

        database.downloadRecordDao().upsert(record)

        assertEquals(1, database.downloadRecordDao().count())
        assertEquals(record, database.downloadRecordDao().findById(record.id))
        assertNull(database.downloadRecordDao().findById("missing"))
    }

    @Test
    fun checkpointReplacementIsAtomicAndRemovesStaleSegments() = runTest {
        val record = DownloadRecordEntity(
            id = "record-checkpoint",
            displayName = "Sample",
            status = "PAUSED",
            progressPercent = 25,
            createdAtEpochMs = 1_000,
            totalBytes = 100,
            downloadedBytes = 25,
            entityTag = "\"fixture\"",
            requiresLinkRefresh = true,
        )
        val initial = listOf(
            DownloadSegmentEntity(
                downloadId = record.id,
                segmentIndex = 0,
                startByte = 0,
                endByteInclusive = 49,
                downloadedBytes = 25,
            ),
            DownloadSegmentEntity(
                downloadId = record.id,
                segmentIndex = 1,
                startByte = 50,
                endByteInclusive = 99,
                downloadedBytes = 0,
            ),
        )
        val dao = database.downloadRecordDao()

        dao.replaceCheckpoint(record, initial)
        dao.replaceCheckpoint(
            record = record.copy(
                progressPercent = 75,
                downloadedBytes = 75,
                updatedAtEpochMs = 2_000,
            ),
            segments = listOf(
                initial[0].copy(downloadedBytes = 50),
                initial[1].copy(downloadedBytes = 25),
            ),
        )

        val restored = requireNotNull(dao.findWithSegments(record.id))
        assertEquals(75L, restored.record.downloadedBytes)
        assertEquals(listOf(50L, 25L), restored.segments.sortedBy { it.segmentIndex }.map { it.downloadedBytes })

        dao.replaceCheckpoint(
            record = restored.record.copy(
                status = "NEEDS_REFRESH",
                downloadedBytes = 0,
                progressPercent = 0,
            ),
            segments = emptyList(),
        )
        assertTrue(requireNotNull(dao.findWithSegments(record.id)).segments.isEmpty())
    }

    @Test
    fun deletingRecordCascadesSegmentCheckpoints() = runTest {
        val dao = database.downloadRecordDao()
        val record = DownloadRecordEntity(
            id = "record-delete",
            displayName = "Delete",
            status = "PAUSED",
            progressPercent = 0,
            createdAtEpochMs = 1_000,
        )
        dao.replaceCheckpoint(
            record,
            listOf(DownloadSegmentEntity(record.id, 0, 0, 9, 0)),
        )

        dao.deleteById(record.id)

        assertNull(dao.findWithSegments(record.id))
        assertEquals(0, dao.count())
    }
}