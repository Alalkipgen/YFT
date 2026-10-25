package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.PublishedFile
import com.alal.yft.core.model.media.BrowserReadAnswer
import com.alal.yft.core.model.media.BrowserReadRequest
import com.alal.yft.core.model.media.BrowserReads
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HlsTransferEngineTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var workspaceRoot: File
    private lateinit var engine: HlsTransferEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        workspaceRoot = directory.newFolder("hls-workspace")
        engine = engine()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `selected VOD chunks publish in manifest order and clean workspace`() = runTest {
        val first = "first-".repeat(40).toByteArray()
        val second = "second-".repeat(30).toByteArray()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/track.m3u8" -> manifestResponse(vodManifest("one.ts", "two.ts"))
                "/one.ts" -> bytesResponse(first)
                    .setBodyDelay(100, TimeUnit.MILLISECONDS)
                "/two.ts" -> bytesResponse(second)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val files = files("ordered.ts")

        val result = engine.transfer(
            plan = plan(server.url("/track.m3u8").toString(), "ordered"),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertEquals((first.size + second.size).toLong(), result.bytesWritten)
        assertEquals(2, result.checkpoint.completedChunkCount)
        assertArrayEquals(first + second, files.completed.readBytes())
        assertFalse(files.partial.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
        val requests = List(server.requestCount) { server.takeRequest() }
        assertEquals(setOf("/track.m3u8", "/one.ts", "/two.ts"), requests.map { it.path }.toSet())
        assertTrue(
            requests
                .filter { it.path != "/track.m3u8" }
                .all { it.getHeader("Cookie") == "session=fixture" },
        )
    }

    @Test
    fun `init map and byte ranges assemble exact selected bytes`() = runTest {
        val source = ByteArray(16) { it.toByte() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/ranged.m3u8") {
                    return manifestResponse(
                        """
                        #EXTM3U
                        #EXT-X-VERSION:7
                        #EXT-X-TARGETDURATION:4
                        #EXT-X-MAP:URI="media.mp4",BYTERANGE="4@0"
                        #EXTINF:4,
                        #EXT-X-BYTERANGE:6@4
                        media.mp4
                        #EXTINF:4,
                        #EXT-X-BYTERANGE:6
                        media.mp4
                        #EXT-X-ENDLIST
                        """.trimIndent(),
                    )
                }
                if (request.path != "/media.mp4") return MockResponse().setResponseCode(404)
                val range = requireNotNull(request.getHeader("Range"))
                val match = RANGE.matchEntire(range)
                    ?: return MockResponse().setResponseCode(400)
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${source.size}")
                    .setHeader("Content-Length", end - start + 1)
                    .setBody(Buffer().write(source.copyOfRange(start, end + 1)))
            }
        }
        val files = files("ranged.mp4")

        val result = engine.transfer(
            plan = plan(server.url("/ranged.m3u8").toString(), "ranged"),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertEquals(source.size.toLong(), result.bytesWritten)
        assertArrayEquals(source, files.completed.readBytes())
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `DRM playlist fails before segment request and removes workspace`() = runTest {
        server.enqueue(
            manifestResponse(
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:4
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:4,
                encrypted.ts
                #EXT-X-ENDLIST
                """.trimIndent(),
            ),
        )
        val files = files("drm.ts")

        val result = engine.transfer(
            plan = plan(server.url("/drm.m3u8").toString(), "drm"),
            destination = files.destination,
        ) as HlsTransferResult.Failure

        assertEquals(DownloadFailureReason.DRM_PROTECTED, result.failure.reason)
        assertEquals(1, server.requestCount)
        assertFalse(files.completed.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `retryable failure checkpoint resumes without downloading completed chunk again`() =
        runTest {
            val oneRequests = AtomicInteger()
            val twoRequests = AtomicInteger()
            val first = "one".repeat(100).toByteArray()
            val second = "two".repeat(80).toByteArray()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/resume.m3u8" -> manifestResponse(vodManifest("one.ts", "two.ts"))
                    "/one.ts" -> {
                        oneRequests.incrementAndGet()
                        bytesResponse(first)
                    }
                    "/two.ts" -> {
                        if (twoRequests.incrementAndGet() == 1) {
                            MockResponse().setResponseCode(503)
                        } else {
                            bytesResponse(second)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val singleAttemptEngine = engine(maxAttempts = 1, maxConcurrentChunks = 1)
            val files = files("resume.ts")
            val plan = plan(server.url("/resume.m3u8").toString(), "resume")

            val firstResult = singleAttemptEngine.transfer(
                plan = plan,
                destination = files.destination,
            ) as HlsTransferResult.Failure

            assertEquals(DownloadFailureReason.SERVER_ERROR, firstResult.failure.reason)
            assertEquals(1, firstResult.checkpoint.completedChunkCount)
            assertEquals(1, oneRequests.get())
            assertTrue(workspaceRoot.listFiles().orEmpty().single().isDirectory)

            val resumed = singleAttemptEngine.transfer(
                plan = plan,
                destination = files.destination,
                resumeFrom = firstResult.checkpoint,
            ) as HlsTransferResult.Completed

            assertEquals(2, resumed.checkpoint.completedChunkCount)
            assertEquals(1, oneRequests.get())
            assertEquals(2, twoRequests.get())
            assertArrayEquals(first + second, files.completed.readBytes())
            assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `discard removes resumable chunk workspace`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/discard.m3u8" -> manifestResponse(vodManifest("one.ts", "two.ts"))
                "/one.ts" -> bytesResponse("one".toByteArray())
                "/two.ts" -> MockResponse().setResponseCode(503)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val singleAttemptEngine = engine(maxAttempts = 1, maxConcurrentChunks = 1)
        val plan = plan(server.url("/discard.m3u8").toString(), "discard")

        val result = singleAttemptEngine.transfer(
            plan = plan,
            destination = files("discard.ts").destination,
        ) as HlsTransferResult.Failure
        assertEquals(1, result.checkpoint.completedChunkCount)
        assertFalse(workspaceRoot.listFiles().orEmpty().isEmpty())

        singleAttemptEngine.discard(plan)

        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `expired plan performs no network or storage work`() = runTest {
        val files = files("expired.ts")

        val result = engine.transfer(
            plan = plan(
                url = server.url("/expired.m3u8").toString(),
                id = "expired",
                expiresAtEpochMs = 99,
            ),
            destination = files.destination,
        ) as HlsTransferResult.Failure

        assertEquals(DownloadFailureReason.EXPIRED_URL, result.failure.reason)
        assertEquals(0, server.requestCount)
        assertFalse(files.partial.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a playlist the site refused is read by the browser and its pieces downloaded`() =
        runTest {
            // P45: the CDN refused YFT's request of the playlist (HTTP 410), not the browser's.
            val piece = "piece-".repeat(20).toByteArray()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/refused.m3u8" -> MockResponse().setResponseCode(410)
                        "/one.ts" -> bytesResponse(piece)
                        else -> MockResponse().setResponseCode(404)
                    }
            }
            val asked = mutableListOf<BrowserReadRequest>()
            val browser = HlsTransferEngine(
                client = OkHttpClient(),
                workspaceRoot = workspaceRoot,
                policy = HlsTransferEngine.Policy(initialRetryDelayMillis = 0),
                clock = { 100 },
                browserReads = { request ->
                    asked += request
                    BrowserReadAnswer(status = 200, text = vodManifest("one.ts"))
                },
            )
            val files = files("refused.ts")
            val url = server.url("/refused.m3u8").toString()

            val result = browser.transfer(plan(url, "refused"), files.destination)

            assertTrue(result.toString(), result is HlsTransferResult.Completed)
            assertArrayEquals(piece, files.completed.readBytes())
            assertEquals(url, asked.single().url)
            assertEquals("https://page.example.test/watch", asked.single().pageUrl)
            assertTrue(asked.single().wantsText)

            // Without the browser's answer the refusal stands.
            val refused = engine.transfer(plan(url, "refused-2"), files("refused-2.ts").destination)
            assertTrue(refused.toString(), refused is HlsTransferResult.Failure)
        }

    @Test
    fun `packed audio pieces lose their ID3 tags so the AAC stream runs to the end`() = runTest {
        val frames = List(3) { piece -> adtsFrames(piece) }
        val pieces = listOf(
            id3Tag(major = 3, body = 63) + frames[0],
            id3Tag(major = 3, body = 63) + frames[1],
            id3Tag(major = 4, body = 20, footer = true) + frames[2],
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/audio.m3u8" -> manifestResponse(vodManifest("a.aac", "b.aac", "c.aac"))
                "/a.aac" -> bytesResponse(pieces[0])
                "/b.aac" -> bytesResponse(pieces[1])
                "/c.aac" -> bytesResponse(pieces[2])
                else -> MockResponse().setResponseCode(404)
            }
        }
        val files = files("packed.aac")

        val result = engine.transfer(
            plan = plan(server.url("/audio.m3u8").toString(), "packed"),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        val expected = frames[0] + frames[1] + frames[2]
        assertArrayEquals(expected, files.completed.readBytes())
        assertEquals(expected.size.toLong(), result.bytesWritten)
    }

    @Test
    fun `an ID3 tag that is not followed by ADTS frames is written as it is`() = runTest {
        val piece = id3Tag(major = 3, body = 63) + "not-adts-".repeat(10).toByteArray()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/track.m3u8" -> manifestResponse(vodManifest("one.ts", "two.ts"))
                "/one.ts", "/two.ts" -> bytesResponse(piece)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val files = files("tagged.ts")

        val result = engine.transfer(
            plan = plan(server.url("/track.m3u8").toString(), "tagged"),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertArrayEquals(piece + piece, files.completed.readBytes())
        assertEquals((piece.size * 2).toLong(), result.bytesWritten)
    }

    @Test
    fun `D1 MPEG-TS pieces planned as an MP4 are remuxed into a real MP4`() = runTest {
        serve("/av.m3u8", "av-0.ts", "av-1.ts")
        val files = files("clip.mp4")

        val result = engine.transfer(
            plan = plan(server.url("/av.m3u8").toString(), "clip", remuxToMp4 = true),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertNull(result.publishedAs)
        val bytes = files.completed.readBytes()
        assertEquals(bytes.size.toLong(), result.bytesWritten)
        assertEquals(listOf("ftyp", "mdat", "moov"), Mp4Dump(bytes).top.map { it.type })
        assertEquals(listOf("vide", "soun"), Mp4Dump(bytes).tracks.map { it.handler })
        assertEquals(2, result.checkpoint.completedChunkCount)
        assertFalse(files.partial.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `D1 the sound alone of an HLS download is remuxed into an M4A`() = runTest {
        serve("/sound.m3u8", "audio-0.ts", "audio-1.ts")
        val files = files("sound.m4a")

        val result = engine.transfer(
            plan = plan(
                server.url("/sound.m3u8").toString(),
                "sound",
                remuxToMp4 = true,
                mimeType = "audio/mp4",
            ),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertNull(result.publishedAs)
        val bytes = files.completed.readBytes()
        assertEquals("M4A ", String(bytes, 8, 4, Charsets.ISO_8859_1))
        assertEquals(listOf("soun"), Mp4Dump(bytes).tracks.map { it.handler })
    }

    @Test
    fun `D1 MPEG-TS that cannot be remuxed is kept as ts never under the MP4's name`() = runTest {
        serve("/old.m3u8", "mpeg2.ts")
        val files = files("clip.mp4")

        val result = engine.transfer(
            plan = plan(server.url("/old.m3u8").toString(), "clip", remuxToMp4 = true),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertEquals(PublishedFile("clip.ts", "video/mp2t"), result.publishedAs)
        val kept = File(files.completed.parentFile, "clip.ts")
        assertArrayEquals(HlsFixtures.bytes("mpeg2.ts"), kept.readBytes())
        assertEquals(kept.length(), result.bytesWritten)
        assertFalse(files.completed.exists())
        assertFalse(files.partial.exists())
    }

    @Test
    fun `D1 a remux that fails halfway leaves exactly the MPEG-TS bytes`() = runTest {
        serve("/av.m3u8", "av-0.ts", "av-1.ts")
        val files = files("clip.mp4")
        val halfway = object : HlsRemuxer {
            override suspend fun remux(
                pieces: List<HlsPiece>,
                kind: HlsMediaKind,
                audioOnly: Boolean,
                output: SeekableDownloadOutput,
            ): Long {
                output.write(0, ByteArray(60_000) { 0x55 }, 0, 60_000)
                throw StreamCopyUnsupported("fixture")
            }
        }

        val result = engine(remuxer = halfway).transfer(
            plan = plan(server.url("/av.m3u8").toString(), "clip", remuxToMp4 = true),
            destination = files.destination,
        ) as HlsTransferResult.Completed

        assertEquals(PublishedFile("clip.ts", "video/mp2t"), result.publishedAs)
        val joined = HlsFixtures.bytes("av-0.ts") + HlsFixtures.bytes("av-1.ts")
        assertArrayEquals(joined, File(files.completed.parentFile, "clip.ts").readBytes())
    }

    @Test
    fun `D1 a storage error while remuxing fails the download and publishes nothing`() = runTest {
        serve("/av.m3u8", "av-0.ts", "av-1.ts")
        val files = files("clip.mp4")
        val full = object : HlsRemuxer {
            override suspend fun remux(
                pieces: List<HlsPiece>,
                kind: HlsMediaKind,
                audioOnly: Boolean,
                output: SeekableDownloadOutput,
            ): Long = throw IOException("No space left on device")
        }

        val result = engine(remuxer = full).transfer(
            plan = plan(server.url("/av.m3u8").toString(), "clip", remuxToMp4 = true),
            destination = files.destination,
        )

        assertTrue(result.toString(), result is HlsTransferResult.Failure)
        assertFalse(files.completed.exists())
        assertFalse(File(files.completed.parentFile, "clip.ts").exists())
    }

    @Test
    fun `D1 fragmented MP4 and plain joins are not remuxed`() = runTest {
        val fragmented = Mp4TestFiles.fragmented(Mp4TestFiles.video(fragments = 2))
        val transport = HlsFixtures.bytes("av-0.ts")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/fmp4.m3u8" -> manifestResponse(vodManifest("whole.mp4"))
                "/ts.m3u8" -> manifestResponse(vodManifest("one.ts"))
                "/whole.mp4" -> bytesResponse(fragmented)
                "/one.ts" -> bytesResponse(transport)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val never = object : HlsRemuxer {
            override suspend fun remux(
                pieces: List<HlsPiece>,
                kind: HlsMediaKind,
                audioOnly: Boolean,
                output: SeekableDownloadOutput,
            ): Long = error("Not remuxed")
        }
        val mp4 = files("whole.mp4")
        val plain = files("plain.ts")

        val first = engine(remuxer = never).transfer(
            plan = plan(server.url("/fmp4.m3u8").toString(), "whole", remuxToMp4 = true),
            destination = mp4.destination,
        ) as HlsTransferResult.Completed
        // Without remuxToMp4 (a merge's track, an older plan) MPEG-TS is joined as before.
        val second = engine(remuxer = never).transfer(
            plan = plan(server.url("/ts.m3u8").toString(), "plain"),
            destination = plain.destination,
        ) as HlsTransferResult.Completed

        assertNull(first.publishedAs)
        assertArrayEquals(fragmented, mp4.completed.readBytes())
        assertNull(second.publishedAs)
        assertArrayEquals(transport, plain.completed.readBytes())
    }

    @Test
    fun `D1 packed MP3 sound is kept as mp3 and packed AAC remuxed into an M4A`() = runTest {
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64) + ByteArray(413)
        // MP3 pieces are joined whole, as before D1: only ID3 tags before ADTS are dropped.
        val mp3Piece = id3Tag(major = 4, body = 20) + mp3
        val aac = Mp4TestFiles.adts(Mp4TestFiles.audio(fragments = 1, perFragment = 8))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/mp3.m3u8" -> manifestResponse(vodManifest("one.mp3", "two.mp3"))
                "/aac.m3u8" -> manifestResponse(vodManifest("one.aac"))
                "/one.mp3", "/two.mp3" -> bytesResponse(mp3Piece)
                "/one.aac" -> bytesResponse(id3Tag(major = 4, body = 20) + aac)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val song = files("song.m4a")
        val sound = files("sound.m4a")

        val kept = engine.transfer(
            plan = plan(
                server.url("/mp3.m3u8").toString(),
                "song",
                remuxToMp4 = true,
                mimeType = "audio/mp4",
            ),
            destination = song.destination,
        ) as HlsTransferResult.Completed
        val remuxed = engine.transfer(
            plan = plan(
                server.url("/aac.m3u8").toString(),
                "sound",
                remuxToMp4 = true,
                mimeType = "audio/mp4",
            ),
            destination = sound.destination,
        ) as HlsTransferResult.Completed

        assertEquals(PublishedFile("song.mp3", "audio/mpeg"), kept.publishedAs)
        assertArrayEquals(
            mp3Piece + mp3Piece,
            File(song.completed.parentFile, "song.mp3").readBytes(),
        )
        assertNull(remuxed.publishedAs)
        val bytes = sound.completed.readBytes()
        assertEquals("M4A ", String(bytes, 8, 4, Charsets.ISO_8859_1))
        assertEquals(8, Mp4Dump(bytes).tracks.single().samples.size)
    }

    /** D1: a VOD playlist at [path] of the [pieces] ([HlsFixtures]), served by name. */
    private fun serve(path: String, vararg pieces: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == path) return manifestResponse(vodManifest(*pieces))
                val name = request.path?.removePrefix("/")
                return if (name in pieces) {
                    bytesResponse(HlsFixtures.bytes(requireNotNull(name)))
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun engine(
        maxAttempts: Int = 3,
        maxConcurrentChunks: Int = 2,
    ): HlsTransferEngine = HlsTransferEngine(
        client = OkHttpClient(),
        workspaceRoot = workspaceRoot,
        policy = HlsTransferEngine.Policy(
            maxAttempts = maxAttempts,
            initialRetryDelayMillis = 0,
            maxConcurrentChunks = maxConcurrentChunks,
            bufferBytes = 1_024,
        ),
        clock = { 100 },
    )

    /** D1: an engine whose remux is [remuxer]. */
    private fun engine(remuxer: HlsRemuxer): HlsTransferEngine = HlsTransferEngine(
        client = OkHttpClient(),
        workspaceRoot = workspaceRoot,
        policy = HlsTransferEngine.Policy(initialRetryDelayMillis = 0, bufferBytes = 1_024),
        clock = { 100 },
        browserReads = BrowserReads.None,
        remuxer = remuxer,
    )

    private fun plan(
        url: String,
        id: String,
        expiresAtEpochMs: Long? = null,
        remuxToMp4: Boolean = false,
        mimeType: String = if (remuxToMp4) "video/mp4" else "video/mp2t",
    ): HlsDownloadPlan = HlsDownloadPlan(
        taskId = "hls-$id",
        playlistUrl = url,
        suggestedFileName = if (remuxToMp4) "$id.mp4" else "$id.ts",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT HLS fixture",
            cookie = "session=fixture",
        ),
        mimeType = mimeType,
        expiresAtEpochMs = expiresAtEpochMs,
        remuxToMp4 = remuxToMp4,
    )

    private fun files(name: String): DestinationFiles {
        val outputDirectory = directory.newFolder("output-${name.substringBefore('.')}")
        val partial = File(outputDirectory, "$name.part")
        val completed = File(outputDirectory, name)
        return DestinationFiles(
            partial = partial,
            completed = completed,
            destination = FileDownloadDestination(partial, completed),
        )
    }

    private fun vodManifest(vararg chunks: String): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-TARGETDURATION:4")
        chunks.forEach { chunk ->
            appendLine("#EXTINF:4,")
            appendLine(chunk)
        }
        append("#EXT-X-ENDLIST")
    }

    private fun id3Tag(major: Int, body: Int, footer: Boolean = false): ByteArray {
        val header = byteArrayOf(
            'I'.code.toByte(),
            'D'.code.toByte(),
            '3'.code.toByte(),
            major.toByte(),
            0,
            (if (footer) 0x10 else 0).toByte(),
            (body shr 21 and 0x7F).toByte(),
            (body shr 14 and 0x7F).toByte(),
            (body shr 7 and 0x7F).toByte(),
            (body and 0x7F).toByte(),
        )
        val tail = if (footer) {
            byteArrayOf('3'.code.toByte(), 'D'.code.toByte(), 'I'.code.toByte()) +
                header.copyOfRange(3, 10)
        } else {
            ByteArray(0)
        }
        return header + ByteArray(body) { 0x50 } + tail
    }

    private fun adtsFrames(seed: Int): ByteArray = (0 until 4).fold(ByteArray(0)) { all, frame ->
        all + byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()) +
            ByteArray(12) { (seed * 16 + frame).toByte() }
    }

    private fun manifestResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/vnd.apple.mpegurl")
        .setBody(body)

    private fun bytesResponse(bytes: ByteArray): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "video/mp2t")
        .setBody(Buffer().write(bytes))

    private data class DestinationFiles(
        val partial: File,
        val completed: File,
        val destination: FileDownloadDestination,
    )

    private companion object {
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}
