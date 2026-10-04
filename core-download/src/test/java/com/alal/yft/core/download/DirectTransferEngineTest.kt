package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectTransferEngineTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var engine: DirectTransferEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = DirectTransferEngine(
            client = OkHttpClient(),
            policy = DirectTransferEngine.Policy(
                maxAttempts = 3,
                initialRetryDelayMillis = 0,
                bufferBytes = 1_024,
                checkpointIntervalBytes = 1,
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `segmented ranges produce one verified completed file`() = runTest {
        val content = fixtureBytes(257)
        server.dispatcher = rangeDispatcher(content)
        val files = files("segmented.bin")

        val result = engine.transfer(
            plan = plan(server.url("/segmented.bin").toString(), content.size.toLong()),
            metadata = metadata(
                url = server.url("/segmented.bin").toString(),
                totalBytes = content.size.toLong(),
                supportsRanges = true,
            ),
            destination = files.destination,
        ) as DirectTransferResult.Completed

        assertEquals(content.size.toLong(), result.bytesWritten)
        assertArrayEquals(content, files.completed.readBytes())
        assertFalse(files.partial.exists())
        assertEquals(4, server.requestCount)
        repeat(server.requestCount) {
            val request = server.takeRequest()
            assertNotNull(request.getHeader("Range"))
            assertEquals(ETAG, request.getHeader("If-Range"))
        }
    }

    @Test
    fun `ignored range response resets partial work and falls back to one full GET`() = runTest {
        val content = fixtureBytes(96)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody(Buffer().write(content))
        }
        val files = files("fallback.bin")

        val result = engine.transfer(
            plan = plan(
                url = server.url("/fallback.bin").toString(),
                expectedBytes = content.size.toLong(),
                segmentCount = 3,
            ),
            metadata = metadata(
                url = server.url("/fallback.bin").toString(),
                totalBytes = content.size.toLong(),
                supportsRanges = true,
            ),
            destination = files.destination,
        )

        assertTrue(result is DirectTransferResult.Completed)
        assertArrayEquals(content, files.completed.readBytes())
        val requests = List(server.requestCount) { server.takeRequest() }
        assertTrue(requests.any { it.getHeader("Range") != null })
        assertTrue(requests.any { it.getHeader("Range") == null })
    }

    @Test
    fun `matching validator resumes only missing byte ranges`() = runTest {
        val content = fixtureBytes(20)
        server.dispatcher = rangeDispatcher(content)
        val files = files("resume.bin")
        files.partial.writeBytes(ByteArray(content.size).also { output ->
            content.copyInto(output, endIndex = 5)
        })
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = content.size.toLong(),
            entityTag = ETAG,
            lastModified = null,
            segments = listOf(
                DownloadSegment(0, 0, 9, downloadedBytes = 5),
                DownloadSegment(1, 10, 19, downloadedBytes = 0),
            ),
        )

        val result = engine.transfer(
            plan = plan(
                url = server.url("/resume.bin").toString(),
                expectedBytes = content.size.toLong(),
                segmentCount = 2,
            ),
            metadata = metadata(
                url = server.url("/resume.bin").toString(),
                totalBytes = content.size.toLong(),
                supportsRanges = true,
            ),
            destination = files.destination,
            resumeFrom = checkpoint,
        )

        assertTrue(result is DirectTransferResult.Completed)
        assertArrayEquals(content, files.completed.readBytes())
        val ranges = List(server.requestCount) {
            server.takeRequest().getHeader("Range")
        }.toSet()
        assertEquals(setOf("bytes=5-9", "bytes=10-19"), ranges)
    }

    @Test
    fun `cancellation checkpoints partial bytes without publishing them`() = runBlocking {
        val content = fixtureBytes(256 * 1_024)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(content))
                .throttleBody(1_024, 25, TimeUnit.MILLISECONDS),
        )
        val files = files("cancelled.bin")
        val progressStarted = CompletableDeferred<Unit>()
        val latestCheckpoint = AtomicReference<DirectTransferCheckpoint>()
        val transfer = async(start = CoroutineStart.UNDISPATCHED) {
            engine.transfer(
                plan = plan(
                    url = server.url("/cancelled.bin").toString(),
                    expectedBytes = content.size.toLong(),
                ),
                metadata = metadata(
                    url = server.url("/cancelled.bin").toString(),
                    totalBytes = content.size.toLong(),
                    supportsRanges = false,
                ),
                destination = files.destination,
                onProgress = {
                    if (it.downloadedBytes > 0) progressStarted.complete(Unit)
                },
                onCheckpoint =(latestCheckpoint::set),
            )
        }

        withTimeout(5_000) { progressStarted.await() }
        transfer.cancelAndJoin()

        assertFalse(files.completed.exists())
        val checkpoint = latestCheckpoint.get()
        assertNotNull(checkpoint)
        assertTrue(checkpoint.downloadedBytes in 1 until content.size.toLong())
        files.destination.discard()
        assertFalse(files.partial.exists())
    }

    @Test
    fun `short response fails integrity and never publishes`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("short"),
        )
        val files = files("short.bin")

        val result = engine.transfer(
            plan = plan(server.url("/short.bin").toString(), expectedBytes = 10),
            metadata = metadata(
                url = server.url("/short.bin").toString(),
                totalBytes = 10,
                supportsRanges = false,
            ),
            destination = files.destination,
        ) as DirectTransferResult.Failure

        assertEquals(DownloadFailureReason.INTEGRITY_MISMATCH, result.failure.reason)
        assertFalse(files.completed.exists())
        assertTrue(files.partial.exists())
    }

    @Test
    fun `retryable server error restarts no-range transfer`() = runTest {
        val content = fixtureBytes(32)
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(content)),
        )
        val files = files("retry.bin")

        val result = engine.transfer(
            plan = plan(
                url = server.url("/retry.bin").toString(),
                expectedBytes = content.size.toLong(),
            ),
            metadata = metadata(
                url = server.url("/retry.bin").toString(),
                totalBytes = content.size.toLong(),
                supportsRanges = false,
            ),
            destination = files.destination,
        )

        assertTrue(result is DirectTransferResult.Completed)
        assertArrayEquals(content, files.completed.readBytes())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `zero byte source commits without a network request`() = runTest {
        val files = files("empty.bin")

        val result = engine.transfer(
            plan = plan(server.url("/empty.bin").toString(), expectedBytes = 0),
            metadata = metadata(
                url = server.url("/empty.bin").toString(),
                totalBytes = 0,
                supportsRanges = true,
            ),
            destination = files.destination,
        ) as DirectTransferResult.Completed

        assertEquals(0L, result.bytesWritten)
        assertTrue(files.completed.isFile)
        assertEquals(0L, files.completed.length())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `write ENOSPC maps to insufficient storage`() = runTest {
        val content = fixtureBytes(16)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(content)),
        )
        val destination = object : DownloadDestination {
            override fun prepare(expectedLength: Long?) = Unit
            override fun temporaryLength(): Long? = null
            override fun open(): SeekableDownloadOutput = object : SeekableDownloadOutput {
                override fun write(
                    position: Long,
                    buffer: ByteArray,
                    offset: Int,
                    byteCount: Int,
                ) {
                    throw IOException("ENOSPC: No space left on device")
                }

                override fun setLength(length: Long) = Unit
                override fun sync() = Unit
                override fun close() = Unit
            }

            override fun commit() = error("must not commit")
            override fun discard() = Unit
        }

        val result = engine.transfer(
            plan = plan(
                url = server.url("/full.bin").toString(),
                expectedBytes = content.size.toLong(),
            ),
            metadata = metadata(
                url = server.url("/full.bin").toString(),
                totalBytes = content.size.toLong(),
                supportsRanges = false,
            ),
            destination = destination,
        ) as DirectTransferResult.Failure

        assertEquals(DownloadFailureReason.INSUFFICIENT_STORAGE, result.failure.reason)
    }

    @Test
    fun `preallocation ENOSPC maps to insufficient storage without requesting media`() = runTest {
        val destination = object : DownloadDestination {
            override fun prepare(expectedLength: Long?) {
                throw IOException("No space left on device")
            }

            override fun temporaryLength(): Long? = null
            override fun open(): SeekableDownloadOutput = error("must not open")
            override fun commit() = error("must not commit")
            override fun discard() = Unit
        }

        val result = engine.transfer(
            plan = plan(
                url = server.url("/preallocation-full.bin").toString(),
                expectedBytes = 16,
            ),
            metadata = metadata(
                url = server.url("/preallocation-full.bin").toString(),
                totalBytes = 16,
                supportsRanges = true,
            ),
            destination = destination,
        ) as DirectTransferResult.Failure

        assertEquals(DownloadFailureReason.INSUFFICIENT_STORAGE, result.failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `bounded requests split each segment into ranges of at most the cap`() = runTest {
        val content = fixtureBytes(204_800)
        server.dispatcher = rangeDispatcher(content)
        val files = files("bounded.bin")
        val url = server.url("/bounded.bin").toString()

        val result = engine.transfer(
            plan = plan(url, content.size.toLong(), segmentCount = 2)
                .copy(maxRequestBytes = 65_536),
            metadata = metadata(url, content.size.toLong(), supportsRanges = true),
            destination = files.destination,
        ) as DirectTransferResult.Completed

        assertEquals(content.size.toLong(), result.bytesWritten)
        assertArrayEquals(content, files.completed.readBytes())
        val ranges = List(server.requestCount) { server.takeRequest().getHeader("Range") }
        assertEquals(
            setOf(
                "bytes=0-65535",
                "bytes=65536-102399",
                "bytes=102400-167935",
                "bytes=167936-204799",
            ),
            ranges.toSet(),
        )
        assertEquals(4, ranges.size)
    }

    @Test
    fun `each completed bounded request resets the retry count`() = runTest {
        val content = fixtureBytes(204_800)
        server.dispatcher = flakyRangeDispatcher(content, failOnceAt = setOf(65_536, 196_608))
        val files = files("flaky.bin")
        val url = server.url("/flaky.bin").toString()
        val twoAttempts = DirectTransferEngine(
            client = OkHttpClient(),
            policy = DirectTransferEngine.Policy(
                maxAttempts = 2,
                initialRetryDelayMillis = 0,
                bufferBytes = 1_024,
                checkpointIntervalBytes = 1,
            ),
        )

        val result = twoAttempts.transfer(
            plan = plan(url, content.size.toLong(), segmentCount = 1)
                .copy(maxRequestBytes = 65_536),
            metadata = metadata(url, content.size.toLong(), supportsRanges = true),
            destination = files.destination,
        ) as DirectTransferResult.Completed

        assertEquals(content.size.toLong(), result.bytesWritten)
        assertArrayEquals(content, files.completed.readBytes())
        val ranges = List(server.requestCount) { server.takeRequest().getHeader("Range") }
        assertEquals(
            listOf(
                "bytes=0-65535",
                "bytes=65536-131071",
                "bytes=65536-131071",
                "bytes=131072-196607",
                "bytes=196608-204799",
                "bytes=196608-204799",
            ),
            ranges,
        )
    }

    private fun flakyRangeDispatcher(content: ByteArray, failOnceAt: Set<Int>): Dispatcher {
        val failed = Collections.synchronizedSet(mutableSetOf<Int>())
        val ranges = rangeDispatcher(content)
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val start = request.getHeader("Range")
                    ?.let(RANGE::matchEntire)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
                if (start != null && start in failOnceAt && failed.add(start)) {
                    return MockResponse().setResponseCode(500)
                }
                return ranges.dispatch(request)
            }
        }
    }

    private fun rangeDispatcher(content: ByteArray): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = requireNotNull(request.getHeader("Range"))
            val match = RANGE.matchEntire(range)
                ?: return MockResponse().setResponseCode(400)
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            val body = content.copyOfRange(start, end + 1)
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${content.size}")
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("ETag", ETAG)
                .setBody(Buffer().write(body))
        }
    }

    private fun plan(
        url: String,
        expectedBytes: Long?,
        segmentCount: Int = 4,
    ): DirectDownloadPlan = DirectDownloadPlan(
        taskId = "fixture-task",
        sourceUrl = url,
        suggestedFileName = "fixture.bin",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT transfer fixture",
            cookie = "session=fixture",
        ),
        expectedBytes = expectedBytes,
        preferredSegmentCount = segmentCount,
    )

    private fun metadata(
        url: String,
        totalBytes: Long?,
        supportsRanges: Boolean,
    ): RemoteFileMetadata = RemoteFileMetadata(
        finalUrl = url,
        totalBytes = totalBytes,
        supportsByteRanges = supportsRanges,
        entityTag = ETAG,
        lastModified = null,
        contentType = "application/octet-stream",
        suggestedFileName = "fixture.bin",
    )

    private fun files(name: String): DestinationFiles {
        val outputDirectory = directory.newFolder(name.removeSuffix(".bin"))
        val partial = File(outputDirectory, "$name.part")
        val completed = File(outputDirectory, name)
        return DestinationFiles(
            partial = partial,
            completed = completed,
            destination = FileDownloadDestination(partial, completed),
        )
    }

    private fun fixtureBytes(size: Int): ByteArray =
        ByteArray(size) { index -> (index % 251).toByte() }

    private data class DestinationFiles(
        val partial: File,
        val completed: File,
        val destination: FileDownloadDestination,
    )

    private companion object {
        const val ETAG = "\"fixture-v1\""
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}
