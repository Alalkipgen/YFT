package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.master.toolkit.QualityLadder

/**
 * L2 (`MASTER_KEY_SOCIAL_PLAN.md` §7.4's backlog, for S6): of a rung's rows that V passed, each
 * picture height's best first. On the owner's retest (2026-10-23, Instagram slot 5) the tab-fetch
 * rung's answer held three versions of the post's video (`rows 3 (DIRECT)`), each named 720 × 1280
 * (task 15's F1 gives a version that states no size its video's own) and none with a bitrate
 * (Instagram's recipe reads none; main's rows take theirs from the address). The sheet offers one
 * row per height — at one height the higher bitrate first, else the rows' own order — so it
 * offered the answer's first: 897 KB, where the tab's own playback of that height was 1.9 MB. V
 * reads each file's start and gives each row its file's total and length ([SocialRowCheck]), so
 * the versions of one height can be told apart by their rate.
 *
 * [bestFirst] orders, among the picture rows of one standard height (the picture's short side, as
 * the sheet names it) with the same sound (a file, or one merged with a sound file) and the same
 * video family, the highest rate first — its stated bitrate, else its file's total over its
 * length; on a tie the earlier — in the places those rows held, so the sheet's row of that height
 * is the best one. No row is dropped (the trial's "the same item" still finds every file main
 * shares) and every other row keeps its place. A height whose rows do not all state a rate keeps
 * its order (no guess); a sound-only row, a manifest and a row without a height are not moved.
 * Pure: it reads nothing and never adds or changes a row.
 */
object RowPick {
    fun bestFirst(rows: List<MediaCandidate>): List<MediaCandidate> {
        val placed = rows.toMutableList()
        rows.indices.groupBy { slotOf(rows[it]) }.forEach { (slot, same) ->
            if (slot == null || same.size < 2) return@forEach
            val rates = same.map { rateOf(rows[it]) }
            if (rates.any { it == null }) return@forEach
            // Stable: equal rates keep their order.
            val best = same.indices.sortedByDescending { checkNotNull(rates[it]) }.map { same[it] }
            same.forEachIndexed { at, index -> placed[index] = rows[best[at]] }
        }
        return placed
    }

    /** One standard height's slot: rows of one slot are versions of one picture. */
    private data class Slot(val height: Int, val merged: Boolean, val family: QualityLadder.Family)

    private fun slotOf(row: MediaCandidate): Slot? {
        if (QualityLadder.isAudio(row) || row.kind == MediaKind.HLS || row.kind == MediaKind.DASH) return null
        val height = standardHeight(row) ?: return null
        return Slot(height, row.audioCompanion != null, QualityLadder.videoFamily(row))
    }

    /**
     * The standard height [row]'s picture is named after (its short side's nearest: 720 × 1280 is
     * 720), or null when it states no height.
     */
    internal fun standardHeight(row: MediaCandidate): Int? {
        row.height?.takeIf { it > 0 } ?: return null
        return QualityLadder.qualityName(row)?.removeSuffix("p")?.toIntOrNull()
    }

    /** [row]'s rate in bits per second: its stated bitrate, else its file's total over its length. */
    internal fun rateOf(row: MediaCandidate): Long? {
        row.bitrateBitsPerSecond?.takeIf { it > 0 }?.let { return it }
        val bytes = row.contentLengthBytes?.takeIf { it > 0 } ?: return null
        val millis = row.durationMillis?.takeIf { it > 0 } ?: return null
        return bytes * BITS_PER_BYTE * MILLIS_PER_SECOND / millis
    }

    private const val BITS_PER_BYTE = 8L
    private const val MILLIS_PER_SECOND = 1_000L
}
