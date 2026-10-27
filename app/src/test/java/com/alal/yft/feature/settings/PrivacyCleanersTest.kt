package com.alal.yft.feature.settings

import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.SiteLookupKey
import com.alal.yft.download.DownloadOrigin
import com.alal.yft.download.DownloadRenewals
import com.alal.yft.download.DownloadSources
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

    @Test
    fun sessionMediaCleanerDropsEveryDownloadsSourceAndItsRenewalLines() = runTest {
        // Track E (E-D2): where a download came from goes with the cleared session.
        val candidate = MediaCandidate(
            pageUrl = "https://example.test/watch",
            mediaUrl = "https://cdn.example.test/video.mp4?sig=secret",
            sources = setOf(CandidateSource.DOM),
            kind = MediaKind.DIRECT,
            videoId = "clip-1",
        )
        val file = MediaVariant(
            id = "direct-0",
            playbackUrl = candidate.mediaUrl,
            kind = MediaKind.DIRECT,
            trackType = MediaTrackType.AUDIO_VIDEO,
            requestContext = BrowserRequestContext(candidate.pageUrl, null, null),
        )
        val asset = MediaAsset(candidate.pageUrl, "Clip", null, null, listOf(file), 1L)
        val sources = DownloadSources().apply {
            remember("task-1", DownloadOrigin(candidate, file), asset, file, "Clip.mp4")
            note("task-1", "Fresh link (403 at 1.0 MB): resumed at 1.0 MB")
        }

        SessionMediaCleaner(
            DetectedMediaStore(),
            PreviewSelectionStore(),
            SiteLookupCache(),
            sources,
        ).clear()

        assertNull(sources["task-1"])
        assertEquals(DownloadRenewals.None, sources.renewals.value)
    }
}
