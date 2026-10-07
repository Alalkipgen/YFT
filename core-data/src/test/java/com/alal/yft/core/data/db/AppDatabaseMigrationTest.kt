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

    @Test
    fun migrationFrom5To6KeepsEveryDownloadRecordAndAddsAnEmptyBrowserHistory() {
        helper.createDatabase(TEST_DATABASE, 5).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id, display_name, status, progress_percent, created_at_epoch_ms,
                    last_error_code, plan_type, downloaded_bytes, checkpoint_payload,
                    last_error_detail
                ) VALUES
                    ('done', 'Done', 'COMPLETED', 100, 5000, NULL, 'DIRECT', 900, NULL, NULL),
                    ('paused', 'Paused', 'PAUSED', 40, 6000, NULL, 'HLS', 400, 'payload',
                        NULL),
                    ('failed', 'Failed', 'FAILED', 10, 7000, 'NETWORK', 'DIRECT', 100, NULL,
                        'READ_SOURCE|403|')
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO download_segments VALUES ('paused', 0, 0, 999, 400), " +
                    "('paused', 1, 1000, NULL, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            AppDatabase.MIGRATION_5_6,
        )

        migrated.query(
            """
            SELECT id, status, progress_percent, plan_type, downloaded_bytes, checkpoint_payload,
                last_error_code, last_error_detail
            FROM download_records ORDER BY created_at_epoch_ms
            """.trimIndent(),
        ).use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) {
                    val columns = 0 until cursor.columnCount
                    add(columns.joinToString("|") { cursor.getString(it) ?: "-" })
                }
            }
            assertEquals(
                listOf(
                    "done|COMPLETED|100|DIRECT|900|-|-|-",
                    "paused|PAUSED|40|HLS|400|payload|-|-",
                    "failed|FAILED|10|DIRECT|100|-|NETWORK|READ_SOURCE|403|",
                ),
                rows,
            )
        }
        migrated.query("SELECT COUNT(*) FROM download_segments WHERE download_id = 'paused'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        migrated.query("SELECT COUNT(*) FROM browser_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.execSQL(
            "INSERT INTO browser_history VALUES ('https://example.com/', 'Example', " +
                "'example.com', 8000, 1)",
        )
        migrated.query("SELECT title, visit_count FROM browser_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Example", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    @Test
    fun migrationFrom1To6ValidatesTheCompleteChain() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO download_records (
                    id, display_name, status, progress_percent, created_at_epoch_ms
                ) VALUES ('record-6', 'Old', 'COMPLETED', 100, 3000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
        )

        migrated.query("SELECT id, status FROM download_records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("record-6", cursor.getString(0))
            assertEquals("COMPLETED", cursor.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM browser_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
