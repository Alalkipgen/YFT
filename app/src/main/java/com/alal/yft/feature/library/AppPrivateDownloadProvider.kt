package com.alal.yft.feature.library

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only access to finished app-private downloads for the app a user opens or shares one with.
 *
 * androidx `FileProvider` has no root for `noBackupFilesDir`, where these files live, so this
 * provider serves exactly that one directory. A URI names one published file directly inside it;
 * nothing else resolves. The provider is not exported: other apps can read a file only through
 * the per-URI grant YFT attaches when the user opens or shares it, and nobody can write.
 */
class AppPrivateDownloadProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = fileFor(uri) ?: return null
        val columns = projection
            ?.filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
            ?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(
                columns.map { column ->
                    if (column == OpenableColumns.DISPLAY_NAME) file.name else file.length()
                },
            )
        }
    }

    override fun getType(uri: Uri): String? =
        fileFor(uri)?.let { LibraryMimeTypes.forFileName(it.name) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Downloads are shared read-only")
        val file = fileFor(uri) ?: throw FileNotFoundException("No such download")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Downloads are shared read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Downloads are shared read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Downloads are shared read-only")

    private fun fileFor(uri: Uri): File? {
        val context = context ?: return null
        if (uri.scheme != "content" || uri.authority != authority(context)) return null
        val name = uri.pathSegments.singleOrNull() ?: return null
        return AppPrivateDownloads.resolve(AppPrivateDownloads.root(context), name)
    }

    companion object {
        private const val AUTHORITY_SUFFIX = ".downloads"

        fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

        fun uriFor(context: Context, fileName: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(fileName)
            .build()
    }
}
