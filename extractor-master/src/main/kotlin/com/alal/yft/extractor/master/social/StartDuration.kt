package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.verify.SegmentIndexReader

/**
 * Social plan S1a (10): a file's length (its playing time) from the first bytes V read of it
 * ([SocialRowCheck.START_BYTES]), for a row that states none: the plugin player's answer (F1)
 * names no length, and F2's rows state one only where the page does. ISO BMFF only, three ways:
 *
 * 1. The movie header (`mvhd`, inside `moov`): its duration over its timescale. A progressive
 *    file's `moov` is far longer than the bytes read; it is walked as far as they go (its `mvhd`
 *    comes first), and each box is read only when the bytes hold it whole.
 * 2. Else a fragmented file's length (`mehd`, inside `moov`'s `mvex`) over `mvhd`'s timescale
 *    (a DASH file's `mvhd` states 0).
 * 3. Else the first whole top-level `sidx` (an on-demand DASH file's index, [SegmentIndexReader]).
 *
 * At most [MAX_BOXES] boxes; a length from 1 ms to 48 hours, else null (none in the bytes, a cut
 * header, no ISO BMFF). Pure: never decodes, plays or asks anything, and nothing is logged.
 */
internal object StartDuration {
    /** The longest length counted (48 hours), as the R4 reader's. */
    private const val MAX_SECONDS = 172_800L
    private const val MAX_BOXES = 256

    fun millis(bytes: ByteArray): Long? {
        var movieMillis: Long? = null
        var scale = 0L
        var fragmentTicks: Long? = null
        var indexMillis: Long? = null
        var boxes = 0

        fun u32(at: Int): Long = (0..3).fold(0L) { n, i -> (n shl 8) or (bytes[at + i].toLong() and 255) }

        fun u64(at: Int): Long? {
            if (at + 8 > bytes.size) return null
            val high = u32(at)
            if (high > 0x7fffffff) return null
            return (high shl 32) or u32(at + 4)
        }

        fun movieHeader(body: Int, end: Int) {
            val version = bytes[body].toInt() and 255
            val scaleAt = body + if (version == 1) 20 else 12
            val ticksAt = scaleAt + 4
            if (version !in 0..1 || ticksAt + (if (version == 1) 8 else 4) > end) return
            scale = u32(scaleAt)
            val ticks = if (version == 1) u64(ticksAt) else u32(ticksAt)
            if (scale > 0 && ticks != null && ticks in 1..MAX_SECONDS * scale) movieMillis = ticks * 1_000 / scale
        }

        fun fragmentHeader(body: Int, end: Int) {
            when (bytes[body].toInt() and 255) {
                0 -> if (body + 8 <= end) fragmentTicks = u32(body + 4)
                1 -> if (body + 12 <= end) fragmentTicks = u64(body + 4)
            }
        }

        /** The boxes from [begin] to [limit] (the bytes' end for a box they hold only in part). */
        fun walk(begin: Int, limit: Int, depth: Int) {
            var at = begin
            while (at + 8 <= limit && ++boxes <= MAX_BOXES) {
                val size = u32(at)
                val header = if (size == 1L) 16 else 8
                if (at + header > limit) return
                val length = when (size) {
                    1L -> u64(at + 8) ?: return
                    0L -> (limit - at).toLong()
                    else -> size
                }
                if (length < header) return
                val whole = length <= limit - at
                val end = if (whole) at + length.toInt() else limit
                val body = at + header
                when (String(bytes, at + 4, 4, Charsets.US_ASCII)) {
                    "moov" -> if (depth == 0) walk(body, end, 1)
                    "mvex" -> if (depth == 1) walk(body, end, 2)
                    "mvhd" -> if (depth == 1 && whole && body < end) movieHeader(body, end)
                    "mehd" -> if (depth == 2 && whole && body < end) fragmentHeader(body, end)
                    "sidx" -> if (depth == 0 && whole && indexMillis == null) {
                        indexMillis = SegmentIndexReader.sidxBody(bytes, body, end)?.durationMillis
                    }
                }
                // Nothing after a box the bytes hold only in part is in them.
                if (!whole) return
                at = end
            }
        }

        walk(0, bytes.size, 0)
        val fragment = fragmentTicks?.takeIf { scale > 0 && it in 1..MAX_SECONDS * scale }?.let { it * 1_000 / scale }
        return listOfNotNull(movieMillis, fragment, indexMillis).firstOrNull { it in 1..MAX_SECONDS * 1_000 }
    }
}
