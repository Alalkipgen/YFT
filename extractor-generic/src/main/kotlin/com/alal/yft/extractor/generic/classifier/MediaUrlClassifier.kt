package com.alal.yft.extractor.generic.classifier

import com.alal.yft.core.model.media.MediaKind
import java.net.URI

object MediaUrlClassifier {
    private val directExtensions = setOf(
        "3gp", "aac", "flac", "m4a", "m4v", "mkv", "mov", "mp3", "mp4",
        "mpeg", "mpg", "oga", "ogg", "ogv", "opus", "ts", "wav", "webm",
    )
    private val hlsMimeTypes = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
    )

    fun classify(url: String, mimeType: String? = null): MediaKind? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null

        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        when {
            normalizedMime in hlsMimeTypes -> return MediaKind.HLS
            normalizedMime == "application/dash+xml" -> return MediaKind.DASH
            normalizedMime?.startsWith("video/") == true -> return MediaKind.DIRECT
            normalizedMime?.startsWith("audio/") == true -> return MediaKind.DIRECT
        }

        val extension = uri.path
            ?.substringAfterLast('/', missingDelimiterValue = "")
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
        return when {
            extension == "m3u8" -> MediaKind.HLS
            extension == "mpd" -> MediaKind.DASH
            extension in directExtensions -> MediaKind.DIRECT
            else -> null
        }
    }
}
