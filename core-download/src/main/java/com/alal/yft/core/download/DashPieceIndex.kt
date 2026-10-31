package com.alal.yft.core.download

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * v1.0.2 fix R7: one index (`sidx`) for a DASH track joined from its manifest's pieces (a
 * `SegmentTemplate`, or a `SegmentList` with an initialization piece), so that a player knows
 * the whole length at once and can seek. The initialization piece may state no length (Unified
 * Streaming's `mvhd` states 0, with no `mehd`) and each piece may hold an index of its own, of
 * that piece alone. Media3's fragmented-MP4 reader, YFT's player, takes each index it comes to
 * for the whole file: the length grew as the pieces were read (tears-of-steel's 240p: about 1:00
 * at first, then up to 12:14), and a file without any index could not be sought.
 *
 * [of] reads the joined pieces with the merge's own reader ([Mp4TrackReader], any codec) and
 * plans one `sidx` (version 1) right before the first piece's first `moof`: a reference for each
 * piece, from its first `moof` to the next piece's (Media3 counts them from the end of the
 * index, Android's reader from the first `moof`), lasting the durations of its samples. The
 * pieces' own `sidx` and `mfra` boxes, whose places no longer fit, are kept as `free` boxes of
 * the same size ([rename]). Pieces it does not read that way (not one fragmented MP4 track of
 * picture or sound, a `tfhd` that names where its data is, a piece without samples) are joined
 * as they are, as before: [of] answers null.
 */
internal class DashPieceIndex private constructor(
    /** The `sidx`, header included. */
    val box: ByteArray,
    /** The byte of the joined pieces that [box] goes before: the first piece's first `moof`. */
    val insertAt: Long,
    /** Where the pieces' own `sidx` and `mfra` boxes have their type, in the joined pieces. */
    private val oldTypes: LongArray,
) {
    /**
     * Writes `free` over the type of each of the pieces' own `sidx` and `mfra` boxes in the first
     * [length] bytes of [buffer], which hold the joined pieces' bytes from [from] on.
     */
    fun rename(buffer: ByteArray, from: Long, length: Int) {
        val end = from + length
        var index = firstAtOrAfter(from - (FREE.size - 1))
        while (index < oldTypes.size && oldTypes[index] < end) {
            val type = oldTypes[index]
            for (byte in FREE.indices) {
                val place = type + byte
                if (place in from until end) buffer[(place - from).toInt()] = FREE[byte]
            }
            index += 1
        }
    }

    private fun firstAtOrAfter(position: Long): Int {
        var low = 0
        var high = oldTypes.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (oldTypes[middle] < position) low = middle + 1 else high = middle
        }
        return low
    }

    /** A piece's top-level boxes, at their places in the joined pieces. */
    private class PieceBoxes(
        val firstMoof: Long?,
        /** The piece's `moov`, at its place in the piece. */
        val moov: LongRange?,
        /** Whether it holds a `moof` or an `mdat`. */
        val media: Boolean,
        val oldTypes: List<Long>,
    )

    /** The pieces' files read as one file ([Mp4Source]), one file open at a time. */
    private class JoinedPieces(
        private val files: List<File>,
        private val starts: LongArray,
    ) : Mp4Source, Closeable {
        override val size: Long = starts[files.size]

        private var openIndex = -1
        private var channel: FileChannel? = null

        override fun readFully(target: ByteBuffer, position: Long) {
            if (position < 0 || position + target.remaining() > size) {
                throw EOFException("Joined pieces end early")
            }
            var at = position
            while (target.hasRemaining()) {
                val file = fileAt(at)
                val length = minOf(target.remaining().toLong(), starts[file + 1] - at).toInt()
                val view = target.duplicate()
                view.limit(view.position() + length)
                val source = channelOf(file)
                var from = at - starts[file]
                while (view.hasRemaining()) {
                    val read = source.read(view, from)
                    if (read < 0) throw EOFException("DASH piece ended early")
                    from += read
                }
                target.position(target.position() + length)
                at += length
            }
        }

        override fun close() {
            channel?.close()
            channel = null
            openIndex = -1
        }

        /** The file that holds byte [at]: the last one that starts at or before it. */
        private fun fileAt(at: Long): Int {
            var low = 0
            var high = files.lastIndex
            while (low < high) {
                val middle = (low + high + 1) ushr 1
                if (starts[middle] <= at) low = middle else high = middle - 1
            }
            return low
        }

        private fun channelOf(index: Int): FileChannel {
            val open = channel
            if (open != null && openIndex == index) return open
            close()
            return RandomAccessFile(files[index], "r").channel.also {
                channel = it
                openIndex = index
            }
        }
    }

    companion object {
        /** A `sidx` counts its references in 16 bits and each one's bytes in 31. */
        private const val MAX_REFERENCES = 0xffff
        private const val MAX_REFERENCE_BYTES = 0x7fffffffL
        private const val SIDX_HEADER = 40
        private const val SIDX_ENTRY = 12
        private const val SIDX_VERSION_1 = 1 shl 24

        /** starts_with_SAP and SAP_type 1: the piece starts with a sync sample. */
        private const val STARTS_WITH_SAP = 0x90000000.toInt()
        private const val TFHD_BASE_OFFSET = 0x1
        private val OLD_INDEXES = setOf("sidx", "mfra")
        private val SIDX = "sidx".toByteArray(Charsets.ISO_8859_1)
        private val FREE = "free".toByteArray(Charsets.ISO_8859_1)

        /** The index of the joined [files], the initialization piece first; null: none. */
        fun of(files: List<File>): DashPieceIndex? = try {
            plan(files)
        } catch (_: Exception) {
            null
        }

        private fun plan(files: List<File>): DashPieceIndex? {
            val pieces = files.size - 1
            if (pieces !in 1..MAX_REFERENCES) return null
            val starts = LongArray(files.size + 1)
            for ((index, file) in files.withIndex()) {
                val length = file.length()
                if (length <= 0) return null
                starts[index + 1] = starts[index] + length
            }
            val boxes = ArrayList<PieceBoxes>(files.size)
            for ((index, file) in files.withIndex()) boxes += scan(file, starts[index]) ?: return null
            val init = boxes.first()
            val moov = init.moov ?: return null
            if (init.media) return null
            val moofs = LongArray(pieces)
            for (piece in 0 until pieces) moofs[piece] = boxes[piece + 1].firstMoof ?: return null
            val (trackId, kind) = trackOf(files.first(), moov) ?: return null
            val track = JoinedPieces(files, starts).use { joined ->
                Mp4TrackReader(joined, kind, anyCodec = true).read()
            }
            // Piece p's samples, in decode order, are first[p] until first[p + 1].
            val first = IntArray(pieces + 1)
            var sample = 0
            for (piece in 0 until pieces) {
                first[piece] = sample
                val end = starts[piece + 2]
                while (sample < track.count && track.offsets[sample] < end) {
                    val offset = track.offsets[sample]
                    if (offset < moofs[piece] || offset + track.sizes[sample] > end) return null
                    sample += 1
                }
                if (sample == first[piece]) return null
            }
            if (sample != track.count) return null
            first[pieces] = sample
            val box = ByteBuffer.allocate(SIDX_HEADER + SIDX_ENTRY * pieces)
            box.putInt(box.capacity())
            box.put(SIDX)
            box.putInt(SIDX_VERSION_1)
            box.putInt(trackId)
            box.putInt(track.timescale.toInt())
            box.putLong(earliestPresentation(track, first[1]))
            // first_offset: the first piece's first moof follows the index.
            box.putLong(0L)
            box.putShort(0)
            box.putShort(pieces.toShort())
            for (piece in 0 until pieces) {
                val next = if (piece + 1 < pieces) moofs[piece + 1] else starts[files.size]
                val bytes = next - moofs[piece]
                if (bytes !in 1..MAX_REFERENCE_BYTES) return null
                var ticks = 0L
                for (index in first[piece] until first[piece + 1]) ticks += track.durations[index]
                if (ticks > UINT32) return null
                box.putInt(bytes.toInt())
                box.putInt(ticks.toInt())
                box.putInt(if (track.isSync(first[piece])) STARTS_WITH_SAP else 0)
            }
            return DashPieceIndex(
                box = box.array(),
                insertAt = moofs.first(),
                oldTypes = boxes.flatMap { it.oldTypes }.toLongArray(),
            )
        }

        /** The first piece's earliest presentation time, of its [count] samples, in ticks. */
        private fun earliestPresentation(track: Mp4Track, count: Int): Long {
            var decode = track.firstDecodeTime
            var earliest = Long.MAX_VALUE
            for (index in 0 until count) {
                earliest = minOf(earliest, decode + track.compositionOffset(index))
                decode += track.durations[index]
            }
            return (earliest + track.editShift).coerceAtLeast(0L)
        }

        /** The top-level boxes of the piece [file], which starts at [start] of the joined ones. */
        private fun scan(file: File, start: Long): PieceBoxes? {
            RandomAccessFile(file, "r").use { input -> return scan(input, start) }
        }

        private fun scan(input: RandomAccessFile, start: Long): PieceBoxes? {
            val length = input.length()
            val head = ByteArray(LARGE_BOX_HEADER)
            var firstMoof: Long? = null
            var moov: LongRange? = null
            var media = false
            val oldTypes = ArrayList<Long>()
            var at = 0L
            while (at < length) {
                if (length - at < BOX_HEADER) return null
                val headBytes = minOf(LARGE_BOX_HEADER.toLong(), length - at).toInt()
                input.seek(at)
                input.readFully(head, 0, headBytes)
                val view = ByteBuffer.wrap(head)
                val size32 = view.getInt(0).toLong() and UINT32
                val type = fourcc(view, 4)
                var headerSize = BOX_HEADER
                val size = when (size32) {
                    // A box to the end of its piece would run on to the end of the joined file.
                    0L -> return null
                    1L -> {
                        if (headBytes < LARGE_BOX_HEADER) return null
                        headerSize = LARGE_BOX_HEADER
                        view.getLong(BOX_HEADER)
                    }
                    else -> size32
                }
                if (size < headerSize || size > length - at) return null
                when (type) {
                    in OLD_INDEXES -> oldTypes += start + at + 4
                    "moov" -> if (moov == null) moov = at until at + size else return null
                    "moof" -> {
                        if (firstMoof == null) firstMoof = start + at
                        if (namesItsDataPlace(input, at, headerSize, size)) return null
                        media = true
                    }
                    "mdat" -> media = true
                }
                at += size
            }
            return PieceBoxes(firstMoof, moov, media, oldTypes)
        }

        /** Whether a `tfhd` of the `moof` at [at] names where its data is (an absolute offset). */
        private fun namesItsDataPlace(
            input: RandomAccessFile,
            at: Long,
            headerSize: Int,
            size: Long,
        ): Boolean {
            if (size > Mp4TrackReader.MAX_MOOF_BYTES) return true
            val bytes = ByteArray(size.toInt())
            input.seek(at)
            input.readFully(bytes)
            val moof = Mp4Box("moof", ByteBuffer.wrap(bytes), headerSize, bytes.size)
            return moof.children()
                .filter { it.type == "traf" }
                .flatMap { traf -> traf.children().filter { it.type == "tfhd" } }
                .any { tfhd -> tfhd.flags and TFHD_BASE_OFFSET != 0 }
        }

        /** The `moov` at [moov] of [file], of one track of picture or sound: its ID and kind. */
        private fun trackOf(file: File, moov: LongRange): Pair<Int, StreamCopyTrackKind>? {
            val length = moov.last - moov.first + 1
            if (length > Mp4TrackReader.MAX_MOOV_BYTES) return null
            val bytes = ByteArray(length.toInt())
            RandomAccessFile(file, "r").use { input ->
                input.seek(moov.first)
                input.readFully(bytes)
            }
            val view = ByteBuffer.wrap(bytes)
            val large = (view.getInt(0).toLong() and UINT32) == 1L
            val headerSize = if (large) LARGE_BOX_HEADER else BOX_HEADER
            val trak = Mp4Box("moov", view, headerSize, bytes.size).children()
                .singleOrNull { it.type == "trak" }
                ?: return null
            val boxes = trak.children()
            val tkhd = boxes.first("tkhd") ?: return null
            val handler = boxes.first("mdia")?.children()?.first("hdlr")?.fourcc(8) ?: return null
            val kind = StreamCopyTrackKind.entries.firstOrNull { it.handler == handler }
                ?: return null
            return tkhd.s32(if (tkhd.version == 1) 20 else 12) to kind
        }
    }
}
