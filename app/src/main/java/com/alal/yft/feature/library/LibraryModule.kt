package com.alal.yft.feature.library

import android.content.Context
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.download.SaveFolders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideLibraryRepository(
        @ApplicationContext context: Context,
        fileDeleter: DownloadedFileDeleter,
        preferences: DownloadPreferencesRepository,
        folders: SaveFolders,
    ): LibraryRepository = AndroidLibraryRepository(
        context = context,
        fileDeleter = fileDeleter,
        // v1.0.2 item 28: the chosen folder is listed while new downloads go there.
        chosenFolder = {
            preferences.preferences.first()
                .takeIf { it.location == DownloadLocation.CHOSEN_FOLDER }
                ?.folder
        },
        folders = folders,
    )

    @Provides
    @Singleton
    fun provideMediaDetailsSource(source: RetrieverMediaDetailsSource): MediaDetailsSource = source

    @Provides
    @Singleton
    fun provideLibraryPlayback(playback: ExoLibraryPlayback): LibraryPlayback = playback
}
