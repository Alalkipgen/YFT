package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.solver.OwnSolverLocator.appendLocator

/**
 * Phase 1.1 S5: the JSON job and reply of Master's own solver worker
 * (`extractor-master-android/src/main/assets/yft-own-solver/own-solver-worker.js`).
 *
 * ```
 * job    {"type":"player","keep_prepared":true,"player":"…","n":[…],"sig":[…]}
 *        {"type":"prepared","program":"…","n":[…],"sig":[…]}
 *        N5b (ownSolverNext only): "strategy":"B","hint":false (player text only),
 *        "tripwire":true (worker only), "deadline_ms":40000 (A.2) and "locator":{} or
 *        {"v":1,"n":{…},"sig":{…}} (A.4, worker only), see [Options]
 * reply  {"type":"result","n":{input:output},"sig":{…},"failed":"sig:disagree","prepared_program":"…",
 *         "init_errors":0,"init_error_classes":["TypeError"],
 *         "locator":{"v":1,"n":{…},"sig":{…}},"locator_hit":["n","sig"]}
 *        {"type":"error","error":"<error class>"}
 * ```
 *
 * `init_errors` (Phase 1.2 N5a) counts the player's guarded top-level statements that threw and
 * `init_error_classes` names their classes; a run that never instantiated has neither, which
 * decodes as an unknown count (null): the ladder (OwnSolverLadderRule) does not trust it.
 *
 * N5b A.2: at its deadline the core keeps only the kinds whose requested checks all finished; the
 * others fail as `n:budget` / `sig:budget` (B's own screen cap fails the run as `budget`), see
 * [Decoded.Solved.budget]. A top level the deadline cut has no init count.
 *
 * N5b A.4: a job with a locator ([Options.locator], data only, see [OwnSolverLocator]) asks the
 * core for its parts: the reply's `locator` holds a part per kind it answered cleanly and
 * `locator_hit` the kinds a sent part answered. A reply locator not exactly of the core's form is
 * ignored.
 *
 * The job is encoded by hand so the player text is escaped exactly once. The reply crosses a
 * script-engine boundary, so it goes through the bounded parser and only answers to inputs that
 * were asked are kept. Failure strings are classes ("sig:disagree", "no candidates"), never
 * values.
 */
internal object OwnSolverProtocol {
    /** Upper bound for one reply, which may carry the prepared program (about a player's size). */
    const val MAX_OUTPUT_CHARS: Int = 12 * 1024 * 1024

    private const val MAX_OUTPUT_NODES = 20_000
    private const val MAX_FAILURES = 8
    private const val MAX_FAILURE_CHARS = 64
    private const val MAX_INIT_ERROR_CLASSES = 9
    private const val MAX_HITS = 2
    private const val RATE = "n"
    private const val SIGNATURE = "sig"

    /** One kind refused by SelfCheck: `n:<reason>` or `sig:<reason>`. */
    private val KIND_FAILURE = Regex("(n|sig):[a-z-]{1,32}")

    /** N5b A.2: the failure class of a run (or a kind) that ran out of time. */
    private const val BUDGET = "budget"

    sealed interface Player {
        /** The player script exactly as YouTube serves it. */
        class Raw(val source: String) : Player

        /** The core's prepared program from an earlier run of the same player. */
        class Prepared(val program: String) : Player
    }

    /** One job: distinct inputs per kind. */
    data class Batch(
        val rateInputs: List<String>,
        val signatureInputs: List<String>,
    ) {
        init {
            require(rateInputs.isNotEmpty() || signatureInputs.isNotEmpty()) {
                "A solver job must ask for something"
            }
        }

        /** Inputs are session-derived values, so only their counts print. */
        override fun toString(): String =
            "Batch(rateInputs=${rateInputs.size}, signatureInputs=${signatureInputs.size})"
    }

    sealed interface Decoded {
        /** The core ran; a kind it refused is empty and named in [failures]. */
        class Solved(
            val rate: Map<String, String>,
            val signature: Map<String, String>,
            val preparedProgram: String?,
            val failures: List<String>,
            /** Init errors of the run (N5a); null when the reply has no count: unknown, untrusted. */
            val initErrors: Int? = null,
            /** Their classes (`TypeError`), at most nine names; never a message. */
            val initErrorClasses: List<String> = emptyList(),
            /** N5b A.4: the core's parts (locate mode only); null: none, or not of the core's form. */
            val locator: OwnSolverLocator.Parts? = null,
            /** N5b A.4: the kinds a sent part answered (the core's `locator_hit`). */
            val locatorHits: Set<OwnSolverLadderRule.Kind> = emptySet(),
        ) : Decoded {
            /** A failure that is not one kind's: the run as a whole produced nothing usable. */
            val failedWhole: Boolean get() = failures.any { !KIND_FAILURE.matches(it) }

            /**
             * N5b A.2: the run ran out of time: a kind (`sig:budget`) or the whole run (`budget`,
             * B's screen cap). Not a broken program: the ladder keeps it and its budget memory
             * decides whether the step runs again.
             */
            val budget: Boolean
                get() = failures.any { it == BUDGET || it == "$RATE:$BUDGET" || it == "$SIGNATURE:$BUDGET" }

            val isEmpty: Boolean get() = rate.isEmpty() && signature.isEmpty()

            override fun toString(): String =
                "Solved(rate=${rate.size}, signature=${signature.size}, " +
                    "prepared=${preparedProgram != null}, failures=$failures, " +
                    "initErrors=$initErrors, initErrorClasses=$initErrorClasses, " +
                    "locator=${locator != null}, locatorHits=$locatorHits)"
        }

        /** The worker could not run the core at all. */
        data class Failed(val reason: String) : Decoded
    }

    /**
     * Phase 1.2 N5b: what the ladder asks of one job (ownSolverNext only; Phase 1.1 sends none):
     * strategy B with or without P's hint (from the player text only, nothing kept), the
     * environment tripwire (the worker checks it; the page skips it) and (A.2) the job's
     * deadline in milliseconds, min(the step's cap, the time left in the lookup); it counts from
     * the job's arrival in the worker or the page. A.4: [locator] (worker steps only; the page
     * refuses the field) asks for the core's parts and sends the cached ones, of the job's
     * strategy; empty parts: locate only.
     */
    data class Options(
        val strategyB: Boolean = false,
        val hint: Boolean = false,
        val tripwire: Boolean = false,
        val deadlineMillis: Long? = null,
        val locator: OwnSolverLocator.Parts? = null,
    ) {
        init {
            require(deadlineMillis == null || deadlineMillis >= 0) { "A deadline is not negative" }
            require(locator == null || listOfNotNull(locator.rate, locator.signature).all { it.isB == strategyB }) {
                "A job's parts are of its strategy"
            }
        }
    }

    fun encode(player: Player, batch: Batch, keepPrepared: Boolean, options: Options = Options()): String {
        require(!options.strategyB || player is Player.Raw) { "Strategy B runs from the player text" }
        val source = when (player) {
            is Player.Raw -> player.source
            is Player.Prepared -> player.program
        }
        return buildString(source.length + source.length / 8 + 256) {
            when (player) {
                is Player.Raw -> {
                    append("{\"type\":\"player\",\"keep_prepared\":")
                    append(keepPrepared)
                    append(",\"player\":")
                }

                is Player.Prepared -> append("{\"type\":\"prepared\",\"program\":")
            }
            appendJsonString(source)
            append(",\"n\":")
            appendJsonArray(batch.rateInputs)
            append(",\"sig\":")
            appendJsonArray(batch.signatureInputs)
            if (options.strategyB) append(",\"strategy\":\"B\",\"hint\":").append(options.hint)
            if (options.tripwire) append(",\"tripwire\":true")
            options.deadlineMillis?.let { append(",\"deadline_ms\":").append(it) }
            options.locator?.let {
                append(",\"locator\":")
                appendLocator(it)
            }
            append('}')
        }
    }

    /** Returns null when [output] is not a worker reply at all. */
    fun decode(output: String, batch: Batch): Decoded? {
        if (output.length > MAX_OUTPUT_CHARS) return null
        val root = BoundedJsonParser.parse(output, maxNodes = MAX_OUTPUT_NODES) ?: return null
        return when ((root["type"] as? JsonValue.Text)?.value) {
            "error" -> Decoded.Failed(
                failureClass((root["error"] as? JsonValue.Text)?.value ?: "error"),
            )

            "result" -> Decoded.Solved(
                rate = valuesOf(root[RATE], batch.rateInputs),
                signature = valuesOf(root[SIGNATURE], batch.signatureInputs),
                preparedProgram = (root["prepared_program"] as? JsonValue.Text)?.value
                    ?.takeIf(String::isNotBlank),
                failures = (root["failed"] as? JsonValue.Text)?.value
                    ?.split(',')
                    ?.map { failureClass(it.trim()) }
                    ?.filter(String::isNotEmpty)
                    ?.take(MAX_FAILURES)
                    .orEmpty(),
                initErrors = initErrorsOf(root["init_errors"]),
                initErrorClasses = (root["init_error_classes"] as? JsonValue.Array)?.items.orEmpty()
                    .mapNotNull { (it as? JsonValue.Text)?.value?.let { text -> failureClass(text) } }
                    .filter(String::isNotEmpty)
                    .take(MAX_INIT_ERROR_CLASSES),
                locator = OwnSolverLocator.decode(root["locator"]),
                locatorHits = hitsOf(root["locator_hit"]),
            )

            else -> null
        }
    }

    /** Keeps only answers to inputs that were asked, so a reply cannot add values of its own. */
    private fun valuesOf(node: JsonValue?, inputs: List<String>): Map<String, String> {
        val data = node as? JsonValue.Object ?: return emptyMap()
        return inputs.mapNotNull { input ->
            (data.entries[input] as? JsonValue.Text)?.value
                ?.takeIf(String::isNotEmpty)
                ?.let { input to it }
        }.toMap()
    }

    /** N5b A.4: the kinds named in `locator_hit` ("n", "sig"); anything else names none. */
    private fun hitsOf(node: JsonValue?): Set<OwnSolverLadderRule.Kind> {
        val items = (node as? JsonValue.Array)?.items ?: return emptySet()
        if (items.size > MAX_HITS) return emptySet()
        return items.mapNotNull { item ->
            when ((item as? JsonValue.Text)?.value) {
                RATE -> OwnSolverLadderRule.Kind.RATE
                SIGNATURE -> OwnSolverLadderRule.Kind.SIGNATURE
                else -> null
            }
        }.toSet()
    }

    /** A whole, non-negative count; a huge one stays a positive count, anything else is unknown. */
    private fun initErrorsOf(node: JsonValue?): Int? {
        val count = (node as? JsonValue.Number)?.asLong ?: return null
        return if (count < 0) null else count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Failure classes are short words; anything else is cut, so no value can ride along. */
    private fun failureClass(text: String): String = text
        .take(MAX_FAILURE_CHARS)
        .filter { it.isLetterOrDigit() || it in " :-_." }

    private fun StringBuilder.appendJsonArray(values: List<String>) {
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendJsonString(value)
        }
        append(']')
    }

    internal fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ' || character == '\u2028' || character == '\u2029') {
                    append("\\u")
                    val code = character.code
                    for (shift in intArrayOf(12, 8, 4, 0)) append(HEX[(code shr shift) and 0xF])
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private const val HEX = "0123456789abcdef"
}
