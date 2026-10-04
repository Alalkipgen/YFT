package com.alal.yft.core.media.resolver

import java.util.Locale

/**
 * What the boxes at the start of an MP4 file say about its picture and sound (P3).
 *
 * Only values the file states are returned; anything it does not state stays null.
 */
data class Mp4Header(
    val width: Int? = null,
    val height: Int? = null,
    val framesPerSecond: Double? = null,
    val durationMillis: Long? = null,
    /** The video sample entry, for example `avc1`, `hvc1` or `av01`. */
    val videoCodec: String? = null,
    /** The audio sample entry, `mp4a.40.2` for AAC-LC. */
    val audioCodec: String? = null,
    val audioBitrateBitsPerSecond: Long? = null,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
)

/** Reads [length] bytes of one file from [offset]; null when the range cannot be read. */
fun interface RangeReader {
    suspend fun read(offset: Long, length: Int): ByteArray?
}

/**
 * Finds an MP4 file's tracks from its `moov` box without downloading the media (P3).
 *
 * The first read covers [FIRST_READ_BYTES]; a `moov` after the media data or a track beyond the
 * first read costs one small ranged read each, at most [MAX_READS] in all. Malformed or truncated
 * boxes end the walk with what was read so far, so a hostile file can only cost those reads.
 */
object Mp4HeaderParser {
    const val FIRST_READ_BYTES = 256 * 1_024
    const val NEXT_READ_BYTES = 32 * 1_024
    const val MAX_READS = 6

    suspend fun parse(reader: RangeReader, totalBytes: Long?): Mp4Header? {
        val file = CachedFile(reader, totalBytes)
        val first = file.header(0) ?: return null
        if (first.type !in FIRST_BOX_TYPES) return null
        var offset = 0L
        var boxes = 0
        while (boxes++ < MAX_TOP_LEVEL_BOXES) {
            val box = file.header(offset) ?: return null
            if (box.type == "moov") return file.moov(box)
            val end = box.end ?: return null
            if (end <= offset || (totalBytes != null && end >= totalBytes)) return null
            offset = end
        }
        return null
    }

    private class Box(val type: String, val start: Long, val headerSize: Int, val end: Long?) {
        val payload: Long get() = start + headerSize
    }

    private class Track(
        var handler: String? = null,
        var width: Int? = null,
        var height: Int? = null,
        var timescale: Long? = null,
        var durationUnits: Long? = null,
        var sampleDelta: Long? = null,
        var codec: String? = null,
        var entryWidth: Int? = null,
        var entryHeight: Int? = null,
        var bitrate: Long? = null,
    )

    private class CachedFile(private val reader: RangeReader, private val totalBytes: Long?) {
        private val chunks = mutableListOf<Pair<Long, ByteArray>>()
        private var reads = 0

        /** [length] bytes at [offset], reading more of the file when the cache lacks them. */
        suspend fun bytes(offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length <= 0) return null
            if (totalBytes != null && offset + length > totalBytes) return null
            cached(offset, length)?.let { return it }
            if (reads >= MAX_READS) return null
            val size = maxOf(length, if (reads == 0) FIRST_READ_BYTES else NEXT_READ_BYTES)
            val wanted = totalBytes?.let { minOf(size.toLong(), it - offset).toInt() } ?: size
            reads += 1
            val read = reader.read(offset, wanted) ?: return null
            chunks += offset to read
            return cached(offset, length)
        }

        private fun cached(offset: Long, length: Int): ByteArray? {
            chunks.forEach { (start, data) ->
                val from = offset - start
                if (from >= 0 && from + length <= data.size) {
                    return data.copyOfRange(from.toInt(), from.toInt() + length)
                }
            }
            return null
        }

        suspend fun header(offset: Long): Box? {
            val head = bytes(offset, BOX_HEADER) ?: return null
            val size32 = head.u32(0)
            val type = String(head, 4, 4, Charsets.ISO_8859_1)
            if (!type.all { it.isLetterOrDigit() || it == ' ' || it == '-' }) return null
            return when (size32) {
                0L -> Box(type, offset, BOX_HEADER, totalBytes)
                1L -> {
                    val large = bytes(offset + BOX_HEADER, LARGE_SIZE) ?: return null
                    val size = large.u64(0).takeIf { it >= BOX_HEADER + LARGE_SIZE } ?: return null
                    Box(type, offset, BOX_HEADER + LARGE_SIZE, offset + size)
                }
                else -> if (size32 < BOX_HEADER) {
                    null
                } else {
                    Box(type, offset, BOX_HEADER, offset + size32)
                }
            }
        }

        /** The child boxes of [parent], read lazily, at most [MAX_CHILDREN]. */
        suspend fun children(parent: Box, onChild: suspend (Box) -> Unit) {
            val end = parent.end ?: return
            var offset = parent.payload
            var count = 0
            while (offset + BOX_HEADER <= end && count++ < MAX_CHILDREN) {
                val child = header(offset) ?: return
                val childEnd = child.end ?: return
                if (childEnd <= offset || childEnd > end) return
                onChild(child)
                offset = childEnd
            }
        }

        suspend fun moov(moov: Box): Mp4Header? {
            var movieTimescale: Long? = null
            var movieDuration: Long? = null
            val tracks = mutableListOf<Track>()
            children(moov) { child ->
                when (child.type) {
                    "mvhd" -> fullBox(child, 32)?.let { (version, data) ->
                        val timescaleAt = if (version == 1) 20 else 12
                        movieTimescale = data.u32(timescaleAt)
                        movieDuration = if (version == 1) {
                            data.u64(timescaleAt + 4)
                        } else {
                            data.u32(timescaleAt + 4)
                        }
                    }
                    "trak" -> tracks += track(child)
                }
            }
            val video = tracks.firstOrNull { it.handler == "vide" }
            val audio = tracks.firstOrNull { it.handler == "soun" }
            if (video == null && audio == null) return null
            val duration = movieDuration?.let { units ->
                movieTimescale?.takeIf { it > 0 }?.let { units * MILLIS / it }
            } ?: (video ?: audio)?.durationMillis()
            return Mp4Header(
                width = video?.let { it.width ?: it.entryWidth },
                height = video?.let { it.height ?: it.entryHeight },
                framesPerSecond = video?.framesPerSecond(),
                durationMillis = duration?.takeIf { it > 0 },
                videoCodec = video?.codec,
                audioCodec = audio?.codec,
                audioBitrateBitsPerSecond = audio?.bitrate,
                hasVideo = video != null,
                hasAudio = audio != null,
            )
        }

        private suspend fun track(trak: Box): Track {
            val track = Track()
            children(trak) { child ->
                when (child.type) {
                    "tkhd" -> fullBox(child, 96)?.let { (version, data) ->
                        val widthAt = if (version == 1) 88 else 76
                        track.width = data.fixed16(widthAt)
                        track.height = data.fixed16(widthAt + 4)
                    }
                    "mdia" -> media(child, track)
                }
            }
            return track
        }

        private suspend fun media(mdia: Box, track: Track) {
            children(mdia) { child ->
                when (child.type) {
                    "mdhd" -> fullBox(child, 32)?.let { (version, data) ->
                        val timescaleAt = if (version == 1) 20 else 12
                        track.timescale = data.u32(timescaleAt)
                        track.durationUnits = if (version == 1) {
                            data.u64(timescaleAt + 4)
                        } else {
                            data.u32(timescaleAt + 4)
                        }
                    }
                    "hdlr" -> bytes(child.payload, 12)?.let { data ->
                        track.handler = String(data, 8, 4, Charsets.ISO_8859_1)
                    }
                    "minf" -> children(child) { info ->
                        if (info.type == "stbl") sampleTable(info, track)
                    }
                }
            }
        }

        private suspend fun sampleTable(stbl: Box, track: Track) {
            children(stbl) { child ->
                when (child.type) {
                    "stsd" -> sampleDescription(child, track)
                    "stts" -> bytes(child.payload, 16)?.let { data ->
                        if (data.u32(4) >= 1) track.sampleDelta = data.u32(12).takeIf { it > 0 }
                    }
                }
            }
        }

        private suspend fun sampleDescription(stsd: Box, track: Track) {
            val entry = header(stsd.payload + 8) ?: return
            val entryEnd = entry.end ?: return
            val type = entry.type
            when (track.handler) {
                "vide" -> {
                    track.codec = type.trim().lowercase(Locale.US)
                    bytes(entry.payload, VISUAL_ENTRY_BYTES)?.let { data ->
                        track.entryWidth = data.u16(24).takeIf { it > 0 }
                        track.entryHeight = data.u16(26).takeIf { it > 0 }
                    }
                }
                "soun" -> {
                    if (type != "mp4a") {
                        track.codec = type.trim().lowercase(Locale.US)
                        return
                    }
                    track.codec = "mp4a"
                    // QuickTime sound entries 1 and 2 carry 16 or 36 more bytes before the boxes.
                    val version = bytes(entry.payload + 8, 2)?.u16(0) ?: return
                    val extra = when (version) {
                        1 -> 16
                        2 -> 36
                        else -> 0
                    }
                    var offset = entry.payload + AUDIO_ENTRY_BYTES + extra
                    var count = 0
                    while (offset + BOX_HEADER <= entryEnd && count++ < MAX_CHILDREN) {
                        val child = header(offset) ?: return
                        val childEnd = child.end ?: return
                        if (childEnd <= offset || childEnd > entryEnd) return
                        when (child.type) {
                            "esds" -> {
                                val length = (childEnd - child.payload).toInt()
                                    .coerceAtMost(MAX_ESDS_BYTES)
                                bytes(child.payload, length)?.let { esds(it, track) }
                            }
                            "btrt" -> bytes(child.payload, 12)?.let { data ->
                                track.bitrate = track.bitrate ?: data.u32(8).takeIf { it > 0 }
                            }
                        }
                        offset = childEnd
                    }
                }
            }
        }

        /** The ES descriptor: AAC's object type and the average bitrate. */
        private fun esds(data: ByteArray, track: Track) {
            var position = 4
            fun length(): Int? {
                var value = 0
                repeat(4) {
                    if (position >= data.size) return null
                    val byte = data[position++].toInt() and 0xFF
                    value = (value shl 7) or (byte and 0x7F)
                    if (byte and 0x80 == 0) return value
                }
                return value
            }
            if (position >= data.size || data[position++].toInt() != ES_DESCRIPTOR) return
            length() ?: return
            if (position + 3 > data.size) return
            val flags = data[position + 2].toInt() and 0xFF
            position += 3
            if (flags and 0x80 != 0) position += 2
            if (flags and 0x40 != 0) {
                if (position >= data.size) return
                position += 1 + (data[position].toInt() and 0xFF)
            }
            if (flags and 0x20 != 0) position += 2
            if (position >= data.size || data[position++].toInt() != DECODER_CONFIG) return
            length() ?: return
            if (position + 13 > data.size) return
            val objectType = data[position].toInt() and 0xFF
            val average = data.u32(position + 9)
            track.bitrate = average.takeIf { it > 0 } ?: track.bitrate
            position += 13
            var audioObjectType: Int? = null
            if (position < data.size && data[position].toInt() == DECODER_SPECIFIC) {
                position += 1
                length()
                if (position < data.size) {
                    audioObjectType = (data[position].toInt() and 0xFF) shr 3
                }
            }
            if (objectType == AAC_OBJECT_TYPE) {
                track.codec = audioObjectType?.takeIf { it > 0 }
                    ?.let { "mp4a.40.$it" } ?: "mp4a.40"
            }
        }

        /** A full box's version and the first [length] bytes of its payload. */
        private suspend fun fullBox(box: Box, length: Int): Pair<Int, ByteArray>? {
            val end = box.end ?: return null
            val available = (end - box.payload).coerceAtMost(length.toLong()).toInt()
            if (available < 4) return null
            val data = bytes(box.payload, available) ?: return null
            val padded = if (data.size < length) data.copyOf(length) else data
            return (data[0].toInt() and 0xFF) to padded
        }
    }

    private fun Track.durationMillis(): Long? {
        val scale = timescale?.takeIf { it > 0 } ?: return null
        return durationUnits?.let { it * MILLIS / scale }
    }

    private fun Track.framesPerSecond(): Double? {
        val scale = timescale?.takeIf { it > 0 } ?: return null
        val delta = sampleDelta?.takeIf { it > 0 } ?: return null
        val rate = scale.toDouble() / delta
        if (rate !in MIN_FPS..MAX_FPS) return null
        return Math.round(rate * 100) / 100.0
    }

    private fun ByteArray.u16(at: Int): Int =
        ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

    private fun ByteArray.u32(at: Int): Long =
        ((this[at].toLong() and 0xFF) shl 24) or ((this[at + 1].toLong() and 0xFF) shl 16) or
            ((this[at + 2].toLong() and 0xFF) shl 8) or (this[at + 3].toLong() and 0xFF)

    private fun ByteArray.u64(at: Int): Long = (u32(at) shl 32) or u32(at + 4)

    /** A 16.16 fixed-point size, or null when it is zero or implausible. */
    private fun ByteArray.fixed16(at: Int): Int? =
        (u32(at) shr 16).toInt().takeIf { it in 1..MAX_DIMENSION }

    private const val BOX_HEADER = 8
    private const val LARGE_SIZE = 8
    private const val MAX_TOP_LEVEL_BOXES = 16
    private const val MAX_CHILDREN = 64
    private const val VISUAL_ENTRY_BYTES = 28
    private const val AUDIO_ENTRY_BYTES = 28
    private const val MAX_ESDS_BYTES = 256
    private const val ES_DESCRIPTOR = 0x03
    private const val DECODER_CONFIG = 0x04
    private const val DECODER_SPECIFIC = 0x05
    private const val AAC_OBJECT_TYPE = 0x40
    private const val MAX_DIMENSION = 16_384
    private const val MILLIS = 1_000L
    private const val MIN_FPS = 1.0
    private const val MAX_FPS = 240.0
    private val FIRST_BOX_TYPES = setOf("ftyp", "moov", "free", "skip", "wide", "mdat", "pdin")
}
