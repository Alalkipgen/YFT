package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.generic.manifest.ManifestReader
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
 *
 * P24: no player runs here, so the page's own words say which file is its video. JSON-LD
 * `VideoObject`s (`contentUrl`, `embedUrl`, with their `duration`), Open Graph and Twitter
 * video streams and a manifest (`.m3u8`, `.mpd`) named in the page's own scripts are
 * [PageMediaRole.MAIN]; clips from thumbnail attributes (`data-preview…`, `data-mediabook`,
 * `data-src` on thumbnails) and muted looping `<video>`s are [PageMediaRole.PREVIEW]. When the
 * page lists more than the cap, its own videos are kept first.
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
            role: PageMediaRole? = null,
            durationMillis: Long? = null,
        ) {
            val url = resolve(base, rawUrl) ?: return
            found[url]?.let { known ->
                // P24: the page's word on a file it lists twice: its video stays its video.
                val upgraded = when {
                    known.pageRole == PageMediaRole.MAIN || role == null -> known.pageRole
                    else -> role
                }
                found[url] = known.copy(
                    pageRole = upgraded,
                    durationMillis = known.durationMillis ?: durationMillis,
                )
                return
            }
            if (found.size >= maxCandidates * COLLECT_FACTOR && role != PageMediaRole.MAIN) return
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
                    durationMillis = durationMillis,
                    observedAtEpochMs = observedAtEpochMs,
                    pageRole = role,
                ),
            ) ?: return
            found[url] = candidate.copy(confidence = confidence)
        }

        // 1. Media elements: the strongest signal, even without a recognisable extension.
        MEDIA_ELEMENT.findAll(html).forEach { element ->
            val raw = element.groupValues[2]
            val attributes = attributes(raw)
            val poster = attributes["poster"]
            val role = previewElementRole(raw, attributes)
            offer(
                rawUrl = attributes.source(),
                mimeType = attributes["type"],
                poster = poster,
                confidence = CandidateConfidence.HIGH,
                requireMediaSignal = false,
                role = role,
            )
            SOURCE_TAG.findAll(element.groupValues[3]).forEach { source ->
                val sourceAttributes = attributes(source.groupValues[1])
                offer(
                    rawUrl = sourceAttributes.source(),
                    mimeType = sourceAttributes["type"],
                    poster = poster,
                    confidence = CandidateConfidence.HIGH,
                    requireMediaSignal = false,
                    role = role,
                )
            }
        }
        // Opening tags without a closing one (malformed or self-closed) still carry a src.
        MEDIA_OPENING_TAG.findAll(html).forEach { tag ->
            val raw = tag.groupValues[2]
            val attributes = attributes(raw)
            offer(
                rawUrl = attributes.source(),
                mimeType = attributes["type"],
                poster = attributes["poster"],
                confidence = CandidateConfidence.HIGH,
                requireMediaSignal = false,
                role = previewElementRole(raw, attributes),
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
                role = PageMediaRole.MAIN.takeIf { property in MAIN_STREAM_PROPERTIES },
            )
        }

        // 3. JSON-LD VideoObject / AudioObject content URLs; P24: a VideoObject's are the page's
        // video, with its stated length, and so is an embedUrl that is a media file.
        JSON_LD.findAll(html).forEach { block ->
            val json = block.groupValues[1]
            val declaresMedia = MEDIA_OBJECT.containsMatchIn(json)
            val video = VIDEO_OBJECT.containsMatchIn(json)
            val length = DURATION.findAll(json).map { it.groupValues[1] }.toList()
                .singleOrNull()
                ?.let(ManifestReader::isoDurationMillis)
                .takeIf { video }
            CONTENT_URL.findAll(json).forEach { match ->
                offer(
                    rawUrl = unescapeJson(match.groupValues[1]),
                    mimeType = null,
                    poster = null,
                    confidence = CandidateConfidence.MEDIUM,
                    requireMediaSignal = !declaresMedia,
                    role = PageMediaRole.MAIN.takeIf { video },
                    durationMillis = length,
                )
            }
            if (video) {
                EMBED_URL.findAll(json).forEach { match ->
                    offer(
                        rawUrl = unescapeJson(match.groupValues[1]),
                        mimeType = null,
                        poster = null,
                        confidence = CandidateConfidence.MEDIUM,
                        requireMediaSignal = true,
                        role = PageMediaRole.MAIN,
                        durationMillis = length,
                    )
                }
            }
        }

        // P24: a manifest the page's own scripts name is the page's video.
        INLINE_SCRIPT.findAll(html).forEach { script ->
            if (JSON_LD_TYPE.containsMatchIn(script.groupValues[1])) return@forEach
            LINK_TOKEN.findAll(unescapeJson(script.groupValues[2])).take(maxLinkTokens)
                .forEach { token ->
                    val link = token.value.trimEnd('.', ',', ';', ':', '!', '?')
                    val kind = MediaUrlClassifier.classify(link)
                    if (kind != MediaKind.HLS && kind != MediaKind.DASH) return@forEach
                    offer(
                        rawUrl = link,
                        mimeType = null,
                        poster = null,
                        confidence = CandidateConfidence.MEDIUM,
                        requireMediaSignal = true,
                        role = PageMediaRole.MAIN,
                    )
                }
        }

        // P24: clips in thumbnail attributes are previews of other pages' videos.
        ANY_TAG.findAll(html).forEach { tag ->
            val raw = tag.groupValues[2]
            if (!raw.contains("data-", ignoreCase = true)) return@forEach
            val attributes = attributes(raw)
            val thumbnail = tag.groupValues[1].equals("img", ignoreCase = true) ||
                THUMBNAIL_BOX.containsMatchIn(attributes["class"].orEmpty()) ||
                THUMBNAIL_BOX.containsMatchIn(attributes["id"].orEmpty())
            attributes.forEach { (name, value) ->
                val preview = name.startsWith("data-") && PREVIEW_ATTRIBUTE.containsMatchIn(name) ||
                    thumbnail && name == "data-src"
                if (!preview) return@forEach
                offer(
                    rawUrl = value,
                    mimeType = null,
                    poster = null,
                    confidence = CandidateConfidence.MEDIUM,
                    requireMediaSignal = true,
                    role = PageMediaRole.PREVIEW,
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
        return Result(title = title, candidates = kept(found.values.toList()))
    }

    /**
     * At most [maxCandidates], in the page's order; P24: a page that lists more keeps its own
     * videos first, so they are never the ones cut.
     */
    private fun kept(candidates: List<MediaCandidate>): List<MediaCandidate> {
        if (candidates.size <= maxCandidates) return candidates
        val (main, rest) = candidates.partition { it.pageRole == PageMediaRole.MAIN }
        return (main + rest).take(maxCandidates)
    }

    /**
     * P24: a `<video>` that loops without sound, or sits in a thumbnail box by its own class or
     * id, is a preview.
     */
    private fun previewElementRole(raw: String, attributes: Map<String, String>): PageMediaRole? {
        val mutedLoop = BOOLEAN_MUTED.containsMatchIn(raw) && BOOLEAN_LOOP.containsMatchIn(raw)
        val box = THUMBNAIL_BOX.containsMatchIn(attributes["class"].orEmpty()) ||
            THUMBNAIL_BOX.containsMatchIn(attributes["id"].orEmpty())
        return PageMediaRole.PREVIEW.takeIf { mutedLoop || box }
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

        /** P24: how many more than the cap are read, so the page's own videos are not cut. */
        const val COLLECT_FACTOR = 4
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
        val VIDEO_OBJECT = Regex(""""@type"\s*:\s*"VideoObject"""")
        val DURATION = Regex(""""duration"\s*:\s*"([^"]{1,40})"""")
        val EMBED_URL = Regex(""""embedUrl"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val INLINE_SCRIPT = Regex("""<script\b([^>]*)>(.{0,500000}?)</script\s*>""", OPTIONS)
        val JSON_LD_TYPE = Regex("""(?i)ld\+json|\bsrc\s*=""")
        val ANY_TAG = Regex("""<([a-zA-Z][a-zA-Z0-9-]*)\s([^>]{0,4000})>""")
        val PREVIEW_ATTRIBUTE = Regex("(?i)preview|mediabook|teaser")
        val THUMBNAIL_BOX = Regex("(?i)thumb|preview|teaser")
        val BOOLEAN_MUTED = Regex("""(?i)(?:^|\s)muted(?:\s|=|$)""")
        val BOOLEAN_LOOP = Regex("""(?i)(?:^|\s)loop(?:\s|=|$)""")

        /** P24: the stream properties that name the page's video, not its sound. */
        val MAIN_STREAM_PROPERTIES = setOf(
            "og:video:secure_url",
            "og:video:url",
            "og:video",
            "twitter:player:stream",
        )
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
