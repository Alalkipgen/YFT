package com.alal.yft.feature.settings

import android.content.Context
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideBrowsingDataCleaner(
        @ApplicationContext context: Context,
        detectedMedia: DetectedMediaStore,
        previewSelection: PreviewSelectionStore,
    ): BrowsingDataCleaner = CompositeBrowsingDataCleaner(
        listOf(
            WebViewBrowsingDataCleaner(context),
            SessionMediaCleaner(detectedMedia, previewSelection),
        ),
    )

    @Provides
    @Singleton
    fun provideDownloadHistory(queue: DownloadQueue): DownloadHistory = QueueDownloadHistory(queue)
}
