package com.alal.yft.core.media.resolver

/**
 * Playlist read-ahead: the quality and sound playlists the HLS size loop is sure to request, so
 * they can be read at the same time instead of one after another.
 *
 * The loop asks each quality's playlist in order and, after one gives a length, the playlist of
 * that quality's sound (once per sound), until it has made [budget] requests; an id it asked
 * before keeps its answer. A read is sure when the loop asks it inside the budget whatever the
 * open reads answer. Starting only sure reads keeps the loop's requests, its budget and its
 * estimates those of reading one at a time; only the waiting is shorter.
 */
internal class PlaylistReadAhead(
    private val qualities: List<Quality>,
    private val budget: Int,
) {
    /**
     * A quality's playlist [id] and, when the loop reads one after this quality's length, the id
     * of its sound's playlist ([soundId]). A playlist the loop cannot address is not listed.
     */
    data class Quality(val id: String, val soundId: String?)

    /**
     * The ids the loop is sure to request, in its order. [answered] holds each finished read:
     * true when it gave a length.
     */
    fun sure(answered: Map<String, Boolean>): List<String> {
        // Everything the loop may have requested before the current step (an upper bound) and
        // what it certainly requested (a lower bound, all inside the budget).
        val possible = mutableSetOf<String>()
        val sure = linkedSetOf<String>()
        for (quality in qualities) {
            if (sure.size >= budget) break
            val qualitySure = if (quality.id in possible) {
                quality.id in sure
            } else {
                val inBudget = possible.size < budget
                possible += quality.id
                if (inBudget) sure += quality.id
                inBudget
            }
            val sound = quality.soundId ?: continue
            val length = answered[quality.id]
            if (length == false) continue
            val before = possible.size - if (sound in possible) 1 else 0
            possible += sound
            if (qualitySure && length == true && before < budget) sure += sound
        }
        return sure.toList()
    }
}
