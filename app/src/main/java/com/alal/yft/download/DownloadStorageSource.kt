package com.alal.yft.download

import android.os.Build
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.SaveFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where new downloads are saved and how much room is left there, for the Downloads footer. */
interface DownloadStorageSource {
    /** The location new downloads use after the platform fallback; null when unknown. */
    val location: Flow<DownloadLocation?>

    /** Free bytes at [location], or null when the volume cannot be measured. */
    suspend fun freeBytes(location: DownloadLocation): Long?

    /**
     * v1.0.2 item 28: the chosen folder's name while new downloads can go there, asked again
     * each time; null when it can no longer be used, as they go to [defaultLocation] then.
     */
    suspend fun folderName(): String? = null

    /**
     * M1 (v1.0.2): free bytes where the chosen folder is, when that can be read (the phone's own
     * storage); null otherwise (a memory card, a cloud app) and when the folder cannot be used.
     */
    suspend fun folderFreeBytes(): Long? = null

    /** Where new downloads go when the chosen folder cannot be used. */
    val defaultLocation: DownloadLocation get() = DownloadLocation.SHARED_DOWNLOADS

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
    private val preferences: DownloadPreferencesRepository,
    private val space: StorageSpace,
    private val sharedDownloadsSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** v1.0.2 item 28: whether the chosen folder can still be used. */
    private val folders: SaveFolders = SaveFolders.None,
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

    /** M1 (v1.0.2): the folder's path ("Movies/Test", "Download/YFT"), else its name. */
    override suspend fun folderName(): String? = usableFolder()?.let(SaveFolderPaths::label)

    override suspend fun folderFreeBytes(): Long? {
        val folder = usableFolder()?.takeIf(SaveFolderPaths::onPhoneStorage) ?: return null
        return freeBytes(DownloadLocation.SHARED_DOWNLOADS)
    }

    private suspend fun usableFolder(): SaveFolder? {
        val folder = preferences.preferences.first().folder ?: return null
        val usable = withContext(ioDispatcher) { folders.usable(folder) }
        return folder.takeIf { usable }
    }

    override val defaultLocation: DownloadLocation = if (sharedDownloadsSupported) {
        DownloadLocation.SHARED_DOWNLOADS
    } else {
        DownloadLocation.APP_STORAGE
    }
}
