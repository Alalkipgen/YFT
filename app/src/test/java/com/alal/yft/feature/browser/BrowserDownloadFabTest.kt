package com.alal.yft.feature.browser

import com.alal.yft.feature.browser.BrowserDownloadFab.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadFabTest {
    @Test
    fun showsOnlyWhileThePageHasSavableMediaAndNothingCoversIt() {
        assertTrue(visible())
        assertFalse("start page", visible(hasPage = false))
        assertFalse("new page or DRM only", visible(savableCount = 0))
        assertFalse("found sheet expanded", visible(sheetExpanded = true))
        assertFalse("typing an address", visible(editingAddress = true))
    }

    @Test
    fun labelCountsWhatWasFound() {
        assertEquals("Download video, 1 found", BrowserDownloadFab.label(1))
        assertEquals("Download video, 3 found", BrowserDownloadFab.label(3))
    }

    @Test
    fun youTubeFacebookAndTikTokPagesShowTheButtonBeforeAnythingWasFound() {
        assertTrue("feed with nothing found", visible(savableCount = 0, findsFocusedVideo = true))
        assertFalse("start page", visible(hasPage = false, findsFocusedVideo = true))
        assertFalse("sheet expanded", visible(sheetExpanded = true, findsFocusedVideo = true))
        assertFalse("typing an address", visible(editingAddress = true, findsFocusedVideo = true))
        val onScreen = "Download the video on screen"
        assertEquals(onScreen, BrowserDownloadFab.label(0, findsOnScreen = true))
        assertEquals(onScreen, BrowserDownloadFab.label(4, findsOnScreen = true))
    }

    @Test
    fun aFeedLooksForTheVideoOnScreenAndAVideoPageOpensItsOwnVideo() {
        // A feed: whatever it found may belong to any of its videos.
        assertEquals(Action.FIND_VIDEO_ON_SCREEN, action(0, feedSite = true, feedPage = true))
        assertEquals(Action.FIND_VIDEO_ON_SCREEN, action(1, feedSite = true, feedPage = true))
        assertEquals(Action.FIND_VIDEO_ON_SCREEN, action(3, feedSite = true, feedPage = true))
        // A watch page, short or reel of those sites that found nothing yet.
        assertEquals(Action.FIND_VIDEO_ON_SCREEN, action(0, feedSite = true, feedPage = false))
        assertEquals(Action.OPEN_VIDEO, action(1, feedSite = true, feedPage = false))
        assertEquals(Action.SHOW_LIST, action(2, feedSite = true, feedPage = false))
        // Every other site keeps P3's button.
        assertNull(action(0, feedSite = false, feedPage = false))
        assertEquals(Action.OPEN_VIDEO, action(1, feedSite = false, feedPage = false))
        assertEquals(Action.SHOW_LIST, action(2, feedSite = false, feedPage = false))
    }

    private fun action(count: Int, feedSite: Boolean, feedPage: Boolean) =
        BrowserDownloadFab.action(count, findsFocusedVideo = feedSite, feedPage = feedPage)

    private fun visible(
        hasPage: Boolean = true,
        savableCount: Int = 1,
        sheetExpanded: Boolean = false,
        editingAddress: Boolean = false,
        findsFocusedVideo: Boolean = false,
    ) = BrowserDownloadFab.isVisible(
        hasPage,
        savableCount,
        sheetExpanded,
        editingAddress,
        findsFocusedVideo,
    )
}
