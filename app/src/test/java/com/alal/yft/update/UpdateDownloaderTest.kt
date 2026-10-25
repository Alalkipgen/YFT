package com.alal.yft.update

import com.alal.yft.detection.ExtractorTlsFixture
import com.alal.yft.update.Updates.found
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The release's checksum and APK from a local HTTPS server (a test-only certificate). */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateDownloaderTest {
    private val tls = ExtractorTlsFixture()
    private val logs = mutableListOf<String>()
    private val directory: File = Files.createTempDirectory("updates").toFile()
    private val bytes = Random(7).nextBytes(200 * 1024)
    private val sha256 = UpdatePolicy.sha256Hex(bytes)

    @After
    fun tearDown() {
        tls.close()
        directory.deleteRecursively()
    }

    private fun TestScope.downloader() = downloader(UnconfinedTestDispatcher(testScheduler))

    private fun downloader(io: CoroutineDispatcher) = UpdateDownloader(
        client = tls.client,
        allowedHosts = setOf("localhost"),
        directory = directory,
        ioDispatcher = io,
        log = { logs += it },
    )

    private fun update(size: Long = bytes.size.toLong(), sums: String? = SUMS_PATH) =
        found("v1.0.1").copy(
            apkUrl = tls.url(APK_PATH).toString(),
            apkSize = size,
            sumsUrl = sums?.let { tls.url(it).toString() },
        )

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun apk(body: ByteArray = bytes) =
        MockResponse().setResponseCode(200).setBody(Buffer().write(body))

    private fun chunked(body: ByteArray) =
        MockResponse().setResponseCode(200).setChunkedBody(Buffer().write(body), 4096)

    private fun enqueue(vararg responses: MockResponse) = responses.forEach(tls.server::enqueue)

    private val target: File get() = File(directory, UpdateDownloader.FILE_NAME)

    @Test
    fun theChecksumIsTheApksOwnLine() = runTest(UnconfinedTestDispatcher()) {
        enqueue(
            ok(
                "${"a".repeat(64)}  video-downloader-1.0.1.apk.idsig\r\n" +
                    "${sha256.lowercase()}  video-downloader-1.0.1.apk\r\n",
            ),
        )

        val result = downloader().checksum(update())

        assertEquals(SumsResult.Found(sha256), result)
        val request = tls.server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals(SUMS_PATH, request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals(listOf("Update checksum: localhost 200"), logs)
    }

    @Test
    fun noListOrNoLineForTheApkIsNoChecksum() = runTest(UnconfinedTestDispatcher()) {
        enqueue(
            MockResponse().setResponseCode(404),
            ok("$sha256  video-downloader-1.0.0.apk\n"),
        )

        assertEquals(SumsResult.Missing, downloader().checksum(update()))
        assertEquals(SumsResult.Missing, downloader().checksum(update()))
        // A release without the asset asks nothing.
        assertEquals(SumsResult.Missing, downloader().checksum(update(sums = null)))
        assertEquals(2, tls.server.requestCount)
    }

    @Test
    fun anOversizeListAnotherAnswerOrNoConnectionFail() = runTest(UnconfinedTestDispatcher()) {
        val long = "$sha256  video-downloader-1.0.1.apk\n" + "#".repeat(16 * 1024)
        enqueue(ok(long), MockResponse().setResponseCode(500))
        val update = update()

        assertEquals(SumsResult.Failed, downloader().checksum(update))
        assertEquals(SumsResult.Failed, downloader().checksum(update))
        // No server at all (a kept connection would take a queued answer as a 200).
        tls.close()
        assertEquals(SumsResult.Offline, downloader().checksum(update))
    }

    @Test
    fun theApkIsWrittenAndHashedAsItComes() = runTest(UnconfinedTestDispatcher()) {
        enqueue(apk())
        val progress = mutableListOf<Int>()
        val downloader = downloader()

        val result = downloader.download(update(), sha256) { progress += it }

        assertEquals(DownloadResult.Done(target), result)
        assertEquals(target, downloader.file)
        assertArrayEquals(bytes, target.readBytes())
        assertEquals(100, progress.last())
        assertEquals(progress.sorted().distinct(), progress)
        val request = tls.server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals(APK_PATH, request.path)
        assertNull(request.getHeader("Cookie"))
        assertEquals(listOf("Update download: localhost 200"), logs)
    }

    @Test
    fun anotherHashOrSizeIsDamagedAndLeavesNoFile() = runTest(UnconfinedTestDispatcher()) {
        val changed = bytes.copyOf().also { it[1234] = (it[1234] + 1).toByte() }
        enqueue(
            apk(changed),
            // Content-Length says another size: nothing is written.
            apk(bytes.copyOf(bytes.size - 1)),
            // No Content-Length: the bytes themselves are counted.
            chunked(bytes + byteArrayOf(1)),
            chunked(bytes.copyOf(bytes.size - 10)),
        )
        val downloader = downloader()

        repeat(4) {
            assertEquals(DownloadResult.Damaged, downloader.download(update(), sha256) {})
            assertFalse(target.exists())
        }
    }

    @Test
    fun redirectsOnlyToAllowedHttpsHosts() = runTest(UnconfinedTestDispatcher()) {
        val plain = tls.url("/asset").newBuilder().scheme("http").build()
        enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "/asset"),
            apk(),
            MockResponse().setResponseCode(302).setHeader("Location", plain.toString()),
            MockResponse().setResponseCode(302)
                .setHeader("Location", "https://example.com/asset"),
        )
        val downloader = downloader()

        assertTrue(downloader.download(update(), sha256) {} is DownloadResult.Done)
        assertEquals(DownloadResult.Failed, downloader.download(update(), sha256) {})
        assertEquals(DownloadResult.Failed, downloader.download(update(), sha256) {})
        assertFalse(target.exists())
        assertEquals(4, tls.server.requestCount)
        // Hosts and statuses only: no address is logged.
        assertTrue(logs.none { "/" in it })
    }

    @Test
    fun anotherAnswerOrAnAssetTooLargeFails() = runTest(UnconfinedTestDispatcher()) {
        enqueue(MockResponse().setResponseCode(403))
        val downloader = downloader()

        assertEquals(DownloadResult.Failed, downloader.download(update(), sha256) {})
        val tooLarge = UpdateDownloader.MAX_APK_BYTES + 1
        assertEquals(DownloadResult.Failed, downloader.download(update(tooLarge), sha256) {})
        assertEquals(DownloadResult.Failed, downloader.download(update(0), sha256) {})
        // Neither size was asked for.
        assertEquals(1, tls.server.requestCount)
    }

    @Test
    fun aBrokenConnectionLeavesNoFile() = runTest(UnconfinedTestDispatcher()) {
        enqueue(apk().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        val result = downloader().download(update(), sha256) {}

        assertEquals(DownloadResult.Offline, result)
        assertFalse(target.exists())
    }

    @Test
    fun cancellingStopsTheReadAtOnceAndLeavesNoFile() = runBlocking {
        // A kilobyte a second: the whole file would take minutes.
        enqueue(apk().throttleBody(1024, 1, TimeUnit.SECONDS))
        val downloader = downloader(Dispatchers.IO)
        val started = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.Default) {
            downloader.download(update(), sha256) { if (it > 0) started.complete(Unit) }
        }
        withTimeout(WAIT_MILLIS) { started.await() }
        assertTrue(target.exists())
        withTimeout(WAIT_MILLIS) { job.cancelAndJoin() }

        assertFalse(target.exists())
    }

    private companion object {
        const val APK_PATH = "/Alalkipgen/YFT/releases/download/v1.0.1/video-downloader-1.0.1.apk"
        const val SUMS_PATH = "/Alalkipgen/YFT/releases/download/v1.0.1/SHA256SUMS"
        const val WAIT_MILLIS = 10_000L
    }
}
