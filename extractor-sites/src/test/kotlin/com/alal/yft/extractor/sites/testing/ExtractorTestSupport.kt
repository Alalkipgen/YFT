package com.alal.yft.extractor.sites.testing

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.PoTokenProvider
import com.alal.yft.extractor.api.PoTokenRequest
import com.alal.yft.extractor.api.PoTokenResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure

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
 * requested, which is how short-link resolution is exercised offline. Posted documents are
 * answered by [postResponder], which sees the body, so two clients asking the same endpoint can
 * receive different answers. [getResponder] sees the request headers, so one page address can
 * answer two identities differently.
 */
internal class FakeExtractorHttpClient(
    private val responses: Map<String, ExtractorHttpResult> = emptyMap(),
    private val fallback: ExtractorHttpResult = ExtractorHttpResult.Failure(
        SiteExtractionFailure.HTTP_STATUS,
        404,
    ),
    private val postResponder: (url: String, body: String) -> ExtractorHttpResult? =
        { _, _ -> null },
    private val getResponder: (url: String, headers: Map<String, String>) -> ExtractorHttpResult? =
        { _, _ -> null },
) : ExtractorHttpClient {
    val requestedUrls = mutableListOf<String>()
    val requestedHeaders = mutableListOf<Map<String, String>>()
    val requestedBodyLimits = mutableListOf<Long>()

    val postedUrls = mutableListOf<String>()
    val postedBodies = mutableListOf<String>()
    val postedHeaders = mutableListOf<Map<String, String>>()
    val postedBodyLimits = mutableListOf<Long>()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        requestedUrls += url
        requestedHeaders += headers
        requestedBodyLimits += maxBodyBytes
        return getResponder(url, headers) ?: responses[url] ?: fallback
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        postedUrls += url
        postedBodies += body
        postedHeaders += headers
        postedBodyLimits += maxBodyBytes
        return postResponder(url, body) ?: fallback
    }

    companion object {
        fun serving(
            url: String,
            body: String,
            finalUrl: String = url,
            statusCode: Int = 200,
            cookies: List<ResponseCookie> = emptyList(),
        ): FakeExtractorHttpClient = FakeExtractorHttpClient(
            mapOf(url to html(body, finalUrl, statusCode, cookies)),
        )

        fun html(
            body: String,
            finalUrl: String,
            statusCode: Int = 200,
            cookies: List<ResponseCookie> = emptyList(),
        ): ExtractorHttpResult.Success = ExtractorHttpResult.Success(
            statusCode = statusCode,
            body = body,
            finalUrl = finalUrl,
            contentType = "text/html; charset=utf-8",
            cookies = cookies,
        )

        fun json(body: String, finalUrl: String): ExtractorHttpResult.Success =
            ExtractorHttpResult.Success(
                statusCode = 200,
                body = body,
                finalUrl = finalUrl,
                contentType = "application/json; charset=utf-8",
            )
    }
}

/**
 * Stands in for the app's player-script host.
 *
 * It records every request and answers through [answer], so tests can script success, refusal
 * or implausible values without any script engine.
 */
internal class FakePlayerScriptRunner(
    override val isAvailable: Boolean = true,
    private val answer: (PlayerScriptRequest) -> PlayerScriptResult = ::transformAll,
) : PlayerScriptRunner {
    val requests = mutableListOf<PlayerScriptRequest>()

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
        requests += request
        return answer(request)
    }

    companion object {
        /** A deterministic stand-in for the site's transforms, so tests can predict the URLs. */
        fun transform(kind: PlayerScriptChallengeKind, input: String): String = when (kind) {
            PlayerScriptChallengeKind.SIGNATURE -> "S" + input.reversed()
            PlayerScriptChallengeKind.RATE_PARAM -> "N" + input.reversed()
        }

        fun transformAll(request: PlayerScriptRequest): PlayerScriptResult =
            PlayerScriptResult.Success(
                request.challenges.associate { it.key to transform(it.kind, it.input) },
            )
    }
}

/** Records token requests and answers them with a fixed result. */
internal class FakePoTokenProvider(
    override val isAvailable: Boolean = true,
    private val answer: (PoTokenRequest) -> PoTokenResult = { PoTokenResult.Minted(TOKEN) },
) : PoTokenProvider {
    val requests = mutableListOf<PoTokenRequest>()

    override suspend fun mint(request: PoTokenRequest): PoTokenResult {
        requests += request
        return answer(request)
    }

    companion object {
        /** A fixture token in the URL-safe alphabet a real one uses. */
        const val TOKEN: String = "MnFixturePoToken-0123456789_abcdefghijklmnop"
    }
}
