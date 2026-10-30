package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 item 28: a download into a folder picked through Android's folder picker (SAF) keeps
 * its place. A Retry resumes into the same temporary document, a Retry after the file itself
 * failed starts over in a new one in the same folder, and a folder that goes away while the
 * download runs fails it as storage unavailable, as other destination failures do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SafDownloadQueueTest {
    @Test
    fun `a SAF download resumes into the same temporary document`() = runTest {
        val tree = FakeTree()
        val destination = SafDownloadDestination.create(
            store = tree,
            treeUri = TREE,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            temporaryId = "resume",
        )
        val runner = WritingRunner { call -> NETWORK_FAILURE.takeIf { call == 0 } }
        val tasks = InMemoryTasks()
        val queue = DownloadQueue(tasks, runner, backgroundScope, maxConcurrentDownloads = 1)
        queue.enqueue(
            plan = plan("resume"),
            metadata = metadata(),
            destination = destination,
            destinationSpec = safSpec(destination),
        )
        runCurrent()
        val failed = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.FAILED, failed.status)
        assertEquals(DownloadDestinationKind.SAF_DOCUMENT, failed.destinationKind)
        assertEquals("content://tree.test/document/1", failed.destinationUri)
        // The document was sized up front; its first bytes are written.
        val partial = tree.documents.getValue("content://tree.test/document/1").bytes()
        assertEquals(TOTAL_BYTES, partial.size.toLong())
        assertArrayEquals(
            CONTENT.copyOf(PARTIAL_BYTES.toInt()),
            partial.copyOf(PARTIAL_BYTES.toInt()),
        )

        queue.resume("resume")
        runCurrent()

        val done = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertEquals(listOf<DownloadDestination>(destination, destination), runner.destinations)
        assertEquals("The Retry goes on at byte 4", listOf(0L, PARTIAL_BYTES), runner.resumedFrom)
        assertEquals(listOf(".clip.mp4.yft-resume.part"), tree.created.map { it.name })
        assertTrue(tree.deleted.isEmpty())
        val published = tree.documents.getValue("content://tree.test/document/1-final")
        assertEquals("clip.mp4", published.name)
        assertArrayEquals(CONTENT, published.bytes())
        assertEquals("content://tree.test/document/1-final", done.destinationUri)
        assertEquals(done.destinationUri, tasks.snapshot().single().destinationUri)
    }

    @Test
    fun `a SAF retry after a storage failure starts over in the same folder`() = runTest {
        val tree = FakeTree()
        val destination = SafDownloadDestination.create(
            store = tree,
            treeUri = TREE,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            temporaryId = "renew",
        )
        val runner = WritingRunner { call -> STORAGE_FAILURE.takeIf { call == 0 } }
        val tasks = InMemoryTasks()
        val queue = DownloadQueue(tasks, runner, backgroundScope, maxConcurrentDownloads = 1)
        queue.enqueue(plan("renew"), metadata(), destination, safSpec(destination))
        runCurrent()
        assertEquals(DownloadTaskStatus.FAILED, queue.tasks.value.single().status)

        queue.resume("renew")
        runCurrent()

        val done = queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.COMPLETED, done.status)
        assertEquals("The Retry starts at byte 0", listOf(0L, 0L), runner.resumedFrom)
        // The broken document went; its successor has the same name in the same folder.
        assertEquals(listOf("content://tree.test/document/1"), tree.deleted)
        assertEquals(
            listOf(
                Created(TREE, ".clip.mp4.yft-renew.part", "video/mp4"),
                Created(TREE, ".clip.mp4.yft-renew.part", "video/mp4"),
            ),
            tree.created,
        )
        val renewed = runner.destinations[1]
        assertEquals("content://tree.test/document/2", renewed.recoveryUri)
        assertEquals(DownloadDestinationKind.SAF_DOCUMENT, done.destinationKind)
        assertEquals("content://tree.test/document/2-final", done.destinationUri)
        assertArrayEquals(
            CONTENT,
            tree.documents.getValue("content://tree.test/document/2-final").bytes(),
        )
    }

    @Test
    fun `a folder that goes away while a download runs fails it as storage unavailable`() =
        runTest {
            val server = MockWebServer()
            server.dispatcher = rangeDispatcher(CONTENT)
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            try {
                // The folder is deleted once every byte is written, before the check.
                val tree = FakeTree(goneAfterBytes = CONTENT.size.toLong())
                val destination = SafDownloadDestination.create(
                    store = tree,
                    treeUri = TREE,
                    displayName = "clip.mp4",
                    mimeType = "video/mp4",
                    temporaryId = "gone",
                )
                val url = server.url("/clip.mp4").toString()
                val engine = DirectTransferEngine(
                    client = OkHttpClient(),
                    policy = DirectTransferEngine.Policy(initialRetryDelayMillis = 0),
                )
                val queue = DownloadQueue(InMemoryTasks(), engine, backgroundScope, 1)
                queue.enqueue(plan("gone", url), metadata(url), destination, safSpec(destination))

                val failed = queue.tasks.first { tasks ->
                    tasks.singleOrNull()?.status == DownloadTaskStatus.FAILED
                }.single()

                assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, failed.failureReason)
                assertEquals(DownloadFailureStage.VERIFY, failed.failure?.stage)
                assertTrue(
                    failed.failure?.detail.toString(),
                    failed.failure?.detail.orEmpty().startsWith("FileNotFoundException"),
                )
                assertTrue("Nothing was published", tree.renamed.isEmpty())
            } finally {
                server.shutdown()
            }
        }

    private fun safSpec(destination: DownloadDestination) =
        DownloadDestinationSpec(DownloadDestinationKind.SAF_DOCUMENT, destination.recoveryUri)

    private fun plan(id: String, url: String = "http://localhost/clip.mp4") = DirectDownloadPlan(
        taskId = id,
        sourceUrl = url,
        suggestedFileName = "clip.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/",
            userAgent = null,
            cookie = null,
        ),
        expectedBytes = TOTAL_BYTES,
        preferredSegmentCount = 1,
    )

    private fun metadata(url: String = "http://localhost/clip.mp4") = RemoteFileMetadata(
        finalUrl = url,
        totalBytes = TOTAL_BYTES,
        supportsByteRanges = true,
        entityTag = ETAG,
        lastModified = null,
        contentType = "video/mp4",
        suggestedFileName = "clip.mp4",
    )

    private fun rangeDispatcher(content: ByteArray): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val match = request.getHeader("Range")?.let(RANGE::matchEntire)
                ?: return MockResponse().setResponseCode(400)
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${content.size}")
                .setHeader("Content-Type", "video/mp4")
                .setHeader("ETag", ETAG)
                .setBody(Buffer().write(content.copyOfRange(start, end + 1)))
        }
    }

    /**
     * Writes [CONTENT] through the destination as the direct engine does: [PARTIAL_BYTES] and
     * the failure [outcome] gives for a call, or the rest, the length check and the commit.
     */
    private class WritingRunner(
        private val outcome: (call: Int) -> DownloadFailure?,
    ) : DirectTransferRunner {
        val destinations = mutableListOf<DownloadDestination>()
        val resumedFrom = mutableListOf<Long>()

        override suspend fun transfer(
            plan: DirectDownloadPlan,
            metadata: RemoteFileMetadata,
            destination: DownloadDestination,
            resumeFrom: DirectTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
        ): DirectTransferResult {
            val call = destinations.size
            destinations += destination
            val start = resumeFrom?.downloadedBytes ?: 0L
            resumedFrom += start
            if (start == 0L) destination.prepare(TOTAL_BYTES)
            val failure = outcome(call)
            val end = if (failure != null) start + PARTIAL_BYTES else TOTAL_BYTES
            destination.open().use { output ->
                output.write(start, CONTENT, start.toInt(), (end - start).toInt())
                output.sync()
            }
            val checkpoint = checkpointAt(end)
            onCheckpoint(checkpoint)
            if (failure != null) return DirectTransferResult.Failure(failure, checkpoint)
            check(destination.temporaryLength() == TOTAL_BYTES)
            destination.commit()
            return DirectTransferResult.Completed(TOTAL_BYTES, checkpoint)
        }
    }

    private class InMemoryTasks : DownloadTaskStore {
        private val gate = Mutex()
        private val records = LinkedHashMap<String, StoredDownloadTask>()

        override suspend fun loadAll(): List<StoredDownloadTask> =
            gate.withLock { records.values.toList() }

        override suspend fun save(task: StoredDownloadTask) {
            gate.withLock { records[task.id] = task }
        }

        override suspend fun delete(id: String) {
            gate.withLock { records.remove(id) }
        }

        suspend fun snapshot(): List<StoredDownloadTask> = loadAll()
    }

    private data class Created(val treeUri: String, val name: String, val mimeType: String)

    private class Document(var name: String) {
        private var data = ByteArray(0)
        var length: Long = 0

        @Synchronized
        fun write(position: Long, buffer: ByteArray, offset: Int, byteCount: Int) {
            val end = position + byteCount
            if (end > data.size) data = data.copyOf(end.toInt())
            buffer.copyInto(data, position.toInt(), offset, offset + byteCount)
            length = maxOf(length, end)
        }

        @Synchronized
        fun resize(newLength: Long) {
            data = data.copyOf(newLength.toInt())
            length = newLength
        }

        @Synchronized
        fun bytes(): ByteArray = data.copyOf(length.toInt())
    }

    /**
     * A document tree as a SAF provider keeps it: a new document has its file at once and a
     * rename gives a new address. Once [goneAfterBytes] bytes were written the folder is gone:
     * every later call fails as Android's providers do for a missing file.
     */
    private class FakeTree(private val goneAfterBytes: Long = Long.MAX_VALUE) :
        PublicContentStore {
        val documents = LinkedHashMap<String, Document>()
        val created = mutableListOf<Created>()
        val renamed = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        private var written = 0L
        private val gone: Boolean get() = written >= goneAfterBytes

        override fun createPendingMedia(
            displayName: String,
            mimeType: String,
            relativePath: String,
        ): String = error("Not a MediaStore download")

        override fun publishPendingMedia(uri: String) = error("Not a MediaStore download")
        override fun deletePendingMedia(uri: String) = error("Not a MediaStore download")
        override fun renamePendingMedia(uri: String, extension: String, mimeType: String) =
            error("Not a MediaStore download")

        override fun createTemporaryDocument(
            treeUri: String,
            temporaryDisplayName: String,
            mimeType: String,
        ): String {
            if (gone) throw IOException("Cannot create temporary SAF document")
            created += Created(treeUri, temporaryDisplayName, mimeType)
            return "content://tree.test/document/${created.size}".also { uri ->
                documents[uri] = Document(temporaryDisplayName)
            }
        }

        override fun renameTemporaryDocument(
            temporaryDocumentUri: String,
            finalDisplayName: String,
        ): String {
            val document = existing(temporaryDocumentUri)
            documents.remove(temporaryDocumentUri)
            document.name = finalDisplayName
            renamed += temporaryDocumentUri
            return "$temporaryDocumentUri-final".also { documents[it] = document }
        }

        override fun deleteTemporaryDocument(uri: String) {
            existing(uri)
            documents.remove(uri)
            deleted += uri
        }

        override fun length(uri: String): Long = existing(uri).length

        override fun open(uri: String): SeekableDownloadOutput {
            val document = existing(uri)
            return object : SeekableDownloadOutput {
                override fun write(position: Long, buffer: ByteArray, offset: Int, byteCount: Int) {
                    if (gone) throw IOException("EIO (I/O error)")
                    document.write(position, buffer, offset, byteCount)
                    written += byteCount
                }

                override fun setLength(length: Long) = document.resize(length)
                override fun sync() = Unit
                override fun close() = Unit
            }
        }

        private fun existing(uri: String): Document {
            if (gone) throw FileNotFoundException("Missing file for $uri")
            return documents[uri] ?: throw FileNotFoundException("Missing file for $uri")
        }
    }

    private companion object {
        const val TREE = "content://tree.test/tree/movies"
        const val ETAG = "\"fixture\""
        val CONTENT = ByteArray(10) { index -> (index * 7).toByte() }
        val TOTAL_BYTES = CONTENT.size.toLong()
        const val PARTIAL_BYTES = 4L
        val RANGE = Regex("bytes=(\\d+)-(\\d+)")
        val STORAGE_FAILURE = DownloadFailure(
            reason = DownloadFailureReason.STORAGE_UNAVAILABLE,
            stage = DownloadFailureStage.WRITE_FILE,
            detail = "IOException: EIO (I/O error)",
        )
        val NETWORK_FAILURE = DownloadFailure(
            reason = DownloadFailureReason.NETWORK,
            stage = DownloadFailureStage.READ_SOURCE,
            detail = "SocketException: Connection reset",
        )

        fun checkpointAt(downloaded: Long) = DirectTransferCheckpoint(
            totalBytes = TOTAL_BYTES,
            entityTag = ETAG,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, TOTAL_BYTES - 1, downloaded)),
        )
    }
}
