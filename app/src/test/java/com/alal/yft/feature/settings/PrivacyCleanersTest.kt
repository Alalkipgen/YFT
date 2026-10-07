package com.alal.yft.feature.settings

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.SiteLookupKey
import com.alal.yft.feature.browser.FakeBrowserHistoryRepository
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyCleanersTest {
    @Test
    fun compositeRunsEveryCleanerInOrder() = runTest {
        val calls = mutableListOf<String>()

        CompositeBrowsingDataCleaner(
            listOf(
                BrowsingDataCleaner { calls += "web" },
                BrowsingDataCleaner { calls += "media" },
            ),
        ).clear()

        assertEquals(listOf("web", "media"), calls)
    }

    @Test
    fun clearingBrowsingDataClearsTheBrowsersHistoryToo() = runTest {
        val history = FakeBrowserHistoryRepository()
        history.record("https://example.com/a", "A", 1_000)
        history.record("https://example.com/b", "B", 2_000)

        BrowserHistoryCleaner(history).clear()

        assertEquals(1, history.clears)
        assertEquals(emptyList<Any>(), history.pages.value)
    }

    @Test
    fun sessionMediaCleanerDropsDetectedMediaAndThePreviewSelection() = runTest {
        val candidate = MediaCandidate(
            pageUrl = "https://example.test/watch",
            mediaUrl = "https://cdn.example.test/video.mp4?sig=secret",
            sources = setOf(CandidateSource.DOM),
            kind = MediaKind.DIRECT,
        )
        val detected = DetectedMediaStore().apply {
            publish(candidate.pageUrl, "Page", listOf(candidate))
        }
        val selection = PreviewSelectionStore().apply { select(candidate) }
        // P17: a lookup remembered with the cleared session goes as well.
        val key = SiteLookupKey("fixture", "1", session = true)
        val lookups = SiteLookupCache().apply {
            put(key, SiteAdapterOutcome.Detected("fixture", listOf(candidate)), 1L)
        }

        SessionMediaCleaner(detected, selection, lookups).clear()

        assertNull(detected.page.value)
        assertNull(selection.selection.value)
        assertNull(lookups.get(key, 1L))
    }
}
