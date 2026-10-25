package com.alal.yft.core.download

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Where the stream copy writes the merged file (P35): a file channel, or a test's sink. */
internal interface StreamCopyOutput {
    /** Writes all of [source]'s remaining bytes at [position]. */
    fun write(source: ByteBuffer, position: Long)

    /** Cuts the file to [size] bytes when it is longer. */
    fun truncate(size: Long)
}

/** A channel of the merged file; positioned writes, so a descriptor's offset stays put. */
internal class FileChannelStreamCopyOutput(private val channel: FileChannel) : StreamCopyOutput {
    override fun write(source: ByteBuffer, position: Long) {
        var at = position
        while (source.hasRemaining()) at += channel.write(source, at)
    }

    override fun truncate(size: Long) {
        if (channel.size() > size) channel.truncate(size)
    }
}

/**
 * Where each track goes in the merged file (P35): its samples in chunks of about one second,
 * their times and its edit list. A sample's composition time in the merged file is its decode
 * time since the track's first sample + its composition offset + [compositionShift]; the edit
 * list then presents it at that time + [editOffset], which keeps both tracks where the inputs
 * presented them, the earlier one starting at 0.
 */
internal class StreamCopyTrackLayout(
    val track: Mp4Track,
    /** Added to every composition offset so none is negative (`ctts` version 0). */
    val compositionShift: Int,
    /** In ticks: > 0 an empty edit first (a delay), < 0 the media starts that far in. */
    val editOffset: Long,
    /** The end of the last presented sample, in composition ticks. */
    val compositionEnd: Long,
    val chunkFirstSample: IntArray,
    val chunkSamples: IntArray,
    val chunkDecodeTimes: LongArray,
    val chunkCount: Int,
    /**
     * D1: the composition time of the track's first presented sample, in ticks, when the edit
     * list should start there: a track presented after the start then waits in an empty edit,
     * which every player reads the same way (FFmpeg moves a sample presented after an edit's
     * media time back to that time). Null for the P35 merge, whose edits are as they were.
     */
    private val firstComposition: Long? = null,
) {
    val chunkOffsets = LongArray(chunkCount)

    /** The edit list in movie ticks ([MOVIE_TIMESCALE]): segment duration to media time. */
    val edits: List<Pair<Long, Long>> = firstComposition?.let(::editsFrom) ?: when {
        editOffset > 0 -> {
            val delay = rescale(editOffset, track.timescale, MOVIE_TIMESCALE, round = true)
            val presented = rescale(compositionEnd, track.timescale, MOVIE_TIMESCALE)
            if (delay > 0) listOf(delay to -1L, presented to 0L) else emptyList()
        }
        editOffset < 0 -> {
            val presented = compositionEnd + editOffset
            if (presented <= 0) unsupported("edit list")
            listOf(rescale(presented, track.timescale, MOVIE_TIMESCALE) to -editOffset)
        }
        else -> emptyList()
    }

    /**
     * D1: an empty edit while the track is not yet presented, then the media from [first] on (or
     * from where the start is, for a track presented before it).
     */
    private fun editsFrom(first: Long): List<Pair<Long, Long>> {
        val delay = editOffset + first
        val mediaTime = if (delay >= 0) first else -editOffset
        val presented = compositionEnd - mediaTime
        if (presented <= 0) unsupported("edit list")
        val shown = rescale(presented, track.timescale, MOVIE_TIMESCALE)
        val wait = if (delay > 0) rescale(delay, track.timescale, MOVIE_TIMESCALE, true) else 0L
        return when {
            wait > 0 -> listOf(wait to -1L, shown to mediaTime)
            mediaTime > 0 -> listOf(shown to mediaTime)
            else -> emptyList()
        }
    }

    /** The track's length in movie ticks, for `tkhd` and `mvhd`. */
    val movieDuration: Long = if (edits.isEmpty()) {
        rescale(track.mediaDuration, track.timescale, MOVIE_TIMESCALE, round = true)
    } else {
        edits.sumOf { it.first }
    }

    /** Sample [index]'s decode time since the track's first sample, in ticks. */
    fun decodeTime(index: Int): Long {
        var low = 0
        var high = chunkCount - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (chunkFirstSample[middle] <= index) low = middle else high = middle - 1
        }
        var time = chunkDecodeTimes[low]
        for (sample in chunkFirstSample[low] until index) time += track.durations[sample]
        return time
    }

    /** Sample [index]'s composition time in the merged file, before the edit list, in ticks. */
    fun compositionTime(index: Int): Long =
        decodeTime(index) + track.compositionOffset(index) + compositionShift

    companion object {
        const val MOVIE_TIMESCALE = 1_000L
    }
}

/**
 * The merged file's layout (P35): `ftyp`, one `mdat` with the chunks of both tracks in time
 * order, then `moov`. Every size is known before a byte is written, so nothing seeks back.
 */
internal class StreamCopyLayout(
    val video: StreamCopyTrackLayout,
    val audio: StreamCopyTrackLayout,
) {
    val payloadBytes: Long = video.track.totalBytes + audio.track.totalBytes

    /** 16 bytes (a 64-bit size) when the samples need more than 4 GiB. */
    val mdatHeaderBytes: Int =
        if (payloadBytes + BOX_HEADER > UINT32) LARGE_BOX_HEADER else BOX_HEADER
    val payloadStart: Long = FTYP.size.toLong() + mdatHeaderBytes

    /** The chunks in file order: track (0 video, 1 audio) and chunk number of each. */
    val order: IntArray
    val orderChunks: IntArray

    init {
        val total = video.chunkCount + audio.chunkCount
        order = IntArray(total)
        orderChunks = IntArray(total)
        var nextVideo = 0
        var nextAudio = 0
        var position = payloadStart
        for (slot in 0 until total) {
            val takeVideo = nextAudio >= audio.chunkCount || nextVideo < video.chunkCount &&
                chunkTime(video, nextVideo) <= chunkTime(audio, nextAudio)
            val layout = if (takeVideo) video else audio
            val chunk = if (takeVideo) nextVideo++ else nextAudio++
            order[slot] = if (takeVideo) 0 else 1
            orderChunks[slot] = chunk
            layout.chunkOffsets[chunk] = position
            position += chunkBytes(layout, chunk)
        }
    }

    val payloadEnd: Long = payloadStart + payloadBytes

    fun chunkBytes(layout: StreamCopyTrackLayout, chunk: Int): Long {
        val first = layout.chunkFirstSample[chunk]
        var bytes = 0L
        for (sample in first until first + layout.chunkSamples[chunk]) {
            bytes += layout.track.sizes[sample]
        }
        return bytes
    }

    /** Where the chunk starts on the merged file's timeline, in seconds, for the order only. */
    private fun chunkTime(layout: StreamCopyTrackLayout, chunk: Int): Double {
        val ticks = layout.chunkDecodeTimes[chunk] + layout.compositionShift + layout.editOffset
        return ticks.toDouble() / layout.track.timescale
    }

    companion object {
        /** `ftyp`: isom, minor version 512, compatible isom, iso2, avc1, mp41 (as MediaMuxer). */
        val FTYP: ByteArray = ByteBuffer.allocate(32).apply {
            putInt(32)
            put("ftypisom".toByteArray(Charsets.ISO_8859_1))
            putInt(512)
            put("isomiso2avc1mp41".toByteArray(Charsets.ISO_8859_1))
        }.array()

        /** Lays out [video] and [audio] in chunks of about a second, [maxChunkBytes] at most. */
        fun of(
            video: Mp4Track,
            audio: Mp4Track,
            maxChunkBytes: Long = MAX_CHUNK_BYTES,
        ): StreamCopyLayout {
            val timings = listOf(video, audio).map(::Timing)
            // Both tracks keep the inputs' times; the one presented first starts at 0.
            val startUs = timings.minOf { it.startUs }
            val layouts = listOf(video, audio).mapIndexed { index, track ->
                val timing = timings[index]
                val startTicks = microsToTicks(startUs, track.timescale)
                val editOffset = track.firstDecodeTime - timing.shift + track.editShift - startTicks
                chunked(track, timing, editOffset, maxChunkBytes)
            }
            return StreamCopyLayout(layouts[0], layouts[1])
        }

        /**
         * D1 (the HLS remux): the layouts of [tracks] whose samples are already in the file,
         * chunk by chunk ([PlacedChunks]), timed as [of] times them: each keeps its own times
         * and the one presented first starts at 0; each edit list starts at the track's first
         * presented sample.
         */
        fun placed(tracks: List<Pair<Mp4Track, PlacedChunks>>): List<StreamCopyTrackLayout> {
            require(tracks.isNotEmpty())
            val timings = tracks.map { (track, _) -> Timing(track) }
            val startUs = timings.minOf { it.startUs }
            return tracks.mapIndexed { index, (track, chunks) ->
                val timing = timings[index]
                val startTicks = microsToTicks(startUs, track.timescale)
                val editOffset =
                    track.firstDecodeTime - timing.shift + track.editShift - startTicks
                val times = LongArray(chunks.count)
                var sample = 0
                var time = 0L
                for (chunk in 0 until chunks.count) {
                    while (sample < chunks.firstSample[chunk]) {
                        time += track.durations[sample]
                        sample += 1
                    }
                    times[chunk] = time
                }
                StreamCopyTrackLayout(
                    track = track,
                    compositionShift = timing.shift,
                    editOffset = editOffset,
                    compositionEnd = timing.compositionEnd,
                    chunkFirstSample = chunks.firstSample.copyOf(chunks.count),
                    chunkSamples = chunks.samples.copyOf(chunks.count),
                    chunkDecodeTimes = times,
                    chunkCount = chunks.count,
                    firstComposition = timing.firstComposition,
                ).also { layout ->
                    chunks.offsets.copyInto(layout.chunkOffsets, 0, 0, chunks.count)
                }
            }
        }

        private fun chunked(
            track: Mp4Track,
            timing: Timing,
            editOffset: Long,
            maxChunkBytes: Long,
        ): StreamCopyTrackLayout {
            var chunks = 0
            eachChunk(track, maxChunkBytes) { _, _, _ -> chunks += 1 }
            val firsts = IntArray(chunks)
            val counts = IntArray(chunks)
            val times = LongArray(chunks)
            var chunk = 0
            eachChunk(track, maxChunkBytes) { first, samples, decodeTime ->
                firsts[chunk] = first
                counts[chunk] = samples
                times[chunk] = decodeTime
                chunk += 1
            }
            return StreamCopyTrackLayout(
                track = track,
                compositionShift = timing.shift,
                editOffset = editOffset,
                compositionEnd = timing.compositionEnd,
                chunkFirstSample = firsts,
                chunkSamples = counts,
                chunkDecodeTimes = times,
                chunkCount = chunks,
            )
        }

        /** Cuts [track] into chunks of a second or [maxChunkBytes] at most, a sample at least. */
        private inline fun eachChunk(
            track: Mp4Track,
            maxChunkBytes: Long,
            chunk: (first: Int, samples: Int, decodeTime: Long) -> Unit,
        ) {
            var sample = 0
            var decodeTime = 0L
            while (sample < track.count) {
                val first = sample
                var ticks = 0L
                var bytes = 0L
                do {
                    ticks += track.durations[sample]
                    bytes += track.sizes[sample]
                    sample += 1
                } while (
                    sample < track.count && ticks < track.timescale &&
                    bytes + track.sizes[sample] <= maxChunkBytes
                )
                chunk(first, sample - first, decodeTime)
                decodeTime += ticks
            }
        }

        /** A track's composition range: the shift that makes offsets ≥ 0, start and end. */
        private class Timing(track: Mp4Track) {
            val shift: Int
            val compositionEnd: Long
            val startUs: Long

            /** D1: the first presented sample's composition time, in ticks, with [shift]. */
            val firstComposition: Long

            init {
                var minOffset = 0L
                var earliest = Long.MAX_VALUE
                var latestEnd = Long.MIN_VALUE
                var decodeTime = 0L
                for (index in 0 until track.count) {
                    val offset = track.compositionOffset(index).toLong()
                    minOffset = minOf(minOffset, offset)
                    earliest = minOf(earliest, decodeTime + offset)
                    latestEnd = maxOf(latestEnd, decodeTime + offset + track.durations[index])
                    decodeTime += track.durations[index]
                }
                val shift = -minOffset
                val maxOffset = (0 until track.count).maxOf { track.compositionOffset(it).toLong() }
                if (maxOffset + shift > Int.MAX_VALUE) unsupported("composition offsets")
                this.shift = shift.toInt()
                compositionEnd = latestEnd + shift
                firstComposition = earliest + shift
                val presented = track.firstDecodeTime + earliest + track.editShift
                startUs = ticksToMicros(presented.coerceAtLeast(0L), track.timescale)
            }
        }

        const val MAX_CHUNK_BYTES = 8L * 1_024 * 1_024
    }
}

/**
 * D1: a track's chunks as they were written: the first sample, the sample count and the file
 * offset of each of the first [count] chunks.
 */
internal class PlacedChunks(
    val firstSample: IntArray,
    val samples: IntArray,
    val offsets: LongArray,
    val count: Int,
) {
    init {
        require(count >= 0 && firstSample.size >= count && samples.size >= count)
        require(offsets.size >= count)
    }
}

/** [ticks] of [from] in ticks of [to]: rounded down, or to the nearest with [round]. */
internal fun rescale(ticks: Long, from: Long, to: Long, round: Boolean = false): Long {
    val whole = ticks / from * to
    val part = ticks % from * to
    return whole + if (round) (part + from / 2) / from else part / from
}

internal fun ticksToMicros(ticks: Long, timescale: Long): Long = rescale(ticks, timescale, MICROS)

internal fun microsToTicks(micros: Long, timescale: Long): Long =
    rescale(micros, MICROS, timescale, round = true)

private const val MICROS = 1_000_000L
