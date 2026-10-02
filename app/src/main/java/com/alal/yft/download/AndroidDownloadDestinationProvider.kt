package com.alal.yft.download

import android.content.Context
import android.os.Build
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.download.MediaStoreDownloadDestination
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Prefers a pending MediaStore item so a verified file lands in the user's Downloads folder.
 *
 * Pending MediaStore items need Android 10 (API 29). Older releases fall back to app-private
 * storage because exposing a half-written public file under its final name is not acceptable, and
 * a SAF tree destination requires a folder the user has not been asked for yet.
 */
class AndroidDownloadDestinationProvider(
    private val context: Context,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : DownloadDestinationProvider {
    override fun prepare(fileName: String, mimeType: String?): PreparedDestination =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prepareMediaStore(fileName, mimeType)
        } else {
            prepareAppPrivate(fileName)
        }

    private fun prepareMediaStore(fileName: String, mimeType: String?): PreparedDestination {
        val destination = MediaStoreDownloadDestination.create(
            resolver = context.contentResolver,
            displayName = fileName,
            mimeType = mimeType,
        )
        return PreparedDestination(
            destination = destination,
            spec = DownloadDestinationSpec(
                kind = DownloadDestinationKind.MEDIA_STORE,
                uri = destination.recoveryUri,
            ),
        )
    }

    private fun prepareAppPrivate(fileName: String): PreparedDestination {
        val root = File(context.noBackupFilesDir, APP_PRIVATE_DIRECTORY)
        if (!root.isDirectory && !root.mkdirs()) {
            throw IOException("Unable to create the download directory")
        }
        val completed = File(root, fileName)
        val partial = File(root, "${fileName}.${ids().sanitizeId()}.part")
        return PreparedDestination(
            destination = FileDownloadDestination(
                partialFile = partial,
                completedFile = completed,
            ),
            spec = DownloadDestinationSpec(kind = DownloadDestinationKind.APP_PRIVATE),
        )
    }

    private fun String.sanitizeId(): String = lowercase(Locale.US)
        .filter { it.isLetterOrDigit() }
        .take(16)
        .ifBlank { "part" }

    private companion object {
        const val APP_PRIVATE_DIRECTORY = "downloads"
    }
}
