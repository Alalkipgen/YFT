package com.alal.yft.detection.tiktok

import java.net.URI
import java.util.Locale

/**
 * P40 (G3, `TT_HOME_COOKIES=ON`): Home's lookup of a TikTok link uses the cookies YFT's own
 * browser already has for `tiktok.com` (from the user's own visits), sent to TikTok only and
 * never logged or stored elsewhere. With `TT_HOME_COOKIES=OFF` Home stays cookie-free.
 */
object TikTokHomeSession {
    /** The browser's TikTok cookies for [url] when it is a TikTok link and the owner allows it. */
    fun cookieFor(
        url: String,
        settings: TikTokPageSettings,
        cookies: TikTokCookies = CookieManagerTikTokCookies,
    ): String? {
        if (!settings.homeCookies) return null
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US)
            ?.removeSuffix(".")
            ?: return null
        if (host != TIKTOK_DOMAIN && !host.endsWith(".$TIKTOK_DOMAIN")) return null
        return cookies.header(TikTokPageScript.COOKIE_PAGE)
    }

    private const val TIKTOK_DOMAIN = "tiktok.com"
}
