package com.alal.yft.download

import android.content.Context
import android.os.Build
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadDestinationSpec
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.download.SafDownloadDestination
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.SaveFolder
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Prefers a pending MediaStore item so a verified file lands in the user's Downloads folder.
 *
 * Pending MediaStore items need Android 10 (API 29). Older releases fall back to app-private
 * storage because exposing a half-written public file under its final name is not acceptable.
 * Users can also choose app storage explicitly, or a folder of their own (Save folder) through
 * Android's folder picker: a hidden temporary document there ([SafDownloadDestination]) gets the
 * file's name only after the check, on every Android version. A folder that can no longer be
 * used gives way to the default place, and the prepared destination says so.
 *
 * App-private and folder names are reserved when the destination is prepared: a name that is
 * already published or staged by another download gets a " (n)" suffix, so two downloads of the
 * same title can never collide when the second one is published.
 */
class AndroidDownloadDestinationProvider(
    private val context: Context,
    private val folders: SaveFolders = AndroidSaveFolders(context),
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : DownloadDestinationProvider {
    private val reservationLock = Any()

    override fun prepare(
        fileName: String,
        mimeType: String?,
        location: DownloadLocation,
        folder: SaveFolder?,
    ): PreparedDestination = when (location) {
        DownloadLocation.CHOSEN_FOLDER -> prepareChosenFolder(fileName, mimeType, folder)
        DownloadLocation.SHARED_DOWNLOADS, DownloadLocation.APP_STORAGE ->
            prepareDefault(fileName, mimeType, location)
    }

    private fun prepareDefault(
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

    /**
     * The chosen folder while YFT may still make files there; else the default place, which
     * the row then names. A folder that refuses the new document counts as gone too.
     */
    private fun prepareChosenFolder(
        fileName: String,
        mimeType: String?,
        folder: SaveFolder?,
    ): PreparedDestination {
        if (folder != null && folders.usable(folder)) {
            try {
                return prepareFolder(folder, fileName, mimeType)
            } catch (_: IOException) {
                // Gone between the check and the new document: the default place below.
            }
        }
        return prepareDefault(fileName, mimeType, DownloadLocation.SHARED_DOWNLOADS)
            .copy(folderUnavailable = true)
    }

    private fun prepareFolder(
        folder: SaveFolder,
        fileName: String,
        mimeType: String?,
    ): PreparedDestination = synchronized(reservationLock) {
        val names = folders.entries(folder)?.map { it.name }
            ?: throw IOException("Unable to read the chosen folder")
        val chosen = FolderNames.unique(fileName, names)
        val destination = SafDownloadDestination.create(
            resolver = context.contentResolver,
            treeUri = folder.treeUri,
            displayName = chosen,
            mimeType = mimeType,
            temporaryId = ids(),
        )
        PreparedDestination(
            destination = destination,
            spec = DownloadDestinationSpec(
                kind = DownloadDestinationKind.SAF_DOCUMENT,
                uri = destination.recoveryUri,
            ),
            fileName = chosen,
        )
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
                partialName = partial.name,
            )
        }

    /**
     * The file of a download the app closed, reopened where its bytes are: the same pending
     * MediaStore item, the same temporary document, or the same `.part` file in app storage. The
     * record's text is used as a name, never as a path, and a file that is no longer there gives
     * null, so the row says it cannot go on instead of writing somewhere else.
     */
    override fun reopen(saved: SavedDestination): DownloadDestination? {
        if (!saved.reopenable) return null
        return try {
            when (saved.kind) {
                DownloadDestinationKind.APP_PRIVATE -> {
                    val partialName = saved.partialName ?: return null
                    val root = File(context.noBackupFilesDir, APP_PRIVATE_DIRECTORY)
                    val partial = File(root, partialName)
                    when {
                        partialName.any { it == '/' || it == '\\' } -> null
                        !partial.isFile -> null
                        else -> FileDownloadDestination(
                            partialFile = partial,
                            completedFile = File(root, saved.displayName),
                        )
                    }
                }
                DownloadDestinationKind.MEDIA_STORE -> saved.uri?.let { uri ->
                    MediaStoreDownloadDestination.resume(context.contentResolver, uri)
                }
                DownloadDestinationKind.SAF_DOCUMENT -> saved.uri?.let { uri ->
                    SafDownloadDestination.resume(
                        resolver = context.contentResolver,
                        temporaryDocumentUri = uri,
                        displayName = saved.displayName,
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
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

    fun unique(fileName: String, existing: Set<String>): String =
        unique(fileName) { name ->
            name in existing || existing.any { it.startsWith("$name.") && it.endsWith(".part") }
        }

    /** [fileName], or the first "name (n).ext" that is not [taken]. */
    fun unique(fileName: String, taken: (String) -> Boolean): String {
        if (!taken(fileName)) return fileName
        val dot = fileName.lastIndexOf('.').takeIf { it > 0 }
        val stem = if (dot == null) fileName else fileName.substring(0, dot)
        val extension = if (dot == null) "" else fileName.substring(dot)
        for (suffix in 1..MAX_SUFFIX) {
            val candidate = "$stem ($suffix)$extension"
            if (!taken(candidate)) return candidate
        }
        throw IOException("Too many downloads share this name")
    }
}

/**
 * Save folder: a free name in a chosen folder, given its entries' [names]: a name is taken
 * by a file or folder of that name, in any case (memory cards ignore it), or by another
 * download's hidden temporary document (`.name.yft-<id>.part`, to which the phone's storage
 * may add the type's extension).
 */
internal object FolderNames {
    fun unique(fileName: String, names: Collection<String>): String {
        val existing = names.map { it.lowercase(Locale.ROOT) }.toSet()
        return AppPrivateNames.unique(fileName) { candidate ->
            val name = candidate.lowercase(Locale.ROOT)
            name in existing || existing.any { it.startsWith(".$name.yft-") }
        }
    }
}
