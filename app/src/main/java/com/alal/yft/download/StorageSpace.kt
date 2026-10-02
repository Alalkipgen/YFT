package com.alal.yft.download

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.alal.yft.core.model.settings.DownloadLocation
import java.io.File

/** Free bytes where downloads for a location are staged and published, or null when unknown. */
fun interface StorageSpace {
    fun availableBytes(location: DownloadLocation): Long?

    companion object {
        /** Free space cannot be measured; a full disk is then reported when a write fails. */
        val Unknown = StorageSpace { null }
    }
}

/**
 * Measures the volumes a download touches. Streaming transfers stage their segments in app
 * storage even when the result goes to the shared Downloads folder, so the smaller of the two
 * volumes decides; on most devices both are the same data partition.
 */
class StatFsStorageSpace(
    private val context: Context,
) : StorageSpace {
    override fun availableBytes(location: DownloadLocation): Long? {
        val volumes = buildList<File> {
            add(context.noBackupFilesDir)
            if (
                location == DownloadLocation.SHARED_DOWNLOADS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ) {
                @Suppress("DEPRECATION") // Only measured, never written directly.
                add(Environment.getExternalStorageDirectory())
            }
        }
        return volumes
            .mapNotNull { volume -> runCatching { StatFs(volume.path).availableBytes }.getOrNull() }
            .minOrNull()
    }
}
