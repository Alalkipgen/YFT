package com.alal.yft.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadRecordEntity::class,
        DownloadSegmentEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao

    companion object {
        const val VERSION = 3
        const val NAME = "yft.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN last_error_code TEXT DEFAULT NULL",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_records " +
                        "ADD COLUMN plan_type TEXT NOT NULL DEFAULT 'DIRECT'",
                )
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN total_bytes INTEGER DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE download_records " +
                        "ADD COLUMN downloaded_bytes INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN mime_type TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE download_records " +
                        "ADD COLUMN destination_kind TEXT NOT NULL DEFAULT 'APP_PRIVATE'",
                )
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN destination_uri TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN entity_tag TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN last_modified TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE download_records " +
                        "ADD COLUMN preferred_segment_count INTEGER NOT NULL DEFAULT 4",
                )
                database.execSQL(
                    "ALTER TABLE download_records " +
                        "ADD COLUMN requires_link_refresh INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    "ALTER TABLE download_records " +
                        "ADD COLUMN updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS download_segments (
                        download_id TEXT NOT NULL,
                        segment_index INTEGER NOT NULL,
                        start_byte INTEGER NOT NULL,
                        end_byte_inclusive INTEGER DEFAULT NULL,
                        downloaded_bytes INTEGER NOT NULL,
                        PRIMARY KEY(download_id, segment_index),
                        FOREIGN KEY(download_id) REFERENCES download_records(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_download_segments_download_id " +
                        "ON download_segments(download_id)",
                )
                database.execSQL(
                    """
                    UPDATE download_records
                    SET status = 'NEEDS_REFRESH'
                    WHERE status IN (
                        'QUEUED',
                        'PROBING',
                        'RUNNING',
                        'PAUSING',
                        'WAITING_FOR_NETWORK',
                        'VERIFYING'
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
