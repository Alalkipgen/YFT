package com.alal.yft.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.download.policy.DownloadNetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Exposes the persisted download queue as observable UI state and forwards user controls.
 *
 * The queue remains the single owner of transfer state, so this view model performs no transfer
 * work and keeps no copy of sensitive request context.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val queue: DownloadQueue,
    private val networkStatus: DownloadNetworkStatus,
) : ViewModel() {
    /** Without a network policy, for tests that only exercise the queue. */
    constructor(queue: DownloadQueue) : this(queue, DownloadNetworkStatus.AlwaysAllowed)

    private val mutableUiState = MutableStateFlow(DownloadsUiState.Empty)
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Applying the policy here also covers work resumed from this screen before anything
            // new was queued in this process.
            networkStatus.ensureApplied()
        }
        viewModelScope.launch {
            queue.restore()
            combine(queue.tasks, networkStatus.state) { tasks, network ->
                DownloadsUiState.from(tasks).copy(network = network)
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
}
