package com.alal.yft.core.model.media

import com.alal.yft.core.model.logging.SensitiveValueRedactor

enum class MediaTrackType {
    AUDIO_VIDEO,
    VIDEO,
    AUDIO,
}

enum class MediaSizeAccuracy {
    EXACT,
    ESTIMATED,
}

enum class VariantSupport {
    SUPPORTED,
    UNSUPPORTED_CODEC,
}

/**
 * One truthful, independently selectable representation of a detected asset.
 *
 * [playbackUrl] may contain short-lived credentials, so it is intentionally omitted from
 * [toString]. Unknown metadata stays null rather than being inferred from a label or URL.
 */
data class MediaVariant(
    val id: String,
    val playbackUrl: String,
    val kind: MediaKind,
    val trackType: MediaTrackType,
    val requestContext: BrowserRequestContext,
    val manifestVariantId: String? = null,
    val label: String? = null,
    val mimeType: String? = null,
    val container: String? = null,
    val codecs: List<String> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
    val framesPerSecond: Double? = null,
    val bitrateBitsPerSecond: Long? = null,
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val sizeAccuracy: MediaSizeAccuracy? = null,
    val language: String? = null,
    val audioGroupId: String? = null,
    val support: VariantSupport = VariantSupport.SUPPORTED,
    val expiresAtEpochMs: Long? = null,
    /**
     * Set when [playbackUrl] is a video-only file: the audio downloaded with it and merged into
     * one MP4. [trackType] then describes the merged result.
     */
    val audioCompanion: CompanionAudio? = null,
    /** Set when the download is this audio converted to MP3 on the phone ([Mp3Variants]). */
    val mp3: Mp3Conversion? = null,
    /**
     * Set when [playbackUrl] is a video file whose sound is kept as an M4A on the phone
     * ([AudioFromVideo]); [trackType] then describes the audio result.
     */
    val audioFromVideo: Boolean = false,
    /** The bitrate of the sound inside a video file, when the file states it. */
    val audioBitrateBitsPerSecond: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(playbackUrl.isNotBlank())
        require(audioCompanion == null || trackType == MediaTrackType.AUDIO_VIDEO) {
            "A variant with a companion audio track describes the merged video"
        }
        require(
            mp3 == null ||
                (kind == MediaKind.DIRECT && trackType == MediaTrackType.AUDIO),
        ) { "Only a whole audio file is converted to MP3" }
        require(
            !audioFromVideo ||
                (kind == MediaKind.DIRECT && trackType == MediaTrackType.AUDIO &&
                    audioCompanion == null),
        ) { "Only the sound of one whole video file is kept as audio" }
        require(audioBitrateBitsPerSecond == null || audioBitrateBitsPerSecond > 0)
        require(width == null || width > 0)
        require(height == null || height > 0)
        require(framesPerSecond == null || framesPerSecond > 0)
        require(bitrateBitsPerSecond == null || bitrateBitsPerSecond > 0)
        require(durationMillis == null || durationMillis >= 0)
        require(sizeBytes == null || sizeBytes >= 0)
        require((sizeBytes == null) == (sizeAccuracy == null)) {
            "Size accuracy must be present exactly when size is known"
        }
    }

    val isPreviewable: Boolean
        get() = support == VariantSupport.SUPPORTED

    override fun toString(): String = buildString {
        append("MediaVariant(id=")
        append(id)
        append(", playbackUrl=[REDACTED], kind=")
        append(kind)
        append(", trackType=")
        append(trackType)
        append(", manifestVariantId=")
        append(manifestVariantId)
        append(", codecs=")
        append(codecs)
        append(", dimensions=")
        append(width)
        append('x')
        append(height)
        append(", support=")
        append(support)
        append(", audioCompanion=")
        append(audioCompanion != null)
        append(", mp3Kbps=")
        append(mp3?.bitrateKbps)
        append(", audioFromVideo=")
        append(audioFromVideo)
        append(')')
    }
}

data class MediaAsset(
    val sourcePageUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    val variants: List<MediaVariant>,
    val resolvedAtEpochMs: Long,
) {
    init {
        require(sourcePageUrl.isNotBlank())
        require(durationMillis == null || durationMillis >= 0)
        require(variants.isNotEmpty())
        require(variants.map(MediaVariant::id).distinct().size == variants.size) {
            "Variant IDs must be unique within an asset"
        }
    }

    override fun toString(): String = buildString {
        append("MediaAsset(sourcePageUrl=")
        append(SensitiveValueRedactor.redact(sourcePageUrl))
        append(", title=")
        append(title)
        append(", durationMillis=")
        append(durationMillis)
        append(", variantCount=")
        append(variants.size)
        append(", resolvedAtEpochMs=")
        append(resolvedAtEpochMs)
        append(')')
    }
}

enum class VariantResolutionFailure {
    INVALID_URL,
    EXPIRED_URL,
    DRM_PROTECTED,
    UNSUPPORTED_CODEC,
    MALFORMED_MANIFEST,
    MANIFEST_TOO_LARGE,
    UNSAFE_REDIRECT,
    TOO_MANY_REDIRECTS,
    HTTP_STATUS,
    NETWORK,
    NO_VARIANTS,
}

/** P24: which request a [VariantResolutionResult.Failure] stopped at, for the sheet's Details. */
enum class ResolutionStep {
    /** The address itself, before any request. */
    ADDRESS,

    /** The file's type and size (HEAD or a one-byte range). */
    FILE_CHECK,

    /** The HLS or DASH manifest: the list of the video's qualities. */
    MANIFEST,

    /** One quality's own HLS playlist, read for the video's length. */
    MEDIA_PLAYLIST,

    /** Anything after the requests: reading what came back. */
    PREPARE,
}

sealed interface VariantResolutionResult {
    data class Success(val asset: MediaAsset) : VariantResolutionResult

    data class Failure(
        val reason: VariantResolutionFailure,
        val httpStatusCode: Int? = null,
        /** P24: the request the failure came from; null when unknown. */
        val step: ResolutionStep? = null,
        /** P24: the host that request went to, never its path or query; null when unknown. */
        val host: String? = null,
        /** P39: the exception's simple class name (e.g. SocketTimeoutException), never its
         *  message; null when unknown. */
        val error: String? = null,
    ) : VariantResolutionResult
}