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
