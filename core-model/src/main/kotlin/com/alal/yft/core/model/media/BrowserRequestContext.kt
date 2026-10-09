package com.alal.yft.core.model.media

import com.alal.yft.core.model.logging.SensitiveValueRedactor
import java.net.URI

data class BrowserRequestContext(
    val pageUrl: String?,
    val userAgent: String?,
    val cookie: String?,
    val observedHeaders: Map<String, String> = emptyMap(),
) {
    fun replayHeaders(): Map<String, String> {
        val output = linkedMapOf<String, String>()
        observedHeaders.forEach { (name, value) ->
            if (name.isReplayableHeader() && value.isNotBlank()) output[name] = value
        }

        // P45: what the browser itself sent stands; the context fills only what is missing.
        if (!userAgent.isNullOrBlank()) output.replaceCaseInsensitive("User-Agent", userAgent)
        output.replaceCaseInsensitive("Cookie", cookie)
        if (output.keys.none { it.equals("Referer", ignoreCase = true) }) {
            pageUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?.let { output["Referer"] = it }
        }
        if (output.keys.none { it.equals("Accept", ignoreCase = true) }) {
            output["Accept"] = "*/*"
        }
        return output
    }

    /** P45: this context with the browser's [agent] when it names none itself. */
    fun withUserAgent(agent: String?): BrowserRequestContext =
        if (userAgent.isNullOrBlank() && !agent.isNullOrBlank()) copy(userAgent = agent) else this

    fun mergedWith(other: BrowserRequestContext): BrowserRequestContext = BrowserRequestContext(
        pageUrl = other.pageUrl ?: pageUrl,
        userAgent = other.userAgent ?: userAgent,
        cookie = other.cookie ?: cookie,
        observedHeaders = observedHeaders + other.observedHeaders,
    )

    override fun toString(): String = buildString {
        append("BrowserRequestContext(pageUrl=")
        append(pageUrl?.let(SensitiveValueRedactor::redact))
        append(", userAgentPresent=")
        append(!userAgent.isNullOrBlank())
        append(", cookie=")
        append(if (cookie.isNullOrBlank()) "absent" else "[REDACTED]")
        append(", observedHeaderNames=")
        append(observedHeaders.keys.sorted())
        append(')')
    }

    private fun String.isReplayableHeader(): Boolean = lowercase() !in BLOCKED_HEADERS

    private fun MutableMap<String, String>.replaceCaseInsensitive(name: String, value: String?) {
        keys.filter { it.equals(name, ignoreCase = true) }
            .toList()
            .forEach(::remove)
        if (!value.isNullOrBlank()) this[name] = value
    }

    companion object {
        /**
         * P45: the context of a link a browser page names in its markup or its scripts before any
         * request of it was seen, asked the way the page's own player asks for it in Chromium: a
         * file on another origin than the page's gets the page's origin as `Origin` and only that
         * origin as `Referer` (the browser's default `strict-origin-when-cross-origin`); a file on
         * the page's own origin gets the whole page address. Never a cookie: the page's cookies
         * belong to its site, not to the file's host. A CDN that refused YFT's bare request
         * (FIX_ADD_PLAN P45: HTTP 410 and 474 on the owner's phone) gets what the browser sends.
         */
        fun pageLink(
            pageUrl: String,
            mediaUrl: String,
            userAgent: String? = null,
        ): BrowserRequestContext {
            val page = httpsOrigin(pageUrl)
            val target = origin(mediaUrl)
            val headers = if (page != null && target != null && page != target) {
                mapOf("Origin" to page, "Referer" to "$page/")
            } else {
                emptyMap()
            }
            return BrowserRequestContext(pageUrl, userAgent, cookie = null, headers)
        }

        private fun httpsOrigin(url: String): String? =
            origin(url)?.takeIf { it.startsWith("https://") }

        /** `scheme://host[:port]` of [url], lowercase; null for an address without a host. */
        private fun origin(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()?.takeIf { it == "https" || it == "http" }
                ?: return null
            val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
            val defaultPort = if (scheme == "https") HTTPS_PORT else HTTP_PORT
            val port = uri.port.takeIf { it > 0 && it != defaultPort }?.let { ":$it" }.orEmpty()
            return "$scheme://$host$port"
        }

        private const val HTTPS_PORT = 443
        private const val HTTP_PORT = 80

        private val BLOCKED_HEADERS = setOf(
            "connection",
            "content-length",
            "host",
            "if-range",
            "proxy-connection",
            "range",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
        )
    }
}
