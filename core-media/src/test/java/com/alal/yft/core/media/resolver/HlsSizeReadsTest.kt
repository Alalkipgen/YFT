package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserReadAnswer
import com.alal.yft.core.model.media.BrowserReads
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.VariantResolutionResult
import java.util.Collections
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * HLS size estimates with playlist read-ahead: the playlists read and the qualities sized are those
 * of reading one playlist at a time, at most three reads are open, and closing the sheet stops
 * them. A refused playlist still goes to the browser read.
 */
class HlsSizeReadsTest {
    private lateinit var server: MockWebServer
    private val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun aGoneFirstQualityWithItsOwnSoundLeavesTheOthersTheirEstimates() = runBlocking {
        // The master an earlier read-ahead draft sized only three qualities of.
        val master = Master(
            sounds = mapOf("a" to Playlist.TWENTY, "b" to Playlist.TWENTY),
            qualities = listOf(Quality("a", Playlist.GONE)) +
                List(6) { Quality("b", Playlist.TWENTY) },
        )
        serve(master)
        val sized = sizedPaths(resolve())
        assertEquals(setOf("/v1.m3u8", "/v2.m3u8", "/v3.m3u8", "/v4.m3u8"), sized)
        assertEquals(master.oneAtATime().first.toSet(), playlistsAsked())
        assertEquals(6, playlistsAsked().size)
    }

    /**
     * Generated masters with gone, live and mismatched playlists, answers in shuffled order: the
     * same playlists are asked and the same qualities sized as one read at a time.
     */
    @Test
    fun theSamePlaylistsGiveTheSameEstimatesAsOneAtATime() = runBlocking {
        val random = Random(SEED)
        repeat(CASES) { case ->
            val sounds = (0 until random.nextInt(3)).associate { "g$it" to random.playlist() }
            val master = Master(
                sounds = sounds,
                qualities = List(1 + random.nextInt(8)) {
                    Quality(
                        group = sounds.keys.toList()
                            .takeIf { it.isNotEmpty() && random.nextInt(4) > 0 }
                            ?.let { it[random.nextInt(it.size)] },
                        playlist = random.playlist(),
                        average = random.nextInt(8) > 0,
                    )
                },
            )
            asked.clear()
            serve(master, delays = random)
            val (requests, sized) = master.oneAtATime()
            val result = resolve()
            assertEquals("case $case $master", sized, sizedPaths(result))
            assertEquals("case $case $master", requests.toSet(), playlistsAsked())
            assertEquals("case $case $master", requests.size, asked.count { it != MASTER })
        }
    }

    @Test
    fun atMostThreePlaylistsAreReadAtOnce() = runBlocking {
        val open = AtomicInteger()
        val most = AtomicInteger()
        val master = Master(
            sounds = mapOf("a" to Playlist.TWENTY),
            qualities = List(5) { Quality("a", Playlist.TWENTY) },
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                asked += path
                if (path == MASTER) return master.response(path)
                most.accumulateAndGet(open.incrementAndGet(), ::maxOf)
                Thread.sleep(SLOW_MILLIS)
                open.decrementAndGet()
                return master.response(path)
            }
        }
        val sized = sizedPaths(resolve())
        assertEquals(master.oneAtATime().second, sized)
        assertEquals(5, sized.size)
        assertEquals(3, most.get())
        assertEquals(6, playlistsAsked().size)
    }

    @Test
    fun closingTheSheetStopsTheOpenReads() = runBlocking {
        val release = CountDownLatch(1)
        val master = Master(
            sounds = mapOf("a" to Playlist.TWENTY),
            qualities = List(5) { Quality("a", Playlist.TWENTY) },
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                asked += path
                if (path != MASTER) release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                return master.response(path)
            }
        }
        try {
            val lookup = async(Dispatchers.Default) { resolve() }
            withTimeout(WAIT_MILLIS) {
                while (playlistsAsked().size < 3) delay(POLL_MILLIS)
            }
            lookup.cancel()
            withTimeout(WAIT_MILLIS) { lookup.join() }
            assertTrue(lookup.isCancelled)
            delay(SLOW_MILLIS)
            assertEquals(3, playlistsAsked().size)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun refusedPlaylistsGoToTheBrowserReadOneAtATime() = runBlocking {
        val master = Master(
            sounds = mapOf("a" to Playlist.TWENTY),
            qualities = List(4) { Quality("a", Playlist.TWENTY) },
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                asked += path
                return if (path == MASTER) {
                    master.response(path)
                } else {
                    MockResponse().setResponseCode(403)
                }
            }
        }
        // Like the WebView reader: one read at a time.
        val lock = Mutex()
        val open = AtomicInteger()
        val most = AtomicInteger()
        val browser = BrowserReads { request ->
            lock.withLock {
                most.accumulateAndGet(open.incrementAndGet(), ::maxOf)
                delay(BROWSER_MILLIS)
                open.decrementAndGet()
                val path = request.url.substringAfter(server.url("/").toString().removeSuffix("/"))
                BrowserReadAnswer(status = 200, contentType = HLS, text = master.body(path))
            }
        }
        val sized = sizedPaths(resolve(browser))
        assertEquals(master.oneAtATime().second, sized)
        assertEquals(4, sized.size)
        assertEquals(1, most.get())
    }

    private fun Random.playlist(): Playlist = when (nextInt(10)) {
        0 -> Playlist.GONE
        1 -> Playlist.LIVE
        2 -> Playlist.LONGER
        else -> Playlist.TWENTY
    }

    private fun serve(master: Master, delays: Random? = null) {
        val waits = delays?.let { random -> List(16) { random.nextInt(MAX_JITTER_MILLIS) } }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                asked += path
                val response = master.response(path)
                val wait = waits?.get(path.hashCode().mod(waits.size)) ?: 0
                return response.setBodyDelay(wait.toLong(), TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun playlistsAsked(): Set<String> = asked.filter { it != MASTER }.toSet()

    private suspend fun resolve(
        browser: BrowserReads = BrowserReads.None,
    ): VariantResolutionResult =
        DefaultVariantResolver(
            client = OkHttpClient(),
            policy = DefaultVariantResolver.Policy(),
            clock = { NOW },
            browserReads = browser,
        ).resolve(
            MediaCandidate(
                pageUrl = PAGE,
                mediaUrl = server.url(MASTER).toString(),
                sources = setOf(CandidateSource.REQUEST),
                kind = MediaKind.HLS,
                requestContext = BrowserRequestContext(
                    pageUrl = PAGE,
                    userAgent = "YFT fixture",
                    cookie = null,
                ),
                confidence = CandidateConfidence.HIGH,
                observedAtEpochMs = NOW,
            ),
        )

    private fun sizedPaths(result: VariantResolutionResult): Set<String> =
        (result as VariantResolutionResult.Success).asset.variants
            // A sound-from-video row has its own estimate from the sound's rate.
            .filter { !it.audioFromVideo && it.sizeAccuracy == MediaSizeAccuracy.ESTIMATED }
            .map { "/" + it.playbackUrl.substringAfterLast('/') }
            .toSet()

    /** A quality playlist's answer. */
    private enum class Playlist(val seconds: Int?, val finished: Boolean = true) {
        TWENTY(20),
        LONGER(25),
        LIVE(20, finished = false),
        GONE(null),
    }

    private data class Quality(
        val group: String?,
        val playlist: Playlist,
        val average: Boolean = true,
    )

    /** A master with sound groups [sounds] and [qualities] at /v0.m3u8, /v1.m3u8, …. */
    private data class Master(val sounds: Map<String, Playlist>, val qualities: List<Quality>) {
        fun body(path: String): String? = when {
            path == MASTER -> buildString {
                appendLine("#EXTM3U")
                sounds.keys.forEach { group ->
                    appendLine(
                        "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"$group\",NAME=\"Sound\"," +
                            "DEFAULT=YES,AUTOSELECT=YES,URI=\"$group.m3u8\"",
                    )
                }
                qualities.forEachIndexed { index, quality ->
                    val average = if (quality.average) ",AVERAGE-BANDWIDTH=800000" else ""
                    val sound = quality.group?.let { ",AUDIO=\"$it\"" }.orEmpty()
                    appendLine(
                        "#EXT-X-STREAM-INF:BANDWIDTH=1000000$average,RESOLUTION=640x360," +
                            "CODECS=\"avc1.42E01E,mp4a.40.2\"$sound",
                    )
                    appendLine("v$index.m3u8")
                }
            }
            else -> playlistAt(path)?.let { playlist ->
                val seconds = playlist.seconds ?: return null
                "#EXTM3U\n#EXT-X-TARGETDURATION:10\n" +
                    "#EXTINF:10,\nx.ts\n".repeat(seconds / 10) +
                    (if (seconds % 10 > 0) "#EXTINF:${seconds % 10},\nz.ts\n" else "") +
                    if (playlist.finished) "#EXT-X-ENDLIST\n" else ""
            }
        }

        fun response(path: String): MockResponse = body(path)?.let {
            MockResponse().setHeader("Content-Type", HLS).setBody(it)
        } ?: MockResponse().setResponseCode(404)

        private fun playlistAt(path: String): Playlist? {
            val name = path.removePrefix("/").removeSuffix(".m3u8")
            sounds[name]?.let { return it }
            return name.removePrefix("v").toIntOrNull()?.let { qualities.getOrNull(it)?.playlist }
        }

        /**
         * The size loop reading one playlist at a time: the playlists it asks, the sized paths.
         * A master with no average rate only has its first quality's playlist read for a length.
         */
        fun oneAtATime(): Pair<List<String>, Set<String>> {
            if (qualities.none { it.average }) return listOf("/v0.m3u8") to emptySet()
            val made = mutableListOf<String>()
            val seen = mutableMapOf<String, Long?>()
            fun finite(path: String): Long? {
                if (seen.containsKey(path)) return seen[path]
                if (made.size >= MAX_PLAYLISTS) return null
                made += path
                val playlist = playlistAt(path)
                return playlist?.seconds?.takeIf { playlist.finished }?.let { it * 1_000L }
                    .also { seen[path] = it }
            }
            val sized = mutableSetOf<String>()
            qualities.forEachIndexed { index, quality ->
                if (!quality.average) return@forEachIndexed
                val path = "/v$index.m3u8"
                val length = finite(path) ?: return@forEachIndexed
                val group = quality.group
                if (group != null) {
                    val sound = finite("/$group.m3u8") ?: return@forEachIndexed
                    if (sound != length) return@forEachIndexed
                }
                sized += path
            }
            return made to sized
        }
    }

    private companion object {
        const val PAGE = "https://page.example.test/watch"
        const val MASTER = "/master.m3u8"
        const val HLS = "application/vnd.apple.mpegurl"
        const val NOW = 2_000_000_000_000L
        const val MAX_PLAYLISTS = 6
        const val SEED = 8_112_026L
        const val CASES = 60
        const val MAX_JITTER_MILLIS = 25
        const val SLOW_MILLIS = 200L
        const val BROWSER_MILLIS = 50L
        const val WAIT_SECONDS = 10L
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
    }
}
