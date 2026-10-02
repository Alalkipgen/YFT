package com.alal.yft.download

import android.content.Context
import android.os.Build
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.model.settings.DownloadLocation
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Prefers a pending MediaStore item so a verified file lands in the user's Downloads folder.
 *
 * Pending MediaStore items need Android 10 (API 29). Older releases fall back to app-private
 * storage because exposing a half-written public file under its final name is not acceptable, and
 * a SAF tree destination requires a folder the user has not been asked for yet. Users can also
 * choose app storage explicitly.
 *
 * App-private names are reserved when the destination is prepared: a name that is already
 * published or staged by another download gets a " (n)" suffix, so two downloads of the same
 * title can never collide when the second one is published.
 */
class AndroidDownloadDestinationProvider(
    private val context: Context,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : DownloadDestinationProvider {
    private val reservationLock = Any()

    override fun prepare(
        fileName: String,
        mimeType: String?,
        location: DownloadLocation,
    ): PreparedDestination =
        if (
            location == DownloadLocation.SHARED_DOWNLOADS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
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
            fileName = fileName,
        )
    }

    private fun prepareAppPrivate(fileName: String): PreparedDestination =
        synchronized(reservationLock) {
            val root = File(context.noBackupFilesDir, APP_PRIVATE_DIRECTORY)
            if (!root.isDirectory && !root.mkdirs()) {
                throw IOException("Unable to create the download directory")
            }
            val chosen = AppPrivateNames.unique(fileName, root.list().orEmpty().toSet())
            val partial = File(root, "$chosen.${ids().sanitizeId()}.part")
            if (!partial.createNewFile()) {
                throw IOException("Unable to reserve the download file")
            }
            PreparedDestination(
                destination = FileDownloadDestination(
                    partialFile = partial,
                    completedFile = File(root, chosen),
                ),
                spec = DownloadDestinationSpec(kind = DownloadDestinationKind.APP_PRIVATE),
                fileName = chosen,
            )
        }

    private fun String.sanitizeId(): String = lowercase(Locale.US)
        .filter { it.isLetterOrDigit() }
        .take(16)
        .ifBlank { "part" }

    companion object {
        const val APP_PRIVATE_DIRECTORY = "downloads"
    }
}

/** Picks a free name in a directory that holds published files and `.part` reservations. */
internal object AppPrivateNames {
    private const val MAX_SUFFIX = 999

    fun unique(fileName: String, existing: Set<String>): String {
        if (!existing.taken(fileName)) return fileName
        val dot = fileName.lastIndexOf('.').takeIf { it > 0 }
        val stem = if (dot == null) fileName else fileName.substring(0, dot)
        val extension = if (dot == null) "" else fileName.substring(dot)
        for (suffix in 1..MAX_SUFFIX) {
            val candidate = "$stem ($suffix)$extension"
            if (!existing.taken(candidate)) return candidate
        }
        throw IOException("Too many downloads share this name")
    }

    private fun Set<String>.taken(name: String): Boolean =
        name in this || any { it.startsWith("$name.") && it.endsWith(".part") }
}
