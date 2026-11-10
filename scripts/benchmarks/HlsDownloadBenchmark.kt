package com.alal.yft.core.download

import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Measures the real host transfer engine against a local synthetic byte server, without remux.
 * These bytes are not media: this cannot establish playback, device speed, memory or disk I/O.
 */
class HlsDownloadBenchmark {
    @get:Rule val directory = TemporaryFolder()
    @Test fun measure() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val bodies = (0 until 12).map { n -> ByteArray(256 * 1024) { i -> (n * 17 + i).toByte() } }
            var heterogeneous = false
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/index.m3u8") {
                        val manifest = buildString {
                            appendLine("#EXTM3U"); appendLine("#EXT-X-TARGETDURATION:4")
                            repeat(12) { appendLine("#EXTINF:4,"); appendLine("$it.ts") }
                            appendLine("#EXT-X-ENDLIST")
                        }
                        return MockResponse().setBody(manifest)
                    }
                    val index = request.path!!.removePrefix("/").removeSuffix(".ts").toInt()
                    val delay = if (heterogeneous && index % 3 == 0) 500L else 60L
                    return MockResponse().setBody(Buffer().write(bodies[index])).setBodyDelay(delay, TimeUnit.MILLISECONDS)
                }
            }
            val client = OkHttpClient()
            for (scenario in listOf("homogeneous", "heterogeneous")) {
                heterogeneous = scenario == "heterogeneous"
                val times = mutableListOf<Long>()
                repeat(5) { round ->
                    val root = directory.newFolder("$scenario-$round")
                    val out = File(root, "done.ts")
                    val engine = HlsTransferEngine(client, File(root, "workspace"), HlsTransferEngine.Policy(maxConcurrentChunks = 3, initialRetryDelayMillis = 0))
                    val plan = HlsDownloadPlan(taskId = "$scenario-$round", playlistUrl = server.url("/index.m3u8").toString(), suggestedFileName = "done.ts", requestContext = BrowserRequestContext(pageUrl = "https://example.test/watch", userAgent = "YFT benchmark", cookie = null), mimeType = "video/mp2t", remuxToMp4 = false)
                    val start = System.nanoTime()
                    val result = engine.transfer(plan, FileDownloadDestination(File(root, "done.part"), out))
                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                    assertTrue("transfer failed: $result", result is HlsTransferResult.Completed)
                    assertArrayEquals(bodies.fold(ByteArray(0)) { all, part -> all + part }, out.readBytes())
                    times += elapsed
                }
                println("HLS_BENCH scenario=$scenario warmup_ms=${times.first()} runs_ms=${times.drop(1).joinToString(",")} segments=12 segment_bytes=262144 concurrency=3")
            }
        } finally { server.shutdown() }
    }
}
