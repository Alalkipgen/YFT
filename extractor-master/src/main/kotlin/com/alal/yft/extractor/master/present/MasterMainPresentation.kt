package com.alal.yft.extractor.master.present

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import java.security.MessageDigest

/** Maps the selected main and alternatives to the unchanged MediaGroup/otherVideos sheet API. */
data class MasterMainPresentation(val main: MediaGroup, val more: List<MediaGroup>) {
    companion object {
        fun key(media: MediaCandidate): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(media.mediaUrl.toByteArray())
            return "master:ui:" + bytes.take(12).joinToString("") { "%02x".format(it) }
        }

        /**
         * One group per [keyOf] value, in first-seen order; the first is main. By default every
         * file is its own group; R4 passes fingerprint keys so one video's qualities share one.
         */
        fun from(
            candidates: List<MediaCandidate>,
            keyOf: (MediaCandidate) -> String = ::key,
        ): MasterMainPresentation {
            val groups = candidates.groupBy(keyOf).map { (groupKey, members) ->
                MediaGroup(groupKey, members.first().title, members)
            }
            return MasterMainPresentation(groups.first(), groups.drop(1))
        }
    }
}