package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.generic.manifest.ManifestReader
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
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

/**
 * At most two 256-KiB reads of a previously validated HTTPS MP4 (ranges) or HLS playlist (the
 * playlist, then a master's first same-origin video playlist), normal TLS unchanged.
 */
class CapturedMediaMetadata(client: OkHttpClient) {
    private val http = client.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).retryOnConnectionFailure(false)
        .callTimeout(3, TimeUnit.SECONDS).build()

    suspend fun enrich(candidate: MediaCandidate): MediaCandidate {
        if (candidate.durationMillis != null && candidate.width != null &&
            candidate.height != null
        ) return candidate
        return inspect(candidate).candidate
    }

    /**
     * R4: the same bounded read, keeping the keyframe cues as a [MediaFingerprint]. An MP4 gives
     * its `sidx`; an HLS playlist gives its `#EXTINF` pieces (a master's first video playlist is
     * read only on the master's own origin, so no header goes to another site).
     */
    suspend fun inspect(candidate: MediaCandidate): InspectedMedia {
        if (!secure(candidate.mediaUrl)) return InspectedMedia.of(candidate)
        val mime = candidate.mimeType?.substringBefore(';')?.lowercase()
        if (candidate.kind == MediaKind.HLS || mime in HLS_MIMES) return playlist(candidate)
        if (mime != "video/mp4") return InspectedMedia.of(candidate)
        val prefix = range(candidate, 0) ?: return InspectedMedia.of(candidate)
        var facts = CapturedMp4Facts.read(prefix)
        val cues = facts?.cuesMillis.orEmpty()
        val length = candidate.contentLengthBytes
        if (facts?.durationMillis == null && facts?.protected != true &&
            length != null && length > MAX_BYTES
        ) {
            val suffix = range(candidate, length - MAX_BYTES)
            if (suffix != null) facts = CapturedMp4Facts.read(suffix) ?: facts
        }
        val updated = candidate.copy(
            mimeType = if (facts?.audioOnly == true) "audio/mp4" else candidate.mimeType,
            durationMillis = facts?.durationMillis ?: candidate.durationMillis,
            width = facts?.width ?: candidate.width,
            height = facts?.height ?: candidate.height,
            drmHint = true.takeIf { facts?.protected == true } ?: candidate.drmHint,
        )
        return InspectedMedia(
            updated,
            MediaFingerprint(updated.durationMillis, cues.ifEmpty { facts?.cuesMillis.orEmpty() }),
        )
    }

    private suspend fun playlist(candidate: MediaCandidate): InspectedMedia {
        val text = fetch(candidate.mediaUrl, candidate, offset = null)
            ?.toString(Charsets.UTF_8) ?: return InspectedMedia.of(candidate)
        var timeline = SegmentIndexReader.extinf(text)
        if (timeline == null) {
            val first = ManifestReader.hls(text, candidate.mediaUrl)
                ?.takeIf { it.master }?.firstPlaylistUrl
            if (first != null && secure(first) &&
                UrlPolicy.origin(first) == UrlPolicy.origin(candidate.mediaUrl)
            ) {
                timeline = fetch(first, candidate, offset = null)
                    ?.toString(Charsets.UTF_8)?.let(SegmentIndexReader::extinf)
            }
        }
        val updated = candidate.copy(
            durationMillis = candidate.durationMillis
                ?: timeline?.takeUnless { it.protected }?.durationMillis,
            drmHint = true.takeIf { timeline?.protected == true } ?: candidate.drmHint,
        )
        return InspectedMedia(
            updated,
            MediaFingerprint.of(timeline?.takeUnless { it.protected }, updated.durationMillis),
        )
    }

    private suspend fun range(candidate: MediaCandidate, offset: Long): ByteArray? =
        fetch(candidate.mediaUrl, candidate, offset)

    /** One GET of at most [MAX_BYTES]; a ranged one when [offset] is set. */
    private suspend fun fetch(url: String, candidate: MediaCandidate, offset: Long?): ByteArray? {
        val builder = Request.Builder().url(url).get()
        candidate.requestContext.replayHeaders().forEach { (key, value) ->
            builder.header(key, value)
        }
        if (offset != null) builder.header("Range", "bytes=$offset-${offset + MAX_BYTES - 1}")
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
                        offset == null && answer.code != 200 ||
                        offset != null && offset > 0 && answer.code != 206
                    ) return@withContext null
                    if (offset != null && answer.code == 206 &&
                        !answer.header("Content-Range").orEmpty()
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
        val HLS_MIMES = setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl")

        /** Same check the capture session applies; kept local so this stays Android-free. */
        fun secure(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null
        }.getOrDefault(false)
    }
}