package com.alal.yft.feature.library

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPrivateDownloadProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var provider: AppPrivateDownloadProvider
    private lateinit var root: File

    @Before
    fun setUp() {
        root = AppPrivateDownloads.root(context).apply { mkdirs() }
        File(root, "Clip.mp4").writeBytes(ByteArray(5) { it.toByte() })
        File(root, "Clip (1).mp4.0a1b.part").writeText("partial")
        File(context.noBackupFilesDir, "secret.txt").writeText("private")
        provider = Robolectric.setupContentProvider(
            AppPrivateDownloadProvider::class.java,
            AppPrivateDownloadProvider.authority(context),
        )
    }

    @Test
    fun servesAPublishedFileReadOnlyWithItsNameSizeAndType() {
        val uri = AppPrivateDownloadProvider.uriFor(context, "Clip.mp4")

        provider.query(uri, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "Clip.mp4",
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)),
            )
            assertEquals(5L, cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
        assertEquals("video/mp4", provider.getType(uri))
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertEquals(listOf<Byte>(0, 1, 2, 3, 4), bytes.toList())

        listOf("w", "rw", "rwt", "wa").forEach { mode ->
            try {
                provider.openFile(uri, mode)
                fail("mode $mode must be refused")
            } catch (_: SecurityException) {
                // expected
            }
        }
    }

    @Test
    fun nothingOutsideTheDownloadDirectoryOrStillStagingResolves() {
        val authority = AppPrivateDownloadProvider.authority(context)
        listOf(
            Uri.parse("content://$authority/..%2Fsecret.txt"),
            Uri.parse("content://$authority/..%2F..%2Fshared_prefs%2Fprefs.xml"),
            Uri.parse("content://$authority/nested/Clip.mp4"),
            Uri.parse("content://$authority/"),
            AppPrivateDownloadProvider.uriFor(context, "Clip (1).mp4.0a1b.part"),
            Uri.parse("content://another.authority/Clip.mp4"),
        ).forEach { uri ->
            assertNull("query must refuse $uri", provider.query(uri, null, null, null, null))
            assertNull("type must refuse $uri", provider.getType(uri))
            try {
                provider.openFile(uri, "r")
                fail("open must refuse $uri")
            } catch (_: FileNotFoundException) {
                // expected
            }
        }
    }

    @Test
    fun writesAreRefused() {
        val uri = AppPrivateDownloadProvider.uriFor(context, "Clip.mp4")
        listOf<() -> Unit>(
            { provider.insert(uri, ContentValues()) },
            { provider.update(uri, ContentValues(), null, null) },
            { provider.delete(uri, null, null) },
        ).forEach { write ->
            try {
                write()
                fail("writes must be refused")
            } catch (_: UnsupportedOperationException) {
                // expected
            }
        }
        assertTrue(File(root, "Clip.mp4").isFile)
    }

    @Test
    fun manifestKeepsTheProviderPrivateWithPerUriGrants() {
        val info = context.packageManager.resolveContentProvider(
            AppPrivateDownloadProvider.authority(context),
            PackageManager.GET_META_DATA,
        )!!

        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
    }
}
