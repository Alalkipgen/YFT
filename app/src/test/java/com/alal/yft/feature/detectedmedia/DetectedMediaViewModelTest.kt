package com.alal.yft.feature.detectedmedia

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
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
    fun onlyListedCandidatesWithoutDrmHintsReachPreview() {
        val store = DetectedMediaStore()
        val selection = PreviewSelectionStore()
        val playable = candidate(1)
        val drmProtected = candidate(2).copy(drmHint = true)
        store.publish(page, "Fixture", listOf(playable, drmProtected))
        val viewModel = DetectedMediaViewModel(store, selection)

        assertFalse(viewModel.selectForPreview(candidate(3)))
        assertFalse(viewModel.selectForPreview(drmProtected))
        assertNull(selection.selection.value)
        assertTrue(viewModel.selectForPreview(playable))
        assertEquals(playable, selection.selection.value)
    }

    @Test
    fun clearEmptiesTheScreenAndLaterSelectionsFail() {
        val store = DetectedMediaStore()
        val selection = PreviewSelectionStore()
        val playable = candidate(1)
        store.publish(page, "Fixture", listOf(playable))
        val viewModel = DetectedMediaViewModel(store, selection)

        viewModel.clear()

        assertNull(viewModel.page.value)
        assertFalse(viewModel.selectForPreview(playable))
    }

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
