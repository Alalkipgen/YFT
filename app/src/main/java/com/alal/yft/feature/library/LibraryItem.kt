package com.alal.yft.feature.library

import android.webkit.MimeTypeMap
import java.util.Locale

/** Where a finished download lives, which decides how it is listed, shared and deleted. */
enum class LibraryLocation(val label: String) {
    /** A MediaStore item in the shared `Download/YFT/` collection (Android 10 and later). */
    SHARED_DOWNLOADS("Download/YFT"),

    /** A file in storage only YFT can read; other apps get it only through a per-file grant. */
    APP_STORAGE("App storage"),

    /**
     * v1.0.2 item 28: a file at the top of the folder picked in Android's folder picker (a SAF
     * document); the item's [LibraryItem.place] is the folder's name.
     */
    CHOSEN_FOLDER("Chosen folder"),
}

/**
 * One finished download shown in the library.
 *
 * [uri] is always a `content://` URI, either the MediaStore item, YFT's own read-only provider or
 * (item 28) the chosen folder's document, so the same value can be played in the app and handed
 * to another app with a read grant.
 */
data class LibraryItem(
    val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
    val location: LibraryLocation,
    /** Where the file is, in words: the location's, or (item 28) the chosen folder's name. */
    val place: String = location.label,
) {
    val isAudio: Boolean
        get() = mimeType?.startsWith("audio/") == true

    val isVideo: Boolean
        get() = mimeType?.startsWith("video/") == true

    /** Whether the in-app player is offered; unknown types are left to other apps. */
    val isPlayable: Boolean
        get() = isVideo || isAudio

    /** The same file, whether it was listed here or started from Downloads. */
    fun isSameFileAs(other: LibraryItem?): Boolean =
        other != null && (other.id == id || other.uri == uri)
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
