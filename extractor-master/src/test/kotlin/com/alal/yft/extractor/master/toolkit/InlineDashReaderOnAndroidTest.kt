package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.social.SiteRowCheck
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.VkPageDash
import com.alal.yft.extractor.master.testing.AndroidLikeDocumentBuilderFactory
import com.alal.yft.extractor.master.testing.withAndroidParser
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 R9: Android's own XML parser knows none of the hardening settings Master's inline DASH
 * reader ([InlineDashReader.read]) asked all at once, so on a phone every inline manifest went
 * unread: Master's layers found no whole file in one, VK's page manifest gave no row
 * ([VkPageDash]) and the census's row check left a DASH row unknown ([SiteRowCheck]). The JVM's
 * parser knows them all, so the other tests passed. The reader now asks each one on its own and
 * refuses a document type itself.
 */
class InlineDashReaderOnAndroidTest {
    @Test
    fun `an inline manifest gives its whole files with Android's XML parser`() {
        val onJvm = InlineDashReader.read(MANIFEST, request(), null, KEY)

        val onAndroid = withAndroidParser {
            assertTrue(DocumentBuilderFactory.newInstance() is AndroidLikeDocumentBuilderFactory)
            InlineDashReader.read(MANIFEST, request(), null, KEY)
        }

        assertEquals(listOf(FB_360, FB_720, FB_AUDIO), onAndroid.candidates.map { it.mediaUrl })
        assertEquals(listOf(640, 1280, null), onAndroid.candidates.map { it.height })
        assertEquals(FB_AUDIO, onAndroid.candidates[1].audioCompanion?.mediaUrl)
        assertEquals(mapOf(FB_360 to 400_000L, FB_720 to 1_500_000L, FB_AUDIO to 64_000L), onAndroid.bandwidths)
        assertFalse(onAndroid.drm)
        assertEquals(onJvm, onAndroid)
    }

    @Test
    fun `VK's page manifest gives its tallest file and its sound with Android's XML parser`() {
        val vk = MANIFEST.replace("https://video.xx.fbcdn.net/v/t42/", "https://vkvd123.okcdn.ru/")

        val read = withAndroidParser { VkPageDash.read(vk, VK_MANIFEST, NOW_MS) }

        read as VkPageDash.Read.Files
        assertEquals("https://vkvd123.okcdn.ru/v720.mp4?_nc_cat=1&oh=fixture", read.picture.mediaUrl)
        assertEquals("https://vkvd123.okcdn.ru/audio.mp4?oh=fixture", read.sound?.mediaUrl)
        assertEquals(10_000L, read.durationMillis)
    }

    @Test
    fun `the row check reads a DASH row's manifest with Android's XML parser`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.html(MANIFEST, url).takeIf { url == FB_MANIFEST } },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 9_000L) },
        )

        val checked = withAndroidParser { SiteRowCheck(http).check(SiteWays.FACEBOOK, row(), NOW_MS) }

        assertEquals(SiteRowCheck.Checked(SiteRowCheck.Verdict.PASS, null, 206, requests = 2), checked)
        assertEquals(listOf(FB_720), http.probedUrls)
    }

    @Test
    fun `a manifest with a document type or an external entity stays refused on both parsers`() {
        val body = MANIFEST.substringAfter("?>")
        val refused = listOf(
            """<?xml version="1.0"?><!DOCTYPE MPD [<!ENTITY x "y">]>$body""",
            """<!DOCTYPE MPD [<!ENTITY secret SYSTEM "file:///etc/passwd">]>""" +
                body.replace("v720.mp4?_nc_cat=1&amp;oh=fixture", "&secret;"),
            """<!doctype MPD SYSTEM "https://dtd.example.test/mpd.dtd">$body""",
        )
        // The JVM's own parser refused them before R9 too; the Android-like one has no hardening
        // of its own, so only the reader's refusal stands between it and the entities.
        refused.forEach { xml ->
            assertEquals(xml, InlineDashReader.Result(emptyList()), InlineDashReader.read(xml, request(), null, KEY))
            withAndroidParser {
                assertEquals(xml, InlineDashReader.Result(emptyList()), InlineDashReader.read(xml, request(), null, KEY))
            }
        }
        // The same manifest without them is read on both.
        assertEquals(3, InlineDashReader.read(MANIFEST, request(), null, KEY).candidates.size)
        withAndroidParser { assertEquals(3, InlineDashReader.read(MANIFEST, request(), null, KEY).candidates.size) }
    }

    private fun request() = MasterRequest(FB_MANIFEST, 0, NOW_MS, SiteExtractionFailure.NO_MEDIA_FOUND)

    private fun row() = MediaCandidate(
        pageUrl = FB_REEL,
        mediaUrl = FB_MANIFEST,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DASH,
        mimeType = "application/dash+xml",
        requestContext = BrowserRequestContext(FB_REEL, "fixture-agent", null),
    )

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val KEY = "fixture"
        const val FB_REEL = "https://www.facebook.com/reel/1234567890123456"
        const val FB_MANIFEST = "https://video.xx.fbcdn.net/v/t42/manifest.mpd?oh=fixture"
        const val FB_360 = "https://video.xx.fbcdn.net/v/t42/v360.mp4?_nc_cat=1&oh=fixture"
        const val FB_720 = "https://video.xx.fbcdn.net/v/t42/v720.mp4?_nc_cat=1&oh=fixture"
        const val FB_AUDIO = "https://video.xx.fbcdn.net/v/t42/audio.mp4?oh=fixture"
        const val VK_MANIFEST = "https://vkvd123.okcdn.ru/manifest.mpd?oh=fixture"

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
