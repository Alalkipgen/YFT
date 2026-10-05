package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P4 on a real device or emulator: a one-second video-only MP4 in the DASH on-demand layout
 * (fragments behind a `sidx`, like Facebook's and YouTube's tracks) and a one-second AAC track,
 * both made with ffmpeg, become one MP4 that Android's own extractor reads back with both
 * tracks and every frame. AV1 is probed on Android 14+ and stays off while it fails. P6 adds
 * VP9 and Opus WebM tracks merged into one WebM.
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

    /**
     * P6: YouTube's 2K and 4K are VP9 WebM with Opus WebM sound, merged into one WebM from
     * Android 10 ([AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK]). Both one-second tracks are made
     * with ffmpeg (libvpx-vp9 160 × 90 at 15 fps, libopus 48 kHz).
     */
    @Test
    fun aVp9VideoAndItsOpusSoundBecomeOneWebm() {
        assumeTrue(Build.VERSION.SDK_INT >= AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK)
        val output = File(directory, "vp9.webm")

        val result = AndroidMp4AudioVideoMuxer().mux(
            asset("mux/video-vp9.webm"),
            asset("mux/audio-opus.webm"),
            output,
            AudioVideoMuxCompatibility.WEBM_OUTPUT_MIME,
        )

        assertEquals(output.length(), (result as LocalMuxResult.Completed).bytesWritten)
        assertMerged(output, MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_AUDIO_OPUS)
    }

    /**
     * AV1 merges are off ([AudioVideoMuxCompatibility.AV1_MP4_ENABLED]) because this merge failed
     * on the API 34 emulator (P4). The probe still tries it on Android 14+ and logs a `YFT-DIAG`
     * line with the step that failed, so CI shows when a new image starts to write AV1.
     */
    @Test
    fun av1MergesStayOffWhileThisPhoneCannotMakeThem() {
        assumeTrue(Build.VERSION.SDK_INT >= AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK)
        val video = asset("mux/video-av1.mp4")
        val output = File(directory, "av1.mp4")

        val result = AndroidMp4AudioVideoMuxer().mux(video, asset("mux/audio-aac.m4a"), output)
        val step = if (result is LocalMuxResult.Completed) "none" else failedStep(video)
        Log.i(
            TAG,
            "YFT-DIAG av1-mux probe sdk=${Build.VERSION.SDK_INT} " +
                "decoder=${platformHasDecoder(AV1_MIME_TYPE)} " +
                "result=${result.javaClass.simpleName} step=$step",
        )

        if (result is LocalMuxResult.Completed) {
            assertMerged(output, AV1_MIME_TYPE)
        } else {
            assertFalse(
                "AV1 merges must stay off while the muxer fails",
                AudioVideoMuxCompatibility.AV1_MP4_ENABLED,
            )
        }
    }

    /** The first MediaExtractor/MediaMuxer step that fails for the AV1 track, as one word. */
    private fun failedStep(video: File): String {
        var step = "extract"
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(video.path)
            val format = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .first { it.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
            step = "create"
            muxer = MediaMuxer(
                File(directory, "probe.mp4").path,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            step = "addTrack"
            muxer.addTrack(format)
            step = "start"
            muxer.start()
            "samples"
        } catch (error: Exception) {
            "$step-${error.javaClass.simpleName}"
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun assertMerged(
        file: File,
        videoMimeType: String,
        audioMimeType: String = MediaFormat.MIMETYPE_AUDIO_AAC,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val mimeTypes = (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
            assertEquals(listOf(videoMimeType, audioMimeType), mimeTypes)
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
