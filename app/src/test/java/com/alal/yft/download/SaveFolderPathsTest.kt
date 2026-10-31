package com.alal.yft.download

import com.alal.yft.core.model.settings.SaveFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M1 (v1.0.2): a picked folder's readable path, from the folder picker's address. */
class SaveFolderPathsTest {
    @Test
    fun `a folder on the phone's storage is named by its path`() {
        val yft = folder("primary%3ADownload%2FYFT", "YFT")
        val test = folder("primary%3AMovies%2FTest", "Test")

        assertEquals("Download/YFT", SaveFolderPaths.label(yft))
        assertEquals("Movies/Test", SaveFolderPaths.label(test))
        assertTrue(SaveFolderPaths.onPhoneStorage(yft))
        assertTrue(SaveFolderPaths.onPhoneStorage(test))
        // A picker's document part after the tree changes nothing.
        val document = SaveFolder("$STORAGE/tree/primary%3AMovies/document/primary%3AMovies", "Movies")
        assertEquals("Movies", SaveFolderPaths.label(document))
    }

    @Test
    fun `a memory card's folder has its path but no measured space`() {
        val card = folder("1A2B-3C4D%3AMovies%2FHoliday", "Holiday")

        assertEquals("Movies/Holiday", SaveFolderPaths.label(card))
        assertFalse(SaveFolderPaths.onPhoneStorage(card))
    }

    @Test
    fun `other folders keep their name`() {
        val root = folder("primary%3A", "Phone")
        val cloud = SaveFolder("content://com.google.android.apps.docs.storage/tree/abc", "Drive")
        val broken = SaveFolder("not a uri", "Odd")

        assertEquals("Phone", SaveFolderPaths.label(root))
        assertNull(SaveFolderPaths.path(root))
        assertEquals("Drive", SaveFolderPaths.label(cloud))
        assertFalse(SaveFolderPaths.onPhoneStorage(cloud))
        assertEquals("Odd", SaveFolderPaths.label(broken))
        assertFalse(SaveFolderPaths.onPhoneStorage(broken))
    }

    private fun folder(id: String, name: String) = SaveFolder("$STORAGE/tree/$id", name)

    private companion object {
        const val STORAGE = "content://com.android.externalstorage.documents"
    }
}
