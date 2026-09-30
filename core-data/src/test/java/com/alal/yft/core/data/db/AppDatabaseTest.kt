package com.alal.yft.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}