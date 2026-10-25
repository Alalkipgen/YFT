package com.alal.yft.extractor.master.solver

import java.io.File
import java.io.FileOutputStream

/**
 * Phase 1.2 N5b A.4/A.5: where a small solver store (the locator cache, the V ledger) keeps its
 * text between process runs. Neither call throws: a store that cannot read starts empty, one that
 * cannot write stays in memory.
 */
interface OwnSolverTextFile {
    /** The stored text, or null (none yet, too long, or unreadable). */
    fun read(): String?

    /** Replaces the stored text; false when it could not. */
    fun write(text: String): Boolean
}

/**
 * An app-private file written whole and atomically: the text goes to a temporary file next to it,
 * which then replaces it in one rename, so a reader (or the next process after a crash) sees the
 * old text or the new one, never a part. [locate] runs on first use, off the caller's thread of
 * construction (the app names a file under `filesDir`).
 */
class AtomicTextFile(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val locate: () -> File,
) : OwnSolverTextFile {
    constructor(file: File, maxChars: Int = DEFAULT_MAX_CHARS) : this(maxChars, { file })

    init {
        require(maxChars > 0)
    }

    private val file: File? by lazy { runCatching(locate).getOrNull() }

    @Synchronized
    override fun read(): String? = try {
        val target = file
        if (target == null || !target.isFile || target.length() > maxChars.toLong() * MAX_BYTES_PER_CHAR) {
            null
        } else {
            target.readText(Charsets.UTF_8).takeIf { it.length <= maxChars }
        }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    override fun write(text: String): Boolean {
        if (text.length > maxChars) return false
        val target = file ?: return false
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        return try {
            target.parentFile?.mkdirs()
            FileOutputStream(temp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            temp.renameTo(target) || run {
                temp.delete()
                false
            }
        } catch (_: Exception) {
            runCatching { temp.delete() }
            false
        }
    }

    companion object {
        /** The stores are small (≤ 32 locator entries, ≤ 64 V keys): a few KiB. */
        const val DEFAULT_MAX_CHARS: Int = 256 * 1024

        private const val MAX_BYTES_PER_CHAR = 3
        private const val TEMP_SUFFIX = ".tmp"
    }
}
