package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.MediaCandidate
import java.net.URI
import kotlin.math.abs

/**
 * Social plan S1a (9) and task 6b (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): Master's rows against main's
 * for one link — the app's trial line (`; trial: Master's rows M, main's N; the same video|item:
 * yes|no; duration within 1 s: yes|no|unknown`, the app's `SocialLadder.trialSegment`, word for
 * word) and the site census's ladder part (each of Master's rows judged on its own).
 *
 * The set, as the trial line says it: **the same video** when every one of Master's rows names
 * the one video main's rows name ([compare]'s `video`: the video a row's key names in the lookup;
 * a row without a key does not). X's rows (main's rows' key is X's) compare **the item** too
 * (decision 11): every row's file names the one item main's files name ([XLadder.itemOf]).
 * Instagram's (decision 19 (iv)): at least one of Master's rows shares a file (its file's path or
 * its audio's) with main's rows — Master's rows add the 360p files main drops, so not every row is
 * one of main's. **The duration**: every duration Master's rows state within 1 s of every one
 * main's rows state; unknown when either side states none. **The heights** (task 16, F6):
 * Facebook's line adds the heights each side's rows state (`; heights 720,360 vs 720,360`:
 * Master's, then main's; each side's distinct heights, tallest first, `not stated` when its rows
 * state none — L1, the retest of 2026-10-23: main's Facebook rows state no height when the line is
 * made, the sheet measuring them later, P25), so the retest checks each of Master's against main's
 * when both sides state theirs; Instagram's adds each side's tallest (`; tallest: Master's H,
 * main's H`).
 *
 * Task 6b, each row ([Verdict]), stricter than the set and never kinder:
 *
 * - **WRONG** — proven another post's: its key names a video none of main's rows name (main's
 *   one video, or each of several); on X its file names an item none of main's files name; or the
 *   duration it states is more than 1 s from every duration main's rows state.
 * - **UNKNOWN** — not proven either way: main's rows name several videos and the row one of them
 *   (or X's several items and the row one of them), the row has no key (or on X no item), or on
 *   Instagram it shares no file with main's rows (the 360p file main drops is not proof of another
 *   item, and not proof of the same).
 * - **MATCH** — the row's key (and X's item, Instagram's shared file) is main's.
 *
 * A missing duration is no mismatch; a matching duration alone proves nothing (an UNKNOWN row
 * stays UNKNOWN), and nothing turns a WRONG row UNKNOWN. Counts and words only: never a link or
 * an ID.
 */
object TrialCompare {
    /** The most two durations may differ and still count as the same, in milliseconds. */
    const val DURATION_SLACK_MILLIS: Long = 1_000L

    /** Task 6b: one of Master's rows against main's rows. */
    enum class Verdict { MATCH, WRONG, UNKNOWN }

    /**
     * Master's [masterRows] rows against main's [mainRows]: [item] for X's and Instagram's rows
     * ("the same item"), [same] and [duration] as the trial line says them, each side's tallest
     * height ([masterTallest], [mainTallest]: `unknown` when its rows state none), task 16's
     * heights for Facebook's line ([facebook]: [masterHeights], [mainHeights], `720,360` or
     * `not stated`, L1) and one [verdicts] entry per row of Master's, in order.
     */
    class Compared(
        val masterRows: Int,
        val mainRows: Int,
        val item: Boolean,
        val instagram: Boolean,
        val same: Boolean,
        val duration: String,
        val masterTallest: String,
        val mainTallest: String,
        val verdicts: List<Verdict>,
        val facebook: Boolean = false,
        val masterHeights: String = NOT_STATED,
        val mainHeights: String = NOT_STATED,
    ) {
        /** Rows proven another post's. */
        val wrong: Int get() = verdicts.count { it == Verdict.WRONG }

        /** Rows not proven either way. */
        val unknown: Int get() = verdicts.count { it == Verdict.UNKNOWN }

        /** `the same video: yes`, `the same item: no`, …. */
        val sameText: String get() = "the same ${if (item) "item" else "video"}: ${if (same) "yes" else "no"}"

        /** The trial line's words after `; trial: ` (the app's segment). */
        fun trialText(): String = "Master's rows $masterRows, main's $mainRows; $sameText; duration within 1 s: $duration" +
            when {
                instagram -> "; tallest: Master's $masterTallest, main's $mainTallest"
                facebook -> "; heights $masterHeights vs $mainHeights"
                else -> ""
            }
    }

    /**
     * Master's [rows] against main's [mains]; [video]: the video a row's key names in the lookup
     * (main's rows carry the link's own key, so a link main resolves first names the video its
     * resolved link names). The site is main's rows' key's.
     */
    fun compare(rows: List<MediaCandidate>, mains: List<MediaCandidate>, video: (String?) -> String?): Compared {
        val mainVideo = mains.map { video(it.videoId) }.distinct().singleOrNull()
        val sameVideo = mainVideo != null && rows.isNotEmpty() && rows.all { video(it.videoId) == mainVideo }
        val site = mains.firstOrNull()?.videoId?.substringBefore(':', missingDelimiterValue = "")
        val x = site == XLadder.SITE_ID
        val instagram = site == InstagramLadder.SITE_ID
        val facebook = site == FacebookLadder.SITE_ID
        val mainItem = mains.map { XLadder.itemOf(it.mediaUrl) }.distinct().singleOrNull()
        val mainPaths = pathsOf(mains)
        val same = sameVideo && when {
            x -> mainItem != null && rows.all { XLadder.itemOf(it.mediaUrl) == mainItem }
            instagram -> rows.any { row -> pathsOf(listOf(row)).any(mainPaths::contains) }
            else -> true
        }
        val masterMillis = rows.mapNotNull(::millisOf)
        val mainMillis = mains.mapNotNull(::millisOf)
        val duration = when {
            masterMillis.isEmpty() || mainMillis.isEmpty() -> "unknown"
            masterMillis.all { master -> mainMillis.all { abs(master - it) <= DURATION_SLACK_MILLIS } } -> "yes"
            else -> "no"
        }
        val mainVideos = mains.mapNotNull { video(it.videoId) }.toSet()
        val mainItems = mains.mapNotNull { XLadder.itemOf(it.mediaUrl) }.toSet()
        val verdicts = rows.map { row ->
            val key = video(row.videoId)
            val identity = when {
                key == null -> Verdict.UNKNOWN
                // A video none of main's rows name, even when theirs are several: another post's.
                mainVideos.isNotEmpty() && key !in mainVideos -> Verdict.WRONG
                mainVideo == null -> Verdict.UNKNOWN
                x -> {
                    val item = XLadder.itemOf(row.mediaUrl)
                    when {
                        item == null -> Verdict.UNKNOWN
                        mainItems.isNotEmpty() && item !in mainItems -> Verdict.WRONG
                        mainItem == null -> Verdict.UNKNOWN
                        else -> Verdict.MATCH
                    }
                }
                instagram -> if (pathsOf(listOf(row)).any(mainPaths::contains)) Verdict.MATCH else Verdict.UNKNOWN
                else -> Verdict.MATCH
            }
            // A stated length more than 1 s from every one main's rows state is another post's.
            val millis = millisOf(row)
            val longer = millis != null && mainMillis.isNotEmpty() && mainMillis.none { abs(millis - it) <= DURATION_SLACK_MILLIS }
            if (longer) Verdict.WRONG else identity
        }
        return Compared(
            masterRows = rows.size,
            mainRows = mains.size,
            item = x || instagram,
            instagram = instagram,
            same = same,
            duration = duration,
            masterTallest = tallestOf(rows),
            mainTallest = tallestOf(mains),
            verdicts = verdicts,
            facebook = facebook,
            masterHeights = heightsOf(rows),
            mainHeights = heightsOf(mains),
        )
    }

    private fun millisOf(row: MediaCandidate): Long? = row.durationMillis?.takeIf { it > 0 }

    /** The file paths of [rows] (each row's file and its audio's; no host, no query). */
    private fun pathsOf(rows: List<MediaCandidate>): Set<String> = rows.flatMap { row ->
        listOfNotNull(row.mediaUrl, row.audioCompanion?.mediaUrl)
    }.mapNotNull { url -> runCatching { URI(url).path }.getOrNull()?.takeIf { it.isNotEmpty() } }.toSet()

    /** The tallest height [rows] state, `unknown` when none states one. */
    private fun tallestOf(rows: List<MediaCandidate>): String =
        rows.mapNotNull { row -> row.height?.takeIf { it > 0 } }.maxOrNull()?.toString() ?: UNKNOWN

    /**
     * Task 16: the heights [rows] state, each once, tallest first (`720,360`); L1: `not stated` when
     * none states one (the rows name no height: nothing to check, not a fault).
     */
    private fun heightsOf(rows: List<MediaCandidate>): String =
        rows.mapNotNull { row -> row.height?.takeIf { it > 0 } }.distinct().sortedDescending().joinToString(",").ifEmpty { NOT_STATED }

    private const val UNKNOWN = "unknown"

    /** L1: a side whose rows state no height (Facebook's heights). */
    private const val NOT_STATED = "not stated"
}
