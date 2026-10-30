package com.alal.yft.core.download

import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.Collections
import javax.net.ssl.SSLHandshakeException
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v1.0.2 fix F1 (phone test 59): DASH-IF's Netflix test vector is an https manifest whose
 * `BaseURL` is at http (an https copy of that host has a certificate for another name; the same
 * files are under the manifest's own https address). No request is ever sent in cleartext: the
 * same address at https first, then the part below the `BaseURL` under the manifest's address,
 * the first that answers as media; else the download fails as before, with a detail. And the
 * manifest is read with Android's own XML parser, which knows none of the hardening settings.
 */
class DashHttpBaseUrlTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `an http BaseURL under an https manifest is refused, upgraded or left out`() {
        fun read(choice: DashDownloadManifestParser.HttpBaseUrl, manifest: String = vector()) =
            DashDownloadManifestParser.parse(
                manifest = manifest,
                manifestUrl = MANIFEST.toHttpUrl(),
                representationId = "2",
                expectedTrackType = MediaTrackType.VIDEO,
                maxChunks = 100,
                httpBaseUrl = choice,
            )

        assertEquals(
            DashDownloadManifestParser.Result.HttpBaseUrlRefused,
            read(DashDownloadManifestParser.HttpBaseUrl.REFUSE),
        )
        val upgraded = read(DashDownloadManifestParser.HttpBaseUrl.SAME_AS_HTTPS)
            as DashDownloadManifestParser.Result.OneFile
        assertEquals("$ORIGIN_HTTPS/dash264/netflix/$VIDEO_FILE", upgraded.url.toString())
        val below = read(DashDownloadManifestParser.HttpBaseUrl.UNDER_MANIFEST)
            as DashDownloadManifestParser.Result.OneFile
        assertEquals("$CDN/dash264/netflix/$VIDEO_FILE", below.url.toString())
        assertEquals(983L, below.initialization?.endInclusive)

        // The representation's own BaseURL at http: nothing is below it to look for.
        val own = vector(rootBaseUrl = "", videoBaseUrl = "$ORIGIN_HTTP/dash264/netflix/$VIDEO_FILE")
        assertEquals(
            DashDownloadManifestParser.Result.HttpBaseUrlRefused,
            read(DashDownloadManifestParser.HttpBaseUrl.REFUSE, own),
        )
        assertEquals(
            DashDownloadManifestParser.Result.Malformed,
            read(DashDownloadManifestParser.HttpBaseUrl.UNDER_MANIFEST, own),
        )
        assertEquals(
            "$ORIGIN_HTTPS/dash264/netflix/$VIDEO_FILE",
            (read(DashDownloadManifestParser.HttpBaseUrl.SAME_AS_HTTPS, own)
                as DashDownloadManifestParser.Result.OneFile).url.toString(),
        )
        // An https BaseURL is read as before, whatever the choice.
        val https = vector(rootBaseUrl = "$ORIGIN_HTTPS/dash264/netflix/")
        assertEquals(
            "$ORIGIN_HTTPS/dash264/netflix/$VIDEO_FILE",
            (read(DashDownloadManifestParser.HttpBaseUrl.REFUSE, https)
                as DashDownloadManifestParser.Result.OneFile).url.toString(),
        )
    }

    @Test
    fun `the manifest is read with Android's XML parser, and a document type is refused`() {
        withAndroidParser {
            // The parser does refuse what the hardening asks.
            val factory = DocumentBuilderFactory.newInstance()
            assertTrue(factory is AndroidLikeDocumentBuilderFactory)
            assertTrue(runCatching { factory.isXIncludeAware = false }.isFailure)

            val file = DashDownloadManifestParser.parse(
                manifest = vector(),
                manifestUrl = MANIFEST.toHttpUrl(),
                representationId = "1",
                expectedTrackType = MediaTrackType.AUDIO,
                maxChunks = 100,
                httpBaseUrl = DashDownloadManifestParser.HttpBaseUrl.UNDER_MANIFEST,
            ) as DashDownloadManifestParser.Result.OneFile
            assertEquals("$CDN/dash264/netflix/$AUDIO_FILE", file.url.toString())
            assertEquals(MediaTrackType.AUDIO, file.trackType)

            val doctype = """<?xml version="1.0"?><!DOCTYPE MPD [<!ENTITY x "y">]>""" +
                vector().substringAfter("?>")
            assertEquals(
                DashDownloadManifestParser.Result.Malformed,
                DashDownloadManifestParser.parse(
                    manifest = doctype,
                    manifestUrl = MANIFEST.toHttpUrl(),
                    representationId = "1",
                    expectedTrackType = MediaTrackType.AUDIO,
                    maxChunks = 100,
                ),
            )
        }
    }

    @Test
    fun `the files come from the manifest's https address when the https copy fails`() =
        runBlocking {
            val network = FakeNetwork()
            val video = bytes(3_000)
            network.serve("$CDN/dash264/netflix/tc1.mpd", vector().toByteArray(), MPD_TYPE)
            network.serve("$CDN/dash264/netflix/$VIDEO_FILE", video, "video/mp4")
            network.badCertificate += "origin.example.test"
            val out = destination("below")

            val result = engine(network).transfer(
                plan = plan("below", "2", MediaTrackType.VIDEO),
                destination = out.second,
            ) as DashTransferResult.Completed

            assertArrayEquals(video, out.first.readBytes())
            assertEquals(3_000L, result.bytesWritten)
            assertTrue(network.asked.none { it.startsWith("http:") })
            // The https copy of the BaseURL first, then the manifest's own address.
            assertEquals("$ORIGIN_HTTPS/dash264/netflix/$VIDEO_FILE", network.asked[1])
            assertTrue(network.asked.drop(2).all { it == "$CDN/dash264/netflix/$VIDEO_FILE" })
        }

    @Test
    fun `the https copy of the BaseURL is read when it answers as media`() = runBlocking {
        val network = FakeNetwork()
        val sound = bytes(1_500)
        network.serve("$CDN/dash264/netflix/tc1.mpd", vector().toByteArray(), MPD_TYPE)
        network.serve("$ORIGIN_HTTPS/dash264/netflix/$AUDIO_FILE", sound, "audio/mp4")
        val out = destination("upgraded")

        engine(network).transfer(
            plan = plan("upgraded", "1", MediaTrackType.AUDIO),
            destination = out.second,
        ) as DashTransferResult.Completed

        assertArrayEquals(sound, out.first.readBytes())
        assertTrue(network.asked.none { it.startsWith("http:") })
        assertTrue(network.asked.none { it == "$CDN/dash264/netflix/$AUDIO_FILE" })
    }

    @Test
    fun `pieces under an http BaseURL come from its https copy`() = runBlocking {
        val network = FakeNetwork()
        network.serve("$CDN/live/pieces.mpd", templateManifest().toByteArray(), MPD_TYPE)
        val init = bytes(40)
        val first = bytes(60, salt = 3)
        val second = bytes(70, salt = 5)
        network.serve("$ORIGIN_HTTPS/pieces/init-v1.mp4", init, "video/mp4")
        network.serve("$ORIGIN_HTTPS/pieces/v1-1.m4s", first, "video/iso.segment")
        network.serve("$ORIGIN_HTTPS/pieces/v1-2.m4s", second, "video/iso.segment")
        val out = destination("pieces")

        engine(network).transfer(
            plan = plan("pieces", "v1", MediaTrackType.VIDEO, manifest = "$CDN/live/pieces.mpd"),
            destination = out.second,
        ) as DashTransferResult.Completed

        assertArrayEquals(init + first + second, out.first.readBytes())
        assertTrue(network.asked.none { it.startsWith("http:") })
    }

    @Test
    fun `no https copy that answers as media fails as before, saying why`() = runBlocking {
        val network = FakeNetwork()
        network.serve("$CDN/dash264/netflix/tc1.mpd", vector().toByteArray(), MPD_TYPE)
        network.badCertificate += "origin.example.test"
        // The manifest's host answers with a page, not the file.
        network.serve("$CDN/dash264/netflix/$VIDEO_FILE", "<html></html>".toByteArray(), "text/html")
        val out = destination("none")

        val result = engine(network).transfer(
            plan = plan("none", "2", MediaTrackType.VIDEO),
            destination = out.second,
        ) as DashTransferResult.Failure

        assertEquals(DownloadFailureReason.MALFORMED_RESPONSE, result.failure.reason)
        assertEquals(DownloadFailureStage.READ_SOURCE, result.failure.stage)
        assertEquals(
            "the manifest names its files at an http address (BaseURL); no https copy answered",
            result.failure.detail,
        )
        assertTrue(network.asked.none { it.startsWith("http:") })
        assertEquals(3, network.asked.size)
    }

    private fun engine(network: FakeNetwork): DashTransferEngine = DashTransferEngine(
        client = OkHttpClient.Builder().addInterceptor(network).build(),
        workspaceRoot = directory.newFolder(),
        policy = DashTransferEngine.Policy(
            maxAttempts = 1,
            initialRetryDelayMillis = 0,
            maxChunkBytes = 1_000,
            maxConcurrentChunks = 1,
            bufferBytes = 1_024,
            firstRangeBytes = 400,
            progressIntervalMillis = 0,
        ),
        clock = { 100 },
        log = {},
    )

    private fun plan(
        id: String,
        representationId: String,
        trackType: MediaTrackType,
        manifest: String = "$CDN/dash264/netflix/tc1.mpd",
    ) = DashDownloadPlan(
        taskId = "http-base-$id",
        manifestUrl = manifest,
        representationId = representationId,
        trackType = trackType,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT http BaseURL fixture",
            cookie = null,
        ),
        mimeType = if (trackType == MediaTrackType.AUDIO) "audio/mp4" else "video/mp4",
        codecs = emptyList(),
    )

    private fun destination(name: String): Pair<File, FileDownloadDestination> {
        val parent = directory.newFolder("out-$name")
        val completed = File(parent, "$name.mp4")
        return completed to FileDownloadDestination(File(parent, "$name.mp4.part"), completed)
    }

    private fun bytes(size: Int, salt: Int = 0): ByteArray =
        ByteArray(size) { index -> ((index + salt) % 251).toByte() }

    /** The test vector's shape: a root BaseURL at http, each representation one file. */
    private fun vector(
        rootBaseUrl: String = "$ORIGIN_HTTP/dash264/netflix/",
        videoBaseUrl: String = VIDEO_FILE,
    ): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
            mediaPresentationDuration="PT654S" minBufferTime="PT4S"
            profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
          ${if (rootBaseUrl.isEmpty()) "" else "<BaseURL>$rootBaseUrl</BaseURL>"}
          <Period>
            <AdaptationSet mimeType="audio/mp4" codecs="mp4a.40.5" lang="en">
              <Representation id="1" bandwidth="72000">
                <BaseURL>$AUDIO_FILE</BaseURL>
                <SegmentBase indexRange="871-1131"><Initialization range="0-870"/></SegmentBase>
              </Representation>
            </AdaptationSet>
            <AdaptationSet mimeType="video/mp4" codecs="avc1.42401E" contentType="video">
              <Representation id="2" bandwidth="150000" width="480" height="360" frameRate="24">
                <BaseURL>$videoBaseUrl</BaseURL>
                <SegmentBase indexRange="984-1244"><Initialization range="0-983"/></SegmentBase>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun templateManifest(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT4S">
          <BaseURL>$ORIGIN_HTTP/pieces/</BaseURL>
          <Period>
            <AdaptationSet contentType="video" mimeType="video/mp4" codecs="avc1.4d401f">
              <SegmentTemplate initialization="init-${'$'}RepresentationID${'$'}.mp4"
                  media="${'$'}RepresentationID${'$'}-${'$'}Number${'$'}.m4s"
                  duration="2" timescale="1" startNumber="1"/>
              <Representation id="v1" bandwidth="100000" width="640" height="360"/>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    /**
     * The network as an interceptor: each address's bytes (ranges answered), a host whose https
     * certificate does not fit, 404 for the rest; every address asked, in order.
     */
    private class FakeNetwork : Interceptor {
        private val files = mutableMapOf<String, Pair<ByteArray, String>>()
        val badCertificate = mutableSetOf<String>()
        val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

        fun serve(url: String, bytes: ByteArray, type: String) {
            files[url] = bytes to type
        }

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
            if (range == null) {
                return builder.code(200).message("OK").header("Content-Type", type)
                    .body(bytes.toResponseBody(type.toMediaType())).build()
            }
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
        const val CDN = "https://cdn.example.test"
        const val ORIGIN_HTTP = "http://origin.example.test"
        const val ORIGIN_HTTPS = "https://origin.example.test"
        const val MANIFEST = "$CDN/dash264/netflix/tc1.mpd"
        const val AUDIO_FILE = "ElephantsDream_AAC48K_064.mp4.dash"
        const val VIDEO_FILE = "ElephantsDream_H264BPL30_0100.264.dash"
        const val MPD_TYPE = "application/dash+xml"
    }
}

/**
 * Android's own `DocumentBuilderFactory` (`org.apache.harmony.xml.parsers`) as it answers: no
 * XInclude setting, no feature but namespaces and validation, no attribute. The documents
 * themselves come from the JVM's parser.
 */
class AndroidLikeDocumentBuilderFactory : DocumentBuilderFactory() {
    override fun newDocumentBuilder(): DocumentBuilder =
        newInstance(JVM_FACTORY, AndroidLikeDocumentBuilderFactory::class.java.classLoader)
            .also { it.isNamespaceAware = isNamespaceAware }
            .newDocumentBuilder()

    override fun setXIncludeAware(state: Boolean): Unit =
        throw UnsupportedOperationException("This parser does not support specification")

    override fun setAttribute(name: String, value: Any?): Unit = throw IllegalArgumentException(name)

    override fun getAttribute(name: String): Any = throw IllegalArgumentException(name)

    override fun setFeature(name: String, value: Boolean) {
        if (name != NAMESPACES && name != VALIDATION) throw ParserConfigurationException(name)
    }

    override fun getFeature(name: String): Boolean = when (name) {
        NAMESPACES -> isNamespaceAware
        VALIDATION -> isValidating
        else -> throw ParserConfigurationException(name)
    }

    private companion object {
        const val JVM_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl"
        const val NAMESPACES = "http://xml.org/sax/features/namespaces"
        const val VALIDATION = "http://xml.org/sax/features/validation"
    }
}

/** Runs [block] with [AndroidLikeDocumentBuilderFactory] as the JVM's factory. */
fun withAndroidParser(block: () -> Unit) {
    val property = "javax.xml.parsers.DocumentBuilderFactory"
    val before = System.getProperty(property)
    System.setProperty(property, AndroidLikeDocumentBuilderFactory::class.java.name)
    try {
        block()
    } finally {
        if (before == null) System.clearProperty(property) else System.setProperty(property, before)
    }
}
