package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track E (E1): the queue asks for a fresh link when a file's link dies after the download
 * started, keeps the resume point, counts its tries, and stops cleanly. The engine and the
 * app's renewal source are fakes; the engines' own same-file checks have their own tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadLinkRenewalTest {
    @Test
    fun `without a renewal source every failure ends as before`() = runTest {
        val engine = FakeEngine { call, _ ->
            when (call) {
                0 -> Run.Fail(DENIED, bytes = 4)
                else -> Run.Fail(BAD_REQUEST, bytes = 4)
            }
        }
        val queue = queue(engine)
        queue.enqueue(direct("denied"), metadata(), FakeDestination())
        queue.enqueue(direct("bad"), metadata(), FakeDestination())
        runCurrent()

        val tasks = queue.tasks.value.associateBy { it.id }
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, tasks.getValue("denied").status)
        assertTrue(tasks.getValue("denied").requiresLinkRefresh)
        assertEquals(DENIED, tasks.getValue("denied").failure)
        assertEquals(4L, tasks.getValue("denied").downloadedBytes)
        assertEquals(DownloadTaskStatus.FAILED, tasks.getValue("bad").status)
        assertEquals(2, engine.calls.size)

        // A "Link expired" row stays one without a source: Resume and Retry change nothing.
        queue.resume("denied")
        runCurrent()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, queue.task("denied").status)
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun `a dead link asks once and the fresh link resumes from the kept checkpoint`() = runTest {
        val engine = FakeEngine { call, _ ->
            if (call == 0) Run.Fail(DENIED, bytes = 4) else Run.Complete
        }
        val answer = CompletableDeferred<LinkRenewal>()
        val renewal = FakeRenewal { answer.await() }
        val store = InMemoryStore()
        val queue = queue(engine, renewal, store = store)
        queue.enqueue(direct("dies"), metadata(), FakeDestination())
        runCurrent()

        val renewing = queue.task("dies")
        assertEquals(DownloadTaskStatus.RENEWING_LINK, renewing.status)
        assertEquals(4L, renewing.downloadedBytes)
        assertEquals(DownloadTaskStatus.RENEWING_LINK, store.loadAll().single().status)
        val request = renewal.requests.single()
        assertEquals("dies", request.taskId)
        assertEquals(DownloadPlanType.DIRECT, request.planType)
        assertEquals(DownloadFailureReason.ACCESS_DENIED, request.reason)
        assertEquals(403, request.httpStatusCode)
        assertEquals(DownloadFailureStage.CONNECT, request.stage)
        assertEquals(4L, request.bytesKept)
        assertEquals(4L, request.bytesFromThisLink)
        assertFalse(request.byUser)
        assertEquals(0, request.automaticRenewals)
        // No link, cookie or page in the request: its only text is the task id.
        assertEquals(
            listOf("taskId"),
            LinkRenewalRequest::class.java.declaredFields
                .filter { it.type == String::class.java }
                .map { it.name },
        )
        assertFalse(request.toString().contains("https"))

        answer.complete(LinkRenewal.Fresh(direct("dies", link = FRESH_LINK), metadata()))
        runCurrent()

        assertEquals(DownloadTaskStatus.COMPLETED, queue.task("dies").status)
        assertEquals(2, engine.calls.size)
        val second = engine.calls[1]
        assertEquals(FRESH_LINK, (second.plan as DirectDownloadPlan).sourceUrl)
        assertEquals(4L, second.resumeFrom?.downloadedBytes)
        assertEquals(DownloadRequestTag("dies", 0), engine.calls[0].tag)
        assertEquals(DownloadRequestTag("dies", 1), second.tag)
        assertEquals(1, renewal.requests.size)
    }

    @Test
    fun `every reason of a dead link asks for a fresh link`() = runTest {
        val reasons = listOf(
            DownloadFailure(DownloadFailureReason.EXPIRED_URL, stage = CONNECT),
            DownloadFailure(DownloadFailureReason.AUTHENTICATION_REQUIRED, 401, CONNECT),
            DENIED,
            DownloadFailure(DownloadFailureReason.GONE, 410, CONNECT),
            DownloadFailure(DownloadFailureReason.YOUTUBE_REFUSED, 403, CONNECT),
        )
        reasons.forEach { failure ->
            val engine = FakeEngine { call, _ ->
                if (call == 0) Run.Fail(failure, bytes = 2) else Run.Complete
            }
            val renewal = FakeRenewal { request ->
                LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), metadata())
            }
            val queue = queue(engine, renewal)
            queue.enqueue(direct("t"), metadata(), FakeDestination())
            runCurrent()

            assertEquals(failure.reason.name, 1, renewal.requests.size)
            assertEquals(failure.reason, renewal.requests.single().reason)
            assertEquals(failure.reason.name, DownloadTaskStatus.COMPLETED, queue.task("t").status)
        }
    }

    @Test
    fun `a 400 asks only after this link gave bytes`() = runTest {
        val engine = FakeEngine { call, plan ->
            when {
                plan.taskId == "at-zero" -> Run.Fail(BAD_REQUEST, bytes = 0)
                call < 2 -> Run.Fail(BAD_REQUEST, bytes = 3)
                else -> Run.Complete
            }
        }
        val renewal = FakeRenewal { request ->
            LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), metadata())
        }
        val queue = queue(engine, renewal, capacity = 1)
        queue.enqueue(direct("at-zero"), metadata(), FakeDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.FAILED, queue.task("at-zero").status)
        assertTrue(renewal.requests.isEmpty())

        queue.enqueue(direct("after-bytes"), metadata(), FakeDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.COMPLETED, queue.task("after-bytes").status)
        assertEquals(listOf("after-bytes"), renewal.requests.map { it.taskId })
        assertEquals(400, renewal.requests.single().httpStatusCode)
        assertEquals(3L, renewal.requests.single().bytesFromThisLink)
    }

    @Test
    fun `other failures never ask for a fresh link`() = runTest {
        val failures = listOf(
            DownloadFailure(DownloadFailureReason.NOT_FOUND, 404, CONNECT),
            DownloadFailure(DownloadFailureReason.RANGE_NOT_SATISFIABLE, 416, CONNECT),
            DownloadFailure(DownloadFailureReason.SERVER_ERROR, 503, CONNECT),
            DownloadFailure(DownloadFailureReason.HTTP_STATUS, 429, CONNECT),
            DownloadFailure(DownloadFailureReason.NETWORK, stage = DownloadFailureStage.READ_SOURCE),
            DownloadFailure(DownloadFailureReason.STORAGE_UNAVAILABLE, stage = WRITE),
            DownloadFailure(DownloadFailureReason.INSUFFICIENT_STORAGE, stage = WRITE),
            DownloadFailure(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY),
        )
        val renewal = FakeRenewal { error("never asked") }
        failures.forEachIndexed { index, failure ->
            val engine = FakeEngine { _, _ -> Run.Fail(failure, bytes = 2) }
            val queue = queue(engine, renewal)
            queue.enqueue(direct("t$index"), metadata(), FakeDestination())
            runCurrent()
            assertEquals(failure.reason.name, DownloadTaskStatus.FAILED, queue.task("t$index").status)
        }
        assertTrue(renewal.requests.isEmpty())
    }

    /** G1 and §7.3: Resume after the link expired keeps the bytes of every plan type. */
    @Test
    fun `a resume after the link expired keeps the saved checkpoint of every plan type`() =
        runTest {
            listOf(
                direct("direct"),
                hls("hls"),
                dash("dash"),
                mux("mux"),
            ).forEach { plan ->
                val hold = CompletableDeferred<Run>()
                val engine = FakeEngine { call, _ ->
                    when (call) {
                        0 -> Run.Hold(bytes = 4, next = hold)
                        1 -> Run.Fail(EXPIRED, untouched = true)
                        else -> Run.Complete
                    }
                }
                val renewal = FakeRenewal {
                    LinkRenewal.Fresh(
                        plan.withLink(FRESH_LINK),
                        metadata().takeIf { plan is DirectDownloadPlan },
                    )
                }
                val queue = queue(engine, renewal)
                queue.enqueueAny(plan)
                runCurrent()
                queue.pause(plan.taskId)
                assertEquals(DownloadTaskStatus.PAUSED, queue.task(plan.taskId).status)
                val saved = queue.task(plan.taskId).checkpoint
                assertEquals(4L, saved.downloadedBytes)

                queue.resume(plan.taskId)
                runCurrent()

                val name = plan.javaClass.simpleName
                assertEquals(name, 1, renewal.requests.size)
                assertEquals(name, 4L, renewal.requests.single().bytesKept)
                assertEquals(name, 0L, renewal.requests.single().bytesFromThisLink)
                assertEquals(name, saved, engine.calls[2].resumeFrom)
                assertEquals(name, DownloadTaskStatus.COMPLETED, queue.task(plan.taskId).status)
            }
        }

    /** G1 for a merge: a track the failed run did not touch keeps its saved state. */
    @Test
    fun `a merge keeps the saved state of the track the failed run did not touch`() = runTest {
        val plan = mux("mux")
        val savedVideo = dashCheckpoint(bytes = 6, complete = false)
        val engine = FakeEngine { call, _ ->
            when (call) {
                0 -> Run.Custom(
                    checkpoint = AudioVideoMuxCheckpoint(video = savedVideo, audio = null),
                    failure = DownloadFailure(DownloadFailureReason.NETWORK, stage = READ),
                )
                1 -> Run.Custom(
                    // The video's manifest was refused; the sound got 2 bytes.
                    checkpoint = AudioVideoMuxCheckpoint(
                        video = DashTransferCheckpoint(null, emptyList()),
                        audio = dashCheckpoint(bytes = 2, complete = false),
                    ),
                    failure = DENIED,
                )
                else -> Run.Complete
            }
        }
        val renewal = FakeRenewal { LinkRenewal.Fresh(plan.withLink(FRESH_LINK)) }
        val queue = queue(engine, renewal)
        queue.enqueue(plan, FakeDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.FAILED, queue.task("mux").status)

        queue.resume("mux")
        runCurrent()

        val resumed = engine.calls[2].resumeFrom as AudioVideoMuxCheckpoint
        assertEquals(savedVideo, resumed.video)
        assertEquals(2L, resumed.audio?.downloadedBytes)
        assertEquals(DownloadTaskStatus.COMPLETED, queue.task("mux").status)
    }

    @Test
    fun `no fresh link stops as link expired with its line and keeps the partial file`() =
        runTest {
            val engine = FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }
            val renewal = FakeRenewal { LinkRenewal.Unavailable("this quality is no longer offered") }
            val store = InMemoryStore()
            val destination = FakeDestination()
            val queue = queue(engine, renewal, store = store)
            queue.enqueue(direct("gone"), metadata(), destination)
            runCurrent()

            val task = queue.task("gone")
            assertEquals(DownloadTaskStatus.NEEDS_REFRESH, task.status)
            assertTrue(task.requiresLinkRefresh)
            assertEquals(DownloadFailureReason.ACCESS_DENIED, task.failureReason)
            assertEquals(403, task.failure?.httpStatusCode)
            assertEquals(
                "Fresh link: this quality is no longer offered; 403 text/plain len 0",
                task.failure?.detail,
            )
            assertEquals(4L, task.downloadedBytes)
            assertEquals(task, store.loadAll().single())
            assertFalse(destination.discarded.get())
            assertEquals(1, engine.calls.size)
        }

    @Test
    fun `declined renewals end as before`() = runTest {
        val engine = FakeEngine { call, plan ->
            when {
                plan.taskId == "denied" -> Run.Fail(DENIED, bytes = 4)
                call == 1 -> Run.Fail(BAD_REQUEST, bytes = 4)
                else -> Run.Complete
            }
        }
        val renewal = FakeRenewal { LinkRenewal.Declined }
        val queue = queue(engine, renewal, capacity = 1)
        queue.enqueue(direct("denied"), metadata(), FakeDestination())
        queue.enqueue(direct("bad"), metadata(), FakeDestination())
        runCurrent()

        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, queue.task("denied").status)
        assertEquals(DENIED, queue.task("denied").failure)
        val bad = queue.task("bad")
        assertEquals(DownloadTaskStatus.FAILED, bad.status)
        assertEquals(BAD_REQUEST, bad.failure)
        assertEquals(2, renewal.requests.size)

        // A failed row's Retry asks the same link again, as before.
        queue.resume("bad")
        runCurrent()
        assertEquals(DownloadTaskStatus.COMPLETED, queue.task("bad").status)
        assertEquals(ORIGINAL_LINK, (engine.calls.last().plan as DirectDownloadPlan).sourceUrl)
        assertEquals(2, renewal.requests.size)
    }

    @Test
    fun `a renewal that takes too long or fails stops with its line`() = runTest {
        val engine = FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }
        val slow = FakeRenewal { awaitCancellation() }
        val queue = queue(engine, slow)
        queue.enqueue(direct("slow"), metadata(), FakeDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.RENEWING_LINK, queue.task("slow").status)

        advanceTimeBy(89_999L)
        runCurrent()
        assertEquals(DownloadTaskStatus.RENEWING_LINK, queue.task("slow").status)
        advanceTimeBy(2L)
        runCurrent()
        val stopped = queue.task("slow")
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
        assertTrue(
            stopped.failure?.detail.orEmpty()
                .startsWith("Fresh link: the site did not answer in 90 s"),
        )

        val broken = FakeRenewal { throw IOException("lookup broke") }
        val other = queue(FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }, broken)
        other.enqueue(direct("broken"), metadata(), FakeDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, other.task("broken").status)
        assertTrue(
            other.task("broken").failure?.detail.orEmpty()
                .startsWith("Fresh link: IOException: lookup broke"),
        )
    }

    /** E-D3: one fresh link per failure, another only after it gave bytes, 3 at most. */
    @Test
    fun `automatic renewals stop after three and a retry asks once more`() = runTest {
        val engine = FakeEngine { call, _ ->
            if (call < 4) Run.Fail(DENIED, bytes = 2) else Run.Complete
        }
        var fresh = 0
        val renewal = FakeRenewal { request ->
            fresh += 1
            LinkRenewal.Fresh(direct(request.taskId, link = "$FRESH_LINK&n=$fresh"), metadata())
        }
        val queue = queue(engine, renewal)
        queue.enqueue(direct("capped"), metadata(), FakeDestination())
        runCurrent()

        assertEquals(listOf(0, 1, 2), renewal.requests.map { it.automaticRenewals })
        assertTrue(renewal.requests.none { it.byUser })
        val stopped = queue.task("capped")
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, stopped.status)
        assertEquals(8L, stopped.downloadedBytes)
        assertEquals(4, engine.calls.size)
        assertEquals(listOf(0, 1, 2, 3), engine.calls.map { it.tag?.link })

        // E-D6: a Retry is one lookup, not counted in the three.
        queue.resume("capped")
        runCurrent()
        assertEquals(4, renewal.requests.size)
        assertTrue(renewal.requests.last().byUser)
        assertEquals(3, renewal.requests.last().automaticRenewals)
        assertEquals(DownloadTaskStatus.COMPLETED, queue.task("capped").status)
        assertEquals(8L, engine.calls.last().resumeFrom?.downloadedBytes)
    }

    @Test
    fun `a fresh link that dies before it gives bytes is not renewed again`() = runTest {
        val engine = FakeEngine { call, _ ->
            when (call) {
                0 -> Run.Fail(DENIED, bytes = 4)
                1 -> Run.Fail(DENIED, bytes = 0)
                else -> Run.Complete
            }
        }
        val renewal = FakeRenewal { request ->
            LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), metadata())
        }
        val queue = queue(engine, renewal)
        queue.enqueue(direct("zero"), metadata(), FakeDestination())
        runCurrent()

        assertEquals(1, renewal.requests.size)
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, queue.task("zero").status)
        assertEquals(4L, queue.task("zero").downloadedBytes)
    }

    /** E-D5': a "YouTube refused" is renewed once per download; refused again, it stops as B1. */
    @Test
    fun `youtube refused is renewed once`() = runTest {
        val refused = DownloadFailure(DownloadFailureReason.YOUTUBE_REFUSED, 403, CONNECT)
        val engine = FakeEngine { _, _ -> Run.Fail(refused, bytes = 3) }
        val renewal = FakeRenewal { request ->
            LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), metadata())
        }
        val queue = queue(engine, renewal)
        queue.enqueue(direct("yt"), metadata(), FakeDestination())
        runCurrent()

        assertEquals(1, renewal.requests.size)
        assertEquals(2, engine.calls.size)
        val task = queue.task("yt")
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, task.status)
        assertEquals(DownloadFailureReason.YOUTUBE_REFUSED, task.failureReason)
    }

    @Test
    fun `pause during a renewal drops the late answer and resume asks again`() = runTest {
        val engine = FakeEngine { call, _ ->
            if (call == 0) Run.Fail(DENIED, bytes = 4) else Run.Complete
        }
        val first = CompletableDeferred<LinkRenewal>()
        val asked = AtomicInteger()
        val renewal = FakeRenewal { request ->
            if (asked.incrementAndGet() == 1) {
                first.await()
            } else {
                LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), metadata())
            }
        }
        val queue = queue(engine, renewal)
        queue.enqueue(direct("paused"), metadata(), FakeDestination())
        runCurrent()
        assertEquals(DownloadTaskStatus.RENEWING_LINK, queue.task("paused").status)

        queue.pause("paused")
        first.complete(LinkRenewal.Fresh(direct("paused", link = FRESH_LINK), metadata()))
        runCurrent()

        assertEquals(DownloadTaskStatus.PAUSED, queue.task("paused").status)
        assertEquals(1, renewal.cancelled.get())
        assertEquals(1, engine.calls.size)

        queue.resume("paused")
        runCurrent()
        assertEquals(2, renewal.requests.size)
        assertTrue(renewal.requests.last().byUser)
        assertEquals(DownloadTaskStatus.COMPLETED, queue.task("paused").status)
        assertEquals(4L, engine.calls.last().resumeFrom?.downloadedBytes)
    }

    @Test
    fun `cancel and delete during a renewal leave no file and no workspace`() = runTest {
        val engine = FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }
        val renewal = FakeRenewal { awaitCancellation() }
        val queue = queue(engine, renewal)
        val cancelled = FakeDestination()
        val deleted = FakeDestination()
        queue.enqueue(hls("cancelled"), cancelled)
        queue.enqueue(dash("deleted"), deleted)
        runCurrent()
        assertTrue(queue.tasks.value.all { it.status == DownloadTaskStatus.RENEWING_LINK })

        queue.cancel("cancelled")
        queue.deleteRecord("deleted")
        runCurrent()

        assertEquals(DownloadTaskStatus.CANCELLED, queue.task("cancelled").status)
        assertTrue(cancelled.discarded.get())
        assertTrue(deleted.discarded.get())
        assertEquals(listOf("cancelled", "deleted"), engine.discarded.map { it.taskId })
        assertEquals(listOf("cancelled"), queue.tasks.value.map { it.id })
        assertEquals(2, renewal.cancelled.get())
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun `a lost network stops the renewal and it runs again first when the network is back`() =
        runTest {
            val engine = FakeEngine { call, _ ->
                if (call == 0) Run.Fail(DENIED, bytes = 4) else Run.Complete
            }
            val asked = AtomicInteger()
            val renewal = FakeRenewal { request ->
                if (asked.incrementAndGet() == 1) {
                    awaitCancellation()
                } else {
                    LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), metadata())
                }
            }
            val queue = queue(engine, renewal)
            queue.enqueue(direct("offline"), metadata(), FakeDestination())
            runCurrent()

            queue.setNetworkAvailable(false)
            runCurrent()
            assertEquals(DownloadTaskStatus.WAITING_FOR_NETWORK, queue.task("offline").status)
            assertEquals(1, renewal.cancelled.get())

            queue.setNetworkAvailable(true)
            runCurrent()
            assertEquals(2, renewal.requests.size)
            assertFalse(renewal.requests.last().byUser)
            assertEquals(2, engine.calls.size)
            assertEquals(DownloadTaskStatus.COMPLETED, queue.task("offline").status)
        }

    @Test
    fun `a renewing download keeps its slot and goes on first`() = runTest {
        val dies = CompletableDeferred<Run>()
        val engine = FakeEngine { call, plan ->
            when {
                plan.taskId == "first" && call == 0 -> Run.Hold(bytes = 4, next = dies)
                else -> Run.Complete
            }
        }
        val answer = CompletableDeferred<LinkRenewal>()
        val renewal = FakeRenewal { answer.await() }
        val queue = queue(engine, renewal, capacity = 1)
        queue.enqueue(direct("first"), metadata(), FakeDestination())
        queue.enqueue(direct("second"), metadata(), FakeDestination())
        runCurrent()

        dies.complete(Run.Fail(DENIED, bytes = 0))
        runCurrent()
        assertEquals(DownloadTaskStatus.RENEWING_LINK, queue.task("first").status)
        assertEquals(DownloadTaskStatus.QUEUED, queue.task("second").status)

        answer.complete(LinkRenewal.Fresh(direct("first", link = FRESH_LINK), metadata()))
        runCurrent()
        assertEquals(listOf("first", "first", "second"), engine.calls.map { it.plan.taskId })
        assertTrue(queue.tasks.value.all { it.status == DownloadTaskStatus.COMPLETED })
    }

    @Test
    fun `a renewing download reads back as link expired after the app was closed`() = runTest {
        val saved = StoredDownloadTask(
            id = "restored",
            displayName = "restored.mp4",
            status = DownloadTaskStatus.RENEWING_LINK,
            totalBytes = TOTAL,
            downloadedBytes = 4,
            mimeType = "video/mp4",
            destinationKind = DownloadDestinationKind.APP_PRIVATE,
            destinationUri = null,
            preferredSegmentCount = 1,
            requiresLinkRefresh = false,
            failureReason = DownloadFailureReason.ACCESS_DENIED,
            checkpoint = directCheckpoint(4),
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
            failure = DENIED,
        )
        val renewal = FakeRenewal { error("no source after a restart") }
        val queue = queue(FakeEngine { _, _ -> Run.Complete }, renewal, InMemoryStore(listOf(saved)))

        queue.restore()
        queue.resume("restored")
        runCurrent()

        val restored = queue.task("restored")
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, restored.status)
        assertTrue(restored.requiresLinkRefresh)
        assertEquals(4L, restored.checkpoint.downloadedBytes)
        assertTrue(renewal.requests.isEmpty())
    }

    @Test
    fun `a fresh link of another kind or without its file facts is not used`() = runTest {
        val engine = FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }
        val renewal = FakeRenewal { request ->
            when (request.taskId) {
                "kind" -> LinkRenewal.Fresh(hls("kind"))
                "task" -> LinkRenewal.Fresh(direct("another", link = FRESH_LINK), metadata())
                else -> LinkRenewal.Fresh(direct(request.taskId, link = FRESH_LINK), null)
            }
        }
        val queue = queue(engine, renewal)
        listOf("kind", "task", "facts").forEach { id ->
            queue.enqueue(direct(id), metadata(), FakeDestination())
        }
        runCurrent()

        val details = queue.tasks.value.associate { it.id to it.failure?.detail.orEmpty() }
        assertTrue(queue.tasks.value.all { it.status == DownloadTaskStatus.NEEDS_REFRESH })
        assertTrue(details.getValue("kind").startsWith("Fresh link: another kind of file"))
        assertTrue(details.getValue("task").startsWith("Fresh link: another kind of file"))
        assertTrue(details.getValue("facts").startsWith("Fresh link: the file did not answer"))
        assertEquals(3, engine.calls.size)
    }

    /** G2: a "Link expired" row keeps only its destination, so Delete still cleans up. */
    @Test
    fun `delete of a link expired row cleans its partial file without keeping its link`() =
        runTest {
            val engine = FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }
            listOf<DownloadLinkRenewal>(
                DownloadLinkRenewal.None,
                FakeRenewal { LinkRenewal.Unavailable("no page") },
            ).forEach { renewal ->
                val destination = FakeDestination()
                val queue = queue(engine, renewal)
                queue.enqueue(
                    direct("expired").copy(mp3 = null, audioOnly = true),
                    metadata(),
                    destination,
                )
                runCurrent()
                assertEquals(DownloadTaskStatus.NEEDS_REFRESH, queue.task("expired").status)

                queue.deleteRecord("expired")

                assertTrue(destination.discarded.get())
                assertTrue(queue.tasks.value.isEmpty())
                val discarded = engine.discarded.last() as DirectDownloadPlan
                assertEquals("expired", discarded.taskId)
                assertTrue("the conversion's workspace is still found", discarded.audioOnly)
                assertFalse(discarded.sourceUrl.startsWith("http"))
                assertNull(discarded.requestContext.cookie)
                assertNull(discarded.requestContext.pageUrl)
                assertNull(discarded.requestContext.userAgent)
            }
        }

    @Test
    fun `a merge's parked plan keeps both track ids for the cleanup`() = runTest {
        val engine = FakeEngine { _, _ -> Run.Fail(DENIED, bytes = 4) }
        val queue = queue(engine)
        queue.enqueue(mux("merge"), FakeDestination())
        runCurrent()

        queue.deleteRecord("merge")

        val discarded = engine.discarded.single() as AudioVideoMuxDownloadPlan
        assertEquals("merge-video", discarded.video.taskId)
        assertEquals("merge-audio", discarded.audio.taskId)
        assertFalse(discarded.video.manifestUrl.startsWith("http"))
        assertFalse(discarded.audio.manifestUrl.startsWith("http"))
        assertNull(discarded.video.requestContext.cookie)
    }

    @Test
    fun `a fresh direct link states its file's total`() = runTest {
        val engine = FakeEngine { call, _ ->
            if (call == 0) Run.Fail(DENIED, bytes = 4) else Run.Hold(bytes = 0, next = CompletableDeferred())
        }
        val renewal = FakeRenewal { request ->
            LinkRenewal.Fresh(
                direct(request.taskId, link = FRESH_LINK),
                metadata().copy(totalBytes = TOTAL + 6),
            )
        }
        val queue = queue(engine, renewal)
        queue.enqueue(direct("total"), metadata(), FakeDestination())
        runCurrent()

        val running = queue.task("total")
        assertEquals(DownloadTaskStatus.RUNNING, running.status)
        assertEquals(TOTAL + 6, running.totalBytes)
        assertNull(running.failure)
        assertFalse(running.requiresLinkRefresh)
    }

    // ---- helpers ----

    private fun TestScope.queue(
        engine: FakeEngine,
        renewal: DownloadLinkRenewal = DownloadLinkRenewal.None,
        store: InMemoryStore = InMemoryStore(),
        capacity: Int = 2,
    ): DownloadQueue = DownloadQueue(
        store = store,
        transferDispatcher = engine,
        scope = backgroundScope,
        maxConcurrentDownloads = capacity,
        linkRenewal = renewal,
    )

    private fun DownloadQueue.task(id: String): StoredDownloadTask =
        tasks.value.single { it.id == id }

    private suspend fun DownloadQueue.enqueueAny(plan: DownloadPlan) {
        when (plan) {
            is DirectDownloadPlan -> enqueue(plan, metadata(), FakeDestination())
            is HlsDownloadPlan -> enqueue(plan, FakeDestination())
            is DashDownloadPlan -> enqueue(plan, FakeDestination())
            is AudioVideoMuxDownloadPlan -> enqueue(plan, FakeDestination())
        }
    }

    private fun DownloadPlan.withLink(link: String): DownloadPlan = when (this) {
        is DirectDownloadPlan -> copy(sourceUrl = link)
        is HlsDownloadPlan -> copy(playlistUrl = link)
        is DashDownloadPlan -> copy(manifestUrl = link)
        is AudioVideoMuxDownloadPlan -> copy(
            video = video.copy(manifestUrl = "$link&track=video"),
            audio = audio.copy(manifestUrl = "$link&track=audio"),
        )
    }

    /** One run of the fake engine: what it writes from the checkpoint it resumes, then its end. */
    private sealed interface Run {
        /** Writes [bytes] more, then fails; [untouched] fails before reading the saved state. */
        data class Fail(
            val failure: DownloadFailure,
            val bytes: Long = 0,
            val untouched: Boolean = false,
        ) : Run

        /** Ends with this exact checkpoint and failure. */
        data class Custom(val checkpoint: TransferCheckpoint, val failure: DownloadFailure) : Run

        /** Writes [bytes] more, then waits for its [next] run, or is stopped. */
        data class Hold(val bytes: Long, val next: CompletableDeferred<Run>) : Run

        /** Writes to the end and completes. */
        data object Complete : Run
    }

    private data class Call(
        val plan: DownloadPlan,
        val resumeFrom: TransferCheckpoint?,
        val tag: DownloadRequestTag?,
    )

    private class FakeEngine(
        private val script: (call: Int, plan: DownloadPlan) -> Run,
    ) : DownloadTransferDispatcher {
        val calls = CopyOnWriteArrayList<Call>()
        val discarded = CopyOnWriteArrayList<DownloadPlan>()

        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult {
            val call = calls.size
            calls += Call(plan, resumeFrom, currentCoroutineContext()[DownloadRequestTag])
            val start = resumeFrom?.downloadedBytes ?: 0L
            return play(script(call, plan), plan, start, onCheckpoint)
        }

        private suspend fun play(
            run: Run,
            plan: DownloadPlan,
            start: Long,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult = when (run) {
            is Run.Fail -> if (run.untouched) {
                QueueTransferResult.Failure(run.failure, untouchedCheckpoint(plan))
            } else {
                val written = checkpointOf(plan, start + run.bytes, complete = false)
                onCheckpoint(written)
                QueueTransferResult.Failure(run.failure, written)
            }
            is Run.Custom -> QueueTransferResult.Failure(run.failure, run.checkpoint)
            is Run.Hold -> {
                if (run.bytes > 0) {
                    onCheckpoint(checkpointOf(plan, start + run.bytes, complete = false))
                }
                play(run.next.await(), plan, start + run.bytes, onCheckpoint)
            }
            Run.Complete -> {
                val done = checkpointOf(plan, TOTAL, complete = true)
                onCheckpoint(done)
                QueueTransferResult.Completed(TOTAL, done)
            }
        }

        override suspend fun discard(plan: DownloadPlan) {
            discarded += plan
        }
    }

    private class FakeRenewal(
        private val answer: suspend (LinkRenewalRequest) -> LinkRenewal,
    ) : DownloadLinkRenewal {
        val requests = CopyOnWriteArrayList<LinkRenewalRequest>()
        val cancelled = AtomicInteger()

        override suspend fun renew(request: LinkRenewalRequest): LinkRenewal {
            requests += request
            return try {
                answer(request)
            } catch (cancellation: CancellationException) {
                cancelled.incrementAndGet()
                throw cancellation
            }
        }
    }

    private class FakeDestination : DownloadDestination {
        val discarded = AtomicBoolean(false)

        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = 0
        override fun open(): SeekableDownloadOutput = error("The fake engine writes no file")
        override fun commit() = Unit
        override fun discard() {
            discarded.set(true)
        }
    }

    private class InMemoryStore(
        initial: List<StoredDownloadTask> = emptyList(),
    ) : DownloadTaskStore {
        private val gate = Mutex()
        private val records = LinkedHashMap(initial.associateBy(StoredDownloadTask::id))

        override suspend fun loadAll(): List<StoredDownloadTask> =
            gate.withLock { records.values.toList() }

        override suspend fun save(task: StoredDownloadTask) {
            gate.withLock { records[task.id] = task }
        }

        override suspend fun delete(id: String) {
            gate.withLock { records.remove(id) }
        }
    }

    private companion object {
        const val TOTAL = 10L
        const val ETAG = "\"fixture\""
        const val ORIGINAL_LINK = "https://cdn.example.test/file.mp4?sig=old"
        const val FRESH_LINK = "https://cdn.example.test/file.mp4?sig=fresh"
        const val FINGERPRINT = "c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00"
        val CONNECT = DownloadFailureStage.CONNECT
        val READ = DownloadFailureStage.READ_SOURCE
        val WRITE = DownloadFailureStage.WRITE_FILE
        val VERIFY = DownloadFailureStage.VERIFY
        val DENIED = DownloadFailure(
            reason = DownloadFailureReason.ACCESS_DENIED,
            httpStatusCode = 403,
            stage = CONNECT,
            detail = "403 text/plain len 0",
        )
        val BAD_REQUEST = DownloadFailure(DownloadFailureReason.HTTP_STATUS, 400, CONNECT)
        val EXPIRED = DownloadFailure(DownloadFailureReason.EXPIRED_URL, stage = CONNECT)

        fun context(): BrowserRequestContext = BrowserRequestContext(
            pageUrl = "https://example.test/watch?v=fixture",
            userAgent = "fixture",
            cookie = "session=fixture",
        )

        fun direct(id: String, link: String = ORIGINAL_LINK) = DirectDownloadPlan(
            taskId = id,
            sourceUrl = link,
            suggestedFileName = "$id.mp4",
            requestContext = context(),
            mimeType = "video/mp4",
            expectedBytes = TOTAL,
            preferredSegmentCount = 1,
        )

        fun hls(id: String) = HlsDownloadPlan(
            taskId = id,
            playlistUrl = "https://cdn.example.test/$id.m3u8?sig=old",
            suggestedFileName = "$id.mp4",
            requestContext = context(),
            mimeType = "video/mp4",
        )

        fun dash(id: String, type: MediaTrackType = MediaTrackType.VIDEO) = DashDownloadPlan(
            taskId = id,
            manifestUrl = "https://cdn.example.test/$id.mp4?sig=old",
            representationId = type.name.lowercase(),
            trackType = type,
            suggestedFileName = "$id.mp4",
            requestContext = context(),
            mimeType = if (type == MediaTrackType.VIDEO) "video/mp4" else "audio/mp4",
            wholeFile = WholeFileTrack(totalBytes = TOTAL),
        )

        fun mux(id: String) = AudioVideoMuxDownloadPlan(
            taskId = id,
            video = dash("$id-video", MediaTrackType.VIDEO),
            audio = dash("$id-audio", MediaTrackType.AUDIO),
            suggestedFileName = "$id.mp4",
        )

        fun metadata() = RemoteFileMetadata(
            finalUrl = ORIGINAL_LINK,
            totalBytes = TOTAL,
            supportsByteRanges = true,
            entityTag = ETAG,
            lastModified = null,
            contentType = "video/mp4",
            suggestedFileName = "file.mp4",
        )

        fun directCheckpoint(bytes: Long) = DirectTransferCheckpoint(
            totalBytes = TOTAL,
            entityTag = ETAG,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, TOTAL - 1, bytes)),
        )

        fun dashCheckpoint(bytes: Long, complete: Boolean) = DashTransferCheckpoint(
            manifestFingerprint = FINGERPRINT,
            chunks = listOf(StreamChunkCheckpoint(0, bytes, complete)),
        )

        fun checkpointOf(plan: DownloadPlan, bytes: Long, complete: Boolean): TransferCheckpoint =
            when (plan) {
                is DirectDownloadPlan -> directCheckpoint(bytes)
                is HlsDownloadPlan -> HlsTransferCheckpoint(
                    manifestFingerprint = FINGERPRINT,
                    chunks = listOf(StreamChunkCheckpoint(0, bytes, complete)),
                )
                is DashDownloadPlan -> dashCheckpoint(bytes, complete)
                is AudioVideoMuxDownloadPlan -> if (complete) {
                    AudioVideoMuxCheckpoint(
                        video = dashCheckpoint(bytes / 2, complete = true),
                        audio = dashCheckpoint(bytes - bytes / 2, complete = true),
                        videoReady = true,
                        audioReady = true,
                        stage = AudioVideoMuxStage.COMPLETED,
                    )
                } else {
                    AudioVideoMuxCheckpoint(video = dashCheckpoint(bytes, complete = false))
                }
            }

        fun untouchedCheckpoint(plan: DownloadPlan): TransferCheckpoint = when (plan) {
            is DirectDownloadPlan -> DirectTransferCheckpoint(TOTAL, ETAG, null, emptyList())
            is HlsDownloadPlan -> HlsTransferCheckpoint(null, emptyList())
            is DashDownloadPlan -> DashTransferCheckpoint(null, emptyList())
            is AudioVideoMuxDownloadPlan -> AudioVideoMuxCheckpoint()
        }
    }
}
