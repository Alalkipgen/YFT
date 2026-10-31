package com.alal.yft.core.download

import com.alal.yft.core.download.Mp4TestFiles.Sample
import com.alal.yft.core.download.Mp4TestFiles.Stated
import com.alal.yft.core.download.Mp4TestFiles.Track
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v1.0.2 fix R6: ElephantsDream's DASH video (10 MHz ticks, 15,691 samples to 10:54.445, its
 * headers stating 10:52.652) merged in full only up to 7:11. Android 16 states a fragmented
 * input's duration from its `tkhd`, so the stream copy's check took the merged file's longer,
 * right length for a fault; today's MediaMuxer way then took the times from Android's fragment
 * reader, which counts 32 bits of ticks and wraps after 7:09.5, and its short file counted.
 * Here the same shape at 7:30: the stream copy keeps every sample, and a short file from
 * today's way fails as incompatible tracks with what it held.
 */
class MergedLengthCheckTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `a DASH video whose headers state less than its samples is stream-copied whole`() {
        val (videoFile, audioFile) = inputs()
        val phone = PhoneMediaMuxer()
        val output = File(directory.root, "merged.mp4")

        val attempt = StreamCopyAudioVideoMuxer(
            fallback = LengthCheckedAudioVideoMuxer(phone),
            check = android16Durations(output),
        ).mux(videoFile, audioFile, MuxOutput.ToFile(output), "video/mp4") { _, _ -> }

        assertEquals("today's way", 0, phone.calls)
        assertEquals(LocalMuxResult.Completed(output.length()), attempt.result)
        assertEquals(MuxDetails.STREAM_COPY, attempt.details?.path)
        assertNull(attempt.details?.streamCopyNote)
        val dump = Mp4Dump(output.readBytes())
        assertEquals(listOf(VIDEO_SAMPLES, AUDIO_SAMPLES), dump.tracks.map { it.samples.size })
        // Every sample's length, past the 7:09.5 where 32 bits of 10 MHz ticks wrap.
        assertEquals(VIDEO_SAMPLES * FRAME_TICKS, dump.tracks[0].mediaDuration)
        assertEquals(AUDIO_SAMPLES * 1_024L, dump.tracks[1].mediaDuration)
        // The film: as long as its longer track, the sound's 7:30.005.
        assertEquals(450_005L, dump.movieDuration)
        // The input's own header states 7:28.2, as the server wrote it.
        assertEquals(STATED_MS, Mp4Dump(videoFile.readBytes()).tracks.single().trackDuration)
    }

    @Test
    fun `a merge that today's way cut at the wrap fails as incompatible tracks`() {
        for (squeeze in listOf(false, true)) {
            for (descriptor in listOf(false, true)) {
                val (videoFile, audioFile) = inputs()
                val phone = PhoneMediaMuxer(squeeze = squeeze)
                val output = File(directory.root, "merged-$squeeze-$descriptor.mp4")
                val file = if (descriptor) RandomAccessFile(output, "rw") else null
                val target = file?.let { MuxOutput.ToDescriptor(it.fd) } ?: MuxOutput.ToFile(output)

                val attempt = file.use {
                    StreamCopyAudioVideoMuxer(
                        fallback = LengthCheckedAudioVideoMuxer(phone),
                        // What Android 16 said of the merged file before R6.
                        check = { _, _, _, _ -> "video duration" },
                    ).mux(videoFile, audioFile, target, "video/mp4") { _, _ -> }
                }

                val case = "squeeze $squeeze, descriptor $descriptor"
                assertEquals(case, 1, phone.calls)
                val failure = attempt.result as LocalMuxResult.Failure
                assertEquals(case, DownloadFailureReason.INCOMPATIBLE_TRACKS, failure.reason)
                val kept = if (squeeze) VIDEO_SAMPLES else WRAP_SAMPLE
                assertEquals(
                    case,
                    "merged video ends at 7:09 of 7:30, $kept of $VIDEO_SAMPLES samples; " +
                        "stream copy check failed, video duration",
                    failure.detail,
                )
                assertTrue(case, attempt.samplesWritten > 0)
                // Emptied: a new file is deleted, a destination's file cut to nothing.
                if (descriptor) {
                    assertEquals(case, 0L, output.length())
                } else {
                    assertFalse(case, output.exists())
                }
            }
        }
    }

    @Test
    fun `a whole merge by today's way counts as before`() {
        val (videoFile, audioFile) = inputs()
        val phone = PhoneMediaMuxer(whole = true)
        val output = File(directory.root, "merged.mp4")

        val attempt = StreamCopyAudioVideoMuxer(
            fallback = LengthCheckedAudioVideoMuxer(phone),
            check = { _, _, _, _ -> "video duration" },
        ).mux(videoFile, audioFile, MuxOutput.ToFile(output), "video/mp4") { _, _ -> }

        assertEquals(1, phone.calls)
        assertEquals(LocalMuxResult.Completed(output.length()), attempt.result)
        assertEquals(MuxDetails.MEDIA_MUXER, attempt.details?.path)
        assertEquals("check failed, video duration", attempt.details?.streamCopyNote)
        assertEquals(VIDEO_SAMPLES, Mp4Dump(output.readBytes()).tracks[0].samples.size)
    }

    @Test
    fun `a merged duration counts against its samples, and against its input only when shorter`() {
        // ElephantsDream on Android 16: the merged file states its samples' 10:54.445, the
        // input's tkhd 10:52.652; a frame is 41.7 ms (+ 1 ms).
        assertFalse(streamCopyDurationMismatch(654_445_000, 654_445_458, 652_652_000, 42_708))
        // Before R6 the distance counted either way: 1.793 s, more than a frame.
        assertTrue(abs(654_445_000L - 652_652_000L) > 42_708)
        // Still a fault: a merged track shorter than its input states, or off its samples.
        assertTrue(streamCopyDurationMismatch(652_652_000, 652_652_000, 654_445_000, 42_708))
        assertTrue(streamCopyDurationMismatch(654_445_000, 650_000_000, null, 42_708))
        assertFalse(streamCopyDurationMismatch(654_445_000, 654_440_000, 654_445_000, 42_708))
        assertFalse(streamCopyDurationMismatch(null, 654_445_458, 652_652_000, 42_708))
    }

    @Test
    fun `leading non-sync video frames, which Android's writer drops, are no shortfall`() {
        val input = MergeTrackLength(
            samples = 100,
            durationUs = 4_000_000,
            longestSampleUs = 40_000,
            leadingNonSync = 2,
            leadingNonSyncUs = 80_000,
        )
        val whole = MergeTrackLength(98, 3_920_000, 40_000)
        assertNull(MergedLength.shortfall("video", input, whole))
        assertEquals(
            "merged video ends at 0:03 of 0:04, 97 of 100 samples",
            MergedLength.shortfall("video", input, MergeTrackLength(97, 3_880_000, 40_000)),
        )
        // A second short is no fault (the last sample's length, a start); more is.
        assertNull(MergedLength.shortfall("video", input, MergeTrackLength(98, 2_920_001, 40_000)))
        val short = MergeTrackLength(98, 2_900_000, 40_000)
        assertNotNull(MergedLength.shortfall("video", input, short))
        assertEquals("merged file has no audio", MergedLength.shortfall("audio", input, null))
        assertEquals(
            "merged file has no audio",
            MergedLength.shortfall("audio", input, MergeTrackLength(0, 0, 0)),
        )
    }

    @Test
    fun `times read as minutes, and hours without a second colon`() {
        assertEquals("7:09", MergedLength.clock(429_500_343))
        assertEquals("10:54", MergedLength.clock(654_445_458))
        assertEquals("1h02m03s", MergedLength.clock(3_723_000_000))
        val detail = "merged video ends at 1h02m03s of 1h05m00s, 93075 of 97500 samples"
        assertEquals(detail, DownloadFailureDetails.sanitize(detail))
    }

    @Test
    fun `an input's length leaves out tfdt jumps and reads any codec or ADTS`() {
        val jumped = Mp4TestFiles.video().let { track ->
            Track(
                video = true,
                timescale = track.timescale,
                fragments = track.fragments,
                decodeTimes = listOf(0L, 20_480L, 35_840L),
            )
        }
        val bytes = Mp4TestFiles.fragmented(jumped)
        val read = Mp4TrackReader(BytesSource(bytes), StreamCopyTrackKind.VIDEO).read()
        assertEquals(5_120L, read.gapTicks)
        val length = MergedLength.ofInput(BytesSource(bytes), StreamCopyTrackKind.VIDEO)!!
        assertEquals(90 to 3_000_000L, length.samples to length.durationUs)

        val av1 = Track(
            video = true,
            timescale = 15_360,
            fragments = Mp4TestFiles.video().fragments,
            sampleEntry = "av01",
        )
        val av1Bytes = Mp4TestFiles.fragmented(av1)
        assertThrows(StreamCopyUnsupported::class.java) {
            Mp4TrackReader(BytesSource(av1Bytes), StreamCopyTrackKind.VIDEO).read()
        }
        assertEquals(
            90,
            MergedLength.ofInput(BytesSource(av1Bytes), StreamCopyTrackKind.VIDEO)?.samples,
        )
        val adts = Mp4TestFiles.adts(Mp4TestFiles.audio())
        val sound = MergedLength.ofInput(BytesSource(adts), StreamCopyTrackKind.AUDIO)
        assertEquals(129, sound?.samples)
        assertNull(MergedLength.ofInput(BytesSource(ByteArray(64)), StreamCopyTrackKind.AUDIO))
    }

    private fun inputs(): Pair<File, File> {
        val video = File(directory.root, "video.mp4")
        val audio = File(directory.root, "audio.m4a")
        if (!video.exists()) video.writeBytes(Mp4TestFiles.fragmented(longVideo()))
        if (!audio.exists()) audio.writeBytes(Mp4TestFiles.fragmented(longAudio()))
        return video to audio
    }

    /** 7:30 of 24 fps video in 10 MHz ticks, a sync sample every 6 s; headers state 7:28.2. */
    private fun longVideo(): Track {
        val random = Random(6)
        val samples = List(VIDEO_SAMPLES) { index ->
            Sample(random.nextBytes(1), FRAME_TICKS.toInt(), sync = index % 144 == 0)
        }
        return Track(
            video = true,
            timescale = 10_000_000,
            fragments = samples.chunked(144),
            stated = Stated(1_000, STATED_MS, STATED_MS),
        )
    }

    /** The sound: AAC frames in 24 kHz ticks, as ElephantsDream's. */
    private fun longAudio(): Track {
        val random = Random(7)
        val samples = List(AUDIO_SAMPLES) { Sample(random.nextBytes(1), 1_024) }
        return Track(
            video = false,
            timescale = 24_000,
            fragments = samples.chunked(141),
            stated = Stated(1_000, STATED_MS, STATED_MS),
        )
    }

    /**
     * The stream copy's check as Android 16 states durations (`tkhd` ÷ `mvhd` timescale) with
     * the production rule ([streamCopyDurationMismatch]) on [output].
     */
    private fun android16Durations(output: File) = StreamCopyCheck { _, layout, video, audio ->
        val merged = statedUs(output)
        listOf(layout.video to video, layout.audio to audio).withIndex()
            .firstNotNullOfOrNull { (index, entry) ->
                val (track, input) = entry
                val samples = track.track
                val longest = (0 until samples.count).maxOf { samples.durations[it] }.toLong()
                val frameUs = ticksToMicros(longest, samples.timescale) + 1_000 +
                    ticksToMicros(abs(track.editOffset), samples.timescale)
                val plannedUs = ticksToMicros(samples.mediaDuration, samples.timescale)
                val mismatch = streamCopyDurationMismatch(
                    mergedUs = merged[index],
                    plannedUs = plannedUs,
                    inputUs = statedUs(input).single(),
                    frameUs = frameUs,
                )
                if (mismatch) "${if (index == 0) "video" else "audio"} duration" else null
            }
    }

    private fun statedUs(file: File): List<Long> {
        val dump = Mp4Dump(file.readBytes())
        return dump.tracks.map { it.trackDuration * 1_000_000 / dump.movieTimescale }
    }

    /**
     * Today's MediaMuxer way as the phone ran it, faked: Android's fragment reader gives the
     * video's times in 32 bits of ticks, which wrap after 2^32 (7:09.5 at 10 MHz). Both tracks
     * end at the wrap; or, with [squeeze], every sample stays with decode times 0.1 ms apart
     * from there on (AOSP's MPEG4Writer); [whole] writes everything.
     */
    private class PhoneMediaMuxer(
        private val squeeze: Boolean = false,
        private val whole: Boolean = false,
    ) : ProgressAudioVideoMuxer {
        var calls = 0

        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult = error("not used")

        override fun mux(
            videoFile: File,
            audioFile: File,
            output: MuxOutput,
            outputMimeType: String,
            onProgress: MuxProgressListener,
        ): MuxAttempt {
            calls += 1
            return RandomAccessFile(videoFile, "r").use { videoInput ->
                RandomAccessFile(audioFile, "r").use { audioInput ->
                    val videoSource = FileChannelMp4Source(videoInput.channel)
                    val audioSource = FileChannelMp4Source(audioInput.channel)
                    val video = Mp4TrackReader(videoSource, StreamCopyTrackKind.VIDEO).read()
                    val audio = Mp4TrackReader(audioSource, StreamCopyTrackKind.AUDIO).read()
                    var wrap = 0
                    var ticks = 0L
                    while (wrap < video.count && ticks < WRAP_TICKS) {
                        ticks += video.durations[wrap++]
                    }
                    val (videoOut, audioOut) = when {
                        whole -> video to audio
                        squeeze -> video.squeezedFrom(wrap - 1) to audio
                        else -> {
                            val endUs = ticksToMicros(ticks, video.timescale)
                            var sounds = 0
                            var soundTicks = 0L
                            while (
                                sounds < audio.count &&
                                ticksToMicros(soundTicks, audio.timescale) < endUs
                            ) {
                                soundTicks += audio.durations[sounds++]
                            }
                            video.cut(wrap) to audio.cut(sounds)
                        }
                    }
                    val layout = StreamCopyLayout.of(videoOut, audioOut)
                    val writer = StreamCopyWriter(ByteBuffer.allocateDirect(64 * 1_024))
                    val size = ChannelStreamCopyOutputs.open(output).use { opened ->
                        writer.writeData(layout, videoSource, audioSource, opened) { _, _ -> }
                        writer.writeMoov(layout, opened).also(opened::truncate)
                    }
                    MuxAttempt(
                        result = LocalMuxResult.Completed(size),
                        samplesWritten = videoOut.count.toLong() + audioOut.count,
                        details = MuxDetails(
                            MuxDetails.MEDIA_MUXER,
                            videoOut.count.toLong(),
                            audioOut.count.toLong(),
                        ),
                    )
                }
            }
        }

        private fun Mp4Track.cut(count: Int): Mp4Track =
            copy(count, durations.copyOf(count))

        /** Every sample from [index] on lasts 0.1 ms. */
        private fun Mp4Track.squeezedFrom(index: Int): Mp4Track {
            val squeezed = durations.copyOf(count)
            for (at in index until count) squeezed[at] = (timescale / 10_000).toInt()
            return copy(count, squeezed)
        }

        private fun Mp4Track.copy(count: Int, durations: IntArray): Mp4Track = Mp4Track(
            kind = kind,
            timescale = timescale,
            language = language,
            sampleDescription = sampleDescription,
            nalLengthSize = nalLengthSize,
            width = width,
            height = height,
            matrix = matrix,
            editShift = editShift,
            firstDecodeTime = firstDecodeTime,
            count = count,
            offsets = offsets.copyOf(count),
            sizes = sizes.copyOf(count),
            durations = durations,
            compositionOffsets = compositionOffsets?.copyOf(count),
            sync = sync?.copyOf(count),
        )
    }

    private class BytesSource(private val bytes: ByteArray) : Mp4Source {
        override val size: Long = bytes.size.toLong()

        override fun readFully(target: ByteBuffer, position: Long) {
            require(position + target.remaining() <= size) { "past the end" }
            target.put(bytes, position.toInt(), target.remaining())
        }
    }

    private companion object {
        const val VIDEO_SAMPLES = 10_800
        const val AUDIO_SAMPLES = 10_547
        const val FRAME_TICKS = 416_667L
        const val STATED_MS = 448_200L
        const val WRAP_TICKS = 1L shl 32

        /** The first sample whose time Android's reader gives wrapped: 10,308 × 41.67 ms. */
        const val WRAP_SAMPLE = 10_308
    }
}
