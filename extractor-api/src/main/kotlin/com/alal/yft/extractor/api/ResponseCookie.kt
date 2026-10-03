package com.alal.yft.extractor.api

import java.net.URI
import java.util.Locale

/**
 * A cookie that one of a lookup's own responses set, reduced to what a later request needs.
 *
 * The HTTP client keeps only cookies whose domain matches the response that set them, drops
 * deletions and expired values, and holds them in memory for that lookup; nothing is stored.
 * A browser would send the cookie to [domain] (and its subdomains unless [hostOnly]) under
 * [path]. [toString] never shows the value.
 */
data class ResponseCookie(
    val name: String,
    val value: String,
    val domain: String,
    val hostOnly: Boolean,
    val path: String = "/",
) {
    init {
        require(name.isNotBlank()) { "A cookie needs a name" }
        require(domain.isNotBlank() && domain == domain.lowercase(Locale.US)) {
            "The cookie domain must be a lower-case host"
        }
        require(path.startsWith("/")) { "The cookie path must be absolute" }
    }

    /** The `name=value` pair for a `Cookie` header. */
    val pair: String
        get() = "$name=$value"

    /** True when a browser would send this cookie to the HTTPS address [url]. */
    fun matches(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase(Locale.US)?.removeSuffix(".") ?: return false
        val hostMatches = host == domain || !hostOnly && host.endsWith(".$domain")
        return hostMatches && pathMatches(uri.rawPath.orEmpty().ifEmpty { "/" })
    }

    private fun pathMatches(requestPath: String): Boolean =
        requestPath == path ||
            requestPath.startsWith(path) &&
            (path.endsWith("/") || requestPath[path.length] == '/')

    override fun toString(): String =
        "ResponseCookie(name=$name, domain=$domain, hostOnly=$hostOnly, path=$path, " +
            "value=[REDACTED])"
}