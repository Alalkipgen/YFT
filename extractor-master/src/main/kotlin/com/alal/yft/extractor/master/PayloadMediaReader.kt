package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.generic.manifest.ManifestReader

internal data class Discovery(
    val candidates: List<MediaCandidate>,
    val details: List<String>,
    val terminalFailure: SiteExtractionFailure? = null,
)

/**
 * Bounded, read-only discovery from material already delivered to a page.
 *
 * Uses the existing JSON reader/classifier rather than copying their implementations. Known
 * player-field shapes are deliberately small; no endpoints, signatures or login flows are
 * invented. Unaddressed/cipher-only formats wait for the browser's own decoded requests.
 */
internal class PayloadMediaReader {
    fun read(request: MasterRequest, snapshot: PageSnapshot): Discovery {
        val read = Reader(request)
        snapshot.html?.let(read::html)
        snapshot.apiResponses.forEach(read::json)
        snapshot.requests.forEach { observed ->
            if (!observed.method.equals("GET", true)) return@forEach
            val url = UrlPolicy.secure(observed.url) ?: return@forEach
            val focused = snapshot.playingMediaUrl?.let(UrlPolicy::secure)
                ?.let(UrlPolicy::whole) == UrlPolicy.whole(url)
            if (
                request.expectedContentId != null && observed.contentId != null &&
                observed.contentId != request.expectedContentId
            ) {
                return@forEach
            }
            val candidate = read.candidate(
                url = url,
                mime = observed.mimeType,
                id = observed.contentId ?: request.expectedContentId.takeIf { focused },
                role = observed.pageRole ?: PageMediaRole.MAIN.takeIf { focused },
                source = CandidateSource.REQUEST,
            ) ?: return@forEach
            read.add(
                candidate.copy(
                    requestContext = UrlPolicy.context(
                        observed.context, url, url, request.pageUrl,
                    ),
                    observedAtEpochMs = observed.observedAtEpochMs,
                ),
            )
        }
        return Discovery(
            read.candidates,
            listOf("master: discovered ${read.candidates.size} bounded media observations"),
            read.terminalFailure,
        )
    }

    private class Reader(private val request: MasterRequest) {
        val candidates = mutableListOf<MediaCandidate>()
        var terminalFailure: SiteExtractionFailure? = null
        private var document = 0
        private var nodes = 0

        fun add(candidate: MediaCandidate) {
            if (candidates.size < MAX_RAW_CANDIDATES) candidates += candidate
        }

        fun candidate(
            url: String?,
            mime: String? = null,
            id: String? = null,
            role: PageMediaRole? = PageMediaRole.MAIN,
            source: CandidateSource = CandidateSource.MANIFEST,
            node: JsonValue? = null,
            key: String? = null,
        ): MediaCandidate? {
            val address = UrlPolicy.resolve(request.pageUrl, url) ?: return null
            val kind = MediaUrlClassifier.classify(address, mime) ?: return null
            if (kind == MediaKind.DIRECT && isSegment(address)) return null
            return MediaCandidate(
                pageUrl = request.pageUrl,
                mediaUrl = address,
                sources = setOf(source),
                kind = kind,
                mimeType = mime,
                title = node["title"].asStringOrNull ?: node["name"].asStringOrNull,
                durationMillis = ManifestReader.isoDurationMillis(
                    node["duration"].asStringOrNull ?: node["duration"].asLongOrNull?.toString(),
                ),
                contentLengthBytes = node["contentLength"].asLongOrNull?.takeIf { it > 0 },
                requestContext = UrlPolicy.context(
                    request.requestContext, request.pageUrl, address, request.pageUrl,
                ),
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = UrlPolicy.expiry(address),
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
                codecs = codecs(mime),
                videoId = id?.let { "master:${requestSite()}:$it" },
                width = dimension(node["width"]),
                height = dimension(node["height"]),
                bitrateBitsPerSecond = node["bitrate"].asLongOrNull?.takeIf { it > 0 },
                pageRole = role,
                pageVideoKey = key,
            )
        }

        fun html(body: String) {
            val scripts = SCRIPT.findAll(body).toList()
            val markup = SCRIPT.replace(body, "")
            val videos = VIDEO.findAll(markup).toList()
            videos.forEachIndexed { index, match ->
                val key = "master:html-player:$index"
                val role = PageMediaRole.MAIN.takeIf { videos.size == 1 }
                (listOf(match.groupValues[1]) + SOURCE.findAll(match.groupValues[2])
                    .map { it.groupValues[1] }.toList()).forEach { tag ->
                    val attrs = attributes(tag)
                    candidate(
                        attrs["src"], attrs["type"], request.expectedContentId, role,
                        CandidateSource.DOM, key = key,
                    )?.let(::add)
                }
            }
            META.findAll(markup).forEach { match ->
                val attrs = attributes(match.groupValues[1])
                if (attrs["property"]?.lowercase() in OG_VIDEO) {
                    candidate(
                        attrs["content"], id = request.expectedContentId,
                        source = CandidateSource.DOM, key = "master:opengraph",
                    )?.let(::add)
                }
            }
            scripts.forEach { match ->
                val attrs = attributes(match.groupValues[1])
                if (
                    attrs["type"]?.lowercase() in JSON_TYPES ||
                    attrs["id"] in JSON_SCRIPT_IDS
                ) {
                    json(match.groupValues[2])
                }
            }
            assignedPlayerJson(body)?.let(::json)
        }

        fun json(body: String) {
            val root = BoundedJsonParser.parse(body, maxDepth = 48, maxNodes = 30_000) ?: return
            document += 1
            val pending = ArrayDeque<Pair<JsonValue, String?>>()
            pending.add(root to null)
            while (pending.isNotEmpty() && nodes < MAX_VISITED_NODES) {
                val (value, inheritedId) = pending.removeLast()
                nodes += 1
                when (value) {
                    is JsonValue.Array -> value.items.asReversed().forEach {
                        pending.add(it to inheritedId)
                    }
                    is JsonValue.Object -> {
                        val id = contentId(value) ?: inheritedId
                        val matches = request.expectedContentId == null ||
                            id == request.expectedContentId
                        if (matches) node(value, id, "master:payload:$document:$nodes")
                        value.entries.values.toList().asReversed().forEach {
                            pending.add(it to id)
                        }
                    }
                    else -> Unit
                }
            }
        }

        private fun node(node: JsonValue.Object, id: String?, key: String) {
            if (
                node["isDrm"].asBooleanOrNull == true ||
                node["is_drm_protected"].asBooleanOrNull == true
            ) {
                terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                return
            }
            if (node["streamingData"] != null) youtube(node, id, key)
            val fields = listOf(
                "browser_native_hd_url", "browser_native_sd_url", "playable_url",
                "playable_url_quality_hd", "video_url", "contentUrl", "dash_manifest_url",
            )
            fields.forEach { field ->
                val mime = when (field) {
                    "dash_manifest_url" -> "application/dash+xml"
                    "contentUrl" -> node["encodingFormat"].asStringOrNull
                    else -> "video/mp4"
                }
                candidate(node[field].asStringOrNull, mime, id, node = node, key = key)
                    ?.let(::add)
            }
            node["video_versions"].asArrayOrEmpty.forEach { version ->
                candidate(version["url"].asStringOrNull, "video/mp4", id, node = version,
                    key = key)?.let(::add)
            }
            listOf("playAddr", "downloadAddr").forEach { field ->
                val address = node[field].asStringOrNull
                candidate(address, "video/mp4", id, node = node, key = key)?.let(::add)
                node[field]["UrlList"].asArrayOrEmpty.forEach {
                    candidate(it.asStringOrNull, "video/mp4", id, node = node, key = key)
                        ?.let(::add)
                }
            }
            node["bitrateInfo"].asArrayOrEmpty.forEach { version ->
                version.path("PlayAddr", "UrlList").asArrayOrEmpty.forEach {
                    candidate(it.asStringOrNull, "video/mp4", id, node = version, key = key)
                        ?.let(::add)
                }
            }
            node["video_info"]["variants"].asArrayOrEmpty.forEach { version ->
                candidate(
                    version["url"].asStringOrNull,
                    version["content_type"].asStringOrNull,
                    id, node = version, key = key,
                )?.let(::add)
            }
            listOf("dash_manifest_xml_string", "dash_manifest").forEach { field ->
                node[field].asStringOrNull?.takeIf { it.trimStart().startsWith("<") }?.let {
                    val parsed = InlineDashReader.read(it, request, id, key)
                    candidates.addAll(parsed.candidates.take(MAX_RAW_CANDIDATES - candidates.size))
                    if (parsed.drm) terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                }
            }
        }

        private fun youtube(node: JsonValue, id: String?, key: String) {
            val status = node["playabilityStatus"]["status"].asStringOrNull
            if (status != null && status != "OK") return
            if (node["videoDetails"]["isLive"].asBooleanOrNull == true) return
            val data = node["streamingData"]
            if (data["drmFamilies"].asArrayOrEmpty.isNotEmpty()) {
                terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                return
            }
            val progressive = data["formats"].asArrayOrEmpty.mapNotNull {
                candidate(it["url"].asStringOrNull, it["mimeType"].asStringOrNull, id,
                    node = it, key = key)
            }
            val adaptive = data["adaptiveFormats"].asArrayOrEmpty.mapNotNull {
                candidate(it["url"].asStringOrNull, it["mimeType"].asStringOrNull, id,
                    node = it, key = key)
            }
            val audio = adaptive.filter { it.mimeType?.startsWith("audio/mp4") == true }
                .filter { it.codecs.any { codec -> codec.startsWith("mp4a") } }
                .maxByOrNull { it.bitrateBitsPerSecond ?: 0 }
            (progressive + adaptive).forEach { stream ->
                val companion = audio?.takeIf {
                    stream in adaptive && stream.mimeType?.startsWith("video/mp4") == true &&
                        stream.codecs.any { codec -> codec.startsWith("avc1") }
                }?.let(::companion)
                add(
                    stream.copy(
                        title = node["videoDetails"]["title"].asStringOrNull,
                        durationMillis = node["videoDetails"]["lengthSeconds"].asLongOrNull
                            ?.takeIf { it in 1..Long.MAX_VALUE / 1_000 }?.times(1_000),
                        audioCompanion = companion,
                    ),
                )
            }
        }

        private fun contentId(node: JsonValue): String? {
            node["videoDetails"]["videoId"].asStringOrNull?.let { return it }
            listOf("shortcode", "code", "rest_id", "videoId", "video_id").forEach {
                node[it].asStringOrNull?.let { value -> return value }
            }
            val hasMedia = node["video"] != null || MEDIA_ID_FIELDS.any { node[it] != null }
            return if (hasMedia) {
                node["id"].asStringOrNull ?: node["id"].asLongOrNull?.toString()
            } else {
                null
            }
        }

        private fun requestSite(): String =
            UrlPolicy.origin(request.pageUrl)?.substringAfter("https://").orEmpty()
    }

    companion object {
        private const val MAX_RAW_CANDIDATES = 200
        private const val MAX_VISITED_NODES = 30_000
        private val VIDEO = Regex("""(?is)<video\b([^>]*)>(.*?)</video\s*>""")
        private val SOURCE = Regex("""(?is)<source\b([^>]*)>""")
        private val META = Regex("""(?is)<meta\b([^>]*)>""")
        private val SCRIPT = Regex("""(?is)<script\b([^>]*)>(.*?)</script\s*>""")
        private val ATTRIBUTE = Regex(
            """([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        )
        private val OG_VIDEO = setOf("og:video", "og:video:url", "og:video:secure_url")
        private val JSON_TYPES = setOf("application/json", "application/ld+json")
        private val JSON_SCRIPT_IDS = setOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE")
        private val MEDIA_ID_FIELDS = setOf(
            "browser_native_hd_url", "playable_url", "video_versions", "video_url",
            "dash_manifest_xml_string", "dash_manifest", "video_info",
        )
        private val CODECS = Regex("""(?i)codecs\s*=\s*"([^"]+)"""")
        private val ASSIGNMENT = Regex("""\bytInitialPlayerResponse\s*=\s*(?=\{)""")

        private fun attributes(tag: String): Map<String, String> =
            ATTRIBUTE.findAll(tag).associate { match ->
                match.groupValues[1].lowercase() to
                    match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
            }

        internal fun codecs(mime: String?): List<String> =
            mime?.let { CODECS.find(it)?.groupValues?.get(1) }
                ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

        internal fun dimension(value: JsonValue?): Int? =
            value.asLongOrNull?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()

        internal fun companion(candidate: MediaCandidate) = CompanionAudio(
            mediaUrl = candidate.mediaUrl,
            mimeType = candidate.mimeType?.substringBefore(';') ?: "audio/mp4",
            codecs = candidate.codecs,
            requestContext = candidate.requestContext,
            contentLengthBytes = candidate.contentLengthBytes,
            bitrateBitsPerSecond = candidate.bitrateBitsPerSecond,
            expiresAtEpochMs = candidate.expiresAtEpochMs,
        )

        private fun isSegment(url: String): Boolean =
            url.substringBefore('?').substringAfterLast('.').lowercase() in setOf("ts", "m4s")

        private fun assignedPlayerJson(html: String): String? {
            val begin = ASSIGNMENT.find(html)?.range?.last?.plus(1) ?: return null
            var depth = 0
            var quoted = false
            var escaped = false
            for (index in begin until html.length) {
                val character = html[index]
                if (quoted) {
                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == '"' -> quoted = false
                    }
                } else {
                    when (character) {
                        '"' -> quoted = true
                        '{' -> depth += 1
                        '}' -> {
                            depth -= 1
                            if (depth == 0) return html.substring(begin, index + 1)
                        }
                    }
                }
            }
            return null
        }
    }
}