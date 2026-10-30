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

/**
 * v1.0.2 item 5b: what deleting several saved files at once came to. Android asks the user
 * first about [asking] (files YFT may not delete on its own): the screen launches [request],
 * then deletes them again. Android 10 asks about one file at a time, so the files after it are
 * [waiting] for that answer before they are tried again.
 */
data class FilesDeletion(
    /** Gone, also those that were gone already. */
    val deleted: List<SavedDownloadFile> = emptyList(),
    /** Still there. */
    val failed: List<SavedDownloadFile> = emptyList(),
    /** Android's one request for all of [asking], or null when nothing needs asking. */
    val request: IntentSender? = null,
    val asking: List<SavedDownloadFile> = emptyList(),
    val waiting: List<SavedDownloadFile> = emptyList(),
)

/** Deletes a finished download's file: the Downloads screen's "Delete file" and the Library. */
fun interface DownloadedFileDeleter {
    suspend fun delete(file: SavedDownloadFile): FileDeletion

    /**
     * v1.0.2 item 5b: deletes [files] together, so that Android asks once for all of those that
     * need the user's consent where it can (Android 11 and later). This default deletes them
     * one at a time and stops at the first that needs consent; the rest wait.
     */
    suspend fun deleteAll(files: List<SavedDownloadFile>): FilesDeletion {
        val deleted = mutableListOf<SavedDownloadFile>()
        val failed = mutableListOf<SavedDownloadFile>()
        files.forEachIndexed { index, file ->
            when (val result = delete(file)) {
                FileDeletion.Deleted -> deleted += file
                FileDeletion.Failed -> failed += file
                is FileDeletion.NeedsConsent -> return FilesDeletion(
                    deleted = deleted,
                    failed = failed,
                    request = result.request,
                    asking = listOf(file),
                    waiting = files.drop(index + 1),
                )
            }
        }
        return FilesDeletion(deleted = deleted, failed = failed)
    }

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

    /**
     * Android 11+: the system's "Allow YFT to delete?" request; v1.0.2 item 5b: one request for
     * all of [uris].
     */
    fun mediaDeleteRequest(uris: List<String>): IntentSender

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
            when (val attempt = attempt(file)) {
                is Attempt.Done -> attempt.result
                is Attempt.Refused -> consentFor(attempt.uri, attempt.error)
            }
        }

    /**
     * v1.0.2 item 5b: tries every file first; then one Android request covers all the
     * MediaStore items YFT may not delete on its own (Android 11+). Android 10 asks about one
     * item at a time, so the items after it wait; before Android 10 there is nothing to ask.
     */
    override suspend fun deleteAll(files: List<SavedDownloadFile>): FilesDeletion =
        withContext(ioDispatcher) {
            val deleted = mutableListOf<SavedDownloadFile>()
            val failed = mutableListOf<SavedDownloadFile>()
            val refused = mutableListOf<Pair<SavedDownloadFile, Attempt.Refused>>()
            files.forEach { file ->
                when (val attempt = attempt(file)) {
                    is Attempt.Done -> when (attempt.result) {
                        FileDeletion.Deleted -> deleted += file
                        else -> failed += file
                    }

                    is Attempt.Refused -> refused += file to attempt
                }
            }
            when {
                refused.isEmpty() -> FilesDeletion(deleted = deleted, failed = failed)
                sdkInt >= Build.VERSION_CODES.R -> {
                    val request = runCatching {
                        system.mediaDeleteRequest(refused.map { (_, attempt) -> attempt.uri })
                    }.getOrNull()
                    if (request == null) {
                        FilesDeletion(deleted = deleted, failed = failed + refused.map { it.first })
                    } else {
                        FilesDeletion(deleted, failed, request, asking = refused.map { it.first })
                    }
                }

                sdkInt == Build.VERSION_CODES.Q -> askOneAtATime(deleted, failed, refused)
                else -> FilesDeletion(deleted = deleted, failed = failed + refused.map { it.first })
            }
        }

    /** Android 10: the first refused item with a user action asks; the ones after it wait. */
    private fun askOneAtATime(
        deleted: List<SavedDownloadFile>,
        failed: MutableList<SavedDownloadFile>,
        refused: List<Pair<SavedDownloadFile, Attempt.Refused>>,
    ): FilesDeletion {
        refused.forEachIndexed { index, (file, attempt) ->
            val request = runCatching { system.recoverableAction(attempt.error) }.getOrNull()
            if (request != null) {
                return FilesDeletion(
                    deleted = deleted,
                    failed = failed,
                    request = request,
                    asking = listOf(file),
                    waiting = refused.drop(index + 1).map { it.first },
                )
            }
            failed += file
        }
        return FilesDeletion(deleted = deleted, failed = failed)
    }

    /** One try at deleting [file]; a MediaStore item YFT may not delete is [Attempt.Refused]. */
    private fun attempt(file: SavedDownloadFile): Attempt = when (file.kind) {
        DownloadDestinationKind.MEDIA_STORE -> file.uri?.let(::deleteMediaItem)
        DownloadDestinationKind.SAF_DOCUMENT -> file.uri?.let(::deleteDocument)?.let(Attempt::Done)
        DownloadDestinationKind.APP_PRIVATE -> Attempt.Done(deleteAppFile(file.displayName))
    } ?: Attempt.Done(FileDeletion.Failed)

    private fun deleteMediaItem(uri: String): Attempt = try {
        val deleted = system.deleteMediaItem(uri) > 0
        if (deleted || !exists { system.mediaItemExists(uri) }) {
            Attempt.Done(FileDeletion.Deleted)
        } else {
            Attempt.Done(FileDeletion.Failed)
        }
    } catch (error: SecurityException) {
        Attempt.Refused(uri, error)
    } catch (_: IllegalArgumentException) {
        // An address MediaStore no longer knows.
        val stays = exists { system.mediaItemExists(uri) }
        Attempt.Done(if (stays) FileDeletion.Failed else FileDeletion.Deleted)
    } catch (_: UnsupportedOperationException) {
        Attempt.Done(FileDeletion.Failed)
    }

    private fun consentFor(uri: String, error: SecurityException): FileDeletion {
        val request = runCatching {
            when {
                sdkInt >= Build.VERSION_CODES.R -> system.mediaDeleteRequest(listOf(uri))
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

    /** What one try at deleting a file came to, before anyone is asked. */
    private sealed interface Attempt {
        data class Done(val result: FileDeletion) : Attempt

        /** A MediaStore item YFT may not delete without the user's consent. */
        data class Refused(val uri: String, val error: SecurityException) : Attempt
    }
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

    override fun mediaDeleteRequest(uris: List<String>): IntentSender =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, uris.map(Uri::parse)).intentSender
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
