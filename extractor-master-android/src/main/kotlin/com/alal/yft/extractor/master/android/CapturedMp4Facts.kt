package com.alal.yft.extractor.master.android

/** Bounded ISO-BMFF metadata only. Never decodes, plays or seeks a video. */
internal object CapturedMp4Facts {
    data class Facts(
        val durationMillis: Long?,
        val width: Int?,
        val height: Int?,
        val protected: Boolean,
    )

    fun read(bytes: ByteArray): Facts? {
        var duration: Long? = null
        var width: Int? = null
        var height: Int? = null
        var protected = false
        var nodes = 0
        fun u32(at: Int): Long = (0..3).fold(0L) { n, i ->
            (n shl 8) or (bytes[at + i].toLong() and 255)
        }
        fun u64(at: Int): Long? {
            val hi = u32(at)
            if (hi > 0x7fffffff) return null
            return (hi shl 32) or u32(at + 4)
        }
        fun walk(begin: Int, limit: Int, depth: Int) {
            if (depth > 12) return
            var at = begin
            while (at + 8 <= limit && ++nodes <= 2_048) {
                val size = u32(at)
                val extended = size == 1L
                val header = if (extended) 16 else 8
                if (at + header > limit) break
                val length = when {
                    extended -> u64(at + 8) ?: break
                    size == 0L -> (limit - at).toLong()
                    else -> size
                }
                if (length < header || length > limit - at) break
                val end = at + length.toInt()
                val body = at + header
                val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
                when (type) {
                    "pssh", "sinf", "schm", "tenc", "encv", "enca" -> protected = true
                    "moov", "trak", "mdia", "minf", "stbl", "schi" ->
                        walk(body, end, depth + 1)
                    "stsd" -> if (body + 8 <= end) walk(body + 8, end, depth + 1)
                    "mvhd" -> {
                        val version = bytes.getOrNull(body)?.toInt()?.and(255) ?: -1
                        val scaleAt = body + if (version == 1) 20 else 12
                        val durationAt = scaleAt + 4
                        val needed = if (version == 1) 8 else 4
                        if (version in 0..1 && durationAt + needed <= end) {
                            val scale = u32(scaleAt)
                            val ticks = if (version == 1) u64(durationAt) else u32(durationAt)
                            if (scale > 0 && ticks != null && ticks in 1..172_800L * scale) {
                                duration = ticks * 1_000 / scale
                            }
                        }
                    }
                    "tkhd" -> {
                        if (end - body >= 84) {
                            val w = (u32(end - 8) ushr 16).toInt()
                            val h = (u32(end - 4) ushr 16).toInt()
                            if (w in 1..16_384 && h in 1..16_384 &&
                                w.toLong() * h > (width ?: 0).toLong() * (height ?: 0)
                            ) { width = w; height = h }
                        }
                    }
                }
                at = end
            }
        }
        walk(0, bytes.size, 0)
        // A suffix range can begin inside mdat. Accept only a complete, bounded moov box.
        if (duration == null) {
            for (at in 0 until (bytes.size - 8).coerceAtLeast(0)) {
                if (String(bytes, at + 4, 4, Charsets.US_ASCII) != "moov") continue
                val size = u32(at)
                if (size in 8..(bytes.size - at).toLong()) {
                    walk(at, at + size.toInt(), 0)
                    if (duration != null) break
                }
            }
        }
        return if (duration == null && width == null && !protected) null
        else Facts(duration, width, height, protected)
    }
}