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
) {
    init {
        require(id.isNotBlank())
        require(playbackUrl.isNotBlank())
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

sealed interface VariantResolutionResult {
    data class Success(val asset: MediaAsset) : VariantResolutionResult

    data class Failure(
        val reason: VariantResolutionFailure,
        val httpStatusCode: Int? = null,
    ) : VariantResolutionResult
}