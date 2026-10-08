package com.alal.yft.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteAdapterCoordinatorTest {
    @Test
    fun `an unmatched page leaves detection to the generic pipeline`() = runTest {
        val coordinator = coordinator(FakeExtractor())

        val outcome = coordinator.inspect(
            pageUrl = "https://unrelated.test/watch/1",
            requestContext = context(),
            nowEpochMs = 10,
        )

        assertEquals(SiteAdapterOutcome.NotHandled, outcome)
    }

    @Test
    fun `adapter candidates are re-anchored to the live page address`() = runTest {
        val coordinator = coordinator(FakeExtractor())

        val outcome = coordinator.inspect(
            pageUrl = "https://fixture.test/video/42?utm_source=share",
            requestContext = context(),
            nowEpochMs = 10,
        ) as SiteAdapterOutcome.Detected

        assertEquals("fixture", outcome.adapterId)
        assertEquals(
            listOf("https://fixture.test/video/42?utm_source=share"),
            outcome.candidates.map(MediaCandidate::pageUrl),
        )
        // The request context keeps the canonical page so the origin policy still decides replay.
        assertEquals(
            "https://fixture.test/video/42",
            outcome.candidates.single().requestContext.pageUrl,
        )
        // Every file of one extraction is one video, so the found lists show one row (P3).
        assertEquals("fixture:42", outcome.candidates.single().videoId)
    }

    @Test
    fun `a disabled adapter quietly defers to the generic pipeline`() = runTest {
        val coordinator = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(FakeExtractor()), SiteAdapterFlags { false }),
        )

        val outcome = coordinator.inspect(
            pageUrl = "https://fixture.test/video/42",
            requestContext = context(),
            nowEpochMs = 10,
        )

        assertEquals(SiteAdapterOutcome.NotHandled, outcome)
    }

    @Test
    fun `each failure carries its own user-facing message and fallback decision`() = runTest {
        val expected = mapOf(
            SiteExtractionFailure.NO_MEDIA_FOUND to true,
            SiteExtractionFailure.RESPONSE_CHANGED to true,
            SiteExtractionFailure.RATE_LIMITED to true,
            SiteExtractionFailure.LOGIN_REQUIRED to false,
            SiteExtractionFailure.BOT_CHECK to false,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE to false,
            SiteExtractionFailure.GEO_RESTRICTED to false,
            SiteExtractionFailure.DRM_PROTECTED to false,
        )
        val messages = mutableSetOf<String>()

        expected.forEach { (reason, allowsFallback) ->
            val coordinator = coordinator(
                FakeExtractor(result = SiteExtractionResult.Failure(reason)),
            )

            val outcome = coordinator.inspect(
                pageUrl = "https://fixture.test/video/42",
                requestContext = context(),
                nowEpochMs = 10,
            ) as SiteAdapterOutcome.Failed

            assertEquals(reason.name, reason, outcome.reason)
            assertEquals(reason.name, allowsFallback, outcome.allowsGenericFallback)
            assertTrue(reason.name, outcome.message.contains("Fixture Site"))
            messages += outcome.message
        }

        assertEquals(expected.size, messages.size)
    }

    @Test
    fun `a bot check asks for the browser instead of a sign-in`() = runTest {
        val outcome = coordinator(
            FakeExtractor(
                result = SiteExtractionResult.Failure(
                    SiteExtractionFailure.BOT_CHECK,
                    details = listOf("client WEB: LOGIN_REQUIRED; SABR no"),
                ),
            ),
        ).inspect("https://fixture.test/video/42", context(), 1L) as SiteAdapterOutcome.Failed

        assertEquals(
            "Fixture Site wants to check that this is not a bot. Open the video in YFT's " +
                "browser, let it play for a moment, then tap Try again.",
            outcome.message,
        )
        assertFalse(outcome.message.contains("Sign in"))
        assertFalse(outcome.allowsGenericFallback)
        assertTrue(outcome.canRetry)
        assertTrue(outcome.retriesAfterPlayback)
        assertEquals(
            listOf("adapter fixture: BOT_CHECK", "client WEB: LOGIN_REQUIRED; SABR no"),
            outcome.details,
        )
    }

    @Test
    fun `only failures that asking again can change offer Try again`() = runTest {
        val retry = SiteExtractionFailure.entries.associateWith { reason ->
            val outcome = coordinator(FakeExtractor(SiteExtractionResult.Failure(reason)))
                .inspect("https://fixture.test/video/42", context(), 1L)
                as SiteAdapterOutcome.Failed
            outcome.canRetry to outcome.retriesAfterPlayback
        }

        assertEquals(
            setOf(
                SiteExtractionFailure.UNSUPPORTED_URL,
                SiteExtractionFailure.ADAPTER_DISABLED,
                SiteExtractionFailure.DRM_PROTECTED,
                SiteExtractionFailure.GEO_RESTRICTED,
            ),
            retry.filterValues { (canRetry, _) -> !canRetry }.keys,
        )
        assertEquals(
            setOf(SiteExtractionFailure.BOT_CHECK),
            retry.filterValues { (_, afterPlayback) -> afterPlayback }.keys,
        )
    }

    @Test
    fun `the matched adapter decides what counts as its player fetching media`() {
        val coordinator = coordinator(FakeExtractor())

        assertTrue(
            coordinator.isPlayerMediaRequest(
                "https://fixture.test/video/42",
                "https://media.fixture.test/stream?part=1",
            ),
        )
        assertFalse(
            coordinator.isPlayerMediaRequest(
                "https://fixture.test/video/42",
                "https://cdn.fixture.test/logo.png",
            ),
        )
        // A page no adapter handles never counts, whatever it requests.
        assertFalse(
            coordinator.isPlayerMediaRequest(
                "https://elsewhere.test/watch",
                "https://media.fixture.test/stream?part=1",
            ),
        )
        val crashing = coordinator(FakeExtractor(mediaCheckFails = true))
        assertFalse(
            crashing.isPlayerMediaRequest(
                "https://fixture.test/video/42",
                "https://media.fixture.test/stream?part=1",
            ),
        )
    }

    @Test
    fun `an adapter crash is contained and never surfaces internal detail`() = runTest {
        val coordinator = coordinator(
            FakeExtractor(failure = IllegalStateException("parser exploded at offset 912")),
        )

        val outcome = coordinator.inspect(
            pageUrl = "https://fixture.test/video/42",
            requestContext = context(),
            nowEpochMs = 10,
        ) as SiteAdapterOutcome.Failed

        // P39 (R25): a crash is named by its class, never reported as a changed page.
        assertEquals(SiteExtractionFailure.MALFORMED_RESPONSE, outcome.reason)
        assertTrue(outcome.allowsGenericFallback)
        assertFalse(outcome.message.contains("912"))
        assertTrue(outcome.details.toString(), "error: IllegalStateException" in outcome.details)
        assertFalse(outcome.details.toString().contains("912"))
    }

    @Test
    fun `an adapter's own message is shown instead of the reason's general one`() = runTest {
        val own = "This Fixture Site link does not open a video."
        val outcome = coordinator(
            FakeExtractor(
                result = SiteExtractionResult.Failure(
                    reason = SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                    details = listOf("page: phone · HTTP 200 · 12 KB · landed on: home page"),
                    message = own,
                ),
            ),
        ).inspect(
            pageUrl = "https://fixture.test/video/42",
            requestContext = context(),
            nowEpochMs = 10,
        ) as SiteAdapterOutcome.Failed
        val changed = SiteExtractionResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
        val general = coordinator(FakeExtractor(result = changed)).inspect(
            pageUrl = "https://fixture.test/video/42",
            requestContext = context(),
            nowEpochMs = 10,
        ) as SiteAdapterOutcome.Failed

        assertEquals(own, outcome.message)
        assertTrue(outcome.details.toString(), outcome.details.any { it.contains("home page") })
        assertEquals(
            "Fixture Site's page could not be read. Tap Details to see why, or Try again.",
            general.message,
        )
    }

    @Test
    fun `the adapter receives the live browser session context`() = runTest {
        val extractor = FakeExtractor()
        val coordinator = coordinator(extractor)

        coordinator.inspect(
            pageUrl = "https://fixture.test/video/42",
            requestContext = context(),
            nowEpochMs = 1_234,
        )

        val request = extractor.requests.single()
        assertEquals("fixture-agent", request.requestContext.userAgent)
        assertEquals("sessionid=fixture", request.requestContext.cookie)
        assertEquals(1_234L, request.nowEpochMs)
        assertEquals("42", request.identity.contentId)
    }

    private fun coordinator(extractor: SiteExtractor) =
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)))

    @Test
    fun `failure details preserve stage and HTTP status but never session data`() = runTest {
        val outcome = coordinator(
            FakeExtractor(
                SiteExtractionResult.Failure(
                    SiteExtractionFailure.HTTP_STATUS,
                    httpStatusCode = 403,
                    details = listOf(
                        "page GET 403",
                        "Cookie: redaction-fixture",
                        "signature=redaction-fixture",
                        "pot=redaction-fixture",
                        "media https://cdn.test/private?opaque=redaction-fixture",
                    ),
                ),
            ),
        ).inspect("https://fixture.test/video/42", context(), 1L) as SiteAdapterOutcome.Failed

        assertEquals(
            listOf("adapter fixture: HTTP_STATUS", "adapter HTTP 403", "page GET 403",
                "media https://cdn.test"),
            outcome.details,
        )
        val copied = outcome.details.joinToString("\n")
        listOf("?", "Cookie", "signature=", "pot=", "redaction-fixture").forEach {
            assertFalse(copied.contains(it))
        }
    }

    private fun context() = BrowserRequestContext(
        pageUrl = "https://fixture.test/video/42",
        userAgent = "fixture-agent",
        cookie = "sessionid=fixture",
    )

    private class FakeExtractor(
        private val result: SiteExtractionResult? = null,
        private val failure: Throwable? = null,
        private val mediaCheckFails: Boolean = false,
    ) : SiteExtractor {
        override val id: String = "fixture"
        override val displayName: String = "Fixture Site"
        val requests = mutableListOf<SiteExtractionRequest>()

        override fun identify(pageUrl: String): SitePageIdentity? {
            val prefix = "https://fixture.test/video/"
            if (!pageUrl.startsWith(prefix)) return null
            val contentId = pageUrl.removePrefix(prefix).substringBefore('?')
            if (contentId.isBlank()) return null
            return SitePageIdentity("fixture", contentId, "$prefix$contentId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean {
            check(!mediaCheckFails) { "fixture crash" }
            return requestUrl.startsWith("https://media.fixture.test/")
        }

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            failure?.let { throw it }
            return result ?: SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = request.identity.canonicalPageUrl,
                        mediaUrl = "https://cdn.fixture.test/${request.identity.contentId}.mp4",
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                        requestContext = BrowserRequestContext(
                            pageUrl = request.identity.canonicalPageUrl,
                            userAgent = request.requestContext.userAgent,
                            cookie = request.requestContext.cookie,
                        ),
                    ),
                ),
            )
        }
    }
}
