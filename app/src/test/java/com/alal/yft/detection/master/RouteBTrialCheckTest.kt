package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B) F7: the route-B trial's comparison of Master's rows with
 * main's set-aside rows of the same lookup — the files' one-byte check now, whether the links and
 * the download headers are main's, and which names differ. Names, statuses and counts only.
 */
class RouteBTrialCheckTest {
    @Test
    fun mainsOwnLinksAreSaidSameAndEachDistinctFileIsCheckedOnce() = runTest {
        val checked = mutableListOf<String>()
        val rows = listOf(row(VIDEO_134, AUDIO_140), row(VIDEO_133, AUDIO_140))

        val segment = RouteBTrialCheck.segment(
            rows,
            rows.map { it.copy() },
            RouteBTrialCheck.FileCheck { url, _ ->
                checked += url
                403
            },
        )

        assertEquals("; check: 403×3; same as main's: 2 of 2 rows, headers same", segment)
        assertEquals(listOf(VIDEO_134, AUDIO_140, VIDEO_133), checked)
    }

    @Test
    fun anotherLinkNamesWhatDiffersAndChecksMainsLinkToo() = runTest {
        val master = row("$VIDEO_134&n=AAAA", AUDIO_140, agent = "Agent-One")
        val main = row("$VIDEO_134&n=BBBB&pot=SECRETPOT", AUDIO_140, agent = "Agent-Two")

        val segment = RouteBTrialCheck.segment(
            listOf(master),
            listOf(main),
            RouteBTrialCheck.FileCheck { url, _ -> if ("pot=" in url) 206 else 403 },
        )

        assertEquals(
            "; check: 403×2; same as main's: 0 of 1 rows, headers differ (User-Agent); differ: n; " +
                "main's only: pot; main's check: 206×1",
            segment,
        )
        for (value in listOf("AAAA", "BBBB", "SECRETPOT", "Agent-One", "Agent-Two", "googlevideo", "SIG1")) {
            assertFalse(value, value in segment)
        }
    }

    @Test
    fun aStreamMainNeverHadIsCountedAndNoCheckRunsWithoutOne() = runTest {
        val segment = RouteBTrialCheck.segment(listOf(row(VIDEO_133)), listOf(row(VIDEO_134)), check = null)

        assertEquals("; same as main's: 0 of 1 rows, no main's stream to compare; streams not in main's: 1", segment)
    }

    @Test
    fun oddOrManyNamesStayShort() = runTest {
        val master = row("$VIDEO_134&a=1&b=1&c2=1&d=1&e=1&f=1&g=1&%3Cx%3E=1")
        val main = row(VIDEO_134)

        val segment = RouteBTrialCheck.segment(listOf(master), listOf(main), check = null)

        assertEquals(
            "; same as main's: 0 of 1 rows, headers same; Master's only: a, b, c2, d, e, f, …",
            segment,
        )
        val odd = RouteBTrialCheck.segment(listOf(row("$VIDEO_134&%3Cx%3E=1")), listOf(main), check = null)
        assertEquals("; same as main's: 0 of 1 rows, headers same; Master's only: ?", odd)
    }

    /**
     * D7 run 11's first trial APK closed its answers on the caller's thread, the main thread (route
     * B's lookup runs there): an answer whose body has not all arrived then reads the network on
     * it, which Android stops with a crash. The check now asks, reads and closes off that thread.
     */
    @Test
    fun theFileCheckNeverReadsOrWritesTheNetworkOnItsCallersThread() {
        val server = MockWebServer()
        // A refusal whose short body comes in two parts, the second one late.
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
                .throttleBody(4, 300, TimeUnit.MILLISECONDS),
        )
        server.start()
        // The thread itself (a coroutine's debug name can be added to a thread's name).
        val callerThread = AtomicReference<Thread>()
        val caller = Executors.newSingleThreadExecutor { task -> Thread(task).also(callerThread::set) }
            .asCoroutineDispatcher()
        try {
            val callerTouches = AtomicInteger()
            val client = OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .socketFactory(
                    WatchedSockets { if (Thread.currentThread() === callerThread.get()) callerTouches.incrementAndGet() },
                )
                .build()

            val status = runBlocking(caller) {
                RouteBTrialCheck.OkHttpFileCheck(client).status(
                    server.url("/videoplayback?itag=134").toString(),
                    BrowserRequestContext(PAGE, "Agent-Check", null),
                )
            }

            assertEquals(403, status)
            assertEquals("network reads or writes on the caller's thread", 0, callerTouches.get())
            val request = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(request)
            assertEquals("bytes=0-0", request!!.getHeader("Range"))
            assertEquals("identity", request.getHeader("Accept-Encoding"))
            assertEquals("Agent-Check", request.getHeader("User-Agent"))
        } finally {
            caller.close()
            server.shutdown()
        }
    }

    /** Plain sockets that report each read, write or flush, as Android's main-thread rule sees them. */
    private class WatchedSockets(private val touched: () -> Unit) : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun getInputStream(): InputStream {
                val stream = super.getInputStream()
                return object : FilterInputStream(stream) {
                    override fun read(): Int {
                        touched()
                        return super.read()
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        touched()
                        return super.read(b, off, len)
                    }
                }
            }

            override fun getOutputStream(): OutputStream {
                val stream = super.getOutputStream()
                return object : FilterOutputStream(stream) {
                    override fun write(b: Int) {
                        touched()
                        stream.write(b)
                    }

                    override fun write(b: ByteArray, off: Int, len: Int) {
                        touched()
                        stream.write(b, off, len)
                    }

                    override fun flush() {
                        touched()
                        stream.flush()
                    }
                }
            }
        }

        override fun createSocket(host: String?, port: Int): Socket = unsupported()

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            unsupported()

        override fun createSocket(host: InetAddress?, port: Int): Socket = unsupported()

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int,
        ): Socket = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("OkHttp asks createSocket()")
    }

    private fun row(video: String, audio: String? = null, agent: String = "Agent") = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = video,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        requestContext = BrowserRequestContext(PAGE, agent, null),
        audioCompanion = audio?.let {
            CompanionAudio(it, "audio/mp4", listOf("mp4a.40.2"), BrowserRequestContext(PAGE, agent, null))
        },
    )

    private companion object {
        const val PAGE = "https://m.youtube.com/watch?v=Yft0Fixture"
        const val MEDIA = "https://rr1---sn-test.googlevideo.com/videoplayback?expire=1900000000&c=VISIONOS&sig=SIG1"
        const val VIDEO_134 = "$MEDIA&itag=134"
        const val VIDEO_133 = "$MEDIA&itag=133"
        const val AUDIO_140 = "$MEDIA&itag=140"
    }
}
