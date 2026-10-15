package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.JsonValue

/**
 * Phase 1.3 D7 prep (MASTER_KEY_PHASE1_3_PLAN.md §4 D5b, D6, D7): the owner's live-check switches,
 * in a debug build only (`-Pyft.ladderSkip`, `-Pyft.ladderMutate`; release and preview: none). Each
 * changes visionOS's live answer in memory before the judge, never a request:
 *  - `visionos`: the answer is not used (the chain goes on as after a refusal; O expected);
 *  - `visionos-adaptive`: its adaptive formats are left out (H expected);
 *  - `L1`, `L2`, `L3`, `L5`, `L7`: the simulator's layout changes of the same names (L expected:
 *    the normal mode's itags found again, every L row V-passed). An answer the change does not
 *    touch (L5 without a cipher) stays as it came.
 */
class LadderTrial private constructor(val skip: String?, val mutation: String?) {
    /** The mode's name for the ladder's notes; never a value. */
    val label: String get() = skip ?: mutation ?: "none"

    /** [root] after this trial's change, or null when the trial leaves it as it came. */
    internal fun apply(root: JsonValue.Object): JsonValue.Object? = when {
        skip == SKIP_VISION_OS_ADAPTIVE -> withStreaming(root) { data ->
            if ("adaptiveFormats" !in data) null else LinkedHashMap(data).apply { remove("adaptiveFormats") }
        }
        mutation != null -> mutate(mutation, root)
        else -> null
    }

    override fun toString(): String = "LadderTrial($label)"

    companion object {
        const val SKIP_VISION_OS = "visionos"
        const val SKIP_VISION_OS_ADAPTIVE = "visionos-adaptive"
        val SKIPS: Set<String> = setOf(SKIP_VISION_OS, SKIP_VISION_OS_ADAPTIVE)
        val MUTATIONS: List<String> = listOf("L1", "L2", "L3", "L5", "L7")
        val NONE = LadderTrial(null, null)

        /** Blank = none; one mode at a time; an unknown value is refused (the build checks the same lists). */
        fun of(skip: String, mutate: String): LadderTrial {
            val skipped = skip.trim().ifEmpty { null }
            val mutated = mutate.trim().ifEmpty { null }
            require(skipped == null || skipped in SKIPS) { "unknown ladderSkip" }
            require(mutated == null || mutated in MUTATIONS) { "unknown ladderMutate" }
            require(skipped == null || mutated == null) { "ladderSkip and ladderMutate are separate modes" }
            return if (skipped == null && mutated == null) NONE else LadderTrial(skipped, mutated)
        }

        private val LISTS = listOf("formats", "adaptiveFormats")

        /**
         * The layout change [id] (plan §3 L matrix) on a player answer, or null when it changes
         * nothing: L1 `streamingData` moved under `playback`; L2 the lists renamed; L3 each address
         * key renamed; L5 each `signatureCipher` renamed; L7 each list made an object keyed by itag.
         */
        internal fun mutate(id: String, root: JsonValue.Object): JsonValue.Object? = when (id) {
            "L1" -> root.entries["streamingData"]?.let { data ->
                JsonValue.Object(
                    LinkedHashMap(root.entries).apply {
                        remove("streamingData")
                        put("playback", JsonValue.Object(mapOf("streamingData" to data)))
                    },
                )
            }
            "L2" -> withStreaming(root) { data ->
                if (LISTS.none { it in data }) null else renamed(data, mapOf("formats" to "muxedFormats", "adaptiveFormats" to "separateFormats"))
            }
            "L3" -> eachFormat(root) { format -> if ("url" in format) renamed(format, mapOf("url" to "streamUrl")) else null }
            "L5" -> eachFormat(root) { format ->
                if ("signatureCipher" in format) renamed(format, mapOf("signatureCipher" to "streamCipher")) else null
            }
            "L7" -> withStreaming(root) { data ->
                var changed = false
                val out = LinkedHashMap<String, JsonValue>()
                for ((key, value) in data) {
                    val list = (value as? JsonValue.Array)?.takeIf { key in LISTS }
                    if (list == null) {
                        out[key] = value
                        continue
                    }
                    val map = LinkedHashMap<String, JsonValue>()
                    list.items.forEachIndexed { index, item ->
                        val itag = (item as? JsonValue.Object)?.entries?.get("itag")?.let { (it as? JsonValue.Number)?.text ?: (it as? JsonValue.Text)?.value }
                        val name = itag?.takeIf { it !in map } ?: "${itag ?: "format"}-$index"
                        map[name] = item
                    }
                    out[key] = JsonValue.Object(map)
                    changed = true
                }
                out.takeIf { changed }
            }
            else -> null
        }

        private fun withStreaming(root: JsonValue.Object, change: (Map<String, JsonValue>) -> Map<String, JsonValue>?): JsonValue.Object? {
            val data = root.entries["streamingData"] as? JsonValue.Object ?: return null
            val changed = change(data.entries) ?: return null
            return JsonValue.Object(LinkedHashMap(root.entries).apply { put("streamingData", JsonValue.Object(changed)) })
        }

        private fun eachFormat(root: JsonValue.Object, change: (Map<String, JsonValue>) -> Map<String, JsonValue>?): JsonValue.Object? =
            withStreaming(root) { data ->
                var changed = false
                val out = LinkedHashMap(data)
                for (key in LISTS) {
                    val list = data[key] as? JsonValue.Array ?: continue
                    out[key] = JsonValue.Array(
                        list.items.map { item ->
                            val format = item as? JsonValue.Object ?: return@map item
                            change(format.entries)?.let { changed = true; JsonValue.Object(it) } ?: item
                        },
                    )
                }
                out.takeIf { changed }
            }

        /** [entries] with each key of [names] renamed in place (the order kept). */
        private fun renamed(entries: Map<String, JsonValue>, names: Map<String, String>): Map<String, JsonValue> =
            LinkedHashMap<String, JsonValue>().apply { for ((key, value) in entries) put(names[key] ?: key, value) }
    }
}
