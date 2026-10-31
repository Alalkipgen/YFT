package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxEngine
import com.alal.yft.core.download.DashTransferEngine
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.media.resolver.DefaultVariantResolver
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.detection.DashSoundPairing
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import com.alal.yft.feature.quickdownload.QuickDownloadChoices
import com.alal.yft.feature.quickdownload.SheetOption
import com.alal.yft.feature.quickdownload.SheetSource
import java.io.File
import java.nio.ByteBuffer
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 fix R3 on the CI emulator (API 34), with Android's own XML parser and muxer: the
 * owner's `bbb_30fps.mpd` (one `SegmentTemplate` for each adaptation set; its sound `bbb_a64k`,
 * HE-AAC with no language, an `Accessibility` and a `Role`) gives the sheet its M4A and every
 * video with that sound, and a quality in pieces of that shape downloads as one MP4 with its
 * picture and its sound. The network is in memory.
 */
@RunWith(AndroidJUnit4::class)
class DashPiecesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val workspace = File(instrumentation.targetContext.cacheDir, "r3-${UUID.randomUUID()}")
    private val network = InMemoryNetwork()

    @After
    fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test
    fun bbb30fpsHasItsM4aAndEveryVideoWithThatSound() = runBlocking {
        network.serve(C_URL, assetBytes("dash/bbb_30fps.mpd"), DASH)
        val candidate = candidate(C_URL)

        val asset = read(candidate)
        val sound = asset.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals("bbb_a64k", sound.manifestVariantId)
        assertEquals(listOf("mp4a.40.5"), sound.codecs)
        assertTrue(sound.dashPieces)
        val videos = asset.variants.filter { it.trackType != MediaTrackType.AUDIO }
        assertEquals(10, videos.size)
        videos.forEach { video ->
            assertEquals(video.manifestVariantId, MediaTrackType.AUDIO_VIDEO, video.trackType)
            assertEquals("bbb_a64k", video.audioCompanion?.representationId)
        }
        val rows = requireNotNull(
            QuickDownloadChoices.of(
                MediaGroups.of(listOf(candidate)).single(),
                listOf(SheetSource(candidate, asset, resolved = true)),
                VideoPlaybackSupport.ANY,
            ),
        )
        assertEquals(listOf("M4A"), rows.audio.map(SheetOption::title))
        assertEquals("67 kbps", rows.audio.single().detail)
        assertEquals(
            listOf("2160p · 4K", "1080p · Full HD", "720p · HD", "480p", "360p", "240p", "144p"),
            rows.video.map(SheetOption::title),
        )
        assertTrue(rows.video.none { QuickDownloadChoices.NO_SOUND in it.chips })
    }

    @Test
    fun aQualityInPiecesDownloadsAsOneMp4WithItsSound() = runBlocking {
        network.serve(CLIP_URL, CLIP.replace('#', '$').toByteArray(), DASH)
        val pictures = serveInPieces("mux/video-avc.mp4", "$BASE/clip_v/clip_v", "m4v")
        val sounds = serveInPieces("mux/audio-aac.m4a", "$BASE/clip_a/clip_a", "m4a")
        assertEquals(3, pictures.size)
        assertEquals(4, sounds.size)
        val candidate = candidate(CLIP_URL)
        val asset = read(candidate)
        val video = asset.variants.single { it.trackType != MediaTrackType.AUDIO }
        assertEquals("clip_a", video.audioCompanion?.representationId)

        val planned = DownloadPlanFactory.create(asset, video, "r3-merge", NOW, av1Enabled = false)
        val request = (planned as DownloadPlanResult.Ready).request as DownloadRequest.Mux
        assertEquals("clip_v", request.plan.video.representationId)
        assertEquals("clip_a", request.plan.audio.representationId)
        workspace.mkdirs()
        val merged = File(workspace, "clip.mp4")
        val engine = AudioVideoMuxEngine(
            dashTransfer = DashTransferEngine(
                client = network.client(),
                workspaceRoot = File(workspace, "tracks"),
                log = {},
            ),
            muxer = AndroidMp4AudioVideoMuxer(),
            workspaceRoot = File(workspace, "merge"),
            log = {},
        )
        val result = engine.transfer(
            plan = request.plan,
            destination = FileDownloadDestination(File(workspace, "clip.mp4.part"), merged),
        )

        assertTrue("$result", result is AudioVideoMuxResult.Completed)
        assertTrue(network.asked.containsAll(pictures + sounds))
        assertEquals(
            listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC),
            trackTypes(merged),
        )
        assertEquals(15, sampleCount(merged, track = 0))
        val source = File(workspace, "audio-aac.m4a").apply {
            writeBytes(assetBytes("mux/audio-aac.m4a"))
        }
        assertEquals(sampleCount(source, track = 0), sampleCount(merged, track = 1))
    }

    /** What the sheet reads for [candidate]: the manifest's rows, each video with its sound. */
    private suspend fun read(candidate: MediaCandidate): MediaAsset {
        val resolver = DefaultVariantResolver(
            client = network.client(),
            policy = DefaultVariantResolver.Policy(),
            clock = { NOW },
        )
        val result = DashSoundPairing.pair(resolver.resolve(candidate), candidate)
        return (result as VariantResolutionResult.Success).asset
    }

    /**
     * Serves the fragmented MP4 [file] as a manifest's pieces: its header (`ftyp`, `moov`) as
     * `<prefix>_0`, then each `moof` with its `mdat` as `<prefix>_1`, `_2`, …; the fragments'
     * addresses, in order.
     */
    private fun serveInPieces(file: String, prefix: String, extension: String): List<String> {
        val boxes = boxesOf(assetBytes(file))
        val header = boxes.filter { (type) -> type in HEADER }
            .map { (_, bytes) -> bytes }
            .reduce { all, box -> all + box }
        val fragments = boxes.filter { (type) -> type == "moof" || type == "mdat" }
            .map { (_, bytes) -> bytes }
            .chunked(2) { (moof, mdat) -> moof + mdat }
        val type = if (extension == "m4a") "audio/mp4" else "video/mp4"
        network.serve("${prefix}_0.$extension", header, type)
        return fragments.mapIndexed { index, bytes ->
            "${prefix}_${index + 1}.$extension".also { url -> network.serve(url, bytes, type) }
        }
    }

    /** The top-level boxes of an MP4 file: each one's type and its bytes. */
    private fun boxesOf(bytes: ByteArray): List<Pair<String, ByteArray>> = buildList {
        var offset = 0
        while (offset + 8 <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, offset, 4).int
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            require(size >= 8 && offset + size <= bytes.size) { "box $type at $offset" }
            add(type to bytes.copyOfRange(offset, offset + size))
            offset += size
        }
    }

    private fun trackTypes(file: File): List<String?> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
        } finally {
            extractor.release()
        }
    }

    /** The samples of [file]'s [track], read from its start. */
    private fun sampleCount(file: File, track: Int): Int {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            extractor.selectTrack(track)
            val buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
            var count = 0
            while (extractor.readSampleData(buffer, 0) >= 0) {
                count += 1
                extractor.advance()
            }
            return count
        } finally {
            extractor.release()
        }
    }

    private fun assetBytes(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private fun candidate(url: String) = MediaCandidate(
        pageUrl = url,
        mediaUrl = url,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DASH,
        mimeType = DASH,
        requestContext = BrowserRequestContext(url, "YFT R3 instrumented test", null),
        confidence = CandidateConfidence.HIGH,
        observedAtEpochMs = NOW,
    )

    /** Each address's bytes (ranges answered), else 404; every address asked. */
    private class InMemoryNetwork : Interceptor {
        private val files: MutableMap<String, Pair<ByteArray, String>> =
            Collections.synchronizedMap(mutableMapOf())
        val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

        fun serve(url: String, bytes: ByteArray, type: String) {
            files[url] = bytes to type
        }

        fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()
            asked += url
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            val (bytes, type) = files[url]
                ?: return builder.code(404).message("Not Found")
                    .body(ByteArray(0).toResponseBody()).build()
            val range = request.header("Range")?.let(RANGE::matchEntire)
                ?: return builder.code(200).message("OK").header("Content-Type", type)
                    .body(bytes.toResponseBody(type.toMediaType())).build()
            val start = range.groupValues[1].toInt()
            val end = minOf(range.groupValues[2].toInt(), bytes.size - 1)
            return builder.code(206).message("Partial Content")
                .header("Content-Type", type)
                .header("Content-Range", "bytes $start-$end/${bytes.size}")
                .body(bytes.copyOfRange(start, end + 1).toResponseBody(type.toMediaType()))
                .build()
        }

        private companion object {
            val RANGE = Regex("""bytes=(\d+)-(\d+)""")
        }
    }

    private companion object {
        const val NOW = 2_000_000_000_000L
        const val DASH = "application/dash+xml"
        const val SAMPLE_BUFFER_BYTES = 1_024 * 1_024
        const val C_URL = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
        const val BASE = "https://media.example.test/r3"
        const val CLIP_URL = "$BASE/clip.mpd"
        val HEADER = setOf("ftyp", "moov")

        /**
         * `bbb_30fps.mpd`'s shape for the one-second test tracks (`#` stands for `$`): the
         * video's 3 pieces of 5 frames at 15 fps, the sound's 4 pieces of up to 13 AAC frames.
         */
        val CLIP = """
            <MPD mediaPresentationDuration="PT1S" minBufferTime="PT2.00S" type="static"
              profiles="urn:mpeg:dash:profile:isoff-live:2011"
              xmlns="urn:mpeg:dash:schema:mpd:2011">
             <BaseURL>./</BaseURL>
             <Period>
              <AdaptationSet mimeType="video/mp4" contentType="video" par="16:9">
               <SegmentTemplate duration="5120" timescale="15360" startNumber="1"
                 media="#RepresentationID#/#RepresentationID#_#Number#.m4v"
                 initialization="#RepresentationID#/#RepresentationID#_0.m4v"/>
               <Representation id="clip_v" codecs="avc1.4d400a" bandwidth="60000" width="160"
                 height="90" frameRate="15" sar="1:1"/>
              </AdaptationSet>
              <AdaptationSet mimeType="audio/mp4" contentType="audio">
               <Accessibility schemeIdUri="urn:tva:metadata:cs:AudioPurposeCS:2007" value="6"/>
               <Role schemeIdUri="urn:mpeg:dash:role:2011" value="main"/>
               <SegmentTemplate duration="13312" timescale="44100" startNumber="1"
                 media="#RepresentationID#/#RepresentationID#_#Number#.m4a"
                 initialization="#RepresentationID#/#RepresentationID#_0.m4a"/>
               <Representation id="clip_a" codecs="mp4a.40.2" bandwidth="64000"
                 audioSamplingRate="44100">
                <AudioChannelConfiguration
                  schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
               </Representation>
              </AdaptationSet>
             </Period>
            </MPD>
        """.trimIndent()
    }
}
