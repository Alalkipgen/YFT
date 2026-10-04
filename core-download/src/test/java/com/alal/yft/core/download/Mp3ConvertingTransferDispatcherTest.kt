package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3ConvertingTransferDispatcherTest {
    private val root = Files.createTempDirectory("mp3-dispatch").toFile()
    private val workspaces = File(root, "download-mp3")
    private val published = File(root, "Song MP3 192 kbps.mp3")
    private val destination = FileDownloadDestination(File(root, "Song.mp3.part"), published)
    private val aac = ByteArray(4_096) { (it % 251).toByte() }
    private val mp3 = "ID3-and-frames".toByteArray()
    private val delegate = FakeDelegate(aac)
    private val transcoder = FakeTranscoder(mp3)
    private val dispatcher = Mp3ConvertingTransferDispatcher(delegate, transcoder, workspaces)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun otherPlansGoStraightToTheDelegate() = runBlocking {
        val plain = plan(mp3 = null)

        val result = dispatcher.transfer(plain, metadata(), destination, null, {}, {})

        assertTrue(result is QueueTransferResult.Completed)
        assertSame(destination, delegate.destinations.single())
        assertEquals(0, transcoder.calls)
        assertArrayEquals(aac, published.readBytes())
    }

    @Test
    fun mp3PlansDownloadTheAacFileConvertItAndPublishOnlyTheMp3() = runBlocking {
        val result = dispatcher.transfer(plan(), metadata(), destination, null, {}, {})

        val completed = result as QueueTransferResult.Completed
        assertEquals(mp3.size.toLong(), completed.bytesWritten)
        assertEquals(aac.size.toLong(), completed.checkpoint.downloadedBytes)
        assertFalse("AAC goes to a workspace", delegate.destinations.single() === destination)
        assertArrayEquals(aac, transcoder.sources.single())
        assertEquals(Mp3Encoding(192, "Song"), transcoder.encodings.single())
        assertArrayEquals(mp3, published.readBytes())
        assertFalse("The workspace is removed", workspace().exists())
    }

    @Test
    fun aFileThePhoneCannotDecodePublishesNothingAndFreesTheSpace() = runBlocking {
        transcoder.failWith = DownloadFailureReason.INCOMPATIBLE_TRACKS

        val result = dispatcher.transfer(plan(), metadata(), destination, null, {}, {})

        val failure = result as QueueTransferResult.Failure
        assertEquals(DownloadFailure(DownloadFailureReason.INCOMPATIBLE_TRACKS), failure.failure)
        assertEquals(0L, failure.checkpoint.downloadedBytes)
        assertFalse(published.exists())
        assertFalse(workspace().exists())
    }

    @Test
    fun aFullDiskKeepsTheAacFileSoTheRetryOnlyConverts() = runBlocking {
        transcoder.failWith = DownloadFailureReason.INSUFFICIENT_STORAGE
        val first = dispatcher.transfer(plan(), metadata(), destination, null, {}, {})
        val failure = first as QueueTransferResult.Failure
        assertEquals(aac.size.toLong(), failure.checkpoint.downloadedBytes)
        assertFalse(published.exists())

        transcoder.failWith = null
        val retry = dispatcher.transfer(plan(), metadata(), destination, failure.checkpoint, {}, {})

        assertTrue(retry is QueueTransferResult.Completed)
        assertEquals("The finished AAC file is not fetched again", 1, delegate.destinations.size)
        assertEquals(2, transcoder.calls)
        assertArrayEquals(mp3, published.readBytes())
    }

    @Test
    fun aFailedDownloadIsReturnedWithoutConverting() = runBlocking {
        delegate.failWith = DownloadFailureReason.NETWORK

        val result = dispatcher.transfer(plan(), metadata(), destination, null, {}, {})

        assertEquals(
            DownloadFailureReason.NETWORK,
            (result as QueueTransferResult.Failure).failure.reason,
        )
        assertEquals(0, transcoder.calls)
        assertFalse(published.exists())
    }

    @Test
    fun aResumeWithoutItsPartialFileStartsOver() = runBlocking {
        val stale = checkpoint(downloaded = 1_024)

        dispatcher.transfer(plan(), metadata(), destination, stale, {}, {})

        assertNull(delegate.resumes.single())
        assertArrayEquals(mp3, published.readBytes())
    }

    @Test
    fun discardRemovesTheWorkspace() = runBlocking {
        transcoder.failWith = DownloadFailureReason.STORAGE_UNAVAILABLE
        dispatcher.transfer(plan(), metadata(), destination, null, {}, {})
        assertTrue(workspace().isDirectory)

        dispatcher.discard(plan())

        assertFalse(workspace().exists())
        assertEquals(1, delegate.discards)
    }

    private fun workspace() =
        File(workspaces, DownloadWorkspaces.nameFor(DownloadWorkspaces.MP3_PREFIX, "task-1"))

    private fun plan(mp3: Mp3Encoding? = Mp3Encoding(192, "Song")) = DirectDownloadPlan(
        taskId = "task-1",
        sourceUrl = "https://rr1.example.test/audio.m4a?sig=private",
        suggestedFileName = "Song MP3 192 kbps.mp3",
        requestContext = BrowserRequestContext("https://example.test/watch", null, null),
        mimeType = if (mp3 == null) "audio/mp4" else Mp3Encoding.MIME_TYPE,
        mp3 = mp3,
    )

    private fun metadata() = RemoteFileMetadata(
        finalUrl = "https://rr1.example.test/audio.m4a",
        totalBytes = aac.size.toLong(),
        supportsByteRanges = true,
        entityTag = null,
        lastModified = null,
        contentType = "audio/mp4",
        suggestedFileName = "audio.m4a",
    )

    private fun checkpoint(downloaded: Long) = DirectTransferCheckpoint(
        totalBytes = aac.size.toLong(),
        entityTag = null,
        lastModified = null,
        segments = listOf(DownloadSegment(0, 0, aac.size - 1L, downloaded)),
    )

    /** Writes [bytes] into whatever destination it is given, like the direct engine. */
    private inner class FakeDelegate(private val bytes: ByteArray) : DownloadTransferDispatcher {
        val destinations = mutableListOf<DownloadDestination>()
        val resumes = mutableListOf<TransferCheckpoint?>()
        var failWith: DownloadFailureReason? = null
        var discards = 0

        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult {
            destinations += destination
            resumes += resumeFrom
            failWith?.let {
                return QueueTransferResult.Failure(DownloadFailure(it), checkpoint(0))
            }
            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val done = checkpoint(bytes.size.toLong())
            return QueueTransferResult.Completed(bytes.size.toLong(), done)
        }

        override suspend fun discard(plan: DownloadPlan) {
            discards += 1
        }
    }

    private class FakeTranscoder(private val output: ByteArray) : LocalMp3Transcoder {
        val sources = mutableListOf<ByteArray>()
        val encodings = mutableListOf<Mp3Encoding>()
        var failWith: DownloadFailureReason? = null
        val calls get() = sources.size

        override suspend fun transcode(
            source: File,
            output: File,
            encoding: Mp3Encoding,
        ): Mp3TranscodeResult {
            sources += source.readBytes()
            encodings += encoding
            output.writeBytes(this.output.copyOf(this.output.size / 2))
            failWith?.let { return Mp3TranscodeResult.Failure(it) }
            output.writeBytes(this.output)
            return Mp3TranscodeResult.Completed(this.output.size.toLong())
        }
    }
}
