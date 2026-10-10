package com.alal.yft.extractor.master.verify

/**
 * R4: segment timing only — DASH/ISO-BMFF `sidx` subsegment durations and HLS `#EXTINF` piece
 * lengths, read from bytes or text that were already fetched within the metadata budget. Never
 * decodes, plays or seeks a video and never requests anything itself.
 *
 * A [Timeline]'s cues are the start of every segment after the first. Each DASH subsegment and
 * each HLS piece starts on a keyframe, so qualities cut from one video share the cues while an
 * ad, preview or related clip of a similar length does not.
 */
object SegmentIndexReader {
    data class Timeline(
        val durationMillis: Long,
        val cuesMillis: List<Long>,
        /** HLS only: the playlist names a DRM key (SAMPLE-AES or a non-identity key format). */
        val protected: Boolean = false,
    )

    const val MAX_CUES = 512
    private const val MAX_SECONDS = 172_800L
    private const val MAX_TOP_LEVEL_BOXES = 64

    /** The first complete top-level `sidx` box of [bytes] (an on-demand DASH file's index). */
    fun sidx(bytes: ByteArray): Timeline? {
        var at = 0
        var boxes = 0
        while (at + 8 <= bytes.size && ++boxes <= MAX_TOP_LEVEL_BOXES) {
            val size = u32(bytes, at)
            val header = if (size == 1L) 16 else 8
            if (at + header > bytes.size) return null
            val length = when (size) {
                1L -> u64(bytes, at + 8) ?: return null
                0L -> (bytes.size - at).toLong()
                else -> size
            }
            if (length < header || length > bytes.size - at) return null
            if (String(bytes, at + 4, 4, Charsets.US_ASCII) == "sidx") {
                return sidxBody(bytes, at + header, at + length.toInt())
            }
            at += length.toInt()
        }
        return null
    }

    /**
     * The body of one `sidx` box ([body] until [end]). Only an index whose every reference is a
     * media subsegment counts (a hierarchical index points at other indexes, not at media).
     */
    internal fun sidxBody(bytes: ByteArray, body: Int, end: Int): Timeline? {
        val version = bytes.getOrNull(body)?.toInt()?.and(255) ?: return null
        if (version !in 0..1) return null
        val countAt = body + if (version == 1) 30 else 22
        if (countAt + 2 > end) return null
        val scale = u32(bytes, body + 8)
        val count = ((bytes[countAt].toInt() and 255) shl 8) or (bytes[countAt + 1].toInt() and 255)
        if (scale <= 0 || count !in 1..2_048 || countAt + 2L + count * 12L > end) return null
        var ticks = 0L
        val cues = ArrayList<Long>(minOf(count, MAX_CUES))
        for (index in 0 until count) {
            val entry = countAt + 2 + index * 12
            if (u32(bytes, entry) and 0x80000000L != 0L) return null
            if (index > 0 && cues.size < MAX_CUES) cues += ticks * 1_000 / scale
            ticks += u32(bytes, entry + 4)
        }
        if (ticks !in 1..MAX_SECONDS * scale) return null
        return Timeline(ticks * 1_000 / scale, cues)
    }

    /**
     * An ended HLS media playlist's length and piece starts; null for a master playlist, a live
     * or event playlist without its end, or text that is not HLS.
     */
    fun extinf(text: String): Timeline? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull()?.removePrefix("\uFEFF") != "#EXTM3U") return null
        var seconds = 0.0
        var pieces = 0
        var ended = false
        var protected = false
        val cues = ArrayList<Long>()
        for (line in lines.drop(1)) {
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> return null
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val piece = line.substringAfter(':').substringBefore(',').trim()
                        .toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
                    if (pieces > 0 && cues.size < MAX_CUES) cues += Math.round(seconds * 1_000)
                    seconds += piece
                    pieces++
                }
                line.equals("#EXT-X-ENDLIST", ignoreCase = true) ||
                    line.equals("#EXT-X-PLAYLIST-TYPE:VOD", ignoreCase = true) -> ended = true
                line.startsWith("#EXT-X-KEY", ignoreCase = true) ||
                    line.startsWith("#EXT-X-SESSION-KEY", ignoreCase = true) ->
                    if (DRM_KEY.containsMatchIn(line)) protected = true
            }
        }
        val millis = Math.round(seconds * 1_000)
        if (protected) return Timeline(millis, cues, protected = true)
        if (!ended || pieces == 0 || millis !in 1..MAX_SECONDS * 1_000) return null
        return Timeline(millis, cues)
    }

    /** Same DRM key rule the validator applies to a manifest it reads. */
    private val DRM_KEY = Regex("""(?i)METHOD=SAMPLE-AES|KEYFORMAT\s*=\s*"(?!identity")[^"]+"""")

    private fun u32(bytes: ByteArray, at: Int): Long = (0..3).fold(0L) { n, i ->
        (n shl 8) or (bytes[at + i].toLong() and 255)
    }

    private fun u64(bytes: ByteArray, at: Int): Long? {
        if (at + 8 > bytes.size) return null
        val hi = u32(bytes, at)
        if (hi > 0x7fffffff) return null
        return (hi shl 32) or u32(bytes, at + 4)
    }
}
