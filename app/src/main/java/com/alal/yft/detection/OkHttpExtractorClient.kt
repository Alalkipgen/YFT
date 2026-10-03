package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

/**
 * Bounded HTTPS client for site adapters.
 *
 * Redirects are followed manually so every hop can be re-checked for HTTPS and counted, matching
 * the policy the media resolver already uses. The body is read with an explicit cap and an
 * oversized response fails instead of being silently truncated, because a truncated page would
 * look like a changed site rather than a budget problem.
 *
 * There is no cookie jar: a lookup never sends a cookie it was not given. The cookies its own
 * responses set are only reported in [ExtractorHttpResult.Success.cookies], in memory.
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
            withContext(Dispatchers.IO) { fetch(url, headers, maxBodyBytes, jsonBody = null) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        require(maxBodyBytes > 0)
        return try {
            withContext(Dispatchers.IO) { fetch(url, headers, maxBodyBytes, jsonBody = body) }
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
        jsonBody: String?,
    ): ExtractorHttpResult {
        var current = url.toSecureHttpUrl()
            ?: return ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        // A JSON POST that is redirected with 301/302/303 continues as a GET, as browsers do;
        // 307/308 explicitly preserve the method and body.
        var pendingBody = jsonBody
        var redirects = 0
        // Credentials belong to the site they were captured for. A redirect that leaves that
        // site continues without them, as a browser's cookie scoping would.
        val credentialHost = current.host
        var forwardCredentials = true
        // Keyed by name, domain and path, as a browser keys them; later responses replace values.
        val cookies = LinkedHashMap<Triple<String, String, String>, ResponseCookie>()

        while (true) {
            val bodyForHop = pendingBody
            val request = Request.Builder()
                .url(current)
                .apply {
                    if (bodyForHop == null) {
                        get()
                    } else {
                        post(bodyForHop.toRequestBody(JSON_MEDIA_TYPE))
                    }
                }
                .apply {
                    headers.forEach { (name, value) ->
                        val allowed = forwardCredentials || !SiteScope.isCredentialHeader(name)
                        if (allowed && name.isNotBlank() && value.isNotBlank()) header(name, value)
                    }
                }
                .build()

            val hop = extractorClient.newCall(request).execute().use { response ->
                cookies.collect(current, response.headers)
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
                        if (response.code !in METHOD_PRESERVING_REDIRECTS) pendingBody = null
                        if (!SiteScope.sameSite(credentialHost, next.host)) {
                            forwardCredentials = false
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
                            cookies = cookies.values.toList(),
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

    /**
     * Keeps the name and value of every cookie [url]'s response set. OkHttp's parser already
     * rejects a cookie whose domain does not match [url] or is a public suffix. A deletion or an
     * expired value removes an earlier copy; oversized and surplus cookies are ignored.
     */
    private fun MutableMap<Triple<String, String, String>, ResponseCookie>.collect(
        url: HttpUrl,
        headers: Headers,
    ) {
        val now = System.currentTimeMillis()
        Cookie.parseAll(url, headers).forEach { cookie ->
            val key = Triple(cookie.name, cookie.domain, cookie.path)
            when {
                cookie.expiresAt <= now -> remove(key)
                cookie.name.length + cookie.value.length > MAX_COOKIE_CHARS -> Unit
                key !in this && size >= MAX_COOKIES -> Unit
                else -> this[key] = ResponseCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    hostOnly = cookie.hostOnly,
                    path = cookie.path,
                )
            }
        }
    }

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
        const val MAX_COOKIES = 50
        const val MAX_COOKIE_CHARS = 4_096
        val METHOD_PRESERVING_REDIRECTS = setOf(307, 308)
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}