package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus
import java.util.Locale

/**
 * Social plan S3 (task 8, `MASTER_KEY_SOCIAL_PLAN.md` §7.5): the site census's weekly check W —
 * YouTube's W ([ClientCensus.Mode.WEEKLY]: a few reference videos, each way once, at most 60 s and
 * 2 MB, a request cap, and `CensusDiff`'s lines with the runbook beside them) for the four sites,
 * run by the census's own steps (`SiteCensus.run` with [ClientCensus.Mode.WEEKLY]); debug only
 * (the census screen's "Weekly check W" button, in a social-ladder build only, so Master's part is
 * the site's ladder), on the owner's tap. It schedules nothing.
 *
 * - **Slots** ([SLOTS]): three of the owner's census slots per site, by case kind (never a link):
 *   [MASTER_SLOT], a public video (main's rows, V, Master's part), and two cases main answers
 *   without rows or by its item or link rule. A W slot the owner left empty is not run (a note).
 * - **Each way once:** main's lookup once per W slot (no retry), V once on main's best row, Master's
 *   part on [MASTER_SLOT] only (the site's way once; each rung asks at most once, S7; the other
 *   slots: [MAIN_ONLY]), V once on Master's best row. Instagram's tab-fetch probe is never part of
 *   the census.
 * - **Caps per site** ([Caps]): [MAX_MILLIS] (YouTube's W's 60 s), [MAX_BYTES] (its 2 MB) and
 *   [MAX_REQUESTS] (sized from B0: about 5 requests per slot with V on Facebook, the most asking
 *   site, so 3 slots and one Master part fit with room; TikTok, X and Instagram ask fewer). Each is
 *   checked before every step (main's lookup, V, Master's part), as YouTube's W checks them before
 *   every request; the time left also bounds each step, so a site never runs past [MAX_MILLIS] (a
 *   step it cuts is not run: [TIME]). A step that starts under the request and byte caps finishes,
 *   so a site can pass them by that one step's asks; the report prints each site's spend. A cap
 *   reached stops the site's remaining steps ([REQUESTS], [BYTES], [TIME]: "not run"), with no
 *   cooldown.
 * - **Stops and cooldowns:** the census's own (a 429 or a bot check stops the site and cools it 30
 *   minutes; a cooling site is skipped): "not run", never waited out, never counted as passed.
 * - **Baselines and the diff:** W's own baselines (per site, VPN and session, as the census keys
 *   them; apart from the full census's), and `SiteCensusDiff`'s lines one per change, each naming
 *   its runbook [Step].
 * - **Walled runs** (task 15's F5, [walled]): a site whose every ask got its login wall (the login
 *   page, or a login status) read what this network's address gets, not what the app does: its
 *   lines stay (BREAKING against a baseline with data), one note says so ([WALLED]), and the run is
 *   never the site's baseline; a stored baseline that was walled (an earlier APK kept it) is set
 *   aside, so the next run starts it. A partly walled run is as before.
 */
object SiteWeekly {
    /**
     * W's slots per site, [MASTER_SLOT] first: Facebook a video, a share link (main resolves it
     * first), removed; TikTok a single video, a photo post, removed; Instagram a single video,
     * photo-only, removed; X a video, several videos (the item), removed.
     */
    val SLOTS: Map<String, List<Int>> = linkedMapOf(
        "facebook" to listOf(1, 8, 13),
        "tiktok" to listOf(1, 6, 9),
        "instagram" to listOf(1, 8, 10),
        "x" to listOf(1, 6, 8),
    )

    const val MASTER_SLOT = 1
    const val MAX_MILLIS: Long = ClientCensus.WEEKLY_MAX_MILLIS
    const val MAX_BYTES: Long = ClientCensus.WEEKLY_MAX_BYTES
    const val MAX_REQUESTS: Int = 24

    /** W's own stops (a cap reached; no cooldown). */
    const val REQUESTS = "requests"
    const val BYTES = "bytes"
    const val TIME = "time"

    /** Why a W slot other than [MASTER_SLOT] has no Master part. */
    const val MAIN_ONLY = "W: main only"

    /** A site whose W slots the owner left empty. */
    const val NO_LINKS = "no link in W's slots"

    /** Task 15 (F5): the note of a site whose every ask in a W run got its login wall ([walled]; §7.5). */
    const val WALLED = "every ask walled: likely this network's address, §7.5"

    /** One site's caps in a W run (tests size them smaller). */
    class Caps(val requests: Int = MAX_REQUESTS, val bytes: Long = MAX_BYTES, val millis: Long = MAX_MILLIS) {
        override fun toString(): String = "SiteWeekly.Caps($requests requests, $bytes bytes, $millis ms)"
    }

    /** The runbook's steps (§7.5): one per kind of W's diff lines. */
    enum class Step(val number: Int, val what: String) {
        SITE_STOP(1, "the site's stop"),
        MAIN(2, "main's answer"),
        VERDICT(3, "the slot's verdict"),
        FILE_CHECK(4, "V"),
        POST(5, "the item or the length"),
        MASTER(6, "Master's part"),
        WRONG_ROWS(7, "Master's wrong rows"),
        CONDITIONS(8, "the run's conditions"),
        ;

        val label: String get() = "W runbook step $number ($what)"
    }

    /** [site]'s W slots of [links] (the owner's slot → link). */
    fun linksOf(site: Site, links: Map<Int, String>): Map<Int, String> {
        val slots = SLOTS[site.id].orEmpty()
        return links.filterKeys { it in slots }
    }

    /** A note for each W slot of [site] the owner left empty. */
    fun missing(site: Site, links: Map<Int, String>): List<String> = SLOTS[site.id].orEmpty()
        .filter { slot -> links[slot].isNullOrBlank() }
        .map { slot -> "W slot $slot (${site.kind(slot)?.label ?: "?"}): no link, not run" }

    /**
     * Task 15 (F5): whether every ask of [site] (its part of a W export, or a stored baseline's)
     * got the site's login wall: at least one ask, main's and Master's on every slot looked up, each
     * the login page (its reading) or a login status (401, 403: `LOGIN_REQUIRED`), and no rows read
     * (main's or Master's). One answer of another kind (data, a removed post's word, a 429, no
     * answer) and the run is not walled.
     */
    internal fun walled(site: JsonValue): Boolean {
        val rows = site["rows"].asArrayOrEmpty.filter { it["skipped"].asStringOrNull == null }
        val asks = rows.flatMap { it["main"]["asks"].asArrayOrEmpty + it["master"]["asks"].asArrayOrEmpty }
        return asks.isNotEmpty() && asks.all(::walledAsk) &&
            rows.none { it["main"]["answer"].asStringOrNull == ROWS || it["master"]["answer"].asStringOrNull == ROWS }
    }

    private fun walledAsk(ask: JsonValue): Boolean =
        ask["mark"].asStringOrNull == AnswerMark.LOGIN_PAGE.label ||
            ask["failure"].asStringOrNull == SiteExtractionFailure.LOGIN_REQUIRED.name

    private const val ROWS = "rows"

    /** W's first line: its diff's headline, then each site not run or stopped, and why. */
    internal fun headline(run: CensusRunResult, diff: SiteCensusDiff.Result): String {
        val sites = run.sites.mapNotNull { site ->
            when {
                site.skipped != null -> "${site.site.name} not run (${site.skipped})"
                site.stop != null -> "${site.site.name} stopped (${site.stop}), the rest not run"
                else -> null
            }
        }
        return "W (weekly check): ${diff.headline}" + sites.joinToString("") { " · $it" }
    }

    /** W's lines before the census's report: its caps and slots, then each site's spend or why it did not run. */
    internal fun header(run: CensusRunResult, caps: Caps): String = buildString {
        appendLine(
            "Weekly check W (S3): per site at most ${caps.millis / 1000} s, ${mb(caps.bytes)} and ${caps.requests} " +
                "requests, each checked before every step (the time bounds each step too); each way once; " +
                "Master's part on slot $MASTER_SLOT only.",
        )
        appendLine(
            "W's slots: " + SLOTS.entries.joinToString(" · ") { (id, slots) ->
                "${SiteWays.site(id)?.name ?: id} ${slots.joinToString(", ")}"
            },
        )
        for (site in run.sites) {
            val state = site.skipped?.let { "not run ($it)" }
                ?: ((site.stop?.let { "stopped ($it): the rest not run" } ?: "ran") +
                    ", ${site.requests} requests, ${if (site.bytes < 0) "bytes not measured" else mb(site.bytes)}, " +
                    "${(site.millis + 500) / 1000} s")
            appendLine("${site.site.name}: $state")
        }
        appendLine(
            "A part not run is never a pass; each CHANGED or BREAKING line names its W runbook step " +
                "(MASTER_KEY_SOCIAL_PLAN.md §7.5).",
        )
        appendLine()
    }

    private fun mb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
