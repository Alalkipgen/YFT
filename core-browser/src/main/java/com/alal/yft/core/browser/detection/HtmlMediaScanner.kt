package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI

/**
 * Finds media a page declares in its markup, without running any of it.
 *
 * This is the headless counterpart of the browser's DOM probe, used when Home checks a link
 * before anything is opened: `<video>` and `<audio>` sources (and their `<source>` children),
 * Open Graph video and audio tags, JSON-LD `contentUrl` values, and absolute links whose path
 * ends in a media extension, including JSON-escaped ones inside inline scripts. Only HTTPS
 * results are kept, because that is all YFT downloads. Input size is the caller's bound; the
 * candidate count is capped here.
 */
class HtmlMediaScanner(
    private val maxCandidates: Int = 50,
    private val maxLinkTokens: Int = 10_000,
) {
    init {
        require(maxCandidates > 0)
        require(maxLinkTokens > 0)
    }

    data class Result(
        val title: String?,
        val candidates: List<MediaCandidate>,
    )

    fun scan(html: String, pageUrl: String, observedAtEpochMs: Long): Result {
        val base = runCatching { URI(pageUrl) }.getOrNull()
            ?: return Result(title = null, candidates = emptyList())
        val found = LinkedHashMap<String, MediaCandidate>()
        val meta = metaTags(html)

        fun offer(
            rawUrl: String?,
            mimeType: String?,
            poster: String?,
            confidence: CandidateConfidence,
            requireMediaSignal: Boolean,
        ) {
            if (found.size >= maxCandidates) return
            val url = resolve(base, rawUrl) ?: return
            if (url in found) return
            val type = mimeType?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
            if (type != null && !type.isMediaType()) return
            if (requireMediaSignal && MediaUrlClassifier.classify(url, type) == null) return
            val candidate = BrowserObservationMapper.fromDom(
                DomMediaObservation(
                    pageUrl = pageUrl,
                    mediaUrl = url,
                    mimeType = type,
                    title = null,
                    thumbnailUrl = resolve(base, poster),
                    durationMillis = null,
                    observedAtEpochMs = observedAtEpochMs,
                ),
            ) ?: return
            found[url] = candidate.copy(confidence = confidence)
        }

        // 1. Media elements: the strongest signal, even without a recognisable extension.
        MEDIA_ELEMENT.findAll(html).forEach { element ->
            val attributes = attributes(element.groupValues[2])
            val poster = attributes["poster"]
            offer(
                rawUrl = attributes.source(),
                mimeType = attributes["type"],
                poster = poster,
                confidence = CandidateConfidence.HIGH,
                requireMediaSignal = false,
            )
            SOURCE_TAG.findAll(element.groupValues[3]).forEach { source ->
                val sourceAttributes = attributes(source.groupValues[1])
                offer(
                    rawUrl = sourceAttributes.source(),
                    mimeType = sourceAttributes["type"],
                    poster = poster,
                    confidence = CandidateConfidence.HIGH,
                    requireMediaSignal = false,
                )
            }
        }
        // Opening tags without a closing one (malformed or self-closed) still carry a src.
        MEDIA_OPENING_TAG.findAll(html).forEach { tag ->
            val attributes = attributes(tag.groupValues[2])
            offer(
                rawUrl = attributes.source(),
                mimeType = attributes["type"],
                poster = attributes["poster"],
                confidence = CandidateConfidence.HIGH,
                requireMediaSignal = false,
            )
        }

        // 2. Open Graph and Twitter stream tags. og:video is often an HTML player page, so a
        // declared type or a media URL is required.
        val image = meta["og:image"] ?: meta["og:image:secure_url"]
        OPEN_GRAPH_MEDIA.forEach { (property, typeProperty) ->
            offer(
                rawUrl = meta[property],
                mimeType = meta[typeProperty],
                poster = image,
                confidence = CandidateConfidence.MEDIUM,
                requireMediaSignal = meta[typeProperty] == null,
            )
        }

        // 3. JSON-LD VideoObject / AudioObject content URLs.
        JSON_LD.findAll(html).forEach { block ->
            val json = block.groupValues[1]
            val declaresMedia = MEDIA_OBJECT.containsMatchIn(json)
            CONTENT_URL.findAll(json).forEach { match ->
                offer(
                    rawUrl = unescapeJson(match.groupValues[1]),
                    mimeType = null,
                    poster = null,
                    confidence = CandidateConfidence.MEDIUM,
                    requireMediaSignal = !declaresMedia,
                )
            }
        }

        // 4. Any absolute link with a media extension, including `https:\/\/…` in scripts.
        val unescaped = unescapeJson(html)
        LINK_TOKEN.findAll(unescaped).take(maxLinkTokens).forEach { token ->
            offer(
                rawUrl = token.value.trimEnd('.', ',', ';', ':', '!', '?'),
                mimeType = null,
                poster = null,
                confidence = CandidateConfidence.MEDIUM,
                requireMediaSignal = true,
            )
        }

        val title = (meta["og:title"] ?: meta["twitter:title"] ?: titleOf(html))
            ?.let(::decodeEntities)
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.take(MAX_TITLE_LENGTH)
            ?.takeIf(String::isNotEmpty)
        return Result(title = title, candidates = found.values.toList())
    }

    private fun metaTags(html: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        META_TAG.findAll(html).forEach { tag ->
            val attributes = attributes(tag.groupValues[1])
            val key = (attributes["property"] ?: attributes["name"])?.trim()?.lowercase()
                ?: return@forEach
            val content = attributes["content"]?.trim()?.takeIf(String::isNotEmpty)
                ?: return@forEach
            values.putIfAbsent(key, content)
        }
        return values
    }

    private fun titleOf(html: String): String? = TITLE.find(html)?.groupValues?.get(1)

    private fun attributes(raw: String): Map<String, String> = buildMap {
        ATTRIBUTE.findAll(raw).forEach { match ->
            val name = match.groupValues[1].lowercase()
            val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value
            if (value != null && name !in this) put(name, decodeEntities(value))
        }
    }

    private fun Map<String, String>.source(): String? = this["src"] ?: this["data-src"]

    private fun resolve(base: URI, raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (value.startsWith("blob:", ignoreCase = true) ||
            value.startsWith("data:", ignoreCase = true)
        ) {
            return null
        }
        val resolved = runCatching { base.resolve(value.replace(" ", "%20")) }.getOrNull()
            ?: return null
        if (!resolved.scheme.equals("https", ignoreCase = true)) return null
        if (resolved.host.isNullOrBlank() || resolved.userInfo != null) return null
        return resolved.toASCIIString()
    }

    private fun String.isMediaType(): Boolean =
        startsWith("video/") || startsWith("audio/") ||
            this == "application/dash+xml" || contains("mpegurl")

    private companion object {
        const val MAX_TITLE_LENGTH = 200
        val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

        // Bounded lazy bodies keep an unclosed tag from rescanning the rest of the page.
        val MEDIA_ELEMENT = Regex("""<(video|audio)\b([^>]*)>(.{0,20000}?)</\1\s*>""", OPTIONS)
        val MEDIA_OPENING_TAG = Regex("""<(video|audio)\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val SOURCE_TAG = Regex("""<source\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val META_TAG = Regex("""<meta\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val TITLE = Regex("""<title\b[^>]*>(.{0,2000}?)</title\s*>""", OPTIONS)
        val JSON_LD = Regex(
            """<script\b[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>""" +
                """(.{0,200000}?)</script\s*>""",
            OPTIONS,
        )
        val MEDIA_OBJECT = Regex(""""@type"\s*:\s*"(?:VideoObject|AudioObject)"""")
        val CONTENT_URL = Regex(""""contentUrl"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val ATTRIBUTE = Regex(
            """([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
        )
        val LINK_TOKEN = Regex("""https://[^\s"'<>()\\{}|^`\[\]]+""", RegexOption.IGNORE_CASE)
        val WHITESPACE = Regex("""\s+""")
        val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);""")

        /** Open Graph media properties with the property that declares their MIME type. */
        val OPEN_GRAPH_MEDIA = listOf(
            "og:video:secure_url" to "og:video:type",
            "og:video:url" to "og:video:type",
            "og:video" to "og:video:type",
            "og:audio:secure_url" to "og:audio:type",
            "og:audio:url" to "og:audio:type",
            "og:audio" to "og:audio:type",
            "twitter:player:stream" to "twitter:player:stream:content_type",
        )

        fun decodeEntities(value: String): String = ENTITY.replace(value) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> {
                    val code = if (entity.startsWith("#x", ignoreCase = true)) {
                        entity.substring(2).toIntOrNull(16)
                    } else {
                        entity.substring(1).toIntOrNull()
                    }
                    code?.takeIf { it in 1..0x10FFFF }
                        ?.let { String(Character.toChars(it)) }
                        ?: match.value
                }
            }
        }

        /** Undoes the JSON escapes that hide links in scripts: `\/`, `\u002F` and `\u0026`. */
        fun unescapeJson(value: String): String = value
            .replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\u003D", "=", ignoreCase = true)
    }
}
