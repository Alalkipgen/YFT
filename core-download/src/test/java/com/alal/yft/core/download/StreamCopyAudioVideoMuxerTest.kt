package com.alal.yft.core.download

import com.alal.yft.core.download.Mp4TestFiles.Edit
import com.alal.yft.core.download.Mp4TestFiles.Sample
import com.alal.yft.core.download.Mp4TestFiles.Track
import com.alal.yft.core.model.download.DownloadFailureReason
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P35's stream copy in plain Kotlin: MP4 and M4A tracks built in the test are copied into one
 * MP4 whose tables are read back independently ([Mp4Dump]); what it does not support, and a
 * failed check, go to today's way once.
 */
class StreamCopyAudioVideoMuxerTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `an MP4 video and an M4A sound are stream-copied, not merged by today's way`() {
        val video = Mp4TestFiles.video()
        val audio = Mp4TestFiles.audio()
        val fallback = RecordingFallback()
        val output = file("merged.mp4")

        val attempt = StreamCopyAudioVideoMuxer(fallback, PASSING).mux(
            file("video.mp4", Mp4TestFiles.fragmented(video)),
            file("audio.m4a", Mp4TestFiles.fragmented(audio)),
            MuxOutput.ToFile(output),
            "video/mp4",
        ) { _, _ -> }

        assertEquals("today's way", emptyList<String>(), fallback.calls)
        assertEquals(LocalMuxResult.Completed(output.length()), attempt.result)
        assertEquals(219L, attempt.samplesWritten)
        val details = attempt.details!!
        assertEquals(MuxDetails.STREAM_COPY, details.path)
        assertEquals(90L to 129L, details.videoSamples to details.audioSamples)
        assertEquals(listOf("parse", "data", "index", "check"), details.phases.map { it.first })
        assertNull(details.streamCopyNote)
        assertTrue(details.summary(), details.summary().startsWith("stream copy: 90 video + "))

        val merged = output.readBytes()
        val dump = Mp4Dump(merged)
        assertEquals(listOf("ftyp", "mdat", "moov"), dump.top.map { it.type })
        assertEquals(listOf("vide", "soun"), dump.tracks.map { it.handler })
        assertEquals(listOf(1, 2), dump.tracks.map { it.trackId })
        assertEquals(160 shl 16, dump.tracks[0].width)
        assertSamples(video, dump.tracks[0], merged)
        assertSamples(audio, dump.tracks[1], merged)
        assertTrue(dump.tracks[0].boxTypes.containsAll(listOf("ctts", "stss", "stco")))
        assertFalse("sound has no ctts", "ctts" in dump.tracks[1].boxTypes)
        assertFalse("every sound sample is a sync sample", "stss" in dump.tracks[1].boxTypes)
        assertEquals(0, dump.tracks[0].cttsVersion)
        assertEquals(emptyList<Pair<Long, Long>>(), dump.tracks[0].edits)
        assertEquals(emptyList<Pair<Long, Long>>(), dump.tracks[1].edits)
        // The sample descriptions stay byte for byte.
        listOf(video, audio).forEachIndexed { index, track ->
            val input = Mp4Dump(Mp4TestFiles.fragmented(track)).tracks.single()
            assertArrayEquals(input.sampleDescription, dump.tracks[index].sampleDescription)
        }
        assertEquals(3_000L, dump.movieDuration)
        assertEquals(1_000L, dump.movieTimescale)
    }

    @Test
    fun `a plain M4A with uneven samples gives tables that match every sample`() {
        val random = kotlin.random.Random(3)
        val samples = List(100) { index ->
            Sample(random.nextBytes(64), duration = if (index == 99) 512 else 1_024)
        }
        val audio = Track(video = false, timescale = 48_000, fragments = listOf(samples))
        val video = Mp4TestFiles.video(fragments = 2)
        val output = file("plain.mp4")

        val attempt = StreamCopyAudioVideoMuxer(RecordingFallback(), PASSING).mux(
            file("video.mp4", Mp4TestFiles.fragmented(video)),
            file("audio.m4a", Mp4TestFiles.plain(audio)),
            MuxOutput.ToFile(output),
            "video/mp4",
        ) { _, _ -> }

        assertEquals(MuxDetails.STREAM_COPY, attempt.details?.path)
        val dump = Mp4Dump(output.readBytes())
        assertSamples(audio, dump.tracks[1], output.readBytes())
        assertEquals(99L * 1_024 + 512, dump.tracks[1].mediaDuration)
        // Equal sizes: one stsz size instead of a table.
        val stszSizes = dump.tracks[1].samples.map { it.size }.distinct()
        assertEquals(listOf(64), stszSizes)
    }

    @Test
    fun `chunks of about a second alternate between video and sound in time order`() {
        val video = Mp4TestFiles.video()
        val audio = Mp4TestFiles.audio()
        val output = file("chunks.mp4")

        StreamCopyAudioVideoMuxer(RecordingFallback(), PASSING).mux(
            file("video.mp4", Mp4TestFiles.fragmented(video)),
            file("audio.m4a", Mp4TestFiles.fragmented(audio)),
            MuxOutput.ToFile(output),
            "video/mp4",
        ) { _, _ -> }

        val dump = Mp4Dump(output.readBytes())
        val chunks = dump.tracks.flatMapIndexed { track, dumped ->
            var first = 0
            dumped.chunkOffsets.mapIndexed { chunk, offset ->
                val start = dumped.samples[first].decodeTime.toDouble() / dumped.timescale
                val samples = dumped.chunkSamples[chunk]
                val end = dumped.samples[first + samples - 1].let { it.decodeTime } +
                    (if (track == 0) 512 else 1_024)
                first += samples
                Chunk(track, offset, start, end.toDouble() / dumped.timescale - start)
            }
        }.sortedBy { it.offset }
        assertEquals(listOf(0, 1, 0, 1, 0, 1), chunks.map { it.track })
        assertEquals(chunks.map { it.start }.sorted(), chunks.map { it.start })
        assertTrue("$chunks", chunks.all { it.seconds <= 1.05 })
    }

    @Test
    fun `past 4 GiB the chunk offsets go into co64 and the mdat gets a 64-bit size`() {
        val big = 48 * 1_024 * 1_024
        val samples = List(100) { index ->
            Sample(ByteArray(0), duration = 512, sync = index % 10 == 0, size = big)
        }
        val video = Track(video = true, timescale = 15_360, fragments = listOf(samples))
        val prefix = Mp4TestFiles.header(video) +
            Mp4TestFiles.moof(video, 1, 0, samples, mdatHeaderBytes = 16)
        val payload = 100L * big
        val mdatHeader = ByteBuffer.allocate(16).putInt(1).put("mdat".toByteArray())
            .putLong(payload + 16).array()
        val videoSource = VirtualSource(prefix + mdatHeader, prefix.size + 16 + payload)
        val audioBytes = Mp4TestFiles.fragmented(Mp4TestFiles.audio())
        val audioSource = VirtualSource(audioBytes, audioBytes.size.toLong())

        val videoTrack = Mp4TrackReader(videoSource, StreamCopyTrackKind.VIDEO).read()
        val audioTrack = Mp4TrackReader(audioSource, StreamCopyTrackKind.AUDIO).read()
        val layout = StreamCopyLayout.of(videoTrack, audioTrack)
        val sink = CapturingOutput(captureFrom = layout.payloadEnd)
        val writer = StreamCopyWriter(ByteBuffer.allocateDirect(4 * 1_024 * 1_024))
        writer.writeData(layout, videoSource, audioSource, sink) { _, _ -> }
        val size = writer.writeMoov(layout, sink)

        assertTrue(layout.payloadEnd > 0xffffffffL)
        val header = ByteBuffer.wrap(sink.start)
        assertEquals(1, header.getInt(32))
        assertEquals("mdat", String(sink.start, 36, 4))
        assertEquals(layout.payloadBytes + 16, header.getLong(40))
        val moov = sink.end.toByteArray()
        assertEquals(size, layout.payloadEnd + moov.size)
        val dump = Mp4Dump(moov)
        val dumpedVideo = dump.tracks[0]
        assertTrue(dumpedVideo.boxTypes.toString(), "co64" in dumpedVideo.boxTypes)
        assertTrue(dumpedVideo.chunkOffsets.last() > 0xffffffffL)
        // Each sample sits where the layout copied it, in the order of the input.
        assertEquals(100, dumpedVideo.samples.size)
        assertEquals(48L, layout.payloadStart)
        val all = dump.tracks.flatMap { it.samples }.sortedBy { it.offset }
        var expected = layout.payloadStart
        for (sample in all) {
            assertEquals(expected, sample.offset)
            expected += sample.size
        }
        assertEquals(layout.payloadEnd, expected)
        assertEquals(10, dumpedVideo.samples.count { it.sync })
    }

    @Test
    fun `encrypted or odd tracks are not supported and today's way merges once`() {
        val video = Mp4TestFiles.video()
        val audio = Mp4TestFiles.audio()
        val audioFile = file("audio.m4a", Mp4TestFiles.fragmented(audio))
        val pssh = Mp4TestFiles.fullBox("pssh", 0, 0, ByteArray(20))
        val cases = listOf(
            "encrypted" to fragmented(video, sampleEntry = "encv"),
            "encrypted" to fragmented(video, extraMoov = listOf(pssh)),
            "encrypted" to fragmented(video, extraTraf = listOf(Mp4TestFiles.box("senc"))),
            "encrypted" to fragmented(video, extraEntry = listOf(Mp4TestFiles.box("sinf"))),
            "2 tracks in one file" to fragmented(video, tracks = 2),
            "codec hvc1" to fragmented(video, sampleEntry = "hvc1"),
            "not a video track" to Mp4TestFiles.fragmented(audio),
            "broken size of moof" to Mp4TestFiles.fragmented(video).also { bytes ->
                // The first moof claims more bytes than the file has.
                val at = String(bytes, Charsets.ISO_8859_1).indexOf("moof") - 4
                ByteBuffer.wrap(bytes).putInt(at, Int.MAX_VALUE)
            },
        )

        for ((reason, bytes) in cases) {
            val fallback = RecordingFallback()
            val output = file("odd.mp4")

            val attempt = StreamCopyAudioVideoMuxer(fallback, PASSING).mux(
                file("odd-video.mp4", bytes),
                audioFile,
                MuxOutput.ToFile(output),
                "video/mp4",
            ) { _, _ -> }

            assertEquals(reason, listOf("video/mp4"), fallback.calls)
            assertEquals(reason, false, fallback.outputExisted)
            assertEquals(reason, "not supported, $reason", attempt.details?.streamCopyNote)
            assertEquals(reason, MuxDetails.MEDIA_MUXER, attempt.details?.path)
            assertEquals(LocalMuxResult.Completed(FALLBACK_BYTES.size.toLong()), attempt.result)
            assertArrayEquals(FALLBACK_BYTES, output.readBytes())
        }
    }

    @Test
    fun `sample data outside an mdat is not supported`() {
        val video = Mp4TestFiles.video(fragments = 1)
        val bytes = Mp4TestFiles.fragmented(video)
        // The mdat becomes a free box: the samples no longer lie in an mdat.
        val at = String(bytes, Charsets.ISO_8859_1).lastIndexOf("mdat")
        "free".toByteArray().copyInto(bytes, at)
        val fallback = RecordingFallback()

        val attempt = StreamCopyAudioVideoMuxer(fallback, PASSING).mux(
            file("video.mp4", bytes),
            file("audio.m4a", Mp4TestFiles.fragmented(Mp4TestFiles.audio())),
            MuxOutput.ToFile(file("outside.mp4")),
            "video/mp4",
        ) { _, _ -> }

        assertEquals(1, fallback.calls.size)
        assertEquals("not supported, sample data outside mdat", attempt.details?.streamCopyNote)
    }

    @Test
    fun `a failed check empties the output and today's way merges once`() {
        val inputs = inputs()
        for (descriptor in listOf(false, true)) {
            val fallback = RecordingFallback()
            val output = file("checked-$descriptor.mp4")
            val opened = RandomAccessFile(output, "rw")
            val muxOutput = if (descriptor) {
                MuxOutput.ToDescriptor(opened.fd)
            } else {
                opened.close()
                MuxOutput.ToFile(output)
            }
            var checked = 0L
            val check = StreamCopyCheck { _, _, _, _ ->
                checked = output.length()
                "video sample bytes"
            }

            val attempt = StreamCopyAudioVideoMuxer(fallback, check).mux(
                inputs.first,
                inputs.second,
                muxOutput,
                "video/mp4",
            ) { _, _ -> }
            if (descriptor) {
                assertTrue("the descriptor stays open", opened.fd.valid())
                opened.close()
            }

            assertTrue("checked a written file", checked > 0)
            assertEquals(listOf("video/mp4"), fallback.calls)
            if (descriptor) {
                assertEquals("emptied for today's way", 0L, fallback.outputLength)
            } else {
                assertEquals("deleted for today's way", false, fallback.outputExisted)
            }
            assertEquals("check failed, video sample bytes", attempt.details?.streamCopyNote)
            assertTrue(
                attempt.details!!.summary(),
                attempt.details!!.summary().startsWith(
                    "MediaMuxer (stream copy: check failed, video sample bytes): 5 video + ",
                ),
            )
        }
    }

    @Test
    fun `a failure of today's way after the stream copy keeps both reasons`() {
        val inputs = inputs()
        val fallback = RecordingFallback(
            result = LocalMuxResult.Failure(
                DownloadFailureReason.INCOMPATIBLE_TRACKS,
                "Muxer died",
            ),
        )

        val failing = StreamCopyCheck { _, _, _, _ -> "audio duration" }
        val attempt = StreamCopyAudioVideoMuxer(fallback, failing).mux(
            inputs.first,
            inputs.second,
            MuxOutput.ToFile(file("both.mp4")),
            "video/mp4",
        ) { _, _ -> }

        assertEquals(
            LocalMuxResult.Failure(
                DownloadFailureReason.INCOMPATIBLE_TRACKS,
                "Muxer died; stream copy check failed, audio duration",
            ),
            attempt.result,
        )
    }

    @Test
    fun `progress moves on by the bytes copied and ends at 100 percent`() {
        val inputs = inputs()
        val steps = mutableListOf<Pair<Long, Long>>()

        StreamCopyAudioVideoMuxer(RecordingFallback(), PASSING, bufferBytes = 1_024).mux(
            inputs.first,
            inputs.second,
            MuxOutput.ToFile(file("progress.mp4")),
            "video/mp4",
        ) { done, total -> steps += done to total }

        val total = steps.first().second
        assertTrue(steps.all { it.second == total })
        assertEquals(steps.map { it.first }.sorted(), steps.map { it.first })
        assertEquals(total to total, steps.last())
        assertTrue("$steps", steps.map { it.first }.distinct().size >= 6)
    }

    @Test
    fun `a WebM merge goes straight to today's way`() {
        val inputs = inputs()
        val fallback = RecordingFallback()

        val attempt = StreamCopyAudioVideoMuxer(fallback, PASSING).mux(
            inputs.first,
            inputs.second,
            MuxOutput.ToFile(file("merged.webm")),
            "video/webm",
        ) { _, _ -> }

        assertEquals(listOf("video/webm"), fallback.calls)
        assertNull(attempt.details?.streamCopyNote)
    }

    @Test
    fun `a stopped download stops the stream copy and leaves no output`() {
        val inputs = inputs()
        val fallback = RecordingFallback()
        val output = file("stopped.mp4")

        assertThrows(CancellationException::class.java) {
            StreamCopyAudioVideoMuxer(fallback, PASSING).mux(
                inputs.first,
                inputs.second,
                MuxOutput.ToFile(output),
                "video/mp4",
            ) { _, _ -> throw CancellationException("Merge stopped") }
        }

        assertFalse(output.exists())
        assertEquals(emptyList<String>(), fallback.calls)
    }

    @Test
    fun `the destination's descriptor gets the same file and stays open`() {
        val inputs = inputs()
        val byFile = file("by-file.mp4")
        val byDescriptor = file("by-descriptor.mp4")
        val muxer = StreamCopyAudioVideoMuxer(RecordingFallback(), PASSING)
        muxer.mux(inputs.first, inputs.second, MuxOutput.ToFile(byFile), "video/mp4") { _, _ -> }

        RandomAccessFile(byDescriptor, "rw").use { opened ->
            opened.write(ByteArray(100_000))
            val attempt = muxer.mux(
                inputs.first,
                inputs.second,
                MuxOutput.ToDescriptor(opened.fd),
                "video/mp4",
            ) { _, _ -> }

            assertTrue(opened.fd.valid())
            opened.fd.sync()
            assertEquals(LocalMuxResult.Completed(byFile.length()), attempt.result)
        }
        assertArrayEquals(byFile.readBytes(), byDescriptor.readBytes())
    }

    @Test
    fun `edit lists keep both tracks where the inputs presented them`() {
        // The video's edit starts it at its first frame's composition offset (512); the sound
        // starts half a second later.
        val video = Mp4TestFiles.video().let { track ->
            Track(true, track.timescale, track.fragments, edits = listOf(Edit(0, 512)))
        }
        val audio = Mp4TestFiles.audio().let { track ->
            Track(false, track.timescale, track.fragments, firstDecodeTime = 22_050)
        }
        val output = file("edits.mp4")

        StreamCopyAudioVideoMuxer(RecordingFallback(), PASSING).mux(
            file("video.mp4", Mp4TestFiles.fragmented(video)),
            file("audio.m4a", Mp4TestFiles.fragmented(audio)),
            MuxOutput.ToFile(output),
            "video/mp4",
        ) { _, _ -> }

        val dump = Mp4Dump(output.readBytes())
        val videoEnd = video.samples.foldIndexed(0L to 0L) { _, (time, end), sample ->
            time + sample.duration to maxOf(end, time + sample.compositionOffset + sample.duration)
        }.second
        assertEquals(listOf((videoEnd - 512) * 1_000 / 15_360 to 512L), dump.tracks[0].edits)
        val audioEnd = 129L * 1_024
        assertEquals(
            listOf(500L to -1L, audioEnd * 1_000 / 44_100 to 0L),
            dump.tracks[1].edits,
        )
        assertEquals(500L + audioEnd * 1_000 / 44_100, dump.tracks[1].trackDuration)
    }

    @Test
    fun `negative composition offsets are shifted into ctts version 0 and the edit list`() {
        val base = Mp4TestFiles.video(fragments = 1)
        val samples = base.samples.mapIndexed { index, sample ->
            Sample(sample.bytes, sample.duration, if (index % 2 == 1) -512 else 0, sample.sync)
        }
        val video = Track(true, base.timescale, listOf(samples))
        val output = file("negative.mp4")

        StreamCopyAudioVideoMuxer(RecordingFallback(), PASSING).mux(
            file("video.mp4", Mp4TestFiles.fragmented(video)),
            file("audio.m4a", Mp4TestFiles.fragmented(Mp4TestFiles.audio(fragments = 1))),
            MuxOutput.ToFile(output),
            "video/mp4",
        ) { _, _ -> }

        val dumped = Mp4Dump(output.readBytes()).tracks[0]
        assertEquals(0, dumped.cttsVersion)
        assertEquals(
            samples.map { it.compositionOffset + 512 },
            dumped.samples.map { it.compositionOffset },
        )
        assertEquals(512L, dumped.edits.single().second)
    }

    @Test
    fun `a fragment whose tfdt leaves a gap lengthens the sample before it`() {
        val base = Mp4TestFiles.video(fragments = 2, perFragment = 10)
        val video = Track(
            true,
            base.timescale,
            base.fragments,
            decodeTimes = listOf(0L, 10L * 512 + 100),
        )
        val track = Mp4TrackReader(
            VirtualSource(Mp4TestFiles.fragmented(video)),
            StreamCopyTrackKind.VIDEO,
        ).read()

        assertEquals(20, track.count)
        assertEquals(612, track.durations[9])
        assertEquals(512, track.durations[10])
        assertEquals(20L * 512 + 100, track.mediaDuration)
    }

    private fun assertSamples(input: Track, dumped: Mp4Dump.DumpedTrack, merged: ByteArray) {
        val samples = input.samples
        assertEquals(samples.size, dumped.samples.size)
        assertEquals(input.timescale.toLong(), dumped.timescale)
        assertEquals(samples.sumOf { it.duration.toLong() }, dumped.mediaDuration)
        var decodeTime = 0L
        samples.forEachIndexed { index, sample ->
            val copied = dumped.samples[index]
            assertEquals("size $index", sample.bytes.size, copied.size)
            val at = copied.offset.toInt()
            val bytes = merged.copyOfRange(at, at + copied.size)
            assertArrayEquals("bytes $index", sample.bytes, bytes)
            assertEquals("decode time $index", decodeTime, copied.decodeTime)
            assertEquals("offset $index", sample.compositionOffset, copied.compositionOffset)
            assertEquals("sync $index", !input.video || sample.sync, copied.sync)
            decodeTime += sample.duration
        }
    }

    private fun fragmented(
        base: Track,
        sampleEntry: String = base.sampleEntry,
        extraMoov: List<ByteArray> = emptyList(),
        extraTraf: List<ByteArray> = emptyList(),
        extraEntry: List<ByteArray> = emptyList(),
        tracks: Int = 1,
    ): ByteArray = Mp4TestFiles.fragmented(
        Track(
            video = base.video,
            timescale = base.timescale,
            fragments = base.fragments,
            sampleEntry = sampleEntry,
            extraMoov = extraMoov,
            extraTraf = extraTraf,
            extraEntry = extraEntry,
            tracks = tracks,
        ),
    )

    private fun inputs(): Pair<File, File> = file(
        "video.mp4",
        Mp4TestFiles.fragmented(Mp4TestFiles.video()),
    ) to file("audio.m4a", Mp4TestFiles.fragmented(Mp4TestFiles.audio()))

    private fun file(name: String, bytes: ByteArray? = null): File =
        File(directory.root, name).apply { bytes?.let(::writeBytes) }

    private data class Chunk(
        val track: Int,
        val offset: Long,
        val start: Double,
        val seconds: Double,
    )

    /** Today's way, faked: writes [FALLBACK_BYTES] and says what it found. */
    private class RecordingFallback(
        private val result: LocalMuxResult = LocalMuxResult.Completed(FALLBACK_BYTES.size.toLong()),
    ) : ProgressAudioVideoMuxer {
        val calls = mutableListOf<String>()
        var outputExisted: Boolean? = null
        var outputLength: Long? = null

        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult = result

        override fun mux(
            videoFile: File,
            audioFile: File,
            output: MuxOutput,
            outputMimeType: String,
            onProgress: MuxProgressListener,
        ): MuxAttempt {
            calls += outputMimeType
            when (output) {
                is MuxOutput.ToFile -> {
                    outputExisted = output.file.exists()
                    output.file.writeBytes(FALLBACK_BYTES)
                }
                is MuxOutput.ToDescriptor -> {
                    outputLength = java.io.FileInputStream(output.descriptor).channel.size()
                }
            }
            return MuxAttempt(
                result = result,
                samplesWritten = 10,
                details = MuxDetails(MuxDetails.MEDIA_MUXER, 5, 5, listOf("open" to 1L)),
            )
        }
    }

    /** A file whose bytes after [prefix] are virtual: reading them only moves the buffer on. */
    private class VirtualSource(
        private val prefix: ByteArray,
        override val size: Long = prefix.size.toLong(),
    ) : Mp4Source {
        override fun readFully(target: ByteBuffer, position: Long) {
            require(position + target.remaining() <= size) { "past the end" }
            if (position < prefix.size) {
                val count = minOf(target.remaining().toLong(), prefix.size - position).toInt()
                target.put(prefix, position.toInt(), count)
            }
            target.position(target.limit())
        }
    }

    /** Keeps the first 64 bytes and everything from [captureFrom] on; drops the rest. */
    private class CapturingOutput(private val captureFrom: Long) : StreamCopyOutput {
        val start = ByteArray(64)
        val end = ByteArrayOutputStream()

        override fun write(source: ByteBuffer, position: Long) {
            val bytes = ByteArray(source.remaining())
            source.get(bytes)
            for (index in bytes.indices) {
                val at = position + index
                if (at < start.size) start[at.toInt()] = bytes[index]
            }
            if (position + bytes.size > captureFrom) {
                val skip = maxOf(0L, captureFrom - position).toInt()
                end.write(bytes, skip, bytes.size - skip)
            }
        }

        override fun truncate(size: Long) = Unit
    }

    private companion object {
        val PASSING = StreamCopyCheck { _, _, _, _ -> null }
        val FALLBACK_BYTES = "today's way".toByteArray()
    }
}
