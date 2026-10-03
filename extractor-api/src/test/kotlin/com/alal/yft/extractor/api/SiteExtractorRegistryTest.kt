package com.alal.yft.extractor.api

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteExtractorRegistryTest {
    @Test
    fun `an unmatched page falls through to the generic detector`() {
        val registry = SiteExtractorRegistry(listOf(FakeExtractor("alpha", "alpha.test")))

        assertEquals(SiteAdapterSelection.None, registry.select("https://other.test/watch/1"))
    }

    @Test
    fun `a matched page resolves the adapter and its canonical identity`() {
        val registry = SiteExtractorRegistry(listOf(FakeExtractor("alpha", "alpha.test")))

        val matched = registry.select("https://alpha.test/video/42?utm_source=x")
            as SiteAdapterSelection.Matched

        assertEquals("alpha", matched.extractor.id)
        assertEquals("42", matched.identity.contentId)
        assertEquals("https://alpha.test/video/42", matched.identity.canonicalPageUrl)
    }

    @Test
    fun `a disabled adapter is reported instead of being silently matched`() {
        val registry = SiteExtractorRegistry(
            extractors = listOf(FakeExtractor("alpha", "alpha.test")),
            flags = { it != "alpha" },
        )

        val selection = registry.select("https://alpha.test/video/42")

        assertEquals(SiteAdapterSelection.Disabled("alpha"), selection)
        assertEquals(listOf("alpha"), registry.disabledAdapterIds())
    }

    @Test
    fun `duplicate adapter ids are rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteExtractorRegistry(
                listOf(
                    FakeExtractor("alpha", "alpha.test"),
                    FakeExtractor("alpha", "beta.test"),
                ),
            )
        }
    }

    @Test
    fun `two adapters claiming one page is a programming error, not a silent winner`() {
        val registry = SiteExtractorRegistry(
            listOf(
                FakeExtractor("alpha", "shared.test"),
                FakeExtractor("beta", "shared.test"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            registry.select("https://shared.test/video/7")
        }
    }

    @Test
    fun `access failures do not fall back to the generic detector`() {
        val blocked = listOf(
            SiteExtractionFailure.DRM_PROTECTED,
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.BOT_CHECK,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.GEO_RESTRICTED,
        )

        blocked.forEach { reason ->
            assertFalse(
                reason.name,
                SiteExtractionResult.Failure(reason).allowsGenericFallback,
            )
        }
        assertTrue(
            SiteExtractionResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
                .allowsGenericFallback,
        )
        assertTrue(
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
                .allowsGenericFallback,
        )
    }

    @Test
    fun `an empty successful extraction is rejected so no fabricated media is returned`() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteExtractionResult.Success(emptyList())
        }
    }

    @Test
    fun `an insecure canonical page url is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SitePageIdentity("alpha", "1", "http://alpha.test/video/1")
        }
    }

    @Test
    fun `the extraction request never renders the page url or session context`() {
        val request = SiteExtractionRequest(
            identity = SitePageIdentity(
                "alpha",
                "1",
                "https://alpha.test/video/1?token=supersecret",
            ),
            requestContext = BrowserRequestContext(
                pageUrl = "https://alpha.test/video/1",
                userAgent = "fixture-agent",
                cookie = "session=supersecret",
            ),
            nowEpochMs = 10,
        )

        val rendered = request.toString()

        assertFalse(rendered.contains("supersecret"))
        assertTrue(rendered.contains("siteId=alpha"))
    }

    private class FakeExtractor(
        override val id: String,
        private val host: String,
    ) : SiteExtractor {
        override val displayName: String = host

        override fun identify(pageUrl: String): SitePageIdentity? {
            val prefix = "https://$host/video/"
            if (!pageUrl.startsWith(prefix)) return null
            val contentId = pageUrl.removePrefix(prefix).substringBefore('?')
            if (contentId.isBlank()) return null
            return SitePageIdentity(
                siteId = id,
                contentId = contentId,
                canonicalPageUrl = "$prefix$contentId",
            )
        }

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = request.identity.canonicalPageUrl,
                        mediaUrl = "https://$host/media/${request.identity.contentId}.mp4",
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                    ),
                ),
            )
    }

    @Test
    fun `a registry with no adapters is usable and lists nothing`() {
        val registry = SiteExtractorRegistry(emptyList())

        assertEquals(emptyList<String>(), registry.adapterIds)
        assertNull((registry.select("https://alpha.test/video/1") as? SiteAdapterSelection.Matched))
    }
}
