package com.alal.yft.core.download

import com.alal.yft.core.download.RangeFileDispatcher.Throttle
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** P41: the FAST_START file layout, its fingerprints, its switch and its start times. */
class DashFastStartLayoutTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var workspaceRoot: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        workspaceRoot = directory.newFolder("fast-start")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a checkpoint of the equal ranges of before resumes with them`() = runBlocking {
        val bytes = fileBytes(2_500)
        server.dispatcher = failingFrom(bytes, 2_000)
        val files = files("legacy")
        val before = engine(fastStart = false, maxAttempts = 1).transfer(
            plan = plan("legacy", totalBytes = 2_500),
            destination = files.destination,
        ) as DashTransferResult.Failure
        assertEquals(2, before.checkpoint.completedChunkCount)

        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        val resumed = engine(firstRangeBytes = 400).transfer(
            plan = plan("legacy", totalBytes = 2_500),
            destination = files.destination,
            resumeFrom = before.checkpoint,
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(listOf("bytes=2000-2499"), ranges.ranges())
        assertEquals(before.checkpoint.manifestFingerprint, resumed.checkpoint.manifestFingerprint)
    }

    @Test
    fun `a new download takes the small first range and its own fingerprint`() = runBlocking {
        val bytes = fileBytes(2_500)
        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        val files = files("fresh")

        val fresh = engine(firstRangeBytes = 400, maxConcurrentChunks = 1).transfer(
            plan = plan("fresh", totalBytes = 2_500),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(
            listOf("bytes=0-399", "bytes=400-1399", "bytes=1400-2399", "bytes=2400-2499"),
            ranges.ranges(),
        )
        val equal = engine(fastStart = false).transfer(
            plan = plan("equal", totalBytes = 2_500),
            destination = files("equal").destination,
        ) as DashTransferResult.Completed
        assertTrue(fresh.checkpoint.manifestFingerprint != equal.checkpoint.manifestFingerprint)
    }

    @Test
    fun `a new layout checkpoint resumes with the new layout`() = runBlocking {
        val bytes = fileBytes(2_500)
        server.dispatcher = failingFrom(bytes, 1_400)
        val files = files("again")
        val fast = engine(firstRangeBytes = 400, maxAttempts = 1, maxConcurrentChunks = 1)
        val before = fast.transfer(
            plan = plan("again", totalBytes = 2_500),
            destination = files.destination,
        ) as DashTransferResult.Failure
        assertEquals(2, before.checkpoint.completedChunkCount)

        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        fast.transfer(
            plan = plan("again", totalBytes = 2_500),
            destination = files.destination,
            resumeFrom = before.checkpoint,
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(listOf("bytes=1400-2399", "bytes=2400-2499"), ranges.ranges())
    }

    @Test
    fun `FAST_START off keeps the length request and the equal ranges`() = runBlocking {
        val bytes = fileBytes(2_500)
        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        val files = files("off")

        engine(fastStart = false, maxConcurrentChunks = 1).transfer(
            plan = plan("off"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(
            listOf("bytes=0-0", "bytes=0-999", "bytes=1000-1999", "bytes=2000-2499"),
            ranges.ranges(),
        )
    }

    @Test
    fun `YouTube media hosts take four ranges at once and other hosts three`() = runBlocking {
        server.dispatcher = RangeFileDispatcher(fileBytes(8_000)) { Throttle(10, 1_000) }
        val youTube = rangesAtOnce("https://rr1---sn-fixture.googlevideo.com/video.mp4")
        server.dispatcher = RangeFileDispatcher(fileBytes(8_000)) { Throttle(10, 1_000) }
        val other = rangesAtOnce("https://media.example.test/video.mp4")

        assertEquals(4, youTube)
        assertEquals(3, other)
    }

    @Test
    fun `start times go to the log and into a failure's details`() = runBlocking {
        val bytes = fileBytes(2_500)
        server.dispatcher = RangeFileDispatcher(bytes)
        val lines = Collections.synchronizedList(mutableListOf<String>())

        engine(log = { lines += it }).transfer(
            plan = plan("logged", totalBytes = 2_500),
            destination = files("logged").destination,
        ) as DashTransferResult.Completed

        assertEquals(1, lines.size)
        assertTrue(lines.single(), START_LINE.matches(lines.single()))
        assertTrue(lines.single(), "(known)" in lines.single())
        assertTrue(lines.none { "http" in it || "sig" in it || "127.0.0.1" in it })

        server.dispatcher = failingFrom(bytes, 0)
        val failed = engine(maxAttempts = 1).transfer(
            plan = plan("failed"),
            destination = files("failed").destination,
        ) as DashTransferResult.Failure
        assertEquals(DownloadFailureReason.SERVER_ERROR, failed.failure.reason)
        val timeline = failed.failure.startTimeline.orEmpty()
        assertTrue(timeline, timeline.startsWith("start: plan "))
        assertTrue(timeline, "first progress \u2013" in timeline)
    }

    /** How many ranges a whole file of eight asks for in its first second at [url]. */
    private suspend fun rangesAtOnce(url: String): Int = kotlinx.coroutines.coroutineScope {
        val ranges = server.dispatcher as RangeFileDispatcher
        val local = server.url("/video.mp4")
        // The file's address names the media host; the answers come from the local server.
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    chain.proceed(chain.request().newBuilder().url(local).build())
                },
            )
            .build()
        val engine = DashTransferEngine(
            client = client,
            workspaceRoot = workspaceRoot,
            policy = DashTransferEngine.Policy(initialRetryDelayMillis = 0),
            clock = { 100 },
        )
        val job = launch(Dispatchers.IO) {
            engine.transfer(
                plan = plan("hosts-${url.length}", totalBytes = 8_000).copy(manifestUrl = url),
                destination = files("hosts-${url.length}").destination,
            )
        }
        delay(1_500)
        val count = ranges.ranges().size
        job.cancelAndJoin()
        count
    }

    private fun failingFrom(bytes: ByteArray, failFrom: Long) = object : okhttp3.mockwebserver
        .Dispatcher() {
        private val ranges = RangeFileDispatcher(bytes)

        override fun dispatch(
            request: okhttp3.mockwebserver.RecordedRequest,
        ): okhttp3.mockwebserver.MockResponse {
            val start = request.getHeader("Range")
                ?.substringAfter("bytes=")
                ?.substringBefore('-')
                ?.toLongOrNull()
            if (start != null && start >= failFrom && request.getHeader("Range") != "bytes=0-0") {
                return okhttp3.mockwebserver.MockResponse().setResponseCode(500)
            }
            return ranges.dispatch(request)
        }
    }

    private fun engine(
        fastStart: Boolean = true,
        firstRangeBytes: Long = 1_024L * 1_024,
        maxAttempts: Int = 3,
        maxConcurrentChunks: Int = 2,
        log: (String) -> Unit = {},
    ): DashTransferEngine = DashTransferEngine(
        client = OkHttpClient(),
        workspaceRoot = workspaceRoot,
        policy = DashTransferEngine.Policy(
            maxAttempts = maxAttempts,
            initialRetryDelayMillis = 0,
            maxConcurrentChunks = maxConcurrentChunks,
            bufferBytes = 1_024,
            fastStart = fastStart,
            firstRangeBytes = firstRangeBytes,
        ),
        clock = { 100 },
        log = log,
    )

    private fun plan(id: String, totalBytes: Long? = null): DashDownloadPlan = DashDownloadPlan(
        taskId = "layout-$id",
        manifestUrl = server.url("/video.mp4?sig=fixture").toString(),
        representationId = "video",
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT layout fixture",
            cookie = null,
        ),
        mimeType = "video/mp4",
        codecs = listOf("avc1.4d401f"),
        wholeFile = WholeFileTrack(totalBytes = totalBytes, maxRequestBytes = 1_000),
    )

    private fun files(name: String): Files {
        val parent = directory.newFolder("out-$name")
        val partial = File(parent, "$name.mp4.part")
        val completed = File(parent, "$name.mp4")
        return Files(completed, FileDownloadDestination(partial, completed))
    }

    private class Files(val completed: File, val destination: FileDownloadDestination)

    private companion object {
        val START_LINE = Regex(
            "DASH start: plan \\d+\\.\\d s \u00b7 length \\d+\\.\\d s \\([a-z ]+\\) \u00b7 " +
                "first byte \\d+\\.\\d s \u00b7 first progress \\d+\\.\\d s",
        )
    }
}
