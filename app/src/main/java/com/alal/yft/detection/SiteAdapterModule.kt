package com.alal.yft.detection

import android.content.Context
import android.webkit.WebSettings
import com.alal.yft.BuildConfig
import com.alal.yft.core.browser.policy.BrowserUserAgent
import com.alal.yft.detection.potoken.BotGuardPoTokenProvider
import com.alal.yft.detection.script.WebViewSolverEngine
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.detection.tiktok.TikTokPageEngine
import com.alal.yft.detection.tiktok.TikTokPageScript
import com.alal.yft.detection.tiktok.TikTokPageSettings
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.PoTokenProvider
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import com.alal.yft.extractor.master.solver.OwnSolverSelection
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokAgents
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.vimeo.VimeoExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     * call timeout; every other transport rule is the same. Master Phase 1.1 S5: with both
     * opt-in flags on (`-Pyft.ownSolver`, `-Pyft.masterCapture`) Master's own solver runs instead
     * of the bundled ejs solver; otherwise this is main's runner, built exactly as on main.
     */
    @Provides
    @Singleton
    fun providePlayerScriptRunner(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): PlayerScriptRunner {
        val http = OkHttpExtractorClient(
            client = client,
            policy = OkHttpExtractorClient.Policy(
                callTimeoutSeconds = PLAYER_FETCH_TIMEOUT_SECONDS,
            ),
        )
        return OwnSolverSelection.runner(
            ownSolver = BuildConfig.OWN_SOLVER_ENABLED,
            masterCapture = BuildConfig.MASTER_CAPTURE_ENABLED,
            own = { OwnPlayerScriptRunner(http = http, engine = WebViewOwnSolverEngine(context)) },
            main = { YouTubePlayerScriptRunner(http = http, engine = WebViewSolverEngine(context)) },
        )
    }

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
        @ApplicationContext context: Context,
        client: OkHttpClient,
        http: ExtractorHttpClient,
        playerScripts: PlayerScriptRunner,
        poTokens: PoTokenProvider,
    ): List<SiteExtractor> = listOf(
        // P39 (TT_AGENT=CHROME, TT_HOME_COOKIES=ON): TikTok's pages are asked with the WebView's
        // own agent and desktop Chrome, and a short link's redirect cookies reach its page.
        TikTokExtractor(
            http = OkHttpExtractorClient(
                client = client,
                policy = OkHttpExtractorClient.Policy(sendResponseCookies = true),
            ),
            agents = TikTokAgents(webViewAgent = WebViewAgent(context)::get),
            // P46 (owner's choice B, TT_SERVICE=ON): the public download service when
            // TikTok's own pages give no file; only the post's address goes there.
            askDownloadService = true,
        ),
        FacebookExtractor(http),
        VimeoExtractor(http),
        YouTubeExtractor(http, playerScripts, poTokens),
        // P48: X's public embed answer for a post's video (whole MP4 files with sound).
        XExtractor(http),
        // P49: Instagram's own answers (app API when signed in, GraphQL, page, embed page).
        InstagramExtractor(http),
    )

    @Provides
    @Singleton
    fun provideSiteExtractorRegistry(
        extractors: List<@JvmSuppressWildcards SiteExtractor>,
        flags: SiteAdapterFlags,
    ): SiteExtractorRegistry = SiteExtractorRegistry(extractors, flags)

    /** P40: the owner's answers G3 and G5 (`TT_HOME_COOKIES=ON`, `TT_HIDDEN_PAGE=ON`). */
    @Provides
    @Singleton
    fun provideTikTokPageSettings(): TikTokPageSettings = TikTokPageSettings.OWNER

    /** P40 step 4: TikTok's own page in a hidden WebView, for a link no tab shows. */
    @Provides
    @Singleton
    fun provideHiddenPageReader(
        @ApplicationContext context: Context,
        settings: TikTokPageSettings,
        registry: SiteExtractorRegistry,
    ): HiddenPageReader = TikTokPageEngine(
        context = context,
        settings = settings,
        isMedia = { url ->
            registry.enabled(TikTokPageScript.SITE_ID)?.isPlayerMediaRequest(url) == true
        },
    )

    private const val YOUTUBE_ADAPTER_ID = "youtube"
    private const val PLAYER_FETCH_TIMEOUT_SECONDS = 60L
}

/** The WebView's own user agent (the tab's, without the WebView marks), read once on Main. */
private class WebViewAgent(private val context: Context) {
    @Volatile
    private var cached: String? = null

    suspend fun get(): String? = cached ?: withContext(Dispatchers.Main) {
        runCatching { BrowserUserAgent.from(WebSettings.getDefaultUserAgent(context)) }
            .getOrNull()
            ?.also { cached = it }
    }
}
