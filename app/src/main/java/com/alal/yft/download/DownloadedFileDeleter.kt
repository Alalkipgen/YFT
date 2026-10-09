package com.alal.yft.download

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.feature.library.AppPrivateDownloads
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A finished download's file, where its record says it was saved (P42). */
data class SavedDownloadFile(
    val kind: DownloadDestinationKind,
    /** The MediaStore item or SAF document; unused for app storage. */
    val uri: String?,
    /** The file's name, which finds an app-private file. */
    val displayName: String,
)

/** What deleting a saved file came to (P42). */
sealed interface FileDeletion {
    /** The file is gone, also when it was gone already. */
    data object Deleted : FileDeletion

    /**
     * Android asks the user first (an item YFT no longer owns, for example after a reinstall):
     * the screen launches [request], then deletes again.
     */
    data class NeedsConsent(val request: IntentSender) : FileDeletion

    /** The file is still there. */
    data object Failed : FileDeletion
}

/** Deletes a finished download's file: the Downloads screen's "Delete file" and the Library. */
fun interface DownloadedFileDeleter {
    suspend fun delete(file: SavedDownloadFile): FileDeletion

    companion object {
        /** For tests and previews without storage: nothing is deleted. */
        val Unavailable = DownloadedFileDeleter { FileDeletion.Failed }
    }
}

/** The Android calls [AndroidDownloadedFileDeleter] makes, so each path is tested with fakes. */
interface FileDeleteSystem {
    /** Rows deleted; throws [SecurityException] when YFT may not delete the item. */
    fun deleteMediaItem(uri: String): Int

    fun mediaItemExists(uri: String): Boolean

    /** Android 11+: the system's "Allow YFT to delete this file?" request. */
    fun mediaDeleteRequest(uri: String): IntentSender

    /** Android 10: the user action of a [RecoverableSecurityException], else null. */
    fun recoverableAction(error: SecurityException): IntentSender?

    /** True when deleted; throws [FileNotFoundException] when the document is gone. */
    fun deleteDocument(uri: String): Boolean

    fun documentExists(uri: String): Boolean

    /** YFT's finished app-private file of that name, or null for a name that is not one. */
    fun appFile(displayName: String): File?
}

/**
 * Deletes by the record's destination (P42): YFT's MediaStore item through the content
 * resolver (asking the user when Android requires it), a SAF document through
 * DocumentsContract and an app-private file directly. A file that is already gone counts as
 * deleted, so its row can go too.
 */
class AndroidDownloadedFileDeleter(
    private val system: FileDeleteSystem,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadedFileDeleter {
    override suspend fun delete(file: SavedDownloadFile): FileDeletion =
        withContext(ioDispatcher) {
            when (file.kind) {
                DownloadDestinationKind.MEDIA_STORE -> file.uri?.let(::deleteMediaItem)
                DownloadDestinationKind.SAF_DOCUMENT -> file.uri?.let(::deleteDocument)
                DownloadDestinationKind.APP_PRIVATE -> deleteAppFile(file.displayName)
            } ?: FileDeletion.Failed
        }

    private fun deleteMediaItem(uri: String): FileDeletion = try {
        val deleted = system.deleteMediaItem(uri) > 0
        if (deleted || !exists { system.mediaItemExists(uri) }) {
            FileDeletion.Deleted
        } else {
            FileDeletion.Failed
        }
    } catch (error: SecurityException) {
        consentFor(uri, error)
    } catch (_: IllegalArgumentException) {
        // An address MediaStore no longer knows.
        if (exists { system.mediaItemExists(uri) }) FileDeletion.Failed else FileDeletion.Deleted
    } catch (_: UnsupportedOperationException) {
        FileDeletion.Failed
    }

    private fun consentFor(uri: String, error: SecurityException): FileDeletion {
        val request = runCatching {
            when {
                sdkInt >= Build.VERSION_CODES.R -> system.mediaDeleteRequest(uri)
                sdkInt == Build.VERSION_CODES.Q -> system.recoverableAction(error)
                else -> null
            }
        }.getOrNull()
        return request?.let(FileDeletion::NeedsConsent) ?: FileDeletion.Failed
    }

    private fun deleteDocument(uri: String): FileDeletion = try {
        val deleted = system.deleteDocument(uri)
        if (deleted || !exists { system.documentExists(uri) }) {
            FileDeletion.Deleted
        } else {
            FileDeletion.Failed
        }
    } catch (_: FileNotFoundException) {
        FileDeletion.Deleted
    } catch (_: SecurityException) {
        if (exists { system.documentExists(uri) }) FileDeletion.Failed else FileDeletion.Deleted
    } catch (_: IllegalArgumentException) {
        if (exists { system.documentExists(uri) }) FileDeletion.Failed else FileDeletion.Deleted
    } catch (_: UnsupportedOperationException) {
        FileDeletion.Failed
    }

    private fun deleteAppFile(displayName: String): FileDeletion {
        val file = system.appFile(displayName) ?: return FileDeletion.Failed
        return if (!file.exists() || file.delete() || !file.exists()) {
            FileDeletion.Deleted
        } else {
            FileDeletion.Failed
        }
    }

    /** Whether the file is still there; an unanswered question counts as yes. */
    private inline fun exists(check: () -> Boolean): Boolean =
        runCatching(check).getOrDefault(true)
}

/** [FileDeleteSystem] on the phone. */
class AndroidFileDeleteSystem(private val context: Context) : FileDeleteSystem {
    private val resolver get() = context.contentResolver

    override fun deleteMediaItem(uri: String): Int = resolver.delete(Uri.parse(uri), null, null)

    override fun mediaItemExists(uri: String): Boolean = resolver.query(
        Uri.parse(uri),
        arrayOf(MediaStore.MediaColumns._ID),
        null,
        null,
        null,
    )?.use { it.moveToFirst() } ?: false

    override fun mediaDeleteRequest(uri: String): IntentSender =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, listOf(Uri.parse(uri))).intentSender
        } else {
            throw UnsupportedOperationException("Delete requests need Android 11")
        }

    override fun recoverableAction(error: SecurityException): IntentSender? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> null
        error is RecoverableSecurityException -> error.userAction.actionIntent.intentSender
        else -> null
    }

    override fun deleteDocument(uri: String): Boolean =
        DocumentsContract.deleteDocument(resolver, Uri.parse(uri))

    override fun documentExists(uri: String): Boolean = try {
        resolver.query(
            Uri.parse(uri),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } ?: false
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun appFile(displayName: String): File? {
        val root = AppPrivateDownloads.root(context)
        AppPrivateDownloads.resolve(root, displayName)?.let { return it }
        // A plain name whose file is gone: there is nothing left to delete.
        val plain = displayName.isNotBlank() && displayName != "." && displayName != ".." &&
            displayName.none { it == '/' || it == '\\' || it == '\u0000' }
        return File(root, displayName).takeIf { plain && !it.exists() }
    }
}
