package com.alal.yft.core.download

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.Response

/**
 * A YouTube live stream's media file, on air or recently ended ([LiveOriginFile]), fetched the
 * way yt-dlp fetches it (owner item 6, run 4f). Its address without `sq` serves only the newest
 * piece (on the owner's phone piece 2021 of a 2:48 stream, 671,325 bytes, then nothing), so the
 * file is asked piece after piece, `sq=0` up to the `X-Head-Seqnum` its server names ([head]),
 * and the pieces, each a fragmented MP4 with its own `moov`, are joined into one
 * ([LivePieceJoin]). WebM pieces are not joined.
 */
internal object LivePieces {
    /** The newest pieces, which the server may not serve: a missing one ends the file there. */
    const val TAIL_PIECES = 2

    fun pieceUrl(file: HttpUrl, sq: Int): HttpUrl =
        file.newBuilder().setQueryParameter("sq", sq.toString()).build()

    /** The newest piece's number, from [response]'s `X-Head-Seqnum`; null when it has none. */
    fun head(response: Response): Int? =
        response.header("X-Head-Seqnum")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

    fun isWebm(mimeType: String?): Boolean =
        mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)?.endsWith("/webm") == true

    /** "2h48m23s", for the Detail: the Detail's cleaning takes "2:48:23" for an address. */
    fun clock(millis: Long): String {
        val seconds = millis / 1_000
        return String.format(
            Locale.US,
            "%dh%02dm%02ds",
            seconds / 3_600,
            seconds / 60 % 60,
            seconds % 60,
        )
    }
}

/** Why pieces cannot be joined; [notReady] when the first one does not start a fragmented MP4. */
internal class LivePieceProblem(message: String, val notReady: Boolean = false) :
    Exception(message)

/**
 * The pieces of a live stream joined into one fragmented MP4 (run 4f) and read at any position
 * ([Mp4Source]), so the merge's own reader checks the joined bytes before they are written: the
 * first piece's `ftyp` and `moov`, then of every piece the bytes from its first `moof` to the end
 * of its last `mdat`. The `ftyp` and `moov` each later piece repeats (the same track) and the
 * boxes around its fragments (`emsg`, `sidx`) are left out. A `tfhd` that names where its data
 * is (an absolute offset) is moved with its piece.
 */
internal class LivePieceJoin private constructor(
    private val header: ByteArray,
    private val spans: List<Span>,
    private val patches: List<Patch>,
) : Mp4Source, Closeable {
    /** [length] bytes of [file] from [from], at [at] of the joined file. */
    private class Span(val file: File, val from: Long, val length: Long, val at: Long)

    /** The 8-byte [value] at [at] of the joined file. */
    private class Patch(val at: Long, val value: Long)

    /** Pieces joined: piece 0 and the ones after it, up to a missing one at the tail. */
    val pieces: Int get() = spans.size

    override val size: Long = header.size + spans.sumOf { it.length }

    private var openFile: File? = null
    private var channel: FileChannel? = null

    override fun readFully(target: ByteBuffer, position: Long) {
        val first = target.position()
        val count = target.remaining()
        if (position < 0 || position + count > size) throw EOFException("Joined pieces end early")
        var at = position
        while (target.hasRemaining()) {
            if (at < header.size) {
                val length = minOf(target.remaining().toLong(), header.size - at).toInt()
                target.put(header, at.toInt(), length)
                at += length
                continue
            }
            val span = spans[spanAt(at)]
            val inside = at - span.at
            val length = minOf(target.remaining().toLong(), span.length - inside).toInt()
            val view = target.duplicate()
            view.limit(view.position() + length)
            read(span.file, view, span.from + inside)
            target.position(target.position() + length)
            at += length
        }
        val end = position + count
        var index = firstPatchAfter(position - PATCH_BYTES)
        while (index < patches.size && patches[index].at < end) {
            val patch = patches[index]
            for (byte in 0 until PATCH_BYTES) {
                val place = patch.at + byte
                if (place in position until end) {
                    val shift = (PATCH_BYTES - 1 - byte) * 8
                    val value = (patch.value ushr shift).toByte()
                    target.put(first + (place - position).toInt(), value)
                }
            }
            index += 1
        }
    }

    override fun close() {
        channel?.close()
        channel = null
        openFile = null
    }

    private fun read(file: File, target: ByteBuffer, position: Long) {
        val source = channel?.takeIf { openFile == file } ?: run {
            close()
            RandomAccessFile(file, "r").channel.also {
                channel = it
                openFile = file
            }
        }
        var at = position
        while (target.hasRemaining()) {
            val read = source.read(target, at)
            if (read < 0) throw EOFException("Live piece ended early")
            at += read
        }
    }

    /** The span that holds byte [at] of the joined file. */
    private fun spanAt(at: Long): Int {
        var low = 0
        var high = spans.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (spans[middle].at <= at) low = middle else high = middle - 1
        }
        return low
    }

    /** The first patch that starts after [at]. */
    private fun firstPatchAfter(at: Long): Int {
        var low = 0
        var high = patches.size
        while (low < high) {
            val middle = (low + high) / 2
            if (patches[middle].at <= at) low = middle + 1 else high = middle
        }
        return low
    }

    /** Where a piece's boxes are: its `ftyp` and `moov`, and the span of its fragments. */
    private class Layout(
        val ftyp: LongRange?,
        val moov: LongRange?,
        val spanStart: Long,
        val spanEnd: Long,
        /** Each `tfhd` absolute data offset: where it is in the piece, and its value. */
        val baseOffsets: List<Pair<Long, Long>>,
    )

    companion object {
        private const val PATCH_BYTES = 8
        private const val TFHD_BASE_OFFSET = 0x1

        /**
         * Joins the piece [files], in order. A piece from [tailFrom] on that is missing, empty
         * or broken ends the file before it; any other one fails ([LivePieceProblem]).
         */
        fun join(files: List<File>, tailFrom: Int): LivePieceJoin {
            var header = ByteArray(0)
            var format: ByteArray? = null
            var firstMoov = ByteArray(0)
            val spans = ArrayList<Span>()
            val patches = ArrayList<Patch>()
            var at = 0L
            for ((index, file) in files.withIndex()) {
                val tail = index >= tailFrom && index > 0
                val piece = try {
                    if (!file.isFile || file.length() == 0L) {
                        throw LivePieceProblem("sq $index missing", notReady = index == 0)
                    }
                    val layout = scan(file, index)
                    if (index == 0) {
                        val moov = layout.moov
                            ?: throw LivePieceProblem("sq 0 has no moov", notReady = true)
                        firstMoov = bytes(file, moov)
                        format = formatOf(firstMoov)
                            ?: throw LivePieceProblem("sq 0 moov is not one fragmented track", true)
                        header = (layout.ftyp?.let { bytes(file, it) } ?: ByteArray(0)) + firstMoov
                        at = header.size.toLong()
                    } else {
                        layout.moov?.let { moov ->
                            val own = bytes(file, moov)
                            val same = own.contentEquals(firstMoov) ||
                                formatOf(own).contentEquals(format)
                            if (!same) {
                                throw LivePieceProblem("sq $index is another track format")
                            }
                        }
                    }
                    layout
                } catch (problem: LivePieceProblem) {
                    if (tail) break
                    throw problem
                } catch (problem: StreamCopyUnsupported) {
                    if (tail) break
                    throw LivePieceProblem("sq $index: ${problem.message}", notReady = index == 0)
                }
                val shift = at - piece.spanStart
                spans += Span(file, piece.spanStart, piece.spanEnd - piece.spanStart, at)
                piece.baseOffsets.forEach { (place, value) ->
                    patches += Patch(place + shift, value + shift)
                }
                at += piece.spanEnd - piece.spanStart
            }
            if (spans.isEmpty()) throw LivePieceProblem("no pieces", notReady = true)
            return LivePieceJoin(header, spans, patches)
        }

        /** The top-level boxes of a piece: `ftyp` and `moov` first, then its fragments. */
        private fun scan(file: File, index: Int): Layout =
            RandomAccessFile(file, "r").use { input -> scan(input, index) }

        private fun scan(input: RandomAccessFile, index: Int): Layout {
            val length = input.length()
            var ftyp: LongRange? = null
            var moov: LongRange? = null
            var spanStart = -1L
            var spanEnd = -1L
            var lastMoof = -1L
            val bases = ArrayList<Pair<Long, Long>>()
            val head = ByteArray(16)
            var at = 0L
            while (at < length) {
                if (length - at < 8) throw LivePieceProblem("sq $index: broken box at the end")
                input.seek(at)
                input.readFully(head, 0, minOf(16L, length - at).toInt())
                val view = ByteBuffer.wrap(head)
                val size32 = view.getInt(0).toLong() and 0xffffffffL
                val type = fourcc(view, 4)
                var headerSize = 8
                val size = when (size32) {
                    0L -> length - at
                    1L -> {
                        if (length - at < 16) throw LivePieceProblem("sq $index: broken $type")
                        headerSize = 16
                        view.getLong(8)
                    }
                    else -> size32
                }
                if (size < headerSize || size > length - at) {
                    throw LivePieceProblem("sq $index: broken size of $type")
                }
                when (type) {
                    "ftyp" -> if (spanStart >= 0) {
                        throw LivePieceProblem("sq $index: ftyp after moof")
                    } else if (ftyp == null) {
                        ftyp = at until at + size
                    }
                    "moov" -> if (spanStart >= 0 || moov != null) {
                        throw LivePieceProblem("sq $index: a second moov")
                    } else {
                        moov = at until at + size
                    }
                    "moof" -> {
                        if (spanStart < 0) spanStart = at
                        lastMoof = at
                        bases += baseOffsets(input, at, headerSize, size, index)
                    }
                    "mdat" -> if (spanStart >= 0) spanEnd = at + size
                }
                at += size
            }
            if (spanStart < 0) {
                throw LivePieceProblem("sq $index has no moof", notReady = index == 0)
            }
            if (spanEnd < lastMoof) throw LivePieceProblem("sq $index: moof without mdat")
            return Layout(ftyp, moov, spanStart, spanEnd, bases)
        }

        /** Each `tfhd` of the `moof` at [at] that names an absolute data offset. */
        private fun baseOffsets(
            input: RandomAccessFile,
            at: Long,
            headerSize: Int,
            size: Long,
            index: Int,
        ): List<Pair<Long, Long>> {
            if (size > Mp4TrackReader.MAX_MOOF_BYTES) {
                throw LivePieceProblem("sq $index: moof too large")
            }
            val bytes = ByteArray(size.toInt())
            input.seek(at)
            input.readFully(bytes)
            val moof = Mp4Box("moof", ByteBuffer.wrap(bytes), headerSize, bytes.size)
            return moof.children()
                .filter { it.type == "traf" }
                .flatMap { traf -> traf.children().filter { it.type == "tfhd" } }
                .filter { tfhd -> tfhd.flags and TFHD_BASE_OFFSET != 0 }
                .map { tfhd -> at + tfhd.start + 8 to tfhd.s64(8) }
        }

        /** A `moov`'s one track: its id and `stsd`; null when it is not one fragmented track. */
        private fun formatOf(moov: ByteArray): ByteArray? {
            val view = ByteBuffer.wrap(moov)
            val headerSize = if ((view.getInt(0).toLong() and 0xffffffffL) == 1L) 16 else 8
            val children = Mp4Box("moov", view, headerSize, moov.size).children()
            if (children.first("mvex") == null) return null
            val trak = children.filter { it.type == "trak" }.singleOrNull() ?: return null
            val parts = trak.children()
            val tkhd = parts.first("tkhd") ?: return null
            val stsd = parts.first("mdia")?.children()?.first("minf")?.children()
                ?.first("stbl")?.children()?.first("stsd")
                ?: return null
            val id = if (tkhd.version == 1) 20 else 12
            return tkhd.bytes(id, id + 4) +
                stsd.withHeader()
        }

        private fun bytes(file: File, range: LongRange): ByteArray {
            val length = range.last - range.first + 1
            if (length > Mp4TrackReader.MAX_MOOV_BYTES) throw LivePieceProblem("moov too large")
            return RandomAccessFile(file, "r").use { input ->
                ByteArray(length.toInt()).also { bytes ->
                    input.seek(range.first)
                    input.readFully(bytes)
                }
            }
        }
    }
}
