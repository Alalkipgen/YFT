package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashTransferEngineTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var workspaceRoot: File
    private lateinit var engine: DashTransferEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        workspaceRoot = directory.newFolder("dash-workspace")
        engine = engine()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `selected representation chunks publish in manifest order and clean workspace`() = runTest {
        val first = "first-".repeat(40).toByteArray()
        val second = "second-".repeat(30).toByteArray()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/manifest.mpd" -> manifestResponse(segmentListManifest("one.m4s", "two.m4s"))
                "/one.m4s" -> bytesResponse(first)
                    .setBodyDelay(100, TimeUnit.MILLISECONDS)
                "/two.m4s" -> bytesResponse(second)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val files = files("ordered.mp4")

        val result = engine.transfer(
            plan = plan(server.url("/manifest.mpd").toString(), "ordered"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertEquals((first.size + second.size).toLong(), result.bytesWritten)
        assertEquals(2, result.checkpoint.completedChunkCount)
        assertArrayEquals(first + second, files.completed.readBytes())
        assertFalse(files.partial.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
        val requests = List(server.requestCount) { server.takeRequest() }
        assertEquals(
            setOf("/manifest.mpd", "/one.m4s", "/two.m4s"),
            requests.map { it.path }.toSet(),
        )
        assertTrue(
            requests
                .filter { it.path != "/manifest.mpd" }
                .all { it.getHeader("Cookie") == "session=fixture" },
        )
    }

    @Test
    fun `SegmentList initialization and byte ranges assemble exact selected bytes`() = runTest {
        val source = ByteArray(16) { it.toByte() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/ranged.mpd") {
                    return manifestResponse(
                        """
                        <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
                          <Period>
                            <AdaptationSet contentType="video" mimeType="video/mp4">
                              <Representation id="v1">
                                <BaseURL>media.mp4</BaseURL>
                                <SegmentList>
                                  <Initialization range="0-3" />
                                  <SegmentURL mediaRange="4-9" />
                                  <SegmentURL mediaRange="10-15" />
                                </SegmentList>
                              </Representation>
                            </AdaptationSet>
                          </Period>
                        </MPD>
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
            plan = plan(server.url("/ranged.mpd").toString(), "ranged"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertEquals(source.size.toLong(), result.bytesWritten)
        assertArrayEquals(source, files.completed.readBytes())
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `DRM manifest fails before segment request and removes workspace`() = runTest {
        server.enqueue(
            manifestResponse(
                """
                <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
                  <Period>
                    <AdaptationSet contentType="video" mimeType="video/mp4">
                      <ContentProtection schemeIdUri="urn:uuid:fixture" />
                      <Representation id="v1"><BaseURL>encrypted.mp4</BaseURL></Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
                """.trimIndent(),
            ),
        )
        val files = files("drm.mp4")

        val result = engine.transfer(
            plan = plan(server.url("/drm.mpd").toString(), "drm"),
            destination = files.destination,
        ) as DashTransferResult.Failure

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
                    "/resume.mpd" -> manifestResponse(
                        segmentListManifest("one.m4s", "two.m4s"),
                    )
                    "/one.m4s" -> {
                        oneRequests.incrementAndGet()
                        bytesResponse(first)
                    }
                    "/two.m4s" -> {
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
            val files = files("resume.mp4")
            val plan = plan(server.url("/resume.mpd").toString(), "resume")

            val firstResult = singleAttemptEngine.transfer(
                plan = plan,
                destination = files.destination,
            ) as DashTransferResult.Failure

            assertEquals(DownloadFailureReason.SERVER_ERROR, firstResult.failure.reason)
            assertEquals(1, firstResult.checkpoint.completedChunkCount)
            assertEquals(1, oneRequests.get())
            assertTrue(workspaceRoot.listFiles().orEmpty().single().isDirectory)

            val resumed = singleAttemptEngine.transfer(
                plan = plan,
                destination = files.destination,
                resumeFrom = firstResult.checkpoint,
            ) as DashTransferResult.Completed

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
                "/discard.mpd" -> manifestResponse(
                    segmentListManifest("one.m4s", "two.m4s"),
                )
                "/one.m4s" -> bytesResponse("one".toByteArray())
                "/two.m4s" -> MockResponse().setResponseCode(503)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val singleAttemptEngine = engine(maxAttempts = 1, maxConcurrentChunks = 1)
        val plan = plan(server.url("/discard.mpd").toString(), "discard")

        val result = singleAttemptEngine.transfer(
            plan = plan,
            destination = files("discard.mp4").destination,
        ) as DashTransferResult.Failure
        assertEquals(1, result.checkpoint.completedChunkCount)
        assertFalse(workspaceRoot.listFiles().orEmpty().isEmpty())

        singleAttemptEngine.discard(plan)

        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `expired plan performs no network or storage work`() = runTest {
        val files = files("expired.mp4")

        val result = engine.transfer(
            plan = plan(
                url = server.url("/expired.mpd").toString(),
                id = "expired",
                expiresAtEpochMs = 99,
            ),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.EXPIRED_URL, result.failure.reason)
        assertEquals(0, server.requestCount)
        assertFalse(files.partial.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `whole file track downloads in bounded ranges and reports its length`() = runTest {
        val bytes = ByteArray(2_500) { index -> (index % 251).toByte() }
        server.dispatcher = wholeFileDispatcher(bytes)
        val files = files("whole.mp4")
        val progress = mutableListOf<DownloadProgress>()

        val result = engine(maxConcurrentChunks = 1).transfer(
            plan = wholeFilePlan(server.url("/video.mp4?sig=one").toString(), "whole"),
            destination = files.destination,
            onProgress = { progress += it },
        ) as DashTransferResult.Completed

        assertEquals(2_500L, result.bytesWritten)
        assertEquals(3, result.checkpoint.completedChunkCount)
        assertArrayEquals(bytes, files.completed.readBytes())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
        val ranges = List(server.requestCount) { server.takeRequest().getHeader("Range") }
        // FAST_START (P41): the first range states the length, so there is no bytes=0-0 probe.
        assertEquals(
            listOf("bytes=0-999", "bytes=1000-1999", "bytes=2000-2499"),
            ranges,
        )
        assertTrue(progress.all { it.totalBytes == 2_500L })
        assertEquals(2_500L, progress.last().downloadedBytes)
    }

    @Test
    fun `whole file track with a stated length skips the length request`() = runTest {
        val bytes = ByteArray(1_500) { index -> (index % 13).toByte() }
        server.dispatcher = wholeFileDispatcher(bytes)
        val files = files("stated.mp4")

        val result = engine().transfer(
            plan = wholeFilePlan(
                url = server.url("/video.mp4").toString(),
                id = "stated",
                totalBytes = 1_500,
            ),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertEquals(1_500L, result.bytesWritten)
        assertArrayEquals(bytes, files.completed.readBytes())
        val ranges = List(server.requestCount) { server.takeRequest().getHeader("Range") }
        assertEquals(setOf("bytes=0-999", "bytes=1000-1499"), ranges.toSet())
    }

    @Test
    fun `whole file server that ignores ranges is refused`() = runTest {
        server.dispatcher = wholeFileDispatcher(ByteArray(2_000), ignoreRanges = true)
        val files = files("ignored.mp4")

        val result = engine().transfer(
            plan = wholeFilePlan(server.url("/video.mp4").toString(), "ignored"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.UNSUPPORTED_SOURCE, result.failure.reason)
        assertEquals(1, server.requestCount)
        assertFalse(files.completed.exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `whole file track resumes finished ranges from a refreshed address`() = runTest {
        val bytes = ByteArray(2_500) { index -> (index % 97).toByte() }
        server.dispatcher = wholeFileDispatcher(bytes, failFrom = 2_000)
        val files = files("resume-whole.mp4")
        val singleAttempt = engine(maxAttempts = 1, maxConcurrentChunks = 1)

        val first = singleAttempt.transfer(
            plan = wholeFilePlan(server.url("/video.mp4?sig=old").toString(), "resume-whole"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.SERVER_ERROR, first.failure.reason)
        assertEquals(2, first.checkpoint.completedChunkCount)
        val firstRunRequests = server.requestCount
        repeat(firstRunRequests) { server.takeRequest() }

        server.dispatcher = wholeFileDispatcher(bytes)
        val resumed = singleAttempt.transfer(
            plan = wholeFilePlan(server.url("/video.mp4?sig=new").toString(), "resume-whole"),
            destination = files.destination,
            resumeFrom = first.checkpoint,
        ) as DashTransferResult.Completed

        assertEquals(2_500L, resumed.bytesWritten)
        assertArrayEquals(bytes, files.completed.readBytes())
        val ranges = List(server.requestCount - firstRunRequests) {
            server.takeRequest().getHeader("Range")
        }
        assertEquals(listOf("bytes=0-0", "bytes=2000-2499"), ranges)
    }

    @Test
    fun `a live stream's pieces are asked from sq 0 to its head and joined into one track`() =
        runTest {
            val (track, pieces) = livePieces()
            server.dispatcher = livePieceDispatcher(pieces)
            val files = files("live.mp4")
            val progress = mutableListOf<DownloadProgress>()

            val result = engine().transfer(
                plan = livePlan("live", expectedDurationMillis = 1_500),
                destination = files.destination,
                onProgress = { progress += it },
            ) as DashTransferResult.Completed

            assertEquals(files.completed.length(), result.bytesWritten)
            assertSamples(track.samples, files.completed)
            val first = server.takeRequest()
            assertEquals("HEAD", first.method)
            assertEquals(null, first.requestUrl?.queryParameter("sq"))
            val asked = List(server.requestCount - 1) {
                server.takeRequest().requestUrl?.queryParameter("sq").orEmpty()
            }
            // Piece 0 alone first, then the rest; every piece once, with no Range.
            assertEquals("0", asked.first())
            assertEquals(listOf("0", "1", "2", "3"), asked.sorted())
            assertTrue(progress.all { it.totalBytes == null })
        }

    @Test
    fun `a live stream whose first piece starts no fragmented MP4 is not ready yet`() = runTest {
        val (_, pieces) = livePieces()
        val broken = listOf(Mp4TestFiles.box("mdat", ByteArray(100))) + pieces.drop(1)
        server.dispatcher = livePieceDispatcher(broken)
        val files = files("early.mp4")

        val result = engine().transfer(
            plan = livePlan("early"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.LIVE_NOT_READY, result.failure.reason)
        assertEquals("live: sq 0 has no moof", result.failure.detail)
        // The HEAD and piece 0, nothing more.
        assertEquals(2, server.requestCount)
        assertFalse(files.completed.exists())
    }

    @Test
    fun `a live stream whose server names no head piece is not ready yet`() = runTest {
        val (_, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces, head = null)
        val files = files("headless.mp4")

        val result = engine().transfer(
            plan = livePlan("headless"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.LIVE_NOT_READY, result.failure.reason)
        assertEquals("live head: 200, no X-Head-Seqnum", result.failure.detail)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a live stream much shorter than its video is not ready yet`() = runTest {
        val (_, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces)
        val files = files("short-live.mp4")

        val result = engine().transfer(
            plan = livePlan("short-live", expectedDurationMillis = 10_000),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.LIVE_NOT_READY, result.failure.reason)
        assertEquals("live 0h00m00s of 0h00m10s · 4/4 pieces", result.failure.detail)
        assertFalse(files.completed.exists())
    }

    @Test
    fun `a missing newest piece ends the live file before it`() = runTest {
        val (track, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces) { sq, _ -> 404.takeIf { sq == 3 } }
        val files = files("tail.mp4")

        engine().transfer(
            plan = livePlan("tail"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertSamples(track.fragments.take(3).flatten(), files.completed)
    }

    @Test
    fun `a missing piece in the middle fails the live file and names it`() = runTest {
        val (_, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces) { sq, _ -> 404.takeIf { sq == 1 } }
        val files = files("hole.mp4")

        val result = engine().transfer(
            plan = livePlan("hole"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.NOT_FOUND, result.failure.reason)
        assertEquals(404, result.failure.httpStatusCode)
        assertTrue(
            result.failure.detail.orEmpty(),
            result.failure.detail.orEmpty().startsWith("live sq 1/3 · 404 "),
        )
        assertFalse(files.completed.exists())
    }

    @Test
    fun `a live piece refused or rate limited for a while is asked again`() = runTest {
        val (track, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces) { sq, ask ->
            when {
                sq == 1 && ask == 1 -> 429
                sq == 2 && ask <= 2 -> 403
                else -> null
            }
        }
        val files = files("again.mp4")

        engine().transfer(
            plan = livePlan("again"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertSamples(track.samples, files.completed)
    }

    @Test
    fun `a live piece refused every time fails as access denied`() = runTest {
        val (_, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces) { sq, _ -> 403.takeIf { sq == 1 } }
        val files = files("refused.mp4")

        val result = engine().transfer(
            plan = livePlan("refused"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.ACCESS_DENIED, result.failure.reason)
        assertTrue(
            result.failure.detail.orEmpty(),
            result.failure.detail.orEmpty().startsWith("live sq 1/3 · 403 "),
        )
        assertFalse(files.completed.exists())
    }

    @Test
    fun `live WebM pieces are not joined and nothing is asked`() = runTest {
        val (_, pieces) = livePieces()
        server.dispatcher = livePieceDispatcher(pieces)
        val files = files("live.webm")

        val result = engine().transfer(
            plan = livePlan("webm").copy(mimeType = "video/webm", codecs = listOf("vp9")),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.UNSUPPORTED_SOURCE, result.failure.reason)
        assertEquals(
            "live WebM pieces are not joined; pick an MP4 quality",
            result.failure.detail,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a live piece whose tfhd names where its data is keeps it after the join`() = runTest {
        val track = Mp4TestFiles.video(fragments = 3, perFragment = 4)
        var time = 0L
        val pieces = track.fragments.mapIndexed { index, samples ->
            val header = Mp4TestFiles.header(track) + Mp4TestFiles.box("emsg", ByteArray(index * 7))
            val piece = header + absoluteMoof(index + 1, time, samples, header.size.toLong()) +
                Mp4TestFiles.box("mdat", samples.fold(ByteArray(0)) { all, it -> all + it.bytes })
            time += samples.sumOf { it.duration.toLong() }
            piece
        }
        server.dispatcher = livePieceDispatcher(pieces)
        val files = files("absolute.mp4")

        engine().transfer(
            plan = livePlan("absolute"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertSamples(track.samples, files.completed)
    }

    @Test
    fun `a normal whole file with the live server's headers fails and names them`() = runTest {
        val bytes = ByteArray(2_500) { index -> (index % 251).toByte() }
        server.dispatcher = liveOriginDispatcher(bytes)
        val files = files("liar.mp4")

        val result = engine(maxConcurrentChunks = 1).transfer(
            plan = wholeFilePlan(server.url("/video.mp4").toString(), "liar"),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.INTEGRITY_MISMATCH, result.failure.reason)
        assertEquals(DownloadFailureStage.CONNECT, result.failure.stage)
        // As on the owner's phone: a total of one small piece, the whole asked length.
        assertEquals(
            "asked bytes 0-399, got bytes 0-399/400, length 1000",
            result.failure.detail,
        )
        assertFalse(files.completed.exists())
    }

    @Test
    fun `a normal whole file that answers short fails and names what was asked and what came`() =
        runTest {
            val bytes = ByteArray(2_500) { index -> (index % 251).toByte() }
            server.dispatcher = wholeFileDispatcher(bytes, answerBytes = 400)
            val files = files("short.mp4")

            val result = engine(maxConcurrentChunks = 1).transfer(
                plan = wholeFilePlan(server.url("/video.mp4?source=youtube").toString(), "short"),
                destination = files.destination,
            ) as DashTransferResult.Failure

            assertEquals(DownloadFailureReason.INTEGRITY_MISMATCH, result.failure.reason)
            assertEquals(DownloadFailureStage.CONNECT, result.failure.stage)
            assertEquals(
                "asked bytes 0-999, got bytes 0-399/2500, length 400",
                result.failure.detail,
            )
            assertFalse(files.completed.exists())
        }

    @Test
    fun `a normal whole file whose stated length is wrong still fails`() = runTest {
        val bytes = ByteArray(1_500) { index -> (index % 13).toByte() }
        server.dispatcher = wholeFileDispatcher(bytes)
        val files = files("stale.mp4")

        val result = engine(maxConcurrentChunks = 1).transfer(
            plan = wholeFilePlan(server.url("/video.mp4").toString(), "stale", totalBytes = 1_800),
            destination = files.destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.INTEGRITY_MISMATCH, result.failure.reason)
        assertEquals(DownloadFailureStage.CONNECT, result.failure.stage)
        assertEquals(
            "asked bytes 1000-1799, got bytes 1000-1499/1500, length 500",
            result.failure.detail,
        )
        assertFalse(files.completed.exists())
    }

    /** A live stream of [count] pieces, each a fragmented MP4 with its own ftyp and moov. */
    private fun livePieces(count: Int = 4): Pair<Mp4TestFiles.Track, List<ByteArray>> {
        val track = Mp4TestFiles.video(fragments = count, perFragment = 5)
        var time = 0L
        val pieces = track.fragments.mapIndexed { index, samples ->
            val data = samples.fold(ByteArray(0)) { all, sample -> all + sample.bytes }
            val piece = Mp4TestFiles.header(track) +
                Mp4TestFiles.moof(track, index + 1, time, samples) +
                Mp4TestFiles.box("mdat", data)
            time += samples.sumOf { it.duration.toLong() }
            piece
        }
        return track to pieces
    }

    /** A moof whose tfhd names its data's place in the piece: [at], where the moof is. */
    private fun absoluteMoof(
        sequence: Int,
        decodeTime: Long,
        samples: List<Mp4TestFiles.Sample>,
        at: Long,
    ): ByteArray {
        val entries = java.nio.ByteBuffer.allocate(samples.size * 16)
        samples.forEach { sample ->
            entries.putInt(sample.duration)
            entries.putInt(sample.size)
            entries.putInt(if (sample.sync) 0x02000000 else 0x01010000)
            entries.putInt(sample.compositionOffset)
        }
        val tfhd = Mp4TestFiles.fullBox(
            "tfhd",
            0,
            0x000001,
            Mp4TestFiles.int(1),
            Mp4TestFiles.long(at),
        )
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

    /** [file] holds one video track with [samples], in order, and nothing else. */
    private fun assertSamples(samples: List<Mp4TestFiles.Sample>, file: File) {
        java.io.RandomAccessFile(file, "r").use { input ->
            val source = FileChannelMp4Source(input.channel)
            val track = Mp4TrackReader(source, StreamCopyTrackKind.VIDEO).read()
            assertEquals(samples.size, track.count)
            samples.forEachIndexed { index, sample ->
                val bytes = java.nio.ByteBuffer.allocate(track.sizes[index])
                source.readFully(bytes, track.offsets[index])
                assertArrayEquals("sample $index", sample.bytes, bytes.array())
                assertEquals(sample.duration, track.durations[index])
            }
        }
    }

    private fun livePlan(id: String, expectedDurationMillis: Long? = null): DashDownloadPlan =
        wholeFilePlan(server.url("/video.mp4?source=yt_live_broadcast").toString(), id)
            .copy(expectedDurationMillis = expectedDurationMillis)

    /**
     * A live stream's server as yt-dlp and the owner's phone see it: a HEAD of the address names
     * the newest piece (`X-Head-Seqnum`, none when [head] is null) and `sq=N` sends piece N.
     * [status] answers the [ask]th request for a piece with an error code instead.
     */
    private fun livePieceDispatcher(
        pieces: List<ByteArray>,
        head: Int? = pieces.lastIndex,
        status: (sq: Int, ask: Int) -> Int? = { _, _ -> null },
    ): Dispatcher = object : Dispatcher() {
        private val asks = java.util.concurrent.ConcurrentHashMap<Int, AtomicInteger>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val response = MockResponse().setHeader("Content-Type", "video/mp4")
            head?.let { response.setHeader("X-Head-Seqnum", it) }
            if (request.method == "HEAD") return response.setResponseCode(200)
            if (request.getHeader("Range") != null) return MockResponse().setResponseCode(400)
            val sq = request.requestUrl?.queryParameter("sq")?.toIntOrNull()
                ?: return MockResponse().setResponseCode(400)
            val ask = asks.getOrPut(sq) { AtomicInteger() }.incrementAndGet()
            status(sq, ask)?.let { code ->
                return response.setResponseCode(code)
                    .setHeader("Content-Type", "text/plain")
                    .setHeader("Retry-After", "0")
            }
            val bytes = pieces.getOrNull(sq) ?: return response.setResponseCode(404)
            return response.setResponseCode(200)
                .setHeader("X-Sequence-Num", sq)
                .setBody(Buffer().write(bytes))
        }
    }

    /**
     * A live-origin server as the phone results suggest: an answer holds at most one 400-byte
     * piece and its Content-Range is true (`bytes S-E/E+1`), but its Content-Length is the whole
     * ask and after the piece it sends nothing more; 416 past the end. [stallAt]: the first
     * answer there sends 200 bytes and stops. [forbidFrom]: 403 from there on.
     */
    private fun liveOriginDispatcher(
        bytes: ByteArray,
        stallAt: Int? = null,
        forbidFrom: Int? = null,
    ): Dispatcher = object : Dispatcher() {
        private val stalled = java.util.concurrent.atomic.AtomicBoolean(false)

        override fun dispatch(request: RecordedRequest): MockResponse {
            val match = request.getHeader("Range")?.let(RANGE::matchEntire)
                ?: return MockResponse().setResponseCode(400)
            val start = match.groupValues[1].toInt()
            if (forbidFrom != null && start >= forbidFrom) {
                return MockResponse().setResponseCode(403)
            }
            if (start > bytes.lastIndex) {
                return MockResponse()
                    .setResponseCode(416)
                    .setHeader("Content-Range", "bytes */${bytes.size}")
            }
            val end = minOf(start + 399, bytes.lastIndex)
            val response = MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(bytes.copyOfRange(start, end + 1)))
                .setHeader("Content-Range", "bytes $start-$end/${end + 1}")
                .setHeader("Content-Length", match.groupValues[2].toInt() - start + 1)
            if (start == stallAt && stalled.compareAndSet(false, true)) {
                response.throttleBody(200, 1, TimeUnit.SECONDS)
            }
            return response
        }
    }

    private fun wholeFileDispatcher(
        bytes: ByteArray,
        failFrom: Long? = null,
        ignoreRanges: Boolean = false,
        answerBytes: Int? = null,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.requestUrl?.encodedPath != "/video.mp4") {
                return MockResponse().setResponseCode(404)
            }
            val match = request.getHeader("Range")?.let(RANGE::matchEntire)
            if (ignoreRanges || match == null) return bytesResponse(bytes)
            val start = match.groupValues[1].toInt()
            val asked = minOf(match.groupValues[2].toInt(), bytes.lastIndex)
            // A live-origin server may stop short of the asked end.
            val end = answerBytes?.let { minOf(asked, start + it - 1) } ?: asked
            if (failFrom != null && start >= failFrom) {
                return MockResponse().setResponseCode(500)
            }
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                .setBody(Buffer().write(bytes, start, end - start + 1))
        }
    }

    private fun wholeFilePlan(
        url: String,
        id: String,
        totalBytes: Long? = null,
    ): DashDownloadPlan = plan(url, id).copy(
        representationId = "video",
        wholeFile = WholeFileTrack(totalBytes = totalBytes, maxRequestBytes = 1_000),
    )

    private fun engine(
        maxAttempts: Int = 3,
        maxConcurrentChunks: Int = 2,
    ): DashTransferEngine = DashTransferEngine(
        client = OkHttpClient(),
        workspaceRoot = workspaceRoot,
        policy = DashTransferEngine.Policy(
            maxAttempts = maxAttempts,
            initialRetryDelayMillis = 0,
            maxConcurrentChunks = maxConcurrentChunks,
            bufferBytes = 1_024,
        ),
        clock = { 100 },
    )

    private fun plan(
        url: String,
        id: String,
        expiresAtEpochMs: Long? = null,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = "dash-$id",
        manifestUrl = url,
        representationId = "v1",
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT DASH fixture",
            cookie = "session=fixture",
        ),
        mimeType = "video/mp4",
        codecs = listOf("avc1.4d401f"),
        expiresAtEpochMs = expiresAtEpochMs,
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

    private fun segmentListManifest(vararg chunks: String): String = """
        <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
          <Period>
            <AdaptationSet contentType="video" mimeType="video/mp4" codecs="avc1.4d401f">
              <Representation id="v1">
                <SegmentList>
                  ${chunks.joinToString(separator = "\n") { """<SegmentURL media="$it" />""" }}
                </SegmentList>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun manifestResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/dash+xml")
        .setBody(body)

    private fun bytesResponse(bytes: ByteArray): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "video/mp4")
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
