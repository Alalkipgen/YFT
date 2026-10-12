package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get

/**
 * Phase 1.1 S5: the JSON job and reply of Master's own solver worker
 * (`extractor-master-android/src/main/assets/yft-own-solver/own-solver-worker.js`).
 *
 * ```
 * job    {"type":"player","keep_prepared":true,"player":"…","n":[…],"sig":[…]}
 *        {"type":"prepared","program":"…","n":[…],"sig":[…]}
 * reply  {"type":"result","n":{input:output},"sig":{…},"failed":"sig:disagree","prepared_program":"…",
 *         "init_errors":0,"init_error_classes":["TypeError"]}
 *        {"type":"error","error":"<error class>"}
 * ```
 *
 * `init_errors` (Phase 1.2 N5a) counts the player's guarded top-level statements that threw and
 * `init_error_classes` names their classes; a run that never instantiated has neither, which
 * decodes as an unknown count (null): the ladder (OwnSolverLadderRule) does not trust it.
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
    private const val RATE = "n"
    private const val SIGNATURE = "sig"

    /** One kind refused by SelfCheck: `n:<reason>` or `sig:<reason>`. */
    private val KIND_FAILURE = Regex("(n|sig):[a-z-]{1,32}")

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
        ) : Decoded {
            /** A failure that is not one kind's: the run as a whole produced nothing usable. */
            val failedWhole: Boolean get() = failures.any { !KIND_FAILURE.matches(it) }

            val isEmpty: Boolean get() = rate.isEmpty() && signature.isEmpty()

            override fun toString(): String =
                "Solved(rate=${rate.size}, signature=${signature.size}, " +
                    "prepared=${preparedProgram != null}, failures=$failures, " +
                    "initErrors=$initErrors, initErrorClasses=$initErrorClasses)"
        }

        /** The worker could not run the core at all. */
        data class Failed(val reason: String) : Decoded
    }

    fun encode(player: Player, batch: Batch, keepPrepared: Boolean): String {
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
