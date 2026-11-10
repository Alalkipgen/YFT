package com.alal.yft.feature.quickdownload

import com.alal.yft.core.download.EndedLiveSizes
import com.alal.yft.core.download.FinishedStreamSizes
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.OTHER_PAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finished stream size sample in the sheet: the owner's Roshy, jav.guru and Pornhub masters
 * (2026-11-08) state `BANDWIDTH` only, so their rows read "Size unknown" until a sample of their
 * playlists' pieces sizes them.
 */
class FinishedStreamSheetSizesTest {
    private val sound = "https://cdn.example.test/hls/audio.m3u8"

    /** A master stating peak rates only: 1080p, 720p and 480p, with no size of their own. */
    private fun peakOnly(separateSound: Boolean = false): List<SheetSource> {
        val master = QuickDownloadFixtures.video(
            null,
            kind = MediaKind.HLS,
            videoId = null,
            label = null,
            index = 1,
        ).copy(pageUrl = OTHER_PAGE, pageVideoKey = "player-1")
        val stated = QuickDownloadFixtures.resolvedAsset(master).variants.single()
        val ladder = listOf(1080 to 3_122_112L, 720 to 1_674_457L, 480 to 786_895L)
            .map { (height, bandwidth) ->
                stated.copy(
                    id = "hls-$height",
                    playbackUrl = "https://cdn.example.test/hls/index-$height.m3u8",
                    kind = MediaKind.HLS,
                    mimeType = "application/x-mpegURL",
                    width = height * 16 / 9,
                    height = height,
                    bitrateBitsPerSecond = bandwidth,
                    sizeBytes = null,
                    sizeAccuracy = null,
                    trackType = if (separateSound) {
                        MediaTrackType.AUDIO_VIDEO
                    } else {
                        stated.trackType
                    },
                    audioCompanion = if (separateSound) {
                        CompanionAudio(
                            mediaUrl = sound,
                            mimeType = "application/x-mpegURL",
                            codecs = listOf("mp4a.40.2"),
                            requestContext = stated.requestContext,
                        )
                    } else {
                        null
                    },
                )
            }
        val asset = QuickDownloadFixtures.resolvedAsset(master).copy(variants = ladder)
        return listOf(SheetSource(master, asset, resolved = true))
    }

    private fun choices(sources: List<SheetSource>) = QuickDownloadChoices.of(
        QuickDownloadFixtures.group(sources.map { it.candidate }),
        sources,
    )!!

    private fun size(bytes: Long) = EndedLiveSizes.Size(
        bytes = bytes,
        lowBytes = bytes * 97 / 100,
        highBytes = bytes * 103 / 100,
        piecesRead = 48,
        pieces = 885,
    )

    @Test
    fun peakRateRowsAreSampledFromTheirOwnPlaylists() {
        val choices = choices(peakOnly())
        assertTrue(choices.video.all { it.size == null })

        val tracks = QuickDownloadChoices.finishedStreamTracks(choices)

        assertEquals(
            listOf(1080, 720, 480).map { "https://cdn.example.test/hls/index-$it.m3u8" }.toSet(),
            tracks.map { it.url }.toSet(),
        )
        assertTrue(tracks.none { it.sound })
        // Not a live broadcast's files: the ended live sample does not take them.
        assertTrue(QuickDownloadChoices.endedLiveTracks(choices).isEmpty())
        assertTrue(FinishedStreamSizes.isPlaylist("application/x-mpegURL"))
        assertFalse(FinishedStreamSizes.isPlaylist("video/mp4"))
    }

    @Test
    fun aRowShowsItsSampledSizeAsARoughEstimate() {
        val choices = choices(peakOnly())
        val p720 = "https://cdn.example.test/hls/index-720.m3u8"
        val p480 = "https://cdn.example.test/hls/index-480.m3u8"

        val reading = QuickDownloadChoices.withSampledSizes(
            choices,
            mapOf(p720 to size(1_945_380_000)),
            sampling = setOf(p480),
        )

        val row720 = reading.video.first { it.rankHeight == 720 }
        assertEquals("~1.8 GB", row720.size)
        assertEquals(listOf(QuickDownloadChoices.ROUGH_ESTIMATE), row720.chips)
        val row480 = reading.video.first { it.rankHeight == 480 }
        assertNull(row480.size)
        assertTrue(row480.sizePending)
        // A row whose sample gave nothing keeps "Size unknown", not pending.
        val row1080 = reading.video.first { it.rankHeight == 1080 }
        assertNull(row1080.size)
        assertFalse(row1080.sizePending)
    }

    @Test
    fun aQualityWithSeparateSoundCountsBothPlaylists() {
        val choices = choices(peakOnly(separateSound = true))
        val p720 = "https://cdn.example.test/hls/index-720.m3u8"

        val tracks = QuickDownloadChoices.finishedStreamTracks(choices)
        assertEquals(1, tracks.count { it.sound && it.url == sound })

        val partly = QuickDownloadChoices.withSampledSizes(
            choices,
            mapOf(p720 to size(300 * QuickDownloadFixtures.MIB)),
            sampling = setOf(sound),
        )
        // Its sound is still being read: no size from the picture alone.
        assertNull(partly.video.first { it.rankHeight == 720 }.size)

        val done = QuickDownloadChoices.withSampledSizes(
            choices,
            mapOf(
                p720 to size(300 * QuickDownloadFixtures.MIB),
                sound to size(40 * QuickDownloadFixtures.MIB),
            ),
            sampling = emptySet(),
        )
        assertEquals("~340 MB", done.video.first { it.rankHeight == 720 }.size)
    }

    @Test
    fun aRowWithAnAverageRateOrAStatedSizeIsNotSampled() {
        // The master states AVERAGE-BANDWIDTH, so its rows have "~" sizes already; the MP4 is
        // stated.
        val sources = QuickDownloadFixtures.otherSite()
        val choices = QuickDownloadChoices.of(
            QuickDownloadFixtures.group(sources.map { it.candidate }),
            sources,
        )!!

        assertTrue(QuickDownloadChoices.finishedStreamTracks(choices).isEmpty())
    }
}
