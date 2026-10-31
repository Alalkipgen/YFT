package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.instagram.InstagramUrls
import com.alal.yft.extractor.sites.testing.AndroidLikeDocumentBuilderFactory
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import com.alal.yft.extractor.sites.testing.withAndroidParser
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 R9: Android's own XML parser knows none of the hardening settings the inline manifest
 * reader ([FacebookDashManifests.document]) asked all at once, so on a phone Facebook's and
 * Instagram's inline DASH manifests went unread: only the progressive files were left (no merged
 * sizes, no sound file, no heights from the manifest). The JVM's parser knows them all, so the
 * other tests passed. The reader now asks each one on its own and refuses a document type itself.
 */
class InlineManifestOnAndroidTest {
    @Test
    fun `Facebook's inline manifest gives every file with Android's XML parser`() = runTest {
        val onJvm = facebookRows()

        val onAndroid = withAndroidParser {
            assertTrue(DocumentBuilderFactory.newInstance() is AndroidLikeDocumentBuilderFactory)
            facebookRows()
        }

        assertEquals(
            listOf(
                "$CDN/r/fixture-dash-reel-hd.mp4?oe=F2A52380&oh=fixture",
                "$CDN/r/fixture-dash-reel-sd.mp4?oe=F2A52380&oh=fixture",
                "$CDN/o1/v/fixture-av1-1080.mp4?oe=F2A52380&oh=fixture",
                "$CDN/o1/v/fixture-av1-720-q80.mp4?oe=F2A52380&oh=fixture",
                "$CDN/o1/v/fixture-audio-heaac.mp4?oe=F2A52380&oh=fixture",
            ),
            onAndroid.map(MediaCandidate::mediaUrl),
        )
        assertEquals(onJvm, onAndroid)
        val fullHd = onAndroid[2]
        assertEquals(1078, fullHd.height)
        assertEquals(31_500L, fullHd.durationMillis)
        assertNotNull(fullHd.audioCompanion)
    }

    @Test
    fun `Instagram's merged sizes come from its manifest with Android's XML parser`() = runTest {
        val onJvm = instagramRows()

        val onAndroid = withAndroidParser { instagramRows() }

        assertEquals(listOf(1280, 1920, 1280, 854, null), onAndroid.map { it.height })
        assertTrue(onAndroid.subList(1, 4).all { it.audioCompanion != null })
        assertEquals("audio/mp4", onAndroid.last().mimeType)
        assertEquals(onJvm, onAndroid)
    }

    @Test
    fun `a manifest with a document type or an external entity stays refused on both parsers`() {
        val manifest = MANIFEST.substringAfter("?>")
        val refused = listOf(
            """<?xml version="1.0"?><!DOCTYPE MPD [<!ENTITY x "y">]>$manifest""",
            """<!DOCTYPE MPD [<!ENTITY secret SYSTEM "file:///etc/passwd">]>""" +
                manifest.replace("fixture-video.mp4", "&secret;"),
            """<!doctype MPD SYSTEM "https://dtd.example.test/mpd.dtd">$manifest""",
            URLEncoder.encode("""<!DOCTYPE MPD [<!ENTITY x "y">]>$manifest""", UTF_8),
        )
        // The JVM's own parser refused them before R9 too; the Android-like one has no hardening
        // of its own, so only the reader's refusal stands between it and the entities.
        refused.forEach { xml ->
            assertNull(xml, FacebookDashManifests.parse(xml))
            withAndroidParser { assertNull(xml, FacebookDashManifests.parse(xml)) }
        }
        // The same manifest without them is read on both, encoded or not.
        listOf(MANIFEST, URLEncoder.encode(MANIFEST, UTF_8)).forEach { xml ->
            assertEquals(2, FacebookDashManifests.parse(xml)?.tracks?.size)
            withAndroidParser { assertEquals(2, FacebookDashManifests.parse(xml)?.tracks?.size) }
        }
    }

    private suspend fun facebookRows(): List<MediaCandidate> {
        val http = FakeExtractorHttpClient(
            responses = mapOf(
                PAGE_URL to FakeExtractorHttpClient.html(
                    Fixtures.read("facebook/reel_inline_dash.html"),
                    PAGE_URL,
                ),
            ),
        )
        val request = SiteExtractionRequest(
            identity = SitePageIdentity(
                siteId = "facebook",
                contentId = VIDEO_ID,
                canonicalPageUrl = PAGE_URL,
            ),
            requestContext = BrowserRequestContext(PAGE_URL, AGENT, "c_user=0; xs=fixture-cookie"),
            nowEpochMs = NOW_EPOCH_MS,
        )
        val result = FacebookExtractor(http).extract(request) as SiteExtractionResult.Success
        return result.candidates
    }

    private suspend fun instagramRows(): List<MediaCandidate> {
        val url = "https://www.instagram.com/reel/C9fixtREEL1/?igsh=abc"
        val http = FakeExtractorHttpClient(
            getResponder = { address, _ ->
                FakeExtractorHttpClient.json(
                    Fixtures.read("instagram/graphql_reel.json"),
                    "https://www.instagram.com/x",
                ).takeIf { "/graphql/query/" in address }
            },
        )
        val request = SiteExtractionRequest(
            identity = requireNotNull(InstagramUrls.identify(url)),
            requestContext = BrowserRequestContext(url, AGENT, null),
            nowEpochMs = 1_000L,
        )
        val result = InstagramExtractor(http).extract(request) as SiteExtractionResult.Success
        return result.candidates
    }

    private companion object {
        const val VIDEO_ID = "7180001112223340"
        const val PAGE_URL = "https://www.facebook.com/watch/?v=$VIDEO_ID"
        const val CDN = "https://video.example-cdn.test"
        const val AGENT = "fixture-agent"
        const val NOW_EPOCH_MS = 1_700_000_000_000
        val UTF_8: String = StandardCharsets.UTF_8.name()

        /** One AVC video file and one AAC sound file, as Facebook's inline manifests list them. */
        val MANIFEST = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S"
                profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="1v" bandwidth="900000" codecs="avc1.64001F"
                      mimeType="video/mp4" width="720" height="1280">
                    <BaseURL>$CDN/o1/v/fixture-video.mp4?oh=fixture</BaseURL>
                    <SegmentBase indexRange="805-2348"><Initialization range="0-804"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio">
                  <Representation id="1a" bandwidth="64000" codecs="mp4a.40.2" mimeType="audio/mp4">
                    <BaseURL>$CDN/o1/v/fixture-audio.mp4?oh=fixture</BaseURL>
                    <SegmentBase indexRange="805-2348"><Initialization range="0-804"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
    }
}
