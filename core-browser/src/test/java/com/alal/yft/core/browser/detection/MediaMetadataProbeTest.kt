package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaMetadataProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var probe: MediaMetadataProbe

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        probe = MediaMetadataProbe(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mimeOnlyVideoIsDetectedFromHeadWithoutReadingBody() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4; charset=binary")
                .setHeader("Content-Length", "4096"),
        )

        val result = probe.probe(candidate(server.url("/opaque").toString()))

        val detected = result as MediaMetadataProbe.Result.Detected
        assertEquals(MediaKind.DIRECT, detected.candidate.kind)
        assertEquals("video/mp4", detected.candidate.mimeType)
        assertEquals(4096L, detected.candidate.contentLengthBytes)
        assertEquals(CandidateConfidence.HIGH, detected.candidate.confidence)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun headRejectionFallsBackToOneByteGetAndReadsManifestMetadata() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                .setHeader("Content-Range", "bytes 0-0/987")
                .setBody("#"),
        )

        val result = probe.probe(candidate(server.url("/stream").toString()))

        val detected = result as MediaMetadataProbe.Result.Detected
        assertEquals(MediaKind.HLS, detected.candidate.kind)
        assertEquals(987L, detected.candidate.contentLengthBytes)
        assertTrue(CandidateSource.MANIFEST in detected.candidate.sources)
        assertEquals("HEAD", server.takeRequest().method)
        val rangeRequest = server.takeRequest()
        assertEquals("GET", rangeRequest.method)
        assertEquals("bytes=0-0", rangeRequest.getHeader("Range"))
    }

    @Test
    fun sameOriginRedirectKeepsContextAndMarksCandidate() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/movie.mp4"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4"),
        )

        val result = probe.probe(
            candidate(
                url = server.url("/signed").toString(),
                context = requestContext(),
            ),
        )

        val redirected = result as MediaMetadataProbe.Result.Detected
        assertEquals(server.url("/movie.mp4").toString(), redirected.candidate.mediaUrl)
        assertTrue(CandidateSource.REDIRECT in redirected.candidate.sources)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("session=test-only", first.getHeader("Cookie"))
        assertEquals("session=test-only", second.getHeader("Cookie"))
        assertEquals("Bearer test-only-credential", second.getHeader("Authorization"))
    }

    @Test
    fun crossOriginRedirectStripsBrowserCredentialsAndCustomHeaders() = runTest {
        val target = MockWebServer()
        target.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", target.url("/video.mp4")),
            )
            target.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4"),
            )

            val result = probe.probe(
                candidate(
                    url = server.url("/redirect").toString(),
                    context = requestContext(),
                ),
            )

            assertTrue(result is MediaMetadataProbe.Result.Detected)
            val originalRequest = server.takeRequest()
            val redirectedRequest = target.takeRequest()
            assertEquals("session=test-only", originalRequest.getHeader("Cookie"))
            assertNull(redirectedRequest.getHeader("Cookie"))
            assertNull(redirectedRequest.getHeader("Authorization"))
            assertNull(redirectedRequest.getHeader("Referer"))
            assertNull(redirectedRequest.getHeader("X-Test-Context"))
            assertEquals("YFT-Test-Agent", redirectedRequest.getHeader("User-Agent"))
            assertEquals("video/*", redirectedRequest.getHeader("Accept"))
        } finally {
            target.shutdown()
        }
    }

    @Test
    fun clearlyNonMediaResponseIsRejected() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setHeader("Content-Length", "128"),
        )

        val result = probe.probe(candidate(server.url("/looks-like.mp4").toString()))

        assertEquals(
            MediaMetadataProbe.NotMediaReason.CLEARLY_NON_MEDIA,
            (result as MediaMetadataProbe.Result.NotMedia).reason,
        )
    }

    @Test
    fun literalBlobIsRejectedWithoutNetworkRequest() = runTest {
        val result = probe.probe(candidate("blob:https://example.test/id"))

        assertEquals(
            MediaMetadataProbe.NotMediaReason.INVALID_URL,
            (result as MediaMetadataProbe.Result.NotMedia).reason,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun redirectLimitIsBounded() = runTest {
        repeat(6) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "/again-$it"),
            )
        }

        val result = probe.probe(candidate(server.url("/start").toString()))

        assertEquals(
            MediaMetadataProbe.FailureReason.TOO_MANY_REDIRECTS,
            (result as MediaMetadataProbe.Result.Failed).reason,
        )
        assertEquals(6, server.requestCount)
    }

    private fun candidate(
        url: String,
        context: BrowserRequestContext = BrowserRequestContext(
            pageUrl = "https://page.test/watch",
            userAgent = null,
            cookie = null,
        ),
    ) = MediaCandidate(
        pageUrl = "https://page.test/watch",
        mediaUrl = url,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.UNKNOWN,
        requestContext = context,
        confidence = CandidateConfidence.LOW,
        observedAtEpochMs = 1,
    )

    private fun requestContext() = BrowserRequestContext(
        pageUrl = "https://page.test/watch",
        userAgent = "YFT-Test-Agent",
        cookie = "session=test-only",
        observedHeaders = mapOf(
            "Authorization" to "Bearer test-only-credential",
            "Accept" to "video/*",
            "X-Test-Context" to "test-only",
            "Range" to "bytes=50-100",
        ),
    )
}