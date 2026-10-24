package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.BoundedJsonParser
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D0: the public version sources are read as data, one pattern-matched value each. */
class CensusSourcesTest {
    @Test
    fun `the reference tables give one value per identity`() {
        val readers = CensusSources.DOCUMENTS.associate { document -> document.id to document.readers.associate { it.identity to it.values } }

        assertEquals(listOf("1.02"), readers.getValue("ytdlp").getValue("visionos")(Census.ytDlp()))
        assertEquals(listOf("21.26.364"), readers.getValue("ytdlp").getValue("android")(Census.ytDlp()))
        assertEquals(listOf("1.02"), readers.getValue("youtubejs").getValue("visionos")(Census.YOUTUBE_JS_TEXT))
        assertEquals(listOf("21.03.36"), readers.getValue("youtubejs").getValue("android")(Census.YOUTUBE_JS_TEXT))
        assertEquals(listOf("1.02"), readers.getValue("appstore").getValue("visionos")(Census.APP_STORE_TEXT))
        assertEquals(listOf("1.02.10"), CensusSources.appStoreValues(Census.APP_STORE_TEXT))
        assertEquals(listOf("1.05", "Varies"), CensusSources.appStoreForms("{\"results\":[{\"version\":\"1.05.34\"},{\"version\":\"Varies\"}]}"))
        assertEquals(null, CensusSources.appStoreForm("1.05"))
        assertTrue(CensusSources.APP_STORE_URL.endsWith("bundleId=com.google.visionosyoutube"))
        assertEquals(listOf("20.41.33"), readers.getValue("playstore").getValue("android")("x [[[\"20.41.33\"]] y"))
        assertTrue(CensusSources.appStoreListsVision(Census.APP_STORE_TEXT))
        assertFalse(CensusSources.appStoreListsVision("{\"results\":[]}"))
        assertTrue(CensusSources.appStoreValues("not json").isEmpty())
    }

    @Test
    fun `the answer's own cver is found`() {
        val root = requireNotNull(BoundedJsonParser.parse(Census.visionOs(Census.V1, cver = "1.02")))
        assertEquals(listOf("1.02"), CensusSources.answerValues(root))
    }

    @Test
    fun `values are judged by pattern, count and order against the bundled one`() {
        val equal = CensusSources.fact("visionos", "ytdlp", true, null, listOf("1.02"), "1.02")
        assertEquals(listOf(true, false, true, "equal"), listOf(equal.pattern, equal.newer, equal.sameAsBundled, equal.accepted))

        val newer = CensusSources.fact("visionos", "ytdlp", true, null, listOf("1.10"), "1.02")
        assertEquals(listOf(true, true, "not-tested"), listOf(newer.pattern, newer.newer, newer.accepted))

        val storeNumber = CensusSources.fact("visionos", "appstore", true, null, listOf("21.40.4"), "1.02")
        assertEquals(listOf(false, null, null), listOf(storeNumber.pattern, storeNumber.newer, storeNumber.accepted))

        val two = CensusSources.fact("android", "youtubejs", true, null, listOf("21.03.36", "21.26.364"), "21.26.364")
        assertEquals(listOf(null, 2, "2 values"), listOf(two.value, two.values, two.note))

        val junk = CensusSources.fact("android", "playstore", true, null, listOf("Varies with device<script>"), "21.26.364")
        assertEquals("Varies with devicescript", junk.value)
        assertFalse(junk.pattern)
    }

    @Test
    fun `patterns and order follow each identity's numbering`() {
        assertTrue(CensusSources.VISION_OS_VERSION.matches("1.02"))
        assertFalse(CensusSources.VISION_OS_VERSION.matches("1.2"))
        assertFalse(CensusSources.VISION_OS_VERSION.matches("21.40.4"))
        assertTrue(CensusSources.ANDROID_VERSION.matches("21.26.364"))
        assertTrue(CensusSources.ANDROID_VERSION.matches("21.03.36"))
        assertFalse(CensusSources.ANDROID_VERSION.matches("21.26"))
        assertTrue(CensusSources.WEB_VERSION.matches("2.20261002.01.00"))
        assertFalse(CensusSources.WEB_VERSION.matches("1.20261002.01.00"))
        assertEquals(1, CensusSources.compare("1.10", "1.02"))
        assertEquals(-1, CensusSources.compare("21.03.36", "21.26.364"))
        assertEquals(0, CensusSources.compare("1.02", "1.02.0"))
        assertNull(CensusSources.compare("1.0a", "1"))
    }

    @Test
    fun `the sources are public https documents read under 64 KiB`() {
        assertEquals(64L * 1024, CensusSources.MAX_SOURCE_BYTES)
        assertEquals(listOf("ytdlp", "youtubejs", "appstore", "playstore"), CensusSources.DOCUMENTS.map { it.id })
        val hosts = CensusSources.DOCUMENTS.map { URI(it.url).also { uri -> assertEquals("https", uri.scheme) }.host }
        assertEquals(listOf("raw.githubusercontent.com", "raw.githubusercontent.com", "itunes.apple.com", "play.google.com"), hosts)
        assertTrue(CensusSources.DOCUMENTS.none { "key=" in it.url || "token" in it.url })
    }
}
