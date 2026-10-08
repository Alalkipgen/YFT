package com.alal.yft.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.BackgroundHealthStore
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.download.BackgroundSystemStatus
import com.alal.yft.download.DownloadServiceStarter
import com.alal.yft.download.DownloadStorageSource
import com.alal.yft.download.InMemoryBackgroundHealthStore
import com.alal.yft.download.policy.DownloadNetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Exposes the persisted download queue as observable UI state and forwards user controls.
 *
 * The queue remains the single owner of transfer state, so this view model performs no transfer
 * work and keeps no copy of sensitive request context. Speeds come from the [DownloadSpeedMeter]
 * the notification reads too (P34), from the progress the queue publishes.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val queue: DownloadQueue,
    private val networkStatus: DownloadNetworkStatus,
    private val storageSource: DownloadStorageSource,
    private val speeds: DownloadSpeedMeter,
    private val health: BackgroundHealthStore,
    private val systemStatus: BackgroundSystemStatus,
    private val serviceStarter: DownloadServiceStarter,
) : ViewModel() {
    /** Without a network policy or storage, for tests that only exercise the queue. */
    constructor(queue: DownloadQueue) : this(queue, DownloadNetworkStatus.AlwaysAllowed)

    /** Without storage, for tests of the network policy. */
    constructor(queue: DownloadQueue, networkStatus: DownloadNetworkStatus) :
        this(queue, networkStatus, DownloadStorageSource.None)

    /** Without the background service and cards, for tests of the queue and storage. */
    constructor(
        queue: DownloadQueue,
        networkStatus: DownloadNetworkStatus,
        storageSource: DownloadStorageSource,
    ) : this(
        queue = queue,
        networkStatus = networkStatus,
        storageSource = storageSource,
        speeds = DownloadSpeedMeter(),
        health = InMemoryBackgroundHealthStore(),
        systemStatus = BackgroundSystemStatus.Unlimited,
        serviceStarter = NoServiceStarter,
    )

    private val mutableUiState = MutableStateFlow(DownloadsUiState.Empty)
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    private val system = MutableStateFlow(BackgroundSystemState.Unlimited)

    init {
        viewModelScope.launch {
            // Applying the policy here also covers work resumed from this screen before anything
            // new was queued in this process.
            networkStatus.ensureApplied()
        }
        viewModelScope.launch {
            queue.restore()
            val measured = queue.tasks.map { tasks -> tasks to speeds.update(tasks) }
            combine(
                measured,
                networkStatus.state,
                storage(),
                background(),
            ) { (tasks, rates), network, storage, background ->
                DownloadsUiState.from(tasks, rates).copy(
                    network = network,
                    storage = storage,
                    background = background,
                )
            }.collect { state ->
                mutableUiState.value = state
            }
        }
    }

    fun onAction(action: DownloadAction, id: String) {
        viewModelScope.launch {
            when (action) {
                DownloadAction.PAUSE -> queue.pause(id)
                DownloadAction.RESUME, DownloadAction.RETRY -> {
                    queue.resume(id)
                    // P34: a resumed download needs the service too, or it stops in the
                    // background; Pause all had stopped it.
                    serviceStarter.start()
                }
                DownloadAction.CANCEL -> queue.cancel(id)
                DownloadAction.DELETE -> queue.deleteRecord(id)
            }
        }
    }

    fun pauseAll() {
        viewModelScope.launch { queue.pauseAll() }
    }

    /** Reads the battery and notification state again: YFT is back on the screen (P34). */
    fun refreshBackground() {
        val state = systemStatus.read()
        if (state.notificationsVisible) health.resetNotificationsCard()
        system.value = state
    }

    /** "Not now" on the battery card: hidden until the phone freezes YFT again. */
    fun hideBatteryCard() = health.hideBatteryCard()

    fun hideNotificationsCard() = health.hideNotificationsCard()

    private fun background(): Flow<BackgroundCardsUiState> =
        combine(system, health.health, ::backgroundCards)

    /**
     * The save location and its free space, measured again whenever the location changes or a
     * download finishes. Rows never wait for it: it starts as null and stays null on failure.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun storage(): Flow<DownloadStorageSummary?> = combine(
        storageSource.location,
        queue.tasks
            .map { tasks -> tasks.count { it.status == DownloadTaskStatus.COMPLETED } }
            .distinctUntilChanged(),
    ) { location, _ -> location }
        .mapLatest { location ->
            location?.let { DownloadStorageSummary(it, storageSource.freeBytes(it)) }
        }
        .onStart { emit(null) }
        .catch { emit(null) }
}

/** Tests and previews start no service. */
private object NoServiceStarter : DownloadServiceStarter {
    override fun start() = Unit
}
