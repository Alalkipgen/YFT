package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.ladder.CensusJson
import com.alal.yft.extractor.master.modules.youtube.ladder.CensusSanitizer
import com.alal.yft.extractor.master.policy.TerminalRules
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * S0b: a row's class, from main's answer as Master's engine sees its reason: [RECOVERABLE] (the
 * engine would ask Master's contract), [BROWSER] (`TerminalRules.BROWSER_REQUIRED`), [FINAL]
 * (`TerminalRules.NEVER_FALLBACK`); or main's rows, its time limit, no adapter, an error.
 */
internal enum class RowClass(val label: String) {
    ROWS("rows"),
    RECOVERABLE("recoverable"),
    BROWSER("browser"),
    FINAL("final"),
    TIME_LIMIT("time limit"),
    NOT_HANDLED("not handled"),
    ERROR("error"),
    ;

    companion object {
        fun of(reason: SiteExtractionFailure): RowClass = when (reason) {
            in TerminalRules.BROWSER_REQUIRED -> BROWSER
            in TerminalRules.NEVER_FALLBACK -> FINAL
            else -> RECOVERABLE
        }
    }
}

/** S0b: how Master's way ran for a row. */
internal enum class MasterRun(val label: String) { ROUTE_B("route B"), TRIAL("trial"), NOT_RUN("not run") }

/** S0b: main's part of a row: its answer, its asks (labelled) and the ways it did not reach. */
internal class MainPart(
    val answer: String,
    val reason: SiteExtractionFailure?,
    val statusCode: Int?,
    val rows: Int,
    val tallest: Int?,
    val millis: Long,
    val asks: List<LookupRecord.Ask>,
    val notReached: List<String>,
    val hiddenPage: AnswerMark?,
    val repeated: Int,
    val afterStop: Int,
    val botChecks: Int,
    val stopHosts: Int,
)

/** S0b: Master's part of a row: how it ran (or why not), its answer, route B's counts, V. */
internal class MasterPart(
    val run: MasterRun,
    val why: String?,
    val answer: String? = null,
    val reason: SiteExtractionFailure? = null,
    val rows: Int = 0,
    val tallest: Int? = null,
    val replayed: Int = 0,
    val newRequests: Int = 0,
    val refused: Int = 0,
    val checks: Int = 0,
    val asks: List<LookupRecord.Ask> = emptyList(),
    val v: SiteRowCheck.Checked? = null,
    val vSkipped: String? = null,
    val millis: Long = 0,
)

/** S0b: one row (a link in its [slot]); [skipped] says why it was not looked up. */
internal class CensusRowResult(
    val slot: Int,
    val kind: Kind,
    val session: Boolean = false,
    val skipped: String? = null,
    val main: MainPart? = null,
    val rowClass: RowClass? = null,
    val expected: Boolean? = null,
    val note: String? = null,
    val v: SiteRowCheck.Checked? = null,
    val vSkipped: String? = null,
    val master: MasterPart? = null,
    val requests: Int = 0,
)

/** S0b: one site of a run; [bytes] is -1 when not measured; [cooldownFrom]: when a 429 or bot check stopped it. */
internal class CensusSiteResult(
    val site: Site,
    val rows: List<CensusRowResult> = emptyList(),
    val requests: Int = 0,
    val bytes: Long = -1,
    val stop: String? = null,
    val cooldownFrom: Long? = null,
    val skipped: String? = null,
    val millis: Long = 0,
    val notes: List<String> = emptyList(),
) {
    val number: SiteNumber by lazy { SiteNumber(rows) }
}

/** S0b: one run of the census. */
internal class CensusRunResult(
    val atMillis: Long,
    val millis: Long,
    val notes: List<String>,
    val sites: List<CensusSiteResult>,
)

/**
 * S0b: a site's number (§7.1): main's recoverable failures among the rows that should download
 * (the slot's kind wants rows, with the session when it was there), and how many of them Master's
 * way recovered (route B rows that passed V); the other classes, the rows not as expected, V on
 * main's rows and the trials beside it.
 */
internal class SiteNumber(rows: List<CensusRowResult>) {
    private val looked = rows.filter { it.main != null }
    private val download = looked.filter { it.kind.expect.wantsRows(it.session) }
    private val trials = looked.filter { it.master?.run == MasterRun.TRIAL }

    val shouldDownload = download.size
    val recoverable = download.count { it.rowClass == RowClass.RECOVERABLE }
    val masterRecovered = download.count {
        it.rowClass == RowClass.RECOVERABLE && it.master?.run == MasterRun.ROUTE_B &&
            it.master.v?.verdict == SiteRowCheck.Verdict.PASS
    }
    val browser = download.count { it.rowClass == RowClass.BROWSER }
    val final = download.count { it.rowClass == RowClass.FINAL }
    val timeLimit = download.count { it.rowClass == RowClass.TIME_LIMIT }
    val notAsExpected = looked.count { it.expected == false }
    val routeB = looked.count { it.master?.run == MasterRun.ROUTE_B }
    val trialCount = trials.size
    val trialRows = trials.count { (it.master?.rows ?: 0) > 0 }
    val trialPass = trials.count { it.master?.v?.verdict == SiteRowCheck.Verdict.PASS }
    val mainPass = looked.count { it.v?.verdict == SiteRowCheck.Verdict.PASS }
    val mainFail = looked.count { it.v?.verdict == SiteRowCheck.Verdict.FAIL }
    val mainUnknown = looked.count { it.v?.verdict == SiteRowCheck.Verdict.UNKNOWN }
}

/** S0b: the export (one run) as JSON: labels, counts, sizes and fingerprints only. */
internal object SiteCensusJson {
    fun dateOf(epochMillis: Long): String = CensusJson.dateOf(epochMillis)

    fun run(run: CensusRunResult): String = obj {
        field("tool", SiteCensus.TOOL_LABEL)
        field("fmt", SiteCensusState.FORMAT)
        field("at", run.atMillis)
        field("date", dateOf(run.atMillis))
        field("ms", run.millis)
        strings("notes", run.notes)
        list("sites", run.sites.map(::site))
    }

    private fun site(result: CensusSiteResult): String = obj {
        val number = result.number
        field("site", result.site.id)
        field("name", result.site.name)
        field("slots", result.site.slots.size)
        field("skipped", result.skipped)
        field("stop", result.stop)
        field("req", result.requests)
        field("bytes", result.bytes)
        field("ms", result.millis)
        strings("notes", result.notes)
        raw(
            "number",
            obj {
                field("download", number.shouldDownload)
                field("recoverable", number.recoverable)
                field("master", number.masterRecovered)
                field("browser", number.browser)
                field("final", number.final)
                field("timeLimit", number.timeLimit)
                field("notAsExpected", number.notAsExpected)
                field("routeB", number.routeB)
                field("trials", number.trialCount)
                field("trialRows", number.trialRows)
                field("trialPass", number.trialPass)
                field("vPass", number.mainPass)
                field("vFail", number.mainFail)
                field("vUnknown", number.mainUnknown)
            },
        )
        list("rows", result.rows.map(::row))
    }

    private fun row(row: CensusRowResult): String = obj {
        field("slot", row.slot)
        field("kind", row.kind.label)
        field("session", row.session)
        field("skipped", row.skipped)
        field("class", row.rowClass?.label)
        field("expected", row.expected)
        field("note", row.note)
        field("req", row.requests.takeIf { row.main != null })
        row.main?.let { raw("main", main(it)) }
        row.v?.let { raw("v", checked(it)) }
        field("vSkipped", row.vSkipped)
        row.master?.let { raw("master", master(it)) }
    }

    private fun main(main: MainPart): String = obj {
        field("answer", main.answer)
        field("reason", main.reason?.name)
        field("status", main.statusCode)
        field("rows", main.rows)
        field("tallest", main.tallest)
        field("ms", main.millis)
        field("hiddenPage", main.hiddenPage?.label)
        strings("notReached", main.notReached)
        field("repeated", main.repeated)
        field("afterStop", main.afterStop)
        field("botChecks", main.botChecks)
        field("stopHosts", main.stopHosts)
        list("asks", main.asks.map(::ask))
    }

    private fun master(master: MasterPart): String = obj {
        field("run", master.run.label)
        field("why", master.why)
        if (master.run != MasterRun.NOT_RUN) {
            field("answer", master.answer)
            field("reason", master.reason?.name)
            field("rows", master.rows)
            field("tallest", master.tallest)
            field("replayed", master.replayed)
            field("new", master.newRequests)
            field("refused", master.refused)
            field("checks", master.checks)
            field("ms", master.millis)
            list("asks", master.asks.map(::ask))
            master.v?.let { raw("v", checked(it)) }
            field("vSkipped", master.vSkipped)
        }
    }

    private fun checked(checked: SiteRowCheck.Checked): String = obj {
        field("verdict", checked.verdict.label)
        field("why", checked.why)
        field("status", checked.statusCode)
        field("req", checked.requests)
        if (checked.rateLimited) field("rateLimited", true)
    }

    private fun ask(ask: LookupRecord.Ask): String = obj {
        field("way", ask.way)
        field("m", ask.method)
        field("status", ask.statusCode)
        field("failure", ask.failure?.name)
        field("mark", ask.mark?.label)
        field("shape", ask.shape)
        field("at", ask.atMillis)
        field("took", ask.tookMillis)
        if (!ask.answered) field("noAnswer", true)
        if (ask.authorized) field("authorized", true)
        if (ask.repeated) field("repeated", true)
        if (ask.afterStop) field("afterStop", true)
        if (ask.rateLimited) field("rateLimited", true)
    }

    /** One JSON object; fields with a null value are left out. */
    internal fun obj(build: Fields.() -> Unit): String = Fields().apply(build).text()

    internal class Fields {
        private val out = StringBuilder("{")

        fun field(name: String, value: Any?) {
            if (value == null) return
            key(name)
            when (value) {
                is String -> out.append(CensusJson.quote(value))
                else -> out.append(value.toString())
            }
        }

        fun strings(name: String, values: List<String>) = list(name, values.map(CensusJson::quote))

        fun list(name: String, items: List<String>) {
            key(name)
            out.append('[').append(items.joinToString(",")).append(']')
        }

        fun raw(name: String, json: String) {
            key(name)
            out.append(json)
        }

        private fun key(name: String) {
            if (out.length > 1) out.append(',')
            out.append(CensusJson.quote(name)).append(':')
        }

        fun text(): String = out.append('}').toString()
    }
}

/** S0b: the report's text: the headline, the rules, each site's number and each row. */
internal object SiteCensusReport {
    fun headline(run: CensusRunResult): String =
        "${SiteCensus.TOOL_LABEL}: " + run.sites.joinToString(" · ") { site ->
            val number = site.number
            site.skipped?.let { "${site.site.name} $it" }
                ?: ("${site.site.name} recoverable ${number.recoverable} of ${number.shouldDownload}, " +
                    "Master ${number.masterRecovered}" + (site.stop?.let { ", stopped ($it)" } ?: ""))
        }

    fun text(run: CensusRunResult): String = buildString {
        val rows = run.sites.sumOf { it.rows.size }
        appendLine(
            "${SiteCensus.TOOL_LABEL} — ${SiteCensusJson.dateOf(run.atMillis)} — ${run.sites.size} sites, " +
                "$rows rows, ${seconds(run.millis)}",
        )
        appendLine("Main: the app's adapters, fresh, with the browser's identity; each ask once; 90 s per row.")
        appendLine(
            "Master: the site's R8 way once, under route B's rules (\"trial\": after main's rows " +
                "or the site's final answer about the post).",
        )
        appendLine(
            "V: one byte of one row per answer, with the download's headers, on the site's media " +
                "hosts only (HLS or another host: unknown).",
        )
        appendLine("The hidden page's own loads are neither counted nor listed.")
        if (run.notes.isNotEmpty()) appendLine("Notes: ${run.notes.joinToString("; ")}")
        run.sites.forEach { site -> site(site) }
    }

    private fun StringBuilder.site(result: CensusSiteResult) {
        appendLine()
        val name = result.site.name
        result.skipped?.let {
            appendLine("$name — skipped: $it")
            result.notes.forEach { note -> appendLine("  $note") }
            return
        }
        val number = result.number
        appendLine(
            "$name — recoverable ${number.recoverable} of ${number.shouldDownload} rows that should " +
                "download; Master recovered ${number.masterRecovered}",
        )
        appendLine(
            "  requests ${result.requests}, ${bytes(result.bytes)}, ${seconds(result.millis)}" +
                (result.stop?.let { "; stopped: $it" + if (result.cooldownFrom != null) " (30 min)" else "" } ?: ""),
        )
        appendLine(
            "  also: browser ${number.browser}, final ${number.final}, time limit ${number.timeLimit}; " +
                "not as expected ${number.notAsExpected}; main's V pass ${number.mainPass}, " +
                "fail ${number.mainFail}, unknown ${number.mainUnknown}; route B ${number.routeB}; " +
                "trials ${number.trialCount} (rows ${number.trialRows}, V pass ${number.trialPass})",
        )
        result.notes.forEach { appendLine("  $it") }
        result.rows.forEach { row(it) }
    }

    private fun StringBuilder.row(row: CensusRowResult) {
        val head = "  slot ${row.slot} ${row.kind.label}" + (if (row.session) ", session" else "")
        val main = row.main
        if (main == null) {
            appendLine("$head: not looked up (${row.skipped})")
            return
        }
        val answer = when (main.answer) {
            "rows" -> "rows ${main.rows}" + (main.tallest?.let { " (tallest $it)" } ?: "")
            "failed" -> "failed ${main.reason?.name}" + (main.statusCode?.let { " $it" } ?: "")
            else -> main.answer
        }
        val v = row.v?.let { ", V ${checked(it)}" } ?: row.vSkipped?.let { ", V not run ($it)" } ?: ""
        val verdict = if (row.expected == true) "as expected" else "not as expected (${row.note})"
        appendLine("$head: $answer [${row.rowClass?.label}]$v — $verdict; requests ${row.requests}, ${main.millis} ms")
        val asks = main.asks.joinToString(" · ") { ask(it) }.ifEmpty { "no ask" }
        appendLine("    main: $asks")
        val extra = listOfNotNull(
            main.hiddenPage?.let { "hidden page: ${it.label}" },
            main.notReached.takeIf { it.isNotEmpty() }?.let { "not reached: ${it.joinToString(", ")}" },
            main.repeated.takeIf { it > 0 }?.let { "repeated $it" },
            main.afterStop.takeIf { it > 0 }?.let { "after a stop $it" },
            main.botChecks.takeIf { it > 0 }?.let { "bot checks $it" },
            main.stopHosts.takeIf { it > 0 }?.let { "stopped hosts $it" },
        )
        if (extra.isNotEmpty()) appendLine("    ${extra.joinToString("; ")}")
        row.master?.let { master(it) }
    }

    private fun StringBuilder.master(master: MasterPart) {
        if (master.run == MasterRun.NOT_RUN) {
            appendLine("    Master not run: ${master.why}")
            return
        }
        val run = master.run.label + (master.why?.let { " after $it" } ?: "")
        val answer = when (master.answer) {
            "rows" -> "rows ${master.rows}" + (master.tallest?.let { " (tallest $it)" } ?: "")
            "failed", "skipped" -> "${master.answer} ${master.reason?.name}"
            else -> master.answer
        }
        val v = master.v?.let { ", V ${checked(it)}" } ?: master.vSkipped?.let { ", V not run ($it)" } ?: ""
        appendLine(
            "    Master $run: $answer$v; replayed ${master.replayed}, new ${master.newRequests}, " +
                "refused ${master.refused}, checks ${master.checks}, ${master.millis} ms",
        )
        if (master.asks.isNotEmpty()) appendLine("      ${master.asks.joinToString(" · ") { ask(it) }}")
    }

    private fun checked(checked: SiteRowCheck.Checked): String =
        checked.verdict.label + listOfNotNull(checked.statusCode?.toString(), checked.why)
            .joinToString(" ").let { if (it.isEmpty()) "" else " $it" }

    private fun ask(ask: LookupRecord.Ask): String {
        val answer = ask.statusCode?.toString() ?: ask.failure?.name ?: if (ask.answered) "answered" else "no answer"
        val flags = listOfNotNull(
            "authorized".takeIf { ask.authorized },
            "repeated".takeIf { ask.repeated },
            "after a stop".takeIf { ask.afterStop },
            "429".takeIf { ask.rateLimited && ask.statusCode != HTTP_TOO_MANY },
        )
        return listOfNotNull(ask.way ?: "unlabelled", ask.method, answer, ask.mark?.label, ask.shape, "${ask.tookMillis} ms")
            .joinToString(" ") + if (flags.isEmpty()) "" else " (${flags.joinToString(", ")})"
    }

    private fun bytes(bytes: Long): String =
        if (bytes < 0) "bytes not measured" else String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

    private fun seconds(millis: Long): String = "${(millis + 500) / 1000} s"

    private const val HTTP_TOO_MANY = 429
}

/**
 * S0b: the screen's state file (`files/master/site-census-last.json`): each site's cooldown after
 * a 429 or a bot check ([stops]: until when, why), the last run's time and its export.
 */
internal class SiteCensusState(
    val stops: Map<String, Stop>,
    val lastAtMillis: Long = 0,
    val lastJson: String? = null,
) {
    class Stop(val untilMillis: Long, val why: String)

    companion object {
        const val FORMAT = 1
        private const val MAX_STATE_NODES = 400_000
        private val WHY = setOf(SiteCensus.STOP_429, SiteCensus.STOP_BOT_CHECK, SiteCensus.STOP_RATE_LIMIT)

        /** The stored state; an empty one when [text] is missing or unreadable. */
        fun read(text: String?): SiteCensusState {
            val empty = SiteCensusState(emptyMap())
            if (text.isNullOrBlank()) return empty
            val root = BoundedJsonParser.parse(text, maxDepth = 32, maxNodes = MAX_STATE_NODES) ?: return empty
            if (root["fmt"].asLongOrNull != FORMAT.toLong()) return empty
            val stops = (root["stops"] as? JsonValue.Object)?.entries.orEmpty().mapNotNull { (id, value) ->
                val until = value["until"].asLongOrNull ?: return@mapNotNull null
                if (SiteWays.site(id) == null) return@mapNotNull null
                id to Stop(until, value["why"].asStringOrNull?.takeIf { it in WHY } ?: "stop")
            }.toMap()
            return SiteCensusState(stops, root["lastAt"].asLongOrNull ?: 0)
        }

        fun write(state: SiteCensusState): String = SiteCensusJson.obj {
            field("fmt", FORMAT)
            raw(
                "stops",
                SiteCensusJson.obj {
                    state.stops.forEach { (id, stop) ->
                        raw(id, SiteCensusJson.obj {
                            field("until", stop.untilMillis)
                            field("why", stop.why)
                        })
                    }
                },
            )
            field("lastAt", state.lastAtMillis)
            state.lastJson?.let { raw("last", it) }
        }
    }
}

/**
 * S0b: privacy (S8): the values a row's link holds (the link, main's canonical address and post
 * ID, the address's own pieces: a handle, a code; not the site's structural words) and the last
 * guard ([CensusSanitizer]: any address becomes `[address]`, each value `*`).
 */
internal object CensusText {
    private const val MIN_PIECE = 5

    /** A shorter number is not a post ID and would hit the report's own numbers (times, sizes). */
    private const val MIN_NUMBER = 14
    private const val UNSAFE = "\"\\{}[]:,<>"
    private val STRUCTURAL = setOf(
        "video", "videos", "reel", "reels", "watch", "share", "status", "statuses", "groups", "group",
        "permalink", "permalink.php", "posts", "post", "story.php", "embed", "photo", "photos",
        "photo.php", "video.php", "plugins", "captioned", "stories", "media", "tweet", "live",
    )

    fun secretsOf(link: String, page: SitePageIdentity?): Set<String> = buildSet {
        link.trim().takeIf(String::isNotEmpty)?.let(::add)
        page?.let {
            add(it.canonicalPageUrl)
            it.contentId.takeIf(::safe)?.let(::add)
        }
        listOfNotNull(link, page?.canonicalPageUrl).forEach { address ->
            val uri = runCatching { URI(address.trim()) }.getOrNull() ?: return@forEach
            uri.rawPath.orEmpty().split('/').forEach { piece(it) }
            uri.rawQuery.orEmpty().split('&').forEach { piece(it.substringAfter('=', "")) }
        }
    }

    private fun MutableSet<String>.piece(raw: String) {
        val decoded = runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8) }.getOrNull()
        listOfNotNull(raw, decoded).map(String::trim).flatMap { listOf(it, it.removePrefix("@")) }.forEach { piece ->
            if (piece.lowercase() !in STRUCTURAL && safe(piece)) add(piece)
        }
    }

    /** Long enough, not a short number, and nothing that would break the export's JSON when masked. */
    private fun safe(value: String): Boolean =
        value.length >= MIN_PIECE && value.none { it in UNSAFE } &&
            !(value.all(Char::isDigit) && value.length < MIN_NUMBER)

    fun clean(text: String, secrets: Collection<String>): String = CensusSanitizer.clean(text, secrets)

    /** A context line (the network type): letters and punctuation only, at most 80 characters. */
    fun note(text: String): String? = CensusSanitizer.reason(text, max = 80)
}
