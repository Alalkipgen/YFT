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
import com.alal.yft.detection.Av1Decoders
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
     * Phase 1.4 item 1: a 2K/4K VP9 keyframe is larger than the 256 KiB buffer a WebM track gets
     * (it states no maximum input size); on the owner's phone (Android 16, 2026-10-16) the 1440p
     * merge failed with a bare `IllegalArgumentException`. The video is 800 × 450 noise, 25 frames
     * at 25 fps (libvpx-vp9, crf 10), its keyframe 355,713 bytes; it is kept as Base64 text
     * (`video-vp9-big-keyframe.webm.b64`, 357,213 bytes decoded) and merged with the Opus track.
     */
    @Test
    fun aVp9KeyframeOver256KibMergesIntoOneWebm() {
        assumeTrue(Build.VERSION.SDK_INT >= AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK)
        val video = base64Asset("mux/video-vp9-big-keyframe.webm.b64", "video-vp9-big-keyframe.webm")
        assertEquals(BIG_KEYFRAME_FILE_BYTES, video.length())
        val output = File(directory, "vp9-big.webm")

        val result = AndroidMp4AudioVideoMuxer().mux(
            video,
            asset("mux/audio-opus.webm"),
            output,
            AudioVideoMuxCompatibility.WEBM_OUTPUT_MIME,
        )

        assertTrue("$result", result is LocalMuxResult.Completed)
        assertEquals(output.length(), (result as LocalMuxResult.Completed).bytesWritten)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(output.path)
            val mimeTypes = (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
            assertEquals(listOf(MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_AUDIO_OPUS), mimeTypes)
            val format = extractor.getTrackFormat(0)
            assertEquals(800, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(450, format.getInteger(MediaFormat.KEY_HEIGHT))
            extractor.selectTrack(0)
            val buffer = java.nio.ByteBuffer.allocateDirect(BIG_KEYFRAME_FILE_BYTES.toInt())
            var frames = 0
            var largest = 0
            while (extractor.sampleTime >= 0) {
                buffer.clear()
                largest = maxOf(largest, extractor.readSampleData(buffer, 0))
                frames += 1
                extractor.advance()
            }
            assertEquals(25, frames)
            assertEquals(BIG_KEYFRAME_BYTES, largest)
        } finally {
            extractor.release()
        }
    }

    /**
     * AV1 merges are off ([AudioVideoMuxCompatibility.AV1_MP4_ENABLED]) because this merge failed
     * on the API 34 emulator (P4). The probe still tries it on Android 14+ and logs a `YFT-DIAG`
     * line with the step that failed, so CI shows when a new image starts to write AV1. v1.0.2
     * item 26: the line also names the kinds of the phone's AV1 decoders ([Av1Decoders.kinds]:
     * hardware, software, both or none), as the debug switch's Details line does. v1.0.2 fix R8:
     * an AV1 merge is a stream copy first (`EditListedSoundMergeInstrumentedTest`), so the probe
     * merges today's MediaMuxer way alone (`fastMerge = false`), the stream copy's fallback.
     */
    @Test
    fun av1MergesStayOffWhileThisPhoneCannotMakeThem() {
        assumeTrue(Build.VERSION.SDK_INT >= AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK)
        val video = asset("mux/video-av1.mp4")
        val output = File(directory, "av1.mp4")

        val result = AndroidMp4AudioVideoMuxer(fastMerge = false)
            .mux(video, asset("mux/audio-aac.m4a"), output)
        val step = if (result is LocalMuxResult.Completed) "none" else failedStep(video)
        Log.i(
            TAG,
            "YFT-DIAG av1-mux probe sdk=${Build.VERSION.SDK_INT} " +
                "decoder=${platformHasDecoder(AV1_MIME_TYPE)} " +
                "kinds=${Av1Decoders.kinds(Av1Decoders.installed())} " +
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

    /** A binary fixture kept as Base64 text (MIME lines), decoded into the test's directory. */
    private fun base64Asset(name: String, fileName: String): File {
        val text = instrumentation.context.assets.open(name).bufferedReader().use { it.readText() }
        val file = File(directory, fileName)
        file.writeBytes(android.util.Base64.decode(text, android.util.Base64.DEFAULT))
        return file
    }

    private companion object {
        const val TAG = "YftMuxTest"
        const val AV1_MIME_TYPE = "video/av01"
        const val BIG_KEYFRAME_FILE_BYTES = 357_213L
        const val BIG_KEYFRAME_BYTES = 355_713
    }
}
