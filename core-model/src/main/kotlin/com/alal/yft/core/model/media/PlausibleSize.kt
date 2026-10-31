package com.alal.yft.core.model.media

/**
 * v1.0.2 fix G1: a size read over a bad connection can be absurd; a YouTube M4A of 1:47:11
 * once read "1 B". A size under 1 KB for each second of the media's length is treated as
 * unknown everywhere: the sheet's row, its Download button and the download's total. A clip of
 * a few seconds keeps its real small size, and a size whose media has no known length stays.
 */
object PlausibleSize {
    /** 1 KB (1,000 bytes) a second, 8 kbps: less than any real audio or video file. */
    const val MIN_BYTES_PER_SECOND: Long = 1_000

    /** [bytes], or null when it is less than [MIN_BYTES_PER_SECOND] for [durationMillis]. */
    fun of(bytes: Long?, durationMillis: Long?): Long? {
        bytes ?: return null
        val length = durationMillis?.takeIf { it > 0 } ?: return bytes
        return bytes.takeIf { it.toDouble() * 1_000 / length >= MIN_BYTES_PER_SECOND }
    }
}
