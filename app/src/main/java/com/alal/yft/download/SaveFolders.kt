package com.alal.yft.download

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.alal.yft.core.model.settings.SaveFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry at the top of a chosen folder, as its documents provider lists it. */
data class FolderEntry(
    val documentId: String,
    /** The entry's document address under the folder's tree, which YFT may read and write. */
    val uri: String,
    val name: String,
    /** [Document.MIME_TYPE_DIR] for a folder. */
    val mimeType: String?,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR

    override fun toString(): String = "FolderEntry(name=$name, mimeType=$mimeType)"
}

/**
 * v1.0.2 item 28: the folder the user picks in Android's folder picker (the Storage Access
 * Framework) for new downloads. YFT keeps read and write access to it ([take]) and never gives
 * that access back, so the files saved there earlier still open after another choice. Before
 * each download it checks that it can still make files there ([usable]): a deleted folder, a
 * removed memory card or access the user took back sends the download to the default place.
 *
 * The plain functions block on the folder's provider; call them away from the main thread.
 */
interface SaveFolders {
    /** Keeps the access the picker gave to [treeUri]; null when YFT cannot save files there. */
    fun take(treeUri: String): SaveFolder?

    /** Whether YFT may still make files in [folder]. */
    fun usable(folder: SaveFolder): Boolean

    /** The entries at the top of [folder]; null when it cannot be read. */
    fun entries(folder: SaveFolder): List<FolderEntry>?

    /** [take] away from the main thread, for screens. */
    suspend fun takeInBackground(treeUri: String): SaveFolder? = take(treeUri)

    /** [usable] away from the main thread, for screens. */
    suspend fun usableInBackground(folder: SaveFolder): Boolean = usable(folder)

    /** No folder can be used: tests and screens without storage. */
    object None : SaveFolders {
        override fun take(treeUri: String): SaveFolder? = null

        override fun usable(folder: SaveFolder): Boolean = false

        override fun entries(folder: SaveFolder): List<FolderEntry>? = null
    }
}

/** [SaveFolders] on the phone, through [DocumentsContract] (no extra library). */
class AndroidSaveFolders(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SaveFolders {
    private val resolver get() = context.contentResolver

    override fun take(treeUri: String): SaveFolder? = guarded {
        val tree = treeOf(treeUri) ?: return@guarded null
        resolver.takePersistableUriPermission(tree, ACCESS)
        val root = root(tree)?.takeIf(Root::writable) ?: return@guarded null
        SaveFolder(treeUri = tree.toString(), name = root.name)
    }

    override fun usable(folder: SaveFolder): Boolean = guarded {
        val tree = treeOf(folder.treeUri) ?: return@guarded false
        val granted = resolver.persistedUriPermissions.any { permission ->
            permission.uri == tree && permission.isReadPermission && permission.isWritePermission
        }
        granted && root(tree)?.writable == true
    } ?: false

    override fun entries(folder: SaveFolder): List<FolderEntry>? = guarded {
        val tree = treeOf(folder.treeUri) ?: return@guarded null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        resolver.query(children, ENTRY_PROJECTION, null, null, null)?.use { cursor ->
            cursor.entries(tree)
        }
    }

    override suspend fun takeInBackground(treeUri: String): SaveFolder? =
        withContext(ioDispatcher) { take(treeUri) }

    override suspend fun usableInBackground(folder: SaveFolder): Boolean =
        withContext(ioDispatcher) { usable(folder) }

    private fun treeOf(value: String): Uri? = Uri.parse(value).takeIf { uri ->
        uri.scheme == "content" && DocumentsContract.isTreeUri(uri)
    }

    /** The folder itself: its name, and whether it is a folder new files can be made in. */
    private fun root(tree: Uri): Root? {
        val document = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        return resolver.query(document, ROOT_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE))
            val flags = cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME))
            Root(
                name = name?.takeIf(String::isNotBlank) ?: FALLBACK_NAME,
                writable = mimeType == Document.MIME_TYPE_DIR &&
                    flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0,
            )
        }
    }

    private fun Cursor.entries(tree: Uri): List<FolderEntry> = buildList {
        val idColumn = getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
        val nameColumn = getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
        val sizeColumn = getColumnIndexOrThrow(Document.COLUMN_SIZE)
        val modifiedColumn = getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
        while (moveToNext()) {
            val id = getString(idColumn)?.takeIf(String::isNotBlank) ?: continue
            val name = getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
            add(
                FolderEntry(
                    documentId = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                    name = name,
                    mimeType = getString(mimeColumn),
                    sizeBytes = if (isNull(sizeColumn)) null else getLong(sizeColumn),
                    modifiedAtEpochMs = if (isNull(modifiedColumn)) {
                        null
                    } else {
                        getLong(modifiedColumn).takeIf { it > 0L }
                    },
                ),
            )
        }
    }

    /** A provider that is gone, refuses YFT or breaks gives no answer, never a crash. */
    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private data class Root(val name: String, val writable: Boolean)

    private companion object {
        const val ACCESS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val FALLBACK_NAME = "Chosen folder"
        val ROOT_PROJECTION = arrayOf(
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
        )
        val ENTRY_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
