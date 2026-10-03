package com.alal.yft.feature.library

import androidx.compose.ui.graphics.ImageBitmap
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
    @Test
    fun qualityComesFromThePicturesShortSide() {
        assertEquals("720p", qualityLabel(1280, 720))
        assertEquals("720p", qualityLabel(720, 1280))
        assertEquals("1080p", qualityLabel(1920, 1080))
        assertEquals("360p", qualityLabel(640, 360))
        assertEquals("4K", qualityLabel(3840, 2160))
        assertEquals("8K", qualityLabel(7680, 4320))
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
