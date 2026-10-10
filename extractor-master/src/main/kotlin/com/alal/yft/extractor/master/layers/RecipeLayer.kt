package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.recipes.NodeRule
import com.alal.yft.extractor.master.recipes.PayloadRecipes
import com.alal.yft.extractor.master.recipes.TikTokStatusRecipe
import com.alal.yft.extractor.master.recipes.YoutubeStreamingRecipe
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.HtmlScan
import com.alal.yft.extractor.master.toolkit.InlineDashReader

/**
 * L4: a bounded walk over delivered JSON (JSON scripts, assigned player JSON, API responses)
 * that applies the data-only [PayloadRecipes]. No endpoint, signature or login is invented;
 * unaddressed/cipher-only formats wait for the browser's own decoded requests (L1).
 */
internal class RecipeLayer : MasterLayer {
    override val id = LayerId.L4_RECIPE

    override fun collect(request: MasterRequest, snapshot: PageSnapshot): Evidence {
        val walk = Walk(request)
        snapshot.html?.let { body ->
            HtmlScan.SCRIPT.findAll(body).forEach { match ->
                val attrs = HtmlScan.attributes(match.groupValues[1])
                if (
                    attrs["type"]?.lowercase() in PayloadRecipes.JSON_SCRIPT_TYPES ||
                    attrs["id"] in PayloadRecipes.JSON_SCRIPT_IDS
                ) {
                    walk.json(match.groupValues[2])
                }
            }
            HtmlScan.assignedObject(body, PayloadRecipes.ASSIGNED_PLAYER_JSON)?.let(walk::json)
        }
        snapshot.apiResponses.forEach(walk::json)
        return Evidence(walk.candidates, terminalFailure = walk.terminalFailure)
    }

    private class Walk(private val request: MasterRequest) {
        val candidates = mutableListOf<MediaCandidate>()
        var terminalFailure: SiteExtractionFailure? = null
        private val factory = CandidateFactory(request)
        private var document = 0
        private var nodes = 0

        private fun add(candidate: MediaCandidate) {
            if (candidates.size < LayerStack.MAX_RAW_CANDIDATES) candidates += candidate
        }

        fun json(body: String) {
            if (document >= MAX_DOCUMENTS) return
            document += 1
            val root = BoundedJsonParser.parse(body, maxDepth = 48, maxNodes = 30_000) ?: return
            if (tikTokStatus(root)) return
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

        /** True when the document reports a non-zero status; reading it stops there. */
        private fun tikTokStatus(root: JsonValue): Boolean {
            val scope = root[TikTokStatusRecipe.SCOPE]
            TikTokStatusRecipe.DETAIL_FIELDS.forEach { field ->
                val detail = scope[field] ?: return@forEach
                val id = detail.path(*TikTokStatusRecipe.ID_PATH).asStringOrNull
                if (id != null && request.expectedContentId?.let { it != id } == true) {
                    return@forEach
                }
                val status = TikTokStatusRecipe.STATUS_FIELDS
                    .firstNotNullOfOrNull { detail[it].asLongOrNull }
                if (status != null && status != 0L) {
                    terminalFailure = TikTokStatusRecipe.failure(status)
                    return true
                }
            }
            return false
        }

        private fun node(node: JsonValue.Object, id: String?, key: String) {
            if (
                node[PayloadRecipes.PRIVATE_FLAG].asBooleanOrNull == true &&
                PayloadRecipes.MEDIA_ID_FIELDS.any { node[it] != null }
            ) {
                terminalFailure = SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
                return
            }
            if (PayloadRecipes.DRM_FLAGS.any { node[it].asBooleanOrNull == true }) {
                terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                return
            }
            if (node[YoutubeStreamingRecipe.FIELD] != null) {
                val streams = YoutubeStreamingRecipe.read(node, id, key, factory)
                if (streams.drm) {
                    terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                } else {
                    streams.streams.forEach(::add)
                }
            }
            PayloadRecipes.NODE_RULES.forEach { rule -> apply(rule, node, id, key) }
        }

        private fun apply(rule: NodeRule, node: JsonValue.Object, id: String?, key: String) {
            fun address(url: String?, mime: String?, from: JsonValue?) =
                factory.candidate(url, mime, id, node = from, key = key)?.let(::add)
            when (rule) {
                is NodeRule.Address -> address(
                    node[rule.field].asStringOrNull,
                    rule.mime ?: rule.mimeField?.let { node[it].asStringOrNull },
                    node,
                )
                is NodeRule.Versions ->
                    node.path(*rule.path.toTypedArray()).asArrayOrEmpty.forEach { version ->
                        address(
                            version[rule.url].asStringOrNull,
                            rule.mime ?: rule.mimeField?.let { version[it].asStringOrNull },
                            version,
                        )
                    }
                is NodeRule.AddressOrList -> {
                    address(node[rule.field].asStringOrNull, rule.mime, node)
                    node[rule.field][rule.list].asArrayOrEmpty.forEach {
                        address(it.asStringOrNull, rule.mime, node)
                    }
                }
                is NodeRule.VersionLists -> node[rule.array].asArrayOrEmpty.forEach { version ->
                    version.path(*rule.listPath.toTypedArray()).asArrayOrEmpty.forEach {
                        address(it.asStringOrNull, rule.mime, version)
                    }
                }
                is NodeRule.InlineDash -> node[rule.field].asStringOrNull
                    ?.takeIf { it.trimStart().startsWith("<") }?.let {
                        val parsed = InlineDashReader.read(it, request, id, key)
                        candidates.addAll(
                            parsed.candidates.take(LayerStack.MAX_RAW_CANDIDATES - candidates.size),
                        )
                        if (parsed.drm) terminalFailure = SiteExtractionFailure.DRM_PROTECTED
                    }
            }
        }

        private fun contentId(node: JsonValue): String? {
            node["videoDetails"]["videoId"].asStringOrNull?.let { return it }
            PayloadRecipes.CONTENT_ID_FIELDS.forEach {
                node[it].asStringOrNull?.let { value -> return value }
            }
            val hasMedia = node["video"] != null ||
                PayloadRecipes.MEDIA_ID_FIELDS.any { node[it] != null }
            return if (hasMedia) {
                node["id"].asStringOrNull ?: node["id"].asLongOrNull?.toString()
            } else {
                null
            }
        }
    }

    private companion object {
        const val MAX_DOCUMENTS = 16
        const val MAX_VISITED_NODES = 30_000
    }
}
