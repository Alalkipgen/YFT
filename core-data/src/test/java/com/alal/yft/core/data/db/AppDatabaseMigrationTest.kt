package com.alal.yft.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFrom1To2PreservesRowsAndAddsNullableErrorCode() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id, display_name, status, progress_percent, created_at_epoch_ms
                ) VALUES ('record-1', 'Sample', 'QUEUED', 25, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT id, display_name, progress_percent, last_error_code FROM download_records",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("record-1", cursor.getString(0))
            assertEquals("Sample", cursor.getString(1))
            assertEquals(25, cursor.getInt(2))
            assertNull(cursor.getString(3))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
