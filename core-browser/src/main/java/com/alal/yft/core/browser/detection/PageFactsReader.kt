package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.extractor.generic.manifest.ManifestReader
import java.net.URI

/**
 * P28: reads what a page states about its own video ([PageVideoFacts]) from its meta tags and
 * JSON-LD only, never from its visible text: the length (`VideoObject.duration`, even when the
 * object names no media file, `og:video:duration`, `video:duration`, `itemprop="duration"`), the
 * title (`og:title`, the VideoObject's `name`, else the page title without the site's name) and
 * the picture (`og:image`, the VideoObject's `thumbnailUrl`). Home reads the HTML
 * ([fromHtml]); the browser's DOM probe sends the same parts from the live page ([fromParts]).
 */
object PageFactsReader {
    fun fromHtml(html: String, pageUrl: String): PageVideoFacts {
        val meta = LinkedHashMap<String, String>()
        META_TAG.findAll(html).forEach { tag ->
            val attributes = attributes(tag.groupValues[1])
            val key = (attributes["property"] ?: attributes["name"] ?: attributes["itemprop"])
                ?.trim()?.lowercase() ?: return@forEach
            val content = attributes["content"]?.trim()?.takeIf(String::isNotEmpty)
                ?: return@forEach
            meta.putIfAbsent(key, content)
        }
        val jsonLd = JSON_LD.findAll(html).map { it.groupValues[1] }.take(MAX_JSON_LD).toList()
        val title = TITLE.find(html)?.groupValues?.get(1)
        return fromParts(meta, jsonLd, title, pageUrl)
    }

    /**
     * The facts from a page's [meta] tags (lower-case `property`, `name` or `itemprop` to
     * `content`), its JSON-LD blocks and its [documentTitle].
     */
    fun fromParts(
        meta: Map<String, String>,
        jsonLd: List<String>,
        documentTitle: String?,
        pageUrl: String,
    ): PageVideoFacts {
        val video = jsonLd.asSequence().take(MAX_JSON_LD)
            .mapNotNull { block -> MiniJson.parse(block.trim()) }
            .flatMap { videoObjects(it, depth = 0) }
            .toList()
        // Several VideoObjects of different lengths (a list of related videos) state nothing.
        val ldLength = video.mapNotNull { length(it["duration"] as? String) }.distinct()
            .singleOrNull()
        val stated = ldLength
            ?: length(meta["og:video:duration"])
            ?: length(meta["video:duration"])
            ?: length(meta["duration"])
        val title = clean(meta["og:title"])
            ?: clean(meta["twitter:title"])
            ?: video.firstNotNullOfOrNull { clean(it["name"] as? String) }
            ?: withoutSiteName(clean(documentTitle), meta["og:site_name"], pageUrl)
        val picture = https(pageUrl, meta["og:image:secure_url"])
            ?: https(pageUrl, meta["og:image"])
            ?: video.firstNotNullOfOrNull { https(pageUrl, firstText(it["thumbnailUrl"])) }
            ?: https(pageUrl, meta["twitter:image"])
        return PageVideoFacts(durationMillis = stated, title = title, thumbnailUrl = picture)
    }

    /** A length as JSON-LD and Open Graph state it: ISO 8601, seconds, or `h:mm:ss`. */
    internal fun length(value: String?): Long? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        CLOCK.matchEntire(text)?.let { match ->
            val parts = match.value.split(':').map { it.toLong() }
            val seconds = parts.fold(0L) { total, part -> total * SIXTY + part }
            return (seconds * MILLIS_PER_SECOND).takeIf { it > 0 && it <= MAX_LENGTH_MILLIS }
        }
        return ManifestReader.isoDurationMillis(text)?.takeIf { it <= MAX_LENGTH_MILLIS }
    }

    private fun videoObjects(value: Any?, depth: Int): Sequence<Map<*, *>> {
        if (depth > MAX_WALK_DEPTH) return emptySequence()
        return when (value) {
            is Map<*, *> -> {
                val own = if (isVideoObject(value["@type"])) sequenceOf(value) else emptySequence()
                own + value.values.asSequence()
                    .filter { it is Map<*, *> || it is List<*> }
                    .flatMap { videoObjects(it, depth + 1) }
            }
            is List<*> -> value.asSequence().flatMap { videoObjects(it, depth + 1) }
            else -> emptySequence()
        }
    }

    private fun isVideoObject(type: Any?): Boolean = when (type) {
        is String -> type.equals("VideoObject", ignoreCase = true)
        is List<*> -> type.any { it is String && it.equals("VideoObject", ignoreCase = true) }
        else -> false
    }

    private fun firstText(value: Any?): String? = when (value) {
        is String -> value
        is List<*> -> value.firstOrNull { it is String } as? String
        is Map<*, *> -> value["url"] as? String
        else -> null
    }

    private fun clean(value: String?): String? =
        value?.let { HtmlMediaScanner.decodeEntities(it) }
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.take(MAX_TITLE_LENGTH)
            ?.takeIf(String::isNotEmpty)

    /**
     * The page title without the site's name: "Long walk - Example Videos" -> "Long walk" when
     * the last part names the site (its `og:site_name` or its host's name).
     */
    internal fun withoutSiteName(title: String?, siteName: String?, pageUrl: String): String? {
        val text = title ?: return null
        val separator = SEPARATORS.findAll(text).lastOrNull() ?: return text
        val head = text.substring(0, separator.range.first).trim()
        val tail = text.substring(separator.range.last + 1).trim().lowercase()
        if (head.isEmpty() || tail.isEmpty()) return text
        val host = runCatching { URI(pageUrl).host }.getOrNull()?.lowercase()
            ?.removePrefix("www.")
        val hostName = host?.split('.')?.dropLast(1)?.lastOrNull()
        val compactTail = tail.replace(NON_WORD, "")
        val names = listOfNotNull(siteName?.trim()?.lowercase(), hostName, host)
            .map { it.replace(NON_WORD, "") }
            .filter(String::isNotEmpty)
        return if (names.any { compactTail.contains(it) || it.contains(compactTail) }) {
            head
        } else {
            text
        }
    }

    private fun https(pageUrl: String, raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val resolved = runCatching { URI(pageUrl).resolve(value.replace(" ", "%20")) }.getOrNull()
            ?: return null
        if (!resolved.scheme.equals("https", ignoreCase = true)) return null
        if (resolved.host.isNullOrBlank() || resolved.userInfo != null) return null
        return resolved.toASCIIString()
    }

    private fun attributes(raw: String): Map<String, String> = buildMap {
        ATTRIBUTE.findAll(raw).forEach { match ->
            val name = match.groupValues[1].lowercase()
            val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value
            if (value != null && name !in this) put(name, HtmlMediaScanner.decodeEntities(value))
        }
    }

    private const val MAX_JSON_LD = 8
    private const val MAX_WALK_DEPTH = 6
    private const val MAX_TITLE_LENGTH = 200
    private const val SIXTY = 60L
    private const val MILLIS_PER_SECOND = 1_000L

    /** Longer than a day is not a video's length (live streams state odd values). */
    private const val MAX_LENGTH_MILLIS = 86_400_000L
    private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val META_TAG = Regex("""<meta\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("""<title\b[^>]*>(.{0,2000}?)</title\s*>""", OPTIONS)
    private val JSON_LD = Regex(
        """<script\b[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>""" +
            """(.{0,200000}?)</script\s*>""",
        OPTIONS,
    )
    private val ATTRIBUTE = Regex(
        """([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
    )
    private val CLOCK = Regex("""\d{1,2}(?::\d{1,2}){1,2}""")
    private val SEPARATORS = Regex("""\s[|\-–—·]\s""")
    private val WHITESPACE = Regex("""\s+""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]""")
}
