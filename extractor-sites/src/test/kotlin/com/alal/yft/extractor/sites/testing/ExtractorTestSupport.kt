package com.alal.yft.extractor.sites.testing

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult

/** Loads a committed fixture so adapter tests never touch the network. */
internal object Fixtures {
    fun read(path: String): String =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$path")) {
            "Missing fixture: $path"
        }.use { stream -> stream.readBytes().decodeToString() }
}

/**
 * Records adapter requests and replays scripted responses.
 *
 * Responses are keyed by URL so a redirect fixture can return a different final URL than the one
 * requested, which is how short-link resolution is exercised offline.
 */
internal class FakeExtractorHttpClient(
    private val responses: Map<String, ExtractorHttpResult> = emptyMap(),
    private val fallback: ExtractorHttpResult = ExtractorHttpResult.Failure(
        com.alal.yft.extractor.api.SiteExtractionFailure.HTTP_STATUS,
        404,
    ),
) : ExtractorHttpClient {
    val requestedUrls = mutableListOf<String>()
    val requestedHeaders = mutableListOf<Map<String, String>>()
    val requestedBodyLimits = mutableListOf<Long>()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        requestedUrls += url
        requestedHeaders += headers
        requestedBodyLimits += maxBodyBytes
        return responses[url] ?: fallback
    }

    companion object {
        fun serving(
            url: String,
            body: String,
            finalUrl: String = url,
            statusCode: Int = 200,
        ): FakeExtractorHttpClient = FakeExtractorHttpClient(
            mapOf(
                url to ExtractorHttpResult.Success(
                    statusCode = statusCode,
                    body = body,
                    finalUrl = finalUrl,
                    contentType = "text/html; charset=utf-8",
                ),
            ),
        )
    }
}
