package com.alal.yft.core.browser.policy

import java.net.URI

sealed interface BrowserAddressResult {
    data class Valid(val url: String) : BrowserAddressResult
    data class Invalid(val reason: String) : BrowserAddressResult
}

object BrowserAddressNormalizer {
    fun normalize(input: String): BrowserAddressResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return BrowserAddressResult.Invalid("Enter a web address")
        if (trimmed.equals("about:blank", ignoreCase = true)) {
            return BrowserAddressResult.Valid("about:blank")
        }

        val candidate = if (SCHEME_PATTERN.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return BrowserAddressResult.Invalid("Enter a valid HTTPS address")
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return BrowserAddressResult.Invalid("Only HTTPS pages are supported")
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null) {
            return BrowserAddressResult.Invalid("Enter a valid HTTPS address")
        }
        return BrowserAddressResult.Valid(uri.toASCIIString())
    }

    fun isAllowedTopLevelUrl(url: String): Boolean = when (val result = normalize(url)) {
        is BrowserAddressResult.Valid -> result.url == "about:blank" || result.url.startsWith("https://")
        is BrowserAddressResult.Invalid -> false
    }

    private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
