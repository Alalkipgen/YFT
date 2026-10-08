package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpMediaValidatorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var serverTls: HandshakeCertificates
    private val extraServers = mutableListOf<MockWebServer>()

    @Before
    fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate).build()
        client = OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        server = newServer()
    }

    @After
    fun close() {
        server.shutdown()
        extraServers.forEach(MockWebServer::shutdown)
    }

    @Test
    fun `direct check reads a prefix and reports the whole file size`() = runBlocking {
        server.enqueue(mp4())
        val result = validate() as ValidationResult.Valid
        assertEquals(32_768L, result.candidate.contentLengthBytes)
        assertEquals("bytes=0-511", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `an opaque URL can be classified from the response MIME`() = runBlocking {
        server.enqueue(mp4())
        val result = validate(candidate(server.url("/opaque").toString()))
            as ValidationResult.Valid
        assertEquals(MediaKind.DIRECT, result.candidate.kind)
    }

    @Test
    fun `HTML behind an MP4 address is not downloadable media`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html")
            .setBody("<html>Sign in</html>"))
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, rejected(validate()))
    }

    @Test
    fun `false video MIME does not pass the file signature check`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4")
            .setBody("<html>Not an MP4</html>"))
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, rejected(validate()))
    }

    @Test
    fun `missing range total is not invented`() = runBlocking {
        server.enqueue(mp4().removeHeader("Content-Range"))
        assertEquals(SiteExtractionFailure.MALFORMED_RESPONSE, rejected(validate()))
    }

    @Test
    fun `expired address is refused without making a request`() = runBlocking {
        val result = validate(candidate(server.url("/video.mp4?expire=1").toString()))
        assertEquals(SiteExtractionFailure.EXPIRED_LINK, rejected(result))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `HTTP 410 is a structured expired-link failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(410))
        assertEquals(SiteExtractionFailure.EXPIRED_LINK, rejected(validate()))
    }

    @Test
    fun `rate limit makes one call without automatic retries`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(SiteExtractionFailure.RATE_LIMITED, rejected(validate()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `non DRM finite HLS is read within a manifest budget`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
            .setBody("#EXTM3U\n#EXTINF:5,\npiece.ts\n#EXT-X-ENDLIST\n"))
        val input = candidate(server.url("/video.m3u8").toString()).copy(kind = MediaKind.HLS)
        val result = validate(input) as ValidationResult.Valid
        assertEquals(5_000L, result.candidate.durationMillis)
        assertNull(result.candidate.contentLengthBytes)
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `DRM HLS and DASH are refused`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
            .setBody("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"REDACTED\"\n"))
        val hls = candidate(server.url("/video.m3u8").toString()).copy(kind = MediaKind.HLS)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, rejected(validate(hls)))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/dash+xml")
            .setBody("<MPD><Period><ContentProtection/></Period></MPD>"))
        val dash = candidate(server.url("/video.mpd").toString()).copy(kind = MediaKind.DASH)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, rejected(validate(dash)))
    }

    @Test
    fun `manifest over read budget is refused`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
            .setBody("#EXTM3U\n" + "x".repeat(300)))
        val input = candidate(server.url("/video.m3u8").toString()).copy(kind = MediaKind.HLS)
        val result = OkHttpMediaValidator(client, maxManifestBytes = 100).validate(input, NOW)
        assertEquals(SiteExtractionFailure.RESPONSE_TOO_LARGE, rejected(result))
    }

    @Test
    fun `cross origin redirect strips credentials and custom headers`() = runBlocking {
        val other = newServer().also(extraServers::add)
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", other.url("/video.mp4")))
        other.enqueue(mp4())
        val input = candidate(server.url("/video.mp4").toString()).copy(
            requestContext = BrowserRequestContext(
                PAGE, "fixture-agent", "session=REDACTED",
                mapOf("Authorization" to "Bearer REDACTED", "X-Secret" to "REDACTED"),
            ),
        )
        val result = validate(input) as ValidationResult.Valid
        assertEquals("session=REDACTED", server.takeRequest().getHeader("Cookie"))
        val final = other.takeRequest()
        assertNull(final.getHeader("Cookie"))
        assertNull(final.getHeader("Authorization"))
        assertNull(final.getHeader("X-Secret"))
        assertEquals("fixture-agent", final.getHeader("User-Agent"))
        assertNull(result.candidate.requestContext.cookie)
    }

    @Test
    fun `credentials do not return after a cross origin redirect back`() = runBlocking {
        val other = newServer().also(extraServers::add)
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", other.url("/hop")))
        other.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", server.url("/final.mp4")))
        server.enqueue(mp4())
        val input = candidate(server.url("/video.mp4").toString()).copy(
            requestContext = BrowserRequestContext(PAGE, null, "session=REDACTED"),
        )
        assertTrue(validate(input) is ValidationResult.Valid)
        assertEquals("session=REDACTED", server.takeRequest().getHeader("Cookie"))
        assertNull(server.takeRequest().getHeader("Cookie"))
        assertNull(other.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `HTTPS downgrade and redirect loops are refused`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", "http://localhost/video.mp4"))
        assertEquals(SiteExtractionFailure.UNSUPPORTED_URL, rejected(validate()))
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Location", server.url("/loop.mp4")))
        }
        val result = OkHttpMediaValidator(client, maxRedirects = 1)
            .validate(candidate(server.url("/video.mp4").toString()), NOW)
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, rejected(result))
    }

    @Test
    fun `normal TLS verification is never disabled`() = runBlocking {
        server.enqueue(mp4())
        val result = OkHttpMediaValidator(OkHttpClient())
            .validate(candidate(server.url("/video.mp4").toString()), NOW)
        assertEquals(SiteExtractionFailure.NETWORK, rejected(result))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `response cookies are not persisted or sent by a cookie jar`() = runBlocking {
        server.enqueue(mp4().setHeader("Set-Cookie", "session=REDACTED; Secure"))
        server.enqueue(mp4())
        assertTrue(validate() is ValidationResult.Valid)
        assertTrue(validate() is ValidationResult.Valid)
        assertFalse(server.takeRequest().headers.names().contains("Cookie"))
        assertFalse(server.takeRequest().headers.names().contains("Cookie"))
    }

    private suspend fun validate(
        input: com.alal.yft.core.model.media.MediaCandidate =
            candidate(server.url("/video.mp4").toString()),
    ): ValidationResult = OkHttpMediaValidator(client).validate(input, NOW)

    private fun rejected(result: ValidationResult): SiteExtractionFailure =
        (result as ValidationResult.Rejected).reason

    private fun newServer() = MockWebServer().apply {
        useHttps(serverTls.sslSocketFactory(), false)
        start()
    }

    private fun mp4() = MockResponse().setResponseCode(206)
        .setHeader("Content-Type", "video/mp4")
        .setHeader("Content-Range", "bytes 0-511/32768")
        .setBody(Buffer().write(ByteArray(512).apply {
            this[3] = 24
            "ftypisom".toByteArray().copyInto(this, 4)
        }))
}