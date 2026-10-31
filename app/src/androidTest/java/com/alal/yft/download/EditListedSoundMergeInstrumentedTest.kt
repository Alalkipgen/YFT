package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.download.LocalMuxResult
import com.alal.yft.core.download.MuxAttempt
import com.alal.yft.core.download.MuxDetails
import com.alal.yft.core.download.MuxOutput
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 fix R8 on the CI emulator: a sound whose edit list starts its media at 2,048 ticks, as
 * tears-of-steel-av1's DASH sound (`audio_eng=128002`), keeps every sample in a merge. Android 11
 * and newer give its first two samples times before 0, which today's MediaMuxer way took for
 * the end of the sound (the merge kept the picture alone). The AV1 test video is stream-copied
 * with it (the stream copy took no AV1 before R8, and this emulator's MediaMuxer writes no AV1,
 * P4); the AVC test video goes today's way with it. Each merged sound is read back with
 * MediaExtractor: every sample of its input, byte for byte and at the input's time.
 */
@RunWith(AndroidJUnit4::class)
class EditListedSoundMergeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val directory = File(context.cacheDir, "r8-merge").apply { mkdirs() }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun anAv1VideoIsStreamCopiedWithEverySampleOfASoundThatStartsBefore0() {
        assumeTrue(Build.VERSION.SDK_INT >= AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK)
        val video = File(directory, "video-av1.mp4").apply { writeBytes(assetBytes(AV1_ASSET)) }
        val audio = editListedSound()
        val output = File(directory, "av1.mp4")

        val attempt = AndroidMp4AudioVideoMuxer()
            .mux(video, audio, MuxOutput.ToFile(output), MP4) { _, _ -> }

        val details = completed(attempt, output)
        assertEquals(details.summary(), MuxDetails.STREAM_COPY, details.path)
        assertNull(details.summary(), details.streamCopyNote)
        assertEquals(listOf(AV1, AAC), mimeTypes(output))
        assertSameSamples("video", samples(video, 0), samples(output, 0), tickUs(AV1_TIMESCALE))
        val sound = assertSound(audio, output, "av1")
        assertEquals(15L to sound.toLong(), details.videoSamples to details.audioSamples)
        // The merged file's sound plays in Media3 (the app's player), the picture left out.
        val played = playSound(output)
        assertTrue("sound ${played.durationMs} ms", abs(played.durationMs - 1_000) <= 250)
        assertTrue("no sound track selected", played.soundSelected)
    }

    @Test
    fun todaysWayKeepsEverySampleOfASoundThatStartsBefore0() {
        val video = File(directory, "video-avc.mp4").apply { writeBytes(assetBytes(AVC_ASSET)) }
        val audio = editListedSound()
        val output = File(directory, "avc.mp4")

        val attempt = AndroidMp4AudioVideoMuxer(fastMerge = false)
            .mux(video, audio, MuxOutput.ToFile(output), MP4) { _, _ -> }

        val details = completed(attempt, output)
        assertEquals(details.summary(), MuxDetails.MEDIA_MUXER, details.path)
        assertEquals(listOf(AVC, AAC), mimeTypes(output))
        val sound = assertSound(audio, output, "avc")
        assertEquals(15L to sound.toLong(), details.videoSamples to details.audioSamples)
    }

    private fun completed(attempt: MuxAttempt, output: File): MuxDetails {
        assertTrue("${attempt.result}", attempt.result is LocalMuxResult.Completed)
        assertEquals(output.length(), (attempt.result as LocalMuxResult.Completed).bytesWritten)
        return requireNotNull(attempt.details) { "no details" }
    }

    /**
     * The merged sound in [output] against its input [audio]: every sample at the input's time
     * (within two ticks: MediaMuxer keeps times in microseconds), the first ones before 0 from
     * Android 11. Returns the count.
     */
    private fun assertSound(audio: File, output: File, case: String): Int {
        val input = samples(audio, 0)
        val merged = samples(output, 1)
        Log.i(
            TAG,
            "YFT-DIAG r8-merge $case sdk=${Build.VERSION.SDK_INT} samples=${input.size}/" +
                "${merged.size} inputStart=${input.first().timeUs}us " +
                "mergedStart=${merged.firstOrNull()?.timeUs}us",
        )
        assertEquals("sound samples", SOUND_SAMPLES, input.size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertTrue("input starts at ${input.first().timeUs} us", input.first().timeUs < 0)
        }
        assertSameSamples("sound", input, merged, 2 * tickUs(AAC_TIMESCALE))
        return merged.size
    }

    private fun assertSameSamples(
        name: String,
        input: List<Sampled>,
        merged: List<Sampled>,
        slackUs: Long,
    ) {
        assertEquals("$name samples", input.size, merged.size)
        input.indices.forEach { index ->
            val at = "$name sample $index"
            val (inputUs, mergedUs) = input[index].timeUs to merged[index].timeUs
            assertTrue(
                "$at: input $inputUs us, merged $mergedUs us",
                abs(mergedUs - inputUs) <= slackUs,
            )
            assertArrayEquals(at, input[index].bytes, merged[index].bytes)
        }
    }

    /** Every sample of [file]'s track [track], with MediaExtractor: its time and its bytes. */
    private fun samples(file: File, track: Int): List<Sampled> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            extractor.selectTrack(track)
            val buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
            val samples = mutableListOf<Sampled>()
            // R8: a time before 0 is a sample too; the end is when no track is left.
            while (extractor.sampleTrackIndex == track) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                assertTrue("sample size $size", size > 0)
                buffer.position(0)
                buffer.limit(size)
                val bytes = ByteArray(size).also(buffer::get)
                samples += Sampled(extractor.sampleTime, bytes)
                extractor.advance()
            }
            return samples
        } finally {
            extractor.release()
        }
    }

    private fun mimeTypes(file: File): List<String?> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
        } finally {
            extractor.release()
        }
    }

    /**
     * The test sound (`mux/audio-aac.m4a`: 44.1 kHz, 45 samples in 4 fragments) with an edit list
     * that starts its media at 2,048 ticks: the `edts` goes after the `tkhd`, so only `trak` and
     * `moov` grow (the fragments' data offsets count from their `moof`, and the `sidx` points past
     * itself); the `mfra` at the end, whose offsets would move, is left out.
     */
    private fun editListedSound(): File {
        val bytes = assetBytes(AAC_ASSET)
        val out = ByteArrayOutputStream()
        var at = 0
        while (at < bytes.size) {
            val size = intAt(bytes, at)
            when (typeAt(bytes, at)) {
                "moov" -> out.write(withEditList(bytes.copyOfRange(at, at + size)))
                "mfra" -> Unit
                else -> out.write(bytes, at, size)
            }
            at += size
        }
        return File(directory, "audio-edit-list.m4a").apply { writeBytes(out.toByteArray()) }
    }

    private fun withEditList(moov: ByteArray): ByteArray {
        val trak = childAt(moov, BOX_HEADER, "trak")
        val tkhd = childAt(moov, trak + BOX_HEADER, "tkhd")
        val insertAt = tkhd + intAt(moov, tkhd)
        val edts = editList()
        val grown = moov.copyOfRange(0, insertAt) + edts + moov.copyOfRange(insertAt, moov.size)
        val view = ByteBuffer.wrap(grown)
        view.putInt(0, moov.size + edts.size)
        view.putInt(trak, intAt(moov, trak) + edts.size)
        return grown
    }

    /** `edts` with one `elst` entry (version 0): the media from [EDIT_MEDIA_TIME] on, rate 1. */
    private fun editList(): ByteArray {
        val elst = ByteBuffer.allocate(ELST_BYTES)
            .putInt(ELST_BYTES)
            .put("elst".toByteArray(Charsets.ISO_8859_1))
            .putInt(0)
            .putInt(1)
            .putInt(0)
            .putInt(EDIT_MEDIA_TIME)
            .putShort(1)
            .putShort(0)
        return ByteBuffer.allocate(BOX_HEADER + ELST_BYTES)
            .putInt(BOX_HEADER + ELST_BYTES)
            .put("edts".toByteArray(Charsets.ISO_8859_1))
            .put(elst.array())
            .array()
    }

    private fun childAt(box: ByteArray, from: Int, type: String): Int {
        var at = from
        while (at + BOX_HEADER <= box.size) {
            if (typeAt(box, at) == type) return at
            at += intAt(box, at)
        }
        throw AssertionError("no $type box")
    }

    private fun intAt(bytes: ByteArray, at: Int): Int = ByteBuffer.wrap(bytes, at, 4).int

    private fun typeAt(bytes: ByteArray, at: Int): String =
        String(bytes, at + 4, 4, Charsets.ISO_8859_1)

    /** Prepares [file]'s sound alone in ExoPlayer: its duration and whether a sound is chosen. */
    private fun playSound(file: File): Played {
        val player = onMain {
            ExoPlayer.Builder(context).build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + PLAYER_TIMEOUT_MS
            while (true) {
                val (state, error) = onMain { player.playbackState to player.playerError }
                if (error != null) throw AssertionError("ExoPlayer: ${error.errorCodeName}", error)
                if (state == Player.STATE_READY) break
                assertTrue(
                    "ExoPlayer: still in state $state",
                    SystemClock.elapsedRealtime() < deadline,
                )
                SystemClock.sleep(POLL_MS)
            }
            return onMain {
                Played(player.duration, player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
            }
        } finally {
            onMain { player.release() }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun assetBytes(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private fun tickUs(timescale: Int): Long = 1_000_000L / timescale + 1

    private class Sampled(val timeUs: Long, val bytes: ByteArray)

    private class Played(val durationMs: Long, val soundSelected: Boolean)

    private companion object {
        const val TAG = "YftEditListMerge"
        const val AV1_ASSET = "mux/video-av1.mp4"
        const val AVC_ASSET = "mux/video-avc.mp4"
        const val AAC_ASSET = "mux/audio-aac.m4a"
        const val MP4 = AudioVideoMuxCompatibility.MP4_OUTPUT_MIME
        const val AV1 = "video/av01"
        const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        const val AAC = MediaFormat.MIMETYPE_AUDIO_AAC

        /** As tears-of-steel-av1's sound: its media starts 2,048 ticks in (46 ms at 44.1 kHz). */
        const val EDIT_MEDIA_TIME = 2_048
        const val SOUND_SAMPLES = 45
        const val AV1_TIMESCALE = 15_360
        const val AAC_TIMESCALE = 44_100
        const val BOX_HEADER = 8
        const val ELST_BYTES = 28
        const val SAMPLE_BUFFER_BYTES = 1_024 * 1_024
        const val PLAYER_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
    }
}
