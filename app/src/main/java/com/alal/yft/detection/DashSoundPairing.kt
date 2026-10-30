package com.alal.yft.detection

import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.media.VariantSupport
import java.util.Locale

/**
 * v1.0.2 item 59: on a page without a site reader, a DASH manifest whose qualities are single
 * files (`SegmentBase`, [MediaVariant.dashOneFile]) offers each video quality with the
 * manifest's sound, merged as the other merges are ([DeviceMergeSupport]: AVC with AAC into an
 * MP4; VP9 with Opus into a WebM from Android 10; AV1 only with the debug build's test switch,
 * item 26). The sound is the manifest's first language at its highest bitrate the phone merges
 * with that video. A video the phone merges with none of the sounds stays as it was: "No sound".
 *
 * A site reader's rows ([MediaCandidate.videoId]) are left as they are, and so is a manifest of
 * `SegmentTemplate` or `SegmentList` pieces: only the generic page path changes.
 */
object DashSoundPairing {
    private val device: DeviceMergeSupport by lazy { DeviceMergeSupport() }

    /** [result] with each of its silent one-file DASH video qualities given a sound to merge. */
    fun pair(
        result: VariantResolutionResult,
        candidate: MediaCandidate,
        merges: (MediaVariant, CompanionAudio) -> Boolean = { video, audio ->
            device.canMerge(video, audio)
        },
    ): VariantResolutionResult {
        if (result !is VariantResolutionResult.Success || candidate.videoId != null) return result
        val variants = pair(result.asset.variants, merges)
        if (variants == result.asset.variants) return result
        return result.copy(asset = result.asset.copy(variants = variants))
    }

    /** [variants] with each silent one-file DASH video given the first sound [merges] takes. */
    internal fun pair(
        variants: List<MediaVariant>,
        merges: (MediaVariant, CompanionAudio) -> Boolean,
    ): List<MediaVariant> {
        val sounds = variants.filter { it.isOneFile(MediaTrackType.AUDIO) }
        if (sounds.isEmpty()) return variants
        val language = sounds.first().languageKey()
        val ordered = sounds.sortedWith(
            compareByDescending<MediaVariant> { it.languageKey() == language }
                .thenByDescending { it.bitrateBitsPerSecond ?: 0L },
        )
        return variants.map { video ->
            if (!video.isOneFile(MediaTrackType.VIDEO) || video.audioCompanion != null) {
                return@map video
            }
            ordered.asSequence()
                .filter { it.playbackUrl == video.playbackUrl }
                .mapNotNull { sound -> sound.companion()?.let { sound to it } }
                .firstOrNull { (_, companion) -> merges(video, companion) }
                ?.let { (sound, companion) -> video.withSound(sound, companion) }
                ?: video
        }
    }

    private fun MediaVariant.isOneFile(track: MediaTrackType): Boolean =
        kind == MediaKind.DASH && dashOneFile && trackType == track &&
            support == VariantSupport.SUPPORTED && !manifestVariantId.isNullOrBlank() &&
            mp3 == null && !audioFromVideo

    private fun MediaVariant.languageKey(): String? =
        language?.trim()?.lowercase(Locale.US)?.takeIf(String::isNotEmpty)

    /** The sound as a merge's companion; none without the file type its manifest states. */
    private fun MediaVariant.companion(): CompanionAudio? {
        val type = mimeType?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return CompanionAudio(
            mediaUrl = playbackUrl,
            mimeType = type,
            codecs = codecs.filter(String::isNotBlank),
            requestContext = requestContext,
            bitrateBitsPerSecond = bitrateBitsPerSecond?.takeIf { it > 0 },
            expiresAtEpochMs = expiresAtEpochMs,
            representationId = manifestVariantId,
        )
    }

    /** The video with [sound]: its size is both files' when both are known, else unknown. */
    private fun MediaVariant.withSound(sound: MediaVariant, companion: CompanionAudio): MediaVariant {
        val total = sizeBytes?.let { picture ->
            sound.sizeBytes?.takeIf { picture <= Long.MAX_VALUE - it }?.let { picture + it }
        }
        val exact = sizeAccuracy == MediaSizeAccuracy.EXACT &&
            sound.sizeAccuracy == MediaSizeAccuracy.EXACT
        return copy(
            trackType = MediaTrackType.AUDIO_VIDEO,
            audioCompanion = companion,
            sizeBytes = total,
            sizeAccuracy = when {
                total == null -> null
                exact -> MediaSizeAccuracy.EXACT
                else -> MediaSizeAccuracy.ESTIMATED
            },
        )
    }
}
