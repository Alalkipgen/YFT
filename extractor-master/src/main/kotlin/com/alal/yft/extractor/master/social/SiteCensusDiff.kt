package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.lookup.AnswerMark
import java.util.Locale

/**
 * Social plan 0.4 (b) (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the site census diff. Two exports of
 * "YFT site census" (the earlier and the later run) → one line per slot that changed, per site:
 *
 * - **BREAKING** — a worsening: main's rows gone (rows → anything else), a row as expected that is
 *   no longer, main's V `pass` → `fail`, a row Master recovered (route B, V pass) that it no longer
 *   recovers, or a site stopped by the site (a 429, a bot check, its rate limit) where it was not.
 * - **CHANGED** — any other difference: main's answer, class, reason, rows or tallest row; the
 *   judge; V; the post's item ("item N of M"); the best row's duration; Master's way, answer, rows
 *   or V; the session; a stop gone, or the census's own budget.
 *
 * A slot is compared only when both runs looked it up; otherwise (a site or a slot in one run
 * only, a site or a row skipped) a note says so. A field an export lacks (an older export, before
 * 0.4) is not compared. Times and request counts move from run to run and are not compared. The
 * lines hold sites, slots, kinds, labels, reasons and numbers only, as the exports do.
 *
 * 0.4 (c) (§7.4 step 2): exports of format 1 and 2 are read. A change of the Master phase's stop
 * (`masterStop`) alone is a note, never CHANGED or BREAKING, and the headline adds "· trials
 * incomplete (why)" when the later run has one. A Master part that did not run because of that
 * stop (or its cooldown), or whose own ask, check or V got the 429 or the bot check, is not
 * compared (a note), route B's lost recovery too; main's rules are unchanged. Nothing compared →
 * "NOT COMPARED", never "NO CHANGE". The census's own diff ([compare] with baselines): each site
 * against its own baseline, a slot whose link changed reads "new link" (a note).
 *
 * Task 6b: a site whose Master parts were its ladder's in one run only (`ladder`: a social-ladder
 * build's census against another build's) has its Master parts not compared (a note); main's
 * are. Both runs the ladder's: Master's part also compares the ladder's comparison (the same
 * video or item, the duration, the wrong and the unknown rows), and a ladder part with no wrong
 * row that has one in the later run is BREAKING. The rungs' states are not compared (a cooling
 * host moves them from run to run). An export without these fields (an older one) reads as
 * before.
 *
 * Task 8 (S3, the weekly check W; `compare(..., weekly = true)`): the same rules, but one line per
 * change (a slot's main, judge, V, item or length, Master's part, kind or session; a site's
 * stop), each naming its W runbook step ([Line.step], [SiteWeekly.Step]: `→ W runbook step N
 * (…)`, §7.5). The census's lines (W off) are as before, byte for byte.
 */
internal object SiteCensusDiff {
    enum class Level { CHANGED, BREAKING }

    /** Task 8: [step], W's runbook step for the line (W's lines only; the census's have none). */
    data class Line(val level: Level, val text: String, val step: SiteWeekly.Step? = null) {
        fun render(): String = "${level.name} $text" + (step?.let { " → ${it.label}" } ?: "")
    }

    /** [lines]: the changes the headline counts; [notes]: what was not compared and why. */
    data class Result(val headline: String, val lines: List<Line>, val notes: List<String>) {
        val breaking: Int get() = lines.count { it.level == Level.BREAKING }

        fun text(): String = (listOf(headline) + lines.map(Line::render) + notes.map { "note: $it" }).joinToString("\n")
    }

    /**
     * 0.4 (c): one site's baseline as the census's state keeps it: [site], the site's part of that
     * run's export (format 2), [date], that run's; [newLinks], the slots whose link changed since.
     */
    class Baseline(val site: JsonValue, val date: String?, val newLinks: Set<Int> = emptySet())

    private const val MAX_NODES = 400_000
    private const val EARLIER = "earlier"
    private const val LATER = "later"
    private const val PASS = "pass"
    private const val FAIL = "fail"
    private val SITE_STOPS = setOf(SiteCensus.STOP_429, SiteCensus.STOP_BOT_CHECK, SiteCensus.STOP_RATE_LIMIT)
    private const val FIRST_RUN = "FIRST RUN — nothing to compare yet"

    /** The export formats the diff reads: 1 (before 0.4 (c): no Master-phase stop) and today's. */
    private const val FORMAT_1 = 1L

    /** [before] and [after] as export texts (two files); [before] null: no earlier run. */
    fun compare(before: String?, after: String): Result {
        val earlier = before?.let { text -> parse(text) ?: return notCompared(EARLIER) }
        return compare(earlier, parse(after))
    }

    /** [before] and [after] as parsed exports; [before] null: no earlier run. */
    fun compare(before: JsonValue?, after: JsonValue?): Result {
        if (after == null || !isExport(after)) return notCompared(LATER)
        val trials = incomplete(after)
        if (before == null) return Result(FIRST_RUN + trials, emptyList(), emptyList())
        if (!isExport(before)) return notCompared(EARLIER)
        val lines = ArrayList<Line>()
        val notes = ArrayList<String>()
        var compared = 0
        val earlier = sitesOf(before)
        val later = sitesOf(after)
        for ((id, site) in later) {
            val prior = earlier[id]
            if (prior == null) {
                notes += "${site.name}: only in the later run"
                continue
            }
            compared += compareSite(prior, site, lines, notes)
        }
        earlier.filterKeys { it !in later }.values.forEach { notes += "${it.name}: only in the earlier run" }
        val against = before["date"].asStringOrNull?.let { "against the run of $it" } ?: "against the earlier run"
        return Result(headline(lines, compared, against) + trials, lines, notes)
    }

    /**
     * 0.4 (c): the census's own diff: [after] (the run's export) site by site against [baselines]
     * (site ID → its baseline for the run's VPN and the site's session; a site without one is not
     * compared, the caller's [notes] say why). [firstRun]: the state holds no baseline at all.
     * Task 8: [weekly], W's diff (a line per change, each with its runbook step).
     */
    fun compare(
        after: JsonValue?,
        baselines: Map<String, Baseline>,
        notes: List<String>,
        firstRun: Boolean,
        weekly: Boolean = false,
    ): Result {
        if (after == null || !isExport(after)) return notCompared(LATER)
        val trials = incomplete(after)
        val lines = ArrayList<Line>()
        val siteNotes = ArrayList<String>()
        var compared = 0
        val dates = LinkedHashMap<String, String>()
        for ((id, site) in sitesOf(after)) {
            if (site.skipped != null) {
                siteNotes += "${site.name}: skipped in the later run (${site.skipped})"
                continue
            }
            val baseline = baselines[id] ?: continue
            val prior = viewOf(baseline.site, SiteCensusState.FORMAT.toLong())?.second ?: continue
            dates[site.name] = baseline.date ?: "?"
            compared += compareSite(prior, site, lines, siteNotes, baseline.newLinks, weekly)
        }
        siteNotes += notes
        if (dates.isEmpty()) {
            val headline = if (firstRun) FIRST_RUN else "NOT COMPARED — no baseline yet for this run's sites"
            return Result(headline + trials, emptyList(), siteNotes)
        }
        val against = dates.values.distinct().singleOrNull()?.let { "against the run of $it" }
            ?: dates.entries.joinToString("; ", "against each site's baseline (", ")") { (name, date) -> "$name: $date" }
        return Result(headline(lines, compared, against) + trials, lines, siteNotes)
    }

    /** The headline's verdict; 0.4 (c): no slot compared → NOT COMPARED, never NO CHANGE. */
    private fun headline(lines: List<Line>, compared: Int, against: String): String = when {
        lines.any { it.level == Level.BREAKING } -> "BREAKING (${lines.size}) — $against"
        lines.isNotEmpty() -> "CHANGED (${lines.size}) — $against"
        compared > 0 -> "NO CHANGE — $against"
        else -> "NOT COMPARED — no slot looked up in both runs, $against"
    }

    /** One site both runs hold; returns how many of its slots both runs looked up (0.4 (c)). */
    private fun compareSite(
        before: SiteView,
        after: SiteView,
        lines: MutableList<Line>,
        notes: MutableList<String>,
        newLinks: Set<Int> = emptySet(),
        weekly: Boolean = false,
    ): Int {
        val name = after.name
        if (before.skipped != null || after.skipped != null) {
            before.skipped?.let { notes += "$name: skipped in the earlier run ($it)" }
            after.skipped?.let { notes += "$name: skipped in the later run ($it)" }
            return 0
        }
        if (before.stop != after.stop) {
            val step = SiteWeekly.Step.SITE_STOP.takeIf { weekly }
            when {
                after.stop == null -> lines += Line(Level.CHANGED, "$name: no stop (the earlier run stopped: ${before.stop})", step)
                after.stop in SITE_STOPS && before.stop !in SITE_STOPS ->
                    lines += Line(Level.BREAKING, "$name: stopped (${after.stop})" + (before.stop?.let { "; the earlier run: $it" } ?: ""), step)
                else -> lines += Line(Level.CHANGED, "$name: stopped (${after.stop})" + (before.stop?.let { "; the earlier run: $it" } ?: ""), step)
            }
        }
        // 0.4 (c): the Master phase's stop alone is a note; an export without it (format 1) is not compared.
        if (before.format != FORMAT_1 && after.format != FORMAT_1 && before.masterStop != after.masterStop) {
            notes += "$name: " + when {
                before.masterStop == null -> "trials incomplete in the later run (${after.masterStop})"
                after.masterStop == null -> "trials incomplete in the earlier run (${before.masterStop})"
                else -> "trials incomplete (${before.masterStop} → ${after.masterStop})"
            }
        }
        // Task 6b: the ladder's Master parts against another build's are not compared.
        val masters = before.ladder == after.ladder
        if (!masters) {
            notes += "$name: Master's parts not compared (the ladder in the ${if (after.ladder) "later" else "earlier"} run only)"
        }
        var compared = 0
        for ((slot, row) in after.rows) {
            val prior = before.rows[slot]
            val label = "$name slot $slot (${row.kind ?: prior?.kind ?: "?"})"
            when {
                prior == null -> notes += "$label: only in the later run"
                slot in newLinks -> notes += "$label: new link"
                prior.skipped != null || row.skipped != null -> {
                    prior.skipped?.let { notes += "$label: not looked up in the earlier run ($it)" }
                    row.skipped?.let { notes += "$label: not looked up in the later run ($it)" }
                }
                else -> {
                    compared++
                    lines += compareRow(label, prior, row, notes, masters, weekly)
                }
            }
        }
        before.rows.filterKeys { it !in after.rows }.forEach { (slot, row) ->
            notes += "$name slot $slot (${row.kind ?: "?"}): only in the earlier run"
        }
        return compared
    }

    /**
     * The line for a slot both runs looked up (none when nothing compared changed); task 8: in W
     * ([weekly]), a line per change, each with its runbook step.
     */
    private fun compareRow(
        label: String,
        before: RowView,
        after: RowView,
        notes: MutableList<String>,
        masters: Boolean = true,
        weekly: Boolean = false,
    ): List<Line> {
        val changes = ArrayList<Change>()
        if (before.kind != null && after.kind != null && before.kind != after.kind) {
            changes += Change("kind ${before.kind} → ${after.kind}", SiteWeekly.Step.CONDITIONS)
        }
        if (before.session != after.session) {
            changes += Change("session ${yesNo(before.session)} → ${yesNo(after.session)}", SiteWeekly.Step.CONDITIONS)
        }
        val mainBefore = before.mainText()
        val mainAfter = after.mainText()
        if (mainBefore != mainAfter) {
            changes += Change(
                "main $mainBefore → $mainAfter",
                SiteWeekly.Step.MAIN,
                breaking = before.mainAnswer == ROWS && after.mainAnswer != ROWS,
            )
        }
        if (before.judge() != after.judge()) {
            changes += Change(
                "${before.judge()} → ${after.judge()}",
                SiteWeekly.Step.VERDICT,
                breaking = before.expected == true && after.expected == false,
            )
        }
        if (before.v != after.v) {
            changes += Change(
                "V ${before.v ?: "not run"} → ${after.v ?: "not run"}",
                SiteWeekly.Step.FILE_CHECK,
                breaking = before.v == PASS && after.v == FAIL,
            )
        }
        if (before.item != null && after.item != null && before.item != after.item) {
            changes += Change("item ${before.item} → ${after.item}", SiteWeekly.Step.POST)
        }
        if (before.bestS != null && after.bestS != null && before.bestS != after.bestS) {
            changes += Change("best ${seconds(before.bestS)} → ${seconds(after.bestS)}", SiteWeekly.Step.POST)
        }
        // 0.4 (c): a Master part the Master phase's stop kept from running (or its cooldown),
        // or whose own ask, check or V met it, is not compared; route B's lost recovery too.
        // Task 6b: nor one of the ladder's against another build's (the site's note says so).
        if (masters && (before.masterStopped != null || after.masterStopped != null)) {
            notes += "$label: Master not compared (" + listOfNotNull(
                before.masterStopped?.let { "the earlier run: $it" },
                after.masterStopped?.let { "the later run: $it" },
            ).joinToString("; ") + ")"
        } else if (masters) {
            val masterBefore = before.masterText()
            val masterAfter = after.masterText()
            if (masterBefore != masterAfter) {
                // Task 6b: the ladder's rows proven another post's, where it had none.
                val wrong = before.wrong == 0L && after.wrong > 0
                changes += Change(
                    "Master $masterBefore → $masterAfter",
                    if (wrong) SiteWeekly.Step.WRONG_ROWS else SiteWeekly.Step.MASTER,
                    breaking = (before.recovered && !after.recovered) || wrong,
                )
            }
        }
        if (changes.isEmpty()) return emptyList()
        if (weekly) return changes.map { Line(if (it.breaking) Level.BREAKING else Level.CHANGED, "$label: ${it.text}", it.step) }
        val level = if (changes.any(Change::breaking)) Level.BREAKING else Level.CHANGED
        return listOf(Line(level, "$label: ${changes.joinToString("; ") { it.text }}"))
    }

    /** Task 8: one change of a slot, its W runbook step and whether it is a worsening. */
    private class Change(val text: String, val step: SiteWeekly.Step, val breaking: Boolean = false)

    /** An export's text, parsed (bounded); null when it is not JSON. */
    fun parse(text: String): JsonValue? = BoundedJsonParser.parse(text, maxDepth = 32, maxNodes = MAX_NODES)

    private fun notCompared(which: String) =
        Result("NOT COMPARED — the $which file is not a site census export", emptyList(), emptyList())

    private fun formatOf(root: JsonValue?): Long? = root["fmt"].asLongOrNull

    private fun isExport(root: JsonValue?): Boolean =
        root is JsonValue.Object && root["tool"].asStringOrNull == SiteCensus.TOOL_LABEL &&
            formatOf(root).let { it == FORMAT_1 || it == SiteCensusState.FORMAT.toLong() }

    /** 0.4 (c): " · trials incomplete (why)" when a site of [after] has a Master-phase stop, else "". */
    private fun incomplete(after: JsonValue): String {
        val words = sitesOf(after).values.mapNotNull(SiteView::masterStop).distinct()
        return if (words.isEmpty()) "" else " · trials incomplete (${words.joinToString(", ")})"
    }

    private fun sitesOf(root: JsonValue): Map<String, SiteView> {
        val format = formatOf(root)
        return root["sites"].asArrayOrEmpty.mapNotNull { viewOf(it, format) }.toMap(LinkedHashMap())
    }

    /** One site of an export of [format] (a baseline's: the state's), as its ID and view; null without an ID. */
    private fun viewOf(site: JsonValue, format: Long?): Pair<String, SiteView>? {
        val id = site["site"].asStringOrNull ?: return null
        val rows = site["rows"].asArrayOrEmpty.mapNotNull { row ->
            val slot = row["slot"].asLongOrNull?.toInt() ?: return@mapNotNull null
            slot to RowView(row)
        }.toMap(sortedMapOf())
        val masterStop = site["masterStop"].asStringOrNull ?: site["masterCooldown"].asStringOrNull?.let { SiteCensus.COOLDOWN }
        return id to SiteView(
            site["name"].asStringOrNull ?: id,
            site["skipped"].asStringOrNull,
            site["stop"].asStringOrNull,
            rows,
            format,
            masterStop,
            ladder = site["ladder"].asBooleanOrNull == true,
        )
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private fun seconds(value: Double): String = String.format(Locale.US, "%.1f s", value)

    private const val ROWS = "rows"

    /** 0.4 (c): [format], the export's; [masterStop], the Master phase's stop (or its cooldown). */
    private class SiteView(
        val name: String,
        val skipped: String?,
        val stop: String?,
        val rows: Map<Int, RowView>,
        val format: Long?,
        val masterStop: String?,
        val ladder: Boolean = false,
    )

    /** One row of an export, as the diff reads it; a missing field is null. */
    private class RowView(row: JsonValue) {
        val kind = row["kind"].asStringOrNull
        val session = row["session"].asBooleanOrNull == true
        val skipped = row["skipped"].asStringOrNull
        val rowClass = row["class"].asStringOrNull
        val expected = row["expected"].asBooleanOrNull
        val ambiguous = row["ambiguous"].asBooleanOrNull == true
        val note = row["note"].asStringOrNull
        private val main = row["main"]
        val mainAnswer = main["answer"].asStringOrNull
        private val reason = main["reason"].asStringOrNull
        private val status = main["status"].asLongOrNull
        private val rows = main["rows"].asLongOrNull
        private val tallest = main["tallest"].asLongOrNull
        val bestS = main["bestS"].asDoubleOrNull
        val item = main["item"].asLongOrNull?.let { number ->
            main["items"].asLongOrNull?.let { count -> "$number of $count" }
        }
        val v = row["v"]["verdict"].asStringOrNull
        private val master = row["master"]
        private val masterRun = master["run"].asStringOrNull
        private val masterWhy = master["why"].asStringOrNull
        private val masterAnswer = master["answer"].asStringOrNull
        private val masterReason = master["reason"].asStringOrNull
        private val masterRows = master["rows"].asLongOrNull
        private val masterV = master["v"]["verdict"].asStringOrNull

        /** Task 6b: the ladder's comparison (an older export, or a part without rows: none). */
        private val same = master["same"].asStringOrNull
        private val duration = master["duration"].asStringOrNull
        private val unknown = master["unknown"].asLongOrNull

        /** Task 6b: the ladder's rows proven another post's (0 without the field). */
        val wrong: Long = master["wrong"].asLongOrNull ?: 0
        private val ladderWrong = master["wrong"].asLongOrNull

        /**
         * 0.4 (c): the Master phase's stop this row's Master part met, or null: it did not run by
         * that stop or its cooldown (`stopped`), or its own ask or V got the 429 or the bot check.
         */
        val masterStopped: String? = master["stopped"].asStringOrNull
            ?: master["asks"].asArrayOrEmpty.firstNotNullOfOrNull { ask ->
                when {
                    ask["rateLimited"].asBooleanOrNull == true -> SiteCensus.STOP_429
                    ask["mark"].asStringOrNull == AnswerMark.BOT_CHECK.label -> SiteCensus.STOP_BOT_CHECK
                    else -> null
                }
            }
            ?: SiteCensus.STOP_429.takeIf { master["v"]["rateLimited"].asBooleanOrNull == true }

        /** Master recovered the row: route B rows that passed V. */
        val recovered: Boolean get() = masterRun == "route B" && masterV == PASS

        fun mainText(): String = when (mainAnswer) {
            ROWS -> "rows ${rows ?: "?"}" + (tallest?.let { " (tallest $it)" } ?: "")
            "failed" -> "failed ${reason ?: "?"}" + (status?.let { " $it" } ?: "")
            null -> "no answer"
            else -> mainAnswer
        } + (rowClass?.let { " [$it]" } ?: "")

        fun judge(): String = when {
            expected == true -> "as expected"
            expected == false -> "not as expected" + (note?.let { " ($it)" } ?: "")
            ambiguous || mainAnswer != null -> note ?: SiteCensus.AMBIGUOUS
            else -> "not judged"
        }

        fun masterText(): String = when (masterRun) {
            null -> "none"
            "not run" -> "not run" + (masterWhy?.let { " ($it)" } ?: "")
            else -> masterRun + ": " + when (masterAnswer) {
                ROWS -> "rows ${masterRows ?: "?"}"
                "failed", "skipped" -> "$masterAnswer ${masterReason ?: "?"}"
                null -> "no answer"
                else -> masterAnswer
            } + (masterV?.let { ", V $it" } ?: "") + ladderText()
        }

        /** Task 6b: `, same yes, duration yes, wrong 0 (unknown 0)`, the fields the part has; "" without. */
        private fun ladderText(): String = listOfNotNull(
            same?.let { "same $it" },
            duration?.let { "duration $it" },
            ladderWrong?.let { "wrong $it" + (unknown?.let { count -> " (unknown $count)" } ?: "") },
        ).joinToString(", ").let { if (it.isEmpty()) "" else ", $it" }
    }
}
