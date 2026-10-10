/*
 * Provenance (Master toolkit T3, copied, not moved; main 34a41890):
 *   extractor-sites/.../facebook/FacebookPageParser.kt  mediaNode/search (prefer the node whose ID
 *                                                       matches the page; related videos lose)
 *   extractor-sites/.../instagram/InstagramMedia.kt     find (the post with the code, else none)
 *   extractor-sites/.../tiktok/TikTokPageParser.kt      parseItem (expected item ID only)
 * Adapted: one bounded depth-first walk that carries the nearest content ID down the tree.
 */
package com.alal.yft.extractor.master.toolkit

import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get

/**
 * T3: a media-node walk **anchored to the expected content ID**. Every object learns the
 * nearest content ID above it (its own first). With an expected ID, only objects under that ID
 * are offered to the visitor; a suggested or related video's node carries its own ID and is
 * counted in [rejected] instead. Without an expected ID nothing is anchored and the visitor
 * decides how much to trust what it sees.
 */
internal class AnchoredMediaWalk(
    private val expectedId: String?,
    private val maxNodes: Int,
    private val skipSubtrees: Set<String> = MediaKeyTable.SKIP_SUBTREES,
) {
    /** One object, the property name it sits under (arrays pass it on) and its content ID. */
    data class Visit(
        val node: JsonValue.Object,
        val key: String?,
        val contentId: String?,
        val anchored: Boolean,
    )

    var visited = 0
        private set
    var rejected = 0
        private set
    val exhausted: Boolean get() = visited >= maxNodes

    /**
     * Walks [root]. [holdsMedia] says whether an object directly holds a media address; an
     * entity's own `id` only names content when it does. [visit] returns nested documents
     * (JSON delivered inside strings) to walk with the same inherited ID.
     */
    fun walk(
        root: JsonValue,
        inheritedId: String? = null,
        holdsMedia: (JsonValue.Object, String?) -> Boolean,
        visit: (Visit) -> List<JsonValue>,
    ) {
        data class Pending(val value: JsonValue, val key: String?, val id: String?)
        val pending = ArrayDeque<Pending>()
        pending.add(Pending(root, null, inheritedId))
        while (pending.isNotEmpty() && visited < maxNodes) {
            val (value, key, inherited) = pending.removeLast()
            visited += 1
            when (value) {
                is JsonValue.Array -> value.items.asReversed().forEach {
                    pending.add(Pending(it, key, inherited))
                }
                is JsonValue.Object -> {
                    val id = contentId(value, key, holdsMedia) ?: inherited
                    val anchored = expectedId != null && id == expectedId
                    val offered = expectedId == null || anchored
                    if (offered) {
                        visit(Visit(value, key, id, anchored)).asReversed().forEach {
                            pending.add(Pending(it, key, id))
                        }
                    } else if (holdsMedia(value, key)) {
                        rejected += 1
                    }
                    value.entries.entries.toList().asReversed().forEach { (name, child) ->
                        if (name !in skipSubtrees) pending.add(Pending(child, name, id))
                    }
                }
                else -> Unit
            }
        }
    }

    /** L4's ID rule first (so L3 anchors wherever L4 does), then an entity's own `id`. */
    private fun contentId(
        node: JsonValue.Object,
        key: String?,
        holdsMedia: (JsonValue.Object, String?) -> Boolean,
    ): String? {
        node["videoDetails"]["videoId"].asStringOrNull?.let { return it }
        MediaKeyTable.ID_FIELDS.forEach { field ->
            node[field].asStringOrNull?.let { return it }
        }
        val plainId = node["id"].asStringOrNull ?: node["id"].asLongOrNull?.toString()
        if (plainId == null) return null
        if (MediaKeyTable.MEDIA_ID_FIELDS.any { node[it] != null }) return plainId
        val typename = node["__typename"].asStringOrNull
        val entity = (typename != null && MediaKeyTable.ENTITY_TYPENAME.containsMatchIn(typename)) ||
            MediaKeyTable.ENTITY_FIELDS.any { node[it] != null }
        if (entity && holdsMedia(node, key)) return plainId
        // The expected ID itself is always an anchor, whatever else the node holds.
        return plainId.takeIf { it == expectedId }
    }
}
