package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.MediaCandidate
import kotlin.math.abs

/**
 * R4: what ties one video's files together without decoding them — the length and, when a file
 * states it, its keyframe cues ([SegmentIndexReader]). Length alone never groups two files; it
 * takes both a near-identical length and agreeing cues.
 */
data class MediaFingerprint(
    val durationMillis: Long?,
    val cuesMillis: List<Long> = emptyList(),
) {
    /**
     * Whether the keyframe cues of two files agree over the span both cover: true when nearly all
     * line up, false when most do not, null when either file states too few cues to tell.
     */
    fun keyframesAgree(other: MediaFingerprint): Boolean? {
        val mineLast = cuesMillis.lastOrNull() ?: return null
        val theirLast = other.cuesMillis.lastOrNull() ?: return null
        val span = minOf(mineLast, theirLast) + CUE_SLACK_MS
        val mine = cuesMillis.filter { it <= span }.take(MAX_COMPARED)
        val theirs = other.cuesMillis.filter { it <= span }.take(MAX_COMPARED)
        if (mine.size < MIN_CUES || theirs.size < MIN_CUES) return null
        val share = minOf(matched(mine, other.cuesMillis), matched(theirs, cuesMillis))
        return when {
            share >= AGREE -> true
            share < DISAGREE -> false
            else -> null
        }
    }

    /**
     * Qualities of one video: lengths within [SAME_LENGTH_MS] (one frame when the cues are a
     * fixed grid, which any two videos cut the same way share) and agreeing cues.
     */
    fun sameVideo(other: MediaFingerprint): Boolean {
        val mine = durationMillis ?: return false
        val theirs = other.durationMillis ?: return false
        if (keyframesAgree(other) != true) return false
        val slack = if (uniform(cuesMillis) && uniform(other.cuesMillis)) GRID_LENGTH_MS
        else SAME_LENGTH_MS
        return abs(mine - theirs) <= slack
    }

    /** Another video: the cues disagree, whatever the lengths. */
    fun otherVideo(other: MediaFingerprint): Boolean = keyframesAgree(other) == false

    companion object {
        const val CUE_SLACK_MS = 120L
        const val SAME_LENGTH_MS = 250L
        const val GRID_LENGTH_MS = 40L
        const val MIN_CUES = 3
        const val MAX_COMPARED = 64
        private const val AGREE = 0.9
        private const val DISAGREE = 0.5

        fun of(candidate: MediaCandidate) = MediaFingerprint(candidate.durationMillis)

        fun of(timeline: SegmentIndexReader.Timeline?, durationMillis: Long?) =
            MediaFingerprint(durationMillis ?: timeline?.durationMillis, timeline?.cuesMillis.orEmpty())

        private fun matched(cues: List<Long>, against: List<Long>): Double {
            val hits = cues.count { cue ->
                val at = against.binarySearch(cue).let { if (it >= 0) it else -it - 1 }
                listOfNotNull(against.getOrNull(at), against.getOrNull(at - 1))
                    .any { abs(it - cue) <= CUE_SLACK_MS }
            }
            return hits.toDouble() / cues.size
        }

        /** Every gap between cues the same (within a millisecond of rounding). */
        private fun uniform(cues: List<Long>): Boolean {
            val gaps = cues.zipWithNext { a, b -> b - a }.ifEmpty { listOfNotNull(cues.firstOrNull()) }
            val first = gaps.firstOrNull() ?: return false
            return gaps.all { abs(it - first) <= 1 } && abs(cues.first() - first) <= 1
        }
    }
}

/** A captured file after the bounded metadata read, with its [fingerprint]. */
data class InspectedMedia(val candidate: MediaCandidate, val fingerprint: MediaFingerprint) {
    companion object {
        fun of(candidate: MediaCandidate) = InspectedMedia(candidate, MediaFingerprint.of(candidate))
    }
}
