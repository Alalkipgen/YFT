package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.MediaKind

internal enum class VkManifestVerdict(val label: String) {
    VIDEO("video"),
    AUDIO_ONLY("audio-only"),

    /** Not a manifest, or one that cannot tell (an HLS media playlist, an MPD without tracks). */
    UNKNOWN("not judged"),
}

/**
 * Whether a VK manifest carries video (VK_PLAN §4.2 step 3), asked only when the video's data has
 * no MP4.
 *
 * HLS: a master playlist is video when a variant has a `RESOLUTION` or a video codec, audio-only
 * when it lists variants and none has either. DASH: video when an `AdaptationSet` or
 * `Representation` says so (`mimeType="video/…"`, `contentType="video"`, a `width`/`height`, a
 * video codec), audio-only when the tracks it has are all audio. Anything else — a media
 * playlist, an MPD without tracks, a page that is not a manifest — is [VkManifestVerdict.UNKNOWN]
 * and never reported as audio-only.
 */
internal object VkJudge {
    private const val VARIANT_TAG = "#EXT-X-STREAM-INF:"
    private val RESOLUTION = Regex("\\bRESOLUTION=[0-9]+x[0-9]+")
    private val CODECS = Regex("\\b[Cc][Oo][Dd][Ee][Cc][Ss]\\s*=\\s*\"([^\"]*)\"")
    private val VIDEO_CODECS =
        listOf("avc1", "avc3", "hvc1", "hev1", "vp08", "vp8", "vp09", "vp9", "av01", "dvh1", "dvhe")

    private val DASH_TAG = Regex("<(?:AdaptationSet|Representation)\\b[^>]*>")
    private val DASH_VIDEO = listOf(
        Regex("\\bmimeType\\s*=\\s*\"video/"),
        Regex("\\bcontentType\\s*=\\s*\"video\""),
        Regex("\\s(?:width|height)\\s*=\\s*\"[1-9]"),
    )
    private val DASH_AUDIO = listOf(
        Regex("\\bmimeType\\s*=\\s*\"audio/"),
        Regex("\\bcontentType\\s*=\\s*\"audio\""),
    )

    fun judge(kind: MediaKind, text: String): VkManifestVerdict = when (kind) {
        MediaKind.HLS -> hls(text)
        MediaKind.DASH -> dash(text)
        else -> VkManifestVerdict.UNKNOWN
    }

    fun hls(text: String): VkManifestVerdict {
        if (!text.trimStart().startsWith("#EXTM3U")) return VkManifestVerdict.UNKNOWN
        val variants = text.lineSequence().map(String::trim)
            .filter { it.startsWith(VARIANT_TAG) }
            .toList()
        return when {
            variants.isEmpty() -> VkManifestVerdict.UNKNOWN
            variants.any { RESOLUTION.containsMatchIn(it) || hasVideoCodec(it) } ->
                VkManifestVerdict.VIDEO

            else -> VkManifestVerdict.AUDIO_ONLY
        }
    }

    fun dash(text: String): VkManifestVerdict {
        if ("<MPD" !in text) return VkManifestVerdict.UNKNOWN
        val tracks = DASH_TAG.findAll(text).map { it.value }.toList()
        return when {
            tracks.any(::isVideoTrack) -> VkManifestVerdict.VIDEO
            tracks.any(::isAudioTrack) -> VkManifestVerdict.AUDIO_ONLY
            else -> VkManifestVerdict.UNKNOWN
        }
    }

    private fun isVideoTrack(tag: String): Boolean =
        DASH_VIDEO.any { it.containsMatchIn(tag) } || hasVideoCodec(tag)

    private fun isAudioTrack(tag: String): Boolean = DASH_AUDIO.any { it.containsMatchIn(tag) }

    private fun hasVideoCodec(tag: String): Boolean =
        CODECS.findAll(tag).any { match ->
            match.groupValues[1].split(',').any { codec ->
                codec.trim().lowercase().substringBefore('.') in VIDEO_CODECS
            }
        }
}
