package com.alal.yft.detection

import android.content.Context
import android.webkit.WebSettings
import com.alal.yft.BuildConfig
import com.alal.yft.core.browser.policy.BrowserUserAgent
import com.alal.yft.detection.master.BackupFinder
import com.alal.yft.detection.master.BackupFinderPlayerScripts
import com.alal.yft.detection.master.SocialLadder
import com.alal.yft.detection.master.VkTrialBreak
import com.alal.yft.detection.master.YouTubeRouteB
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
import com.alal.yft.extractor.master.android.solver.OwnSolverAppBreaker
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.android.solver.WebViewPageSolverEngine
import com.alal.yft.extractor.master.modules.youtube.YouTubeSignatureGuard
import com.alal.yft.extractor.master.solver.AtomicTextFile
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import com.alal.yft.extractor.master.solver.OwnSolverLocators
import com.alal.yft.extractor.master.solver.OwnSolverVLedger
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
import java.io.File
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
    /**
     * The client the site adapters share. Phase 1.4 item 7 (YouTube routing B) R1: in a route-B
     * build (`-Pyft.youtubeRouteB`, debug only) it records each lookup's requests; otherwise it is
     * the plain client. Social plan S1a (2): in a social-ladder build (`-Pyft.socialLadder`, debug
     * only) it records them and keeps main's pages too ([SocialLadder.mainClient]); flag off: as
     * before.
     */
    @Provides
    @Singleton
    fun provideExtractorHttpClient(client: OkHttpClient): ExtractorHttpClient =
        SocialLadder.mainClient(client)

    @Provides
    @Singleton
    fun provideMergeSupport(): MergeSupport = DeviceMergeSupport()

    @Provides
    @Singleton
    fun provideVideoPlaybackSupport(): VideoPlaybackSupport = DeviceVideoPlaybackSupport()

    /** Decision 30 (i): Settings' "Backup finder", one per process. */
    @Provides
    @Singleton
    fun provideBackupFinder(@ApplicationContext context: Context): BackupFinder = BackupFinder.get(context)

    /** VK-Master wave 2 (agent D, item 7): the trial's "VK value break", one per process (off elsewhere). */
    @Provides
    @Singleton
    fun provideVkTrialBreak(@ApplicationContext context: Context): VkTrialBreak = VkTrialBreak.get(context)

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
     * of the bundled ejs solver; otherwise this is main's runner, built exactly as on main. Phase
     * 1.4 item 7 (YouTube routing B): in a route-B build (`-Pyft.youtubeRouteB`, debug only) the
     * player client follows route B's rules in a route-B run and marks a 429 in the lookup's record
     * (`YouTubeRouteB.scriptClient`); otherwise it is the plain client. Decision 30 (i): with the
     * own solver, Settings' Backup finder chooses at each request — on, the own solver; off,
     * main's runner exactly as on main ([BackupFinderPlayerScripts]).
     */
    @Provides
    @Singleton
    fun providePlayerScriptRunner(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        backupFinder: BackupFinder,
    ): PlayerScriptRunner {
        val http = YouTubeRouteB.scriptClient(
            OkHttpExtractorClient(
                client = client,
                policy = OkHttpExtractorClient.Policy(
                    callTimeoutSeconds = PLAYER_FETCH_TIMEOUT_SECONDS,
                ),
            ),
        )
        val main = { YouTubePlayerScriptRunner(http = http, engine = WebViewSolverEngine(context)) }
        return OwnSolverSelection.runner(
            ownSolver = BuildConfig.OWN_SOLVER_ENABLED,
            masterCapture = BuildConfig.MASTER_CAPTURE_ENABLED,
            own = {
                BackupFinderPlayerScripts(
                    on = backupFinder::isOn,
                    own = {
                        OwnPlayerScriptRunner(
                            http = http,
                            engine = WebViewOwnSolverEngine(context),
                            // Phase 1.2 N5b: the ladder only with -Pyft.ownSolverNext (debug builds). A.4/A.5:
                            // the locator cache and the V ledger in app-private files (data only; each
                            // file is found on first use, off this thread).
                            next = if (BuildConfig.OWN_SOLVER_NEXT_ENABLED) {
                                OwnPlayerScriptRunner.Next(
                                    page = WebViewPageSolverEngine(context),
                                    // N7: the breaker the debug build's owner canary shares (one per process).
                                    breaker = OwnSolverAppBreaker.get(context),
                                    locators = OwnSolverLocators(
                                        AtomicTextFile { File(context.filesDir, OWN_SOLVER_LOCATORS_FILE) },
                                    ),
                                    ledger = OwnSolverVLedger(
                                        AtomicTextFile { File(context.filesDir, OWN_SOLVER_V_LEDGER_FILE) },
                                    ),
                                )
                            } else {
                                null
                            },
                        )
                    },
                    main = main,
                )
            },
            main = main,
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
        // Social plan S2b: in a social-ladder build its asks and pages are kept in the lookup
        // (TikTok's ladder reads them, T2); flag off: today's client.
        TikTokExtractor(
            http = SocialLadder.tiktokClient(client),
            agents = TikTokAgents(webViewAgent = WebViewAgent(context)::get),
            // Decision 22 (the owner, 2026-10-22; P46's choice B reversed, TT_SERVICE=OFF): the
            // public download service (tikwm.com) is never asked, flag off and on alike; a post
            // TikTok's own pages and the hidden page give no file fails with TikTok's own reason.
            // Back on only on the owner's go (MASTER_KEY_SOCIAL_PLAN.md section 8, decision 22).
            askDownloadService = false,
        ),
        FacebookExtractor(http),
        VimeoExtractor(http),
        // Phase 1.2 N5b A.8: with the own solver (ownSolver, with or without ownSolverNext) its
        // signatures are used only after the live check V; main's runner gets the reader as is.
        YouTubeSignatureGuard.guard(http, playerScripts) { runner ->
            YouTubeExtractor(http, runner, poTokens)
        },
        // P48: X's public embed answer for a post's video (whole MP4 files with sound).
        XExtractor(http),
        // P49: Instagram's own answers (app API when signed in, GraphQL, page, embed page).
        InstagramExtractor(http),
        // VK-Master wave 2 (agent D, item 7): in #12 trial only, main asks with the value the
        // trial's VK value break chose broken (before main's record); every other build: http.
        com.alal.yft.extractor.sites.vk.VkExtractor(VkTrialBreak.client(context, http)),
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

    /** N5b A.4/A.5 (ownSolverNext only), under `filesDir`: data only, never code. */
    private const val OWN_SOLVER_LOCATORS_FILE = "own-solver/locators.json"
    private const val OWN_SOLVER_V_LEDGER_FILE = "own-solver/v-ledger.json"
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
