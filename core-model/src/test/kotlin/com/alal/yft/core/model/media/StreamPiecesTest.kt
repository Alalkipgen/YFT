package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPiecesTest {
    private val page = "https://page.test/watch/1"

    @Test
    fun aLiveStreamsNewerPiecesInItsFolderAreItsPiecesNotVideos() {
        // A live widget's playlist names three fMP4 pieces with opaque names; the player keeps
        // fetching newer ones in the same folder.
        val live = StreamPieces.of(
            live = true,
            pieceUrls = listOf(
                "https://cam.test/b-hls-12/77/aaaa1111.mp4?sig=1",
                "https://cam.test/b-hls-12/77/bbbb2222.mp4?sig=2",
            ),
        )!!

        assertTrue(live.names("https://cam.test/b-hls-12/77/aaaa1111.mp4?sig=9"))
        assertTrue(live.names("https://cam.test/b-hls-12/77/cccc3333.mp4"))
        assertFalse(live.names("https://cam.test/b-hls-12/78/cccc3333.mp4"))
        assertFalse(live.names("https://cam.test/b-hls-12/77/cccc3333.webm"))
        assertFalse(live.names("https://other.test/b-hls-12/77/cccc3333.mp4"))
        assertEquals("StreamPieces(live=true, pieces=2)", live.toString())
    }

    @Test
    fun aFinishedStreamOnlyClaimsTheFilesItNamesAndPieceOnlyTypes() {
        val vod = StreamPieces.of(
            live = false,
            pieceUrls = listOf(
                "https://cdn.test/v/9/init.mp4",
                "https://cdn.test/v/9/part1.mp4",
                "https://cdn.test/v/9/seg-1.ts",
            ),
        )!!

        assertTrue(vod.names("https://cdn.test/v/9/part1.mp4?t=1"))
        assertTrue(vod.names("https://cdn.test/v/9/init.mp4"))
        // A whole MP4 next to a finished stream's pieces may be the page's other file.
        assertFalse(vod.names("https://cdn.test/v/9/720p.mp4"))
        // A .ts file in a pieces' folder is a piece.
        assertTrue(vod.names("https://cdn.test/v/9/seg-99.ts"))
    }

    @Test
    fun aFileAnythingNamesAsAVideoIsNeverAPiece() {
        val live = StreamPieces.of(true, listOf("https://cam.test/l/1/a.mp4"))!!
        val streams = listOf(live)
        val piece = direct("https://cam.test/l/1/b.mp4")

        assertTrue(StreamPieces.isPiece(piece, streams))
        assertFalse(StreamPieces.isPiece(piece, emptyList()))
        assertFalse(StreamPieces.isPiece(piece.copy(videoId = "v1"), streams))
        assertFalse(StreamPieces.isPiece(piece.copy(pageVideoKey = "720p"), streams))
        assertFalse(StreamPieces.isPiece(piece.copy(pageRole = PageMediaRole.MAIN), streams))
        assertFalse(StreamPieces.isPiece(piece.copy(sources = setOf(CandidateSource.DOM)), streams))
        assertFalse(
            StreamPieces.isPiece(
                piece.copy(sources = setOf(CandidateSource.REQUEST, CandidateSource.DOWNLOAD_LISTENER)),
                streams,
            ),
        )
        assertFalse(StreamPieces.isPiece(piece.copy(kind = MediaKind.HLS), streams))
    }

    @Test
    fun aStreamIsLiveOnlyWhenItsReadShowsNoEndAndNothingStatesALength() {
        val live = StreamPieces.of(true, listOf("https://cam.test/l/1/a.mp4"))
        val ended = StreamPieces.of(false, listOf("https://cdn.test/v/1/a.ts"))
        val stream = direct("https://cam.test/l/1/p.m3u8").copy(kind = MediaKind.HLS)

        assertNull(StreamPieces.of(true, emptyList()))
        assertTrue(StreamPieces.isLive(group(stream.copy(streamPieces = live))))
        assertFalse(StreamPieces.isLive(group(stream)))
        assertFalse(StreamPieces.isLive(group(stream.copy(streamPieces = live, durationMillis = 60_000))))
        assertFalse(StreamPieces.isLive(group(stream.copy(streamPieces = live, videoId = "yt"))))
        assertFalse(
            StreamPieces.isLive(
                group(stream.copy(streamPieces = live), stream.copy(mediaUrl = "https://cdn.test/v/1/p.m3u8", streamPieces = ended)),
            ),
        )
    }

    private fun direct(url: String) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
    )

    private fun group(vararg files: MediaCandidate) = MediaGroup(key = "g", title = null, candidates = files.toList())
}
