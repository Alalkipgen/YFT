package com.alal.yft.detection

import android.content.Context
import com.alal.yft.BuildConfig
import com.alal.yft.detection.potoken.BotGuardPoTokenProvider
import com.alal.yft.detection.script.WebViewSolverEngine
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.PoTokenProvider
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
    fun provideMergeSupport(): MergeSupport = DeviceMergeSupport()

    @Provides
    @Singleton
    fun provideVideoPlaybackSupport(): VideoPlaybackSupport = DeviceVideoPlaybackSupport()

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

    /**
     * The YouTube proof-of-origin host (ADR-006).
     *
     * It reads the player script for its attestation key, so it shares the player client's
     * longer call timeout.
     */
    @Provides
    @Singleton
    fun providePoTokenProvider(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): PoTokenProvider = BotGuardPoTokenProvider(
        context = context,
        client = client,
        http = OkHttpExtractorClient(
            client = client,
            policy = OkHttpExtractorClient.Policy(
                callTimeoutSeconds = PLAYER_FETCH_TIMEOUT_SECONDS,
            ),
        ),
    )

    @Provides
    @Singleton
    fun provideSiteExtractors(
        http: ExtractorHttpClient,
        playerScripts: PlayerScriptRunner,
        poTokens: PoTokenProvider,
    ): List<SiteExtractor> = listOf(
        TikTokExtractor(http),
        FacebookExtractor(http),
        VimeoExtractor(http),
        YouTubeExtractor(http, playerScripts, poTokens),
    )

    @Provides
    @Singleton
    fun provideSiteExtractorRegistry(
        extractors: List<@JvmSuppressWildcards SiteExtractor>,
        flags: SiteAdapterFlags,
    ): SiteExtractorRegistry = SiteExtractorRegistry(extractors, flags)

    private const val YOUTUBE_ADAPTER_ID = "youtube"
    private const val PLAYER_FETCH_TIMEOUT_SECONDS = 60L
}
