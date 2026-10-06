package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMediaScannerTest {
    private val scanner = HtmlMediaScanner()
    private val page = "https://example.com/watch/ocean"

    @Test
    fun mediaElementsAndTheirSourcesAreFoundAndResolvedAgainstThePage() {
        val html = """
            <html><head><title>Ocean &amp; Waves</title></head><body>
            <video poster="/img/poster.jpg" controls>
              <source src="media/ocean-720.mp4" type="video/mp4">
              <source src='https://cdn.example.com/ocean.webm' type='video/webm'>
            </video>
            <audio src="//cdn.example.com/waves.m4a"></audio>
            </body></html>
        """.trimIndent()

        val result = scanner.scan(html, page, observedAtEpochMs = 7)

        assertEquals("Ocean & Waves", result.title)
        assertEquals(
            listOf(
                "https://example.com/watch/media/ocean-720.mp4",
                "https://cdn.example.com/ocean.webm",
                "https://cdn.example.com/waves.m4a",
            ),
            result.candidates.map { it.mediaUrl },
        )
        val first = result.candidates.first()
        assertEquals("video/mp4", first.mimeType)
        assertEquals("https://example.com/img/poster.jpg", first.thumbnailUrl)
        assertEquals(CandidateConfidence.HIGH, first.confidence)
        assertEquals(setOf(CandidateSource.DOM), first.sources)
        assertEquals(page, first.pageUrl)
        assertEquals(7L, first.observedAtEpochMs)
        assertTrue(result.candidates.all { it.kind == MediaKind.DIRECT })
    }

    @Test
    fun openGraphStreamsCountButHtmlPlayerPagesDoNot() {
        val html = """
            <meta property="og:title" content="Launch replay">
            <meta property="og:image" content="https://example.com/thumb.jpg">
            <meta property="og:video" content="https://example.com/embed/123">
            <meta property="og:video:type" content="text/html">
            <meta property="og:audio" content="https://example.com/audio/launch.mp3">
            <meta name="twitter:player:stream" content="https://example.com/live/master.m3u8">
        """.trimIndent()

        val result = scanner.scan(html, page, observedAtEpochMs = 0)

        assertEquals("Launch replay", result.title)
        assertEquals(
            listOf(
                "https://example.com/audio/launch.mp3",
                "https://example.com/live/master.m3u8",
            ),
            result.candidates.map { it.mediaUrl },
        )
        assertEquals(MediaKind.HLS, result.candidates[1].kind)
        assertEquals(CandidateConfidence.MEDIUM, result.candidates[0].confidence)
    }

    @Test
    fun jsonLdContentUrlsAndEscapedScriptLinksAreFound() {
        val html = """
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"VideoObject",
             "contentUrl":"https:\/\/media.example.org\/files\/clip-1080"}
            </script>
            <script>
              var player = {"hls":"https:\/\/stream.example.org\/v\/index.m3u8?token=a\u0026b=1"};
            </script>
            <p>Mirror: https://files.example.org/talk.mp3.</p>
        """.trimIndent()

        val result = scanner.scan(html, page, observedAtEpochMs = 0)

        assertEquals(
            listOf(
                "https://media.example.org/files/clip-1080",
                "https://stream.example.org/v/index.m3u8?token=a&b=1",
                "https://files.example.org/talk.mp3",
            ),
            result.candidates.map { it.mediaUrl },
        )
        assertEquals(MediaKind.UNKNOWN, result.candidates[0].kind)
        assertEquals(MediaKind.HLS, result.candidates[1].kind)
    }

    @Test
    fun insecureBlobDataAndNonMediaLinksAreIgnoredAndDuplicatesCollapse() {
        val html = """
            <video src="blob:https://example.com/123"></video>
            <video src="data:video/mp4;base64,AAAA"></video>
            <video src="http://insecure.example.com/clip.mp4"></video>
            <a href="https://example.com/about.html">About</a>
            <img src="https://example.com/photo.jpg">
            <video src="https://example.com/clip.mp4"></video>
            <a href="https://example.com/clip.mp4">Download</a>
        """.trimIndent()

        val result = scanner.scan(html, page, observedAtEpochMs = 0)

        assertEquals(listOf("https://example.com/clip.mp4"), result.candidates.map { it.mediaUrl })
        assertNull(result.title)
    }

    @Test
    fun candidateCountIsCapped() {
        val html = (1..80).joinToString("\n") { "https://cdn.example.com/part-$it.mp4" }

        val result = HtmlMediaScanner(maxCandidates = 5).scan(html, page, observedAtEpochMs = 0)

        assertEquals(5, result.candidates.size)
    }

    @Test
    fun aVideoPageWithPreviewThumbnailsNamesItsStreamAsItsVideo() {
        // P24: the owner's case: the page's player config names an HLS master; 40 thumbnail
        // links carry muted looping preview clips; an ad frame carries no file.
        val html = fixture("p24-preview-grid.html")
        val watch = "https://videos.example.test/watch/42"

        val result = scanner.scan(html, watch, observedAtEpochMs = 0)

        val master = "https://stream.example.test/v42/master.m3u8?token=fixture"
        val main = result.candidates.filter { it.pageRole == PageMediaRole.MAIN }
        assertEquals(listOf(master), main.map { it.mediaUrl })
        assertEquals(MediaKind.HLS, main.single().kind)
        assertEquals(40, result.candidates.count { it.pageRole == PageMediaRole.PREVIEW })
        assertEquals(41, result.candidates.size)
        assertEquals("Long walk by the river", result.title)
        val list = MediaGroups.ofPage(MediaGroups.pageVideos(result.candidates))
        assertEquals(listOf(master), list.videos.single().candidates.map { it.mediaUrl })
        assertEquals(40, list.previews.size)

        // A page that lists more than the cap keeps its own video first.
        val capped = HtmlMediaScanner(maxCandidates = 10).scan(html, watch, observedAtEpochMs = 0)
        assertEquals(10, capped.candidates.size)
        assertEquals(master, capped.candidates.first().mediaUrl)
    }

    @Test
    fun thePagesOwnWordsMarkItsVideoAndItsPreviews() {
        val html = """
            <meta property="og:video:url" content="https://cdn.example.com/v/main.mp4">
            <script type="application/ld+json">
            {"@type":"VideoObject","duration":"PT12M5S",
             "contentUrl":"https://cdn.example.com/v/main-hd.mp4",
             "embedUrl":"https://cdn.example.com/v/main.m3u8"}
            </script>
            <div class="card" data-preview-url="https://cdn.example.com/p/1.mp4"></div>
            <span data-teaser="https://cdn.example.com/p/2.mp4"></span>
            <img class="tile" data-src="https://cdn.example.com/p/3.mp4">
            <div class="tile" data-src="https://cdn.example.com/v/other.mp4"></div>
            <video autoplay muted loop src="https://cdn.example.com/p/4.mp4"></video>
            <video controls src="https://cdn.example.com/v/also.mp4"></video>
            <p>https://cdn.example.com/p/1.mp4</p>
        """.trimIndent()

        val result = scanner.scan(html, page, observedAtEpochMs = 0)
        val roles = result.candidates.associate { candidate ->
            candidate.mediaUrl.substringAfter(".com/") to candidate.pageRole
        }

        assertEquals(PageMediaRole.MAIN, roles["v/main.mp4"])
        assertEquals(PageMediaRole.MAIN, roles["v/main-hd.mp4"])
        assertEquals(PageMediaRole.MAIN, roles["v/main.m3u8"])
        assertEquals(PageMediaRole.PREVIEW, roles["p/1.mp4"])
        assertEquals(PageMediaRole.PREVIEW, roles["p/2.mp4"])
        assertEquals(PageMediaRole.PREVIEW, roles["p/3.mp4"])
        assertEquals(PageMediaRole.PREVIEW, roles["p/4.mp4"])
        assertNull(roles["v/also.mp4"])
        // A data-src on a box that is not a thumbnail is just a link.
        assertNull(roles["v/other.mp4"])
        // The VideoObject's one length is its files' length.
        val lengths = result.candidates.associate { it.mediaUrl.substringAfter(".com/") to
            it.durationMillis }
        assertEquals(725_000L, lengths["v/main-hd.mp4"])
        assertEquals(725_000L, lengths["v/main.m3u8"])
        assertNull(lengths["v/main.mp4"])
    }

    private fun fixture(name: String): String {
        val resource = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing fixture $name"
        }
        return resource.bufferedReader().use { it.readText() }
    }

    @Test
    fun anUnclosedMediaTagDoesNotHideLaterMedia() {
        val html = "<video src=\"https://example.com/a.mp4\">" + "x".repeat(50_000) +
            "<audio src=\"https://example.com/b.mp3\"></audio>"

        val result = scanner.scan(html, page, observedAtEpochMs = 0)

        assertEquals(
            listOf("https://example.com/b.mp3", "https://example.com/a.mp4"),
            result.candidates.map { it.mediaUrl },
        )
    }
}
