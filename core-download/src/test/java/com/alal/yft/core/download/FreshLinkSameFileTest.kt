package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Track E (E1): a fresh link through the real engines and a local media host. The same file
 * resumes from what it kept; another file (other validators or length, other pieces) starts
 * again at byte 0 and ends right; a fresh link refused before the engine reads the saved state
 * keeps it (G1); a merge's finished track of another length is fetched again (G3); an MP3 or
 * M4A source resumes in its workspace. Every request names its download and link.
 */
class FreshLinkSameFileTest {
    @get:Rule
    val directory = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var site: Site
    private lateinit var scope: CoroutineScope
    private val tags = CopyOnWriteArrayList<Pair<String?, DownloadRequestTag?>>()

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
    fun `a direct file resumes on its fresh link from the bytes it kept`() = runBlocking {
        val content = bytes(SIZE, seed = 1)
        site.file("/file.bin", "old", content, etag = V1)
        site.file("/file.bin", "new", content, etag = V1)
        site.refuse("/file.bin", "old", fromByte = 2 * CHUNK)
        val renewal = Renewals(
            LinkRenewal.Fresh(direct("file", "new", SIZE), metadata("file", "new", SIZE, V1)),
        )
        val queue = queue(renewal)
        val output = output("file.bin")

        queue.enqueue(direct("file", "old", SIZE), metadata("file", "old", SIZE, V1), output.file)
        val done = queue.awaitEnd("file")

        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertArrayEquals(content, output.completed.readBytes())
        assertEquals(
            listOf("bytes=0-65535", "bytes=65536-131071", "bytes=131072-196607"),
            site.asked("/file.bin", "old"),
        )
        assertEquals(
            listOf("bytes=131072-196607", "bytes=196608-204799"),
            site.asked("/file.bin", "new"),
        )
        val request = renewal.requests.single()
        assertEquals(2 * CHUNK, request.bytesKept)
        assertEquals(2 * CHUNK, request.bytesFromThisLink)
        // E1 (11): each request names its download and link; the server never sees it.
        assertEquals(setOf(DownloadRequestTag("file", 0)), tagsOf("old"))
        assertEquals(setOf(DownloadRequestTag("file", 1)), tagsOf("new"))
    }

    @Test
    fun `a direct fresh link to another version or length starts again at byte 0`() =
        runBlocking {
            val old = bytes(SIZE, seed = 1)
            listOf(
                Triple("version", bytes(SIZE, seed = 2), V2),
                Triple("length", bytes(SIZE + 1_000, seed = 3), V1),
            ).forEach { (name, fresh, etag) ->
                site.file("/$name.bin", "old", old, etag = V1)
                site.file("/$name.bin", "new", fresh, etag = etag)
                site.refuse("/$name.bin", "old", fromByte = 2 * CHUNK)
                val queue = queue(
                    Renewals(
                        LinkRenewal.Fresh(
                            direct(name, "new", fresh.size),
                            metadata(name, "new", fresh.size, etag),
                        ),
                    ),
                )
                val output = output("$name.bin")

                queue.enqueue(direct(name, "old", SIZE), metadata(name, "old", SIZE, V1), output.file)
                val done = queue.awaitEnd(name)

                assertEquals(name, DownloadTaskStatus.COMPLETED, done.status)
                assertEquals(name, fresh.size.toLong(), done.totalBytes)
                assertArrayEquals(name, fresh, output.completed.readBytes())
                assertEquals(name, "bytes=0-65535", site.asked("/$name.bin", "new").first())
            }
        }

    @Test
    fun `an HLS fresh playlist of the same pieces resumes and one of other pieces starts again`() =
        runBlocking {
            val pieces = mapOf(
                "one.ts" to "one".repeat(100).toByteArray(),
                "two.ts" to "two".repeat(80).toByteArray(),
                "three.ts" to "three".repeat(70).toByteArray(),
                "four.ts" to "four".repeat(60).toByteArray(),
            )
            listOf(
                "same" to listOf("one.ts", "two.ts"),
                "other" to listOf("three.ts", "four.ts"),
            ).forEach { (name, freshPieces) ->
                servePlaylist(name, "old", listOf("one.ts", "two.ts"), pieces)
                servePlaylist(name, "new", freshPieces, pieces)
                site.refuse("/$name/two.ts", "old")
                val queue = queue(Renewals(LinkRenewal.Fresh(hls(name, "new"))))
                val output = output("$name.ts")

                queue.enqueue(hls(name, "old"), output.file)
                val done = queue.awaitEnd(hls(name, "old").taskId)

                assertEquals(name, DownloadTaskStatus.COMPLETED, done.status)
                val expected = freshPieces.map(pieces::getValue).reduce(ByteArray::plus)
                assertArrayEquals(name, expected, output.completed.readBytes())
                assertEquals(name, 1, site.asked("/$name/two.ts", "old").size)
            }
            // The kept piece is not asked again on the same playlist's fresh link.
            assertTrue(site.asked("/same/one.ts", "new").isEmpty())
            assertEquals(1, site.asked("/other/three.ts", "new").size)
        }

    @Test
    fun `a DASH whole file resumes on its fresh link and one of another length starts again`() =
        runBlocking {
            val old = bytes(2_500, seed = 4)
            listOf(
                "same" to old,
                "other" to bytes(3_000, seed = 5),
            ).forEach { (name, fresh) ->
                site.file("/$name.mp4", "old", old, type = "video/mp4")
                site.file("/$name.mp4", "new", fresh, type = "video/mp4")
                site.refuse("/$name.mp4", "old", fromByte = 2_000)
                val queue = queue(Renewals(LinkRenewal.Fresh(wholeFile(name, "new"))))
                val output = output("$name.mp4")

                queue.enqueue(wholeFile(name, "old"), output.file)
                val done = queue.awaitEnd(wholeFile(name, "old").taskId)

                assertEquals(name, DownloadTaskStatus.COMPLETED, done.status)
                assertArrayEquals(name, fresh, output.completed.readBytes())
            }
            assertEquals(listOf("bytes=0-0", "bytes=2000-2499"), site.asked("/same.mp4", "new"))
            assertEquals(
                listOf("bytes=0-0", "bytes=0-999", "bytes=1000-1999", "bytes=2000-2999"),
                site.asked("/other.mp4", "new"),
            )
        }

    /** G3: a merge's finished track is kept only while the fresh plan states its length. */
    @Test
    fun `a merge fetches a finished track again when its fresh link states another length`() =
        runBlocking {
            val video = bytes(1_500, seed = 6)
            val audio = bytes(2_500, seed = 7)
            listOf(
                "same" to video,
                "other" to bytes(1_800, seed = 8),
            ).forEach { (name, freshVideo) ->
                site.file("/$name/video.mp4", "old", video, type = "video/mp4")
                site.file("/$name/audio.mp4", "old", audio, type = "audio/mp4")
                site.file("/$name/video.mp4", "new", freshVideo, type = "video/mp4")
                site.file("/$name/audio.mp4", "new", audio, type = "audio/mp4")
                site.refuse("/$name/audio.mp4", "old", fromByte = 1_000)
                val fresh = merge(name, "new", freshVideo.size, audio.size)
                val queue = queue(Renewals(LinkRenewal.Fresh(fresh)))
                val output = output("$name.mp4")

                queue.enqueue(merge(name, "old", video.size, audio.size), output.file)
                val done = queue.awaitEnd(fresh.taskId)

                assertEquals(name, DownloadTaskStatus.COMPLETED, done.status)
                assertArrayEquals(name, muxed(freshVideo, audio), output.completed.readBytes())
                assertEquals(
                    name,
                    listOf("bytes=1000-1999", "bytes=2000-2499"),
                    site.asked("/$name/audio.mp4", "new"),
                )
            }
            assertTrue(site.asked("/same/video.mp4", "new").isEmpty())
            assertEquals(
                listOf("bytes=0-999", "bytes=1000-1799"),
                site.asked("/other/video.mp4", "new"),
            )
        }

    @Test
    fun `an M4A and an MP3 source resume on their fresh link in the workspace`() = runBlocking {
        val content = bytes(SIZE, seed = 9)
        listOf(
            "m4a" to { plan: DirectDownloadPlan ->
                plan.copy(audioOnly = true, mimeType = "audio/mp4", suggestedFileName = "song.m4a")
            },
            "mp3" to { plan: DirectDownloadPlan ->
                plan.copy(
                    mp3 = Mp3Encoding(192, "Song"),
                    mimeType = Mp3Encoding.MIME_TYPE,
                    suggestedFileName = "song.mp3",
                )
            },
        ).forEach { (name, converted) ->
            site.file("/$name.bin", "old", content, etag = V1)
            site.file("/$name.bin", "new", content, etag = V1)
            site.refuse("/$name.bin", "old", fromByte = 2 * CHUNK)
            val converter = FakeConverter()
            val queue = queue(
                Renewals(
                    LinkRenewal.Fresh(
                        converted(direct(name, "new", SIZE)),
                        metadata(name, "new", SIZE, V1),
                    ),
                ),
                converter = converter,
            )
            val output = output("$name.out")

            queue.enqueue(
                converted(direct(name, "old", SIZE)),
                metadata(name, "old", SIZE, V1),
                output.file,
            )
            val done = queue.awaitEnd(name)

            assertEquals(name, DownloadTaskStatus.COMPLETED, done.status)
            assertEquals(
                name,
                listOf("bytes=131072-196607", "bytes=196608-204799"),
                site.asked("/$name.bin", "new"),
            )
            assertArrayEquals(name, content, converter.sources.single())
            assertArrayEquals(name, FakeConverter.OUTPUT, output.completed.readBytes())
        }
    }

    /**
     * G1: a fresh link that fails before its engine reads the saved state (an expired link, a
     * refused playlist, manifest or length request) keeps the saved state; a Retry's fresh link
     * then goes on from it.
     */
    @Test
    fun `a fresh link refused before the engine reads the saved state keeps it`() = runBlocking {
        val content = bytes(SIZE, seed = 10)
        site.file("/g1.bin", "old", content, etag = V1)
        site.file("/g1.bin", "new", content, etag = V1)
        site.refuse("/g1.bin", "old", fromByte = 2 * CHUNK)
        val pieces = mapOf(
            "one.ts" to "one".repeat(100).toByteArray(),
            "two.ts" to "two".repeat(80).toByteArray(),
        )
        servePlaylist("g1", "old", pieces.keys.toList(), pieces)
        servePlaylist("g1", "new", pieces.keys.toList(), pieces)
        site.refuse("/g1/two.ts", "old")
        site.refuse("/g1/list.m3u8", "dead")
        val chunks = mapOf(
            "one.m4s" to "one".repeat(90).toByteArray(),
            "two.m4s" to "two".repeat(70).toByteArray(),
        )
        serveManifest("g1", "old", chunks)
        serveManifest("g1", "new", chunks)
        site.refuse("/g1/two.m4s", "old")
        site.refuse("/g1/manifest.mpd", "dead")
        val whole = bytes(2_500, seed = 11)
        site.file("/g1.mp4", "old", whole, type = "video/mp4")
        site.file("/g1.mp4", "new", whole, type = "video/mp4")
        site.refuse("/g1.mp4", "old", fromByte = 2_000)
        site.refuse("/g1.mp4", "dead")
        val video = bytes(1_500, seed = 12)
        val audio = bytes(2_500, seed = 13)
        listOf("old", "new").forEach { sig ->
            site.file("/g1/video.mp4", sig, video, type = "video/mp4")
            site.file("/g1/audio.mp4", sig, audio, type = "audio/mp4")
        }
        site.refuse("/g1/audio.mp4", "old", fromByte = 1_000)
        val expired = NOW - 1
        val output = mapOf(
            "direct" to output("g1.bin"),
            "hls" to output("g1.ts"),
            "dash" to output("g1-dash.mp4"),
            "whole" to output("g1-whole.mp4"),
            "merge" to output("g1-merge.mp4"),
        )
        val dashPlan = { sig: String -> segmentList("g1", sig) }
        val renewals = mapOf(
            "direct" to Renewals(
                LinkRenewal.Fresh(
                    direct("g1", "expired", SIZE).copy(expiresAtEpochMs = expired),
                    metadata("g1", "expired", SIZE, V1),
                ),
                LinkRenewal.Fresh(direct("g1", "new", SIZE), metadata("g1", "new", SIZE, V1)),
            ),
            "hls" to Renewals(
                LinkRenewal.Fresh(hls("g1", "dead")),
                LinkRenewal.Fresh(hls("g1", "new")),
            ),
            "dash" to Renewals(
                LinkRenewal.Fresh(dashPlan("dead")),
                LinkRenewal.Fresh(dashPlan("new")),
            ),
            "whole" to Renewals(
                LinkRenewal.Fresh(wholeFile("g1", "dead")),
                LinkRenewal.Fresh(wholeFile("g1", "new")),
            ),
            "merge" to Renewals(
                LinkRenewal.Fresh(
                    merge("g1", "new", video.size, audio.size).let { plan ->
                        plan.copy(audio = plan.audio.copy(expiresAtEpochMs = expired))
                    },
                ),
                LinkRenewal.Fresh(merge("g1", "new", video.size, audio.size)),
            ),
        )
        val ids = mapOf(
            "direct" to "g1",
            "hls" to hls("g1", "old").taskId,
            "dash" to dashPlan("old").taskId,
            "whole" to wholeFile("g1", "old").taskId,
            "merge" to merge("g1", "old", video.size, audio.size).taskId,
        )
        val kept = mutableMapOf<String, Long>()
        renewals.forEach { (name, renewal) ->
            val queue = queue(renewal)
            val file = output.getValue(name).file
            when (name) {
                "direct" -> queue.enqueue(
                    direct("g1", "old", SIZE),
                    metadata("g1", "old", SIZE, V1),
                    file,
                )
                "hls" -> queue.enqueue(hls("g1", "old"), file)
                "dash" -> queue.enqueue(dashPlan("old"), file)
                "whole" -> queue.enqueue(wholeFile("g1", "old"), file)
                else -> queue.enqueue(merge("g1", "old", video.size, audio.size), file)
            }
            val id = ids.getValue(name)

            val stopped = queue.awaitEnd(id)
            assertEquals(name, DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
            assertEquals(name, 1, renewal.requests.size)
            assertTrue(name, stopped.checkpoint.downloadedBytes > 0)
            kept[name] = stopped.checkpoint.downloadedBytes

            queue.resume(id)
            val done = queue.awaitEnd(id)
            assertEquals(name, DownloadTaskStatus.COMPLETED, done.status)
            assertTrue(name, renewal.requests.last().byUser)
            assertEquals(name, kept.getValue(name), renewal.requests.last().bytesKept)
        }

        assertArrayEquals(content, output.getValue("direct").completed.readBytes())
        assertEquals(2 * CHUNK, kept.getValue("direct"))
        assertEquals(
            listOf("bytes=131072-196607", "bytes=196608-204799"),
            site.asked("/g1.bin", "new"),
        )
        assertArrayEquals(
            pieces.values.reduce(ByteArray::plus),
            output.getValue("hls").completed.readBytes(),
        )
        assertTrue(site.asked("/g1/one.ts", "new").isEmpty())
        assertArrayEquals(
            chunks.values.reduce(ByteArray::plus),
            output.getValue("dash").completed.readBytes(),
        )
        assertTrue(site.asked("/g1/one.m4s", "new").isEmpty())
        assertArrayEquals(whole, output.getValue("whole").completed.readBytes())
        assertEquals(listOf("bytes=0-0", "bytes=2000-2499"), site.asked("/g1.mp4", "new"))
        assertArrayEquals(muxed(video, audio), output.getValue("merge").completed.readBytes())
        assertTrue(site.asked("/g1/video.mp4", "new").isEmpty())
        assertEquals(
            listOf("bytes=1000-1999", "bytes=2000-2499"),
            site.asked("/g1/audio.mp4", "new"),
        )
    }

    @Test
    fun `a request outside the queue names no download`() = runBlocking {
        val content = bytes(SIZE, seed = 14)
        site.file("/plain.bin", "old", content, etag = V1)
        val output = output("plain.bin")

        val result = dispatcher(FakeConverter()).transfer(
            plan = direct("plain", "old", SIZE),
            metadata = metadata("plain", "old", SIZE, V1),
            destination = output.file,
            resumeFrom = null,
        )

        assertTrue(result is QueueTransferResult.Completed)
        assertEquals(4, tags.size)
        assertTrue(tags.all { it.second == null })
    }

    // ---- helpers ----

    private fun queue(
        renewal: DownloadLinkRenewal,
        converter: FakeConverter = FakeConverter(),
    ): DownloadQueue = DownloadQueue(
        store = MemoryStore(),
        transferDispatcher = dispatcher(converter),
        scope = scope,
        maxConcurrentDownloads = 2,
        linkRenewal = renewal,
    )

    private fun dispatcher(converter: FakeConverter): DownloadTransferDispatcher {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                tags += request.url.queryParameter("sig") to
                    request.tag(DownloadRequestTag::class.java)
                chain.proceed(request)
            }
            .build()
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
        val engines = DefaultDownloadTransferDispatcher(
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
                policy = HlsTransferEngine.Policy(
                    maxAttempts = 1,
                    initialRetryDelayMillis = 0,
                    maxConcurrentChunks = 1,
                    bufferBytes = 1_024,
                ),
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
        return Mp3ConvertingTransferDispatcher(
            delegate = engines,
            transcoder = converter,
            workspaceRoot = folder("mp3-workspace"),
            audioExtractor = converter,
        )
    }

    private fun folder(name: String): File =
        File(directory.root, name).apply { mkdirs() }

    private suspend fun DownloadQueue.awaitEnd(id: String): StoredDownloadTask =
        withTimeout(WAIT_MILLIS) {
            tasks.mapNotNull { all -> all.firstOrNull { it.id == id } }
                .first { it.status in ENDED }
        }

    private fun tagsOf(sig: String): Set<DownloadRequestTag?> =
        tags.filter { it.first == sig }.map { it.second }.toSet()

    private fun link(path: String, sig: String): String = server.url("$path?sig=$sig").toString()

    private fun direct(name: String, sig: String, size: Int) = DirectDownloadPlan(
        taskId = name,
        sourceUrl = link("/$name.bin", sig),
        suggestedFileName = "$name.bin",
        requestContext = CONTEXT,
        mimeType = "application/octet-stream",
        expectedBytes = size.toLong(),
        preferredSegmentCount = 1,
        maxRequestBytes = CHUNK,
    )

    private fun metadata(name: String, sig: String, size: Int, etag: String) = RemoteFileMetadata(
        finalUrl = link("/$name.bin", sig),
        totalBytes = size.toLong(),
        supportsByteRanges = true,
        entityTag = etag,
        lastModified = null,
        contentType = "application/octet-stream",
        suggestedFileName = "$name.bin",
    )

    private fun hls(name: String, sig: String) = HlsDownloadPlan(
        taskId = "hls-$name",
        playlistUrl = link("/$name/list.m3u8", sig),
        suggestedFileName = "$name.ts",
        requestContext = CONTEXT,
        mimeType = "video/mp2t",
    )

    private fun segmentList(name: String, sig: String) = DashDownloadPlan(
        taskId = "dash-$name",
        manifestUrl = link("/$name/manifest.mpd", sig),
        representationId = "v1",
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "$name.mp4",
        requestContext = CONTEXT,
        mimeType = "video/mp4",
        codecs = listOf("avc1.4d401f"),
    )

    private fun wholeFile(name: String, sig: String) = DashDownloadPlan(
        taskId = "whole-$name",
        manifestUrl = link("/$name.mp4", sig),
        representationId = "video",
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "$name.mp4",
        requestContext = CONTEXT,
        mimeType = "video/mp4",
        codecs = listOf("avc1.4d401f"),
        wholeFile = WholeFileTrack(totalBytes = null, maxRequestBytes = 1_000),
    )

    private fun merge(name: String, sig: String, videoBytes: Int, audioBytes: Int) =
        AudioVideoMuxDownloadPlan(
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
                wholeFile = WholeFileTrack(videoBytes.toLong(), maxRequestBytes = 1_000),
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
                wholeFile = WholeFileTrack(audioBytes.toLong(), maxRequestBytes = 1_000),
            ),
            suggestedFileName = "$name.mp4",
        )

    private fun servePlaylist(
        name: String,
        sig: String,
        listed: List<String>,
        pieces: Map<String, ByteArray>,
    ) {
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-TARGETDURATION:4")
            listed.forEach { piece ->
                appendLine("#EXTINF:4,")
                appendLine("$piece?sig=$sig")
            }
            append("#EXT-X-ENDLIST")
        }
        site.file("/$name/list.m3u8", sig, playlist.toByteArray(), type = HLS_TYPE)
        listed.forEach { piece ->
            site.file("/$name/$piece", sig, pieces.getValue(piece), type = "video/mp2t")
        }
    }

    private fun serveManifest(name: String, sig: String, chunks: Map<String, ByteArray>) {
        val segments = chunks.keys.joinToString("\n") { """<SegmentURL media="$it?sig=$sig" />""" }
        val manifest = """
            <MPD type="static" xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4" codecs="avc1.4d401f">
                  <Representation id="v1">
                    <SegmentList>
                      $segments
                    </SegmentList>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        site.file("/$name/manifest.mpd", sig, manifest.toByteArray(), type = DASH_TYPE)
        chunks.forEach { (chunk, body) ->
            site.file("/$name/$chunk", sig, body, type = "video/mp4")
        }
    }

    private fun output(name: String): Output {
        val parent = folder("output-${name.replace('.', '-')}")
        val partial = File(parent, "$name.part")
        val completed = File(parent, name)
        return Output(completed, FileDownloadDestination(partial, completed))
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
     * A local media host: each file by path and signature (`sig`), the 403s of a link that
     * died, and every request it answered, in order.
     */
    private class Site : Dispatcher() {
        private val files = ConcurrentHashMap<String, Served>()
        private val refusals = CopyOnWriteArrayList<Pair<String, Long>>()
        private val asked = CopyOnWriteArrayList<Pair<String, String>>()

        fun file(
            path: String,
            sig: String,
            bytes: ByteArray,
            type: String = "application/octet-stream",
            etag: String? = null,
        ) {
            files["$path?sig=$sig"] = Served(bytes, type, etag)
        }

        /** From [fromByte] on, [path] with [sig] answers 403: that link died. */
        fun refuse(path: String, sig: String, fromByte: Long = 0) {
            refusals += "$path?sig=$sig" to fromByte
        }

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
            if (refusals.any { (refused, fromByte) -> refused == key && start >= fromByte }) {
                return MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "text/plain")
            }
            val served = files[key] ?: return MockResponse().setResponseCode(404)
            val response = MockResponse().setHeader("Content-Type", served.type)
            served.etag?.let { response.setHeader("ETag", it) }
            if (match == null) {
                return response.setResponseCode(200).setBody(Buffer().write(served.bytes))
            }
            val end = minOf(match.groupValues[2].toLong(), served.bytes.size - 1L)
            return response
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${served.bytes.size}")
                .setBody(Buffer().write(served.bytes, start.toInt(), (end - start + 1).toInt()))
        }

        private class Served(val bytes: ByteArray, val type: String, val etag: String?)
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

    /** The MP3 encoder and the M4A extractor: keep each source, write [OUTPUT]. */
    private class FakeConverter : LocalMp3Transcoder, LocalAudioExtractor {
        val sources = CopyOnWriteArrayList<ByteArray>()

        override suspend fun transcode(
            source: File,
            output: File,
            encoding: Mp3Encoding,
        ): Mp3TranscodeResult = extract(source, output)

        override suspend fun extract(source: File, output: File): Mp3TranscodeResult {
            sources += source.readBytes()
            output.writeBytes(OUTPUT)
            return Mp3TranscodeResult.Completed(OUTPUT.size.toLong())
        }

        companion object {
            val OUTPUT = "converted-sound".toByteArray()
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
        const val V1 = "\"fixture-v1\""
        const val V2 = "\"fixture-v2\""
        const val HLS_TYPE = "application/vnd.apple.mpegurl"
        const val DASH_TYPE = "application/dash+xml"
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
        val CONTEXT = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT fresh link fixture",
            cookie = "session=fixture",
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
