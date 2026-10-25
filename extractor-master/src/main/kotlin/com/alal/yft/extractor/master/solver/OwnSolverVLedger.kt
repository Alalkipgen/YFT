package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.solver.OwnSolverProtocol.appendJsonString

/**
 * Phase 1.2 N5b A.5: the V passes (ownSolverNext only): the keys ([PendingCheck.cacheKey]:
 * player version, step, the player text's SHA-256 and the hash of the locator part the run used,
 * `-` for none) whose signatures passed the live check. A lookup whose key passed needs no V; any
 * other player text or part is another key, so V runs again (a poisoned locator entry never rides
 * on an earlier pass).
 *
 * At most [maxKeys] keys, the oldest go first. Without [file] it lives in memory (Phase 1.2 N5a's
 * `passed` set); with one (the app's `files/own-solver/v-ledger.json`) it is read on first use and
 * written whole after each new pass. A file that cannot be read or is not exactly this format
 * gives an empty ledger (V runs again); IO never throws.
 */
class OwnSolverVLedger(
    private val file: OwnSolverTextFile? = null,
    private val maxKeys: Int = DEFAULT_MAX_KEYS,
) {
    init {
        require(maxKeys > 0)
    }

    /** Oldest first. */
    private val keys = LinkedHashSet<String>()
    private var loaded = false

    @Synchronized
    internal fun passed(key: String): Boolean {
        load()
        return key in keys
    }

    @Synchronized
    internal fun pass(key: String) {
        load()
        if (!KEY.matches(key) || key in keys) return
        keys += key
        while (keys.size > maxKeys) keys.remove(keys.first())
        file?.let { target -> runCatching { target.write(encode(keys)) } }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val text = file?.let { runCatching(it::read).getOrNull() } ?: return
        decode(text, maxKeys)?.let(keys::addAll)
    }

    companion object {
        const val DEFAULT_MAX_KEYS: Int = 64

        private const val FORMAT = 1
        private const val MAX_NODES = 1_000
        private val FILE_KEYS = setOf("format", "passed")

        /** player|STEP|script SHA-256 (empty when unknown)|part hash or `-`. */
        private val KEY = Regex("[A-Za-z0-9_-]{4,32}\\|(WORKER_P|WORKER_B|PAGE_P|PAGE_B)\\|([0-9a-f]{64})?\\|(-|[0-9a-f]{16})")

        internal fun encode(keys: Collection<String>): String = buildString {
            append("{\"format\":").append(FORMAT).append(",\"passed\":[")
            keys.forEachIndexed { index, key ->
                if (index > 0) append(',')
                appendJsonString(key)
            }
            append("]}")
        }

        /** The keys, oldest first; null when the text is not exactly this format. */
        internal fun decode(text: String, maxKeys: Int): List<String>? {
            val root = BoundedJsonParser.parse(text, maxNodes = MAX_NODES) as? JsonValue.Object ?: return null
            if (!root.entries.keys.all { it in FILE_KEYS }) return null
            if ((root.entries["format"] as? JsonValue.Number)?.text != FORMAT.toString()) return null
            val items = (root.entries["passed"] as? JsonValue.Array)?.items ?: return null
            if (items.size > maxKeys) return null
            val keys = items.map { item -> (item as? JsonValue.Text)?.value?.takeIf(KEY::matches) ?: return null }
            return keys.takeIf { it.toSet().size == it.size }
        }
    }
}
