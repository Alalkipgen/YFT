package com.alal.yft.ui.format

import java.util.Locale

/** Short labels used on cards: "96 MB", "7.4 MB", "1.2 GB", "4:12", "1:02:03". */
object YftFormat {
    private const val UNIT = 1_024.0
    private const val MAX_EXTENSION_WITH_DOT = 6
    private val SIZE_UNITS = listOf("KB", "MB", "GB", "TB")

    /** One decimal below 10 of a unit, none above and no ".0": "7.4 MB", "7 MB", "96 MB". */
    fun bytes(bytes: Long): String {
        if (bytes < UNIT) return "$bytes B"
        var value = bytes / UNIT
        var index = 0
        while (value >= UNIT && index < SIZE_UNITS.lastIndex) {
            value /= UNIT
            index++
        }
        val pattern = if (value < 10) "%.1f" else "%.0f"
        val number = String.format(Locale.US, pattern, value).removeSuffix(".0")
        return "$number ${SIZE_UNITS[index]}"
    }

    /** Media length as m:ss, or h:mm:ss from an hour. */
    fun duration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) + 500) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** "MP4", "M4A": the file's extension, or its MIME subtype when the name has none. */
    fun format(fileName: String, mimeType: String?): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
        val subtype = mimeType?.substringAfter('/')?.substringBefore(';')
            ?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
        return (extension ?: subtype)?.uppercase(Locale.US)
    }

    /** The name shown for a file: without its extension, and never blank. */
    fun title(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        val hasExtension = dot > 0 && fileName.length - dot <= MAX_EXTENSION_WITH_DOT
        val base = if (hasExtension) fileName.substring(0, dot) else fileName
        return base.ifBlank { fileName }
    }
}
