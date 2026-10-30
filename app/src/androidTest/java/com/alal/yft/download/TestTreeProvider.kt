package com.alal.yft.download

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/**
 * v1.0.2 item 28: a real documents provider for [ChosenFolderInstrumentedTest], in the test
 * APK's own process, as a phone's storage is in its own: [ROOT_ID] is the folder (a directory
 * in this APK's files) and "root:YFT/<name>" an entry at its top. Android checks each call the
 * app makes against the access the folder picker gives ([TestTreeAccessProvider] gives it
 * here). Like a phone's storage, it adds the type's extension to a new file's name that lacks
 * it, and gives a taken name a " (n)" suffix.
 */
class TestTreeProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: ROOT_COLUMNS).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, "root")
                .add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
                .add(Root.COLUMN_TITLE, "YFT test tree")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            addDocument(documentId, existing(documentId))
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val folder = existing(parentDocumentId)
        if (parentDocumentId != ROOT_ID) throw FileNotFoundException("Not the folder")
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            folder.listFiles().orEmpty().sortedBy(File::getName).forEach { child ->
                addDocument("$ROOT_ID/${child.name}", child)
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_ID && documentId.startsWith("$ROOT_ID/")

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = existing(documentId)
        if (!file.isFile) throw FileNotFoundException("Not a file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = existing(parentDocumentId)
        if (parentDocumentId != ROOT_ID) throw FileNotFoundException("Not the folder")
        val file = unique(parent, nameFor(mimeType, displayName))
        val made = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        if (!made) throw IllegalStateException("Cannot create the document")
        return "$ROOT_ID/${file.name}"
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = existing(documentId)
        if (documentId == ROOT_ID) throw UnsupportedOperationException("The folder keeps its name")
        val target = unique(requireNotNull(file.parentFile), displayName)
        if (!file.renameTo(target)) throw IllegalStateException("Cannot rename the document")
        return "$ROOT_ID/${target.name}"
    }

    override fun deleteDocument(documentId: String) {
        val file = existing(documentId)
        if (!file.deleteRecursively()) throw IllegalStateException("Cannot delete the document")
    }

    private fun existing(documentId: String): File =
        fileOf(documentId).takeIf(File::exists) ?: throw FileNotFoundException("Missing document")

    private fun fileOf(documentId: String): File {
        val folder = folder(requireNotNull(context))
        if (documentId == ROOT_ID) return folder
        val name = documentId.removePrefix("$ROOT_ID/")
        val plain = name != documentId && name.isNotBlank() && '/' !in name &&
            name != "." && name != ".."
        if (!plain) throw FileNotFoundException("Not a document")
        return File(folder, name)
    }

    private fun MatrixCursor.addDocument(documentId: String, file: File) {
        val flags = if (file.isDirectory) {
            Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE
        } else {
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME
        }
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, mimeTypeOf(file))
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_SIZE, if (file.isDirectory) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    companion object {
        const val ROOT_ID = "root:YFT"

        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        /** The folder's directory, in the test APK's own files. */
        fun folder(context: Context): File = File(context.filesDir, "tree/YFT")

        /** A new file's name, with its type's extension, as a phone's storage gives it. */
        private fun nameFor(mimeType: String, displayName: String): String {
            if (mimeType == Document.MIME_TYPE_DIR || mimeType == OCTET_STREAM) return displayName
            val types = MimeTypeMap.getSingleton()
            val wanted = types.getExtensionFromMimeType(mimeType) ?: return displayName
            val extension = displayName.substringAfterLast('.', "")
            val typeOfName = types.getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
            return if (typeOfName == mimeType || extension == wanted) {
                displayName
            } else {
                "$displayName.$wanted"
            }
        }

        /** [name] in [parent], or the first free "name (n).ext", as a phone's storage does. */
        private fun unique(parent: File, name: String): File {
            File(parent, name).takeUnless(File::exists)?.let { return it }
            val dot = name.lastIndexOf('.').takeIf { it > 0 }
            val stem = if (dot == null) name else name.substring(0, dot)
            val extension = if (dot == null) "" else name.substring(dot)
            return (1..MAX_SUFFIX).map { File(parent, "$stem ($it)$extension") }
                .firstOrNull { !it.exists() }
                ?: throw IllegalStateException("Too many documents share this name")
        }

        private fun mimeTypeOf(file: File): String = when {
            file.isDirectory -> Document.MIME_TYPE_DIR
            else -> MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
                ?: OCTET_STREAM
        }

        private const val MAX_SUFFIX = 32
        private const val OCTET_STREAM = "application/octet-stream"
    }
}
