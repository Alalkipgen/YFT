package com.alal.yft.download

import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.LinkRenewal
import com.alal.yft.core.download.LinkRenewalRequest
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.detection.BrowserPageReader
import com.alal.yft.detection.HeadlessIdentity
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.PageReread
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.download.RenewalFixtures.MIB
import com.alal.yft.download.RenewalFixtures.NOW
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.downloads.DownloadAction
import com.alal.yft.feature.downloads.DownloadRowUiState
import com.alal.yft.feature.downloads.DownloadsUiState
import com.alal.yft.feature.downloads.downloadDetailsText
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track E (E3, E-D8): a dead link of a page no adapter reads is renewed by reading the page again
 * once, quietly, as its owner read it (P37's reader: the browser's with the tab's agent, Home's
 * with Home's agent and no cookie), and taking the same file from it: its address under a fresh
 * signature, else the video of its length (within 2 s). A pasted file link, a page that is the
 * file itself and a file an adapter's page did not name are not renewed; nor is a 400 off VK.
 * The readers, the resolver and the file probe are fakes; the enqueue and the queue are real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PageRereadRenewalTest {
    private val vk = FakeSite("vk")
    private val coordinator = SiteAdapterCoordinator(
        registry = SiteExtractorRegistry(listOf(vk)),
        mergeSupport = MergeSupport { true },
        lookups = SiteLookupCache(),
    )
    private val sources = DownloadSources(adapterPage = coordinator::handles)
    private val resolver = FakeResolver()
    private val probe = RecordingProbe()
    private val browser = FakeReader()
    private val home = FakeReader()
    private val renewal = SiteLinkRenewal(
        sources = sources,
        coordinator = { coordinator },
        resolver = { resolver },
        probe = probe,
        clock = { NOW },
        pageReaders = PageReaders(browser = browser, home = home),
    )

    @Test
    fun `the browser's page read again gives the same file under a fresh signature`() = runTest {
        browser.answer = { read -> PageReread.Found(pageFiles(read + 1), facts = null) }
        val harness = EnqueueHarness(this, sources, probe)
        val started = download(harness, pageFiles(0).single { it.height == 720 })

        val source = sources[started.taskId]!!
        assertTrue(source.rereadable)
        assertFalse("$source", "example" in "$source")
        val fresh = fresh(renewal.renew(ask(started.taskId)))

        val plan = fresh.plan as DirectDownloadPlan
        assertEquals(clip("720", 1), plan.sourceUrl)
        assertEquals(started.taskId, plan.taskId)
        assertEquals(started.fileName, plan.suggestedFileName)
        assertEquals(clip("720", 1), probe.plans.last().sourceUrl)
        assertEquals(clip("720", 1), fresh.metadata?.finalUrl)
        assertEquals(listOf(PAGE to TAB_AGENT), browser.reads)
        assertTrue(home.reads.isEmpty())
        assertTrue(vk.requests.isEmpty())
    }

    @Test
    fun `files that state no height are told apart by their address`() = runTest {
        val files = { read: Int ->
            listOf("hd", "sd").map { pageFile(clip(it, read), height = null) }
                .let { if (read % 2 == 1) it.reversed() else it }
        }
        browser.answer = { read -> PageReread.Found(files(read + 1), facts = null) }
        val harness = EnqueueHarness(this, sources, probe)
        val started = download(harness, files(0).first())

        val plan = fresh(renewal.renew(ask(started.taskId))).plan as DirectDownloadPlan

        assertEquals(clip("hd", 1), plan.sourceUrl)
    }

    @Test
    fun `a file that moved is the page's video of its length, the closest within 2 s`() =
        runTest {
            val harness = EnqueueHarness(this, sources, probe)
            val id = download(harness, pageFiles(0).single { it.height == 720 }).taskId
            browser.answer = { read ->
                PageReread.Found(
                    listOf(
                        pageFile(clip("720", read, OTHER_HOST), 720, LENGTH + 30_000),
                        pageFile(clip("720", read, OTHER_HOST).replace("clip", "copy"), 720,
                            LENGTH + 1_500),
                    ),
                    facts = null,
                )
            }

            val plan = fresh(renewal.renew(ask(id))).plan as DirectDownloadPlan

            assertEquals(clip("720", 0, OTHER_HOST).replace("clip", "copy"), plan.sourceUrl)
        }

    @Test
    fun `a page that gives no fresh link says why without an address`() = runTest {
        val harness = EnqueueHarness(this, sources, probe)
        val id = download(harness, pageFiles(0).single { it.height == 720 }).taskId

        browser.answer = { PageReread.NoPlayer }
        assertEquals(LinkRenewal.Unavailable(SiteLinkRenewal.NO_PLAYER), renewal.renew(ask(id)))
        browser.answer = { PageReread.Failed }
        assertEquals(LinkRenewal.Unavailable(SiteLinkRenewal.NOT_READ), renewal.renew(ask(id)))
        browser.thrown = IOException("fixture")
        assertEquals(LinkRenewal.Unavailable(SiteLinkRenewal.NOT_READ), renewal.renew(ask(id)))
        browser.thrown = null
        // Another video (5 s longer), a file of another quality, a file under DRM.
        browser.answer = { read ->
            PageReread.Found(listOf(pageFile(clip("720", read), 720, LENGTH + 5_000)), null)
        }
        assertEquals(GONE, renewal.renew(ask(id)))
        browser.answer = { read ->
            PageReread.Found(listOf(pageFile(clip("480", read), 480)), facts = null)
        }
        assertEquals(
            LinkRenewal.Unavailable(SiteLinkRenewal.QUALITY_GONE),
            renewal.renew(ask(id)),
        )
        browser.answer = { read ->
            PageReread.Found(listOf(pageFile(clip("720", read), 720).copy(drmHint = true)), null)
        }
        assertEquals(GONE, renewal.renew(ask(id)))

        assertEquals(6, browser.reads.size)
        listOf(SiteLinkRenewal.NO_PLAYER, SiteLinkRenewal.NOT_READ).forEach { why ->
            assertFalse(why, "http" in why)
        }
        assertNull(sources.renewals.value.notes[id])
    }

    @Test
    fun `a page's stream comes back as the same rendition of its fresh playlist`() = runTest {
        val master = { read: Int ->
            pageFile("https://cdn.example.test/live/master.m3u8?token=$read", null, kind = HLS)
        }
        browser.answer = { read -> PageReread.Found(listOf(master(read + 1)), facts = null) }
        val harness = EnqueueHarness(this, sources, probe)
        val started = download(harness, master(0)) { files -> files.single { it.height == 720 } }

        val fresh = fresh(renewal.renew(ask(started.taskId, DownloadPlanType.HLS)))

        val plan = fresh.plan as HlsDownloadPlan
        assertEquals("https://cdn.example.test/live/master.m3u8/720.m3u8?token=1", plan.playlistUrl)
        assertEquals(started.fileName, plan.suggestedFileName)
        assertNull(fresh.metadata)
    }

    @Test
    fun `Home's page is read again with Home's agent and no cookie`() = runTest {
        home.answer = { read -> PageReread.Found(pageFiles(read + 1), facts = null) }
        val harness = EnqueueHarness(this, sources, probe)
        val started = download(harness, pageFiles(0)[1], owner = LookupOwner.HOME)

        fresh(renewal.renew(ask(started.taskId)))

        assertEquals(listOf(PAGE to HeadlessIdentity.USER_AGENT), home.reads)
        assertTrue(browser.reads.isEmpty())
    }

    @Test
    fun `a pasted file link, the file's own page and an adapter's page are not read again`() =
        runTest {
            browser.answer = { read -> PageReread.Found(pageFiles(read + 1), facts = null) }
            val harness = EnqueueHarness(this, sources, probe)
            val file = pageFiles(0)[1]
            val downloads = listOf(
                file.copy(sources = setOf(CandidateSource.PASTED_URL)),
                file.copy(pageUrl = clip("720", 0).replace("token=0", "view=1")),
                file.copy(pageUrl = vk.page()),
            ).map { download(harness, it).taskId }

            downloads.forEach { id ->
                assertFalse(id, sources[id]!!.renewable)
                assertEquals(LinkRenewal.Declined, renewal.renew(ask(id)))
            }

            assertTrue(browser.reads.isEmpty())
            assertTrue(vk.requests.isEmpty())
            assertEquals(emptySet<String>(), sources.renewals.value.renewable)
        }

    @Test
    fun `a 400 off VK and a build without re-reads renew nothing`() = runTest {
        browser.answer = { read -> PageReread.Found(pageFiles(read + 1), facts = null) }
        val harness = EnqueueHarness(this, sources, probe)
        val id = download(harness, pageFiles(0)[1]).taskId
        val noRereads = listOf(
            SiteLinkRenewal(sources, { coordinator }, { resolver }, probe, clock = { NOW }),
            SiteLinkRenewal(
                sources, { coordinator }, { resolver }, probe, clock = { NOW },
                pageReaders = PageReaders(browser = FakeReader(rereads = 0), home = home),
            ),
        )

        val badRequest = ask(id, reason = DownloadFailureReason.HTTP_STATUS, status = 400)
        assertEquals(LinkRenewal.Declined, renewal.renew(badRequest))
        noRereads.forEach { assertEquals(LinkRenewal.Declined, it.renew(ask(id))) }

        assertTrue(browser.reads.isEmpty())
    }

    @Test
    fun `a page's download whose link dies at 41 MB goes on from the page read again`() =
        runTest {
            browser.answer = { read -> PageReread.Found(pageFiles(read + 1), facts = null) }
            val transfers = ScriptedTransfers { run -> (41 * MIB).takeIf { run == 0 } }
            val harness = EnqueueHarness(this, sources, probe, transfers, renewal)
            backgroundScope.launch { harness.queue.tasks.collect(sources::prune) }
            val id = download(harness, pageFiles(0)[1]).taskId
            runCurrent()

            assertEquals(DownloadTaskStatus.COMPLETED, harness.queue.tasks.value.single().status)
            assertEquals(
                listOf(clip("720", 0), clip("720", 1)),
                transfers.plans.map { (it as DirectDownloadPlan).sourceUrl },
            )
            assertEquals(listOf(0L, 41 * MIB), transfers.resumedFrom)
            val line = "Fresh link (403 at 41.0 MB): resumed at 41.0 MB"
            assertEquals(listOf(line), sources.renewals.value.notes[id])
            val details = downloadDetailsText(rowOf(harness), "1.0.1", "15", 35)
            assertTrue(details, line in details.lines())
            assertFalse(details, "http" in details)
            assertEquals(listOf(PAGE to TAB_AGENT), browser.reads)
        }

    @Test
    fun `a page not read again leaves Link expired, and Retry reads it again`() = runTest {
        browser.answer = { PageReread.Failed }
        val transfers = ScriptedTransfers { run -> (41 * MIB).takeIf { run == 0 } }
        val harness = EnqueueHarness(this, sources, probe, transfers, renewal)
        val id = download(harness, pageFiles(0)[1]).taskId
        runCurrent()

        val stopped = harness.queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
        val detail = stopped.failure?.detail.orEmpty()
        assertTrue(detail, detail.startsWith("Fresh link: ${SiteLinkRenewal.NOT_READ}"))
        val row = rowOf(harness)
        assertTrue(row.canRenew)
        assertTrue(DownloadAction.RETRY in row.availableActions)

        browser.answer = { read -> PageReread.Found(pageFiles(read), facts = null) }
        harness.queue.resume(id)
        runCurrent()

        assertEquals(DownloadTaskStatus.COMPLETED, harness.queue.tasks.value.single().status)
        assertEquals(clip("720", 1), (transfers.plans.last() as DirectDownloadPlan).sourceUrl)
        assertEquals(
            listOf("Fresh link on Retry (403 at 41.0 MB): resumed at 41.0 MB"),
            sources.renewals.value.notes[id],
        )
        assertEquals(2, browser.reads.size)
    }

    /**
     * v1.0.2: a page read again without its player data (a notice, a check) leaves "Link
     * expired" with Retry, and its row says to reopen the page; a read that finds the player
     * takes that back.
     */
    @Test
    fun `a page read again without its player is marked until a read finds the player`() =
        runTest {
            browser.answer = { PageReread.NoPlayer }
            val transfers = ScriptedTransfers { run -> (41 * MIB).takeIf { run == 0 } }
            val harness = EnqueueHarness(this, sources, probe, transfers, renewal)
            val id = download(harness, pageFiles(0)[1]).taskId
            runCurrent()

            val stopped = harness.queue.tasks.value.single()
            assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
            val row = rowOf(harness)
            assertTrue(row.noPlayerOnPage)
            assertTrue(DownloadAction.RETRY in row.availableActions)
            harness.queue.resume(id)
            runCurrent()
            val again = harness.queue.tasks.value.single()
            assertEquals(DownloadTaskStatus.NEEDS_REFRESH, again.status)
            assertTrue(rowOf(harness).noPlayerOnPage)

            browser.answer = { read -> PageReread.Found(pageFiles(read), facts = null) }
            harness.queue.resume(id)
            runCurrent()

            assertEquals(DownloadTaskStatus.COMPLETED, harness.queue.tasks.value.single().status)
            assertFalse(id in sources.renewals.value.noPlayer)
            assertEquals(3, browser.reads.size)
        }

    /** What the sheet queues for [candidate]: the file [pick] takes of its resolved ones. */
    private suspend fun download(
        harness: EnqueueHarness,
        candidate: MediaCandidate,
        owner: LookupOwner = LookupOwner.BROWSER,
        pick: (List<MediaVariant>) -> MediaVariant = { it.first() },
    ): EnqueueResult.Started {
        val asset = (resolver.resolve(candidate) as VariantResolutionResult.Success).asset
        val file = pick(asset.variants)
        val origin = DownloadOrigin(candidate, base = file, owner = owner, userAgent = TAB_AGENT)
        val result = harness.enqueuer.enqueue(asset, file, origin)
        assertTrue("$result", result is EnqueueResult.Started)
        return result as EnqueueResult.Started
    }

    private fun fresh(answer: LinkRenewal): LinkRenewal.Fresh {
        assertTrue("$answer", answer is LinkRenewal.Fresh)
        return answer as LinkRenewal.Fresh
    }

    private fun rowOf(harness: EnqueueHarness): DownloadRowUiState =
        DownloadsUiState.from(harness.queue.tasks.value, renewals = sources.renewals.value)
            .rows
            .single()

    private fun ask(
        taskId: String,
        planType: DownloadPlanType = DownloadPlanType.DIRECT,
        reason: DownloadFailureReason = DownloadFailureReason.ACCESS_DENIED,
        status: Int? = 403,
    ) = LinkRenewalRequest(
        taskId = taskId,
        planType = planType,
        reason = reason,
        httpStatusCode = status,
        stage = DownloadFailureStage.CONNECT,
        bytesKept = 41 * MIB,
        bytesFromThisLink = 41 * MIB,
        byUser = false,
        automaticRenewals = 0,
    )

    /** The page's player files as read [read] gives them, a new order every other read. */
    private fun pageFiles(read: Int) = listOf(1080, 720, 360)
        .map { height -> pageFile(clip("$height", read), height) }
        .let { if (read % 2 == 1) it.reversed() else it }

    /** One file the page's markup names, signed for read [read] ([clip]). */
    private fun pageFile(
        url: String,
        height: Int?,
        length: Long = LENGTH,
        kind: MediaKind = MediaKind.DIRECT,
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = url,
        sources = setOf(CandidateSource.DOM),
        kind = kind,
        mimeType = "video/mp4".takeIf { kind == MediaKind.DIRECT },
        title = TITLE,
        durationMillis = length,
        requestContext = BrowserRequestContext.pageLink(PAGE, url, TAB_AGENT),
        codecs = listOf("avc1.64001F", "mp4a.40.2"),
        height = height,
        width = height?.let { it * 16 / 9 },
    )

    private fun clip(name: String, read: Int, host: String = "cdn.example.test") =
        "https://$host/videos/clip-$name.mp4?token=$read"

    /** P37's reader: read n answers [answer] (n), or throws [thrown]; [rereads] as built. */
    private class FakeReader(
        override val rereads: Int = BrowserPageReader.DEFAULT_REREADS,
    ) : BrowserPageReader {
        val reads = CopyOnWriteArrayList<Pair<String, String?>>()
        var answer: (read: Int) -> PageReread = { PageReread.Failed }
        var thrown: Exception? = null

        override suspend fun read(pageUrl: String, userAgent: String?): PageReread {
            val read = reads.size
            reads += pageUrl to userAgent
            thrown?.let { throw it }
            return answer(read)
        }
    }

    private companion object {
        const val PAGE = "https://www.example.test/watch/42"
        const val TITLE = "Ocean waves"
        const val LENGTH = 252_000L
        const val OTHER_HOST = "cdn2.example.test"
        const val TAB_AGENT = "Mozilla/5.0 (Linux; Android 15; wv) WebView/test"
        val HLS = MediaKind.HLS
        val GONE = LinkRenewal.Unavailable(SiteLinkRenewal.VIDEO_GONE)
    }
}
