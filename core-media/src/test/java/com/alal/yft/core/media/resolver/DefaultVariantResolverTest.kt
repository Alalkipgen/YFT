package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.media.VariantSupport
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultVariantResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var redirectTarget: MockWebServer
    private lateinit var resolver: DefaultVariantResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        redirectTarget = MockWebServer()
        server.start()
        redirectTarget.start()
        resolver = DefaultVariantResolver(
            client = OkHttpClient(),
            policy = DefaultVariantResolver.Policy(),
            clock = { NOW },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        redirectTarget.shutdown()
    }

    @Test
    fun `direct MP4 uses bounded HEAD metadata and reports exact size`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", "4096"),
        )

        val result = resolver.resolve(candidate(server.url("/movie.mp4").toString()))
            as VariantResolutionResult.Success

        val variant = result.asset.variants.single()
        assertEquals(MediaKind.DIRECT, variant.kind)
        assertEquals(4_096L, variant.sizeBytes)
        assertEquals(MediaSizeAccuracy.EXACT, variant.sizeAccuracy)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `video with a companion audio track resolves to one merged variant`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", "4096"),
        )
        val audio = CompanionAudio(
            mediaUrl = server.url("/audio.m4a?expire=${NOW / 1_000 + 600}").toString(),
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            requestContext = BrowserRequestContext(
                pageUrl = "https://page.example.test/watch",
                userAgent = "YFT fixture",
                cookie = null,
            ),
            contentLengthBytes = 1_024,
            expiresAtEpochMs = NOW + 300_000,
        )

        val result = resolver.resolve(
            candidate(
                url = server.url("/video.mp4").toString(),
                expiresAtEpochMs = NOW + 600_000,
            ).copy(codecs = listOf("avc1.64001F"), audioCompanion = audio),
        ) as VariantResolutionResult.Success

        val variant = result.asset.variants.single()
        assertEquals(MediaTrackType.AUDIO_VIDEO, variant.trackType)
        assertEquals(listOf("avc1.64001F"), variant.codecs)
        assertEquals(audio, variant.audioCompanion)
        assertEquals(5_120L, variant.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, variant.sizeAccuracy)
        assertEquals(NOW + 300_000, variant.expiresAtEpochMs)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expired companion audio fails before any network request`() = runTest {
        val audio = CompanionAudio(
            mediaUrl = server.url("/audio.m4a").toString(),
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            requestContext = BrowserRequestContext("https://page.example.test/watch", null, null),
            expiresAtEpochMs = NOW - 1,
        )

        val result = resolver.resolve(
            candidate(server.url("/video.mp4").toString()).copy(audioCompanion = audio),
        ) as VariantResolutionResult.Failure

        assertEquals(VariantResolutionFailure.EXPIRED_URL, result.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `multi variant HLS resolves video qualities and separate audio`() = runTest {
        server.enqueue(
            manifestResponse(
                """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="stereo",NAME="English",LANGUAGE="en",URI="audio/en.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=850000,RESOLUTION=1280x720,FRAME-RATE=30,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="stereo"
                video/720.m3u8
                #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=1800000,RESOLUTION=1920x1080,FRAME-RATE=59.94,CODECS="avc1.640028,mp4a.40.2",AUDIO="stereo"
                video/1080.m3u8
                """.trimIndent(),
                "application/vnd.apple.mpegurl",
            ),
        )

        val result = resolver.resolve(
            candidate(server.url("/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Success

        assertEquals(3, result.asset.variants.size)
        val fullHd = result.asset.variants.single { it.height == 1080 }
        assertEquals(1920, fullHd.width)
        assertEquals(59.94, fullHd.framesPerSecond!!, 0.001)
        assertEquals(MediaTrackType.VIDEO, fullHd.trackType)
        val audio = result.asset.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals("en", audio.language)
        assertTrue(audio.playbackUrl.endsWith("/audio/en.m3u8"))
    }

    @Test
    fun `DASH resolves separate tracks and marks bitrate sizes estimated`() = runTest {
        server.enqueue(
            manifestResponse(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <MPD mediaPresentationDuration="PT10S" xmlns="urn:mpeg:dash:schema:mpd:2011">
                  <Period>
                    <AdaptationSet contentType="video" mimeType="video/mp4" codecs="avc1.4d401f">
                      <Representation id="v720" bandwidth="800000" width="1280" height="720" frameRate="30000/1001" />
                    </AdaptationSet>
                    <AdaptationSet contentType="audio" mimeType="audio/mp4" codecs="mp4a.40.2" lang="en">
                      <Representation id="a-en" bandwidth="128000" />
                    </AdaptationSet>
                  </Period>
                </MPD>
                """.trimIndent(),
                "application/dash+xml",
            ),
        )

        val result = resolver.resolve(
            candidate(server.url("/manifest.mpd").toString(), MediaKind.DASH),
        ) as VariantResolutionResult.Success

        assertEquals(10_000L, result.asset.durationMillis)
        val video = result.asset.variants.single { it.trackType == MediaTrackType.VIDEO }
        assertEquals(1_000_000L, video.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, video.sizeAccuracy)
        assertEquals(29.970, video.framesPerSecond!!, 0.001)
        val audio = result.asset.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals(160_000L, audio.sizeBytes)
        assertEquals("en", audio.language)
    }

    @Test
    fun `range probe keeps size unknown when server omits total length`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-0/*")
                .setBody("x"),
        )

        val result = resolver.resolve(candidate(server.url("/unknown.mp4").toString()))
            as VariantResolutionResult.Success

        assertNull(result.asset.variants.single().sizeBytes)
        assertNull(result.asset.variants.single().sizeAccuracy)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `expired candidate fails before any network request`() = runTest {
        val result = resolver.resolve(
            candidate(
                url = server.url("/expired.mp4").toString(),
                expiresAtEpochMs = NOW - 1,
            ),
        ) as VariantResolutionResult.Failure

        assertEquals(VariantResolutionFailure.EXPIRED_URL, result.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cookie protected direct preview replays same origin browser context`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Cookie") == "session=fixture") {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "video/mp4")
                        .setHeader("Content-Length", "256")
                } else {
                    MockResponse().setResponseCode(403)
                }
        }

        val result = resolver.resolve(
            candidate(
                url = server.url("/protected.mp4").toString(),
                cookie = "session=fixture",
            ),
        )

        assertTrue(result is VariantResolutionResult.Success)
        assertEquals("session=fixture", server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `redirect does not forward cookie to another origin`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", redirectTarget.url("/final.mp4")),
        )
        redirectTarget.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", "512"),
        )

        val result = resolver.resolve(
            candidate(
                url = server.url("/redirect.mp4").toString(),
                cookie = "session=fixture",
            ),
        ) as VariantResolutionResult.Success

        assertEquals("session=fixture", server.takeRequest().getHeader("Cookie"))
        assertNull(redirectTarget.takeRequest().getHeader("Cookie"))
        assertNull(result.asset.variants.single().requestContext.cookie)
    }

    @Test
    fun `unsupported manifest codec fails explicitly`() = runTest {
        server.enqueue(
            manifestResponse(
                """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360,CODECS="wvc1"
                legacy.m3u8
                """.trimIndent(),
                "application/x-mpegURL",
            ),
        )

        val result = resolver.resolve(
            candidate(server.url("/unsupported.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Failure

        assertEquals(VariantResolutionFailure.UNSUPPORTED_CODEC, result.reason)
    }

    @Test
    fun `DRM manifest fails explicitly`() = runTest {
        server.enqueue(
            manifestResponse(
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXT-X-KEY:METHOD=SAMPLE-AES,URI="license"
                #EXTINF:6,
                segment.ts
                """.trimIndent(),
                "application/x-mpegURL",
            ),
        )

        val result = resolver.resolve(
            candidate(server.url("/drm.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Failure

        assertEquals(VariantResolutionFailure.DRM_PROTECTED, result.reason)
    }

    @Test
    fun `malformed manifest fails without inventing variants`() = runTest {
        server.enqueue(manifestResponse("not a playlist", "application/x-mpegURL"))

        val result = resolver.resolve(
            candidate(server.url("/broken.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Failure

        assertEquals(VariantResolutionFailure.MALFORMED_MANIFEST, result.reason)
    }

    private fun candidate(
        url: String,
        kind: MediaKind = MediaKind.DIRECT,
        expiresAtEpochMs: Long? = null,
        cookie: String? = null,
    ): MediaCandidate = MediaCandidate(
        pageUrl = "https://page.example.test/watch",
        mediaUrl = url,
        sources = setOf(CandidateSource.REQUEST),
        kind = kind,
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT fixture",
            cookie = cookie,
        ),
        confidence = CandidateConfidence.HIGH,
        expiresAtEpochMs = expiresAtEpochMs,
        observedAtEpochMs = NOW,
    )

    private fun manifestResponse(body: String, contentType: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", contentType)
            .setBody(body)

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}