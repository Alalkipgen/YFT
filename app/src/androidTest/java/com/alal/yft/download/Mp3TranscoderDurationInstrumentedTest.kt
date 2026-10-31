package com.alal.yft.download

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp3Transcoder
import com.alal.yft.core.download.LameMp3Encoder
import com.alal.yft.core.download.Mp3EncoderFactory
import com.alal.yft.core.download.Mp3FrameEncoder
import com.alal.yft.core.download.Mp3TranscodeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.Mp3Encoding
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 fix R5 on the emulator: the MP3 has its source's length. The decoder's thread hands
 * LAME every sample the AAC decoder gives, once and in order (the same count and checksum as a
 * plain decode of the source, also while LAME is slow and the decoder waits), and the MP3
 * decodes to as many samples plus at most LAME's delay and last-frame padding. A conversion
 * that fails or is cancelled leaves no decoder thread behind.
 */
@RunWith(AndroidJUnit4::class)
class Mp3TranscoderDurationInstrumentedTest {
    private val directory = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "mp3-duration",
    ).apply { mkdirs() }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aMinuteOfStereoSoundKeepsItsLengthAndEverySample() =
        assertKeepsItsSound("mp3/stereo-44k.m4a", seconds = 60)

    @Test
    fun monoSoundKeepsItsLengthAndEverySample() =
        assertKeepsItsSound("mp3/mono-48k.m4a", seconds = 20)

    @Test
    fun aFormatLameRefusesFailsAndLeavesNoDecoderThread() = runBlocking {
        val refusing = Mp3EncoderFactory { _, _, _ -> null }

        val result = AndroidMp3Transcoder(refusing).transcode(
            source = Mp3TestFiles.asset(directory, "mp3/stereo-44k.m4a"),
            output = File(directory, "refused.mp3"),
            encoding = Mp3Encoding(128, null),
        )

        assertEquals(
            DownloadFailureReason.INCOMPATIBLE_TRACKS,
            (result as Mp3TranscodeResult.Failure).reason,
        )
        assertNoDecoderThread()
    }

    @Test
    fun aCancelledConversionStopsAtOnceAndLeavesNoDecoderThread() = runBlocking {
        val (source, _) = Mp3TestFiles.repeated(
            directory,
            Mp3TestFiles.asset(directory, "mp3/stereo-44k.m4a"),
            targetUs = 60_000_000L,
        )
        // 5 ms a frame: the hand-off fills and the decoder's thread waits for room.
        val slow = RecordingEncoders(slowCalls = Int.MAX_VALUE, slowMillis = 5)
        val conversion = launch(Dispatchers.IO) {
            AndroidMp3Transcoder(slow).transcode(
                source,
                File(directory, "cancelled.mp3"),
                Mp3Encoding(128, null),
            )
        }
        delay(500)
        val cancelled = SystemClock.elapsedRealtime()

        conversion.cancelAndJoin()

        val took = SystemClock.elapsedRealtime() - cancelled
        assertTrue("stopped $took ms after the cancel", took < 2_000)
        assertTrue("it had started", slow.samplesPerChannel > 0)
        assertNoDecoderThread()
    }

    private fun assertKeepsItsSound(fixture: String, seconds: Int) = runBlocking {
        val (source, _) = Mp3TestFiles.repeated(
            directory,
            Mp3TestFiles.asset(directory, fixture),
            targetUs = seconds * 1_000_000L,
        )
        val decoded = Mp3TestFiles.sound(source)
        // Slow at first, so the decoder runs ahead, fills the hand-off and waits.
        val lame = RecordingEncoders(slowCalls = SLOW_CALLS, slowMillis = 1)
        val output = File(directory, "kept.mp3")

        val result = AndroidMp3Transcoder(lame).transcode(source, output, Mp3Encoding(128, "R5"))

        assertTrue("$result", result is Mp3TranscodeResult.Completed)
        assertEquals(decoded.sampleRate, lame.sampleRate)
        assertEquals(decoded.channels, lame.channels)
        assertEquals("samples LAME got", decoded.samplesPerChannel, lame.samplesPerChannel)
        assertEquals("their checksum, in order", decoded.checksum, lame.checksum)
        val mp3 = Mp3TestFiles.sound(output)
        assertEquals(decoded.sampleRate, mp3.sampleRate)
        assertEquals(decoded.channels, mp3.channels)
        val extra = mp3.samplesPerChannel - decoded.samplesPerChannel
        Log.i(
            TAG,
            "$seconds-second ${decoded.channels}-channel source: LAME got all " +
                "${decoded.samplesPerChannel} decoded samples a channel in order; the MP3 " +
                "decodes to ${mp3.samplesPerChannel} (+$extra, " +
                "${extra * 1_000 / decoded.sampleRate} ms)",
        )
        // LAME's delay (576 samples) and its last frame's padding: under 3 frames of 1 152.
        assertTrue("$extra samples more", extra in 0..MAX_EXTRA_SAMPLES)
        assertNoDecoderThread()
    }

    private fun assertNoDecoderThread() {
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (decoderThreads() > 0 && SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        assertEquals("decoder threads left", 0, decoderThreads())
    }

    private fun decoderThreads() = Thread.getAllStackTraces().keys.count { thread ->
        thread.name == DECODER_THREAD && thread.isAlive
    }

    /** LAME, recording what it gets; its first [slowCalls] encodes wait [slowMillis] each. */
    private class RecordingEncoders(
        private val slowCalls: Int,
        private val slowMillis: Long,
    ) : Mp3EncoderFactory {
        @Volatile var sampleRate = 0
        @Volatile var channels = 0
        @Volatile var samplesPerChannel = 0L
        @Volatile var checksum = 0L
        private var calls = 0

        override fun open(sampleRate: Int, channels: Int, bitrateKbps: Int): Mp3FrameEncoder? {
            val lame = LameMp3Encoder.open(sampleRate, channels, bitrateKbps) ?: return null
            this.sampleRate = sampleRate
            this.channels = channels
            return object : Mp3FrameEncoder {
                override fun encode(pcm: ShortArray, samplesPerChannel: Int, sink: OutputStream) {
                    if (calls++ < slowCalls) Thread.sleep(slowMillis)
                    this@RecordingEncoders.samplesPerChannel += samplesPerChannel
                    checksum = Mp3TestFiles.checksum(checksum, pcm, samplesPerChannel * channels)
                    lame.encode(pcm, samplesPerChannel, sink)
                }

                override fun flush(sink: OutputStream) = lame.flush(sink)

                override fun close() = lame.close()
            }
        }
    }

    private companion object {
        const val TAG = "YftMp3Timing"
        const val DECODER_THREAD = "yft-mp3-decode"
        const val SLOW_CALLS = 200
        const val MAX_EXTRA_SAMPLES = 3 * 1_152L
    }
}
