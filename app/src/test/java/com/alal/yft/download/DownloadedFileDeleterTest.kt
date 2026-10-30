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
        assertEquals(listOf(listOf(MEDIA_URI)), system.deleteRequests)
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

    @Test
    fun severalItemsThatNeedConsentShareOneSystemRequestOnAndroidElevenAndLater() = runTest {
        // v1.0.2 item 5b: the Library's select mode deletes the picked files together.
        val system = FakeSystem(refused = setOf(MEDIA_B, MEDIA_C))
        val document = SavedDownloadFile(DownloadDestinationKind.SAF_DOCUMENT, DOC_URI, "d.mp4")
        val files = listOf(media(), media(MEDIA_B), document, media(MEDIA_C))

        val result = deleter(system, Build.VERSION_CODES.R).deleteAll(files)

        assertEquals(listOf(listOf(MEDIA_B, MEDIA_C)), system.deleteRequests)
        assertEquals(
            FilesDeletion(
                deleted = listOf(files[0], files[2]),
                request = system.request,
                asking = listOf(files[1], files[3]),
            ),
            result,
        )
        assertEquals(listOf(MEDIA_URI), system.deletedMedia)
        assertEquals(listOf(DOC_URI), system.deletedDocuments)
        // Nothing to ask: no request at all.
        val owned = FakeSystem()
        assertEquals(
            FilesDeletion(deleted = listOf(files[0], files[1])),
            deleter(owned).deleteAll(files.take(2)),
        )
        assertTrue(owned.deleteRequests.isEmpty())
    }

    @Test
    fun androidTenAsksAboutOneItemAtATimeAndTheOthersWait() = runTest {
        val system = FakeSystem(
            refused = setOf(MEDIA_URI, MEDIA_B, MEDIA_C),
            unaskable = setOf(MEDIA_URI),
        )
        val files = listOf(media(), media(MEDIA_B), media(MEDIA_C))

        val result = deleter(system, Build.VERSION_CODES.Q).deleteAll(files)

        assertEquals(
            FilesDeletion(
                failed = listOf(files[0]),
                request = system.request,
                asking = listOf(files[1]),
                waiting = listOf(files[2]),
            ),
            result,
        )
        assertTrue(system.deleteRequests.isEmpty())
        assertEquals(2, system.recoverableAsked)
    }

    @Test
    fun withoutARequestTheRefusedItemsAreNotDeleted() = runTest {
        val files = listOf(media(), media(MEDIA_B))
        val expected = FilesDeletion(deleted = listOf(files[0]), failed = listOf(files[1]))

        assertEquals(expected, deleter(FakeSystem(refused = setOf(MEDIA_B)), 28).deleteAll(files))
        val broken = FakeSystem(refused = setOf(MEDIA_B), requestFails = true)
        assertEquals(expected, deleter(broken).deleteAll(files))
    }

    @Test
    fun theDefaultDeleteAllGoesOneAtATimeAndStopsAtTheFirstRequest() = runTest {
        val request = FakeSystem().request
        val answers = mapOf(
            "a" to FileDeletion.Deleted,
            "b" to FileDeletion.Failed,
            "c" to FileDeletion.NeedsConsent(request),
            "d" to FileDeletion.Deleted,
        )
        val tried = mutableListOf<String>()
        val deleter = DownloadedFileDeleter { file ->
            tried += file.displayName
            answers.getValue(file.displayName)
        }
        val files = answers.keys.map { name ->
            SavedDownloadFile(DownloadDestinationKind.APP_PRIVATE, null, name)
        }

        val result = deleter.deleteAll(files)

        assertEquals(listOf("a", "b", "c"), tried)
        assertEquals(
            FilesDeletion(
                deleted = listOf(files[0]),
                failed = listOf(files[1]),
                request = request,
                asking = listOf(files[2]),
                waiting = listOf(files[3]),
            ),
            result,
        )
    }

    private fun deleter(system: FakeSystem, sdkInt: Int = Build.VERSION_CODES.VANILLA_ICE_CREAM) =
        AndroidDownloadedFileDeleter(system, sdkInt, Dispatchers.Unconfined)

    private fun media(uri: String = MEDIA_URI) =
        SavedDownloadFile(DownloadDestinationKind.MEDIA_STORE, uri, "movie.mp4")

    private class FakeSystem(
        private val mediaRows: Int = 1,
        private val mediaExists: Boolean = false,
        private val mediaError: SecurityException? = null,
        private val recoverable: Boolean = true,
        private val documentDeleted: Boolean = true,
        private val documentExists: Boolean = false,
        private val documentError: Exception? = null,
        private val appFile: File? = null,
        /** v1.0.2 item 5b: the items YFT may not delete on its own, by URI. */
        private val refused: Set<String> = emptySet(),
        /** Android 10: refused items without a user action. */
        private val unaskable: Set<String> = emptySet(),
        private val requestFails: Boolean = false,
    ) : FileDeleteSystem {
        val request: IntentSender = PendingIntent.getActivity(
            ApplicationProvider.getApplicationContext(),
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
        val deletedMedia = mutableListOf<String>()
        val deleteRequests = mutableListOf<List<String>>()
        val deletedDocuments = mutableListOf<String>()
        var recoverableAsked = 0

        override fun deleteMediaItem(uri: String): Int {
            mediaError?.let { throw it }
            if (uri in refused) throw SecurityException(uri)
            deletedMedia += uri
            return mediaRows
        }

        override fun mediaItemExists(uri: String): Boolean = mediaExists

        override fun mediaDeleteRequest(uris: List<String>): IntentSender {
            if (requestFails) throw UnsupportedOperationException("no request")
            deleteRequests += listOf(uris)
            return request
        }

        override fun recoverableAction(error: SecurityException): IntentSender? {
            recoverableAsked += 1
            return request.takeIf { recoverable && error.message !in unaskable }
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
        const val MEDIA_B = "content://media/external/downloads/43"
        const val MEDIA_C = "content://media/external/downloads/44"
        const val DOC_URI = "content://com.android.externalstorage.documents/document/a"
    }
}
