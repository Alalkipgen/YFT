package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DomMediaProbeTest {
    @Test
    fun scriptIsReadOnlyAndTargetsMediaSourcesAndMetadata() {
        val script = DomMediaProbe.script

        assertTrue(script.contains("video, audio"))
        assertTrue(script.contains("video source, audio source"))
        assertTrue(script.contains("og:video"))
        assertTrue(script.contains("JSON.stringify"))
        listOf("document.write", "appendChild", "fetch(", "XMLHttpRequest").forEach { mutation ->
            assertFalse(script.contains(mutation))
        }
    }

    @Test
    fun parserMapsFixtureEntriesAndSkipsLiteralBlob() {
        val fixture = JSONArray()
            .put(
                JSONObject()
                    .put("url", "https://cdn.test/movie.mp4")
                    .put("type", "video/mp4")
                    .put("title", "Fixture page")
                    .put("poster", "https://cdn.test/poster.jpg")
                    .put("duration", 12.5),
            )
            .put(
                JSONObject()
                    .put("url", "https://cdn.test/master.m3u8")
                    .put("type", "application/vnd.apple.mpegurl"),
            )
            .put(JSONObject().put("url", "blob:https://example.test/id"))
        val javascriptResult = JSONObject.quote(fixture.toString())

        val candidates = DomProbeResultParser().parse(
            pageUrl = "https://example.test/watch",
            javascriptResult = javascriptResult,
            observedAtEpochMs = 100,
        )

        assertEquals(2, candidates.size)
        assertEquals(setOf(MediaKind.DIRECT, MediaKind.HLS), candidates.map { it.kind }.toSet())
        assertEquals(12_500L, candidates.first { it.kind == MediaKind.DIRECT }.durationMillis)
    }

    @Test
    fun parserMarksPreviewsAndThePagesNamedStream() {
        // Muted loops and thumbnails' clips are previews; an og:video file is the video.
        fun entry(url: String) = JSONObject().put("url", url).put("type", "video/mp4")
        val fixture = JSONArray()
            .put(entry("https://cdn.test/p/1.mp4").put("muted", true).put("loop", true))
            .put(entry("https://cdn.test/p/2.mp4").put("thumbnail", true))
            .put(entry("https://cdn.test/v/main.mp4").put("element", "meta"))
            .put(entry("https://cdn.test/v/muted.mp4").put("muted", true).put("element", "video"))

        val candidates = DomProbeResultParser().parse(
            pageUrl = "https://example.test/watch",
            javascriptResult = fixture.toString(),
            observedAtEpochMs = 1,
        )

        assertEquals(
            listOf(PageMediaRole.PREVIEW, PageMediaRole.PREVIEW, PageMediaRole.MAIN, null),
            candidates.map { it.pageRole },
        )
        listOf("muted", "loop", "thumbnail").forEach { flag ->
            assertTrue(DomMediaProbe.script.contains(flag))
        }
    }

    @Test
    fun parserGroupsOnePlayersFilesAndReadsThePictureThePageStates() {
        // The owner's Commons page as the probe reports it: one element's three <source>
        // children, each with the picture the page states for it.
        fun source(url: String, width: Int, height: Int) = JSONObject()
            .put("url", url)
            .put("type", "video/webm; codecs=\"vp9, opus\"")
            .put("element", "source")
            .put("group", "element-0")
            .put("width", width)
            .put("height", height)
        val fixture = JSONArray()
            .put(source("https://cdn.test/clip.480p.webm", 854, 480))
            .put(source("https://cdn.test/clip.1080p.webm", 1920, 1080))
            .put(source("https://cdn.test/clip.240p.webm", 426, 240))

        val candidates = DomProbeResultParser().parse(
            pageUrl = "https://example.test/File:Clip.webm",
            javascriptResult = fixture.toString(),
            observedAtEpochMs = 1,
        )

        assertEquals(listOf(480, 1080, 240), candidates.map { it.height })
        assertEquals(listOf(854, 1920, 426), candidates.map { it.width })
        // One video: the sheet lists them as its qualities, not as three videos.
        assertEquals(setOf("element-0"), candidates.mapNotNull { it.pageVideoKey }.toSet())
        assertEquals(1, MediaGroups.of(candidates).size)
        assertTrue(DomMediaProbe.script.contains("data-height"))
        // An element with no <source> children groups nothing: its own file is its own (on a
        // page whose player setup names the qualities, that file can be the ad).
        assertTrue(DomMediaProbe.script.contains("querySelector('source')"))
    }

    @Test
    fun parserKeepsTwoPlayersFilesApart() {
        fun entry(url: String, group: String) = JSONObject()
            .put("url", url)
            .put("type", "video/mp4")
            .put("group", group)
        val fixture = JSONArray()
            .put(entry("https://cdn.test/a.mp4", "element-0"))
            .put(entry("https://cdn.test/b.mp4", "element-1"))

        val candidates = DomProbeResultParser().parse(
            pageUrl = "https://example.test/watch",
            javascriptResult = fixture.toString(),
            observedAtEpochMs = 1,
        )

        assertEquals(listOf("element-0", "element-1"), candidates.map { it.pageVideoKey })
        assertEquals(2, MediaGroups.of(candidates).size)
    }

    @Test
    fun parserBoundsAndToleratesMalformedResults() {
        val fixture = JSONArray()
            .put(JSONObject().put("url", "https://cdn.test/1.mp4"))
            .put(JSONObject().put("url", "https://cdn.test/2.mp4"))

        assertEquals(
            1,
            DomProbeResultParser(maxEntries = 1).parse("https://example.test", fixture.toString(), 1).size,
        )
        assertTrue(DomProbeResultParser().parse("https://example.test", "not-json", 1).isEmpty())
    }
}
