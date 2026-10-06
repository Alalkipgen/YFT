package com.alal.yft.thumbnail

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedDownloadThumbnailsTest {
    private val directory: File = Files.createTempDirectory("yft-saved").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val records = MutableStateFlow(emptySet<String>())
    private val asked = mutableListOf<String>()
    private val remote = object : RemoteThumbnails {
        override fun cached(url: String): ImageBitmap? = null

        override suspend fun load(url: String): ImageBitmap? {
            synchronized(asked) { asked += url }
            return if (url == PICTURE) testPicture(640, 360) else null
        }
    }
    private val encoded = mutableListOf<Int>()

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun theStartedDownloadsPictureIsSavedReadAndDeletedWithItsRecord() = runBlocking {
        val store = store()
        records.value = setOf("task-1", "task-2")

        store.saveFrom("task-1", PICTURE)
        val saved = File(directory, "task-1.jpg")
        waitFor { saved.isFile }
        assertEquals(listOf(640), encoded)
        assertNotNull(store.load("task-1"))
        assertNotNull(store.cached("task-1"))
        // The Library finds it by the published file.
        assertNotNull(store.loadForFile("content://media/1"))
        assertNull(store.loadForFile("content://media/2"))
        assertNull(store.load("task-2"))

        // Deleting the record (Downloads' Delete, Clear finished or the janitor) deletes it.
        records.value = setOf("task-2")
        waitFor { !saved.exists() }
        assertNull(store.cached("task-1"))
    }

    @Test
    fun aPictureThatDoesNotLoadSavesNothing() = runBlocking {
        val store = store()
        records.value = setOf("task-1")

        store.saveFrom("task-1", "https://img.example.test/gone.jpg")
        waitFor { asked.isNotEmpty() }
        Thread.sleep(100)
        assertEquals(0, directory.listFiles().orEmpty().size)
        assertNull(store.load("task-1"))
    }

    @Test
    fun onlyPlainIdsNameFilesAndOrphansGoAfterARestart() = runBlocking {
        val store = store()
        assertNull(store.file("../escape"))
        assertNull(store.file(""))
        store.saveFrom("../escape", PICTURE)
        directory.mkdirs()
        val orphan = File(directory, "gone.jpg").apply { writeBytes(byteArrayOf(1)) }
        val kept = File(directory, "task-3.jpg").apply { writeBytes(byteArrayOf(1)) }
        val fresh = File(directory, "new.jpg").apply { writeBytes(byteArrayOf(1)) }
        orphan.setLastModified(1_000)
        kept.setLastModified(1_000)

        store.keepOnly(setOf("task-3"), olderThanEpochMs = 2_000)
        assertFalse(orphan.exists())
        assertTrue(kept.exists())
        assertTrue("saved after the process started", fresh.exists())
        assertTrue(asked.isEmpty())
    }

    private fun store() = SavedDownloadThumbnails(
        directory = directory,
        remote = remote,
        fileOwner = { uri -> "task-1".takeIf { uri == "content://media/1" } },
        recordIds = records,
        scope = scope,
        ioDispatcher = Dispatchers.IO,
        encoder = { image ->
            synchronized(encoded) { encoded += image.width }
            byteArrayOf(1, 2, 3)
        },
        decoder = { file -> if (file.length() > 0) testPicture(320, 180) else null },
    )

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not met in time", condition())
    }

    private companion object {
        const val PICTURE = "https://i.ytimg.com/vi/AAAAAAAAAA1/hqdefault.jpg"
    }
}
