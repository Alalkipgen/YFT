package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.layers.Evidence
import com.alal.yft.extractor.master.layers.LayerStack
import com.alal.yft.extractor.master.layers.RecipeLayer
import com.alal.yft.extractor.master.toolkit.InlineDashReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerStackTest {
    private val reader = LayerStack.standard()

    /** R2: YouTube rows stay off in the standard stack; the recipe itself is kept for R6. */
    private val youtubeRecipe = LayerStack(listOf(RecipeLayer(youtubeStreams = true)))

    @Test
    fun `R2 a YouTube payload yields no Master rows`() {
        assertTrue(read("youtube-player.json", "yt-fixture").candidates.isEmpty())
        val html = "<script>var ytInitialPlayerResponse = " +
            fixture("youtube-player.json") + ";</script>"
        listOf(PAGE, YOUTUBE).forEach { page ->
            assertTrue(
                reader.collect(youtubeRequest(page), PageSnapshot(page, 1, html = html))
                    .candidates.isEmpty(),
            )
        }
        // Even an opted-in recipe never answers a YouTube host.
        val onYoutube = youtubeRecipe.collect(
            youtubeRequest(YOUTUBE),
            PageSnapshot(YOUTUBE, 1, apiResponses = listOf(fixture("youtube-player.json"))),
        )
        assertTrue(onYoutube.candidates.isEmpty())
        assertNull(onYoutube.terminalFailure)
    }

    @Test
    fun `R2 a YouTube DRM statement is terminal whatever the playability says`() {
        val format = """{"url":"$MEDIA","mimeType":"video/mp4"}"""
        val shapes = listOf(
            """{"drmFamilies":["WIDEVINE"],"formats":[$format]}""",
            """{"drmParams":"fixture","formats":[$format]}""",
            """{"adaptiveFormats":[{"url":"$MEDIA","drmFamilies":["WIDEVINE"]}]}""",
            """{"formats":[{"url":"$MEDIA","drmTrackType":"DRM_TRACK_TYPE_HD"}]}""",
        )
        listOf("OK", "UNPLAYABLE", "LOGIN_REQUIRED").forEach { status ->
            shapes.forEach { data ->
                val body = """{"videoDetails":{"videoId":"target"},""" +
                    """"playabilityStatus":{"status":"$status"},"streamingData":$data}"""
                listOf(reader, youtubeRecipe).forEach { layers ->
                    val found = layers.collect(request(id = "target"), snapshot(body))
                    assertEquals(body, SiteExtractionFailure.DRM_PROTECTED, found.terminalFailure)
                    assertTrue(found.candidates.isEmpty())
                }
            }
        }
        val session = """{"videoDetails":{"videoId":"target"},""" +
            """"playbackTracking":{"drmSessionId":"s"},"streamingData":{"formats":[$format]}}"""
        assertEquals(
            SiteExtractionFailure.DRM_PROTECTED,
            reader.collect(request(id = "target"), snapshot(session)).terminalFailure,
        )
    }

    @Test
    fun `opted-in YouTube recipe keeps metadata and pairs AVC with AAC`() {
        val found = youtubeRecipe.collect(
            request(id = "yt-fixture"), snapshot(fixture("youtube-player.json")),
        )
        assertEquals(3, found.candidates.size)
        val video = found.candidates.single { it.height == 720 }
        assertEquals("https://cdn.example.test/sound.m4a", video.audioCompanion?.mediaUrl)
        assertEquals(17_000L, video.durationMillis)
        assertNull(found.candidates.single { it.height == 360 }.audioCompanion)
    }

    @Test
    fun `YouTube cipher only data never fabricates an address`() {
        val body = """{"videoDetails":{"videoId":"target"},"streamingData":""" +
            """{"adaptiveFormats":[{"signatureCipher":"REDACTED","mimeType":"video/mp4"}]}}"""
        listOf(reader, youtubeRecipe).forEach {
            assertTrue(it.collect(request(id = "target"), snapshot(body)).candidates.isEmpty())
        }
    }

    @Test
    fun `assigned watch player JSON is read without executing script`() {
        val html = "<script>var ytInitialPlayerResponse = " +
            fixture("youtube-player.json") + ";</script>"
        val found = youtubeRecipe.collect(
            request(id = "yt-fixture"), PageSnapshot(PAGE, 1, html = html),
        )
        assertEquals(3, found.candidates.size)
    }

    @Test
    fun `Facebook inline MPD yields whole tracks with sound companion`() {
        val found = read("facebook-inline.json", "fb-fixture")
        assertEquals(3, found.candidates.size)
        val video = found.candidates.single { it.height == 720 }
        assertNotNull(video.audioCompanion)
        assertNull(video.bitrateBitsPerSecond)
    }

    @Test
    fun `TikTok reflow page yields play address and bitrate ladder`() {
        val found = reader.collect(
            request(id = "tt-fixture"),
            PageSnapshot(PAGE, 1, html = fixture("tiktok-reflow.html")),
        )
        assertEquals(2, found.candidates.size)
        assertTrue(found.candidates.any { it.height == 720 })
    }

    @Test
    fun `Instagram version fixture yields only the requested shortcode`() {
        assertEquals(2, read("instagram-reel.json", "ig-fixture").candidates.size)
        assertTrue(read("instagram-reel.json", "another").candidates.isEmpty())
    }

    @Test
    fun `X fixture yields progressive and HLS without inventing an API call`() {
        val found = read("x-video.json", "x-fixture")
        assertEquals(
            setOf(MediaKind.DIRECT, MediaKind.HLS), found.candidates.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `HTML sources resolve relative addresses but not blob literals`() {
        val html = """<video src='/main.mp4'><source src='blob:fixture'></video>"""
        val found = reader.collect(request(), PageSnapshot(PAGE, 1, html = html))
        assertEquals("https://example.test/main.mp4", found.candidates.single().mediaUrl)
    }

    @Test
    fun `page cookies are never copied to a different CDN origin`() {
        val context = BrowserRequestContext(
            PAGE, "fixture-agent", "session=REDACTED",
            mapOf("Authorization" to "Bearer REDACTED", "X-Secret" to "REDACTED"),
        )
        val found = reader.collect(
            request().copy(requestContext = context),
            snapshot("""{"video_url":"$MEDIA"}"""),
        ).candidates.single()
        assertNull(found.requestContext.cookie)
        assertTrue(found.requestContext.observedHeaders.isEmpty())
        assertEquals("fixture-agent", found.requestContext.userAgent)
    }

    @Test
    fun `captured media keeps only that request's own origin context`() {
        val context = BrowserRequestContext(PAGE, "fixture-agent", "session=REDACTED")
        val found = reader.collect(request(), snapshot(
            playing = MEDIA,
            requests = listOf(CapturedRequest(MEDIA, context = context)),
        )).candidates.single()
        assertEquals("session=REDACTED", found.requestContext.cookie)
    }

    @Test
    fun `malformed and too deeply nested JSON fail closed`() {
        assertTrue(reader.collect(request(), snapshot("{broken")).candidates.isEmpty())
        val nested = "[".repeat(80) + """{"video_url":"$MEDIA"}""" + "]".repeat(80)
        assertTrue(reader.collect(request(), snapshot(nested)).candidates.isEmpty())
    }

    @Test
    fun `explicit DRM evidence is a terminal discovery verdict`() {
        val found = reader.collect(request(), snapshot(
            """{"id":"one","video_url":"$MEDIA","is_drm_protected":true}""",
        ))
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, found.terminalFailure)
        assertTrue(found.candidates.isEmpty())
    }

    @Test
    fun `inline MPD rejects external entities and segmented representations`() {
        val unsafe = """<!DOCTYPE MPD [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>""" +
            """<MPD><Period><BaseURL>&secret;</BaseURL></Period></MPD>"""
        assertTrue(InlineDashReader.read(unsafe, request(), null, "fixture").candidates.isEmpty())
        val segmented = """<MPD><Period><AdaptationSet mimeType='video/mp4'>""" +
            """<SegmentTemplate media='piece-1.m4s'/><Representation>""" +
            """<BaseURL>https://cdn.example.test/init.mp4</BaseURL>""" +
            """</Representation></AdaptationSet></Period></MPD>"""
        assertTrue(
            InlineDashReader.read(segmented, request(), null, "fixture").candidates.isEmpty(),
        )
    }

    @Test
    fun `snapshot request and result debug strings contain no bodies or credentials`() {
        val observed = CapturedRequest("$MEDIA?signature=REDACTED",
            context = BrowserRequestContext(PAGE, null, "session=REDACTED"))
        val snap = snapshot("SENSITIVE_BODY", requests = listOf(observed))
        val text = listOf(snap, request(snap), observed).joinToString()
        assertFalse(text.contains("SENSITIVE_BODY"))
        assertFalse(text.contains("signature"))
        assertFalse(text.contains("session=REDACTED"))
        assertFalse(text.contains(MEDIA))
    }

    private fun read(file: String, id: String): Evidence =
        reader.collect(request(id = id), snapshot(fixture(file)))

    private fun youtubeRequest(page: String) =
        MasterRequest(page, 1, NOW, SiteExtractionFailure.RESPONSE_CHANGED, "yt-fixture")

    private companion object {
        const val YOUTUBE = "https://www.youtube.com/watch?v=yt-fixture"
    }
}
