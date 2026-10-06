package com.alal.yft.ui.format

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * P25: the names and one-line descriptions a quality has wherever YFT lists it — the Download
 * sheet on every site and the found list — so they read the same: "1080p · Full HD",
 * "720p · HD", "480p"; "M4A"; "MP3 · 128 kbps". Pure, so the table is unit-tested.
 */
object YftQualityNames {
    /** The heights rows are named after, from 144p to 8K. */
    val STANDARD_HEIGHTS = listOf(144, 240, 360, 480, 720, 1_080, 1_440, 2_160, 4_320)

    /** The original sound, a file or a video's own AAC track copied without re-encoding. */
    const val M4A = "M4A"

    const val ORIGINAL_SOUND = "Original sound, fastest"
    const val PLAYS_EVERYWHERE = "Plays everywhere"

    /** A video whose picture is not known yet: the file the page itself plays. */
    const val AS_THE_PAGE_PLAYS = "As the page plays it"

    /** The standard height for a picture of [width] × [height]: nearest to its short side. */
    fun standardHeight(width: Int?, height: Int): Int {
        val side = if (width != null && width > 0) minOf(width, height) else height
        // On a tie the lower name wins, so a row never claims more than the picture has.
        return STANDARD_HEIGHTS.minBy { standard -> abs(standard - side) }
    }

    /** "1080p", or "1080p60" above 31 frames a second. */
    fun quality(standardHeight: Int, framesPerSecond: Double? = null): String {
        val rate = framesPerSecond?.takeIf { it > HIGH_FRAME_RATE }?.roundToInt()?.toString()
        return "${standardHeight}p${rate.orEmpty()}"
    }

    /** "2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD", "480p", "360p", "144p". */
    fun videoName(standardHeight: Int, framesPerSecond: Double? = null): String =
        listOfNotNull(quality(standardHeight, framesPerSecond), resolutionName(standardHeight))
            .joinToString(" · ")

    /** The line under a video row, by the height it is named (HD as 720, SD as 480) after. */
    fun videoDescription(height: Int?): String = when {
        height == null || height <= 0 -> AS_THE_PAGE_PLAYS
        height <= 144 -> "Low quality, smallest file"
        height <= 240 -> "Low quality for quick play"
        height <= 480 -> "Normal quality for quick play"
        height <= 720 -> "Clear view and quick play"
        height <= 1_080 -> "High details for full screen play"
        else -> "High details for big screen play"
    }

    fun mp3Name(kbps: Int): String = "MP3 · $kbps kbps"

    /** Bytes for [bitrateBitsPerSecond] over [durationMillis], or null when either is unknown. */
    fun estimatedBytes(bitrateBitsPerSecond: Long?, durationMillis: Long?): Long? {
        if (bitrateBitsPerSecond == null || bitrateBitsPerSecond <= 0) return null
        if (durationMillis == null || durationMillis <= 0) return null
        val bytes = bitrateBitsPerSecond.toDouble() * (durationMillis / BIT_MILLIS_PER_BYTE)
        return bytes.takeIf { it.isFinite() && it >= 1 && it < Long.MAX_VALUE.toDouble() }
            ?.toLong()
    }

    private fun resolutionName(height: Int): String? = when {
        height >= 4_320 -> "8K"
        height >= 2_160 -> "4K"
        height >= 1_440 -> "2K"
        height >= 1_080 -> "Full HD"
        height >= 720 -> "HD"
        else -> null
    }

    private const val HIGH_FRAME_RATE = 31.0
    private const val BIT_MILLIS_PER_BYTE = 8_000.0
}
