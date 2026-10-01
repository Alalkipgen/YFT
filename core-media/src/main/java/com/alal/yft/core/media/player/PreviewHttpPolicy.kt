package com.alal.yft.core.media.player

import com.alal.yft.core.model.media.BrowserRequestContext
import java.util.Locale
import okhttp3.HttpUrl

internal class PreviewHttpPolicy(
    private val credentialOrigin: HttpUrl,
    requestContext: BrowserRequestContext,
) {
    private val contextHeaders = requestContext.replayHeaders()

    val contextHeaderNames: Set<String>
        get() = contextHeaders.keys

    fun headersFor(targetUrl: HttpUrl): Map<String, String> {
        if (targetUrl.hasSameOrigin(credentialOrigin)) return contextHeaders
        return contextHeaders.filterKeys { name ->
            name.lowercase(Locale.US) in CROSS_ORIGIN_HEADER_ALLOWLIST
        }
    }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private companion object {
        val CROSS_ORIGIN_HEADER_ALLOWLIST = setOf(
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
        )
    }
}