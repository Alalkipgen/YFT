package com.alal.yft.core.model.media

import java.util.Locale

/**
 * The music of a video that has no audio-only file (P3).
 *
 * Sites such as Facebook serve some videos only as MP4 files with sound. The phone then downloads
 * the MP4 and copies its AAC track into an M4A without re-encoding; MP3 is made from the same
 * track ([Mp3Variants]). Pure, so the rules are unit-tested.
 */
object AudioFromVideo {
    const val MIME_TYPE = "audio/mp4"
    const val CONTAINER = "m4a"
    const val LABEL = "M4A"

    /** True for a supported, whole MP4 video file whose sound is AAC. */
    fun canExtract(video: MediaVariant): Boolean {
        if (video.kind != MediaKind.DIRECT || video.trackType != MediaTrackType.AUDIO_VIDEO) {
            return false
        }
        if (video.audioCompanion != null || video.mp3 != null || video.audioFromVideo) return false
        if (video.support != VariantSupport.SUPPORTED) return false
        val mime = video.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        val container = video.container?.trim()?.trimStart('.')?.lowercase(Locale.US)
        if (mime !in MP4_MIMES && container !in MP4_CONTAINERS) return false
        return video.codecs.any { it.trim().lowercase(Locale.US).startsWith(AAC_CODEC_PREFIX) }
    }

    /**
     * The M4A kept from [video]'s sound, or null when it cannot be extracted. The size is an
     * estimate from the sound's stated bitrate and the length; unknown when either is missing.
     */
    fun of(video: MediaVariant, assetDurationMillis: Long? = null): MediaVariant? {
        if (!canExtract(video)) return null
        val duration = (video.durationMillis ?: assetDurationMillis)?.takeIf { it > 0 }
        val bitrate = video.audioBitrateBitsPerSecond
        val estimate = if (duration != null && bitrate != null) {
            duration * bitrate / BITS_PER_BYTE / MILLIS_PER_SECOND
        } else {
            null
        }
        val audioCodecs = video.codecs
            .filter { it.trim().lowercase(Locale.US).startsWith(AAC_CODEC_PREFIX) }
        return video.copy(
            id = "${video.id}$ID_SUFFIX",
            manifestVariantId = null,
            trackType = MediaTrackType.AUDIO,
            label = LABEL,
            mimeType = MIME_TYPE,
            container = CONTAINER,
            codecs = audioCodecs,
            width = null,
            height = null,
            framesPerSecond = null,
            bitrateBitsPerSecond = bitrate,
            durationMillis = duration,
            sizeBytes = estimate,
            sizeAccuracy = estimate?.let { MediaSizeAccuracy.ESTIMATED },
            audioFromVideo = true,
        )
    }

    private const val ID_SUFFIX = "-m4a"
    private const val AAC_CODEC_PREFIX = "mp4a"
    private const val BITS_PER_BYTE = 8
    private const val MILLIS_PER_SECOND = 1_000
    private val MP4_MIMES = setOf("video/mp4", "video/quicktime", "video/x-m4v")
    private val MP4_CONTAINERS = setOf("mp4", "m4v", "mov", "quicktime")
}
