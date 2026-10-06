package com.alal.yft.download

import android.annotation.TargetApi
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P20 (R1) on the CI emulator (API 34) with the real MediaStore. A new pending row in
 * `Download/YFT/` has no file until its first "rw" open: reading its length must not fail,
 * out-of-order writes land where they belong, commit() publishes the row, discard() removes it,
 * and the real [DirectTransferEngine] saves a ranged download into a new row. There is no
 * network: an application interceptor answers every request from memory. Each test deletes the
 * row it created, also when it fails.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@TargetApi(Build.VERSION_CODES.Q)
class MediaStoreDownloadInstrumentedTest {
    private val resolver: ContentResolver =
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    @Test
    fun aNewPendingRowTakesWritesOutOfOrderAndIsPublishedInDownloadYft() {
        val destination = MediaStoreDownloadDestination.create(resolver, newName(), VIDEO_MP4)
        val uri = Uri.parse(destination.recoveryUri)
        try {
            val before = destination.temporaryLength()
            assertTrue("length before prepare: $before", before == null || before == 0L)
            val content = pattern(ONE_MIB)

            destination.prepare(ONE_MIB.toLong())
            destination.open().use { output ->
                QUARTER_ORDER.forEach { quarter ->
                    val start = quarter * QUARTER
                    output.write(start.toLong(), content, start, QUARTER)
                }
                output.sync()
            }
            assertEquals(ONE_MIB.toLong(), destination.temporaryLength())
            destination.commit()

            val row = requireNotNull(row(uri)) { "The published row is missing" }
            assertEquals(ONE_MIB.toLong(), row.size)
            assertFalse(row.pending)
            assertEquals(MediaStoreDownloadDestination.DEFAULT_RELATIVE_PATH, row.relativePath)
            assertArrayEquals(content, readAll(uri))
        } finally {
            delete(uri)
        }
    }

    @Test
    fun theDirectEngineSavesARangedDownloadIntoANewPendingRow() = runBlocking<Unit> {
        val content = pattern(3 * ONE_MIB)
        val ranges = Collections.synchronizedList(mutableListOf<String?>())
        val engine = DirectTransferEngine(client = inMemoryClient(content, ranges))
        val destination = MediaStoreDownloadDestination.create(resolver, newName(), VIDEO_MP4)
        val uri = Uri.parse(destination.recoveryUri)
        try {
            val result = engine.transfer(
                plan = DirectDownloadPlan(
                    taskId = "p20-${UUID.randomUUID()}",
                    sourceUrl = SOURCE_URL,
                    suggestedFileName = "yft-p20.mp4",
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
                    suggestedFileName = "yft-p20.mp4",
                ),
                destination = destination,
                // A new queue task starts with an empty checkpoint, like this one.
                resumeFrom = DirectTransferCheckpoint(
                    totalBytes = content.size.toLong(),
                    entityTag = ETAG,
                    lastModified = null,
                    segments = emptyList(),
                ),
            )

            assertTrue(result.toString(), result is DirectTransferResult.Completed)
            val completed = result as DirectTransferResult.Completed
            assertEquals(content.size.toLong(), completed.bytesWritten)
            assertEquals(4, ranges.size)
            assertTrue(ranges.toString(), ranges.all { it != null })
            assertFalse(requireNotNull(row(uri)) { "The published row is missing" }.pending)
            assertArrayEquals(content, readAll(uri))
        } finally {
            delete(uri)
        }
    }

    @Test
    fun discardRemovesThePendingRow() {
        val destination = MediaStoreDownloadDestination.create(resolver, newName(), VIDEO_MP4)
        val uri = Uri.parse(destination.recoveryUri)
        try {
            destination.prepare(QUARTER.toLong())
            assertNotNull(row(uri))

            destination.discard()

            assertNull(row(uri))
        } finally {
            delete(uri)
        }
    }

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

    private fun row(uri: Uri): Row? {
        val projection = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_PENDING,
            MediaStore.MediaColumns.RELATIVE_PATH,
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
                Row(
                    size = if (it.isNull(0)) null else it.getLong(0),
                    pending = it.getInt(1) == 1,
                    relativePath = it.getString(2),
                )
            }
        }
    }

    private fun readAll(uri: Uri): ByteArray =
        requireNotNull(resolver.openInputStream(uri)) { "Cannot read $uri" }.use { it.readBytes() }

    private fun delete(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    private fun newName(): String = "yft-p20-${UUID.randomUUID().toString().take(8)}.mp4"

    private fun pattern(size: Int): ByteArray = ByteArray(size) { index ->
        (index * 31 % 251).toByte()
    }

    private data class Row(
        val size: Long?,
        val pending: Boolean,
        val relativePath: String?,
    )

    private companion object {
        const val ONE_MIB = 1_024 * 1_024
        const val QUARTER = ONE_MIB / 4
        const val VIDEO_MP4 = "video/mp4"
        const val ETAG = "\"yft-p20\""
        const val SOURCE_URL = "https://media.example.test/p20/video.mp4"
        val QUARTER_ORDER = listOf(2, 0, 3, 1)
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}
