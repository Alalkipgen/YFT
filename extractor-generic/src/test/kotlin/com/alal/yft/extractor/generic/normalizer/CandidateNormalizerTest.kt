package com.alal.yft.extractor.generic.normalizer

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateNormalizerTest {
    private val pageUrl = "https://example.test/watch?id=1"

    @Test
    fun deduplicatesSignedUrlsAndMergesRichestMetadataAndContext() {
        val first = candidate(
            mediaUrl = "https://cdn.test/movie.mp4?token=old&quality=720",
            sources = setOf(CandidateSource.REQUEST),
            confidence = CandidateConfidence.MEDIUM,
            observedAt = 10,
            context = BrowserRequestContext(pageUrl, "UA", null, mapOf("Accept" to "video/*")),
        )
        val second = candidate(
            mediaUrl = "https://cdn.test/movie.mp4?token=new&quality=720",
            sources = setOf(CandidateSource.DOM),
            title = "Fixture movie",
            durationMillis = 60_000,
            confidence = CandidateConfidence.HIGH,
            observedAt = 20,
            context = BrowserRequestContext(pageUrl, null, "session=fake", emptyMap()),
        )

        val result = CandidateNormalizer().normalize(pageUrl, listOf(first, second))

        assertEquals(1, result.size)
        assertTrue(result.single().mediaUrl.contains("token=new"))
        assertEquals(setOf(CandidateSource.REQUEST, CandidateSource.DOM), result.single().sources)
        assertEquals("Fixture movie", result.single().title)
        assertEquals(60_000L, result.single().durationMillis)
        assertEquals("session=fake", result.single().requestContext.cookie)
        assertEquals("video/*", result.single().requestContext.observedHeaders["Accept"])
    }

    @Test
    fun preservesDistinctQualityParameters() {
        val candidates = listOf(
            candidate("https://cdn.test/movie.mp4?quality=720&token=a"),
            candidate("https://cdn.test/movie.mp4?quality=1080&token=b"),
        )

        assertEquals(2, CandidateNormalizer().normalize(pageUrl, candidates).size)
    }

    @Test
    fun rejectsBlobTrackingTinyAndWrongPageObservations() {
        val candidates = listOf(
            candidate("blob:https://example.test/id", kind = MediaKind.UNKNOWN, sources = setOf(CandidateSource.DOM)),
            candidate("https://cdn.test/pixel.mp4", contentLengthBytes = 20_000),
            candidate("https://cdn.test/tiny.mp4", contentLengthBytes = 512),
            candidate("https://cdn.test/valid.mp4", candidatePageUrl = "https://example.test/other"),
        )

        assertTrue(CandidateNormalizer().normalize(pageUrl, candidates).isEmpty())
    }

    @Test
    fun acceptsUnknownDomCandidateButNotUnknownRequestCandidate() {
        val dom = candidate(
            mediaUrl = "https://cdn.test/generated?id=media",
            kind = MediaKind.UNKNOWN,
            sources = setOf(CandidateSource.DOM),
        )
        val request = candidate(
            mediaUrl = "https://cdn.test/generated?id=request",
            kind = MediaKind.UNKNOWN,
            sources = setOf(CandidateSource.REQUEST),
        )

        val result = CandidateNormalizer().normalize(pageUrl, listOf(dom, request))

        assertEquals(1, result.size)
        assertTrue(result.single().mediaUrl.contains("id=media"))
    }

    @Test
    fun boundsCandidatesAndOrdersByConfidenceThenRecency() {
        val candidates = (1..6).map { index ->
            candidate(
                mediaUrl = "https://cdn.test/$index.mp4",
                confidence = if (index == 2) CandidateConfidence.HIGH else CandidateConfidence.LOW,
                observedAt = index.toLong(),
            )
        }

        val result = CandidateNormalizer(CandidateNormalizer.Policy(maxCandidates = 3))
            .normalize(pageUrl, candidates)

        assertEquals(3, result.size)
        assertTrue(result.first().mediaUrl.endsWith("/2.mp4"))
        assertFalse(result.any { it.mediaUrl.endsWith("/1.mp4") })
    }

    private fun candidate(
        mediaUrl: String,
        candidatePageUrl: String = pageUrl,
        sources: Set<CandidateSource> = setOf(CandidateSource.REQUEST),
        kind: MediaKind = MediaKind.DIRECT,
        title: String? = null,
        durationMillis: Long? = null,
        contentLengthBytes: Long? = null,
        confidence: CandidateConfidence = CandidateConfidence.MEDIUM,
        observedAt: Long = 1,
        context: BrowserRequestContext = BrowserRequestContext(candidatePageUrl, null, null),
    ) = MediaCandidate(
        pageUrl = candidatePageUrl,
        mediaUrl = mediaUrl,
        sources = sources,
        kind = kind,
        title = title,
        durationMillis = durationMillis,
        contentLengthBytes = contentLengthBytes,
        confidence = confidence,
        observedAtEpochMs = observedAt,
        requestContext = context,
    )
}
