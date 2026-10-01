package com.alal.yft.download

import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.DirectTransferRunner
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.RoomDownloadTaskStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    @DownloadApplicationScope
    fun provideDownloadScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideDownloadQueue(
        store: DownloadTaskStore,
        runner: DirectTransferRunner,
        @DownloadApplicationScope scope: CoroutineScope,
    ): DownloadQueue = DownloadQueue(
        store = store,
        transferRunner = runner,
        scope = scope,
    )
}