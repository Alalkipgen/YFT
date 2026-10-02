package com.alal.yft.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.model.settings.DownloadLocation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the app-private fallback used before Android 10, where a pending MediaStore item is not
 * available. The MediaStore path needs a real content provider and is verified on device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidDownloadDestinationProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `staged bytes stay in a partial file until the download is committed`() {
        val provider = AndroidDownloadDestinationProvider(context) { "ABC-123" }

        val prepared = provider.prepare("Clip name.mp4", "video/mp4")
        prepared.destination.prepare(4)

        assertEquals(DownloadDestinationKind.APP_PRIVATE, prepared.spec.kind)
        assertNull(prepared.spec.uri)
        val root = File(context.noBackupFilesDir, "downloads")
        val partial = root.listFiles()!!.single()
        assertEquals("Clip name.mp4.abc123.part", partial.name)
        assertFalse(File(root, "Clip name.mp4").exists())

        prepared.destination.open().use { output ->
            output.write(0, byteArrayOf(1, 2, 3, 4), 0, 4)
            output.sync()
        }
        assertEquals(4L, prepared.destination.temporaryLength())

        prepared.destination.commit()

        assertTrue(File(root, "Clip name.mp4").isFile)
        assertFalse(partial.exists())
    }

    @Test
    fun `a discarded download leaves no partial file behind`() {
        val provider = AndroidDownloadDestinationProvider(context) { "d1" }

        val prepared = provider.prepare("Other.mp4", null)
        prepared.destination.prepare(null)
        prepared.destination.discard()

        val root = File(context.noBackupFilesDir, "downloads")
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `concurrent downloads of the same name reserve distinct names`() {
        var index = 0
        val provider = AndroidDownloadDestinationProvider(context) { "id-${index++}" }

        val first = provider.prepare("Same.mp4", "video/mp4")
        val second = provider.prepare("Same.mp4", "video/mp4")
        first.destination.prepare(null)
        second.destination.prepare(null)

        assertEquals("Same.mp4", first.fileName)
        assertEquals("Same (1).mp4", second.fileName)
        val names = File(context.noBackupFilesDir, "downloads")
            .listFiles()!!
            .map { it.name }
            .sorted()
        assertEquals(listOf("Same (1).mp4.id1.part", "Same.mp4.id0.part"), names)

        first.destination.commit()
        second.destination.commit()
        val published = File(context.noBackupFilesDir, "downloads").list()!!.sorted()
        assertEquals(listOf("Same (1).mp4", "Same.mp4"), published)
    }

    @Test
    fun `a name that is already published gets the next free suffix`() {
        val root = File(context.noBackupFilesDir, "downloads").apply { mkdirs() }
        File(root, "Clip.mp4").writeText("old")
        File(root, "Clip (1).mp4").writeText("older")
        val provider = AndroidDownloadDestinationProvider(context) { "n" }

        val prepared = provider.prepare("Clip.mp4", "video/mp4")
        prepared.destination.prepare(null)
        prepared.destination.commit()

        assertEquals("Clip (2).mp4", prepared.fileName)
        assertEquals("old", File(root, "Clip.mp4").readText())
        assertTrue(File(root, "Clip (2).mp4").isFile)
    }

    @Test
    @Config(sdk = [35])
    fun `app storage is used when the user chooses it on newer releases`() {
        val provider = AndroidDownloadDestinationProvider(context) { "p" }

        val prepared = provider.prepare("Private.mp4", "video/mp4", DownloadLocation.APP_STORAGE)

        assertEquals(DownloadDestinationKind.APP_PRIVATE, prepared.spec.kind)
        assertNull(prepared.destination.recoveryUri)
        val root = File(context.noBackupFilesDir, "downloads")
        assertEquals(listOf("Private.mp4.p.part"), root.list()!!.toList())
    }

    @Test
    fun `names without an extension are numbered too`() {
        val taken = setOf("notes", "notes (1).abc.part")

        assertEquals("notes (2)", AppPrivateNames.unique("notes", taken + "notes (1)"))
        assertEquals("notes (1)", AppPrivateNames.unique("notes", setOf("notes")))
        assertEquals("fresh.mp4", AppPrivateNames.unique("fresh.mp4", taken))
    }
}
