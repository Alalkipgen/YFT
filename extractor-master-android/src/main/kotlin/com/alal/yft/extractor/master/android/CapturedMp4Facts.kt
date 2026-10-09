package com.alal.yft.extractor.master.android

/** Bounded ISO-BMFF metadata only. Never decodes, plays or seeks a video. */
internal object CapturedMp4Facts {
    data class Facts(
        val durationMillis: Long?,
        val width: Int?,
        val height: Int?,
        val protected: Boolean,
        val audioOnly: Boolean = false,
    )

    fun read(bytes: ByteArray): Facts? {
        var duration: Long? = null
        var movieScale = 0L
        var extendedTicks: Long? = null
        var width: Int? = null
        var height: Int? = null
        var protected = false
        var audioTrack = false
        var videoTrack = false
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
                    "moov", "trak", "mdia", "minf", "stbl", "schi", "mvex" ->
                        walk(body, end, depth + 1)
                    "stsd" -> if (body + 8 <= end) walk(body + 8, end, depth + 1)
                    "mvhd" -> {
                        val version = bytes.getOrNull(body)?.toInt()?.and(255) ?: -1
                        val scaleAt = body + if (version == 1) 20 else 12
                        val durationAt = scaleAt + 4
                        val needed = if (version == 1) 8 else 4
                        if (version in 0..1 && durationAt + needed <= end) {
                            val scale = u32(scaleAt)
                            movieScale = scale
                            val ticks = if (version == 1) u64(durationAt) else u32(durationAt)
                            if (scale > 0 && ticks != null && ticks in 1..172_800L * scale) {
                                duration = ticks * 1_000 / scale
                            }
                        }
                    }
                    "mehd" -> {
                        val version = bytes.getOrNull(body)?.toInt()?.and(255) ?: -1
                        if (version == 0 && body + 8 <= end) extendedTicks = u32(body + 4)
                        if (version == 1 && body + 12 <= end) extendedTicks = u64(body + 4)
                    }
                    "sidx" -> {
                        val version = bytes.getOrNull(body)?.toInt()?.and(255) ?: -1
                        val countAt = body + if (version == 1) 30 else 22
                        if (version in 0..1 && countAt + 2 <= end) {
                            val scale = u32(body + 8)
                            val count = ((bytes[countAt].toInt() and 255) shl 8) or
                                (bytes[countAt + 1].toInt() and 255)
                            var ticks = 0L
                            var direct = count > 0 && count <= 2_048 &&
                                countAt + 2L + count * 12L <= end
                            if (direct) repeat(count) { index ->
                                val entry = countAt + 2 + index * 12
                                if (u32(entry) and 0x80000000L != 0L) direct = false
                                ticks += u32(entry + 4)
                            }
                            if (duration == null && direct && scale > 0 &&
                                ticks in 1..172_800L * scale
                            ) duration = ticks * 1_000 / scale
                        }
                    }
                    "hdlr" -> if (body + 12 <= end) {
                        when (String(bytes, body + 8, 4, Charsets.US_ASCII)) {
                            "soun" -> audioTrack = true
                            "vide" -> videoTrack = true
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
        val ticks = extendedTicks
        if (duration == null && movieScale > 0 && ticks != null &&
            ticks in 1..172_800L * movieScale
        ) duration = ticks * 1_000 / movieScale
        return if (duration == null && width == null && !protected && !audioTrack) null
        else Facts(duration, width, height, protected, audioTrack && !videoTrack)
    }
}