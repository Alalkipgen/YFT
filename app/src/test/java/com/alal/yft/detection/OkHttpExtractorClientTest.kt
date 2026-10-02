package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpExtractorClientTest {
    private val session = mapOf(
        "Cookie" to "SID=fixture",
        "Authorization" to "Bearer fixture",
        "User-Agent" to "FixtureAgent/1.0",
    )

    @Test
    fun `a redirect within the site keeps the session`() = runTest {
        val server = FakeServer(
            "https://www.youtube.com/watch?v=x" to redirect(302, "https://m.youtube.com/watch?v=x"),
            "https://m.youtube.com/watch?v=x" to ok("page"),
        )

        val result = server.client().get("https://www.youtube.com/watch?v=x", session, 1_024)

        assertEquals("page", (result as ExtractorHttpResult.Success).body)
        assertEquals("https://m.youtube.com/watch?v=x", result.finalUrl)
        server.requests.forEach { request ->
            assertEquals("SID=fixture", request.header("Cookie"))
            assertEquals("Bearer fixture", request.header("Authorization"))
        }
    }

    @Test
    fun `a redirect to another site drops credentials for the rest of the chain`() = runTest {
        val server = FakeServer(
            "https://www.youtube.com/a" to redirect(302, "https://tracker.example.test/b"),
            "https://tracker.example.test/b" to redirect(302, "https://www.youtube.com/c"),
            "https://www.youtube.com/c" to ok("done"),
        )

        val result = server.client().get("https://www.youtube.com/a", session, 1_024)

        assertTrue(result is ExtractorHttpResult.Success)
        val (first, foreign, back) = server.requests
        assertEquals("SID=fixture", first.header("Cookie"))
        assertNull(foreign.header("Cookie"))
        assertNull(foreign.header("Authorization"))
        assertEquals("FixtureAgent/1.0", foreign.header("User-Agent"))
        // Coming back does not restore what a foreign hop could have observed being withheld.
        assertNull(back.header("Cookie"))
        assertNull(back.header("Authorization"))
    }

    @Test
    fun `a see-other redirect turns a JSON post into a get, a 307 keeps it`() = runTest {
        val server = FakeServer(
            "https://www.youtube.com/see-other" to redirect(303, "https://www.youtube.com/next"),
            "https://www.youtube.com/temporary" to redirect(307, "https://www.youtube.com/next"),
            "https://www.youtube.com/next" to ok("{}"),
        )
        val client = server.client()

        client.postJson("https://www.youtube.com/see-other", "{\"a\":1}", session, 1_024)
        client.postJson("https://www.youtube.com/temporary", "{\"a\":1}", session, 1_024)

        val methods = server.requests.map { it.method }
        assertEquals(listOf("POST", "GET", "POST", "POST"), methods)
        assertEquals("{\"a\":1}", server.bodies[3])
    }

    @Test
    fun `insecure addresses and downgrades are refused`() = runTest {
        val server = FakeServer(
            "https://www.youtube.com/down" to redirect(302, "http://www.youtube.com/plain"),
        )
        val client = server.client()

        assertEquals(
            SiteExtractionFailure.UNSUPPORTED_URL,
            (client.get("http://www.youtube.com/", session, 1_024) as Failure).reason,
        )
        assertEquals(
            SiteExtractionFailure.UNSUPPORTED_URL,
            (client.get("https://user@www.youtube.com/", session, 1_024) as Failure).reason,
        )
        assertEquals(
            SiteExtractionFailure.UNSUPPORTED_URL,
            (client.get("https://www.youtube.com/down", session, 1_024) as Failure).reason,
        )
        assertEquals(1, server.requests.size)
    }

    @Test
    fun `redirect loops and oversized bodies fail instead of truncating`() = runTest {
        val server = FakeServer(
            "https://www.youtube.com/loop" to redirect(302, "https://www.youtube.com/loop"),
            "https://www.youtube.com/big" to ok("x".repeat(2_048)),
        )
        val client = server.client()

        assertEquals(
            SiteExtractionFailure.RESPONSE_CHANGED,
            (client.get("https://www.youtube.com/loop", session, 1_024) as Failure).reason,
        )
        assertEquals(
            SiteExtractionFailure.RESPONSE_TOO_LARGE,
            (client.get("https://www.youtube.com/big", session, 1_024) as Failure).reason,
        )
    }

    @Test
    fun `status codes map onto structured failures`() = runTest {
        val codes = mapOf(
            403 to SiteExtractionFailure.LOGIN_REQUIRED,
            404 to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            429 to SiteExtractionFailure.RATE_LIMITED,
            451 to SiteExtractionFailure.GEO_RESTRICTED,
            500 to SiteExtractionFailure.HTTP_STATUS,
        )
        val server = FakeServer(
            *codes.keys.map { "https://www.youtube.com/$it" to status(it) }.toTypedArray(),
        )
        val client = server.client()

        codes.forEach { (code, reason) ->
            val failure = client.get("https://www.youtube.com/$code", session, 1_024) as Failure
            assertEquals(reason, failure.reason)
            assertEquals(code, failure.statusCode)
        }
    }

    private class Reply(val code: Int, val location: String? = null, val body: String = "")

    private fun redirect(code: Int, location: String) = Reply(code, location)

    private fun ok(body: String) = Reply(200, body = body)

    private fun status(code: Int) = Reply(code)

    /** Answers from a fixed table before anything reaches the network. */
    private class FakeServer(vararg routes: Pair<String, Reply>) : Interceptor {
        private val table = routes.toMap()
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String?>()

        fun client() = OkHttpExtractorClient(OkHttpClient.Builder().addInterceptor(this).build())

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            bodies += request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }
            val reply = table[request.url.toString()] ?: Reply(404)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.code)
                .message("fixture")
                .apply { reply.location?.let { header("Location", it) } }
                .body(reply.body.toResponseBody("text/plain".toMediaType()))
                .build()
        }
    }
}

private typealias Failure = ExtractorHttpResult.Failure
