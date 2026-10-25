package com.alal.yft.extractor.master.social

/**
 * Social plan task 16 (F6): a file's picture size (width × height, as the picture shows) from the
 * first bytes V read of it ([SocialRowCheck.START_BYTES]), for a row that states none: the plugin
 * player's answer names no size, as it names no length ([StartDuration], S1a (10)). ISO BMFF only,
 * inside the file's `moov` (a progressive file's, walked as far as the bytes go, or a fragmented
 * file's, whose tracks state their headers and sample entries as well), track by track:
 *
 * 1. The track's header (`tkhd`): its width × height (16.16 fixed point), for a picture track — its
 *    handler (`mdia` → `hdlr`) `vide`, or, with no handler in the bytes, a non-zero size (a sound
 *    track's is 0 × 0).
 * 2. Else the picture track's visual sample entry (`mdia` → `minf` → `stbl` → `stsd`: `avc1`,
 *    `avc3`, `hvc1`, `hev1`, `vp09`, `av01`): its width and height (16-bit).
 *
 * A 90° or 270° track matrix (`a` = `d` = 0, `b` and `c` not) swaps them, as the picture shows. The
 * first picture track with a size wins; each side [MIN_SIDE] to [MAX_SIDE], else none. None also
 * for a `moov` past the bytes, a box the bytes hold only in part (each box is read only whole), a
 * sound-only file and bytes that are no ISO BMFF. At most [MAX_BOXES] boxes. Pure: never decodes,
 * plays or asks anything, and nothing is logged.
 */
internal object StartPicture {
    /** The shortest side counted, in pixels. */
    const val MIN_SIDE = 16

    /** The longest side counted (8K's), in pixels. */
    const val MAX_SIDE = 8_192

    private const val MAX_BOXES = 256

    /** The visual sample entries whose size is read (`stsd`'s first entry). */
    private val VISUAL = setOf("avc1", "avc3", "hvc1", "hev1", "vp09", "av01")

    /** A picture [width] × [height] as it shows (a turned track's swapped). */
    data class Size(val width: Int, val height: Int) {
        override fun toString(): String = "$width × $height"
    }

    /** One track's facts, as far as the bytes go. */
    private class Track {
        var handler: String? = null
        var header: Size? = null
        var turned = false
        var entry: Size? = null
    }

    fun of(bytes: ByteArray): Size? {
        var boxes = 0
        var found: Size? = null

        fun u16(at: Int): Int = ((bytes[at].toInt() and 255) shl 8) or (bytes[at + 1].toInt() and 255)

        fun u32(at: Int): Long = (0..3).fold(0L) { n, i -> (n shl 8) or (bytes[at + i].toLong() and 255) }

        fun u64(at: Int): Long? {
            if (at + 8 > bytes.size) return null
            val high = u32(at)
            if (high > 0x7fffffff) return null
            return (high shl 32) or u32(at + 4)
        }

        fun type(at: Int): String = String(bytes, at, 4, Charsets.US_ASCII)

        /** `tkhd`: the matrix at 40 (version 0; version 1: 52), the width and height after it. */
        fun trackHeader(track: Track, body: Int, end: Int) {
            val matrix = body + when (bytes[body].toInt() and 255) {
                0 -> 40
                1 -> 52
                else -> return
            }
            if (matrix + 44 > end) return
            val a = u32(matrix)
            val b = u32(matrix + 4)
            val c = u32(matrix + 12)
            val d = u32(matrix + 16)
            track.turned = a == 0L && d == 0L && b != 0L && c != 0L
            val width = (u32(matrix + 36) ushr 16).toInt()
            val height = (u32(matrix + 40) ushr 16).toInt()
            if (width > 0 && height > 0) track.header = Size(width, height)
        }

        /** `stsd`: the first entry, a visual one, its width at 32 and height at 34. */
        fun sampleEntry(track: Track, body: Int, end: Int) {
            val entry = body + 8
            if (entry + 36 > end || u32(body + 4) < 1) return
            val size = u32(entry)
            if (size < 36 || size > end - entry || type(entry + 4) !in VISUAL) return
            val width = u16(entry + 32)
            val height = u16(entry + 34)
            if (width > 0 && height > 0) track.entry = Size(width, height)
        }

        fun pictureOf(track: Track): Size? {
            val picture = when (track.handler) {
                "vide" -> true
                null -> track.header != null
                else -> false
            }
            if (!picture) return null
            val size = track.header ?: track.entry ?: return null
            val shown = if (track.turned) Size(size.height, size.width) else size
            return shown.takeIf { it.width in MIN_SIDE..MAX_SIDE && it.height in MIN_SIDE..MAX_SIDE }
        }

        /** The boxes from [begin] to [limit] (the bytes' end for a box they hold only in part). */
        fun walk(begin: Int, limit: Int, depth: Int, track: Track?) {
            var at = begin
            while (found == null && at + 8 <= limit && ++boxes <= MAX_BOXES) {
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
                val name = type(at + 4)
                when {
                    depth == 0 -> if (name == "moov") walk(body, end, 1, null)
                    depth == 1 -> if (name == "trak") {
                        val each = Track()
                        walk(body, end, 2, each)
                        found = pictureOf(each)
                    }
                    track != null -> when (name) {
                        "tkhd" -> if (depth == 2 && whole && body < end) trackHeader(track, body, end)
                        "mdia" -> if (depth == 2) walk(body, end, 3, track)
                        "hdlr" -> if (depth == 3 && whole && body + 12 <= end) track.handler = type(body + 8)
                        "minf" -> if (depth == 3) walk(body, end, 4, track)
                        "stbl" -> if (depth == 4) walk(body, end, 5, track)
                        "stsd" -> if (depth == 5 && whole) sampleEntry(track, body, end)
                    }
                }
                // Nothing after a box the bytes hold only in part is in them.
                if (!whole) return
                at = end
            }
        }

        walk(0, bytes.size, 0, null)
        return found
    }
}
