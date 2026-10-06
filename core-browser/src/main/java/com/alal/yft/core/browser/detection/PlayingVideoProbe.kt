package com.alal.yft.core.browser.detection

import org.json.JSONTokener

/**
 * Which video a page is playing, read once when the Download button is tapped on a page with
 * several videos, so the sheet opens for that one first (P12).
 *
 * The script answers the address the playing `<video>` reads. Only an HTTPS address counts: a
 * stream the page builds itself (`blob:`) names no file, and the button then opens the largest.
 */
object PlayingVideoProbe {
    val script: String = """
        (function() {
          var videos = document.getElementsByTagName('video');
          for (var i = 0; i < videos.length; i++) {
            var video = videos[i];
            if (!video.paused && !video.ended && video.readyState > 2) {
              return video.currentSrc || video.src || null;
            }
          }
          return null;
        })();
    """.trimIndent()

    /** The playing video's address from the script's [javascriptResult], or null. */
    fun parse(javascriptResult: String?): String? {
        val trimmed = javascriptResult?.trim()
            ?.takeIf { it.length in 2..MAX_RESULT_LENGTH && it != "null" }
            ?: return null
        val url = if (trimmed.startsWith('"')) {
            runCatching { JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        } else {
            trimmed
        }?.trim() ?: return null
        return url.takeIf { it.startsWith(HTTPS, ignoreCase = true) && it.length > HTTPS.length }
    }

    private const val HTTPS = "https://"
    private const val MAX_RESULT_LENGTH = 4_096
}
