package com.alal.yft.extractor.sites.vk

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.sites.testing.Fixtures
import java.net.URI
import kotlinx.coroutines.delay

internal const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) FixtureBrowser/1.0"
internal const val PAGE_COOKIE = "remixsid=fixture-session"
internal const val NOW_MS = 1_761_700_000_000L

/** The expiry the tests put into the fixtures' `expires=FIXTURE` (VK's 13-digit form). */
internal const val EXPIRES_MS = 1_761_800_000_000L

internal const val VIDEO_LINK = "https://vk.com/video-50883936_456244102"
internal const val CLIP_LINK = "https://vk.com/clip30014565_456240946"
internal const val POSTLIVE_LINK = "https://vk.com/video-140332_456239111"
internal const val VIDEO_OWNER = -50883936L
internal const val VIDEO_ID = 456244102L

/** The VK0 fixtures, with a real-looking expiry put in (query `expires=` and `/expires/` path). */
internal object VkFixtures {
    fun video(): String = Fixtures.read("vk/embed-video-public.html").withExpiry()

    fun clip(): String = Fixtures.read("vk/embed-clip-public.html").withExpiry()

    fun postlive(): String = Fixtures.read("vk/embed-postlive.html").withExpiry()

    fun notFound(): String = Fixtures.read("vk/embed-not-found.html")

    fun shell(): String = Fixtures.read("vk/embed-spa-shell-vkvideo.html")

    fun desktopPage(): String = Fixtures.read("vk/page-vkvideo-desktop.html")

    /** VK5: the embed's adult-content answer (English), and the same page in Russian. */
    fun adult(): String = Fixtures.read("vk/embed-adult-content.html")

    fun adultRussian(): String = adult().replace(ADULT_TEXT_EN, ADULT_TEXT_RU)

    /** VK5: api.vkvideo.ru's `video.get` for an adult video, a deleted one, and error 5. */
    fun apiAdult(): String = Fixtures.read("vk/api-video-get-adult.json").withExpiry()

    fun apiDeleted(): String = Fixtures.read("vk/api-video-get-deleted.json")

    fun apiAuthError(): String = Fixtures.read("vk/api-error-auth.json")

    /** The fixture's message as VK sends it (no-break spaces as at VK5's recon). */
    const val ADULT_TEXT_EN = "Algorithms determined that the\u00A0video may contain adult " +
        "content. Such videos can&#39;t be viewed on\u00A0external sites"
    const val ADULT_TEXT_RU = "Алгоритмы определили, что видео может содержать контент " +
        "для\u00A0взрослых,\u00A0— такие ролики не получится смотреть на\u00A0внешних сайтах"

    private fun String.withExpiry(): String =
        replace("expires=FIXTURE", "expires=$EXPIRES_MS")
            .replace("expires\\/FIXTURE", "expires\\/${EXPIRES_MS / 1_000}")
}

/** Synthetic embed pages in VK's shape: `window.cur` → `apiPrefetchCache` → `response.items`. */
internal object VkPages {
    fun embed(vararg items: String): String = cache(
        "{\"method\":\"video.get\",\"response\":{\"count\":${items.size}," +
            "\"items\":[${items.joinToString(",")}]},\"error\":null}",
    )

    fun cache(vararg entries: String): String =
        "<!DOCTYPE html><html><head><title>VK</title></head><body><script>" +
            "window.cur = Object.assign(window.cur || {}, {\"apiPrefetchCache\":[" +
            entries.joinToString(",") +
            "]}, {\"screen\":\"embed\",\"module\":\"video\"});</script></body></html>"

    /** One item; [files] and [title] are raw JSON (the title without its quotes). */
    fun item(
        files: String?,
        ownerId: Long = VIDEO_OWNER,
        id: Long = VIDEO_ID,
        title: String? = "Synthetic fixture",
        more: String = "",
    ): String = buildString {
        append("{\"id\":").append(id).append(",\"owner_id\":").append(ownerId)
        title?.let { append(",\"title\":\"").append(it).append('"') }
        append(",\"duration\":30")
        append(more)
        files?.let { append(",\"files\":{").append(it).append('}') }
        append('}')
    }

    /** `"key":"https://vkvd1.okcdn.ru/{key}/F?expires=…"` for each key. */
    fun files(vararg keys: String): String =
        keys.joinToString(",") { key -> "\"$key\":\"${fileUrl(key)}\"" }

    fun fileUrl(key: String): String = "https://vkvd1.okcdn.ru/$key/F?expires=$EXPIRES_MS"
}

/** VK5: path D's adult video (the API fixture's) and login.vk.ru's token answer. */
internal const val ADULT_LINK = "https://vkvideo.ru/video-215889201_456243439"
internal const val ADULT_CANONICAL = "https://vk.com/video-215889201_456243439"
internal const val ADULT_OWNER = -215889201L
internal const val ADULT_ID = 456243439L
internal const val TOKEN_HOST = "login.vk.ru"
internal const val API_HOST = "api.vkvideo.ru"

/** A token-like test value; it holds FIXTURE so a leak into Details fails the hygiene rule. */
internal const val TOKEN = "anonym.FIXTURE.token.0123456789"
internal const val TOKEN_2 = "anonym.FIXTURE.token.9876543210"

internal object VkApiPages {
    /** login.vk.ru's answer in VK5 recon's shape: both expiries in epoch seconds. */
    fun token(value: String = TOKEN, expiresAtEpochMs: Long = NOW_MS + 86_400_000L): String =
        "{\"type\":\"okay\",\"data\":{\"access_token\":\"$value\"," +
            "\"expires\":${expiresAtEpochMs / 1_000},\"expired_at\":${expiresAtEpochMs / 1_000}}}"

    fun error(code: Int): String =
        "{\"error\":{\"error_code\":$code,\"error_msg\":\"Synthetic fixture\"}}"

    /** `video.get`'s answer shape around [items] (raw JSON). */
    fun videoGet(vararg items: String): String =
        "{\"response\":{\"count\":${items.size},\"items\":[${items.joinToString(",")}]}}"
}

/**
 * VK's two embed hosts as a test scripts them. Embed requests are answered per host in order
 * (the last answer repeats), manifests by URL; [delayMillis] (virtual time) stands in for a slow
 * server, by the request's 1-based number. Every request is recorded. VK5: [api] answers path D's
 * hosts (login.vk.ru, api.vkvideo.ru) in order per host, the last answer repeating; a POST to any
 * other host fails the test.
 */
internal class ScriptedVkHttp(
    private val embeds: Map<String, List<ExtractorHttpResult>>,
    private val manifests: Map<String, ExtractorHttpResult> = emptyMap(),
    private val delayMillis: (number: Int) -> Long = { 0L },
    private val api: Map<String, List<ExtractorHttpResult>> = emptyMap(),
) : ExtractorHttpClient {
    data class Request(
        val url: String,
        val headers: Map<String, String>,
        val maxBodyBytes: Long,
        val method: String = "GET",
        val body: String? = null,
    ) {
        val host: String
            get() = URI(url).host

        val isEmbed: Boolean
            get() = URI(url).path == "/video_ext.php"

        val isApi: Boolean
            get() = host == TOKEN_HOST || host == API_HOST
    }

    val requests = mutableListOf<Request>()
    private val asked = mutableMapOf<String, Int>()

    val embedRequests: List<Request>
        get() = requests.filter(Request::isEmbed)

    val apiRequests: List<Request>
        get() = requests.filter(Request::isApi)

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val request = Request(url, headers, maxBodyBytes)
        requests += request
        val wait = delayMillis(requests.size)
        if (wait > 0) delay(wait)
        if (request.isApi) return apiAnswer(request)
        if (!request.isEmbed) return manifests[url] ?: status(404)
        val attempt = asked.getOrDefault(request.host, 0)
        asked[request.host] = attempt + 1
        val script = embeds[request.host] ?: return status(404)
        return script[minOf(attempt, script.lastIndex)]
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val request = Request(url, headers, maxBodyBytes, method = "POST", body = body)
        check(request.host == TOKEN_HOST) { "VK posts only path D's token request" }
        requests += request
        val wait = delayMillis(requests.size)
        if (wait > 0) delay(wait)
        return apiAnswer(request)
    }

    private fun apiAnswer(request: Request): ExtractorHttpResult {
        val attempt = asked.getOrDefault(request.host, 0)
        asked[request.host] = attempt + 1
        val script = api[request.host] ?: error("No script for ${request.host}")
        return script[minOf(attempt, script.lastIndex)]
    }

    companion object {
        fun page(body: String): ExtractorHttpResult.Success = ExtractorHttpResult.Success(
            statusCode = 200,
            body = body,
            finalUrl = "https://vk.com/video_ext.php",
            contentType = "text/html; charset=windows-1251",
        )

        fun json(body: String): ExtractorHttpResult.Success = ExtractorHttpResult.Success(
            statusCode = 200,
            body = body,
            finalUrl = "https://api.vkvideo.ru/method/video.get",
            contentType = "application/json; charset=utf-8",
        )

        fun text(body: String): ExtractorHttpResult.Success = ExtractorHttpResult.Success(
            statusCode = 200,
            body = body,
            finalUrl = "https://vkvd1.okcdn.ru/manifest",
            contentType = "application/octet-stream",
        )

        fun status(code: Int): ExtractorHttpResult.Failure = ExtractorHttpResult.Failure(
            when (code) {
                403 -> SiteExtractionFailure.LOGIN_REQUIRED
                404 -> SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
                429 -> SiteExtractionFailure.RATE_LIMITED
                451 -> SiteExtractionFailure.GEO_RESTRICTED
                else -> SiteExtractionFailure.HTTP_STATUS
            },
            code,
        )
    }
}
