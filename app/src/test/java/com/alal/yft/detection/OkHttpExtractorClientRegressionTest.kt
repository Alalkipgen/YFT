package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P39 regression proof. Only the client API P38 already had is used (one constructor argument,
 * `get`, and the interface's `probe`), so these tests also run against the P38 client, where
 * each one fails: a thrown error escaped, a loop read as a changed page, no file checks.
 */
class OkHttpExtractorClientRegressionTest {
    private val seen = mutableListOf<Request>()

    @Test
    fun `a cookie pair with a character OkHttp refuses does not stop the request`() = runTest {
        val result = runCatching {
            client { it.code(200) }.get(
                "https://www.tiktok.com/@a/video/1",
                mapOf("User-Agent" to "FixtureAgent/1.0", "Cookie" to "SID=fixture; n=caf\u00e9"),
            )
        }

        assertTrue(result.toString(), result.getOrNull() is ExtractorHttpResult.Success)
        assertEquals("SID=fixture", seen.single().header("Cookie"))
    }

    @Test
    fun `an unexpected error becomes a failure instead of escaping`() = runTest {
        val result = runCatching {
            client { error("fixture") }.get("https://www.tiktok.com/@a/video/1", emptyMap())
        }

        val failure = result.getOrNull() as? ExtractorHttpResult.Failure
        assertTrue(result.toString(), failure != null)
        assertEquals(SiteExtractionFailure.MALFORMED_RESPONSE, failure?.reason)
    }

    @Test
    fun `too many redirects is an HTTP problem, not a changed page`() = runTest {
        val loop = client { it.code(302).header("Location", "https://www.tiktok.com/loop") }

        val result = loop.get("https://www.tiktok.com/loop", emptyMap())

        assertEquals(
            SiteExtractionFailure.HTTP_STATUS,
            (result as ExtractorHttpResult.Failure).reason,
        )
    }

    @Test
    fun `a file check reads the file's exact size from one byte`() = runTest {
        val client = client { it.code(206).header("Content-Range", "bytes 0-0/2004627") }

        val result = client.probe("https://v16-webapp-prime.us.tiktok.com/video/tos/a/", emptyMap())

        assertEquals(ExtractorProbeResult.Answered(206, 2_004_627), result)
        assertEquals("bytes=0-0", seen.single().header("Range"))
    }

    private fun client(answer: (Response.Builder) -> Response.Builder): OkHttpExtractorClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            seen += request
            answer(
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture")
                    .body("x".toResponseBody()),
            ).build()
        }
        return OkHttpExtractorClient(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }
}
