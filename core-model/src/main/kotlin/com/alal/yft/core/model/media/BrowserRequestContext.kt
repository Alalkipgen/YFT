package com.alal.yft.core.model.media

import com.alal.yft.core.model.logging.SensitiveValueRedactor

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

        output.replaceCaseInsensitive("User-Agent", userAgent)
        output.replaceCaseInsensitive("Cookie", cookie)
        output.replaceCaseInsensitive(
            "Referer",
            pageUrl?.takeIf { it.startsWith("https://", ignoreCase = true) },
        )
        if (output.keys.none { it.equals("Accept", ignoreCase = true) }) {
            output["Accept"] = "*/*"
        }
        return output
    }

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

    private companion object {
        val BLOCKED_HEADERS = setOf(
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
