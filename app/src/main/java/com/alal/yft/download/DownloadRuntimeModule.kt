package com.alal.yft.download

import android.content.Context
import android.os.Process
import android.os.SystemClock
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
import com.alal.yft.core.download.HlsTransferEngine
import com.alal.yft.core.download.HlsTransferRunner
import com.alal.yft.core.download.LocalAudioVideoMuxer
import com.alal.yft.core.download.LocalMp3Transcoder
import com.alal.yft.core.download.Mp3ConvertingTransferDispatcher
import com.alal.yft.core.download.RoomDownloadTaskStore
import com.alal.yft.download.policy.ConnectivityNetworkMonitor
import com.alal.yft.download.policy.DownloadNetworkStatus
import com.alal.yft.download.policy.DownloadPolicyController
import com.alal.yft.download.policy.DownloadPolicyGate
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.thumbnail.DownloadThumbnails
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
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DownloadRuntimeModule {
    @Provides
    @Singleton
    fun provideDownloadTaskStore(dao: DownloadRecordDao): DownloadTaskStore =
        RoomDownloadTaskStore(dao)

    @Provides
    @Singleton
    fun provideDirectTransferRunner(client: OkHttpClient): DirectTransferRunner =
        DirectTransferEngine(client)

    @Provides
    @Singleton
    fun provideHlsTransferRunner(
        client: OkHttpClient,
        @ApplicationContext context: Context,
    ): HlsTransferRunner = HlsTransferEngine(
        client = client,
        workspaceRoot = File(context.noBackupFilesDir, HLS_WORKSPACE_DIRECTORY),
    )

    @Provides
    @Singleton
    fun provideDashTransferRunner(
        client: OkHttpClient,
        @ApplicationContext context: Context,
    ): DashTransferRunner = DashTransferEngine(
        client = client,
        workspaceRoot = File(context.noBackupFilesDir, DASH_WORKSPACE_DIRECTORY),
    )

    @Provides
    @Singleton
    fun provideLocalAudioVideoMuxer(): LocalAudioVideoMuxer =
        AndroidMp4AudioVideoMuxer()

    @Provides
    @Singleton
    fun provideAudioVideoMuxRunner(
        dash: DashTransferRunner,
        muxer: LocalAudioVideoMuxer,
        @ApplicationContext context: Context,
    ): AudioVideoMuxRunner = AudioVideoMuxEngine(
        dashTransfer = dash,
        muxer = muxer,
        workspaceRoot = File(context.noBackupFilesDir, MUX_WORKSPACE_DIRECTORY),
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
        @ApplicationContext context: Context,
    ): DownloadTransferDispatcher = Mp3ConvertingTransferDispatcher(
        delegate = DefaultDownloadTransferDispatcher(
            direct = direct,
            hls = hls,
            dash = dash,
            mux = mux,
        ),
        transcoder = mp3,
        workspaceRoot = File(context.noBackupFilesDir, MP3_WORKSPACE_DIRECTORY),
    )

    @Provides
    @Singleton
    @DownloadApplicationScope
    fun provideDownloadScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideDownloadQueue(
        store: DownloadTaskStore,
        dispatcher: DownloadTransferDispatcher,
        @DownloadApplicationScope scope: CoroutineScope,
    ): DownloadQueue = DownloadQueue(
        store = store,
        transferDispatcher = dispatcher,
        scope = scope,
    )

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
    ): DownloadDestinationProvider = AndroidDownloadDestinationProvider(context)

    @Provides
    @Singleton
    fun provideDownloadServiceStarter(
        @ApplicationContext context: Context,
    ): DownloadServiceStarter = ForegroundDownloadServiceStarter(context)

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
    ): DownloadStorageSource = PreferencesDownloadStorageSource(preferences, space)

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
    ): DownloadEnqueuer = DownloadEnqueuer(
        queue = queue,
        probe = probe,
        destinations = destinations,
        serviceStarter = serviceStarter,
        policy = policy,
        location = { preferences.preferences.first().location },
        storage = storage,
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