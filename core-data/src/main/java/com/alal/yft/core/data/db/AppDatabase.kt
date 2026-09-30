package com.alal.yft.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadRecordEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao

    companion object {
        const val VERSION = 2
        const val NAME = "yft.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_records ADD COLUMN last_error_code TEXT DEFAULT NULL",
                )
            }
        }
    }
}
