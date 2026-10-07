package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PageVideoFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P28: what a page states about its own video, from its meta tags and JSON-LD only. */
class PageFactsReaderTest {
    private val page = "https://tube.example.test/watch/77"

    @Test
    fun aVideoObjectStatesItsLengthEvenWithoutAMediaFile() {
        val html = """
            <title>Harbour lights at dusk - Example Tube</title>
            <meta property="og:title" content="Harbour lights at dusk">
            <meta property="og:image" content="/v77/poster.jpg">
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"VideoObject","name":"Ignored",
             "duration":"PT16M24S","embedUrl":"https://tube.example.test/embed/77"}
            </script>
            <p>Duration: 99:99 in the page text is never read.</p>
        """.trimIndent()

        val facts = PageFactsReader.fromHtml(html, page)

        assertEquals(
            PageVideoFacts(
                durationMillis = 984_000,
                title = "Harbour lights at dusk",
                thumbnailUrl = "https://tube.example.test/v77/poster.jpg",
            ),
            facts,
        )
    }

    @Test
    fun theOtherStatedLengthsAreReadInTheirOwnForms() {
        fun length(meta: String) = PageFactsReader.fromHtml(meta, page).durationMillis

        assertEquals(754_000L, length("""<meta property="og:video:duration" content="754">"""))
        assertEquals(754_000L, length("""<meta property="video:duration" content="754">"""))
        assertEquals(3_723_000L, length("""<meta itemprop="duration" content="PT1H2M3S">"""))
        assertEquals(984_000L, length("""<meta itemprop="duration" content="16:24">"""))
        assertEquals(3_723_000L, length("""<meta itemprop="duration" content="1:02:03">"""))
        assertNull(length("""<meta itemprop="duration" content="soon">"""))
        assertNull(length("""<meta property="og:video:duration" content="0">"""))
    }

    @Test
    fun aListOfRelatedVideosOfDifferentLengthsStatesNoLength() {
        val html = """
            <script type="application/ld+json">
            {"@graph":[{"@type":"VideoObject","name":"One","duration":"PT1M"},
                       {"@type":["VideoObject"],"name":"Two","duration":"PT2M"}]}
            </script>
        """.trimIndent()

        val facts = PageFactsReader.fromHtml(html, page)

        assertNull(facts.durationMillis)
        assertEquals("One", facts.title)
    }

    @Test
    fun aNestedVideoObjectGivesItsNameAndPicture() {
        val html = """
            <title>Example Tube</title>
            <script type="application/ld+json">
            {"@type":"WebPage","mainEntity":{"@type":"VideoObject","name":"Quiet bay",
             "duration":"PT10M","thumbnailUrl":["https://img.example.test/bay.jpg"]}}
            </script>
        """.trimIndent()

        val facts = PageFactsReader.fromHtml(html, page)

        assertEquals(600_000L, facts.durationMillis)
        assertEquals("Quiet bay", facts.title)
        assertEquals("https://img.example.test/bay.jpg", facts.thumbnailUrl)
    }

    @Test
    fun thePageTitleLosesTheSitesName() {
        fun title(text: String, site: String? = null): String? {
            val meta = site?.let { """<meta property="og:site_name" content="$it">""" }.orEmpty()
            return PageFactsReader.fromHtml("$meta<title>$text</title>", page).title
        }

        assertEquals("Harbour lights", title("Harbour lights - Example Tube", "Example Tube"))
        assertEquals("Harbour lights", title("Harbour lights | Tube"))
        assertEquals("Harbour lights - Part 2", title("Harbour lights - Part 2"))
        assertEquals("Fish & chips", title("Fish &amp; chips"))
    }

    @Test
    fun onlyAnHttpsPictureIsKept() {
        val html = """<meta property="og:image" content="http://img.example.test/a.jpg">"""

        assertNull(PageFactsReader.fromHtml(html, page).thumbnailUrl)
        assertEquals(PageVideoFacts(), PageFactsReader.fromHtml("<p>nothing</p>", page))
    }

    @Test
    fun theLiveProbesPartsGiveTheSameFacts() {
        val facts = PageFactsReader.fromParts(
            meta = mapOf(
                "og:title" to "Harbour lights at dusk",
                "og:image" to "https://img.example.test/v77/poster.jpg",
            ),
            jsonLd = listOf("""{"@type":"VideoObject","duration":"PT16M24S"}""", "{broken"),
            documentTitle = "Harbour lights at dusk - Example Tube",
            pageUrl = page,
        )

        assertEquals(984_000L, facts.durationMillis)
        assertEquals("Harbour lights at dusk", facts.title)
    }
}
