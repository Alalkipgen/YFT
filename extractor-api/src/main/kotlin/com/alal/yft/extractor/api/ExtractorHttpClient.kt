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

    /**
     * Asks whether the media file at [url] answers, reading at most its first byte.
     *
     * Adapters use this to list only qualities whose file really opens and to read their exact
     * size. Implementations apply the same HTTPS and credential rules as [get], never retry, and
     * stop after [timeoutMillis]. A client that cannot check files answers
     * [ExtractorProbeResult.Unsupported], so adapters keep their rows unchecked.
     */
    suspend fun probe(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): ExtractorProbeResult = ExtractorProbeResult.Unsupported

    companion object {
        const val DEFAULT_MAX_BODY_BYTES: Long = 2L * 1024 * 1024
        const val DEFAULT_PROBE_TIMEOUT_MILLIS: Long = 5_000L
    }
}

/** What a one-byte file check found. Never carries the address or any request header. */
sealed interface ExtractorProbeResult {
    /** The file answered; [totalBytes] is its exact size when the server stated it. */
    data class Answered(
        val statusCode: Int,
        val totalBytes: Long? = null,
    ) : ExtractorProbeResult {
        init {
            require(statusCode in 200..299)
            require(totalBytes == null || totalBytes >= 0)
        }
    }

    /** The file did not answer; [error] is a short exception class name, never a message. */
    data class Refused(
        val reason: SiteExtractionFailure,
        val statusCode: Int? = null,
        val error: String? = null,
    ) : ExtractorProbeResult {
        init {
            require(statusCode == null || statusCode in 100..599)
        }
    }

    /** This client cannot check files. */
    data object Unsupported : ExtractorProbeResult
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
        /** Short non-sensitive notes about the request, such as how many headers were left out. */
        val details: List<String> = emptyList(),
    ) : ExtractorHttpResult {
        init {
            require(statusCode in 200..299)
            require(finalUrl.isNotBlank())
        }
    }

    data class Failure(
        val reason: SiteExtractionFailure,
        val statusCode: Int? = null,
        /** Short non-sensitive notes, such as the exception class that stopped the request. */
        val details: List<String> = emptyList(),
    ) : ExtractorHttpResult {
        init {
            require(statusCode == null || statusCode in 100..599)
        }
    }
}
