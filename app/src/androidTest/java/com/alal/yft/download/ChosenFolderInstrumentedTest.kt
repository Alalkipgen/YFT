package com.alal.yft.download

import android.annotation.TargetApi
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.SaveFolder
import com.alal.yft.feature.library.AndroidLibraryRepository
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import java.io.IOException
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 item 28 on the CI emulator (API 34) with a real folder tree and no folder picker. The
 * tree is [TestTreeProvider], a documents provider in the test APK's own process, so each call
 * crosses processes and Android checks it as it checks a phone's storage; the access the picker
 * would give comes from [TestTreeAccessProvider]. YFT keeps that access as it does after the
 * picker ([AndroidSaveFolders.take]), the real [DirectTransferEngine] saves a ranged download
 * into the folder through a hidden document, the Library lists the folder's video and audio and
 * deletes them, and a folder YFT can no longer use sends the download to Download/YFT with the
 * notice. There is no network: an interceptor answers every request from memory. Each test
 * empties the folder and takes the access back.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@TargetApi(Build.VERSION_CODES.Q)
class ChosenFolderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver: ContentResolver = context.contentResolver
    private val access: Uri =
        Uri.parse("content://${instrumentation.context.packageName}.tree-access")
    private val folders = AndroidSaveFolders(context)
    private val provider = AndroidDownloadDestinationProvider(context)

    @Before
    fun emptyTheFolder() {
        call(TestTreeAccessProvider.RESET)
    }

    @After
    fun takeTheAccessBack() {
        runCatching { call(TestTreeAccessProvider.REVOKE) }
        runCatching { call(TestTreeAccessProvider.RESET) }
    }

    @Test
    fun aPickedFolderIsKeptWithItsNameAndUsable() {
        val folder = pick()

        assertEquals("YFT", folder.name)
        assertTrue(folders.usable(folder))
        assertTrue(
            resolver.persistedUriPermissions.any { it.uri.toString() == folder.treeUri },
        )
        assertEquals(emptyList<String>(), names(folder))
    }

    @Test
    fun theDirectEngineSavesARangedDownloadIntoTheFolderThroughAHiddenDocument() =
        runBlocking<Unit> {
            val folder = pick()
            val prepared = provider.prepare(NAME, VIDEO_MP4, DownloadLocation.CHOSEN_FOLDER, folder)
            assertEquals(DownloadDestinationKind.SAF_DOCUMENT, prepared.spec.kind)
            assertFalse(prepared.folderUnavailable)
            assertEquals(NAME, prepared.fileName)
            // Hidden while it is written; the phone's storage may add the type's extension.
            val hidden = names(folder).single()
            assertTrue(hidden, hidden.startsWith(".$NAME.yft-"))
            // A seekable file across processes, so the merges can write into it in place.
            assertNotNull(prepared.destination.openFileDescriptorOutput()?.also { it.close() })

            val content = pattern(3 * ONE_MIB)
            val ranges = Collections.synchronizedList(mutableListOf<String?>())
            val result = DirectTransferEngine(client = inMemoryClient(content, ranges)).transfer(
                plan = DirectDownloadPlan(
                    taskId = "item28-${UUID.randomUUID()}",
                    sourceUrl = SOURCE_URL,
                    suggestedFileName = NAME,
                    requestContext = BrowserRequestContext(
                        pageUrl = null,
                        userAgent = null,
                        cookie = null,
                    ),
                    mimeType = VIDEO_MP4,
                    expectedBytes = content.size.toLong(),
                    preferredSegmentCount = 4,
                ),
                metadata = RemoteFileMetadata(
                    finalUrl = SOURCE_URL,
                    totalBytes = content.size.toLong(),
                    supportsByteRanges = true,
                    entityTag = ETAG,
                    lastModified = null,
                    contentType = VIDEO_MP4,
                    suggestedFileName = NAME,
                ),
                destination = prepared.destination,
                resumeFrom = DirectTransferCheckpoint(
                    totalBytes = content.size.toLong(),
                    entityTag = ETAG,
                    lastModified = null,
                    segments = emptyList(),
                ),
            )

            assertTrue(result.toString(), result is DirectTransferResult.Completed)
            assertEquals(4, ranges.size)
            assertEquals(listOf(NAME), names(folder))
            assertArrayEquals(content, readAll(Uri.parse(prepared.destination.publishedUri)))
            // The next download of that name gets a free one.
            val second = provider.prepare(NAME, VIDEO_MP4, DownloadLocation.CHOSEN_FOLDER, folder)
            assertEquals("yft-item28 (1).mp4", second.fileName)
            second.destination.discard()
            assertEquals(listOf(NAME), names(folder))
        }

    @Test
    fun theLibraryListsTheFolderVideoAndAudioAndDeletesThem() = runBlocking<Unit> {
        val folder = pick()
        val video = pattern(4_096)
        put(folder, "clip.mp4", VIDEO_MP4, video)
        put(folder, "song.mp3", "audio/mpeg", pattern(2_048))
        put(folder, "notes.txt", "text/plain", pattern(16))
        // Another download's hidden document, and a folder: neither is listed.
        put(folder, ".clip 2.mp4.yft-1.part", VIDEO_MP4, pattern(16))
        DocumentsContract.createDocument(
            resolver,
            folderDocument(folder),
            Document.MIME_TYPE_DIR,
            "Old",
        )
        val library = AndroidLibraryRepository(
            context = context,
            chosenFolder = { folder },
            folders = folders,
        )

        val listed = library.folderItems()
        assertEquals(listOf("clip.mp4", "song.mp3"), listed.map { it.displayName }.sorted())
        assertTrue(listed.toString(), listed.all { it.place == "YFT" })
        val clip = listed.single { it.displayName == "clip.mp4" }
        assertEquals(VIDEO_MP4, clip.mimeType)
        assertEquals(video.size.toLong(), clip.sizeBytes)
        // Open and Share hand this address to the player or the other app.
        assertArrayEquals(video, readAll(Uri.parse(clip.uri)))

        assertTrue(library.delete(clip))
        assertFalse(names(folder).contains("clip.mp4"))
        // A file that is already gone counts as deleted.
        assertTrue(library.delete(clip))
        assertEquals(listOf("song.mp3"), library.folderItems().map { it.displayName })
    }

    @Test
    fun aFolderWhoseAccessIsGoneSendsTheDownloadToDownloadYftWithTheNotice() {
        val folder = pick()
        call(TestTreeAccessProvider.REVOKE)

        assertFalse(folders.usable(folder))
        val prepared = provider.prepare(NAME, VIDEO_MP4, DownloadLocation.CHOSEN_FOLDER, folder)
        try {
            assertEquals(DownloadDestinationKind.MEDIA_STORE, prepared.spec.kind)
            assertTrue(prepared.folderUnavailable)
        } finally {
            prepared.destination.discard()
        }
    }

    @Test
    fun aDeletedFolderIsNotUsedAndADownloadStillWritingThereFails() = runBlocking<Unit> {
        val folder = pick()
        val prepared = provider.prepare(NAME, VIDEO_MP4, DownloadLocation.CHOSEN_FOLDER, folder)
        assertEquals(DownloadDestinationKind.SAF_DOCUMENT, prepared.spec.kind)
        call(TestTreeAccessProvider.REMOVE)

        assertFalse(folders.usable(folder))
        assertNull(folders.entries(folder))
        assertThrows(IOException::class.java) { prepared.destination.open().close() }
        val library = AndroidLibraryRepository(
            context = context,
            chosenFolder = { folder },
            folders = folders,
        )
        assertEquals(emptyList<LibraryItem>(), library.folderItems())
        val next = provider.prepare(NAME, VIDEO_MP4, DownloadLocation.CHOSEN_FOLDER, folder)
        try {
            assertEquals(DownloadDestinationKind.MEDIA_STORE, next.spec.kind)
            assertTrue(next.folderUnavailable)
        } finally {
            next.destination.discard()
        }
    }

    /** The picker's part: the access, then YFT keeps it as it does after the picker. */
    private fun pick(): SaveFolder {
        val tree = requireNotNull(
            call(TestTreeAccessProvider.GRANT)?.getString(TestTreeAccessProvider.TREE),
        )
        return requireNotNull(folders.take(tree)) { "YFT could not keep the folder" }
    }

    private fun call(method: String) = resolver.call(access, method, context.packageName, null)

    private suspend fun AndroidLibraryRepository.folderItems(): List<LibraryItem> =
        items().filter { it.location == LibraryLocation.CHOSEN_FOLDER }

    private fun names(folder: SaveFolder): List<String> =
        requireNotNull(folders.entries(folder)) { "The folder cannot be read" }
            .map(FolderEntry::name)
            .sorted()

    private fun folderDocument(folder: SaveFolder): Uri {
        val tree = Uri.parse(folder.treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
    }

    private fun put(folder: SaveFolder, name: String, mimeType: String, bytes: ByteArray) {
        val uri = requireNotNull(
            DocumentsContract.createDocument(resolver, folderDocument(folder), mimeType, name),
        ) { "Cannot create $name" }
        requireNotNull(resolver.openOutputStream(uri)) { "Cannot write $name" }
            .use { it.write(bytes) }
    }

    private fun readAll(uri: Uri): ByteArray =
        requireNotNull(resolver.openInputStream(uri)) { "Cannot read $uri" }.use { it.readBytes() }

    /** Answers in memory: 200 with Content-Length, or 206 with Content-Range for a range. */
    private fun inMemoryClient(
        content: ByteArray,
        ranges: MutableList<String?>,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                val range = request.header("Range")
                ranges += range
                val match = range?.let(RANGE::matchEntire)
                val start = match?.groupValues?.get(1)?.toInt() ?: 0
                val end = match?.groupValues?.get(2)?.toInt() ?: (content.size - 1)
                val body = content.copyOfRange(start, end + 1)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (match == null) 200 else 206)
                    .message(if (match == null) "OK" else "Partial Content")
                    .header("Content-Type", VIDEO_MP4)
                    .header("Content-Length", body.size.toString())
                    .header("ETag", ETAG)
                    .apply {
                        if (match != null) {
                            header("Content-Range", "bytes $start-$end/${content.size}")
                        }
                    }
                    .body(body.toResponseBody(VIDEO_MP4.toMediaType()))
                    .build()
            },
        )
        .build()

    private fun pattern(size: Int): ByteArray = ByteArray(size) { index ->
        (index * 31 % 251).toByte()
    }

    private companion object {
        const val ONE_MIB = 1_024 * 1_024
        const val NAME = "yft-item28.mp4"
        const val VIDEO_MP4 = "video/mp4"
        const val ETAG = "\"yft-item28\""
        const val SOURCE_URL = "https://media.example.test/item28/video.mp4"
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}
