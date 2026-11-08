package com.alal.yft.download

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.alal.yft.BuildConfig
import com.alal.yft.core.browser.detection.HeadlessPageFetcher
import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.download.AndroidMp3Transcoder
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxEngine
import com.alal.yft.core.download.AudioVideoMuxRunner
import com.alal.yft.core.download.DashTransferEngine
import com.alal.yft.core.download.DashTransferRunner
import com.alal.yft.core.download.DefaultDownloadTransferDispatcher
import com.alal.yft.core.download.DirectRangeProbe
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.DirectTransferRunner
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.DownloadWorkspaces
import com.alal.yft.core.download.EndedLiveSampler
import com.alal.yft.core.download.EndedLiveSizes
import com.alal.yft.core.download.FinishedStreamSampler
import com.alal.yft.core.download.FinishedStreamSizes
import com.alal.yft.core.download.HlsTransferEngine
import com.alal.yft.core.download.HlsTransferRunner
import com.alal.yft.core.download.LocalAudioVideoMuxer
import com.alal.yft.core.download.LocalMp3Transcoder
import com.alal.yft.core.download.Mp3ConvertingTransferDispatcher
import com.alal.yft.core.download.PlaylistTrackTransferRunner
import com.alal.yft.core.download.RoomDownloadTaskStore
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.media.BrowserReads
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.detection.Av1MergeSwitch
import com.alal.yft.detection.BrowserPageReader
import com.alal.yft.detection.HeadlessIdentity
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.TabPageReader
import com.alal.yft.detection.tiktok.TikTokPageSettings
import com.alal.yft.download.policy.ConnectivityNetworkMonitor
import com.alal.yft.download.policy.DownloadNetworkStatus
import com.alal.yft.download.policy.DownloadPolicyController
import com.alal.yft.download.policy.DownloadPolicyGate
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.thumbnail.DownloadThumbnails
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadApplicationScope

/**
 * Fresh link renewal: a download in one of these states needs no source read back from its record;
 * one that was never tried, failed or needs a fresh link does, so its row can renew it.
 */
private val NOT_RESTORED = setOf(
    DownloadTaskStatus.COMPLETED,
    DownloadTaskStatus.CANCELLED,
)

@Module
@InstallIn(SingletonComponent::class)
object DownloadRuntimeModule {
    @Provides
    @Singleton
    fun provideDownloadTaskStore(dao: DownloadRecordDao): DownloadTaskStore =
        RoomDownloadTaskStore(dao)

    /**
     * Fresh link renewal: the trial's "Break this download's link"; in every other build it is off
     * and the downloads' client is the app's own.
     */
    @Provides
    @Singleton
    fun provideTrialLinkBreak(): TrialLinkBreak = TrialLinkBreak(BuildConfig.SOCIAL_TRIAL)

    @Provides
    @Singleton
    fun provideDirectTransferRunner(
        client: OkHttpClient,
        trial: TrialLinkBreak,
    ): DirectTransferRunner = DirectTransferEngine(trial.around(client))

    @Provides
    @Singleton
    fun provideHlsTransferRunner(
        client: OkHttpClient,
        @ApplicationContext context: Context,
        browserReads: BrowserReads,
        trial: TrialLinkBreak,
    ): HlsTransferRunner = HlsTransferEngine(
        client = trial.around(client),
        workspaceRoot = File(context.noBackupFilesDir, HLS_WORKSPACE_DIRECTORY),
        browserReads = browserReads,
    )

    @Provides
    @Singleton
    fun provideDashTransferRunner(
        client: OkHttpClient,
        @ApplicationContext context: Context,
        trial: TrialLinkBreak,
    ): DashTransferRunner = DashTransferEngine(
        client = trial.around(client),
        workspaceRoot = File(context.noBackupFilesDir, DASH_WORKSPACE_DIRECTORY),
    )

    /** Ended live size sample: the sheet's sizes for a recently ended live's rows. */
    @Provides
    @Singleton
    fun provideEndedLiveSizes(client: OkHttpClient): EndedLiveSizes = EndedLiveSampler(client)

    /** Finished stream size sample: the sheet's sizes for HLS rows stating no average rate. */
    @Provides
    @Singleton
    fun provideFinishedStreamSizes(client: OkHttpClient): FinishedStreamSizes =
        FinishedStreamSampler(client)

    @Provides
    @Singleton
    fun provideLocalAudioVideoMuxer(): LocalAudioVideoMuxer =
        AndroidMp4AudioVideoMuxer()

    @Provides
    @Singleton
    fun provideAudioVideoMuxRunner(
        dash: DashTransferRunner,
        hls: HlsTransferRunner,
        muxer: LocalAudioVideoMuxer,
        @ApplicationContext context: Context,
        space: StorageSpace,
    ): AudioVideoMuxRunner = AudioVideoMuxEngine(
        // Sound pairing: an HLS video quality and its audio rendition, fetched as HLS, then merged.
        dashTransfer = PlaylistTrackTransferRunner(dash = dash, hls = hls),
        muxer = muxer,
        workspaceRoot = File(context.noBackupFilesDir, MUX_WORKSPACE_DIRECTORY),
        // The smaller of app storage and the shared volume: a merge writes into one or both.
        freeBytes = { space.availableBytes(DownloadLocation.SHARED_DOWNLOADS) },
        // AV1 merge switch: AV1 merges, a debug build's test switch (the release: off).
        av1Merges = Av1MergeSwitch::merges,
    )

    @Provides
    @Singleton
    fun provideLocalMp3Transcoder(): LocalMp3Transcoder = AndroidMp3Transcoder()

    @Provides
    @Singleton
    fun provideDownloadTransferDispatcher(
        direct: DirectTransferRunner,
        hls: HlsTransferRunner,
        dash: DashTransferRunner,
        mux: AudioVideoMuxRunner,
        mp3: LocalMp3Transcoder,
        sources: DownloadSources,
        @ApplicationContext context: Context,
    ): DownloadTransferDispatcher = Mp3ConvertingTransferDispatcher(
        delegate = DefaultDownloadTransferDispatcher(
            direct = direct,
            hls = hls,
            dash = dash,
            mux = mux,
            // Live download: "Live video saved 54 s short of 2h10m33s, at the end: …".
            onDetail = sources::note,
        ),
        transcoder = mp3,
        workspaceRoot = File(context.noBackupFilesDir, MP3_WORKSPACE_DIRECTORY),
        // MP3 conversion: "MP3: 4:12 of sound, decode 1.2 s, encode 6.8 s" in the row's Details.
        onDetail = sources::note,
    )

    @Provides
    @Singleton
    @DownloadApplicationScope
    fun provideDownloadScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fresh link renewal: whose sign-in a fresh link's lookup sends, by the lookup's owner. */
    @Provides
    @Singleton
    fun provideLookupSessions(tikTok: TikTokPageSettings): LookupSessions =
        AndroidLookupSessions(tikTok)

    /** Fresh link renewal: the phone's network changes, for "YouTube refused". */
    @Provides
    @Singleton
    fun provideNetworkChanges(
        network: NetworkStatusSource,
        @DownloadApplicationScope scope: CoroutineScope,
    ): NetworkChanges = NetworkChanges(network, scope)

    /**
     * Fresh link renewal: each queued download's source, in memory only. A page an adapter reads
     * renews only the adapter's video.
     */
    @Provides
    @Singleton
    fun provideDownloadSources(
        sessions: LookupSessions,
        changes: NetworkChanges,
        coordinator: Lazy<SiteAdapterCoordinator>,
    ): DownloadSources = DownloadSources(
        sessions = sessions,
        adapterPage = { pageUrl -> coordinator.get().handles(pageUrl) },
        networkChanges = { changes.value },
    )

    /**
     * Fresh link renewal: fresh links from the site's page. The lookup's parts are made when a link
     * first dies, so the queue never waits for them and no cycle runs through them. A page no
     * adapter reads is read again as its owner read it: the browser's re-read (the tab's agent and
     * cookies), or Home's (Home's agent, no cookie).
     */
    @Provides
    @Singleton
    fun provideSiteLinkRenewal(
        sources: DownloadSources,
        coordinator: Lazy<SiteAdapterCoordinator>,
        resolver: Lazy<VariantResolver>,
        probe: DirectMetadataProbe,
        sessions: LookupSessions,
        changes: NetworkChanges,
        browserPages: BrowserPageReader,
        client: OkHttpClient,
        destinations: DownloadDestinationProvider,
    ): SiteLinkRenewal = SiteLinkRenewal(
        sources = sources,
        coordinator = { coordinator.get() },
        resolver = { resolver.get() },
        probe = probe,
        sessions = sessions,
        networkChanges = { changes.value },
        destinations = destinations,
        pageReaders = PageReaders(
            browser = browserPages,
            home = TabPageReader(
                fetch = HeadlessPageFetcher(
                    client,
                    userAgent = HeadlessIdentity.USER_AGENT,
                    navigationHeaders = HeadlessIdentity.NAVIGATION_HEADERS,
                )::fetchForTab,
                cookies = { null },
            ),
        ),
    )

    @Provides
    @Singleton
    fun provideDownloadQueue(
        store: DownloadTaskStore,
        dispatcher: DownloadTransferDispatcher,
        @DownloadApplicationScope scope: CoroutineScope,
        renewal: SiteLinkRenewal,
        sources: DownloadSources,
    ): DownloadQueue = DownloadQueue(
        store = store,
        transferDispatcher = dispatcher,
        scope = scope,
        linkRenewal = renewal,
    ).also { queue ->
        // A source goes when its download completes, is cancelled or removed. Each source is also
        // saved with its download's record, so a Retry after the app closed gets a fresh link of
        // the same file and goes on from the bytes it kept; a source the app no longer holds takes
        // its saved card with it (Clear browsing data, a finished download, a removed row).
        scope.launch {
            combine(queue.tasks, sources.renewals) { tasks, _ -> tasks }.collect { tasks ->
                tasks.filter { it.status !in NOT_RESTORED && it.renewalSource != null }
                    .forEach { task -> sources.restored(task.id, task.renewalSource!!) }
                sources.prune(tasks)
                tasks.forEach { task ->
                    val card = sources.card(task.id)
                    if (card != task.renewalSource) queue.setRenewalSource(task.id, card)
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideDirectRangeProbe(client: OkHttpClient): DirectRangeProbe =
        DirectRangeProbe(client)

    @Provides
    @Singleton
    fun provideDirectMetadataProbe(probe: DirectRangeProbe): DirectMetadataProbe =
        DirectRangeMetadataProbe(probe)

    @Provides
    @Singleton
    fun provideDownloadDestinationProvider(
        @ApplicationContext context: Context,
        folders: SaveFolders,
    ): DownloadDestinationProvider = AndroidDownloadDestinationProvider(context, folders)

    /** Save folder: the folder picked in Android's folder picker, and YFT's access to it. */
    @Provides
    @Singleton
    fun provideSaveFolders(@ApplicationContext context: Context): SaveFolders =
        AndroidSaveFolders(context)

    @Provides
    @Singleton
    fun provideDownloadServiceStarter(
        @ApplicationContext context: Context,
    ): DownloadServiceStarter = ForegroundDownloadServiceStarter(context)

    /** Download queue: freezes, the cards the user put away and whether a download was started. */
    @Provides
    @Singleton
    fun provideBackgroundHealthStore(
        @ApplicationContext context: Context,
    ): BackgroundHealthStore = SharedPreferencesBackgroundHealthStore(context)

    @Provides
    @Singleton
    fun provideBackgroundSystemStatus(
        @ApplicationContext context: Context,
    ): BackgroundSystemStatus = AndroidBackgroundSystemStatus(context)

    @Provides
    @Singleton
    fun provideNetworkStatusSource(
        @ApplicationContext context: Context,
    ): NetworkStatusSource = ConnectivityNetworkMonitor(context)

    @Provides
    @Singleton
    fun provideDownloadPolicyController(
        queue: DownloadQueue,
        network: NetworkStatusSource,
        preferences: DownloadPreferencesRepository,
        @DownloadApplicationScope scope: CoroutineScope,
    ): DownloadPolicyController = DownloadPolicyController(
        queue = queue,
        network = network,
        preferences = preferences,
        scope = scope,
    )

    @Provides
    fun provideDownloadPolicyGate(controller: DownloadPolicyController): DownloadPolicyGate =
        controller

    @Provides
    fun provideDownloadNetworkStatus(controller: DownloadPolicyController): DownloadNetworkStatus =
        controller

    @Provides
    @Singleton
    fun provideStorageSpace(@ApplicationContext context: Context): StorageSpace =
        StatFsStorageSpace(context)

    @Provides
    @Singleton
    fun provideDownloadStorageSource(
        preferences: DownloadPreferencesRepository,
        space: StorageSpace,
        folders: SaveFolders,
    ): DownloadStorageSource =
        PreferencesDownloadStorageSource(preferences, space, folders = folders)

    @Provides
    @Singleton
    fun provideDownloadEnqueuer(
        queue: DownloadQueue,
        probe: DirectMetadataProbe,
        destinations: DownloadDestinationProvider,
        serviceStarter: DownloadServiceStarter,
        policy: DownloadPolicyGate,
        preferences: DownloadPreferencesRepository,
        storage: StorageSpace,
        sources: DownloadSources,
    ): DownloadEnqueuer = DownloadEnqueuer(
        queue = queue,
        probe = probe,
        destinations = destinations,
        serviceStarter = serviceStarter,
        policy = policy,
        location = { preferences.preferences.first().location },
        storage = storage,
        sources = sources,
        folder = { preferences.preferences.first().folder },
    )

    @Provides
    @Singleton
    fun providePreviewDownloadStarter(enqueuer: DownloadEnqueuer): PreviewDownloadStarter =
        enqueuer

    @Provides
    @Singleton
    fun provideDownloadStorageJanitor(
        queue: DownloadQueue,
        @ApplicationContext context: Context,
        @DownloadApplicationScope scope: CoroutineScope,
        thumbnails: DownloadThumbnails,
    ): DownloadStorageJanitor {
        val storage = context.noBackupFilesDir
        return DownloadStorageJanitor(
            queue = queue,
            appDownloadsRoot = File(
                storage,
                AndroidDownloadDestinationProvider.APP_PRIVATE_DIRECTORY,
            ),
            workspaceRoots = listOf(
                HLS_WORKSPACE_DIRECTORY to DownloadWorkspaces.HLS_PREFIX,
                DASH_WORKSPACE_DIRECTORY to DownloadWorkspaces.DASH_PREFIX,
                MUX_WORKSPACE_DIRECTORY to DownloadWorkspaces.MUX_PREFIX,
                MP3_WORKSPACE_DIRECTORY to DownloadWorkspaces.MP3_PREFIX,
            ).map { (directory, prefix) -> WorkspaceRoot(File(storage, directory), prefix) },
            processStartEpochMs = ::processStartEpochMs,
            scope = scope,
            thumbnails = thumbnails,
        )
    }

    /** Wall-clock time this process started; files older than this belong to a dead process. */
    private fun processStartEpochMs(): Long =
        System.currentTimeMillis() -
            (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
}

internal const val HLS_WORKSPACE_DIRECTORY = "download-hls"
internal const val DASH_WORKSPACE_DIRECTORY = "download-dash"
internal const val MUX_WORKSPACE_DIRECTORY = "download-mux"
internal const val MP3_WORKSPACE_DIRECTORY = "download-mp3"