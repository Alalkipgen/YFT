package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

    suspend fun probe(candidate: MediaCandidate): Result = withContext(Dispatchers.IO) {
        try {
            probeBlocking(candidate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            Result.Failed(FailureReason.NETWORK)
        } catch (_: IllegalArgumentException) {
            Result.NotMedia(NotMediaReason.INVALID_URL)
        }
    }

    private fun probeBlocking(candidate: MediaCandidate): Result {
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

    private fun execute(
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

        return probeClient.newCall(request.build()).execute().use { response ->
            response.toMetadata()
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