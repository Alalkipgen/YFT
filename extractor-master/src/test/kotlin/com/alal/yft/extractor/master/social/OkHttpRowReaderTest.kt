package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import java.net.InetAddress
import java.net.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Social plan S1a (6), S6: V's read of a row's file. One GET with a `Range` of the asked bytes and
 * the headers given (never a cookie or a credential), HTTPS on one of Facebook's media hosts only,
 * no redirect followed, at most the asked bytes read; a non-2xx answer is a failure with its
 * status, no answer one without. Task 9: one reader for every site, each read on its own site's
 * hosts ([RowReadSite], as S6 gives it; a read without one sends nothing). A local HTTPS server
 * under Facebook's (and X's) media host names; no live calls.
 */
class OkHttpRowReaderTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    /** Task 9: the reader as S6 asks it in a Facebook lookup (each read carries Facebook as its site). */
    private lateinit var reader: MediaStartReader

    @Before
    fun setup() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName(HOST)
            .addSubjectAlternativeName(OTHER_HOST)
            .addSubjectAlternativeName(X_HOST)
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .proxy(Proxy.NO_PROXY)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
            })
            .build()
        reader = OkHttpRowReader(client).readingFor(SiteWays.FACEBOOK)
    }

    @After
    fun close() {
        server.shutdown()
    }

    @Test
    fun `one ranged read with the headers given, never the session, at most the bytes asked`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-4095/32768")
                .setBody(Buffer().write(ByteArray(10_000) { it.toByte() })),
        )
        val headers = mapOf(
            "User-Agent" to "fixture-agent",
            "Referer" to "https://www.facebook.com/",
            "Cookie" to "c_user=0; xs=fixture-cookie",
            "Authorization" to "Bearer fixture",
            "Range" to "bytes=100-",
        )
        val read = reader.read(url(HOST), headers, 4096) as MediaStart.Read
        assertEquals(206, read.statusCode)
        assertEquals("video/mp4", read.contentType)
        assertEquals("bytes 0-4095/32768", read.contentRange)
        assertEquals(4096, read.bytes.size)
        assertEquals(255.toByte(), read.bytes[255])

        val asked = server.takeRequest()
        assertEquals("GET", asked.method)
        assertEquals("bytes=0-4095", asked.getHeader("Range"))
        assertEquals("identity", asked.getHeader("Accept-Encoding"))
        assertEquals("fixture-agent", asked.getHeader("User-Agent"))
        assertEquals("https://www.facebook.com/", asked.getHeader("Referer"))
        assertNull(asked.getHeader("Cookie"))
        assertNull(asked.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `only HTTPS on a media host of the site is asked`() = runBlocking {
        val elsewhere = listOf(
            "https://video.example-cdn.test:${server.port}/v/file.mp4",
            "http://$HOST:${server.port}/v/file.mp4",
            "https://fbcdn.net.example-cdn.test:${server.port}/v/file.mp4",
            "not a link",
        )
        for (url in elsewhere) {
            val read = reader.read(url, emptyMap(), 4096)
            assertTrue(url, read is MediaStart.Failed && read.statusCode == null)
        }
        assertEquals(0, server.requestCount)
        val tooMany = runCatching { reader.read(url(HOST), emptyMap(), OkHttpRowReader.MAX_BYTES + 1) }
        assertTrue(tooMany.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no redirect is followed, not even to the same host`() = runBlocking {
        for (target in listOf(url(OTHER_HOST), url(HOST, "/v/moved.mp4"))) {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target))
            val read = reader.read(url(HOST), emptyMap(), 4096)
            assertTrue(read is MediaStart.Failed && read.statusCode == 302)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a non-2xx answer fails with its status, no answer without one`() = runBlocking {
        for (status in listOf(403, 410, 429)) {
            server.enqueue(MockResponse().setResponseCode(status))
            val read = reader.read(url(HOST), emptyMap(), 4096)
            assertTrue("$status", read is MediaStart.Failed && read.statusCode == status)
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val silent = reader.read(url(HOST), emptyMap(), 4096)
        assertTrue(silent is MediaStart.Failed && silent.statusCode == null)
    }

    @Test
    fun `task 9 - one reader reads each read's own site's hosts, and a read without a site sends nothing`() = runBlocking {
        // HTTP/1.1, so the server records the host each read asked.
        val shared = OkHttpRowReader(client.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).build())
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-4095/32768")
                .setBody(Buffer().write(ByteArray(4096))),
        )

        val x = withContext(RowReadSite(SiteWays.X)) { shared.read(url(X_HOST, X_PATH), emptyMap(), 4096) }

        assertTrue("$x", x is MediaStart.Read && x.statusCode == 206)
        assertEquals("$X_HOST:${server.port}", server.takeRequest().getHeader("Host"))
        // The same reader: X's file under Facebook's site, Facebook's under X's, either without a
        // site: nothing sent, no status.
        val refused = listOf(
            withContext(RowReadSite(SiteWays.FACEBOOK)) { shared.read(url(X_HOST, X_PATH), emptyMap(), 4096) },
            withContext(RowReadSite(SiteWays.X)) { shared.read(url(HOST), emptyMap(), 4096) },
            shared.read(url(HOST), emptyMap(), 4096),
            shared.read(url(X_HOST, X_PATH), emptyMap(), 4096),
        )
        refused.forEach { assertTrue("$it", it is MediaStart.Failed && it.statusCode == null) }
        assertEquals(1, server.requestCount)
        assertEquals("OkHttpRowReader", shared.toString())
    }

    private fun url(host: String, path: String = "/v/file.mp4"): String =
        "https://$host:${server.port}$path?oe=F2A52380&oh=fixture"

    /** [this] reader with each read in a lookup of [site]'s (its [RowReadSite], as S6 reads). */
    private fun MediaStartReader.readingFor(site: Site) = MediaStartReader { url, headers, maxBytes ->
        withContext(RowReadSite(site)) { read(url, headers, maxBytes) }
    }

    private companion object {
        const val HOST = "video.fixture.fbcdn.net"
        const val OTHER_HOST = "other.fixture.fbcdn.net"
        const val X_HOST = "video.twimg.com"
        const val X_PATH = "/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4"
    }
}
