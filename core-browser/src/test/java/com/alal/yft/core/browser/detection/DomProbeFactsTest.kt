package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.core.model.media.PageVideoFacts
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P28: the live page's facts and player setup, as the DOM probe sends them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DomProbeFactsTest {
    private val page = "https://tube.example.test/watch/77"
    private val parser = DomProbeResultParser()

    private fun answer(scripts: List<String> = emptyList()): String {
        val facts = JSONObject()
            .put(
                "meta",
                JSONObject()
                    .put("og:title", "Harbour lights at dusk")
                    .put("og:image", "https://img.example.test/v77/poster.jpg"),
            )
            .put("jsonLd", JSONArray().put("""{"@type":"VideoObject","duration":"PT16M24S"}"""))
            .put("documentTitle", "Harbour lights at dusk - Example Tube")
        val entries = JSONArray()
            .put(
                JSONObject().put("element", "facts").put("facts", facts)
                    .put("scripts", JSONArray(scripts)),
            )
            .put(
                JSONObject().put("url", "https://cdn.adnet.example.test/ad.mp4")
                    .put("element", "video"),
            )
        // WebView hands the script's string back JSON-quoted.
        return JSONObject.quote(entries.toString())
    }

    @Test
    fun theProbesFactsEntryGivesThePagesFacts() {
        assertEquals(
            PageVideoFacts(
                durationMillis = 984_000,
                title = "Harbour lights at dusk",
                thumbnailUrl = "https://img.example.test/v77/poster.jpg",
            ),
            parser.facts(page, answer()),
        )
        assertNull(parser.facts(page, """[{"url":"https://cdn.example.test/a.mp4"}]"""))
        assertNull(parser.facts(page, null))
    }

    @Test
    fun thePlayersSetupBecomesThePagesVideoWithItsQualities() {
        val setup = """
            jwplayer("p").setup({sources:[
              {file:"https://cdn.example.test/v77/480.mp4",label:"480p"},
              {file:"https://cdn.example.test/v77/720.mp4",label:"720p"}]});
        """.trimIndent()

        val candidates = parser.parse(page, answer(listOf(setup)), observedAtEpochMs = 5)

        val videos = MediaGroups.pageVideos(candidates)
        assertEquals(2, videos.size)
        val own = videos.first()
        assertEquals(listOf(480, 720), own.candidates.map { it.height })
        assertTrue(own.candidates.all { it.pageRole == PageMediaRole.MAIN })
        assertEquals(listOf("https://cdn.adnet.example.test/ad.mp4"), videos.last().candidates.map {
            it.mediaUrl
        })
    }

    @Test
    fun theProbeScriptReadsMetaAndJsonLdButNoPageText() {
        val script = DomMediaProbe.script

        assertTrue(script.contains("application/ld+json"))
        assertTrue(script.contains("og:video:duration"))
        assertTrue(script.contains("element: 'facts'"))
        assertTrue(!script.contains("innerText") && !script.contains("body.textContent"))
    }
}
