package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.generic.manifest.ManifestReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Enriches an observed candidate from response headers without downloading the media body.
 *
 * Redirects are followed manually so browser credentials are never replayed to another origin.
 * A range GET is used only when HEAD is unsupported or does not expose enough media metadata.
 */
class MediaMetadataProbe(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
) {
    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 10,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
        }
    }

    sealed interface Result {
        data class Detected(val candidate: MediaCandidate) : Result
        data class NotMedia(val reason: NotMediaReason) : Result
        data class Failed(val reason: FailureReason) : Result
    }

    enum class NotMediaReason {
        INVALID_URL,
        CLEARLY_NON_MEDIA,
        NO_MEDIA_SIGNAL,
    }

    enum class FailureReason {
        NETWORK,
        HTTP_STATUS,
        INVALID_REDIRECT,
        INSECURE_REDIRECT,
        TOO_MANY_REDIRECTS,
    }

    private val probeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(policy.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    suspend fun probe(candidate: MediaCandidate): Result = try {
        probeNetwork(candidate)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: IOException) {
        Result.Failed(FailureReason.NETWORK)
    } catch (_: IllegalArgumentException) {
        Result.NotMedia(NotMediaReason.INVALID_URL)
    }

    /**
     * P24: the length and tallest picture an HLS or DASH [candidate] states, read from its
     * manifest's text and, for an HLS master (which states no length), from its first
     * quality's playlist: so the length of a player that plays a page-built stream finds its
     * manifest. Two small bounded text requests at most, sent like the header probe and with
     * the page's `Origin`; null when nothing could be read. Never reads media.
     */
    suspend fun readManifest(candidate: MediaCandidate): MediaCandidate? = try {
        readManifestText(candidate)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private suspend fun readManifestText(candidate: MediaCandidate): MediaCandidate? {
        if (candidate.kind != MediaKind.HLS && candidate.kind != MediaKind.DASH) return null
        val manifestUrl = candidate.mediaUrl.toSafeHttpUrl() ?: return null
        val (finalUrl, text) = fetchText(candidate, manifestUrl, manifestUrl) ?: return null
        val facts = if (candidate.kind == MediaKind.HLS) {
            ManifestReader.hls(text, finalUrl.toString())
        } else {
            ManifestReader.dash(text)
        } ?: return null
        var duration = facts.durationMillis
        val playlist = facts.firstPlaylistUrl?.toSafeHttpUrl()
        if (duration == null && playlist != null) {
            duration = fetchText(candidate, manifestUrl, playlist)
                ?.let { (url, body) -> ManifestReader.hls(body, url.toString()) }
                ?.durationMillis
        }
        if (duration == null && facts.height == null) return null
        return candidate.copy(
            durationMillis = candidate.durationMillis ?: duration,
            width = candidate.width ?: facts.width.takeIf { candidate.height == null },
            height = candidate.height ?: facts.height,
        )
    }

    /**
     * A manifest's text, at most [MAX_MANIFEST_BYTES], with redirects followed like the header
     * probe: only to HTTPS, and without credentials to another origin than [credentialOrigin].
     */
    private suspend fun fetchText(
        candidate: MediaCandidate,
        credentialOrigin: HttpUrl,
        target: HttpUrl,
    ): Pair<HttpUrl, String>? {
        var currentUrl = target
        repeat(policy.maxRedirects + 1) {
            val headers = candidate.requestContext
                .probeHeaders(currentUrl.hasSameOrigin(credentialOrigin))
                .withPageOrigin(candidate.requestContext.pageUrl)
            val request = Request.Builder().url(currentUrl).get()
            headers.forEach { (name, value) -> runCatching { request.header(name, value) } }
            request.header("Accept", MANIFEST_ACCEPT)
            val answer = suspendCancellableCoroutine { continuation ->
                val call = probeClient.newCall(request.build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, error: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val result = runCatching { response.use { it.toText() } }
                            if (!continuation.isActive) return
                            result.fold(
                                onSuccess = continuation::resume,
                                onFailure = continuation::resumeWithException,
                            )
                        }
                    },
                )
            }
            if (answer.code in REDIRECT_CODES) {
                val next = answer.location?.let(currentUrl::resolve)
                    ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
                    ?: return null
                if (currentUrl.isHttps && !next.isHttps) return null
                currentUrl = next
                return@repeat
            }
            if (answer.code !in SUCCESS_CODES) return null
            return answer.body?.let { currentUrl to it }
        }
        return null
    }

    private class TextAnswer(val code: Int, val location: String?, val body: String?)

    private fun Response.toText(): TextAnswer {
        if (code !in SUCCESS_CODES) return TextAnswer(code, header("Location"), null)
        val source = body ?: return TextAnswer(code, null, null)
        val bytes = source.byteStream().use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(BUFFER_SIZE)
            while (buffer.size() <= MAX_MANIFEST_BYTES) {
                val read = stream.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
        val text = bytes.takeIf { it.size <= MAX_MANIFEST_BYTES }?.toString(Charsets.UTF_8)
        return TextAnswer(code, null, text)
    }

    /** P24: a page's player asks for its manifest with the page's `Origin`; so does this. */
    private fun Map<String, String>.withPageOrigin(pageUrl: String?): Map<String, String> {
        if (keys.any { it.equals("Origin", ignoreCase = true) }) return this
        if (keys.none { it.equals("Referer", ignoreCase = true) }) return this
        val page = pageUrl?.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return this
        val port = if (page.port == HTTPS_PORT) "" else ":${page.port}"
        return this + ("Origin" to "https://${page.host}$port")
    }

    private suspend fun probeNetwork(candidate: MediaCandidate): Result {
        val initialUrl = candidate.mediaUrl.toSafeHttpUrl()
            ?: return Result.NotMedia(NotMediaReason.INVALID_URL)
        var currentUrl = initialUrl
        var redirectCount = 0

        while (true) {
            val sameOrigin = currentUrl.hasSameOrigin(initialUrl)
            val headers = candidate.requestContext.probeHeaders(sameOrigin)
            val head = execute(currentUrl, headers, Method.HEAD)

            if (head.isRedirect) {
                val redirect = resolveRedirect(head, currentUrl, redirectCount) ?: return when {
                    redirectCount >= policy.maxRedirects ->
                        Result.Failed(FailureReason.TOO_MANY_REDIRECTS)
                    head.location.isNullOrBlank() ->
                        Result.Failed(FailureReason.INVALID_REDIRECT)
                    else ->
                        Result.Failed(FailureReason.INSECURE_REDIRECT)
                }
                currentUrl = redirect
                redirectCount += 1
                continue
            }

            val metadata = when {
                head.code == HTTP_METHOD_NOT_ALLOWED || head.code == HTTP_NOT_IMPLEMENTED ->
                    execute(currentUrl, headers, Method.RANGE_GET)
                head.code !in SUCCESS_CODES ->
                    return Result.Failed(FailureReason.HTTP_STATUS)
                head.needsRangeFallback() ->
                    execute(currentUrl, headers, Method.RANGE_GET)
                else -> head
            }

            if (metadata.isRedirect) {
                val redirect = resolveRedirect(metadata, currentUrl, redirectCount) ?: return when {
                    redirectCount >= policy.maxRedirects ->
                        Result.Failed(FailureReason.TOO_MANY_REDIRECTS)
                    metadata.location.isNullOrBlank() ->
                        Result.Failed(FailureReason.INVALID_REDIRECT)
                    else ->
                        Result.Failed(FailureReason.INSECURE_REDIRECT)
                }
                currentUrl = redirect
                redirectCount += 1
                continue
            }
            if (metadata.code !in SUCCESS_CODES) {
                return Result.Failed(FailureReason.HTTP_STATUS)
            }

            val mimeType = metadata.mimeType
            if (mimeType.isClearlyNonMedia()) {
                return Result.NotMedia(NotMediaReason.CLEARLY_NON_MEDIA)
            }
            val kind = MediaUrlClassifier.classify(currentUrl.toString(), mimeType)
                ?: candidate.kind.takeIf { it != MediaKind.UNKNOWN }
                ?: return Result.NotMedia(NotMediaReason.NO_MEDIA_SIGNAL)
            val redirected = redirectCount > 0 || currentUrl != initialUrl
            val sources = buildSet {
                addAll(candidate.sources)
                if (redirected) add(CandidateSource.REDIRECT)
                if (kind == MediaKind.HLS || kind == MediaKind.DASH) {
                    add(CandidateSource.MANIFEST)
                }
            }

            return Result.Detected(
                candidate.copy(
                    mediaUrl = currentUrl.toString(),
                    sources = sources,
                    kind = kind,
                    mimeType = mimeType ?: candidate.mimeType,
                    contentLengthBytes = metadata.totalLengthBytes
                        ?: candidate.contentLengthBytes,
                    confidence = if (mimeType.confirmsMedia()) {
                        CandidateConfidence.HIGH
                    } else {
                        candidate.confidence
                    },
                ),
            )
        }
    }

    private suspend fun execute(
        url: HttpUrl,
        headers: Map<String, String>,
        method: Method,
    ): ResponseMetadata {
        val request = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            runCatching { request.header(name, value) }
        }
        when (method) {
            Method.HEAD -> request.head()
            Method.RANGE_GET -> request.get().header("Range", RANGE_FIRST_BYTE)
        }

        return suspendCancellableCoroutine { continuation ->
            val call = probeClient.newCall(request.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use { it.toMetadata() }
                        }
                        if (!continuation.isActive) return
                        result.fold(
                            onSuccess = continuation::resume,
                            onFailure = continuation::resumeWithException,
                        )
                    }
                },
            )
        }
    }

    private fun resolveRedirect(
        response: ResponseMetadata,
        fromUrl: HttpUrl,
        redirectCount: Int,
    ): HttpUrl? {
        if (redirectCount >= policy.maxRedirects) return null
        val target = response.location
            ?.let(fromUrl::resolve)
            ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
            ?: return null
        if (fromUrl.isHttps && !target.isHttps) return null
        return target
    }

    private fun Response.toMetadata(): ResponseMetadata {
        val normalizedMime = header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotEmpty)
        val contentRangeTotal = header("Content-Range")
            ?.substringAfterLast('/', missingDelimiterValue = "")
            ?.trim()
            ?.takeUnless { it == "*" }
            ?.toLongOrNull()
        val contentLength = header("Content-Length")?.toLongOrNull()
        return ResponseMetadata(
            code = code,
            requestedUrl = request.url,
            location = header("Location"),
            mimeType = normalizedMime,
            totalLengthBytes = contentRangeTotal ?: contentLength,
        )
    }

    private fun ResponseMetadata.needsRangeFallback(): Boolean =
        mimeType == null && MediaUrlClassifier.classify(requestedUrl.toString()) == null

    private fun BrowserRequestContext.probeHeaders(sameOrigin: Boolean): Map<String, String> {
        val replayable = replayHeaders()
        if (sameOrigin) return replayable
        return replayable.filterKeys { name ->
            name.lowercase() in CROSS_ORIGIN_HEADER_ALLOWLIST
        }
    }

    private fun String.toSafeHttpUrl(): HttpUrl? = toHttpUrlOrNull()
        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private fun String?.confirmsMedia(): Boolean = when {
        this == null -> false
        startsWith("video/") || startsWith("audio/") -> true
        this in MANIFEST_MIME_TYPES -> true
        else -> false
    }

    private fun String?.isClearlyNonMedia(): Boolean = when {
        this == null -> false
        startsWith("image/") || startsWith("font/") -> true
        this in NON_MEDIA_MIME_TYPES -> true
        contains("javascript") -> true
        else -> false
    }

    private enum class Method {
        HEAD,
        RANGE_GET,
    }

    private data class ResponseMetadata(
        val code: Int,
        val requestedUrl: HttpUrl,
        val location: String?,
        val mimeType: String?,
        val totalLengthBytes: Long?,
    ) {
        val isRedirect: Boolean get() = code in REDIRECT_CODES
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 524_288
        const val BUFFER_SIZE = 8_192
        const val HTTPS_PORT = 443
        const val MANIFEST_ACCEPT = "application/vnd.apple.mpegurl, application/x-mpegurl, " +
            "application/dash+xml, */*;q=0.1"
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_NOT_IMPLEMENTED = 501
        const val RANGE_FIRST_BYTE = "bytes=0-0"
        val SUCCESS_CODES = 200..299
        val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
        val CROSS_ORIGIN_HEADER_ALLOWLIST = setOf(
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
        )
        val MANIFEST_MIME_TYPES = setOf(
            "application/dash+xml",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )
        val NON_MEDIA_MIME_TYPES = setOf(
            "application/json",
            "application/xhtml+xml",
            "text/css",
            "text/html",
        )
    }
}