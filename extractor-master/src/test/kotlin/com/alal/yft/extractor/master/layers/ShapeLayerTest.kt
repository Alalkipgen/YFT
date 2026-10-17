package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.toolkit.MediaKeyTable
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import com.alal.yft.extractor.master.verify.CandidateGate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Phase 1 R3: L3 shape search with content-ID anchoring (MASTER_KEY_PHASE1_PLAN.md §2 R3). */
class ShapeLayerTest {
    private val siteFixtures = System.getProperty("yft.siteFixtures")?.let(::File)

    @Test
    fun `shape results include every fixed-key result on all committed fixtures`() {
        val problems = fixtures().mapNotNull { (name, page, body) ->
            val missing = urls(RecipeLayer(), page, body) - urls(ShapeLayer(), page, body)
            missing.takeIf { it.isNotEmpty() }?.let { "$name lacks ${it.size}" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `renamed keys do not reduce hits`() {
        var renamedFixtures = 0
        val problems = fixtures().mapNotNull { (name, page, body) ->
            val before = urls(RecipeLayer(), page, body)
            if (before.isEmpty()) return@mapNotNull null
            val renamed = renameKnownKeys(body)
            if (renamed == body) return@mapNotNull null
            renamedFixtures += 1
            assertTrue(name, urls(RecipeLayer(), page, renamed).size < before.size)
            val missing = before - urls(ShapeLayer(), page, renamed)
            missing.takeIf { it.isNotEmpty() }?.let { "$name lacks ${it.size} after renaming" }
        }
        assertTrue("too few fixtures were renamed: $renamedFixtures", renamedFixtures >= 20)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the suggested or related video is rejected by anchoring`() {
        val html = shape("related-videos.html")
        val main = collect(PAGE, html = html, id = "111")
        assertEquals(
            setOf(
                "https://video.example-cdn.test/v/main-720.mp4?oe=7A000000",
                "https://video.example-cdn.test/v/main-360.mp4?oe=7A000000",
            ),
            main.candidates.map { it.mediaUrl }.toSet(),
        )
        assertTrue(main.candidates.all { it.pageRole == PageMediaRole.MAIN })
        assertTrue(main.details.single().contains("left out 2 other-video nodes"))
        assertEquals(
            listOf("https://video.example-cdn.test/v/other-720.mp4?oe=7A000000"),
            collect(PAGE, html = html, id = "222").candidates.map { it.mediaUrl },
        )

        val items = shape("related-items.json")
        val first = collect(TIKTOK, api = items, id = "7400000000000000001")
        assertEquals(
            listOf("https://v16-webapp.example-cdn.test/video/tos/main/?mime_type=video_mp4"),
            first.candidates.map { it.mediaUrl },
        )
        assertEquals(720, first.candidates.single().width)
        // Without an expected ID nothing L3 finds is named the page's main video.
        val open = collect(PAGE, html = html, id = null)
        assertEquals(4, open.candidates.size)
        assertTrue(open.candidates.none { it.pageRole == PageMediaRole.MAIN })
    }

    @Test
    fun `finders read scripts by type, assignments, JSON in strings and entity-encoded JSON`() {
        val found = collect("https://www.example.test/watch/555", html = shape("finders.html"), id = "555")
        assertEquals(
            setOf(
                "https://media.example-cdn.test/entity-360.mp4",
                "https://media.example-cdn.test/assigned-1080",
                "https://media.example-cdn.test/context-480.mp4",
                "https://media.example-cdn.test/attr-720.mp4",
            ),
            found.candidates.map { it.mediaUrl }.toSet(),
        )
        assertNull(found.terminalFailure)
        assertEquals(1080, found.candidates.single { it.mediaUrl.endsWith("1080") }.height)
        assertTrue(found.candidates.all { it.confidence == CandidateConfidence.MEDIUM })

        // Unanchored: images, ads and page links are never media; a hover loop is a preview.
        val open = collect("https://www.example.test/watch/555", html = shape("finders.html"))
        val urls = open.candidates.associate { it.mediaUrl.substringAfterLast('/') to it.pageRole }
        assertFalse(urls.keys.any { it.contains("thumb") || it.contains("still") || it == "ad.mp4" })
        assertFalse(urls.keys.any { it == "555" || it == "poster" })
        // A page link under a media-hinted key on the page's own host is not a file.
        val link = """{"video_id":"555","videoUrl":"https://www.example.test/video/555","width":1}"""
        assertTrue(collect("https://www.example.test/watch/555", api = link).candidates.isEmpty())
        assertEquals(PageMediaRole.PREVIEW, urls["hover-preview.mp4"])
    }

    @Test
    fun `a DRM statement or licence object stops L3, a past config expiry drops its files`() {
        val drm = """{"video":{"id":1},"request":{"files":{"drm":{"widevine":{}},""" +
            """"dash":{"cdns":{"a":{"url":"https://cdn.example.test/v/licensed.mpd"}}}}}}"""
        assertEquals(
            SiteExtractionFailure.DRM_PROTECTED,
            collect(PAGE, api = drm).terminalFailure,
        )
        val empty = drm.replace(""""drm":{"widevine":{}}""", """"drm":{}""")
        assertNull(collect(PAGE, api = empty).terminalFailure)

        val config = """{"request":{"expires":1699000000,"files":{"progressive":[""" +
            """{"height":720,"url":"https://cdn.example.test/v/old-720.mp4"}]}}}"""
        val old = collect(PAGE, api = config).candidates.single()
        assertEquals(1_699_000_000_000L, old.expiresAtEpochMs)
        assertFalse(CandidateGate.eligible(old, NOW))
    }

    @Test
    fun `all negative fixtures give 0 media`() {
        val normalizer = CandidateNormalizer(
            CandidateNormalizer.Policy(maxCandidates = 16, tinyDirectAssetBytes = 0),
        )
        val negatives = fixtures().filter { NEGATIVE.containsMatchIn(it.name) }
        assertTrue("negatives: ${negatives.size}", negatives.size >= 25)
        val problems = negatives.mapNotNull { (name, page, body) ->
            val snapshot = snapshotOf(name, page, body)
            val evidence = LayerStack.standard().collect(requestOf(page), snapshot)
            if (evidence.terminalFailure != null) return@mapNotNull null
            val offered = CandidateGate.admit(
                evidence.candidates, normalizer.normalize(page, evidence.candidates), NOW,
            )
            "$name offers ${offered.size}".takeIf { offered.isNotEmpty() }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `L3 only adds and earlier layers' rows reach the normalizer unchanged`() {
        fixtures().forEach { (name, page, body) ->
            val snapshot = snapshotOf(name, page, body)
            val before = LayerStack.withoutShape().collect(requestOf(page), snapshot)
            val after = LayerStack.standard().collect(requestOf(page), snapshot)
            assertEquals(name, before.candidates, after.candidates.take(before.candidates.size))
            assertEquals(name, before.terminalFailure ?: after.terminalFailure, after.terminalFailure)
            val added = after.candidates.drop(before.candidates.size)
            val earlier = before.candidates.map { UrlPolicy.whole(it.mediaUrl) }.toSet()
            assertTrue(name, added.none { UrlPolicy.whole(it.mediaUrl) in earlier })
        }
    }

    @Test
    fun `never on a YouTube host and never inside streamingData`() {
        val format = """{"url":"https://rr1.googlevideo.example.test/videoplayback.mp4",""" +
            """"mimeType":"video/mp4"}"""
        val body = """{"videoDetails":{"videoId":"yt"},"streamingData":{"formats":[$format]}}"""
        assertTrue(collect(PAGE, api = body, id = "yt").candidates.isEmpty())
        val open = """{"videoId":"yt","clip":$format}"""
        assertEquals(1, collect(PAGE, api = open, id = "yt").candidates.size)
        assertTrue(collect("https://www.youtube.com/watch?v=yt", api = open, id = "yt").candidates.isEmpty())
        assertTrue(collect("https://m.youtube.com/watch?v=yt", api = open).candidates.isEmpty())
    }

    // ---- helpers ----

    private data class Fixture(val name: String, val page: String, val body: String)

    private fun fixtures(): List<Fixture> {
        assumeTrue("site fixtures not supplied", siteFixtures?.isDirectory == true)
        val root = checkNotNull(siteFixtures)
        val main = root.walkTopDown().filter { it.isFile }.sortedBy { it.path }.map { file ->
            val site = file.parentFile.name
            Fixture("$site/${file.name}", PAGES[site] ?: "https://$site.example.test/f", file.readText())
        }.toList()
        val master = listOf("facebook-inline.json", "instagram-reel.json", "tiktok-reflow.html", "x-video.json")
            .map { Fixture("master/$it", PAGE, resource("/fixtures/$it")) }
        return main + master
    }

    private fun snapshotOf(name: String, page: String, body: String) =
        if (name.endsWith(".json")) {
            PageSnapshot(page, 1, apiResponses = listOf(body))
        } else {
            PageSnapshot(page, 1, html = body)
        }

    private fun requestOf(page: String, id: String? = null) =
        MasterRequest(page, 1, NOW, SiteExtractionFailure.RESPONSE_CHANGED, id)

    private fun urls(layer: MasterLayer, page: String, body: String): Set<String> {
        val snapshot = if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
            PageSnapshot(page, 1, apiResponses = listOf(body))
        } else {
            PageSnapshot(page, 1, html = body)
        }
        return layer.collect(requestOf(page), snapshot).candidates
            .map { UrlPolicy.whole(it.mediaUrl) }.toSet()
    }

    private fun collect(page: String, html: String? = null, api: String? = null, id: String? = null) =
        ShapeLayer().collect(
            requestOf(page, id),
            PageSnapshot(page, 1, html = html, apiResponses = listOfNotNull(api)),
        )

    private fun shape(name: String) = resource("/shape/$name")

    private fun resource(path: String) = checkNotNull(javaClass.getResource(path)).readText()

    /** Every key the fixed tables know, renamed as a site would rename it. */
    private fun renameKnownKeys(body: String): String {
        val keys = MediaKeyTable.ADDRESS_KEYS.keys + MediaKeyTable.VERSION_LIST_KEYS.keys +
            MediaKeyTable.ADDRESS_LIST_KEYS + listOf("PlayAddr", "bitrateInfo") +
            listOf("dash_manifest_xml_string", "dash_manifest")
        var out = body
        keys.forEach { key ->
            out = out.replace("\"$key\"", "\"${key}_v2\"").replace("\\\"$key\\\"", "\\\"${key}_v2\\\"")
        }
        return out
    }

    private companion object {
        const val PAGE = "https://example.test/watch/fixture"
        const val TIKTOK = "https://www.tiktok.com/@fixture/video/7400000000000000001"
        val PAGES = mapOf(
            "facebook" to "https://www.facebook.com/fixture/videos/1/",
            "tiktok" to "https://www.tiktok.com/@fixture/video/1",
            "instagram" to "https://www.instagram.com/reel/fixture/",
            "x" to "https://x.com/fixture/status/1",
            "vimeo" to "https://vimeo.com/1",
            "youtube" to "https://www.youtube.com/watch?v=fixture",
        )

        /** Fixtures whose page offers nothing playable (or must stop). */
        val NEGATIVE = Regex(
            "no_media|unavailable|login|geo|expired|insecure|malformed|private|tombstone|" +
                "photo|no_video|no_files|null|password|age_gate|drm|cipher|sabr|bot_check|" +
                "embed_refused|player_live|home_page",
        )
    }
}
