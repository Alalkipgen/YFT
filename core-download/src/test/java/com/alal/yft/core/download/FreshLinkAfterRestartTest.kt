package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A download the app closed: the queue restores its task as `NEEDS_REFRESH` with its checkpoint,
 * its link and its file gone. A Retry asks the app for a fresh link of the same file and for the
 * task's own file again, reopened where the bytes it kept are, so the download goes on from them
 * instead of starting over; when it cannot, the row says why and keeps the bytes.
 */
class FreshLinkAfterRestartTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var site: Site
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        site = Site()
        server = MockWebServer()
        server.dispatcher = site
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun `a retry after the app closed goes on from the bytes it kept`() = runBlocking {
        val content = bytes(SIZE, seed = 1)
        site.file("/closed.bin", content)
        val output = output("closed.bin", content)
        val store = MemoryStore()
        store.save(closed("closed", kept = 2 * CHUNK, card = CARD))
        val renewal = Renewals(
            LinkRenewal.Fresh(
                plan = direct("closed"),
                metadata = metadata("closed"),
                destination = output.destination,
            ),
        )
        val queue = queue(store, renewal)
        queue.restore()

        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, queue.tasks.value.single().status)
        assertEquals(CARD, queue.tasks.value.single().renewalSource)

        queue.resume("closed")
        val done = queue.awaitEnd("closed")

        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertArrayEquals(content, output.completed.readBytes())
        // It asked for the bytes it did not have, not from the start.
        assertEquals(listOf("bytes=131072-196607", "bytes=196608-204799"), site.asked("/closed.bin"))
        val request = renewal.requests.single()
        assertTrue(request.reopenDestination)
        assertTrue(request.byUser)
        assertEquals(2 * CHUNK, request.bytesKept)
        assertEquals(0L, request.bytesFromThisLink)
    }

    @Test
    fun `a fresh link without the task's file stops with a clear reason and keeps the bytes`() =
        runBlocking {
            val store = MemoryStore()
            store.save(closed("nofile", kept = 2 * CHUNK, card = CARD))
            val renewal = Renewals(
                LinkRenewal.Fresh(plan = direct("nofile"), metadata = metadata("nofile")),
            )
            val queue = queue(store, renewal)
            queue.restore()

            queue.resume("nofile")
            val stopped = queue.awaitEnd("nofile")

            assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
            assertTrue(stopped.requiresLinkRefresh)
            assertEquals(2 * CHUNK, stopped.checkpoint.downloadedBytes)
            assertTrue(stopped.failure?.detail.orEmpty().contains("could not be reopened"))
            assertTrue(site.asked("/nofile.bin").isEmpty())
        }

    @Test
    fun `a page with no fresh link says why and keeps the bytes`() = runBlocking {
        val store = MemoryStore()
        store.save(closed("gone", kept = 2 * CHUNK, card = CARD))
        val renewal = Renewals(LinkRenewal.Unavailable("the video is no longer on the page"))
        val queue = queue(store, renewal)
        queue.restore()

        queue.resume("gone")
        val stopped = queue.awaitEnd("gone")

        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
        assertEquals(2 * CHUNK, stopped.checkpoint.downloadedBytes)
        assertTrue(
            stopped.failure?.detail.orEmpty()
                .contains("Fresh link: the video is no longer on the page"),
        )
    }

    @Test
    fun `with no renewal source a retry after the app closed stays as it was`() = runBlocking {
        val store = MemoryStore()
        store.save(closed("plain", kept = 2 * CHUNK, card = CARD))
        val queue = DownloadQueue(
            store = store,
            transferDispatcher = dispatcher(),
            scope = scope,
            maxConcurrentDownloads = 1,
        )
        queue.restore()

        queue.resume("plain")
        delay(NO_LOOP_WAIT_MILLIS)

        val stopped = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
        assertEquals(2 * CHUNK, stopped.checkpoint.downloadedBytes)
        assertTrue(site.asked("/plain.bin").isEmpty())
    }

    @Test
    fun `the saved card stays with the task through its checkpoints and a restart`() =
        runBlocking {
            val content = bytes(SIZE, seed = 2)
            site.file("/card.bin", content)
            val output = output("card.bin", content)
            val store = MemoryStore()
            val queue = queue(store, Renewals())
            queue.enqueue(
                direct("card"),
                metadata("card"),
                output.destination,
                DownloadDestinationSpec(DownloadDestinationKind.APP_PRIVATE),
            )
            val id = queue.tasks.value.single().id
            queue.setRenewalSource(id, CARD)

            assertEquals(CARD, store.loadAll().single().renewalSource)

            // The app closes: a new queue over the same store reads the card back.
            val reopened = queue(store, Renewals())
            reopened.restore()
            assertEquals(CARD, reopened.tasks.value.single().renewalSource)

            // A card the app no longer holds goes with it.
            reopened.setRenewalSource(id, null)
            assertNull(store.loadAll().single().renewalSource)
        }

    // ---- helpers ----

    private fun queue(store: DownloadTaskStore, renewal: DownloadLinkRenewal): DownloadQueue =
        DownloadQueue(
            store = store,
            transferDispatcher = dispatcher(),
            scope = scope,
            maxConcurrentDownloads = 1,
            linkRenewal = renewal,
        )

    private fun dispatcher(): DownloadTransferDispatcher {
        val client = OkHttpClient()
        val dash = DashTransferEngine(
            client = client,
            workspaceRoot = folder("dash-workspace"),
            policy = DashTransferEngine.Policy(
                maxAttempts = 1,
                initialRetryDelayMillis = 0,
                maxConcurrentChunks = 1,
                bufferBytes = 1_024,
            ),
            clock = { NOW },
        )
        return DefaultDownloadTransferDispatcher(
            direct = DirectTransferEngine(
                client = client,
                policy = DirectTransferEngine.Policy(
                    maxAttempts = 1,
                    initialRetryDelayMillis = 0,
                    bufferBytes = 1_024,
                    checkpointIntervalBytes = 1,
                ),
                clock = { NOW },
            ),
            hls = HlsTransferEngine(
                client = client,
                workspaceRoot = folder("hls-workspace"),
                policy = HlsTransferEngine.Policy(maxAttempts = 1, initialRetryDelayMillis = 0),
                clock = { NOW },
            ),
            dash = dash,
            mux = AudioVideoMuxEngine(
                dashTransfer = dash,
                muxer = JoiningMuxer(),
                workspaceRoot = folder("mux-workspace"),
                clock = { NOW },
                sdkInt = 24,
            ),
        )
    }

    private fun folder(name: String): File = File(directory.root, name).apply { mkdirs() }

    /** The task the app left behind: its checkpoint, its card, no link and no file in memory. */
    private fun closed(name: String, kept: Long, card: String) = StoredDownloadTask(
        id = name,
        displayName = "$name.mp4",
        status = DownloadTaskStatus.NEEDS_REFRESH,
        planType = DownloadPlanType.DIRECT,
        totalBytes = SIZE.toLong(),
        downloadedBytes = kept,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = true,
        failureReason = DownloadFailureReason.EXPIRED_URL,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = SIZE.toLong(),
            entityTag = null,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, SIZE - 1L, kept)),
        ),
        createdAtEpochMs = NOW,
        updatedAtEpochMs = NOW,
        renewalSource = card,
    )

    /** The file the app left on the phone: its partial file holds [content]'s first bytes. */
    private fun output(name: String, content: ByteArray): Output {
        val parent = File(directory.root, "output-${name.replace('.', '-')}").apply { mkdirs() }
        val partial = File(parent, "$name.part")
        partial.writeBytes(content.copyOfRange(0, 2 * CHUNK.toInt()))
        return Output(
            completed = File(parent, name),
            destination = FileDownloadDestination(partial, File(parent, name)),
        )
    }

    private data class Output(
        val completed: File,
        val destination: FileDownloadDestination,
    )

    private fun direct(name: String) = DirectDownloadPlan(
        taskId = name,
        sourceUrl = server.url("/$name.bin").toString(),
        suggestedFileName = "$name.mp4",
        requestContext = CONTEXT,
        mimeType = "video/mp4",
        expectedBytes = SIZE.toLong(),
        preferredSegmentCount = 1,
        maxRequestBytes = CHUNK,
    )

    private fun metadata(name: String) = RemoteFileMetadata(
        finalUrl = server.url("/$name.bin").toString(),
        totalBytes = SIZE.toLong(),
        supportsByteRanges = true,
        entityTag = null,
        lastModified = null,
        contentType = "video/mp4",
        suggestedFileName = "$name.mp4",
    )

    private suspend fun DownloadQueue.awaitEnd(id: String): StoredDownloadTask =
        withTimeout(WAIT_MILLIS) {
            tasks.mapNotNull { all -> all.firstOrNull { it.id == id } }
                .first { it.status in ENDED }
        }

    private fun bytes(size: Int, seed: Int): ByteArray =
        ByteArray(size) { index -> ((index * 31) + seed * 7).toByte() }

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

    /** A media host that serves each file in the ranges it is asked for. */
    private class Site : Dispatcher() {
        private val files = ConcurrentHashMap<String, ByteArray>()
        private val asked = CopyOnWriteArrayList<String>()

        fun file(path: String, bytes: ByteArray) {
            files[path] = bytes
        }

        fun asked(path: String): List<String> = asked.filter { it.startsWith("$path ") }
            .map { it.substringAfter(' ') }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = requireNotNull(request.requestUrl)
            val path = url.encodedPath
            val range = request.getHeader("Range")
            asked += "$path ${range ?: "all"}"
            val content = files[path] ?: return MockResponse().setResponseCode(404)
            val match = range?.let(RANGE::matchEntire)
            val start = match?.groupValues?.get(1)?.toLong() ?: 0L
            val end = match?.groupValues?.get(2)?.toLong() ?: (content.size - 1).toLong()
            val slice = content.copyOfRange(start.toInt(), (end + 1).toInt())
            return MockResponse()
                .setResponseCode(if (range == null) 200 else 206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Accept-Ranges", "bytes")
                .setHeader(
                    "Content-Range",
                    "bytes $start-$end/${content.size}",
                )
                .setBody(Buffer().write(slice))
        }
    }

    /** Joins the two tracks as "mux[video|audio]", as the merge tests' muxer does. */
    private class JoiningMuxer : LocalAudioVideoMuxer {
        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult {
            val bytes = "mux[".toByteArray() +
                videoFile.readBytes() + "|".toByteArray() + audioFile.readBytes() +
                "]".toByteArray()
            outputFile.writeBytes(bytes)
            return LocalMuxResult.Completed(bytes.size.toLong())
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

    private companion object {
        const val SIZE = 204_800
        const val CHUNK = 65_536L
        const val NOW = 1_000_000L
        const val WAIT_MILLIS = 20_000L
        const val NO_LOOP_WAIT_MILLIS = 500L
        const val CARD = "v1|card"
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
        val CONTEXT = BrowserRequestContext(
            pageUrl = "https://vk.example.test/video-1_2",
            userAgent = "YFT restart fixture",
            cookie = null,
        )
        val ENDED = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.NEEDS_REFRESH,
        )
    }
}
