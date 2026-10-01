package com.alal.yft.core.download

import java.io.Closeable
import java.io.File
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
        if (!partialFile.isFile) throw IOException("Partial download is missing")
        if (completedFile.exists()) throw IOException("Completed destination already exists")
        if (!partialFile.renameTo(completedFile)) {
            throw IOException("Cannot publish completed download")
        }
    }

    override fun discard() {
        if (partialFile.exists() && !partialFile.delete()) {
            throw IOException("Cannot delete partial download")
        }
    }
}

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
