package com.alal.yft.core.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadPreferencesTest {
    private data class Option(val name: String, val height: Int?, val bitrate: Long? = null)

    private val options = listOf(
        Option("360p", 360, 500_000),
        Option("1080p", 1_080, 4_000_000),
        Option("720p-low", 720, 1_500_000),
        Option("720p-high", 720, 2_500_000),
        Option("2160p", 2_160, 12_000_000),
    )

    private fun QualityPreference.choose(list: List<Option> = options) =
        pick(list, Option::height, Option::bitrate)?.name

    @Test
    fun `each preference picks the best variant under its ceiling`() {
        assertEquals("2160p", QualityPreference.HIGHEST.choose())
        assertEquals("1080p", QualityPreference.UP_TO_1080P.choose())
        assertEquals("720p-high", QualityPreference.UP_TO_720P.choose())
        assertEquals("360p", QualityPreference.UP_TO_480P.choose())
        assertEquals("360p", QualityPreference.LOWEST.choose())
    }

    @Test
    fun `a ceiling every variant exceeds falls back to the smallest`() {
        val tall = listOf(Option("1080p", 1_080), Option("720p", 720))

        assertEquals("720p", QualityPreference.UP_TO_480P.choose(tall))
    }

    @Test
    fun `unknown heights are used only when nothing states a height`() {
        val unknown = listOf(Option("first", null), Option("second", null))
        val mixed = listOf(Option("unknown", null), Option("240p", 240))

        assertEquals("first", QualityPreference.LOWEST.choose(unknown))
        assertEquals("first", QualityPreference.HIGHEST.choose(unknown))
        assertEquals("240p", QualityPreference.HIGHEST.choose(mixed))
        assertNull(QualityPreference.HIGHEST.choose(emptyList()))
    }

    @Test
    fun `defaults are conservative and concurrency stays in range`() {
        val defaults = DownloadPreferences()

        assertEquals(QualityPreference.UP_TO_720P, defaults.defaultQuality)
        assertEquals("720p-high", defaults.defaultQuality.choose())
        assertEquals(DownloadLocation.SHARED_DOWNLOADS, defaults.location)
        assertEquals(false, defaults.unmeteredOnly)
        assertEquals(true, defaults.confirmOnMeteredNetwork)
        assertEquals(2, defaults.maxConcurrentDownloads)
        assertThrows(IllegalArgumentException::class.java) {
            DownloadPreferences(maxConcurrentDownloads = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DownloadPreferences(maxConcurrentDownloads = 5)
        }
    }
}
