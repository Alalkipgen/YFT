package com.alal.yft.update

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/** What one question to GitHub gave. */
sealed interface CheckResult {
    /** GitHub answered and no release qualifies. */
    data object UpToDate : CheckResult

    data class Available(val update: FoundUpdate) : CheckResult

    /** No connection or a timeout: not counted, so the next app open asks again. */
    data object Offline : CheckResult

    /** GitHub's rate limit: no request before [until] (epoch millis, at most 24 h ahead). */
    data class RateLimited(val until: Long) : CheckResult

    /** Any other answer: another status, a bad or oversize body, a refused redirect. */
    data object Failed : CheckResult
}

/** Asks GitHub for the update to offer; [UpdateChecker] in the app, fakes in tests. */
fun interface ReleaseCheck {
    suspend fun check(installedCode: Long, testMode: Boolean): CheckResult
}

/**
 * The check (U1): `GET https://api.github.com/repos/Alalkipgen/YFT/releases?per_page=10` with
 * GitHub's JSON media type and API version and nothing else — no token, no cookie, no account. A
 * client derived from the shared one (the system's CAs only): connect 15 s, the whole call 30 s,
 * redirects followed here only to the updater's HTTPS hosts. At most [GitHubReleases.MAX_BYTES]
 * of the body are read. Logs carry the host and the status only.
 */
class UpdateChecker internal constructor(
    client: OkHttpClient,
    private val releasesUrl: HttpUrl,
    private val allowedHosts: Set<String>,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    callTimeoutMillis: Long = CALL_TIMEOUT_SECONDS * 1000,
) : ReleaseCheck {
    constructor(client: OkHttpClient) : this(
        client = client,
        releasesUrl = RELEASES_URL.toHttpUrl(),
        allowedHosts = UpdateHttp.HOSTS,
        ioDispatcher = Dispatchers.IO,
        clock = System::currentTimeMillis,
        log = { message -> Log.i(TAG, message) },
    )

    private val http = UpdateHttp(
        client = client.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .build(),
        allowedHosts = allowedHosts,
    )

    override suspend fun check(installedCode: Long, testMode: Boolean): CheckResult =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url(releasesUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .get()
                .build()
            try {
                http.execute(request).use { response ->
                    log("Update check: ${response.request.url.host} ${response.code}")
                    read(response, installedCode, testMode)
                }
            } catch (refused: UpdateHttp.Refused) {
                log("Update check refused: ${refused.message}")
                CheckResult.Failed
            } catch (failure: IOException) {
                log("Update check offline: ${failure.javaClass.simpleName}")
                CheckResult.Offline
            }
        }

    private fun read(response: Response, installedCode: Long, testMode: Boolean): CheckResult =
        when (response.code) {
            HTTP_OK -> {
                val releases = response.body
                    ?.let { readCapped(it, GitHubReleases.MAX_BYTES) }
                    ?.let(GitHubReleases::parse)
                if (releases == null) {
                    CheckResult.Failed
                } else {
                    GitHubReleases.candidate(releases, installedCode, testMode)
                        ?.let { CheckResult.Available(it) }
                        ?: CheckResult.UpToDate
                }
            }
            HTTP_FORBIDDEN, HTTP_TOO_MANY -> rateLimit(response) ?: CheckResult.Failed
            else -> CheckResult.Failed
        }

    /** GitHub's `retry-after`, or `x-ratelimit-remaining: 0` with its reset; else null. */
    private fun rateLimit(response: Response): CheckResult.RateLimited? {
        val now = clock()
        val retryAfter = response.header("retry-after")?.trim()?.toLongOrNull()
        val remaining = response.header("x-ratelimit-remaining")?.trim()
        val reset = response.header("x-ratelimit-reset")?.trim()?.toLongOrNull()
        val until = when {
            retryAfter != null && retryAfter >= 0 ->
                now + retryAfter.coerceAtMost(MAX_WAIT_MS / 1000) * 1000
            remaining == "0" && reset != null && reset > 0 ->
                reset.coerceAtMost(Long.MAX_VALUE / 1000) * 1000
            else -> return null
        }
        return CheckResult.RateLimited(until.coerceIn(now + MIN_WAIT_MS, now + MAX_WAIT_MS))
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Alalkipgen/YFT/releases?per_page=10"
        const val API_VERSION = "2022-11-28"
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val CALL_TIMEOUT_SECONDS = 30L
        const val MIN_WAIT_MS = 60_000L
        const val MAX_WAIT_MS = 24 * 60 * 60_000L
        private const val HTTP_OK = 200
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY = 429
        private const val TAG = "YftUpdate"
    }
}

/**
 * HTTPS calls to the updater's hosts only (§4.6): GitHub's API, its release links and the
 * download hosts behind their redirects. Redirects are followed here, at most [MAX_REDIRECTS],
 * each checked before it is asked; any other host or plain HTTP is refused. No cookies, no cache.
 * The addresses of redirects are never logged or kept: they carry GitHub's signatures.
 */
internal class UpdateHttp(client: OkHttpClient, private val allowedHosts: Set<String>) {
    private val client = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Thrown for a refused address; its message names the host only. */
    class Refused(message: String) : Exception(message)

    fun allows(url: HttpUrl): Boolean = url.isHttps && url.host in allowedHosts

    /** [onCall] sees each hop's call, so a cancelled download can stop the one in flight. */
    fun execute(request: Request, onCall: (Call) -> Unit = {}): Response {
        var current = request
        repeat(MAX_REDIRECTS + 1) {
            if (!allows(current.url)) throw Refused("host ${current.url.host}")
            val call = client.newCall(current)
            onCall(call)
            val response = call.execute()
            if (!response.isRedirect) return response
            val next = response.header("Location")?.let { current.url.resolve(it) }
            response.close()
            current = current.newBuilder().url(next ?: throw Refused("redirect")).build()
        }
        throw Refused("too many redirects")
    }

    companion object {
        const val MAX_REDIRECTS = 5
        val HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
    }
}

/** The body as UTF-8 text, or null when it is longer than [maxBytes]. */
internal fun readCapped(body: ResponseBody, maxBytes: Long): String? {
    if (body.contentLength() > maxBytes) return null
    val source = body.source()
    if (source.request(maxBytes + 1)) return null
    return source.buffer.readUtf8()
}
