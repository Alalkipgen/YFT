package com.alal.yft.feature.downloads

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.BackgroundHealthStore
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.download.BackgroundSystemStatus
import com.alal.yft.download.DownloadServiceStarter
import com.alal.yft.download.DownloadStorageSource
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.download.FileDeletion
import com.alal.yft.download.InMemoryBackgroundHealthStore
import com.alal.yft.download.SavedDownloadFile
import com.alal.yft.download.policy.DownloadNetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** "Delete file" (P42); tests of other controls leave it out. */
    private val fileDeleter: DownloadedFileDeleter = DownloadedFileDeleter.Unavailable,
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
        fileDeleter = DownloadedFileDeleter.Unavailable,
    )

    /** For tests of "Delete file" (P42), with or without its question ([DELETE_CONFIRM]). */
    internal constructor(
        queue: DownloadQueue,
        fileDeleter: DownloadedFileDeleter,
        asksBeforeDeletingFile: Boolean = DELETE_CONFIRM,
    ) : this(
        queue = queue,
        networkStatus = DownloadNetworkStatus.AlwaysAllowed,
        storageSource = DownloadStorageSource.None,
        speeds = DownloadSpeedMeter(),
        health = InMemoryBackgroundHealthStore(),
        systemStatus = BackgroundSystemStatus.Unlimited,
        serviceStarter = NoServiceStarter,
        fileDeleter = fileDeleter,
    ) {
        this.asksBeforeDeletingFile = asksBeforeDeletingFile
    }

    private val mutableUiState = MutableStateFlow(DownloadsUiState.Empty)
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    private val system = MutableStateFlow(BackgroundSystemState.Unlimited)

    private var asksBeforeDeletingFile = DELETE_CONFIRM
    private val mutableDeleteFileQuestion = MutableStateFlow<DeleteFileQuestion?>(null)

    /** The open "Delete this file?" question, or null (P42). */
    val deleteFileQuestion: StateFlow<DeleteFileQuestion?> =
        mutableDeleteFileQuestion.asStateFlow()
    private val consentRequests = Channel<IntentSender>(Channel.BUFFERED)

    /** Android's own "Allow YFT to delete?" requests, for the screen to launch (P42). */
    val deleteConsentRequests: Flow<IntentSender> = consentRequests.receiveAsFlow()
    private var consentFor: String? = null
    private val mutableMessages = Channel<String>(Channel.BUFFERED)

    /** One-off notes for the snackbar: "File deleted", "Could not delete the file". */
    val messages: Flow<String> = mutableMessages.receiveAsFlow()

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

    /** "Delete file" on a finished download (P42): asks first while [DELETE_CONFIRM] is on. */
    fun onDeleteFile(id: String) {
        val task = queue.tasks.value.firstOrNull {
            it.id == id && it.status == DownloadTaskStatus.COMPLETED
        } ?: return
        if (asksBeforeDeletingFile) {
            mutableDeleteFileQuestion.value = DeleteFileQuestion(id, task.displayName)
        } else {
            deleteFile(id, afterConsent = false)
        }
    }

    fun confirmDeleteFile() {
        val question = mutableDeleteFileQuestion.value ?: return
        mutableDeleteFileQuestion.value = null
        deleteFile(question.id, afterConsent = false)
    }

    fun cancelDeleteFile() {
        mutableDeleteFileQuestion.value = null
    }

    /** The answer to Android's request: once allowed, the file is deleted again. */
    fun onDeleteConsentResult(allowed: Boolean) {
        val id = consentFor ?: return
        consentFor = null
        if (allowed) deleteFile(id, afterConsent = true)
    }

    /** Deletes the file, then the row; a file that stays keeps its row. */
    private fun deleteFile(id: String, afterConsent: Boolean) {
        viewModelScope.launch {
            val task = queue.tasks.value.firstOrNull { it.id == id } ?: return@launch
            val file = SavedDownloadFile(
                kind = task.destinationKind,
                uri = task.destinationUri,
                displayName = task.displayName,
            )
            when (val result = fileDeleter.delete(file)) {
                FileDeletion.Deleted -> {
                    queue.deleteRecord(id)
                    mutableMessages.send(FILE_DELETED)
                }

                is FileDeletion.NeedsConsent -> if (afterConsent) {
                    mutableMessages.send(FILE_NOT_DELETED)
                } else {
                    consentFor = id
                    consentRequests.send(result.request)
                }

                FileDeletion.Failed -> mutableMessages.send(FILE_NOT_DELETED)
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

/** "Delete this file?" for the finished download [id] (P42). */
data class DeleteFileQuestion(val id: String, val fileName: String)

/** Ask before "Delete file" deletes (P42, owner default ON). */
internal const val DELETE_CONFIRM = true
internal const val FILE_DELETED = "File deleted"
internal const val FILE_NOT_DELETED = "Could not delete the file"

/** Tests and previews start no service. */
private object NoServiceStarter : DownloadServiceStarter {
    override fun start() = Unit
}
