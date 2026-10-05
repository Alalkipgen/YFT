package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PageNavigationHeaders
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * Fetches a page the way a careful script would, for checking a link before it is opened.
 *
 * Nothing about the user travels with it: no cookies, no browser session, only a plain YFT user
 * agent. Redirects are followed by hand, at most [Policy.maxRedirects] times and never from
 * HTTPS down to HTTP. A response that is itself media is reported without reading its body; an
 * HTML body is read up to [Policy.maxBodyBytes] and the rest is ignored.
 */
class HeadlessPageFetcher(
    client: OkHttpClient,
    private val userAgent: String,
    private val policy: Policy = Policy(),
    navigationHeaders: Map<String, String> = PageNavigationHeaders.DEFAULTS,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {
    data class Policy(
        val maxRedirects: Int = 5,
        val maxBodyBytes: Long = 2L * 1024 * 1024,
        val callTimeoutSeconds: Long = 60,
        val connectTimeoutMillis: Long = 20_000,
        val readTimeoutMillis: Long = 20_000,
        val retryDelaysMillis: List<Long> = listOf(1_000, 3_000),
    ) {
        init {
            require(maxRedirects >= 0)
            require(maxBodyBytes > 0)
            require(callTimeoutSeconds > 0)
            require(connectTimeoutMillis > 0 && readTimeoutMillis > 0)
            require(retryDelaysMillis.size <= 2 && retryDelaysMillis.all { it >= 0 })
        }
    }

    sealed interface Result {
        /** An HTML page, possibly cut at the size limit, and the address it finally came from. */
        data class Page(val url: String, val html: String) : Result

        /** The link answered with media itself, such as a video file or a stream manifest. */
        data class Media(
            val url: String,
            val mimeType: String,
            val contentLengthBytes: Long?,
        ) : Result

        data class Failed(val reason: FailureReason) : Result
    }

    enum class FailureReason {
        INVALID_URL,
        NETWORK,
        HTTP_STATUS,
        INSECURE_REDIRECT,
        TOO_MANY_REDIRECTS,
        NOT_A_PAGE,
    }

    // Only navigation roles can be customized; callers cannot add cookies or account headers.
    private val pageHeaders = PageNavigationHeaders.withDefaults(
        navigationHeaders.filterKeys { name ->
            PageNavigationHeaders.DEFAULTS.keys.any { it.equals(name, ignoreCase = true) }
        },
    )

    private val fetchClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false)
        .connectTimeout(policy.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(policy.readTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(policy.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(url: String): Result {
        var current = url.toHttpUrlOrNull()
            ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
            ?: return Result.Failed(FailureReason.INVALID_URL)
        var redirects = 0
        var retry = 0
        var started = System.nanoTime()
        val budget = TimeUnit.SECONDS.toNanos(policy.callTimeoutSeconds)
        suspend fun waitForRetry(): Boolean {
            if (retry >= policy.retryDelaysMillis.size) return false
            retryDelay(policy.retryDelaysMillis[retry++])
            currentCoroutineContext().ensureActive()
            started = System.nanoTime()
            return true
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            val outcome = try {
                val remaining = budget - (System.nanoTime() - started)
                if (remaining <= 0) throw SocketTimeoutException("Request timed out")
                execute(current, remaining)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: IOException) {
                currentCoroutineContext().ensureActive()
                if (failure is SSLPeerUnverifiedException ||
                    (failure is SSLHandshakeException && failure.cause is CertificateException) ||
                    failure is ProtocolException || !waitForRetry()) {
                    return Result.Failed(FailureReason.NETWORK)
                }
                continue
            }
            when (outcome) {
                is Outcome.Redirect -> {
                    if (redirects >= policy.maxRedirects) {
                        return Result.Failed(FailureReason.TOO_MANY_REDIRECTS)
                    }
                    val target = outcome.location?.let(current::resolve)
                        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
                        ?: return Result.Failed(FailureReason.HTTP_STATUS)
                    if (current.isHttps && !target.isHttps) {
                        return Result.Failed(FailureReason.INSECURE_REDIRECT)
                    }
                    current = target
                    redirects += 1
                }
                Outcome.TransientStatus -> {
                    if (!waitForRetry()) return Result.Failed(FailureReason.HTTP_STATUS)
                }
                is Outcome.Done -> return outcome.result
            }
        }
    }

    private suspend fun execute(url: HttpUrl, remainingNanos: Long): Outcome {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .apply { pageHeaders.forEach { (name, value) -> header(name, value) } }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = fetchClient.newCall(request)
            call.timeout().timeout(remainingNanos, TimeUnit.NANOSECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) {
                            response.close()
                            return
                        }
                        // OkHttp's callback already runs off-main. Keep the cancellation
                        // handler alive through the entire bounded body read, not just headers.
                        try {
                            val outcome = response.use { read(it) }
                            if (continuation.isActive) continuation.resume(outcome)
                        } catch (failure: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
        }
    }

    private fun read(response: Response): Outcome {
        if (response.code in REDIRECT_CODES) return Outcome.Redirect(response.header("Location"))
        if (response.code in RETRYABLE_STATUS_CODES) return Outcome.TransientStatus
        if (response.code !in SUCCESS_CODES) {
            return Outcome.Done(Result.Failed(FailureReason.HTTP_STATUS))
        }
        val finalUrl = response.request.url.toString()
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotEmpty)
        if (mimeType != null && mimeType.isMedia()) {
            return Outcome.Done(
                Result.Media(
                    url = finalUrl,
                    mimeType = mimeType,
                    contentLengthBytes = response.header("Content-Length")?.toLongOrNull(),
                ),
            )
        }
        if (mimeType != null && mimeType !in PAGE_TYPES) {
            return Outcome.Done(Result.Failed(FailureReason.NOT_A_PAGE))
        }
        val body = response.body ?: return Outcome.Done(Result.Page(finalUrl, ""))
        val charset = runCatching { body.contentType()?.charset() }.getOrNull()
            ?: Charsets.UTF_8
        val html = readBounded(body.source(), charset)
        return Outcome.Done(Result.Page(finalUrl, html))
    }

    private fun readBounded(source: okio.BufferedSource, charset: Charset): String {
        val buffer = Buffer()
        while (buffer.size < policy.maxBodyBytes) {
            val read = source.read(buffer, minOf(CHUNK_BYTES, policy.maxBodyBytes - buffer.size))
            if (read == -1L) break
        }
        return buffer.readString(charset)
    }

    private fun String.isMedia(): Boolean =
        startsWith("video/") || startsWith("audio/") || this in MANIFEST_TYPES

    private sealed interface Outcome {
        data object TransientStatus : Outcome
        data class Redirect(val location: String?) : Outcome
        data class Done(val result: Result) : Outcome
    }

    private companion object {
        const val CHUNK_BYTES = 64L * 1024
        val RETRYABLE_STATUS_CODES = setOf(502, 503, 504)
        val SUCCESS_CODES = 200..299
        val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
        val PAGE_TYPES = setOf("text/html", "application/xhtml+xml", "text/plain")
        val MANIFEST_TYPES = setOf(
            "application/dash+xml",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )
    }
}
