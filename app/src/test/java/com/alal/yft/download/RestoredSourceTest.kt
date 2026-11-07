package com.alal.yft.download

import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.RenewalFixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fresh link renewal: a download the app closed reads its source back from the card saved with its
 * record, so its row offers Retry and Retry gets a fresh link of the same file; a card this version
 * does not read leaves the row as it was (reopen the page), and a finished download keeps none.
 */
class RestoredSourceTest {
    @Test
    fun `a closed download's card gives its source back`() {
        val sources = DownloadSources()
        sources.restored(TASK, CARD)

        val source = requireNotNull(sources[TASK])
        assertEquals(PAGE, source.pageUrl)
        assertEquals(VIDEO, source.videoId)
        assertEquals("Fixture 720p.mp4", source.fileName)
        assertEquals(SAVED, source.saved)
        assertTrue(source.renewable)
        assertEquals(setOf(TASK), sources.renewals.value.renewable)
    }

    @Test
    fun `a card this version does not read leaves the download as it was`() {
        val sources = DownloadSources()
        sources.restored(TASK, "v9|something else")

        assertNull(sources[TASK])
        assertTrue(sources.renewals.value.renewable.isEmpty())
    }

    @Test
    fun `a source with no file to reopen is no card at all`() {
        val sources = DownloadSources()
        sources.restored(TASK, CARD)
        assertNull(sources.card(REMOVED))
    }

    @Test
    fun `a card goes when the app no longer holds the source`() {
        val sources = DownloadSources()
        sources.restored(TASK, CARD)
        assertNotNull(sources.card(TASK))

        // Clear browsing data: the pages and the sources go, and their cards with them.
        sources.clear()

        assertNull(sources.card(TASK))
        assertNull(sources[TASK])
    }

    @Test
    fun `a finished download keeps no card`() {
        val sources = DownloadSources()
        sources.restored(TASK, CARD)

        sources.prune(listOf(task(TASK, DownloadTaskStatus.COMPLETED)))

        assertNull(sources.card(TASK))
    }

    @Test
    fun `a download whose row is still listed keeps its card`() {
        val sources = DownloadSources()
        sources.restored(TASK, CARD)

        val listed: List<StoredDownloadTask> = listOf(task(TASK, DownloadTaskStatus.NEEDS_REFRESH))
        sources.prune(listed)

        assertEquals(CARD, sources.card(TASK))
        assertFalse(sources.renewals.value.renewable.isEmpty())
    }

    private companion object {
        const val TASK = "task-closed"
        const val REMOVED = "task-other"
        const val PAGE = "https://vk.example.test/video-1_2"
        const val VIDEO = "vk:-1_2"
        val SAVED = SavedDestination(
            kind = com.alal.yft.core.download.DownloadDestinationKind.APP_PRIVATE,
            uri = null,
            displayName = "Fixture 720p.mp4",
            partialName = "Fixture 720p.mp4.ab12cd.part",
        )
        val CARD = RenewalCard.encode(
            DownloadSource(
                pageUrl = PAGE,
                videoId = VIDEO,
                candidateKind = com.alal.yft.core.model.media.MediaKind.DIRECT,
                owner = com.alal.yft.feature.detectedmedia.LookupOwner.BROWSER,
                userAgent = "Mozilla/5.0 (fixture)",
                session = false,
                quality = DownloadQualityKey(
                    kind = com.alal.yft.core.model.media.MediaKind.DIRECT,
                    trackType = com.alal.yft.core.model.media.MediaTrackType.VIDEO,
                    height = 720,
                ),
                conversion = DownloadConversion(),
                label = "720p",
                title = "Fixture",
                durationMillis = 221_000,
                fileName = "Fixture 720p.mp4",
                networkChanges = 0,
                saved = SAVED,
            ),
        )
    }
}
