package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicDownloadDestinationTest {
    @Test
    fun `MediaStore item stays pending until commit and retains its URI`() {
        val store = FakePublicContentStore()
        val destination = MediaStoreDownloadDestination.create(
            store = store,
            displayName = "../clips/demo.mp4",
            mimeType = "video/mp4; charset=binary",
            relativePath = "/Download/YFT//",
        )

        assertEquals("content://media/pending/1", destination.recoveryUri)
        assertEquals(
            PendingMedia(
                displayName = "_clips_demo.mp4",
                mimeType = "video/mp4",
                relativePath = "Download/YFT/",
            ),
            store.pendingMedia.single(),
        )

        destination.prepare(expectedLength = 6)
        destination.open().use { output ->
            output.write(
                position = 1,
                buffer = byteArrayOf(1, 2, 3, 4),
                offset = 1,
                byteCount = 2,
            )
            output.sync()
        }

        assertEquals(6L, destination.temporaryLength())
        assertTrue(store.publishedMedia.isEmpty())

        destination.commit()
        destination.commit()
        destination.discard()

        assertEquals(listOf("content://media/pending/1"), store.publishedMedia)
        assertTrue(store.deletedMedia.isEmpty())
        assertEquals("content://media/pending/1", destination.publishedUri)
    }

    @Test
    fun `discard removes an unpublished MediaStore item`() {
        val store = FakePublicContentStore()
        val destination = MediaStoreDownloadDestination.create(
            store = store,
            displayName = "clip.mp4",
            mimeType = null,
        )

        destination.discard()
        destination.discard()

        assertEquals(listOf("content://media/pending/1"), store.deletedMedia)
        assertTrue(store.publishedMedia.isEmpty())
    }

    @Test
    fun `SAF download uses temporary name then exposes renamed document URI`() {
        val store = FakePublicContentStore()
        val destination = SafDownloadDestination.create(
            store = store,
            treeUri = "content://documents/tree/downloads",
            displayName = "folder/final.mp4",
            mimeType = "video/mp4",
            temporaryId = "task/42",
        )

        val created = store.temporaryDocuments.single()
        assertEquals("content://documents/tree/downloads", created.treeUri)
        assertEquals(".folder_final.mp4.yft-task42.part", created.displayName)
        assertEquals("video/mp4", created.mimeType)
        assertEquals("content://documents/temp/1", destination.recoveryUri)
        assertTrue(store.renames.isEmpty())

        destination.prepare(expectedLength = 8)
        destination.commit()
        destination.commit()
        destination.discard()

        assertEquals(
            listOf(
                Rename(
                    temporaryUri = "content://documents/temp/1",
                    finalDisplayName = "folder_final.mp4",
                ),
            ),
            store.renames,
        )
        assertEquals("content://documents/final/1", destination.publishedUri)
        assertTrue(store.deletedDocuments.isEmpty())
    }

    @Test
    fun `discard deletes SAF temporary document without publishing`() {
        val store = FakePublicContentStore()
        val destination = SafDownloadDestination.create(
            store = store,
            treeUri = "content://documents/tree/downloads",
            displayName = "final.mp4",
            mimeType = "video/mp4",
            temporaryId = "task-1",
        )

        destination.discard()
        destination.discard()

        assertEquals(listOf("content://documents/temp/1"), store.deletedDocuments)
        assertTrue(store.renames.isEmpty())
    }

    @Test
    fun `resume reuses existing SAF document instead of creating another`() {
        val store = FakePublicContentStore()
        val destination = SafDownloadDestination.resume(
            store = store,
            temporaryDocumentUri = "content://documents/temp/recovered",
            displayName = "recovered.mp4",
        )

        destination.prepare(expectedLength = 12)
        destination.commit()

        assertTrue(store.temporaryDocuments.isEmpty())
        assertEquals(
            Rename("content://documents/temp/recovered", "recovered.mp4"),
            store.renames.single(),
        )
    }

    /** P21: a Retry that starts over writes into a new pending row of the same name. */
    @Test
    fun `renew replaces the MediaStore row with a new empty one of the same name`() {
        val store = FakePublicContentStore()
        val destination = MediaStoreDownloadDestination.create(
            store = store,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
        )

        val renewed = requireNotNull(destination.renew())

        assertEquals(listOf("content://media/pending/1"), store.deletedMedia)
        assertEquals("content://media/pending/2", renewed.recoveryUri)
        assertEquals(store.pendingMedia[0], store.pendingMedia[1])
        renewed.prepare(expectedLength = 4)
        renewed.commit()
        assertEquals(listOf("content://media/pending/2"), store.publishedMedia)
    }

    @Test
    fun `renew replaces the SAF temporary document with a new one of the same name`() {
        val store = FakePublicContentStore()
        val destination = SafDownloadDestination.create(
            store = store,
            treeUri = "content://documents/tree/downloads",
            displayName = "final.mp4",
            mimeType = "video/mp4",
            temporaryId = "task-1",
        )

        val renewed = requireNotNull(destination.renew())

        assertEquals(listOf("content://documents/temp/1"), store.deletedDocuments)
        assertEquals("content://documents/temp/2", renewed.recoveryUri)
        assertEquals(store.temporaryDocuments[0], store.temporaryDocuments[1])
    }

    @Test
    fun `a reopened destination is kept as it is and nothing is deleted`() {
        val store = FakePublicContentStore()
        val media = MediaStoreDownloadDestination.resume(
            store = store,
            pendingItemUri = "content://media/pending/recovered",
        )
        val document = SafDownloadDestination.resume(
            store = store,
            temporaryDocumentUri = "content://documents/temp/recovered",
            displayName = "recovered.mp4",
        )

        assertNull(media.renew())
        assertNull(document.renew())
        assertTrue(store.deletedMedia.isEmpty())
        assertTrue(store.deletedDocuments.isEmpty())
        assertTrue(store.pendingMedia.isEmpty())
        assertTrue(store.temporaryDocuments.isEmpty())
    }

    @Test
    fun `a published destination is never renewed`() {
        val store = FakePublicContentStore()
        val destination = MediaStoreDownloadDestination.create(
            store = store,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
        )
        destination.prepare(expectedLength = 4)
        destination.commit()

        assertThrows(IllegalStateException::class.java) { destination.renew() }
        assertTrue(store.deletedMedia.isEmpty())
        assertEquals(1, store.pendingMedia.size)
    }

    @Test
    fun `renew empties the partial file of an app storage download`() {
        val folder = Files.createTempDirectory("renew").toFile()
        try {
            val partial = File(folder, "clip.mp4.part").apply { writeBytes(ByteArray(8)) }
            val destination = FileDownloadDestination(partial, File(folder, "clip.mp4"))

            val renewed = destination.renew()

            assertEquals(0L, renewed.temporaryLength())
            assertTrue(partial.isFile)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `unsafe relative path is rejected before creating public content`() {
        val store = FakePublicContentStore()

        assertThrows(IllegalArgumentException::class.java) {
            MediaStoreDownloadDestination.create(
                store = store,
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                relativePath = "Download/../Elsewhere",
            )
        }

        assertTrue(store.pendingMedia.isEmpty())
    }

    @Test
    fun `preallocation failure leaves public item unpublished`() {
        val store = FakePublicContentStore(failPreallocation = true)
        val destination = MediaStoreDownloadDestination.create(
            store = store,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
        )

        val failure = assertThrows(IOException::class.java) {
            destination.prepare(expectedLength = 1_024)
        }

        assertTrue(failure.message.orEmpty().contains("No space left"))
        assertTrue(store.publishedMedia.isEmpty())
        assertFalse(store.deletedMedia.contains(destination.recoveryUri))
    }

    @Test
    fun `a new MediaStore item has no file until prepare opens it for writing`() {
        val store = FakePublicContentStore()
        val destination = MediaStoreDownloadDestination.create(
            store = store,
            displayName = "fresh.mp4",
            mimeType = "video/mp4",
        )

        assertThrows(FileNotFoundException::class.java) { destination.temporaryLength() }

        destination.prepare(expectedLength = 1_024)

        assertEquals(1_024L, destination.temporaryLength())
    }

    /**
     * P20 (R1): the JVM twin of MediaStoreDownloadInstrumentedTest. A direct video download into
     * a new pending MediaStore row completes and is published; the old engine read the row's
     * length before prepare() and failed at 0 B with STORAGE_UNAVAILABLE.
     */
    @Test
    fun `a direct download into a new MediaStore item completes and is published`() = runTest {
        val content = ByteArray(3 * 1_024) { index -> (index % 251).toByte() }
        val server = MockWebServer()
        server.dispatcher = rangeDispatcher(content)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val store = FakePublicContentStore()
            val destination = MediaStoreDownloadDestination.create(
                store = store,
                displayName = "video.mp4",
                mimeType = "video/mp4",
            )
            val url = server.url("/video.mp4").toString()
            val engine = DirectTransferEngine(
                client = OkHttpClient(),
                policy = DirectTransferEngine.Policy(initialRetryDelayMillis = 0),
            )

            val result = engine.transfer(
                plan = DirectDownloadPlan(
                    taskId = "p20-task",
                    sourceUrl = url,
                    suggestedFileName = "video.mp4",
                    requestContext = BrowserRequestContext(
                        pageUrl = "https://page.example.test/",
                        userAgent = null,
                        cookie = null,
                    ),
                    expectedBytes = content.size.toLong(),
                    preferredSegmentCount = 4,
                ),
                metadata = RemoteFileMetadata(
                    finalUrl = url,
                    totalBytes = content.size.toLong(),
                    supportsByteRanges = true,
                    entityTag = ETAG,
                    lastModified = null,
                    contentType = "video/mp4",
                    suggestedFileName = "video.mp4",
                ),
                destination = destination,
                resumeFrom = DirectTransferCheckpoint(
                    totalBytes = content.size.toLong(),
                    entityTag = ETAG,
                    lastModified = null,
                    segments = emptyList(),
                ),
            )

            assertTrue(result.toString(), result is DirectTransferResult.Completed)
            assertEquals(listOf("content://media/pending/1"), store.publishedMedia)
            assertArrayEquals(content, store.bytes("content://media/pending/1"))
            assertEquals(4, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

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

    private data class PendingMedia(
        val displayName: String,
        val mimeType: String,
        val relativePath: String,
    )

    private data class TemporaryDocument(
        val treeUri: String,
        val displayName: String,
        val mimeType: String,
    )

    private data class Rename(
        val temporaryUri: String,
        val finalDisplayName: String,
    )

    /**
     * Behaves like Android's MediaStore and SAF providers: a new pending MediaStore row has no
     * file until its first "rw" open, so reading its length throws [FileNotFoundException], like
     * `openFileDescriptor(uri, "r")` does (P20, default on). A SAF document has its file at once.
     */
    private class FakePublicContentStore(
        private val failPreallocation: Boolean = false,
        private val pendingRowsStartWithoutFile: Boolean = true,
    ) : PublicContentStore {
        val pendingMedia = mutableListOf<PendingMedia>()
        val publishedMedia = mutableListOf<String>()
        val deletedMedia = mutableListOf<String>()
        val temporaryDocuments = mutableListOf<TemporaryDocument>()
        val renames = mutableListOf<Rename>()
        val deletedDocuments = mutableListOf<String>()
        private val outputs = mutableMapOf<String, MemoryOutputState>()

        override fun createPendingMedia(
            displayName: String,
            mimeType: String,
            relativePath: String,
        ): String {
            pendingMedia += PendingMedia(displayName, mimeType, relativePath)
            return "content://media/pending/${pendingMedia.size}".also {
                outputs[it] = MemoryOutputState(fileExists = !pendingRowsStartWithoutFile)
            }
        }

        override fun publishPendingMedia(uri: String) {
            publishedMedia += uri
        }

        override fun deletePendingMedia(uri: String) {
            deletedMedia += uri
            outputs.remove(uri)
        }

        override fun createTemporaryDocument(
            treeUri: String,
            temporaryDisplayName: String,
            mimeType: String,
        ): String {
            temporaryDocuments += TemporaryDocument(treeUri, temporaryDisplayName, mimeType)
            return "content://documents/temp/${temporaryDocuments.size}".also {
                outputs[it] = MemoryOutputState()
            }
        }

        override fun renameTemporaryDocument(
            temporaryDocumentUri: String,
            finalDisplayName: String,
        ): String {
            renames += Rename(temporaryDocumentUri, finalDisplayName)
            return "content://documents/final/${renames.size}"
        }

        override fun deleteTemporaryDocument(uri: String) {
            deletedDocuments += uri
            outputs.remove(uri)
        }

        override fun length(uri: String): Long? {
            val state = outputs[uri] ?: return null
            if (!state.fileExists) throw FileNotFoundException("open failed: ENOENT")
            return state.length
        }

        override fun open(uri: String): SeekableDownloadOutput {
            val state = outputs.getOrPut(uri, ::MemoryOutputState)
            state.fileExists = true
            return MemorySeekableOutput(state, failPreallocation)
        }

        fun bytes(uri: String): ByteArray = requireNotNull(outputs[uri]).content()
    }

    private class MemoryOutputState(var fileExists: Boolean = true) {
        var length: Long = 0
        var syncCount: Int = 0
        var data = ByteArray(0)

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
        fun content(): ByteArray = data.copyOf(length.toInt())
    }

    private class MemorySeekableOutput(
        private val state: MemoryOutputState,
        private val failPreallocation: Boolean,
    ) : SeekableDownloadOutput {
        override fun write(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            byteCount: Int,
        ) {
            require(position >= 0)
            require(offset >= 0 && byteCount >= 0 && offset <= buffer.size - byteCount)
            state.write(position, buffer, offset, byteCount)
        }

        override fun setLength(length: Long) {
            if (failPreallocation) throw IOException("No space left on device")
            state.resize(length)
        }

        override fun sync() {
            state.syncCount += 1
        }

        override fun close() = Unit
    }

    private companion object {
        const val ETAG = "\"p20-v1\""
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}
