package com.alal.yft.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.DownloadStorageSource
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
 * work and keeps no copy of sensitive request context. Speeds are measured here, in memory,
 * from the progress the queue publishes.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val queue: DownloadQueue,
    private val networkStatus: DownloadNetworkStatus,
    private val storageSource: DownloadStorageSource,
) : ViewModel() {
    /** Without a network policy or storage, for tests that only exercise the queue. */
    constructor(queue: DownloadQueue) : this(queue, DownloadNetworkStatus.AlwaysAllowed)

    /** Without storage, for tests of the network policy. */
    constructor(queue: DownloadQueue, networkStatus: DownloadNetworkStatus) :
        this(queue, networkStatus, DownloadStorageSource.None)

    private val mutableUiState = MutableStateFlow(DownloadsUiState.Empty)
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    private val rates = TransferRateTracker()

    init {
        viewModelScope.launch {
            // Applying the policy here also covers work resumed from this screen before anything
            // new was queued in this process.
            networkStatus.ensureApplied()
        }
        viewModelScope.launch {
            queue.restore()
            val measured = queue.tasks.map { tasks -> tasks to rates.update(tasks) }
            combine(measured, networkStatus.state, storage()) { (tasks, speeds), network, storage ->
                DownloadsUiState.from(tasks, speeds).copy(network = network, storage = storage)
            }.collect { state ->
                mutableUiState.value = state
            }
        }
    }

    fun onAction(action: DownloadAction, id: String) {
        viewModelScope.launch {
            when (action) {
                DownloadAction.PAUSE -> queue.pause(id)
                DownloadAction.RESUME, DownloadAction.RETRY -> queue.resume(id)
                DownloadAction.CANCEL -> queue.cancel(id)
                DownloadAction.DELETE -> queue.deleteRecord(id)
            }
        }
    }

    fun pauseAll() {
        viewModelScope.launch { queue.pauseAll() }
    }

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
