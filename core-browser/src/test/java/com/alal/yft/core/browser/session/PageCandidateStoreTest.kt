package com.alal.yft.core.browser.session

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageCandidateStoreTest {
    @Test
    fun debouncesNormalizesAndClearsOnNavigation() = runTest {
        val firstPage = "https://example.test/one"
        val secondPage = "https://example.test/two"
        val store = PageCandidateStore(
            scope = this,
            debounceMillis = 100,
        )
        store.beginPage(firstPage)
        store.submit(candidate(firstPage, "https://cdn.test/movie.mp4?token=one", 1))
        store.submit(candidate(firstPage, "https://cdn.test/movie.mp4?token=two", 2))

        advanceTimeBy(99)
        runCurrent()
        assertTrue(store.candidates.value.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, store.candidates.value.size)
        assertTrue(store.candidates.value.single().mediaUrl.contains("token=two"))

        store.beginPage(secondPage)
        assertTrue(store.candidates.value.isEmpty())
        store.submit(candidate(firstPage, "https://cdn.test/stale.mp4", 3))
        advanceTimeBy(100)
        runCurrent()
        assertTrue(store.candidates.value.isEmpty())
    }

    @Test
    fun rawObservationBufferIsBounded() = runTest {
        val page = "https://example.test/watch"
        val store = PageCandidateStore(
            scope = this,
            debounceMillis = 0,
            maxRawObservations = 3,
        )
        store.beginPage(page)
        repeat(5) { index ->
            store.submit(candidate(page, "https://cdn.test/$index.mp4", index.toLong()))
        }
        runCurrent()

        assertEquals(3, store.candidates.value.size)
        assertTrue(store.candidates.value.none { it.mediaUrl.endsWith("/0.mp4") })
    }

    private fun candidate(pageUrl: String, mediaUrl: String, observedAt: Long) = MediaCandidate(
        pageUrl = pageUrl,
        mediaUrl = mediaUrl,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        confidence = CandidateConfidence.MEDIUM,
        observedAtEpochMs = observedAt,
    )
}
