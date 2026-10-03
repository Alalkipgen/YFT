package com.alal.yft.feature.library

import android.content.Context
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
    fun provideLibraryRepository(@ApplicationContext context: Context): LibraryRepository =
        AndroidLibraryRepository(context)

    @Provides
    @Singleton
    fun provideMediaDetailsSource(source: RetrieverMediaDetailsSource): MediaDetailsSource = source

    @Provides
    @Singleton
    fun provideLibraryPlayback(playback: ExoLibraryPlayback): LibraryPlayback = playback
}
