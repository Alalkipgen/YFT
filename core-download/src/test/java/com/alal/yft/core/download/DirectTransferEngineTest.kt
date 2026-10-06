package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.io.FileNotFoundException
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
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
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

    /**
     * P20 (R1): a new MediaStore row has no file until its first "rw" open, so reading its length
     * before prepare() throws. The old engine read it first and failed every fresh video at 0 B
     * with STORAGE_UNAVAILABLE. The queue starts a new task with an empty checkpoint, not null.
     */
    @Test
    fun `a fresh download into a destination without a file until prepare completes`() = runTest {
        val content = fixtureBytes(64)
        server.dispatcher = rangeDispatcher(content)
        val freshStarts = listOf(
            null,
            DirectTransferCheckpoint(
                totalBytes = content.size.toLong(),
                entityTag = ETAG,
                lastModified = null,
                segments = emptyList(),
            ),
        )

        freshStarts.forEachIndexed { index, resumeFrom ->
            val files = files("fresh-$index.bin")
            val url = server.url("/fresh-$index.bin").toString()

            val result = engine.transfer(
                plan = plan(url, content.size.toLong()),
                metadata = metadata(url, content.size.toLong(), supportsRanges = true),
                destination = NoFileUntilPrepared(files.destination),
                resumeFrom = resumeFrom,
            )

            assertTrue("fresh start $index: $result", result is DirectTransferResult.Completed)
            assertArrayEquals(content, files.completed.readBytes())
        }
    }

    @Test
    fun `a resume whose length read fails starts again at byte 0 and completes`() = runTest {
        val content = fixtureBytes(20)
        server.dispatcher = rangeDispatcher(content)
        val files = files("unreadable-resume.bin")
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = content.size.toLong(),
            entityTag = ETAG,
            lastModified = null,
            segments = listOf(
                DownloadSegment(0, 0, 9, downloadedBytes = 5),
                DownloadSegment(1, 10, 19, downloadedBytes = 0),
            ),
        )
        val checkpoints = Collections.synchronizedList(mutableListOf<Long>())
        val url = server.url("/unreadable-resume.bin").toString()

        val result = engine.transfer(
            plan = plan(url, content.size.toLong(), segmentCount = 2),
            metadata = metadata(url, content.size.toLong(), supportsRanges = true),
            destination = NoFileUntilPrepared(files.destination),
            resumeFrom = checkpoint,
            onCheckpoint = { checkpoints += it.downloadedBytes },
        )

        assertTrue(result.toString(), result is DirectTransferResult.Completed)
        assertArrayEquals(content, files.completed.readBytes())
        val ranges = List(server.requestCount) {
            server.takeRequest().getHeader("Range")
        }.toSet()
        assertEquals(setOf("bytes=0-9", "bytes=10-19"), ranges)
        assertEquals(0L, checkpoints.first())
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

    /**
     * P21: a failed write ends the download as a failure of the file at the write step, with the
     * error's class and message kept for Details. It is not retried as a network error.
     */
    @Test
    fun `a failed write is a storage failure of the write step with its detail`() = runTest {
        val content = fixtureBytes(64)
        server.dispatcher = rangeDispatcher(content)
        val files = files("write-fails.bin")
        val url = server.url("/write-fails.bin").toString()

        val result = engine.transfer(
            plan = plan(url, content.size.toLong(), segmentCount = 1),
            metadata = metadata(url, content.size.toLong(), supportsRanges = true),
            destination = FailingOutput(files.destination, failWrite = true),
        ) as DirectTransferResult.Failure

        assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, result.failure.reason)
        assertEquals(DownloadFailureStage.WRITE_FILE, result.failure.stage)
        assertEquals("IOException: EIO (I/O error)", result.failure.detail)
        assertEquals(1, server.requestCount)
        assertFalse(files.completed.exists())
    }

    /**
     * P21: closing the file after good writes is part of writing it. The old engine let the
     * close error through as a dropped connection, asked the server again and ended with
     * NETWORK.
     */
    @Test
    fun `a failed close after good writes is a storage failure, not a network one`() = runTest {
        val content = fixtureBytes(64)
        server.dispatcher = rangeDispatcher(content)
        val files = files("close-fails.bin")
        val url = server.url("/close-fails.bin").toString()

        val result = engine.transfer(
            plan = plan(url, content.size.toLong(), segmentCount = 1),
            metadata = metadata(url, content.size.toLong(), supportsRanges = true),
            destination = FailingOutput(files.destination, failClose = true),
        ) as DirectTransferResult.Failure

        assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, result.failure.reason)
        assertEquals(DownloadFailureStage.WRITE_FILE, result.failure.stage)
        assertEquals("IOException: close failed: EIO", result.failure.detail)
        assertEquals("A file error is not asked for again", 1, server.requestCount)
        assertFalse(files.completed.exists())
    }

    /**
     * P21: a connection that drops while the body is read is a network failure of the read step.
     * Its detail names the error, never the address it came from.
     */
    @Test
    fun `a connection dropped during the body is a network failure of the read step`() =
        runTest {
            val content = fixtureBytes(4_096)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody(Buffer().write(content))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            }
            val files = files("dropped.bin")
            val url = server.url("/dropped.bin").toString()

            val result = engine.transfer(
                plan = plan(url, content.size.toLong()),
                metadata = metadata(url, content.size.toLong(), supportsRanges = false),
                destination = files.destination,
            ) as DirectTransferResult.Failure

            assertEquals(DownloadFailureReason.NETWORK, result.failure.reason)
            assertEquals(DownloadFailureStage.READ_SOURCE, result.failure.stage)
            val detail = requireNotNull(result.failure.detail)
            assertTrue(detail, detail.contains("Exception"))
            for (address in listOf("http", "127.0.0.1", "localhost", server.hostName)) {
                assertFalse("$address in: $detail", detail.contains(address))
            }
            assertEquals("Each attempt is asked for", 3, server.requestCount)
            assertFalse(files.completed.exists())
        }

    /**
     * P21: an IllegalStateException while the body is read is the connection's problem, not the
     * file's. The old engine called every IllegalStateException STORAGE_UNAVAILABLE.
     */
    @Test
    fun `an illegal state of the source is a network failure, not a storage one`() = runTest {
        val content = fixtureBytes(64)
        server.dispatcher = rangeDispatcher(content)
        val files = files("closed-source.bin")
        val url = server.url("/closed-source.bin").toString()
        val closedSource = DirectTransferEngine(
            client = OkHttpClient.Builder()
                .addNetworkInterceptor { chain -> chain.proceed(chain.request()).withClosedBody() }
                .build(),
            policy = DirectTransferEngine.Policy(
                maxAttempts = 3,
                initialRetryDelayMillis = 0,
                bufferBytes = 1_024,
                checkpointIntervalBytes = 1,
            ),
        )

        val result = closedSource.transfer(
            plan = plan(url, content.size.toLong(), segmentCount = 1),
            metadata = metadata(url, content.size.toLong(), supportsRanges = true),
            destination = files.destination,
        ) as DirectTransferResult.Failure

        assertEquals(DownloadFailureReason.NETWORK, result.failure.reason)
        assertEquals("IllegalStateException: closed", result.failure.detail)
        assertFalse(files.completed.exists())
    }

    /** This response, with a body that throws like a source closed under its reader. */
    private fun Response.withClosedBody(): Response {
        val original = requireNotNull(body)
        val closed = object : ResponseBody() {
            override fun contentType(): MediaType? = original.contentType()
            override fun contentLength(): Long = original.contentLength()
            override fun source(): BufferedSource =
                object : ForwardingSource(original.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long =
                        throw IllegalStateException("closed")
                }.buffer()
        }
        return newBuilder().body(closed).build()
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

    /** Like a new MediaStore row on Android: its length cannot be read until prepare(). */
    private class NoFileUntilPrepared(
        private val delegate: DownloadDestination,
    ) : DownloadDestination {
        @Volatile
        private var prepared = false

        override fun prepare(expectedLength: Long?) {
            delegate.prepare(expectedLength)
            prepared = true
        }

        override fun temporaryLength(): Long? {
            if (!prepared) throw FileNotFoundException("open failed: ENOENT")
            return delegate.temporaryLength()
        }

        override fun open(): SeekableDownloadOutput = delegate.open()
        override fun commit() = delegate.commit()
        override fun discard() = delegate.discard()
    }

    /** Writes through [delegate], failing every write or every close when asked to. */
    private class FailingOutput(
        private val delegate: DownloadDestination,
        private val failWrite: Boolean = false,
        private val failClose: Boolean = false,
    ) : DownloadDestination by delegate {
        override fun open(): SeekableDownloadOutput {
            val output = delegate.open()
            return object : SeekableDownloadOutput by output {
                override fun write(
                    position: Long,
                    buffer: ByteArray,
                    offset: Int,
                    byteCount: Int,
                ) {
                    if (failWrite) throw IOException("EIO (I/O error)")
                    output.write(position, buffer, offset, byteCount)
                }

                override fun close() {
                    output.close()
                    if (failClose) throw IOException("close failed: EIO")
                }
            }
        }
    }

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
