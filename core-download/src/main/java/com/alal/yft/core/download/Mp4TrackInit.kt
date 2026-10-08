package com.alal.yft.core.download

/** A track's `trex` defaults for its fragments (P35). */
internal class Trex(val description: Long, val duration: Long, val size: Long, val flags: Int)

/** A merge input's one track as its `moov` describes it (P35). */
internal class TrackInit(
    val kind: StreamCopyTrackKind,
    val trackId: Int,
    val timescale: Long,
    val language: Int,
    val sampleDescription: ByteArray,
    val nalLengthSize: Int?,
    val width: Int,
    val height: Int,
    val matrix: ByteArray?,
    val editShift: Long,
    val trex: Trex?,
    /** The `moov` has `mvex`: the samples are in `moof` fragments. */
    val fragmented: Boolean,
    val stbl: Mp4Box,
) {
    fun track(
        firstDecodeTime: Long,
        count: Int,
        offsets: LongArray,
        sizes: IntArray,
        durations: IntArray,
        compositionOffsets: IntArray?,
        sync: BooleanArray?,
    ): Mp4Track = Mp4Track(
        kind = kind,
        timescale = timescale,
        language = language,
        sampleDescription = sampleDescription,
        nalLengthSize = nalLengthSize,
        width = width,
        height = height,
        matrix = matrix,
        editShift = editShift,
        firstDecodeTime = firstDecodeTime,
        count = count,
        offsets = offsets,
        sizes = sizes,
        durations = durations,
        compositionOffsets = compositionOffsets,
        sync = sync,
    )

    companion object {
        fun parse(
            trak: Mp4Box,
            kind: StreamCopyTrackKind,
            movieTimescale: Long,
            mvex: Mp4Box?,
        ): TrackInit {
            val boxes = trak.children()
            val tkhd = boxes.first("tkhd") ?: unsupported("no tkhd")
            val mdia = boxes.first("mdia") ?: unsupported("no mdia")
            val wide = tkhd.version == 1
            val trackId = tkhd.s32(if (wide) 20 else 12)
            val matrixAt = if (wide) 52 else 40
            val matrix = tkhd.bytes(matrixAt, matrixAt + MATRIX_BYTES)
            var width = tkhd.s32(matrixAt + MATRIX_BYTES)
            var height = tkhd.s32(matrixAt + MATRIX_BYTES + 4)

            val media = mdia.children()
            val mdhd = media.first("mdhd") ?: unsupported("no mdhd")
            val timescale = mdhd.u32(if (mdhd.version == 1) 20 else 12)
            if (timescale <= 0) unsupported("timescale 0")
            val language = mdhd.u16(if (mdhd.version == 1) 32 else 20)
            val hdlr = media.first("hdlr") ?: unsupported("no hdlr")
            if (hdlr.fourcc(8) != kind.handler) unsupported("not a ${kind.name.lowercase()} track")
            val minf = media.first("minf") ?: unsupported("no minf")
            val stbl = minf.children().first("stbl") ?: unsupported("no stbl")
            val tables = stbl.children()
            for (box in tables) {
                when (box.type) {
                    "senc", "saiz", "saio" -> unsupported("encrypted")
                    "sgpd", "sbgp" -> if (box.payloadSize >= 8 && box.fourcc(4) == "seig") {
                        unsupported("encrypted")
                    }
                }
            }
            val stsd = tables.first("stsd") ?: unsupported("no stsd")
            val entry = sampleEntry(stsd, kind)
            val nalLengthSize = if (kind == StreamCopyTrackKind.VIDEO) {
                if (width == 0 || height == 0) {
                    width = entry.u16(VISUAL_WIDTH) shl 16
                    height = entry.u16(VISUAL_WIDTH + 2) shl 16
                }
                nalLengthSize(entry)
            } else {
                null
            }
            val editShift = boxes.first("edts")
                ?.let { edts -> editShift(edts, timescale, movieTimescale) }
                ?: 0L
            val trex = mvex?.children()
                ?.filter { it.type == "trex" }
                ?.firstOrNull { it.s32(4) == trackId }
                ?.let { Trex(it.u32(8), it.u32(12), it.u32(16), it.s32(20)) }
            return TrackInit(
                kind = kind,
                trackId = trackId,
                timescale = timescale,
                language = language,
                sampleDescription = stsd.withHeader(),
                nalLengthSize = nalLengthSize,
                width = width,
                height = height,
                matrix = matrix.takeIf { kind == StreamCopyTrackKind.VIDEO },
                editShift = editShift,
                trex = trex,
                fragmented = mvex != null,
                stbl = stbl,
            )
        }

        /** The one sample entry of [stsd], when it is a codec the merge copies, unencrypted. */
        private fun sampleEntry(stsd: Mp4Box, kind: StreamCopyTrackKind): Mp4Box {
            val count = stsd.u32(4)
            if (count != 1L) unsupported("$count sample descriptions")
            val entry = stsd.children(8).singleOrNull() ?: unsupported("broken stsd box")
            if (entry.type == "encv" || entry.type == "enca") unsupported("encrypted")
            if (entry.type !in kind.sampleEntries) {
                unsupported("codec ${entry.type.filter(Char::isLetterOrDigit)}")
            }
            val fixed = when (kind) {
                StreamCopyTrackKind.VIDEO -> VISUAL_ENTRY_BYTES
                StreamCopyTrackKind.AUDIO -> AUDIO_ENTRY_BYTES + when (entry.u16(8)) {
                    1 -> 16
                    2 -> 36
                    else -> 0
                }
            }
            if (entry.children(fixed).any { it.type == "sinf" }) unsupported("encrypted")
            return entry
        }

        /** The size of the length before each NAL unit, from the entry's `avcC` (1, 2 or 4). */
        private fun nalLengthSize(entry: Mp4Box): Int {
            val avcC = entry.children(VISUAL_ENTRY_BYTES).first("avcC") ?: unsupported("no avcC")
            val size = (avcC.u8(4) and 0x3) + 1
            if (size == 3) unsupported("NAL length size 3")
            return size
        }

        /**
         * The input's edit list as one shift in [timescale] ticks: none, one edit that starts the
         * media at its media time, or an empty edit (a delay) and then such an edit.
         */
        private fun editShift(edts: Mp4Box, timescale: Long, movieTimescale: Long): Long {
            val elst = edts.children().first("elst") ?: return 0L
            val wide = elst.version == 1
            val count = elst.u32(4)
            val entryBytes = if (wide) 20 else 12
            if (8 + count * entryBytes > elst.payloadSize) unsupported("short elst box")
            val edits = (0 until count.toInt()).map { index ->
                val at = 8 + index * entryBytes
                if (wide) {
                    Edit(elst.s64(at), elst.s64(at + 8), elst.s32(at + 16))
                } else {
                    Edit(elst.u32(at), elst.s32(at + 4).toLong(), elst.s32(at + 8))
                }
            }
            val first = edits.firstOrNull() ?: return 0L
            return when {
                edits.size == 1 && first.isNormal -> -first.mediaTime
                edits.size == 2 && first.mediaTime == -1L && edits[1].isNormal -> {
                    if (movieTimescale <= 0) unsupported("edit list")
                    first.duration * timescale / movieTimescale - edits[1].mediaTime
                }
                else -> unsupported("edit list")
            }
        }

        private class Edit(val duration: Long, val mediaTime: Long, val rate: Int) {
            val isNormal: Boolean get() = mediaTime >= 0 && rate == RATE_ONE
        }

        private const val MATRIX_BYTES = 36
        private const val VISUAL_ENTRY_BYTES = 78
        private const val VISUAL_WIDTH = 24
        private const val AUDIO_ENTRY_BYTES = 28
        private const val RATE_ONE = 0x10000
    }
}

/**
 * A plain MP4's sample tables (P35): `stsz` or `stz2`, `stco` or `co64`, `stsc`, `stts`, `ctts`
 * and `stss`.
 */
internal class PlainTables(
    private val init: TrackInit,
    private val tables: List<Mp4Box>,
    private val count: Int,
) {
    /** The track; each chunk's data range goes into [runs] to be checked against the `mdat`s. */
    fun read(runs: RangeList): Mp4Track {
        val sizes = sampleSizes()
        return init.track(
            firstDecodeTime = 0L,
            count = count,
            offsets = offsets(sizes, runs),
            sizes = sizes,
            durations = durations(),
            compositionOffsets = compositionOffsets(),
            sync = if (init.kind == StreamCopyTrackKind.VIDEO) syncSamples() else null,
        )
    }

    private fun sampleSizes(): IntArray {
        val sizes = IntArray(count)
        val stsz = tables.first("stsz")
        if (stsz != null) {
            val fixed = stsz.u32(4)
            if (fixed != 0L) {
                sizes.fill(checkedSize(fixed))
            } else {
                table(stsz, 12, 4L)
                for (index in 0 until count) sizes[index] = checkedSize(stsz.u32(12 + 4 * index))
            }
            return sizes
        }
        val stz2 = tables.first("stz2") ?: unsupported("no stsz")
        val bits = stz2.u8(7)
        if (bits != 4 && bits != 8 && bits != 16) unsupported("stz2 field size $bits")
        if (12 + (count.toLong() * bits + 7) / 8 > stz2.payloadSize) unsupported("short stz2 box")
        for (index in 0 until count) {
            val size = when (bits) {
                4 -> stz2.u8(12 + index / 2).let { if (index % 2 == 0) it shr 4 else it and 0xf }
                8 -> stz2.u8(12 + index)
                else -> stz2.u16(12 + 2 * index)
            }
            sizes[index] = checkedSize(size.toLong())
        }
        return sizes
    }

    private fun durations(): IntArray {
        val stts = tables.first("stts") ?: unsupported("no stts")
        val entries = table(stts, 8, 8L)
        val durations = IntArray(count)
        var index = 0
        for (entry in 0 until entries) {
            val samples = stts.u32(8 + 8 * entry)
            val delta = stts.u32(12 + 8 * entry)
            if (index + samples > count) unsupported("stts longer than stsz")
            if (delta > Int.MAX_VALUE) unsupported("sample duration $delta")
            durations.fill(delta.toInt(), index, index + samples.toInt())
            index += samples.toInt()
        }
        if (index != count) unsupported("stts shorter than stsz")
        // Only the last sample may last 0 ticks.
        for (sample in 0 until count - 1) {
            if (durations[sample] == 0) unsupported("sample duration 0")
        }
        return durations
    }

    private fun compositionOffsets(): IntArray? {
        val ctts = tables.first("ctts") ?: return null
        val entries = table(ctts, 8, 8L)
        val offsets = IntArray(count)
        var index = 0
        for (entry in 0 until entries) {
            val samples = ctts.u32(8 + 8 * entry)
            val offset = ctts.s32(12 + 8 * entry)
            if (index + samples > count) unsupported("ctts longer than stsz")
            offsets.fill(offset, index, index + samples.toInt())
            index += samples.toInt()
        }
        if (index != count) unsupported("ctts shorter than stsz")
        return offsets.takeIf { array -> array.any { it != 0 } }
    }

    private fun syncSamples(): BooleanArray? {
        val stss = tables.first("stss") ?: return null
        val entries = table(stss, 8, 4L)
        if (entries == 0) unsupported("no sync samples")
        val sync = BooleanArray(count)
        for (entry in 0 until entries) {
            val number = stss.u32(8 + 4 * entry)
            if (number < 1 || number > count) unsupported("broken stss box")
            sync[(number - 1).toInt()] = true
        }
        return sync
    }

    private fun offsets(sizes: IntArray, runs: RangeList): LongArray {
        val stco = tables.first("stco")
        val co64 = tables.first("co64")
        val chunks = when {
            stco != null -> LongArray(table(stco, 8, 4L)) { stco.u32(8 + 4 * it) }
            co64 != null -> LongArray(table(co64, 8, 8L)) { co64.s64(8 + 8 * it) }
            else -> unsupported("no stco")
        }
        val stsc = tables.first("stsc") ?: unsupported("no stsc")
        val entries = table(stsc, 8, 12L)
        val offsets = LongArray(count)
        var sample = 0
        for (entry in 0 until entries) {
            val at = 8 + 12 * entry
            val firstChunk = stsc.u32(at)
            val perChunk = stsc.u32(at + 4)
            if (stsc.u32(at + 8) != 1L) unsupported("sample description ${stsc.u32(at + 8)}")
            val lastChunk = if (entry + 1 < entries) stsc.u32(at + 12) - 1 else chunks.size.toLong()
            val expectedFirst = if (entry == 0) 1L else firstChunk
            if (firstChunk != expectedFirst || lastChunk < firstChunk || perChunk <= 0) {
                unsupported("broken stsc box")
            }
            if (lastChunk > chunks.size) unsupported("stsc longer than stco")
            for (chunk in firstChunk..lastChunk) {
                var position = chunks[(chunk - 1).toInt()]
                val start = position
                if (sample + perChunk > count) unsupported("stsc longer than stsz")
                repeat(perChunk.toInt()) {
                    offsets[sample] = position
                    position += sizes[sample]
                    sample += 1
                }
                runs.add(start, position)
            }
        }
        if (sample != count) unsupported("stsc shorter than stsz")
        return offsets
    }

    /** [box]'s entry count, after checking its entries of [entryBytes] fit after [at]. */
    private fun table(box: Mp4Box, at: Int, entryBytes: Long): Int {
        val entries = if (box.type == "stsz") count.toLong() else box.u32(4)
        if (at + entries * entryBytes > box.payloadSize) unsupported("short ${box.type} box")
        return entries.toInt()
    }

    private fun checkedSize(size: Long): Int {
        if (size <= 0 || size > Mp4TrackReader.MAX_SAMPLE_BYTES) unsupported("sample size $size")
        return size.toInt()
    }
}
