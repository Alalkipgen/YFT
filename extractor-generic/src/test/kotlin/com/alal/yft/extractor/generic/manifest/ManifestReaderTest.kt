package com.alal.yft.extractor.generic.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lengths and pictures read from a manifest's text, so a page's video can be ranked. */
class ManifestReaderTest {
    private val masterUrl = "https://stream.example.test/v42/master.m3u8?token=fixture"

    @Test
    fun manifestFactsDoNotPrintPlaylistAddresses() {
        val facts = ManifestFacts(
            firstPlaylistUrl = "https://private.example.test/path/index.m3u8?token=fixture",
            variantPlaylistUrls = listOf("https://private.example.test/other.m3u8?sig=fixture"),
        )
        val text = facts.toString()
        assertFalse(text.contains("private.example.test"))
        assertFalse(text.contains("fixture"))
    }

    @Test
    fun aLivePlaylistNamesItsPiecesAndHasNoLength() {
        // A live widget's fMP4 playlist: an init file and opaque piece names, no end.
        val live = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:9120
            #EXT-X-MAP:URI="init_240p.mp4"
            #EXTINF:2.000,
            Zx81Qa.mp4?sig=fixture
            #EXTINF:2.000,
            https://cam.example.test/b-hls-03/41/Pq72Lm.mp4
        """.trimIndent()

        val facts = ManifestReader.hls(live, "https://cam.example.test/b-hls-03/41/41_240p.m3u8")!!

        assertTrue(facts.live)
        assertNull(facts.durationMillis)
        assertEquals(
            listOf(
                "https://cam.example.test/b-hls-03/41/init_240p.mp4",
                "https://cam.example.test/b-hls-03/41/Zx81Qa.mp4?sig=fixture",
                "https://cam.example.test/b-hls-03/41/Pq72Lm.mp4",
            ),
            facts.pieceUrls,
        )
        assertFalse(facts.toString().contains("fixture"))
        assertTrue(facts.toString().contains("pieceCount=3"))
    }

    @Test
    fun aFinishedPlaylistIsNotLiveAndAMasterNamesNoPieces() {
        val vod = "#EXTM3U\n#EXTINF:6,\nseg-1.ts\n#EXTINF:4,\nseg-2.ts\n#EXT-X-ENDLIST"
        val facts = ManifestReader.hls(vod, "https://cdn.example.test/v/9/index.m3u8")!!

        assertFalse(facts.live)
        assertEquals(10_000L, facts.durationMillis)
        assertEquals(2, facts.pieceUrls.size)
        val master = ManifestReader.hls("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\na.m3u8", masterUrl)!!
        assertFalse(master.live)
        assertTrue(master.pieceUrls.isEmpty())
    }

    @Test
    fun anHlsMasterNamesItsTallestPictureAndTheFirstVideoPlaylist() {
        val master = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=96000,CODECS="mp4a.40.2"
            audio/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            360p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            https://cdn.example.test/v42/720p/index.m3u8
        """.trimIndent()

        val facts = ManifestReader.hls("\uFEFF" + master, masterUrl)!!

        assertTrue(facts.master)
        assertNull(facts.durationMillis)
        assertEquals(1920, facts.width)
        assertEquals(1080, facts.height)
        assertEquals("https://stream.example.test/v42/360p/index.m3u8", facts.firstPlaylistUrl)
    }

    @Test
    fun aFinishedMediaPlaylistAddsItsPiecesAndALiveOneHasNoLength() {
        val pieces = (1..125).joinToString("\n") { "#EXTINF:6.0,\npiece$it.ts" }
        val finished = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n$pieces\n#EXTINF:4.5,\nlast.ts\n" +
            "#EXT-X-ENDLIST"
        assertEquals(754_500L, ManifestReader.hls(finished, masterUrl)!!.durationMillis)

        val vod = "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:9.009,\na.ts\n#EXTINF:3,\nb.ts"
        assertEquals(12_009L, ManifestReader.hls(vod, masterUrl)!!.durationMillis)

        val live = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:7\n#EXTINF:6.0,\na.ts\n#EXTINF:6.0,\nb.ts"
        assertNull(ManifestReader.hls(live, masterUrl)!!.durationMillis)
        assertNull(ManifestReader.hls("<html>not a playlist</html>", masterUrl))
    }

    @Test
    fun aDashManifestStatesItsLengthUnlessItIsLive() {
        val mpd = """
            <?xml version="1.0"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
                 mediaPresentationDuration="PT12M34.5S" minBufferTime="PT2S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="1" width="854" height="480" bandwidth="900000"/>
                  <Representation id="2" width="1280" height="720" bandwidth="2000000"/>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="3" bandwidth="128000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val facts = ManifestReader.dash(mpd)!!
        assertEquals(754_500L, facts.durationMillis)
        assertEquals(1280, facts.width)
        assertEquals(720, facts.height)

        val live = mpd.replace("type=\"static\"", "type=\"dynamic\"")
        assertNull(ManifestReader.dash(live)!!.durationMillis)
        assertNull(ManifestReader.dash("#EXTM3U"))
    }

    @Test
    fun isoLengthsAndPlainSecondsAreRead() {
        assertEquals(3_723_000L, ManifestReader.isoDurationMillis("PT1H2M3S"))
        assertEquals(754_000L, ManifestReader.isoDurationMillis("PT12M34S"))
        assertEquals(86_401_000L, ManifestReader.isoDurationMillis("P1DT1S"))
        assertEquals(754_000L, ManifestReader.isoDurationMillis("754"))
        assertEquals(29_500L, ManifestReader.isoDurationMillis(" pt29.5s "))
        assertNull(ManifestReader.isoDurationMillis("PT0S"))
        assertNull(ManifestReader.isoDurationMillis("twelve minutes"))
        assertNull(ManifestReader.isoDurationMillis(null))
    }
}
