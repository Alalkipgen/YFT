package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.LocalMuxResult
import com.alal.yft.core.download.MuxDetails
import com.alal.yft.core.download.MuxOutput
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P35 on the CI emulator: the stream copy against today's MediaMuxer way, on the one-second test
 * tracks, on P27's 20-minute input and on a 1-hour-sized input (the test tracks' fragments
 * repeated to about 260,000 samples, [LongFragmentedMp4]). The inputs and both merged files are
 * read side by side: the same tracks and the same samples byte for byte; the stream copy's
 * sample times are the inputs' within one tick, and today's within one tick after each track's
 * first sample (MediaMuxer takes a sample's presentation time as its decode time, so it moves
 * the start of a video with B-frames: 59 ms later for the test video on API 34). The stream
 * copy's file must play in Media3 ExoPlayer (prepare, duration, seek to the middle). Each case
 * prints both merge times in a `YFT-DIAG fast-merge` line; on the long input the stream copy
 * must be at least 3 times faster.
 */
@RunWith(AndroidJUnit4::class)
class StreamCopyMergeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val directory = File(context.cacheDir, "p35-merge").apply { mkdirs() }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun theTestTracksAreStreamCopiedLikeTodaysWayAndPlay() {
        val video = File(directory, "video.mp4").apply { writeBytes(assetBytes(VIDEO_ASSET)) }
        val audio = File(directory, "audio.m4a").apply { writeBytes(assetBytes(AUDIO_ASSET)) }

        val comparison = compare("asset", video, audio, minimumUs = 0L)

        assertEquals(listOf(15L, 45L), comparison.samples)
    }

    @Test
    fun aTwentyMinuteInputIsStreamCopiedLikeTodaysWayAndPlays() {
        val comparison = compareLong("twenty", TWENTY_MINUTES_US)

        assertTrue("${comparison.samples}", comparison.samples.sum() >= 70_000)
    }

    @Test
    fun aOneHourSizedInputIsStreamCopiedAtLeastThreeTimesFaster() {
        val comparison = compareLong("hour", HOUR_SIZED_US)

        assertTrue("${comparison.samples}", comparison.samples.sum() >= 250_000)
        assertTrue(
            "stream copy ${comparison.afterMs} ms, today's way ${comparison.beforeMs} ms",
            comparison.beforeMs >= MIN_SPEEDUP * comparison.afterMs,
        )
    }

    private fun compareLong(case: String, minimumUs: Long): Comparison {
        val video = File(directory, "long-video.mp4")
        val audio = File(directory, "long-audio.m4a")
        LongFragmentedMp4.write(assetBytes(VIDEO_ASSET), minimumUs, video)
        LongFragmentedMp4.write(assetBytes(AUDIO_ASSET), minimumUs, audio)
        return compare(case, video, audio, minimumUs)
    }

    /**
     * Merges [video] and [audio] both ways (the stream copy first, so it never reads warmer
     * files), prints both times, then checks that the files match and the stream copy plays.
     */
    private fun compare(case: String, video: File, audio: File, minimumUs: Long): Comparison {
        val after = merge(video, audio, fastMerge = true, name = "stream-copy.mp4")
        val before = merge(video, audio, fastMerge = false, name = "todays-way.mp4")
        val inputBytes = video.length() + audio.length()
        val phases = (before.details.phases + after.details.phases)
            .map { (name, millis) -> name to "${millis}ms" }
        diag(
            case,
            "minutes" to "${minimumUs / 60_000_000}",
            "samples" to "${after.details.videoSamples + after.details.audioSamples}",
            "size" to "${inputBytes / ONE_MIB}MB",
            "before" to "${before.wallMs}ms",
            "after" to "${after.wallMs}ms",
            "speedup" to String.format(Locale.US, "%.1f", before.wallMs / after.wallMs.toDouble()),
            "cpuBefore" to "${before.cpuMs}ms",
            "cpuAfter" to "${after.cpuMs}ms",
            "percents" to "${after.percents}",
            *phases.toTypedArray(),
        )
        assertEquals(after.details.summary(), STREAM_COPY, after.details.path)
        assertNull(after.details.summary(), after.details.streamCopyNote)
        assertEquals(before.details.summary(), MEDIA_MUXER, before.details.path)
        assertEquals(100, after.lastPercent)

        val same = sameSamples(listOf(video, audio), before.file, after.file)
        val samples = same.map { it.count }
        assertEquals(listOf(after.details.videoSamples, after.details.audioSamples), samples)
        assertEquals(listOf(before.details.videoSamples, before.details.audioSamples), samples)
        val played = play(after.file)
        val expectedMs = durationUs(before.file) / 1_000
        diag(
            case,
            "video" to "${samples[0]}",
            "audio" to "${samples[1]}",
            "duration" to "${played.durationMs}ms",
            "expected" to "${expectedMs}ms",
            "seek" to "${played.positionMs}ms",
            "inputStart" to "${same[0].inputStartUs}us",
            "fastStart" to "${same[0].fastStartUs}us",
            "todayStart" to "${same[0].todayStartUs}us",
        )
        assertTrue(
            "ExoPlayer ${played.durationMs} ms, MediaMuxer's file $expectedMs ms",
            abs(played.durationMs - expectedMs) <= DURATION_SLACK_MS,
        )
        assertTrue(
            "seek to ${played.middleMs} ms ended at ${played.positionMs} ms",
            abs(played.positionMs - played.middleMs) <= SEEK_SLACK_MS,
        )
        return Comparison(samples, before.wallMs, after.wallMs)
    }

    private fun merge(video: File, audio: File, fastMerge: Boolean, name: String): Merged {
        val output = File(directory, name)
        val percents = mutableSetOf<Int>()
        var lastPercent = -1
        val muxer = AndroidMp4AudioVideoMuxer(fastMerge = fastMerge)
        val cpu = SystemClock.currentThreadTimeMillis()
        val started = SystemClock.elapsedRealtime()
        val attempt = muxer.mux(video, audio, MuxOutput.ToFile(output), VIDEO_MP4) { done, total ->
            if (total > 0) {
                lastPercent = (done * 100 / total).toInt()
                percents += lastPercent
            }
        }
        val wallMs = SystemClock.elapsedRealtime() - started
        val cpuMs = SystemClock.currentThreadTimeMillis() - cpu
        assertTrue("${attempt.result}", attempt.result is LocalMuxResult.Completed)
        assertEquals(output.length(), (attempt.result as LocalMuxResult.Completed).bytesWritten)
        val details = requireNotNull(attempt.details) { "no details for $name" }
        return Merged(output, details, maxOf(wallMs, 1L), cpuMs, percents.size, lastPercent)
    }

    /**
     * Reads [inputs], [today]'s file and the stream copy's file ([fast]) side by side with
     * MediaExtractor: the same tracks, and for each track the same samples in all three.
     */
    private fun sameSamples(inputs: List<File>, today: File, fast: File): List<SameTrack> {
        val todays = MediaExtractor()
        val fasts = MediaExtractor()
        try {
            todays.setDataSource(today.path)
            fasts.setDataSource(fast.path)
            assertEquals("tracks", 2, todays.trackCount)
            assertEquals("tracks", 2, fasts.trackCount)
            return inputs.mapIndexed { track, input ->
                sameFormat(track, todays.getTrackFormat(track), fasts.getTrackFormat(track))
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(input.path)
                    assertEquals("input tracks", 1, extractor.trackCount)
                    sameTrack(track, extractor, todays, fasts)
                } finally {
                    extractor.release()
                }
            }
        } finally {
            todays.release()
            fasts.release()
        }
    }

    private fun sameFormat(track: Int, expected: MediaFormat, actual: MediaFormat) {
        val mime = expected.getString(MediaFormat.KEY_MIME)
        assertEquals("track $track", if (track == 0) AVC else AAC, mime)
        assertEquals("track $track", mime, actual.getString(MediaFormat.KEY_MIME))
        val keys = if (track == 0) {
            listOf(MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT)
        } else {
            listOf(MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT)
        }
        for (key in keys) {
            assertEquals("track $track $key", expected.getInteger(key), actual.getInteger(key))
        }
        for (key in listOf("csd-0", "csd-1")) {
            if (!expected.containsKey(key)) continue
            assertEquals(
                "track $track $key",
                expected.getByteBuffer(key),
                actual.getByteBuffer(key),
            )
        }
    }

    /**
     * Every sample of [track]: the same bytes in the input, today's file and the stream copy's,
     * and the same sync flag; the stream copy's time is the input's within one tick, and today's
     * within one tick after the track's first sample.
     */
    private fun sameTrack(
        track: Int,
        input: MediaExtractor,
        today: MediaExtractor,
        fast: MediaExtractor,
    ): SameTrack {
        val tickUs = 1_000_000 / (if (track == 0) VIDEO_TIMESCALE else AUDIO_TIMESCALE) + 1
        val inputBuffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
        val todayBuffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
        val fastBuffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
        input.selectTrack(0)
        today.selectTrack(track)
        fast.selectTrack(track)
        var count = 0L
        var starts: Triple<Long, Long, Long>? = null
        try {
            while (true) {
                val inputUs = input.sampleTime
                val todayUs = today.sampleTime
                val fastUs = fast.sampleTime
                val at = "track $track sample $count"
                assertEquals("$at: today's file ended", inputUs < 0, todayUs < 0)
                assertEquals("$at: the stream copy ended", inputUs < 0, fastUs < 0)
                if (inputUs < 0) break
                val (_, todayStart, fastStart) =
                    starts ?: Triple(inputUs, todayUs, fastUs).also { starts = it }
                assertTrue(
                    "$at: input $inputUs us, stream copy $fastUs us",
                    abs(fastUs - inputUs) <= tickUs,
                )
                val shift = (fastUs - fastStart) - (todayUs - todayStart)
                assertTrue(
                    "$at: today's $todayUs us from $todayStart, stream copy $fastUs us " +
                        "from $fastStart",
                    abs(shift) <= tickUs,
                )
                val sync = fast.sampleFlags and SYNC
                assertEquals("$at: sync in today's file", sync, today.sampleFlags and SYNC)
                // MediaExtractor marks only the first sample of an input fragment as sync: right
                // for the test video's fragments, not for sound, where every sample is one.
                if (track == 0) {
                    assertEquals("$at: sync in the input", sync, input.sampleFlags and SYNC)
                }
                val size = read(input, inputBuffer)
                assertEquals("$at: size", size, read(today, todayBuffer))
                assertEquals("$at: size", size, read(fast, fastBuffer))
                assertTrue("$at: bytes of today's file", inputBuffer == todayBuffer)
                assertTrue("$at: bytes of the stream copy", inputBuffer == fastBuffer)
                count += 1
                input.advance()
                today.advance()
                fast.advance()
            }
        } finally {
            input.unselectTrack(0)
            today.unselectTrack(track)
            fast.unselectTrack(track)
        }
        val (inputStart, todayStart, fastStart) = checkNotNull(starts) { "track $track is empty" }
        return SameTrack(count, inputStart, todayStart, fastStart)
    }

    private fun read(extractor: MediaExtractor, buffer: ByteBuffer): Int {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        buffer.position(0)
        buffer.limit(maxOf(size, 0))
        return size
    }

    private fun durationUs(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).maxOf { index ->
                extractor.getTrackFormat(index).getLong(MediaFormat.KEY_DURATION)
            }
        } finally {
            extractor.release()
        }
    }

    /** Prepares [file] in ExoPlayer, reads its duration and seeks to the middle. */
    private fun play(file: File): Played {
        val player = onMain {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
        }
        try {
            awaitReady(player, "prepare")
            val durationMs = onMain { player.duration }
            val middleMs = durationMs / 2
            onMain { player.seekTo(middleMs) }
            awaitReady(player, "seek")
            return Played(durationMs, middleMs, onMain { player.currentPosition })
        } finally {
            onMain { player.release() }
        }
    }

    private fun awaitReady(player: ExoPlayer, step: String) {
        val deadline = SystemClock.elapsedRealtime() + PLAYER_TIMEOUT_MS
        while (true) {
            val (state, error) = onMain { player.playbackState to player.playerError }
            if (error != null) {
                throw AssertionError("ExoPlayer $step: ${error.errorCodeName}", error)
            }
            if (state == Player.STATE_READY) return
            assertTrue(
                "ExoPlayer $step: still in state $state",
                SystemClock.elapsedRealtime() < deadline,
            )
            SystemClock.sleep(POLL_MS)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun diag(case: String, vararg values: Pair<String, String>) {
        val fields = values.joinToString(" ") { (key, value) -> "$key=$value" }
        Log.i(TAG, "YFT-DIAG fast-merge $case sdk=${Build.VERSION.SDK_INT} $fields")
    }

    private fun assetBytes(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private class Merged(
        val file: File,
        val details: MuxDetails,
        val wallMs: Long,
        val cpuMs: Long,
        val percents: Int,
        val lastPercent: Int,
    )

    private class Played(val durationMs: Long, val middleMs: Long, val positionMs: Long)

    private class Comparison(val samples: List<Long>, val beforeMs: Long, val afterMs: Long)

    private class SameTrack(
        val count: Long,
        val inputStartUs: Long,
        val todayStartUs: Long,
        val fastStartUs: Long,
    )

    private companion object {
        const val TAG = "YftFastMerge"
        const val VIDEO_ASSET = "mux/video-avc.mp4"
        const val AUDIO_ASSET = "mux/audio-aac.m4a"
        const val VIDEO_MP4 = "video/mp4"
        const val SYNC = MediaExtractor.SAMPLE_FLAG_SYNC
        const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        const val AAC = MediaFormat.MIMETYPE_AUDIO_AAC
        const val STREAM_COPY = MuxDetails.STREAM_COPY
        const val MEDIA_MUXER = MuxDetails.MEDIA_MUXER
        const val ONE_MIB = 1_024 * 1_024
        const val SAMPLE_BUFFER_BYTES = 1_024 * 1_024
        const val TWENTY_MINUTES_US = 20L * 60 * 1_000_000

        /** About 260,000 samples: 15 video and about 43 sound samples per second. */
        const val HOUR_SIZED_US = 75L * 60 * 1_000_000

        /** The test tracks' timescales: one tick is the allowed difference of a sample time. */
        const val VIDEO_TIMESCALE = 15_360
        const val AUDIO_TIMESCALE = 44_100
        const val DURATION_SLACK_MS = 1_000L
        const val SEEK_SLACK_MS = 1_000L
        const val MIN_SPEEDUP = 3
        const val PLAYER_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
    }
}
