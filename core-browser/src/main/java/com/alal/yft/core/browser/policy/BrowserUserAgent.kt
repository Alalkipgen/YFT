package com.alal.yft.core.browser.policy

/**
 * The browser's identity: the WebView's own user agent without the two tokens that mark an
 * embedded WebView, `; wv` and `Version/4.0`. That is how Chrome on the same phone introduces
 * itself, with the same engine version and device.
 *
 * Some sites send an embedded WebView a player its engine cannot play: TikTok answered the
 * WebView identity with a video the page could not load (MEDIA_ERR_SRC_NOT_SUPPORTED) and this
 * one with a playing video (P2 emulator diagnostics).
 */
object BrowserUserAgent {
    private val EMBEDDED_MARK = Regex(""";\s*wv\)""")
    private val VERSION_TOKEN = Regex("""Version/\d+(\.\d+)*\s+""")

    /** The browser identity for a WebView whose own user agent is [webViewUserAgent]. */
    fun from(webViewUserAgent: String): String =
        webViewUserAgent.replace(EMBEDDED_MARK, ")").replace(VERSION_TOKEN, "")
}
