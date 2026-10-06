package com.alal.yft.thumbnail

import com.alal.yft.detection.ExtractorTlsFixture
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteThumbnailLoaderTest {
    private val tls = ExtractorTlsFixture()
    private val directory: File = Files.createTempDirectory("yft-thumbnails").toFile()
    private val widths = mutableListOf<Int>()
    private val decoder = ThumbnailDecoder { bytes, maxWidth ->
        synchronized(widths) { widths += maxWidth }
        if (bytes.isEmpty()) null else testPicture(4, 3)
    }

    @After
    fun tearDown() {
        tls.close()
        directory.deleteRecursively()
    }

    @Test
    fun plainHttpIsRefusedWithoutAnyRequest() = runBlocking {
        val http = tls.url("/vi/a.jpg").newBuilder().scheme("http").build().toString()

        assertNull(loader().load(http))
        assertNull(loader().load("not an address"))
        assertEquals(0, tls.server.requestCount)
    }

    @Test
    fun noCookieIsSentAndOnlyImagesAreAskedFor() = runBlocking {
        // Even a client that carries a session sends none of it for a picture.
        val session = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                listOf(Cookie.Builder().domain(url.host).name("SID").value("secret").build())
        }
        tls.server.enqueue(image())

        assertNotNull(loader(session = session).load(tls.url("/vi/a.jpg").toString()))
        val request = tls.server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(request.getHeader("Cookie"))
        assertEquals("image/*", request.getHeader("Accept"))
    }

    @Test
    fun aPictureLargerThanTheCapIsNotReadToTheEndOrShown() = runBlocking {
        val tooLarge = Buffer().write(ByteArray(RemoteThumbnailLoader.MAX_BYTES.toInt() + 1))
        // No length stated: the read itself stops at the cap.
        tls.server.enqueue(
            MockResponse().setHeader("Content-Type", "image/jpeg").setChunkedBody(tooLarge, 65_536),
        )
        // A stated length past the cap is refused before reading.
        tls.server.enqueue(
            MockResponse().setHeader("Content-Type", "image/jpeg").setBody("x")
                .setHeader("Content-Length", RemoteThumbnailLoader.MAX_BYTES + 1),
        )

        assertNull(loader().load(tls.url("/chunked.jpg").toString()))
        assertNull(loader().load(tls.url("/stated.jpg").toString()))
        assertTrue("never decoded", widths.isEmpty())
        assertEquals(0, directory.listFiles().orEmpty().size)
    }

    @Test
    fun anAnswerThatIsNotAnImageIsNotShown() = runBlocking {
        tls.server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<p>"))
        tls.server.enqueue(MockResponse().setResponseCode(404))

        assertNull(loader().load(tls.url("/page.jpg").toString()))
        assertNull(loader().load(tls.url("/gone.jpg").toString()))
        assertTrue(widths.isEmpty())
    }

    @Test
    fun picturesAreDecodedAtMost480PixelsWide() = runBlocking {
        assertEquals(1, thumbnailSampleSize(width = 480, maxWidth = 480))
        assertEquals(2, thumbnailSampleSize(width = 481, maxWidth = 480))
        assertEquals(4, thumbnailSampleSize(width = 1280, maxWidth = 480))
        assertEquals(4, thumbnailSampleSize(width = 1920, maxWidth = 480))
        assertEquals(8, thumbnailSampleSize(width = 3840, maxWidth = 480))
        tls.server.enqueue(image())

        loader().load(tls.url("/vi/a.jpg").toString())
        assertEquals(listOf(RemoteThumbnailLoader.MAX_WIDTH), widths)
        assertEquals(480, RemoteThumbnailLoader.MAX_WIDTH)
    }

    @Test
    fun aCachedPictureMakesNoSecondRequest() = runBlocking {
        tls.server.enqueue(image())
        val url = tls.url("/vi/a.jpg").toString()
        val first = loader()

        assertNotNull(first.load(url))
        assertNotNull(first.cached(url))
        assertNotNull(first.load(url))
        // A new process: the disk cache answers, under a name that does not reveal the address.
        assertNotNull(loader().load(url))
        assertEquals(1, tls.server.requestCount)
        val saved = directory.listFiles().orEmpty().single()
        assertEquals(RemoteThumbnailLoader.fileName(url), saved.name)
        assertTrue(!saved.name.contains("vi") && saved.name.length == 64)
    }

    @Test
    fun theDiskCacheDropsTheOldestPicturesPastItsSize() = runBlocking {
        repeat(4) { tls.server.enqueue(image(size = 1_000)) }
        val loader = loader(diskCacheBytes = 2_500)
        val urls = (1..3).map { tls.url("/vi/$it.jpg").toString() }

        urls.forEach { url ->
            loader.load(url)
            // Distinct ages, as file times only have a coarse resolution.
            File(directory, RemoteThumbnailLoader.fileName(url))
                .setLastModified(System.currentTimeMillis() - 10_000L * (3 - urls.indexOf(url)))
        }
        loader.load(tls.url("/vi/4.jpg").toString())

        val left = directory.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(2, left.size)
        assertTrue(RemoteThumbnailLoader.fileName(urls[0]) !in left)
    }

    @Test
    fun atMostTwoPicturesDownloadAtATime() = runBlocking {
        val release = CountDownLatch(1)
        val running = AtomicInteger()
        val most = AtomicInteger()
        tls.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                release.await(10, TimeUnit.SECONDS)
                running.decrementAndGet()
                return image()
            }
        }
        val loader = loader()

        val loads = (1..5).map { index ->
            async(Dispatchers.IO) { loader.load(tls.url("/vi/$index.jpg").toString()) }
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (tls.server.requestCount < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        Thread.sleep(300)
        assertEquals(2, tls.server.requestCount)
        release.countDown()
        assertTrue(loads.awaitAll().all { it != null })
        assertEquals(5, tls.server.requestCount)
        assertEquals(2, most.get())
    }

    private fun loader(
        session: CookieJar? = null,
        diskCacheBytes: Long = RemoteThumbnailLoader.DISK_CACHE_BYTES,
    ) = RemoteThumbnailLoader(
        client = tls.client.newBuilder().apply { session?.let(::cookieJar) }.build(),
        directory = directory,
        ioDispatcher = Dispatchers.IO,
        decoder = decoder,
        diskCacheBytes = diskCacheBytes,
    )

    private fun image(size: Int = 64) = MockResponse()
        .setHeader("Content-Type", "image/jpeg")
        .setBody(Buffer().write(ByteArray(size) { 7 }))
}
