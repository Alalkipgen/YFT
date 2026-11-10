package com.alal.yft.core.download

import com.alal.yft.core.model.media.BrowserRequestContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Ended live size sample (the owner's choice, 2026-11-08): a recently ended YouTube live states
 * no file sizes, and its format rates are peak rates (144p came out at a third of its stated
 * rate). So the sheet reads the sizes of a sample of the replay's own pieces and shows each row
 * "~63 MB" with "Rough estimate", by itself, without a button.
 *
 * Only the start of each sampled piece is fetched (a range of [EndedLiveSampler.Policy.headBytes]):
 * a piece is one fragmented MP4 (`ftyp`, `moov`, `emsg`, `moof`, `mdat`), and its boxes' sizes
 * say how long it is. On the owner's replay (545 pieces) 189 asks read 1.5 MB, and 144p came to
 * ~63.4 MiB, as its download did (63 MB).
 */
interface EndedLiveSizes {
    /** One file of the sheet's rows: a video quality or its sound. [url] is never logged. */
    class Track(
        val url: String,
        val requestContext: BrowserRequestContext,
        val sound: Boolean,
        /** The rate the source states; it only orders the files, it is never a size. */
        val bitrateBitsPerSecond: Long? = null,
        /** A WebM file's pieces are not MP4 boxes: it is not sampled. */
        val mimeType: String? = null,
    ) {
        override fun toString(): String =
            "Track(url=[REDACTED], sound=$sound, bitrateBitsPerSecond=$bitrateBitsPerSecond, " +
                "mimeType=$mimeType)"
    }

    /**
     * About [bytes] once its pieces are joined, likely ([LIKELY_PERCENT] in 100) between
     * [lowBytes] and [highBytes], from [piecesRead] of its [pieces].
     */
    data class Size(
        val bytes: Long,
        val lowBytes: Long,
        val highBytes: Long,
        val piecesRead: Int,
        val pieces: Int,
    ) {
        init {
            require(bytes > 0 && lowBytes in 1..bytes && highBytes >= bytes)
            require(piecesRead in 1..pieces)
        }
    }

    /**
     * The files sized ([sizes], by their [Track.url]); [asks] and [bytesRead] are what the sample
     * cost; [note] says why a file has no size, without addresses.
     */
    data class Outcome(
        val sizes: Map<String, Size>,
        val asks: Int = 0,
        val bytesRead: Long = 0,
        val note: String? = null,
    ) {
        override fun toString(): String =
            "Outcome(sized=${sizes.size}, asks=$asks, bytesRead=$bytesRead, note=$note)"
    }

    /**
     * Sizes for [tracks]; [onSize] hears each file as soon as its size is known. Files that are
     * not a live broadcast's ([isLiveFile]), and WebM files, are left out.
     */
    suspend fun estimate(
        tracks: List<Track>,
        onSize: (String, Size) -> Unit = { _, _ -> },
    ): Outcome

    companion object {
        const val LIKELY_PERCENT = 90

        /** A live broadcast's YouTube file, on air or recently ended ([LiveOriginFile]). */
        fun isLiveFile(url: String): Boolean =
            url.toHttpUrlOrNull()?.let(LiveOriginFile::matches) == true

        /** No sample: every row keeps "Size unknown". */
        val None: EndedLiveSizes = object : EndedLiveSizes {
            override suspend fun estimate(
                tracks: List<Track>,
                onSize: (String, Size) -> Unit,
            ): Outcome = Outcome(emptyMap())
        }
    }
}
