package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.download.DownloadServiceStarter
import com.alal.yft.download.DownloadStorageSource
import com.alal.yft.download.InMemoryBackgroundHealthStore
import com.alal.yft.download.backgroundTask
import com.alal.yft.download.policy.DownloadNetworkStatus
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** P34: Resume starts the background service, and the cards follow the phone's settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelBackgroundTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val health = InMemoryBackgroundHealthStore()
    private var system = BackgroundSystemState(
        unrestricted = false,
        notificationsVisible = false,
        xiaomi = true,
    )
    private val starter = CountingStarter()

    private fun viewModel(vararg tasks: StoredDownloadTask) = DownloadsViewModel(
        queue = DownloadQueue(
            store = MemoryStore(tasks.toList()),
            transferDispatcher = NeverFinishing(),
            scope = CoroutineScope(mainDispatcherRule.dispatcher),
            maxConcurrentDownloads = 1,
            clock = { 1_000 },
            ioDispatcher = mainDispatcherRule.dispatcher,
        ),
        networkStatus = DownloadNetworkStatus.AlwaysAllowed,
        storageSource = DownloadStorageSource.None,
        speeds = DownloadSpeedMeter(),
        health = health,
        systemStatus = { system },
        serviceStarter = starter,
    )

    @Test
    fun resumeAndRetryAlsoStartTheBackgroundService() = runTest {
        val viewModel = viewModel(
            backgroundTask("paused", DownloadTaskStatus.PAUSED),
            backgroundTask("failed", DownloadTaskStatus.FAILED),
        )
        runCurrent()

        viewModel.onAction(DownloadAction.RESUME, "paused")
        runCurrent()
        assertEquals(1, starter.starts)

        viewModel.onAction(DownloadAction.RETRY, "failed")
        viewModel.onAction(DownloadAction.PAUSE, "failed")
        runCurrent()
        assertEquals(2, starter.starts)
    }

    @Test
    fun batteryCardFollowsTheStateReadWhenTheScreenResumes() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertNull(viewModel.uiState.value.background.batteryCard)

        health.markDownloadStarted()
        viewModel.refreshBackground()
        runCurrent()
        val card = viewModel.uiState.value.background.batteryCard
        assertTrue(card!!.canAllow)
        assertTrue(card.xiaomiSteps)

        viewModel.hideBatteryCard()
        runCurrent()
        assertNull(viewModel.uiState.value.background.batteryCard)

        health.recordFreeze(45_000)
        runCurrent()
        assertEquals(
            "Your phone paused YFT in the background for 45 s.",
            viewModel.uiState.value.background.batteryCard?.freezeMessage,
        )
    }

    @Test
    fun notificationsCardHidesWhenDismissedAndReturnsAfterNotificationsWereOn() = runTest {
        val viewModel = viewModel()
        viewModel.refreshBackground()
        runCurrent()
        assertTrue(viewModel.uiState.value.background.notificationsCard)

        viewModel.hideNotificationsCard()
        runCurrent()
        assertFalse(viewModel.uiState.value.background.notificationsCard)

        system = system.copy(notificationsVisible = true)
        viewModel.refreshBackground()
        system = system.copy(notificationsVisible = false)
        viewModel.refreshBackground()
        runCurrent()
        assertTrue(viewModel.uiState.value.background.notificationsCard)
    }

    private class CountingStarter : DownloadServiceStarter {
        var starts = 0

        override fun start() {
            starts += 1
        }
    }

    private class MemoryStore(initial: List<StoredDownloadTask>) : DownloadTaskStore {
        private val tasks = LinkedHashMap<String, StoredDownloadTask>().apply {
            initial.forEach { put(it.id, it) }
        }

        override suspend fun loadAll(): List<StoredDownloadTask> = tasks.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            tasks[task.id] = task
        }

        override suspend fun delete(id: String) {
            tasks.remove(id)
        }
    }

    private class NeverFinishing : DownloadTransferDispatcher {
        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult = awaitCancellation()

        override suspend fun discard(plan: DownloadPlan) = Unit
    }
}
