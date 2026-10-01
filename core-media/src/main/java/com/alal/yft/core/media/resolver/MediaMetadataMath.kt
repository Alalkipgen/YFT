package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.MediaSizeAccuracy

internal data class EstimatedSize(
    val bytes: Long?,
    val accuracy: MediaSizeAccuracy?,
)

internal fun estimateSize(
    bitrateBitsPerSecond: Long?,
    durationMillis: Long?,
): EstimatedSize {
    if (bitrateBitsPerSecond == null || bitrateBitsPerSecond <= 0) {
        return EstimatedSize(null, null)
    }
    if (durationMillis == null || durationMillis <= 0) {
        return EstimatedSize(null, null)
    }

    val bytes = runCatching {
        Math.multiplyExact(bitrateBitsPerSecond, durationMillis) / BITS_PER_BYTE_MILLISECOND
    }.getOrNull()?.takeIf { it >= 0 }
    return if (bytes == null) {
        EstimatedSize(null, null)
    } else {
        EstimatedSize(bytes, MediaSizeAccuracy.ESTIMATED)
    }
}

private const val BITS_PER_BYTE_MILLISECOND = 8_000L