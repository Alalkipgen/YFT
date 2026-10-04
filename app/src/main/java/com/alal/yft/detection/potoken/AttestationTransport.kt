package com.alal.yft.detection.potoken

import com.alal.yft.extractor.api.SiteExtractionFailure
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The two calls YouTube's web player makes to YouTube's attestation service. */
internal fun interface AttestationTransport {
    suspend fun post(call: AttestationCall): AttestationReply
}

internal class AttestationCall(
    val url: String,
    val attestationKey: String,
    val body: String,
    val userAgent: String?,
) {
    init {
        require(url == BotGuardProtocol.CREATE_URL || url == BotGuardProtocol.GENERATE_URL) {
            "Only YouTube's attestation service is called"
        }
    }

    override fun toString(): String = "AttestationCall(url=$url)"
}

internal sealed interface AttestationReply {
    class Success(val body: String) : AttestationReply {
        override fun toString(): String = "AttestationReply.Success(<${body.length} chars>)"
    }

    data class Failure(val reason: SiteExtractionFailure) : AttestationReply
}

/**
 * Calls the attestation service with no cookies, as YouTube's own player script does.
 *
 * The service needs no session: the player sends only its attestation key and BotGuard's
 * answer, so no cookie jar is attached and redirects are refused.
 */
internal class OkHttpAttestationTransport(
    client: OkHttpClient,
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) : AttestationTransport {
    private val http = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun post(call: AttestationCall): AttestationReply = try {
        withContext(Dispatchers.IO) { execute(call) }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: IOException) {
        AttestationReply.Failure(SiteExtractionFailure.NETWORK)
    }

    private fun execute(call: AttestationCall): AttestationReply {
        val request = Request.Builder()
            .url(call.url)
            .post(call.body.toRequestBody(MEDIA_TYPE))
            .header("X-Goog-Api-Key", call.attestationKey)
            .header("X-User-Agent", BotGuardProtocol.CLIENT_HEADER)
            .apply { call.userAgent?.takeIf(String::isNotBlank)?.let { header("User-Agent", it) } }
            .build()
        return http.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_TOO_MANY_REQUESTS ->
                    AttestationReply.Failure(SiteExtractionFailure.RATE_LIMITED)

                !response.isSuccessful ->
                    AttestationReply.Failure(SiteExtractionFailure.HTTP_STATUS)

                else -> {
                    val source = response.body?.source()
                        ?: return@use AttestationReply.Failure(
                            SiteExtractionFailure.MALFORMED_RESPONSE,
                        )
                    if (source.request(maxBodyBytes + 1)) {
                        AttestationReply.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE)
                    } else {
                        AttestationReply.Success(source.readUtf8())
                    }
                }
            }
        }
    }

    private companion object {
        val MEDIA_TYPE = BotGuardProtocol.CONTENT_TYPE.toMediaType()
        const val CALL_TIMEOUT_SECONDS = 20L
        const val HTTP_TOO_MANY_REQUESTS = 429

        /** A challenge is about 100 KB; the cap leaves room for growth. */
        const val DEFAULT_MAX_BODY_BYTES = 2L * 1024 * 1024
    }
}
