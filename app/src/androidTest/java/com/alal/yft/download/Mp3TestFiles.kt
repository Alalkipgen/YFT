package com.alal.yft.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The MP3 tests' sources (G2, R5): the AAC fixtures, longer copies of them and their sound. */
internal object Mp3TestFiles {
    private const val AAC_FRAME_SAMPLES = 1_024
    private const val WAIT_US = 5_000L
    private const val DECODE_LIMIT_MS = 120_000L

    /** The sound of a file's first track as MediaCodec decodes it. */
    data class Sound(
        val samplesPerChannel: Long,
        val sampleRate: Int,
        val channels: Int,
        val checksum: Long,
    )

    /** The test APK's asset [name], copied into [directory]. */
    fun asset(directory: File, name: String): File {
        val file = File(directory, name.substringAfterLast('/'))
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file
    }

    /** [fixture]'s AAC frames repeated to at least [targetUs]: the M4A and its length. */
    fun repeated(directory: File, fixture: File, targetUs: Long): Pair<File, Long> {
        val extractor = MediaExtractor()
        val samples = mutableListOf<ByteArray>()
        val format: MediaFormat
        try {
            extractor.setDataSource(fixture.path)
            extractor.selectTrack(0)
            format = extractor.getTrackFormat(0)
            val buffer = ByteBuffer.allocate(64 * 1_024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.limit(size)
                buffer.position(0)
                samples += ByteArray(size).also { buffer.get(it) }
                extractor.advance()
            }
        } finally {
            extractor.release()
        }
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val frameUs = AAC_FRAME_SAMPLES * 1_000_000L / rate
        val file = File(directory, "long-${fixture.nameWithoutExtension}.m4a")
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var written = 0L
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            while (written * frameUs < targetUs) {
                samples.forEach { sample ->
                    info.set(0, sample.size, written * frameUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    muxer.writeSampleData(track, ByteBuffer.wrap(sample), info)
                    written++
                }
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
        return file to written * frameUs
    }

    /** Decodes [file]'s first track with MediaCodec, one buffer at a time on this thread. */
    fun sound(file: File): Sound {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.path)
        var codec: MediaCodec? = null
        try {
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var pcm = ShortArray(0)
            var samples = 0L
            var checksum = 0L
            val deadline = SystemClock.elapsedRealtime() + DECODE_LIMIT_MS
            while (true) {
                check(SystemClock.elapsedRealtime() < deadline) { "decoding ${file.name} hangs" }
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(WAIT_US)
                    if (input >= 0) {
                        val size = extractor.readSampleData(decoder.getInputBuffer(input)!!, 0)
                        if (size < 0) {
                            val end = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            decoder.queueInputBuffer(input, 0, 0, 0, end)
                            inputDone = true
                        } else {
                            val time = extractor.sampleTime.coerceAtLeast(0)
                            decoder.queueInputBuffer(input, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, WAIT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sampleRate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                if (index < 0) continue
                if (info.size > 0) {
                    val buffer = decoder.getOutputBuffer(index)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val count = shorts.remaining()
                    if (pcm.size < count) pcm = ShortArray(count)
                    shorts.get(pcm, 0, count)
                    samples += count / channels
                    checksum = checksum(checksum, pcm, count)
                }
                decoder.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            return Sound(samples, sampleRate, channels, checksum)
        } finally {
            codec?.let { decoder ->
                runCatching { decoder.stop() }
                decoder.release()
            }
            extractor.release()
        }
    }

    /** [checksum] going on over [count] shorts of [pcm], in their order. */
    fun checksum(checksum: Long, pcm: ShortArray, count: Int): Long {
        var sum = checksum
        for (index in 0 until count) sum = sum * 31 + pcm[index]
        return sum
    }
}
