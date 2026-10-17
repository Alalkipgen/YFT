package com.alal.yft.detection.master

import com.alal.yft.detection.ExtractorTlsFixture
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5a — A's version source reader (MASTER_KEY_PHASE1_3_PLAN.md §4 D5a "As built (A)"):
 * the user's User-Agent only, no cookie, no redirect followed, at most the limit read, HTTPS on
 * `raw.githubusercontent.com` only by default. The local TLS server stands in for the host.
 */
class OkHttpVersionSourceReaderTest {
    private val limit = 64 * 1024L

    private fun local(fixture: ExtractorTlsFixture) = OkHttpVersionSourceReader(fixture.client) { it.isHttps && it.host == "localhost" }

    @Test
    fun readsTheTextWithTheUserAgentAndNoOtherHeader() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setBody("VERSION = '1.03'").setHeader("Content-Type", "text/plain; charset=utf-8"))
            val url = fixture.url("/source.txt").toString()
            val headers = mapOf("User-Agent" to "Agent/1", "Cookie" to "a=b", "Authorization" to "secret", "X-Other" to "x")
            val read = local(fixture).read(url, headers, limit) as ExtractorHttpResult.Success
            assertEquals("VERSION = '1.03'", read.body)
            assertEquals(url, read.finalUrl)
            assertEquals("text/plain; charset=utf-8", read.contentType)
            val request = requireNotNull(fixture.server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("GET", request.method)
            assertEquals("Agent/1", request.getHeader("User-Agent"))
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("X-Other"))
        }
    }

    @Test
    fun aRedirectIsAFailureAndIsNotFollowed() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", fixture.url("/moved.txt").toString()))
            fixture.server.enqueue(MockResponse().setBody("VERSION = '1.03'"))
            val read = local(fixture).read(fixture.url("/source.txt").toString(), emptyMap(), limit)
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 302), read)
            assertEquals(1, fixture.server.requestCount)
        }
    }

    @Test
    fun aFailedStatusIsAFailure() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setResponseCode(503))
            val read = local(fixture).read(fixture.url("/source.txt").toString(), emptyMap(), limit)
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 503), read)
        }
    }

    @Test
    fun aBrokenConnectionIsANetworkFailure() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val read = local(fixture).read(fixture.url("/source.txt").toString(), emptyMap(), limit)
            assertEquals(SiteExtractionFailure.NETWORK, (read as ExtractorHttpResult.Failure).reason)
        }
    }

    @Test
    fun moreThanTheLimitIsTooLarge() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            val url = fixture.url("/source.txt").toString()
            fixture.server.enqueue(MockResponse().setBody("x".repeat(2_000)))
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, 200), local(fixture).read(url, emptyMap(), 1_000))
            fixture.server.enqueue(MockResponse().setChunkedBody("x".repeat(2_000), 100))
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, 200), local(fixture).read(url, emptyMap(), 1_000))
            fixture.server.enqueue(MockResponse().setChunkedBody("x".repeat(1_000), 100))
            assertEquals(1_000, (local(fixture).read(url, emptyMap(), 1_000) as ExtractorHttpResult.Success).body.length)
            // Never more than 64 KiB, whatever the caller asks.
            fixture.server.enqueue(MockResponse().setBody("x".repeat(65 * 1024)))
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, 200), local(fixture).read(url, emptyMap(), 1L shl 20))
        }
    }

    @Test
    fun byDefaultOnlyHttpsOnRawGithubusercontentComIsRead() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            val strict = OkHttpVersionSourceReader(fixture.client)
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL), strict.read(fixture.url("/source.txt").toString(), emptyMap(), limit))
            assertEquals(ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL), strict.read("not an address", emptyMap(), limit))
            assertEquals(0, fixture.server.requestCount)
        }
        assertTrue(OkHttpVersionSourceReader.isSourceAddress("https://raw.githubusercontent.com/owner/repo/main/file.txt".toHttpUrl()))
        listOf(
            "http://raw.githubusercontent.com/owner/repo/main/file.txt",
            "https://raw.githubusercontent.com:8443/owner/repo/main/file.txt",
            "https://raw.githubusercontent.com.example.org/owner/repo/main/file.txt",
            "https://github.com/owner/repo/raw/main/file.txt",
            "https://gist.githubusercontent.com/owner/file.txt",
        ).forEach { assertFalse(it, OkHttpVersionSourceReader.isSourceAddress(it.toHttpUrl())) }
    }
}
