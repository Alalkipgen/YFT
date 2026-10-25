package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * YT-2 canary, owner-run only (`scripts/master-youtube-canary.sh <video id>`); skipped in CI.
 * It passes while visionOS alone still answers a public video with direct addresses. A table
 * value is changed only after this fails.
 */
class MasterYouTubeCanaryTest {
    @Test
    fun `visionOS still answers with direct addresses`() = runBlocking {
        val id = System.getProperty("yft.youtubeCanary")?.trim().orEmpty()
        assumeTrue("set -Pyft.youtubeCanary=<video id> to ask YouTube live", id.isNotEmpty())
        val module = MasterYouTubeModule(LiveHttp())
        val identity = requireNotNull(module.identify("https://www.youtube.com/watch?v=$id"))

        val result = module.extract(
            SiteExtractionRequest(
                identity,
                BrowserRequestContext(identity.canonicalPageUrl, USER_AGENT, null),
                System.currentTimeMillis(),
            ),
        )

        MasterYouTubeClients.describe().forEach { println("canary: $it") }
        val details = when (result) {
            is SiteExtractionResult.Success -> result.details +
                "rows ${result.candidates.size}"
            is SiteExtractionResult.Failure -> result.details + "failure ${result.reason}"
        }
        details.forEach { println("canary: $it") }
        assertTrue("canary failed: $details", result is SiteExtractionResult.Success)
        assertTrue(
            "canary failed: visionOS was not a complete answer",
            MasterYouTubeClients.CANARY_DETAIL in details,
        )
    }

    /** A bounded HTTPS-only client for the canary; the app uses its own OkHttp client. */
    private class LiveHttp : ExtractorHttpClient {
        private val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = call(Request.Builder().url(url).get(), headers, maxBodyBytes)

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = call(
            Request.Builder().url(url).post(body.toRequestBody(JSON)),
            headers,
            maxBodyBytes,
        )

        private suspend fun call(
            builder: Request.Builder,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = withContext(Dispatchers.IO) {
            headers.forEach { (name, value) -> builder.header(name, value) }
            val request = builder.build()
            if (!request.url.isHttps) {
                return@withContext ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
            }
            runCatching {
                client.newCall(request).execute().use { response ->
                    val bytes = response.body?.source()?.let { source ->
                        source.request(maxBodyBytes + 1)
                        source.buffer.size
                    } ?: 0
                    if (bytes > maxBodyBytes) {
                        ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE)
                    } else {
                        ExtractorHttpResult.Success(
                            response.code,
                            response.body?.string().orEmpty(),
                            response.request.url.toString(),
                            response.header("Content-Type"),
                        )
                    }
                }
            }.getOrElse { ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK) }
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0 Mobile Safari/537.36"
        val JSON = "application/json".toMediaType()
    }
}
