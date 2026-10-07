package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI

/**
 * P28: the files a page's own video player is set up with, read from its scripts without
 * running them. Common player setups by player, never by site: JW Player
 * (`setup({file | sources | playlist})`), Video.js (`data-setup`, source lists), Flowplayer,
 * Clappr and Plyr (`source`, `sources`), KVS-style `flashvars` (`video_url`, `video_alt_url`
 * with their `_text` labels) and quality lists in page data (objects with a media address and a
 * `quality`, `label`, `res`, `height` or `size`, as in `mediaDefinitions` or `sources`). Escaped
 * JSON (`\/`) is read. An address a script builds (KVS's `function/0/…`) is not chased: the
 * browser sees its request when the player fetches it.
 */
object PlayerSetupScanner {
    /** One file of the page's player setup: [height] from its label when it names one. */
    data class Source(
        val url: String,
        val height: Int?,
        val mimeType: String?,
    ) {
        override fun toString(): String = "Source(height=$height, mimeType=$mimeType)"
    }

    /** Whether [script] looks like a player setup worth reading. */
    fun mentionsPlayer(script: String): Boolean = PLAYER_WORDS.containsMatchIn(script)

    /** The player setup's files in [script] (a page script or a `data-setup` value). */
    fun sources(script: String, pageUrl: String): List<Source> {
        if (!mentionsPlayer(script)) return emptyList()
        val text = HtmlMediaScanner.unescapeJson(script.take(MAX_SCRIPT_LENGTH))
        val found = LinkedHashMap<String, Source>()
        fun add(raw: String, label: String?, type: String? = null) {
            if (found.size > MAX_SOURCES) return
            val source = source(raw, label, type, pageUrl) ?: return
            val known = found[source.url]
            if (known == null || known.height == null && source.height != null) {
                found[source.url] = source
            }
        }
        // KVS-style flashvars: video_url / video_alt_url2 with video_alt_url2_text: '720p'.
        FLASHVARS_URL.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val label = Regex("""\b${Regex.escape(name)}_text\s*[:=]\s*['"]([^'"]{1,40})['"]""")
                .find(text)?.groupValues?.get(1)
            add(match.groupValues[2], label)
        }
        // Objects without nested braces that name a quality or a type:
        // { file|src|url|videoUrl: "…", label|quality|res|height|size: …, type: "video/mp4" }.
        FLAT_OBJECT.findAll(text).forEach { block ->
            val body = block.value
            val label = LABEL_KEY.find(body)?.groupValues?.let { it[2].ifEmpty { it[3] } }
            val type = TYPE_KEY.find(body)?.groupValues?.get(1)
            if (label == null && type == null) return@forEach
            URL_KEY.findAll(body).forEach { add(it.groupValues[2], label, type) }
        }
        // A setup's own `file:` or `source:` with nested parts (tracks, ads) around it.
        SETUP_URL_KEY.findAll(text).forEach { match -> add(match.groupValues[2], null) }
        // P28: more files than a player has qualities is a list of other videos, not a setup.
        return found.values.toList().takeIf { it.size <= MAX_SOURCES }.orEmpty()
    }

    private fun source(raw: String, label: String?, type: String?, pageUrl: String): Source? {
        val value = raw.trim()
        if (!value.startsWith("https://", ignoreCase = true) &&
            !value.startsWith("//") && !value.startsWith("/")
        ) {
            return null
        }
        val url = runCatching { URI(pageUrl).resolve(value.replace(" ", "%20")) }.getOrNull()
            ?.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
            ?.takeIf { it.userInfo == null }
            ?.toASCIIString()
            ?: return null
        // Previews and ads around the player are not its files.
        if (MediaGroups.isPreviewAddress(url)) return null
        if (BrowserObservationMapper.adRole(url, null) != null) return null
        // KVS serves its files from `…/name.mp4/` with a trailing slash.
        val declared = type?.trim()?.lowercase()?.takeIf {
            it.startsWith("video/") || it.contains("mpegurl") || it == "application/dash+xml"
        }
        val mimeType = when {
            MediaUrlClassifier.classify(url, declared) != null -> declared
            SLASHED_MP4.containsMatchIn(url) -> "video/mp4"
            else -> return null
        }
        return Source(url = url, height = height(label), mimeType = mimeType)
    }

    /** "720p", "720", "HD 720p", "1080p60", "4K" -> a picture height; null for anything else. */
    internal fun height(label: String?): Int? {
        val text = label?.trim()?.lowercase() ?: return null
        if (FOUR_K.containsMatchIn(text)) return HEIGHT_4K
        if (TWO_K.containsMatchIn(text)) return HEIGHT_2K
        SIZE.find(text)?.let { size ->
            return size.groupValues[2].toIntOrNull()?.takeIf { it in HEIGHTS }
        }
        return HEIGHT.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in HEIGHTS }
    }

    private const val MAX_SCRIPT_LENGTH = 300_000
    /** P28: a player has a handful of qualities; more files are a list of other videos. */
    private const val MAX_SOURCES = 8
    private const val HEIGHT_4K = 2160
    private const val HEIGHT_2K = 1440
    private val HEIGHTS = 120..4320
    private val PLAYER_WORDS = Regex(
        """(?i)jwplayer|\.setup\s*\(|flashvars|mediaDefinitions|videojs|data-setup|""" +
            """flowplayer|clappr|plyr|["']?sources["']?\s*:|["']?playlist["']?\s*:|video_url""",
    )
    private val FLASHVARS_URL = Regex(
        """\b(video_url|video_alt_url\d*)\s*[:=]\s*['"]([^'"]{8,2000})['"]""",
    )
    private val FLAT_OBJECT = Regex("""\{[^{}]{1,3000}\}""")
    private val URL_KEY = Regex(
        """["']?\b(file|src|source|url|videoUrl|video_url|hls|mp4|contentUrl)["']?\s*:\s*""" +
            """["']((?:https:)?/[^"'\s]{4,2000})["']""",
        RegexOption.IGNORE_CASE,
    )
    private val SETUP_URL_KEY = Regex(
        """["']?\b(file|source|videoUrl|hls|contentUrl)["']?\s*:\s*""" +
            """["']((?:https:)?/[^"'\s]{4,2000})["']""",
        RegexOption.IGNORE_CASE,
    )
    private val LABEL_KEY = Regex(
        """["']?\b(label|quality|res|height|size)["']?\s*:\s*(?:["']([^"']{1,40})["']|""" +
            """(\d{3,4})(?!\d))""",
        RegexOption.IGNORE_CASE,
    )
    private val TYPE_KEY = Regex(
        """["']?\btype["']?\s*:\s*["']([a-z]+/[a-z0-9.+-]+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val SIZE = Regex("""(\d{3,4})\s*x\s*(\d{3,4})""")
    private val SLASHED_MP4 = Regex("""(?i)\.mp4/(?:\?|$)""")
    private val FOUR_K = Regex("""\b(4k|2160p?|uhd)\b""")
    private val TWO_K = Regex("""\b(2k|1440p?)\b""")
    private val HEIGHT = Regex("""(\d{3,4})\s*p?""")
}
