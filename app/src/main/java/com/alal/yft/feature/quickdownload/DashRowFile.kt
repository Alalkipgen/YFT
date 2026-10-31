package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant

/**
 * v1.0.2 fix R2: the file a DASH row of the sheet downloads. Every quality of a manifest has the
 * manifest's address, so the address found the manifest's first Representation for every row
 * (the owner's Repro A and B on Preview #161: the audio "1" for A's 360p, `audio_eng=64008` for
 * B's 240p and 128 kbps rows). A row is found again, in the manifest read for its download, by
 * its own Representation ([MediaVariant.manifestVariantId]), its kind of track and its sound's
 * Representation ([com.alal.yft.core.model.media.CompanionAudio.representationId]): a merged
 * row, a video-only ("No sound") row and an audio row each keep theirs. A row that is not in the
 * manifest now fails ([missingLine]); it is never another Representation or another track.
 */
internal object DashRowFile {
    /** The start of [missingLine]. */
    const val MISSING = "Not in the manifest now:"

    /** [row]'s file among [fresh], the manifest's qualities read for the download; or none. */
    fun find(fresh: List<MediaVariant>, row: MediaVariant): MediaVariant? {
        if (row.kind != MediaKind.DASH) return null
        val sound = row.audioCompanion?.let { it.representationId ?: return null }
        return fresh.firstOrNull { variant ->
            variant.kind == MediaKind.DASH && variant.isPreviewable &&
                variant.mp3 == null && !variant.audioFromVideo &&
                variant.isRepresentationOf(row) && variant.trackType == row.trackType &&
                (variant.audioCompanion == null) == (sound == null) &&
                variant.audioCompanion?.representationId == sound
        }
    }

    /**
     * The download's Details line when [find] found nothing: "Not in the manifest now:
     * Representation 5 (video) with sound 1".
     */
    fun missingLine(row: MediaVariant): String {
        val track = when {
            row.trackType == MediaTrackType.AUDIO -> "audio"
            row.trackType == MediaTrackType.VIDEO -> "video only"
            row.audioCompanion != null -> "video"
            else -> "video with sound"
        }
        val sound = row.audioCompanion?.let { " with sound ${it.representationId ?: "?"}" }
        return "$MISSING Representation ${row.manifestVariantId ?: row.id} ($track)" +
            sound.orEmpty()
    }

    /** Its Representation's own id; a manifest without one keeps the parser's place for it. */
    private fun MediaVariant.isRepresentationOf(row: MediaVariant): Boolean {
        val representation = row.manifestVariantId?.takeIf(String::isNotBlank)
        return if (representation != null) {
            manifestVariantId == representation
        } else {
            manifestVariantId.isNullOrBlank() && id == row.id
        }
    }
}
