package com.alal.yft.core.browser.detection

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayingVideoProbeTest {
    @Test
    fun thePlayingVideosHttpsAddressIsReadFromTheScriptsAnswer() {
        val url = "https://cdn.example.test/clip.mp4?part=1"

        assertEquals(url, PlayingVideoProbe.parse(JSONObject.quote(url)))
        assertEquals(url, PlayingVideoProbe.parse(url))
        assertTrue(PlayingVideoProbe.script.contains("paused"))
    }

    @Test
    fun noPlayingVideoOrAnAddressThatNamesNoFileIsNull() {
        assertNull(PlayingVideoProbe.parse(null))
        assertNull(PlayingVideoProbe.parse("null"))
        assertNull(PlayingVideoProbe.parse(""))
        assertNull(PlayingVideoProbe.parse(JSONObject.quote("blob:https://example.test/1")))
        assertNull(PlayingVideoProbe.parse(JSONObject.quote("http://cdn.example.test/a.mp4")))
        assertNull(PlayingVideoProbe.parse(JSONObject.quote("https://")))
        assertNull(PlayingVideoProbe.parse("\"" + "a".repeat(5_000) + "\""))
    }

    /** P24: the script's answer as WebView hands it over: a JSON string of the page's videos. */
    private fun answer(vararg videos: JSONObject): String = JSONObject.quote(
        JSONObject()
            .put("now", 1_700_000_100_000L)
            .put("videos", org.json.JSONArray(videos.toList()))
            .toString(),
    )

    private fun element(
        src: String,
        duration: Double? = 754.4,
        time: Double = 30.0,
        area: Double = 360_000.0,
        muted: Boolean = false,
        loop: Boolean = false,
        playing: Boolean = true,
        framed: Boolean = false,
        thumbnail: Boolean = false,
    ): JSONObject = JSONObject()
        .put("src", src)
        .put("playing", playing)
        .put("duration", duration ?: JSONObject.NULL)
        .put("time", time)
        .put("width", 1920)
        .put("height", 1080)
        .put("area", area)
        .put("muted", muted)
        .put("loop", loop)
        .put("autoplay", muted)
        .put("framed", framed)
        .put("thumbnail", thumbnail)

    @Test
    fun aPageBuiltPlayerIsReportedWithItsLengthPictureAndStartTime() {
        val result = answer(
            element("https://media.example.test/clips/7.mp4", 29.0, area = 40_000.0,
                muted = true, loop = true, thumbnail = true),
            element("blob:https://videos.example.test/5b1c", 754.4),
        )

        val player = PlayingVideoProbe.playing(result)!!

        assertNull(player.url)
        assertTrue(player.pageBuilt)
        assertEquals(754_400L, player.durationMillis)
        assertEquals(1_700_000_070_000L, player.startedAtEpochMs)
        assertEquals(1080, player.height)
        assertEquals(false, player.looksLikePreview)
        // The old answer keeps working: a page-built stream names no address.
        assertNull(PlayingVideoProbe.parse(result))
    }

    @Test
    fun aMutedLoopingThumbnailClipIsNotTakenWhileThePagesOwnVideoPlays() {
        val clip = "https://media.example.test/clips/7.mp4"
        val main = "https://stream.example.test/v42/main.mp4"
        val both = answer(
            element(clip, 29.0, area = 900_000.0, muted = true, loop = true),
            element(main, 754.0, area = 200_000.0),
        )
        assertEquals(main, PlayingVideoProbe.parse(both))

        // Of two players: the playing one, then the page's own before a frame's, then the larger.
        val paused = answer(element(clip, playing = false), element(main))
        assertEquals(main, PlayingVideoProbe.parse(paused))
        val framed = answer(element(clip, framed = true, area = 900_000.0), element(main))
        assertEquals(main, PlayingVideoProbe.parse(framed))
        val larger = answer(element(clip, area = 10.0), element(main, area = 20.0))
        assertEquals(main, PlayingVideoProbe.parse(larger))

        // Only a preview plays: it is reported, marked, and the sheet ranks the page instead.
        val onlyPreview = PlayingVideoProbe.playing(answer(element(clip, thumbnail = true)))!!
        assertEquals(clip, onlyPreview.url)
        assertTrue(onlyPreview.looksLikePreview)
        assertNull(PlayingVideoProbe.playing(answer()))
        assertNull(PlayingVideoProbe.playing(answer(element("http://cdn.example.test/a.mp4"))))
        // A live stream has no length.
        assertNull(PlayingVideoProbe.playing(answer(element(main, duration = null)))!!
            .durationMillis)
    }
}
