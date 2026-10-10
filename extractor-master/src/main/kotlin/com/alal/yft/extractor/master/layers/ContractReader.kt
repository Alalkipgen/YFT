/*
 * Provenance (Master R8, copied, not moved; main 34a41890):
 *   extractor-sites/.../x/XExtractor.kt  displayTitle, TRAILING_LINK, MAX_TITLE (a post's
 *                                        first line without its trailing link, 80 characters)
 * Adapted: any trailing link of the line, for every recipe that reads a post's text.
 */
package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
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
        val title = recipe.titlePath?.let { at(node, it).asStringOrNull }
            ?.let { if (recipe.postText) postTitle(it) else it.trim() }?.takeIf(String::isNotEmpty)
        val source = item ?: node
        val duration = recipe.durationPath?.let { at(source, it).asDoubleOrNull }?.takeIf { it > 0 }
            ?.let { if (recipe.durationInMillis) it.toLong() else (it * 1_000).toLong() }
        val thumbnail = recipe.thumbnailPath?.let { at(source, it).asStringOrNull }
            ?.takeIf { it.startsWith("https://") }
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
                    title = row.title ?: title,
                    thumbnailUrl = row.thumbnailUrl ?: thumbnail,
                    durationMillis = row.durationMillis ?: duration,
                    expiresAtEpochMs = listOfNotNull(row.expiresAtEpochMs, expiry).minOrNull(),
                )
            },
            details,
        )
    }

    /** The answer's JSON: the document itself, or a page's data scripts and loader calls. */
    private fun documents(): List<JsonValue> {
        if (!recipe.html) return listOfNotNull(BalancedJson.lenientParse(answer.body, MAX_NODES))
        val texts = mutableListOf<String>()
        PageScripts.scripts(answer.body).forEach { script ->
            if (script.type in PayloadRecipes.JSON_SCRIPT_TYPES) texts += script.body
            recipe.callMarkers.forEach { marker ->
                var index = script.body.indexOf(marker)
                while (index >= 0 && texts.size < MAX_DOCUMENTS) {
                    BalancedJson.objectAfter(script.body, marker, index)?.let(texts::add)
                    index = script.body.indexOf(marker, index + marker.length)
                }
            }
        }
        return texts.take(MAX_DOCUMENTS).mapNotNull { BalancedJson.lenientParse(it, MAX_NODES) }
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
                    if (recipe.nodeIdFields.any { text(value[it]) == contentId } &&
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
        recipe.drmPaths.any { present(at(node, it)) } ||
            recipe.media.any { media -> reach(node, media).isNotEmpty() }

    private fun rowsOf(node: JsonValue): List<MediaCandidate> {
        if (recipe.drmPaths.any { present(at(node, it)) }) {
            drm = true
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
        recipe.media.forEach { media ->
            if (media.onlyIfNone && rows.isNotEmpty()) return@forEach
            val found = mutableListOf<MediaCandidate>()
            for (entry in ordered(reach(node, media), media.order)) {
                if (media.first && found.isNotEmpty()) break
                found += entryRows(node, entry, media)
            }
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
        val address = (entry as? JsonValue.Text)?.value
            ?: media.url.firstNotNullOfOrNull { entry[it].asStringOrNull }
            ?: return emptyList()
        val mime = media.mimeField?.let { entry[it].asStringOrNull } ?: media.mime
        if (media.only != null && !mime.equals(media.only, ignoreCase = true)) return emptyList()
        val row = factory.candidate(
            address, mime, contentId, PageMediaRole.MAIN, CandidateSource.MANIFEST,
            node = entry as? JsonValue.Object, key = group,
        )?.takeIf(::allowedHost) ?: return emptyList()
        val stated = recipe.sizeInUrl?.find(row.mediaUrl)?.groupValues
        val meta = recipe.sizePath?.let { at(node, it) }
        return listOf(
            row.copy(
                width = row.width ?: stated?.get(1)?.toIntOrNull()
                    ?: CandidateFactory.dimension(meta["width"]),
                height = row.height ?: stated?.get(2)?.toIntOrNull()
                    ?: CandidateFactory.dimension(meta["height"]),
            ),
        )
    }

    private fun allowedHost(row: MediaCandidate): Boolean =
        recipe.mediaHosts?.let { SiteContracts.hostIn(row.mediaUrl, it) } != false

    /** L4 + L3 over the whole answer when the key table found nothing (a renamed key). */
    private fun openRead(): Evidence {
        val snapshot = if (recipe.html) {
            PageSnapshot(request.pageUrl, request.generation, html = answer.body)
        } else {
            PageSnapshot(request.pageUrl, request.generation, apiResponses = listOf(answer.body))
        }
        val found = LayerStack(listOf(RecipeLayer(), ShapeLayer()))
            .collect(request.copy(expectedContentId = null, snapshot = null), snapshot)
        return found.copy(candidates = found.candidates.filter(::allowedHost))
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

    private fun ordered(entries: List<JsonValue>, order: ContractOrder): List<JsonValue> =
        when (order) {
            ContractOrder.LISTED -> entries
            ContractOrder.HEIGHT_DESC -> entries.sortedByDescending { it["height"].asLongOrNull ?: 0 }
            ContractOrder.BITRATE_DESC ->
                entries.sortedByDescending { it["bitrate"].asLongOrNull ?: 0 }
        }

    private companion object {
        const val MAX_NODES = 200_000
        const val MAX_DOCUMENTS = 32

        fun at(node: JsonValue?, path: List<String>): JsonValue? = node.path(*path.toTypedArray())

        fun present(value: JsonValue?): Boolean = value != null && value !is JsonValue.Null

        fun text(value: JsonValue?): String? = value.asStringOrNull ?: value.asLongOrNull?.toString()

        const val MAX_TITLE = 80
        val TRAILING_LINK = Regex("""\s*https?://\S+\s*$""")

        /** Main's X displayTitle: the first line without its trailing link, 80 characters. */
        fun postTitle(text: String): String? = text.lineSequence()
            .map { it.replace(TRAILING_LINK, "").trim() }
            .firstOrNull(String::isNotEmpty)
            ?.let { if (it.length > MAX_TITLE) it.take(MAX_TITLE).trimEnd() + "\u2026" else it }
    }
}