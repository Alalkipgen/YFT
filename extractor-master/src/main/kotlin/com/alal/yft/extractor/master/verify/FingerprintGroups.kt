package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.MediaCandidate

/**
 * R4: groups ranked, already checked files by [MediaFingerprint]. Order is kept: the first group
 * holds the best-ranked file (main) and every file that is the same video; later groups are other
 * videos. A lone file whose cues disagree with a main that two agreeing files confirm is dropped
 * as an ad, preview or related clip; an ambiguous file (no cues, or a second agreeing ladder)
 * stays its own group. No file is created or changed.
 */
object FingerprintGroups {
    fun of(
        ranked: List<MediaCandidate>,
        print: (MediaCandidate) -> MediaFingerprint?,
    ): List<List<MediaCandidate>> {
        val groups = mutableListOf<MutableList<MediaCandidate>>()
        for (media in ranked) {
            val mine = print(media)
            val home = mine?.let { p ->
                groups.firstOrNull { group -> print(group.first())?.sameVideo(p) == true }
            }
            if (home != null) home += media else groups += mutableListOf(media)
        }
        val main = groups.firstOrNull() ?: return emptyList()
        val reference = print(main.first())
        if (main.size < 2 || reference == null) return groups
        return groups.filterIndexed { index, group ->
            index == 0 || group.size >= 2 ||
                print(group.first())?.let(reference::otherVideo) != true
        }
    }
}
