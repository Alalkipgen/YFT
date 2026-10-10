/*
 * Provenance (Master R8, copied, not moved; main 34a41890):
 *   extractor-sites/.../x/XExtractor.kt  displayTitle, TRAILING_LINK, MAX_TITLE (a post's
 *                                        first line without its trailing link, 80 characters)
 *   extractor-sites/.../facebook/FacebookPageParser.kt  drmAssessment (a flag counts only when
 *                                        true, a licence map only when non-empty, `drm_info`
 *                                        read inside its JSON text)
 *   extractor-sites/.../tiktok/TikTokPageParser.kt  addressesOf (a text, a list or `UrlList`,
 *                                        https only), codecOf + TikTokCodec.codecTag
 *   extractor-sites/.../tiktok/TikTokExtractor.kt  displayTitle (the title, then the row's
 *                                        label after a dash)
 * Adapted: any trailing link of the line, for every recipe that reads a post's text; DRM
 * markers as recipe paths; the first usable address of a list stands for the file.
 */
package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.recipes.ContractMedia
import com.alal.yft.extractor.master.recipes.ContractOrder
import com.alal.yft.extractor.master.recipes.ContractRecipe
import com.alal.yft.extractor.master.recipes.PayloadRecipes
import com.alal.yft.extractor.master.toolkit.BalancedJson
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.InlineDashReader
import com.alal.yft.extractor.master.toolkit.PageScripts
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/**
 * R8, L2: reads one site's contract answer with that site's key table. The answer was asked for
 * the request's identity, so its files are that video's: rows carry the app's key
 * ("site:contentId") and form one page video. When the key table finds nothing (a renamed key),
 * L4 + L3 read the same answer; access phrases count only when no file was found at all.
 */
internal class ContractReader(
    private val request: MasterRequest,
    private val recipe: ContractRecipe,
    private val answer: ContractAnswer,
) {
    private val factory = CandidateFactory(request)
    private val group = "master:contract:${recipe.site}"
    private var drm = false
    private var noVideo = false

    /** A row's label by its address (main's "With TikTok watermark"). */
    private val labels = mutableMapOf<String, String>()

    /** The media item the rows came from (X: one of the post's media), for its own metadata. */
    private var item: JsonValue? = null

    fun read(): Evidence {
        val label = "master: ${recipe.site} contract"
        val identity = request.identity?.takeIf { it.siteId == recipe.site }
            ?: return Evidence(emptyList(), listOf("$label: answer without its identity"))
        val documents = documents()
        val node = if (recipe.html || recipe.nodeIdFields.isNotEmpty()) {
            documents.firstNotNullOfOrNull { videoNode(it, identity.contentId) }
        } else {
            documents.firstOrNull()?.also { root ->
                val named = recipe.idPaths.firstNotNullOfOrNull { text(at(root, it)) }
                if (named != null && named != identity.contentId) {
                    return Evidence(emptyList(), listOf("$label: the answer names another video"))
                }
            }
        }
        var rows = node?.let(::rowsOf).orEmpty()
        if (drm) {
            return Evidence(
                emptyList(), listOf("$label: protected media"), SiteExtractionFailure.DRM_PROTECTED,
            )
        }
        if (noVideo) {
            return Evidence(
                emptyList(), listOf("$label: a post without a video"),
                SiteExtractionFailure.NO_MEDIA_FOUND,
            )
        }
        val details = mutableListOf("$label: ${rows.size} files from the key table")
        if (rows.isEmpty()) {
            val open = openRead()
            open.terminalFailure?.let { return Evidence(emptyList(), details, it) }
            rows = open.candidates
            if (rows.isNotEmpty()) details += "$label: ${rows.size} files by shape (key table changed)"
        }
        if (rows.isEmpty()) {
            val access = recipe.accessMarkers.firstOrNull { (marker, _) ->
                answer.body.contains(marker, ignoreCase = true)
            }?.second
            return Evidence(emptyList(), details, access)
        }
        val title = recipe.titlePaths.firstNotNullOfOrNull { path ->
            at(node, path).asStringOrNull
                ?.let { if (recipe.postText) postTitle(it) else it.trim() }?.takeIf(String::isNotEmpty)
        }
        val source = item ?: node
        val duration = recipe.durationMillisPaths.firstNotNullOfOrNull { millis(at(source, it), 1.0) }
            ?: recipe.durationSecondsPaths.firstNotNullOfOrNull { millis(at(source, it), 1_000.0) }
        val thumbnail = recipe.thumbnailPaths.firstNotNullOfOrNull { path ->
            at(source, path).asStringOrNull?.takeIf { it.startsWith("https://") }
        }
        val expiry = recipe.expiryPath?.let { at(node, it).asLongOrNull }
            ?.takeIf { it in UrlPolicy.PLAUSIBLE_EXPIRY_SECONDS }?.times(1_000)
        val key = "${identity.siteId}:${identity.contentId}"
        return Evidence(
            rows.map { row ->
                row.copy(
                    videoId = key,
                    pageRole = PageMediaRole.MAIN,
                    pageVideoKey = group,
                    confidence = CandidateConfidence.HIGH,
                    title = titled(row.title ?: title, labels[row.mediaUrl]),
                    thumbnailUrl = row.thumbnailUrl ?: thumbnail,
                    durationMillis = row.durationMillis ?: duration,
                    expiresAtEpochMs = listOfNotNull(row.expiresAtEpochMs, expiry).minOrNull(),
                )
            },
            details,
        )
    }

    /** A page's loader-call payloads (`s.handle({...})`), which no data script holds. */
    private val calls: List<String> by lazy {
        if (!recipe.html) return@lazy emptyList()
        val texts = mutableListOf<String>()
        PageScripts.scripts(answer.body).forEach { script ->
            recipe.callMarkers.forEach { marker ->
                var index = script.body.indexOf(marker)
                while (index >= 0 && texts.size < MAX_DOCUMENTS) {
                    BalancedJson.objectAfter(script.body, marker, index)?.let(texts::add)
                    index = script.body.indexOf(marker, index + marker.length)
                }
            }
        }
        texts
    }

    /** The answer's JSON: the document itself, or a page's data scripts and loader calls. */
    private fun documents(): List<JsonValue> {
        if (!recipe.html) return listOfNotNull(BalancedJson.lenientParse(answer.body, MAX_NODES))
        val scripts = PageScripts.scripts(answer.body)
            .filter { it.type in PayloadRecipes.JSON_SCRIPT_TYPES }.map { it.body }
        return (scripts + calls).take(MAX_DOCUMENTS)
            .mapNotNull { BalancedJson.lenientParse(it, MAX_NODES) }
    }

    /** The first object whose own ID field names the video and that lists a file or DRM. */
    private fun videoNode(root: JsonValue, contentId: String): JsonValue? {
        val pending = ArrayDeque<JsonValue>().apply { add(root) }
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_NODES) {
            visited += 1
            when (val value = pending.removeLast()) {
                is JsonValue.Array -> value.items.asReversed().forEach(pending::add)
                is JsonValue.Object -> {
                    if (recipe.nodeIdFields.any { text(at(value, it.split('.'))) == contentId } &&
                        holdsMedia(value)
                    ) {
                        return value
                    }
                    value.entries.values.toList().asReversed().forEach(pending::add)
                }
                else -> Unit
            }
        }
        return null
    }

    private fun holdsMedia(node: JsonValue): Boolean =
        recipe.drmPaths.any { protects(at(node, it)) } ||
            recipe.noVideoPaths.any { protects(at(node, it)) } ||
            recipe.media.any { media -> reach(node, media).isNotEmpty() }

    private fun rowsOf(node: JsonValue): List<MediaCandidate> {
        if (recipe.drmPaths.any { protects(at(node, it)) }) {
            drm = true
            return emptyList()
        }
        if (recipe.noVideoPaths.any { protects(at(node, it)) }) {
            noVideo = true
            return emptyList()
        }
        val own = itemRows(node, numbered = true)
        if (own.isNotEmpty() || drm) return own
        // The page's `/video/N` names the post's own media, never the embedded post's.
        return recipe.fallbackPath?.let { at(node, it) }
            ?.let { itemRows(it, numbered = false) }.orEmpty()
    }

    /** The post's own media list: the item the page names, else the first one with files. */
    private fun itemRows(node: JsonValue, numbered: Boolean): List<MediaCandidate> {
        val path = recipe.items ?: return filesOf(node)
        val items = at(node, path).asArrayOrEmpty
        val wanted = recipe.itemNumber?.takeIf { numbered }
            ?.find(request.identity?.canonicalPageUrl.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        val named = wanted?.takeIf { it in 1..items.size }?.let { items[it - 1] }
        for (candidate in listOfNotNull(named) + items) {
            val rows = filesOf(candidate)
            if (rows.isNotEmpty()) {
                item = candidate
                return rows
            }
        }
        return emptyList()
    }

    private fun filesOf(node: JsonValue): List<MediaCandidate> {
        val rows = mutableListOf<MediaCandidate>()
        var inline = false
        recipe.media.forEach { media ->
            if (media.onlyIfNone && rows.isNotEmpty()) return@forEach
            if (media.unlessInline && inline) return@forEach
            val found = mutableListOf<MediaCandidate>()
            for (entry in ordered(reach(node, media), media)) {
                if (media.first && found.isNotEmpty()) break
                found += entryRows(node, entry, media)
            }
            if (media.inlineDash && found.isNotEmpty()) inline = true
            rows += found.filter { row -> rows.none { it.mediaUrl == row.mediaUrl } }
        }
        return rows.take(LayerStack.MAX_RAW_CANDIDATES)
    }

    private fun entryRows(
        node: JsonValue,
        entry: JsonValue,
        media: ContractMedia,
    ): List<MediaCandidate> {
        val contentId = request.identity?.contentId
        if (media.inlineDash) {
            val xml = entry.asStringOrNull?.takeIf { it.trimStart().startsWith("<") }
                ?: return emptyList()
            val read = InlineDashReader.read(xml, request, contentId, group)
            if (read.drm) drm = true
            return read.candidates.filter(::allowedHost)
        }
        val mime = media.mimeField?.let { entry[it].asStringOrNull } ?: media.mime
        if (media.only != null && !mime.equals(media.only, ignoreCase = true)) return emptyList()
        val own = entry as? JsonValue.Object
        val row = addresses(entry, media).firstNotNullOfOrNull { address ->
            factory.candidate(
                address, mime, contentId, PageMediaRole.MAIN, CandidateSource.MANIFEST,
                node = own, key = group,
            )?.takeIf(::allowedHost)
        } ?: return emptyList()
        val stated = recipe.sizeInUrl?.find(row.mediaUrl)?.groupValues
        val meta = media.metaPath?.let { at(node, it) }
        val fields = media.fields
        fun field(paths: List<String>): JsonValue? = paths.firstNotNullOfOrNull { path ->
            val steps = path.split('.')
            at(own, steps)?.takeIf(::present) ?: at(meta, steps)?.takeIf(::present)
        }
        media.label?.let { labels[row.mediaUrl] = it }
        return listOf(
            row.copy(
                width = row.width ?: stated?.get(1)?.toIntOrNull()
                    ?: CandidateFactory.dimension(field(fields.width)),
                height = row.height ?: stated?.get(2)?.toIntOrNull()
                    ?: CandidateFactory.dimension(field(fields.height)),
                bitrateBitsPerSecond = row.bitrateBitsPerSecond
                    ?: field(fields.bitrate).asLongOrNull?.takeIf { it > 0 },
                contentLengthBytes = row.contentLengthBytes
                    ?: field(fields.bytes).asLongOrNull?.takeIf { it > 0 },
                codecs = row.codecs.ifEmpty {
                    listOfNotNull(codecTag(fields.codec.mapNotNull { field(listOf(it)).asStringOrNull }))
                },
            ),
        )
    }

    /**
     * One entry's addresses in order: its text, its list's texts, or its address fields'. Only
     * absolute https addresses count (main's TikTok `httpsOrNull`): an empty field is no file.
     */
    private fun addresses(entry: JsonValue, media: ContractMedia): List<String> = when (entry) {
        is JsonValue.Text -> texts(listOf(entry))
        is JsonValue.Array -> texts(entry.items)
        is JsonValue.Object -> media.url.firstNotNullOfOrNull { field ->
            when (val value = at(entry, field.split('.'))) {
                is JsonValue.Text -> texts(listOf(value)).ifEmpty { null }
                is JsonValue.Array -> texts(value.items).ifEmpty { null }
                else -> null
            }
        }.orEmpty()
        else -> emptyList()
    }

    private fun allowedHost(row: MediaCandidate): Boolean =
        recipe.mediaHosts?.let { SiteContracts.hostIn(row.mediaUrl, it) } != false

    /**
     * L4 + L3 over the whole answer when the key table found nothing (a renamed key). A JSON
     * answer is one video's; a page may also list others, so only rows anchored to the
     * identified video count there.
     */
    private fun openRead(): Evidence {
        val snapshot = if (recipe.html) {
            PageSnapshot(request.pageUrl, request.generation, html = answer.body, apiResponses = calls)
        } else {
            PageSnapshot(request.pageUrl, request.generation, apiResponses = listOf(answer.body))
        }
        val anchor = request.identity?.contentId?.takeIf { recipe.html }
        val found = LayerStack(listOf(RecipeLayer(), ShapeLayer()))
            .collect(request.copy(expectedContentId = anchor, snapshot = null), snapshot)
        return found.copy(
            candidates = found.candidates.filter { row ->
                allowedHost(row) && (anchor == null || row.pageRole == PageMediaRole.MAIN)
            },
            // An anonymous answer (no cookie of the user's) never decides access; DRM still stops.
            terminalFailure = found.terminalFailure?.takeIf {
                it == SiteExtractionFailure.DRM_PROTECTED || recipe.cookieDomain != null
            },
        )
    }

    private fun reach(start: JsonValue, media: ContractMedia): List<JsonValue> {
        var level = listOf<Pair<JsonValue, JsonValue?>>(start to null)
        for (segment in media.path) {
            level = level.flatMap { (value, parent) ->
                if (segment != "*") return@flatMap listOfNotNull(value[segment]?.let { it to value })
                when (value) {
                    is JsonValue.Array -> value.items.map { it to value }
                    is JsonValue.Object -> {
                        val preferred = media.preferredBy?.let { parent[it].asStringOrNull }
                        (listOfNotNull(preferred?.let(value.entries::get)) +
                            value.entries.filterKeys { it != preferred }.values)
                            .map { it to value }
                    }
                    else -> emptyList()
                }
            }
        }
        return level.map { it.first }.filter(::present)
    }

    private fun ordered(entries: List<JsonValue>, media: ContractMedia): List<JsonValue> =
        when (media.order) {
            ContractOrder.LISTED -> entries
            ContractOrder.HEIGHT_DESC -> entries.sortedByDescending { it["height"].asLongOrNull ?: 0 }
            ContractOrder.BITRATE_DESC -> entries.sortedByDescending { entry ->
                media.fields.bitrate.firstNotNullOfOrNull { at(entry, it.split('.')).asLongOrNull } ?: 0
            }
        }

    private companion object {
        const val MAX_NODES = 200_000
        const val MAX_DOCUMENTS = 32

        const val MAX_EMBEDDED_CHARS = 16_384

        /**
         * A path may cross a JSON text (Facebook's `drm_info` is one), read within bounds; a
         * number names a list's item (`covers.0`).
         */
        fun at(node: JsonValue?, path: List<String>): JsonValue? {
            var current = node
            for (segment in path) {
                val value = embedded(current)
                current = (if (value is JsonValue.Array) segment.toIntOrNull()?.let { value[it] } else value[segment])
                    ?: return null
            }
            return current
        }

        private fun embedded(value: JsonValue?): JsonValue? {
            val text = (value as? JsonValue.Text)?.value?.trim() ?: return value
            if (!text.startsWith("{") || text.length > MAX_EMBEDDED_CHARS) return value
            return BalancedJson.lenientParse(text, 1_000) ?: value
        }

        fun present(value: JsonValue?): Boolean = value != null && value !is JsonValue.Null

        /** A DRM marker counts when it says something: `true`, a non-empty map, list or text. */
        fun protects(value: JsonValue?): Boolean = when (value) {
            null, is JsonValue.Null -> false
            is JsonValue.Object -> value.entries.isNotEmpty()
            is JsonValue.Array -> value.items.isNotEmpty()
            is JsonValue.Text -> value.value.isNotBlank() && !value.value.trim().equals("false", true)
            else -> value.asBooleanOrNull ?: true
        }

        fun millis(value: JsonValue?, scale: Double): Long? =
            value.asDoubleOrNull?.takeIf { it > 0 && it * scale < MAX_DURATION_MILLIS }
                ?.let { (it * scale).toLong() }?.takeIf { it > 0 }

        const val MAX_DURATION_MILLIS = 1_000_000_000.0

        fun text(value: JsonValue?): String? = value.asStringOrNull ?: value.asLongOrNull?.toString()

        const val MAX_TITLE = 80
        val TRAILING_LINK = Regex("""\s*https?://\S+\s*$""")

        /** Main's X displayTitle: the first line without its trailing link, 80 characters. */
        fun postTitle(text: String): String? = text.lineSequence()
            .map { it.replace(TRAILING_LINK, "").trim() }
            .firstOrNull(String::isNotEmpty)
            ?.let { if (it.length > MAX_TITLE) it.take(MAX_TITLE).trimEnd() + "\u2026" else it }

        /** Main's TikTok displayTitle: the title, then the row's label after a dash. */
        fun titled(title: String?, label: String?): String? = when {
            label.isNullOrBlank() -> title
            title.isNullOrBlank() -> label
            else -> "$title \u2014 $label"
        }

        /** Main's TikTok addressesOf: the https texts, trimmed, once each. */
        fun texts(values: List<JsonValue>): List<String> =
            values.mapNotNull { value ->
                value.asStringOrNull?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
            }.distinct()

        /** Main's TikTok codecOf + TikTokCodec.codecTag: the codec a file's data names. */
        fun codecTag(names: List<String>): String? {
            val text = names.joinToString(" ").lowercase(java.util.Locale.US)
            return when {
                listOf("265", "hevc", "bytevc1", "hvc1").any(text::contains) -> "hvc1"
                listOf("264", "avc").any(text::contains) -> "avc1"
                else -> null
            }
        }
    }
}