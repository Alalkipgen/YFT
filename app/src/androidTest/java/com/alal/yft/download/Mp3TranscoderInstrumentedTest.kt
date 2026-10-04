package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp3Transcoder
import com.alal.yft.core.download.Id3v2Tag
import com.alal.yft.core.download.Mp3TranscodeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.Mp3Encoding
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T18 on a real device or emulator: MediaCodec decodes short AAC fixtures (made with ffmpeg's
 * AAC encoder) and LAME encodes them; Android's own extractor must read the MP3 back.
 */
@RunWith(AndroidJUnit4::class)
class Mp3TranscoderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val directory = File(instrumentation.targetContext.cacheDir, "mp3-test").apply {
        mkdirs()
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun stereoAacBecomesA192KbpsMp3WithItsTitle() = runBlocking {
        val output = File(directory, "stereo.mp3")

        val result = AndroidMp3Transcoder().transcode(
            source = asset("mp3/stereo-44k.m4a"),
            output = output,
            encoding = Mp3Encoding(192, "Ocean waves"),
        )

        val bytes = (result as Mp3TranscodeResult.Completed).bytesWritten
        val data = output.readBytes()
        assertEquals(data.size.toLong(), bytes)
        val tag = Id3v2Tag.title("Ocean waves")
        assertArrayEquals(tag, data.copyOf(tag.size))
        assertFrame(data, tag.size, bitrateIndex = 11)
        // Two seconds at 192 kbps is 48 000 bytes, plus the encoder's padding frames.
        assertTrue("size $bytes", bytes in 44_000L..60_000L)
        assertReadsBackAsMp3(output, durationMs = 2_000, channels = 2)
    }

    @Test
    fun monoAacBecomesA128KbpsMp3() = runBlocking {
        val output = File(directory, "mono.mp3")

        val result = AndroidMp3Transcoder().transcode(
            source = asset("mp3/mono-48k.m4a"),
            output = output,
            encoding = Mp3Encoding(128, null),
        )

        assertTrue(result is Mp3TranscodeResult.Completed)
        val data = output.readBytes()
        assertFrame(data, 0, bitrateIndex = 9)
        assertReadsBackAsMp3(output, durationMs = 1_000, channels = 1)
    }

    @Test
    fun aFileWithoutAacAudioFailsAsIncompatible() = runBlocking {
        val source = File(directory, "not-audio.m4a").apply { writeBytes(ByteArray(4_096) { 7 }) }

        val result = AndroidMp3Transcoder().transcode(
            source = source,
            output = File(directory, "none.mp3"),
            encoding = Mp3Encoding(192, "x"),
        )

        assertEquals(
            Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS),
            result,
        )
    }

    private fun asset(name: String): File {
        val file = File(directory, name.substringAfterLast('/'))
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file
    }

    /** An MPEG-1 Layer III frame header (no CRC) with the given bitrate index. */
    private fun assertFrame(data: ByteArray, offset: Int, bitrateIndex: Int) {
        assertEquals(0xFF, data[offset].toInt() and 0xFF)
        assertEquals(0xFB, data[offset + 1].toInt() and 0xFF)
        assertEquals(bitrateIndex, (data[offset + 2].toInt() and 0xF0) shr 4)
    }

    private fun assertReadsBackAsMp3(file: File, durationMs: Long, channels: Int) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_MPEG, format.getString(MediaFormat.KEY_MIME))
            assertEquals(channels, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000
            assertTrue("duration $duration ms", abs(duration - durationMs) <= 250)
        } finally {
            extractor.release()
        }
    }
}
