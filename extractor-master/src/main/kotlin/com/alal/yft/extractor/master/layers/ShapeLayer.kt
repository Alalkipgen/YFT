package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.master.recipes.PayloadRecipes
import com.alal.yft.extractor.master.toolkit.AnchoredMediaWalk
import com.alal.yft.extractor.master.toolkit.BalancedJson
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.HtmlScan
import com.alal.yft.extractor.master.toolkit.InlineDashReader
import com.alal.yft.extractor.master.toolkit.MediaKeyTable
import com.alal.yft.extractor.master.toolkit.PageScripts
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import java.net.URI

/**
 * L3 (Phase 1 R3): key-name-independent search for media-shaped JSON in delivered material.
 *
 * It reads more places than L4 (every JSON script by its own type/id, objects assigned in
 * scripts, JSON inside strings, entity-encoded JSON attributes, API responses) and accepts an
 * address by its shape: a known key from [MediaKeyTable], a media file extension, a sibling
 * MIME type, or a media-hinted key next to file facts (width, bitrate, …). Every node is
 * **anchored to the expected content ID** ([AnchoredMediaWalk]); without one, nothing L3 finds
 * is named the page's main video, so only the playing file (or its group) can select it.
 *
 * Additive: [LayerStack] drops what an earlier layer already found, and L3 rows are
 * [CandidateConfidence.MEDIUM], so they never displace L1/L2/L4 rows under the candidate cap.
 * Never on a YouTube host, never inside `streamingData` (R2: only the YouTube module answers).
 */
internal class ShapeLayer : MasterLayer {
    override val id = LayerId.L3_SHAPE
    override val additive = true

    override fun collect(request: MasterRequest, snapshot: PageSnapshot): Evidence {
        if (TerminalRules.youtube(request.pageUrl)) return Evidence(emptyList())
        val search = Search(request)
        documents(snapshot).forEach(search::document)
        val details = if (search.rejected > 0) {
            listOf("master: shape search left out ${search.rejected} other-video nodes")
        } else {
            emptyList()
        }
        return Evidence(search.candidates, details, search.terminalFailure)
    }

    /** JSON documents in reading order: what L4 reads first, then the wider finders. */
    private fun documents(snapshot: PageSnapshot): Sequence<String> = sequence {
        val html = snapshot.html
        val scripts = html?.let(PageScripts::scripts).orEmpty()
        scripts.forEach { script ->
            if (script.type in PayloadRecipes.JSON_SCRIPT_TYPES || script.id in DATA_SCRIPT_IDS) {
                yield(script.body)
            }
        }
        yieldAll(snapshot.apiResponses)
        if (html == null) return@sequence
        scripts.forEach { script ->
            if (script.type in PayloadRecipes.JSON_SCRIPT_TYPES || script.id in DATA_SCRIPT_IDS) {
                return@forEach
            }
            val body = script.body
            if (script.type in JSON_LIKE_TYPES || (script.type == null && looksLikeJson(body))) {
                yield(body)
            } else {
                yieldAll(PageScripts.assigned(script))
            }
        }
        yieldAll(PageScripts.embeddedDocuments(html))
        JSON_ATTRIBUTE.findAll(HtmlScan.SCRIPT.replace(html, "")).take(MAX_ATTRIBUTES).forEach {
            yield(BalancedJson.decodeEntities(it.groupValues[1]))
        }
    }

    private class Search(private val request: MasterRequest) {
        val candidates = mutableListOf<MediaCandidate>()
        var terminalFailure: SiteExtractionFailure? = null
        var rejected = 0
        private val factory = CandidateFactory(request)
        private val pageHost = host(request.pageUrl)
        private val seen = HashSet<String>()
        private var documentCount = 0
        private var documentExpiry: Long? = null
        private var nodeBudget = MAX_VISITED_NODES

        fun document(body: String) {
            if (documentCount >= MAX_DOCUMENTS || nodeBudget <= 0) return
            if (body.length > BalancedJson.MAX_CHARS) return
            val root = BalancedJson.lenientParse(body) ?: return
            documentCount += 1
            val walk = AnchoredMediaWalk(request.expectedContentId, nodeBudget)
            val doc = documentCount
            val first = candidates.size
            documentExpiry = null
            walk.walk(root, holdsMedia = ::holdsMedia) { visit -> node(visit, doc) }
            // A config's own `expires` (Vimeo `request.expires`) bounds every file it lists.
            documentExpiry?.let { expiry ->
                for (index in first until candidates.size) {
                    val found = candidates[index]
                    candidates[index] = found.copy(
                        expiresAtEpochMs = minOf(found.expiresAtEpochMs ?: Long.MAX_VALUE, expiry),
                    )
                }
            }
            nodeBudget -= walk.visited
            rejected += walk.rejected
        }

        private fun node(visit: AnchoredMediaWalk.Visit, doc: Int): List<JsonValue> {
            val nested = mutableListOf<JsonValue>()
            if (MediaKeyTable.drmStatement(visit.node)) {
                terminalFailure = SiteExtractionFailure.DRM_PROTECTED
            }
            MediaKeyTable.EXPIRY_FIELDS.forEach { field ->
                visit.node[field].asLongOrNull?.takeIf { it in UrlPolicy.PLAUSIBLE_EXPIRY_SECONDS }
                    ?.let { seconds ->
                        documentExpiry = minOf(documentExpiry ?: Long.MAX_VALUE, seconds * 1_000)
                    }
            }
            val role = when {
                visit.anchored -> PageMediaRole.MAIN
                else -> null
            }
            val key = "master:shape:$doc:${visit.contentId ?: "page"}"
            visit.node.entries.forEach { (name, value) ->
                if (name in MediaKeyTable.SKIP_SUBTREES) return@forEach
                when (value) {
                    is JsonValue.Text -> text(name, value.value, visit, role, key, nested)
                    is JsonValue.Array -> if (listOfAddresses(name, visit.key)) {
                        value.items.take(MAX_LIST).forEach { item ->
                            item.asStringOrNull?.let {
                                address(name, it, visit, role, key, listParent = visit.key)
                            }
                        }
                    }
                    else -> Unit
                }
            }
            return nested
        }

        private fun text(
            name: String,
            value: String,
            visit: AnchoredMediaWalk.Visit,
            role: PageMediaRole?,
            key: String,
            nested: MutableList<JsonValue>,
        ) {
            val trimmed = value.trimStart()
            when {
                trimmed.startsWith("<") && trimmed.contains("<MPD", ignoreCase = true) -> {
                    val parsed = InlineDashReader.read(trimmed, request, visit.contentId, key)
                    parsed.candidates.forEach { add(it.copy(pageRole = role), it.mediaUrl) }
                    if (parsed.drm) terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                }
                name in MediaKeyTable.DRM_INFO_FIELDS -> Unit // read by drmStatement only
                (trimmed.startsWith("{") || trimmed.startsWith("[")) &&
                    nestedCount < MAX_NESTED && value.length <= MAX_NESTED_CHARS -> {
                    BalancedJson.lenientParse(value)?.let {
                        nestedCount += 1
                        nested += it
                    }
                }
                else -> address(name, value, visit, role, key, listParent = null)
            }
        }

        private var nestedCount = 0

        private fun address(
            name: String,
            raw: String,
            visit: AnchoredMediaWalk.Visit,
            role: PageMediaRole?,
            key: String,
            listParent: String?,
        ) {
            if (!raw.startsWith("https://", true) && !raw.startsWith("//") &&
                !raw.startsWith("/")
            ) {
                return
            }
            val mime = mimeFor(name, raw, visit, listParent) ?: return
            val guessed = mime == GUESSED_MP4
            val shaped = if (MediaKeyTable.hinted(name, MediaKeyTable.PREVIEW_HINTS) ||
                MediaKeyTable.hinted(visit.key, MediaKeyTable.PREVIEW_HINTS)
            ) {
                PageMediaRole.PREVIEW
            } else {
                role
            }
            val candidate = factory.candidate(
                raw, if (guessed) "video/mp4" else mime.ifEmpty { null }, visit.contentId, shaped,
                CandidateSource.MANIFEST, node = visit.node, key = key,
            ) ?: return
            if (
                guessed && host(candidate.mediaUrl) == pageHost &&
                !MediaKeyTable.hinted(name, MediaKeyTable.PLAYER_HINTS) &&
                !MediaKeyTable.hinted(listParent, MediaKeyTable.PLAYER_HINTS)
            ) {
                return // an extension-less `videoUrl` on the page's own host is a page link
            }
            add(candidate, candidate.mediaUrl)
        }

        /** The MIME type to classify [raw] with; "" for "by extension"; null for "not media". */
        private fun mimeFor(
            name: String,
            raw: String,
            visit: AnchoredMediaWalk.Visit,
            listParent: String?,
        ): String? {
            // Known keys first: L3 must find what L4 finds.
            MediaKeyTable.ADDRESS_KEYS[name]?.let { return it }
            if (name in MediaKeyTable.ADDRESS_KEYS) {
                return sibling(visit.node) ?: ""
            }
            if (listParent != null) {
                MediaKeyTable.ADDRESS_LIST_PARENTS[listParent.lowercase()]?.let { return it }
            }
            if (name == "url" || name == "src" || name == "uri") {
                visit.key?.let { MediaKeyTable.VERSION_LIST_KEYS[it] }?.let { return it }
            }
            if (MediaKeyTable.hinted(name, MediaKeyTable.IMAGE_HINTS)) return null
            if (MediaKeyTable.AD_HINTS.containsMatchIn(name)) return null
            if (imageExtension(raw)) return null
            val siblingMime = sibling(visit.node)?.takeIf { addressKey(name) }
            if (siblingMime != null) return siblingMime
            if (hasMediaExtension(raw)) return ""
            // An address without an extension: a media-hinted key path next to file facts.
            val hinted = MediaKeyTable.hinted(name, MediaKeyTable.MEDIA_HINTS) ||
                (listParent != null && MediaKeyTable.hinted(listParent, MediaKeyTable.MEDIA_HINTS))
            val facts = visit.node.entries.keys.count { it.lowercase() in MediaKeyTable.SHAPE_FIELDS }
            return if (hinted && facts >= 1) GUESSED_MP4 else null
        }

        private fun sibling(node: JsonValue.Object): String? =
            MediaKeyTable.MIME_FIELDS.firstNotNullOfOrNull { node[it].asStringOrNull }
                ?.let(::normalizedMime)

        private fun listOfAddresses(name: String, parentKey: String?): Boolean =
            name in MediaKeyTable.ADDRESS_LIST_KEYS ||
                parentKey?.lowercase() in MediaKeyTable.ADDRESS_LIST_PARENTS ||
                (
                    MediaKeyTable.hinted(name, MediaKeyTable.MEDIA_HINTS) &&
                        !MediaKeyTable.hinted(name, MediaKeyTable.IMAGE_HINTS)
                    ) ||
                (name.lowercase().contains("url") && parentKey != null &&
                    MediaKeyTable.hinted(parentKey, MediaKeyTable.MEDIA_HINTS) &&
                    !MediaKeyTable.hinted(parentKey, MediaKeyTable.IMAGE_HINTS))

        fun holdsMedia(node: JsonValue.Object, key: String?): Boolean =
            node.entries.any { (name, value) ->
                val text = value.asStringOrNull ?: return@any false
                (text.startsWith("https://", true) || text.trimStart().startsWith("<")) &&
                    (name in MediaKeyTable.ADDRESS_KEYS || hasMediaExtension(text) ||
                        text.contains("<MPD", true))
            }

        private fun add(candidate: MediaCandidate, url: String) {
            if (candidates.size >= LayerStack.MAX_RAW_CANDIDATES) return
            if (!seen.add(UrlPolicy.whole(url))) return
            candidates += candidate.copy(confidence = CandidateConfidence.MEDIUM)
        }
    }

    private companion object {
        /** Marks a MIME type L3 inferred from a hinted key next to file facts. */
        const val GUESSED_MP4 = "video/mp4;guessed"
        const val MAX_DOCUMENTS = 40
        const val MAX_VISITED_NODES = 120_000
        const val MAX_LIST = 16
        const val MAX_NESTED = 16
        const val MAX_NESTED_CHARS = 1024 * 1024
        const val MAX_ATTRIBUTES = 16
        val DATA_SCRIPT_IDS = PayloadRecipes.JSON_SCRIPT_IDS
        val JSON_LIKE_TYPES = setOf("text/json", "application/x-json", "text/x-json")
        val JSON_ATTRIBUTE = Regex("""(?i)\bdata-[\w-]+\s*=\s*"(\{[^"]{2,262144}\})"""")

        fun looksLikeJson(body: String): Boolean {
            val first = body.trimStart().firstOrNull()
            return first == '{' || first == '['
        }

        fun addressKey(name: String): Boolean {
            val lower = name.lowercase()
            return lower in setOf("url", "src", "uri", "href", "file", "link", "location") ||
                MediaKeyTable.MEDIA_HINTS.any(lower::contains) || lower.endsWith("url")
        }

        fun normalizedMime(value: String): String? {
            val lower = value.trim().lowercase()
            return when {
                lower.startsWith("video/") || lower.startsWith("audio/") -> value.trim()
                lower.contains("mpegurl") || lower == "application/dash+xml" -> value.trim()
                lower == "mp4" || lower == "video_mp4" -> "video/mp4"
                else -> null
            }
        }

        fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

        private fun extension(url: String): String =
            runCatching { URI(url).path }.getOrNull().orEmpty()
                .substringAfterLast('/').substringAfterLast('.', "").lowercase()

        fun hasMediaExtension(url: String): Boolean {
            val ext = extension(url)
            return ext == "m3u8" || ext == "mpd" || MediaUrlClassifier.isDirectExtension(ext)
        }

        fun imageExtension(url: String): Boolean = extension(url) in MediaKeyTable.IMAGE_EXTENSIONS
    }
}
