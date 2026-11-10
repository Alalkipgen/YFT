package com.alal.yft.core.download

import com.alal.yft.core.download.EndedLiveSizes.Outcome
import com.alal.yft.core.download.EndedLiveSizes.Size
import com.alal.yft.core.download.EndedLiveSizes.Track
import java.util.Locale

/**
 * Finished stream size sample: an HLS quality whose playlist states no average rate shows
 * "Size unknown", because its `BANDWIDTH` is a peak rate, not an average. When its playlist is
 * finished (`EXT-X-ENDLIST`), the sheet asks a spread sample of its pieces for their lengths
 * (one byte each) and shows the row "~X MB" with "Rough estimate", by itself, without a button.
 *
 * The files are [Track]s of the rows: a quality's playlist and its separate sound's. A size is
 * shown only: the download's size, space check and progress are not changed by it. On Roshy's
 * finished playlists (885 pieces each, 2026-11-08) 48 pieces gave 480p, 720p and 1080p within
 * 1.4% of the measured total of all their pieces.
 */
interface FinishedStreamSizes {
    /** Sizes for [tracks]; [onSize] hears each file as soon as its size is known. */
    suspend fun estimate(
        tracks: List<Track>,
        onSize: (String, Size) -> Unit = { _, _ -> },
    ): Outcome

    companion object {
        /** An HLS playlist's type, as the page or the master states it. */
        fun isPlaylist(mimeType: String?): Boolean =
            mimeType?.trim()?.lowercase(Locale.US) in PLAYLIST_TYPES

        private val PLAYLIST_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )

        /** No sample: every row keeps "Size unknown". */
        val None: FinishedStreamSizes = object : FinishedStreamSizes {
            override suspend fun estimate(
                tracks: List<Track>,
                onSize: (String, Size) -> Unit,
            ): Outcome = Outcome(emptyMap())
        }
    }
}
