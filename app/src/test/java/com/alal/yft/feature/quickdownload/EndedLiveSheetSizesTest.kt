package com.alal.yft.feature.quickdownload

import com.alal.yft.core.download.EndedLiveSizes
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ended live size sample in the sheet: the owner's replay (2026-11-08) stated no sizes; its rows
 * take the sizes their pieces gave, a video with its sound counted once each.
 */
class EndedLiveSheetSizesTest {
    private val sound = "https://rr1.media.test/videoplayback?itag=140&source=yt_live_broadcast"

    private fun live(height: Int) = QuickDownloadFixtures.video(height, merged = true).let {
        it.copy(
            mediaUrl = "https://rr1.media.test/videoplayback?itag=$height&source=yt_live_broadcast",
            audioCompanion = it.audioCompanion!!.copy(mediaUrl = sound, contentLengthBytes = null),
            bitrateBitsPerSecond = height * 1_000L,
        )
    }

    private val candidates = listOf(
        live(720),
        live(144),
        QuickDownloadFixtures.audio(128).copy(mediaUrl = sound),
        // A finished video's file: its size is stated and nothing is sampled for it.
        QuickDownloadFixtures.video(360, 11 * MIB),
    )

    private fun size(mib: Long) = EndedLiveSizes.Size(
        bytes = mib * MIB,
        lowBytes = mib * MIB * 9 / 10,
        highBytes = mib * MIB * 11 / 10,
        piecesRead = 24,
        pieces = 545,
    )

    @Test
    fun onlyTheLiveFilesOfRowsWithoutASizeAreSampled() {
        val choices = QuickDownloadFixtures.choices(candidates)!!

        val tracks = QuickDownloadChoices.endedLiveTracks(choices)

        // Each file once: the sound shared by both videos and the M4A row is one file.
        assertEquals(3, tracks.size)
        assertEquals(1, tracks.count { it.sound })
        assertTrue(tracks.all { EndedLiveSizes.isLiveFile(it.url) })
        assertFalse(EndedLiveSizes.isLiveFile("https://media.example.test/video-360.mp4"))
    }

    @Test
    fun aRowShowsItsFilesSampledSizesOnceAll() {
        val choices = QuickDownloadFixtures.choices(candidates)!!
        val video720 = live(720).mediaUrl
        val video144 = live(144).mediaUrl

        val reading = QuickDownloadChoices.withSampledSizes(
            choices,
            mapOf(sound to size(42)),
            sampling = setOf(video720, video144, sound),
        )
        val row720 = reading.video.first { it.rankHeight == 720 }
        assertNull(row720.size)
        assertTrue(row720.sizePending)
        // The M4A's own file is sized already.
        val m4a = reading.audio.first { it.title == "M4A" }
        assertEquals("~42 MB", m4a.size)
        assertEquals(listOf(QuickDownloadChoices.ROUGH_ESTIMATE), m4a.chips)

        val done = QuickDownloadChoices.withSampledSizes(
            reading,
            mapOf(sound to size(42), video720 to size(346), video144 to size(21)),
            sampling = emptySet(),
        )
        // Video and sound, each once: 346 + 42 MB and 21 + 42 MB.
        assertEquals("~388 MB", done.video.first { it.rankHeight == 720 }.size)
        assertEquals("~63 MB", done.video.first { it.rankHeight == 144 }.size)
        assertTrue(done.video.first { it.rankHeight == 144 }.chips.contains("Rough estimate"))
        assertFalse(done.options.any { it.sizePending })
        // The download gets the row's variant as it was: an estimate is never its stated size.
        assertNull(done.video.first { it.rankHeight == 720 }.variant.sizeBytes)
        // A row with its own size keeps it, without the chip.
        val stated = done.video.first { it.rankHeight == 360 }
        assertEquals("11 MB", stated.size)
        assertTrue(QuickDownloadChoices.ROUGH_ESTIMATE !in stated.chips)
        // Applied again (another size check came in), nothing doubles.
        val again = QuickDownloadChoices.withSampledSizes(done, mapOf(sound to size(42)), emptySet())
        assertEquals(
            listOf(QuickDownloadChoices.ROUGH_ESTIMATE),
            again.audio.first { it.title == "M4A" }.chips,
        )
        // A row whose video got no size goes back to "Size unknown".
        assertNull(again.video.first { it.rankHeight == 720 }.size)
        assertTrue(QuickDownloadChoices.ROUGH_ESTIMATE !in again.video.first { it.rankHeight == 720 }.chips)
    }
}
