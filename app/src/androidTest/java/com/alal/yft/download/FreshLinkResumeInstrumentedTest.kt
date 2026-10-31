package com.alal.yft.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AudioVideoMuxRunner
import com.alal.yft.core.download.DashTransferRunner
import com.alal.yft.core.download.DefaultDownloadTransferDispatcher
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadLinkRenewal
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.download.HlsTransferRunner
import com.alal.yft.core.download.LinkRenewal
import com.alal.yft.core.download.LinkRenewalRequest
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 R10 on Android: the app's queue and direct engine, a partial file in the app's cache
 * and a media host in memory (the app's network rules forbid a local server) whose file states
 * no ETag or Last-Modified, as VK's and Facebook's media hosts answer. A 403 in the middle, then
 * a fresh link: the download goes on from the bytes it kept. VK's srcIp case: the network goes,
 * and when it is back the same link answers 400 at its first request; the download gets a fresh
 * link and goes on from the bytes it kept. Both failed before R10: the fresh link started again
 * at byte 0, and the 400 stopped the row as "HTTP 400". The lookup is a fake that answers the
 * fresh link (R10 calls the existing lookups unchanged).
 */
@RunWith(AndroidJUnit4::class)
class FreshLinkResumeInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "r10-${UUID.randomUUID()}")
    private val network = InMemoryHost()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        network.releaseAll()
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun aFileWithoutValidatorsGoesOnFromItsBytesOnTheFreshLinkAfterA403() = runBlocking<Unit> {
        val content = bytes(seed = 1)
        network.serve(link("clip", "old"), content)
        network.serve(link("clip", "new"), content)
        network.refuse(link("clip", "old"), fromByte = 2 * CHUNK, code = 403)
        val renewal = Renewals(LinkRenewal.Fresh(plan("clip", "new"), metadata("clip", "new")))
        val queue = queue(renewal)
        val output = File(directory, "clip.mp4")

        queue.enqueue(plan("clip", "old"), metadata("clip", "old"), destination(output))
        val done = queue.awaitEnd("clip")

        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertArrayEquals(content, output.readBytes())
        assertEquals(
            listOf("bytes=131072-196607", "bytes=196608-204799"),
            network.asked(link("clip", "new")),
        )
        val request = renewal.requests.single()
        assertEquals(403, request.httpStatusCode)
        assertEquals(2 * CHUNK, request.bytesKept)
    }

    @Test
    fun vksLinkThatAnswers400AfterALostNetworkGetsAFreshLinkAndGoesOn() = runBlocking<Unit> {
        val content = bytes(seed = 2)
        network.serve(link("vk", "old"), content)
        network.serve(link("vk", "new"), content)
        val held = network.hold(link("vk", "old"), fromByte = 2 * CHUNK)
        val renewal = Renewals(LinkRenewal.Fresh(plan("vk", "new"), metadata("vk", "new")))
        val queue = queue(renewal)
        val output = File(directory, "vk.mp4")

        queue.enqueue(plan("vk", "old"), metadata("vk", "old"), destination(output))
        held.awaitArrival()
        withTimeout(WAIT_MILLIS) {
            queue.tasks.mapNotNull { all -> all.firstOrNull { it.id == "vk" } }
                .first { it.checkpoint.downloadedBytes == 2 * CHUNK }
        }
        queue.setNetworkAvailable(false)
        assertEquals(DownloadTaskStatus.WAITING_FOR_NETWORK, queue.tasks.value.single().status)
        network.refuse(link("vk", "old"), fromByte = 0, code = 400)
        held.release()
        queue.setNetworkAvailable(true)
        val done = queue.awaitEnd("vk")

        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertArrayEquals(content, output.readBytes())
        assertEquals("bytes=131072-196607", network.asked(link("vk", "old")).last())
        val request = renewal.requests.single()
        assertEquals(400, request.httpStatusCode)
        assertEquals(2 * CHUNK, request.bytesKept)
        assertEquals(2 * CHUNK, request.bytesFromThisLink)
        assertEquals(
            listOf("bytes=131072-196607", "bytes=196608-204799"),
            network.asked(link("vk", "new")),
        )
    }

    private fun queue(renewal: DownloadLinkRenewal): DownloadQueue = DownloadQueue(
        store = MemoryStore(),
        transferDispatcher = DefaultDownloadTransferDispatcher(
            direct = DirectTransferEngine(
                client = OkHttpClient.Builder().addInterceptor(network).build(),
                policy = DirectTransferEngine.Policy(
                    maxAttempts = 1,
                    initialRetryDelayMillis = 0,
                    bufferBytes = 1_024,
                    checkpointIntervalBytes = 1,
                ),
            ),
            hls = NoStreams,
            dash = NoStreams,
            mux = NoStreams,
        ),
        scope = scope,
        linkRenewal = renewal,
    )

    private suspend fun DownloadQueue.awaitEnd(id: String): StoredDownloadTask =
        withTimeout(WAIT_MILLIS) {
            tasks.mapNotNull { all -> all.firstOrNull { it.id == id } }
                .first { it.status in ENDED }
        }

    private fun destination(output: File): FileDownloadDestination {
        directory.mkdirs()
        return FileDownloadDestination(
            partialFile = File(directory, "${output.name}.part"),
            completedFile = output,
        )
    }

    private fun link(name: String, sig: String) =
        "https://media.example.test/r10/$name.mp4?sig=$sig"

    private fun plan(name: String, sig: String) = DirectDownloadPlan(
        taskId = name,
        sourceUrl = link(name, sig),
        suggestedFileName = "$name.mp4",
        requestContext = BrowserRequestContext(pageUrl = null, userAgent = null, cookie = null),
        mimeType = VIDEO_MP4,
        expectedBytes = SIZE.toLong(),
        preferredSegmentCount = 1,
        maxRequestBytes = CHUNK,
    )

    private fun metadata(name: String, sig: String) = RemoteFileMetadata(
        finalUrl = link(name, sig),
        totalBytes = SIZE.toLong(),
        supportsByteRanges = true,
        entityTag = null,
        lastModified = null,
        contentType = VIDEO_MP4,
        suggestedFileName = "$name.mp4",
    )

    /** Answers each renewal in turn; none left: no fresh link. */
    private class Renewals(vararg answers: LinkRenewal) : DownloadLinkRenewal {
        private val answers = answers.toList()
        val requests = CopyOnWriteArrayList<LinkRenewalRequest>()

        override suspend fun renew(request: LinkRenewalRequest): LinkRenewal {
            val index = requests.size
            requests += request
            return answers.getOrElse(index) { LinkRenewal.Unavailable("no more links") }
        }
    }

    /**
     * Each address's bytes in ranges, without ETag or Last-Modified; the refusals of a link
     * that died; one request held while the network goes; every range asked, in order.
     */
    private class InMemoryHost : Interceptor {
        private val files = ConcurrentHashMap<String, ByteArray>()
        private val refusals = CopyOnWriteArrayList<Triple<String, Long, Int>>()
        private val holds = CopyOnWriteArrayList<Hold>()
        private val asked = CopyOnWriteArrayList<Pair<String, String>>()

        fun serve(url: String, bytes: ByteArray) {
            files[url] = bytes
        }

        fun refuse(url: String, fromByte: Long, code: Int) {
            refusals += Triple(url, fromByte, code)
        }

        fun hold(url: String, fromByte: Long): Hold = Hold(url, fromByte).also(holds::add)

        fun releaseAll() = holds.forEach(Hold::release)

        fun asked(url: String): List<String> = asked.filter { it.first == url }.map { it.second }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()
            val range = request.header("Range")
            asked += url to (range ?: "all")
            val match = range?.let(RANGE::matchEntire)
            val start = match?.groupValues?.get(1)?.toLong() ?: 0L
            holds.firstOrNull { it.url == url && start >= it.fromByte }?.waitOnce()
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            refusals.lastOrNull { (refused, fromByte) -> refused == url && start >= fromByte }
                ?.let { (_, _, code) ->
                    return builder.code(code).message("Refused")
                        .header("Content-Type", "text/plain")
                        .body(ByteArray(0).toResponseBody()).build()
                }
            val bytes = files[url]
                ?: return builder.code(404).message("Not Found")
                    .body(ByteArray(0).toResponseBody()).build()
            match ?: return builder.code(200).message("OK").header("Content-Type", VIDEO_MP4)
                .body(bytes.toResponseBody(VIDEO_MP4.toMediaType())).build()
            val end = minOf(match.groupValues[2].toLong(), bytes.size - 1L)
            return builder.code(206).message("Partial Content")
                .header("Content-Type", VIDEO_MP4)
                .header("Content-Range", "bytes $start-$end/${bytes.size}")
                .body(
                    bytes.copyOfRange(start.toInt(), end.toInt() + 1)
                        .toResponseBody(VIDEO_MP4.toMediaType()),
                )
                .build()
        }

        class Hold(val url: String, val fromByte: Long) {
            private val used = AtomicBoolean(false)
            private val arrived = CountDownLatch(1)
            private val released = CountDownLatch(1)

            fun waitOnce() {
                if (!used.compareAndSet(false, true)) return
                arrived.countDown()
                released.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }

            fun awaitArrival() {
                check(arrived.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)) { "$url never asked" }
            }

            fun release() = released.countDown()
        }
    }

    private class MemoryStore : DownloadTaskStore {
        private val tasks = ConcurrentHashMap<String, StoredDownloadTask>()

        override suspend fun loadAll(): List<StoredDownloadTask> = tasks.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            tasks[task.id] = task
        }

        override suspend fun delete(id: String) {
            tasks.remove(id)
        }
    }

    /** The kinds of transfer these tests do not use. */
    private object NoStreams : HlsTransferRunner, DashTransferRunner, AudioVideoMuxRunner {
        override suspend fun transfer(
            plan: HlsDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: HlsTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit,
        ): HlsTransferResult = error("Not used in this test")

        override suspend fun discard(plan: HlsDownloadPlan) = Unit

        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult = error("Not used in this test")

        override suspend fun discard(plan: DashDownloadPlan) = Unit

        override suspend fun transfer(
            plan: AudioVideoMuxDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: AudioVideoMuxCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
        ): AudioVideoMuxResult = error("Not used in this test")

        override suspend fun discard(plan: AudioVideoMuxDownloadPlan) = Unit
    }

    private companion object {
        const val SIZE = 204_800
        const val CHUNK = 65_536L
        const val WAIT_MILLIS = 20_000L
        const val VIDEO_MP4 = "video/mp4"
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
        val ENDED = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.NEEDS_REFRESH,
            DownloadTaskStatus.CANCELLED,
        )

        fun bytes(seed: Int): ByteArray =
            ByteArray(SIZE) { index -> ((index * 31 + seed * 17) % 251).toByte() }
    }
}
