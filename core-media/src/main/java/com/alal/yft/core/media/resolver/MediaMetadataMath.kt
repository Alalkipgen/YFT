package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.MediaSizeAccuracy

internal data class EstimatedSize(
    val bytes: Long?,
    val accuracy: MediaSizeAccuracy?,
)

/**
 * A size from a bitrate the source states as the stream's average and the length it states, marked
 * [MediaSizeAccuracy.ESTIMATED]. Only for an average rate (an HLS master's `AVERAGE-BANDWIDTH`): a
 * peak or nominal rate (DASH `@bandwidth`, HLS `BANDWIDTH`, YouTube's live format rate) times the
 * length is not a reliable file size, so it is never shown as one.
 */
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