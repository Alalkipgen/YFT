package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 fix F1 (phone test 59): Android's own XML parser knows none of the hardening settings
 * the manifest reader asked all at once, so on a phone every DASH manifest was unreadable ("Couldn't
 * get this video."); the JVM's parser knows them all, so the tests passed. The reader now asks
 * each one on its own and refuses a document type itself.
 */
class DashManifestOnAndroidTest {
    @Test
    fun `the test vector's manifest is read with Android's XML parser`() {
        withAndroidParser { readVector() }
    }

    private fun readVector() {
        val factory = DocumentBuilderFactory.newInstance()
        assertTrue(factory is AndroidLikeDocumentBuilderFactory)
        assertTrue(runCatching { factory.isXIncludeAware = false }.isFailure)

        val parsed = parse(VECTOR) as ManifestParseResult.Parsed

        assertEquals(654_000L, parsed.durationMillis)
        val sound = parsed.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals("en", sound.language)
        assertEquals(72_000L, sound.bitrateBitsPerSecond)
        assertTrue(sound.dashOneFile)
        val video = parsed.variants.filter { it.trackType == MediaTrackType.VIDEO }
        assertEquals(4, video.size)
        assertTrue(video.all { it.width == 480 && it.height == 360 && it.dashOneFile })
        assertEquals(24.0, video.first().framesPerSecond!!, 0.001)
    }

    @Test
    fun `a manifest with a document type stays refused on both parsers`() {
        val doctype = """<?xml version="1.0"?><!DOCTYPE MPD [<!ENTITY x "y">]>""" +
            VECTOR.substringAfter("?>")
        assertEquals(ManifestParseResult.Malformed, parse(doctype))
        withAndroidParser { assertEquals(ManifestParseResult.Malformed, parse(doctype)) }
        // The JVM's parser reads the manifest as before.
        assertTrue(parse(VECTOR) is ManifestParseResult.Parsed)
    }

    private fun parse(manifest: String): ManifestParseResult = DashManifestParser.parse(
        manifest = manifest,
        manifestUrl = "https://cdn.example.test/dash264/netflix/tc1.mpd",
        requestContext = BrowserRequestContext(
            pageUrl = "https://cdn.example.test/dash264/netflix/tc1.mpd",
            userAgent = "YFT fixture",
            cookie = null,
        ),
        expiresAtEpochMs = null,
    )

    private companion object {
        /** DASH-IF's Netflix test vector's shape: a root BaseURL at http, one file each. */
        val VECTOR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
              mediaPresentationDuration="PT654S" minBufferTime="PT4S"
              profiles="http://dashif.org/guidelines/dash264,urn:mpeg:dash:profile:isoff-on-demand:2011">
              <BaseURL>http://origin.example.test/dash264/netflix/</BaseURL>
              <Period>
                <!-- English Audio -->
                <AdaptationSet mimeType="audio/mp4" codecs="mp4a.40.5" lang="en">
                  <Representation id="1" bandwidth="72000">
                    <BaseURL>ElephantsDream_AAC48K_064.mp4.dash</BaseURL>
                    <SegmentBase indexRange="871-11131"><Initialization range="0-870"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="video/mp4" codecs="avc1.42401E" contentType='video'
                    maxWidth="480" maxHeight="360" maxFrameRate="24" par="4:3">
                  <Representation id="2" bandwidth="200000" width="480" height="360" frameRate="24">
                    <BaseURL>ElephantsDream_2.264.dash</BaseURL>
                    <SegmentBase indexRange="984-11244"><Initialization range="0-983"/></SegmentBase>
                  </Representation>
                  <Representation id="3" bandwidth="300000" width="480" height="360" frameRate="24">
                    <BaseURL>ElephantsDream_3.264.dash</BaseURL>
                    <SegmentBase indexRange="984-11244"><Initialization range="0-983"/></SegmentBase>
                  </Representation>
                  <Representation id="4" bandwidth="400000" width="480" height="360" frameRate="24">
                    <BaseURL>ElephantsDream_4.264.dash</BaseURL>
                    <SegmentBase indexRange="984-11244"><Initialization range="0-983"/></SegmentBase>
                  </Representation>
                  <Representation id="5" bandwidth="500000" width="480" height="360" frameRate="24">
                    <BaseURL>ElephantsDream_5.264.dash</BaseURL>
                    <SegmentBase indexRange="984-11244"><Initialization range="0-983"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
    }
}

/**
 * Android's own `DocumentBuilderFactory` (`org.apache.harmony.xml.parsers`) as it answers: no
 * XInclude setting, no feature but namespaces and validation, no attribute. The documents
 * themselves come from the JVM's parser.
 */
class AndroidLikeDocumentBuilderFactory : DocumentBuilderFactory() {
    override fun newDocumentBuilder(): DocumentBuilder =
        newInstance(JVM_FACTORY, AndroidLikeDocumentBuilderFactory::class.java.classLoader)
            .also { it.isNamespaceAware = isNamespaceAware }
            .newDocumentBuilder()

    override fun setXIncludeAware(state: Boolean): Unit =
        throw UnsupportedOperationException("This parser does not support specification")

    override fun setAttribute(name: String, value: Any?): Unit = throw IllegalArgumentException(name)

    override fun getAttribute(name: String): Any = throw IllegalArgumentException(name)

    override fun setFeature(name: String, value: Boolean) {
        if (name != NAMESPACES && name != VALIDATION) throw ParserConfigurationException(name)
    }

    override fun getFeature(name: String): Boolean = when (name) {
        NAMESPACES -> isNamespaceAware
        VALIDATION -> isValidating
        else -> throw ParserConfigurationException(name)
    }

    private companion object {
        const val JVM_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl"
        const val NAMESPACES = "http://xml.org/sax/features/namespaces"
        const val VALIDATION = "http://xml.org/sax/features/validation"
    }
}

/** Runs [block] with [AndroidLikeDocumentBuilderFactory] as the JVM's factory. */
fun <T> withAndroidParser(block: () -> T): T {
    val property = "javax.xml.parsers.DocumentBuilderFactory"
    val before = System.getProperty(property)
    System.setProperty(property, AndroidLikeDocumentBuilderFactory::class.java.name)
    try {
        return block()
    } finally {
        if (before == null) System.clearProperty(property) else System.setProperty(property, before)
    }
}
