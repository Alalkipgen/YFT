package com.alal.yft.core.browser.detection

import java.net.URI
import java.util.Locale
import org.json.JSONObject
import org.json.JSONTokener

/** The link of the video in focus on a feed page, and how the page script found it. */
data class FocusedVideo(val url: String, val source: Source) {
    enum class Source {
        /** The page's own address is a video (a watch page, a short or a reel). */
        PAGE,

        /** The link beside the `<video>` that is playing. */
        PLAYING,

        /** The video, or the video link, closest to the middle of the screen. */
        CENTRE,
    }

    override fun toString(): String = "FocusedVideo(url=[REDACTED], source=$source)"
}

/**
 * P5: finds the video in focus on a feed page (YouTube, Facebook, TikTok) when the browser's
 * Download button is tapped and the page has no single video of its own.
 *
 * [script] runs once per tap, on the main thread, through `WebView.evaluateJavascript`. It returns
 * one link of the page's own site and nothing else (no page text, no cookies): the page's own
 * address when that is a video, else the link beside the playing `<video>`, else the link beside
 * the visible `<video>` nearest the middle of the screen, else the video link nearest the middle.
 * [parse] accepts that link only on the page's own site and only in a known video or post shape,
 * rebuilt on the site's main host without tracking parameters, so the site adapter can look it up.
 */
object FocusedVideoProbe {
    val script: String = """
        (() => {
          const videos = [
            /^\/watch\/?\?(?:[^#]*&)?v=[\w-]+/,
            /^\/(?:watch\.php|video\.php|video\/?)\?(?:[^#]*&)?v=\d+/,
            /^\/watch\/live\/?\?(?:[^#]*&)?v=\d+/,
            /^\/shorts\/[\w-]{11}(?:[/?#]|$)/,
            /^\/reels?\/\d+/,
            /^\/[^/?#]+\/videos\/(?:[^/?#]+\/)?\d+/,
            /^\/share\/[vr]\/[\w-]+/,
            /^\/@[^/?#]+\/video\/\d+/
          ];
          const posts = [
            /^\/(?:story|permalink)\.php\?/,
            /^\/[^/?#]+\/posts\/[\w-]+/,
            /^\/groups\/[^/?#]+\/(?:posts|permalink)\/\d+/,
            /^\/share\/p\/[\w-]+/
          ];
          const site = location.hostname.split('.').slice(-2).join('.');
          const facebook = site === 'facebook.com';
          const link = (value, shapes) => {
            if (!value) return null;
            try {
              const url = new URL(value, document.baseURI);
              if (url.protocol !== 'https:') return null;
              if (url.hostname !== site && !url.hostname.endsWith('.' + site)) return null;
              const where = url.pathname + url.search;
              return shapes.some(shape => shape.test(where)) ? url.href : null;
            } catch (_) { return null; }
          };
          const answer = (url, source) => JSON.stringify({ url, source });
          const own = link(location.href, videos);
          if (own) return answer(own, 'page');
          const width = window.innerWidth || document.documentElement.clientWidth;
          const height = window.innerHeight || document.documentElement.clientHeight;
          const shown = element => {
            const box = element.getBoundingClientRect();
            const w = Math.min(box.right, width) - Math.max(box.left, 0);
            const h = Math.min(box.bottom, height) - Math.max(box.top, 0);
            return w > 0 && h > 0 ? { box, area: w * h } : null;
          };
          const middle = box => Math.abs((box.top + box.bottom) / 2 - height / 2) +
            Math.abs((box.left + box.right) / 2 - width / 2) / 4;
          const linkBeside = element => {
            const own = element.getBoundingClientRect();
            const away = box => Math.abs(box.top + box.bottom - own.top - own.bottom);
            const nearestIn = (node, shapes) => {
              const found = [...node.querySelectorAll('a[href]')]
                .map(anchor => ({ url: link(anchor.getAttribute('href'), shapes), anchor }))
                .filter(item => item.url)
                .map(item => ({ url: item.url, gap: away(item.anchor.getBoundingClientRect()) }))
                .sort((a, b) => a.gap - b.gap);
              return found.length ? found[0].url : null;
            };
            for (let node = element, depth = 0; node && node !== document.body && depth < 15;
                node = node.parentElement, depth += 1) {
              if (node.tagName === 'A') {
                const url = link(node.getAttribute('href'), videos);
                if (url) return url;
              }
              const id = node.getAttribute('data-video-id');
              if (facebook && id && /^\d{6,25}$/.test(id)) {
                return 'https://www.facebook.com/watch/?v=' + id;
              }
              const url = nearestIn(node, videos) || (facebook ? nearestIn(node, posts) : null);
              if (url) return url;
            }
            return null;
          };
          const visible = [...document.querySelectorAll('video')]
            .map(video => ({ video, seen: shown(video) }))
            .filter(item => item.seen);
          const playing = visible
            .filter(item => !item.video.paused && !item.video.ended && item.video.readyState > 1)
            .sort((a, b) => b.seen.area - a.seen.area)[0];
          const playingUrl = playing ? linkBeside(playing.video) : null;
          if (playingUrl) return answer(playingUrl, 'playing');
          const centred = visible.sort((a, b) => middle(a.seen.box) - middle(b.seen.box))[0];
          const centredUrl = centred ? linkBeside(centred.video) : null;
          if (centredUrl) return answer(centredUrl, 'centre');
          const nearest = [...document.querySelectorAll('a[href]')]
            .map(anchor => ({ anchor, url: link(anchor.getAttribute('href'), videos) }))
            .map(item => ({ url: item.url, seen: item.url ? shown(item.anchor) : null }))
            .filter(item => item.url && item.seen)
            .sort((a, b) => middle(a.seen.box) - middle(b.seen.box))[0];
          return answer(nearest ? nearest.url : null, nearest ? 'centre' : 'none');
        })();
    """.trimIndent()

    /** Whether the page is on a site whose feeds the button reads (YouTube, Facebook, TikTok). */
    fun isFeedSite(pageUrl: String?): Boolean = pageUrl?.let(::siteOf) != null

    /**
     * The focused video from the script's [javascriptResult] for the page at [pageUrl], or null
     * when the page has no video in focus or the link is not a video of the page's own site.
     */
    fun parse(javascriptResult: String?, pageUrl: String?): FocusedVideo? {
        val site = pageUrl?.let(::siteOf) ?: return null
        val json = decode(javascriptResult) ?: return null
        val item = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val source = when (item.optString("source", "")) {
            "page" -> FocusedVideo.Source.PAGE
            "playing" -> FocusedVideo.Source.PLAYING
            "centre" -> FocusedVideo.Source.CENTRE
            else -> return null
        }
        val raw = (item.opt("url") as? String)?.trim()?.takeIf { it.length in 1..MAX_URL_LENGTH }
            ?: return null
        val url = canonicalVideoUrl(raw, site) ?: return null
        return FocusedVideo(url, source)
    }

    /** The video link rebuilt on its site's main host, or null when it is not a video link. */
    fun canonicalVideoUrl(url: String, site: FeedSite): String? {
        val uri = runCatching { URI(uriSafe(url)) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (siteOfHost(host) != site) return null
        val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        val query = uri.rawQuery.orEmpty()
        return when (site) {
            FeedSite.YOUTUBE -> youtube(segments, query)
            FeedSite.FACEBOOK -> facebook(segments, query)
            FeedSite.TIKTOK -> tiktok(segments)
        }
    }

    private fun youtube(segments: List<String>, query: String): String? {
        val id = when {
            segments == listOf("watch") -> parameter(query, "v")
            segments.size == 2 && segments[0] == "shorts" -> segments[1]
            else -> null
        }?.takeIf(YOUTUBE_ID::matches) ?: return null
        return if (segments[0] == "shorts") "$YOUTUBE/shorts/$id" else "$YOUTUBE/watch?v=$id"
    }

    private fun facebook(segments: List<String>, query: String): String? {
        val head = segments.firstOrNull()?.lowercase(Locale.US) ?: return null
        val second = segments.getOrNull(1)?.lowercase(Locale.US)
        return when {
            head in FACEBOOK_WATCH && segments.size == 1 ||
                head == "watch" && second == "live" && segments.size == 2 ->
                parameter(query, "v")
                    ?.takeIf(FACEBOOK_ID::matches)
                    ?.let { "$FACEBOOK/watch/?v=$it" }

            head == "reel" || head == "reels" -> segments.getOrNull(1)
                ?.takeIf { segments.size == 2 && FACEBOOK_ID.matches(it) }
                ?.let { "$FACEBOOK/reel/$it/" }

            head == "share" -> segments.getOrNull(2)
                ?.takeIf { segments.size == 3 && second in SHARE_KINDS && SHARE_CODE.matches(it) }
                ?.let { "$FACEBOOK/share/$second/$it/" }

            head in STORY_PAGES && segments.size == 1 -> {
                val story = parameter(query, "story_fbid")?.takeIf(POST_ID::matches)
                val owner = parameter(query, "id")?.takeIf(FACEBOOK_ID::matches)
                if (story != null && owner != null) {
                    "$FACEBOOK/$head?story_fbid=$story&id=$owner"
                } else {
                    null
                }
            }

            head == "groups" -> groupPost(segments)
            second == "videos" -> segments.drop(2).lastOrNull(FACEBOOK_ID::matches)
                ?.takeIf { segments.size in 3..4 && HANDLE.matches(segments[0]) }
                ?.let { "$FACEBOOK/${segments[0]}/videos/$it/" }

            second == "posts" -> segments.getOrNull(2)
                ?.takeIf { segments.size == 3 && HANDLE.matches(segments[0]) }
                ?.takeIf(POST_ID::matches)
                ?.let { "$FACEBOOK/${segments[0]}/posts/$it" }

            else -> null
        }
    }

    private fun groupPost(segments: List<String>): String? {
        if (segments.size != 4 || !HANDLE.matches(segments[1])) return null
        val kind = segments[2].lowercase(Locale.US).takeIf { it in GROUP_POSTS } ?: return null
        val id = segments[3].takeIf(POST_ID::matches) ?: return null
        return "$FACEBOOK/groups/${segments[1]}/$kind/$id/"
    }

    private fun tiktok(segments: List<String>): String? {
        if (segments.size != 3 || segments[1] != "video") return null
        val user = segments[0].takeIf(TIKTOK_USER::matches) ?: return null
        val id = segments[2].takeIf(TIKTOK_ID::matches) ?: return null
        return "https://www.tiktok.com/$user/video/$id"
    }

    /**
     * The link with the characters `java.net.URI` refuses percent-encoded: browsers leave some
     * as they are, such as the brackets in Facebook's `__cft__[0]` tracking parameter.
     */
    private fun uriSafe(url: String): String = buildString(url.length) {
        url.forEach { char ->
            if (char in URI_CHARACTERS) {
                append(char)
            } else {
                char.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
                    val value = byte.toInt()
                    append('%').append(HEX[value shr 4 and 0xF]).append(HEX[value and 0xF])
                }
            }
        }
    }

    private fun parameter(query: String, name: String): String? = query.split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.takeIf(String::isNotEmpty)

    private fun siteOf(pageUrl: String): FeedSite? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        return uri.host?.lowercase(Locale.US)?.let(::siteOfHost)
    }

    private fun siteOfHost(host: String): FeedSite? = FeedSite.entries.firstOrNull { site ->
        host == site.domain || host.endsWith(".${site.domain}")
    }

    private fun decode(result: String?): String? {
        val trimmed = result?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        if (trimmed.length > MAX_RESULT_LENGTH) return null
        return if (trimmed.startsWith('"')) {
            runCatching { JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        } else {
            trimmed
        }
    }

    /** The sites whose feeds the Download button reads; their adapters do the lookup. */
    enum class FeedSite(val domain: String) {
        YOUTUBE("youtube.com"),
        FACEBOOK("facebook.com"),
        TIKTOK("tiktok.com"),
    }

    private const val HEX = "0123456789ABCDEF"
    private val URI_CHARACTERS: Set<Char> =
        (('a'..'z') + ('A'..'Z') + ('0'..'9') + "-._~:/?#@!$&'()*+,;=%".toList()).toSet()
    private const val MAX_URL_LENGTH = 2_048
    private const val MAX_RESULT_LENGTH = 4_096
    private const val YOUTUBE = "https://www.youtube.com"
    private const val FACEBOOK = "https://www.facebook.com"
    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
    private val FACEBOOK_ID = Regex("\\d{6,25}")
    private val POST_ID = Regex("\\d{6,25}|pfbid[0-9A-Za-z]{10,100}")
    private val HANDLE = Regex("[A-Za-z0-9._-]{1,80}")
    private val SHARE_CODE = Regex("[A-Za-z0-9_-]{4,32}")
    private val SHARE_KINDS = setOf("v", "r", "p")
    private val STORY_PAGES = setOf("story.php", "permalink.php")
    private val GROUP_POSTS = setOf("posts", "permalink")
    private val FACEBOOK_WATCH = setOf("watch", "watch.php", "video", "video.php")
    private val TIKTOK_USER = Regex("@[A-Za-z0-9._-]{1,40}")
    private val TIKTOK_ID = Regex("\\d{5,25}")
}
