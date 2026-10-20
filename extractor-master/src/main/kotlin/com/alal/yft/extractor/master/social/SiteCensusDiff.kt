package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
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
 */
internal object SiteCensusDiff {
    enum class Level { CHANGED, BREAKING }

    data class Line(val level: Level, val text: String) {
        fun render(): String = "${level.name} $text"
    }

    /** [lines]: the changes the headline counts; [notes]: what was not compared and why. */
    data class Result(val headline: String, val lines: List<Line>, val notes: List<String>) {
        val breaking: Int get() = lines.count { it.level == Level.BREAKING }

        fun text(): String = (listOf(headline) + lines.map(Line::render) + notes.map { "note: $it" }).joinToString("\n")
    }

    private const val MAX_NODES = 400_000
    private const val EARLIER = "earlier"
    private const val LATER = "later"
    private const val PASS = "pass"
    private const val FAIL = "fail"
    private val SITE_STOPS = setOf(SiteCensus.STOP_429, SiteCensus.STOP_BOT_CHECK, SiteCensus.STOP_RATE_LIMIT)

    /** [before] and [after] as export texts (two files); [before] null: no earlier run. */
    fun compare(before: String?, after: String): Result {
        val earlier = before?.let { text -> parse(text) ?: return notCompared(EARLIER) }
        return compare(earlier, parse(after))
    }

    /** [before] and [after] as parsed exports; [before] null: no earlier run. */
    fun compare(before: JsonValue?, after: JsonValue?): Result {
        if (after == null || !isExport(after)) return notCompared(LATER)
        if (before == null) return Result("FIRST RUN — nothing to compare yet", emptyList(), emptyList())
        if (!isExport(before)) return notCompared(EARLIER)
        val lines = ArrayList<Line>()
        val notes = ArrayList<String>()
        val earlier = sitesOf(before)
        val later = sitesOf(after)
        for ((id, site) in later) {
            val prior = earlier[id]
            if (prior == null) {
                notes += "${site.name}: only in the later run"
                continue
            }
            compareSite(prior, site, lines, notes)
        }
        earlier.filterKeys { it !in later }.values.forEach { notes += "${it.name}: only in the earlier run" }
        val against = before["date"].asStringOrNull?.let { "against the run of $it" } ?: "against the earlier run"
        val headline = when {
            lines.any { it.level == Level.BREAKING } -> "BREAKING (${lines.size}) — $against"
            lines.isNotEmpty() -> "CHANGED (${lines.size}) — $against"
            else -> "NO CHANGE — $against"
        }
        return Result(headline, lines, notes)
    }

    private fun compareSite(before: SiteView, after: SiteView, lines: MutableList<Line>, notes: MutableList<String>) {
        val name = after.name
        if (before.skipped != null || after.skipped != null) {
            before.skipped?.let { notes += "$name: skipped in the earlier run ($it)" }
            after.skipped?.let { notes += "$name: skipped in the later run ($it)" }
            return
        }
        if (before.stop != after.stop) {
            when {
                after.stop == null -> lines += Line(Level.CHANGED, "$name: no stop (the earlier run stopped: ${before.stop})")
                after.stop in SITE_STOPS && before.stop !in SITE_STOPS ->
                    lines += Line(Level.BREAKING, "$name: stopped (${after.stop})" + (before.stop?.let { "; the earlier run: $it" } ?: ""))
                else -> lines += Line(Level.CHANGED, "$name: stopped (${after.stop})" + (before.stop?.let { "; the earlier run: $it" } ?: ""))
            }
        }
        for ((slot, row) in after.rows) {
            val prior = before.rows[slot]
            val label = "$name slot $slot (${row.kind ?: prior?.kind ?: "?"})"
            when {
                prior == null -> notes += "$label: only in the later run"
                prior.skipped != null || row.skipped != null -> {
                    prior.skipped?.let { notes += "$label: not looked up in the earlier run ($it)" }
                    row.skipped?.let { notes += "$label: not looked up in the later run ($it)" }
                }
                else -> compareRow(label, prior, row)?.let(lines::add)
            }
        }
        before.rows.filterKeys { it !in after.rows }.forEach { (slot, row) ->
            notes += "$name slot $slot (${row.kind ?: "?"}): only in the earlier run"
        }
    }

    /** One line for a slot both runs looked up, or null when nothing compared changed. */
    private fun compareRow(label: String, before: RowView, after: RowView): Line? {
        val changes = ArrayList<String>()
        var breaking = false
        if (before.kind != null && after.kind != null && before.kind != after.kind) {
            changes += "kind ${before.kind} → ${after.kind}"
        }
        if (before.session != after.session) changes += "session ${yesNo(before.session)} → ${yesNo(after.session)}"
        val mainBefore = before.mainText()
        val mainAfter = after.mainText()
        if (mainBefore != mainAfter) {
            changes += "main $mainBefore → $mainAfter"
            if (before.mainAnswer == ROWS && after.mainAnswer != ROWS) breaking = true
        }
        if (before.judge() != after.judge()) {
            changes += "${before.judge()} → ${after.judge()}"
            if (before.expected == true && after.expected == false) breaking = true
        }
        if (before.v != after.v) {
            changes += "V ${before.v ?: "not run"} → ${after.v ?: "not run"}"
            if (before.v == PASS && after.v == FAIL) breaking = true
        }
        if (before.item != null && after.item != null && before.item != after.item) {
            changes += "item ${before.item} → ${after.item}"
        }
        if (before.bestS != null && after.bestS != null && before.bestS != after.bestS) {
            changes += "best ${seconds(before.bestS)} → ${seconds(after.bestS)}"
        }
        val masterBefore = before.masterText()
        val masterAfter = after.masterText()
        if (masterBefore != masterAfter) {
            changes += "Master $masterBefore → $masterAfter"
            if (before.recovered && !after.recovered) breaking = true
        }
        if (changes.isEmpty()) return null
        return Line(if (breaking) Level.BREAKING else Level.CHANGED, "$label: ${changes.joinToString("; ")}")
    }

    /** An export's text, parsed (bounded); null when it is not JSON. */
    fun parse(text: String): JsonValue? = BoundedJsonParser.parse(text, maxDepth = 32, maxNodes = MAX_NODES)

    private fun notCompared(which: String) =
        Result("NOT COMPARED — the $which file is not a site census export", emptyList(), emptyList())

    private fun isExport(root: JsonValue?): Boolean =
        root is JsonValue.Object && root["tool"].asStringOrNull == SiteCensus.TOOL_LABEL &&
            root["fmt"].asLongOrNull == SiteCensusState.FORMAT.toLong()

    private fun sitesOf(root: JsonValue): Map<String, SiteView> = root["sites"].asArrayOrEmpty.mapNotNull { site ->
        val id = site["site"].asStringOrNull ?: return@mapNotNull null
        val rows = site["rows"].asArrayOrEmpty.mapNotNull { row ->
            val slot = row["slot"].asLongOrNull?.toInt() ?: return@mapNotNull null
            slot to RowView(row)
        }.toMap(sortedMapOf())
        id to SiteView(site["name"].asStringOrNull ?: id, site["skipped"].asStringOrNull, site["stop"].asStringOrNull, rows)
    }.toMap(LinkedHashMap())

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private fun seconds(value: Double): String = String.format(Locale.US, "%.1f s", value)

    private const val ROWS = "rows"

    private class SiteView(val name: String, val skipped: String?, val stop: String?, val rows: Map<Int, RowView>)

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
            } + (masterV?.let { ", V $it" } ?: "")
        }
    }
}
