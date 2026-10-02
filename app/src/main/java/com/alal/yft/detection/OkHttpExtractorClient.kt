package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/**
 * Bounded HTTPS client for site adapters.
 *
 * Redirects are followed manually so every hop can be re-checked for HTTPS and counted, matching
 * the policy the media resolver already uses. The body is read with an explicit cap and an
 * oversized response fails instead of being silently truncated, because a truncated page would
 * look like a changed site rather than a budget problem.
 */
class OkHttpExtractorClient(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
) : ExtractorHttpClient {
    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 15,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
        }
    }

    private val extractorClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(policy.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        require(maxBodyBytes > 0)
        return try {
            withContext(Dispatchers.IO) { fetch(url, headers, maxBodyBytes) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
    }

    private fun fetch(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        var current = url.toSecureHttpUrl()
            ?: return ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        var redirects = 0

        while (true) {
            val request = Request.Builder()
                .url(current)
                .get()
                .apply {
                    headers.forEach { (name, value) ->
                        if (name.isNotBlank() && value.isNotBlank()) header(name, value)
                    }
                }
                .build()

            val hop = extractorClient.newCall(request).execute().use { response ->
                when {
                    response.isRedirect || response.code == HTTP_PERMANENT_REDIRECT -> {
                        val location = response.header("Location")
                            ?: return ExtractorHttpResult.Failure(
                                SiteExtractionFailure.MALFORMED_RESPONSE,
                                response.code,
                            )
                        val next = current.resolve(location)
                            ?.takeIf { it.isHttps }
                            ?: return ExtractorHttpResult.Failure(
                                SiteExtractionFailure.UNSUPPORTED_URL,
                                response.code,
                            )
                        redirects += 1
                        if (redirects > policy.maxRedirects) {
                            return ExtractorHttpResult.Failure(
                                SiteExtractionFailure.RESPONSE_CHANGED,
                                response.code,
                            )
                        }
                        next
                    }

                    !response.isSuccessful -> return ExtractorHttpResult.Failure(
                        response.code.toFailure(),
                        response.code,
                    )

                    else -> {
                        val body = response.body
                            ?: return ExtractorHttpResult.Failure(
                                SiteExtractionFailure.MALFORMED_RESPONSE,
                                response.code,
                            )
                        if (body.contentLength() > maxBodyBytes) {
                            return ExtractorHttpResult.Failure(
                                SiteExtractionFailure.RESPONSE_TOO_LARGE,
                                response.code,
                            )
                        }
                        val text = body.source().readBounded(maxBodyBytes)
                            ?: return ExtractorHttpResult.Failure(
                                SiteExtractionFailure.RESPONSE_TOO_LARGE,
                                response.code,
                            )
                        return ExtractorHttpResult.Success(
                            statusCode = response.code,
                            body = text,
                            finalUrl = current.toString(),
                            contentType = response.header("Content-Type"),
                        )
                    }
                }
            }
            current = hop
        }
    }

    /** Reads at most [maxBodyBytes]; a longer body returns null so the caller can fail cleanly. */
    private fun BufferedSource.readBounded(maxBodyBytes: Long): String? =
        if (request(maxBodyBytes + 1)) null else readUtf8()

    /** Rejects non-HTTPS addresses and any address carrying inline authority information. */
    private fun String.toSecureHttpUrl(): HttpUrl? {
        if (runCatching { URI(this).userInfo }.getOrNull() != null) return null
        return toHttpUrlOrNull()?.takeIf { it.isHttps }
    }

    private fun Int.toFailure(): SiteExtractionFailure = when (this) {
        401, 403, 407 -> SiteExtractionFailure.LOGIN_REQUIRED
        404, 410 -> SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
        429 -> SiteExtractionFailure.RATE_LIMITED
        451 -> SiteExtractionFailure.GEO_RESTRICTED
        else -> SiteExtractionFailure.HTTP_STATUS
    }

    private companion object {
        const val HTTP_PERMANENT_REDIRECT = 308
    }
}