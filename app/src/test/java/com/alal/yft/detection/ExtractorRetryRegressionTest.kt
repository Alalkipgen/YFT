package com.alal.yft.detection

import com.alal.yft.extractor.api.ExtractorHttpResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the original public constructor so it runs against the actual pre-P10 client. */
class ExtractorRetryRegressionTest {
    @Test
    fun aDroppedFirstConnectionSucceedsOnTheSecondRequest() = runBlocking {
        ExtractorTlsFixture().use { fixture ->
            fixture.server.enqueue(
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
            )
            fixture.server.enqueue(MockResponse().setBody("fixture page"))
            val result = OkHttpExtractorClient(fixture.client).get(
                fixture.server.url("/page").toString(),
                emptyMap(),
                1_024,
            )
            assertTrue(result is ExtractorHttpResult.Success)
            assertEquals("fixture page", (result as ExtractorHttpResult.Success).body)
            assertEquals(2, fixture.server.requestCount)
        }
    }
}
