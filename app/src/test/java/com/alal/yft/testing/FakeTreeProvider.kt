package com.alal.yft.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException
import org.robolectric.Robolectric

/**
 * v1.0.2 item 28: a folder picked in Android's folder picker, for Robolectric tests. Its
 * documents are files under [FakeTree.root]: "primary:<folder>" is a folder and
 * "primary:<folder>/<name>" an entry in it. It answers the calls [DocumentsContract] makes
 * (create, rename, delete) and opens files read-write, like the phone's own storage. A folder
 * whose name starts with "ReadOnly" cannot get new files.
 */
class FakeTreeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val segments = uri.pathSegments
        if (segments.size !in 4..5 || segments[0] != "tree" || segments[2] != "document") {
            return null
        }
        val documentId = segments[3]
        val file = fileOf(documentId) ?: return null
        val columns = projection?.map { it }?.toTypedArray() ?: COLUMNS
        val cursor = MatrixCursor(columns)
        if (segments.size == 5) {
            if (segments[4] != "children" || !file.isDirectory) return null
            file.listFiles().orEmpty().sortedBy(File::getName).forEach { child ->
                cursor.addRow(row(columns, "$documentId/${child.name}", child))
            }
        } else {
            if (!file.exists()) return null
            cursor.addRow(row(columns, documentId, file))
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        @Suppress("DEPRECATION")
        val uri = extras?.getParcelable<Uri>(EXTRA_URI) ?: return null
        val documentId = DocumentsContract.getDocumentId(uri)
        val file = fileOf(documentId) ?: throw FileNotFoundException(documentId)
        return when (method) {
            CREATE -> {
                if (!file.isDirectory || !writable(file)) throw SecurityException("read-only")
                val name = extras.getString(Document.COLUMN_DISPLAY_NAME)!!
                val child = File(file, name)
                if (!child.createNewFile()) throw IllegalStateException("$name exists")
                Bundle().apply { putParcelable(EXTRA_URI, uriOf(uri, "$documentId/$name")) }
            }

            RENAME -> {
                if (!file.exists()) throw FileNotFoundException(documentId)
                val name = extras.getString(Document.COLUMN_DISPLAY_NAME)!!
                val parentId = documentId.substringBeforeLast('/')
                val target = File(file.parentFile, name)
                if (target.exists() || !file.renameTo(target)) {
                    throw IllegalStateException("$name exists")
                }
                Bundle().apply { putParcelable(EXTRA_URI, uriOf(uri, "$parentId/$name")) }
            }

            DELETE -> {
                if (!file.exists()) throw FileNotFoundException(documentId)
                file.deleteRecursively()
                Bundle()
            }

            else -> super.call(method, arg, extras)
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileOf(DocumentsContract.getDocumentId(uri))
            ?.takeIf(File::isFile)
            ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? =
        fileOf(DocumentsContract.getDocumentId(uri))?.let(::mimeTypeOf)

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun fileOf(documentId: String): File? {
        val path = documentId.removePrefix(ROOT_ID_PREFIX)
        if (path == documentId || path.isBlank() || path.split('/').any { it == ".." }) {
            return null
        }
        return File(FakeTree.root(context!!), path)
    }

    private fun row(columns: Array<String>, documentId: String, file: File): Array<Any?> =
        columns.map { column ->
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> documentId
                Document.COLUMN_DISPLAY_NAME -> file.name
                Document.COLUMN_MIME_TYPE -> mimeTypeOf(file)
                Document.COLUMN_FLAGS -> flagsOf(file)
                Document.COLUMN_SIZE -> if (file.isDirectory) null else file.length()
                Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                else -> null
            }
        }.toTypedArray()

    private fun flagsOf(file: File): Int = when {
        file.isDirectory && writable(file) -> Document.FLAG_DIR_SUPPORTS_CREATE
        file.isDirectory -> 0
        else -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
            Document.FLAG_SUPPORTS_RENAME
    }

    private fun writable(directory: File): Boolean = !directory.name.startsWith("ReadOnly")

    private fun mimeTypeOf(file: File): String = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        else -> when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "txt" -> "text/plain"
            "jpg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun uriOf(base: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(base, documentId)

    private companion object {
        // DocumentsContract's own names for these calls and their extra (hidden in the SDK).
        const val CREATE = "android:createDocument"
        const val RENAME = "android:renameDocument"
        const val DELETE = "android:deleteDocument"
        const val EXTRA_URI = "uri"
        const val ROOT_ID_PREFIX = "primary:"
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}

/** The [FakeTreeProvider]'s folders, and the access a picker would give to them. */
object FakeTree {
    const val AUTHORITY = "com.alal.yft.test.documents"
    private const val ACCESS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun install(): FakeTreeProvider =
        Robolectric.setupContentProvider(FakeTreeProvider::class.java, AUTHORITY)

    fun root(context: Context): File = File(context.filesDir, "fake-tree")

    /** The folder [name] on "the phone", made when missing. */
    fun folder(context: Context, name: String = "YFT"): File =
        File(root(context), name).apply { mkdirs() }

    /** What the folder picker gives for the folder [name]. */
    fun treeUri(name: String = "YFT"): Uri =
        DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:$name")

    /** The user takes YFT's access to the folder [treeUri] back, as in Android's settings. */
    fun revoke(context: Context, treeUri: String) {
        context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), ACCESS)
    }
}
