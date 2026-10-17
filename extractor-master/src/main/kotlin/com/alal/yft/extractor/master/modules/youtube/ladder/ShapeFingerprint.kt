package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.JsonValue
import java.security.MessageDigest

/**
 * Phase 1.3 D0: the key-name shape of one player answer (MASTER_KEY_PHASE1_3_PLAN.md §4 D0 and
 * W), so the census and the weekly check can tell when YouTube moved or renamed a field.
 *
 * Only key names are read, never a value. Arrays are collapsed (`a.b[].c`), a key that does not
 * look like a field name (`^[A-Za-z_][A-Za-z0-9_]{0,63}$`) or equals a redacted value (the video
 * ID) is written as `*`, and the walk is bounded: depth ≤ [MAX_DEPTH], ≤ [MAX_NODES] nodes,
 * ≤ [MAX_PATHS] paths ([truncated] says whether a bound was reached).
 *
 * [core] is what the report and the state file keep: every top-level key and every path under
 * the three subtrees the copied parser reads (`playabilityStatus`, `streamingData`,
 * `videoDetails`). [coreHash] and [fullHash] (all paths) are 12 hex digits of SHA-256 over the
 * sorted paths, one per line.
 */
internal class ShapeFingerprint private constructor(
    val core: List<String>,
    val coreHash: String,
    val fullHash: String,
    val fullPaths: Int,
    val truncated: Boolean,
) {
    override fun toString(): String =
        "ShapeFingerprint(core=${core.size}, coreHash=$coreHash, fullHash=$fullHash, " +
            "fullPaths=$fullPaths, truncated=$truncated)"

    companion object {
        const val MAX_DEPTH = 16
        const val MAX_NODES = 100_000
        const val MAX_PATHS = 2_000
        private const val HASH_HEX = 12

        /** The subtrees the copied parser reads; only their paths are listed in full. */
        val CORE_ROOTS: Set<String> = setOf("playabilityStatus", "streamingData", "videoDetails")

        private val KEY = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")

        /**
         * The paths the copied parser reads to build rows. One of them disappearing is
         * **BREAKING** in the weekly check. Plan adapted (D0): the parser's negative or
         * alternative signals (cipher fields, DRM fields, the SABR address, `isLive`, reason
         * texts) are left out, because their removal never loses a row; a lost row shows up as
         * a class or count line instead.
         */
        val PARSER_READS: Set<String> = buildSet {
            add("playabilityStatus.status")
            add("streamingData")
            add("streamingData.formats")
            add("streamingData.adaptiveFormats")
            add("streamingData.expiresInSeconds")
            for (list in listOf("streamingData.formats[]", "streamingData.adaptiveFormats[]")) {
                listOf(
                    "itag", "url", "mimeType", "qualityLabel", "averageBitrate", "bitrate", "width",
                    "height", "contentLength", "audioQuality", "audioSampleRate",
                    "audioTrack.audioIsDefault", "isDrc", "fps",
                ).forEach { add("$list.$it") }
            }
            listOf(
                "videoId", "title", "author", "lengthSeconds", "thumbnail.thumbnails[].url",
                "thumbnail.thumbnails[].width",
            ).forEach { add("videoDetails.$it") }
        }

        fun of(root: JsonValue, redact: Set<String> = emptySet()): ShapeFingerprint {
            val walker = Walker(redact.filterTo(HashSet()) { it.isNotEmpty() })
            walker.walk(root, path = "", depth = 0)
            val all = walker.paths.toList()
            val core = all.filter(::isCore)
            return ShapeFingerprint(
                core = core,
                coreHash = hashOf(core),
                fullHash = hashOf(all),
                fullPaths = all.size,
                truncated = walker.truncated,
            )
        }

        /** 12 hex digits of SHA-256 over the sorted [paths], one per line. */
        fun hashOf(paths: Collection<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(paths.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(HASH_HEX)
        }

        /** The root segment of [path]: its first key, before any `.` or `[`. */
        fun rootOf(path: String): String = path.substringBefore('.').substringBefore('[')

        private fun isCore(path: String): Boolean {
            val root = rootOf(path)
            return root == path || root in CORE_ROOTS
        }
    }

    private class Walker(private val redact: Set<String>) {
        val paths = java.util.TreeSet<String>()
        var truncated = false
        private var nodes = 0

        fun walk(value: JsonValue, path: String, depth: Int) {
            if (truncated) return
            nodes += 1
            if (nodes > MAX_NODES) {
                truncated = true
                return
            }
            when (value) {
                is JsonValue.Object -> {
                    if (value.entries.isEmpty()) return
                    if (depth >= MAX_DEPTH) {
                        truncated = true
                        return
                    }
                    for ((key, child) in value.entries) {
                        val name = if (KEY.matches(key) && key !in redact) key else "*"
                        val childPath = if (path.isEmpty()) name else "$path.$name"
                        if (childPath !in paths) {
                            if (paths.size >= MAX_PATHS) {
                                truncated = true
                                return
                            }
                            paths += childPath
                        }
                        walk(child, childPath, depth + 1)
                        if (truncated) return
                    }
                }

                is JsonValue.Array -> {
                    if (value.items.isEmpty()) return
                    if (depth >= MAX_DEPTH) {
                        truncated = true
                        return
                    }
                    val itemPath = "$path[]"
                    for (item in value.items) {
                        walk(item, itemPath, depth + 1)
                        if (truncated) return
                    }
                }

                else -> Unit
            }
        }
    }
}

/**
 * Phase 1.3 D0: one run's key paths as a dictionary plus one bit string per answer, so a report
 * and the state file list each path once (`parent:child,child`, top-level keys under the empty
 * parent) and each answer as hex digits (bit i = the i-th path in dictionary order).
 */
internal object ShapeDictionary {
    /** The dictionary groups for the union of [sets], in their fixed order. */
    fun groups(sets: Collection<Collection<String>>): List<String> {
        val union = sets.flatMapTo(java.util.TreeSet()) { it }
        val byParent = java.util.TreeMap<String, java.util.TreeSet<String>>()
        for (path in union) {
            val parent = if ('.' in path) path.substringBeforeLast('.') else ""
            val child = path.substringAfterLast('.')
            byParent.getOrPut(parent) { java.util.TreeSet() } += child
        }
        return byParent.map { (parent, children) -> parent + ":" + children.joinToString(",") }
    }

    /** The paths of [groups], in dictionary order. */
    fun flatten(groups: List<String>): List<String> = groups.flatMap { group ->
        val parent = group.substringBefore(':')
        group.substringAfter(':').split(',').filter(String::isNotEmpty).map { child ->
            if (parent.isEmpty()) child else "$parent.$child"
        }
    }

    /** [paths] as hex digits over [order] (4 paths per digit, the first path the high bit). */
    fun bits(order: List<String>, paths: Collection<String>): String {
        val present = paths.toHashSet()
        val builder = StringBuilder((order.size + 3) / 4)
        var index = 0
        while (index < order.size) {
            var nibble = 0
            for (offset in 0 until 4) {
                nibble = nibble shl 1
                val at = index + offset
                if (at < order.size && order[at] in present) nibble = nibble or 1
            }
            builder.append("0123456789abcdef"[nibble])
            index += 4
        }
        return builder.toString()
    }

    /**
     * The paths [bits] marks in [order], sorted like [ShapeFingerprint.core]; null when [bits] is
     * not a hex string of the right length.
     */
    fun decode(order: List<String>, bits: String): List<String>? {
        if (bits.length != (order.size + 3) / 4) return null
        val paths = ArrayList<String>()
        bits.forEachIndexed { digitIndex, digit ->
            val nibble = Character.digit(digit, 16)
            if (nibble < 0) return null
            for (offset in 0 until 4) {
                val at = digitIndex * 4 + offset
                if (at < order.size && (nibble shr (3 - offset)) and 1 == 1) paths += order[at]
            }
        }
        return paths.sorted()
    }
}
