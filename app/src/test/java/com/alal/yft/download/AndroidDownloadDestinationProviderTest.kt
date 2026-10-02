package com.alal.yft.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
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
    fun `concurrent downloads of the same name use distinct partial files`() {
        var index = 0
        val provider = AndroidDownloadDestinationProvider(context) { "id-${index++}" }

        val first = provider.prepare("Same.mp4", "video/mp4")
        val second = provider.prepare("Same.mp4", "video/mp4")
        first.destination.prepare(null)
        second.destination.prepare(null)

        val names = File(context.noBackupFilesDir, "downloads")
            .listFiles()!!
            .map { it.name }
            .sorted()
        assertEquals(listOf("Same.mp4.id0.part", "Same.mp4.id1.part"), names)
    }
}
