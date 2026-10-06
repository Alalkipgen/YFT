package com.alal.yft.extractor.generic.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P24: lengths and pictures read from a manifest's text, so a page's video can be ranked. */
class ManifestReaderTest {
    private val masterUrl = "https://stream.example.test/v42/master.m3u8?token=fixture"

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
