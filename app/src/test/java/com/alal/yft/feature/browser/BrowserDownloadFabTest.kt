package com.alal.yft.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun visible(
        hasPage: Boolean = true,
        savableCount: Int = 1,
        sheetExpanded: Boolean = false,
        editingAddress: Boolean = false,
    ) = BrowserDownloadFab.isVisible(hasPage, savableCount, sheetExpanded, editingAddress)
}
