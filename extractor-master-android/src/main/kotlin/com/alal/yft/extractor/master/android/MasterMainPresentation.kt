package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import java.security.MessageDigest

/** Maps the selected main and alternatives to the unchanged MediaGroup/otherVideos sheet API. */
data class MasterMainPresentation(val main: MediaGroup, val more: List<MediaGroup>) {
    companion object {
        internal fun key(media: MediaCandidate): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(media.mediaUrl.toByteArray())
            return "master:ui:" + bytes.take(12).joinToString("") { "%02x".format(it) }
        }

        internal fun from(candidates: List<MediaCandidate>): MasterMainPresentation {
            val groups = candidates.map { MediaGroup(key(it), it.title, listOf(it)) }
            return MasterMainPresentation(groups.first(), groups.drop(1))
        }
    }
}