package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v1.0.2 R10: a download whose link dies in the middle (a 403 or 410 after an hour; VK's 400,
 * E-D1, after a Wi-Fi ↔ mobile switch) goes on by itself on the fresh link of the same quality
 * that Track E's lookup finds, from the bytes it kept, once per failure. Through the real
 * engines and a local media host whose files state no ETag or Last-Modified, as VK's and
 * Facebook's media hosts answer: before R10 such a file started again at byte 0 on its fresh
 * link and on every run of the same link, and a run after a lost network that its link refused
 * at once counted as a link that gave nothing.
 */
class FreshLinkResumeTest {
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
        site.releaseAll()
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun `a 403 then a fresh link that works finishes with every byte from the checkpoint`() =
        resumesAfter(code = 403)

    @Test
    fun `a 410 then a fresh link that works finishes with every byte from the checkpoint`() =
        resumesAfter(code = 410)

    private fun resumesAfter(code: Int): Unit = runBlocking {
        val name = "dies-$code"
        val content = bytes(SIZE, seed = code)
        site.file("/$name.bin", "old", content)
        site.file("/$name.bin", "new", content)
        site.refuse("/$name.bin", "old", fromByte = 2 * CHUNK, code = code)
        val renewal = Renewals(LinkRenewal.Fresh(direct(name, "new"), metadata(name, "new")))
        val queue = queue(renewal)
        val output = output("$name.bin")

        queue.enqueue(direct(name, "old"), metadata(name, "old"), output.file)
        val done = queue.awaitEnd(name)

        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertArrayEquals(content, output.completed.readBytes())
        assertEquals(
            listOf("bytes=0-65535", "bytes=65536-131071", "bytes=131072-196607"),
            site.asked("/$name.bin", "old"),
        )
        // Before R10 the fresh link asked "bytes=0-65535" first: it started again at byte 0.
        assertEquals(
            listOf("bytes=131072-196607", "bytes=196608-204799"),
            site.asked("/$name.bin", "new"),
        )
        val request = renewal.requests.single()
        assertEquals(code, request.httpStatusCode)
        assertEquals(2 * CHUNK, request.bytesKept)
        assertEquals(2 * CHUNK, request.bytesFromThisLink)
        assertFalse(request.byUser)
    }

    /**
     * A second 403, from the fresh link at its first request: the row stops as "Link expired"
     * (NEEDS_REFRESH) after one lookup, no loop, with the bytes the first link gave; a Retry's
     * fresh link goes on from them. Before R10 the fresh link's run started again at byte 0, so
     * the row kept nothing and the Retry downloaded the file from the start.
     */
    @Test
    fun `a second 403 stops as link expired once and keeps the bytes for a retry`() =
        runBlocking {
            val content = bytes(SIZE, seed = 5)
            listOf("old", "dead", "new").forEach { sig -> site.file("/twice.bin", sig, content) }
            site.refuse("/twice.bin", "old", fromByte = 2 * CHUNK)
            site.refuse("/twice.bin", "dead")
            val renewal = Renewals(
                LinkRenewal.Fresh(direct("twice", "dead"), metadata("twice", "dead")),
                LinkRenewal.Fresh(direct("twice", "new"), metadata("twice", "new")),
            )
            val queue = queue(renewal)
            val output = output("twice.bin")

            queue.enqueue(direct("twice", "old"), metadata("twice", "old"), output.file)
            val stopped = queue.awaitEnd("twice")

            assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
            assertTrue(stopped.requiresLinkRefresh)
            assertEquals(403, stopped.failure?.httpStatusCode)
            delay(NO_LOOP_WAIT_MILLIS)
            assertEquals(1, renewal.requests.size)
            assertEquals(listOf("bytes=131072-196607"), site.asked("/twice.bin", "dead"))
            assertEquals(2 * CHUNK, stopped.checkpoint.downloadedBytes)
            assertEquals(2 * CHUNK, stopped.downloadedBytes)

            queue.resume("twice")
            val done = queue.awaitEnd("twice")

            assertEquals(DownloadTaskStatus.COMPLETED, done.status)
            assertArrayEquals(content, output.completed.readBytes())
            assertEquals(2, renewal.requests.size)
            assertTrue(renewal.requests.last().byUser)
            assertEquals(2 * CHUNK, renewal.requests.last().bytesKept)
            assertEquals(
                listOf("bytes=131072-196607", "bytes=196608-204799"),
                site.asked("/twice.bin", "new"),
            )
        }

    /**
     * VK's srcIp case: the network goes in the middle (the queue stops the run, the checkpoint
     * stays); when it is back the same link runs again from the checkpoint and VK, whose link is
     * bound to the network it was found on, answers 400 at that run's first request. Before R10
     * the run started again at byte 0 and its 400 came with no bytes from this link, so the row
     * failed with "HTTP 400" and Retry used the same dead link.
     */
    @Test
    fun `a link that answers 400 after a lost network gets a fresh link and resumes`() =
        runBlocking {
            val content = bytes(SIZE, seed = 6)
            site.file("/vk.bin", "old", content)
            site.file("/vk.bin", "new", content)
            val held = site.hold("/vk.bin", "old", fromByte = 2 * CHUNK)
            val renewal = Renewals(LinkRenewal.Fresh(direct("vk", "new"), metadata("vk", "new")))
            val queue = queue(renewal)
            val output = output("vk.bin")

            queue.enqueue(direct("vk", "old"), metadata("vk", "old"), output.file)
            held.awaitArrival()
            queue.awaitKept("vk") { it.downloadedBytes == 2 * CHUNK }
            queue.switchNetwork(held) { site.refuse("/vk.bin", "old", code = 400) }
            val done = queue.awaitEnd("vk")

            assertEquals(DownloadTaskStatus.COMPLETED, done.status)
            assertArrayEquals(content, output.completed.readBytes())
            // The held range, then the same range on the network that came back: 400.
            assertEquals(
                listOf("bytes=131072-196607", "bytes=131072-196607"),
                site.asked("/vk.bin", "old").drop(2),
            )
            val request = renewal.requests.single()
            assertEquals(400, request.httpStatusCode)
            assertEquals(2 * CHUNK, request.bytesKept)
            assertEquals(2 * CHUNK, request.bytesFromThisLink)
            assertEquals(
                listOf("bytes=131072-196607", "bytes=196608-204799"),
                site.asked("/vk.bin", "new"),
            )
        }

    /**
     * A merge where one track gets the 403: both tracks get the fresh plan's links, the
     * finished video is kept and the sound goes on from its bytes. Track E (PR #13) did this
     * before R10 too, since a DASH whole file is known by its length; it stays here as the
     * owner's list names it. The merges below fail before R10.
     */
    @Test
    fun `a merge whose track gets a 403 goes on from the bytes kept on both fresh links`() =
        runBlocking {
            val video = bytes(1_500, seed = 7)
            val audio = bytes(2_500, seed = 8)
            serveMerge("m403", "old", video, audio)
            serveMerge("m403", "new", video, audio)
            site.refuse("/m403/audio.mp4", "old", fromByte = 1_000)
            val renewal = Renewals(LinkRenewal.Fresh(merge("m403", "new")))
            val queue = queue(renewal)
            val output = output("m403.mp4")

            queue.enqueue(merge("m403", "old"), output.file)
            val done = queue.awaitEnd("merge-m403")

            assertEquals(DownloadTaskStatus.COMPLETED, done.status)
            assertArrayEquals(muxed(video, audio), output.completed.readBytes())
            val request = renewal.requests.single()
            assertEquals(403, request.httpStatusCode)
            assertEquals(1_500L + 1_000L, request.bytesKept)
            assertEquals(
                listOf("bytes=1000-1999", "bytes=2000-2499"),
                site.asked("/m403/audio.mp4", "new"),
            )
            assertTrue(site.asked("/m403/video.mp4", "new").isEmpty())
        }

    /** VK's srcIp case for a merge: its sound's link answers 400 after a lost network. */
    @Test
    fun `a merge whose link answers 400 after a lost network gets fresh links and resumes`() =
        runBlocking {
            val video = bytes(1_500, seed = 9)
            val audio = bytes(2_500, seed = 10)
            serveMerge("vkm", "old", video, audio)
            serveMerge("vkm", "new", video, audio)
            val held = site.hold("/vkm/audio.mp4", "old", fromByte = 1_000)
            val renewal = Renewals(LinkRenewal.Fresh(merge("vkm", "new")))
            val queue = queue(renewal)
            val output = output("vkm.mp4")

            queue.enqueue(merge("vkm", "old"), output.file)
            held.awaitArrival()
            queue.awaitKept("merge-vkm") { it.videoDoneAndSound(1_000) }
            queue.switchNetwork(held) {
                site.refuse("/vkm/video.mp4", "old", code = 400)
                site.refuse("/vkm/audio.mp4", "old", code = 400)
            }
            val done = queue.awaitEnd("merge-vkm")

            assertEquals(DownloadTaskStatus.COMPLETED, done.status)
            assertArrayEquals(muxed(video, audio), output.completed.readBytes())
            assertEquals(
                listOf("bytes=0-999", "bytes=1000-1999", "bytes=1000-1999"),
                site.asked("/vkm/audio.mp4", "old"),
            )
            val request = renewal.requests.single()
            assertEquals(400, request.httpStatusCode)
            assertEquals(2_500L, request.bytesKept)
            assertEquals(2_500L, request.bytesFromThisLink)
            assertEquals(
                listOf("bytes=1000-1999", "bytes=2000-2499"),
                site.asked("/vkm/audio.mp4", "new"),
            )
            assertTrue(site.asked("/vkm/video.mp4", "new").isEmpty())
        }

    /**
     * A merge's track gets a 403, its fresh link gives bytes, then the network goes and the
     * fresh link refuses the run after it at its first request (a link bound to the network it
     * was found on). That link gave bytes, so this new failure gets its one fresh link. Before
     * R10 only the bytes of the run counted: a fresh link that "gave nothing" stopped as "Link
     * expired" without a lookup.
     */
    @Test
    fun `a merge's fresh link that gave bytes is renewed when it refuses after a lost network`() =
        runBlocking {
            val video = bytes(1_500, seed = 11)
            val audio = bytes(2_500, seed = 12)
            listOf("old", "mid", "new").forEach { sig -> serveMerge("again", sig, video, audio) }
            site.refuse("/again/audio.mp4", "old", fromByte = 1_000)
            val held = site.hold("/again/audio.mp4", "mid", fromByte = 2_000)
            val renewal = Renewals(
                LinkRenewal.Fresh(merge("again", "mid")),
                LinkRenewal.Fresh(merge("again", "new")),
            )
            val queue = queue(renewal)
            val output = output("again.mp4")

            queue.enqueue(merge("again", "old"), output.file)
            held.awaitArrival()
            queue.awaitKept("merge-again") { it.videoDoneAndSound(2_000) }
            queue.switchNetwork(held) { site.refuse("/again/audio.mp4", "mid") }
            val done = queue.awaitEnd("merge-again")

            assertEquals(DownloadTaskStatus.COMPLETED, done.status)
            assertArrayEquals(muxed(video, audio), output.completed.readBytes())
            assertEquals(2, renewal.requests.size)
            val again = renewal.requests.last()
            assertEquals(403, again.httpStatusCode)
            assertEquals(3_500L, again.bytesKept)
            assertEquals(1_000L, again.bytesFromThisLink)
            assertFalse(again.byUser)
            assertEquals(listOf("bytes=2000-2499"), site.asked("/again/audio.mp4", "new"))
            assertTrue(site.asked("/again/video.mp4", "mid").isEmpty())
            assertTrue(site.asked("/again/video.mp4", "new").isEmpty())
        }

    // ---- helpers ----

    private fun queue(renewal: DownloadLinkRenewal): DownloadQueue = DownloadQueue(
        store = MemoryStore(),
        transferDispatcher = dispatcher(),
        scope = scope,
        maxConcurrentDownloads = 2,
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

    /**
     * The phone's network goes while [held]'s request waits (the queue stops the run and keeps
     * its checkpoint); [refuse] makes the host refuse the link found on that network; then the
     * network comes back and the same link runs again.
     */
    private suspend fun DownloadQueue.switchNetwork(held: Site.Hold, refuse: () -> Unit) {
        setNetworkAvailable(false)
        assertEquals(
            DownloadTaskStatus.WAITING_FOR_NETWORK,
            tasks.value.single().status,
        )
        refuse()
        held.release()
        setNetworkAvailable(true)
    }

    private fun TransferCheckpoint.videoDoneAndSound(bytes: Long): Boolean =
        this is AudioVideoMuxCheckpoint && videoReady && audio?.downloadedBytes == bytes

    private fun folder(name: String): File = File(directory.root, name).apply { mkdirs() }

    private suspend fun DownloadQueue.awaitEnd(id: String): StoredDownloadTask =
        withTimeout(WAIT_MILLIS) {
            tasks.mapNotNull { all -> all.firstOrNull { it.id == id } }
                .first { it.status in ENDED }
        }

    private suspend fun DownloadQueue.awaitKept(
        id: String,
        kept: (TransferCheckpoint) -> Boolean,
    ): StoredDownloadTask = withTimeout(WAIT_MILLIS) {
        tasks.mapNotNull { all -> all.firstOrNull { it.id == id } }.first { kept(it.checkpoint) }
    }

    private fun link(path: String, sig: String): String = server.url("$path?sig=$sig").toString()

    private fun direct(name: String, sig: String) = DirectDownloadPlan(
        taskId = name,
        sourceUrl = link("/$name.bin", sig),
        suggestedFileName = "$name.mp4",
        requestContext = CONTEXT,
        mimeType = "video/mp4",
        expectedBytes = SIZE.toLong(),
        preferredSegmentCount = 1,
        maxRequestBytes = CHUNK,
    )

    /** The file's facts as VK's and Facebook's media hosts state them: no ETag or Last-Modified. */
    private fun metadata(name: String, sig: String) = RemoteFileMetadata(
        finalUrl = link("/$name.bin", sig),
        totalBytes = SIZE.toLong(),
        supportsByteRanges = true,
        entityTag = null,
        lastModified = null,
        contentType = "video/mp4",
        suggestedFileName = "$name.mp4",
    )

    private fun serveMerge(name: String, sig: String, video: ByteArray, audio: ByteArray) {
        site.file("/$name/video.mp4", sig, video, type = "video/mp4")
        site.file("/$name/audio.mp4", sig, audio, type = "audio/mp4")
    }

    private fun merge(name: String, sig: String) = AudioVideoMuxDownloadPlan(
        taskId = "merge-$name",
        video = DashDownloadPlan(
            taskId = "merge-$name-video",
            manifestUrl = link("/$name/video.mp4", sig),
            representationId = "video",
            trackType = MediaTrackType.VIDEO,
            suggestedFileName = "$name-video.mp4",
            requestContext = CONTEXT,
            mimeType = "video/mp4",
            codecs = listOf("avc1.4d401f"),
            wholeFile = WholeFileTrack(VIDEO_BYTES, maxRequestBytes = 1_000),
        ),
        audio = DashDownloadPlan(
            taskId = "merge-$name-audio",
            manifestUrl = link("/$name/audio.mp4", sig),
            representationId = "audio",
            trackType = MediaTrackType.AUDIO,
            suggestedFileName = "$name-audio.m4a",
            requestContext = CONTEXT,
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            wholeFile = WholeFileTrack(AUDIO_BYTES, maxRequestBytes = 1_000),
        ),
        suggestedFileName = "$name.mp4",
    )

    private fun output(name: String): Output {
        val parent = folder("output-${name.replace('.', '-')}")
        return Output(
            completed = File(parent, name),
            file = FileDownloadDestination(File(parent, "$name.part"), File(parent, name)),
        )
    }

    private data class Output(val completed: File, val file: FileDownloadDestination)

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
     * A local media host: each file by path and signature (`sig`), without ETag or
     * Last-Modified; the refusals of a link that died; a request held while the network goes;
     * and every request it answered, in order.
     */
    private class Site : Dispatcher() {
        private val files = ConcurrentHashMap<String, Served>()
        private val refusals = CopyOnWriteArrayList<Refusal>()
        private val holds = CopyOnWriteArrayList<Hold>()
        private val asked = CopyOnWriteArrayList<Pair<String, String>>()

        fun file(
            path: String,
            sig: String,
            bytes: ByteArray,
            type: String = "video/mp4",
        ) {
            files["$path?sig=$sig"] = Served(bytes, type)
        }

        /** From [fromByte] on, [path] with [sig] answers [code]: that link died. */
        fun refuse(path: String, sig: String, fromByte: Long = 0, code: Int = 403) {
            refusals += Refusal("$path?sig=$sig", fromByte, code)
        }

        /** The first request of [path] with [sig] from [fromByte] on waits for [Hold.release]. */
        fun hold(path: String, sig: String, fromByte: Long): Hold =
            Hold("$path?sig=$sig", fromByte).also(holds::add)

        fun releaseAll() = holds.forEach(Hold::release)

        /** The ranges asked of [path] with [sig], "all" for a request without a range. */
        fun asked(path: String, sig: String): List<String> =
            asked.filter { it.first == "$path?sig=$sig" }.map { it.second }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = requireNotNull(request.requestUrl)
            val key = "${url.encodedPath}?sig=${url.queryParameter("sig")}"
            val range = request.getHeader("Range")
            asked += key to (range ?: "all")
            val match = range?.let(RANGE::matchEntire)
            val start = match?.groupValues?.get(1)?.toLong() ?: 0L
            holds.firstOrNull { it.key == key && start >= it.fromByte }?.waitOnce()
            val refusal = refusals.lastOrNull { it.key == key && start >= it.fromByte }
            if (refusal != null) {
                return MockResponse()
                    .setResponseCode(refusal.code)
                    .setHeader("Content-Type", "text/plain")
            }
            val served = files[key] ?: return MockResponse().setResponseCode(404)
            val response = MockResponse().setHeader("Content-Type", served.type)
            if (match == null) {
                return response.setResponseCode(200).setBody(Buffer().write(served.bytes))
            }
            val end = minOf(match.groupValues[2].toLong(), served.bytes.size - 1L)
            return response
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${served.bytes.size}")
                .setBody(Buffer().write(served.bytes, start.toInt(), (end - start + 1).toInt()))
        }

        class Hold(val key: String, val fromByte: Long) {
            private val used = AtomicBoolean(false)
            private val arrived = CountDownLatch(1)
            private val released = CountDownLatch(1)

            fun waitOnce() {
                if (!used.compareAndSet(false, true)) return
                arrived.countDown()
                released.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }

            fun awaitArrival() {
                check(arrived.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)) { "$key never asked" }
            }

            fun release() = released.countDown()
        }

        private class Served(val bytes: ByteArray, val type: String)

        private class Refusal(val key: String, val fromByte: Long, val code: Int)
    }

    /** Joins the two tracks as "mux[video|audio]", like the merge tests' muxer. */
    private class JoiningMuxer : LocalAudioVideoMuxer {
        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult {
            val bytes = muxed(videoFile.readBytes(), audioFile.readBytes())
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
        const val VIDEO_BYTES = 1_500L
        const val AUDIO_BYTES = 2_500L
        const val NOW = 1_000_000L
        const val WAIT_MILLIS = 20_000L
        const val NO_LOOP_WAIT_MILLIS = 500L
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
        val CONTEXT = BrowserRequestContext(
            pageUrl = "https://vk.example.test/video-1_2",
            userAgent = "YFT fresh link fixture",
            cookie = null,
        )
        val ENDED = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.NEEDS_REFRESH,
            DownloadTaskStatus.CANCELLED,
        )

        fun bytes(size: Int, seed: Int): ByteArray =
            ByteArray(size) { index -> ((index * 31 + seed * 17) % 251).toByte() }

        fun muxed(video: ByteArray, audio: ByteArray): ByteArray =
            "mux[".toByteArray() + video + "|".toByteArray() + audio + "]".toByteArray()
    }
}
