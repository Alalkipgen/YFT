package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PageNavigationHeaders
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.CookieJar
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeadlessPageFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
        val client = OkHttpClient.Builder().cookieJar(leakyJar).build()

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
            OkHttpClient(),
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
            OkHttpClient(),
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
    fun redirectLoopsStopAtTheLimit() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again"))
        }
        val fetcher = HeadlessPageFetcher(
            OkHttpClient(),
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
        val client = OkHttpClient.Builder()
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

        val result = fetcher().fetch(server.url("/drop").toString())

        assertEquals(failed(HeadlessPageFetcher.FailureReason.NETWORK), result)
        assertTrue(server.requestCount <= 1)
    }

    private fun fetcher() = HeadlessPageFetcher(OkHttpClient(), USER_AGENT)

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
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) YFT/test"
    }
}
