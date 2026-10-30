package com.alal.yft.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.DashTransferEngine
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.media.resolver.DefaultVariantResolver
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.VariantResolutionResult
import java.io.File
import java.util.Collections
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 fix F1 (phone test 59) on the CI emulator (API 34), with Android's own XML parser: the
 * shape of DASH-IF's Netflix test vector (an https manifest, its root `BaseURL` at http, each
 * representation one file) is read into its rows, and a track is downloaded from the manifest's
 * own https address when the https copy of the `BaseURL` fails; nothing is asked in cleartext.
 * The network is in memory.
 */
@RunWith(AndroidJUnit4::class)
class DashManifestInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workspace = File(context.cacheDir, "f1-${UUID.randomUUID()}")
    private val network = InMemoryNetwork()

    @After
    fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test
    fun theTestVectorsManifestIsReadIntoItsRows() = runBlocking {
        network.serve(MANIFEST, VECTOR.toByteArray(), "application/dash+xml")
        val resolver = DefaultVariantResolver(
            client = network.client(),
            policy = DefaultVariantResolver.Policy(),
            clock = { NOW },
        )

        val result = resolver.resolve(
            MediaCandidate(
                pageUrl = MANIFEST,
                mediaUrl = MANIFEST,
                sources = setOf(CandidateSource.REQUEST),
                kind = MediaKind.DASH,
                requestContext = context(),
                confidence = CandidateConfidence.HIGH,
                observedAtEpochMs = NOW,
            ),
        )

        val asset = (result as VariantResolutionResult.Success).asset
        val sound = asset.variants.single { it.trackType == MediaTrackType.AUDIO }
        assertEquals("en", sound.language)
        val video = asset.variants.filter { it.trackType == MediaTrackType.VIDEO }
        assertEquals(2, video.size)
        assertTrue(video.all { it.width == 480 && it.height == 360 && it.dashOneFile })
        assertTrue(network.asked.none { it.startsWith("http:") })
    }

    @Test
    fun aTrackComesFromTheManifestsHttpsAddress() = runBlocking {
        val sound = ByteArray(1_500) { index -> (index % 251).toByte() }
        network.serve(MANIFEST, VECTOR.toByteArray(), "application/dash+xml")
        network.serve("$CDN/netflix/$AUDIO_FILE", sound, "audio/mp4")
        network.badCertificate += ORIGIN_HOST
        workspace.mkdirs()
        val completed = File(workspace, "sound.m4a")
        val engine = DashTransferEngine(
            client = network.client(),
            workspaceRoot = File(workspace, "work"),
            log = {},
        )

        val result = engine.transfer(
            plan = DashDownloadPlan(
                taskId = "f1-sound",
                manifestUrl = MANIFEST,
                representationId = "1",
                trackType = MediaTrackType.AUDIO,
                suggestedFileName = "sound.m4a",
                requestContext = context(),
                mimeType = "audio/mp4",
                codecs = emptyList(),
            ),
            destination = FileDownloadDestination(File(workspace, "sound.m4a.part"), completed),
        )

        assertTrue(result is DashTransferResult.Completed)
        assertArrayEquals(sound, completed.readBytes())
        assertTrue(network.asked.none { it.startsWith("http:") })
        assertTrue(network.asked.contains("https://$ORIGIN_HOST/netflix/$AUDIO_FILE"))
    }

    private fun context() = BrowserRequestContext(
        pageUrl = MANIFEST,
        userAgent = "YFT F1 instrumented test",
        cookie = null,
    )

    /** Each address's bytes (ranges answered), a host whose certificate does not fit, 404. */
    private class InMemoryNetwork : Interceptor {
        private val files = mutableMapOf<String, Pair<ByteArray, String>>()
        val badCertificate = mutableSetOf<String>()
        val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

        fun serve(url: String, bytes: ByteArray, type: String) {
            files[url] = bytes to type
        }

        fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()
            asked += url
            if (request.url.host in badCertificate) {
                throw SSLHandshakeException("certificate for another host")
            }
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
        const val CDN = "https://cdn.example.test/dash264"
        const val ORIGIN_HOST = "origin.example.test"
        const val MANIFEST = "$CDN/netflix/tc1.mpd"
        const val AUDIO_FILE = "ElephantsDream_AAC48K_064.mp4.dash"
        val VECTOR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
              mediaPresentationDuration="PT654S" minBufferTime="PT4S"
              profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
              <BaseURL>http://$ORIGIN_HOST/netflix/</BaseURL>
              <Period>
                <AdaptationSet mimeType="audio/mp4" codecs="mp4a.40.5" lang="en">
                  <Representation id="1" bandwidth="72000">
                    <BaseURL>$AUDIO_FILE</BaseURL>
                    <SegmentBase indexRange="871-1131"><Initialization range="0-870"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="video/mp4" codecs="avc1.42401E" contentType="video">
                  <Representation id="2" bandwidth="150000" width="480" height="360" frameRate="24">
                    <BaseURL>ElephantsDream_H264BPL30_0100.264.dash</BaseURL>
                    <SegmentBase indexRange="984-1244"><Initialization range="0-983"/></SegmentBase>
                  </Representation>
                  <Representation id="5" bandwidth="750000" width="480" height="360" frameRate="24">
                    <BaseURL>ElephantsDream_H264BPL30_0500.264.dash</BaseURL>
                    <SegmentBase indexRange="985-1245"><Initialization range="0-984"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
    }
}
