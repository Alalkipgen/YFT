package com.alal.yft.core.download

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID

/**
 * A MediaStore destination that remains pending until the transfer engine verifies every byte.
 *
 * Pending MediaStore items require Android 10 (API 29). Older devices should use a persisted SAF
 * tree destination so an incomplete file is never exposed with its final name.
 */
class MediaStoreDownloadDestination private constructor(
    private val store: PublicContentStore,
    private val pendingItemUri: String,
    /** How the row was made, to make another one ([renew]); null for a reopened row. */
    private val request: PendingMediaRequest? = null,
) : DownloadDestination {
    @Volatile
    private var lifecycle = DestinationLifecycle.ACTIVE

    override val recoveryUri: String = pendingItemUri
    override val publishedUri: String = pendingItemUri

    override fun prepare(expectedLength: Long?) {
        require(expectedLength == null || expectedLength >= 0)
        check(lifecycle == DestinationLifecycle.ACTIVE) {
            "Download destination is not writable"
        }
        store.open(pendingItemUri).use { output ->
            if (expectedLength != null) output.setLength(expectedLength)
            output.sync()
        }
    }

    override fun temporaryLength(): Long? = store.length(pendingItemUri)

    override fun open(): SeekableDownloadOutput {
        check(lifecycle == DestinationLifecycle.ACTIVE) {
            "Download destination is not writable"
        }
        return store.open(pendingItemUri)
    }

    @Synchronized
    override fun commit() {
        if (lifecycle == DestinationLifecycle.COMMITTED) return
        check(lifecycle == DestinationLifecycle.ACTIVE) {
            "Download destination was discarded"
        }
        store.publishPendingMedia(pendingItemUri)
        lifecycle = DestinationLifecycle.COMMITTED
    }

    @Synchronized
    override fun discard() {
        if (lifecycle != DestinationLifecycle.ACTIVE) return
        store.deletePendingMedia(pendingItemUri)
        lifecycle = DestinationLifecycle.DISCARDED
    }

    /**
     * Deletes this pending row first, so the new row gets the same name, then makes the new
     * one; a row that cannot be deleted is left for the storage janitor.
     */
    @Synchronized
    override fun renew(): DownloadDestination? {
        val request = request ?: return null
        check(lifecycle != DestinationLifecycle.COMMITTED) { "Download destination was published" }
        runCatching { discard() }
        return create(
            store = store,
            displayName = request.displayName,
            mimeType = request.mimeType,
            relativePath = request.relativePath,
        )
    }

    private data class PendingMediaRequest(
        val displayName: String,
        val mimeType: String?,
        val relativePath: String,
    )

    companion object {
        const val DEFAULT_RELATIVE_PATH = "Download/YFT/"

        /**
         * Creates the pending row immediately so [recoveryUri] can be persisted with the task.
         */
        fun create(
            resolver: ContentResolver,
            displayName: String,
            mimeType: String?,
            relativePath: String = DEFAULT_RELATIVE_PATH,
        ): MediaStoreDownloadDestination = create(
            store = AndroidPublicContentStore(resolver),
            displayName = displayName,
            mimeType = mimeType,
            relativePath = relativePath,
        )

        /** Reopens a still-pending MediaStore row after the caller refreshes its source link. */
        fun resume(
            resolver: ContentResolver,
            pendingItemUri: String,
        ): MediaStoreDownloadDestination = resume(
            store = AndroidPublicContentStore(resolver),
            pendingItemUri = pendingItemUri,
        )

        internal fun create(
            store: PublicContentStore,
            displayName: String,
            mimeType: String?,
            relativePath: String = DEFAULT_RELATIVE_PATH,
        ): MediaStoreDownloadDestination {
            val uri = store.createPendingMedia(
                displayName = sanitizePublicFileName(displayName),
                mimeType = normalizeMimeType(mimeType),
                relativePath = normalizeRelativePath(relativePath),
            )
            return MediaStoreDownloadDestination(
                store = store,
                pendingItemUri = requireContentUri(uri),
                request = PendingMediaRequest(displayName, mimeType, relativePath),
            )
        }

        internal fun resume(
            store: PublicContentStore,
            pendingItemUri: String,
        ): MediaStoreDownloadDestination =
            MediaStoreDownloadDestination(store, requireContentUri(pendingItemUri))
    }
}

/**
 * A SAF tree destination that writes to a uniquely named temporary sibling and renames only after
 * integrity verification. The caller must persist read/write permission for the selected tree.
 */
class SafDownloadDestination private constructor(
    private val store: PublicContentStore,
    private val temporaryDocumentUri: String,
    private val finalDisplayName: String,
    /** How the document was made, to make another one ([renew]); null for a reopened one. */
    private val request: TemporaryDocumentRequest? = null,
) : DownloadDestination {
    @Volatile
    private var committedUri: String? = null

    @Volatile
    private var lifecycle = DestinationLifecycle.ACTIVE

    override val recoveryUri: String = temporaryDocumentUri
    override val publishedUri: String
        get() = committedUri ?: temporaryDocumentUri

    override fun prepare(expectedLength: Long?) {
        require(expectedLength == null || expectedLength >= 0)
        check(lifecycle == DestinationLifecycle.ACTIVE) {
            "Download destination is not writable"
        }
        store.open(temporaryDocumentUri).use { output ->
            if (expectedLength != null) output.setLength(expectedLength)
            output.sync()
        }
    }

    override fun temporaryLength(): Long? = store.length(temporaryDocumentUri)

    override fun open(): SeekableDownloadOutput {
        check(lifecycle == DestinationLifecycle.ACTIVE) {
            "Download destination is not writable"
        }
        return store.open(temporaryDocumentUri)
    }

    @Synchronized
    override fun commit() {
        if (lifecycle == DestinationLifecycle.COMMITTED) return
        check(lifecycle == DestinationLifecycle.ACTIVE) {
            "Download destination was discarded"
        }
        committedUri = requireContentUri(
            store.renameTemporaryDocument(
                temporaryDocumentUri = temporaryDocumentUri,
                finalDisplayName = finalDisplayName,
            ),
        )
        lifecycle = DestinationLifecycle.COMMITTED
    }

    @Synchronized
    override fun discard() {
        if (lifecycle != DestinationLifecycle.ACTIVE) return
        store.deleteTemporaryDocument(temporaryDocumentUri)
        lifecycle = DestinationLifecycle.DISCARDED
    }

    /** Deletes this temporary document first, so the new one gets the same name. */
    @Synchronized
    override fun renew(): DownloadDestination? {
        val request = request ?: return null
        check(lifecycle != DestinationLifecycle.COMMITTED) { "Download destination was published" }
        runCatching { discard() }
        return create(
            store = store,
            treeUri = request.treeUri,
            displayName = request.displayName,
            mimeType = request.mimeType,
            temporaryId = request.temporaryId,
        )
    }

    private data class TemporaryDocumentRequest(
        val treeUri: String,
        val displayName: String,
        val mimeType: String?,
        val temporaryId: String,
    )

    companion object {
        /**
         * Creates a temporary document immediately so its URI can be persisted for recovery.
         *
         * [temporaryId] should be a task ID when one already exists. It is sanitized and is never
         * interpreted as a path.
         */
        fun create(
            resolver: ContentResolver,
            treeUri: String,
            displayName: String,
            mimeType: String?,
            temporaryId: String = UUID.randomUUID().toString(),
        ): SafDownloadDestination = create(
            store = AndroidPublicContentStore(resolver),
            treeUri = treeUri,
            displayName = displayName,
            mimeType = mimeType,
            temporaryId = temporaryId,
        )

        /** Reopens a temporary SAF document after the caller refreshes its source link. */
        fun resume(
            resolver: ContentResolver,
            temporaryDocumentUri: String,
            displayName: String,
        ): SafDownloadDestination = resume(
            store = AndroidPublicContentStore(resolver),
            temporaryDocumentUri = temporaryDocumentUri,
            displayName = displayName,
        )

        internal fun create(
            store: PublicContentStore,
            treeUri: String,
            displayName: String,
            mimeType: String?,
            temporaryId: String,
        ): SafDownloadDestination {
            val finalName = sanitizePublicFileName(displayName)
            val safeId = sanitizeTemporaryId(temporaryId)
            val temporaryName = ".$finalName.yft-$safeId.part"
            val uri = store.createTemporaryDocument(
                treeUri = requireContentUri(treeUri),
                temporaryDisplayName = temporaryName,
                mimeType = normalizeMimeType(mimeType),
            )
            return SafDownloadDestination(
                store = store,
                temporaryDocumentUri = requireContentUri(uri),
                finalDisplayName = finalName,
                request = TemporaryDocumentRequest(treeUri, displayName, mimeType, temporaryId),
            )
        }

        internal fun resume(
            store: PublicContentStore,
            temporaryDocumentUri: String,
            displayName: String,
        ): SafDownloadDestination = SafDownloadDestination(
            store = store,
            temporaryDocumentUri = requireContentUri(temporaryDocumentUri),
            finalDisplayName = sanitizePublicFileName(displayName),
        )
    }
}

internal interface PublicContentStore {
    fun createPendingMedia(
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): String

    fun publishPendingMedia(uri: String)
    fun deletePendingMedia(uri: String)

    fun createTemporaryDocument(
        treeUri: String,
        temporaryDisplayName: String,
        mimeType: String,
    ): String

    fun renameTemporaryDocument(
        temporaryDocumentUri: String,
        finalDisplayName: String,
    ): String

    fun deleteTemporaryDocument(uri: String)
    fun length(uri: String): Long?
    fun open(uri: String): SeekableDownloadOutput
}

internal class AndroidPublicContentStore(
    private val resolver: ContentResolver,
) : PublicContentStore {
    override fun createPendingMedia(
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("Pending MediaStore downloads require Android 10 or newer")
        }
        return createPendingMediaOnAndroid10(displayName, mimeType, relativePath)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun createPendingMediaOnAndroid10(
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): String = storageCall("Cannot create pending MediaStore item") {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?.toString()
            ?: throw IOException("Cannot create pending MediaStore item")
    }

    override fun publishPendingMedia(uri: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("Pending MediaStore downloads require Android 10 or newer")
        }
        publishPendingMediaOnAndroid10(parseContentUri(uri))
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun publishPendingMediaOnAndroid10(uri: Uri) {
        storageCall("Cannot publish MediaStore item") {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(uri, values, null, null) != 1) {
                throw IOException("Cannot publish MediaStore item")
            }
        }
    }

    override fun deletePendingMedia(uri: String) {
        storageCall("Cannot discard pending MediaStore item") {
            if (resolver.delete(parseContentUri(uri), null, null) != 1) {
                throw IOException("Cannot discard pending MediaStore item")
            }
        }
    }

    override fun createTemporaryDocument(
        treeUri: String,
        temporaryDisplayName: String,
        mimeType: String,
    ): String = storageCall("Cannot create temporary SAF document") {
        val tree = parseContentUri(treeUri)
        if (!DocumentsContract.isTreeUri(tree)) {
            throw IOException("SAF destination must be a document tree")
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        DocumentsContract.createDocument(
            resolver,
            parent,
            mimeType,
            temporaryDisplayName,
        )?.toString() ?: throw IOException("Cannot create temporary SAF document")
    }

    override fun renameTemporaryDocument(
        temporaryDocumentUri: String,
        finalDisplayName: String,
    ): String = storageCall("Cannot publish SAF document") {
        DocumentsContract.renameDocument(
            resolver,
            parseContentUri(temporaryDocumentUri),
            finalDisplayName,
        )?.toString() ?: throw IOException("Cannot publish SAF document")
    }

    override fun deleteTemporaryDocument(uri: String) {
        storageCall("Cannot discard temporary SAF document") {
            if (!DocumentsContract.deleteDocument(resolver, parseContentUri(uri))) {
                throw IOException("Cannot discard temporary SAF document")
            }
        }
    }

    override fun length(uri: String): Long? = storageCall("Cannot inspect destination") {
        val contentUri = parseContentUri(uri)
        val descriptor = try {
            resolver.openFileDescriptor(contentUri, "r")
        } catch (_: FileNotFoundException) {
            // A new pending MediaStore row has no file until its first "rw" open (P20).
            return@storageCall lengthOfRowWithoutFile(contentUri)
        }
        descriptor?.use { opened ->
            opened.statSize.takeIf { it >= 0 }?.let { return@storageCall it }
        }
        querySize(contentUri)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                cursor.getLong(0).takeIf { it >= 0 }
            } else {
                null
            }
        }
    }

    /**
     * A row whose file cannot be opened holds no bytes yet, so its length is null, like a
     * [FileDownloadDestination] that was never written; its stored size is not trusted, because
     * a resume must never count bytes that are not there. A missing row is a storage failure.
     */
    private fun lengthOfRowWithoutFile(contentUri: Uri): Long? {
        val rowExists = querySize(contentUri, includePending = true)
            ?.use { cursor -> cursor.moveToFirst() }
            ?: false
        if (!rowExists) throw FileNotFoundException("Destination is missing")
        return null
    }

    private fun querySize(contentUri: Uri, includePending: Boolean = false): Cursor? {
        val projection = arrayOf(OpenableColumns.SIZE)
        val mediaItem = includePending && contentUri.authority == MediaStore.AUTHORITY
        return when {
            mediaItem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                queryIncludingPendingOnAndroid11(contentUri, projection)
            mediaItem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                resolver.query(includePendingOnAndroid10(contentUri), projection, null, null, null)
            else -> resolver.query(contentUri, projection, null, null, null)
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun queryIncludingPendingOnAndroid11(
        contentUri: Uri,
        projection: Array<String>,
    ): Cursor? = resolver.query(
        contentUri,
        projection,
        Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        },
        null,
    )

    @Suppress("DEPRECATION")
    @TargetApi(Build.VERSION_CODES.Q)
    private fun includePendingOnAndroid10(contentUri: Uri): Uri =
        MediaStore.setIncludePending(contentUri)

    override fun open(uri: String): SeekableDownloadOutput =
        storageCall("Cannot open destination") {
            val descriptor = resolver.openFileDescriptor(parseContentUri(uri), "rw")
                ?: throw IOException("Cannot open destination")
            try {
                ParcelFileSeekableDownloadOutput(descriptor)
            } catch (failure: Exception) {
                runCatching { descriptor.close() }
                throw failure
            }
        }

    private fun parseContentUri(value: String): Uri {
        val uri = Uri.parse(requireContentUri(value))
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw IOException("Destination must use a content URI")
        }
        return uri
    }
}

private class ParcelFileSeekableDownloadOutput(
    descriptor: ParcelFileDescriptor,
) : SeekableDownloadOutput {
    private val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
    private val channel: FileChannel = output.channel

    override fun write(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        byteCount: Int,
    ) {
        require(position >= 0)
        require(offset >= 0 && byteCount >= 0 && offset <= buffer.size - byteCount)
        seekableWrite {
            val source = ByteBuffer.wrap(buffer, offset, byteCount)
            var writePosition = position
            while (source.hasRemaining()) {
                val written = channel.write(source, writePosition)
                if (written <= 0) throw IOException("Destination stopped accepting bytes")
                writePosition += written
            }
        }
    }

    override fun setLength(length: Long) {
        require(length >= 0)
        seekableWrite {
            val currentLength = channel.size()
            when {
                length < currentLength -> channel.truncate(length)
                length > currentLength -> {
                    channel.write(ByteBuffer.wrap(byteArrayOf(0)), length - 1)
                }
            }
        }
    }

    override fun sync() {
        seekableWrite { channel.force(true) }
    }

    override fun close() {
        output.close()
    }

    private inline fun <T> seekableWrite(block: () -> T): T =
        try {
            block()
        } catch (failure: IOException) {
            throw failure
        } catch (failure: RuntimeException) {
            throw IOException("Destination does not support seekable writes", failure)
        }
}

private inline fun <T> storageCall(
    failureMessage: String,
    block: () -> T,
): T = try {
    block()
} catch (failure: IOException) {
    throw failure
} catch (failure: SecurityException) {
    throw IOException(failureMessage, failure)
} catch (failure: RuntimeException) {
    throw IOException(failureMessage, failure)
}

private fun requireContentUri(value: String): String {
    val normalized = value.trim()
    require(normalized.startsWith("content://", ignoreCase = true)) {
        "Destination must use a content URI"
    }
    return normalized
}

private fun sanitizePublicFileName(value: String): String {
    val sanitized = buildString {
        value.trim().forEach { character ->
            append(
                when {
                    character == '/' || character == '\\' -> '_'
                    character.code < 0x20 || character.code == 0x7f -> '_'
                    else -> character
                },
            )
        }
    }.trim(' ', '.')
        .take(MAX_PUBLIC_FILE_NAME_CHARS)
    return sanitized.ifBlank { "download" }
}

private fun sanitizeTemporaryId(value: String): String {
    val sanitized = value
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .take(MAX_TEMPORARY_ID_CHARS)
    return sanitized.ifBlank { UUID.randomUUID().toString() }
}

private fun normalizeMimeType(value: String?): String {
    val normalized = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(MIME_TYPE_PATTERN::matches)
    return normalized ?: DEFAULT_MIME_TYPE
}

private fun normalizeRelativePath(value: String): String {
    val segments = value
        .replace('\\', '/')
        .split('/')
        .filter(String::isNotBlank)
    require(segments.isNotEmpty()) { "Relative path must not be empty" }
    require(segments.none { segment ->
        segment == "." ||
            segment == ".." ||
            segment.any { it.code < 0x20 || it.code == 0x7f }
    }) {
        "Relative path contains an unsafe segment"
    }
    return segments.joinToString(separator = "/", postfix = "/")
}

private const val DEFAULT_MIME_TYPE = "application/octet-stream"
private const val MAX_PUBLIC_FILE_NAME_CHARS = 180
private const val MAX_TEMPORARY_ID_CHARS = 64
private val MIME_TYPE_PATTERN =
    Regex("""[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+""")

private enum class DestinationLifecycle {
    ACTIVE,
    COMMITTED,
    DISCARDED,
}
