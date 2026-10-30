package com.alal.yft.feature.downloads

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.BackgroundHealthStore
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.download.BackgroundSystemStatus
import com.alal.yft.download.DownloadServiceStarter
import com.alal.yft.download.DownloadSources
import com.alal.yft.download.DownloadStorageSource
import com.alal.yft.download.DownloadedFileDeleter
import com.alal.yft.download.FileDeletion
import com.alal.yft.download.InMemoryBackgroundHealthStore
import com.alal.yft.download.SavedDownloadFile
import com.alal.yft.download.TrialLinkBreak
import com.alal.yft.download.policy.DownloadNetworkStatus
import com.alal.yft.feature.settings.YouTubeSessionReset
import com.alal.yft.feature.settings.YouTubeSessionResetResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    /** "Reset YouTube session" (item 7 Track B3); tests of other controls leave it out. */
    private val youTubeSession: YouTubeSessionReset = YouTubeSessionReset.Unavailable,
    /** Track E: which rows a Retry renews, and their fresh links' lines. */
    private val sources: DownloadSources = DownloadSources(),
    /** Track E (E-D9): the trial's "Break this download's link"; off in every other build. */
    private val trialBreak: TrialLinkBreak = TrialLinkBreak(available = false),
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

    /** For tests of "Reset YouTube session" (item 7 Track B3). */
    internal constructor(queue: DownloadQueue, youTubeSession: YouTubeSessionReset) : this(
        queue = queue,
        networkStatus = DownloadNetworkStatus.AlwaysAllowed,
        storageSource = DownloadStorageSource.None,
        speeds = DownloadSpeedMeter(),
        health = InMemoryBackgroundHealthStore(),
        systemStatus = BackgroundSystemStatus.Unlimited,
        serviceStarter = NoServiceStarter,
        youTubeSession = youTubeSession,
    )

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

    /** Item 5 (v1.0.2): the files "Delete file" is still deleting, or null. */
    private var deleteRun: DeleteRun? = null
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
                sources.renewals,
            ) { (tasks, rates), network, storage, background, renewals ->
                DownloadsUiState.from(tasks, rates, renewals).copy(
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
    fun onDeleteFile(id: String) = onDeleteFiles(listOf(id))

    /**
     * Item 5 (v1.0.2): "Delete file" for the finished downloads picked in select mode; one
     * question names the count ("Delete 3 files?"), or the file when there is one.
     */
    fun onDeleteFiles(ids: List<String>) {
        val finished = finishedTasks(ids)
        if (finished.isEmpty()) return
        if (asksBeforeDeletingFile) {
            mutableDeleteFileQuestion.value =
                DeleteFileQuestion(finished.map { it.id }, finished.first().displayName)
        } else {
            deleteFiles(finished.map { it.id })
        }
    }

    /** Item 5 (v1.0.2): "Remove from list" for the picked downloads; their files stay. */
    fun removeFromList(ids: List<String>) {
        val finished = finishedTasks(ids)
        viewModelScope.launch { finished.forEach { queue.deleteRecord(it.id) } }
    }

    fun confirmDeleteFile() {
        val question = mutableDeleteFileQuestion.value ?: return
        mutableDeleteFileQuestion.value = null
        deleteFiles(question.ids)
    }

    fun cancelDeleteFile() {
        mutableDeleteFileQuestion.value = null
    }

    /**
     * The answer to Android's request: once allowed, the file is deleted again; either way the
     * other picked files follow.
     */
    fun onDeleteConsentResult(allowed: Boolean) {
        val id = consentFor ?: return
        consentFor = null
        val run = deleteRun ?: return
        viewModelScope.launch {
            if (allowed) {
                when (deleteOne(id)) {
                    FileDeletion.Deleted -> run.deleted++
                    null -> Unit
                    else -> run.failed++
                }
            }
            continueDeleting(run)
        }
    }

    private fun finishedTasks(ids: List<String>): List<StoredDownloadTask> {
        val tasks = queue.tasks.value.associateBy { it.id }
        return ids.distinct().mapNotNull { id ->
            tasks[id]?.takeIf { it.status == DownloadTaskStatus.COMPLETED }
        }
    }

    /** Deletes the files one after another (item 5); a file that stays keeps its row. */
    private fun deleteFiles(ids: List<String>) {
        val running = deleteRun
        if (running != null) {
            val added = ids.filterNot { it in running.left }
            added.forEach(running.left::addLast)
            running.total += added.size
            return
        }
        val run = DeleteRun(ids)
        deleteRun = run
        viewModelScope.launch { continueDeleting(run) }
    }

    private suspend fun continueDeleting(run: DeleteRun) {
        while (true) {
            val id = run.left.removeFirstOrNull() ?: break
            when (val result = deleteOne(id)) {
                FileDeletion.Deleted -> run.deleted++
                FileDeletion.Failed -> run.failed++
                is FileDeletion.NeedsConsent -> {
                    // Android asks first; the rest waits for the answer.
                    consentFor = id
                    consentRequests.send(result.request)
                    return
                }

                null -> Unit
            }
        }
        deleteRun = null
        filesDeletedMessage(run.total, run.deleted, run.failed)?.let { mutableMessages.send(it) }
    }

    /** Deletes the file, then the row; null when the download is no longer listed. */
    private suspend fun deleteOne(id: String): FileDeletion? {
        val task = queue.tasks.value.firstOrNull { it.id == id } ?: return null
        val file = SavedDownloadFile(
            kind = task.destinationKind,
            uri = task.destinationUri,
            displayName = task.displayName,
        )
        val result = fileDeleter.delete(file)
        if (result == FileDeletion.Deleted) queue.deleteRecord(id)
        return result
    }

    /**
     * Item 7 Track B3: "Reset YouTube session", asked on a download YouTube refused while its
     * link had time left; the snackbar says what went (the screen asked first).
     */
    fun resetYouTubeSession() {
        viewModelScope.launch {
            val message = try {
                youTubeResetMessage(youTubeSession.reset())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                YOUTUBE_RESET_FAILED
            }
            mutableMessages.send(message)
        }
    }

    fun pauseAll() {
        viewModelScope.launch { queue.pauseAll() }
    }

    /**
     * Track E (E-D9), the trial only: the download's current link answers 403 from now on, so
     * its next request finds it dead and the queue gets a fresh one.
     */
    fun breakLink(id: String) = trialBreak.breakLink(id)

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
        .mapLatest { location -> location?.let { summary(it) } }
        .onStart { emit(null) }
        .catch { emit(null) }

    /**
     * v1.0.2 item 28: a chosen folder shows its name (its free space cannot be measured); one
     * that can no longer be used shows the default place, where new downloads go then.
     */
    private suspend fun summary(location: DownloadLocation): DownloadStorageSummary {
        if (location != DownloadLocation.CHOSEN_FOLDER) {
            return DownloadStorageSummary(location, storageSource.freeBytes(location))
        }
        val folderName = storageSource.folderName()
        if (folderName != null) return DownloadStorageSummary(location, null, folderName)
        val fallback = storageSource.defaultLocation
        return DownloadStorageSummary(fallback, storageSource.freeBytes(fallback))
    }
}

/**
 * "Delete this file?" for the finished download [id] (P42); item 5 (v1.0.2): or "Delete 3
 * files?" for the downloads picked in select mode ([ids], [fileName] being the first one's).
 */
data class DeleteFileQuestion(val ids: List<String>, val fileName: String) {
    constructor(id: String, fileName: String) : this(listOf(id), fileName)

    val id: String
        get() = ids.first()

    val count: Int
        get() = ids.size
}

/** Item 5 (v1.0.2): the picked files left to delete and how the deleted ones went. */
private class DeleteRun(ids: List<String>) {
    val left = ArrayDeque(ids)
    var total = ids.size
    var deleted = 0
    var failed = 0
}

/** Ask before "Delete file" deletes (P42, owner default ON). */
internal const val DELETE_CONFIRM = true
internal const val FILE_DELETED = "File deleted"
internal const val FILE_NOT_DELETED = "Could not delete the file"

/**
 * The snackbar after "Delete file" (item 5): one file keeps P42's words; several say how many
 * went and how many stayed. Nothing when every file stayed because Android's request was
 * declined.
 */
internal fun filesDeletedMessage(total: Int, deleted: Int, failed: Int): String? = when {
    deleted == 0 && failed == 0 -> null
    total == 1 -> if (deleted == 1) FILE_DELETED else FILE_NOT_DELETED
    failed == 0 -> "${files(deleted)} deleted"
    deleted == 0 -> "Could not delete ${files(failed)}"
    else -> "${files(deleted)} deleted; ${files(failed)} could not be deleted"
}

private fun files(count: Int): String = if (count == 1) "1 file" else "$count files"

/** Item 7 Track B3: what "Reset YouTube session" did, for the snackbar. */
internal fun youTubeResetMessage(result: YouTubeSessionResetResult): String =
    if (result.cookiesLeft == 0) {
        "YouTube session reset (${cookies(result.cookiesRemoved)} removed). Reopen the page " +
            "in the browser, then download again."
    } else {
        "YouTube session partly reset: ${cookies(result.cookiesLeft)} stayed, which Clear " +
            "browsing data in Settings removes (it signs you out of every site in YFT)."
    }

private fun cookies(count: Int): String = when (count) {
    0 -> "no cookies"
    1 -> "1 cookie"
    else -> "$count cookies"
}

internal const val YOUTUBE_RESET_FAILED = "Could not reset the YouTube session"

/** Tests and previews start no service. */
private object NoServiceStarter : DownloadServiceStarter {
    override fun start() = Unit
}
