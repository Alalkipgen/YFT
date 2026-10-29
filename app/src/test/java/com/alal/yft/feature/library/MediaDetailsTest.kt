package com.alal.yft.feature.library

import androidx.compose.ui.graphics.ImageBitmap
import com.alal.yft.ui.format.YftQualityNames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Native graphics draws real bitmaps and hit-tests the mini player's top-rounded shape.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaDetailsTest {
    /**
     * v1.0.2: a file is named as the Download sheet named its row, by the standard height nearest
     * to its short side: YouTube's 1080p (itag 299) of a 2:1 video, 1920 × 960, stays "1080p".
     */
    @Test
    fun qualityIsTheNameTheSheetGaveThePicture() {
        assertEquals("720p", qualityLabel(1280, 720))
        assertEquals("720p", qualityLabel(720, 1280))
        assertEquals("1080p", qualityLabel(1920, 1080))
        assertEquals("1080p", qualityLabel(1920, 960))
        assertEquals("1080p", qualityLabel(960, 1920))
        assertEquals("480p", qualityLabel(848, 478))
        assertEquals("360p", qualityLabel(640, 360))
        assertEquals("4K", qualityLabel(3840, 2160))
        assertEquals("8K", qualityLabel(7680, 4320))
        // The sheet's row name (without its frame rate) for the same picture.
        val pictures = listOf(1920 to 960, 848 to 478, 1280 to 544, 3840 to 1600, 1080 to 1350)
        pictures.forEach { (width, height) ->
            val sheet = YftQualityNames.quality(YftQualityNames.standardHeight(width, height))
            assertEquals("$width × $height", sheet, qualityLabel(width, height))
        }
        assertNull(qualityLabel(null, 720))
        assertNull(qualityLabel(1280, 0))
        assertNull(MediaDetails.Unknown.qualityLabel)
    }

    @Test
    fun theFrameIsATenthInAndNeverPastTenSeconds() {
        assertEquals(0L, previewFrameTimeUs(null))
        assertEquals(0L, previewFrameTimeUs(0))
        assertEquals(2_520_000L, previewFrameTimeUs(25_200))
        assertEquals(10_000_000L, previewFrameTimeUs(600_000))
    }

    @Test
    fun detailsAreReadOnceAndKeptWithinTheMemoryBudget() = runTest {
        val reads = mutableListOf<String>()
        val source = RetrieverMediaDetailsSource(
            read = { uri, _ ->
                reads += uri
                MediaDetails(durationMs = 1_000, image = ImageBitmap(10, 10))
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            // Room for two 10 x 10 pictures.
            maxCacheBytes = 800,
        )
        assertNull(source.cached("a"))

        val first = source.load("a", isAudio = false)
        assertSame(first, source.load("a", isAudio = false))
        assertSame(first, source.cached("a"))
        assertEquals(listOf("a"), reads)

        source.load("b", isAudio = false)
        source.load("c", isAudio = false)
        assertNull(source.cached("a"))
        assertEquals(400, first.cacheBytes())
        assertEquals(256, MediaDetails.Unknown.cacheBytes())
    }
}
