package com.alal.yft.download

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The deleter behind "Delete file" in Downloads and Delete in the Library (P42). */
@Module
@InstallIn(SingletonComponent::class)
object DownloadFileModule {
    @Provides
    @Singleton
    fun provideDownloadedFileDeleter(@ApplicationContext context: Context): DownloadedFileDeleter =
        AndroidDownloadedFileDeleter(AndroidFileDeleteSystem(context))
}
