package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.solver.OwnSolverTextFile

/**
 * Phase 1.3 D3 — health v2 (MASTER_KEY_PHASE1_3_PLAN.md §4 D3; guardrails 4, 5, 7 and 8).
 *
 * Per client of the table ([MasterClientTable] ids only): the last class and its time, the last
 * pass, the cooldown end, the working variant (the rung that answered), the learned version (a
 * public version string with its source and time) and the L shape maps (shape fingerprint → key
 * paths only), with counts. Also the version-source clock (at most one fetch a day per identity)
 * and the bot-check window (30 min) that blocks only the rungs that send requests — A, O, S's
 * re-ask and a version source ((က+), guardrail 4) — never L, Z, V, the init check, H or a local S.
 *
 * Cooldowns (v1): `REFUSED` 1 h; `SABR_ONLY`, `TOKEN_NEEDED`, `VERSION_REJECTED` and
 * `SHAPE_CHANGED` 24 h. A class a rung repaired (A, S, Z, L, H) counts as a pass with that rung as
 * the working variant, so a cooldown starts only when the client still fails after its rungs. A
 * client in cooldown is asked only when every other client failed in the lookup, and then only
 * the one whose cooldown ends first ([firstCooling]); the bot-check window has no such exception.
 *
 * Kept in memory and, when [file] is given, as one JSON text (`files/master/client-health.json`):
 * never an address, token, cookie, visitor data or video ID; every stored string is checked
 * against a strict pattern on write and on read. A file that cannot be read starts empty.
 */
internal class ClientHealth(
    private val file: OwnSolverTextFile? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The rung that answered for a client: its own first request, or the rung that repaired it. */
    enum class Rung { R0, A, S, Z, L, H, O }

    class Client internal constructor(val id: String) {
        var lastClass: CensusClass? = null
            internal set
        var lastAt: Long = 0
            internal set
        var lastPass: Long? = null
            internal set
        var cooldownUntil: Long? = null
            internal set
        var variant: Rung? = null
            internal set
        var learnedVersion: String? = null
            internal set
        var learnedSource: String? = null
            internal set
        var learnedAt: Long? = null
            internal set
        var asked: Int = 0
            internal set
        var passed: Int = 0
            internal set
        internal val shapes = LinkedHashMap<String, List<String>>()
        val shapeMaps: Map<String, List<String>> get() = shapes

        override fun toString(): String = "Client($id, ${lastClass ?: "-"}, asked=$asked, passed=$passed)"
    }

    private val clients = LinkedHashMap<String, Client>()
    private val sourceReads = LinkedHashMap<String, Long>()
    private var botCheckUntil = 0L
    private var loaded = false

    @Synchronized
    fun client(id: String): Client? = load().clients[id]

    /**
     * Records [answerClass] for [id]; [repairedBy] is the rung that repaired it. [passed]: its rows
     * may be shown (a direct answer whose V did not pass, or was not needed, says so).
     */
    @Synchronized
    fun record(
        id: String,
        answerClass: CensusClass,
        repairedBy: Rung? = null,
        passed: Boolean = repairedBy != null || answerClass == CensusClass.DIRECT,
    ) {
        if (id !in IDS) return
        load()
        val now = clock()
        val client = clients.getOrPut(id) { Client(id) }
        client.asked += 1
        client.lastClass = answerClass
        client.lastAt = now
        when {
            passed -> pass(client, repairedBy ?: Rung.R0, now)
            answerClass == CensusClass.BOT_CHECK -> botCheckUntil = now + BOT_CHECK_MILLIS
            else -> cooldownOf(answerClass)?.let { client.cooldownUntil = now + it }
        }
        save()
    }

    /** A later rung of the same lookup (H) completed [id]: a pass with that rung, not a new ask. */
    @Synchronized
    fun repaired(id: String, rung: Rung) {
        if (id !in IDS) return
        load()
        pass(clients.getOrPut(id) { Client(id) }, rung, clock())
        save()
    }

    private fun pass(client: Client, rung: Rung, now: Long) {
        client.lastPass = now
        client.passed += 1
        client.variant = rung
        client.cooldownUntil = null
    }

    /** True inside the 30 min after a bot check: the request-sending rungs stay quiet. */
    @Synchronized
    fun botChecked(): Boolean = clock() < load().botCheckUntil

    @Synchronized
    fun cooling(id: String): Boolean = load().clients[id]?.cooldownUntil?.let { clock() < it } == true

    /** Whether [id] may be asked now; [sendsRequest] = a rung that sends a request (A, O, S's re-ask, a source). */
    @Synchronized
    fun mayAsk(id: String, sendsRequest: Boolean = true): Boolean =
        !(sendsRequest && botChecked()) && !cooling(id)

    @Synchronized
    fun passedWithin(id: String, millis: Long): Boolean =
        load().clients[id]?.lastPass?.let { clock() - it in 0..millis } == true

    /** [ids] in the ladder's order: passed in 24 h, then unknown, then cooling (soonest end first). */
    @Synchronized
    fun order(ids: List<String>): List<String> {
        load()
        val (cool, warm) = ids.partition(::cooling)
        val (passed, unknown) = warm.partition { passedWithin(it, DAY_MILLIS) }
        return passed + unknown + cool.sortedBy { clients[it]?.cooldownUntil ?: Long.MAX_VALUE }
    }

    /** The one cooling client of [ids] that may still be asked when every other client failed. */
    @Synchronized
    fun firstCooling(ids: List<String>): String? =
        if (botChecked()) null else ids.filter(::cooling).minByOrNull { clients[it]?.cooldownUntil ?: Long.MAX_VALUE }

    /** Keeps a version that answered OK with a V-passed row (guardrail 5): public text only. */
    @Synchronized
    fun learn(id: String, version: String, source: String) {
        if (id !in IDS || !VERSION.matches(version) || !WORD.matches(source)) return
        val client = load().clients.getOrPut(id) { Client(id) }
        client.learnedVersion = version
        client.learnedSource = source
        client.learnedAt = clock()
        save()
    }

    /** A learned version that failed once is dropped (the base once, then the next rung). */
    @Synchronized
    fun forgetVersion(id: String) {
        val client = load().clients[id] ?: return
        client.learnedVersion = null
        client.learnedSource = null
        client.learnedAt = null
        save()
    }

    @Synchronized
    fun learnedVersion(id: String): String? = load().clients[id]?.learnedVersion

    /** L step 7: the located key paths for [fingerprint] (paths only, bounded). */
    @Synchronized
    fun rememberShape(id: String, fingerprint: String, paths: Collection<String>) {
        if (id !in IDS || !FINGERPRINT.matches(fingerprint)) return
        val kept = paths.filter(PATH::matches).distinct().take(MAX_PATHS)
        if (kept.isEmpty()) return
        val client = load().clients.getOrPut(id) { Client(id) }
        client.shapes.remove(fingerprint)
        client.shapes[fingerprint] = kept
        while (client.shapes.size > MAX_SHAPES) client.shapes.remove(client.shapes.keys.first())
        save()
    }

    @Synchronized
    fun shape(id: String, fingerprint: String): List<String>? = load().clients[id]?.shapes?.get(fingerprint)

    @Synchronized
    fun dropShape(id: String, fingerprint: String) {
        if (load().clients[id]?.shapes?.remove(fingerprint) != null) save()
    }

    /** The version-source clock: at most one fetch a day per identity (guardrails 5 and 7). */
    @Synchronized
    fun maySourceRead(id: String): Boolean = load().sourceReads[id]?.let { clock() - it >= DAY_MILLIS } ?: true

    @Synchronized
    fun sourceRead(id: String) {
        if (id !in IDS) return
        load().sourceReads[id] = clock()
        save()
    }

    /** One line per client for a debug screen: class, minutes ago, rung; never an address or value. */
    @Synchronized
    fun describe(): List<String> {
        load()
        val now = clock()
        return clients.values.map { client ->
            buildString {
                append(client.id).append(": ").append(client.lastClass ?: "-")
                append(", ").append((now - client.lastAt).coerceAtLeast(0) / MINUTE_MILLIS).append(" min ago")
                append(", asked ").append(client.asked).append(", passed ").append(client.passed)
                client.variant?.let { append(", via ").append(it) }
                if (cooling(client.id)) append(", cooling")
                client.learnedVersion?.let { append(", learned ").append(it) }
                if (client.shapes.isNotEmpty()) append(", shape maps ").append(client.shapes.size)
            }
        } + listOfNotNull("bot check: request-sending rungs quiet".takeIf { now < botCheckUntil })
    }

    @Synchronized
    fun toJson(): String = buildString {
        load()
        append("{\"v\":1,\"botCheckUntil\":").append(botCheckUntil).append(",\"clients\":{")
        clients.values.forEachIndexed { index, client ->
            if (index > 0) append(',')
            append('"').append(client.id).append("\":{")
            append("\"asked\":").append(client.asked).append(",\"passed\":").append(client.passed)
            append(",\"at\":").append(client.lastAt)
            client.lastClass?.let { append(",\"class\":\"").append(it.name).append('"') }
            client.lastPass?.let { append(",\"pass\":").append(it) }
            client.cooldownUntil?.let { append(",\"until\":").append(it) }
            client.variant?.let { append(",\"variant\":\"").append(it.name).append('"') }
            client.learnedVersion?.let { append(",\"version\":\"").append(it).append('"') }
            client.learnedSource?.let { append(",\"source\":\"").append(it).append('"') }
            client.learnedAt?.let { append(",\"learnedAt\":").append(it) }
            if (client.shapes.isNotEmpty()) {
                append(",\"shapes\":{")
                client.shapes.entries.forEachIndexed { i, (fingerprint, paths) ->
                    if (i > 0) append(',')
                    append('"').append(fingerprint).append("\":[")
                    paths.forEachIndexed { j, path -> if (j > 0) append(','); append('"').append(path).append('"') }
                    append(']')
                }
                append('}')
            }
            append('}')
        }
        append("},\"sources\":{")
        sourceReads.entries.forEachIndexed { index, (id, at) ->
            if (index > 0) append(',')
            append('"').append(id).append("\":").append(at)
        }
        append("}}")
    }

    private fun save() {
        file?.write(toJson())
    }

    private fun load(): ClientHealth {
        if (loaded) return this
        loaded = true
        val root = file?.read()?.let(ShapeLocator::parse) as? JsonValue.Object ?: return this
        botCheckUntil = number(root.entries["botCheckUntil"]) ?: 0L
        (root.entries["clients"] as? JsonValue.Object)?.entries?.forEach { (id, value) ->
            val node = value as? JsonValue.Object ?: return@forEach
            if (id !in IDS) return@forEach
            val client = Client(id)
            client.asked = number(node.entries["asked"])?.toInt() ?: 0
            client.passed = number(node.entries["passed"])?.toInt() ?: 0
            client.lastAt = number(node.entries["at"]) ?: 0L
            client.lastClass = text(node.entries["class"])?.let { name -> CensusClass.entries.firstOrNull { it.name == name } }
            client.lastPass = number(node.entries["pass"])
            client.cooldownUntil = number(node.entries["until"])
            client.variant = text(node.entries["variant"])?.let { name -> Rung.entries.firstOrNull { it.name == name } }
            client.learnedVersion = text(node.entries["version"])?.takeIf(VERSION::matches)
            client.learnedSource = text(node.entries["source"])?.takeIf(WORD::matches)
            client.learnedAt = number(node.entries["learnedAt"])
            (node.entries["shapes"] as? JsonValue.Object)?.entries?.forEach { (fingerprint, paths) ->
                if (!FINGERPRINT.matches(fingerprint) || client.shapes.size >= MAX_SHAPES) return@forEach
                val kept = (paths as? JsonValue.Array)?.items.orEmpty().mapNotNull(::text).filter(PATH::matches).take(MAX_PATHS)
                if (kept.isNotEmpty()) client.shapes[fingerprint] = kept
            }
            clients[id] = client
        }
        (root.entries["sources"] as? JsonValue.Object)?.entries?.forEach { (id, at) ->
            if (id in IDS) number(at)?.let { sourceReads[id] = it }
        }
        return this
    }

    private fun number(value: JsonValue?): Long? = (value as? JsonValue.Number)?.text?.toLongOrNull()

    private fun text(value: JsonValue?): String? = (value as? JsonValue.Text)?.value

    companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        const val BOT_CHECK_MILLIS = 30 * MINUTE_MILLIS
        const val MAX_SHAPES = 4
        const val MAX_PATHS = 64
        val IDS: Set<String> get() = MasterClientTable.ENTRIES.map { it.id }.toSet()
        private val VERSION = Regex("^[0-9]{1,4}(\\.[0-9]{1,8}){1,3}$")
        private val WORD = Regex("^[a-z][a-z0-9.-]{0,31}$")
        private val FINGERPRINT = Regex("^[0-9a-f]{8,64}$")
        private val PATH = Regex("^[A-Za-z0-9_.*\\[\\]]{1,200}$")

        fun cooldownOf(answerClass: CensusClass): Long? = when (answerClass) {
            CensusClass.REFUSED -> HOUR_MILLIS
            CensusClass.SABR_ONLY, CensusClass.TOKEN_NEEDED, CensusClass.VERSION_REJECTED, CensusClass.SHAPE_CHANGED -> DAY_MILLIS
            else -> null
        }
    }
}
