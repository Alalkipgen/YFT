package com.alal.yft.download

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P42: "Delete file" deletes by the record's destination, with fakes for each place. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadedFileDeleterTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun yftsMediaStoreItemIsDeleted() = runTest {
        val system = FakeSystem(mediaRows = 1)

        assertEquals(FileDeletion.Deleted, deleter(system).delete(media()))
        assertEquals(listOf(MEDIA_URI), system.deletedMedia)
    }

    @Test
    fun aMediaStoreItemThatIsGoneCountsAsDeleted() = runTest {
        val gone = FakeSystem(mediaRows = 0, mediaExists = false)
        val stays = FakeSystem(mediaRows = 0, mediaExists = true)

        assertEquals(FileDeletion.Deleted, deleter(gone).delete(media()))
        assertEquals(FileDeletion.Failed, deleter(stays).delete(media()))
    }

    @Test
    fun androidElevenAndLaterAskWithTheSystemDeleteRequest() = runTest {
        val system = FakeSystem(mediaError = SecurityException("not owner"))

        val result = deleter(system, Build.VERSION_CODES.R).delete(media())

        assertEquals(FileDeletion.NeedsConsent(system.request), result)
        assertEquals(listOf(MEDIA_URI), system.deleteRequests)
        assertEquals(0, system.recoverableAsked)
    }

    @Test
    fun androidTenAsksWithTheRecoverableAction() = runTest {
        val recoverable = FakeSystem(mediaError = SecurityException("recoverable"))
        val other = FakeSystem(mediaError = SecurityException("other"), recoverable = false)

        assertEquals(
            FileDeletion.NeedsConsent(recoverable.request),
            deleter(recoverable, Build.VERSION_CODES.Q).delete(media()),
        )
        assertEquals(1, recoverable.recoverableAsked)
        assertTrue(recoverable.deleteRequests.isEmpty())
        assertEquals(FileDeletion.Failed, deleter(other, Build.VERSION_CODES.Q).delete(media()))
        // Before Android 10 there is no request to make.
        assertEquals(
            FileDeletion.Failed,
            deleter(FakeSystem(mediaError = SecurityException("old")), 28).delete(media()),
        )
    }

    @Test
    fun aSafDocumentIsDeletedAndAMissingOneCountsAsDeleted() = runTest {
        val document = SavedDownloadFile(DownloadDestinationKind.SAF_DOCUMENT, DOC_URI, "a.mp4")

        val deleted = FakeSystem()
        assertEquals(FileDeletion.Deleted, deleter(deleted).delete(document))
        assertEquals(listOf(DOC_URI), deleted.deletedDocuments)
        val missing = FakeSystem(documentError = FileNotFoundException("gone"))
        assertEquals(FileDeletion.Deleted, deleter(missing).delete(document))
        val refused = FakeSystem(documentError = SecurityException("no grant"))
        assertEquals(FileDeletion.Failed, deleter(refused).delete(document))
        val kept = FakeSystem(documentDeleted = false, documentExists = true)
        assertEquals(FileDeletion.Failed, deleter(kept).delete(document))
    }

    @Test
    fun anAppPrivateFileIsDeletedAndAMissingOneCountsAsDeleted() = runTest {
        val file = directory.newFile("song.mp3").apply { writeText("audio") }
        val saved = SavedDownloadFile(DownloadDestinationKind.APP_PRIVATE, null, "song.mp3")

        assertEquals(FileDeletion.Deleted, deleter(FakeSystem(appFile = file)).delete(saved))
        assertFalse(file.exists())
        assertEquals(FileDeletion.Deleted, deleter(FakeSystem(appFile = file)).delete(saved))
        // A name that is not one of YFT's files is never touched.
        assertEquals(FileDeletion.Failed, deleter(FakeSystem(appFile = null)).delete(saved))
    }

    @Test
    fun aRecordWithoutAnAddressIsNotDeleted() = runTest {
        val noUri = SavedDownloadFile(DownloadDestinationKind.MEDIA_STORE, null, "a.mp4")

        assertEquals(FileDeletion.Failed, deleter(FakeSystem(mediaRows = 1)).delete(noUri))
    }

    private fun deleter(system: FakeSystem, sdkInt: Int = Build.VERSION_CODES.VANILLA_ICE_CREAM) =
        AndroidDownloadedFileDeleter(system, sdkInt, Dispatchers.Unconfined)

    private fun media() =
        SavedDownloadFile(DownloadDestinationKind.MEDIA_STORE, MEDIA_URI, "movie.mp4")

    private class FakeSystem(
        private val mediaRows: Int = 1,
        private val mediaExists: Boolean = false,
        private val mediaError: SecurityException? = null,
        private val recoverable: Boolean = true,
        private val documentDeleted: Boolean = true,
        private val documentExists: Boolean = false,
        private val documentError: Exception? = null,
        private val appFile: File? = null,
    ) : FileDeleteSystem {
        val request: IntentSender = PendingIntent.getActivity(
            ApplicationProvider.getApplicationContext(),
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
        val deletedMedia = mutableListOf<String>()
        val deleteRequests = mutableListOf<String>()
        val deletedDocuments = mutableListOf<String>()
        var recoverableAsked = 0

        override fun deleteMediaItem(uri: String): Int {
            mediaError?.let { throw it }
            deletedMedia += uri
            return mediaRows
        }

        override fun mediaItemExists(uri: String): Boolean = mediaExists

        override fun mediaDeleteRequest(uri: String): IntentSender {
            deleteRequests += uri
            return request
        }

        override fun recoverableAction(error: SecurityException): IntentSender? {
            recoverableAsked += 1
            return request.takeIf { recoverable }
        }

        override fun deleteDocument(uri: String): Boolean {
            documentError?.let { throw it }
            deletedDocuments += uri
            return documentDeleted
        }

        override fun documentExists(uri: String): Boolean =
            documentExists || documentError is SecurityException

        override fun appFile(displayName: String): File? = appFile
    }

    private companion object {
        const val MEDIA_URI = "content://media/external/downloads/42"
        const val DOC_URI = "content://com.android.externalstorage.documents/document/a"
    }
}
