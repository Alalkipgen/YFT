package com.alal.yft.feature.library

import android.webkit.MimeTypeMap
import java.util.Locale

/** Where a finished download lives, which decides how it is listed, shared and deleted. */
enum class LibraryLocation(val label: String) {
    /** A MediaStore item in the shared `Download/YFT/` collection (Android 10 and later). */
    SHARED_DOWNLOADS("Download/YFT"),

    /** A file in storage only YFT can read; other apps get it only through a per-file grant. */
    APP_STORAGE("App storage"),
}

/**
 * One finished download shown in the library.
 *
 * [uri] is always a `content://` URI, either the MediaStore item or YFT's own read-only provider,
 * so the same value can be played in the app and handed to another app with a read grant.
 */
data class LibraryItem(
    val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
    val location: LibraryLocation,
) {
    val isAudio: Boolean
        get() = mimeType?.startsWith("audio/") == true

    /** Whether the in-app player is offered; unknown types are left to other apps. */
    val isPlayable: Boolean
        get() = mimeType != null && (mimeType.startsWith("video/") || isAudio)
}

/** MIME types for the file names YFT produces, with the platform table as a fallback. */
internal object LibraryMimeTypes {
    private val known = mapOf(
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "ts" to "video/mp2t",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "mp3" to "audio/mpeg",
        "weba" to "audio/webm",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
    )

    fun forFileName(name: String): String? {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotEmpty() }
            ?: return null
        return known[extension]
            ?: runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) }
                .getOrNull()
    }
}
