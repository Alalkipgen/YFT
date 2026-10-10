package com.alal.yft.feature.library

import android.content.Context
import com.alal.yft.download.DownloadedFileDeleter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideLibraryRepository(
        @ApplicationContext context: Context,
        fileDeleter: DownloadedFileDeleter,
    ): LibraryRepository = AndroidLibraryRepository(context, fileDeleter = fileDeleter)

    @Provides
    @Singleton
    fun provideMediaDetailsSource(source: RetrieverMediaDetailsSource): MediaDetailsSource = source

    @Provides
    @Singleton
    fun provideLibraryPlayback(playback: ExoLibraryPlayback): LibraryPlayback = playback
}
