package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.BrowserRequestContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectRangeProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var redirectTarget: MockWebServer
    private lateinit var probe: DirectRangeProbe

    @Before
    fun setUp() {
        server = MockWebServer()
        redirectTarget = MockWebServer()
        server.start()
        redirectTarget.start()
        probe = DirectRangeProbe(
            client = OkHttpClient(),
            clock = { NOW },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        redirectTarget.shutdown()
    }

    @Test
    fun `range probe confirms total and validators`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "4096")
                .setHeader("Content-Type", "video/mp4"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/4096")
                .setHeader("ETag", "\"fixture\"")
                .setHeader("Last-Modified", "Wed, 01 Oct 2026 00:00:00 GMT")
                .setBody("x"),
        )

        val result = probe.probe(plan(server.url("/movie.mp4").toString()))
            as DirectProbeResult.Success

        assertEquals(4_096L, result.metadata.totalBytes)
        assertTrue(result.metadata.supportsByteRanges)
        assertEquals("\"fixture\"", result.metadata.entityTag)
        assertEquals("video/mp4", result.metadata.contentType)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `explicit HEAD range support avoids a second request`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Length", "1024")
                .setHeader("Content-Type", "video/mp4"),
        )

        val result = probe.probe(plan(server.url("/movie.mp4").toString()))
            as DirectProbeResult.Success

        assertTrue(result.metadata.supportsByteRanges)
        assertEquals(1_024L, result.metadata.totalBytes)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `HEAD rejection falls back to bounded no-range GET`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x".repeat(2_048)),
        )

        val result = probe.probe(plan(server.url("/single.mp4").toString()))
            as DirectProbeResult.Success

        assertFalse(result.metadata.supportsByteRanges)
        assertEquals(2_048L, result.metadata.totalBytes)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `missing response length remains unknown`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody("fixture", 2),
        )

        val result = probe.probe(plan(server.url("/unknown.mp4").toString()))
            as DirectProbeResult.Success

        assertNull(result.metadata.totalBytes)
        assertFalse(result.metadata.supportsByteRanges)
    }

    @Test
    fun `cross-origin redirect strips cookie and keeps only the page origin as referrer`() =
        runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", redirectTarget.url("/final.mp4")),
        )
        redirectTarget.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Length", "512"),
        )

        val result = probe.probe(
            plan(
                url = server.url("/redirect.mp4").toString(),
                cookie = "session=fixture",
            ),
        )

        assertTrue(result is DirectProbeResult.Success)
        assertEquals("session=fixture", server.takeRequest().getHeader("Cookie"))
        val redirectedRequest = redirectTarget.takeRequest()
        assertNull(redirectedRequest.getHeader("Cookie"))
        assertEquals("https://page.example.test/", redirectedRequest.getHeader("Referer"))
        assertNull(redirectedRequest.getHeader("Origin"))
        assertEquals("fixture-agent", redirectedRequest.getHeader("User-Agent"))
    }

    @Test
    fun `another host gets the observed page origin and an origin-only referrer`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", redirectTarget.url("/cdn/piece.mp4")),
        )
        redirectTarget.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Length", "512"),
        )
        val base = plan(server.url("/piece.mp4").toString(), cookie = "session=fixture")

        probe.probe(
            base.copy(
                requestContext = base.requestContext.copy(
                    pageUrl = "https://page.example.test:8443/watch/42?t=1",
                    observedHeaders = mapOf(
                        "Origin" to "https://page.example.test:8443",
                        "Authorization" to "Bearer fixture",
                    ),
                ),
            ),
        )

        val first = server.takeRequest()
        assertEquals("https://page.example.test:8443", first.getHeader("Origin"))
        assertEquals("https://page.example.test:8443/watch/42?t=1", first.getHeader("Referer"))
        val other = redirectTarget.takeRequest()
        assertEquals("https://page.example.test:8443", other.getHeader("Origin"))
        assertEquals("https://page.example.test:8443/", other.getHeader("Referer"))
        assertNull(other.getHeader("Cookie"))
        assertNull(other.getHeader("Authorization"))
    }

    @Test
    fun `a HEAD sent to a web page asks the file itself again with the range GET`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Length", "5120")
                .setHeader("Content-Type", "text/html; charset=utf-8"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/13631866")
                .setHeader("Content-Type", "video/mp4")
                .setBody("x"),
        )

        val result = probe.probe(plan(server.url("/videos/clip.mp4").toString()))
            as DirectProbeResult.Success

        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("/", server.takeRequest().path)
        val rangeRequest = server.takeRequest()
        assertEquals("GET", rangeRequest.method)
        assertEquals("/videos/clip.mp4", rangeRequest.path)
        assertEquals("bytes=0-0", rangeRequest.getHeader("Range"))
        assertEquals(server.url("/videos/clip.mp4").toString(), result.metadata.finalUrl)
        assertEquals(13_631_866L, result.metadata.totalBytes)
        assertTrue(result.metadata.supportsByteRanges)
        assertEquals("video/mp4", result.metadata.contentType)
        assertEquals("clip.mp4", result.metadata.suggestedFileName)
    }

    @Test
    fun `expired plan fails before network`() = runTest {
        val result = probe.probe(
            plan(
                url = server.url("/expired.mp4").toString(),
                expiresAtEpochMs = NOW - 1,
            ),
        ) as DirectProbeResult.Failure

        assertEquals(DownloadFailureReason.EXPIRED_URL, result.failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `malformed partial response fails instead of enabling ranges`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes nonsense")
                .setBody("x"),
        )

        val result = probe.probe(plan(server.url("/broken.mp4").toString()))
            as DirectProbeResult.Failure

        assertEquals(DownloadFailureReason.MALFORMED_RESPONSE, result.failure.reason)
    }

    @Test
    fun `empty remote file accepts unsatisfied zero range`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */0"),
        )

        val result = probe.probe(plan(server.url("/empty.bin").toString()))
            as DirectProbeResult.Success

        assertEquals(0L, result.metadata.totalBytes)
        assertTrue(result.metadata.supportsByteRanges)
    }

    @Test
    fun `content disposition filename is decoded and sanitized`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Length", "12")
                .setHeader(
                    "Content-Disposition",
                    "attachment; filename*=UTF-8''my%20movie%2Ffinal.mp4",
                ),
        )

        val result = probe.probe(plan(server.url("/opaque").toString()))
            as DirectProbeResult.Success

        assertEquals("my movie_final.mp4", result.metadata.suggestedFileName)
    }

    @Test
    fun `HTTP errors map to structured reasons`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = probe.probe(plan(server.url("/forbidden.mp4").toString()))
            as DirectProbeResult.Failure

        assertEquals(DownloadFailureReason.ACCESS_DENIED, result.failure.reason)
        assertEquals(403, result.failure.httpStatusCode)
    }

    @Test
    fun `insecure production URL is rejected`() = runTest {
        val result = probe.probe(plan("http://example.test/movie.mp4"))
            as DirectProbeResult.Failure

        assertEquals(DownloadFailureReason.INVALID_URL, result.failure.reason)
    }

    @Test
    fun `coroutine cancellation cancels an in-flight call`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            probe.probe(plan(server.url("/slow.mp4").toString()))
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        deferred.cancelAndJoin()

        assertTrue(deferred.isCancelled)
    }

    private fun plan(
        url: String,
        cookie: String? = null,
        expiresAtEpochMs: Long? = null,
    ): DirectDownloadPlan = DirectDownloadPlan(
        taskId = "fixture-task",
        sourceUrl = url,
        suggestedFileName = "fallback.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = cookie,
        ),
        expiresAtEpochMs = expiresAtEpochMs,
    )

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}