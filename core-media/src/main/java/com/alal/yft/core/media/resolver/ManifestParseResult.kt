package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.MediaVariant

internal sealed interface ManifestParseResult {
    data class Parsed(
        val variants: List<MediaVariant>,
        val durationMillis: Long?,
    ) : ManifestParseResult

    data object DrmProtected : ManifestParseResult
    data object Malformed : ManifestParseResult
}