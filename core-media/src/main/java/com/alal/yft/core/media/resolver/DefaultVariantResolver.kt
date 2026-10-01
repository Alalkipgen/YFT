package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.media.VariantSupport
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Singleton
class DefaultVariantResolver(
    client: OkHttpClient,
    private val policy: Policy,
    private val clock: () -> Long,
) : VariantResolver {
    @Inject
    constructor(client: OkHttpClient) : this(
        client = client,
        policy = Policy(),
        clock = System::currentTimeMillis,
    )

    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 10,
        val maxManifestBytes: Int = 1_048_576,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
            require(maxManifestBytes > 0)
        }
    }

    private val resolverClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(policy.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult = try {
        resolveSafely(candidate)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: IOException) {
        VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK)
    } catch (_: IllegalArgumentException) {
        VariantResolutionResult.Failure(VariantResolutionFailure.INVALID_URL)
    }

    private suspend fun resolveSafely(candidate: MediaCandidate): VariantResolutionResult {
        val initialUrl = candidate.mediaUrl.toSafeHttpUrl()
            ?: return VariantResolutionResult.Failure(VariantResolutionFailure.INVALID_URL)
        val expiresAt = candidate.expiresAtEpochMs ?: initialUrl.expiryEpochMillis()
        if (expiresAt != null && expiresAt <= clock()) {
            return VariantResolutionResult.Failure(VariantResolutionFailure.EXPIRED_URL)
        }
        if (candidate.drmHint == true) {
            return VariantResolutionResult.Failure(VariantResolutionFailure.DRM_PROTECTED)
        }

        return when (candidate.effectiveKind()) {
            MediaKind.HLS -> resolveManifest(candidate, initialUrl, expiresAt, MediaKind.HLS)
            MediaKind.DASH -> resolveManifest(candidate, initialUrl, expiresAt, MediaKind.DASH)
            MediaKind.DIRECT,
            MediaKind.UNKNOWN,
            -> resolveDirect(candidate, initialUrl, expiresAt)
        }
    }

    private suspend fun resolveDirect(
        candidate: MediaCandidate,
        initialUrl: HttpUrl,
        expiresAt: Long?,
    ): VariantResolutionResult {
        val head = when (
            val execution = execute(
                candidate = candidate,
                credentialOrigin = initialUrl,
                initialUrl = initialUrl,
                method = RequestMethod.HEAD,
            )
        ) {
            is HttpExecution.Failed -> return execution.toResolutionFailure()
            is HttpExecution.Completed -> execution
        }

        var finalUrl = head.finalUrl
        var metadata = head.response.use { it.toDirectMetadata() }
        if (metadata.code == HTTP_METHOD_NOT_ALLOWED || metadata.code == HTTP_NOT_IMPLEMENTED) {
            metadata = DirectMetadata(code = metadata.code)
        } else if (metadata.code !in SUCCESS_CODES) {
            return VariantResolutionResult.Failure(
                reason = VariantResolutionFailure.HTTP_STATUS,
                httpStatusCode = metadata.code,
            )
        }

        if (
            metadata.code == HTTP_METHOD_NOT_ALLOWED ||
            metadata.code == HTTP_NOT_IMPLEMENTED ||
            metadata.mimeType == null ||
            metadata.totalLengthBytes == null
        ) {
            val range = when (
                val execution = execute(
                    candidate = candidate,
                    credentialOrigin = initialUrl,
                    initialUrl = finalUrl,
                    method = RequestMethod.RANGE_GET,
                )
            ) {
                is HttpExecution.Failed -> return execution.toResolutionFailure()
                is HttpExecution.Completed -> execution
            }
            finalUrl = range.finalUrl
            val rangeMetadata = range.response.use { it.toDirectMetadata() }
            if (rangeMetadata.code !in SUCCESS_CODES) {
                return VariantResolutionResult.Failure(
                    reason = VariantResolutionFailure.HTTP_STATUS,
                    httpStatusCode = rangeMetadata.code,
                )
            }
            metadata = DirectMetadata(
                code = rangeMetadata.code,
                mimeType = rangeMetadata.mimeType ?: metadata.mimeType,
                totalLengthBytes = rangeMetadata.totalLengthBytes
                    ?: metadata.totalLengthBytes,
            )
        }

        val mimeType = metadata.mimeType ?: candidate.mimeType?.normalizedMimeType()
        val support = if (mimeType.isKnownUnsupportedMediaMime()) {
            VariantSupport.UNSUPPORTED_CODEC
        } else {
            VariantSupport.SUPPORTED
        }
        if (support == VariantSupport.UNSUPPORTED_CODEC) {
            return VariantResolutionResult.Failure(
                VariantResolutionFailure.UNSUPPORTED_CODEC,
            )
        }
        val sizeBytes = metadata.totalLengthBytes
            ?.takeIf { it >= 0 }
            ?: candidate.contentLengthBytes?.takeIf { it >= 0 }
        val variant = MediaVariant(
            id = "direct-0",
            playbackUrl = finalUrl.toString(),
            kind = MediaKind.DIRECT,
            trackType = if (mimeType?.startsWith("audio/") == true) {
                MediaTrackType.AUDIO
            } else {
                MediaTrackType.AUDIO_VIDEO
            },
            requestContext = candidate.requestContext.forTarget(
                credentialOrigin = initialUrl,
                targetUrl = finalUrl,
            ),
            label = candidate.title ?: "Direct media",
            mimeType = mimeType,
            container = mimeType.toContainer(finalUrl),
            durationMillis = candidate.durationMillis,
            sizeBytes = sizeBytes,
            sizeAccuracy = sizeBytes?.let { MediaSizeAccuracy.EXACT },
            support = support,
            expiresAtEpochMs = expiresAt,
        )
        return success(candidate, listOf(variant), candidate.durationMillis)
    }

    private suspend fun resolveManifest(
        candidate: MediaCandidate,
        initialUrl: HttpUrl,
        expiresAt: Long?,
        kind: MediaKind,
    ): VariantResolutionResult {
        val execution = when (
            val result = execute(
                candidate = candidate,
                credentialOrigin = initialUrl,
                initialUrl = initialUrl,
                method = RequestMethod.GET,
                accept = when (kind) {
                    MediaKind.HLS -> HLS_ACCEPT
                    MediaKind.DASH -> DASH_ACCEPT
                    else -> null
                },
            )
        ) {
            is HttpExecution.Failed -> return result.toResolutionFailure()
            is HttpExecution.Completed -> result
        }
        val body = execution.response.use { response ->
            if (response.code !in SUCCESS_CODES) {
                return VariantResolutionResult.Failure(
                    reason = VariantResolutionFailure.HTTP_STATUS,
                    httpStatusCode = response.code,
                )
            }
            val responseBody = response.body
                ?: return VariantResolutionResult.Failure(
                    VariantResolutionFailure.MALFORMED_MANIFEST,
                )
            if (responseBody.contentLength() > policy.maxManifestBytes) {
                return VariantResolutionResult.Failure(
                    VariantResolutionFailure.MANIFEST_TOO_LARGE,
                )
            }
            val bytes = responseBody.byteStream().readNBytes(policy.maxManifestBytes + 1)
            if (bytes.size > policy.maxManifestBytes) {
                return VariantResolutionResult.Failure(
                    VariantResolutionFailure.MANIFEST_TOO_LARGE,
                )
            }
            bytes.toString(Charsets.UTF_8)
        }
        val finalManifestUrl = execution.finalUrl.toString()
        val parsed = when (kind) {
            MediaKind.HLS -> HlsManifestParser.parse(
                manifest = body,
                manifestUrl = finalManifestUrl,
                requestContext = candidate.requestContext,
                expiresAtEpochMs = expiresAt,
            )
            MediaKind.DASH -> DashManifestParser.parse(
                manifest = body,
                manifestUrl = finalManifestUrl,
                requestContext = candidate.requestContext,
                expiresAtEpochMs = expiresAt,
            )
            else -> error("Only manifest kinds can be parsed")
        }
        return when (parsed) {
            ManifestParseResult.DrmProtected -> VariantResolutionResult.Failure(
                VariantResolutionFailure.DRM_PROTECTED,
            )
            ManifestParseResult.Malformed -> VariantResolutionResult.Failure(
                VariantResolutionFailure.MALFORMED_MANIFEST,
            )
            is ManifestParseResult.Parsed -> {
                val variants = parsed.variants.map { variant ->
                    val target = variant.playbackUrl.toSafeHttpUrl()
                        ?: return VariantResolutionResult.Failure(
                            VariantResolutionFailure.INVALID_URL,
                        )
                    variant.copy(
                        requestContext = candidate.requestContext.forTarget(
                            credentialOrigin = initialUrl,
                            targetUrl = target,
                        ),
                    )
                }
                if (variants.none(MediaVariant::isPreviewable)) {
                    VariantResolutionResult.Failure(
                        VariantResolutionFailure.UNSUPPORTED_CODEC,
                    )
                } else {
                    success(
                        candidate = candidate,
                        variants = variants,
                        durationMillis = parsed.durationMillis ?: candidate.durationMillis,
                    )
                }
            }
        }
    }

    private fun success(
        candidate: MediaCandidate,
        variants: List<MediaVariant>,
        durationMillis: Long?,
    ): VariantResolutionResult = if (variants.isEmpty()) {
        VariantResolutionResult.Failure(VariantResolutionFailure.NO_VARIANTS)
    } else {
        VariantResolutionResult.Success(
            MediaAsset(
                sourcePageUrl = candidate.pageUrl,
                title = candidate.title,
                thumbnailUrl = candidate.thumbnailUrl,
                durationMillis = durationMillis,
                variants = variants,
                resolvedAtEpochMs = clock(),
            ),
        )
    }

    private suspend fun execute(
        candidate: MediaCandidate,
        credentialOrigin: HttpUrl,
        initialUrl: HttpUrl,
        method: RequestMethod,
        accept: String? = null,
    ): HttpExecution {
        var currentUrl = initialUrl
        var redirectCount = 0
        while (true) {
            val requestBuilder = Request.Builder().url(currentUrl)
            candidate.requestContext
                .headersForTarget(credentialOrigin, currentUrl)
                .forEach { (name, value) ->
                    runCatching { requestBuilder.header(name, value) }
                }
            if (accept != null) requestBuilder.header("Accept", accept)
            when (method) {
                RequestMethod.HEAD -> requestBuilder.head()
                RequestMethod.RANGE_GET -> requestBuilder
                    .get()
                    .header("Range", RANGE_FIRST_BYTE)
                RequestMethod.GET -> requestBuilder.get()
            }

            val response = resolverClient.newCall(requestBuilder.build()).await()
            if (response.code !in REDIRECT_CODES) {
                return HttpExecution.Completed(response, currentUrl)
            }
            if (redirectCount >= policy.maxRedirects) {
                response.close()
                return HttpExecution.Failed(VariantResolutionFailure.TOO_MANY_REDIRECTS)
            }
            val target = response.header("Location")
                ?.let(currentUrl::resolve)
                ?.takeIf { it.isSafeHttpUrl() }
            response.close()
            if (target == null || currentUrl.isHttps && !target.isHttps) {
                return HttpExecution.Failed(VariantResolutionFailure.UNSAFE_REDIRECT)
            }
            currentUrl = target
            redirectCount += 1
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }

    private fun HttpExecution.Failed.toResolutionFailure(): VariantResolutionResult.Failure =
        VariantResolutionResult.Failure(reason)

    private fun MediaCandidate.effectiveKind(): MediaKind {
        if (kind != MediaKind.UNKNOWN) return kind
        val mime = mimeType?.normalizedMimeType()
        return when {
            mime in HLS_MIME_TYPES || mediaUrl.substringBefore('?')
                .endsWith(".m3u8", ignoreCase = true) -> MediaKind.HLS
            mime == DASH_MIME_TYPE || mediaUrl.substringBefore('?')
                .endsWith(".mpd", ignoreCase = true) -> MediaKind.DASH
            else -> MediaKind.DIRECT
        }
    }

    private fun HttpUrl.expiryEpochMillis(): Long? {
        val value = queryParameterNames
            .firstOrNull { name -> name.lowercase(Locale.US) in EXPIRY_QUERY_NAMES }
            ?.let(::queryParameter)
            ?: return null
        value.toLongOrNull()?.let { numeric ->
            return if (numeric < EPOCH_MILLIS_THRESHOLD) {
                runCatching { Math.multiplyExact(numeric, 1_000L) }.getOrNull()
            } else {
                numeric
            }
        }
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun String.toSafeHttpUrl(): HttpUrl? = toHttpUrlOrNull()
        ?.takeIf { it.isSafeHttpUrl() }

    private fun HttpUrl.isSafeHttpUrl(): Boolean =
        username.isEmpty() &&
            password.isEmpty() &&
            (isHttps || scheme == "http" && host.isLoopbackHost())

    private fun String.isLoopbackHost(): Boolean =
        equals("localhost", ignoreCase = true) || this == "127.0.0.1" || this == "::1"

    private fun BrowserRequestContext.headersForTarget(
        credentialOrigin: HttpUrl,
        targetUrl: HttpUrl,
    ): Map<String, String> {
        val replayable = replayHeaders()
        if (targetUrl.hasSameOrigin(credentialOrigin)) return replayable
        return replayable.filterKeys { name ->
            name.lowercase(Locale.US) in CROSS_ORIGIN_HEADER_ALLOWLIST
        }
    }

    private fun BrowserRequestContext.forTarget(
        credentialOrigin: HttpUrl,
        targetUrl: HttpUrl,
    ): BrowserRequestContext {
        if (targetUrl.hasSameOrigin(credentialOrigin)) return this
        return BrowserRequestContext(
            pageUrl = null,
            userAgent = userAgent,
            cookie = null,
            observedHeaders = observedHeaders.filterKeys { name ->
                name.lowercase(Locale.US) in CROSS_ORIGIN_HEADER_ALLOWLIST
            },
        )
    }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private fun Response.toDirectMetadata(): DirectMetadata {
        val contentRange = header("Content-Range")
        val contentRangeTotal = contentRange
            ?.substringAfterLast('/', missingDelimiterValue = "")
            ?.trim()
            ?.takeUnless { it == "*" }
            ?.toLongOrNull()
        val contentLength = header("Content-Length")
            ?.toLongOrNull()
            ?.takeUnless { code == 206 && contentRange != null }
        return DirectMetadata(
            code = code,
            mimeType = header("Content-Type")?.normalizedMimeType(),
            totalLengthBytes = contentRangeTotal ?: contentLength,
        )
    }

    private fun String.normalizedMimeType(): String =
        substringBefore(';').trim().lowercase(Locale.US)

    private fun String?.isKnownUnsupportedMediaMime(): Boolean {
        val mime = this ?: return false
        if (!mime.startsWith("video/") && !mime.startsWith("audio/")) return false
        return mime !in SUPPORTED_DIRECT_MIME_TYPES
    }

    private fun String?.toContainer(url: HttpUrl): String? = when (this) {
        "video/mp4", "audio/mp4" -> "MP4"
        "video/webm", "audio/webm" -> "WebM"
        "video/x-matroska" -> "Matroska"
        "audio/mpeg" -> "MP3"
        "audio/aac" -> "AAC"
        "audio/ogg", "video/ogg" -> "Ogg"
        "video/quicktime" -> "QuickTime"
        else -> url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf(String::isNotBlank)
            ?.uppercase(Locale.US)
    }

    private enum class RequestMethod {
        HEAD,
        RANGE_GET,
        GET,
    }

    private sealed interface HttpExecution {
        data class Completed(
            val response: Response,
            val finalUrl: HttpUrl,
        ) : HttpExecution

        data class Failed(
            val reason: VariantResolutionFailure,
        ) : HttpExecution
    }

    private data class DirectMetadata(
        val code: Int,
        val mimeType: String? = null,
        val totalLengthBytes: Long? = null,
    )

    private companion object {
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_NOT_IMPLEMENTED = 501
        const val RANGE_FIRST_BYTE = "bytes=0-0"
        const val DASH_MIME_TYPE = "application/dash+xml"
        const val DASH_ACCEPT = "application/dash+xml, application/xml;q=0.9, */*;q=0.1"
        const val HLS_ACCEPT =
            "application/vnd.apple.mpegurl, application/x-mpegurl, */*;q=0.1"
        const val EPOCH_MILLIS_THRESHOLD = 100_000_000_000L
        val SUCCESS_CODES = 200..299
        val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
        val EXPIRY_QUERY_NAMES = setOf("exp", "expire", "expires", "expiration")
        val CROSS_ORIGIN_HEADER_ALLOWLIST = setOf(
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
        )
        val HLS_MIME_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )
        val SUPPORTED_DIRECT_MIME_TYPES = setOf(
            "audio/aac",
            "audio/mp4",
            "audio/mpeg",
            "audio/ogg",
            "audio/webm",
            "video/mp4",
            "video/ogg",
            "video/quicktime",
            "video/webm",
            "video/x-matroska",
        )
    }
}