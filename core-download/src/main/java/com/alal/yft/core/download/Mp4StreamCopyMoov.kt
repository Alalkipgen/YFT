package com.alal.yft.core.download

import java.nio.ByteBuffer

/** Buffered, positioned writes of the merged file's boxes (P35). */
internal class BoxSink(
    private val output: StreamCopyOutput,
    private val buffer: ByteBuffer,
    /** Where the buffer's first byte goes in the file. */
    private var flushedTo: Long,
) {
    init {
        buffer.clear()
    }

    /** Where the next byte goes in the file. */
    val position: Long get() = flushedTo + buffer.position()

    fun u8(value: Int) {
        room(1)
        buffer.put(value.toByte())
    }

    fun u16(value: Int) {
        room(2)
        buffer.putShort(value.toShort())
    }

    fun u32(value: Long) {
        room(4)
        buffer.putInt(value.toInt())
    }

    fun u64(value: Long) {
        room(8)
        buffer.putLong(value)
    }

    fun bytes(array: ByteArray) {
        var offset = 0
        while (offset < array.size) {
            room(1)
            val count = minOf(buffer.remaining(), array.size - offset)
            buffer.put(array, offset, count)
            offset += count
        }
    }

    fun fourcc(type: String) = bytes(type.toByteArray(Charsets.ISO_8859_1))

    fun zeros(count: Int) = repeat(count) { u8(0) }

    fun flush() {
        buffer.flip()
        val count = buffer.remaining()
        output.write(buffer, flushedTo)
        flushedTo += count
        buffer.clear()
    }

    private fun room(bytes: Int) {
        if (buffer.remaining() < bytes) flush()
    }
}

/** A box of the merged file's `moov` (P35), whose size is known before it is written. */
internal abstract class OutBox(val type: String) {
    abstract val payloadSize: Long
    val size: Long get() = BOX_HEADER + payloadSize

    protected abstract fun writePayload(sink: BoxSink)

    open fun write(sink: BoxSink) {
        val start = sink.position
        sink.u32(size)
        sink.fourcc(type)
        writePayload(sink)
        // A wrong size would make a broken file; the stream copy then gives way to MediaMuxer.
        check(sink.position - start == size) { "Wrong $type size" }
    }
}

private class ContainerBox(type: String, private val children: List<OutBox>) : OutBox(type) {
    override val payloadSize: Long = children.sumOf { it.size }

    override fun writePayload(sink: BoxSink) = children.forEach { it.write(sink) }
}

private class LeafBox(
    type: String,
    override val payloadSize: Long,
    private val body: (BoxSink) -> Unit,
) : OutBox(type) {
    override fun writePayload(sink: BoxSink) = body(sink)
}

/** A box kept byte for byte from an input, header included (the `stsd`). */
private class VerbatimBox(private val box: ByteArray) :
    OutBox(String(box, 4, 4, Charsets.ISO_8859_1)) {
    override val payloadSize: Long = box.size - BOX_HEADER.toLong()

    override fun writePayload(sink: BoxSink) = Unit

    override fun write(sink: BoxSink) = sink.bytes(box)
}

/**
 * Writes the merged file (P35): `ftyp` and the `mdat` header, every sample copied in large
 * blocks — runs of samples that follow each other in the input go at once — then `moov` with
 * the tables of both tracks.
 */
internal class StreamCopyWriter(private val buffer: ByteBuffer) {
    /** Copies the samples; [onProgress] hears the bytes copied after each chunk. */
    fun writeData(
        layout: StreamCopyLayout,
        video: Mp4Source,
        audio: Mp4Source,
        output: StreamCopyOutput,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        buffer.clear()
        buffer.put(StreamCopyLayout.FTYP)
        if (layout.mdatHeaderBytes == LARGE_BOX_HEADER) {
            buffer.putInt(1)
            buffer.put(MDAT)
            buffer.putLong(layout.payloadBytes + LARGE_BOX_HEADER)
        } else {
            buffer.putInt((layout.payloadBytes + BOX_HEADER).toInt())
            buffer.put(MDAT)
        }
        var flushedTo = 0L
        var done = 0L
        for (slot in layout.order.indices) {
            val isVideo = layout.order[slot] == 0
            val track = if (isVideo) layout.video else layout.audio
            val source = if (isVideo) video else audio
            val chunk = layout.orderChunks[slot]
            val samples = track.track
            var sample = track.chunkFirstSample[chunk]
            val end = sample + track.chunkSamples[chunk]
            while (sample < end) {
                val start = samples.offsets[sample]
                var length = samples.sizes[sample].toLong()
                sample += 1
                while (sample < end && samples.offsets[sample] == start + length) {
                    length += samples.sizes[sample]
                    sample += 1
                }
                var at = start
                var left = length
                while (left > 0) {
                    if (!buffer.hasRemaining()) flushedTo = flush(output, flushedTo)
                    val count = minOf(left, buffer.remaining().toLong()).toInt()
                    val limit = buffer.limit()
                    buffer.limit(buffer.position() + count)
                    source.readFully(buffer, at)
                    buffer.limit(limit)
                    at += count
                    left -= count
                }
                done += length
            }
            onProgress(done, layout.payloadBytes)
        }
        flushedTo = flush(output, flushedTo)
        check(flushedTo == layout.payloadEnd) { "Wrong mdat size" }
    }

    /** Writes `moov` after the samples; returns the merged file's size. */
    fun writeMoov(layout: StreamCopyLayout, output: StreamCopyOutput): Long {
        val moov = StreamCopyMoov.build(layout)
        val sink = BoxSink(output, buffer, layout.payloadEnd)
        moov.write(sink)
        sink.flush()
        return layout.payloadEnd + moov.size
    }

    private fun flush(output: StreamCopyOutput, position: Long): Long {
        buffer.flip()
        val count = buffer.remaining()
        output.write(buffer, position)
        buffer.clear()
        return position + count
    }

    private companion object {
        val MDAT = "mdat".toByteArray(Charsets.ISO_8859_1)
    }
}

/**
 * The merged file's `moov` (P35), much as MediaMuxer writes it: `mvhd` (1 ms ticks), then per
 * track `tkhd`, `edts` when the track does not start at its first composition time, and `mdia`
 * with the input's timescale, language and `stsd`, and new `stts`, `ctts`, `stss`, `stsc`,
 * `stsz` and `stco` (`co64` past 4 GiB).
 */
internal object StreamCopyMoov {
    fun build(layout: StreamCopyLayout): OutBox {
        val duration = maxOf(layout.video.movieDuration, layout.audio.movieDuration)
        val video = trak(layout.video, VIDEO_TRACK)
        return ContainerBox("moov", listOf(mvhd(duration), video, trak(layout.audio, AUDIO_TRACK)))
    }

    private fun mvhd(duration: Long): OutBox {
        val wide = duration > UINT32
        return LeafBox("mvhd", if (wide) 112L else 100L) { sink ->
            sink.u32(if (wide) VERSION_1 else 0L)
            times(sink, wide, StreamCopyTrackLayout.MOVIE_TIMESCALE, duration)
            sink.u32(FIXED_ONE)
            sink.u16(VOLUME_ONE)
            sink.zeros(10)
            sink.bytes(IDENTITY_MATRIX)
            sink.zeros(24)
            sink.u32(3L)
        }
    }

    private fun trak(layout: StreamCopyTrackLayout, trackId: Int): OutBox =
        ContainerBox("trak", listOfNotNull(tkhd(layout, trackId), edts(layout), mdia(layout)))

    private fun tkhd(layout: StreamCopyTrackLayout, trackId: Int): OutBox {
        val duration = layout.movieDuration
        val wide = duration > UINT32
        val video = layout.track.kind == StreamCopyTrackKind.VIDEO
        return LeafBox("tkhd", if (wide) 96L else 84L) { sink ->
            sink.u32((if (wide) VERSION_1 else 0L) or TRACK_ENABLED_IN_MOVIE)
            if (wide) {
                sink.u64(0L)
                sink.u64(0L)
            } else {
                sink.u32(0L)
                sink.u32(0L)
            }
            sink.u32(trackId.toLong())
            sink.u32(0L)
            if (wide) sink.u64(duration) else sink.u32(duration)
            sink.zeros(8)
            sink.u16(0)
            sink.u16(0)
            sink.u16(if (video) 0 else VOLUME_ONE)
            sink.u16(0)
            sink.bytes(layout.track.matrix ?: IDENTITY_MATRIX)
            sink.u32(if (video) layout.track.width.toLong() else 0L)
            sink.u32(if (video) layout.track.height.toLong() else 0L)
        }
    }

    private fun edts(layout: StreamCopyTrackLayout): OutBox? {
        val edits = layout.edits
        if (edits.isEmpty()) return null
        val wide = edits.any { (duration, time) -> duration > UINT32 || time > Int.MAX_VALUE }
        val entryBytes = if (wide) 20L else 12L
        val elst = LeafBox("elst", 8L + entryBytes * edits.size) { sink ->
            sink.u32(if (wide) VERSION_1 else 0L)
            sink.u32(edits.size.toLong())
            for ((duration, time) in edits) {
                if (wide) {
                    sink.u64(duration)
                    sink.u64(time)
                } else {
                    sink.u32(duration)
                    sink.u32(time)
                }
                sink.u32(FIXED_ONE)
            }
        }
        return ContainerBox("edts", listOf(elst))
    }

    private fun mdia(layout: StreamCopyTrackLayout): OutBox =
        ContainerBox("mdia", listOf(mdhd(layout.track), hdlr(layout.track.kind), minf(layout)))

    private fun mdhd(track: Mp4Track): OutBox {
        val wide = track.mediaDuration > UINT32
        return LeafBox("mdhd", if (wide) 36L else 24L) { sink ->
            sink.u32(if (wide) VERSION_1 else 0L)
            times(sink, wide, track.timescale, track.mediaDuration)
            sink.u16(if (track.language == 0) UNDETERMINED else track.language)
            sink.u16(0)
        }
    }

    private fun hdlr(kind: StreamCopyTrackKind): OutBox {
        val name = (if (kind == StreamCopyTrackKind.VIDEO) "VideoHandler" else "SoundHandler")
            .toByteArray(Charsets.ISO_8859_1) + 0
        return LeafBox("hdlr", 24L + name.size) { sink ->
            sink.u32(0L)
            sink.u32(0L)
            sink.fourcc(kind.handler)
            sink.zeros(12)
            sink.bytes(name)
        }
    }

    private fun minf(layout: StreamCopyTrackLayout): OutBox {
        val header = if (layout.track.kind == StreamCopyTrackKind.VIDEO) {
            LeafBox("vmhd", 12L) { sink ->
                sink.u32(1L)
                sink.zeros(8)
            }
        } else {
            LeafBox("smhd", 8L) { sink ->
                sink.u32(0L)
                sink.zeros(4)
            }
        }
        val dref = LeafBox("dref", 20L) { sink ->
            sink.u32(0L)
            sink.u32(1L)
            sink.u32(12L)
            sink.fourcc("url ")
            sink.u32(1L)
        }
        val dinf = ContainerBox("dinf", listOf(dref))
        return ContainerBox("minf", listOf(header, dinf, stbl(layout)))
    }

    private fun stbl(layout: StreamCopyTrackLayout): OutBox = ContainerBox(
        "stbl",
        listOfNotNull(
            VerbatimBox(layout.track.sampleDescription),
            stts(layout.track),
            ctts(layout),
            stss(layout.track),
            stsc(layout),
            stsz(layout.track),
            chunkOffsets(layout),
        ),
    )

    private fun stts(track: Mp4Track): OutBox {
        val durations = track.durations
        val runs = runs(track.count) { durations[it] == durations[it - 1] }
        return LeafBox("stts", 8L + 8L * runs) { sink ->
            sink.u32(0L)
            sink.u32(runs.toLong())
            eachRun(track.count, { durations[it] == durations[it - 1] }) { first, count ->
                sink.u32(count.toLong())
                sink.u32(durations[first].toLong())
            }
        }
    }

    private fun ctts(layout: StreamCopyTrackLayout): OutBox? {
        val track = layout.track
        val offsets = track.compositionOffsets ?: return null
        val shift = layout.compositionShift
        val same: (Int) -> Boolean = { offsets[it] == offsets[it - 1] }
        val runs = runs(track.count, same)
        return LeafBox("ctts", 8L + 8L * runs) { sink ->
            sink.u32(0L)
            sink.u32(runs.toLong())
            eachRun(track.count, same) { first, count ->
                sink.u32(count.toLong())
                sink.u32((offsets[first] + shift).toLong())
            }
        }
    }

    private fun stss(track: Mp4Track): OutBox? {
        val sync = track.sync ?: return null
        if (track.kind != StreamCopyTrackKind.VIDEO) return null
        val syncCount = (0 until track.count).count { sync[it] }
        if (syncCount == track.count) return null
        if (syncCount == 0) unsupported("no sync samples")
        return LeafBox("stss", 8L + 4L * syncCount) { sink ->
            sink.u32(0L)
            sink.u32(syncCount.toLong())
            for (index in 0 until track.count) if (sync[index]) sink.u32(index + 1L)
        }
    }

    private fun stsc(layout: StreamCopyTrackLayout): OutBox {
        val counts = layout.chunkSamples
        val same: (Int) -> Boolean = { counts[it] == counts[it - 1] }
        val runs = runs(layout.chunkCount, same)
        return LeafBox("stsc", 8L + 12L * runs) { sink ->
            sink.u32(0L)
            sink.u32(runs.toLong())
            eachRun(layout.chunkCount, same) { first, _ ->
                sink.u32(first + 1L)
                sink.u32(counts[first].toLong())
                sink.u32(1L)
            }
        }
    }

    private fun stsz(track: Mp4Track): OutBox {
        val sizes = track.sizes
        val uniform = (1 until track.count).all { sizes[it] == sizes[0] }
        return LeafBox("stsz", 12L + if (uniform) 0L else 4L * track.count) { sink ->
            sink.u32(0L)
            sink.u32(if (uniform) sizes[0].toLong() else 0L)
            sink.u32(track.count.toLong())
            if (!uniform) for (index in 0 until track.count) sink.u32(sizes[index].toLong())
        }
    }

    private fun chunkOffsets(layout: StreamCopyTrackLayout): OutBox {
        val offsets = layout.chunkOffsets
        val wide = offsets.any { it > UINT32 }
        val entryBytes = if (wide) 8L else 4L
        return LeafBox(if (wide) "co64" else "stco", 8L + entryBytes * layout.chunkCount) { sink ->
            sink.u32(0L)
            sink.u32(layout.chunkCount.toLong())
            for (offset in offsets) if (wide) sink.u64(offset) else sink.u32(offset)
        }
    }

    private fun times(sink: BoxSink, wide: Boolean, timescale: Long, duration: Long) {
        if (wide) {
            sink.u64(0L)
            sink.u64(0L)
            sink.u32(timescale)
            sink.u64(duration)
        } else {
            sink.u32(0L)
            sink.u32(0L)
            sink.u32(timescale)
            sink.u32(duration)
        }
    }

    /** How many runs of equal neighbours [count] entries make. */
    private inline fun runs(count: Int, sameAsPrevious: (Int) -> Boolean): Int {
        var runs = if (count > 0) 1 else 0
        for (index in 1 until count) if (!sameAsPrevious(index)) runs += 1
        return runs
    }

    private inline fun eachRun(
        count: Int,
        sameAsPrevious: (Int) -> Boolean,
        run: (first: Int, count: Int) -> Unit,
    ) {
        var first = 0
        for (index in 1..count) {
            if (index == count || !sameAsPrevious(index)) {
                run(first, index - first)
                first = index
            }
        }
    }

    private const val VIDEO_TRACK = 1
    private const val AUDIO_TRACK = 2
    private const val VERSION_1 = 0x01000000L
    private const val TRACK_ENABLED_IN_MOVIE = 0x3L
    private const val FIXED_ONE = 0x00010000L
    private const val VOLUME_ONE = 0x0100
    private const val UNDETERMINED = 0x55c4
    private val IDENTITY_MATRIX: ByteArray = ByteBuffer.allocate(36).apply {
        putInt(0x00010000)
        putInt(0)
        putInt(0)
        putInt(0)
        putInt(0x00010000)
        putInt(0)
        putInt(0)
        putInt(0)
        putInt(0x40000000)
    }.array()
}
