package com.alal.yft.feature.detectedmedia

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedMediaViewModelTest {
    private val page = "https://example.test/watch"

    @Test
    fun storeKeepsOnePageAndCapsItsCandidates() {
        val store = DetectedMediaStore()
        store.publish(page, "First", listOf(candidate(0)))
        val many = (1..DetectedMediaStore.MAX_CANDIDATES + 5).map(::candidate)

        store.publish("https://example.test/next", null, many)

        val published = store.page.value!!
        assertEquals("https://example.test/next", published.pageUrl)
        assertNull(published.pageTitle)
        assertEquals(DetectedMediaStore.MAX_CANDIDATES, published.candidates.size)
        assertEquals(many.first(), published.candidates.first())
        store.clear()
        assertNull(store.page.value)
    }

    @Test
    fun onlyListedVideosWithoutDrmHintsReachTheDownloadSheet() {
        val store = DetectedMediaStore()
        val playable = candidate(1)
        val drmProtected = candidate(2).copy(drmHint = true)
        store.publish(page, "Fixture", listOf(playable, drmProtected))
        val viewModel = DetectedMediaViewModel(store)

        assertFalse(viewModel.selectForDownload(video(candidate(3))))
        assertFalse(viewModel.selectForDownload(video(drmProtected)))
        assertNull(store.selection.value)
        assertTrue(viewModel.selectForDownload(video(playable)))
        assertEquals(listOf(playable), store.selection.value?.candidates)
    }

    @Test
    fun aChosenVideoStaysWithItsPageAndGoesWithTheNextPage() {
        val store = DetectedMediaStore()
        val playable = candidate(1)
        store.publish(page, "Fixture", listOf(playable))
        store.select(video(playable))

        // More media found on the same page keeps the choice; another page drops it.
        store.publish(page, "Fixture", listOf(playable, candidate(2)))
        assertEquals(listOf(playable), store.selection.value?.candidates)
        store.publish("https://example.test/next", null, listOf(candidate(3)))
        assertNull(store.selection.value)
    }

    @Test
    fun clearEmptiesTheScreenAndLaterSelectionsFail() {
        val store = DetectedMediaStore()
        val playable = candidate(1)
        store.publish(page, "Fixture", listOf(playable))
        val viewModel = DetectedMediaViewModel(store)
        assertTrue(viewModel.selectForDownload(video(playable)))

        viewModel.clear()

        assertNull(viewModel.page.value)
        assertNull(store.selection.value)
        assertFalse(viewModel.selectForDownload(video(playable)))
    }

    private fun video(candidate: MediaCandidate): MediaGroup =
        MediaGroups.of(listOf(candidate)).single()

    private fun candidate(index: Int) = MediaCandidate(
        pageUrl = page,
        mediaUrl = "https://cdn.example.test/video-$index.mp4",
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        title = "Video $index",
        observedAtEpochMs = index.toLong(),
    )
}
