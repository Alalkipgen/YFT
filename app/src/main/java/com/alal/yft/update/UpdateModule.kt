package com.alal.yft.update

import android.content.Context
import com.alal.yft.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    /** One controller for the process: its checks outlive the screen that started them. */
    @Provides
    @Singleton
    fun provideUpdateController(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): UpdateController = UpdateController(
        environment = UpdateEnvironment(
            mode = UpdatePolicy.mode(context),
            installedCode = BuildConfig.VERSION_CODE.toLong(),
            installedName = BuildConfig.VERSION_NAME,
        ),
        checker = UpdateChecker(client),
        store = PreferencesUpdateStore(
            context.getSharedPreferences(PreferencesUpdateStore.FILE, Context.MODE_PRIVATE),
        ),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        clock = System::currentTimeMillis,
    )
}
