package com.alal.yft.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
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
import java.io.File
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The save path on Android 9 (API 28), where a public Downloads row needs no permission because
 * there is none to write to: YFT saves in its own storage (`noBackupFilesDir/downloads`). The real
 * provider, the real engine and a real MP4 from the test assets: the file appears under its name
 * with every byte of the download, its bytes are the file the site served, Android's own reader
 * opens it (so the Library can play it), and the same partial file is opened again after the app
 * closed, where the bytes it kept are.
 *
 * Runs in CI on an API 28 emulator (`scripts/ci-emulator-smoke.sh` runs it there) and on any
 * device of that version. Nothing here needs a network: an interceptor answers in memory.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P)
class AppPrivateSaveInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val root = File(context.noBackupFilesDir, AndroidDownloadDestinationProvider.APP_PRIVATE_DIRECTORY)
    private val created = mutableListOf<File>()

    @After
    fun cleanUp() {
        created.forEach { file -> runCatching { file.delete() } }
        created.clear()
    }

    @Test
    fun aDownloadIsSavedInAppStorageWithEveryByteAndOpensAsMedia() = runBlocking<Unit> {
        val content = asset(CLIP)
        val ranges = mutableListOf<String?>()
        val name = newName()
        val prepared = AndroidDownloadDestinationProvider(context).prepare(
            fileName = name,
            mimeType = VIDEO_MP4,
            location = DownloadLocation.APP_STORAGE,
        )
        // Android 9 has no public Downloads row to write: the file stays in YFT's storage.
        assertEquals(DownloadDestinationKind.APP_PRIVATE, prepared.spec.kind)
        assertNotNull("a closed download needs its partial file's name", prepared.partialName)

        val engine = DirectTransferEngine(client = inMemoryClient(content, ranges))
        val result = engine.transfer(
            plan = DirectDownloadPlan(
                taskId = "p28-${UUID.randomUUID()}",
                sourceUrl = SOURCE_URL,
                suggestedFileName = name,
                requestContext = BrowserRequestContext(
                    pageUrl = null,
                    userAgent = null,
                    cookie = null,
                ),
                mimeType = VIDEO_MP4,
                expectedBytes = content.size.toLong(),
                preferredSegmentCount = 2,
            ),
            metadata = RemoteFileMetadata(
                finalUrl = SOURCE_URL,
                totalBytes = content.size.toLong(),
                supportsByteRanges = true,
                entityTag = ETAG,
                lastModified = null,
                contentType = VIDEO_MP4,
                suggestedFileName = name,
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
        val saved = File(root, name)
        created += saved
        assertTrue("the file is not in ${root.name}: ${root.list()?.toList()}", saved.isFile)
        assertEquals(content.size.toLong(), saved.length())
        assertArrayEquals(content, saved.readBytes())
        // The Library plays it: Android's own reader opens the saved file.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(saved.absolutePath)
            assertTrue(
                "no duration read from $name",
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0")
                    .toLong() > 0,
            )
        } finally {
            retriever.release()
        }
        // No partial file is left behind.
        assertFalse(root.listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    @Test
    fun theSamePartialFileIsOpenedAgainAfterTheAppClosed() {
        val provider = AndroidDownloadDestinationProvider(context)
        val prepared = provider.prepare("closed-${UUID.randomUUID()}.mp4", VIDEO_MP4, DownloadLocation.APP_STORAGE)
        val partial = File(root, requireNotNull(prepared.partialName))
        created += partial
        prepared.destination.prepare(4)
        prepared.destination.open().use { output ->
            output.write(0, byteArrayOf(1, 2, 3, 4), 0, 4)
            output.sync()
        }

        val reopened = requireNotNull(provider.reopen(SavedDestination.of(prepared)))

        assertEquals(4L, reopened.temporaryLength())
        prepared.destination.discard()
        assertNull(provider.reopen(SavedDestination.of(prepared)))
    }

    private fun asset(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private fun newName(): String = "yft-p28-${UUID.randomUUID().toString().take(8)}.mp4"

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

    private companion object {
        const val CLIP = "mp4/clip-video-aac.mp4"
        const val VIDEO_MP4 = "video/mp4"
        const val ETAG = "\"yft-p28\""
        const val SOURCE_URL = "https://media.example.test/p28/video.mp4"
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}
