package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import java.util.Locale

/**
 * P50: an HLS master that names its sound apart (`#EXT-X-MEDIA:TYPE=AUDIO` with a URI, as X and
 * many players do) leaves every video quality silent on its own. Such a quality (`AUDIO="group"`)
 * is offered with its group's first audio rendition as its companion and is downloaded as two
 * playlists merged into one MP4, so it is not listed as "No sound".
 *
 * Only where the phone's MP4 muxer takes both: AVC video (`avc1`, `avc3`) and AAC sound
 * (`mp4a.40.*`), both stated in the master's `CODECS`. Any other quality stays as it was. The
 * audio renditions stay listed on their own.
 */
internal object HlsAudioPairing {
    fun pair(variants: List<MediaVariant>): List<MediaVariant> {
        val audioByGroup = variants
            .filter { it.kind == MediaKind.HLS && it.trackType == MediaTrackType.AUDIO }
            .filter { it.audioGroupId != null }
            .groupBy { it.audioGroupId }
            .mapValues { (_, renditions) -> renditions.first() }
        if (audioByGroup.isEmpty()) return variants
        return variants.map { variant -> withCompanion(variant, audioByGroup) }
    }

    private fun withCompanion(
        variant: MediaVariant,
        audioByGroup: Map<String?, MediaVariant>,
    ): MediaVariant {
        if (variant.kind != MediaKind.HLS || variant.trackType != MediaTrackType.VIDEO) {
            return variant
        }
        if (variant.audioCompanion != null) return variant
        val audio = variant.audioGroupId?.let(audioByGroup::get) ?: return variant
        val videoCodecs = variant.codecs.filterNot(::isAudioCodec)
        val audioCodecs = audio.codecs.ifEmpty { variant.codecs.filter(::isAudioCodec) }
        if (videoCodecs.isEmpty() || !videoCodecs.all(::isAvc)) return variant
        if (audioCodecs.isEmpty() || !audioCodecs.all(::isAac)) return variant
        return variant.copy(
            trackType = MediaTrackType.AUDIO_VIDEO,
            codecs = videoCodecs,
            audioCompanion = CompanionAudio(
                mediaUrl = audio.playbackUrl,
                mimeType = audio.mimeType ?: HLS_MIME_TYPE,
                codecs = audioCodecs,
                requestContext = audio.requestContext,
                expiresAtEpochMs = audio.expiresAtEpochMs,
            ),
        )
    }

    private fun isAudioCodec(codec: String): Boolean = CodecSupport.hasAudio(listOf(codec))

    private fun isAvc(codec: String): Boolean =
        codec.trim().lowercase(Locale.US).let { it.startsWith("avc1") || it.startsWith("avc3") }

    private fun isAac(codec: String): Boolean =
        codec.trim().lowercase(Locale.US).startsWith("mp4a.40.")

    private const val HLS_MIME_TYPE = "application/x-mpegURL"
}
