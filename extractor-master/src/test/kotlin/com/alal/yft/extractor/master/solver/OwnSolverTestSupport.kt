package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.solver.OwnSolverProtocol.appendJsonString

/**
 * Stands in for the own solver's worker: it reads each job like the worker does and answers
 * through [reply], so tests script results, refusals and broken runs without a script engine.
 */
internal class ScriptedOwnEngine(
    override val isAvailable: Boolean = true,
    private val reply: suspend (Job) -> String? = { answerAll(it) },
) : OwnSolverEngine {
    val jobs = mutableListOf<Job>()

    override suspend fun run(input: String): String? {
        val job = Job.parse(input)
        jobs += job
        return reply(job)
    }

    class Job(
        val type: String,
        val keepPrepared: Boolean,
        val player: String?,
        val program: String?,
        val n: List<String>,
        val sig: List<String>,
    ) {
        companion object {
            fun parse(input: String): Job {
                val root = requireNotNull(BoundedJsonParser.parse(input)) { "job is not JSON" }
                fun text(key: String) = (root[key] as? JsonValue.Text)?.value
                fun list(key: String) = (root[key] as JsonValue.Array).items
                    .map { (it as JsonValue.Text).value }
                return Job(
                    type = requireNotNull(text("type")),
                    keepPrepared = (root["keep_prepared"] as? JsonValue.Bool)?.value == true,
                    player = text("player"),
                    program = text("program"),
                    n = list("n"),
                    sig = list("sig"),
                )
            }
        }
    }

    companion object {
        fun rate(input: String): String = "N" + input.reversed()

        fun signature(input: String): String = "S" + input.reversed()

        /** The prepared program a raw job yields, named after the player it came from. */
        fun programOf(player: String): String = "prepared<$player>"

        fun answerAll(job: Job): String = result(
            n = job.n.associateWith(::rate),
            sig = job.sig.associateWith(::signature),
            prepared = preparedFor(job),
        )

        fun preparedFor(job: Job): String? =
            if (job.type == "player" && job.keepPrepared) programOf(job.player.orEmpty()) else null

        fun result(
            n: Map<String, String> = emptyMap(),
            sig: Map<String, String> = emptyMap(),
            failed: String? = null,
            prepared: String? = null,
        ): String = buildString {
            append("{\"type\":\"result\",\"n\":")
            appendObject(n)
            append(",\"sig\":")
            appendObject(sig)
            failed?.let { append(",\"failed\":"); appendJsonString(it) }
            prepared?.let { append(",\"prepared_program\":"); appendJsonString(it) }
            append('}')
        }

        fun error(name: String): String = "{\"type\":\"error\",\"error\":\"$name\"}"

        private fun StringBuilder.appendObject(values: Map<String, String>) {
            append('{')
            values.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                appendJsonString(key)
                append(':')
                appendJsonString(value)
            }
            append('}')
        }
    }
}
