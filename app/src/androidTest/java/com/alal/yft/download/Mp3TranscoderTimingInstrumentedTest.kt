package com.alal.yft.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp3Transcoder
import com.alal.yft.core.download.Mp3Times
import com.alal.yft.core.download.Mp3TranscodeResult
import com.alal.yft.core.model.download.Mp3Encoding
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 fix G2 on the emulator: a 4-minute and a 1-hour AAC source (the 2-second fixture's
 * frames repeated) convert to MP3 at 128 kbps, and Android reads each MP3 back at its length.
 * For the report, each logs its Details line and how long the decode loop before G2 (one input
 * and one output a pass, 10 ms waits) takes to decode the same source; CI shows the
 * "YftMp3Timing" lines as notices.
 */
@RunWith(AndroidJUnit4::class)
class Mp3TranscoderTimingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val directory = File(instrumentation.targetContext.cacheDir, "mp3-timing").apply {
        mkdirs()
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aFourMinuteSourceConvertsAndSaysItsTimes() = convert(minutes = 4)

    @Test
    fun aOneHourSourceConvertsAndSaysItsTimes() = convert(minutes = 60)

    private fun convert(minutes: Int) = runBlocking {
        val (source, lengthUs) = repeated(asset("mp3/stereo-44k.m4a"), minutes * 60_000_000L)
        val output = File(directory, "long.mp3")
        val started = SystemClock.elapsedRealtime()

        val result = AndroidMp3Transcoder().transcode(source, output, Mp3Encoding(128, "Timing"))

        val total = SystemClock.elapsedRealtime() - started
        val line = (result as Mp3TranscodeResult.Completed).detail.orEmpty()
        assertTrue(line, LINE.matches(line))
        val before = legacyDecode(source)
        Log.i(
            TAG,
            "$minutes-minute source: after G2: $line (all ${seconds(total)}); before G2: $before",
        )
        assertReadsBack(output, lengthUs / 1_000)
    }

    /** The fixture's AAC frames repeated to at least [targetUs]: the file and its length. */
    private fun repeated(fixture: File, targetUs: Long): Pair<File, Long> {
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
        val file = File(directory, "long.m4a")
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

    /**
     * The decode loop before G2, decode only: at most one input and one output a pass, each
     * with a 10 ms wait. Stopped after [LEGACY_LIMIT_MS], saying how far it came.
     */
    private fun legacyDecode(source: File): String {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.path)
        extractor.selectTrack(0)
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            codec.configure(extractor.getTrackFormat(0), null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var pcm = ShortArray(0)
            var soundUs = 0L
            val started = SystemClock.elapsedRealtime()
            while (true) {
                val spent = SystemClock.elapsedRealtime() - started
                if (spent > LEGACY_LIMIT_MS) {
                    return "decode stopped after ${seconds(spent)} at " +
                        "${Mp3Times.length(soundUs / 1_000)} of sound"
                }
                if (!inputDone) {
                    val input = codec.dequeueInputBuffer(LEGACY_TIMEOUT_US)
                    if (input >= 0) {
                        val buffer = codec.getInputBuffer(input)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            val end = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            codec.queueInputBuffer(input, 0, 0, 0, end)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, LEGACY_TIMEOUT_US)
                if (index < 0) continue
                if (info.size > 0) {
                    val shorts = codec.getOutputBuffer(index)!!.asShortBuffer()
                    if (pcm.size < shorts.remaining()) pcm = ShortArray(shorts.remaining())
                    shorts.get(pcm, 0, shorts.remaining())
                    soundUs = info.presentationTimeUs
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            return "decode ${seconds(SystemClock.elapsedRealtime() - started)}"
        } finally {
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

    private fun asset(name: String): File {
        val file = File(directory, name.substringAfterLast('/'))
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file
    }

    private fun seconds(millis: Long) = String.format(Locale.US, "%.1f s", millis / 1_000.0)

    private companion object {
        const val TAG = "YftMp3Timing"
        const val AAC_FRAME_SAMPLES = 1_024
        const val LEGACY_TIMEOUT_US = 10_000L
        const val LEGACY_LIMIT_MS = 120_000L
        val LINE = Regex("""MP3: [0-9:]+ of sound, decode [0-9. a-z]+, encode [0-9. a-z]+""")
    }
}
