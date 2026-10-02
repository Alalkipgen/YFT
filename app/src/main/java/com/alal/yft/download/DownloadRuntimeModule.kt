package com.alal.yft.download

import android.content.Context
import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxEngine
import com.alal.yft.core.download.AudioVideoMuxRunner
import com.alal.yft.core.download.DashTransferEngine
import com.alal.yft.core.download.DashTransferRunner
import com.alal.yft.core.download.DefaultDownloadTransferDispatcher
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.DirectTransferRunner
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.HlsTransferEngine
import com.alal.yft.core.download.HlsTransferRunner
import com.alal.yft.core.download.LocalAudioVideoMuxer
import com.alal.yft.core.download.RoomDownloadTaskStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        workspaceRoot = File(context.noBackupFilesDir, "download-hls"),
    )

    @Provides
    @Singleton
    fun provideDashTransferRunner(
        client: OkHttpClient,
        @ApplicationContext context: Context,
    ): DashTransferRunner = DashTransferEngine(
        client = client,
        workspaceRoot = File(context.noBackupFilesDir, "download-dash"),
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
        workspaceRoot = File(context.noBackupFilesDir, "download-mux"),
    )

    @Provides
    @Singleton
    fun provideDownloadTransferDispatcher(
        direct: DirectTransferRunner,
        hls: HlsTransferRunner,
        dash: DashTransferRunner,
        mux: AudioVideoMuxRunner,
    ): DownloadTransferDispatcher = DefaultDownloadTransferDispatcher(
        direct = direct,
        hls = hls,
        dash = dash,
        mux = mux,
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
}