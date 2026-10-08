package com.alal.yft.core.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.abs

/** P35: positioned writes into a destination's descriptor through Android's `Os` calls. */
internal object AndroidStreamCopyOutputs : StreamCopyOutputs {
    override fun open(output: MuxOutput): OpenStreamCopyOutput = when (output) {
        is MuxOutput.ToFile -> ChannelStreamCopyOutputs.open(output)
        is MuxOutput.ToDescriptor -> DescriptorOutput(output.descriptor)
    }

    override fun discard(output: MuxOutput) {
        when (output) {
            is MuxOutput.ToFile -> ChannelStreamCopyOutputs.discard(output)
            is MuxOutput.ToDescriptor -> {
                Os.ftruncate(output.descriptor, 0L)
                Os.lseek(output.descriptor, 0L, OsConstants.SEEK_SET)
            }
        }
    }

    /** The descriptor stays open: its owner closes it. */
    private class DescriptorOutput(private val descriptor: FileDescriptor) : OpenStreamCopyOutput {
        override fun write(source: ByteBuffer, position: Long) {
            var at = position
            while (source.hasRemaining()) at += Os.pwrite(descriptor, source, at)
        }

        override fun truncate(size: Long) {
            if (Os.fstat(descriptor).st_size > size) Os.ftruncate(descriptor, size)
        }

        override fun close() = Unit
    }
}

/**
 * Reads a stream-copied file back with Android's MediaExtractor before it counts (P35): two
 * tracks with the inputs' formats (MIME, picture size, sample rate, channels), durations within
 * a frame, and the first samples, a few sync samples and every sample from the last sync sample
 * on equal byte for byte and in time to the inputs'; that last stretch also proves the count.
 * AVC samples come back with start codes, so the input's are compared in that form.
 */
internal class MediaExtractorStreamCopyCheck : StreamCopyCheck {
    override fun check(
        output: MuxOutput,
        layout: StreamCopyLayout,
        videoFile: File,
        audioFile: File,
    ): String? {
        val tracks = listOf(layout.video to videoFile, layout.audio to audioFile)
        for ((index, entry) in tracks.withIndex()) {
            val (track, input) = entry
            val name = if (index == 0) "video" else "audio"
            val reason = try {
                TrackCheck(output, track, index, input, name).run()
            } catch (error: Exception) {
                "$name unreadable, ${error.javaClass.simpleName}"
            }
            if (reason != null) return reason
        }
        return null
    }

    private class TrackCheck(
        private val output: MuxOutput,
        private val layout: StreamCopyTrackLayout,
        private val trackIndex: Int,
        private val inputFile: File,
        private val name: String,
    ) {
        private val track = layout.track
        private val timescale = track.timescale
        private val tickUs = (MICROS + timescale - 1) / timescale
        private val buffer = ByteBuffer.allocateDirect(maxSampleBytes() * 2 + SLACK_BYTES)

        /** Composition ticks and extractor time of the first sample whose time counts. */
        private var baseTicks: Long? = null
        private var baseUs = 0L

        /** The media time the edit list starts at: earlier samples may come back clamped. */
        private val editStart = maxOf(0L, -layout.editOffset)

        fun run(): String? {
            val inputFormat = inputFormat()
            val extractor = MediaExtractor()
            try {
                when (output) {
                    is MuxOutput.ToFile -> extractor.setDataSource(output.file.absolutePath)
                    is MuxOutput.ToDescriptor -> extractor.setDataSource(output.descriptor)
                }
                if (extractor.trackCount != 2) return "${extractor.trackCount} tracks"
                val format = extractor.getTrackFormat(trackIndex)
                formatMismatch(format, inputFormat)?.let { return it }
                extractor.selectTrack(trackIndex)
                return RandomAccessFile(inputFile, "r").use { input ->
                    samplesMismatch(extractor, input)
                }
            } finally {
                extractor.release()
            }
        }

        private fun inputFormat(): MediaFormat {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(inputFile.absolutePath)
                if (extractor.trackCount != 1) unsupported("input tracks")
                return extractor.getTrackFormat(0)
            } finally {
                extractor.release()
            }
        }

        private fun formatMismatch(format: MediaFormat, input: MediaFormat): String? {
            if (format.getString(MediaFormat.KEY_MIME) != input.getString(MediaFormat.KEY_MIME)) {
                return "$name type"
            }
            val keys = if (track.kind == StreamCopyTrackKind.VIDEO) {
                listOf(MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT)
            } else {
                listOf(MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT)
            }
            for (key in keys) {
                if (format.intOrNull(key) != input.intOrNull(key)) return "$name format"
            }
            val frameUs = ticksToMicros(maxDuration(), timescale) + MILLI_US +
                ticksToMicros(abs(layout.editOffset), timescale)
            val mergedUs = format.longOrNull(MediaFormat.KEY_DURATION)
            val plannedUs = ticksToMicros(track.mediaDuration, timescale)
            if (mergedUs != null && abs(mergedUs - plannedUs) > frameUs) return "$name duration"
            val inputUs = input.longOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0 }
            if (mergedUs != null && inputUs != null && abs(mergedUs - inputUs) > frameUs) {
                return "$name duration"
            }
            return null
        }

        private fun samplesMismatch(extractor: MediaExtractor, input: RandomAccessFile): String? {
            // The first samples, in decode order.
            val first = minOf(FIRST_SAMPLES, track.count)
            for (index in 0 until first) {
                sampleMismatch(extractor, input, index)?.let { return it }
                extractor.advance()
            }
            // A few sync samples across the file, then everything from the last one on.
            val syncs = syncSamples()
            if (syncs.isEmpty()) return "$name sync samples"
            val picks = listOf(syncs.size / 3, syncs.size * 2 / 3).distinct().map(syncs::get)
            for (index in picks) {
                val landed = seek(extractor, index, syncs) ?: return "$name seek"
                sampleMismatch(extractor, input, landed)?.let { return it }
            }
            val tailStart = if (track.kind == StreamCopyTrackKind.VIDEO) {
                syncs.last()
            } else {
                maxOf(0, track.count - TAIL_SAMPLES)
            }
            val landed = seek(extractor, tailStart, syncs) ?: return "$name seek"
            var index = landed
            while (extractor.sampleTrackIndex == trackIndex) {
                if (index >= track.count) return "$name has more samples"
                // A long stretch is counted; its start and its last sample are compared.
                if (index - landed < TAIL_SAMPLES * 4 || index == track.count - 1) {
                    sampleMismatch(extractor, input, index)?.let { return it }
                }
                index += 1
                extractor.advance()
            }
            return if (index == track.count) null else "$name has fewer samples"
        }

        private fun syncSamples(): IntArray {
            val syncs = IntArray((0 until track.count).count(track::isSync))
            var next = 0
            for (index in 0 until track.count) if (track.isSync(index)) syncs[next++] = index
            return syncs
        }

        /** Seeks near sample [index]; returns the sync sample the extractor landed on. */
        private fun seek(extractor: MediaExtractor, index: Int, syncs: IntArray): Int? {
            val base = baseTicks ?: return null
            val targetUs = baseUs + scale(layout.compositionTime(index) - base, timescale, MICROS)
            extractor.seekTo(targetUs + tickUs / 2, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            if (extractor.sampleTrackIndex != trackIndex) return null
            val landedTicks = base + scale(extractor.sampleTime - baseUs, MICROS, timescale)
            // Sync samples are in time order: the nearest by time is the one landed on.
            var low = 0
            var high = syncs.size - 1
            while (low < high) {
                val middle = (low + high) ushr 1
                if (layout.compositionTime(syncs[middle]) < landedTicks) {
                    low = middle + 1
                } else {
                    high = middle
                }
            }
            val after = syncs[low]
            val before = syncs[maxOf(0, low - 1)]
            val distanceAfter = abs(layout.compositionTime(after) - landedTicks)
            val distanceBefore = abs(layout.compositionTime(before) - landedTicks)
            return if (distanceBefore < distanceAfter) before else after
        }

        private fun sampleMismatch(
            extractor: MediaExtractor,
            input: RandomAccessFile,
            index: Int,
        ): String? {
            if (extractor.sampleTrackIndex != trackIndex) return "$name sample $index missing"
            val composition = layout.compositionTime(index)
            val timeUs = extractor.sampleTime
            if (composition >= editStart) {
                val base = baseTicks
                if (base == null) {
                    baseTicks = composition
                    baseUs = timeUs
                } else {
                    val expectedUs = baseUs + scale(composition - base, timescale, MICROS)
                    if (abs(timeUs - expectedUs) > 2 * tickUs + 2) return "$name time"
                }
            }
            val sync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
            if (track.kind == StreamCopyTrackKind.VIDEO && sync != track.isSync(index)) {
                return "$name sync flag"
            }
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            val expected = expectedBytes(input, index)
            if (size != expected.size) return "$name sample size"
            for (at in expected.indices) {
                if (buffer.get(at) != expected[at]) return "$name sample bytes"
            }
            return null
        }

        /** The input's sample as MediaExtractor gives it: AVC NAL units with start codes. */
        private fun expectedBytes(input: RandomAccessFile, index: Int): ByteArray {
            val raw = ByteArray(track.sizes[index])
            input.seek(track.offsets[index])
            input.readFully(raw)
            val lengthSize = track.nalLengthSize ?: return raw
            val converted = ByteArrayOutputStream(raw.size + SLACK_BYTES)
            var at = 0
            while (at < raw.size) {
                if (at + lengthSize > raw.size) return raw
                var length = 0L
                repeat(lengthSize) { length = (length shl 8) or (raw[at++].toLong() and 0xff) }
                if (at + length > raw.size) return raw
                if (length == 0L) continue
                converted.write(START_CODE)
                converted.write(raw, at, length.toInt())
                at += length.toInt()
            }
            return converted.toByteArray()
        }

        private fun maxSampleBytes(): Int = (0 until track.count).maxOf { track.sizes[it] }

        private fun maxDuration(): Long =
            (0 until track.count).maxOf { track.durations[it] }.toLong()
    }

    private companion object {
        const val FIRST_SAMPLES = 8
        const val TAIL_SAMPLES = 8
        const val SLACK_BYTES = 1_024
        const val MICROS = 1_000_000L
        const val MILLI_US = 1_000L
        val START_CODE = byteArrayOf(0, 0, 0, 1)

        /** [value] of [from] in ticks of [to], rounded down, either sign. */
        fun scale(value: Long, from: Long, to: Long): Long =
            if (value < 0) -rescale(-value, from, to) else rescale(value, from, to)

        fun MediaFormat.intOrNull(key: String): Int? =
            if (containsKey(key)) getInteger(key) else null

        fun MediaFormat.longOrNull(key: String): Long? =
            if (containsKey(key)) getLong(key) else null
    }
}
