package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.recipes.ContractRecipe
import com.alal.yft.extractor.master.recipes.PayloadRecipes
import com.alal.yft.extractor.master.toolkit.BalancedJson
import com.alal.yft.extractor.master.toolkit.MediaKeyTable
import com.alal.yft.extractor.master.toolkit.PageScripts

/**
 * Social plan S1a (7) (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): whether the documents a Facebook
 * answer's rows came from hold one video under the video ID, so a row bound to the ID is that
 * video's own file. [FacebookLadder] reads it for F1's answer and for each of F2's pages, only
 * when the readers gave rows; anything but [Reading.ONE_VIDEO] drops that answer's rows.
 *
 * Its own bounded walk over the documents the readers read (F1: the plugin player's JSON scripts
 * and loader calls, as `ContractReader` reads them, [pluginDocuments]; F2: the page's selected
 * data scripts), the JSON texts inside them too. An object's own ID: `video_id`, `videoId`,
 * `shortcode`, `code` or `rest_id`, else a numeric `id` (a story's or a fragment's text ID names
 * no video); an object without one is in its parent's scope. A file: an https address whose path
 * ends in a media file's extension (a manifest or an image is none), compared by its path (the
 * host and the query aside: copies of one file differ in their signatures). In the video's scope
 * two videos are under the ID when:
 * - an ID-less list item holds two or more files (another ID's subtree aside): a list of other
 *   videos, or of whole videos;
 * - two objects with the video's ID give one field disjoint files (copies share their files;
 *   renditions listed under one field share it);
 * - an ID-less list item holds a file under a field an object above it in the scope also has,
 *   empty or with another file (outside a list only another file counts: the delivery copies'
 *   shape, a copy of the fields beside an empty one);
 * - a row's file is listed only under another ID (a decoy whose plain `id` the readers do not
 *   take for one).
 * Over the walk's bound the video is not proven ([Reading.OVER_BOUND]): no rows either.
 *
 * Known limits: an ID-less entry inside the video's object with one file under a field the object
 * lacks or leaves empty still reads as the video's (a single rendition looks the same); the
 * plugin player's second item claiming the video, when the first lists no file, is taken (as main
 * would); what the readers find outside these documents (the page's other finders) is not walked;
 * the rows of several pages are not compared.
 */
internal object FacebookIdentity {
    enum class Reading(val line: String) {
        ONE_VIDEO("one video under the video ID"),
        TWO_VIDEOS("two videos under the video ID; no rows"),
        OVER_BOUND("the video ID not proven (over the walk's bound); no rows"),
    }

    /** At most this many JSON values walked in one reading, the nested texts' too. */
    const val MAX_NODES = 500_000
    private const val MAX_DOCUMENTS = 32
    private const val MAX_DOCUMENT_NODES = 200_000
    private const val MAX_NESTED = 64
    private const val MAX_NESTED_CHARS = 1024 * 1024
    private const val MAX_HOLDERS = 1_024
    private const val HTTPS = "https://"
    private val ID_FIELDS = listOf("video_id", "videoId", "shortcode", "code", "rest_id")
    private val FILE_EXTENSIONS = listOf(".mp4", ".m4v", ".m4a", ".mov", ".webm", ".mp3", ".aac")

    /** The JSON texts `ContractReader` reads in a player's answer [body]: data scripts, calls. */
    fun pluginDocuments(body: String, recipe: ContractRecipe): List<String> {
        if (!recipe.html) return listOf(body)
        val scripts = PageScripts.scripts(body)
        val embedded = if (recipe.embeddedJson) PageScripts.embeddedDocuments(body) else emptyList()
        val data = scripts.filter { it.type in PayloadRecipes.JSON_SCRIPT_TYPES }.map { it.body }
        val calls = mutableListOf<String>()
        scripts.forEach { script ->
            recipe.callMarkers.forEach { marker ->
                var index = script.body.indexOf(marker)
                while (index >= 0 && calls.size < MAX_DOCUMENTS) {
                    BalancedJson.objectAfter(script.body, marker, index)?.let(calls::add)
                    index = script.body.indexOf(marker, index + marker.length)
                }
            }
        }
        return (embedded + data + calls).take(MAX_DOCUMENTS)
    }

    /** How [documents] hold [videoId]'s video, given the rows' addresses ([rows]) read from them. */
    fun read(
        documents: List<String>,
        videoId: String,
        rows: Collection<String>,
        maxNodes: Int = MAX_NODES,
    ): Reading = Walk(videoId, maxNodes).read(documents, rows)

    /** The path of an https address of a media file; null for anything else. */
    internal fun filePath(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(HTTPS, ignoreCase = true)) return null
        val rest = trimmed.substring(HTTPS.length)
        val slash = rest.indexOf('/').takeIf { it > 0 } ?: return null
        val path = rest.substring(slash).substringBefore('?').substringBefore('#')
        val lower = path.lowercase()
        return path.takeIf { FILE_EXTENSIONS.any(lower::endsWith) }
    }

    /** An object with the video's own ID: the files under it in the scope, by field. */
    private class Holder(val outer: Holder?) {
        val fields = HashMap<String, MutableSet<String>>()
    }

    /** An ID-less list item in the video's scope: the files under it, up to the next ID. */
    private class Region(val outer: Region?) {
        val paths = HashSet<String>()
    }

    /** An object on the walk: its entries, its place and what it inherits. */
    private class Node(
        val entries: Map<String, JsonValue>,
        val parent: Node?,
        val listItem: Boolean,
        val ownId: String?,
        val scope: String?,
        val holder: Holder?,
        val region: Region?,
    )

    private class Pending(val value: JsonValue, val key: String?, val parent: Node?, val inList: Boolean)

    private class Walk(private val videoId: String, private val maxNodes: Int) {
        private val holders = mutableListOf<Holder>()
        private val ownPaths = HashSet<String>()
        private val otherPaths = HashSet<String>()
        private var two = false
        private var visited = 0
        private var nested = 0

        fun read(documents: List<String>, rows: Collection<String>): Reading {
            for (text in documents) {
                val root = BalancedJson.lenientParse(text, MAX_DOCUMENT_NODES) ?: continue
                if (!walk(root)) return Reading.OVER_BOUND
                if (two) return Reading.TWO_VIDEOS
            }
            if (holders.size > MAX_HOLDERS) return Reading.OVER_BOUND
            if (disjoint()) return Reading.TWO_VIDEOS
            val decoy = rows.mapNotNull(::filePath).any { it !in ownPaths && it in otherPaths }
            return if (decoy) Reading.TWO_VIDEOS else Reading.ONE_VIDEO
        }

        /** Walks one document; false over the bound. */
        private fun walk(root: JsonValue): Boolean {
            val pending = ArrayDeque<Pending>().apply { add(Pending(root, null, null, false)) }
            while (pending.isNotEmpty() && !two) {
                visited += 1
                if (visited > maxNodes) return false
                val next = pending.removeLast()
                when (val value = next.value) {
                    is JsonValue.Object -> {
                        val node = node(value, next.parent, next.inList)
                        value.entries.forEach { (name, child) ->
                            if (name !in MediaKeyTable.SKIP_SUBTREES) pending.add(Pending(child, name, node, false))
                        }
                    }
                    is JsonValue.Array -> value.items.forEach { pending.add(Pending(it, next.key, next.parent, true)) }
                    is JsonValue.Text -> {
                        val path = filePath(value.value)
                        if (path != null) {
                            file(path, next.key, next.parent)
                        } else {
                            nestedDocument(value.value)?.let { pending.add(Pending(it, next.key, next.parent, false)) }
                        }
                    }
                    else -> Unit
                }
            }
            return true
        }

        private fun node(value: JsonValue.Object, parent: Node?, inList: Boolean): Node {
            val ownId = ownId(value.entries)
            val scope = ownId ?: parent?.scope
            val inScope = scope == videoId
            val holder = when {
                ownId == videoId -> Holder(parent?.holder?.takeIf { parent.scope == videoId }).also(holders::add)
                inScope -> parent?.holder
                else -> null
            }
            val region = when {
                ownId != null || !inScope -> null
                inList -> Region(parent?.region)
                else -> parent?.region
            }
            return Node(value.entries, parent, inList, ownId, scope, holder, region)
        }

        /** A file at [field] of [parent] (the object holding it, or the list holding it). */
        private fun file(path: String, field: String?, parent: Node?) {
            val scope = parent?.scope ?: return
            if (scope != videoId) {
                otherPaths += path
                return
            }
            ownPaths += path
            field ?: return
            var holder = parent.holder
            while (holder != null) {
                holder.fields.getOrPut(field) { HashSet() } += path
                holder = holder.outer
            }
            var region = parent.region
            while (region != null) {
                region.paths += path
                if (region.paths.size > 1) two = true
                region = region.outer
            }
            if (parent.ownId == null) fieldAbove(path, field, parent)
        }

        /** An ID-less object's file whose field an object above it in the scope also has. */
        private fun fieldAbove(path: String, field: String, start: Node) {
            var strict = start.listItem
            var above = start.parent
            while (above != null && above.scope == videoId && !two) {
                when (val value = above.entries[field]) {
                    is JsonValue.Null -> if (strict) two = true
                    is JsonValue.Text -> {
                        val other = filePath(value.value)
                        if (other != null && other != path || other == null && strict && value.value.isBlank()) two = true
                    }
                    else -> Unit
                }
                strict = strict || above.listItem
                above = above.parent
            }
        }

        /** Two objects with the video's ID that give one field disjoint files. */
        private fun disjoint(): Boolean {
            for (first in holders.indices) {
                for (second in first + 1 until holders.size) {
                    val fields = holders[second].fields
                    holders[first].fields.forEach { (field, paths) ->
                        val other = fields[field]
                        if (!other.isNullOrEmpty() && paths.isNotEmpty() && paths.none(other::contains)) return true
                    }
                }
            }
            return false
        }

        private fun nestedDocument(text: String): JsonValue? {
            if (nested >= MAX_NESTED || text.length > MAX_NESTED_CHARS) return null
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
            return BalancedJson.lenientParse(text)?.also { nested += 1 }
        }

        private fun ownId(entries: Map<String, JsonValue>): String? {
            ID_FIELDS.forEach { field -> idText(entries[field])?.let { return it } }
            return idText(entries["id"])?.takeIf { id -> id.all(Char::isDigit) }
        }

        private fun idText(value: JsonValue?): String? = when (value) {
            is JsonValue.Text -> value.value.trim().takeIf { it.isNotEmpty() }
            is JsonValue.Number -> value.asLong?.toString() ?: value.text
            else -> null
        }
    }
}
