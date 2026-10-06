package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Resolves direct-file range support and validators without consuming the response body.
 *
 * Redirects are handled manually so browser credentials never cross origins and HTTPS cannot
 * downgrade to cleartext. Plain HTTP is accepted only for loopback test fixtures.
 */
class DirectRangeProbe(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 15,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
        }
    }

    private val http = SecureDownloadHttp(
        client = client,
        maxRedirects = policy.maxRedirects,
        callTimeoutSeconds = policy.callTimeoutSeconds,
    )

    suspend fun probe(plan: DirectDownloadPlan): DirectProbeResult = try {
        probeSafely(plan)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: IOException) {
        failure(DownloadFailureReason.NETWORK)
    } catch (_: IllegalArgumentException) {
        failure(DownloadFailureReason.INVALID_URL)
    }

    private suspend fun probeSafely(plan: DirectDownloadPlan): DirectProbeResult {
        val initialUrl = plan.sourceUrl.toSafeDownloadUrl()
            ?: return failure(DownloadFailureReason.INVALID_URL)
        if (plan.expiresAtEpochMs?.let { it <= clock() } == true) {
            return failure(DownloadFailureReason.EXPIRED_URL)
        }

        val headExecution = when (
            val execution = execute(
                credentialOrigin = initialUrl,
                initialUrl = initialUrl,
                context = plan.requestContext,
                method = ProbeMethod.HEAD,
            )
        ) {
            is Execution.Failed -> return execution.asResult()
            is Execution.Completed -> execution
        }

        val headMetadata = headExecution.response.use { response ->
            when {
                response.code in HEAD_FALLBACK_CODES -> null
                response.code !in SUCCESS_CODES -> return response.toFailure()
                else -> response.toMetadata(
                    finalUrl = headExecution.finalUrl,
                    rangeRequested = false,
                )
            }
        }

        // Some file hosts send HEAD to their home page while GET returns the file: then the
        // HEAD answer describes a web page, so ask the original address again with the GET.
        val headGaveWebPage = headMetadata?.contentType in WEB_PAGE_TYPES
        val fileMetadata = headMetadata.takeUnless { headGaveWebPage }
        if (
            fileMetadata != null &&
            fileMetadata.supportsByteRanges &&
            fileMetadata.totalBytes != null
        ) {
            return fileMetadata.toSuccess(plan)
        }

        val rangeExecution = when (
            val execution = execute(
                credentialOrigin = initialUrl,
                initialUrl = if (headGaveWebPage) initialUrl else headExecution.finalUrl,
                context = plan.requestContext,
                method = ProbeMethod.RANGE_GET,
            )
        ) {
            is Execution.Failed -> return execution.asResult()
            is Execution.Completed -> execution
        }

        val rangeMetadata = rangeExecution.response.use { response ->
            when {
                response.code == HTTP_RANGE_NOT_SATISFIABLE -> {
                    val total = response.header("Content-Range")
                        ?.parseUnsatisfiedContentRangeTotal()
                    if (total == 0L) {
                        ResponseMetadata(
                            finalUrl = rangeExecution.finalUrl,
                            totalBytes = 0,
                            supportsByteRanges = true,
                            entityTag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                            contentType = response.normalizedContentType(),
                            dispositionFileName = response.contentDispositionFileName(),
                        )
                    } else {
                        return failure(
                            DownloadFailureReason.RANGE_NOT_SATISFIABLE,
                            response.code,
                        )
                    }
                }
                response.code !in SUCCESS_CODES -> return response.toFailure()
                else -> response.toMetadata(
                    finalUrl = rangeExecution.finalUrl,
                    rangeRequested = true,
                ) ?: return failure(DownloadFailureReason.MALFORMED_RESPONSE)
            }
        }

        return rangeMetadata
            .mergeFallback(fileMetadata)
            .toSuccess(plan)
    }

    private suspend fun execute(
        credentialOrigin: HttpUrl,
        initialUrl: HttpUrl,
        context: BrowserRequestContext,
        method: ProbeMethod,
    ): Execution = when (
        val execution = http.execute(
            credentialOrigin = credentialOrigin,
            initialUrl = initialUrl,
            context = context,
        ) {
            when (method) {
                ProbeMethod.HEAD -> head()
                ProbeMethod.RANGE_GET -> {
                    get()
                    header("Range", FIRST_BYTE_RANGE)
                }
            }
        }
    ) {
        is SecureDownloadHttp.Result.Completed -> Execution.Completed(
            response = execution.response,
            finalUrl = execution.finalUrl,
        )
        is SecureDownloadHttp.Result.Failed -> Execution.Failed(execution.reason)
    }

    private fun Response.toMetadata(
        finalUrl: HttpUrl,
        rangeRequested: Boolean,
    ): ResponseMetadata? {
        val parsedRange = header("Content-Range")?.parseSatisfiedContentRange()
        if (code == HTTP_PARTIAL_CONTENT && (parsedRange == null || parsedRange.startByte != 0L)) {
            return null
        }
        if (rangeRequested && code == HTTP_PARTIAL_CONTENT && parsedRange == null) return null

        val rangeWasHonored = code == HTTP_PARTIAL_CONTENT && parsedRange != null
        val contentLength = header("Content-Length")
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
        return ResponseMetadata(
            finalUrl = finalUrl,
            totalBytes = parsedRange?.totalBytes ?: contentLength,
            supportsByteRanges = rangeWasHonored || (
                !rangeRequested && header("Accept-Ranges").supportsBytes()
                ),
            entityTag = header("ETag"),
            lastModified = header("Last-Modified"),
            contentType = normalizedContentType(),
            dispositionFileName = contentDispositionFileName(),
        )
    }

    private fun ResponseMetadata.mergeFallback(fallback: ResponseMetadata?): ResponseMetadata =
        copy(
            totalBytes = totalBytes ?: fallback?.totalBytes,
            entityTag = entityTag ?: fallback?.entityTag,
            lastModified = lastModified ?: fallback?.lastModified,
            contentType = contentType ?: fallback?.contentType,
            dispositionFileName = dispositionFileName ?: fallback?.dispositionFileName,
        )

    private fun ResponseMetadata.toSuccess(plan: DirectDownloadPlan): DirectProbeResult.Success {
        val fileName = dispositionFileName
            ?.sanitizeFileName()
            ?: finalUrl.pathSegments.lastOrNull()
                ?.sanitizeFileName()
            ?: plan.suggestedFileName.sanitizeFileName()
            ?: DEFAULT_FILE_NAME
        return DirectProbeResult.Success(
            RemoteFileMetadata(
                finalUrl = finalUrl.toString(),
                totalBytes = totalBytes,
                supportsByteRanges = supportsByteRanges,
                entityTag = entityTag,
                lastModified = lastModified,
                contentType = contentType ?: plan.mimeType?.normalizeMimeType(),
                suggestedFileName = fileName,
            ),
        )
    }

    private fun Response.toFailure(): DirectProbeResult.Failure = failure(
        reason = when (code) {
            401 -> DownloadFailureReason.AUTHENTICATION_REQUIRED
            403 -> DownloadFailureReason.ACCESS_DENIED
            404 -> DownloadFailureReason.NOT_FOUND
            410 -> DownloadFailureReason.GONE
            416 -> DownloadFailureReason.RANGE_NOT_SATISFIABLE
            in 500..599 -> DownloadFailureReason.SERVER_ERROR
            else -> DownloadFailureReason.HTTP_STATUS
        },
        statusCode = code,
    )

    private fun Execution.Failed.asResult(): DirectProbeResult.Failure = failure(reason)

    private fun failure(
        reason: DownloadFailureReason,
        statusCode: Int? = null,
    ): DirectProbeResult.Failure = DirectProbeResult.Failure(
        DownloadFailure(reason, statusCode),
    )

    private fun String?.supportsBytes(): Boolean = this
        ?.split(',')
        ?.any { it.trim().equals("bytes", ignoreCase = true) }
        ?: false

    private fun String.parseSatisfiedContentRange(): ParsedContentRange? {
        val match = SATISFIED_CONTENT_RANGE.matchEntire(trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3]
            .takeUnless { it == "*" }
            ?.toLongOrNull()
        if (end < start || total != null && (total <= end || total < 0)) return null
        return ParsedContentRange(start, end, total)
    }

    private fun String.parseUnsatisfiedContentRangeTotal(): Long? {
        val match = UNSATISFIED_CONTENT_RANGE.matchEntire(trim()) ?: return null
        return match.groupValues[1].toLongOrNull()?.takeIf { it >= 0 }
    }

    private fun Response.normalizedContentType(): String? = header("Content-Type")
        ?.normalizeMimeType()
        ?.takeIf(String::isNotBlank)

    private fun String.normalizeMimeType(): String =
        substringBefore(';').trim().lowercase(Locale.US)

    private fun Response.contentDispositionFileName(): String? {
        val value = header("Content-Disposition") ?: return null
        CONTENT_DISPOSITION_UTF8_FILENAME.find(value)
            ?.groupValues
            ?.get(1)
            ?.let { encoded ->
                runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
            }
            ?.let { return it }
        return CONTENT_DISPOSITION_FILENAME.find(value)
            ?.groupValues
            ?.let { groups -> (groups[1].ifBlank { groups[2] }).trim() }
            ?.takeIf(String::isNotBlank)
    }

    private fun String.sanitizeFileName(): String? {
        val sanitized = replace(UNSAFE_FILE_NAME_CHARS, "_")
            .trim()
            .trim('.')
            .take(MAX_FILE_NAME_LENGTH)
            .trim()
            .trim('.')
        return sanitized.takeIf { it.isNotBlank() && it != "." && it != ".." }
    }

    private enum class ProbeMethod {
        HEAD,
        RANGE_GET,
    }

    private sealed interface Execution {
        data class Completed(
            val response: Response,
            val finalUrl: HttpUrl,
        ) : Execution

        data class Failed(val reason: DownloadFailureReason) : Execution
    }

    private data class ParsedContentRange(
        val startByte: Long,
        val endByteInclusive: Long,
        val totalBytes: Long?,
    )

    private data class ResponseMetadata(
        val finalUrl: HttpUrl,
        val totalBytes: Long?,
        val supportsByteRanges: Boolean,
        val entityTag: String?,
        val lastModified: String?,
        val contentType: String?,
        val dispositionFileName: String?,
    )

    private companion object {
        const val FIRST_BYTE_RANGE = "bytes=0-0"
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val DEFAULT_FILE_NAME = "download"
        const val MAX_FILE_NAME_LENGTH = 180

        val SUCCESS_CODES = 200..299
        val HEAD_FALLBACK_CODES = setOf(405, 501)
        val WEB_PAGE_TYPES = setOf("text/html", "application/xhtml+xml")
        val SATISFIED_CONTENT_RANGE =
            Regex("""(?i)^bytes\s+(\d+)-(\d+)/(\d+|\*)$""")
        val UNSATISFIED_CONTENT_RANGE =
            Regex("""(?i)^bytes\s+\*/(\d+)$""")
        val CONTENT_DISPOSITION_UTF8_FILENAME =
            Regex("""(?i)filename\*\s*=\s*UTF-8''([^;\s]+)""")
        val CONTENT_DISPOSITION_FILENAME =
            Regex("""(?i)filename\s*=\s*(?:"([^"]*)"|([^;]*))""")
        val UNSAFE_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\p{Cc}]""")
    }
}