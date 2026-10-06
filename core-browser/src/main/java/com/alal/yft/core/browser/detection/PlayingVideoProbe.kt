package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PlayingVideo
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Which video a page is playing, read once when the Download button is tapped on a page with
 * several videos, so the sheet opens for that one first (P12).
 *
 * P24: the script reports every `<video>` that plays or has started, in the page and in the
 * frames it may read: its address, length, position, picture size, whether it is muted, loops
 * or autoplays, and whether it sits in a frame, a link to another page or a thumbnail box. A
 * stream the page builds itself (`blob:`) names no file; its length and start time then tell
 * its candidates apart ([com.alal.yft.core.model.media.MediaGroups.mainVideo]).
 */
object PlayingVideoProbe {
    val script: String = """
        (function() {
          var found = [];
          var now = Date.now();
          var boxWords = /thumb|preview|teaser|mediabook/i;
          function inThumbnail(video, page) {
            var node = video;
            for (var depth = 0; node && depth < 6; depth++, node = node.parentElement) {
              if (node.tagName === 'A' && node.href && node.href.split('#')[0] !== page) {
                return true;
              }
              var names = (node.className && node.className.baseVal !== undefined)
                ? node.className.baseVal : (node.className || '');
              if (boxWords.test(String(names)) || boxWords.test(node.id || '')) return true;
            }
            return false;
          }
          function visit(doc, framed) {
            var page = String(doc.location && doc.location.href || '').split('#')[0];
            var videos = doc.getElementsByTagName('video');
            for (var i = 0; i < videos.length && found.length < 30; i++) {
              var video = videos[i];
              var playing = !video.paused && !video.ended && video.readyState > 2;
              if (!playing && !(video.currentTime > 0)) continue;
              var box = video.getBoundingClientRect();
              found.push({
                src: video.currentSrc || video.src || '',
                playing: playing,
                duration: isFinite(video.duration) ? video.duration : null,
                time: video.currentTime || 0,
                width: video.videoWidth || null,
                height: video.videoHeight || null,
                area: Math.max(0, box.width) * Math.max(0, box.height),
                muted: !!video.muted || video.volume === 0,
                loop: !!video.loop,
                autoplay: !!video.autoplay,
                framed: framed,
                thumbnail: inThumbnail(video, page)
              });
            }
          }
          visit(document, false);
          for (var f = 0; f < window.frames.length && f < 10; f++) {
            try { visit(window.frames[f].document, true); } catch (e) {}
          }
          return JSON.stringify({now: now, videos: found});
        })();
    """.trimIndent()

    /** The playing video's HTTPS address from the script's [javascriptResult], or null. */
    fun parse(javascriptResult: String?): String? = playing(javascriptResult)?.url

    /**
     * P24: the page's player from the script's [javascriptResult]: of the elements it reports,
     * one that does not look like a preview first, then a playing one before a paused one, one
     * in the page before one in its frames, then the largest on screen, then the longest. An
     * older answer that is just an address gives that address. Null when nothing plays.
     */
    fun playing(javascriptResult: String?): PlayingVideo? {
        val text = decode(javascriptResult) ?: return null
        if (!text.startsWith('{')) return addressOnly(text)
        val answer = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val now = answer.optLong("now", 0L).takeIf { it > 0 }
        val videos = answer.optJSONArray("videos") ?: JSONArray()
        return (0 until minOf(videos.length(), MAX_VIDEOS))
            .mapNotNull { index -> videos.optJSONObject(index)?.let { element(it, now) } }
            .maxWithOrNull(
                compareBy<Reported>(
                    { if (it.video.looksLikePreview) 0 else 1 },
                    { if (it.playing) 1 else 0 },
                    { if (it.video.framed) 0 else 1 },
                    { it.area },
                    { it.video.durationMillis ?: -1L },
                ),
            )
            ?.video
    }

    private class Reported(val video: PlayingVideo, val playing: Boolean, val area: Double)

    private fun element(item: JSONObject, now: Long?): Reported? {
        val source = item.optString("src", "").trim().take(MAX_RESULT_LENGTH)
        val pageBuilt = source.startsWith(BLOB, ignoreCase = true) ||
            source.startsWith(MEDIA_SOURCE, ignoreCase = true)
        val url = source.takeIf {
            it.startsWith(HTTPS, ignoreCase = true) && it.length > HTTPS.length
        }
        if (url == null && !pageBuilt) return null
        val seconds = item.optDouble("duration", Double.NaN)
        val position = item.optDouble("time", Double.NaN)
            .takeIf { it.isFinite() && it >= 0 } ?: 0.0
        val video = PlayingVideo(
            url = url,
            pageBuilt = pageBuilt,
            durationMillis = seconds.takeIf { it.isFinite() && it > 0 }
                ?.times(MILLIS_PER_SECOND)?.toLong()?.takeIf { it > 0 },
            startedAtEpochMs = now?.let { it - (position * MILLIS_PER_SECOND).toLong() },
            width = item.optInt("width", 0).takeIf { it > 0 },
            height = item.optInt("height", 0).takeIf { it > 0 },
            muted = item.optBoolean("muted", false),
            loop = item.optBoolean("loop", false),
            autoplay = item.optBoolean("autoplay", false),
            framed = item.optBoolean("framed", false),
            inThumbnail = item.optBoolean("thumbnail", false),
        )
        val area = item.optDouble("area", 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0
        return Reported(video, playing = item.optBoolean("playing", true), area = area)
    }

    private fun addressOnly(text: String): PlayingVideo? = text
        .takeIf { it.startsWith(HTTPS, ignoreCase = true) && it.length > HTTPS.length }
        ?.let { PlayingVideo(url = it) }

    private fun decode(javascriptResult: String?): String? {
        val trimmed = javascriptResult?.trim()
            ?.takeIf { it.length in 2..MAX_ANSWER_LENGTH && it != "null" }
            ?: return null
        val text = if (trimmed.startsWith('"')) {
            runCatching { JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        } else {
            trimmed
        }?.trim() ?: return null
        return text.takeIf { it.startsWith('{') || it.length <= MAX_RESULT_LENGTH }
    }

    private const val HTTPS = "https://"
    private const val BLOB = "blob:"
    private const val MEDIA_SOURCE = "mediasource:"
    private const val MAX_RESULT_LENGTH = 4_096
    private const val MAX_ANSWER_LENGTH = 200_000
    private const val MAX_VIDEOS = 30
    private const val MILLIS_PER_SECOND = 1_000.0
}
