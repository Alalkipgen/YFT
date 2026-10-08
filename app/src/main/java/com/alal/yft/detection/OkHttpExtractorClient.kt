package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
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
 * By default there is no cookie jar: a lookup never sends a cookie it was not given. The cookies
 * its own responses set are reported in [ExtractorHttpResult.Success.cookies], in memory; with
 * [Policy.sendResponseCookies] they also go to the next redirect hop they match, as a browser's
 * would. A header or cookie pair OkHttp would refuse is left out and counted in the details,
 * and no thrown error escapes: every request ends in a result naming the error's class.
 */
class OkHttpExtractorClient(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) : ExtractorHttpClient {
    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 60,
        val connectTimeoutMillis: Long = 20_000,
        val readTimeoutMillis: Long = 20_000,
        val retryDelaysMillis: List<Long> = listOf(1_000, 3_000),
        /**
         * Sends the cookies this lookup's own responses set to a later redirect hop they match.
         * Some sites set a session on their short link's redirect and refuse the page without it.
         */
        val sendResponseCookies: Boolean = false,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
            require(connectTimeoutMillis > 0 && readTimeoutMillis > 0)
            require(retryDelaysMillis.size <= 2 && retryDelaysMillis.all { it >= 0 })
        }
    }

    private val extractorClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false)
        .connectTimeout(policy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(policy.readTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(policy.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        return request(url, headers, maxBodyBytes, jsonBody = null)
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult = request(url, headers, maxBodyBytes, jsonBody = body)

    /** Only transient transport failures and 502/503/504 get at most two retries. */
    private suspend fun request(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
        jsonBody: String?,
    ): ExtractorHttpResult {
        require(maxBodyBytes > 0)
        val safe = SafeHeaders.of(headers)
        return withContext(Dispatchers.IO) {
            for (attempt in 0..policy.retryDelaysMillis.size) {
                ensureActive()
                var retryableNetwork = true
                val result = try {
                    cancellable { onCall -> fetch(url, safe, maxBodyBytes, jsonBody, onCall) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: IOException) {
                    ensureActive()
                    retryableNetwork = failure !is SSLPeerUnverifiedException &&
                        !(failure is SSLHandshakeException &&
                            failure.cause is CertificateException) &&
                        failure !is ProtocolException
                    ExtractorHttpResult.Failure(
                        SiteExtractionFailure.NETWORK,
                        details = listOf(errorLine(failure)),
                    )
                } catch (failure: Exception) {
                    // A refused header value, a broken redirect address or a parser error is
                    // not a network problem and not a site change; it is reported by class.
                    ensureActive()
                    ExtractorHttpResult.Failure(
                        SiteExtractionFailure.MALFORMED_RESPONSE,
                        details = listOf(errorLine(failure)),
                    )
                }
                val retryable = result is ExtractorHttpResult.Failure && (
                    (result.reason == SiteExtractionFailure.NETWORK && retryableNetwork) ||
                        result.statusCode in RETRYABLE_STATUS_CODES
                    )
                if (!retryable || attempt == policy.retryDelaysMillis.size) {
                    return@withContext result.withDetails(safe.details)
                }
                retryDelay(policy.retryDelaysMillis[attempt])
            }
            error("Retry budget exhausted")
        }
    }

    /**
     * Checks one media file with a one-byte range request: the same HTTPS, redirect and
     * credential rules as [get], no retries, and [timeoutMillis] for the whole chain.
     */
    override suspend fun probe(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Long,
    ): ExtractorProbeResult {
        require(timeoutMillis > 0)
        val safe = SafeHeaders.of(headers)
        return withContext(Dispatchers.IO) {
            try {
                cancellable { onCall -> probeFetch(url, safe, timeoutMillis, onCall) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                ensureActive()
                ExtractorProbeResult.Refused(
                    reason = if (failure is IOException) {
                        SiteExtractionFailure.NETWORK
                    } else {
                        SiteExtractionFailure.MALFORMED_RESPONSE
                    },
                    error = failure.javaClass.simpleName.ifEmpty { "Exception" },
                )
            }
        }
    }

    /** Cancellation closes the active socket even while a response body is being read. */
    private suspend fun <T> cancellable(block: (onCall: (Call) -> Unit) -> T): T =
        coroutineScope {
            val activeCall = AtomicReference<Call?>()
            val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    activeCall.get()?.cancel()
                }
            }
            try {
                block { call ->
                    activeCall.set(call)
                    ensureActive()
                }
            } finally {
                canceller.cancel()
            }
        }

    private fun probeFetch(
        url: String,
        safe: SafeHeaders,
        timeoutMillis: Long,
        onCall: (Call) -> Unit,
    ): ExtractorProbeResult {
        var current = url.toSecureHttpUrl()
            ?: return ExtractorProbeResult.Refused(SiteExtractionFailure.UNSUPPORTED_URL)
        val credentialHost = current.host
        var forwardCredentials = true
        var redirects = 0
        val started = System.nanoTime()
        val budget = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (true) {
            val remaining = budget - (System.nanoTime() - started)
            if (remaining <= 0) throw SocketTimeoutException("File check timed out")
            val request = Request.Builder()
                .url(current)
                .get()
                .apply { addSafeHeaders(safe, forwardCredentials, jar = emptyList()) }
                // One byte and no compression, so the answer states the file's exact size.
                .header("Range", "bytes=0-0")
                .header("Accept-Encoding", "identity")
                .build()
            val call = extractorClient.newCall(request)
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
            onCall(call)
            val hop = call.execute().use { response ->
                when {
                    response.isRedirect || response.code == HTTP_PERMANENT_REDIRECT -> {
                        val next = response.header("Location")
                            ?.let(current::resolve)
                            ?.takeIf { it.isHttps }
                            ?: return ExtractorProbeResult.Refused(
                                SiteExtractionFailure.UNSUPPORTED_URL,
                                response.code,
                            )
                        redirects += 1
                        if (redirects > policy.maxRedirects) {
                            return ExtractorProbeResult.Refused(
                                SiteExtractionFailure.HTTP_STATUS,
                                response.code,
                            )
                        }
                        if (!SiteScope.sameSite(credentialHost, next.host)) {
                            forwardCredentials = false
                        }
                        next
                    }

                    response.code == HTTP_PARTIAL_CONTENT -> return ExtractorProbeResult.Answered(
                        response.code,
                        totalBytesOf(response.header("Content-Range")),
                    )

                    response.isSuccessful -> return ExtractorProbeResult.Answered(
                        response.code,
                        response.body?.contentLength()?.takeIf { it >= 0 },
                    )

                    else -> return ExtractorProbeResult.Refused(
                        response.code.toFailure(),
                        response.code,
                    )
                }
            }
            current = hop
        }
    }

    private fun fetch(
        url: String,
        safe: SafeHeaders,
        maxBodyBytes: Long,
        jsonBody: String?,
        onCall: (Call) -> Unit,
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

        val started = System.nanoTime()
        val budget = TimeUnit.SECONDS.toNanos(policy.callTimeoutSeconds)
        while (true) {
            val remaining = budget - (System.nanoTime() - started)
            if (remaining <= 0) throw SocketTimeoutException("Request timed out")
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
                    val hopUrl = current.toString()
                    val jar = cookies.values.filter {
                        policy.sendResponseCookies && it.matches(hopUrl)
                    }
                    addSafeHeaders(safe, forwardCredentials, jar)
                }
                .build()

            val call = extractorClient.newCall(request)
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
            onCall(call)
            val hop = call.execute().use { response ->
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
                                SiteExtractionFailure.HTTP_STATUS,
                                response.code,
                                details = listOf("too many redirects"),
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

    /**
     * Adds the headers OkHttp accepts. Credentials stay with the site they were captured for;
     * the [jar] holds this lookup's own response cookies that match the hop, and a later value
     * replaces the caller's pair of the same name, as a browser's cookie store would.
     */
    private fun Request.Builder.addSafeHeaders(
        safe: SafeHeaders,
        forwardCredentials: Boolean,
        jar: List<ResponseCookie>,
    ) {
        safe.headers.forEach { (name, value) ->
            if (forwardCredentials || !SiteScope.isCredentialHeader(name)) header(name, value)
        }
        val pairs = LinkedHashMap<String, String>()
        if (forwardCredentials) safe.cookiePairs.forEach { (name, pair) -> pairs[name] = pair }
        jar.filter { SafeHeaders.isValue(it.pair) }.forEach { pairs[it.name] = it.pair }
        if (pairs.isNotEmpty()) header("Cookie", pairs.values.joinToString("; "))
    }

    private fun ExtractorHttpResult.withDetails(extra: List<String>): ExtractorHttpResult =
        when {
            extra.isEmpty() -> this
            this is ExtractorHttpResult.Success -> copy(details = extra + details)
            this is ExtractorHttpResult.Failure -> copy(details = extra + details)
            else -> this
        }

    /** `bytes 0-0/12345` -> 12345; an unknown total (`*`) or a broken header -> null. */
    private fun totalBytesOf(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

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
        val RETRYABLE_STATUS_CODES = setOf(502, 503, 504)
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_PERMANENT_REDIRECT = 308
        const val MAX_COOKIES = 50
        const val MAX_COOKIE_CHARS = 4_096
        val METHOD_PRESERVING_REDIRECTS = setOf(307, 308)
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** A short, non-sensitive diagnostic line naming only the class of [failure]. */
internal fun errorLine(failure: Throwable): String =
    "error: " + failure.javaClass.simpleName.ifEmpty { "Exception" }

/**
 * Request headers split into what OkHttp accepts and a count of what it would refuse.
 *
 * OkHttp throws on a header name outside visible ASCII or a value with other control or
 * non-ASCII characters; a browser's cookie store can hold such a pair, and one bad pair must not
 * stop the whole request. The `Cookie` header is checked pair by pair.
 */
internal class SafeHeaders private constructor(
    val headers: List<Pair<String, String>>,
    /** Name to `name=value` pair, in the caller's order. */
    val cookiePairs: List<Pair<String, String>>,
    val details: List<String>,
) {
    companion object {
        fun of(raw: Map<String, String>): SafeHeaders {
            val headers = mutableListOf<Pair<String, String>>()
            val pairs = mutableListOf<Pair<String, String>>()
            var droppedHeaders = 0
            var droppedPairs = 0
            raw.forEach { (rawName, rawValue) ->
                val name = rawName.trim()
                val value = rawValue.trim()
                when {
                    name.isEmpty() || value.isEmpty() -> Unit
                    !isName(name) -> droppedHeaders += 1
                    name.equals("Cookie", ignoreCase = true) -> value.split(';').forEach { part ->
                        val pair = part.trim()
                        val pairName = pair.substringBefore('=').trim()
                        when {
                            pair.isEmpty() -> Unit
                            pairName.isEmpty() || !isName(pairName) || !isValue(pair) ->
                                droppedPairs += 1
                            else -> pairs += pairName to pair
                        }
                    }
                    !isValue(value) -> droppedHeaders += 1
                    else -> headers += name to value
                }
            }
            val details = buildList {
                if (droppedPairs > 0) add("session pairs left out: $droppedPairs")
                if (droppedHeaders > 0) add("headers left out: $droppedHeaders")
            }
            return SafeHeaders(headers, pairs, details)
        }

        fun isName(text: String): Boolean =
            text.isNotEmpty() && text.all { it in '\u0021'..'\u007e' }

        fun isValue(text: String): Boolean =
            text.all { it == '\t' || it in '\u0020'..'\u007e' }
    }
}
