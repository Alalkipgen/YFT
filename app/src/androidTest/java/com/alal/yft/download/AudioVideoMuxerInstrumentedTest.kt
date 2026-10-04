package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.download.LocalMuxResult
import com.alal.yft.detection.platformHasDecoder
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P4 on a real device or emulator: a one-second video-only MP4 in the DASH on-demand layout
 * (fragments behind a `sidx`, like Facebook's and YouTube's tracks) and a one-second AAC track,
 * both made with ffmpeg, become one MP4 that Android's own extractor reads back with both
 * tracks and every frame. AV1 only from Android 14, the first muxer that writes it.
 */
@RunWith(AndroidJUnit4::class)
class AudioVideoMuxerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val directory = File(instrumentation.targetContext.cacheDir, "mux-test").apply {
        mkdirs()
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun anAvcVideoAndItsAacSoundBecomeOneMp4() {
        val output = File(directory, "avc.mp4")

        val result = AndroidMp4AudioVideoMuxer().mux(
            asset("mux/video-avc.mp4"),
            asset("mux/audio-aac.m4a"),
            output,
        )

        assertEquals(output.length(), (result as LocalMuxResult.Completed).bytesWritten)
        assertMerged(output, MediaFormat.MIMETYPE_VIDEO_AVC)
    }

    @Test
    fun anAv1VideoAndItsAacSoundBecomeOneMp4FromAndroid14() {
        assumeTrue(Build.VERSION.SDK_INT >= AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK)
        Log.i(TAG, "AV1 decoder on this device: ${platformHasDecoder(AV1_MIME_TYPE)}")
        val output = File(directory, "av1.mp4")

        val result = AndroidMp4AudioVideoMuxer().mux(
            asset("mux/video-av1.mp4"),
            asset("mux/audio-aac.m4a"),
            output,
        )

        assertEquals(output.length(), (result as LocalMuxResult.Completed).bytesWritten)
        assertMerged(output, AV1_MIME_TYPE)
    }

    private fun assertMerged(file: File, videoMimeType: String) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val mimeTypes = (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
            assertEquals(listOf(videoMimeType, MediaFormat.MIMETYPE_AUDIO_AAC), mimeTypes)
            val video = extractor.getTrackFormat(0)
            assertEquals(160, video.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(90, video.getInteger(MediaFormat.KEY_HEIGHT))
            val durationMs = video.getLong(MediaFormat.KEY_DURATION) / 1_000
            assertTrue("video $durationMs ms", abs(durationMs - 1_000) <= 150)
            extractor.selectTrack(0)
            var frames = 0
            while (extractor.sampleTime >= 0) {
                frames += 1
                extractor.advance()
            }
            assertEquals(15, frames)
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

    private companion object {
        const val TAG = "YftMuxTest"
        const val AV1_MIME_TYPE = "video/av01"
    }
}
