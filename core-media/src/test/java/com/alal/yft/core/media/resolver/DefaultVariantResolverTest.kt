package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.media.VariantSupport
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
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
        // P24: an HLS master's playlist is read for its length; a test that serves none
        // answers it with 404 at once instead of waiting.
        server.dispatcher = QueueDispatcher().apply { setFailFast(true) }
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

        // The source states the picture, so nothing more is read.
        val stated = candidate(server.url("/movie.mp4").toString())
            .copy(title = "Movie — 720p", width = 1280, height = 720, framesPerSecond = 30.0)
        val result = resolver.resolve(stated) as VariantResolutionResult.Success

        val variant = result.asset.variants.single()
        assertEquals(MediaKind.DIRECT, variant.kind)
        assertEquals(4_096L, variant.sizeBytes)
        assertEquals(MediaSizeAccuracy.EXACT, variant.sizeAccuracy)
        assertEquals(720, variant.height)
        assertEquals(1280, variant.width)
        assertEquals(30.0, variant.framesPerSecond!!, 0.0)
        // The page title is never a quality label (P3, finding G2).
        assertNull(variant.label)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an MP4 that does not state its picture is read from its header`() = runTest {
        val file = Mp4Fixtures.file(width = 640, height = 360, audioKbps = 96)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", file.size.toString()),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-${file.size - 1}/${file.size}")
                .setBody(okio.Buffer().write(file)),
        )

        val result = resolver.resolve(
            candidate(server.url("/sd.mp4").toString()).copy(title = "Reel — SD"),
        ) as VariantResolutionResult.Success

        val variant = result.asset.variants.single()
        assertEquals(360, variant.height)
        assertEquals(640, variant.width)
        assertEquals(MediaTrackType.AUDIO_VIDEO, variant.trackType)
        assertEquals(listOf("avc1", "mp4a.40.2"), variant.codecs)
        assertEquals(96_000L, variant.audioBitrateBitsPerSecond)
        assertEquals(61_000L, variant.durationMillis)
        assertNull(variant.label)
        assertEquals(file.size.toLong(), variant.sizeBytes)
        assertEquals("HEAD", server.takeRequest().method)
        val range = server.takeRequest()
        assertEquals("GET", range.method)
        assertEquals("bytes=0-${file.size - 1}", range.getHeader("Range"))
    }

    @Test
    fun `a silent MP4 is a video without sound and a failed probe changes nothing`() = runTest {
        val silent = Mp4Fixtures.file(audio = false)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", silent.size.toString()),
        )
        server.enqueue(MockResponse().setResponseCode(206).setBody(okio.Buffer().write(silent)))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", "4096"),
        )
        server.enqueue(MockResponse().setResponseCode(403))

        val muted = resolver.resolve(candidate(server.url("/muted.mp4").toString()))
            as VariantResolutionResult.Success
        val refused = resolver.resolve(candidate(server.url("/refused.mp4").toString()))
            as VariantResolutionResult.Success

        assertEquals(MediaTrackType.VIDEO, muted.asset.variants.single().trackType)
        assertEquals(720, muted.asset.variants.single().height)
        val unknown = refused.asset.variants.single()
        assertEquals(MediaTrackType.AUDIO_VIDEO, unknown.trackType)
        assertNull(unknown.height)
        assertEquals(4_096L, unknown.sizeBytes)
    }

    @Test
    fun `a stated sound track stays audio when the server labels the mp4 as video`() = runTest {
        // Facebook's CDN serves a DASH manifest's sound-only track as video/mp4 (P4).
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Length", "4096"),
            )
        }
        server.enqueue(MockResponse().setResponseCode(403))

        val sound = resolver.resolve(
            candidate(server.url("/sound.mp4").toString())
                .copy(mimeType = "audio/mp4", codecs = listOf("mp4a.40.5")),
        ) as VariantResolutionResult.Success

        val audio = sound.asset.variants.single()
        assertEquals("audio/mp4", audio.mimeType)
        assertEquals(MediaTrackType.AUDIO, audio.trackType)
        assertEquals(4_096L, audio.sizeBytes)
        assertEquals(MediaSizeAccuracy.EXACT, audio.sizeAccuracy)
        // Its type is known, so no MP4 header is read.
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals(1, server.requestCount)

        // A stated audio type in another container keeps the server's word (and its probe).
        val webm = resolver.resolve(
            candidate(server.url("/sound.webm").toString()).copy(mimeType = "audio/webm"),
        ) as VariantResolutionResult.Success
        assertEquals("video/mp4", webm.asset.variants.single().mimeType)
        assertEquals(3, server.requestCount)
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

    @Test
    fun `a pages HLS master is read with its Referer and Origin and states its length`() = runTest {
        // P24: the owner's case: a page's player streams an HLS master that states no length.
        val pieces = (1..125).joinToString("") { "#EXTINF:6.0,\npiece$it.ts\n" }
        val playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n$pieces#EXTINF:4.5,\nlast.ts\n" +
            "#EXT-X-ENDLIST\n"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/v/master.m3u8" -> manifestResponse(
                    """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="$AVC_720"
                    720.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="$AVC_1080"
                    1080.m3u8
                    """.trimIndent(),
                    "application/vnd.apple.mpegurl",
                )
                request.path.orEmpty().endsWith("0.m3u8") ->
                    manifestResponse(playlist, "application/vnd.apple.mpegurl")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = resolver.resolve(
            candidate(server.url("/v/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Success

        assertEquals(754_500L, result.asset.durationMillis)
        val hd = result.asset.variants.single { it.height == 720 }
        assertEquals(754_500L, hd.durationMillis)
        // 2.5 Mbit/s for 754.5 s: an estimate the sheet shows with "~".
        assertEquals(235_781_250L, hd.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, hd.sizeAccuracy)
        assertEquals("https://page.example.test", hd.requestContext.observedHeaders["Origin"])
        val master = server.takeRequest()
        assertEquals("https://page.example.test/watch", master.getHeader("Referer"))
        assertEquals("https://page.example.test", master.getHeader("Origin"))
        val quality = server.takeRequest()
        assertTrue(quality.path!!.endsWith("0.m3u8"))
        assertEquals("https://page.example.test", quality.getHeader("Origin"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failure names the step and the host it stopped at`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val refused = resolver.resolve(
            candidate(server.url("/v/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Failure
        assertEquals(VariantResolutionFailure.HTTP_STATUS, refused.reason)
        assertEquals(403, refused.httpStatusCode)
        assertEquals(ResolutionStep.MANIFEST, refused.step)
        assertEquals(server.hostName, refused.host)

        server.enqueue(MockResponse().setResponseCode(404))
        val gone = resolver.resolve(candidate(server.url("/clip.mp4").toString()))
            as VariantResolutionResult.Failure
        assertEquals(ResolutionStep.FILE_CHECK, gone.step)
        assertEquals(404, gone.httpStatusCode)

        val blob = resolver.resolve(candidate("blob:https://page.example.test/5b1c"))
            as VariantResolutionResult.Failure
        assertEquals(VariantResolutionFailure.INVALID_URL, blob.reason)
        assertEquals(ResolutionStep.ADDRESS, blob.step)
        assertNull(blob.host)
    }

    @Test
    fun `a file server that answers HEAD with a web page is asked for the file itself`() =
        runTest {
            // P24 (live check): a file host sent HEAD to its home page, a web page, while a
            // one-byte GET of the same address got the video.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/home" || request.path == "/v/page.mp4" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .apply { if (request.method != "HEAD") setBody("<html></html>") }
                    request.method == "HEAD" ->
                        MockResponse().setResponseCode(301).setHeader("Location", "/home")
                    else -> MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Type", "video/mp4")
                        .setHeader("Content-Range", "bytes 0-0/13631866")
                        .setBody("x")
                }
            }

            val result = resolver.resolve(candidate(server.url("/v/1080p.mp4").toString()))
            assertTrue(result.toString(), result is VariantResolutionResult.Success)

            val file = (result as VariantResolutionResult.Success).asset.variants.single()
            assertEquals("video/mp4", file.mimeType)
            assertEquals(13_631_866L, file.sizeBytes)
            assertEquals(server.url("/v/1080p.mp4").toString(), file.playbackUrl)

            // An address that only ever gives a web page is no video.
            val page = resolver.resolve(candidate(server.url("/v/page.mp4").toString()))
                as VariantResolutionResult.Failure
            assertEquals(VariantResolutionFailure.INVALID_URL, page.reason)
        }

    @Test
    fun `a TikTok-shaped site file resolves to its one MP4 without a manifest step`() = runTest {
        // P39 step 7: the owner's "Download · 1.4 MB" row was one MP4 on a TikTok file host,
        // sent with the page answer's cookie. On the JVM it resolves; the phone's failure came
        // from the request running on the sheet's main thread (see the next test).
        val file = Mp4Fixtures.file(width = 576, height = 1024, audioKbps = 128)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", file.size.toString()),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-${file.size - 1}/${file.size}")
                .setBody(okio.Buffer().write(file)),
        )
        val url = server.url("/video/tos/useast5/tos-useast5-ve-0068c001/fixture/")
            .newBuilder().addQueryParameter("mime_type", "video_mp4").build()

        val result = resolver.resolve(
            candidate(url.toString(), cookie = "tt_chain_token=fixture")
                .copy(title = "Sunrise over the harbour — 540p"),
        )

        assertTrue(result.toString(), result is VariantResolutionResult.Success)
        val variant = (result as VariantResolutionResult.Success).asset.variants.single()
        assertEquals(1024, variant.height)
        assertEquals(file.size.toLong(), variant.sizeBytes)
        assertEquals(2, server.requestCount)
        repeat(2) {
            assertEquals("tt_chain_token=fixture", server.takeRequest().getHeader("Cookie"))
        }
    }

    @Test
    fun `responses are never read on the caller's thread, which Android forbids on main`() =
        runTest {
            // Stands in for NetworkOnMainThreadException: the sheet resolves from the main
            // thread, and P38 closed the file check's range answer there, which reads its unread
            // bytes on that thread; the error then escaped the resolver.
            val callerThread = AtomicReference<Thread>()
            val guarded = resolverReading {
                check(Thread.currentThread() !== callerThread.get()) { "read on the main thread" }
            }
            val file = Mp4Fixtures.file(width = 576, height = 1024, audioKbps = 128)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .removeHeader("Content-Length"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 0-0/${file.size}")
                    .setBody(okio.Buffer().write(file.copyOfRange(0, 1))),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 0-${file.size - 1}/${file.size}")
                    .setBody(okio.Buffer().write(file)),
            )
            val caller = Executors.newSingleThreadExecutor()

            val result = try {
                withContext(caller.asCoroutineDispatcher()) {
                    callerThread.set(Thread.currentThread())
                    runCatching { guarded.resolve(candidate(server.url("/clip.mp4").toString())) }
                }
            } finally {
                caller.shutdown()
            }

            val success = result.getOrNull() as? VariantResolutionResult.Success
            assertTrue(result.toString(), success != null)
            assertEquals(file.size.toLong(), success?.asset?.variants?.single()?.sizeBytes)
            assertEquals(1024, success?.asset?.variants?.single()?.height)
        }

    @Test
    fun `an unexpected error ends as a failure at the step it stopped at`() = runTest {
        val crashing = resolverReading { error("fixture") }
        server.enqueue(manifestResponse("#EXTM3U\n", "application/vnd.apple.mpegurl"))

        val result = runCatching {
            crashing.resolve(candidate(server.url("/v/master.m3u8").toString(), MediaKind.HLS))
        }

        val failure = result.getOrNull() as? VariantResolutionResult.Failure
        assertTrue(result.toString(), failure != null)
        assertEquals(VariantResolutionFailure.MALFORMED_MANIFEST, failure?.reason)
        assertEquals(ResolutionStep.MANIFEST, failure?.step)
        assertEquals(server.hostName, failure?.host)
    }

    /** A resolver whose response bodies call [onBytes] whenever bytes are read from them. */
    private fun resolverReading(onBytes: () -> Unit) = DefaultVariantResolver(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@addInterceptor response
            val source = object : okio.ForwardingSource(body.source()) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { if (it > 0) onBytes() }

                // Like OkHttp, closing an answer that was not read to its end reads the rest.
                override fun close() {
                    try {
                        val rest = okio.Buffer()
                        while (read(rest, DISCARD_BYTES) != -1L) rest.clear()
                    } finally {
                        super.close()
                    }
                }
            }
            response.newBuilder()
                .body(source.buffer().asResponseBody(body.contentType(), body.contentLength()))
                .build()
        }.build(),
        policy = DefaultVariantResolver.Policy(),
        clock = { NOW },
    )

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
        const val DISCARD_BYTES = 8_192L

        /** P24: the codecs of the page's HLS master's two qualities. */
        const val AVC_720 = "avc1.4d401f,mp4a.40.2"
        const val AVC_1080 = "avc1.640028,mp4a.40.2"
    }
}