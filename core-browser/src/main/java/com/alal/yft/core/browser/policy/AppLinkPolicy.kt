package com.alal.yft.core.browser.policy

import java.net.URLDecoder

/**
 * What the browser does with a top-level navigation it cannot load.
 *
 * Sites try to open their own app with `intent://`, `fb://`, `market://` and similar links. YFT
 * never starts another app from a page: an `intent://` link's HTTPS `browser_fallback_url` is
 * loaded instead, and any other app link is dropped silently so the page stays as it was rather
 * than turning blank. Plain HTTP stays blocked with a message.
 */
object AppLinkPolicy {
    sealed interface Decision {
        /** An `http://` page: blocked, and the user is told why. */
        data object Insecure : Decision

        /** The HTTPS page an `intent://` link names for browsers without the app. */
        data class Fallback(val url: String) : Decision

        /** An app link without a usable web page: nothing happens. */
        data object Ignore : Decision
    }

    fun decide(url: String): Decision {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme == "http") return Decision.Insecure
        if (scheme == "intent") fallbackOf(url)?.let { return Decision.Fallback(it) }
        return Decision.Ignore
    }

    /** `intent://…#Intent;scheme=fb;S.browser_fallback_url=https%3A%2F%2F…;end` → the URL. */
    private fun fallbackOf(intentUrl: String): String? {
        val extras = intentUrl.substringAfter("#Intent;", missingDelimiterValue = "")
        val encoded = extras.split(';')
            .firstOrNull { it.startsWith(FALLBACK_EXTRA) }
            ?.removePrefix(FALLBACK_EXTRA)
            ?: return null
        val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: return null
        return decoded.takeIf {
            it.startsWith("https://") && BrowserAddressNormalizer.isAllowedTopLevelUrl(it)
        }
    }

    private const val FALLBACK_EXTRA = "S.browser_fallback_url="
}
