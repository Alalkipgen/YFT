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
}
