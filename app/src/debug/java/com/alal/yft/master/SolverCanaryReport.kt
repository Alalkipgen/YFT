package com.alal.yft.master

import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.OwnSolverProbe
import java.util.Locale

/**
 * Phase 1.2 N7, owner tool (debug build only): the solver canary's results and their report, in
 * plain Kotlin so a JVM test checks them ([SolverCanary] runs the steps on the phone).
 *
 * The report holds the player ID, labels, counts, verdicts, times and byte counts only: never an
 * input, a value, an address or a hash. Verdicts per strategy (P, B, E = the page) and kind (n,
 * sig) over the builds, as the computer canary (`scripts/own-solver-canary.mjs`): wrong (a value
 * that differs from ejs's) before oracle (ejs failed on an input, so the player cannot be judged)
 * before miss (no value where ejs gave one: safe) before pass; fail when no build ran the
 * strategy. V is the live check of the app's lookup on one public video.
 */
internal object SolverCanaryReport {
    /** P hit in the same process, phone build (owner, N7). */
    const val SAME_PROCESS_TARGET_MILLIS: Long = 2_000

    /** P hit after a restart, the engine's cold start included (owner, N7). */
    const val AFTER_RESTART_TARGET_MILLIS: Long = 8_000

    const val TITLE: String = "YFT solver canary"
    const val PHONE: String = "phone"
    const val MAIN: String = "main"

    val KINDS: List<PlayerScriptChallengeKind> =
        listOf(PlayerScriptChallengeKind.RATE_PARAM, PlayerScriptChallengeKind.SIGNATURE)

    /** One kind of one run on one build against ejs: inputs, solved by ejs, agreed, wrong. */
    data class Counts(val total: Int, val ejs: Int, val agree: Int, val wrong: Int) {
        val missed: Int get() = ejs - agree - wrong
        val clean: Boolean get() = total > 0 && agree == total
    }

    /** [inputs] of one kind, ejs's values and a strategy's values, both by input. */
    fun judge(inputs: List<String>, ejs: Map<String, String>, own: Map<String, String>): Counts {
        val solved = inputs.filter { ejs[it] != null }
        return Counts(
            total = inputs.size,
            ejs = solved.size,
            agree = solved.count { own[it] == ejs[it] },
            wrong = solved.count { own[it] != null && own[it] != ejs[it] },
        )
    }

    /** Two runs of one step on the same inputs (the second with the first's parts): the worse counts. */
    fun worst(first: Counts, second: Counts?): Counts = if (second == null) {
        first
    } else {
        Counts(
            total = first.total,
            ejs = minOf(first.ejs, second.ejs),
            agree = minOf(first.agree, second.agree),
            wrong = maxOf(first.wrong, second.wrong),
        )
    }

    enum class Verdict(val label: String) {
        PASS("pass"),
        MISS("miss"),
        ORACLE("oracle"),
        WRONG("wrong"),
        FAIL("fail"),
    }

    /** One strategy's verdict on one kind over the builds (null or no inputs: the build did not run it). */
    fun verdict(builds: List<Counts?>): Verdict {
        val present = builds.filterNotNull().filter { it.total > 0 }
        return when {
            present.isEmpty() -> Verdict.FAIL
            present.any { it.wrong > 0 } -> Verdict.WRONG
            present.any { it.ejs < it.total } -> Verdict.ORACLE
            present.all { it.agree == it.total } -> Verdict.PASS
            else -> Verdict.MISS
        }
    }

    /** The live check V of the lookup's signatures, as the app's gate ran it. */
    enum class VStatus(val label: String, val ok: Boolean) {
        PASS("pass", true),

        /** The lookup's stream needed no signature. */
        NOT_NEEDED("not needed", true),

        /** V already passed for this player text and step (the ledger file), so the gate skipped it. */
        SKIPPED_LEDGER("skipped (ledger pass)", true),
        UNKNOWN("unknown", false),
        FAIL("fail", false),

        /** No signature reached the gate (no lookup, or the runner gave none). */
        NOT_RUN("not run", false),
    }

    data class Header(
        val run: Int,
        /** The first run in this process (after the app was closed): the after-restart numbers. */
        val newProcess: Boolean,
        val android: String,
        val webView: String,
        val app: String,
        /** Today's player (null: not found). */
        val player: String?,
        /**
         * The JS heap limit of the app's WebView renderer (`performance.memory.jsHeapSizeLimit`,
         * Chromium only), read in the tool's own WebView after the steps; null: unknown.
         */
        val jsHeapLimitBytes: Long? = null,
    )

    /** One step's run on one build ([counts] empty when no job went out). */
    data class StepLine(
        val name: String,
        val summary: OwnSolverProbe.Summary,
        val counts: Map<PlayerScriptChallengeKind, Counts>,
        /** Page steps: the longest stall of a visible WebView during the job. */
        val stallMillis: Long? = null,
    )

    /** A timed request: [bytes] over the network, [cached] when the tool's cache answered. */
    data class Part(val name: String, val millis: Long, val bytes: Long, val cached: Boolean = false)

    data class Build(
        val name: String,
        val download: Part?,
        /** Why the build was not judged (a download failure class); null when it was. */
        val failure: String? = null,
        val ejsMillis: Long? = null,
        /** Per kind: the inputs ejs solved, of [total]. */
        val ejsSolved: Map<PlayerScriptChallengeKind, Int> = emptyMap(),
        val total: Map<PlayerScriptChallengeKind, Int> = emptyMap(),
        val p1: StepLine? = null,
        val p2: StepLine? = null,
        val b1: StepLine? = null,
        val b2: StepLine? = null,
        val pageP: StepLine? = null,
        val pageB: StepLine? = null,
    ) {
        val steps: List<StepLine> get() = listOfNotNull(p1, p2, b1, b2, pageP, pageB)
    }

    /** The app's lookup of one public video, V included, with the tool's own runner and files. */
    data class Lookup(
        /** `rows`, `no rows (...)`, `not measured (...)` or `stopped (...)`. */
        val outcome: String,
        val totalMillis: Long?,
        val parts: List<Part>,
        /** The lookup's first worker P job, with its times. */
        val workerP: OwnSolverProbe.Summary?,
        val v: VStatus,
        val jobs: List<OwnSolverProbe.Summary>,
        /** The runner's failure classes. */
        val failures: List<String>,
        /** The lookup's player is today's player (null: unknown). */
        val samePlayer: Boolean?,
    )

    data class Data(val appBytes: Long?, val httpBytes: Long, val calls: Int, val cacheHits: Int)

    data class Report(
        val header: Header,
        val lookup: Lookup?,
        val builds: List<Build>,
        /** The step whose run stopped the canary; null when every step ran. */
        val stoppedAt: String?,
        /** Steps the breaker has off for today's player at the end (labels). */
        val breakerOff: List<String>,
        val signaturesOff: List<String>,
        val data: Data?,
        val totalMillis: Long,
    )

    val STRATEGIES: List<String> = listOf("P", "B", "E")

    /** A build's counts of [strategy] on [kind]: P and B the worse of their two runs, E the ladder's page step. */
    fun countsOf(build: Build, strategy: String, kind: PlayerScriptChallengeKind): Counts? = when (strategy) {
        "P" -> build.p1?.counts?.get(kind)?.let { worst(it, build.p2?.counts?.get(kind)) }
        "B" -> build.b1?.counts?.get(kind)?.let { worst(it, build.b2?.counts?.get(kind)) }
        else -> build.pageP?.let { page ->
            val first = page.counts[kind] ?: return@let null
            // The ladder's E: page P, then page B for a kind page P did not answer cleanly.
            if (kind in page.summary.answered) first else build.pageB?.counts?.get(kind) ?: first
        }
    }

    fun verdicts(report: Report): Map<String, Map<PlayerScriptChallengeKind, Verdict>> =
        STRATEGIES.associateWith { strategy ->
            KINDS.associateWith { kind -> verdict(report.builds.map { countsOf(it, strategy, kind) }) }
        }

    /** Every build judged and every step run, every verdict a pass, and V ok. */
    fun passes(report: Report): Boolean =
        report.stoppedAt == null &&
            report.builds.isNotEmpty() &&
            report.builds.all { it.failure == null } &&
            verdicts(report).values.all { kinds -> kinds.values.all { it == Verdict.PASS } } &&
            report.lookup?.v?.ok == true

    /** A locator hit: the job sent parts and the core used every one. */
    fun hit(summary: OwnSolverProbe.Summary): Boolean =
        summary.outcome == OwnSolverProbe.REPLY &&
            summary.partsSent.isNotEmpty() &&
            summary.locatorHits.containsAll(summary.partsSent)

    /** A hit time against its target, or why there is none ([failed]: parts went out unused). */
    data class Timing(val millis: Long?, val note: String?, val failed: Boolean = false)

    /** The phone build's P2 (prepared program and parts, same process). */
    fun sameProcess(report: Report): Timing = hitTiming(report.builds.firstOrNull { it.name == PHONE }?.p2?.summary, "no parts")

    /** The lookup's worker P job in a new process (the engine's cold start included). */
    fun afterRestart(report: Report): Timing = when {
        !report.header.newProcess -> Timing(null, "same process")
        else -> hitTiming(report.lookup?.workerP, "no parts yet")
    }

    private fun hitTiming(summary: OwnSolverProbe.Summary?, noParts: String): Timing = when {
        summary == null -> Timing(null, "not measured")
        summary.outcome != OwnSolverProbe.REPLY -> Timing(null, summary.outcome, failed = true)
        summary.partsSent.isEmpty() -> Timing(null, noParts)
        !hit(summary) -> Timing(null, "no hit", failed = true)
        else -> Timing(summary.totalMillis, null)
    }

    fun firstLine(report: Report): String = buildString {
        append(TITLE).append(": ").append(if (passes(report)) "PASS" else "FAIL")
        append(" · P hit same process ").append(timing(sameProcess(report), SAME_PROCESS_TARGET_MILLIS))
        append(" · after restart ").append(timing(afterRestart(report), AFTER_RESTART_TARGET_MILLIS))
        report.stoppedAt?.let { append(" · stopped at ").append(safe(it)) }
    }

    private fun timing(timing: Timing, target: Long): String {
        val bound = "(≤ ${target / 1_000} s)"
        val millis = timing.millis
        return when {
            millis != null -> "${seconds(millis)} ${if (millis <= target) "✅" else "❌"} $bound"
            timing.failed -> "— ❌ (${safe(timing.note.orEmpty())}) $bound"
            else -> "— (${safe(timing.note.orEmpty())}) $bound"
        }
    }

    fun render(report: Report): String = buildString {
        appendLine(firstLine(report))
        val header = report.header
        appendLine(
            "run ${header.run} · process ${if (header.newProcess) "new" else "same"} · Android ${safe(header.android)} · " +
                "WebView ${safe(header.webView)} · app ${safe(header.app)} · JS heap limit ${heapLimit(header.jsHeapLimitBytes)}",
        )
        val samePlayer = when (report.lookup?.samePlayer) {
            true -> "same"
            false -> "other"
            null -> "—"
        }
        appendLine("player ${header.player?.let(::safe) ?: "not found"} · lookup player $samePlayer")
        val verdicts = verdicts(report)
        appendLine(
            "verdicts: " + STRATEGIES.joinToString(" · ") { strategy ->
                "$strategy " + KINDS.joinToString(" ") { kind -> "${label(kind)} ${verdicts.getValue(strategy).getValue(kind).label}" }
            } + " · V ${report.lookup?.v?.label ?: VStatus.NOT_RUN.label}",
        )
        appendLine(hitsLine(report))
        report.lookup?.let { appendLookup(it, header.newProcess) }
        report.builds.forEach { appendBuild(it) }
        appendLine(
            "breaker off: " + report.breakerOff.ifEmpty { listOf("none") }.joinToString(", ") +
                " · sig off: " + report.signaturesOff.ifEmpty { listOf("none") }.joinToString(", "),
        )
        report.data?.let { data ->
            appendLine(
                "data: app ${data.appBytes?.let(::bytes) ?: "unknown"} · HTTP ${bytes(data.httpBytes)} in " +
                    "${data.calls} calls, ${data.cacheHits} from the tool's cache",
            )
        }
        append("total ").append(seconds(report.totalMillis))
    }

    /** Every hit time, phone and main (main and B have no target), and the lookup's. */
    private fun hitsLine(report: Report): String {
        fun of(step: (Build) -> StepLine?): String = report.builds.joinToString(", ") { build ->
            val summary = step(build)?.summary
            "${build.name} " + if (summary != null && hit(summary)) seconds(summary.totalMillis ?: 0) else "—"
        }.ifEmpty { "—" }
        val lookup = report.lookup?.workerP?.takeIf(::hit)?.totalMillis?.let(::seconds) ?: "—"
        return "hits: P2 ${of { it.p2 }} · B2 ${of { it.b2 }} · lookup P $lookup " +
            "(process ${if (report.header.newProcess) "new" else "same"})"
    }

    private fun StringBuilder.appendLookup(lookup: Lookup, newProcess: Boolean) {
        val parts = lookup.parts.groupBy { it.name }.entries.joinToString(" · ") { (name, list) ->
            val count = if (list.size > 1) " ×${list.size}" else ""
            val cached = if (list.any { it.cached }) " (cache)" else ""
            "${safe(name)}$count ${seconds(list.sumOf { it.millis })} ${bytes(list.sumOf { it.bytes })}$cached"
        }
        appendLine(
            "lookup (process ${if (newProcess) "new" else "same"}): ${safe(lookup.outcome)}" +
                (lookup.totalMillis?.let { " · ${seconds(it)}" } ?: "") + (if (parts.isEmpty()) "" else " · $parts"),
        )
        lookup.jobs.forEach { appendLine("  ${stepName(it.step)} ${job(it)}") }
        if (lookup.failures.isNotEmpty()) {
            appendLine("  failures: " + lookup.failures.joinToString(", ") { safe(it, MAX_CLASS_CHARS) })
        }
    }

    private fun StringBuilder.appendBuild(build: Build) {
        val download = build.download?.let { part ->
            "download ${seconds(part.millis)} ${bytes(part.bytes)}" + if (part.cached) " (cache)" else ""
        } ?: "download —"
        val failure = build.failure?.let { " · not judged (${safe(it)})" } ?: ""
        val ejs = build.ejsMillis?.let { millis ->
            " · ejs ${seconds(millis)} (" + KINDS.joinToString(" ") { kind ->
                "${label(kind)} ${build.ejsSolved[kind] ?: 0}/${build.total[kind] ?: 0}"
            } + ")"
        } ?: ""
        appendLine("${safe(build.name)}: $download$failure$ejs")
        build.steps.forEach { line ->
            val stall = line.stallMillis?.let { " · stall ${seconds(it)}" } ?: ""
            val counts = KINDS.mapNotNull { kind ->
                line.counts[kind]?.let { " · ${label(kind)} ${counts(it)}" }
            }.joinToString("")
            appendLine("  ${safe(line.name)} ${job(line.summary)}$stall$counts")
        }
    }

    /** One job: outcome, times, parts, answers and failure classes; never a value. */
    fun job(summary: OwnSolverProbe.Summary): String = buildList {
        add(safe(summary.outcome) + if (summary.budget) " budget" else "")
        if (summary.fromPrepared) add("prepared")
        summary.totalMillis?.let { total ->
            add(seconds(total) + (summary.readyMillis?.let { " (ready ${seconds(it)})" } ?: ""))
        }
        if (summary.located) add("sent ${kinds(summary.partsSent)}")
        if (summary.locatorHits.isNotEmpty()) add("hits ${kinds(summary.locatorHits)}")
        if (summary.partsGiven.isNotEmpty()) add("given ${kinds(summary.partsGiven)}")
        if (summary.outcome == OwnSolverProbe.REPLY) add("answered ${kinds(summary.answered)}")
        summary.initErrors?.takeIf { it > 0 }?.let { add("init errors $it") }
        if (summary.failures.isNotEmpty()) add("failures " + summary.failures.joinToString(",") { safe(it, MAX_CLASS_CHARS) })
        if (summary.signaturesOff) add("sig off")
    }.joinToString(" · ")

    private fun counts(counts: Counts): String = buildString {
        append(counts.agree).append('/').append(counts.total)
        if (counts.wrong > 0) append(" wrong ").append(counts.wrong)
        if (counts.ejs < counts.total) append(" (ejs ").append(counts.ejs).append(')')
    }

    fun stepName(step: OwnSolverBreaker.Step): String = when (step) {
        OwnSolverBreaker.Step.WORKER_P -> "P"
        OwnSolverBreaker.Step.WORKER_B -> "B"
        OwnSolverBreaker.Step.PAGE_P -> "page P"
        OwnSolverBreaker.Step.PAGE_B -> "page B"
    }

    fun label(kind: PlayerScriptChallengeKind): String = when (kind) {
        PlayerScriptChallengeKind.RATE_PARAM -> "n"
        PlayerScriptChallengeKind.SIGNATURE -> "sig"
    }

    private fun kinds(kinds: Set<PlayerScriptChallengeKind>): String =
        KINDS.filter { it in kinds }.joinToString(",") { label(it) }.ifEmpty { "none" }

    fun seconds(millis: Long): String = String.format(Locale.ROOT, "%.1f s", millis / 1_000.0)

    fun bytes(count: Long): String = when {
        count < 1_024 -> "$count B"
        count < 1_024 * 1_024 -> "${count / 1_024} KB"
        else -> String.format(Locale.ROOT, "%.1f MB", count / (1_024.0 * 1_024))
    }

    /**
     * Text for the report from a class, a reason or a version: letters, digits and a few signs
     * (no `/`, so never an address), and any long mixed token (a value's form) cut.
     */
    fun safe(text: String, max: Int = MAX_TEXT_CHARS): String =
        text.filter { it.isLetterOrDigit() || it in SAFE_SIGNS }
            .replace(TOKEN) { token -> if (WORD.matches(token.value)) token.value else CUT }
            .take(max)

    /** A heap limit in whole MB, or "unknown" (no Chromium `performance.memory`, or no reading). */
    private fun heapLimit(bytes: Long?): String =
        bytes?.takeIf { it > 0 }?.let { "${(it + BYTES_PER_MB / 2) / BYTES_PER_MB} MB" } ?: "unknown"

    private const val BYTES_PER_MB = 1024L * 1024L
    private const val MAX_TEXT_CHARS = 160
    private const val MAX_CLASS_CHARS = 48
    private const val SAFE_SIGNS = " .,:;()_-+×≤—"
    private const val CUT = "<cut>"

    /** A token as long as a value's shortest form. */
    private val TOKEN = Regex("[A-Za-z0-9_-]{10,}")

    /** Long tokens that are words: an enum name, a lower-case class or a class name (`SyntaxError`, `IOException`). */
    private val WORD = Regex("[A-Z_]+|[a-z-]+|[A-Z]*(?:[A-Z][a-z]+)+")
}