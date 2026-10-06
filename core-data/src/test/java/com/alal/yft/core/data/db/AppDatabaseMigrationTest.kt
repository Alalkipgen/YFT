package com.alal.yft.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun migrationFrom2To3PreservesRowsAndMakesInterruptedWorkRefreshable() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id,
                    display_name,
                    status,
                    progress_percent,
                    created_at_epoch_ms,
                    last_error_code
                ) VALUES ('record-2', 'Interrupted', 'RUNNING', 40, 2000, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        )

        migrated.query(
            """
            SELECT
                id,
                status,
                plan_type,
                downloaded_bytes,
                destination_kind,
                preferred_segment_count,
                requires_link_refresh,
                updated_at_epoch_ms
            FROM download_records
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("record-2", cursor.getString(0))
            assertEquals("NEEDS_REFRESH", cursor.getString(1))
            assertEquals("DIRECT", cursor.getString(2))
            assertEquals(0L, cursor.getLong(3))
            assertEquals("APP_PRIVATE", cursor.getString(4))
            assertEquals(4, cursor.getInt(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals(0L, cursor.getLong(7))
        }
        migrated.execSQL(
            """
            INSERT INTO download_segments (
                download_id,
                segment_index,
                start_byte,
                end_byte_inclusive,
                downloaded_bytes
            ) VALUES ('record-2', 0, 0, 99, 25)
            """.trimIndent(),
        )
        migrated.query(
            "SELECT downloaded_bytes FROM download_segments WHERE download_id = 'record-2'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(25L, cursor.getLong(0))
        }
        migrated.close()
    }

    @Test
    fun migrationFrom3To4PreservesRowsAndAddsNullableCheckpointPayload() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id,
                    display_name,
                    status,
                    progress_percent,
                    created_at_epoch_ms,
                    plan_type,
                    downloaded_bytes
                ) VALUES ('record-stream', 'Stream', 'PAUSED', 40, 2500, 'HLS', 400)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            AppDatabase.MIGRATION_3_4,
        )

        migrated.query(
            """
            SELECT id, plan_type, downloaded_bytes, checkpoint_payload
            FROM download_records
            WHERE id = 'record-stream'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("record-stream", cursor.getString(0))
            assertEquals("HLS", cursor.getString(1))
            assertEquals(400L, cursor.getLong(2))
            assertNull(cursor.getString(3))
        }
        migrated.close()
    }

    @Test
    fun migrationFrom4To5PreservesRowsAndAddsNullableErrorDetail() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id,
                    display_name,
                    status,
                    progress_percent,
                    created_at_epoch_ms,
                    last_error_code,
                    plan_type,
                    downloaded_bytes,
                    checkpoint_payload
                ) VALUES (
                    'record-failed', 'Failed', 'FAILED', 30, 4000, 'NETWORK', 'HLS', 300,
                    'payload'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            AppDatabase.MIGRATION_4_5,
        )

        migrated.query(
            """
            SELECT
                id,
                status,
                last_error_code,
                plan_type,
                downloaded_bytes,
                checkpoint_payload,
                last_error_detail
            FROM download_records
            WHERE id = 'record-failed'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("record-failed", cursor.getString(0))
            assertEquals("FAILED", cursor.getString(1))
            assertEquals("NETWORK", cursor.getString(2))
            assertEquals("HLS", cursor.getString(3))
            assertEquals(300L, cursor.getLong(4))
            assertEquals("payload", cursor.getString(5))
            assertNull(cursor.getString(6))
        }
        migrated.execSQL(
            "UPDATE download_records SET last_error_detail = 'READ_SOURCE||IOException' " +
                "WHERE id = 'record-failed'",
        )
        migrated.query(
            "SELECT last_error_detail FROM download_records WHERE id = 'record-failed'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("READ_SOURCE||IOException", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrationFrom1To5ValidatesTheCompleteChain() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id, display_name, status, progress_percent, created_at_epoch_ms
                ) VALUES ('record-3', 'Queued', 'QUEUED', 0, 3000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
        )

        migrated.query(
            """
            SELECT status, last_error_code, checkpoint_payload, last_error_detail
            FROM download_records
            WHERE id = 'record-3'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("NEEDS_REFRESH", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
