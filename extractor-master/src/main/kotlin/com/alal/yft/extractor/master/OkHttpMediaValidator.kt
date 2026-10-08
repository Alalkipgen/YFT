package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.generic.manifest.ManifestReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource

/**
 * HTTPS-only, no-cookie-jar media check. It reads 512 direct-file bytes, or a bounded manifest,
 * never downloads an entire video. Redirects are manual and never regain stripped credentials.
 * The injected client retains its normal certificate/hostname verification.
 */
class OkHttpMediaValidator(
    client: OkHttpClient,
    private val maxRedirects: Int = 5,
    private val maxManifestBytes: Long = 256 * 1024,
    private val timeoutMillis: Long = 10_000,
) : MasterMediaValidator {
    init {
        require(maxRedirects >= 0 && maxManifestBytes > 0 && timeoutMillis > 0)
    }

    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun validate(
        candidate: MediaCandidate,
        nowEpochMs: Long,
    ): ValidationResult {
        if (candidate.drmHint == true) return rejected(SiteExtractionFailure.DRM_PROTECTED)
        if (candidate.expiresAtEpochMs?.let { it <= nowEpochMs } == true) {
            return rejected(SiteExtractionFailure.EXPIRED_LINK)
        }
        if (UrlPolicy.secure(candidate.mediaUrl) == null) {
            return rejected(SiteExtractionFailure.UNSUPPORTED_URL)
        }
        return withContext(Dispatchers.IO) {
            val active = AtomicReference<Call?>()
            coroutineScope {
                val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        active.get()?.cancel()
                    }
                }
                try {
                    probe(candidate, nowEpochMs) {
                        active.set(it)
                        ensureActive()
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: IOException) {
                    ensureActive()
                    rejected(SiteExtractionFailure.NETWORK)
                } finally {
                    canceller.cancel()
                }
            }
        }
    }

    private fun probe(
        candidate: MediaCandidate,
        now: Long,
        active: (Call) -> Unit,
    ): ValidationResult {
        var current = candidate.mediaUrl.toHttpUrlOrNull()
            ?: return rejected(SiteExtractionFailure.UNSUPPORTED_URL)
        val original = current
        var credentialsAllowed = true
        val headers = candidate.requestContext.replayHeaders()
        val started = System.nanoTime()
        val budget = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        for (redirects in 0..maxRedirects) {
            if (!current.isHttps) return rejected(SiteExtractionFailure.UNSUPPORTED_URL)
            if (UrlPolicy.expiry(current.toString())?.let { it <= now } == true) {
                return rejected(SiteExtractionFailure.EXPIRED_LINK)
            }
            val remaining = budget - (System.nanoTime() - started)
            if (remaining <= 0) return rejected(SiteExtractionFailure.NETWORK)
            val request = Request.Builder().url(current).get().apply {
                val scoped = if (credentialsAllowed) headers else UrlPolicy.publicHeaders(headers)
                scoped.forEach { (name, value) -> header(name, value) }
                if (candidate.kind == MediaKind.DIRECT) header("Range", "bytes=0-511")
                header("Accept-Encoding", "identity")
            }.build()
            val call = http.newCall(request)
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
            active(call)
            call.execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    if (redirects == maxRedirects) {
                        return rejected(SiteExtractionFailure.RESPONSE_CHANGED)
                    }
                    val next = response.header("Location")?.let(current::resolve)
                        ?: return rejected(SiteExtractionFailure.MALFORMED_RESPONSE)
                    if (!next.isHttps) return rejected(SiteExtractionFailure.UNSUPPORTED_URL)
                    credentialsAllowed = credentialsAllowed &&
                        next.scheme == original.scheme && next.host == original.host &&
                        next.port == original.port
                    current = next
                } else {
                    if (response.code !in 200..299) {
                        return rejected(
                            when (response.code) {
                                410 -> SiteExtractionFailure.EXPIRED_LINK
                                429 -> SiteExtractionFailure.RATE_LIMITED
                                else -> SiteExtractionFailure.HTTP_STATUS
                            },
                        )
                    }
                    val body = response.body
                        ?: return rejected(SiteExtractionFailure.MALFORMED_RESPONSE)
                    val mime = body.contentType()?.toString()
                    if (mime?.startsWith("text/html", true) == true) {
                        return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
                    }
                    val kind = MediaUrlClassifier.classify(current.toString(), mime)
                        ?: return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
                    val manifest = kind == MediaKind.HLS || kind == MediaKind.DASH
                    val bytes = if (manifest) {
                        body.source().readAtMost(maxManifestBytes + 1)
                    } else {
                        val count = body.contentLength().takeIf { it >= 0 }?.coerceAtMost(512)
                            ?: 512
                        body.source().readAtMost(count)
                    }
                    if (bytes.isEmpty()) return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
                    if (manifest && bytes.size > maxManifestBytes) {
                        return rejected(SiteExtractionFailure.RESPONSE_TOO_LARGE)
                    }
                    val text = if (manifest) bytes.toString(Charsets.UTF_8) else ""
                    if (manifest && DRM.containsMatchIn(text)) {
                        return rejected(SiteExtractionFailure.DRM_PROTECTED)
                    }
                    if (manifest && text.contains("<!DOCTYPE", true)) {
                        return rejected(SiteExtractionFailure.MALFORMED_RESPONSE)
                    }
                    val facts = when (kind) {
                        MediaKind.HLS -> ManifestReader.hls(text, current.toString())
                        MediaKind.DASH -> ManifestReader.dash(text)
                        else -> null
                    }
                    if (manifest && facts == null) {
                        return rejected(SiteExtractionFailure.MALFORMED_RESPONSE)
                    }
                    val detectedMime = if (manifest) mime else directMime(bytes)
                        ?: return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
                    val range = response.header("Content-Range")
                    val length = if (response.code == 206) {
                        RANGE.matchEntire(range.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
                            ?: return rejected(SiteExtractionFailure.MALFORMED_RESPONSE)
                    } else {
                        body.contentLength().takeIf { it > 0 }
                    }
                    val replay = if (credentialsAllowed) candidate.requestContext else {
                        candidate.requestContext.copy(
                            cookie = null,
                            observedHeaders = UrlPolicy.publicHeaders(
                                candidate.requestContext.observedHeaders,
                            ),
                        )
                    }
                    return ValidationResult.Valid(
                        candidate.copy(
                            mediaUrl = current.toString(),
                            kind = kind,
                            mimeType = mime?.takeIf {
                                it.startsWith("video/") || it.startsWith("audio/") || manifest
                            } ?: candidate.mimeType ?: detectedMime,
                            contentLengthBytes = length.takeUnless { manifest },
                            requestContext = replay,
                            durationMillis = facts?.durationMillis ?: candidate.durationMillis,
                            width = facts?.width ?: candidate.width,
                            height = facts?.height ?: candidate.height,
                            expiresAtEpochMs = listOfNotNull(
                                candidate.expiresAtEpochMs, UrlPolicy.expiry(current.toString()),
                            ).minOrNull(),
                        ),
                    )
                }
            }
        }
        return rejected(SiteExtractionFailure.RESPONSE_CHANGED)
    }

    private fun rejected(reason: SiteExtractionFailure) = ValidationResult.Rejected(reason)

    private fun BufferedSource.readAtMost(limit: Long): ByteArray {
        val output = Buffer()
        while (output.size < limit) {
            if (read(output, minOf(8_192, limit - output.size)) == -1L) break
        }
        return output.readByteArray()
    }

    private fun directMime(bytes: ByteArray): String? {
        if (
            bytes.size >= 12 &&
            bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp"
        ) {
            return "video/mp4"
        }
        if (
            bytes.size >= 4 && bytes.take(4).map { it.toInt() and 255 } ==
            listOf(0x1a, 0x45, 0xdf, 0xa3)
        ) {
            return "video/webm"
        }
        if (bytes.size >= 3 && bytes.take(3).toByteArray().toString(Charsets.US_ASCII) == "ID3") {
            return "audio/mpeg"
        }
        if (bytes.size >= 2 && (bytes[0].toInt() and 255) == 255 &&
            (bytes[1].toInt() and 0xe0) == 0xe0
        ) {
            return "audio/aac"
        }
        return null
    }

    private companion object {
        val RANGE = Regex("""bytes 0-\d+/(\d+)""", RegexOption.IGNORE_CASE)
        val DRM = Regex(
            """(?i)<(?:\w+:)?ContentProtection\b|METHOD=SAMPLE-AES|""" +
                """KEYFORMAT\s*=\s*"(?!identity")[^"]+"""",
        )
    }
}