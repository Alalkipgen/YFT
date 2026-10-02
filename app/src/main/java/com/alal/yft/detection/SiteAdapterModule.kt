package com.alal.yft.detection

import android.content.Context
import com.alal.yft.BuildConfig
import com.alal.yft.detection.script.WebViewSolverEngine
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.vimeo.VimeoExtractor
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Wires the site adapters that ship in this build.
 *
 * Adapters are listed explicitly rather than discovered, so shipping a new site is a reviewable
 * code change and no extractor code is ever loaded from a remote source.
 */
@Module
@InstallIn(SingletonComponent::class)
object SiteAdapterModule {
    @Provides
    @Singleton
    fun provideExtractorHttpClient(client: OkHttpClient): ExtractorHttpClient =
        OkHttpExtractorClient(client)

    @Provides
    @Singleton
    fun provideSiteAdapterFlags(): SiteAdapterFlags = SiteAdapterFlags { adapterId ->
        adapterId != YOUTUBE_ADAPTER_ID || BuildConfig.YOUTUBE_ADAPTER_ENABLED
    }

    /**
     * The YouTube player-script host.
     *
     * Player scripts are larger than adapter pages, so they get their own client with a longer
     * call timeout; every other transport rule is the same.
     */
    @Provides
    @Singleton
    fun providePlayerScriptRunner(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): PlayerScriptRunner = YouTubePlayerScriptRunner(
        http = OkHttpExtractorClient(
            client = client,
            policy = OkHttpExtractorClient.Policy(
                callTimeoutSeconds = PLAYER_FETCH_TIMEOUT_SECONDS,
            ),
        ),
        engine = WebViewSolverEngine(context),
    )

    @Provides
    @Singleton
    fun provideSiteExtractors(
        http: ExtractorHttpClient,
        playerScripts: PlayerScriptRunner,
    ): List<SiteExtractor> = listOf(
        TikTokExtractor(http),
        FacebookExtractor(http),
        VimeoExtractor(http),
        YouTubeExtractor(http, playerScripts),
    )

    @Provides
    @Singleton
    fun provideSiteExtractorRegistry(
        extractors: List<@JvmSuppressWildcards SiteExtractor>,
        flags: SiteAdapterFlags,
    ): SiteExtractorRegistry = SiteExtractorRegistry(extractors, flags)

    private const val YOUTUBE_ADAPTER_ID = "youtube"
    private const val PLAYER_FETCH_TIMEOUT_SECONDS = 45L
}
