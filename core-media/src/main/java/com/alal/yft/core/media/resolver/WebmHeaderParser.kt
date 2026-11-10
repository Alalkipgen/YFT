package com.alal.yft.core.media.resolver

import java.util.Locale

/**
 * Finds a WebM (Matroska) file's tracks from the EBML elements at its start, without the media.
 *
 * One ranged read of [FIRST_READ_BYTES] is all it costs: a WebM states its length in `Info` and
 * its picture, sound and codecs in `Tracks`, both before the first `Cluster` in every file YFT
 * met, Wikimedia Commons' transcodes included. An element whose body runs past that read is read
 * as far as it goes, so a hostile file can only cost that one read. Anything the file does not
 * state stays null, and a file that is not EBML (no 0x1A45DFA3 magic) answers null.
 */
object WebmHeaderParser {
    const val FIRST_READ_BYTES = 256 * 1_024

    /** Whether [bytes] start an EBML document, which is what a WebM or Matroska file is. */
    fun isWebm(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == EBML_MAGIC_0 && bytes[1] == EBML_MAGIC_1 &&
            bytes[2] == EBML_MAGIC_2 && bytes[3] == EBML_MAGIC_3

    /** What the file's first [FIRST_READ_BYTES] state; null when they are not a WebM's. */
    fun parse(bytes: ByteArray): FileHeader? {
        if (bytes.size < MIN_READ_BYTES || !isWebm(bytes)) return null
        return Walk(bytes).run()
    }

    private class Track(
        var type: Int? = null,
        var codecId: String? = null,
        var defaultDurationNs: Long? = null,
        var pixelWidth: Int? = null,
        var pixelHeight: Int? = null,
        var displayWidth: Int? = null,
        var displayHeight: Int? = null,
    )

    private class Element(val id: Long, val from: Int, val to: Int, val unknown: Boolean)

    private class Walk(private val data: ByteArray) {
        private var docType: String? = null
        private var timecodeScaleNs = DEFAULT_TIMECODE_SCALE_NS
        private var durationUnits: Double? = null
        private val tracks = mutableListOf<Track>()

        fun run(): FileHeader? {
            var at = 0
            var count = 0
            while (at < data.size && count++ < MAX_TOP_LEVEL_ELEMENTS) {
                val element = element(at) ?: return null
                when (element.id) {
                    ID_EBML -> children(element.from, element.to) { child ->
                        if (child.id == ID_DOC_TYPE) docType = text(child.from, child.to)
                    }
                    ID_SEGMENT -> segment(element.from, element.to)
                }
                if (element.unknown || element.to <= at) return finished()
                at = element.to
            }
            return finished()
        }

        private fun segment(start: Int, end: Int) {
            children(start, end) { child ->
                when (child.id) {
                    ID_INFO -> children(child.from, child.to) { info ->
                        when (info.id) {
                            ID_TIMECODE_SCALE -> uint(info.from, info.to)
                                ?.takeIf { it > 0 }
                                ?.let { timecodeScaleNs = it }
                            ID_DURATION -> durationUnits = float(info.from, info.to)
                        }
                    }
                    ID_TRACKS -> children(child.from, child.to) { entry ->
                        if (entry.id == ID_TRACK_ENTRY) trackEntry(entry.from, entry.to)
                    }
                }
            }
        }

        private fun trackEntry(start: Int, end: Int) {
            val track = Track()
            children(start, end) { child ->
                when (child.id) {
                    ID_TRACK_TYPE -> track.type = uint(child.from, child.to)?.toInt()
                    ID_CODEC_ID -> track.codecId = text(child.from, child.to)
                    ID_DEFAULT_DURATION -> track.defaultDurationNs = uint(child.from, child.to)
                    ID_VIDEO -> children(child.from, child.to) { video ->
                        when (video.id) {
                            ID_PIXEL_WIDTH -> track.pixelWidth = dimension(video.from, video.to)
                            ID_PIXEL_HEIGHT -> track.pixelHeight = dimension(video.from, video.to)
                            ID_DISPLAY_WIDTH ->
                                track.displayWidth = dimension(video.from, video.to)
                            ID_DISPLAY_HEIGHT ->
                                track.displayHeight = dimension(video.from, video.to)
                        }
                    }
                }
            }
            tracks += track
        }

        private fun finished(): FileHeader? {
            if (docType != null && docType !in DOC_TYPES) return null
            val video = tracks.firstOrNull { it.type == TRACK_TYPE_VIDEO }
            val audio = tracks.firstOrNull { it.type == TRACK_TYPE_AUDIO }
            val width = video?.pixelWidth ?: video?.displayWidth
            val height = video?.pixelHeight ?: video?.displayHeight
            val duration = durationUnits?.let { units ->
                (units * timecodeScaleNs / NANOS_PER_MILLI).toLong().takeIf { it > 0 }
            }
            if (video == null && audio == null && duration == null) return null
            return FileHeader(
                width = width?.takeIf { it in 1..MAX_DIMENSION },
                height = height?.takeIf { it in 1..MAX_DIMENSION },
                framesPerSecond = video?.framesPerSecond(),
                durationMillis = duration,
                videoCodec = video?.codecId?.let(::videoCodec),
                audioCodec = audio?.codecId?.let(::audioCodec),
                hasVideo = video != null,
                hasAudio = audio != null,
            )
        }

        /** Walks [start] to [end], one level deep, at most [MAX_ELEMENTS] elements. */
        private inline fun children(start: Int, end: Int, onChild: (Element) -> Unit) {
            var at = start
            var count = 0
            while (at < end && count++ < MAX_ELEMENTS) {
                val child = element(at) ?: return
                if (child.from > end) return
                onChild(child)
                if (child.unknown || child.to <= at) return
                at = child.to
            }
        }

        /** The element at [at]; its body ends where the buffer ends when it runs past it. */
        private fun element(at: Int): Element? {
            val id = readId(at) ?: return null
            val size = readSize(id.end) ?: return null
            if (size.end > data.size) return null
            val declaredEnd = size.end.toLong() + size.value
            val to = when {
                size.unknown -> data.size
                declaredEnd > data.size -> data.size
                else -> declaredEnd.toInt()
            }
            return Element(id.value, size.end, to, size.unknown)
        }

        private class Field(val value: Long, val end: Int, val unknown: Boolean = false)

        /** An element id, which keeps its length marker's bits, as EBML writes it. */
        private fun readId(at: Int): Field? {
            val first = data[at].toInt() and 0xFF
            if (first == 0) return null
            var length = 1
            var mask = 0x80
            while (length <= MAX_ID_BYTES && first and mask == 0) {
                mask = mask shr 1
                length += 1
            }
            if (length > MAX_ID_BYTES || at + length > data.size) return null
            var value = first.toLong()
            for (index in 1 until length) {
                value = (value shl 8) or (data[at + index].toLong() and 0xFF)
            }
            return Field(value, at + length)
        }

        /** An element size, whose length marker's bits are not part of the value. */
        private fun readSize(at: Int): Field? {
            val first = data[at].toInt() and 0xFF
            if (first == 0) return null
            var length = 1
            var mask = 0x80
            while (length <= MAX_SIZE_BYTES && first and mask == 0) {
                mask = mask shr 1
                length += 1
            }
            if (length > MAX_SIZE_BYTES || at + length > data.size) return null
            var value = (first and (mask - 1)).toLong()
            for (index in 1 until length) {
                value = (value shl 8) or (data[at + index].toLong() and 0xFF)
            }
            val allOnes = (1L shl (7 * length)) - 1
            return Field(value, at + length, unknown = value == allOnes)
        }

        private fun uint(from: Int, to: Int): Long? {
            if (to <= from || to - from > 8) return null
            var value = 0L
            for (index in from until to) value = (value shl 8) or (data[index].toLong() and 0xFF)
            return value
        }

        private fun float(from: Int, to: Int): Double? = when (to - from) {
            4 -> uint(from, to)?.let { java.lang.Float.intBitsToFloat(it.toInt()).toDouble() }
            8 -> uint(from, to)?.let { Double.fromBits(it) }
            else -> null
        }

        private fun dimension(from: Int, to: Int): Int? =
            uint(from, to)?.toInt()?.takeIf { it in 1..MAX_DIMENSION }

        private fun text(from: Int, to: Int): String? {
            if (to <= from) return null
            return String(data, from, to - from, Charsets.UTF_8).trimEnd('\u0000').ifEmpty { null }
        }
    }

    private fun Track.framesPerSecond(): Double? {
        val durationNs = defaultDurationNs?.takeIf { it > 0 } ?: return null
        val rate = NANOS_PER_SECOND / durationNs
        if (rate !in MIN_FPS..MAX_FPS) return null
        return Math.round(rate * 100) / 100.0
    }

    /** The codec a row is named by: the standard's own name for what a WebM calls a codec id. */
    private fun videoCodec(codecId: String): String? = when (codecId.trim().uppercase(Locale.US)) {
        "V_VP8" -> "vp8"
        "V_VP9" -> "vp9"
        "V_AV1" -> "av01"
        "V_MPEG4/ISO/AVC" -> "avc1"
        "V_MPEGH/ISO/HEVC" -> "hvc1"
        else -> null
    }

    private fun audioCodec(codecId: String): String? = when (codecId.trim().uppercase(Locale.US)) {
        "A_OPUS" -> "opus"
        "A_VORBIS" -> "vorbis"
        "A_AAC" -> "mp4a.40"
        "A_MPEG/L3" -> "mp3"
        else -> null
    }

    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_DOC_TYPE = 0x4282L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_INFO = 0x1549A966L
    private const val ID_TIMECODE_SCALE = 0x2AD7B1L
    private const val ID_DURATION = 0x4489L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACK_ENTRY = 0xAEL
    private const val ID_TRACK_TYPE = 0x83L
    private const val ID_CODEC_ID = 0x86L
    private const val ID_DEFAULT_DURATION = 0x23E383L
    private const val ID_VIDEO = 0xE0L
    private const val ID_PIXEL_WIDTH = 0xB0L
    private const val ID_PIXEL_HEIGHT = 0xBAL
    private const val ID_DISPLAY_WIDTH = 0x54B0L
    private const val ID_DISPLAY_HEIGHT = 0x54BAL
    private const val TRACK_TYPE_VIDEO = 1
    private const val TRACK_TYPE_AUDIO = 2
    private const val DEFAULT_TIMECODE_SCALE_NS = 1_000_000L
    private const val NANOS_PER_MILLI = 1_000_000.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MIN_READ_BYTES = 16
    private const val MAX_ID_BYTES = 4
    private const val MAX_SIZE_BYTES = 8
    private const val MAX_ELEMENTS = 256
    private const val MAX_TOP_LEVEL_ELEMENTS = 4
    private const val MAX_DIMENSION = 16_384
    private const val MIN_FPS = 1.0
    private const val MAX_FPS = 240.0
    private val DOC_TYPES = setOf("webm", "matroska")
    private val EBML_MAGIC_0 = 0x1A.toByte()
    private val EBML_MAGIC_1 = 0x45.toByte()
    private val EBML_MAGIC_2 = 0xDF.toByte()
    private val EBML_MAGIC_3 = 0xA3.toByte()
}
