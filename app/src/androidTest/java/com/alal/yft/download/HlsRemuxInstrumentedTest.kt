package com.alal.yft.download

import android.annotation.TargetApi
import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.HlsTransferEngine
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.PublishedFile
import com.alal.yft.core.model.media.BrowserRequestContext
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D1 on the CI emulator (API 34) with the real MediaStore: the real [HlsTransferEngine] saves an
 * HLS download of MPEG-TS pieces (H.264 and AAC, made with FFmpeg; `assets/hls`) into a pending
 * row as an MP4 that Android's own MediaExtractor reads, and keeps pieces it cannot remux
 * (MPEG-2 video) as a `.ts` row, its name and type changed in one update. MediaStore keeps
 * only a type Android knows and otherwise takes the name's: `video/mp2t` is stored as
 * Android's own `video/mp2ts` (D1, CI on API 34).
 * There is no network: an application interceptor answers every request from memory. Each test
 * deletes the row it created, also when it fails.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@TargetApi(Build.VERSION_CODES.Q)
class HlsRemuxInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resolver: ContentResolver = instrumentation.targetContext.contentResolver
    private val workspace = File(instrumentation.targetContext.cacheDir, "d1-${UUID.randomUUID()}")

    @After
    fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test
    fun mpegTsPiecesAreSavedAsAnMp4ThatMediaExtractorReads() = runBlocking<Unit> {
        val name = newName()
        val destination = MediaStoreDownloadDestination.create(resolver, "$name.mp4", VIDEO_MP4)
        val uri = Uri.parse(destination.recoveryUri)
        try {
            val result = engine("av-0.ts", "av-1.ts").transfer(plan(name), destination)

            assertTrue(result.toString(), result is HlsTransferResult.Completed)
            assertNull((result as HlsTransferResult.Completed).publishedAs)
            val row = requireNotNull(row(uri)) { "The published row is missing" }
            assertEquals(Row("$name.mp4", VIDEO_MP4, pending = false), row)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(instrumentation.targetContext, uri, null)
                val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                assertEquals(
                    listOf("video/avc", "audio/mp4a-latm"),
                    formats.map { it.getString(MediaFormat.KEY_MIME) },
                )
                assertEquals(160, formats[0].getInteger(MediaFormat.KEY_WIDTH))
                assertEquals(90, formats[0].getInteger(MediaFormat.KEY_HEIGHT))
                assertEquals(22_050, formats[1].getInteger(MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(20, samples(extractor, track = 0))
                assertEquals(45, samples(extractor, track = 1))
            } finally {
                extractor.release()
            }
        } finally {
            delete(uri)
        }
    }

    @Test
    fun piecesThatCannotBeRemuxedAreKeptAsATsRowOfTypeMp2t() = runBlocking<Unit> {
        val name = newName()
        val destination = MediaStoreDownloadDestination.create(resolver, "$name.mp4", VIDEO_MP4)
        val uri = Uri.parse(destination.recoveryUri)
        try {
            val result = engine("mpeg2.ts").transfer(plan(name), destination)

            assertTrue(result.toString(), result is HlsTransferResult.Completed)
            val completed = result as HlsTransferResult.Completed
            assertEquals(PublishedFile("$name.ts", "video/mp2t"), completed.publishedAs)
            val row = requireNotNull(row(uri)) { "The published row is missing" }
            assertEquals(Row("$name.ts", storedType(TRANSPORT_STREAM, "ts"), pending = false), row)
            assertArrayEquals(fixture("mpeg2.ts"), readAll(uri))
        } finally {
            delete(uri)
        }
    }

    /** Counts [track]'s samples from its start; the first one is a sync sample. */
    private fun samples(extractor: MediaExtractor, track: Int): Int {
        extractor.selectTrack(track)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        assertTrue(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
        var count = 0
        while (extractor.sampleTrackIndex == track) {
            count += 1
            extractor.advance()
        }
        extractor.unselectTrack(track)
        return count
    }

    private fun engine(vararg pieces: String): HlsTransferEngine =
        HlsTransferEngine(client = inMemoryClient(pieces.toList()), workspaceRoot = workspace)

    private fun plan(name: String) = HlsDownloadPlan(
        taskId = "d1-$name",
        playlistUrl = "$BASE_URL/index.m3u8",
        suggestedFileName = "$name.mp4",
        requestContext = BrowserRequestContext(pageUrl = null, userAgent = null, cookie = null),
        mimeType = VIDEO_MP4,
        remuxToMp4 = true,
    )

    /** The playlist of [pieces] and each piece, from memory. */
    private fun inMemoryClient(pieces: List<String>): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath.removePrefix("/d1/")
                val (type, body) = when (path) {
                    "index.m3u8" -> PLAYLIST to playlist(pieces).toByteArray()
                    in pieces -> TRANSPORT_STREAM to fixture(path)
                    else -> null to null
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (body == null) 404 else 200)
                    .message(if (body == null) "Not Found" else "OK")
                    .apply {
                        if (body != null) {
                            header("Content-Length", body.size.toString())
                            body(body.toResponseBody(requireNotNull(type).toMediaType()))
                        } else {
                            body(ByteArray(0).toResponseBody(null))
                        }
                    }
                    .build()
            },
        )
        .build()

    private fun playlist(pieces: List<String>): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:1")
        pieces.forEach { piece ->
            appendLine("#EXTINF:1.0,")
            appendLine(piece)
        }
        append("#EXT-X-ENDLIST")
    }

    private fun fixture(name: String): ByteArray {
        val text = instrumentation.context.assets.open("hls/$name.b64")
            .bufferedReader()
            .use { it.readText() }
        return android.util.Base64.decode(text, android.util.Base64.DEFAULT)
    }

    private fun row(uri: Uri): Row? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_PENDING,
        )
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val arguments = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            resolver.query(uri, projection, arguments, null)
        } else {
            @Suppress("DEPRECATION")
            resolver.query(MediaStore.setIncludePending(uri), projection, null, null, null)
        }
        return cursor?.use {
            if (!it.moveToFirst()) {
                null
            } else {
                Row(name = it.getString(0), mimeType = it.getString(1), pending = it.getInt(2) == 1)
            }
        }
    }

    private fun readAll(uri: Uri): ByteArray =
        requireNotNull(resolver.openInputStream(uri)) { "Cannot read $uri" }.use { it.readBytes() }

    private fun delete(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    /** D1: MediaProvider's rule — a type Android knows is kept, else the extension's is used. */
    private fun storedType(given: String, extension: String): String? {
        val types = MimeTypeMap.getSingleton()
        return if (types.hasMimeType(given)) given else types.getMimeTypeFromExtension(extension)
    }

    private fun newName(): String = "yft-d1-${UUID.randomUUID().toString().take(8)}"

    private data class Row(val name: String?, val mimeType: String?, val pending: Boolean)

    private companion object {
        const val VIDEO_MP4 = "video/mp4"
        const val PLAYLIST = "application/vnd.apple.mpegurl"
        const val TRANSPORT_STREAM = "video/mp2t"
        const val BASE_URL = "https://media.example.test/d1"
    }
}
