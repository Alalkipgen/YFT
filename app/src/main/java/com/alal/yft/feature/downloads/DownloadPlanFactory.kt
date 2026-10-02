package com.alal.yft.feature.downloads

import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantSupport
import com.alal.yft.core.model.download.HlsDownloadPlan
import java.util.Locale

/** A typed plan the download queue can accept, paired with its non-sensitive output naming. */
sealed interface DownloadRequest {
    val fileName: String
    val mimeType: String?

    data class Direct(
        val plan: DirectDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest

    data class Hls(
        val plan: HlsDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest

    data class Dash(
        val plan: DashDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest
}

sealed interface DownloadPlanResult {
    data class Ready(val request: DownloadRequest) : DownloadPlanResult

    data class Rejected(
        val reason: DownloadFailureReason,
        val message: String,
    ) : DownloadPlanResult
}

/**
 * Converts an already-resolved preview selection into a typed download plan.
 *
 * This stays pure so the sensitive playback URL is only copied into the plan, never logged or
 * persisted here. Unsupported or expired selections are rejected explicitly instead of being
 * downgraded to a guess.
 */
object DownloadPlanFactory {
    const val MAX_FILE_NAME_LENGTH = 96

    fun create(
        asset: MediaAsset,
        variant: MediaVariant,
        taskId: String,
        nowEpochMs: Long,
    ): DownloadPlanResult {
        require(taskId.isNotBlank())

        if (variant.support != VariantSupport.SUPPORTED) {
            return rejected(
                DownloadFailureReason.UNSUPPORTED_SOURCE,
                "This track uses a codec YFT cannot download.",
            )
        }
        if (!variant.playbackUrl.startsWith("https://", ignoreCase = true)) {
            return rejected(
                DownloadFailureReason.INVALID_URL,
                "Only HTTPS media sources can be downloaded.",
            )
        }
        val expiry = variant.expiresAtEpochMs
        if (expiry != null && expiry <= nowEpochMs) {
            return rejected(
                DownloadFailureReason.EXPIRED_URL,
                "This media link already expired. Reload the page and try again.",
            )
        }

        val fileName = fileName(asset, variant)
        val context = variant.requestContext

        return when (variant.kind) {
            MediaKind.DIRECT -> DownloadPlanResult.Ready(
                DownloadRequest.Direct(
                    plan = DirectDownloadPlan(
                        taskId = taskId,
                        sourceUrl = variant.playbackUrl,
                        suggestedFileName = fileName,
                        requestContext = context,
                        mimeType = variant.mimeType,
                        expectedBytes = variant.exactSizeBytes(),
                        expiresAtEpochMs = expiry,
                    ),
                    fileName = fileName,
                    mimeType = variant.mimeType,
                ),
            )

            MediaKind.HLS -> DownloadPlanResult.Ready(
                DownloadRequest.Hls(
                    plan = HlsDownloadPlan(
                        taskId = taskId,
                        playlistUrl = variant.playbackUrl,
                        suggestedFileName = fileName,
                        requestContext = context,
                        mimeType = variant.mimeType,
                        expiresAtEpochMs = expiry,
                    ),
                    fileName = fileName,
                    mimeType = variant.mimeType,
                ),
            )

            MediaKind.DASH -> {
                val representationId = variant.manifestVariantId
                if (representationId.isNullOrBlank()) {
                    rejected(
                        DownloadFailureReason.UNSUPPORTED_SOURCE,
                        "This DASH track has no representation to download.",
                    )
                } else {
                    DownloadPlanResult.Ready(
                        DownloadRequest.Dash(
                            plan = DashDownloadPlan(
                                taskId = taskId,
                                manifestUrl = variant.playbackUrl,
                                representationId = representationId,
                                trackType = variant.trackType,
                                suggestedFileName = fileName,
                                requestContext = context,
                                mimeType = variant.mimeType,
                                codecs = variant.codecs.filter(String::isNotBlank),
                                expiresAtEpochMs = expiry,
                            ),
                            fileName = fileName,
                            mimeType = variant.mimeType,
                        ),
                    )
                }
            }

            MediaKind.UNKNOWN -> rejected(
                DownloadFailureReason.UNSUPPORTED_SOURCE,
                "YFT could not identify this media source.",
            )
        }
    }

    /**
     * Builds a safe output name from non-sensitive metadata only. The playback URL is never used
     * because signed URLs can carry credentials.
     */
    internal fun fileName(asset: MediaAsset, variant: MediaVariant): String {
        val base = listOfNotNull(
            asset.title?.takeIf { it.isNotBlank() },
            variant.label?.takeIf { it.isNotBlank() },
        )
            .joinToString(" ")
            .ifBlank { "yft-download" }
            .sanitizeForFileSystem()
            .ifBlank { "yft-download" }

        val extension = extensionFor(variant)
        val maxBase = (MAX_FILE_NAME_LENGTH - extension.length - 1).coerceAtLeast(1)
        return "${base.take(maxBase).trimEnd('.', ' ', '-', '_')}.$extension"
            .ifBlank { "yft-download.$extension" }
    }

    internal fun extensionFor(variant: MediaVariant): String {
        variant.container?.takeIf { it.isNotBlank() }?.let { container ->
            return container.lowercase(Locale.US).trimStart('.').sanitizeForFileSystem()
                .ifBlank { defaultExtension(variant) }
        }
        return when (variant.mimeType?.lowercase(Locale.US)) {
            "video/mp4", "application/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/mp2t" -> "mp4"
            "audio/mp4", "audio/mp4a-latm" -> "m4a"
            "audio/mpeg" -> "mp3"
            "audio/webm" -> "weba"
            "audio/ogg" -> "ogg"
            else -> defaultExtension(variant)
        }
    }

    private fun defaultExtension(variant: MediaVariant): String =
        if (variant.trackType == MediaTrackType.AUDIO) "m4a" else "mp4"

    private fun rejected(
        reason: DownloadFailureReason,
        message: String,
    ): DownloadPlanResult.Rejected = DownloadPlanResult.Rejected(reason, message)

    private fun MediaVariant.exactSizeBytes(): Long? = sizeBytes?.takeIf {
        sizeAccuracy == com.alal.yft.core.model.media.MediaSizeAccuracy.EXACT
    }

    /**
     * Keeps only characters that are safe in a user-visible file name.
     *
     * Dots are intentionally dropped so a hostile title can never produce a traversal segment
     * such as `..`; the extension is appended separately.
     */
    private fun String.sanitizeForFileSystem(): String = trim()
        .map { character ->
            when {
                character.isLetterOrDigit() -> character
                character in ALLOWED_PUNCTUATION -> character
                else -> ' '
            }
        }
        .joinToString("")
        .replace(WHITESPACE_RUN, " ")
        .trim()

    private val WHITESPACE_RUN = Regex("\\s+")
    private const val ALLOWED_PUNCTUATION = "-_()[] "
}
