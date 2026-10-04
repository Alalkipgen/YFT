package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureReason
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
        assertEquals(
            listOf("bytes=0-0", "bytes=0-999", "bytes=1000-1999", "bytes=2000-2499"),
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

    private fun wholeFileDispatcher(
        bytes: ByteArray,
        failFrom: Long? = null,
        ignoreRanges: Boolean = false,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.requestUrl?.encodedPath != "/video.mp4") {
                return MockResponse().setResponseCode(404)
            }
            val match = request.getHeader("Range")?.let(RANGE::matchEntire)
            if (ignoreRanges || match == null) return bytesResponse(bytes)
            val start = match.groupValues[1].toInt()
            val end = minOf(match.groupValues[2].toInt(), bytes.lastIndex)
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
