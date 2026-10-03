package com.alal.yft.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.alal.yft.core.data.db.AppDatabase
import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.data.logging.AndroidAppLogger
import com.alal.yft.core.data.network.NetworkConfiguration
import com.alal.yft.core.data.preferences.DataStoreDownloadPreferencesRepository
import com.alal.yft.core.data.preferences.DataStoreSettingsRepository
import com.alal.yft.core.data.preferences.DataStoreHomeSitesRepository
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.logging.AppLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDownloadPreferencesRepository(
        implementation: DataStoreDownloadPreferencesRepository,
    ): DownloadPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindHomeSitesRepository(
        implementation: DataStoreHomeSitesRepository,
    ): HomeSitesRepository

    @Binds
    @Singleton
    abstract fun bindAppLogger(implementation: AndroidAppLogger): AppLogger
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    fun provideDownloadRecordDao(database: AppDatabase): DownloadRecordDao =
        database.downloadRecordDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings.preferences_pb") },
        )

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetworkConfiguration.createClient()
}
