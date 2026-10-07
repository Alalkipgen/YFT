package com.alal.yft.core.data.history

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * The address a page is remembered under (P31): HTTPS pages only, without the fragment, without
 * a user name or password and without the tracking parameters ads and shares add, so the same
 * page opened from two shares is one entry. Everything else (the start page, `about:`, `data:`,
 * plain HTTP, files) is never remembered.
 */
object BrowserHistoryAddress {
    /** The cleaned address, or null when the page is not one the history keeps. */
    fun clean(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.US)?.takeIf(String::isNotBlank) ?: return null
        val port = if (uri.port == -1 || uri.port == HTTPS_PORT) "" else ":${uri.port}"
        val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
        val query = uri.rawQuery
            ?.split('&')
            ?.filter { it.isNotEmpty() && !isTracking(it.substringBefore('=')) }
            ?.joinToString("&")
            ?.takeIf(String::isNotEmpty)
        return buildString {
            append("https://").append(host).append(port).append(path)
            if (query != null) append('?').append(query)
        }
    }

    /** The host as the list shows it: without `www.`. */
    fun host(cleanUrl: String): String =
        runCatching { URI(cleanUrl).host }.getOrNull().orEmpty().removePrefix("www.")

    private fun isTracking(rawName: String): Boolean {
        val name = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
            .lowercase(Locale.US)
        return name.startsWith("utm_") || name in TRACKING_PARAMETERS
    }

    /** Click and share identifiers that only tell a site where the visit came from. */
    private val TRACKING_PARAMETERS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "igshid", "mc_eid", "yclid",
    )
    private const val HTTPS_PORT = 443
    private const val MAX_LENGTH = 4_096
}
