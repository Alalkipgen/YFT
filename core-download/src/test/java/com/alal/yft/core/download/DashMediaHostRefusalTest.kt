package com.alal.yft.core.download

import com.alal.yft.core.download.RangeFileDispatcher.Throttle
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ForwardingSource
import okio.buffer
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

/**
 * F8 (D7 run 12): a whole file on YouTube's media hosts whose ranges are refused with 403 asks
 * them one at a time and again, for a while; other hosts and the switched-off policy fail at
 * once, as before.
 */
class DashMediaHostRefusalTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var workspaceRoot: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        workspaceRoot = directory.newFolder("refusals")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a media host that refuses the later ranges for a while is asked again one at a time`() =
        runBlocking {
            val bytes = fileBytes(6_000)
            val refusing = Refusing(bytes, refuseFrom = 1_000, forMillis = 400, atOnce = 4)
            server.dispatcher = refusing
            val asks = Asks(server.url("/video.mp4"))
            val files = files("later")

            val result = engine(asks).transfer(
                plan = plan("later", MEDIA_URL, totalBytes = 6_000),
                destination = files.destination,
            ) as DashTransferResult.Completed

            assertEquals(6_000L, result.bytesWritten)
            assertArrayEquals(bytes, files.completed.readBytes())
            assertTrue(refusing.refused.get() >= 3)
            // Once refused, the file asks one range at a time (the first range, asked before,
            // may still be coming in).
            assertEquals(1, asks.mostAtOnceAfterRefusal.get())
            assertEquals(6, refusing.answered.size)
        }

    @Test
    fun `a media host that refuses for good fails after the wait and says so`() = runBlocking {
        val refusing = Refusing(
            fileBytes(4_000),
            refuseFrom = 1_000,
            forMillis = Long.MAX_VALUE,
            atOnce = 4,
        )
        server.dispatcher = refusing
        val asks = Asks(server.url("/video.mp4"))
        val files = files("refused")
        val startedAt = System.nanoTime()

        val result = engine(asks).transfer(
            plan = plan("refused", MEDIA_URL),
            destination = files.destination,
        ) as DashTransferResult.Failure

        val tookMillis = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals(DownloadFailureReason.ACCESS_DENIED, result.failure.reason)
        assertEquals(403, result.failure.httpStatusCode)
        assertEquals(DownloadFailureStage.CONNECT, result.failure.stage)
        val detail = result.failure.detail.orEmpty()
        assertTrue(detail, DETAIL.matches(detail))
        // Waits of 100, 200 and 200 ms: asked again three times, then the next would be late.
        assertTrue("$tookMillis ms", tookMillis >= 450)
        assertEquals(6, refusing.refused.get())
        assertEquals(1, asks.mostAtOnceAfterRefusal.get())
        assertFalse(files.completed.exists())
    }

    @Test
    fun `with the download's own times a media host that refuses for good is asked again once`() =
        runBlocking {
            val refusing = Refusing(
                fileBytes(4_000),
                refuseFrom = 1_000,
                forMillis = Long.MAX_VALUE,
                atOnce = 4,
            )
            server.dispatcher = refusing
            val asks = Asks(server.url("/video.mp4"))
            val own = DashTransferEngine.Policy()
            val startedAt = System.nanoTime()

            val result = engine(
                asks,
                waitMillis = own.mediaHostRefusalWaitMillis,
                firstWaitMillis = own.mediaHostRefusalFirstWaitMillis,
                maxWaitMillis = own.mediaHostRefusalMaxWaitMillis,
            ).transfer(
                plan = plan("own", MEDIA_URL),
                destination = files("own").destination,
            ) as DashTransferResult.Failure

            val tookMillis = (System.nanoTime() - startedAt) / 1_000_000
            assertEquals(DownloadFailureReason.ACCESS_DENIED, result.failure.reason)
            assertEquals(403, result.failure.httpStatusCode)
            assertEquals(DownloadFailureStage.CONNECT, result.failure.stage)
            val detail = result.failure.detail.orEmpty()
            assertTrue(detail, DETAIL.matches(detail))
            assertTrue(detail, detail.endsWith(", 3 s"))
            // F8 trim: the ranges asked at once are refused (3), one is asked again alone 3 s
            // later and refused too, so the file fails, before a second ask would start (9 s).
            assertEquals(4, refusing.refused.get())
            val firstRefusal = refusing.refusedAt.first()
            assertEquals(1, refusing.refusedAt.count { it - firstRefusal >= 2_500 })
            assertTrue("$tookMillis ms", tookMillis in 3_000L..8_000L)
            assertEquals(1, asks.mostAtOnceAfterRefusal.get())
        }

    @Test
    fun `another host that refuses a range fails at once`() = runBlocking {
        val refusing = Refusing(fileBytes(4_000), refuseFrom = 1_000, forMillis = Long.MAX_VALUE)
        server.dispatcher = refusing
        val asks = Asks(server.url("/video.mp4"))

        val result = engine(asks).transfer(
            plan = plan("other", "https://media.example.test/video.mp4?sig=fixture"),
            destination = files("other").destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.ACCESS_DENIED, result.failure.reason)
        assertNull(result.failure.detail)
        assertTrue(refusing.refused.get() in 1..2)
    }

    @Test
    fun `with no wait a media host's refusal fails at once as before`() = runBlocking {
        val refusing = Refusing(fileBytes(4_000), refuseFrom = 1_000, forMillis = Long.MAX_VALUE)
        server.dispatcher = refusing
        val asks = Asks(server.url("/video.mp4"))

        val result = engine(asks, waitMillis = 0).transfer(
            plan = plan("off", MEDIA_URL),
            destination = files("off").destination,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.ACCESS_DENIED, result.failure.reason)
        assertNull(result.failure.detail)
        assertTrue(refusing.refused.get() in 1..3)
    }

    /**
     * Serves the file in ranges, the first one slowly; a range from [refuseFrom] is refused with
     * 403 until [forMillis] after the first request. With [atOnce], no refusal is answered before
     * that many requests came in (at most a second), so the ranges the download asks at once
     * are all asked before it hears of a refusal, however slow the machine.
     */
    private class Refusing(
        bytes: ByteArray,
        private val refuseFrom: Long,
        private val forMillis: Long,
        private val atOnce: Int = 0,
    ) : Dispatcher() {
        private val ranges = RangeFileDispatcher(bytes) { start ->
            Throttle(250, 100).takeIf { start == 0L }
        }
        private var firstAt: Long? = null
        private val firstAsks = CountDownLatch(atOnce)
        val refused = AtomicInteger(0)
        val refusedAt: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val answered: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun dispatch(request: RecordedRequest): MockResponse {
            val now = System.nanoTime() / 1_000_000
            val first = synchronized(this) { firstAt ?: now.also { firstAt = it } }
            firstAsks.countDown()
            val range = request.getHeader("Range").orEmpty()
            val start = range.substringAfter("bytes=").substringBefore('-').toLongOrNull()
            if (start != null && start >= refuseFrom && now - first < forMillis) {
                firstAsks.await(1, TimeUnit.SECONDS)
                refused.incrementAndGet()
                refusedAt += System.nanoTime() / 1_000_000
                return MockResponse().setResponseCode(403)
            }
            answered += range
            return ranges.dispatch(request)
        }
    }

    /**
     * Sends every request to [local] and counts the requests asked after the first 403 that are
     * open at once (until their answer is closed).
     */
    private class Asks(private val local: HttpUrl) : Interceptor {
        private val refusedSeen = AtomicBoolean(false)
        private val lateOpen = AtomicInteger(0)
        val mostAtOnceAfterRefusal = AtomicInteger(0)

        override fun intercept(chain: Interceptor.Chain): Response {
            val late = refusedSeen.get()
            if (late) mostAtOnceAfterRefusal.accumulateAndGet(lateOpen.incrementAndGet(), ::maxOf)
            val closed = AtomicBoolean(false)
            val release = {
                if (late && closed.compareAndSet(false, true)) lateOpen.decrementAndGet()
            }
            val response = try {
                chain.proceed(chain.request().newBuilder().url(local).build())
            } catch (error: Throwable) {
                release()
                throw error
            }
            if (response.code == 403) refusedSeen.set(true)
            val body = response.body ?: return response.also { release() }
            val source = object : ForwardingSource(body.source()) {
                override fun close() {
                    release()
                    super.close()
                }
            }.buffer()
            return response.newBuilder()
                .body(source.asResponseBody(body.contentType(), body.contentLength()))
                .build()
        }
    }

    private fun engine(
        asks: Asks,
        waitMillis: Long = 600,
        firstWaitMillis: Long = 100,
        maxWaitMillis: Long = 200,
    ): DashTransferEngine =
        DashTransferEngine(
            client = OkHttpClient.Builder().addInterceptor(asks).build(),
            workspaceRoot = workspaceRoot,
            policy = DashTransferEngine.Policy(
                maxAttempts = 1,
                initialRetryDelayMillis = 0,
                bufferBytes = 1_024,
                mediaHostRefusalWaitMillis = waitMillis,
                mediaHostRefusalFirstWaitMillis = firstWaitMillis,
                mediaHostRefusalMaxWaitMillis = maxWaitMillis,
            ),
            clock = { 100 },
        )

    private fun plan(id: String, url: String, totalBytes: Long = 4_000): DashDownloadPlan =
        DashDownloadPlan(
            taskId = "refusal-$id",
            manifestUrl = url,
            representationId = "video",
            trackType = MediaTrackType.VIDEO,
            suggestedFileName = "$id.mp4",
            requestContext = BrowserRequestContext(
                pageUrl = "https://page.example.test/watch",
                userAgent = "YFT refusal fixture",
                cookie = null,
            ),
            mimeType = "video/mp4",
            codecs = listOf("avc1.4d401f"),
            // A stated length, as YouTube's clen: the ranges start at once, as on D7 run 12.
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
        const val MEDIA_URL = "https://rr1---sn-fixture.googlevideo.com/video.mp4?sig=fixture"
        val DETAIL = Regex("403×\\d+ at 0 MiB, [1-4] at once, \\d+ s")
    }
}
