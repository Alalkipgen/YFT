package com.alal.yft.core.download

import com.alal.yft.core.download.RangeFileDispatcher.Throttle
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P41: a YouTube-like whole file shows progress from its first bytes. The server is local and
 * slowed down per answer, like a VPN; the measured times are printed for the report.
 */
class DashTransferStartTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var workspaceRoot: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        workspaceRoot = directory.newFolder("dash-start")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `progress moves within one second on a 64 KB per second server`() = runBlocking {
        val ranges = RangeFileDispatcher(fileBytes(3 * MIB)) { Throttle(64L * 1_024, 1_000) }
        server.dispatcher = ranges

        val start = measureStart(plan("slow"), waitMillis = 3_000)

        println("P41 64 KB/s: ${start.describe()}; requests ${ranges.ranges()}")
        assertNotNull("no progress within 3 s", start.firstProgressMillis)
        assertTrue(start.describe(), start.firstProgressMillis!! < 1_000)
    }

    @Test
    fun `progress moves within one second on a VPN-like 1 MB per second server`() =
        runBlocking {
            // 30 MiB, so today's 10 MiB ranges end together: the first % comes after 10 MiB.
            val ranges = RangeFileDispatcher(fileBytes(30 * MIB)) { Throttle(256L * 1_024, 250) }
            server.dispatcher = ranges

            val start = measureStart(plan("vpn"), waitMillis = 15_000)

            println("P41 1 MB/s: ${start.describe()}; requests ${ranges.ranges()}")
            assertNotNull("no progress within 15 s", start.firstProgressMillis)
            assertTrue(start.describe(), start.firstProgressMillis!! < 1_000)
        }

    @Test
    fun `one slow range does not hold back the others`() = runBlocking {
        val bytes = fileBytes(6_000)
        server.dispatcher = RangeFileDispatcher(bytes) { start ->
            Throttle(10, 1_000).takeIf { start == 1_000L }
        }
        val completed = CompletableDeferred<Long>()
        val startedAt = System.nanoTime()
        val job = launch(Dispatchers.IO) {
            engine(maxConcurrentChunks = 2).transfer(
                plan = plan("barrier", totalBytes = 6_000, maxRequestBytes = 1_000),
                destination = files("barrier.mp4").destination,
                onCheckpoint = { checkpoint ->
                    if (checkpoint.completedChunkCount >= 5) {
                        completed.complete((System.nanoTime() - startedAt) / 1_000_000)
                    }
                },
            )
        }

        val at = withTimeoutOrNull(3_000) { completed.await() }
        job.cancelAndJoin()

        println("P41 slow range: the other 5 ranges done after ${at ?: "more than 3000"} ms")
        assertNotNull("the slow range held the others back for 3 s", at)
    }

    @Test
    fun `the first range of an unknown length is 1 MiB and gives the length`() = runBlocking {
        val bytes = fileBytes(11 * MIB + 5)
        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        val files = files("first.mp4")
        val progress = Collections.synchronizedList(mutableListOf<DownloadProgress>())

        val result = engine().transfer(
            plan = plan("first"),
            destination = files.destination,
            onProgress = { progress += it },
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals("bytes=0-1048575", ranges.ranges().first())
        assertEquals(
            setOf("bytes=0-1048575", "bytes=1048576-11534335", "bytes=11534336-11534340"),
            ranges.ranges().toSet(),
        )
        assertEquals(3, ranges.ranges().size)
        assertEquals(3, result.checkpoint.completedChunkCount)
        assertEquals(bytes.size.toLong(), progress.last().downloadedBytes)
        assertEquals(bytes.size.toLong(), progress.last().totalBytes)
    }

    @Test
    fun `a short file of unknown length takes one request`() = runBlocking {
        val bytes = fileBytes(300 * 1_024)
        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        val files = files("short.mp4")

        engine().transfer(
            plan = plan("short"),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(listOf("bytes=0-1048575"), ranges.ranges())
    }

    @Test
    fun `a stated length needs no request for it`() = runBlocking {
        val bytes = fileBytes(3 * MIB)
        val ranges = RangeFileDispatcher(bytes)
        server.dispatcher = ranges
        val files = files("known.mp4")

        engine().transfer(
            plan = plan("known", totalBytes = bytes.size.toLong()),
            destination = files.destination,
        ) as DashTransferResult.Completed

        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(setOf("bytes=0-1048575", "bytes=1048576-3145727"), ranges.ranges().toSet())
        assertEquals(2, ranges.ranges().size)
    }

    @Test
    fun `progress never goes back, ends at the total and comes at most 4 times a second`() =
        runBlocking {
            val bytes = fileBytes(MIB)
            server.dispatcher = RangeFileDispatcher(bytes) { Throttle(64L * 1_024, 250) }
            val files = files("pace.mp4")
            val startedAt = System.nanoTime()
            val events = Collections.synchronizedList(
                mutableListOf<Pair<Long, DownloadProgress>>(),
            )

            engine().transfer(
                plan = plan(
                    id = "pace",
                    totalBytes = bytes.size.toLong(),
                    maxRequestBytes = 128L * 1_024,
                ),
                destination = files.destination,
                onProgress = { events += (System.nanoTime() - startedAt) / 1_000_000 to it },
            ) as DashTransferResult.Completed

            assertArrayEquals(bytes, files.completed.readBytes())
            val progress = events.map { it.second }
            assertTrue(
                progress.zipWithNext().all { (a, b) -> b.downloadedBytes >= a.downloadedBytes },
            )
            assertTrue(progress.all { it.downloadedBytes <= bytes.size })
            assertTrue(progress.all { it.totalBytes == bytes.size.toLong() })
            assertEquals(bytes.size.toLong(), progress.last().downloadedBytes)
            // Between the first update (the start) and the last (the end): 4 a second at most.
            val times = events.map { it.first }.drop(1).dropLast(1)
            val busiestSecond = times.maxOfOrNull { from ->
                times.count { it in from until from + 1_000 }
            }
            println("P41 pace: ${events.size} updates in ${events.last().first} ms")
            assertTrue("$busiestSecond updates in one second", (busiestSecond ?: 0) <= 4)
            assertTrue(
                "no update inside a range",
                progress.any { it.downloadedBytes % (128 * 1_024) != 0L },
            )
        }

    @Test
    fun `a retried range is counted once`() = runBlocking {
        val bytes = fileBytes(2_000)
        val ranges = RangeFileDispatcher(
            bytes = bytes,
            cutOnce = { start -> start == 0L },
            throttle = { start -> Throttle(100, 300).takeIf { start == 0L } },
        )
        server.dispatcher = ranges
        val files = files("retry.mp4")
        val progress = Collections.synchronizedList(mutableListOf<DownloadProgress>())

        val result = engine(maxConcurrentChunks = 1).transfer(
            plan = plan("retry", totalBytes = 2_000, maxRequestBytes = 1_000),
            destination = files.destination,
            onProgress = { progress += it },
        ) as DashTransferResult.Completed

        assertEquals(2_000L, result.bytesWritten)
        assertArrayEquals(bytes, files.completed.readBytes())
        assertEquals(2, ranges.ranges().count { it == "bytes=0-999" })
        assertTrue(progress.zipWithNext().all { (a, b) -> b.downloadedBytes >= a.downloadedBytes })
        assertTrue(progress.all { it.downloadedBytes <= 2_000 })
        assertEquals(2_000L, progress.last().downloadedBytes)
    }

    private data class StartMeasure(
        val lengthMillis: Long?,
        val firstProgressMillis: Long?,
    ) {
        fun describe(): String =
            "length known after ${lengthMillis ?: "-"} ms, " +
                "first progress after ${firstProgressMillis ?: "-"} ms"
    }

    /** Starts [plan], waits up to [waitMillis] for its first progress, then stops it. */
    private suspend fun CoroutineScope.measureStart(
        plan: DashDownloadPlan,
        waitMillis: Long,
    ): StartMeasure {
        val startedAt = System.nanoTime()
        val since = { (System.nanoTime() - startedAt) / 1_000_000 }
        val firstProgress = CompletableDeferred<Long>()
        var lengthAt: Long? = null
        val job = launch(Dispatchers.IO) {
            engine().transfer(
                plan = plan,
                destination = files("${plan.representationId}.mp4").destination,
                onProgress = { progress ->
                    if (progress.totalBytes != null && lengthAt == null) lengthAt = since()
                    if (progress.downloadedBytes > 0) firstProgress.complete(since())
                },
            )
        }
        val at = withTimeoutOrNull(waitMillis) { firstProgress.await() }
        job.cancelAndJoin()
        return StartMeasure(lengthMillis = lengthAt, firstProgressMillis = at)
    }

    private fun engine(maxConcurrentChunks: Int = 3): DashTransferEngine = DashTransferEngine(
        client = OkHttpClient(),
        workspaceRoot = workspaceRoot,
        policy = DashTransferEngine.Policy(
            initialRetryDelayMillis = 0,
            maxConcurrentChunks = maxConcurrentChunks,
        ),
        clock = { 100 },
    )

    private fun plan(
        id: String,
        totalBytes: Long? = null,
        maxRequestBytes: Long = WholeFileTrack.DEFAULT_MAX_REQUEST_BYTES,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = "start-$id-${PLAN_IDS.incrementAndGet()}",
        manifestUrl = server.url("/video.mp4?sig=fixture").toString(),
        representationId = id,
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT start fixture",
            cookie = null,
        ),
        mimeType = "video/mp4",
        codecs = listOf("avc1.4d401f"),
        wholeFile = WholeFileTrack(totalBytes = totalBytes, maxRequestBytes = maxRequestBytes),
    )

    private fun files(name: String): Files {
        val parent = directory.newFolder("out-${name.substringBefore('.')}-${PLAN_IDS.get()}")
        val partial = File(parent, "$name.part")
        val completed = File(parent, name)
        return Files(completed, FileDownloadDestination(partial, completed))
    }

    private class Files(val completed: File, val destination: FileDownloadDestination)

    private companion object {
        val PLAN_IDS = AtomicInteger()
    }
}
