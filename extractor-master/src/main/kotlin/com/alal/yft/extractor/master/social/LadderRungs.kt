package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.MasterResult

/**
 * Task 6b (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the site census's reading of a ladder's rungs from
 * Master's answer lines — which rung ran, which gave rows, which was not asked and why. The
 * rungs: Facebook's F1 (the plugin player), F2 (main's pages) and F3 (the visible tab's own
 * playback); TikTok's T1 (the embed player), T2 (main's pages) and T3; X's X1 (main's answer) and
 * X3; Instagram's I2 (main's answers) and I3. The census has no tab, so F3, T3, X3 and I3 never run:
 * "not run (no tab)" when the engine got that far. Instagram's tab-fetch rung (decision 24, task 13)
 * needs the tab too: it is never asked in a census (no probe in its context) and is not listed.
 *
 * A rung's state, from its own lines (its label's, [FacebookLadder.F1] …):
 *
 * - **rows** — judged `next: CHECK_ROWS`: its rows went to the engine's file check (S6);
 * - **rows not checked (why)** — judged so, but over a budget (S7), not checked;
 * - **run (CLASS)** — judged otherwise (the answer's class; or `next: NEXT` when the line names
 *   no class), or **run (no answer in time)**, or **run** (lines without a judgment);
 * - **not run (why)** — not asked: `stop` (a host stopped in this lookup), `cooldown` (a host
 *   still cooling after a 429 or a bot check), `request budget`, `time budget`, `byte budget`;
 *   `nothing to read` (main read nothing for it); `no video` (a link main resolves first, and
 *   main's answer named no video); `final` (TikTok's photo post on main's pages); `no tab`;
 * - **not reached** — no line: an earlier rung answered, or the ladder did not claim the lookup;
 * - **unknown (time limit | error)** — the engine's bounded lookup timed out (its lines are
 *   gone) or the part threw.
 *
 * Words and classes only, from the ladders' own fixed lines: never a link or an ID.
 */
internal object LadderRungs {
    /** One rung's [state] in a Master part ([why]: the class, the next step or the reason). */
    class Rung(val name: String, val state: String, val why: String? = null) {
        fun text(): String = "$name $state" + (why?.let { " ($it)" } ?: "")

        override fun toString(): String = "Rung(${text()})"
    }

    const val ROWS = "rows"
    const val NOT_CHECKED = "rows not checked"
    const val RUN = "run"
    const val NOT_RUN = "not run"
    const val NOT_REACHED = "not reached"
    const val UNKNOWN = "unknown"

    /** The engine's line when it reached the visible tab's own playback (the census has none). */
    private const val CAPTURE = "master: one browser-assisted capture requested"
    private const val TIMED_OUT = "master: bounded lookup timed out"
    private const val NOT_ASKED = "; not asked"
    private const val NOT_CHECKED_LINE = "; not checked"
    private const val NO_ANSWER = "no answer in time"
    private const val NOTHING = "nothing to read"
    private const val CHECK_ROWS = "CHECK_ROWS"
    private const val PHOTO_ONLY = "PHOTO_ONLY"
    private val JUDGED = Regex("""^([A-Z][A-Z_]*) \(.*\); next: ([A-Z_]+)$""")
    private val NEXT = Regex("""(?:^|; )next: ([A-Z_]+)$""")
    private val REFUSED = Regex("""; refused (\d+)$""")

    /** A site's ladder: its line ([line]) and its rungs, each with its label (null: the tab's playback). */
    private class Ladder(val line: String, val rungs: List<Pair<String, String?>>)

    private val LADDERS = mapOf(
        FacebookLadder.SITE_ID to Ladder(FacebookLadder.LINE, listOf("F1" to FacebookLadder.F1, "F2" to FacebookLadder.F2, "F3" to null)),
        TikTokLadder.SITE_ID to Ladder(TikTokLadder.LINE, listOf("T1" to TikTokLadder.T1, "T2" to TikTokLadder.T2, "T3" to null)),
        XLadder.SITE_ID to Ladder(XLadder.LINE, listOf("X1" to XLadder.X1, "X3" to null)),
        InstagramLadder.SITE_ID to Ladder(InstagramLadder.LINE, listOf("I2" to InstagramLadder.I2, "I3" to null)),
    )

    /** The sites with a ladder. */
    val SITES: Set<String> get() = LADDERS.keys

    /** [site]'s rungs in Master's answer [result] (null: the part threw); null for a site without a ladder. */
    fun of(site: String, result: MasterResult?): List<Rung>? {
        val ladder = LADDERS[site] ?: return null
        val details = detailsOf(result) ?: return ladder.rungs.map { (name, _) -> Rung(name, UNKNOWN, "error") }
        val lines = details.map(String::trim)
        if (TIMED_OUT in lines) return ladder.rungs.map { (name, _) -> Rung(name, UNKNOWN, "time limit") }
        return ladder.rungs.mapIndexed { index, (name, label) ->
            when {
                label == null -> if (CAPTURE in lines) Rung(name, NOT_RUN, "no tab") else Rung(name, NOT_REACHED)
                else -> stateOf(
                    name,
                    lines.filter { it.startsWith("$label:") || it.startsWith("$label ") }.map { it.removePrefix(label).removePrefix(":").trim() },
                    if (index == 0) lines.firstOrNull { it.startsWith("${ladder.line}: ") } else null,
                )
            }
        }
    }

    /** The asks route B refused in [site]'s ladder lines of [result] (`…; refused K`). */
    fun refusedOf(site: String, result: MasterResult?): Int {
        val ladder = LADDERS[site] ?: return 0
        return detailsOf(result).orEmpty().map(String::trim).filter { it.startsWith("${ladder.line} after ") }
            .sumOf { line -> REFUSED.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
    }

    private fun detailsOf(result: MasterResult?): List<String>? = when (result) {
        null -> null
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        is MasterResult.NeedsSelection -> result.details
        is MasterResult.Skipped -> emptyList()
    }

    /** One rung's state from its own lines' words ([own], its label taken off) and the ladder's [head] line. */
    private fun stateOf(name: String, own: List<String>, head: String?): Rung {
        if (own.isEmpty()) {
            return when {
                head == null -> Rung(name, NOT_REACHED)
                PHOTO_ONLY in head -> Rung(name, NOT_RUN, "final")
                else -> Rung(name, NOT_RUN, "no video")
            }
        }
        own.firstOrNull { it.endsWith(NOT_ASKED) }?.let { return Rung(name, NOT_RUN, whyOf(it.removeSuffix(NOT_ASKED))) }
        own.firstOrNull { it.endsWith(NOT_CHECKED_LINE) }?.let { return Rung(name, NOT_CHECKED, whyOf(it.removeSuffix(NOT_CHECKED_LINE))) }
        own.lastOrNull { NEXT.containsMatchIn(it) }?.let { line ->
            val next = NEXT.find(line)?.groupValues?.get(1)
            if (next == CHECK_ROWS) return Rung(name, ROWS)
            return Rung(name, RUN, JUDGED.find(line)?.groupValues?.get(1) ?: next)
        }
        if (own.any { it.endsWith(NO_ANSWER) }) return Rung(name, RUN, NO_ANSWER)
        if (own.any { it.endsWith(NOTHING) }) return Rung(name, NOT_RUN, NOTHING)
        return Rung(name, RUN)
    }

    /** The short word for a rung's reason [why] it was not asked (or its rows not checked). */
    private fun whyOf(why: String): String = when {
        "cooling" in why -> "cooldown"
        "stopped" in why -> "stop"
        "request budget" in why -> "request budget"
        "time budget" in why -> "time budget"
        "byte budget" in why -> "byte budget"
        else -> "other"
    }
}
