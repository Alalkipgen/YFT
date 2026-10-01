package com.alal.yft.core.download

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class FakePublicContentStore(
        private val failPreallocation: Boolean = false,
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
                outputs[it] = MemoryOutputState()
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

        override fun length(uri: String): Long? = outputs[uri]?.length

        override fun open(uri: String): SeekableDownloadOutput {
            val state = outputs.getOrPut(uri, ::MemoryOutputState)
            return MemorySeekableOutput(state, failPreallocation)
        }
    }

    private class MemoryOutputState {
        var length: Long = 0
        var syncCount: Int = 0
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
            state.length = maxOf(state.length, position + byteCount)
        }

        override fun setLength(length: Long) {
            if (failPreallocation) throw IOException("No space left on device")
            state.length = length
        }

        override fun sync() {
            state.syncCount += 1
        }

        override fun close() = Unit
    }
}
