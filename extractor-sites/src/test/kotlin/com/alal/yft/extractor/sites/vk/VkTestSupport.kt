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

/**
 * VK's two embed hosts as a test scripts them. Embed requests are answered per host in order
 * (the last answer repeats), manifests by URL; [delayMillis] (virtual time) stands in for a slow
 * server, by the request's 1-based number. Every request is recorded.
 */
internal class ScriptedVkHttp(
    private val embeds: Map<String, List<ExtractorHttpResult>>,
    private val manifests: Map<String, ExtractorHttpResult> = emptyMap(),
    private val delayMillis: (number: Int) -> Long = { 0L },
) : ExtractorHttpClient {
    data class Request(val url: String, val headers: Map<String, String>, val maxBodyBytes: Long) {
        val host: String
            get() = URI(url).host

        val isEmbed: Boolean
            get() = URI(url).path == "/video_ext.php"
    }

    val requests = mutableListOf<Request>()
    private val asked = mutableMapOf<String, Int>()

    val embedRequests: List<Request>
        get() = requests.filter(Request::isEmbed)

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val request = Request(url, headers, maxBodyBytes)
        requests += request
        val wait = delayMillis(requests.size)
        if (wait > 0) delay(wait)
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
    ): ExtractorHttpResult = error("VK v1 never posts")

    companion object {
        fun page(body: String): ExtractorHttpResult.Success = ExtractorHttpResult.Success(
            statusCode = 200,
            body = body,
            finalUrl = "https://vk.com/video_ext.php",
            contentType = "text/html; charset=windows-1251",
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
