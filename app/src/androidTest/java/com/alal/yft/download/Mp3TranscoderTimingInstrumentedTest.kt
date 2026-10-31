package com.alal.yft.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp3Transcoder
import com.alal.yft.core.download.LameMp3Encoder
import com.alal.yft.core.download.Mp3FrameEncoder
import com.alal.yft.core.download.Mp3Times
import com.alal.yft.core.download.Mp3TranscodeResult
import com.alal.yft.core.model.download.Mp3Encoding
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 fixes G2 and R5 on the emulator: a 4-minute and a 1-hour AAC source (the 2-second
 * fixture's frames repeated) convert to MP3 at 128 kbps, and Android reads each MP3 back at its
 * length. For the report, each logs its Details line and how long G2's one-thread loop (decode
 * and LAME in turn, before R5) takes for the same source; CI shows the "YftMp3Timing" lines as
 * notices.
 */
@RunWith(AndroidJUnit4::class)
class Mp3TranscoderTimingInstrumentedTest {
    private val directory = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "mp3-timing",
    ).apply { mkdirs() }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aFourMinuteSourceConvertsAndSaysItsTimes() = convert(minutes = 4)

    @Test
    fun aOneHourSourceConvertsAndSaysItsTimes() = convert(minutes = 60)

    private fun convert(minutes: Int) = runBlocking {
        val (source, lengthUs) = Mp3TestFiles.repeated(
            directory,
            Mp3TestFiles.asset(directory, "mp3/stereo-44k.m4a"),
            targetUs = minutes * 60_000_000L,
        )
        val output = File(directory, "long.mp3")
        val started = SystemClock.elapsedRealtime()

        val result = AndroidMp3Transcoder().transcode(source, output, Mp3Encoding(128, "Timing"))

        val total = SystemClock.elapsedRealtime() - started
        val line = (result as Mp3TranscodeResult.Completed).detail.orEmpty()
        assertTrue(line, LINE.matches(line))
        val before = g2Loop(source)
        Log.i(TAG, "$minutes-minute source: R5: $line (all ${seconds(total)}); G2: $before")
        assertReadsBack(output, lengthUs / 1_000)
    }

    /**
     * G2's loop, on one thread: each pass queues every free input and takes every ready output,
     * each encoded by LAME (into nothing) before the next; it waits 5 ms only when neither moved.
     * Stopped after [G2_LIMIT_MS], saying how far it came.
     */
    private fun g2Loop(source: File): String {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.path)
        extractor.selectTrack(0)
        val format = extractor.getTrackFormat(0)
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var lame: Mp3FrameEncoder? = null
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val info = MediaCodec.BufferInfo()
            val nowhere = Nowhere()
            var inputDone = false
            var pcm = ShortArray(0)
            var soundUs = 0L
            var encodeNanos = 0L
            val started = System.nanoTime()
            decode@ while (true) {
                val spent = (System.nanoTime() - started) / 1_000_000
                if (spent > G2_LIMIT_MS) {
                    return "stopped after ${seconds(spent)} at " +
                        "${Mp3Times.length(soundUs / 1_000)} of sound"
                }
                var moved = false
                while (!inputDone) {
                    val input = codec.dequeueInputBuffer(0)
                    if (input < 0) break
                    val size = extractor.readSampleData(codec.getInputBuffer(input)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                    moved = true
                }
                var index = codec.dequeueOutputBuffer(info, 0)
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER && !moved) {
                    index = codec.dequeueOutputBuffer(info, G2_IDLE_WAIT_US)
                }
                while (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (index >= 0) {
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val count = shorts.remaining()
                            if (pcm.size < count) pcm = ShortArray(count)
                            shorts.get(pcm, 0, count)
                            val encodeStart = System.nanoTime()
                            val active = lame ?: LameMp3Encoder.open(sampleRate, channels, 128)!!
                                .also { lame = it }
                            active.encode(pcm, count / channels, nowhere)
                            encodeNanos += System.nanoTime() - encodeStart
                            soundUs = info.presentationTimeUs
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@decode
                    }
                    index = codec.dequeueOutputBuffer(info, 0)
                }
            }
            val flushStart = System.nanoTime()
            lame?.flush(nowhere)
            val ended = System.nanoTime()
            encodeNanos += ended - flushStart
            val all = (ended - started) / 1_000_000
            val encode = encodeNanos / 1_000_000
            return "decode ${seconds(all - encode)}, encode ${seconds(encode)} " +
                "(all ${seconds(all)})"
        } finally {
            lame?.close()
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }

    private fun assertReadsBack(file: File, lengthMs: Long) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_MPEG, format.getString(MediaFormat.KEY_MIME))
            val duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000
            assertTrue("duration $duration ms", abs(duration - lengthMs) <= 500 + lengthMs / 100)
        } finally {
            extractor.release()
        }
    }

    private fun seconds(millis: Long) = String.format(Locale.US, "%.1f s", millis / 1_000.0)

    /** LAME's MP3 for G2's loop goes nowhere: only its time counts. */
    private class Nowhere : OutputStream() {
        override fun write(b: Int) = Unit

        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private companion object {
        const val TAG = "YftMp3Timing"
        const val G2_IDLE_WAIT_US = 5_000L
        const val G2_LIMIT_MS = 60_000L
        val LINE = Regex("""MP3: [0-9:]+ of sound, decode [0-9. a-z]+, encode [0-9. a-z]+""")
    }
}
