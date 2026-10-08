package com.alal.yft.core.download

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** The bytes of one merge input (P35), read at any position: a file, or a test's virtual one. */
internal interface Mp4Source {
    val size: Long

    /** Fills [target]'s remaining bytes from [position] on; throws when the input ends first. */
    fun readFully(target: ByteBuffer, position: Long)
}

/** A track file read through its channel, without Android (P35). */
internal class FileChannelMp4Source(private val channel: FileChannel) : Mp4Source {
    override val size: Long = channel.size()

    override fun readFully(target: ByteBuffer, position: Long) {
        var at = position
        while (target.hasRemaining()) {
            val read = channel.read(target, at)
            if (read < 0) throw EOFException("Track file ended early")
            at += read
        }
    }
}

/** Why a track is not stream-copied (P35), in a few fixed words; today's way merges instead. */
internal class StreamCopyUnsupported(reason: String) : Exception(reason)

internal fun unsupported(reason: String): Nothing = throw StreamCopyUnsupported(reason)

/** Which track a merge input holds (P35), with its `hdlr` type and the sample entries copied. */
internal enum class StreamCopyTrackKind(val handler: String, val sampleEntries: Set<String>) {
    VIDEO("vide", setOf("avc1", "avc3")),
    AUDIO("soun", setOf("mp4a")),
}

/** A box read into memory (P35): its [type] and its payload, [start] until [end] of [data]. */
internal class Mp4Box(
    val type: String,
    private val data: ByteBuffer,
    val start: Int,
    val end: Int,
) {
    val payloadSize: Int get() = end - start
    val version: Int get() = u8(0)
    val flags: Int get() = s32(0) and 0xffffff

    /** The boxes inside, from [skip] payload bytes on. */
    fun children(skip: Int = 0): List<Mp4Box> {
        if (skip > payloadSize) unsupported("short $type box")
        return mp4Boxes(data, start + skip, end, type)
    }

    fun u8(at: Int): Int = data.get(index(at, 1)).toInt() and 0xff
    fun u16(at: Int): Int = data.getShort(index(at, 2)).toInt() and 0xffff
    fun s32(at: Int): Int = data.getInt(index(at, 4))
    fun u32(at: Int): Long = s32(at).toLong() and UINT32
    fun s64(at: Int): Long = data.getLong(index(at, 8))
    fun s16(at: Int): Int = data.getShort(index(at, 2)).toInt()
    fun fourcc(at: Int): String = fourcc(data, index(at, 4))

    /** Payload bytes [from] until [until]. */
    fun bytes(from: Int = 0, until: Int = payloadSize): ByteArray {
        val array = ByteArray(until - from)
        val view = data.duplicate()
        view.position(index(from, until - from))
        view.get(array)
        return array
    }

    /** This box again with an 8-byte header, as the merged file keeps it. */
    fun withHeader(): ByteArray {
        val box = ByteBuffer.allocate(BOX_HEADER + payloadSize)
        box.putInt(BOX_HEADER + payloadSize)
        box.put(type.toByteArray(Charsets.ISO_8859_1))
        box.put(bytes())
        return box.array()
    }

    private fun index(at: Int, length: Int): Int {
        if (at < 0 || length < 0 || at.toLong() + length > payloadSize) {
            unsupported("short $type box")
        }
        return start + at
    }
}

/** The boxes from [from] until [to] of [data]; a size that does not fit is "not supported". */
internal fun mp4Boxes(data: ByteBuffer, from: Int, to: Int, parent: String): List<Mp4Box> {
    val boxes = ArrayList<Mp4Box>()
    var at = from
    while (at < to) {
        if (to - at < BOX_HEADER) unsupported("broken box in $parent")
        val size32 = data.getInt(at).toLong() and UINT32
        val type = fourcc(data, at + 4)
        var header = BOX_HEADER
        val size = if (size32 == 1L) {
            if (to - at < LARGE_BOX_HEADER) unsupported("broken box in $parent")
            header = LARGE_BOX_HEADER
            data.getLong(at + BOX_HEADER)
        } else {
            size32
        }
        if (size < header || size > to - at) unsupported("broken size of $type")
        boxes += Mp4Box(type, data, at + header, (at + size).toInt())
        at += size.toInt()
    }
    return boxes
}

internal fun fourcc(data: ByteBuffer, at: Int): String {
    val chars = CharArray(4) { index -> (data.get(at + index).toInt() and 0xff).toChar() }
    return String(chars)
}

internal fun List<Mp4Box>.first(type: String): Mp4Box? = firstOrNull { it.type == type }

/**
 * One track of a merge input (P35), with every sample in decode order: where it is in the input
 * ([offsets], [sizes]), how long it lasts ([durations], in [timescale] ticks), its composition
 * offset (null when all are 0) and whether it is a sync sample (null when all are).
 */
internal class Mp4Track(
    val kind: StreamCopyTrackKind,
    val timescale: Long,
    /** The `mdhd` language, packed as in the input. */
    val language: Int,
    /** The input's `stsd` box, header included, written into the merged file as it is. */
    val sampleDescription: ByteArray,
    /** The length-prefix size of the video's NAL units (AVC), or null for sound. */
    val nalLengthSize: Int?,
    /** The input `tkhd`'s 16.16 width and height and its 36-byte matrix (video). */
    val width: Int,
    val height: Int,
    val matrix: ByteArray?,
    /**
     * Where the input's edit list puts the media (P35), in ticks: a sample is presented at its
     * decode time + composition offset + [editShift].
     */
    val editShift: Long,
    /** The first sample's decode time, in ticks ("tfdt" of the first fragment, else 0). */
    val firstDecodeTime: Long,
    val count: Int,
    val offsets: LongArray,
    val sizes: IntArray,
    val durations: IntArray,
    val compositionOffsets: IntArray?,
    val sync: BooleanArray?,
) {
    val totalBytes: Long = (0 until count).sumOf { sizes[it].toLong() }

    /** The sum of the sample durations, in ticks. */
    val mediaDuration: Long = (0 until count).sumOf { durations[it].toLong() }

    fun compositionOffset(index: Int): Int = compositionOffsets?.get(index) ?: 0

    fun isSync(index: Int): Boolean = sync?.get(index) ?: true
}

/**
 * Reads one merge input for the stream copy (P35) in plain Kotlin: its one track's description
 * and the place, size, duration, composition offset and sync flag of every sample, from a
 * fragmented MP4 (`moof` › `traf` › `tfhd`, `tfdt`, `trun`) or a plain one (`stbl` tables).
 * Anything it does not expect — encryption, more than one track, a codec the merge does not
 * copy, broken sizes, sample data outside an `mdat` — is "not supported"
 * ([StreamCopyUnsupported]).
 */
internal class Mp4TrackReader(
    private val source: Mp4Source,
    private val kind: StreamCopyTrackKind,
    private val maxSamples: Int = MAX_SAMPLES,
) {
    private val window = Window(source)
    private val mdats = RangeList()
    private val runs = RangeList()
    private var init: TrackInit? = null
    private val samples = SampleBuilder()
    private var plain: Mp4Track? = null

    fun read(): Mp4Track {
        var at = 0L
        while (at < source.size) {
            val remaining = source.size - at
            if (remaining < BOX_HEADER) unsupported("broken box at the end")
            val header = window.bytes(at, minOf(remaining, LARGE_BOX_HEADER.toLong()).toInt())
            val view = ByteBuffer.wrap(header)
            val size32 = view.getInt(0).toLong() and UINT32
            val type = fourcc(view, 4)
            var headerSize = BOX_HEADER
            val size = when (size32) {
                0L -> remaining
                1L -> {
                    if (header.size < LARGE_BOX_HEADER) unsupported("broken box at the end")
                    headerSize = LARGE_BOX_HEADER
                    view.getLong(BOX_HEADER)
                }
                else -> size32
            }
            if (size < headerSize || size > remaining) unsupported("broken size of $type")
            when (type) {
                "moov" -> readMoov(at + headerSize, size - headerSize)
                "moof" -> readMoof(at, headerSize, size)
                "mdat" -> mdats.add(at + headerSize, at + size)
            }
            at += size
        }
        val init = init ?: unsupported("no moov")
        val track = plain ?: samples.build(init)
        if (track.count == 0) unsupported("no samples")
        if (!runs.allInside(mdats)) unsupported("sample data outside mdat")
        return track
    }

    private fun readMoov(position: Long, length: Long) {
        if (init != null) unsupported("two moov boxes")
        if (length > MAX_MOOV_BYTES) unsupported("moov too large")
        val bytes = window.bytes(position, length.toInt())
        val moov = Mp4Box("moov", ByteBuffer.wrap(bytes), 0, bytes.size)
        var movieTimescale = 0L
        val traks = mutableListOf<Mp4Box>()
        var mvex: Mp4Box? = null
        for (child in moov.children()) {
            when (child.type) {
                "mvhd" -> movieTimescale = child.u32(if (child.version == 1) 20 else 12)
                "trak" -> traks += child
                "mvex" -> mvex = child
                "pssh" -> unsupported("encrypted")
            }
        }
        val trak = traks.singleOrNull() ?: unsupported("${traks.size} tracks in one file")
        val parsed = TrackInit.parse(trak, kind, movieTimescale, mvex)
        init = parsed
        readPlainTables(parsed, parsed.stbl)
    }

    private fun readPlainTables(init: TrackInit, stbl: Mp4Box) {
        val tables = stbl.children()
        val stsz = tables.first("stsz")
        val stz2 = tables.first("stz2")
        val count = when {
            stsz != null -> stsz.u32(8)
            stz2 != null -> stz2.u32(8)
            else -> 0L
        }
        if (count == 0L) return
        if (init.fragmented) unsupported("samples in moov and in fragments")
        if (count > maxSamples) unsupported("too many samples")
        plain = PlainTables(init, tables, count.toInt()).read(runs)
    }

    private fun readMoof(position: Long, headerSize: Int, size: Long) {
        val init = init ?: unsupported("moof before moov")
        if (!init.fragmented) unsupported("moof without mvex")
        if (size > MAX_MOOF_BYTES) unsupported("moof too large")
        val bytes = window.bytes(position, size.toInt())
        val moof = Mp4Box("moof", ByteBuffer.wrap(bytes), headerSize, bytes.size)
        val trafs = mutableListOf<Mp4Box>()
        for (child in moof.children()) {
            when (child.type) {
                "traf" -> trafs += child
                "pssh" -> unsupported("encrypted")
            }
        }
        val traf = trafs.singleOrNull() ?: unsupported("${trafs.size} track fragments")
        readTraf(init, traf, moofStart = position)
    }

    private fun readTraf(init: TrackInit, traf: Mp4Box, moofStart: Long) {
        val boxes = traf.children()
        for (box in boxes) {
            when (box.type) {
                "senc", "saiz", "saio" -> unsupported("encrypted")
                "sgpd", "sbgp" -> if (box.groupingType() == "seig") unsupported("encrypted")
                "uuid" -> if (box.payloadSize >= 16 && box.bytes(0, 16).contentEquals(PIFF)) {
                    unsupported("encrypted")
                }
            }
        }
        val tfhd = boxes.first("tfhd") ?: unsupported("no tfhd")
        val header = FragmentHeader.parse(tfhd, init)
        boxes.first("tfdt")?.let { tfdt ->
            samples.decodeTimeAt(if (tfdt.version == 1) tfdt.s64(4) else tfdt.u32(4))
        }
        // One track fragment per moof: its data starts at the moof unless tfhd says where.
        val base = header.baseDataOffset ?: moofStart
        var dataEnd = base
        for (trun in boxes.filter { it.type == "trun" }) {
            dataEnd = readTrun(trun, header, base, dataEnd)
        }
    }

    /** Adds [trun]'s samples; returns where its data ends, where a next run without offset goes. */
    private fun readTrun(
        trun: Mp4Box,
        header: FragmentHeader,
        base: Long,
        previousEnd: Long,
    ): Long {
        val flags = trun.flags
        val count = trun.u32(4)
        if (samples.count + count > maxSamples) unsupported("too many samples")
        var at = 8
        val dataStart = if (flags and TRUN_DATA_OFFSET != 0) {
            base + trun.s32(at).also { at += 4 }
        } else {
            previousEnd
        }
        val firstFlags = if (flags and TRUN_FIRST_FLAGS != 0) {
            trun.s32(at).also { at += 4 }
        } else {
            null
        }
        val perSample = PER_SAMPLE_FIELDS.count { flags and it != 0 } * 4
        if (at + count * perSample > trun.payloadSize) unsupported("short trun box")
        var position = dataStart
        for (index in 0 until count.toInt()) {
            val duration = if (flags and TRUN_DURATION != 0) {
                trun.u32(at).also { at += 4 }
            } else {
                header.defaultDuration
            }
            val size = if (flags and TRUN_SIZE != 0) {
                trun.u32(at).also { at += 4 }
            } else {
                header.defaultSize
            }
            val sampleFlags = when {
                flags and TRUN_FLAGS != 0 -> trun.s32(at).also { at += 4 }
                index == 0 && firstFlags != null -> firstFlags
                else -> header.defaultFlags
            }
            val offset = if (flags and TRUN_COMPOSITION != 0) trun.s32(at).also { at += 4 } else 0
            val sync = kind == StreamCopyTrackKind.AUDIO || sampleFlags and NON_SYNC == 0
            samples.add(position, size, duration, offset, sync)
            position += size
        }
        if (count > 0) runs.add(dataStart, position)
        return position
    }

    private fun Mp4Box.groupingType(): String? = if (payloadSize >= 8) fourcc(4) else null

    /** A track's `tfhd` with the `trex` defaults it falls back to. */
    private class FragmentHeader(
        val baseDataOffset: Long?,
        val defaultDuration: Long,
        val defaultSize: Long,
        val defaultFlags: Int,
    ) {
        companion object {
            fun parse(tfhd: Mp4Box, init: TrackInit): FragmentHeader {
                val flags = tfhd.flags
                if (tfhd.s32(4) != init.trackId) unsupported("fragment of another track")
                var at = 8
                val base = if (flags and TFHD_BASE_OFFSET != 0) {
                    tfhd.s64(at).also { at += 8 }
                } else {
                    null
                }
                val description = if (flags and TFHD_DESCRIPTION != 0) {
                    tfhd.u32(at).also { at += 4 }
                } else {
                    init.trex?.description ?: 1L
                }
                if (description != 1L) unsupported("sample description $description")
                val duration = if (flags and TFHD_DURATION != 0) {
                    tfhd.u32(at).also { at += 4 }
                } else {
                    init.trex?.duration ?: 0L
                }
                val size = if (flags and TFHD_SIZE != 0) {
                    tfhd.u32(at).also { at += 4 }
                } else {
                    init.trex?.size ?: 0L
                }
                val sampleFlags = if (flags and TFHD_FLAGS != 0) {
                    tfhd.s32(at)
                } else {
                    init.trex?.flags ?: 0
                }
                return FragmentHeader(base, duration, size, sampleFlags)
            }
        }
    }

    /** Collects the samples of a fragmented track in growing arrays. */
    private inner class SampleBuilder {
        var count = 0
            private set
        private var offsets = LongArray(INITIAL_SAMPLES)
        private var sizes = IntArray(INITIAL_SAMPLES)
        private var durations = IntArray(INITIAL_SAMPLES)
        private var compositions: IntArray? = null
        private var sync: BooleanArray? = null
        private var firstDecodeTime: Long? = null
        private var nextDecodeTime = 0L

        /**
         * A fragment's `tfdt`: the first one starts the track; a later one that does not follow
         * on from the samples so far moves the previous sample's end, as a player would.
         */
        fun decodeTimeAt(time: Long) {
            if (firstDecodeTime == null || count == 0) {
                firstDecodeTime = time
                nextDecodeTime = time
                return
            }
            if (time == nextDecodeTime) return
            val last = durations[count - 1].toLong() + (time - nextDecodeTime)
            if (last <= 0 || last > Int.MAX_VALUE) unsupported("decode times out of order")
            durations[count - 1] = last.toInt()
            nextDecodeTime = time
        }

        fun add(offset: Long, size: Long, duration: Long, composition: Int, isSync: Boolean) {
            if (size <= 0 || size > MAX_SAMPLE_BYTES) unsupported("sample size $size")
            if (duration <= 0 || duration > Int.MAX_VALUE) unsupported("sample duration $duration")
            if (offset < 0) unsupported("sample data outside mdat")
            if (count == offsets.size) grow()
            if (firstDecodeTime == null) firstDecodeTime = nextDecodeTime
            offsets[count] = offset
            sizes[count] = size.toInt()
            durations[count] = duration.toInt()
            if (composition != 0) {
                val array = compositions ?: IntArray(offsets.size).also { compositions = it }
                array[count] = composition
            }
            if (!isSync) {
                val array = sync ?: BooleanArray(offsets.size) { true }.also { sync = it }
                array[count] = false
            } else {
                sync?.set(count, true)
            }
            count += 1
            nextDecodeTime += duration
        }

        fun build(init: TrackInit): Mp4Track = init.track(
            firstDecodeTime = firstDecodeTime ?: 0L,
            count = count,
            offsets = offsets,
            sizes = sizes,
            durations = durations,
            compositionOffsets = compositions,
            sync = sync,
        )

        private fun grow() {
            val capacity = minOf(offsets.size.toLong() * 2, maxSamples.toLong() + 1).toInt()
            offsets = offsets.copyOf(capacity)
            sizes = sizes.copyOf(capacity)
            durations = durations.copyOf(capacity)
            compositions = compositions?.copyOf(capacity)
            sync = sync?.let { old ->
                BooleanArray(capacity) { index -> index >= old.size || old[index] }
            }
        }
    }

    /** Reads the start of the input in 32 KiB steps, so small boxes need no read of their own. */
    private class Window(private val source: Mp4Source) {
        private val buffer = ByteBuffer.allocate(WINDOW_BYTES)
        private var start = -1L
        private var length = 0

        fun bytes(position: Long, count: Int): ByteArray {
            val array = ByteArray(count)
            if (count > WINDOW_BYTES) {
                source.readFully(ByteBuffer.wrap(array), position)
                return array
            }
            if (start < 0 || position < start || position + count > start + length) {
                length = minOf(WINDOW_BYTES.toLong(), source.size - position).toInt()
                buffer.clear()
                buffer.limit(length)
                source.readFully(buffer, position)
                start = position
            }
            System.arraycopy(buffer.array(), (position - start).toInt(), array, 0, count)
            return array
        }
    }

    companion object {
        /** About 7 hours of 30 fps video with its sound; longer merges take today's way. */
        const val MAX_SAMPLES = 2_000_000
        const val MAX_SAMPLE_BYTES = 64L * 1_024 * 1_024
        const val MAX_MOOV_BYTES = 64L * 1_024 * 1_024
        const val MAX_MOOF_BYTES = 16L * 1_024 * 1_024
        private const val INITIAL_SAMPLES = 4_096
        private const val WINDOW_BYTES = 32 * 1_024
        private const val TFHD_BASE_OFFSET = 0x1
        private const val TFHD_DESCRIPTION = 0x2
        private const val TFHD_DURATION = 0x8
        private const val TFHD_SIZE = 0x10
        private const val TFHD_FLAGS = 0x20
        private const val TRUN_DATA_OFFSET = 0x1
        private const val TRUN_FIRST_FLAGS = 0x4
        private const val TRUN_DURATION = 0x100
        private const val TRUN_SIZE = 0x200
        private const val TRUN_FLAGS = 0x400
        private const val TRUN_COMPOSITION = 0x800
        private val PER_SAMPLE_FIELDS =
            listOf(TRUN_DURATION, TRUN_SIZE, TRUN_FLAGS, TRUN_COMPOSITION)
        private const val NON_SYNC = 0x10000

        /** PIFF's sample encryption box (`uuid` a2394f52-5a9b-4f14-a244-6c427c648df4). */
        private val PIFF = byteArrayOf(
            0xa2.toByte(), 0x39, 0x4f, 0x52, 0x5a, 0x9b.toByte(), 0x4f, 0x14,
            0xa2.toByte(), 0x44, 0x6c, 0x42, 0x7c, 0x64, 0x8d.toByte(), 0xf4.toByte(),
        )
    }
}

/** Byte ranges, kept in the order added; checks that each run lies inside one `mdat`. */
internal class RangeList {
    private var starts = LongArray(64)
    private var ends = LongArray(64)
    var size = 0
        private set

    fun add(start: Long, end: Long) {
        if (size == starts.size) {
            starts = starts.copyOf(size * 2)
            ends = ends.copyOf(size * 2)
        }
        starts[size] = start
        ends[size] = end
        size += 1
    }

    /** Whether every range here lies inside one of [containers] (added in file order). */
    fun allInside(containers: RangeList): Boolean {
        for (index in 0 until size) {
            val found = containers.floorIndex(starts[index])
            if (found < 0 || ends[index] > containers.ends[found]) return false
        }
        return true
    }

    /** The last range that starts at or before [position], or -1. */
    private fun floorIndex(position: Long): Int {
        var low = 0
        var high = size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (starts[middle] <= position) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }
}

internal const val BOX_HEADER = 8
internal const val LARGE_BOX_HEADER = 16
internal const val UINT32 = 0xffffffffL
