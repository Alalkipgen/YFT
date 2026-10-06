package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class SecureDownloadHttp(
    client: OkHttpClient,
    maxRedirects: Int,
    callTimeoutSeconds: Long,
) {
    init {
        require(maxRedirects >= 0)
        require(callTimeoutSeconds > 0)
    }

    private val maxRedirects = maxRedirects
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    suspend fun execute(
        credentialOrigin: HttpUrl,
        initialUrl: HttpUrl,
        context: BrowserRequestContext,
        configure: Request.Builder.() -> Unit,
    ): Result {
        var currentUrl = initialUrl
        var redirectCount = 0
        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .apply {
                    context.headersForTarget(credentialOrigin, currentUrl)
                        .forEach { (name, value) ->
                            runCatching { header(name, value) }
                        }
                    header("Accept-Encoding", "identity")
                    configure()
                }
                .build()
            val response = client.newCall(request).await()
            if (response.code !in REDIRECT_CODES) {
                return Result.Completed(response, currentUrl)
            }
            if (redirectCount >= maxRedirects) {
                response.close()
                return Result.Failed(DownloadFailureReason.TOO_MANY_REDIRECTS)
            }
            val target = response.header("Location")
                ?.let(currentUrl::resolve)
                ?.takeIf { it.isSafeDownloadUrl() }
            response.close()
            if (target == null || currentUrl.isHttps && !target.isHttps) {
                return Result.Failed(DownloadFailureReason.UNSAFE_REDIRECT)
            }
            currentUrl = target
            redirectCount += 1
        }
    }

    sealed interface Result {
        data class Completed(
            val response: Response,
            val finalUrl: HttpUrl,
        ) : Result

        data class Failed(val reason: DownloadFailureReason) : Result
    }

    private fun BrowserRequestContext.headersForTarget(
        credentialOrigin: HttpUrl,
        targetUrl: HttpUrl,
    ): Map<String, String> {
        val replayable = replayHeaders()
        if (targetUrl.hasSameOrigin(credentialOrigin)) return replayable
        val headers = replayable.filterKeys { name ->
            name.lowercase(Locale.US) in CROSS_ORIGIN_HEADER_ALLOWLIST
        }.toMutableMap()
        // Like a browser, another host (a playlist's CDN, a redirect's file host) still learns
        // the page's origin: the observed Origin and an origin-only Referer, never cookies.
        replayable.entries
            .firstOrNull { (name, _) -> name.equals("Origin", ignoreCase = true) }
            ?.value
            ?.httpsOrigin()
            ?.let { headers["Origin"] = it }
        pageUrl?.httpsOrigin()?.let { headers["Referer"] = "$it/" }
        return headers
    }

    private fun String.httpsOrigin(): String? = toHttpUrlOrNull()
        ?.takeIf { it.isHttps }
        ?.let { url ->
            val port = if (url.port == HTTPS_PORT) "" else ":${url.port}"
            "https://${url.host}$port"
        }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

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

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val HTTPS_PORT = 443
        val CROSS_ORIGIN_HEADER_ALLOWLIST = setOf(
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
        )
    }
}

internal fun String.toSafeDownloadUrl(): HttpUrl? = toHttpUrlOrNull()
    ?.takeIf { it.isSafeDownloadUrl() }

internal fun HttpUrl.isSafeDownloadUrl(): Boolean =
    username.isEmpty() &&
        password.isEmpty() &&
        (isHttps || scheme == "http" && host.isLoopbackHost())

private fun String.isLoopbackHost(): Boolean =
    equals("localhost", ignoreCase = true) || this == "127.0.0.1" || this == "::1"