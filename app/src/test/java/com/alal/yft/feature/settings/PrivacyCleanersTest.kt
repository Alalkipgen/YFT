package com.alal.yft.feature.settings

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
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

        SessionMediaCleaner(detected, selection).clear()

        assertNull(detected.page.value)
        assertNull(selection.selection.value)
    }
}
