package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PageNavigationHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeadlessPageFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(LOOPBACK, 0)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun pageIsReadWithTheYftAgentAndWithoutTheClientsCookies() = runTest {
        server.enqueue(html("<title>Clip</title>"))
        val leakyJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

            override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                Cookie.Builder().name("session").value("secret").hostOnlyDomain(url.host).build(),
            )
        }
        val client = client().newBuilder().cookieJar(leakyJar).build()

        val result = HeadlessPageFetcher(client, USER_AGENT).fetch(server.url("/clip").toString())

        val page = result as HeadlessPageFetcher.Result.Page
        assertEquals("<title>Clip</title>", page.html)
        assertEquals(server.url("/clip").toString(), page.url)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(USER_AGENT, request.getHeader("User-Agent"))
        PageNavigationHeaders.DEFAULTS.forEach { (name, value) ->
            assertEquals(value, request.getHeader(name))
        }
        assertNull(request.getHeader("Cookie"))
    }

    @Test
    fun customNavigationValuesCannotInjectCookiesOrAccountHeaders() = runTest {
        server.enqueue(html("fixture"))
        val fetcher = HeadlessPageFetcher(
            client(),
            USER_AGENT,
            navigationHeaders = mapOf(
                "accept" to "text/html",
                "Accept-Language" to "fr-FR",
                "Cookie" to "fixture-only",
                "Authorization" to "fixture-only",
            ),
        )

        fetcher.fetch(server.url("/page").toString())

        val request = server.takeRequest()
        assertEquals("text/html", request.getHeader("Accept"))
        assertEquals("fr-FR", request.getHeader("Accept-Language"))
        assertEquals("navigate", request.getHeader("Sec-Fetch-Mode"))
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun largePagesAreCutAtTheSizeLimit() = runTest {
        server.enqueue(html("a".repeat(5_000)))
        val fetcher = HeadlessPageFetcher(
            client(),
            USER_AGENT,
            HeadlessPageFetcher.Policy(maxBodyBytes = 1_024),
        )

        val page = fetcher.fetch(server.url("/big").toString()) as HeadlessPageFetcher.Result.Page

        assertEquals(1_024, page.html.length)
    }

    @Test
    fun pageCharsetFromTheResponseIsUsed() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=ISO-8859-1")
                .setBody(Buffer().write("caf\u00e9".toByteArray(Charsets.ISO_8859_1))),
        )

        val page = fetcher().fetch(server.url("/").toString()) as HeadlessPageFetcher.Result.Page

        assertEquals("caf\u00e9", page.html)
    }

    @Test
    fun mediaAnswerIsReportedWithoutBeingTreatedAsAPage() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(ByteArray(4_096))),
        )

        val result = fetcher().fetch(server.url("/clip.bin").toString())

        assertEquals(
            HeadlessPageFetcher.Result.Media(
                url = server.url("/clip.bin").toString(),
                mimeType = "video/mp4",
                contentLengthBytes = 4_096,
            ),
            result,
        )
    }

    @Test
    fun manifestAnswerCountsAsMedia() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8")
                .setBody("#EXTM3U"),
        )

        val result = fetcher().fetch(server.url("/live").toString())

        assertEquals(
            "application/vnd.apple.mpegurl",
            (result as HeadlessPageFetcher.Result.Media).mimeType,
        )
    }

    @Test
    fun redirectsAreFollowedToTheFinalPage() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/final/page"))
        server.enqueue(html("done"))

        val result = fetcher().fetch(server.url("/start").toString())

        assertEquals(
            HeadlessPageFetcher.Result.Page(server.url("/final/page").toString(), "done"),
            result,
        )
        assertEquals(3, server.requestCount)
        repeat(3) {
            val request = server.takeRequest()
            assertEquals(USER_AGENT, request.getHeader("User-Agent"))
            PageNavigationHeaders.DEFAULTS.forEach { (name, value) ->
                assertEquals(value, request.getHeader(name))
            }
            assertNull(request.getHeader("Cookie"))
        }
    }

    @Test
    fun theTabsReadAgainSendsItsAgentNoCacheAndItsCookiesOnlyToThePagesSite() = runTest {
        val elsewhere = "http://other.test:${server.port}/elsewhere"
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", elsewhere))
        server.enqueue(html("moved"))
        val asked = mutableListOf<String>()
        val session = HeadlessPageFetcher.TabSession(TAB_AGENT) { url ->
            asked += url
            "tab=fixture"
        }
        val anyHost = client().newBuilder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(LOOPBACK)
            })
            .build()

        val result = HeadlessPageFetcher(anyHost, USER_AGENT)
            .fetchForTab(server.url("/watch/5").toString(), session)

        assertEquals(HeadlessPageFetcher.Result.Page(elsewhere, "moved"), result)
        val page = server.takeRequest()
        assertEquals(TAB_AGENT, page.getHeader("User-Agent"))
        assertEquals("no-cache", page.getHeader("Cache-Control"))
        assertEquals("no-cache", page.getHeader("Pragma"))
        assertEquals("tab=fixture", page.getHeader("Cookie"))
        val moved = server.takeRequest()
        assertEquals(TAB_AGENT, moved.getHeader("User-Agent"))
        assertNull(moved.getHeader("Cookie"))
        assertEquals(listOf(server.url("/watch/5").toString()), asked)
        assertTrue(session.toString().contains("userAgentPresent=true"))
        assertTrue(!session.toString().contains("fixture"))
    }

    @Test
    fun homesReadStaysWithoutCookiesOrNoCacheAndATabWithoutAgentUsesYfts() = runTest {
        server.enqueue(html("home"))
        server.enqueue(html("tab"))
        val fetcher = HeadlessPageFetcher(client(), USER_AGENT)

        fetcher.fetch(server.url("/home").toString())
        fetcher.fetchForTab(
            server.url("/tab").toString(),
            HeadlessPageFetcher.TabSession(userAgent = null) { null },
        )

        val home = server.takeRequest()
        assertNull(home.getHeader("Cache-Control"))
        assertNull(home.getHeader("Cookie"))
        val tab = server.takeRequest()
        assertEquals(USER_AGENT, tab.getHeader("User-Agent"))
        assertEquals("no-cache", tab.getHeader("Cache-Control"))
        assertNull(tab.getHeader("Cookie"))
    }

    @Test
    fun redirectLoopsStopAtTheLimit() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again"))
        }
        val fetcher = HeadlessPageFetcher(
            client(),
            USER_AGENT,
            HeadlessPageFetcher.Policy(maxRedirects = 2),
        )

        val result = fetcher.fetch(server.url("/loop").toString())

        assertEquals(failed(HeadlessPageFetcher.FailureReason.TOO_MANY_REDIRECTS), result)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun redirectWithoutLocationFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))

        val result = fetcher().fetch(server.url("/nowhere").toString())

        assertEquals(failed(HeadlessPageFetcher.FailureReason.HTTP_STATUS), result)
    }

    @Test
    fun secureLinkIsNeverFollowedToAnInsecureAddress() = runTest {
        val requested = mutableListOf<String>()
        val client = client().newBuilder()
            .addInterceptor(
                Interceptor { chain ->
                    val url = chain.request().url
                    requested += url.toString()
                    if (url.isHttps) {
                        syntheticResponse(chain, code = 302, location = "http://example.test/")
                    } else {
                        syntheticResponse(chain, code = 200, location = null)
                    }
                },
            )
            .build()

        val result = HeadlessPageFetcher(client, USER_AGENT).fetch("https://example.test/watch")

        assertEquals(failed(HeadlessPageFetcher.FailureReason.INSECURE_REDIRECT), result)
        assertEquals(listOf("https://example.test/watch"), requested)
    }

    @Test
    fun errorStatusFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        val result = fetcher().fetch(server.url("/gone").toString())

        assertEquals(failed(HeadlessPageFetcher.FailureReason.HTTP_STATUS), result)
    }

    @Test
    fun otherDocumentTypesAreNotPages() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF"))

        val result = fetcher().fetch(server.url("/paper").toString())

        assertEquals(failed(HeadlessPageFetcher.FailureReason.NOT_A_PAGE), result)
    }

    @Test
    fun unusableAddressesFailWithoutARequest() = runTest {
        assertEquals(
            failed(HeadlessPageFetcher.FailureReason.INVALID_URL),
            fetcher().fetch("not a link"),
        )
        assertEquals(
            failed(HeadlessPageFetcher.FailureReason.INVALID_URL),
            fetcher().fetch(server.url("/").newBuilder().username("me").build().toString()),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun droppedConnectionIsANetworkFailure() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val fetcher = HeadlessPageFetcher(
            client(), USER_AGENT,
            HeadlessPageFetcher.Policy(retryDelaysMillis = emptyList()),
        )
        val result = fetcher.fetch(server.url("/drop").toString())

        assertEquals(failed(HeadlessPageFetcher.FailureReason.NETWORK), result)
        assertTrue(server.requestCount <= 1)
    }

    @Test
    fun slowProgressSucceedsButAnIdleBodyDoesNot() = runBlocking {
        server.enqueue(html("abcdefghijkl").throttleBody(1, 150, TimeUnit.MILLISECONDS))
        val slow = HeadlessPageFetcher(
            client(), USER_AGENT,
            HeadlessPageFetcher.Policy(
                callTimeoutSeconds = 6, readTimeoutMillis = 1_500,
                retryDelaysMillis = emptyList(),
            ),
        )
        assertEquals("abcdefghijkl", (slow.fetch(server.url("/slow").toString())
            as HeadlessPageFetcher.Result.Page).html)
        server.enqueue(html("late").setBodyDelay(800, TimeUnit.MILLISECONDS))
        val idle = HeadlessPageFetcher(
            client(), USER_AGENT,
            HeadlessPageFetcher.Policy(readTimeoutMillis = 200, retryDelaysMillis = emptyList()),
        )
        assertEquals(failed(HeadlessPageFetcher.FailureReason.NETWORK),
            idle.fetch(server.url("/idle").toString()))
    }

    @Test
    fun droppedConnectionAndTransientStatusesRetryBut404DoesNot() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(html("recovered"))
        val waits = mutableListOf<Long>()
        val retrying = HeadlessPageFetcher(client(), USER_AGENT, retryDelay = { waits += it })
        val url = server.url("/").toString()
        assertEquals("recovered", (retrying.fetch(url) as HeadlessPageFetcher.Result.Page).html)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(1_000L), waits)
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(504))
        server.enqueue(html("ready"))
        assertEquals("ready", (retrying.fetch(url) as HeadlessPageFetcher.Result.Page).html)
        assertEquals(5, server.requestCount)
        assertEquals(listOf(1_000L, 1_000L, 3_000L), waits)
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(failed(HeadlessPageFetcher.FailureReason.HTTP_STATUS), retrying.fetch(url))
        assertEquals(6, server.requestCount)
    }

    @Test
    fun cancellingAHeadlessBodyReadStopsTheSocketWithoutARetry() = runBlocking {
        server.enqueue(html("x".repeat(32)).throttleBody(1, 500, TimeUnit.MILLISECONDS))
        val waits = mutableListOf<Long>()
        val retrying = HeadlessPageFetcher(client(), USER_AGENT, retryDelay = { waits += it })
        val reading = async(start = CoroutineStart.UNDISPATCHED) {
                retrying.fetch(server.url("/body").toString())
            }
        assertNotNull(server.takeRequest(8, TimeUnit.SECONDS))
        withTimeout(5_000) { reading.cancelAndJoin() }
        assertTrue(reading.isCancelled)
        assertTrue(waits.isEmpty())
        assertEquals(1, server.requestCount)
    }

    private fun fetcher() = HeadlessPageFetcher(client(), USER_AGENT)

    /**
     * The server listens on 127.0.0.1 only, and so the client resolves its host: GitHub's runner
     * also maps localhost to ::1, where a retry after a dropped connection would go, because
     * OkHttp tries another address of the host before the one that failed.
     */
    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname == server.hostName) listOf(LOOPBACK) else Dns.SYSTEM.lookup(hostname)
        })
        .build()

    private fun html(body: String) = MockResponse()
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(body)

    private fun failed(reason: HeadlessPageFetcher.FailureReason) =
        HeadlessPageFetcher.Result.Failed(reason)

    private fun syntheticResponse(
        chain: Interceptor.Chain,
        code: Int,
        location: String?,
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("synthetic")
        .apply { if (location != null) header("Location", location) }
        .body("".toResponseBody("text/html".toMediaType()))
        .build()

    private companion object {
        val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) YFT/test"
        const val TAB_AGENT = "Mozilla/5.0 (Linux; Android 15; wv) WebView/test"
    }
}
