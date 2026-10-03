package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookMetadataTest {
    @Test
    fun licenceMapsAndGraphLicenceUrisStillBlockExtraction() {
        val infos = listOf(
            """{"video_license_uri_map":{"widevine":"https://licence.test/fixture"}}""",
            """{"video_license_uri_map":{},"graph_api_video_license_uri":"licence"}""",
            """{"video_license_uri_map":{},"graph_api_video_license_uri":""}""",
        )
        infos.forEach { info ->
            val result = parse(html("\"drm_info\":${quoted(info)}"))
            assertEquals(SiteExtractionFailure.DRM_PROTECTED,
                (result as FacebookParseResult.Failure).reason)
        }
    }

    @Test
    fun explicitProtectionFlagsInEitherNodeStillBlockExtraction() {
        val direct = html("\"is_drm_protected\":true")
        val legacy = html().replace(
            "\"browser_native_hd_url\"", "\"is_drm_protected\":true,\"browser_native_hd_url\"",
        )
        listOf(direct, legacy).forEach { markup ->
            assertEquals(SiteExtractionFailure.DRM_PROTECTED,
                (parse(markup) as FacebookParseResult.Failure).reason)
        }
    }

    @Test
    fun unreadableInfoIsAWarningNotDrmAndNeverCopiesItsRawValue() = runTest {
        val markup = html("\"drm_info\":\"Cookie: private-fixture\"")
        val parsed = parse(markup) as FacebookParseResult.Success
        assertEquals(listOf("drm_info: unreadable metadata"), parsed.details)

        val result = extract(markup) as SiteExtractionResult.Success
        assertTrue(result.details.contains("drm_info: unreadable metadata"))
        assertFalse(result.details.joinToString().contains("private-fixture"))
        assertFalse(result.details.joinToString().contains("Cookie"))
    }

    @Test
    fun unreadableInfoWarningAlsoSurvivesAFailedLookup() = runTest {
        val markup = html("\"drm_info\":\"private-fixture\"")
            .replace("https://cdn.test/hd.mp4", "")
            .replace("https://cdn.test/sd.mp4", "")
        val result = extract(markup) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
        assertTrue(result.details.contains("drm_info: unreadable metadata"))
        assertFalse(result.details.joinToString().contains("private-fixture"))
    }

    @Test
    fun titlesOwnersAndMetaFallbackDecodeEntitiesAndTrimFacebook() {
        val markup = html(
            "\"owner\":{\"name\":\"Owner &amp; Co &#64; &#x2764;\"}",
            title = "A &#xb7; B &#064; &#x1F600; &amp; &#x2764;&#xfe0f; | Facebook",
        )
        val post = (parse(markup) as FacebookParseResult.Success).post
        assertEquals("A · B @ 😀 & ❤️", post.title)
        assertEquals("Owner & Co @ ❤", post.ownerName)

        val fallback = "<meta property=\"og:title\" content=\"Meta &quot;x&quot; | Facebook\">" +
            html(title = null)
        assertEquals("Meta \"x\"", (parse(fallback) as FacebookParseResult.Success).post.title)
    }

    @Test
    fun invalidEntitiesStayLiteralAndEntitiesAreDecodedOnlyOnce() {
        val title = "&#x110000; &#xD800; &#0; &unknown; &amp;#64;"
        val post = (parse(html(title = title)) as FacebookParseResult.Success).post
        assertEquals("&#x110000; &#xD800; &#0; &unknown; &#64;", post.title)

        val meta = "<meta property=\"og:title\" content=\"&amp;#64; | Facebook\">" +
            html(title = null)
        assertEquals("&#64;", (parse(meta) as FacebookParseResult.Success).post.title)
    }

    @Test
    fun metaThumbnailFallbackDecodesItsAttributeQueryOnce() {
        val markup = "<meta property=\"og:image\" " +
            "content=\"https://cdn.test/t.jpg?a=1&amp;b=REDACTED&amp;amp;c\">" + html()
        val post = (parse(markup) as FacebookParseResult.Success).post
        assertEquals("https://cdn.test/t.jpg?a=1&b=REDACTED&amp;c", post.thumbnailUrl)
    }

    @Test
    fun knownDashHeightsAreMatchedToTheRenditionNotGuessedFromHd() {
        val dash = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet>
            <Representation height="720"><BaseURL>https://cdn.test/hd.mp4?x=REDACTED</BaseURL>
            </Representation><Representation height="360"><BaseURL>https://cdn.test/sd.mp4
            </BaseURL></Representation></AdaptationSet></Period></MPD>""".trimIndent()
        val post = (parse(html("\"dash_manifest\":${quoted(dash)}"))
            as FacebookParseResult.Success).post
        assertEquals(listOf("720p · HD", "360p · SD"), post.renditions.map { it.label })
    }

    @Test
    fun unknownHeightsStayHonestAndOnlyExplicitRenditionMetadataSuppliesAHeight() {
        assertEquals(listOf("HD", "SD"),
            (parse(html()) as FacebookParseResult.Success).post.renditions.map { it.label })
        val progressive = """"height":1920,"progressive_urls":[{"metadata":{"quality":"HD",
            "height":720},"progressive_url":"https://cdn.test/hd.mp4"}]""".trimIndent()
        val post = (parse(html(progressive)) as FacebookParseResult.Success).post
        assertEquals(listOf("720p · HD", "SD"), post.renditions.map { it.label })
    }

    @Test
    fun aLoginPhraseCannotDowngradeAPlayablePublicPage() {
        assertTrue(parse("You must log in to continue" + html()) is FacebookParseResult.Success)
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED,
            (parse("<form id=\"loginform\"></form>") as FacebookParseResult.Failure).reason)
    }

    private suspend fun extract(markup: String): SiteExtractionResult {
        val url = "https://www.facebook.com/reel/$ID"
        val identity = requireNotNull(FacebookUrls.identify(url))
        return FacebookExtractor(FakeExtractorHttpClient.serving(url, markup)).extract(
            SiteExtractionRequest(identity, BrowserRequestContext(url, "YFT/fixture", null), 1L),
        )
    }

    private fun parse(markup: String): FacebookParseResult = FacebookPageParser.parse(markup, ID)

    private fun html(metadata: String = "", title: String? = "Fixture"): String =
        """<script type="application/json">{"id":"$ID",
            "videoDeliveryLegacyFields":{"browser_native_hd_url":"https://cdn.test/hd.mp4",
            "browser_native_sd_url":"https://cdn.test/sd.mp4"}
            ${if (title != null) ",\"title\":{\"text\":${quoted(title)}}" else ""}
            ${if (metadata.isNotBlank()) ",$metadata" else ""}}
            </script>""".trimIndent()

    private fun quoted(value: String): String = "\"" + value
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private companion object {
        const val ID = "1234567890123456"
    }
}
