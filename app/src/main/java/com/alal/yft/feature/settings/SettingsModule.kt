package com.alal.yft.feature.settings

import android.content.Context
import com.alal.yft.core.data.history.BrowserHistoryRepository
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.download.DownloadSources
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
        lookups: SiteLookupCache,
        browserHistory: BrowserHistoryRepository,
        downloadSources: DownloadSources,
    ): BrowsingDataCleaner = CompositeBrowsingDataCleaner(
        listOf(
            WebViewBrowsingDataCleaner(context),
            SessionMediaCleaner(detectedMedia, previewSelection, lookups, downloadSources),
            BrowserHistoryCleaner(browserHistory),
        ),
    )

    @Provides
    @Singleton
    fun provideDownloadHistory(queue: DownloadQueue): DownloadHistory = QueueDownloadHistory(queue)

    /** Item 7 Track B3: YouTube's cookies, site storage and remembered lookups only. */
    @Provides
    @Singleton
    fun provideYouTubeSessionReset(lookups: SiteLookupCache): YouTubeSessionReset =
        CookieYouTubeSessionReset(WebViewSiteCookieJar, WebViewSiteStorage, lookups)
}
