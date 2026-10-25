package com.alal.yft.detection.master

import android.content.Context
import android.content.SharedPreferences
import com.alal.yft.BuildConfig
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Decision 30 (i) (`MASTER_KEY_SOCIAL_PLAN.md` section 8): Settings › Browser › "Backup finder",
 * on by default. Off, no lookup asks Master and nothing goes to GitHub raw (R9's signed config,
 * YouTube's version tables): the browser's fallback answers with main's outcome
 * ([AndroidBrowserMasterFallback]), a new tab gets no capture, YouTube's reader runs on main's
 * solver ([BackupFinderPlayerScripts]) and Master's own client refuses every request ([gate]), so
 * a Master run the switch stops halfway asks nothing more. Read synchronously from app
 * preferences, so no lookup runs on a value not loaded yet. Only a build with Master
 * ([available]: the release, the previews, a Master debug build) shows the switch.
 */
class BackupFinder internal constructor(
    private val preferences: SharedPreferences?,
    /** Whether this build has Master, so Settings shows the switch. */
    val available: Boolean,
    initial: Boolean = preferences?.getBoolean(KEY, true) ?: true,
) {
    private val state = MutableStateFlow(initial)

    /** The switch as Settings shows it. */
    val on: StateFlow<Boolean> = state.asStateFlow()

    /** Whether Master may answer: read at each lookup and before each of Master's requests. */
    fun isOn(): Boolean = state.value

    fun set(on: Boolean) {
        preferences?.edit()?.putBoolean(KEY, on)?.apply()
        state.value = on
    }

    companion object {
        /** The app preferences that hold the switch. */
        const val PREFERENCES = "yft_backup_finder"
        const val KEY = "on"

        /** The failure Master's requests get while the switch is off. */
        const val OFF = "Backup finder is off"

        @Volatile
        private var instance: BackupFinder? = null

        /** The app's switch, one per process. */
        fun get(context: Context): BackupFinder = instance ?: synchronized(this) {
            instance ?: BackupFinder(
                preferences = context.applicationContext
                    .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
                available = BuildConfig.MASTER_CAPTURE_ENABLED,
            ).also { instance = it }
        }

        /** A switch kept in memory only (tests). */
        fun inMemory(on: Boolean = true, available: Boolean = true): BackupFinder =
            BackupFinder(preferences = null, available = available, initial = on)

        /**
         * [client] for Master's own requests: each one fails before it is sent while [on] says
         * off. Shares [client]'s connections and rules otherwise.
         */
        fun gate(client: OkHttpClient, on: () -> Boolean): OkHttpClient = client.newBuilder()
            .addInterceptor(
                Interceptor { chain ->
                    if (!on()) throw IOException(OFF)
                    chain.proceed(chain.request())
                },
            )
            .build()
    }
}
