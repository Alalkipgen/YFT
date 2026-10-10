package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Offline response-byte fixtures; never live playback or TLS evidence. */
class CapturedMediaMetadataTest {
    @Test
    fun anActualAudioOnlyHandlerCorrectsAMisleadingVideoContentType() = runBlocking {
        val result = enrich(movie("soun"))
        assertEquals("audio/mp4", result.mimeType)
        assertEquals(60_000L, result.durationMillis)
        assertNull(result.width)
    }

    @Test
    fun aVideoHandlerWithoutDecodedDimensionsRemainsAVideoCandidate() = runBlocking {
        val result = enrich(movie("vide"))
        assertEquals("video/mp4", result.mimeType)
        assertEquals(60_000L, result.durationMillis)
        assertNull(result.width)
    }

    @Test
    fun mixedVideoAndAudioTracksAreNotMisclassifiedAsAudioOnly() = runBlocking {
        assertEquals("video/mp4", enrich(movie("soun", "vide")).mimeType)
    }

    private suspend fun enrich(bytes: ByteArray): MediaCandidate {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("bytes=0-262143", chain.request().header("Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(bytes.toResponseBody("video/mp4".toMediaType())).build()
        }.build()
        try {
            val candidate = MediaCandidate(
                "https://page.test/watch", "https://cdn.test/fixture.mp4",
                setOf(CandidateSource.REQUEST), MediaKind.DIRECT, mimeType = "video/mp4",
            )
            return CapturedMediaMetadata(client).enrich(candidate)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun movie(vararg handlers: String): ByteArray {
        val mvhd = ByteArray(100)
        ByteBuffer.wrap(mvhd).putInt(12, 1_000).putInt(16, 60_000)
        val tracks = handlers.map { handler ->
            val hdlr = ByteArray(24)
            handler.toByteArray(Charsets.US_ASCII).copyInto(hdlr, 8)
            box("trak", box("mdia", box("hdlr", hdlr)))
        }.fold(ByteArray(0)) { all, track -> all + track }
        return box("moov", box("mvhd", mvhd) + tracks)
    }

    private fun box(type: String, bytes: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + bytes.size).putInt(8 + bytes.size)
            .put(type.toByteArray(Charsets.US_ASCII)).put(bytes).array()
}
