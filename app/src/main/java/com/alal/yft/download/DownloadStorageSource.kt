package com.alal.yft.download

import android.os.Build
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where new downloads are saved and how much room is left there, for the Downloads footer. */
interface DownloadStorageSource {
    /** The location new downloads use after the platform fallback; null when unknown. */
    val location: Flow<DownloadLocation?>

    /** Free bytes at [location], or null when the volume cannot be measured. */
    suspend fun freeBytes(location: DownloadLocation): Long?

    /** Nothing to report, for screens and tests without storage access. */
    object None : DownloadStorageSource {
        override val location: Flow<DownloadLocation?> = flowOf(null)

        override suspend fun freeBytes(location: DownloadLocation): Long? = null
    }
}

/**
 * Reads the chosen location from the download preferences. The shared `Download/YFT` folder
 * needs Android 10, so older devices report app storage, which is where their downloads go.
 */
class PreferencesDownloadStorageSource(
    preferences: DownloadPreferencesRepository,
    private val space: StorageSpace,
    private val sharedDownloadsSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadStorageSource {
    override val location: Flow<DownloadLocation?> = preferences.preferences
        .map { stored ->
            if (stored.location == DownloadLocation.SHARED_DOWNLOADS && !sharedDownloadsSupported) {
                DownloadLocation.APP_STORAGE
            } else {
                stored.location
            }
        }
        .distinctUntilChanged()

    override suspend fun freeBytes(location: DownloadLocation): Long? =
        withContext(ioDispatcher) { space.availableBytes(location) }
}
