package com.alal.yft.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.SaveFolder
import com.alal.yft.testing.FakeTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.0.2 item 28: new downloads go to the folder picked in Android's folder picker, as a hidden
 * document named only after the check; a folder that can no longer be used gives way to the
 * default place, which the prepared destination reports for the row's notice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChosenFolderDestinationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folders = AndroidSaveFolders(context)
    private lateinit var directory: File
    private lateinit var folder: SaveFolder

    @Before
    fun setUp() {
        FakeTree.install()
        directory = FakeTree.folder(context)
        folder = folders.take(FakeTree.treeUri().toString())!!
    }

    @Test
    fun `a download in the chosen folder is a hidden document until it is committed`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "task-1" }

        val prepared = provider.inFolder("Clip.mp4")
        prepared.destination.prepare(4)
        prepared.destination.open().use { output ->
            output.write(0, byteArrayOf(1, 2, 3, 4), 0, 4)
            output.sync()
        }

        assertEquals(DownloadDestinationKind.SAF_DOCUMENT, prepared.spec.kind)
        assertEquals(prepared.destination.recoveryUri, prepared.spec.uri)
        assertFalse(prepared.folderUnavailable)
        assertEquals("Clip.mp4", prepared.fileName)
        assertEquals(listOf(".Clip.mp4.yft-task-1.part"), directory.list()!!.toList())
        assertEquals(4L, prepared.destination.temporaryLength())

        prepared.destination.commit()

        assertEquals(listOf("Clip.mp4"), directory.list()!!.toList())
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(directory, "Clip.mp4").readBytes().toList())
        assertTrue(prepared.destination.publishedUri!!.endsWith("primary%3AYFT%2FClip.mp4"))
    }

    @Test
    fun `a discarded download leaves nothing in the chosen folder`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "d1" }

        val prepared = provider.inFolder("Gone.mp4", mimeType = null)
        prepared.destination.prepare(null)
        prepared.destination.discard()

        assertTrue(directory.list()!!.isEmpty())
    }

    @Test
    fun `a renewed download makes its hidden document again under the planned name`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "r1" }
        val prepared = provider.inFolder("Clip.mp4")
        prepared.destination.prepare(3)

        val renewed = prepared.destination.renew()!!
        renewed.prepare(null)

        assertEquals(listOf(".Clip.mp4.yft-r1.part"), directory.list()!!.toList())
        assertEquals(0L, File(directory, ".Clip.mp4.yft-r1.part").length())
        renewed.commit()
        assertEquals(listOf("Clip.mp4"), directory.list()!!.toList())
    }

    @Test
    fun `names already in the folder or planned by another download are not taken`() {
        File(directory, "clip.MP4").writeText("someone else's")
        var index = 0
        val provider = AndroidDownloadDestinationProvider(context, folders) { "id${index++}" }

        val first = provider.inFolder("Clip.mp4")
        val second = provider.inFolder("Clip.mp4")

        assertEquals("Clip (1).mp4", first.fileName)
        assertEquals("Clip (2).mp4", second.fileName)
        first.destination.commit()
        second.destination.commit()
        assertEquals(
            listOf("Clip (1).mp4", "Clip (2).mp4", "clip.MP4"),
            directory.list()!!.sorted(),
        )
        assertEquals("someone else's", File(directory, "clip.MP4").readText())
    }

    @Test
    fun `a folder that can no longer be used sends the download to Download YFT and says so`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "x" }
        FakeTree.revoke(context, folder.treeUri)

        val revoked = provider.inFolder("Clip.mp4")

        assertEquals(DownloadDestinationKind.MEDIA_STORE, revoked.spec.kind)
        assertTrue(revoked.folderUnavailable)

        folders.take(folder.treeUri)
        directory.deleteRecursively()
        val deleted = provider.inFolder("Clip.mp4")

        assertEquals(DownloadDestinationKind.MEDIA_STORE, deleted.spec.kind)
        assertTrue(deleted.folderUnavailable)
        assertFalse(directory.exists())
    }

    @Test
    fun `the chosen place without a folder goes to Download YFT and says so`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "n" }

        val prepared = provider.prepare("Clip.mp4", "video/mp4", DownloadLocation.CHOSEN_FOLDER)

        assertEquals(DownloadDestinationKind.MEDIA_STORE, prepared.spec.kind)
        assertTrue(prepared.folderUnavailable)
    }

    @Test
    fun `the other places never use a folder that was chosen before`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "o" }

        val shared =
            provider.prepare("Clip.mp4", "video/mp4", DownloadLocation.SHARED_DOWNLOADS, folder)
        val private =
            provider.prepare("Own.mp4", "video/mp4", DownloadLocation.APP_STORAGE, folder)

        assertEquals(DownloadDestinationKind.MEDIA_STORE, shared.spec.kind)
        assertFalse(shared.folderUnavailable)
        assertEquals(DownloadDestinationKind.APP_PRIVATE, private.spec.kind)
        assertNull(private.spec.uri)
        assertFalse(private.folderUnavailable)
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test
    @Config(sdk = [28])
    fun `before Android 10 the chosen folder is used as on newer phones`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "seven" }

        val prepared = provider.inFolder("Old.mp4")
        prepared.destination.prepare(null)
        prepared.destination.commit()

        assertEquals(DownloadDestinationKind.SAF_DOCUMENT, prepared.spec.kind)
        assertEquals(listOf("Old.mp4"), directory.list()!!.toList())
    }

    @Test
    @Config(sdk = [28])
    fun `before Android 10 a folder that can no longer be used gives way to app storage`() {
        val provider = AndroidDownloadDestinationProvider(context, folders) { "p" }
        FakeTree.revoke(context, folder.treeUri)

        val prepared = provider.inFolder("Clip.mp4")

        assertEquals(DownloadDestinationKind.APP_PRIVATE, prepared.spec.kind)
        assertTrue(prepared.folderUnavailable)
    }

    private fun AndroidDownloadDestinationProvider.inFolder(
        fileName: String,
        mimeType: String? = "video/mp4",
    ) = prepare(fileName, mimeType, DownloadLocation.CHOSEN_FOLDER, folder)

    @Test
    fun `temporary names of other downloads count as taken, in any case`() {
        assertEquals("Clip.mp4", FolderNames.unique("Clip.mp4", listOf("Other.mp4", "Clip")))
        assertEquals("Clip (1).mp4", FolderNames.unique("Clip.mp4", listOf("CLIP.mp4")))
        assertEquals(
            "Clip (1).mp4",
            FolderNames.unique("Clip.mp4", listOf(".clip.mp4.yft-abc.part")),
        )
        // Some phones add the type's extension to the temporary name.
        assertEquals(
            "Clip (2).mp4",
            FolderNames.unique(
                "Clip.mp4",
                listOf(".Clip.mp4.yft-a.part.mp4", ".Clip (1).mp4.yft-b.part"),
            ),
        )
    }
}
