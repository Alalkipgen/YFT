package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.MediaVariant

internal sealed interface ManifestParseResult {
    data class Parsed(
        val variants: List<MediaVariant>,
        val durationMillis: Long?,
        /**
         * The qualities whose bitrate the manifest states as the stream's average (an HLS master's
         * `AVERAGE-BANDWIDTH`): only their bitrate times a length is a size. A peak or nominal rate
         * is not, so a quality without its average here is sized from nothing.
         */
        val statedAverages: Set<String> = emptySet(),
    ) : ManifestParseResult

    data object DrmProtected : ManifestParseResult
    data object Malformed : ManifestParseResult
}