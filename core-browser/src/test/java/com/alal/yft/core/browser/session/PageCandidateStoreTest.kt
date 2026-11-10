package com.alal.yft.core.browser.session

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.core.model.media.StreamPieces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // What the page states about its video, as its DOM probe reads it again and again.
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

    @Test
    fun aStreamsPiecesAndRefreshesNeverCrowdOutThePagesVideo() = runTest {
        // Two live widgets refresh their playlists every 2 s and fetch a piece a second; the
        // page's own master was asked once, minutes earlier.
        val page = "https://example.test/watch"
        val store = PageCandidateStore(scope = this, debounceMillis = 10, maxRawObservations = 20)
        store.beginPage(page)
        val master = candidate(page, "https://vod.test/hls/5/master.m3u8", 1)
            .copy(kind = MediaKind.HLS, confidence = CandidateConfidence.HIGH)
        store.submit(master)
        val playlist = candidate(page, "https://cam.test/b-hls-03/41/41_240p.m3u8", 2)
            .copy(kind = MediaKind.HLS, confidence = CandidateConfidence.HIGH)
        repeat(50) { store.submit(playlist.copy(observedAtEpochMs = 10L + it)) }
        // Numbered .ts pieces of the page's own stream, before its playlist was read.
        repeat(30) { store.submit(candidate(page, "https://vod.test/hls/5/seg-$it-v1.ts", 100L + it)) }
        advanceTimeBy(10)
        runCurrent()

        val urls = store.candidates.value.map { it.mediaUrl }
        assertTrue(master.mediaUrl in urls)
        assertTrue(playlist.mediaUrl in urls)
        assertEquals(59L, store.candidates.value.first { it.mediaUrl == playlist.mediaUrl }.observedAtEpochMs)

        // Once the widget's playlist is read, its newer pieces are not kept and need no probe.
        val live = StreamPieces.of(true, listOf("https://cam.test/b-hls-03/41/Zx81Qa.mp4"))
        store.submit(playlist.copy(streamPieces = live, observedAtEpochMs = 200))
        val piece = candidate(page, "https://cam.test/b-hls-03/41/Pq72Lm.mp4", 201)
        assertTrue(store.isStreamPiece(piece))
        store.submit(piece)
        assertTrue(store.isStreamPiece(candidate(page, "https://vod.test/hls/5/seg-99-v1.ts", 202)))
        assertFalse(store.isStreamPiece(candidate(page, "https://vod.test/hls/5/720p.mp4", 203)))
        assertFalse(store.isStreamPiece(candidate("https://example.test/other", piece.mediaUrl, 204)))
        advanceTimeBy(10)
        runCurrent()
        assertTrue(store.candidates.value.none { it.mediaUrl == piece.mediaUrl })
        assertTrue(master.mediaUrl in store.candidates.value.map { it.mediaUrl })
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
