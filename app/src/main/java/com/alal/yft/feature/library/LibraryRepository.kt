package com.alal.yft.feature.library

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.model.settings.SaveFolder
import com.alal.yft.download.AndroidDownloadedFileDeleter
import com.alal.yft.download.AndroidFileDeleteSystem
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.download.FileDeletion
import com.alal.yft.download.FolderEntry
import com.alal.yft.download.SaveFolders
import com.alal.yft.download.SavedDownloadFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v1.0.2 item 5b: what deleting several library files at once came to. Android asks the user
 * first about [asking]: the screen launches [request], then they are deleted again; on Android
 * 10 the files [waiting] are tried after that answer.
 */
data class LibraryDeletion(
    /** Gone, also those that were gone already. */
    val deleted: List<LibraryItem> = emptyList(),
    /** Still there. */
    val failed: List<LibraryItem> = emptyList(),
    /** Android's one "Allow YFT to delete?" request for all of [asking], or null. */
    val request: IntentSender? = null,
    val asking: List<LibraryItem> = emptyList(),
    val waiting: List<LibraryItem> = emptyList(),
)

/** Finished downloads the library can list and delete. */
interface LibraryRepository {
    /** Every finished download, newest first. */
    suspend fun items(): List<LibraryItem>

    /**
     * N5 (v1.0.2): the name of the folder picked in Android's folder picker while new downloads
     * go there, for the empty library's text; null otherwise.
     */
    suspend fun chosenFolderName(): String? = null

    /**
     * Deletes the file behind [item]; true when it is gone (also when it was gone already, P42),
     * false when it could not be removed.
     */
    suspend fun delete(item: LibraryItem): Boolean

    /**
     * v1.0.2 item 5b: deletes the files behind [items] together, with one Android request for
     * those that need the user's consent. This default deletes them one at a time and asks
     * nothing.
     */
    suspend fun deleteAll(items: List<LibraryItem>): LibraryDeletion {
        val (deleted, failed) = items.partition { item ->
            try {
                delete(item)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
        }
        return LibraryDeletion(deleted = deleted, failed = failed)
    }
}

/**
 * Lists YFT's MediaStore items in `Download/YFT/` and its published app-private files.
 *
 * Without storage permissions MediaStore returns only items YFT created itself, which is exactly
 * the library's scope. Pending items (transfers still being written) are never listed.
 *
 * v1.0.2 item 28: while new downloads go to a folder picked in Android's folder picker
 * ([chosenFolder]), the video and audio files at its top level are listed too, with the
 * folder's name as their place; hidden entries (a download's temporary document) are not. A
 * folder that can no longer be used lists nothing, and the rest of the list still shows.
 */
class AndroidLibraryRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** The same deleter as the Downloads screen's "Delete file" (P42). */
    private val fileDeleter: DownloadedFileDeleter =
        AndroidDownloadedFileDeleter(AndroidFileDeleteSystem(context), ioDispatcher = ioDispatcher),
    /** v1.0.2 item 28: the chosen folder while it is where new downloads go, else null. */
    private val chosenFolder: suspend () -> SaveFolder? = { null },
    private val folders: SaveFolders = SaveFolders.None,
) : LibraryRepository {
    override suspend fun items(): List<LibraryItem> {
        val folder = chosenFolder()
        return withContext(ioDispatcher) { listed(folder) }
    }

    override suspend fun chosenFolderName(): String? = chosenFolder()?.name

    private fun listed(folder: SaveFolder?): List<LibraryItem> {
        val shared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sharedItems()
        } else {
            emptyList()
        }
        val chosen = folder?.let(::folderItems).orEmpty()
        return (shared + appStorageItems() + chosen).sortedWith(
            compareByDescending<LibraryItem> { it.modifiedAtEpochMs ?: 0L }
                .thenBy { it.displayName },
        )
    }

    override suspend fun delete(item: LibraryItem): Boolean =
        fileDeleter.delete(item.savedFile()) == FileDeletion.Deleted

    /**
     * v1.0.2 item 5b: the picked files go to the deleter together, so Android asks once for all
     * that need the user's consent.
     */
    override suspend fun deleteAll(items: List<LibraryItem>): LibraryDeletion {
        val byFile = items.distinctBy(LibraryItem::id).associateBy { it.savedFile() }
        val result = fileDeleter.deleteAll(byFile.keys.toList())
        val itemsOf = { files: List<SavedDownloadFile> -> files.mapNotNull(byFile::get) }
        return LibraryDeletion(
            deleted = itemsOf(result.deleted),
            failed = itemsOf(result.failed),
            request = result.request,
            asking = itemsOf(result.asking),
            waiting = itemsOf(result.waiting),
        )
    }

    /** Where the file is, as the deleter knows it (P42). */
    private fun LibraryItem.savedFile(): SavedDownloadFile = when (location) {
        LibraryLocation.SHARED_DOWNLOADS -> SavedDownloadFile(
            kind = DownloadDestinationKind.MEDIA_STORE,
            uri = uri,
            displayName = displayName,
        )

        LibraryLocation.APP_STORAGE -> SavedDownloadFile(
            kind = DownloadDestinationKind.APP_PRIVATE,
            uri = null,
            displayName = displayName,
        )

        LibraryLocation.CHOSEN_FOLDER -> SavedDownloadFile(
            kind = DownloadDestinationKind.SAF_DOCUMENT,
            uri = uri,
            displayName = displayName,
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun sharedItems(): List<LibraryItem> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                SHARED_PROJECTION,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                    "${MediaStore.MediaColumns.IS_PENDING} = 0",
                arrayOf("${MediaStoreDownloadDestination.DEFAULT_RELATIVE_PATH}%"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return emptyList()
        return cursor.use { it.toSharedLibraryItems(collection) }
    }

    private fun appStorageItems(): List<LibraryItem> =
        AppPrivateDownloads.finishedFiles(AppPrivateDownloads.root(context)).map { file ->
            LibraryItem(
                id = "app:${file.name}",
                displayName = file.name,
                uri = AppPrivateDownloadProvider.uriFor(context, file.name).toString(),
                mimeType = LibraryMimeTypes.forFileName(file.name),
                sizeBytes = file.length(),
                modifiedAtEpochMs = file.lastModified().takeIf { it > 0L },
                location = LibraryLocation.APP_STORAGE,
            )
        }

    /** The chosen folder's top-level video and audio files; none when it cannot be used. */
    private fun folderItems(folder: SaveFolder): List<LibraryItem> {
        if (!folders.usable(folder)) return emptyList()
        return folders.entries(folder).orEmpty().mapNotNull { entry -> entry.libraryItem(folder) }
    }

    private fun FolderEntry.libraryItem(folder: SaveFolder): LibraryItem? {
        if (isDirectory || name.startsWith(".")) return null
        val type = mimeType?.takeIf { it.startsWith("video/") || it.startsWith("audio/") }
            ?: LibraryMimeTypes.forFileName(name)
                ?.takeIf { it.startsWith("video/") || it.startsWith("audio/") }
            ?: return null
        return LibraryItem(
            id = "saf:$documentId",
            displayName = name,
            uri = uri,
            mimeType = type,
            sizeBytes = sizeBytes,
            modifiedAtEpochMs = modifiedAtEpochMs,
            location = LibraryLocation.CHOSEN_FOLDER,
            place = folder.name,
        )
    }

    internal companion object {
        val SHARED_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
    }
}

/** Maps MediaStore rows in [AndroidLibraryRepository.SHARED_PROJECTION] order to items. */
internal fun Cursor.toSharedLibraryItems(collection: Uri): List<LibraryItem> = buildList {
    val idColumn = getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
    val nameColumn = getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
    val mimeColumn = getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
    val sizeColumn = getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
    val modifiedColumn = getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
    while (moveToNext()) {
        val name = getString(nameColumn)?.takeIf { it.isNotBlank() } ?: continue
        val id = getLong(idColumn)
        add(
            LibraryItem(
                id = "media:$id",
                displayName = name,
                uri = ContentUris.withAppendedId(collection, id).toString(),
                mimeType = getString(mimeColumn) ?: LibraryMimeTypes.forFileName(name),
                sizeBytes = if (isNull(sizeColumn)) null else getLong(sizeColumn),
                modifiedAtEpochMs = if (isNull(modifiedColumn)) {
                    null
                } else {
                    getLong(modifiedColumn) * MILLIS_PER_SECOND
                },
                location = LibraryLocation.SHARED_DOWNLOADS,
            ),
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000L
