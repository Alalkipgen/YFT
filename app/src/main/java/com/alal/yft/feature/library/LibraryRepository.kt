package com.alal.yft.feature.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.alal.yft.core.download.MediaStoreDownloadDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Finished downloads the library can list and delete. */
interface LibraryRepository {
    /** Every finished download, newest first. */
    suspend fun items(): List<LibraryItem>

    /** Deletes the file behind [item]; false when it was already gone or could not be removed. */
    suspend fun delete(item: LibraryItem): Boolean
}

/**
 * Lists YFT's MediaStore items in `Download/YFT/` and its published app-private files.
 *
 * Without storage permissions MediaStore returns only items YFT created itself, which is exactly
 * the library's scope. Pending items (transfers still being written) are never listed.
 */
class AndroidLibraryRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryRepository {
    override suspend fun items(): List<LibraryItem> = withContext(ioDispatcher) {
        val shared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sharedItems()
        } else {
            emptyList()
        }
        (shared + appStorageItems()).sortedWith(
            compareByDescending<LibraryItem> { it.modifiedAtEpochMs ?: 0L }
                .thenBy { it.displayName },
        )
    }

    override suspend fun delete(item: LibraryItem): Boolean = withContext(ioDispatcher) {
        when (item.location) {
            LibraryLocation.SHARED_DOWNLOADS -> runCatching {
                context.contentResolver.delete(Uri.parse(item.uri), null, null) > 0
            }.getOrDefault(false)

            LibraryLocation.APP_STORAGE ->
                AppPrivateDownloads.resolve(AppPrivateDownloads.root(context), item.displayName)
                    ?.delete() == true
        }
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
