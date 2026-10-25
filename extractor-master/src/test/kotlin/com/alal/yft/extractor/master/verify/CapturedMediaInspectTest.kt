package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.master.verify.SegmentIndexReaderTest.Companion.fixture
import com.alal.yft.extractor.master.verify.SegmentIndexReaderTest.Companion.ftyp
import com.alal.yft.extractor.master.verify.SegmentIndexReaderTest.Companion.sidx
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4: the bounded metadata read keeps keyframe cues; offline answers only. */
class CapturedMediaInspectTest {
    private val requests = mutableListOf<Pair<String, String?>>()

    @Test
    fun `an on-demand MP4 gives its sidx cues from the one prefix range`() = runBlocking {
        val bytes = ftyp() + sidx(listOf(2_000, 3_000, 1_000))
        val result = inspect(mp4("https://cdn.test/v/hd.mp4"), mapOf("hd.mp4" to bytes))
        assertEquals(6_000L, result.candidate.durationMillis)
        assertEquals(listOf(2_000L, 5_000L), result.fingerprint.cuesMillis)
        assertEquals(listOf("bytes=0-262143"), requests.map { it.second })
    }

    @Test
    fun `an HLS master is followed to its first playlist on the same origin`() = runBlocking {
        val result = inspect(
            hls("https://cdn.test/v/master.m3u8"),
            mapOf(
                "master.m3u8" to fixture("master.m3u8").toByteArray(),
                "ladder-1080.m3u8" to fixture("ladder-1080.m3u8").toByteArray(),
            ),
        )
        assertEquals(60_021L, result.candidate.durationMillis)
        assertEquals(15, result.fingerprint.cuesMillis.size)
        assertEquals(
            listOf("https://cdn.test/v/master.m3u8", "https://cdn.test/v/ladder-1080.m3u8"),
            requests.map { it.first },
        )
        assertTrue(requests.all { it.second == null })
    }

    @Test
    fun `a master pointing at another origin is not followed`() = runBlocking {
        val master = fixture("master.m3u8")
            .replace("ladder-1080.m3u8", "https://other.test/v/ladder-1080.m3u8")
        val result = inspect(
            hls("https://cdn.test/v/master.m3u8"),
            mapOf("master.m3u8" to master.toByteArray()),
        )
        assertNull(result.candidate.durationMillis)
        assertTrue(result.fingerprint.cuesMillis.isEmpty())
        assertEquals(listOf("https://cdn.test/v/master.m3u8"), requests.map { it.first })
    }

    @Test
    fun `a DRM key in the playlist marks the file protected`() = runBlocking {
        val result = inspect(
            hls("https://cdn.test/v/drm.m3u8"),
            mapOf("drm.m3u8" to fixture("drm.m3u8").toByteArray()),
        )
        assertEquals(true, result.candidate.drmHint)
        assertTrue(result.fingerprint.cuesMillis.isEmpty())
    }

    @Test
    fun `enrich keeps its contract and an http file is never read`() = runBlocking {
        val plain = mp4("http://cdn.test/v/hd.mp4")
        val result = inspect(plain, emptyMap())
        assertEquals(plain, result.candidate)
        assertTrue(requests.isEmpty())
    }

    private suspend fun inspect(
        candidate: MediaCandidate,
        answers: Map<String, ByteArray>,
    ): InspectedMedia {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.toString() to request.header("Range")
            val body = answers[request.url.encodedPath.substringAfterLast('/')]
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            if (body == null) builder.code(404).message("Not Found")
                .body(ByteArray(0).toResponseBody(null)).build()
            else builder.code(200).message("OK")
                .body(body.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        try {
            return CapturedMediaMetadata(client).inspect(candidate)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun mp4(url: String) = MediaCandidate(
        PAGE, url, setOf(CandidateSource.REQUEST), MediaKind.DIRECT, mimeType = "video/mp4",
        requestContext = BrowserRequestContext(PAGE, null, null),
    )

    private fun hls(url: String) = MediaCandidate(
        PAGE, url, setOf(CandidateSource.REQUEST), MediaKind.HLS,
        mimeType = "application/vnd.apple.mpegurl",
    )

    private companion object {
        const val PAGE = "https://page.test/watch"
    }
}
