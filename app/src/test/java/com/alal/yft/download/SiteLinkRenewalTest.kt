package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.LinkRenewal
import com.alal.yft.core.download.LinkRenewalRequest
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.detection.HeadlessIdentity
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.download.RenewalFixtures.MIB
import com.alal.yft.download.RenewalFixtures.NOW
import com.alal.yft.download.RenewalFixtures.TOTAL
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.downloads.DownloadAction
import com.alal.yft.feature.downloads.DownloadRowUiState
import com.alal.yft.feature.downloads.DownloadsUiState
import com.alal.yft.feature.downloads.downloadDetailsText
import com.alal.yft.feature.downloads.showsDetails
import java.io.IOException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track E (E2): a dead link of a site adapter's video is renewed by looking its page up again as
 * its owner did, finding the same video and quality, and planning it under the same task and
 * file name; what is no dead link of a known video is left to the queue. The adapters, the
 * resolver and the file probe are fakes; the coordinator, the enqueue and the queue are real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SiteLinkRenewalTest {
    private var changes = 0L
    private val sessions = FakeSessions()
    private val sources = DownloadSources(sessions) { changes }
    private val resolver = FakeResolver()
    private val probe = RecordingProbe()
    private val youtube = FakeSite("youtube")
    private val facebook = FakeSite("facebook")
    private val instagram = FakeSite("instagram")
    private val tiktok = FakeSite("tiktok")
    private val x = FakeSite("x")
    private val vk = FakeSite("vk")
    private val vimeo = FakeSite("vimeo")
    private val coordinator = SiteAdapterCoordinator(
        registry = SiteExtractorRegistry(
            listOf(youtube, facebook, instagram, tiktok, x, vk, vimeo),
        ),
        mergeSupport = MergeSupport { true },
        lookups = SiteLookupCache(),
    )
    private val renewal = SiteLinkRenewal(
        sources = sources,
        coordinator = { coordinator },
        resolver = { resolver },
        probe = probe,
        sessions = sessions,
        networkChanges = { changes },
        clock = { NOW },
    )

    @Test
    fun `YouTube's merged quality comes back merged with fresh links for both tracks`() =
        runTest {
            youtube.answer = { found(youTubeFiles(it)) }
            val harness = harness()
            val started = download(harness, lookUp(youtube).titled("1080p"))

            val fresh = renew(harness, started, youtube)

            val plan = fresh.plan as AudioVideoMuxDownloadPlan
            assertEquals(link(youtube, "1080", 1), plan.video.manifestUrl)
            assertEquals(link(youtube, "audio", 1), plan.audio.manifestUrl)
            assertNull(fresh.metadata)
            assertEquals("Ocean waves 1080p.mp4", started.fileName)
            assertTrue(probe.plans.isEmpty())
        }

    @Test
    fun `YouTube's frame rate and its whole file keep their own quality`() = runTest {
        youtube.answer = { found(youTubeFiles(it)) }
        val harness = harness()
        val high = download(harness, lookUp(youtube).titled("720p60"))
        val whole = download(harness, lookUp(youtube).titled("360p"))
        val probed = probe.plans.size

        val first = fresh(renewal.renew(ask(high.taskId, DownloadPlanType.AUDIO_VIDEO_MUX)))
        val second = fresh(renewal.renew(ask(whole.taskId)))

        assertEquals("Ocean waves 720p60.mp4", high.fileName)
        assertEquals(
            link(youtube, "720-60", 1),
            (first.plan as AudioVideoMuxDownloadPlan).video.manifestUrl,
        )
        val direct = second.plan as DirectDownloadPlan
        assertEquals(link(youtube, "360", 2), direct.sourceUrl)
        assertEquals(direct, probe.plans.last())
        assertEquals(probed + 1, probe.plans.size)
        assertEquals(RenewalFixtures.metadata(direct.sourceUrl), second.metadata)
        assertEquals(3, youtube.requests.size)
    }

    @Test
    fun `an MP3 is made again from the fresh link of the same sound`() = runTest {
        youtube.answer = { found(youTubeFiles(it)) }
        val harness = harness()
        val started = converting(harness, lookUp(youtube).titled("Audio 128 kbps")) { file, asset ->
            Mp3Variants.of(file, 192, asset.durationMillis)
        }

        val plan = renew(harness, started, youtube).plan as DirectDownloadPlan

        assertEquals("Ocean waves MP3 192 kbps.mp3", started.fileName)
        assertEquals(link(youtube, "audio-128", 1), plan.sourceUrl)
        assertEquals(192, plan.mp3?.bitrateKbps)
        assertEquals("Ocean waves", plan.mp3?.title)
        assertFalse(plan.audioOnly)
    }

    @Test
    fun `Facebook's SD file and the sound kept from it come back from the fresh SD file`() =
        runTest {
            facebook.answer = { found(facebookFiles(it)) }
            resolver.measured = mapOf("hd" to 720, "sd" to 360)
            val harness = harness()
            val video = download(harness, lookUp(facebook).titled("SD"))
            val sound = converting(harness, lookUp(facebook).titled("SD")) { file, asset ->
                AudioFromVideo.of(file, asset.durationMillis)
            }

            val first = fresh(renewal.renew(ask(video.taskId)))
            val second = fresh(renewal.renew(ask(sound.taskId)))

            assertEquals("Ocean waves 360p.mp4", video.fileName)
            assertEquals(link(facebook, "sd", 1), (first.plan as DirectDownloadPlan).sourceUrl)
            val m4a = second.plan as DirectDownloadPlan
            assertEquals("Ocean waves.m4a", sound.fileName)
            assertEquals(sound.fileName, m4a.suggestedFileName)
            assertEquals(link(facebook, "sd", 2), m4a.sourceUrl)
            assertTrue(m4a.audioOnly)
            assertNull(m4a.mp3)
        }

    @Test
    fun `Instagram's height picks the same file among the fresh ones`() = runTest {
        instagram.answer = { found(instagramFiles(it)) }
        val harness = harness()
        val started = download(harness, lookUp(instagram).single { it.height == 720 })

        val plan = renew(harness, started, instagram).plan as DirectDownloadPlan

        assertEquals(link(instagram, "720", 1), plan.sourceUrl)
    }

    @Test
    fun `TikTok's H264 and H265 files of one height stay apart`() = runTest {
        tiktok.answer = { found(tikTokFiles(it)) }
        val harness = harness()
        val h264 = download(harness, lookUp(tiktok).single { "h264" in it.mediaUrl })
        val h265 = download(harness, lookUp(tiktok).single { "h265" in it.mediaUrl })

        val first = fresh(renewal.renew(ask(h264.taskId)))
        val second = fresh(renewal.renew(ask(h265.taskId)))

        assertEquals(link(tiktok, "h264", 1), (first.plan as DirectDownloadPlan).sourceUrl)
        assertEquals(link(tiktok, "h265", 2), (second.plan as DirectDownloadPlan).sourceUrl)
    }

    @Test
    fun `X's two files of one height are told apart by their bitrate`() = runTest {
        x.answer = { found(xFiles(it)) }
        val harness = harness()
        val low = download(harness, lookUp(x).single { "720-low" in it.mediaUrl })

        val plan = renew(harness, low, x).plan as DirectDownloadPlan

        assertEquals(link(x, "720-low", 1), plan.sourceUrl)
    }

    @Test
    fun `VK's 400 is a dead link and its quality is its height`() = runTest {
        vk.answer = { found(vkFiles(it)) }
        val harness = harness()
        val started = download(harness, lookUp(vk).titled("480p"))

        val answer = renewal.renew(
            ask(started.taskId, reason = DownloadFailureReason.HTTP_STATUS, status = 400),
        )

        val plan = fresh(answer).plan as DirectDownloadPlan
        assertEquals(link(vk, "480", 1), plan.sourceUrl)
        assertEquals(started.fileName, plan.suggestedFileName)
    }

    @Test
    fun `Vimeo's playlist, manifest and file qualities come back from the fresh ones`() =
        runTest {
            vimeo.answer = { found(vimeoFiles(it)) }
            val harness = harness()
            val found = lookUp(vimeo)
            val hls = download(harness, found.single { it.kind == MediaKind.HLS }) { variants ->
                variants.single { it.height == 720 }
            }
            val dash = download(harness, found.single { it.kind == MediaKind.DASH }) { variants ->
                variants.single { it.height == 360 }
            }
            val file = download(harness, found.titled("540p"))

            val first = fresh(renewal.renew(ask(hls.taskId, DownloadPlanType.HLS)))
            val second = fresh(renewal.renew(ask(dash.taskId, DownloadPlanType.DASH)))
            val third = fresh(renewal.renew(ask(file.taskId)))

            assertEquals(
                link(vimeo, "master", 1).replace("?", "/720.m3u8?"),
                (first.plan as HlsDownloadPlan).playlistUrl,
            )
            val manifest = second.plan as DashDownloadPlan
            assertEquals(link(vimeo, "dash", 2), manifest.manifestUrl)
            assertEquals("video-360", manifest.representationId)
            assertEquals(link(vimeo, "540", 3), (third.plan as DirectDownloadPlan).sourceUrl)
            assertEquals(
                listOf(hls.fileName, dash.fileName, file.fileName),
                listOf(first, second, third).map { it.plan.suggestedFileName },
            )
            assertEquals(listOf(null, null), listOf(first.metadata, second.metadata))
        }

    @Test
    fun `a lookup is asked again as its owner asked it`() = runTest {
        facebook.answer = { found(facebookFiles(it)) }
        sessions.cookies[LookupOwner.HOME] = "sessionid=home"
        val harness = harness()
        val found = lookUp(facebook)
        val home = download(harness, found.titled("SD"), owner = LookupOwner.HOME)
        val signedOut = download(harness, found.titled("SD"), owner = LookupOwner.BROWSER)
        sessions.cookies[LookupOwner.BROWSER] = "c_user=browser"
        val browser = download(harness, found.titled("HD"), owner = LookupOwner.BROWSER)

        listOf(home, browser, signedOut).forEach { fresh(renewal.renew(ask(it.taskId))) }

        val asked = facebook.requests.drop(1).map { it.requestContext }
        assertEquals(
            listOf(
                HeadlessIdentity.USER_AGENT to "sessionid=home",
                TAB_AGENT to "c_user=browser",
                // The browser held no sign-in for the page then, so none goes now either.
                TAB_AGENT to null,
            ),
            asked.map { it.userAgent to it.cookie },
        )
        assertTrue(asked.all { it.pageUrl == facebook.page() })
    }

    @Test
    fun `a fresh lookup without the same video or quality says why without an address`() =
        runTest {
            facebook.answer = { found(facebookFiles(it)) }
            resolver.measured = mapOf("hd" to 720, "sd" to 360)
            val harness = harness()
            val request = ask(download(harness, lookUp(facebook).titled("SD")).taskId)

            facebook.answer = {
                SiteExtractionResult.Failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE)
            }
            assertEquals(
                LinkRenewal.Unavailable("the site did not give the video (private or unavailable)"),
                renewal.renew(request),
            )
            facebook.answer = { found(facebookFiles(it, videoId = "facebook:other")) }
            assertEquals(GONE, renewal.renew(request))
            facebook.answer = { found(facebookFiles(it, length = LENGTH + 2_500)) }
            assertEquals(GONE, renewal.renew(request))
            facebook.answer = { found(facebookFiles(it, drm = true)) }
            assertEquals(GONE, renewal.renew(request))
            facebook.answer = { found(facebookFiles(it).filter { file -> "hd" in file.mediaUrl }) }
            assertEquals(NO_QUALITY, renewal.renew(request))
            // The SD file now has another picture: no other quality is taken instead.
            facebook.answer = { found(facebookFiles(it)) }
            resolver.measured = mapOf("hd" to 720, "sd" to 480)
            assertEquals(NO_QUALITY, renewal.renew(request))
            resolver.measured = mapOf("hd" to 720, "sd" to 360)
            resolver.failing = { true }
            assertEquals(NO_QUALITY, renewal.renew(request))
            resolver.failing = { false }
            // Two lookups state one video's length within 2 s of each other.
            facebook.answer = { found(facebookFiles(it, length = LENGTH + 1_500)) }
            assertTrue(renewal.renew(request) is LinkRenewal.Fresh)

            assertEquals(9, facebook.requests.size)
            assertEquals(2, probe.plans.size)
        }

    @Test
    fun `a fresh file that cannot be downloaded or does not answer is no fresh link`() =
        runTest {
            instagram.answer = { found(instagramFiles(it)) }
            val harness = harness()
            val chosen = lookUp(instagram).single { it.height == 720 }
            val request = ask(download(harness, chosen).taskId)

            probe.answer = { denied() }
            assertEquals(
                LinkRenewal.Unavailable("the fresh file did not answer (HTTP 403)"),
                renewal.renew(request),
            )
            probe.thrown = IOException("connection reset")
            assertEquals(
                LinkRenewal.Unavailable("the fresh file did not answer (network)"),
                renewal.renew(request),
            )
            probe.thrown = null
            instagram.answer = { lookup ->
                found(instagramFiles(lookup).map { it.copy(mediaUrl = it.mediaUrl.insecure()) })
            }
            assertEquals(
                LinkRenewal.Unavailable("the fresh file cannot be downloaded (invalid url)"),
                renewal.renew(request),
            )
            assertTrue(sources.renewals.value.notes.isEmpty())
        }

    @Test
    fun `what is no dead link of a known video is left to the queue`() = runTest {
        facebook.answer = { found(facebookFiles(it)) }
        val harness = harness()
        val found = lookUp(facebook)
        val started = download(harness, found.titled("SD"))
        // A file no adapter named, and one of a page no adapter reads.
        val unnamed = download(harness, found.titled("HD").copy(videoId = null))
        val elsewhere = download(harness, found.titled("HD").copy(pageUrl = OTHER_PAGE))

        assertEquals(LinkRenewal.Declined, renewal.renew(ask("task-9")))
        assertEquals(LinkRenewal.Declined, renewal.renew(ask(unnamed.taskId)))
        assertEquals(LinkRenewal.Declined, renewal.renew(ask(elsewhere.taskId)))
        // Off VK a 400 is a request the server did not take, not a link that died.
        assertEquals(
            LinkRenewal.Declined,
            renewal.renew(
                ask(started.taskId, reason = DownloadFailureReason.HTTP_STATUS, status = 400),
            ),
        )

        assertEquals(1, facebook.requests.size)
        assertEquals(setOf(started.taskId, elsewhere.taskId), sources.renewals.value.renewable)
    }

    @Test
    fun `YouTube's refusal is renewed on a tap or after the network changed, not by itself`() =
        runTest {
            youtube.answer = { found(youTubeFiles(it)) }
            val harness = harness()
            val started = download(harness, lookUp(youtube).titled("360p"))
            val refused = ask(started.taskId, reason = DownloadFailureReason.YOUTUBE_REFUSED)

            assertEquals(LinkRenewal.Declined, renewal.renew(refused))
            assertEquals(1, youtube.requests.size)
            fresh(renewal.renew(refused.copy(byUser = true)))
            // The fresh link was found on this network: the same network refuses it again.
            assertEquals(LinkRenewal.Declined, renewal.renew(refused))
            changes += 1
            fresh(renewal.renew(refused))

            assertEquals(3, youtube.requests.size)
        }

    @Test
    fun `each fresh link's line says what ended the old one and how the download went on`() =
        runTest {
            instagram.answer = { found(instagramFiles(it)) }
            val harness = harness()
            val id = download(harness, lookUp(instagram).single { it.height == 720 }).taskId

            renewal.wentOn(id, 0, 0)
            assertNull(sources.renewals.value.notes[id])
            fresh(renewal.renew(ask(id, kept = 41 * MIB)))
            renewal.wentOn(id, 41 * MIB, 41 * MIB)
            renewal.wentOn(id, 41 * MIB, 42 * MIB)
            val expired = ask(
                id,
                reason = DownloadFailureReason.EXPIRED_URL,
                status = null,
                kept = 42 * MIB,
            )
            fresh(renewal.renew(expired))
            renewal.wentOn(id, 42 * MIB, 0)
            fresh(renewal.renew(ask(id, kept = 0, byUser = true)))
            renewal.wentOn(id, 0, 0)
            probe.answer = { denied() }
            assertTrue(renewal.renew(ask(id)) is LinkRenewal.Unavailable)
            renewal.wentOn(id, 41 * MIB, 41 * MIB)

            assertEquals(
                listOf(
                    "Fresh link (403 at 41.0 MB): resumed at 41.0 MB",
                    "Fresh link (link expired at 42.0 MB): started over (the file changed)",
                    "Fresh link on Retry (403): started",
                ),
                sources.renewals.value.notes[id],
            )
            val refused = ask(
                id,
                reason = DownloadFailureReason.YOUTUBE_REFUSED,
                status = null,
                kept = MIB,
            )
            assertEquals("Fresh link (YouTube refused at 1.0 MB)", SiteLinkRenewal.headOf(refused))
        }

    @Test
    fun `a download whose link dies after 41 MB goes on with a fresh link and completes`() =
        runTest {
            instagram.answer = { found(instagramFiles(it)) }
            val transfers = ScriptedTransfers { run -> (41 * MIB).takeIf { run == 0 } }
            val harness = EnqueueHarness(this, sources, probe, transfers, renewal)
            backgroundScope.launch { harness.queue.tasks.collect(sources::prune) }
            val id = download(harness, lookUp(instagram).single { it.height == 720 }).taskId
            runCurrent()

            assertEquals(DownloadTaskStatus.COMPLETED, harness.queue.tasks.value.single().status)
            assertEquals(
                listOf(link(instagram, "720", 0), link(instagram, "720", 1)),
                transfers.plans.map { (it as DirectDownloadPlan).sourceUrl },
            )
            assertEquals(listOf(0L, 41 * MIB), transfers.resumedFrom)
            val line = "Fresh link (403 at 41.0 MB): resumed at 41.0 MB"
            assertEquals(listOf(line), sources.renewals.value.notes[id])
            // A completed download needs no source; its line stays for its Details.
            assertNull(sources[id])
            val row = rowOf(harness)
            assertTrue(row.showsDetails)
            val details = downloadDetailsText(row, "1.0.1", "15", 35)
            assertTrue(details, line in details.lines())
            assertTrue(details, "Status: COMPLETED" in details.lines())
            assertFalse(details, "http" in details)
        }

    @Test
    fun `a dead link no lookup could replace by itself is renewed by Retry`() = runTest {
        instagram.answer = { found(instagramFiles(it)) }
        val transfers = ScriptedTransfers { run -> (41 * MIB).takeIf { run == 0 } }
        val harness = EnqueueHarness(this, sources, probe, transfers, renewal)
        backgroundScope.launch { harness.queue.tasks.collect(sources::prune) }
        val id = download(harness, lookUp(instagram).single { it.height == 720 }).taskId
        probe.answer = { denied() }
        runCurrent()

        val stopped = harness.queue.tasks.value.single()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
        val detail = stopped.failure?.detail.orEmpty()
        val expected = "Fresh link: the fresh file did not answer (HTTP 403)"
        assertTrue(detail, detail.startsWith(expected))
        val row = rowOf(harness)
        assertTrue(row.canRenew)
        assertTrue(DownloadAction.RETRY in row.availableActions)

        probe.answer = { DirectProbeResult.Success(RenewalFixtures.metadata(it.sourceUrl)) }
        harness.queue.resume(id)
        runCurrent()

        assertEquals(DownloadTaskStatus.COMPLETED, harness.queue.tasks.value.single().status)
        assertEquals(
            listOf("Fresh link on Retry (403 at 41.0 MB): resumed at 41.0 MB"),
            sources.renewals.value.notes[id],
        )
        assertEquals(3, instagram.requests.size)
        assertEquals(2, transfers.plans.size)
    }

    private fun TestScope.harness() = EnqueueHarness(this, sources, probe)

    /** The sheet's lookup of [site]'s page, as the browser asks it. */
    private suspend fun lookUp(site: FakeSite): List<MediaCandidate> {
        val page = site.page()
        val outcome = coordinator.inspect(page, BrowserRequestContext(page, TAB_AGENT, null), NOW)
        return (outcome as SiteAdapterOutcome.Detected).candidates
    }

    /**
     * What the sheet queues for [candidate]: the file [pick] takes of its resolved ones, with
     * the facts the candidate states, converted by [convert] and named after its quality.
     */
    private suspend fun download(
        harness: EnqueueHarness,
        candidate: MediaCandidate,
        owner: LookupOwner? = LookupOwner.BROWSER,
        convert: (MediaVariant, MediaAsset) -> MediaVariant? = { file, _ -> file },
        pick: (List<MediaVariant>) -> MediaVariant = { it.first() },
    ): EnqueueResult.Started {
        val asset = (resolver.resolve(candidate) as VariantResolutionResult.Success).asset
        val resolved = pick(asset.variants)
        val file = resolved.copy(
            width = resolved.width ?: candidate.width,
            height = resolved.height ?: candidate.height,
            framesPerSecond = resolved.framesPerSecond ?: candidate.framesPerSecond,
            bitrateBitsPerSecond = resolved.bitrateBitsPerSecond ?: candidate.bitrateBitsPerSecond,
        )
        val queued = named(requireNotNull(convert(file, asset)))
        val origin = DownloadOrigin(candidate, base = file, owner = owner, userAgent = TAB_AGENT)
        val result = harness.enqueuer.enqueue(asset, queued, origin)
        assertTrue("$result", result is EnqueueResult.Started)
        return result as EnqueueResult.Started
    }

    private suspend fun download(
        harness: EnqueueHarness,
        candidate: MediaCandidate,
        pick: (List<MediaVariant>) -> MediaVariant,
    ): EnqueueResult.Started = download(harness, candidate, LookupOwner.BROWSER, pick = pick)

    /** Queues [candidate]'s file after [convert] made it an MP3 or a sound kept from a video. */
    private suspend fun converting(
        harness: EnqueueHarness,
        candidate: MediaCandidate,
        convert: (MediaVariant, MediaAsset) -> MediaVariant?,
    ): EnqueueResult.Started = download(harness, candidate, LookupOwner.BROWSER, convert = convert)

    /** [started]'s link died at 41 MB: the fresh one is of the same task, type and name. */
    private suspend fun renew(
        harness: EnqueueHarness,
        started: EnqueueResult.Started,
        site: FakeSite,
    ): LinkRenewal.Fresh {
        val task = harness.queue.tasks.value.single { it.id == started.taskId }
        val fresh = fresh(renewal.renew(ask(started.taskId, task.planType)))
        assertEquals(started.taskId, fresh.plan.taskId)
        assertEquals(task.planType, fresh.plan.type)
        assertEquals(started.fileName, fresh.plan.suggestedFileName)
        // Asked afresh: the answer the sheet's lookup left is not taken again.
        assertEquals(2, site.requests.size)
        return fresh
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
        kept: Long = 41 * MIB,
        byUser: Boolean = false,
    ) = LinkRenewalRequest(
        taskId = taskId,
        planType = planType,
        reason = reason,
        httpStatusCode = status,
        stage = DownloadFailureStage.CONNECT,
        bytesKept = kept,
        bytesFromThisLink = kept,
        byUser = byUser,
        automaticRenewals = 0,
    )

    /** YouTube: merged qualities (the picture and its sound apart), a whole 360p, the sound. */
    private fun youTubeFiles(lookup: Int) = listOf(
        file(
            youtube, lookup, "1080", "1080p", height = 1080, fps = 30.0, bitrate = 4_000_000,
            codecs = listOf("avc1.640028"), merged = true,
        ),
        file(
            youtube, lookup, "720-60", "720p60", height = 720, fps = 60.0,
            codecs = listOf("avc1.4d4020"), merged = true,
        ),
        file(
            youtube, lookup, "720", "720p", height = 720, fps = 30.0,
            codecs = listOf("avc1.4d401f"), merged = true,
        ),
        file(youtube, lookup, "360", "360p", height = 360, fps = 30.0),
        file(
            youtube, lookup, "audio-128", "Audio 128 kbps", mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"), bitrate = 128_000,
        ),
    ).inOrderOf(lookup)

    /** Facebook: HD and SD files that state neither height nor codecs (the MP4 header has it). */
    private fun facebookFiles(
        lookup: Int,
        videoId: String? = null,
        length: Long = LENGTH,
        drm: Boolean? = null,
    ) = listOf("hd" to "HD", "sd" to "SD").map { (name, label) ->
        file(
            facebook, lookup, name, label, codecs = emptyList(), videoId = videoId,
            length = length, drm = drm,
        )
    }.inOrderOf(lookup)

    private fun instagramFiles(lookup: Int) = listOf(1080, 720, 480).map { height ->
        file(
            instagram, lookup, "$height", null, height = height, width = height * 9 / 16,
            bitrate = height * 2_000L,
        )
    }.inOrderOf(lookup)

    /** TikTok: the same picture as H.265 and as H.264, H.265 always first. */
    private fun tikTokFiles(lookup: Int) = listOf(
        file(
            tiktok, lookup, "h265", null, height = 1024, width = 576,
            codecs = listOf("hvc1.1.6.L120.90", "mp4a.40.2"),
        ),
        file(tiktok, lookup, "h264", null, height = 1024, width = 576),
    )

    private fun xFiles(lookup: Int) = listOf(
        file(x, lookup, "720-high", null, height = 720, bitrate = 2_176_000),
        file(x, lookup, "720-low", null, height = 720, bitrate = 1_280_000),
        file(x, lookup, "360", null, height = 360, bitrate = 832_000),
    ).inOrderOf(lookup)

    private fun vkFiles(lookup: Int) = listOf(1080, 720, 480, 360).map { height ->
        file(vk, lookup, "$height", "${height}p", height = height)
    } + file(vk, lookup, "master", null, kind = MediaKind.HLS, codecs = emptyList())

    private fun vimeoFiles(lookup: Int) = listOf(
        file(vimeo, lookup, "master", null, kind = MediaKind.HLS, codecs = emptyList()),
        file(vimeo, lookup, "dash", null, kind = MediaKind.DASH, codecs = emptyList()),
        file(vimeo, lookup, "540", "540p", height = 540),
        file(vimeo, lookup, "360", "360p", height = 360),
    ).inOrderOf(lookup)

    /** A fresh lookup lists the files in another order: a quality is never found by place. */
    private fun List<MediaCandidate>.inOrderOf(lookup: Int) =
        if (lookup % 2 == 1) reversed() else this

    private fun List<MediaCandidate>.titled(label: String): MediaCandidate =
        single { MediaGroups.titleLabel(it.title) == label }

    /** One file of [site]'s video as lookup [lookup] gives it: its link names both. */
    private fun file(
        site: FakeSite,
        lookup: Int,
        name: String,
        label: String?,
        height: Int? = null,
        width: Int? = height?.let { it * 16 / 9 },
        fps: Double? = null,
        bitrate: Long? = null,
        codecs: List<String> = listOf("avc1.64001F", "mp4a.40.2"),
        mimeType: String = "video/mp4",
        kind: MediaKind = MediaKind.DIRECT,
        merged: Boolean = false,
        length: Long = LENGTH,
        videoId: String? = null,
        drm: Boolean? = null,
    ): MediaCandidate {
        val page = site.page()
        return MediaCandidate(
            pageUrl = page,
            mediaUrl = link(site, name, lookup),
            sources = setOf(CandidateSource.PASTED_URL),
            kind = kind,
            mimeType = mimeType.takeIf { kind == MediaKind.DIRECT },
            title = listOfNotNull(TITLE, label).joinToString(" — "),
            durationMillis = length,
            requestContext = BrowserRequestContext(page, null, null),
            drmHint = drm,
            codecs = codecs,
            audioCompanion = if (merged) {
                CompanionAudio(
                    mediaUrl = link(site, "audio", lookup),
                    mimeType = "audio/mp4",
                    codecs = listOf("mp4a.40.2"),
                    requestContext = BrowserRequestContext(page, null, null),
                )
            } else {
                null
            },
            videoId = videoId,
            width = width,
            height = height,
            framesPerSecond = fps,
            bitrateBitsPerSecond = bitrate,
        )
    }

    private fun link(site: FakeSite, name: String, lookup: Int) =
        "https://media.${site.host}/v1/$name?sig=$lookup"

    private fun String.insecure() = replace("https://", "http://")

    private fun found(files: List<MediaCandidate>): SiteExtractionResult =
        SiteExtractionResult.Success(files)

    private fun denied() = DirectProbeResult.Failure(
        DownloadFailure(DownloadFailureReason.ACCESS_DENIED, httpStatusCode = 403),
    )

    /** Video files are named after their quality, as the sheet names them. */
    private fun named(variant: MediaVariant): MediaVariant {
        if (variant.trackType == MediaTrackType.AUDIO) {
            return if (variant.audioFromVideo && variant.mp3 == null) {
                variant.copy(label = null)
            } else {
                variant
            }
        }
        val height = variant.height ?: return variant.copy(label = null)
        val rate = variant.framesPerSecond?.takeIf { it > HIGH_FRAME_RATE }
            ?.let { Math.round(it).toString() }
            .orEmpty()
        return variant.copy(label = "${height}p$rate")
    }

    private val DownloadPlan.type: DownloadPlanType
        get() = when (this) {
            is DirectDownloadPlan -> DownloadPlanType.DIRECT
            is HlsDownloadPlan -> DownloadPlanType.HLS
            is DashDownloadPlan -> DownloadPlanType.DASH
            is AudioVideoMuxDownloadPlan -> DownloadPlanType.AUDIO_VIDEO_MUX
        }

    /** A site adapter for pages `https://<id>.test/video/<id>`; lookup n answers [answer] (n). */
    private class FakeSite(override val id: String) : SiteExtractor {
        override val displayName: String = id
        val host = "$id.test"
        val requests = CopyOnWriteArrayList<SiteExtractionRequest>()
        var answer: (lookup: Int) -> SiteExtractionResult = {
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }

        fun page() = "https://$host/video/v1"

        override fun identify(pageUrl: String): SitePageIdentity? {
            val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
            val path = uri.path.orEmpty()
            if (uri.host != host || !path.startsWith(VIDEO_PATH)) return null
            val video = path.removePrefix(VIDEO_PATH).takeIf(String::isNotBlank) ?: return null
            return SitePageIdentity(id, video, "https://$host$VIDEO_PATH$video")
        }

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            val lookup = requests.size
            requests += request
            return answer(lookup)
        }

        private companion object {
            const val VIDEO_PATH = "/video/"
        }
    }

    /**
     * Resolves like the real resolver: a whole file is one variant keeping its companion and
     * codecs, with the height its MP4 header gives ([measured], by the file's name) when the
     * site states none; a playlist or manifest lists 1080p, 720p and 360p.
     */
    private class FakeResolver : VariantResolver {
        var measured: Map<String, Int> = emptyMap()
        var failing: (MediaCandidate) -> Boolean = { false }

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            if (failing(candidate)) {
                return VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK)
            }
            val variants = when (candidate.kind) {
                MediaKind.DIRECT -> listOf(whole(candidate))
                else -> listOf(1080, 720, 360).map { rendition(candidate, it) }
            }
            return VariantResolutionResult.Success(
                MediaAsset(
                    sourcePageUrl = candidate.pageUrl,
                    title = MediaGroups.baseTitle(candidate.title),
                    thumbnailUrl = null,
                    durationMillis = candidate.durationMillis,
                    variants = variants,
                    resolvedAtEpochMs = NOW,
                ),
            )
        }

        private fun whole(candidate: MediaCandidate): MediaVariant {
            val audio = candidate.mimeType?.startsWith("audio/") == true
            val name = candidate.mediaUrl.substringBefore('?').substringAfterLast('/')
            return MediaVariant(
                id = "direct-0",
                playbackUrl = candidate.mediaUrl,
                kind = MediaKind.DIRECT,
                trackType = if (audio) MediaTrackType.AUDIO else MediaTrackType.AUDIO_VIDEO,
                requestContext = candidate.requestContext,
                mimeType = candidate.mimeType,
                container = if (audio) "m4a" else "mp4",
                codecs = candidate.codecs,
                height = candidate.height ?: measured[name],
                durationMillis = candidate.durationMillis,
                audioCompanion = candidate.audioCompanion,
            )
        }

        private fun rendition(candidate: MediaCandidate, height: Int): MediaVariant {
            val playlist = candidate.kind == MediaKind.HLS
            return MediaVariant(
                id = "${candidate.kind.name.lowercase()}-$height",
                playbackUrl = if (playlist) {
                    candidate.mediaUrl.replace("?", "/$height.m3u8?")
                } else {
                    candidate.mediaUrl
                },
                kind = candidate.kind,
                trackType = MediaTrackType.AUDIO_VIDEO,
                requestContext = candidate.requestContext,
                manifestVariantId = "video-$height",
                mimeType = "video/mp4",
                codecs = listOf("avc1.64001F", "mp4a.40.2"),
                width = height * 16 / 9,
                height = height,
                framesPerSecond = 30.0,
                bitrateBitsPerSecond = height * 4_000L,
                durationMillis = candidate.durationMillis,
            )
        }
    }

    /** A direct file's engine: run n fails with 403 after [failAt] (n) bytes, else completes. */
    private class ScriptedTransfers(
        private val failAt: (run: Int) -> Long?,
    ) : DownloadTransferDispatcher {
        val plans = CopyOnWriteArrayList<DownloadPlan>()
        val resumedFrom = CopyOnWriteArrayList<Long>()

        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult {
            val run = plans.size
            plans += plan
            val start = resumeFrom?.downloadedBytes ?: 0L
            resumedFrom += start
            failAt(run)?.let { bytes ->
                val written = checkpoint(bytes)
                onCheckpoint(written)
                return QueueTransferResult.Failure(DENIED, written)
            }
            onCheckpoint(checkpoint(start + MIB))
            val done = checkpoint(TOTAL)
            onCheckpoint(done)
            return QueueTransferResult.Completed(TOTAL, done)
        }

        override suspend fun discard(plan: DownloadPlan) = Unit

        private fun checkpoint(bytes: Long) = DirectTransferCheckpoint(
            totalBytes = TOTAL,
            entityTag = "\"fixture\"",
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, TOTAL - 1, bytes)),
        )
    }

    private companion object {
        const val TITLE = "Ocean waves"
        const val LENGTH = 252_000L
        const val HIGH_FRAME_RATE = 30.5
        const val TAB_AGENT = "Mozilla/5.0 (Linux; Android 15; wv) WebView/test"
        const val OTHER_PAGE = "https://www.example.test/post/1"
        val GONE = LinkRenewal.Unavailable(SiteLinkRenewal.VIDEO_GONE)
        val NO_QUALITY = LinkRenewal.Unavailable(SiteLinkRenewal.QUALITY_GONE)
        val DENIED = DownloadFailure(
            reason = DownloadFailureReason.ACCESS_DENIED,
            httpStatusCode = 403,
            stage = DownloadFailureStage.CONNECT,
            detail = "403 text/plain len 0",
        )
    }
}
