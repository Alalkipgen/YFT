package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpExtractorNetworkTest {
    private fun policy(seconds: Long = 3, idleMillis: Long = 700) = OkHttpExtractorClient.Policy(
        callTimeoutSeconds = seconds,
        connectTimeoutMillis = 1_000,
        readTimeoutMillis = idleMillis,
        retryDelaysMillis = emptyList(),
    )

    @Test
    fun progressSurvivesAScaledOldBudgetWhileAnIdleBodyTimesOut() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            repeat(2) {
                fixture.server.enqueue(MockResponse().setBody("abcdefghijkl")
                    .throttleBody(1, 150, TimeUnit.MILLISECONDS))
            }
            val url = fixture.server.url("/slow").toString()
            val oldBudget = OkHttpExtractorClient(fixture.client, policy(seconds = 1))
            assertTrue(oldBudget.get(url, emptyMap(), 1_024) is ExtractorHttpResult.Failure)
            val waiting = OkHttpExtractorClient(fixture.client, policy(seconds = 3))
            val result = waiting.get(url, emptyMap(), 1_024) as ExtractorHttpResult.Success
            assertEquals("abcdefghijkl", result.body)
            fixture.server.enqueue(MockResponse().setBody("late")
                .setBodyDelay(800, TimeUnit.MILLISECONDS))
            val idle = OkHttpExtractorClient(fixture.client, policy(idleMillis = 200))
            assertEquals(
                SiteExtractionFailure.NETWORK,
                (idle.get(url, emptyMap(), 1_024) as ExtractorHttpResult.Failure).reason,
            )
        }
    }

    @Test
    fun only502503504RetryTwiceAfterOneAndThreeSeconds() = runBlocking {
        listOf(502, 503, 504).forEach { code ->
            ExtractorTlsFixture().use { fixture ->
                fixture.server.enqueue(MockResponse().setResponseCode(code))
                fixture.server.enqueue(MockResponse().setResponseCode(code))
                fixture.server.enqueue(MockResponse().setBody("ok"))
                val waits = mutableListOf<Long>()
                val client = OkHttpExtractorClient(fixture.client, retryDelay = { waits += it })
                val result = client.get(fixture.server.url("/").toString(), emptyMap(), 1_024)
                assertTrue(result is ExtractorHttpResult.Success)
                assertEquals(listOf(1_000L, 3_000L), waits)
                assertEquals(3, fixture.server.requestCount)
            }
        }
    }

    @Test
    fun aThirdFailureStopsAndOtherHttpStatusesNeverRetry() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            val waits = mutableListOf<Long>()
            val client = OkHttpExtractorClient(fixture.client, retryDelay = { waits += it })
            repeat(3) { fixture.server.enqueue(MockResponse().setResponseCode(503)) }
            val url = fixture.server.url("/").toString()
            assertEquals(503, (client.get(url) as ExtractorHttpResult.Failure).statusCode)
            assertEquals(3, fixture.server.requestCount)
            listOf(401, 403, 404, 429, 500).forEachIndexed { index, code ->
                fixture.server.enqueue(MockResponse().setResponseCode(code))
                assertEquals(code, (client.get(url) as ExtractorHttpResult.Failure).statusCode)
                assertEquals(4 + index, fixture.server.requestCount)
            }
            assertEquals(listOf(1_000L, 3_000L), waits)
        }
    }

    @Test
    fun readOnlyJsonPostsCanRetryWithoutChangingTheBody() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setResponseCode(502))
            fixture.server.enqueue(MockResponse().setBody("{}"))
            val client = OkHttpExtractorClient(fixture.client, retryDelay = {})
            assertTrue(client.postJson(fixture.server.url("/player").toString(), "{}")
                is ExtractorHttpResult.Success)
            repeat(2) {
                val request = fixture.server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("{}", request.body.readUtf8())
            }
        }
    }

    @Test
    fun cancellationClosesABodyReadAndDoesNotRetry() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setBody("x".repeat(32))
                .throttleBody(1, 500, TimeUnit.MILLISECONDS))
            val waits = mutableListOf<Long>()
            val client = OkHttpExtractorClient(fixture.client, retryDelay = { waits += it })
            val reading = async(start = CoroutineStart.UNDISPATCHED) {
                client.get(fixture.server.url("/body").toString())
            }
            assertNotNull(fixture.server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(2_000) { reading.cancelAndJoin() }
            assertTrue(reading.isCancelled)
            assertTrue(waits.isEmpty())
            assertEquals(1, fixture.server.requestCount)
        }
    }
}
