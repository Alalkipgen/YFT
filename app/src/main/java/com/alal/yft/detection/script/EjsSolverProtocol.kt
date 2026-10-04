package com.alal.yft.detection.script

import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.get

/**
 * The JSON protocol of the bundled yt-dlp ejs solver, encoded and decoded in plain Kotlin.
 *
 * The solver takes the site's player script plus the values to compute, and returns one result
 * per request kind. Encoding is hand-written so the player text is escaped exactly once, and
 * decoding goes through the bounded parser because the output crosses a script-engine boundary.
 */
internal object EjsSolverProtocol {
    /** Upper bound for one solver output, which may carry a preprocessed copy of the player. */
    const val MAX_OUTPUT_CHARS: Int = 12 * 1024 * 1024

    private const val MAX_OUTPUT_NODES = 20_000
    private const val RATE_REQUEST = "n"
    private const val SIGNATURE_REQUEST = "sig"

    sealed interface Player {
        /** The player script exactly as YouTube serves it. */
        class Raw(val source: String) : Player

        /** The solver's own reduced form of a player, returned by an earlier run. */
        class Preprocessed(val source: String) : Player
    }

    /** One solver job: distinct inputs per kind, in a fixed order. */
    data class Batch(
        val rateInputs: List<String>,
        val signatureInputs: List<String>,
    ) {
        init {
            require(rateInputs.isNotEmpty() || signatureInputs.isNotEmpty()) {
                "A solver batch must ask for something"
            }
        }

        /** Inputs are session-derived values, so only their counts print. */
        override fun toString(): String =
            "Batch(rateInputs=${rateInputs.size}, signatureInputs=${signatureInputs.size})"
    }

    sealed interface Decoded {
        /**
         * Values per kind keyed by input. A kind the solver could not handle is simply empty, so
         * the adapter drops only the streams that needed it.
         */
        class Solved(
            val rate: Map<String, String>,
            val signature: Map<String, String>,
            val preprocessedPlayer: String?,
        ) : Decoded {
            override fun toString(): String =
                "Solved(rate=${rate.size}, signature=${signature.size}, " +
                    "preprocessed=${preprocessedPlayer != null})"
        }

        /** The solver could not run at all, for example because the player did not parse. */
        data object Failed : Decoded
    }

    fun encode(player: Player, batch: Batch, keepPreprocessed: Boolean): String {
        val source = when (player) {
            is Player.Raw -> player.source
            is Player.Preprocessed -> player.source
        }
        return buildString(source.length + source.length / 8 + 256) {
            when (player) {
                is Player.Raw -> {
                    append("{\"type\":\"player\",\"output_preprocessed\":")
                    append(keepPreprocessed)
                    append(",\"player\":")
                }

                is Player.Preprocessed ->
                    append("{\"type\":\"preprocessed\",\"preprocessed_player\":")
            }
            appendJsonString(source)
            append(",\"requests\":[")
            requestKinds(batch).forEachIndexed { index, (kind, inputs) ->
                if (index > 0) append(',')
                append("{\"type\":\"")
                append(kind)
                append("\",\"challenges\":[")
                inputs.forEachIndexed { inputIndex, input ->
                    if (inputIndex > 0) append(',')
                    appendJsonString(input)
                }
                append("]}")
            }
            append("]}")
        }
    }

    /** Returns null when [output] is not a solver reply at all. */
    fun decode(output: String, batch: Batch): Decoded? {
        if (output.length > MAX_OUTPUT_CHARS) return null
        val root = BoundedJsonParser.parse(output, maxNodes = MAX_OUTPUT_NODES) ?: return null
        val type = (root["type"] as? JsonValue.Text)?.value
        if (type == "error") return Decoded.Failed
        if (type != "result") return null

        val responses = root["responses"].asArrayOrEmpty
        val kinds = requestKinds(batch)
        if (responses.size != kinds.size) return null

        var rate = emptyMap<String, String>()
        var signature = emptyMap<String, String>()
        kinds.forEachIndexed { index, (kind, inputs) ->
            val values = valuesOf(responses[index], inputs)
            if (kind == RATE_REQUEST) rate = values else signature = values
        }
        return Decoded.Solved(
            rate = rate,
            signature = signature,
            preprocessedPlayer = (root["preprocessed_player"] as? JsonValue.Text)?.value
                ?.takeIf(String::isNotBlank),
        )
    }

    private fun requestKinds(batch: Batch): List<Pair<String, List<String>>> = listOfNotNull(
        batch.rateInputs.takeIf { it.isNotEmpty() }?.let { RATE_REQUEST to it },
        batch.signatureInputs.takeIf { it.isNotEmpty() }?.let { SIGNATURE_REQUEST to it },
    )

    /** Keeps only answers to inputs that were asked, so a reply cannot add values of its own. */
    private fun valuesOf(response: JsonValue?, inputs: List<String>): Map<String, String> {
        if ((response["type"] as? JsonValue.Text)?.value != "result") return emptyMap()
        val data = response["data"] as? JsonValue.Object ?: return emptyMap()
        return inputs.mapNotNull { input ->
            (data.entries[input] as? JsonValue.Text)?.value
                ?.takeIf(String::isNotEmpty)
                ?.let { input to it }
        }.toMap()
    }

    private fun StringBuilder.appendJsonString(value: String) {
        JsonText.appendString(this, value)
    }
}
