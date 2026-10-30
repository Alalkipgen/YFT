package com.alal.yft.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 item 70b: an Instagram lookup that ends unread (RESPONSE_CHANGED) on a link whose code is
 * a private post's (longer than 11 characters) shows the private words the app gives every site's
 * private or removed post, with Try again as those words have it; an 11-character code keeps
 * today's words and the login wall its own. Main's real adapter through the coordinator; every
 * request answers as Instagram answers a post it does not show (404, or the login status).
 */
class InstagramPrivateCodeTest {
    @Test
    fun `a private post's code left unread shows the private words with Try again`() = runTest {
        val http = Answers(ExtractorHttpResult.Failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, 404))

        val private = lookUp(http, PRIVATE_REEL)

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, private.reason)
        assertEquals("This Instagram post is private or no longer available.", private.message)
        // Other sites' private words keep Try again (PRIVATE_OR_UNAVAILABLE is not final): so here.
        assertTrue(private.canRetry)
        assertTrue(
            private.details.toString(),
            "link: a private post's code (longer than 11 characters)" in private.details,
        )
        assertTrue(private.details.none { PRIVATE_CODE in it })
        assertEquals("no new request: the same three asks", 3, http.asked.get())
    }

    @Test
    fun `an 11-character code keeps today's words and the wall its own`() = runTest {
        val public = lookUp(
            Answers(ExtractorHttpResult.Failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, 404)),
            PUBLIC_REEL,
        )
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, public.reason)
        assertEquals("Instagram's page could not be read. Tap Details to see why, or Try again.", public.message)
        assertTrue(public.canRetry)
        assertTrue(public.details.none { "private post's code" in it })

        val wall = lookUp(Answers(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 401)), PRIVATE_REEL)
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, wall.reason)
        assertEquals(InstagramExtractor.LOGIN_MESSAGE, wall.message)
        assertTrue(wall.details.none { "private post's code" in it })
    }

    private suspend fun lookUp(http: ExtractorHttpClient, page: String): SiteAdapterOutcome.Failed {
        val coordinator = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(InstagramExtractor(http)), SiteAdapterFlags { true }),
            MergeSupport { true },
        )
        val outcome = coordinator.inspect(page, BrowserRequestContext(page, AGENT, null), NOW)
        assertTrue(outcome.toString(), outcome is SiteAdapterOutcome.Failed)
        return outcome as SiteAdapterOutcome.Failed
    }

    /** Answers every request with [answer], as Instagram's hosts answer a post they hide. */
    private class Answers(private val answer: ExtractorHttpResult) : ExtractorHttpClient {
        val asked = AtomicInteger()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked.incrementAndGet()
            return answer
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            asked.incrementAndGet()
            return answer
        }
    }

    private companion object {
        const val PRIVATE_CODE = "C9fixtREEL1AbCdEfGhIjKlMnOpQrStUv"
        const val PRIVATE_REEL = "https://www.instagram.com/reel/$PRIVATE_CODE/"
        const val PUBLIC_REEL = "https://www.instagram.com/reel/C9fixtREEL1/"
        const val AGENT = "Mozilla/5.0 (Linux; Android 15) test"
        const val NOW = 1_700_000_000_000L
    }
}
