package com.alal.yft.extractor.api

/**
 * Narrow, bounded HTTP boundary for adapters.
 *
 * Adapters are pure JVM code and never own a networking stack, so they stay testable against
 * committed fixtures with no live calls. Implementations enforce HTTPS, a redirect limit, a call
 * timeout and [maxBodyBytes]; a larger body fails rather than being silently truncated.
 */
interface ExtractorHttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    ): ExtractorHttpResult

    /**
     * Posts a JSON document to [url] under the same transport rules as [get].
     *
     * Some sites answer their own web player through a JSON endpoint rather than the page, so an
     * adapter needs a POST to read what the user's browser already receives. Implementations must
     * apply the identical HTTPS, redirect, timeout and size limits; adapters never see a socket.
     */
    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    ): ExtractorHttpResult

    companion object {
        const val DEFAULT_MAX_BODY_BYTES: Long = 2L * 1024 * 1024
    }
}

sealed interface ExtractorHttpResult {
    data class Success(
        val statusCode: Int,
        val body: String,
        val finalUrl: String,
        val contentType: String? = null,
        /**
         * Cookies this lookup's responses set, redirects included, in memory only. They are
         * never sent back automatically; an adapter decides which ones a media request needs.
         */
        val cookies: List<ResponseCookie> = emptyList(),
    ) : ExtractorHttpResult {
        init {
            require(statusCode in 200..299)
            require(finalUrl.isNotBlank())
        }
    }

    data class Failure(
        val reason: SiteExtractionFailure,
        val statusCode: Int? = null,
    ) : ExtractorHttpResult {
        init {
            require(statusCode == null || statusCode in 100..599)
        }
    }
}
