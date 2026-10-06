package com.alal.yft.extractor.sites.facebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P4: Facebook's inline DASH manifest read as whole-file tracks. */
class FacebookDashManifestTest {
    @Test
    fun `every representation is a whole file with its picture, codec and bitrate`() {
        val manifest = FacebookDashManifests.parse(
            mpd(
                video(
                    "avc1.64001f", 1108, 720, 2_749_477,
                    "https://video.example-cdn.test/v/avc-720.mp4?oe=F2A52380&amp;oh=fixture",
                ) + video(
                    "avc1.4d001e", 552, 358, 755_648,
                    "https://video.example-cdn.test/v/avc-360.mp4?oe=F2A52380&amp;oh=fixture",
                ),
                audio("https://video.example-cdn.test/v/audio.mp4?oe=F2A52380&amp;oh=fixture"),
            ),
        )!!

        assertEquals(625_452L, manifest.durationMillis)
        assertEquals(
            listOf(
                FacebookDashTrack(
                    url = "https://video.example-cdn.test/v/avc-720.mp4?oe=F2A52380&oh=fixture",
                    kind = FacebookTrackKind.VIDEO,
                    mimeType = "video/mp4",
                    codec = "avc1.64001f",
                    width = 1108,
                    height = 720,
                    framesPerSecond = 30.0,
                    bandwidthBitsPerSecond = 2_749_477,
                    representationId = "v720",
                ),
                FacebookDashTrack(
                    url = "https://video.example-cdn.test/v/avc-360.mp4?oe=F2A52380&oh=fixture",
                    kind = FacebookTrackKind.VIDEO,
                    mimeType = "video/mp4",
                    codec = "avc1.4d001e",
                    width = 552,
                    height = 358,
                    framesPerSecond = 30.0,
                    bandwidthBitsPerSecond = 755_648,
                    representationId = "v358",
                ),
                FacebookDashTrack(
                    url = "https://video.example-cdn.test/v/audio.mp4?oe=F2A52380&oh=fixture",
                    kind = FacebookTrackKind.AUDIO,
                    mimeType = "audio/mp4",
                    codec = "mp4a.40.5",
                    width = null,
                    height = null,
                    framesPerSecond = null,
                    bandwidthBitsPerSecond = 57_372,
                    representationId = "a",
                ),
            ),
            manifest.tracks,
        )
    }

    @Test
    fun `segment lists, templates, relative addresses and unnamed codecs are no whole file`() {
        val manifest = FacebookDashManifests.parse(
            mpd(
                """
                <Representation id="t" bandwidth="1" codecs="avc1.64001f" mimeType="video/mp4"
                    width="1280" height="720"><BaseURL>https://cdn.test/t.mp4</BaseURL>
                  <SegmentTemplate media="t-${'$'}Number${'$'}.m4s"/></Representation>
                <Representation id="l" bandwidth="1" codecs="avc1.64001f" mimeType="video/mp4"
                    width="1280" height="720"><BaseURL>https://cdn.test/l.mp4</BaseURL>
                  <SegmentList><SegmentURL media="l-1.m4s"/></SegmentList></Representation>
                <Representation id="r" bandwidth="1" codecs="avc1.64001f" mimeType="video/mp4"
                    width="1280" height="720"><BaseURL>video/r.mp4</BaseURL></Representation>
                <Representation id="h" bandwidth="1" codecs="avc1.64001f" mimeType="video/mp4"
                    width="1280" height="720"><BaseURL>http://cdn.test/h.mp4</BaseURL>
                </Representation>
                <Representation id="m" bandwidth="1" codecs="avc1.64001f,mp4a.40.2"
                    mimeType="video/mp4" width="1280" height="720">
                  <BaseURL>https://cdn.test/m.mp4</BaseURL></Representation>
                <Representation id="n" bandwidth="1" mimeType="video/mp4" width="1280"
                    height="720"><BaseURL>https://cdn.test/n.mp4</BaseURL></Representation>
                <Representation id="s" bandwidth="1" codecs="avc1.64001f" mimeType="video/mp4">
                  <BaseURL>https://cdn.test/s.mp4</BaseURL></Representation>
                <Representation id="w" bandwidth="1" codecs="vp9" mimeType="video/webm"
                    width="1280" height="720"><BaseURL>https://cdn.test/w.webm</BaseURL>
                </Representation>
                """,
                audio("https://cdn.test/a.mp4"),
            ),
        )!!

        assertEquals(listOf("https://cdn.test/a.mp4"), manifest.tracks.map { it.url })
    }

    @Test
    fun `a protected, live or foreign document yields no tracks`() {
        val protected = mpd(
            "<ContentProtection schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\"/>" +
                video("avc1.64001f", 1280, 720, 1, "https://cdn.test/v.mp4"),
            audio("https://cdn.test/a.mp4"),
        )
        val live = mpd(video("avc1.64001f", 1280, 720, 1, "https://cdn.test/v.mp4"), "")
            .replace("type=\"static\"", "type=\"dynamic\"")

        assertNull(FacebookDashManifests.parse(protected))
        assertNull(FacebookDashManifests.parse(live))
        assertNull(FacebookDashManifests.parse("<html><body>no manifest</body></html>"))
        assertNull(FacebookDashManifests.parse("not xml"))
        assertNull(
            FacebookDashManifests.parse(
                "<?xml version=\"1.0\"?>" +
                    "<!DOCTYPE MPD [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
                    "<MPD><Period><AdaptationSet><Representation mimeType=\"audio/mp4\" " +
                    "codecs=\"mp4a.40.2\"><BaseURL>https://cdn.test/&x;</BaseURL>" +
                    "</Representation></AdaptationSet></Period></MPD>",
            ),
        )
        assertNull(FacebookDashManifests.parse("<MPD>" + " ".repeat(262_144) + "</MPD>"))
    }

    @Test
    fun `an older URL-encoded manifest and an hour-long duration are read`() {
        val encoded = java.net.URLEncoder.encode(
            mpd("", audio("https://cdn.test/a.mp4?x=1&amp;y=2"))
                .replace("PT625.452698S", "PT1H2M3.5S"),
            "UTF-8",
        )

        val manifest = FacebookDashManifests.parse(encoded)!!

        assertEquals(3_723_500L, manifest.durationMillis)
        assertEquals(listOf("https://cdn.test/a.mp4?x=1&y=2"), manifest.tracks.map { it.url })
    }

    @Test
    fun `a ladder keeps its best bitrate per picture, AVC before AV1, and AAC for the sound`() {
        val tracks = listOf(
            track("av1-720-low", "av01.0.05M.08", 1108, 720, 160_133),
            track("av1-720-top", "av01.0.05M.08", 1108, 720, 917_000),
            track("av1-1080", "av01.0.08M.08", 1660, 1078, 1_340_000),
            track("vp9-1080", "vp09.00.40.08", 1920, 1080, 2_000_000),
            track("avc-720", "avc1.64001f", 1108, 720, 700_000),
            track("avc-360", "avc1.4d001e", 552, 358, 755_648),
            FacebookDashTrack(
                "https://cdn.test/opus.mp4", FacebookTrackKind.AUDIO, "audio/mp4", "opus",
                null, null, null, 128_000,
            ),
            FacebookDashTrack(
                "https://cdn.test/aac-low.mp4", FacebookTrackKind.AUDIO, "audio/mp4",
                "mp4a.40.5", null, null, null, 32_000,
            ),
            FacebookDashTrack(
                "https://cdn.test/aac.mp4", FacebookTrackKind.AUDIO, "audio/mp4", "mp4a.40.5",
                null, null, null, 57_372,
            ),
        )

        val videos = FacebookDashOffers.videos(tracks)

        assertEquals(
            listOf(
                "https://cdn.test/av1-1080.mp4",
                "https://cdn.test/avc-720.mp4",
                "https://cdn.test/avc-360.mp4",
            ),
            videos.map { it.url },
        )
        assertEquals(listOf("1080p", "720p", "360p"), videos.map(FacebookDashOffers::qualityName))
        assertEquals("https://cdn.test/aac.mp4", FacebookDashOffers.audio(tracks)?.url)
        val avcAt720 = tracks.filterNot { (it.height ?: 0) > 1_000 }
        assertEquals(true, FacebookDashOffers.needsAvcLadder(tracks.take(4)))
        assertEquals(true, FacebookDashOffers.needsAvcLadder(tracks))
        assertEquals(true, FacebookDashOffers.needsAvcLadder(tracks.drop(6)))
        assertEquals(false, FacebookDashOffers.needsAvcLadder(avcAt720))
        assertEquals(true, FacebookDashOffers.needsAvcLadder(avcAt720, listOf(1_080)))
        assertEquals(true, FacebookDashOffers.hasAvcWithSound(tracks))
        assertEquals(false, FacebookDashOffers.hasAvcWithSound(tracks.take(4)))
        assertEquals("AVC 720", FacebookDashOffers.bestAvc(tracks))
        assertEquals("no AVC", FacebookDashOffers.bestAvc(tracks.take(4)))
    }

    @Test
    fun `at most six picture sizes are offered, tallest first`() {
        val tracks = (1..9).map { step ->
            track("avc-$step", "avc1.64001f", 160 * step, 90 * step, 100_000L * step)
        }

        val videos = FacebookDashOffers.videos(tracks)

        assertEquals(FacebookDashOffers.MAX_VIDEO_OFFERS, videos.size)
        assertEquals(810, videos.first().height)
    }

    private fun track(name: String, codec: String, width: Int, height: Int, bandwidth: Long) =
        FacebookDashTrack(
            "https://cdn.test/$name.mp4", FacebookTrackKind.VIDEO, "video/mp4", codec, width,
            height, 30.0, bandwidth,
        )

    private fun mpd(videos: String, audio: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
            profiles="urn:mpeg:dash:profile:isoff-on-demand:2011"
            mediaPresentationDuration="PT625.452698S">
          <Period id="0">
            <AdaptationSet id="0" contentType="video" frameRate="15360/512">$videos</AdaptationSet>
            <AdaptationSet id="1" contentType="audio">$audio</AdaptationSet>
          </Period>
        </MPD>"""

    private fun video(codec: String, width: Int, height: Int, bandwidth: Long, url: String) =
        """<Representation id="v$height" bandwidth="$bandwidth" codecs="$codec"
            mimeType="video/mp4" width="$width" height="$height"><BaseURL>$url</BaseURL>
          <SegmentBase indexRange="805-2348"><Initialization range="0-804"/></SegmentBase>
        </Representation>"""

    private fun audio(url: String) =
        """<Representation id="a" bandwidth="57372" codecs="mp4a.40.5" mimeType="audio/mp4">
          <BaseURL>$url</BaseURL>
          <SegmentBase indexRange="824-4611"><Initialization range="0-823"/></SegmentBase>
        </Representation>"""
}
