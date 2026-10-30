package com.alal.yft.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
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

/** v1.0.2 item 28: YFT's access to the folder picked in Android's folder picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSaveFoldersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folders = AndroidSaveFolders(context)

    @Before
    fun setUp() {
        FakeTree.install()
    }

    @Test
    fun `a picked folder is kept with read and write access under its own name`() {
        FakeTree.folder(context, "Movies")
        val tree = FakeTree.treeUri("Movies")

        val folder = folders.take(tree.toString())!!

        assertEquals("Movies", folder.name)
        assertEquals(tree.toString(), folder.treeUri)
        val access = context.contentResolver.persistedUriPermissions.single()
        assertEquals(tree, access.uri)
        assertTrue(access.isReadPermission)
        assertTrue(access.isWritePermission)
        assertTrue(folders.usable(folder))
    }

    @Test
    fun `choosing another folder keeps the access to the first one`() {
        FakeTree.folder(context, "Movies")
        FakeTree.folder(context, "Music")

        folders.take(FakeTree.treeUri("Movies").toString())!!
        val second = folders.take(FakeTree.treeUri("Music").toString())!!

        assertEquals("Music", second.name)
        assertEquals(
            setOf(FakeTree.treeUri("Movies"), FakeTree.treeUri("Music")),
            context.contentResolver.persistedUriPermissions.map { it.uri }.toSet(),
        )
    }

    @Test
    fun `a folder YFT cannot make files in is refused`() {
        FakeTree.folder(context, "ReadOnly card")
        val document = DocumentsContract.buildDocumentUri(FakeTree.AUTHORITY, "primary:YFT")

        assertNull(folders.take(FakeTree.treeUri("ReadOnly card").toString()))
        assertNull(folders.take(FakeTree.treeUri("Missing").toString()))
        assertNull(folders.take(document.toString()))
        assertNull(folders.take("file:///storage/emulated/0/Download"))
        assertNull(folders.take("content://com.example.gone/tree/primary%3AYFT"))
    }

    @Test
    fun `a folder whose access was taken back or that was deleted cannot be used`() {
        val directory = FakeTree.folder(context)
        val folder = folders.take(FakeTree.treeUri().toString())!!

        FakeTree.revoke(context, folder.treeUri)
        assertFalse(folders.usable(folder))

        folders.take(folder.treeUri)
        assertTrue(folders.usable(folder))

        directory.deleteRecursively()
        assertFalse(folders.usable(folder))
        assertNull(folders.entries(folder))
    }

    @Test
    fun `entries are the folder's top level with their names, types and sizes`() {
        val directory = FakeTree.folder(context)
        File(directory, "Clip.mp4").writeBytes(ByteArray(7))
        File(directory, "Album").mkdirs()
        File(directory, "Album/Inner.mp4").writeText("inner")
        val folder = folders.take(FakeTree.treeUri().toString())!!

        val entries = folders.entries(folder)!!

        assertEquals(listOf("Album", "Clip.mp4"), entries.map(FolderEntry::name))
        val album = entries.first()
        assertTrue(album.isDirectory)
        val clip = entries.last()
        assertFalse(clip.isDirectory)
        assertEquals("video/mp4", clip.mimeType)
        assertEquals(7L, clip.sizeBytes)
        assertEquals("primary:YFT/Clip.mp4", clip.documentId)
        assertEquals(
            DocumentsContract.buildDocumentUriUsingTree(
                Uri.parse(folder.treeUri),
                "primary:YFT/Clip.mp4",
            ).toString(),
            clip.uri,
        )
        assertFalse("the address stays out of logs", clip.toString().contains("content:"))
        assertFalse("the address stays out of logs", folder.toString().contains("content:"))
    }
}
