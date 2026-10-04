package com.alal.yft.extractor.generic.classifier

import java.net.URI
import java.util.Locale

/**
 * Which media URLs name the same file or the same stream (P3-FIX). Pure and offline.
 *
 * A site's own player fetches one file in pieces. A byte-range parameter (`bytestart` and
 * `byteend`, or `range=0-1023`) names a piece of the file, not another file, so [wholeFile]
 * drops it and the URL names the whole file again. Numbered pieces of one HLS stream
 * (`seg-12.ts`) share one [segmentFamily]. Distinct files keep distinct URLs: numbered whole
 * files such as `clip-1.mp4` and `clip-2.mp4` are never merged.
 */
object MediaFileUrls {
    private val BYTE_PARAMETERS = setOf("bytestart", "byteend")
    private val NUMBER = Regex("^[0-9]{1,15}$")
    private val BYTE_RANGE = Regex("^[0-9]{1,15}-[0-9]{0,15}$")
    private val DIGITS = Regex("[0-9]+")

    /** A file name the site generated: long, opaque and with a media extension. */
    private val OPAQUE_FILE = Regex("^[A-Za-z0-9_-]{24,}\\.([A-Za-z0-9]{2,4})$")

    /** Extensions of the pieces HLS streams are cut into. */
    private val STREAM_PIECE_EXTENSIONS = setOf("aac", "ts")

    /** [url] without byte-range parameters; [url] itself when it has none or cannot be read. */
    fun wholeFile(url: String): String {
        val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
        val queryStart = url.indexOf('?').takeIf { it in 0 until fragmentStart } ?: return url
        val parameters = url.substring(queryStart + 1, fragmentStart).split('&')
        val kept = parameters.filterNot(::isByteRange)
        if (kept.size == parameters.size) return url
        val query = kept.filter(String::isNotEmpty).joinToString("&")
        return buildString {
            append(url, 0, queryStart)
            if (query.isNotEmpty()) append('?').append(query)
            append(url, fragmentStart, url.length)
        }
    }

    /** True when [url] asks for a piece of a file by its byte range. */
    fun hasByteRange(url: String): Boolean = wholeFile(url) != url

    /**
     * True when the file name is an opaque name the site generated, such as Facebook's and
     * Instagram's CDN names: the path alone names the file, and its query only carries
     * per-request values, so two requests for it are one file.
     */
    fun isOpaqueFile(url: String): Boolean {
        val name = fileName(url) ?: return false
        val extension = OPAQUE_FILE.matchEntire(name)?.groupValues?.get(1) ?: return false
        return MediaUrlClassifier.isDirectExtension(extension)
    }

    /**
     * The HLS stream a numbered piece (`.ts`, `.aac`) belongs to: host, folder and the file name
     * with its numbers blanked, for example `cdn.test/hls/720/seg-#.ts`. Null for any other
     * file, and for a piece without a number.
     */
    fun segmentFamily(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: return null
        val name = path.substringAfterLast('/')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.lowercase(Locale.US) !in STREAM_PIECE_EXTENSIONS) return null
        if (!DIGITS.containsMatchIn(name.substringBeforeLast('.'))) return null
        val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
        return "$host$folder/${name.replace(DIGITS, "#")}"
    }

    private fun isByteRange(parameter: String): Boolean {
        val name = parameter.substringBefore('=').lowercase(Locale.US)
        val value = parameter.substringAfter('=', missingDelimiterValue = "")
        return when (name) {
            in BYTE_PARAMETERS -> NUMBER.matches(value)
            "range" -> BYTE_RANGE.matches(value)
            else -> false
        }
    }

    private fun fileName(url: String): String? =
        runCatching { URI(url) }.getOrNull()
            ?.rawPath
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotEmpty)
}
