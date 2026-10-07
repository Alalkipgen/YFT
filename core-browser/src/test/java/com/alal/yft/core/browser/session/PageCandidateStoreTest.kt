package com.alal.yft.core.browser.session

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun movingThePageKeepsPublishedAndPendingCandidatesUnderTheNewAddress() = runTest {
        val page = "https://example.test/watch?v=1"
        val moved = "https://example.test/watch?v=1&pp=share"
        val store = PageCandidateStore(scope = this, debounceMillis = 100)
        store.beginPage(page)
        store.submit(candidate(page, "https://cdn.test/first.mp4", 1))
        advanceTimeBy(100)
        runCurrent()
        store.submit(candidate(page, "https://cdn.test/second.mp4", 2))

        // The second candidate is still waiting for the debounce when the address changes.
        store.movePage(moved)
        assertEquals(listOf(moved), store.candidates.value.map { it.pageUrl })
        store.submit(candidate(page, "https://cdn.test/stale.mp4", 3))
        advanceTimeBy(100)
        runCurrent()

        assertEquals(
            listOf("https://cdn.test/first.mp4", "https://cdn.test/second.mp4"),
            store.candidates.value.map { it.mediaUrl }.sorted(),
        )
        assertTrue(store.candidates.value.all { it.pageUrl == moved })
    }

    @Test
    fun thePagesFactsAreKeptPerPageAndANewerReadKeepsWhatAnOlderFound() = runTest {
        // P28: what the page states about its video, as its DOM probe reads it again and again.
        val page = "https://example.test/watch"
        val store = PageCandidateStore(scope = this, debounceMillis = 0)
        store.beginPage(page)
        assertNull(store.facts.value)

        store.submitFacts(page, PageVideoFacts(durationMillis = 984_000))
        store.submitFacts(page, PageVideoFacts(title = "Harbour lights"))
        store.submitFacts(page, PageVideoFacts())
        store.submitFacts("https://example.test/other", PageVideoFacts(title = "Other"))

        assertEquals(PageVideoFacts(984_000, "Harbour lights"), store.facts.value)
        store.movePage("$page#t=10")
        assertEquals(984_000L, store.facts.value?.durationMillis)
        store.beginPage("https://example.test/next")
        assertNull(store.facts.value)
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
