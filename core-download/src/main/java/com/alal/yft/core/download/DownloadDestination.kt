package com.alal.yft.core.download

import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.RandomAccessFile

/** Seekable temporary destination that is published only after verified completion. */
interface DownloadDestination {
    /**
     * Stable, non-sensitive URI used to reopen an incomplete public destination after recovery.
     *
     * App-private file destinations intentionally return null.
     */
    val recoveryUri: String?
        get() = null

    /**
     * URI of the published item after [commit]. This may differ from [recoveryUri] when a
     * document provider assigns a new URI while renaming a temporary SAF document.
     */
    val publishedUri: String?
        get() = recoveryUri

    fun prepare(expectedLength: Long?)
    fun temporaryLength(): Long?
    fun open(): SeekableDownloadOutput
    fun commit()
    fun discard()

    /**
     * Starts over (P21): discards this destination and returns a new, empty one of the same kind
     * and name, so a Retry that cannot use the old partial file writes again from byte 0.
     *
     * Returns null, without discarding anything, when this destination cannot make a new one
     * (for example one reopened from a saved address); the caller keeps using it. Throws when
     * the new one cannot be made; this one may be discarded by then, and a later call tries
     * again.
     */
    fun renew(): DownloadDestination? = null

    /**
     * This destination's temporary file as a seekable read-write file descriptor, emptied (P27),
     * for a writer that needs one: Android's MediaMuxer then writes a merged file straight into
     * it, without a second copy. Call after [prepare]; close it before [temporaryLength] and
     * [commit].
     *
     * Returns null (the default) when this destination cannot give one, for example a document
     * whose descriptor is a pipe; the caller then writes through [open]. Throws when the file
     * cannot be opened.
     */
    fun openFileDescriptorOutput(): FileDescriptorOutput? = null

    /**
     * D1: gives the file [commit] publishes the extension [extension] and the type [mimeType],
     * for a download kept in another format than its name says (an HLS download that cannot be
     * remuxed into an MP4 stays MPEG-TS). Call after the bytes are verified, before [commit].
     *
     * Returns the new file name, or null (the default) when this destination cannot be renamed;
     * the caller then publishes nothing. Throws when the rename fails.
     */
    fun renameExtension(extension: String, mimeType: String): String? = null
}

/**
 * D1: [name] with its extension, a short run of letters and digits after its last dot, replaced
 * by [extension], or with [extension] added when it has none.
 */
internal fun withExtension(name: String, extension: String): String {
    val dot = name.lastIndexOf('.')
    val current = if (dot > 0) name.substring(dot + 1) else ""
    val isExtension = current.length in 1..MAX_EXTENSION_CHARS &&
        current.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    val stem = if (isExtension) name.substring(0, dot) else name
    return "$stem.$extension"
}

private const val MAX_EXTENSION_CHARS = 8

/** A destination's file open for reading and writing at any position (P27). */
interface FileDescriptorOutput : Closeable {
    val fileDescriptor: FileDescriptor
}

interface SeekableDownloadOutput : Closeable {
    @Throws(IOException::class)
    fun write(position: Long, buffer: ByteArray, offset: Int, byteCount: Int)

    @Throws(IOException::class)
    fun setLength(length: Long)

    @Throws(IOException::class)
    fun sync()
}

/**
 * Stages bytes in a sibling partial file and uses a same-directory rename as the publish step.
 */
class FileDownloadDestination(
    private val partialFile: File,
    private val completedFile: File,
) : DownloadDestination {
    /** Where [commit] publishes: [completedFile], or the sibling [renameExtension] chose (D1). */
    @Volatile
    private var publishedFile: File = completedFile

    init {
        require(partialFile.absoluteFile != completedFile.absoluteFile)
        require(partialFile.absoluteFile.parentFile == completedFile.absoluteFile.parentFile) {
            "Partial and completed files must share a directory"
        }
    }

    override fun prepare(expectedLength: Long?) {
        require(expectedLength == null || expectedLength >= 0)
        val parent = partialFile.parentFile
            ?: throw IOException("Download destination has no parent directory")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create download directory")
        }
        RandomAccessFile(partialFile, "rw").use { output ->
            if (expectedLength != null) output.setLength(expectedLength)
        }
    }

    override fun temporaryLength(): Long? = partialFile
        .takeIf(File::isFile)
        ?.length()

    override fun open(): SeekableDownloadOutput = FileSeekableDownloadOutput(partialFile)

    override fun commit() {
        val target = publishedFile
        if (!partialFile.isFile) throw IOException("Partial download is missing")
        if (target.exists()) throw IOException("Completed destination already exists")
        if (!partialFile.renameTo(target)) {
            throw IOException("Cannot publish completed download")
        }
    }

    /**
     * D1: the completed file's name with [extension], made unique the way the app picks names
     * (a published file or another download's `.part` reservation takes it): "name (n).ext".
     * [mimeType] is not stored; the extension says it.
     */
    @Synchronized
    override fun renameExtension(extension: String, mimeType: String): String {
        require(extension.isNotBlank() && extension.none { it == '/' || it == '\\' })
        val parent = completedFile.absoluteFile.parentFile
            ?: throw IOException("Download destination has no parent directory")
        val wanted = withExtension(completedFile.name, extension)
        if (wanted == completedFile.name) {
            publishedFile = completedFile
            return wanted
        }
        val existing = parent.list().orEmpty().toSet()
        val stem = wanted.substring(0, wanted.length - extension.length - 1)
        val name = sequenceOf(wanted)
            .plus((1..MAX_NAME_SUFFIX).asSequence().map { "$stem ($it).$extension" })
            .firstOrNull { candidate -> !existing.taken(candidate) }
            ?: throw IOException("Too many downloads share this name")
        publishedFile = File(parent, name)
        return name
    }

    private fun Set<String>.taken(name: String): Boolean =
        name in this || any { it.startsWith("$name.") && it.endsWith(".part") }

    override fun discard() {
        if (partialFile.exists() && !partialFile.delete()) {
            throw IOException("Cannot delete partial download")
        }
    }

    /** Empties the partial file instead of deleting it, so its name stays reserved. */
    override fun renew(): DownloadDestination {
        val parent = partialFile.parentFile
            ?: throw IOException("Download destination has no parent directory")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create download directory")
        }
        RandomAccessFile(partialFile, "rw").use { output -> output.setLength(0) }
        return FileDownloadDestination(partialFile = partialFile, completedFile = completedFile)
    }

    override fun openFileDescriptorOutput(): FileDescriptorOutput {
        val file = RandomAccessFile(partialFile, "rw")
        try {
            file.setLength(0)
        } catch (failure: IOException) {
            runCatching { file.close() }
            throw failure
        }
        return object : FileDescriptorOutput {
            override val fileDescriptor: FileDescriptor = file.fd

            override fun close() = file.close()
        }
    }
}

private const val MAX_NAME_SUFFIX = 999

class FileSeekableDownloadOutput(file: File) : SeekableDownloadOutput {
    private val output = RandomAccessFile(file, "rw")

    override fun write(position: Long, buffer: ByteArray, offset: Int, byteCount: Int) {
        require(position >= 0)
        require(offset >= 0 && byteCount >= 0 && offset <= buffer.size - byteCount)
        output.seek(position)
        output.write(buffer, offset, byteCount)
    }

    override fun setLength(length: Long) {
        require(length >= 0)
        output.setLength(length)
    }

    override fun sync() {
        output.fd.sync()
    }

    override fun close() {
        output.close()
    }
}
