package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.nio.ByteBuffer
import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v1.0.2 item 59: a DASH representation that is one file (`SegmentBase`: its own `BaseURL`, an
 * `Initialization` range and an `indexRange`, the `sidx`) is downloaded as one file in ranges,
 * resumed as a whole file is, and merged with its manifest's sound.
 */
class DashOneFileTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var files: OneFileDispatcher
    private lateinit var workspaceRoot: File

    @Before
    fun setUp() {
        files = OneFileDispatcher()
        server = MockWebServer()
        server.dispatcher = files
        server.start()
        workspaceRoot = directory.newFolder("one-file")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a SegmentBase representation is one file with its ranges`() {
        val video = parse(manifest(), "v360", MediaTrackType.VIDEO)
            as DashDownloadManifestParser.Result.OneFile

        assertEquals("$BASE/media/video-360.mp4", video.url.toString())
        assertEquals(0L, video.initialization?.offset)
        assertEquals(VIDEO_INIT - 1L, video.initialization?.endInclusive)
        assertEquals(VIDEO_INIT.toLong(), video.index?.offset)
        assertEquals(VIDEO_INIT + VIDEO_INDEX - 1L, video.index?.endInclusive)
        assertEquals(VIDEO_INIT + VIDEO_INDEX - 1L, video.statedEndInclusive)
        assertEquals("video/mp4", video.mimeType)
        assertEquals(listOf("avc1.4d401e"), video.codecs)
        assertFalse(video.toString().contains("media.example.test"))

        val audio = parse(manifest(), "a128", MediaTrackType.AUDIO)
            as DashDownloadManifestParser.Result.OneFile
        assertEquals("$BASE/media/audio-en.m4a?k=1", audio.url.toString())
        assertEquals(MediaTrackType.AUDIO, audio.trackType)
        // The SegmentBase of the adaptation set holds for its representations.
        assertEquals(AUDIO_INIT.toLong(), audio.index?.offset)

        // An Initialization that names the same file is still that file.
        val same = manifest(videoInitialization = """sourceURL="video-360.mp4" range="0-699"""")
        assertTrue(
            parse(same, "v360", MediaTrackType.VIDEO) is DashDownloadManifestParser.Result.OneFile,
        )
        // No ranges stated: still one file, asked in ranges all the same.
        val bare = parse(
            manifest(videoSegmentBase = "<SegmentBase/>"),
            "v360",
            MediaTrackType.VIDEO,
        ) as DashDownloadManifestParser.Result.OneFile
        assertEquals(null, bare.statedEndInclusive)
    }

    @Test
    fun `what one file cannot be stays unsupported or malformed`() {
        fun video(manifest: String) = parse(manifest, "v360", MediaTrackType.VIDEO)
        val unsupported = DashDownloadManifestParser.Result.Unsupported
        val malformed = DashDownloadManifestParser.Result.Malformed

        // The initialization in another file.
        assertEquals(
            unsupported,
            video(manifest(videoInitialization = """sourceURL="init.mp4" range="0-699"""")),
        )
        // SegmentBase beside a SegmentTemplate.
        assertEquals(
            unsupported,
            video(
                manifest(
                    videoSegmentBase = """
                        <SegmentBase indexRange="700-799"/>
                        <SegmentTemplate media="${'$'}Number${'$'}.m4s" duration="2" startNumber="1"/>
                    """,
                ),
            ),
        )
        // The file is named by no BaseURL of the representation's own.
        assertEquals(unsupported, video(manifest(videoBaseUrl = "")))
        // Ranges that are no ranges.
        assertEquals(malformed, video(manifest(videoSegmentBase = """<SegmentBase indexRange="x"/>""")))
        assertEquals(
            malformed,
            video(manifest(videoInitialization = """range="9-1"""")),
        )
        assertEquals(
            malformed,
            video(
                manifest(
                    videoSegmentBase = """
                        <SegmentBase indexRange="700-799">
                          <Initialization range="0-699"/><Initialization range="0-99"/>
                        </SegmentBase>
                    """,
                ),
            ),
        )
        // A live manifest, as before.
        assertEquals(
            unsupported,
            video(manifest().replace("type=\"static\"", "type=\"dynamic\"")),
        )
    }

    @Test
    fun `the file comes in ranges without its index asked apart, with its length known`() =
        runBlocking {
            val video = mp4File(VIDEO_INIT, VIDEO_INDEX, VIDEO_SIZE)
            files.serve("/media/stream.mpd", manifest().toByteArray())
            files.serve("/media/video-360.mp4", video)
            val out = files("whole")
            val progress = Collections.synchronizedList(mutableListOf<DownloadProgress>())

            val result = engine().transfer(
                plan = plan("whole", "v360", MediaTrackType.VIDEO),
                destination = out.destination,
                onProgress = { progress += it },
            ) as DashTransferResult.Completed

            assertArrayEquals(video, out.completed.readBytes())
            assertEquals(VIDEO_SIZE.toLong(), result.bytesWritten)
            assertEquals(listOf("/media/stream.mpd none"), files.requestsOf("/media/stream.mpd"))
            // The first small range, then equal ones; the sidx (700-799) is never asked apart.
            assertEquals(
                listOf("bytes=0-399", "bytes=400-1399", "bytes=1400-2399", "bytes=2400-2999"),
                files.rangesOf("/media/video-360.mp4"),
            )
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.all { it.totalBytes == VIDEO_SIZE.toLong() })
            assertEquals(VIDEO_SIZE.toLong(), progress.last().downloadedBytes)
        }

    @Test
    fun `a resume keeps the ranges done while the file and its manifest stay the same`() =
        runBlocking {
            val video = mp4File(VIDEO_INIT, VIDEO_INDEX, VIDEO_SIZE)
            files.serve("/media/stream.mpd", manifest().toByteArray())
            files.serve("/media/video-360.mp4", video)
            files.failFrom("/media/video-360.mp4", 1_400)
            val out = files("resume")
            val plan = plan("resume", "v360", MediaTrackType.VIDEO)

            val before = engine(maxAttempts = 1).transfer(plan, out.destination)
                as DashTransferResult.Failure
            assertEquals(2, before.checkpoint.completedChunkCount)

            files.clear()
            val resumed = engine().transfer(plan, out.destination, resumeFrom = before.checkpoint)
                as DashTransferResult.Completed

            assertArrayEquals(video, out.completed.readBytes())
            // One byte for the length, then only the ranges not done.
            assertEquals(
                listOf("bytes=0-0", "bytes=1400-2399", "bytes=2400-2999"),
                files.rangesOf("/media/video-360.mp4"),
            )
            assertEquals(before.checkpoint.manifestFingerprint, resumed.checkpoint.manifestFingerprint)
        }

    @Test
    fun `a file of another length or a manifest of other ranges starts over`() = runBlocking {
        files.serve("/media/stream.mpd", manifest().toByteArray())
        files.serve("/media/video-360.mp4", mp4File(VIDEO_INIT, VIDEO_INDEX, VIDEO_SIZE))
        files.failFrom("/media/video-360.mp4", 1_400)
        val out = files("changed")
        val plan = plan("changed", "v360", MediaTrackType.VIDEO)
        val before = engine(maxAttempts = 1).transfer(plan, out.destination)
            as DashTransferResult.Failure
        assertEquals(2, before.checkpoint.completedChunkCount)

        // The server now has a longer file under the same name.
        val longer = mp4File(VIDEO_INIT, VIDEO_INDEX, 3_200, salt = 7)
        files.clear()
        files.serve("/media/video-360.mp4", longer)
        val again = engine().transfer(plan, out.destination, resumeFrom = before.checkpoint)
            as DashTransferResult.Completed
        assertArrayEquals(longer, out.completed.readBytes())
        assertEquals(
            listOf("bytes=0-0", "bytes=0-399", "bytes=400-1399", "bytes=1400-2399", "bytes=2400-3199"),
            files.rangesOf("/media/video-360.mp4"),
        )
        assertTrue(before.checkpoint.manifestFingerprint != again.checkpoint.manifestFingerprint)

        // Same length, but the manifest now places the index elsewhere: not the same file.
        val second = files("moved")
        val movedPlan = plan("moved", "v360", MediaTrackType.VIDEO)
        files.clear()
        files.serve("/media/video-360.mp4", mp4File(VIDEO_INIT, VIDEO_INDEX, VIDEO_SIZE))
        files.failFrom("/media/video-360.mp4", 1_400)
        val stopped = engine(maxAttempts = 1).transfer(movedPlan, second.destination)
            as DashTransferResult.Failure
        files.clear()
        files.serve(
            "/media/stream.mpd",
            manifest(videoSegmentBase = moved()).toByteArray(),
        )
        engine().transfer(movedPlan, second.destination, resumeFrom = stopped.checkpoint)
            as DashTransferResult.Completed
        assertEquals(
            listOf("bytes=0-0", "bytes=0-399", "bytes=400-1399", "bytes=1400-2399", "bytes=2400-2999"),
            files.rangesOf("/media/video-360.mp4"),
        )
    }

    @Test
    fun `a file shorter than its manifest's index is no download`() = runBlocking {
        files.serve("/media/stream.mpd", manifest().toByteArray())
        files.serve("/media/video-360.mp4", fileBytes(750))
        val out = files("short")

        val result = engine().transfer(
            plan = plan("short", "v360", MediaTrackType.VIDEO),
            destination = out.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.INTEGRITY_MISMATCH, result.failure.reason)
        assertEquals(DownloadFailureStage.VERIFY, result.failure.stage)
        assertEquals(
            "the file is 750 bytes; its manifest's index ends at byte 799",
            result.failure.detail,
        )
        assertEquals(listOf("bytes=0-399"), files.rangesOf("/media/video-360.mp4"))
        assertFalse(out.completed.exists())
        assertEquals(0, result.checkpoint.completedChunkCount)

        // Without an index, its initialization is the part named.
        files.clear()
        files.serve(
            "/media/stream.mpd",
            manifest(videoSegmentBase = """<SegmentBase><Initialization range="0-899"/></SegmentBase>""")
                .toByteArray(),
        )
        val initialization = engine().transfer(
            plan = plan("short-init", "v360", MediaTrackType.VIDEO),
            destination = files("short-init").destination,
        ) as DashTransferResult.Failure
        assertEquals(
            "the file is 750 bytes; its manifest's initialization ends at byte 899",
            initialization.failure.detail,
        )
    }

    @Test
    fun `a video representation and its manifest's sound are merged as today`() = runBlocking {
        val video = mp4File(VIDEO_INIT, VIDEO_INDEX, VIDEO_SIZE)
        val audio = mp4File(AUDIO_INIT, AUDIO_INDEX, AUDIO_SIZE, salt = 3)
        files.serve("/media/stream.mpd", manifest().toByteArray())
        files.serve("/media/video-360.mp4", video)
        files.serve("/media/audio-en.m4a", audio)
        val muxer = RecordingMuxer()
        val out = files("merged")

        val result = AudioVideoMuxEngine(
            dashTransfer = engine(),
            muxer = muxer,
            workspaceRoot = directory.newFolder("merge-workspace"),
            clock = { 100 },
            sdkInt = 24,
        ).transfer(
            plan = AudioVideoMuxDownloadPlan(
                taskId = "merged",
                video = plan("merged-video", "v360", MediaTrackType.VIDEO),
                audio = plan("merged-audio", "a128", MediaTrackType.AUDIO).copy(
                    mimeType = "audio/mp4",
                    codecs = listOf("mp4a.40.2"),
                    suggestedFileName = "merged.audio.m4a",
                ),
                suggestedFileName = "merged.mp4",
            ),
            destination = out.destination,
        )

        assertTrue(result.toString(), result is AudioVideoMuxResult.Completed)
        assertArrayEquals(video, muxer.video)
        assertArrayEquals(audio, muxer.audio)
        assertArrayEquals(muxer.output, out.completed.readBytes())
        // Each file in ranges; the manifest read once for each track.
        assertEquals(2, files.requestsOf("/media/stream.mpd").size)
        assertEquals(
            listOf("bytes=0-399", "bytes=400-1399", "bytes=1400-1499"),
            files.rangesOf("/media/audio-en.m4a"),
        )
    }

    private fun parse(
        manifest: String,
        representationId: String,
        trackType: MediaTrackType,
    ): DashDownloadManifestParser.Result = DashDownloadManifestParser.parse(
        manifest = manifest,
        manifestUrl = "$BASE/media/stream.mpd".toHttpUrl(),
        representationId = representationId,
        expectedTrackType = trackType,
        maxChunks = 100,
    )

    private fun engine(maxAttempts: Int = 3): DashTransferEngine = DashTransferEngine(
        client = OkHttpClient(),
        workspaceRoot = workspaceRoot,
        policy = DashTransferEngine.Policy(
            maxAttempts = maxAttempts,
            initialRetryDelayMillis = 0,
            maxChunkBytes = 1_000,
            maxConcurrentChunks = 1,
            bufferBytes = 1_024,
            firstRangeBytes = 400,
            progressIntervalMillis = 0,
        ),
        clock = { 100 },
        log = {},
    )

    private fun plan(id: String, representationId: String, trackType: MediaTrackType) =
        DashDownloadPlan(
            taskId = "one-file-$id",
            manifestUrl = server.url("/media/stream.mpd").toString(),
            representationId = representationId,
            trackType = trackType,
            suggestedFileName = "$id.mp4",
            requestContext = BrowserRequestContext(
                pageUrl = "https://page.example.test/watch",
                userAgent = "YFT one-file fixture",
                cookie = null,
            ),
            mimeType = "video/mp4",
            codecs = listOf("avc1.4d401e"),
        )

    private fun files(name: String): Files {
        val parent = directory.newFolder("out-$name")
        val partial = File(parent, "$name.mp4.part")
        val completed = File(parent, "$name.mp4")
        return Files(completed, FileDownloadDestination(partial, completed))
    }

    private class Files(val completed: File, val destination: FileDownloadDestination)

    /** The manifest's index moved by one box: same file length, another layout. */
    private fun moved(): String =
        """<SegmentBase indexRange="710-799"><Initialization range="0-709"/></SegmentBase>"""

    /**
     * A made-up on-demand manifest: one video representation and one sound, each one file with
     * its initialization (`ftyp` and `moov`) and its index (`sidx`) as byte ranges.
     */
    private fun manifest(
        videoBaseUrl: String = "<BaseURL>video-360.mp4</BaseURL>",
        videoInitialization: String = """range="0-${VIDEO_INIT - 1}"""",
        videoSegmentBase: String = """
            <SegmentBase indexRange="$VIDEO_INIT-${VIDEO_INIT + VIDEO_INDEX - 1}">
              <Initialization $videoInitialization/>
            </SegmentBase>
        """,
    ): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
            mediaPresentationDuration="PT10S"
            profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
          <Period>
            <AdaptationSet mimeType="video/mp4" codecs="avc1.4d401e">
              <Representation id="v360" bandwidth="400000" width="640" height="360">
                $videoBaseUrl
                $videoSegmentBase
              </Representation>
            </AdaptationSet>
            <AdaptationSet mimeType="audio/mp4" codecs="mp4a.40.2" lang="en">
              <SegmentBase indexRange="$AUDIO_INIT-${AUDIO_INIT + AUDIO_INDEX - 1}">
                <Initialization range="0-${AUDIO_INIT - 1}"/>
              </SegmentBase>
              <Representation id="a128" bandwidth="128000">
                <BaseURL>audio-en.m4a?k=1</BaseURL>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    /**
     * A made-up fragmented MP4 of [size] bytes: `ftyp` and `moov` ([initBytes], the
     * initialization), `sidx` ([indexBytes], the index), then `moof` and `mdat`.
     */
    private fun mp4File(initBytes: Int, indexBytes: Int, size: Int, salt: Int = 0): ByteArray {
        val media = size - initBytes - indexBytes
        return box("ftyp", 24, salt) + box("moov", initBytes - 24, salt) +
            box("sidx", indexBytes, salt) + box("moof", 200, salt) + box("mdat", media - 200, salt)
    }

    private fun box(type: String, size: Int, salt: Int): ByteArray {
        val buffer = ByteBuffer.allocate(size).putInt(size).put(type.toByteArray(Charsets.US_ASCII))
        var value = salt
        while (buffer.hasRemaining()) {
            buffer.put((value % 251).toByte())
            value += 1
        }
        return buffer.array()
    }

    /** The two track files the merge got, and what it wrote. */
    private class RecordingMuxer : LocalAudioVideoMuxer {
        var video = ByteArray(0)
        var audio = ByteArray(0)
        var output = ByteArray(0)

        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult {
            video = videoFile.readBytes()
            audio = audioFile.readBytes()
            output = "mux[".toByteArray() + video + "|".toByteArray() + audio + "]".toByteArray()
            outputFile.writeBytes(output)
            return LocalMuxResult.Completed(output.size.toLong())
        }
    }

    /**
     * Serves the manifest whole and each file in byte ranges (206 with Content-Range), and fails
     * a file's ranges from [failFrom] with 500 (its one-byte length request aside).
     */
    private class OneFileDispatcher : Dispatcher() {
        private val served = Collections.synchronizedMap(mutableMapOf<String, ByteArray>())
        private val failing = Collections.synchronizedMap(mutableMapOf<String, Long>())
        private val received = Collections.synchronizedList(mutableListOf<Pair<String, String>>())

        fun serve(path: String, bytes: ByteArray) {
            served[path] = bytes
        }

        fun failFrom(path: String, start: Long) {
            failing[path] = start
        }

        /** Forgets the requests and the failures, keeping the files. */
        fun clear() {
            received.clear()
            failing.clear()
        }

        fun requestsOf(path: String): List<String> = synchronized(received) {
            received.filter { it.first == path }.map { "${it.first} ${it.second}" }
        }

        fun rangesOf(path: String): List<String> = synchronized(received) {
            received.filter { it.first == path }.map { it.second }
        }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath.orEmpty()
            val header = request.getHeader("Range")
            received += path to (header ?: "none")
            val bytes = served[path] ?: return MockResponse().setResponseCode(404)
            val match = header?.let(RANGE::matchEntire)
                ?: return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", if (path.endsWith(".mpd")) MPD else "video/mp4")
                    .setBody(Buffer().write(bytes))
            val start = match.groupValues[1].toLong()
            val end = minOf(match.groupValues[2].toLong(), bytes.size - 1L)
            val failFrom = failing[path]
            if (failFrom != null && start >= failFrom && header != "bytes=0-0") {
                return MockResponse().setResponseCode(500)
            }
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                .setBody(Buffer().write(bytes, start.toInt(), (end - start + 1).toInt()))
        }

        private companion object {
            val RANGE = Regex("""bytes=(\d+)-(\d+)""")
            const val MPD = "application/dash+xml"
        }
    }

    private companion object {
        const val BASE = "https://media.example.test"
        const val VIDEO_INIT = 700
        const val VIDEO_INDEX = 100
        const val VIDEO_SIZE = 3_000
        const val AUDIO_INIT = 500
        const val AUDIO_INDEX = 80
        const val AUDIO_SIZE = 1_500
    }
}
