package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
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

/**
 * S0b: main's part of a row: its answer, its asks (labelled) and the ways it did not reach. 0.4:
 * [bestMillis], the best row's duration; [item], the post's item main's answer names; [repeats]
 * and [variants], the record's split of the asks sent again ([repeated] is the endpoint's count).
 */
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
    val bestMillis: Long? = null,
    val item: PostItem? = null,
    val repeats: Int = 0,
    val variants: Int = 0,
)

/**
 * S0b: Master's part of a row: how it ran (or why not), its answer, route B's counts, V; 0.4: the
 * best row's duration; 0.4 (c): [stopped], the Master phase's stop the part met (not run because
 * of it or of an earlier one's cooldown, or its own ask, check or V got the 429 or the bot check):
 * the diff does not compare such a part.
 */
internal class MasterPart(
    val run: MasterRun,
    val why: String?,
    val answer: String? = null,
    val reason: SiteExtractionFailure? = null,
    val rows: Int = 0,
    val tallest: Int? = null,
    val bestMillis: Long? = null,
    val replayed: Int = 0,
    val newRequests: Int = 0,
    val refused: Int = 0,
    val checks: Int = 0,
    val asks: List<LookupRecord.Ask> = emptyList(),
    val v: SiteRowCheck.Checked? = null,
    val vSkipped: String? = null,
    val millis: Long = 0,
    val stopped: String? = null,
    val ladder: LadderPart? = null,
)

/**
 * Task 6b: a Master part the site's ladder ran (the census's pack, a social-ladder build): each
 * rung's state ([rungs], [LadderRungs]) and Master's rows against main's ([compare]: `main`,
 * main's rows; `slot wants none`, no row of main's and the slot never downloads; `main none`, no
 * row of main's for a slot that downloads; null: Master found no row; [mainRows], main's rows):
 * [same] and [duration] as
 * the trial line says them ([TrialCompare]; [item]: X's and Instagram's "the same item"), the
 * rows proven another post's ([wrong]: every row where the slot wants none) and the rows not
 * proven either way ([unknown]: every row when main has none for a slot that downloads). A part
 * the ladder did not run holds no rung ([rungs] empty).
 */
internal class LadderPart(
    val rungs: List<LadderRungs.Rung> = emptyList(),
    val compare: String? = null,
    val item: Boolean = false,
    val same: String? = null,
    val duration: String? = null,
    val wrong: Int = 0,
    val unknown: Int = 0,
    val mainRows: Int = 0,
) {
    /** `rungs: F1 rows · F2 not reached · F3 not reached; …` (the report's line); [masterRows], the part's rows. */
    fun text(masterRows: Int): String = "rungs: " + rungs.joinToString(" · ") { it.text() } + when (compare) {
        null -> ""
        COMPARE_MAIN -> "; Master's rows $masterRows, main's $mainRows; the same ${if (item) "item" else "video"}: $same; " +
            "duration within 1 s: $duration; wrong rows $wrong (unknown $unknown)"
        COMPARE_NONE_WANTED -> "; main's rows none, the slot wants none: wrong rows $wrong (unknown $unknown)"
        else -> "; main's rows none: wrong rows $wrong (unknown $unknown)"
    }

    companion object {
        const val COMPARE_MAIN = "main"
        const val COMPARE_NONE_WANTED = "slot wants none"
        const val COMPARE_MAIN_NONE = "main none"
    }
}

/**
 * S0b: one row (a link in its [slot]); [skipped] says why it was not looked up. 0.4: a looked-up
 * row's [setupMillis] (main's identity of the link) and [millis] (end to end: main, V, Master);
 * [ambiguous]: the judge said neither way ([expected] null, its [note] says why).
 */
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
    val setupMillis: Long = 0,
    val millis: Long = 0,
) {
    val ambiguous: Boolean get() = main != null && expected == null
}

/**
 * S0b: one site of a run; [bytes] is -1 when not measured; [cooldownFrom]: when a 429 or bot check
 * stopped it. 0.4 (c): [stop] is (i)'s; [masterStop], the stop in (ii) or (iii) (only the
 * remaining Master parts skipped; [masterCooldownFrom]: when a 429 or bot check stopped them);
 * [masterCooldown]: an earlier Master-phase stop's cooldown skipped this run's Master parts.
 * Task 6b: [ladder], the site's Master parts are its ladder's (the census's pack).
 */
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
    val masterStop: String? = null,
    val masterCooldownFrom: Long? = null,
    val masterCooldown: String? = null,
    val ladder: Boolean = false,
) {
    val number: SiteNumber by lazy { SiteNumber(rows) }

    /** 0.4 (c): whether a looked-up row had the site's session (the baseline's key). */
    val session: Boolean get() = rows.any { it.main != null && it.session }

    /** 0.4 (c): "main N/M · trials incomplete (why)" when the Master phase stopped or cooled down. */
    val incomplete: String?
        get() = (masterStop ?: masterCooldown)?.let { why ->
            "main ${rows.count { it.main != null }}/${rows.size} · trials incomplete ($why)"
        }
}

/** S0b: one run of the census; 0.4 (c): [vpn], whether Android's network was a VPN; task 8: [weekly], a W run. */
internal class CensusRunResult(
    val atMillis: Long,
    val millis: Long,
    val notes: List<String>,
    val sites: List<CensusSiteResult>,
    val vpn: Boolean = false,
    val weekly: Boolean = false,
)

/**
 * S0b: a site's number (§7.1): main's recoverable failures among the rows that should download
 * (the slot's kind wants rows, with the session when it was there), and how many of them Master's
 * way recovered (route B rows that passed V); the other classes, the rows not as expected (0.4:
 * and the ambiguous ones, which are not counted as either), V on main's rows and the trials
 * beside it.
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
    val ambiguous = looked.count { it.ambiguous }
    val routeB = looked.count { it.master?.run == MasterRun.ROUTE_B }
    val trialCount = trials.size
    val trialRows = trials.count { (it.master?.rows ?: 0) > 0 }
    val trialPass = trials.count { it.master?.v?.verdict == SiteRowCheck.Verdict.PASS }
    val mainPass = looked.count { it.v?.verdict == SiteRowCheck.Verdict.PASS }
    val mainFail = looked.count { it.v?.verdict == SiteRowCheck.Verdict.FAIL }
    val mainUnknown = looked.count { it.v?.verdict == SiteRowCheck.Verdict.UNKNOWN }

    /** Task 6b: the Master parts the site's ladder ran, and of them: with rows, the same video (or item), V pass. */
    private val ladders = looked.filter { it.master?.ladder != null && it.master.run != MasterRun.NOT_RUN }
    val ladderParts = ladders.size
    val ladderRows = ladders.count { (it.master?.rows ?: 0) > 0 }
    val ladderSame = ladders.count { it.master?.ladder?.same == YES }
    val ladderPass = ladders.count { it.master?.v?.verdict == SiteRowCheck.Verdict.PASS }

    /** Task 6b: Master's rows proven another post's, and those not proven either way, over the site's ladder parts. */
    val ladderWrong = ladders.sumOf { it.master?.ladder?.wrong ?: 0 }
    val ladderUnknown = ladders.sumOf { it.master?.ladder?.unknown ?: 0 }

    private companion object {
        const val YES = "yes"
    }
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
        field("vpn", run.vpn)
        // Task 8: a W run's export says so (the census's has no such field, as before).
        if (run.weekly) field("mode", "weekly")
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
        field("masterStop", result.masterStop)
        field("masterCooldown", result.masterCooldown)
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
                field("ambiguous", number.ambiguous)
                field("routeB", number.routeB)
                field("trials", number.trialCount)
                field("trialRows", number.trialRows)
                field("trialPass", number.trialPass)
                field("vPass", number.mainPass)
                field("vFail", number.mainFail)
                field("vUnknown", number.mainUnknown)
                // Task 6b: the ladder's numbers, at the end (an older export has none).
                if (result.ladder) {
                    field("ladderParts", number.ladderParts)
                    field("ladderRows", number.ladderRows)
                    field("ladderSame", number.ladderSame)
                    field("ladderPass", number.ladderPass)
                    field("ladderWrong", number.ladderWrong)
                    field("ladderUnknown", number.ladderUnknown)
                }
            },
        )
        list("rows", result.rows.map(::row))
        if (result.ladder) field("ladder", true)
    }

    private fun row(row: CensusRowResult): String = obj {
        field("slot", row.slot)
        field("kind", row.kind.label)
        field("session", row.session)
        field("skipped", row.skipped)
        field("class", row.rowClass?.label)
        field("expected", row.expected)
        if (row.ambiguous) field("ambiguous", true)
        field("note", row.note)
        field("req", row.requests.takeIf { row.main != null })
        field("setupMs", row.setupMillis.takeIf { row.main != null })
        field("ms", row.millis.takeIf { row.main != null })
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
        main.bestMillis?.let { raw("bestS", decimalSeconds(it)) }
        field("item", main.item?.number)
        field("items", main.item?.count)
        field("ms", main.millis)
        field("hiddenPage", main.hiddenPage?.label)
        strings("notReached", main.notReached)
        field("repeated", main.repeated)
        field("repeats", main.repeats)
        field("variants", main.variants)
        field("afterStop", main.afterStop)
        field("botChecks", main.botChecks)
        field("stopHosts", main.stopHosts)
        list("asks", main.asks.map(::ask))
    }

    private fun master(master: MasterPart): String = obj {
        field("run", master.run.label)
        field("why", master.why)
        field("stopped", master.stopped)
        if (master.run != MasterRun.NOT_RUN) {
            field("answer", master.answer)
            field("reason", master.reason?.name)
            field("rows", master.rows)
            field("tallest", master.tallest)
            master.bestMillis?.let { raw("bestS", decimalSeconds(it)) }
            field("replayed", master.replayed)
            field("new", master.newRequests)
            field("refused", master.refused)
            field("checks", master.checks)
            field("ms", master.millis)
            list("asks", master.asks.map(::ask))
            master.v?.let { raw("v", checked(it)) }
            field("vSkipped", master.vSkipped)
        }
        // Task 6b: the ladder's part, at the end (an older export has none).
        master.ladder?.let { ladder ->
            field("ladder", true)
            if (master.run != MasterRun.NOT_RUN) {
                list(
                    "rungs",
                    ladder.rungs.map { rung ->
                        obj {
                            field("rung", rung.name)
                            field("state", rung.state)
                            field("why", rung.why)
                        }
                    },
                )
                field("compare", ladder.compare)
                if (ladder.item) field("item", true)
                field("same", ladder.same)
                field("duration", ladder.duration)
                field("wrong", ladder.wrong)
                field("unknown", ladder.unknown)
            }
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
        if (ask.repeat) field("repeat", true)
        if (ask.variant) field("variant", true)
        if (ask.afterStop) field("afterStop", true)
        if (ask.rateLimited) field("rateLimited", true)
    }

    /** 0.4: [millis] as seconds with one decimal ("31.5"), a JSON number. */
    fun decimalSeconds(millis: Long): String = String.format(Locale.US, "%.1f", millis.coerceAtLeast(0) / 1000.0)

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
                    "Master ${number.masterRecovered}" + (site.stop?.let { ", stopped ($it)" } ?: "") +
                    (site.incomplete?.let { ", $it" } ?: ""))
        }

    /** The report; 0.4 (b): [diff], this run against the last one, after the rules. */
    fun text(run: CensusRunResult, diff: SiteCensusDiff.Result? = null): String = buildString {
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
            "Order per site: every slot's main and V, then route B, then the trials (after the site's " +
                "final answers, then after main's rows); a 429 or a bot check in main's phase stops the site, " +
                "in Master's only Master's parts (30 min).",
        )
        appendLine(
            "VPN ${if (run.vpn) "on" else "off"} (Android's network); the diff: each site against its own " +
                "baseline (VPN, session), the last run whose main phase completed.",
        )
        appendLine(
            "V: one byte of one row per answer, with the download's headers, on the site's media " +
                "hosts only (HLS or another host: unknown).",
        )
        appendLine("The hidden page's own loads are neither counted nor listed.")
        appendLine(
            "Asks sent again: a repeat is the same ask (method, address, body, signed in or not, agent); " +
                "a variant, the same method and address with another body or identity.",
        )
        appendLine(
            "Times: main's lookup, the row's setup (main's identity of the link) and the whole row; " +
                "best: the best row's duration; item N of M: X's /video/N place of the post's videos, " +
                "Instagram's item of the post's items.",
        )
        if (run.sites.any { it.ladder }) {
            appendLine(
                "Ladder (a social-ladder build): Master's part is the site's ladder (its rungs on main's pages from this " +
                    "census's own lookup, S6's file check, S7's stops and budgets) instead of the R8 way; a wrong row is " +
                    "another post's (another video or item, a duration more than 1 s from every one main's rows state, or a " +
                    "row where the slot wants none), an unknown row is proven neither way; no wrong row is not parity or " +
                    "release readiness.",
            )
        }
        if (run.notes.isNotEmpty()) appendLine("Notes: ${run.notes.joinToString("; ")}")
        diff?.let {
            appendLine()
            appendLine("Diff: ${it.headline}")
            it.lines.forEach { line -> appendLine("  ${line.render()}") }
            it.notes.forEach { note -> appendLine("  note: $note") }
        }
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
                (result.stop?.let { "; stopped: $it" + if (result.cooldownFrom != null) " (30 min)" else "" } ?: "") +
                (result.incomplete?.let { "; $it" + if (result.masterCooldownFrom != null) " (30 min)" else "" } ?: ""),
        )
        appendLine(
            "  also: browser ${number.browser}, final ${number.final}, time limit ${number.timeLimit}; " +
                "not as expected ${number.notAsExpected}, ambiguous ${number.ambiguous}; main's V pass ${number.mainPass}, " +
                "fail ${number.mainFail}, unknown ${number.mainUnknown}; route B ${number.routeB}; " +
                "trials ${number.trialCount} (rows ${number.trialRows}, V pass ${number.trialPass})",
        )
        if (result.ladder) {
            val same = if (result.site.id == XLadder.SITE_ID || result.site.id == InstagramLadder.SITE_ID) "item" else "video"
            appendLine(
                "  ladder: Master parts ${number.ladderParts}, rows ${number.ladderRows}, the same $same ${number.ladderSame}, " +
                    "V pass ${number.ladderPass}, wrong rows ${number.ladderWrong} (unknown ${number.ladderUnknown})",
            )
        }
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
            "rows" -> "rows ${main.rows}" + rowsNote(main.tallest, main.bestMillis, main.item)
            "failed" -> "failed ${main.reason?.name}" + (main.statusCode?.let { " $it" } ?: "")
            else -> main.answer
        }
        val v = row.v?.let { ", V ${checked(it)}" } ?: row.vSkipped?.let { ", V not run ($it)" } ?: ""
        val verdict = when (row.expected) {
            true -> "as expected"
            null -> row.note ?: SiteCensus.AMBIGUOUS
            false -> "not as expected (${row.note})"
        }
        appendLine(
            "$head: $answer [${row.rowClass?.label}]$v — $verdict; requests ${row.requests}, " +
                "${main.millis} ms (setup ${row.setupMillis} ms, row ${row.millis} ms)",
        )
        val asks = main.asks.joinToString(" · ") { ask(it) }.ifEmpty { "no ask" }
        appendLine("    main: $asks")
        val extra = listOfNotNull(
            main.hiddenPage?.let { "hidden page: ${it.label}" },
            main.notReached.takeIf { it.isNotEmpty() }?.let { "not reached: ${it.joinToString(", ")}" },
            main.repeats.takeIf { it > 0 }?.let { "repeats $it" },
            main.variants.takeIf { it > 0 }?.let { "variants $it" },
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
        val run = master.run.label + (if (master.ladder != null) " (ladder)" else "") + (master.why?.let { " after $it" } ?: "")
        val answer = when (master.answer) {
            "rows" -> "rows ${master.rows}" + rowsNote(master.tallest, master.bestMillis, null)
            "failed", "skipped" -> "${master.answer} ${master.reason?.name}"
            else -> master.answer
        }
        val v = master.v?.let { ", V ${checked(it)}" } ?: master.vSkipped?.let { ", V not run ($it)" } ?: ""
        appendLine(
            "    Master $run: $answer$v; replayed ${master.replayed}, new ${master.newRequests}, " +
                "refused ${master.refused}, checks ${master.checks}, ${master.millis} ms",
        )
        if (master.asks.isNotEmpty()) appendLine("      ${master.asks.joinToString(" · ") { ask(it) }}")
        master.ladder?.let { appendLine("      ${it.text(master.rows)}") }
    }

    private fun checked(checked: SiteRowCheck.Checked): String =
        checked.verdict.label + listOfNotNull(checked.statusCode?.toString(), checked.why)
            .joinToString(" ").let { if (it.isEmpty()) "" else " $it" }

    /** " (tallest 1280; best 31.5 s; item 2 of 2)", each part when known; "" when none is. */
    private fun rowsNote(tallest: Int?, bestMillis: Long?, item: PostItem?): String = listOfNotNull(
        tallest?.let { "tallest $it" },
        bestMillis?.let { "best ${SiteCensusJson.decimalSeconds(it)} s" },
        item?.let { "item ${it.number} of ${it.count}" },
    ).joinToString("; ").let { if (it.isEmpty()) "" else " ($it)" }

    private fun ask(ask: LookupRecord.Ask): String {
        val answer = ask.statusCode?.toString() ?: ask.failure?.name ?: if (ask.answered) "answered" else "no answer"
        val flags = listOfNotNull(
            "authorized".takeIf { ask.authorized },
            "repeat".takeIf { ask.repeat },
            "variant".takeIf { ask.variant },
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
 * a 429 or a bot check ([stops]: until when, why), the last run's time and its export ([lastJson]
 * to write). 0.4 (c), format 2: each site's cooldown after a Master-phase stop ([masterStops]:
 * only its Master parts wait) and its [baselines], one per site, VPN and session: the last run
 * whose (i) completed, pass or fail (its part of the export and each slot's link fingerprint,
 * local only). Format 1's cooldowns (and its last run's time) are read; its export is no baseline.
 * Task 8: the weekly check W's own baselines ([weekly], keyed alike; written only once W ran, so a
 * census-only state is as before).
 */
internal class SiteCensusState(
    val stops: Map<String, Stop>,
    val lastAtMillis: Long = 0,
    val lastJson: String? = null,
    val masterStops: Map<String, Stop> = emptyMap(),
    val baselines: Map<Key, Baseline> = emptyMap(),
    val weekly: Map<Key, Baseline> = emptyMap(),
) {
    class Stop(val untilMillis: Long, val why: String)

    /** 0.4 (c): a baseline's key: the site, the VPN (on or off, as Android read it) and the session. */
    data class Key(val site: String, val vpn: Boolean, val session: Boolean) {
        val label: String get() = "VPN ${if (vpn) "on" else "off"}, signed ${if (session) "in" else "out"}"
    }

    /** 0.4 (c): a site's baseline: its run's time and date, each slot's link fingerprint, the site's part of the export. */
    class Baseline(val atMillis: Long, val date: String?, val links: Map<Int, String>, val site: JsonValue)

    companion object {
        const val FORMAT = 2
        private const val FORMAT_1 = 1L
        private const val MAX_STATE_NODES = 400_000
        private val WHY = setOf(SiteCensus.STOP_429, SiteCensus.STOP_BOT_CHECK, SiteCensus.STOP_RATE_LIMIT)
        private val FINGERPRINT = Regex("[0-9a-f]{16}")

        /** The stored state; an empty one when [text] is missing or unreadable. */
        fun read(text: String?): SiteCensusState {
            val empty = SiteCensusState(emptyMap())
            if (text.isNullOrBlank()) return empty
            val root = BoundedJsonParser.parse(text, maxDepth = 32, maxNodes = MAX_STATE_NODES) ?: return empty
            val format = root["fmt"].asLongOrNull
            if (format != FORMAT.toLong() && format != FORMAT_1) return empty
            val stops = stopsOf(root["stops"])
            val lastAt = root["lastAt"].asLongOrNull ?: 0
            if (format == FORMAT_1) return SiteCensusState(stops, lastAt)
            return SiteCensusState(
                stops,
                lastAt,
                masterStops = stopsOf(root["masterStops"]),
                baselines = baselinesOf(root["baselines"]),
                weekly = baselinesOf(root["weekly"]),
            )
        }

        fun write(state: SiteCensusState): String = SiteCensusJson.obj {
            field("fmt", FORMAT)
            raw("stops", stopsText(state.stops))
            raw("masterStops", stopsText(state.masterStops))
            field("lastAt", state.lastAtMillis)
            list("baselines", state.baselines.map { (key, baseline) -> entry(key, baseline) })
            // Task 8: W's baselines, only once W ran (a census-only state as before).
            if (state.weekly.isNotEmpty()) list("weekly", state.weekly.map { (key, baseline) -> entry(key, baseline) })
            state.lastJson?.let { raw("last", it) }
        }

        private fun entry(key: Key, baseline: Baseline): String = SiteCensusJson.obj {
            field("site", key.site)
            field("vpn", key.vpn)
            field("session", key.session)
            field("at", baseline.atMillis)
            field("date", baseline.date)
            raw("links", SiteCensusJson.obj { baseline.links.toSortedMap().forEach { (slot, print) -> field("$slot", print) } })
            raw("export", text(baseline.site))
        }

        private fun stopsOf(value: JsonValue?): Map<String, Stop> =
            (value as? JsonValue.Object)?.entries.orEmpty().mapNotNull { (id, stop) ->
                val until = stop["until"].asLongOrNull ?: return@mapNotNull null
                if (SiteWays.site(id) == null) return@mapNotNull null
                id to Stop(until, stop["why"].asStringOrNull?.takeIf { it in WHY } ?: "stop")
            }.toMap()

        private fun stopsText(stops: Map<String, Stop>): String = SiteCensusJson.obj {
            stops.forEach { (id, stop) ->
                raw(id, SiteCensusJson.obj {
                    field("until", stop.untilMillis)
                    field("why", stop.why)
                })
            }
        }

        private fun baselinesOf(value: JsonValue?): Map<Key, Baseline> = value.asArrayOrEmpty.mapNotNull { entry ->
            val id = entry["site"].asStringOrNull?.takeIf { SiteWays.site(it) != null } ?: return@mapNotNull null
            val vpn = entry["vpn"].asBooleanOrNull ?: return@mapNotNull null
            val session = entry["session"].asBooleanOrNull ?: return@mapNotNull null
            val site = (entry["export"] as? JsonValue.Object)?.takeIf { it["site"].asStringOrNull == id } ?: return@mapNotNull null
            val links = (entry["links"] as? JsonValue.Object)?.entries.orEmpty().mapNotNull { (slot, print) ->
                val number = slot.toIntOrNull() ?: return@mapNotNull null
                number to (print.asStringOrNull?.takeIf(FINGERPRINT::matches) ?: return@mapNotNull null)
            }.toMap()
            Key(id, vpn, session) to Baseline(entry["at"].asLongOrNull ?: 0, entry["date"].asStringOrNull, links, site)
        }.toMap()

        /** [value] as JSON text again (a kept baseline's part of its export). */
        private fun text(value: JsonValue): String = when (value) {
            JsonValue.Null -> "null"
            is JsonValue.Bool -> value.value.toString()
            is JsonValue.Number -> value.text
            is JsonValue.Text -> CensusJson.quote(value.value)
            is JsonValue.Array -> value.items.joinToString(",", "[", "]") { text(it) }
            is JsonValue.Object -> value.entries.entries.joinToString(",", "{", "}") { (key, item) ->
                CensusJson.quote(key) + ":" + text(item)
            }
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
