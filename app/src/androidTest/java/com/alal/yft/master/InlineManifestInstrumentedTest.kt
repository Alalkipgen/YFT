package com.alal.yft.master

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.social.SiteRowCheck
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.util.Collections
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 R9: Android's own XML parser threw for the hardening settings both inline DASH readers
 * asked all at once, so on a phone every inline manifest went unread (the JVM's parser knows them
 * all). These read a small inline manifest through both readers with the emulator's own parser:
 * main's Facebook reader (`FacebookDashManifests`, through [FacebookExtractor], which Instagram's
 * merged sizes use too) and Master's (`InlineDashReader`, through the census's [SiteRowCheck],
 * which Master's layers and VK's page manifest use too). Before R9 both found no file in them; a
 * manifest with a document type is still refused.
 */
@RunWith(AndroidJUnit4::class)
class InlineManifestInstrumentedTest {
    @Test
    fun facebooksInlineManifestGivesItsFilesWithAndroidsParser() = runBlocking {
        val rows = facebookRows(page())

        assertEquals(
            listOf(
                "$CDN/r/fixture-dash-reel-hd.mp4?oe=F2A52380&oh=fixture",
                "$CDN/r/fixture-dash-reel-sd.mp4?oe=F2A52380&oh=fixture",
                "$CDN/o1/v/fixture-av1-1080.mp4?oe=F2A52380&oh=fixture",
                "$CDN/o1/v/fixture-av1-720-q80.mp4?oe=F2A52380&oh=fixture",
                "$CDN/o1/v/fixture-audio-heaac.mp4?oe=F2A52380&oh=fixture",
            ),
            rows.map(MediaCandidate::mediaUrl),
        )
        assertEquals(listOf(null, null, 1078, 720, null), rows.map(MediaCandidate::height))
        assertNotNull(rows[2].audioCompanion)
        assertNotNull(rows[3].audioCompanion)
    }

    @Test
    fun theRowCheckReadsADashRowsManifestWithAndroidsParser() = runBlocking {
        val http = Pages(mapOf(FB_MANIFEST to MANIFEST), ExtractorProbeResult.Answered(206, 9_000L))

        val checked = SiteRowCheck(http).check(SiteWays.FACEBOOK, dashRow(), NOW_MS)

        assertEquals(
            SiteRowCheck.Checked(SiteRowCheck.Verdict.PASS, null, 206, requests = 2),
            checked,
        )
        assertEquals(listOf(FB_720), http.probed)
    }

    @Test
    fun aManifestWithADocumentTypeStaysRefusedWithAndroidsParser() = runBlocking {
        val doctype = MANIFEST.replace("<MPD ", """<!DOCTYPE MPD [<!ENTITY x "y">]><MPD """)
        val http = Pages(mapOf(FB_MANIFEST to doctype), ExtractorProbeResult.Answered(206, 9_000L))

        val checked = SiteRowCheck(http).check(SiteWays.FACEBOOK, dashRow(), NOW_MS)

        assertEquals(
            SiteRowCheck.Checked(SiteRowCheck.Verdict.UNKNOWN, SiteRowCheck.NO_FILE, 200, 1),
            checked,
        )
        assertEquals(emptyList<String>(), http.probed)
        // The page's own manifests (escaped as JSON) with a document type: only its other rows.
        val page = page().replace("?>\\n\\u003CMPD ", "?>\\n\\u003C!DOCTYPE MPD>\\u003CMPD ")
        val rows = facebookRows(page).map(MediaCandidate::mediaUrl)
        assertEquals("$CDN/r/fixture-dash-reel-hd.mp4?oe=F2A52380&oh=fixture", rows.first())
        assertTrue(rows.toString(), rows.none { "/o1/v/" in it })
    }

    /**
     * Why the first two fail on the old code: its readers asked these settings at once, each in
     * one `runCatching`, and this parser refuses one of them, so the old readers read nothing.
     */
    @Test
    fun androidsParserRefusesTheSettingsTheOldReadersAskedAtOnce() {
        val oldFacebook = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute("$PROPERTY/accessExternalDTD", "")
                setAttribute("$PROPERTY/accessExternalSchema", "")
            }
        }
        val oldMaster = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute("$PROPERTY/accessExternalDTD", "")
                setAttribute("$PROPERTY/accessExternalSchema", "")
            }
        }

        assertTrue(oldFacebook.toString(), oldFacebook.isFailure)
        assertTrue(oldMaster.toString(), oldMaster.isFailure)
    }

    private suspend fun facebookRows(page: String): List<MediaCandidate> {
        val request = SiteExtractionRequest(
            identity = SitePageIdentity("facebook", VIDEO_ID, PAGE_URL),
            requestContext = BrowserRequestContext(PAGE_URL, AGENT, null),
            nowEpochMs = 1_700_000_000_000,
        )
        val result = FacebookExtractor(Pages(mapOf(PAGE_URL to page))).extract(request)
        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        return (result as SiteExtractionResult.Success).candidates
    }

    private fun page(): String = InstrumentationRegistry.getInstrumentation().context.assets
        .open("facebook/reel_inline_dash.html").use { it.readBytes().decodeToString() }

    private fun dashRow() = MediaCandidate(
        pageUrl = FB_REEL,
        mediaUrl = FB_MANIFEST,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DASH,
        mimeType = "application/dash+xml",
        requestContext = BrowserRequestContext(FB_REEL, AGENT, null),
    )

    /** The pages and manifests it holds; any other address is a 404. Files answer [probe]. */
    private class Pages(
        private val bodies: Map<String, String>,
        private val probe: ExtractorProbeResult = ExtractorProbeResult.Unsupported,
    ) : ExtractorHttpClient {
        val probed: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = bodies[url]?.let { body ->
            ExtractorHttpResult.Success(200, body, url, "text/html; charset=utf-8")
        } ?: ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)

        override suspend fun probe(
            url: String,
            headers: Map<String, String>,
            timeoutMillis: Long,
        ): ExtractorProbeResult {
            probed += url
            return probe
        }
    }

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val AGENT = "fixture-agent"
        const val VIDEO_ID = "7180001112223340"
        const val PAGE_URL = "https://www.facebook.com/watch/?v=$VIDEO_ID"
        const val CDN = "https://video.example-cdn.test"
        const val FB_REEL = "https://www.facebook.com/reel/1234567890123456"
        const val FB_MANIFEST = "https://video.xx.fbcdn.net/v/t42/manifest.mpd?oh=fixture"
        const val FB_720 = "https://video.xx.fbcdn.net/v/t42/v720.mp4?_nc_cat=1&oh=fixture"
        const val PROPERTY = "http://javax.xml.XMLConstants/property"

        /** Two whole video files (360 absolute, 720 relative) and a sound file, as Facebook's. */
        val MANIFEST = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet mimeType="video/mp4" segmentAlignment="true">
                  <Representation id="v1" codecs="avc1.4d401e" bandwidth="400000" width="360" height="640">
                    <BaseURL>https://video.xx.fbcdn.net/v/t42/v360.mp4?_nc_cat=1&amp;oh=fixture</BaseURL>
                    <SegmentBase indexRange="800-900"><Initialization range="0-799"/></SegmentBase>
                  </Representation>
                  <Representation id="v2" codecs="avc1.64001f" bandwidth="1500000" width="720" height="1280">
                    <BaseURL>v720.mp4?_nc_cat=1&amp;oh=fixture</BaseURL>
                    <SegmentBase indexRange="800-900"><Initialization range="0-799"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="a1" codecs="mp4a.40.2" bandwidth="64000">
                    <BaseURL>audio.mp4?oh=fixture</BaseURL>
                    <SegmentBase indexRange="800-900"><Initialization range="0-799"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
    }
}
