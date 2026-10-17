package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/**
 * Which candidates belong to the focused video. Empty means "cannot tell": the engine reports
 * NeedsSelection instead of guessing.
 */
internal object FocusSelection {
    fun select(candidates: List<MediaCandidate>, snapshot: PageSnapshot): List<MediaCandidate> {
        val focused = snapshot.playingMediaUrl?.let(UrlPolicy::secure)?.let(UrlPolicy::whole)
        val playing = candidates.filter { UrlPolicy.whole(it.mediaUrl) == focused }
        if (playing.isNotEmpty()) {
            val ids = playing.mapNotNull(MediaCandidate::videoId).toSet()
            val keys = playing.mapNotNull(MediaCandidate::pageVideoKey).toSet()
            return candidates.filter {
                it in playing || (it.videoId != null && it.videoId in ids) ||
                    (it.pageVideoKey != null && it.pageVideoKey in keys)
            }
        }
        val named = candidates.filter { it.pageRole == PageMediaRole.MAIN }
        if (named.isEmpty()) return emptyList()
        // Multiple anonymous page videos are not automatically all qualities of one video.
        val groups = named.groupBy {
            it.videoId ?: it.pageVideoKey ?: UrlPolicy.whole(it.mediaUrl)
        }
        return if (groups.size == 1) named else emptyList()
    }
}
