package com.alal.yft.core.download

import com.alal.yft.core.download.Mp4TestFiles.Sample
import com.alal.yft.core.download.Mp4TestFiles.Track
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v1.0.2 fix R7: a video-only or a sound-only DASH track in a manifest's pieces (a
 * `SegmentTemplate` with a `SegmentTimeline` and `$Time$`, as tears-of-steel-av1's 240p and its
 * M4A) is saved with one index (`sidx`) for the whole track, though its initialization piece
 * states no length: right before the first piece's first `moof`, a reference for each piece that
 * lands on its `moof` (counted from the end of the index, as Media3 does, which is where the
 * first `moof` starts, as Android's reader counts), their durations adding up to the whole
 * track. The pieces' own indexes are `free` boxes; every sample is kept. Before R7 the pieces
 * were joined as they are: the video with an index of 4 s in each piece, the sound with none.
 */
class DashPieceIndexTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var workspaceRoot: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        workspaceRoot = directory.newFolder("dash-workspace")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a video-only file in pieces states its whole length in one index`() = runTest {
        // AV1 at 16,000 ticks a second in pieces of 4 s and a shorter last one, each piece with
        // a styp, a free box and its own sidx, as tears-of-steel-av1's video_eng=208000.
        val track = Track(
            video = true,
            timescale = 16_000,
            fragments = fragments(listOf(20, 20, 20, 20, 10), duration = 3_200, Random(7)),
            sampleEntry = "av01",
        )

        val saved = download(track, MediaTrackType.VIDEO, ownIndexes = true)

        assertOneIndex(saved, track, kind = StreamCopyTrackKind.VIDEO)
        // Each piece's own free box, and its own sidx kept as one.
        assertEquals(10, topLevel(saved.bytes).count { it.type == "free" })
    }

    @Test
    fun `an M4A in pieces states its whole length in one index`() = runTest {
        // AAC at 48 kHz whose edit list starts at 2,048 ticks, as audio_eng=128002, in pieces
        // without an index of their own.
        val track = Track(
            video = false,
            timescale = 48_000,
            fragments = fragments(listOf(47, 47, 47, 20), duration = 1_024, Random(8)),
            edits = listOf(Mp4TestFiles.Edit(0, 2_048)),
        )

        val saved = download(track, MediaTrackType.AUDIO, ownIndexes = false)

        assertOneIndex(saved, track, kind = StreamCopyTrackKind.AUDIO)
        assertEquals(4, topLevel(saved.bytes).count { it.type == "free" })
    }

    @Test
    fun `pieces whose tfhd names where their data is are joined as they are`() = runTest {
        // A moof that names its data's place in the file would lose it behind an index.
        val track = Track(
            video = true,
            timescale = 16_000,
            fragments = fragments(listOf(20, 20, 10), duration = 3_200, Random(9)),
        )
        val init = Mp4TestFiles.header(track)
        var at = init.size.toLong()
        var time = 0L
        val pieces = track.fragments.mapIndexed { index, samples ->
            val data = samples.fold(ByteArray(0)) { all, sample -> all + sample.bytes }
            val piece = absoluteMoof(index + 1, time, samples, at) + Mp4TestFiles.box("mdat", data)
            at += piece.size
            time += samples.sumOf { it.duration.toLong() }
            piece
        }

        val saved = download(track, MediaTrackType.VIDEO, init = init, pieces = pieces)

        assertArrayEquals(pieces.fold(init) { all, piece -> all + piece }, saved.bytes)
        assertEquals(saved.pieceBytes, saved.bytesWritten)
        assertSamples(saved.file, track, StreamCopyTrackKind.VIDEO)
    }

    @Test
    fun `the pieces' own indexes become free boxes however their bytes are read`() {
        val track = Track(
            video = true,
            timescale = 16_000,
            fragments = fragments(listOf(3, 3, 2), duration = 3_200, Random(10)),
        )
        val init = Mp4TestFiles.header(track)
        val pieces = piecesOf(track, ownIndexes = true)
        val files = (listOf(init) + pieces).mapIndexed { index, bytes ->
            directory.newFile("piece-$index").apply { writeBytes(bytes) }
        }
        val joined = files.fold(ByteArray(0)) { all, file -> all + file.readBytes() }
        val index = requireNotNull(DashPieceIndex.of(files))
        val whole = joined.copyOf().also { index.rename(it, 0L, it.size) }
        assertEquals(0, topLevel(whole).count { it.type == "sidx" })
        assertEquals(6, topLevel(whole).count { it.type == "free" })

        for (step in 1..9) {
            val copy = ByteArray(joined.size)
            var from = 0
            while (from < joined.size) {
                val length = minOf(step, joined.size - from)
                val window = joined.copyOfRange(from, from + length)
                index.rename(window, from.toLong(), length)
                window.copyInto(copy, from)
                from += length
            }
            assertArrayEquals("$step bytes at a time", whole, copy)
        }
    }

    /** The saved track: one sidx before the first moof, for every piece, and every sample. */
    private fun assertOneIndex(saved: Saved, track: Track, kind: StreamCopyTrackKind) {
        val pieces = track.fragments.size
        assertEquals(saved.bytes.size.toLong(), saved.bytesWritten)
        // The pieces' bytes and the index: 40 bytes and 12 for each piece.
        assertEquals(saved.pieceBytes + 40 + 12 * pieces, saved.bytesWritten)
        val boxes = topLevel(saved.bytes)
        val indexes = boxes.filter { it.type == "sidx" }
        assertEquals("one sidx in ${boxes.map { it.type }}", 1, indexes.size)
        val sidx = indexes.single()
        val moofs = boxes.filter { it.type == "moof" }
        assertEquals(pieces, moofs.size)
        assertTrue(boxes.indexOfFirst { it.type == "moov" } < boxes.indexOf(sidx))
        assertEquals(moofs.first().at, sidx.at + sidx.size)
        val view = ByteBuffer.wrap(saved.bytes, sidx.at.toInt(), sidx.size.toInt()).slice()
        assertEquals("version", 1, view.get(8).toInt())
        assertEquals("reference_ID", 1, view.getInt(12))
        assertEquals("timescale", track.timescale, view.getInt(16))
        assertEquals("earliest_presentation_time", 0L, view.getLong(20))
        assertEquals("first_offset", 0L, view.getLong(28))
        assertEquals("reference_count", pieces, view.getShort(38).toInt() and 0xffff)
        var offset = sidx.at + sidx.size
        var ticks = 0L
        for (piece in 0 until pieces) {
            val entry = 40 + 12 * piece
            assertEquals("piece $piece starts at its moof", moofs[piece].at, offset)
            val duration = view.getInt(entry + 4).toLong() and UINT32
            assertEquals(track.fragments[piece].sumOf { it.duration.toLong() }, duration)
            assertEquals("piece $piece starts with a sync sample", SAP, view.getInt(entry + 8))
            offset += view.getInt(entry)
            ticks += duration
        }
        assertEquals(saved.bytes.size.toLong(), offset)
        // The whole length, as Media3 and Android read it from the index.
        assertEquals(track.samples.sumOf { it.duration.toLong() }, ticks)
        assertSamples(saved.file, track, kind)
    }

    /** [file] holds [track]'s samples, in order, with their durations. */
    private fun assertSamples(file: File, track: Track, kind: StreamCopyTrackKind) {
        RandomAccessFile(file, "r").use { input ->
            val source = FileChannelMp4Source(input.channel)
            val read = Mp4TrackReader(source, kind, anyCodec = true).read()
            assertEquals(track.samples.size, read.count)
            track.samples.forEachIndexed { index, sample ->
                val bytes = ByteBuffer.allocate(read.sizes[index])
                source.readFully(bytes, read.offsets[index])
                assertArrayEquals("sample $index", sample.bytes, bytes.array())
                assertEquals(sample.duration, read.durations[index])
            }
        }
    }

    /**
     * Serves [track] as a manifest's pieces, `$Time$` named: its ftyp and moov as the
     * initialization piece, then each fragment as a piece ([piecesOf]); downloads it.
     */
    private suspend fun download(
        track: Track,
        type: MediaTrackType,
        ownIndexes: Boolean = false,
        init: ByteArray = Mp4TestFiles.header(track),
        pieces: List<ByteArray> = piecesOf(track, ownIndexes),
    ): Saved {
        val name = if (type == MediaTrackType.AUDIO) "audio" else "video"
        val starts = track.fragments.runningFold(0L) { time, samples ->
            time + samples.sumOf { it.duration.toLong() }
        }
        val served = pieces.withIndex().associate { (index, bytes) ->
            "/$name/t-${starts[index]}.m4s" to bytes
        } + ("/$name/init.mp4" to init)
        val timeline = track.fragments.withIndex().joinToString("\n") { (index, samples) ->
            """<S t="${starts[index]}" d="${samples.sumOf { it.duration }}" />"""
        }
        val mime = if (type == MediaTrackType.AUDIO) "audio/mp4" else "video/mp4"
        val manifest = """
            <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet contentType="$name" mimeType="$mime">
                  <SegmentTemplate timescale="${track.timescale}"
                      initialization="$name/init.mp4" media="$name/t-${'$'}Time${'$'}.m4s">
                    <SegmentTimeline>
                      $timeline
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="r1" bandwidth="208000" />
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/manifest.mpd") {
                    return MockResponse()
                        .setHeader("Content-Type", "application/dash+xml")
                        .setBody(manifest)
                }
                val bytes = served[request.path] ?: return MockResponse().setResponseCode(404)
                return MockResponse()
                    .setHeader("Content-Type", mime)
                    .setBody(Buffer().write(bytes))
            }
        }
        val output = directory.newFolder("output-$name-${pieces.size}-${init.size}")
        val completed = File(output, "$name.mp4")
        val engine = DashTransferEngine(
            client = OkHttpClient(),
            workspaceRoot = workspaceRoot,
            policy = DashTransferEngine.Policy(
                initialRetryDelayMillis = 0,
                maxConcurrentChunks = 3,
                bufferBytes = 1_024,
            ),
            clock = { 100 },
        )
        val result = engine.transfer(
            plan = DashDownloadPlan(
                taskId = "dash-r7-$name",
                manifestUrl = server.url("/manifest.mpd").toString(),
                representationId = "r1",
                trackType = type,
                suggestedFileName = "$name.mp4",
                requestContext = BrowserRequestContext(
                    pageUrl = "https://page.example.test/watch",
                    userAgent = "YFT R7 fixture",
                    cookie = null,
                ),
                mimeType = mime,
            ),
            destination = FileDownloadDestination(File(output, "$name.mp4.part"), completed),
        )
        assertTrue("$result", result is DashTransferResult.Completed)
        return Saved(
            file = completed,
            bytesWritten = (result as DashTransferResult.Completed).bytesWritten,
            pieceBytes = init.size.toLong() + pieces.sumOf { it.size.toLong() },
        )
    }

    /**
     * [track]'s fragments as pieces, as Unified Streaming serves them: styp, free, with
     * [ownIndexes] a sidx of the piece alone, then the moof and its mdat.
     */
    private fun piecesOf(track: Track, ownIndexes: Boolean): List<ByteArray> {
        var time = 0L
        return track.fragments.mapIndexed { index, samples ->
            val data = samples.fold(ByteArray(0)) { all, sample -> all + sample.bytes }
            val fragment = Mp4TestFiles.moof(track, index + 1, time, samples) +
                Mp4TestFiles.box("mdat", data)
            val duration = samples.sumOf { it.duration }
            val own = if (ownIndexes) {
                Mp4TestFiles.fullBox(
                    "sidx",
                    0,
                    0,
                    Mp4TestFiles.int(1),
                    Mp4TestFiles.int(track.timescale),
                    Mp4TestFiles.int(time.toInt()),
                    Mp4TestFiles.int(0),
                    Mp4TestFiles.short(0),
                    Mp4TestFiles.short(1),
                    Mp4TestFiles.int(fragment.size),
                    Mp4TestFiles.int(duration),
                    Mp4TestFiles.int(SAP),
                )
            } else {
                ByteArray(0)
            }
            time += duration
            STYP + Mp4TestFiles.box("free", ByteArray(28)) + own + fragment
        }
    }

    /** Fragments of [counts] samples of [duration] ticks, each starting with a sync sample. */
    private fun fragments(counts: List<Int>, duration: Int, random: Random): List<List<Sample>> =
        counts.map { count ->
            List(count) { index ->
                Sample(random.nextBytes(50 + random.nextInt(400)), duration, sync = index == 0)
            }
        }

    /** A moof whose tfhd names its data's place: [at], where the moof is in the joined file. */
    private fun absoluteMoof(
        sequence: Int,
        decodeTime: Long,
        samples: List<Sample>,
        at: Long,
    ): ByteArray {
        val entries = ByteBuffer.allocate(samples.size * 16)
        samples.forEach { sample ->
            entries.putInt(sample.duration)
            entries.putInt(sample.size)
            entries.putInt(if (sample.sync) 0x02000000 else 0x01010000)
            entries.putInt(sample.compositionOffset)
        }
        val tfhd = Mp4TestFiles.fullBox("tfhd", 0, 0x000001, Mp4TestFiles.int(1), Mp4TestFiles.long(at))
        val tfdt = Mp4TestFiles.fullBox("tfdt", 1, 0, Mp4TestFiles.long(decodeTime))
        val mfhd = Mp4TestFiles.fullBox("mfhd", 0, 0, Mp4TestFiles.int(sequence))
        val trunSize = 8 + 4 + 4 + 4 + samples.size * 16
        val moofSize = 8 + mfhd.size + 8 + tfhd.size + tfdt.size + trunSize
        val trun = Mp4TestFiles.fullBox(
            "trun",
            0,
            0x001 or 0x100 or 0x200 or 0x400 or 0x800,
            Mp4TestFiles.int(samples.size),
            Mp4TestFiles.int(moofSize + 8),
            entries.array(),
        )
        return Mp4TestFiles.box("moof", mfhd, Mp4TestFiles.box("traf", tfhd, tfdt, trun))
    }

    /** The top-level boxes of [bytes]. */
    private fun topLevel(bytes: ByteArray): List<TopBox> = buildList {
        var at = 0L
        while (at < bytes.size) {
            val size = ByteBuffer.wrap(bytes, at.toInt(), 4).int.toLong() and UINT32
            val type = String(bytes, at.toInt() + 4, 4, Charsets.ISO_8859_1)
            assertTrue("box $type at $at", size >= 8 && at + size <= bytes.size)
            add(TopBox(type, at, size))
            at += size
        }
    }

    private class TopBox(val type: String, val at: Long, val size: Long)

    private class Saved(val file: File, val bytesWritten: Long, val pieceBytes: Long) {
        val bytes: ByteArray by lazy { file.readBytes() }
    }

    private companion object {
        const val SAP = 0x90000000.toInt()
        val STYP: ByteArray = Mp4TestFiles.box(
            "styp",
            "msdh".toByteArray(Charsets.ISO_8859_1),
            Mp4TestFiles.int(0),
            "msdhmsix".toByteArray(Charsets.ISO_8859_1),
        )
    }
}
