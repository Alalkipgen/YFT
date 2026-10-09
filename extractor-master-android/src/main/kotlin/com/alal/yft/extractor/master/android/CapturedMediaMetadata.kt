package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.MediaCandidate
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/** At most two 256-KiB ranges of a previously validated HTTPS MP4, normal TLS unchanged. */
class CapturedMediaMetadata(client: OkHttpClient) {
    private val http = client.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).retryOnConnectionFailure(false)
        .callTimeout(3, TimeUnit.SECONDS).build()

    suspend fun enrich(candidate: MediaCandidate): MediaCandidate {
        if (candidate.durationMillis != null && candidate.width != null &&
            candidate.height != null
        ) return candidate
        if (candidate.mimeType?.substringBefore(';') != "video/mp4" ||
            !MasterBrowserSession.secure(candidate.mediaUrl)
        ) return candidate
        val prefix = range(candidate, 0) ?: return candidate
        var facts = CapturedMp4Facts.read(prefix)
        val length = candidate.contentLengthBytes
        if (facts?.durationMillis == null && facts?.protected != true &&
            length != null && length > MAX_BYTES
        ) {
            val suffix = range(candidate, length - MAX_BYTES)
            if (suffix != null) facts = CapturedMp4Facts.read(suffix) ?: facts
        }
        return candidate.copy(
            mimeType = if (facts?.audioOnly == true) "audio/mp4" else candidate.mimeType,
            durationMillis = facts?.durationMillis ?: candidate.durationMillis,
            width = facts?.width ?: candidate.width,
            height = facts?.height ?: candidate.height,
            drmHint = true.takeIf { facts?.protected == true } ?: candidate.drmHint,
        )
    }

    private suspend fun range(candidate: MediaCandidate, offset: Long): ByteArray? {
        val builder = Request.Builder().url(candidate.mediaUrl).get()
        candidate.requestContext.replayHeaders().forEach { (key, value) ->
            builder.header(key, value)
        }
        builder.header("Range", "bytes=$offset-${offset + MAX_BYTES - 1}")
        builder.header("Accept-Encoding", "identity")
        val response = suspendCancellableCoroutine<Response?> { continuation ->
            val call = http.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response)
                    else response.close()
                }
            })
        } ?: return null
        return try {
            withContext(Dispatchers.IO) {
                response.use { answer ->
                    if (answer.code !in setOf(200, 206) ||
                        offset > 0 && answer.code != 206
                    ) return@withContext null
                    if (answer.code == 206 && !answer.header("Content-Range").orEmpty()
                            .startsWith("bytes $offset-")
                    ) return@withContext null
                    val source = answer.body?.source() ?: return@withContext null
                    val buffer = Buffer()
                    while (buffer.size < MAX_BYTES) {
                        coroutineContext.ensureActive()
                        if (source.read(buffer, minOf(8_192, MAX_BYTES - buffer.size)) < 0) break
                    }
                    buffer.readByteArray()
                }
            }
        } catch (cancelled: CancellationException) {
            response.close()
            throw cancelled
        } catch (_: IOException) {
            response.close()
            null
        }
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024L
    }
}