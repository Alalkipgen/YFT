package com.alal.yft.download

import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.VariantSupport
import com.alal.yft.download.RenewalFixtures.task
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.LookupOwner
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track E (E2): where each download came from, in memory only: remembered as the queue gets the
 * task, under the name it is saved as, and dropped with the task or by Clear browsing data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSourcesTest {
    @Test
    fun `every kind of download remembers its source under its task and saved name`() = runTest {
        val harness = EnqueueHarness(this)
        harness.destinations.rename = { name -> name.replace(".mp4", " (1).mp4") }
        val merged = file(height = 1080).copy(
            codecs = listOf("avc1.640028"),
            audioCompanion = companion(),
        )
        val queued = listOf(
            MediaKind.DIRECT to file(height = 720),
            MediaKind.HLS to file(MediaKind.HLS, height = 480, container = null),
            MediaKind.DASH to file(MediaKind.DASH, height = 360, container = null)
                .copy(manifestVariantId = "video-360"),
            MediaKind.DIRECT to merged,
        ).map { (kind, variant) ->
            harness.enqueuer.enqueue(asset(variant), variant, origin(candidate(kind), variant))
                as EnqueueResult.Started
        }

        assertEquals(listOf("task-0", "task-1", "task-2", "task-3"), queued.map { it.taskId })
        assertEquals(
            listOf(
                "Fixture 720p (1).mp4",
                "Fixture 480p (1).mp4",
                "Fixture 360p (1).mp4",
                "Fixture 1080p (1).mp4",
            ),
            queued.map { it.fileName },
        )
        queued.forEach { started ->
            val source = harness.sources[started.taskId]!!
            assertEquals(started.fileName, source.fileName)
            assertEquals(PAGE, source.pageUrl)
            assertEquals(VIDEO, source.videoId)
            assertEquals("Fixture", source.title)
            assertEquals(LENGTH, source.durationMillis)
            assertTrue(source.renewable)
        }
        val hls = harness.sources["task-1"]!!
        assertEquals(MediaKind.HLS, hls.candidateKind)
        assertEquals(480, hls.quality.height)
        val mux = harness.sources["task-3"]!!.quality
        assertTrue(mux.merged)
        assertEquals(setOf("avc1"), mux.codecs)
        assertEquals(setOf("mp4a"), mux.soundCodecs)
        assertEquals(
            setOf("task-0", "task-1", "task-2", "task-3"),
            harness.sources.renewals.value.renewable,
        )
    }

    @Test
    fun `a conversion keeps the quality of the file it is made from`() = runTest {
        val harness = EnqueueHarness(this)
        val video = file(height = 360)
        val sound = requireNotNull(AudioFromVideo.of(video, LENGTH))
        val mp3 = requireNotNull(Mp3Variants.of(sound, 192, LENGTH))

        harness.enqueuer.enqueue(asset(video), sound, origin(candidate(), video))
        harness.enqueuer.enqueue(asset(video), mp3, origin(candidate(), video))

        val m4a = harness.sources["task-0"]!!
        assertEquals(DownloadConversion(audioFromVideo = true), m4a.conversion)
        assertEquals(MediaTrackType.AUDIO_VIDEO, m4a.quality.trackType)
        assertEquals(360, m4a.quality.height)
        val converted = harness.sources["task-1"]!!
        assertEquals(DownloadConversion(audioFromVideo = true, mp3Kbps = 192), converted.conversion)
        assertEquals("MP3 192 kbps", converted.label)
        assertEquals(m4a.quality, converted.quality)
    }

    @Test
    fun `a download that never reaches the queue leaves no source`() = runTest {
        val harness = EnqueueHarness(this)
        val variant = file(height = 720)

        // No origin: the screen did not know where the file came from (its quality is kept).
        assertTrue(harness.enqueuer.enqueue(asset(variant), variant) is EnqueueResult.Started)
        harness.probe.answer = {
            DirectProbeResult.Failure(DownloadFailure(DownloadFailureReason.EXPIRED_URL))
        }
        assertTrue(enqueue(harness, variant) is EnqueueResult.Rejected)
        harness.probe.answer = { DirectProbeResult.Success(RenewalFixtures.metadata(it.sourceUrl)) }
        harness.destinations.failure = IOException("no space")
        assertTrue(enqueue(harness, variant) is EnqueueResult.Rejected)
        harness.destinations.failure = null
        val unsupported = variant.copy(support = VariantSupport.UNSUPPORTED_CODEC)
        assertTrue(enqueue(harness, unsupported) is EnqueueResult.Rejected)
        // The queue could not keep the task: what was remembered for it goes again.
        harness.store.failure = IOException("disk full")
        assertTrue(runCatching { enqueue(harness, variant) }.isFailure)

        (0..4).forEach { assertNull(harness.sources["task-$it"]) }
        assertEquals(
            DownloadRenewals(qualities = mapOf("task-0" to "720p")),
            harness.sources.renewals.value,
        )
    }

    /** v1.0.2 (item 50): each video's picked quality, for its row; sound has none. */
    @Test
    fun `every video download keeps the quality it was picked as and sound none`() = runTest {
        val harness = EnqueueHarness(this)
        val wide = file(height = 960).copy(width = 1920)
        val video = file(height = 360)
        val sound = requireNotNull(AudioFromVideo.of(video, LENGTH))
        val mp3 = requireNotNull(Mp3Variants.of(sound, 192, LENGTH))

        enqueue(harness, file(height = 720))
        harness.enqueuer.enqueue(asset(wide), wide)
        enqueue(harness, file(MediaKind.HLS, height = 2160, container = null))
        harness.enqueuer.enqueue(asset(video), sound, origin(candidate(), video))
        harness.enqueuer.enqueue(asset(video), mp3, origin(candidate(), video))
        enqueue(harness, file(height = null))

        assertEquals(
            mapOf("task-0" to "720p", "task-1" to "1080p", "task-2" to "4K"),
            harness.sources.renewals.value.qualities,
        )
    }

    @Test
    fun `a picked quality goes with its download or when it finishes, not with browsing data`() {
        val sources = DownloadSources()
        val ids = listOf("done", "cancelled", "deleted", "paused", "queued")
        ids.forEach { sources.picked(it, "720p") }
        // "queued" is not in the queue's list yet.
        sources.prune(ids.take(4).map { task(it, DownloadTaskStatus.RUNNING) })
        sources.clear()
        assertEquals(ids.toSet(), sources.renewals.value.qualities.keys)

        sources.prune(
            listOf(
                task("done", DownloadTaskStatus.COMPLETED),
                task("cancelled", DownloadTaskStatus.CANCELLED),
                task("paused", DownloadTaskStatus.PAUSED),
            ),
        )
        assertEquals(
            mapOf("paused" to "720p", "queued" to "720p"),
            sources.renewals.value.qualities,
        )
        sources.forget("paused")
        assertEquals(mapOf("queued" to "720p"), sources.renewals.value.qualities)
    }

    @Test
    fun `a source stays until the queue lists its task and then follows the task`() {
        val sources = DownloadSources()
        listOf("running", "done", "cancelled", "deleted", "new").forEach { id ->
            remember(sources, id)
        }
        sources.note("done", DONE_LINE)
        sources.note("cancelled", DONE_LINE)

        sources.prune(
            listOf(
                task("running", DownloadTaskStatus.RUNNING),
                task("done", DownloadTaskStatus.COMPLETED),
                task("cancelled", DownloadTaskStatus.CANCELLED),
                task("deleted", DownloadTaskStatus.NEEDS_REFRESH),
            ),
        )

        assertNotNull(sources["running"])
        assertNull(sources["done"])
        assertNull(sources["cancelled"])
        assertNotNull(sources["deleted"])
        // Remembered just before the queue gets the task: not listed yet, still kept.
        assertNotNull(sources["new"])
        assertEquals(setOf("running", "deleted", "new"), sources.renewals.value.renewable)
        // A completed download keeps its lines for its Details; a cancelled one does not.
        assertEquals(mapOf("done" to listOf(DONE_LINE)), sources.renewals.value.notes)

        sources.prune(listOf(task("running", DownloadTaskStatus.RUNNING)))

        assertNull(sources["deleted"])
        assertNotNull(sources["new"])
        assertEquals(setOf("running", "new"), sources.renewals.value.renewable)
        assertTrue(sources.renewals.value.notes.isEmpty())
    }

    /** v1.0.2: a page read again without its player data is marked until the next answer. */
    @Test
    fun `a page read again without its player stays marked until the next answer`() {
        val sources = DownloadSources()
        listOf("notice", "done", "cancelled", "deleted").forEach { id -> remember(sources, id) }
        listOf("notice", "done", "cancelled", "deleted", "unknown").forEach { id ->
            sources.pageWithoutPlayer(id, without = true)
        }
        // Only a download YFT has the source of is marked.
        assertEquals(
            setOf("notice", "done", "cancelled", "deleted"),
            sources.renewals.value.noPlayer,
        )
        sources.pageWithoutPlayer("notice", without = false)
        assertEquals(setOf("done", "cancelled", "deleted"), sources.renewals.value.noPlayer)
        sources.pageWithoutPlayer("notice", without = true)

        sources.prune(
            listOf(
                task("notice", DownloadTaskStatus.NEEDS_REFRESH),
                task("done", DownloadTaskStatus.COMPLETED),
                task("cancelled", DownloadTaskStatus.CANCELLED),
                task("deleted", DownloadTaskStatus.NEEDS_REFRESH),
            ),
        )
        assertEquals(setOf("notice", "deleted"), sources.renewals.value.noPlayer)
        sources.prune(listOf(task("notice", DownloadTaskStatus.NEEDS_REFRESH)))
        assertEquals(setOf("notice"), sources.renewals.value.noPlayer)
        sources.forget("notice")
        assertTrue(sources.renewals.value.noPlayer.isEmpty())

        remember(sources, "again")
        sources.pageWithoutPlayer("again", without = true)
        sources.clear()
        assertEquals(DownloadRenewals.None, sources.renewals.value)
    }

    @Test
    fun `clear browsing data drops every source and line`() {
        val sources = DownloadSources()
        remember(sources, "a")
        remember(sources, "b")
        sources.note("a", DONE_LINE)

        sources.clear()
        sources.prune(listOf(task("a", DownloadTaskStatus.RUNNING)))

        assertNull(sources["a"])
        assertNull(sources["b"])
        assertEquals(DownloadRenewals.None, sources.renewals.value)
        sources.note("a", DONE_LINE)
        assertEquals(DownloadRenewals.None, sources.renewals.value)
    }

    @Test
    fun `a pasted file no adapter named is not renewable and a download keeps four lines`() {
        val sources = DownloadSources()
        remember(sources, "named")
        remember(sources, "unnamed", candidate(videoId = " "))
        (1..5).forEach { sources.note("named", "line $it") }
        sources.note("unknown", "line")

        assertNull(sources["unnamed"]!!.videoId)
        assertFalse(sources["unnamed"]!!.renewable)
        assertEquals(setOf("named"), sources.renewals.value.renewable)
        assertEquals(
            mapOf("named" to listOf("line 2", "line 3", "line 4", "line 5")),
            sources.renewals.value.notes,
        )
    }

    @Test
    fun `a page's file no adapter named is kept by its address without its signature`() {
        val sources = DownloadSources(adapterPage = { it.startsWith("https://vk.test/") })
        val page = candidate(videoId = null).copy(sources = setOf(CandidateSource.DOM))
        remember(sources, "page", page)
        remember(sources, "pasted", candidate(videoId = null))
        remember(sources, "itself", page.copy(pageUrl = "https://media.example.test/clip?view=1"))
        remember(sources, "adapter", page.copy(pageUrl = "https://vk.test/video/v1"))
        remember(sources, "named", candidate().copy(sources = setOf(CandidateSource.DOM)))
        // A page nobody can say an adapter reads or not is not read again.
        val unknown = DownloadSources(adapterPage = { error("fixture") })
        remember(unknown, "unknown", page)

        val source = sources["page"]!!
        assertEquals("https://media.example.test/clip", source.mediaPath)
        assertTrue(source.rereadable)
        assertTrue(source.renewable)
        // The page's own title ("Fixture — 720p") is no quality word off an adapter.
        assertNull(source.quality.label)
        assertEquals("720p", sources["named"]!!.quality.label)
        assertFalse("$source", "example" in "$source" || "clip" in "$source")
        listOf("pasted", "itself", "adapter").forEach { id ->
            assertNull(id, sources[id]!!.mediaPath)
            assertFalse(id, sources[id]!!.renewable)
        }
        assertNull(sources["named"]!!.mediaPath)
        assertFalse(sources["named"]!!.rereadable)
        assertEquals(setOf("page", "named"), sources.renewals.value.renewable)
        assertNull(unknown["unknown"]!!.mediaPath)
        assertFalse(unknown["unknown"]!!.renewable)
    }

    @Test
    fun `whether the sign-in went with the lookup follows its owner's rule`() {
        val sessions = FakeSessions().apply { cookies[LookupOwner.HOME] = "sessionid=fixture" }
        val sources = DownloadSources(sessions)

        remember(sources, "home", owner = LookupOwner.HOME, agent = TAB_AGENT)
        remember(sources, "browser", owner = LookupOwner.BROWSER, agent = TAB_AGENT)
        remember(sources, "unknown", owner = null, agent = null)

        val home = sources["home"]!!
        assertEquals(LookupOwner.HOME, home.owner)
        assertTrue(home.session)
        assertEquals(TAB_AGENT, home.userAgent)
        assertFalse(sources["browser"]!!.session)
        // A screen that does not know who looked it up follows the browser's rule and agent.
        val unknown = sources["unknown"]!!
        assertEquals(LookupOwner.BROWSER, unknown.owner)
        assertFalse(unknown.session)
        assertEquals("candidate-agent", unknown.userAgent)
    }

    @Test
    fun `a source's network moment is taken when its link is found and renewed`() {
        var changes = 3L
        val sources = DownloadSources(networkChanges = { changes })
        remember(sources, "a")
        changes = 5

        assertEquals(3L, sources["a"]!!.networkChanges)
        sources.renewed("a")
        sources.renewed("unknown")

        assertEquals(5L, sources["a"]!!.networkChanges)
        assertNull(sources["unknown"])
    }

    @Test
    fun `nothing it prints names a page, a file, a title or a sign-in`() {
        val sessions = FakeSessions().apply { cookies[LookupOwner.BROWSER] = "sessionid=fixture" }
        val sources = DownloadSources(sessions)
        val origin = origin(candidate(), file(height = 720), owner = LookupOwner.BROWSER)
        sources.remember("a", origin, asset(origin.base), origin.base, "Fixture 720p.mp4")

        val printed = listOf(origin, sources["a"]!!, sources, sources["a"]!!.quality)
            .joinToString(" ")

        assertTrue(printed, "site=youtube" in printed)
        for (leak in listOf("http", "example.test", "sig=", "sessionid", "Fixture", "agent")) {
            assertFalse("$leak must not be printed: $printed", leak in printed)
        }
    }

    @Test
    fun `a quality is the same while no fact both lookups state differs`() {
        val key = DownloadQualityKey.of(
            file(height = 720, fps = 29.97, container = ".MP4")
                .copy(codecs = AVC_AAC, bitrateBitsPerSecond = 2_000_000),
            label = " 720p ",
        )

        assertEquals(30, key.framesPerSecond)
        assertEquals("mp4", key.container)
        assertEquals(setOf("avc1", "mp4a"), key.codecs)
        assertEquals("720p", key.label)
        assertTrue(key.accepts(keyOf(file(height = 720, fps = 30.0), label = "720P")))
        assertTrue(key.accepts(keyOf(file(height = null, fps = null, container = null))))
        assertTrue(key.accepts(keyOf(file().copy(codecs = listOf("avc1.4d401f", "mp4a.40.5")))))
        assertFalse(key.accepts(keyOf(file(height = 1080))))
        assertFalse(key.accepts(keyOf(file(height = 720, fps = 60.0))))
        assertFalse(key.accepts(keyOf(file(height = 720, container = "webm"))))
        assertFalse(key.accepts(keyOf(file(height = 720), label = "720p60")))
        assertFalse(key.accepts(keyOf(file().copy(codecs = listOf("hvc1.1.6.L93", "mp4a.40.2")))))
        assertFalse(key.accepts(keyOf(file(height = 720).copy(audioCompanion = companion()))))
        assertFalse(key.accepts(keyOf(file(MediaKind.HLS, height = 720))))
        assertFalse(
            key.accepts(keyOf(file(height = 720).copy(trackType = MediaTrackType.VIDEO))),
        )
        assertEquals(0L, key.bitrateGap(2_000_000))
        assertTrue(key.bitrateGap(null) > key.bitrateGap(1L))
    }

    @Test
    fun `a fresh lookup's file is tried only when its own facts may hold the quality`() {
        val key = DownloadQualityKey.of(file().copy(codecs = AVC_AAC), label = "720p")

        assertTrue(key.admits(candidate(height = 720), MediaKind.DIRECT))
        assertTrue(key.admits(candidate(height = null, title = "Fixture"), MediaKind.DIRECT))
        assertFalse(key.admits(candidate(height = 1080), MediaKind.DIRECT))
        assertFalse(key.admits(candidate(title = "Fixture — 720p60"), MediaKind.DIRECT))
        assertFalse(key.admits(candidate(codecs = listOf("hev1.1.6.L93")), MediaKind.DIRECT))
        assertFalse(
            key.admits(candidate().copy(audioCompanion = companion()), MediaKind.DIRECT),
        )
        assertFalse(key.admits(candidate(MediaKind.HLS), MediaKind.DIRECT))
        assertEquals(3, key.sharedWith(candidate(height = 720)))
        assertEquals(2, key.sharedWith(candidate(height = 720, title = "Fixture")))
    }

    @Test
    fun `the network changes count a swap and a return but not the first state or a drop`() =
        runTest {
            val snapshot = MutableStateFlow(WIFI)
            val changes = NetworkChanges(
                object : NetworkStatusSource {
                    override val snapshot: StateFlow<NetworkSnapshot> = snapshot
                },
                backgroundScope,
            )
            runCurrent()
            assertEquals(0L, changes.value)

            snapshot.value = MOBILE
            runCurrent()
            snapshot.value = NetworkSnapshot.OFFLINE
            runCurrent()
            assertEquals(1L, changes.value)
            snapshot.value = MOBILE
            runCurrent()
            snapshot.value = MOBILE.copy(validated = false)
            runCurrent()

            assertEquals(2L, changes.value)
            assertTrue(NetworkChanges.changed(WIFI, MOBILE))
            assertTrue(NetworkChanges.changed(NetworkSnapshot.OFFLINE, WIFI))
            assertFalse(NetworkChanges.changed(WIFI, NetworkSnapshot.OFFLINE))
            assertFalse(NetworkChanges.changed(WIFI, WIFI.copy(validated = false)))
        }

    private suspend fun enqueue(harness: EnqueueHarness, variant: MediaVariant): EnqueueResult =
        harness.enqueuer.enqueue(asset(variant), variant, origin(candidate(), variant))

    private fun remember(
        sources: DownloadSources,
        id: String,
        candidate: MediaCandidate = candidate(),
        owner: LookupOwner? = LookupOwner.BROWSER,
        agent: String? = TAB_AGENT,
    ) {
        val variant = file(height = 720)
        sources.remember(
            id,
            DownloadOrigin(candidate, variant, owner, agent),
            asset(variant),
            variant,
            "Fixture 720p.mp4",
        )
    }

    private fun keyOf(variant: MediaVariant, label: String? = null) =
        DownloadQualityKey.of(variant, label)

    private fun origin(
        candidate: MediaCandidate,
        base: MediaVariant,
        owner: LookupOwner? = LookupOwner.BROWSER,
    ) = DownloadOrigin(candidate, base, owner, TAB_AGENT)

    private fun candidate(
        kind: MediaKind = MediaKind.DIRECT,
        height: Int? = 720,
        title: String = "Fixture — ${height}p",
        videoId: String? = VIDEO,
        codecs: List<String> = AVC_AAC,
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://media.example.test/clip?sig=secret",
        sources = setOf(CandidateSource.PASTED_URL),
        kind = kind,
        mimeType = "video/mp4",
        title = title,
        durationMillis = LENGTH,
        requestContext = BrowserRequestContext(PAGE, "candidate-agent", null),
        codecs = codecs,
        videoId = videoId,
        height = height,
    )

    private fun file(
        kind: MediaKind = MediaKind.DIRECT,
        height: Int? = 720,
        fps: Double? = 30.0,
        container: String? = "mp4",
    ) = MediaVariant(
        id = "variant",
        playbackUrl = "https://media.example.test/clip?sig=secret",
        kind = kind,
        trackType = MediaTrackType.AUDIO_VIDEO,
        requestContext = BrowserRequestContext(PAGE, "fixture-agent", "sessionid=fixture"),
        label = height?.let { "${it}p" },
        mimeType = "video/mp4",
        container = container,
        height = height,
        framesPerSecond = fps,
        durationMillis = LENGTH,
    )

    private fun asset(variant: MediaVariant) = MediaAsset(
        sourcePageUrl = PAGE,
        title = "Fixture",
        thumbnailUrl = null,
        durationMillis = LENGTH,
        variants = listOf(variant),
        resolvedAtEpochMs = 1,
    )

    private fun companion() = CompanionAudio(
        mediaUrl = "https://media.example.test/audio?sig=secret",
        mimeType = "audio/mp4",
        codecs = listOf("mp4a.40.2"),
        requestContext = BrowserRequestContext(PAGE, null, null),
    )

    private companion object {
        const val PAGE = "https://www.example.test/watch?v=fixture"
        const val VIDEO = "youtube:fixture"
        const val LENGTH = 252_000L
        const val TAB_AGENT = "Mozilla/5.0 (Linux; Android 15; wv) WebView/test"
        const val DONE_LINE = "Fresh link (403 at 41.0 MB): resumed at 41.0 MB"
        val AVC_AAC = listOf("avc1.64001F", "mp4a.40.2")
        val WIFI = NetworkSnapshot(connected = true, validated = true, unmetered = true)
        val MOBILE = NetworkSnapshot(connected = true, validated = true, unmetered = false)
    }
}
