package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserReadAnswer
import com.alal.yft.core.model.media.BrowserReadRequest
import com.alal.yft.core.model.media.BrowserReads
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
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
        // An HLS master's playlist is read for its length; a test that serves none
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
        // The page title is never a quality label.
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
    fun `a WebM that does not state its picture is read from its own header`() = runTest {
        // Wikimedia Commons' transcodes are WebM: before, their rows read "Quality unknown"
        // because only MP4 headers were read.
        val file = WebmFixtures.file(width = 1920, height = 1080, framesPerSecond = 30)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/webm")
                .setHeader("Content-Length", file.size.toString()),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/webm")
                .setHeader("Content-Range", "bytes 0-${file.size - 1}/${file.size}")
                .setBody(okio.Buffer().write(file)),
        )

        val result = resolver.resolve(
            candidate(server.url("/clip.webm").toString()).copy(mimeType = "video/webm"),
        ) as VariantResolutionResult.Success

        val variant = result.asset.variants.single()
        assertEquals(1080, variant.height)
        assertEquals(1920, variant.width)
        assertEquals(30.0, variant.framesPerSecond!!, 0.0)
        assertEquals(MediaTrackType.AUDIO_VIDEO, variant.trackType)
        assertEquals(listOf("vp9", "opus"), variant.codecs)
        assertEquals(61_000L, variant.durationMillis)
        assertEquals("WebM", variant.container)
        // One ranged read of the file's start: the WebM states all of its tracks in it.
        assertEquals("HEAD", server.takeRequest().method)
        val range = server.takeRequest()
        assertEquals("GET", range.method)
        assertEquals("bytes=0-${file.size - 1}", range.getHeader("Range"))
        assertEquals(2, server.requestCount)
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
        // Facebook's CDN serves a DASH manifest's sound-only track as video/mp4.
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
        val audio = result.asset.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals("en", audio.language)
        assertTrue(audio.playbackUrl.endsWith("/audio/en.m3u8"))
        // Each quality is merged with its group's sound, not listed as "No sound".
        assertEquals(MediaTrackType.AUDIO_VIDEO, fullHd.trackType)
        assertEquals(listOf("avc1.640028"), fullHd.codecs)
        val sound = requireNotNull(fullHd.audioCompanion)
        assertEquals(audio.playbackUrl, sound.mediaUrl)
        assertEquals(listOf("mp4a.40.2"), sound.codecs)
        assertEquals(audio.requestContext, sound.requestContext)
        val hd = result.asset.variants.single { it.height == 720 }
        assertEquals(audio.playbackUrl, hd.audioCompanion?.mediaUrl)
    }

    @Test
    fun `HLS qualities whose separate sound cannot be merged stay silent`() = runTest {
        val stream = "#EXT-X-STREAM-INF:BANDWIDTH"
        val master = listOf(
            "#EXTM3U",
            """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="AAC",URI="audio/aac.m3u8"""",
            """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="dd",NAME="Dolby",URI="audio/ec3.m3u8"""",
            """$stream=900000,RESOLUTION=1280x720,CODECS="hvc1.1.6.L93.B0,mp4a.40.2",AUDIO="aac"""",
            "hevc/720.m3u8",
            """$stream=800000,RESOLUTION=1280x720,AUDIO="aac"""",
            "plain/720.m3u8",
            """$stream=700000,RESOLUTION=1280x720,CODECS="avc1.4d401f,ec-3",AUDIO="dd"""",
            "dolby/720.m3u8",
            """$stream=600000,RESOLUTION=854x480,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="none"""",
            "avc/480.m3u8",
        ).joinToString("\n")
        server.enqueue(manifestResponse(master, "application/vnd.apple.mpegurl"))

        val result = resolver.resolve(
            candidate(server.url("/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Success

        val qualities = result.asset.variants.filter { it.trackType != MediaTrackType.AUDIO }
        assertEquals(4, qualities.size)
        assertTrue(qualities.all { it.trackType == MediaTrackType.VIDEO })
        assertTrue(qualities.all { it.audioCompanion == null })
        assertEquals(2, result.asset.variants.count { it.trackType == MediaTrackType.AUDIO })
    }

    @Test
    fun `YouTube HLS pairs AVC qualities with the AAC sound and VP9 only above them`() = runTest {
        // YouTube's HLS answer (h-only): the sound is one rendition without CODECS, named in each
        // quality's CODECS first. VP9 with AAC goes into one MP4 by YFT's own
        // stream copy, so a VP9 quality is paired where no AVC one has sound (2160p); at 1080p
        // the AVC row keeps the sound and the VP9 one stays "No sound".
        val stream = "#EXT-X-STREAM-INF:BANDWIDTH"
        val master = listOf(
            "#EXTM3U",
            """#EXT-X-MEDIA:URI="audio/main.m3u8",TYPE=AUDIO,GROUP-ID="main",NAME="Default",DEFAULT=YES""",
            """$stream=4500000,CODECS="mp4a.40.2,avc1.640028",RESOLUTION=1920x1080,AUDIO="main"""",
            "avc/1080.m3u8",
            """$stream=4000000,CODECS="mp4a.40.2,vp09.00.40.08",RESOLUTION=1920x1080,AUDIO="main"""",
            "vp9/1080.m3u8",
            """$stream=18000000,CODECS="mp4a.40.2,vp09.00.51.08",RESOLUTION=3840x2160,AUDIO="main"""",
            "vp9/2160.m3u8",
        ).joinToString("\n")
        server.enqueue(manifestResponse(master, "application/vnd.apple.mpegurl"))

        val result = resolver.resolve(
            candidate(server.url("/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Success

        val variants = result.asset.variants
        val avc = variants.single { it.playbackUrl.endsWith("/avc/1080.m3u8") }
        assertEquals(MediaTrackType.AUDIO_VIDEO, avc.trackType)
        assertEquals(listOf("avc1.640028"), avc.codecs)
        assertEquals(listOf("mp4a.40.2"), requireNotNull(avc.audioCompanion).codecs)
        val vp9 = variants.single { it.playbackUrl.endsWith("/vp9/1080.m3u8") }
        assertEquals(MediaTrackType.VIDEO, vp9.trackType)
        assertEquals(null, vp9.audioCompanion)
        val vp9Uhd = variants.single { it.playbackUrl.endsWith("/vp9/2160.m3u8") }
        assertEquals(MediaTrackType.AUDIO_VIDEO, vp9Uhd.trackType)
        assertEquals(listOf("vp09.00.51.08"), vp9Uhd.codecs)
        assertEquals(listOf("mp4a.40.2"), requireNotNull(vp9Uhd.audioCompanion).codecs)
        assertEquals(1, variants.count { it.trackType == MediaTrackType.AUDIO })
    }

    @Test
    fun `DASH resolves separate tracks and states no size from a peak bandwidth`() = runTest {
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
        // @bandwidth is the peak of a channel the representation can be delivered on, not the
        // file's average: 800 kbit/s over 10 s is not the file's size, so the row states none.
        assertNull(video.sizeBytes)
        assertNull(video.sizeAccuracy)
        assertEquals(800_000L, video.bitrateBitsPerSecond)
        assertEquals(29.970, video.framesPerSecond!!, 0.001)
        val audio = result.asset.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertNull(audio.sizeBytes)
        assertEquals("en", audio.language)
    }

    @Test
    fun `DASH notes each representation that is one file`() = runTest {
        // A SegmentBase with the representation's own BaseURL, in a manifest
        // that is not live, is one file; pieces, another file's initialization or a live
        // manifest are not.
        val manifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD mediaPresentationDuration="PT10S" xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4" codecs="avc1.4d401e">
                  <Representation id="one" bandwidth="400000" width="640" height="360">
                    <BaseURL>video-360.mp4</BaseURL>
                    <SegmentBase indexRange="700-799"><Initialization range="0-699"/></SegmentBase>
                  </Representation>
                  <Representation id="pieces" bandwidth="800000" width="1280" height="720">
                    <SegmentTemplate media="v720-${'$'}Number${'$'}.m4s" duration="2" startNumber="1"/>
                  </Representation>
                  <Representation id="elsewhere" bandwidth="600000" width="854" height="480">
                    <BaseURL>video-480.mp4</BaseURL>
                    <SegmentBase indexRange="700-799">
                      <Initialization sourceURL="init-480.mp4" range="0-699"/>
                    </SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4" codecs="mp4a.40.2" lang="en">
                  <SegmentBase indexRange="500-579"><Initialization range="0-499"/></SegmentBase>
                  <Representation id="sound" bandwidth="128000">
                    <BaseURL>audio-en.m4a</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        server.enqueue(manifestResponse(manifest, "application/dash+xml"))
        server.enqueue(
            manifestResponse(
                manifest.replace("<MPD ", "<MPD type=\"dynamic\" "),
                "application/dash+xml",
            ),
        )

        fun oneFile(result: VariantResolutionResult): Map<String?, Boolean> =
            (result as VariantResolutionResult.Success).asset.variants
                .associate { it.manifestVariantId to it.dashOneFile }

        val static = oneFile(
            resolver.resolve(candidate(server.url("/manifest.mpd").toString(), MediaKind.DASH)),
        )
        assertEquals(
            mapOf("one" to true, "pieces" to false, "elsewhere" to false, "sound" to true),
            static,
        )
        val live = oneFile(
            resolver.resolve(candidate(server.url("/live.mpd").toString(), MediaKind.DASH)),
        )
        assertTrue(live.isNotEmpty())
        assertTrue(live.values.none { it })
    }

    @Test
    fun `DASH notes each representation in pieces and reads bbb_30fps's sound`() = runTest {
        // A SegmentTemplate or SegmentList (the representation's, its adaptation
        // set's or its period's), no SegmentBase beside it, in a manifest that is not live. The
        // sound is bbb_30fps.mpd's: HE-AAC, no language, an Accessibility and a Role.
        val manifest = """
            <MPD mediaPresentationDuration="PT634.566S" type="static"
              xmlns="urn:mpeg:dash:schema:mpd:2011">
              <BaseURL>./</BaseURL>
              <Period>
                <AdaptationSet mimeType="video/mp4" contentType="video">
                  <SegmentTemplate duration="120" timescale="30" startNumber="1"
                    media="${'$'}RepresentationID${'$'}_${'$'}Number${'$'}.m4v"
                    initialization="${'$'}RepresentationID${'$'}_0.m4v"/>
                  <Representation id="template" codecs="avc1.640033" bandwidth="14931538"
                    width="3840" height="2160" frameRate="30"/>
                  <Representation id="both" codecs="avc1.64001f" bandwidth="4952892"
                    width="1280" height="720">
                    <SegmentBase indexRange="700-799"><Initialization range="0-699"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="video/mp4" codecs="avc1.64000d">
                  <Representation id="list" bandwidth="254320" width="320" height="180">
                    <SegmentList duration="4">
                      <Initialization sourceURL="list/init.m4v"/>
                      <SegmentURL media="list/1.m4v"/>
                    </SegmentList>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4" contentType="audio">
                  <Accessibility schemeIdUri="urn:tva:metadata:cs:AudioPurposeCS:2007" value="6"/>
                  <Role schemeIdUri="urn:mpeg:dash:role:2011" value="main"/>
                  <SegmentTemplate duration="192512" timescale="48000" startNumber="1"
                    media="${'$'}RepresentationID${'$'}_${'$'}Number${'$'}.m4a"
                    initialization="${'$'}RepresentationID${'$'}_0.m4a"/>
                  <Representation id="bbb_a64k" codecs="mp4a.40.5" bandwidth="67071"
                    audioSamplingRate="48000">
                    <AudioChannelConfiguration value="2"
                      schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        server.enqueue(manifestResponse(manifest, "application/dash+xml"))
        server.enqueue(
            manifestResponse(
                manifest.replace("type=\"static\"", "type=\"dynamic\""),
                "application/dash+xml",
            ),
        )

        fun pieces(result: VariantResolutionResult) =
            (result as VariantResolutionResult.Success).asset.variants

        val static = pieces(
            resolver.resolve(candidate(server.url("/bbb_30fps.mpd").toString(), MediaKind.DASH)),
        )
        assertEquals(
            mapOf("template" to true, "both" to false, "list" to true, "bbb_a64k" to true),
            static.associate { it.manifestVariantId to it.dashPieces },
        )
        assertTrue(static.none { it.dashOneFile })
        val sound = static.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals("bbb_a64k", sound.manifestVariantId)
        assertEquals(VariantSupport.SUPPORTED, sound.support)
        assertEquals(listOf("mp4a.40.5"), sound.codecs)
        assertEquals("audio/mp4", sound.mimeType)
        assertEquals(67_071L, sound.bitrateBitsPerSecond)
        assertNull(sound.language)
        val live = pieces(
            resolver.resolve(candidate(server.url("/live.mpd").toString(), MediaKind.DASH)),
        )
        assertEquals(4, live.size)
        assertTrue(live.none { it.dashPieces || it.dashOneFile })
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
        // The owner's case: a page's player streams an HLS master that states no length.
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
        // BANDWIDTH is the stream's peak rate, not a reliable file size: the row states no size.
        assertNull(hd.sizeBytes)
        assertNull(hd.sizeAccuracy)
        assertEquals(2_500_000L, hd.bitrateBitsPerSecond)
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
    fun `an HLS master that states the average rate is sized from it and a peak alone is not`() =
        runTest {
            // AVERAGE-BANDWIDTH is the stream's average rate (RFC 8216), so average times length is
            // the file's size, shown with "~". The 1080 quality states a peak only: no size.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/v/master.m3u8" -> manifestResponse(
                        """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS="$AVC_720"
                        720.m3u8
                        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="$AVC_1080"
                        1080.m3u8
                        """.trimIndent(),
                        "application/vnd.apple.mpegurl",
                    )
                    request.path == "/v/720.m3u8" -> manifestResponse(
                        "#EXTM3U\n#EXT-X-TARGETDURATION:755\n#EXTINF:754.5,\npart.ts\n#EXT-X-ENDLIST\n",
                        "application/vnd.apple.mpegurl",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }

            val result = resolver.resolve(
                candidate(
                    url = server.url("/v/master.m3u8").toString(),
                    kind = MediaKind.HLS,
                ).copy(durationMillis = 754_500),
            ) as VariantResolutionResult.Success

            val hd = result.asset.variants.single { it.height == 720 }
            // 1 Mbit/s for 754.5 s, the master's stated average.
            assertEquals(94_312_500L, hd.sizeBytes)
            assertEquals(MediaSizeAccuracy.ESTIMATED, hd.sizeAccuracy)
            assertEquals(754_500L, hd.durationMillis)
            val full = result.asset.variants.single { it.height == 1080 }
            assertNull(full.sizeBytes)
            assertNull(full.sizeAccuracy)
            assertEquals(5_000_000L, full.bitrateBitsPerSecond)
        }

    @Test
    fun `a live HLS window is not an estimate of the whole recording`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/live/master.m3u8" -> manifestResponse(
                    """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS="$AVC_720"
                    720.m3u8
                    """.trimIndent(),
                    "application/vnd.apple.mpegurl",
                )
                "/live/720.m3u8" -> manifestResponse(
                    """
                    #EXTM3U
                    #EXT-X-TARGETDURATION:6
                    #EXTINF:6,
                    segment.ts
                    """.trimIndent(),
                    "application/vnd.apple.mpegurl",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }

        for (duration in listOf(null, 600_000L)) {
            val result = resolver.resolve(
                candidate(server.url("/live/master.m3u8").toString(), MediaKind.HLS)
                    .copy(durationMillis = duration),
            ) as VariantResolutionResult.Success
            val video = result.asset.variants.single()
            assertNull("a live window is not a finite recording size", video.sizeBytes)
            assertNull(video.sizeAccuracy)
        }
    }

    @Test
    fun `HLS average sizes require a finished playlist for each quality`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/mixed/master.m3u8" -> manifestResponse(
                    """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS="$AVC_720"
                    720.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=5000000,AVERAGE-BANDWIDTH=2000000,RESOLUTION=1920x1080,CODECS="$AVC_1080"
                    1080.m3u8
                    """.trimIndent(),
                    "application/vnd.apple.mpegurl",
                )
                "/mixed/720.m3u8" -> manifestResponse(
                    "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n",
                    "application/vnd.apple.mpegurl",
                )
                "/mixed/1080.m3u8" -> manifestResponse(
                    "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n",
                    "application/vnd.apple.mpegurl",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = resolver.resolve(
            candidate(server.url("/mixed/master.m3u8").toString(), MediaKind.HLS)
                .copy(durationMillis = 600_000),
        ) as VariantResolutionResult.Success
        val finite = result.asset.variants.single { it.height == 720 }
        val live = result.asset.variants.single { it.height == 1080 }
        assertEquals(750_000L, finite.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, finite.sizeAccuracy)
        assertNull(live.sizeBytes)
        assertNull(live.sizeAccuracy)
    }

    @Test
    fun `HLS average size includes separate audio only when both playlists are complete`() = runTest {
        val audio = AtomicReference("#EXTM3U\n#EXTINF:6,\na.aac\n#EXT-X-ENDLIST\n")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/sound/master.m3u8" -> manifestResponse(
                    """
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="stereo",NAME="Audio",URI="audio.m3u8"
                    #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="stereo"
                    video.m3u8
                    """.trimIndent(),
                    "application/vnd.apple.mpegurl",
                )
                "/sound/video.m3u8" -> manifestResponse(
                    "#EXTM3U\n#EXTINF:6,\nv.ts\n#EXT-X-ENDLIST\n",
                    "application/vnd.apple.mpegurl",
                )
                "/sound/audio.m3u8" -> manifestResponse(audio.get(), "application/vnd.apple.mpegurl")
                else -> MockResponse().setResponseCode(404)
            }
        }
        suspend fun size(): Long? {
            val result = resolver.resolve(
                candidate(server.url("/sound/master.m3u8").toString(), MediaKind.HLS),
            ) as VariantResolutionResult.Success
            val video = result.asset.variants.single { it.height == 720 }
            assertTrue(video.audioCompanion != null)
            return video.sizeBytes
        }
        // The master average describes the combined rendition; do not add the sound twice.
        assertEquals(750_000L, size())
        audio.set("#EXTM3U\n#EXTINF:6,\na.aac\n")
        assertNull(size())
        audio.set("#EXTM3U\n#EXTINF:5,\na.aac\n#EXT-X-ENDLIST\n")
        assertNull(size())
    }

    @Test
    fun `finished separate HLS tracks can differ by a small frame rounding amount`() = runTest {
        // Apple's public 540p VOD: video 600.000 s, AAC 600.042 s, both ended.
        val videoDuration = AtomicReference("600.000")
        val audioDuration = AtomicReference("600.042")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/rounding/master.m3u8" -> manifestResponse(
                    """
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="stereo",NAME="Audio",URI="audio.m3u8"
                    #EXT-X-STREAM-INF:BANDWIDTH=2523597,AVERAGE-BANDWIDTH=2190673,RESOLUTION=960x540,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="stereo"
                    video.m3u8
                    """.trimIndent(), "application/vnd.apple.mpegurl",
                )
                "/rounding/video.m3u8" -> manifestResponse(
                    "#EXTM3U\n#EXTINF:${videoDuration.get()},\nv.ts\n#EXT-X-ENDLIST\n",
                    "application/vnd.apple.mpegurl",
                )
                "/rounding/audio.m3u8" -> manifestResponse(
                    "#EXTM3U\n#EXTINF:${audioDuration.get()},\na.aac\n#EXT-X-ENDLIST\n",
                    "application/vnd.apple.mpegurl",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        suspend fun quality(): MediaVariant {
            val result = resolver.resolve(
                candidate(server.url("/rounding/master.m3u8").toString(), MediaKind.HLS),
            ) as VariantResolutionResult.Success
            return result.asset.variants.single { it.height == 540 }
        }
        val estimated = quality()
        assertEquals(164_300_475L, estimated.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, estimated.sizeAccuracy)
        assertTrue(estimated.audioCompanion != null)
        audioDuration.set("599.958")
        assertEquals(164_300_475L, quality().sizeBytes)
        audioDuration.set("600.050")
        assertEquals(164_300_475L, quality().sizeBytes)
        audioDuration.set("600.051")
        assertNull(quality().sizeBytes)
        videoDuration.set("10.000")
        audioDuration.set("10.020") // Small in absolute terms, too large for a short video.
        assertNull(quality().sizeBytes)
    }

    @Test
    fun `HLS average size reads at most six quality playlists`() = runTest {
        val master = buildString {
            appendLine("#EXTM3U")
            repeat(7) { index ->
                appendLine(
                    "#EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=1000000," +
                        "RESOLUTION=1280x720,CODECS=\"$AVC_720\"",
                )
                appendLine("$index.m3u8")
            }
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/many/master.m3u8") {
                    manifestResponse(master, "application/vnd.apple.mpegurl")
                } else {
                    manifestResponse(
                        "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n",
                        "application/vnd.apple.mpegurl",
                    )
                }
        }
        val result = resolver.resolve(
            candidate(server.url("/many/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Success
        assertEquals(6, result.asset.variants.count { it.sizeAccuracy == MediaSizeAccuracy.ESTIMATED })
        assertNull(result.asset.variants.last().sizeBytes)
        assertEquals(7, server.requestCount)
    }

    @Test
    fun `an oversized HLS quality playlist does not become an estimate`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/large/master.m3u8") {
                    manifestResponse(
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2500000," +
                            "AVERAGE-BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS=\"$AVC_720\"\n" +
                            "720.m3u8\n",
                        "application/vnd.apple.mpegurl",
                    )
                } else {
                    manifestResponse(
                        "#EXTM3U\n#${"x".repeat(256 * 1_024)}\n" +
                            "#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n",
                        "application/vnd.apple.mpegurl",
                    )
                }
        }
        val result = resolver.resolve(
            candidate(server.url("/large/master.m3u8").toString(), MediaKind.HLS),
        ) as VariantResolutionResult.Success
        assertNull(result.asset.variants.single().sizeBytes)
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
            // Seen in a live check: a file host sent HEAD to its home page, a web page, while a
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
        // The owner's "Download · 1.4 MB" row was one MP4 on a TikTok file host,
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
            // thread, and the file check closed its range answer there, which reads its unread
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
    fun `a file server that answers HEAD with a server error is asked for the file itself`() =
        runTest {
            // In a live check, TikTok's file host answered the Download sheet's HEAD for the
            // 540p H.265 file with 504, while a one-byte GET of the same address got the file.
            val file = Mp4Fixtures.file(width = 576, height = 1024, audioKbps = 128)
            server.enqueue(MockResponse().setResponseCode(504))
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

            val result = resolver.resolve(candidate(server.url("/clip.mp4").toString()))

            assertTrue(result.toString(), result is VariantResolutionResult.Success)
            val variant = (result as VariantResolutionResult.Success).asset.variants.single()
            assertEquals(file.size.toLong(), variant.sizeBytes)
            assertEquals(MediaSizeAccuracy.EXACT, variant.sizeAccuracy)
            assertEquals(1024, variant.height)
            assertEquals("HEAD", server.takeRequest().method)
            assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))

            // The lookup fails only when the range GET fails too, with that answer's status.
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(502))
            val refused = resolver.resolve(candidate(server.url("/gone.mp4").toString()))
                as VariantResolutionResult.Failure
            assertEquals(VariantResolutionFailure.HTTP_STATUS, refused.reason)
            assertEquals(502, refused.httpStatusCode)
            assertEquals(ResolutionStep.FILE_CHECK, refused.step)
            // A status failure has no exception class.
            assertNull(refused.error)
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
        // The failure names its error's class.
        assertEquals("IllegalStateException", failure?.error)
    }

    @Test
    fun `a connection that times out is a network failure naming SocketTimeoutException`() =
        runTest {
            val timingOut = DefaultVariantResolver(
                client = OkHttpClient.Builder().addInterceptor {
                    throw java.net.SocketTimeoutException("timeout")
                }.build(),
                policy = DefaultVariantResolver.Policy(),
                clock = { NOW },
            )

            val failure = timingOut.resolve(candidate(server.url("/clip.mp4").toString()))
                as VariantResolutionResult.Failure

            assertEquals(VariantResolutionFailure.NETWORK, failure.reason)
            // The class name only, never the message.
            assertEquals("SocketTimeoutException", failure.error)
            assertEquals(server.hostName, failure.host)
        }

    @Test
    fun `a file whose HEAD answers 474 is asked by a range GET before failing`() = runTest {
        // The owner's adult-site file answered the sheet's HEAD with HTTP 474.
        server.enqueue(MockResponse().setResponseCode(474))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-0/4096")
                .setBody("x"),
        )
        val stated = candidate(server.url("/clip.mp4").toString()).copy(height = 720)

        val result = resolver.resolve(stated)

        assertTrue(result.toString(), result is VariantResolutionResult.Success)
        assertEquals(
            4_096L,
            (result as VariantResolutionResult.Success).asset.variants.single().sizeBytes,
        )
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `a refused manifest is asked by the browser and its answer stands in`() = runTest {
        // The CDN answered YFT's request of a fresh HLS link with 410 while the page's
        // player played it; the browser's own engine gets it.
        val asked = mutableListOf<BrowserReadRequest>()
        val master = server.url("/v/master.m3u8").toString()
        val browser = resolverWith { request ->
            asked += request
            BrowserReadAnswer(
                status = 200,
                finalUrl = master,
                contentType = "application/vnd.apple.mpegurl",
                text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720," +
                    "CODECS=\"$AVC_720\"\n720.m3u8\n",
            )
        }
        server.enqueue(MockResponse().setResponseCode(410))

        val result = browser.resolve(candidate(master, MediaKind.HLS))

        assertTrue(result.toString(), result is VariantResolutionResult.Success)
        val variant = (result as VariantResolutionResult.Success).asset.variants.single()
        assertEquals(720, variant.height)
        assertEquals(server.url("/v/720.m3u8").toString(), variant.playbackUrl)
        val request = asked.single()
        assertEquals(master, request.url)
        assertEquals("https://page.example.test/watch", request.pageUrl)
        assertEquals("YFT fixture", request.userAgent)
        assertTrue(request.wantsText)
    }

    @Test
    fun `a refusal the browser gets too names the request and the browsers status`() = runTest {
        val browser = resolverWith { BrowserReadAnswer(status = 474) }
        server.enqueue(MockResponse().setResponseCode(474))
        server.enqueue(MockResponse().setResponseCode(474))

        val refused = browser.resolve(candidate(server.url("/clip.mp4").toString()))
            as VariantResolutionResult.Failure

        assertEquals(474, refused.httpStatusCode)
        assertEquals(ResolutionStep.FILE_CHECK, refused.step)
        assertEquals("range GET", refused.request)
        assertEquals(474, refused.browserStatus)

        // A file the browser got stands in with its length; one it could not ask says 0.
        val length = resolverWith {
            BrowserReadAnswer(status = 200, contentType = "video/mp4", contentLength = 8_192)
        }
        server.enqueue(MockResponse().setResponseCode(474))
        server.enqueue(MockResponse().setResponseCode(474))
        val stated = candidate(server.url("/clip.mp4").toString()).copy(height = 720)
        val found = length.resolve(stated) as VariantResolutionResult.Success
        assertEquals(8_192L, found.asset.variants.single().sizeBytes)

        val none = resolverWith { null }
        server.enqueue(MockResponse().setResponseCode(410))
        server.enqueue(MockResponse().setResponseCode(410))
        val unasked = none.resolve(candidate(server.url("/clip.mp4").toString()))
            as VariantResolutionResult.Failure
        assertEquals(0, unasked.browserStatus)
    }

    @Test
    fun `the browser is never asked after a 404, a HEAD or for a site adapters file`() =
        runTest {
            var asked = 0
            val browser = resolverWith {
                asked += 1
                BrowserReadAnswer(status = 200, contentType = "video/mp4", contentLength = 1)
            }
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            val gone = browser.resolve(candidate(server.url("/clip.mp4").toString()))
                as VariantResolutionResult.Failure
            assertEquals(404, gone.httpStatusCode)
            assertNull(gone.browserStatus)

            server.enqueue(MockResponse().setResponseCode(410))
            server.enqueue(MockResponse().setResponseCode(410))
            val named = candidate(server.url("/clip.mp4").toString()).copy(videoId = "site:1")
            val adapter = browser.resolve(named) as VariantResolutionResult.Failure
            assertEquals(410, adapter.httpStatusCode)
            assertEquals(0, asked)
        }

    /** A resolver that asks [reads] when the site refuses its request. */
    private fun resolverWith(reads: BrowserReads) = DefaultVariantResolver(
        client = OkHttpClient(),
        policy = DefaultVariantResolver.Policy(),
        clock = { NOW },
        browserReads = reads,
    )

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

        /** The codecs of the page's HLS master's two qualities. */
        const val AVC_720 = "avc1.4d401f,mp4a.40.2"
        const val AVC_1080 = "avc1.640028,mp4a.40.2"
    }
}