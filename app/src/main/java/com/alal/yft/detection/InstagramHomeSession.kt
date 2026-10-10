package com.alal.yft.detection

import android.webkit.CookieManager
import java.net.URI
import java.util.Locale

/**
 * P49: Home's lookup of an Instagram link uses the cookies YFT's own browser already has for
 * `instagram.com` (the user's own sign-in there), as the browser's lookup of the same post does.
 * They are sent to www.instagram.com only, never to the CDN, and never logged or stored
 * elsewhere. Without a sign-in in the browser there is no cookie and Home asks signed out.
 */
object InstagramHomeSession {
    const val PAGE = "https://www.instagram.com/"

    /** The browser's Instagram cookies when [url] is an Instagram link, else null. */
    fun cookieFor(url: String, cookies: (String) -> String? = ::browserCookie): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US)
            ?.removeSuffix(".")
            ?: return null
        if (host != DOMAIN && !host.endsWith(".$DOMAIN")) return null
        return cookies(PAGE)?.takeIf(String::isNotBlank)
    }

    private fun browserCookie(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    private const val DOMAIN = "instagram.com"
}
