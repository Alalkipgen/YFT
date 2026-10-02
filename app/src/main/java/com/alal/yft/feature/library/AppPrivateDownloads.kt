package com.alal.yft.feature.library

import android.content.Context
import com.alal.yft.download.AndroidDownloadDestinationProvider
import java.io.File

/**
 * The single directory app-private downloads are published in, and the rules for reading it.
 *
 * Staging files (`name.<id>.part`) live in the same directory while a transfer runs; they are
 * never listed, opened or shared.
 */
internal object AppPrivateDownloads {
    private const val PARTIAL_SUFFIX = ".part"

    fun root(context: Context): File = File(
        context.noBackupFilesDir,
        AndroidDownloadDestinationProvider.APP_PRIVATE_DIRECTORY,
    )

    fun isPartial(name: String): Boolean = name.endsWith(PARTIAL_SUFFIX)

    /** Published files directly inside [root]; staging files and directories are skipped. */
    fun finishedFiles(root: File): List<File> = root.listFiles().orEmpty()
        .filter { it.isFile && !isPartial(it.name) }

    /**
     * The published file called [name] directly inside [root], or null.
     *
     * Anything that could leave the directory is refused: separators, `.` and `..`, NUL, links
     * that resolve elsewhere, directories and staging files.
     */
    fun resolve(root: File, name: String?): File? {
        if (name.isNullOrBlank() || name == "." || name == "..") return null
        if (name.any { it == '/' || it == '\\' || it == '\u0000' }) return null
        if (isPartial(name)) return null
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(base, name).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.parentFile == base && it.isFile }
    }
}
