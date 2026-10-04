package com.alal.yft.core.browser.session

import java.net.URI

/**
 * Deduplicates and bounds metadata probes for one top-level page.
 *
 * Volatile signing parameters are omitted only from the budget key. The original URL, including
 * all authorization parameters, remains unchanged for the actual request.
 */
class PageProbeBudget(
    private val maxUniqueUrls: Int = 20,
) {
    init {
        require(maxUniqueUrls > 0)
    }

    private val lock = Any()
    private val seenKeys = linkedSetOf<String>()
    private var currentPageUrl: String? = null

    fun beginPage(pageUrl: String) {
        synchronized(lock) {
            currentPageUrl = pageUrl
            seenKeys.clear()
        }
    }

    /** Keeps the spent budget when the same page only changed the address it shows. */
    fun movePage(pageUrl: String) {
        synchronized(lock) {
            if (currentPageUrl != null) currentPageUrl = pageUrl
        }
    }

    fun tryAcquire(pageUrl: String, requestUrl: String): Boolean = synchronized(lock) {
        if (pageUrl != currentPageUrl || seenKeys.size >= maxUniqueUrls) return false
        val key = requestUrl.budgetKey() ?: return false
        seenKeys.add(key)
    }

    private fun String.budgetKey(): String? {
        val uri = runCatching { URI(this) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme !in setOf("http", "https") || uri.userInfo != null) return null
        val port = when {
            uri.port == -1 -> -1
            scheme == "https" && uri.port == 443 -> -1
            scheme == "http" && uri.port == 80 -> -1
            else -> uri.port
        }
        val authority = if (port == -1) host else "$host:$port"
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        val stableQuery = uri.rawQuery
            ?.split('&')
            ?.filter(String::isNotBlank)
            ?.filterNot { it.substringBefore('=').lowercase().isVolatileParameter() }
            ?.sorted()
            ?.joinToString("&")
            ?.takeIf(String::isNotBlank)
        return "$scheme://$authority$path${stableQuery?.let { "?$it" }.orEmpty()}"
    }

    private fun String.isVolatileParameter(): Boolean =
        this in VOLATILE_PARAMETERS || startsWith("utm_")

    private companion object {
        val VOLATILE_PARAMETERS = setOf(
            "access_token", "auth", "authorization", "expires", "exp", "key",
            "key-pair-id", "policy", "sig", "signature", "token",
        )
    }
}