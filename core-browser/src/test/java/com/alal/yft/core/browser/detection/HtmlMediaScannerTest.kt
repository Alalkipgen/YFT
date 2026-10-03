package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaKind
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
