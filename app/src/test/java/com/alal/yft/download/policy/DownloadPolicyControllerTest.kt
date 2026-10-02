package com.alal.yft.download.policy

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.SeekableDownloadOutput
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.settings.DownloadPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller observes from a background scope, so these tests drive it with [runCurrent]:
 * `advanceUntilIdle` stops as soon as no foreground work is left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadPolicyControllerTest {
    @Test
    fun `wifi only keeps new work waiting on mobile data and starts it on wifi`() = runTest {
        val harness = Harness(this, mobileData, DownloadPreferences(unmeteredOnly = true))

        harness.controller.ensureApplied()
        harness.enqueue("one")
        runCurrent()

        assertEquals(TransferNetworkState.WAITING_FOR_UNMETERED, harness.controller.state.value)
        assertEquals(DownloadTaskStatus.WAITING_FOR_NETWORK, harness.status("one"))

        harness.network.value = wifi
        runCurrent()

        assertEquals(TransferNetworkState.ALLOWED, harness.controller.state.value)
        assertEquals(DownloadTaskStatus.RUNNING, harness.status("one"))
    }

    @Test
    fun `turning wifi only on pauses running work until an unmetered network returns`() =
        runTest {
            val harness = Harness(this, mobileData, DownloadPreferences())
            harness.controller.ensureApplied()
            harness.enqueue("one")
            runCurrent()
            assertEquals(DownloadTaskStatus.RUNNING, harness.status("one"))

            harness.preferences.update { it.copy(unmeteredOnly = true) }
            runCurrent()

            assertEquals(DownloadTaskStatus.WAITING_FOR_NETWORK, harness.status("one"))
        }

    @Test
    fun `losing the network is reported as offline`() = runTest {
        val harness = Harness(this, wifi, DownloadPreferences())
        harness.controller.ensureApplied()

        harness.network.value = NetworkSnapshot.OFFLINE
        runCurrent()

        assertEquals(TransferNetworkState.OFFLINE, harness.controller.state.value)
    }

    @Test
    fun `the concurrency preference bounds how many transfers run`() = runTest {
        val harness = Harness(this, wifi, DownloadPreferences(maxConcurrentDownloads = 1))
        harness.controller.ensureApplied()
        harness.enqueue("one")
        harness.enqueue("two")
        runCurrent()

        assertEquals(DownloadTaskStatus.RUNNING, harness.status("one"))
        assertEquals(DownloadTaskStatus.QUEUED, harness.status("two"))

        harness.preferences.update { it.copy(maxConcurrentDownloads = 2) }
        runCurrent()

        assertEquals(DownloadTaskStatus.RUNNING, harness.status("two"))
    }

    @Test
    fun `applying the policy twice registers its observers once`() = runTest {
        val harness = Harness(this, wifi, DownloadPreferences())

        harness.controller.ensureApplied()
        runCurrent()
        val afterFirst = harness.preferences.collectors
        harness.controller.ensureApplied()
        runCurrent()

        assertEquals(afterFirst, harness.preferences.collectors)
    }

    @Test
    fun `metered confirmation is only asked when it can matter`() {
        val prefs = DownloadPreferences()

        assertTrue(DownloadNetworkPolicy.needsMeteredConfirmation(mobileData, prefs))
        assertFalse(DownloadNetworkPolicy.needsMeteredConfirmation(wifi, prefs))
        assertFalse(
            DownloadNetworkPolicy.needsMeteredConfirmation(
                mobileData,
                prefs.copy(confirmOnMeteredNetwork = false),
            ),
        )
        assertFalse(
            DownloadNetworkPolicy.needsMeteredConfirmation(
                mobileData,
                prefs.copy(unmeteredOnly = true),
            ),
        )
        assertFalse(DownloadNetworkPolicy.needsMeteredConfirmation(NetworkSnapshot.OFFLINE, prefs))
    }

    @Test
    fun `an unvalidated network does not count as online`() {
        val captive = NetworkSnapshot(connected = true, validated = false, unmetered = true)

        assertEquals(
            TransferNetworkState.OFFLINE,
            DownloadNetworkPolicy.stateFor(captive, DownloadPreferences()),
        )
    }

    private class Harness(
        scope: TestScope,
        initialNetwork: NetworkSnapshot,
        initialPreferences: DownloadPreferences,
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val network = MutableStateFlow(initialNetwork)
        val preferences = FakePreferences(initialPreferences)
        val queue = DownloadQueue(
            store = InMemoryStore(),
            transferDispatcher = NeverFinishingDispatcher(),
            scope = scope.backgroundScope,
            clock = { 1_000 },
            ioDispatcher = dispatcher,
        )
        val controller = DownloadPolicyController(
            queue = queue,
            network = object : NetworkStatusSource {
                override val snapshot: StateFlow<NetworkSnapshot> = network
            },
            preferences = preferences,
            scope = scope.backgroundScope,
        )

        suspend fun enqueue(id: String) {
            queue.enqueue(
                plan = DirectDownloadPlan(
                    taskId = id,
                    sourceUrl = "https://media.example.test/$id.mp4",
                    suggestedFileName = "$id.mp4",
                    requestContext = BrowserRequestContext(
                        pageUrl = "https://page.example.test/",
                        userAgent = "fixture-agent",
                        cookie = null,
                    ),
                ),
                metadata = RemoteFileMetadata(
                    finalUrl = "https://media.example.test/$id.mp4",
                    totalBytes = 10,
                    supportsByteRanges = true,
                    entityTag = null,
                    lastModified = null,
                    contentType = "video/mp4",
                    suggestedFileName = "$id.mp4",
                ),
                destination = NoOpDestination(),
            )
        }

        fun status(id: String): DownloadTaskStatus =
            queue.tasks.value.single { it.id == id }.status
    }

    private class FakePreferences(initial: DownloadPreferences) : DownloadPreferencesRepository {
        private val state = MutableStateFlow(initial)
        var collectors = 0

        override val preferences: Flow<DownloadPreferences> = flow {
            collectors += 1
            state.collect { emit(it) }
        }

        override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) {
            state.value = transform(state.value)
        }
    }

    private class InMemoryStore : DownloadTaskStore {
        private val saved = linkedMapOf<String, StoredDownloadTask>()

        override suspend fun loadAll(): List<StoredDownloadTask> = saved.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            saved[task.id] = task
        }

        override suspend fun delete(id: String) {
            saved.remove(id)
        }
    }

    private class NoOpDestination : DownloadDestination {
        override fun prepare(expectedLength: Long?) = Unit
        override fun temporaryLength(): Long? = null
        override fun open(): SeekableDownloadOutput = throw UnsupportedOperationException()
        override fun commit() = Unit
        override fun discard() = Unit
    }

    private class NeverFinishingDispatcher : DownloadTransferDispatcher {
        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult {
            CompletableDeferred<Unit>().await()
            return QueueTransferResult.Completed(
                0,
                DirectTransferCheckpoint(null, null, null, emptyList()),
            )
        }

        override suspend fun discard(plan: DownloadPlan) = Unit
    }

    private companion object {
        val wifi = NetworkSnapshot(connected = true, validated = true, unmetered = true)
        val mobileData = NetworkSnapshot(connected = true, validated = true, unmetered = false)
    }
}
