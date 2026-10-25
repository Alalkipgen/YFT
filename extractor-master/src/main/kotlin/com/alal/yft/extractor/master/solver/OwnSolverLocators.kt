package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.solver.OwnSolverLocator.appendPart
import com.alal.yft.extractor.master.solver.OwnSolverProtocol.appendJsonString

/**
 * Phase 1.2 N5b A.4: the locator cache (ownSolverNext only): per player ID, the SHA-256 of the
 * player text it was learned from and, per kind, the part ([OwnSolverLocator]) of the worker step
 * the ladder took that kind from. [OwnPlayerScriptRunner] sends a step its parts; the core checks
 * them and keeps every check. An entry learned from other player text is dropped on [get].
 *
 * At most [maxEntries] players, the least recently used go first. Without [file] it lives in
 * memory; with one (the app's `files/own-solver/locators.json`) it is read on first use and written
 * whole after each change. A file that cannot be read, is corrupt, or is of another format or core
 * version gives an empty cache; IO never throws. Data only: numbers, short hashes, player IDs and
 * step names; nothing in it ever runs.
 */
class OwnSolverLocators(
    private val file: OwnSolverTextFile? = null,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxEntries > 0)
    }

    /** One kind's part and the worker step (P or B) that gave it: that step's job gets it. */
    internal data class Located(val step: OwnSolverBreaker.Step, val part: OwnSolverLocator.Part) {
        init {
            require(step == OwnSolverBreaker.Step.WORKER_P && !part.isB || step == OwnSolverBreaker.Step.WORKER_B && part.isB) {
                "A part is its worker step's strategy"
            }
        }
    }

    internal data class Entry(val scriptHash: String, val rate: Located?, val signature: Located?) {
        fun of(kind: OwnSolverLadderRule.Kind): Located? = when (kind) {
            OwnSolverLadderRule.Kind.RATE -> rate
            OwnSolverLadderRule.Kind.SIGNATURE -> signature
        }

        fun with(kind: OwnSolverLadderRule.Kind, located: Located?): Entry = when (kind) {
            OwnSolverLadderRule.Kind.RATE -> copy(rate = located)
            OwnSolverLadderRule.Kind.SIGNATURE -> copy(signature = located)
        }

        val isEmpty: Boolean get() = rate == null && signature == null
    }

    /** Player ID to entry, least recently used first. */
    private val entries = LinkedHashMap<String, Entry>(8, 0.75f, true)
    private var loaded = false

    internal val size: Int
        @Synchronized get() {
            load()
            return entries.size
        }

    /** [playerId]'s entry when it was learned from the same player text; else none (and dropped). */
    @Synchronized
    internal fun get(playerId: String, scriptHash: String): Entry? {
        load()
        val entry = entries[playerId] ?: return null
        if (entry.scriptHash == scriptHash) return entry
        entries.remove(playerId)
        save()
        return null
    }

    /** [playerId]'s entry, whatever player text it was learned from (the runner's bookkeeping). */
    @Synchronized
    internal fun peek(playerId: String): Entry? {
        load()
        return entries[playerId]
    }

    /** Keeps [located] as [kind]'s part; an entry of other player text is replaced. */
    @Synchronized
    internal fun put(playerId: String, scriptHash: String, kind: OwnSolverLadderRule.Kind, located: Located) {
        load()
        val old = entries[playerId]?.takeIf { it.scriptHash == scriptHash }
        val entry = (old ?: Entry(scriptHash, null, null)).with(kind, located)
        if (entry == old) return
        entries[playerId] = entry
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
        save()
    }

    /** Drops [kind]'s part (only [step]'s, when given); an entry left without parts goes. */
    @Synchronized
    internal fun drop(playerId: String, kind: OwnSolverLadderRule.Kind, step: OwnSolverBreaker.Step? = null) {
        load()
        val old = entries[playerId] ?: return
        val located = old.of(kind) ?: return
        if (step != null && located.step != step) return
        val entry = old.with(kind, null)
        if (entry.isEmpty) entries.remove(playerId) else entries[playerId] = entry
        save()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val text = file?.let { runCatching(it::read).getOrNull() } ?: return
        decodeFile(text, maxEntries)?.forEach { (playerId, entry) -> entries[playerId] = entry }
    }

    private fun save() {
        val target = file ?: return
        runCatching { target.write(encodeFile(entries)) }
    }

    companion object {
        /** Users rarely meet more than a few player versions; the plan's bound. */
        const val DEFAULT_MAX_ENTRIES: Int = 32

        private const val FORMAT = 1
        private const val MAX_NODES = 20_000
        private val PLAYER_ID = Regex("[A-Za-z0-9_-]{4,32}")
        private val SCRIPT_HASH = Regex("[0-9a-f]{64}")
        private val FILE_KEYS = setOf("format", "core", "entries")
        private val ENTRY_KEYS = setOf("player", "script", "n", "sig")
        private val LOCATED_KEYS = setOf("step", "part")

        internal fun encodeFile(entries: Map<String, Entry>): String = buildString {
            append("{\"format\":").append(FORMAT).append(",\"core\":").append(OwnSolverLocator.VERSION)
            append(",\"entries\":[")
            entries.entries.forEachIndexed { index, (playerId, entry) ->
                if (index > 0) append(',')
                append("{\"player\":")
                appendJsonString(playerId)
                append(",\"script\":")
                appendJsonString(entry.scriptHash)
                entry.rate?.let { append(",\"n\":"); appendLocated(it) }
                entry.signature?.let { append(",\"sig\":"); appendLocated(it) }
                append('}')
            }
            append("]}")
        }

        private fun StringBuilder.appendLocated(located: Located) {
            append("{\"step\":")
            appendJsonString(if (located.step == OwnSolverBreaker.Step.WORKER_B) "B" else "P")
            append(",\"part\":")
            appendPart(located.part)
            append('}')
        }

        /** The entries, oldest first; null when the text is not exactly this format and version. */
        internal fun decodeFile(text: String, maxEntries: Int): List<Pair<String, Entry>>? {
            val root = BoundedJsonParser.parse(text, maxNodes = MAX_NODES) as? JsonValue.Object ?: return null
            if (!root.entries.keys.all { it in FILE_KEYS }) return null
            if ((root.entries["format"] as? JsonValue.Number)?.text != FORMAT.toString()) return null
            if ((root.entries["core"] as? JsonValue.Number)?.text != OwnSolverLocator.VERSION.toString()) return null
            val items = (root.entries["entries"] as? JsonValue.Array)?.items ?: return null
            if (items.size > maxEntries) return null
            val entries = items.map { entryOf(it) ?: return null }
            if (entries.map { it.first }.toSet().size != entries.size) return null
            return entries
        }

        private fun entryOf(node: JsonValue): Pair<String, Entry>? {
            val item = node as? JsonValue.Object ?: return null
            if (!item.entries.keys.all { it in ENTRY_KEYS }) return null
            val playerId = (item.entries["player"] as? JsonValue.Text)?.value?.takeIf(PLAYER_ID::matches) ?: return null
            val script = (item.entries["script"] as? JsonValue.Text)?.value?.takeIf(SCRIPT_HASH::matches) ?: return null
            val rate = item.entries["n"]?.let { locatedOf(it) ?: return null }
            val signature = item.entries["sig"]?.let { locatedOf(it) ?: return null }
            val entry = Entry(script, rate, signature)
            return if (entry.isEmpty) null else playerId to entry
        }

        private fun locatedOf(node: JsonValue): Located? {
            val item = node as? JsonValue.Object ?: return null
            if (!item.entries.keys.all { it in LOCATED_KEYS }) return null
            val step = when ((item.entries["step"] as? JsonValue.Text)?.value) {
                "P" -> OwnSolverBreaker.Step.WORKER_P
                "B" -> OwnSolverBreaker.Step.WORKER_B
                else -> return null
            }
            val part = OwnSolverLocator.decodePart(item.entries["part"]) ?: return null
            if (part.isB != (step == OwnSolverBreaker.Step.WORKER_B)) return null
            return Located(step, part)
        }
    }
}
