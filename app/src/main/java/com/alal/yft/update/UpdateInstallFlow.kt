package com.alal.yft.update

import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the in-app update is (§4.4, §4.5's table); [test]: the reinstall test's, no release's. */
sealed interface InstallState {
    val test: Boolean

    /** Nothing running: the button. */
    data object Idle : InstallState {
        override val test: Boolean
            get() = false
    }

    /** Step 1: installs from YFT not allowed — nothing was downloaded. */
    data class NotAllowed(override val test: Boolean) : InstallState

    /** Steps 3–4: the checksum, then the APK, [percent] of it written. */
    data class Downloading(val percent: Int) : InstallState {
        override val test: Boolean
            get() = false
    }

    /**
     * Every check passed and Android's installer has the session; [confirm] is its confirmation,
     * waiting for YFT to be in front, and null once started.
     */
    data class Installing(
        override val test: Boolean,
        val confirm: InstallConfirmation? = null,
    ) : InstallState

    /** A step failed: nothing was installed. */
    data class Failed(override val test: Boolean, val failure: InstallFailure) : InstallState

    /** Android's installer was cancelled, or Cancel while it waited: nothing was installed. */
    data class Cancelled(override val test: Boolean) : InstallState
}

/** Android's confirmation for session [sessionId]; YFT starts [intent] while it is in front. */
class InstallConfirmation(val sessionId: Int, val intent: Intent)

/** The step that failed (§4.4). */
sealed interface InstallFailure {
    data object NoSpace : InstallFailure

    data object NoChecksum : InstallFailure

    /** The connection broke or timed out. */
    data object DownloadStopped : InstallFailure

    /** Another answer from GitHub, or an address off its hosts. */
    data object DownloadFailed : InstallFailure

    /** Check 1: another hash or size. */
    data object ChecksumMismatch : InstallFailure

    /** Checks 2 and 3. */
    data class Check(val verdict: ApkVerdict) : InstallFailure

    /** Android's installer answered [status] (`PackageInstaller.STATUS_*`). */
    data class Android(val status: Int) : InstallFailure

    /** Android's confirmation did not open. */
    data object NoInstaller : InstallFailure
}

/**
 * **Download APK to update** in YFT (§4.4), in this process-wide scope so it outlives the screen:
 * 1 allowed to install? 2 free space; 3 the release's `SHA256SUMS` line; 4 the APK, check 1 the
 * checksum; check 2 the signature; check 3 the package and the version; then Android's installer,
 * which asks the user. The checks run in that order and every failure stops before any session.
 * In test mode, **Reinstall this build (test)** sends this build's own APK through checks 2 and 3
 * and the same session — no release, so no checksum is claimed. Never in the release.
 */
class UpdateInstallFlow(
    private val environment: UpdateEnvironment,
    private val source: ApkSource,
    private val reader: ApkReader,
    private val system: InstallSystem,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var sessionId: Int? = null

    // Step 8: what an earlier run left — the cached APK and YFT's own sessions.
    private val cleanup: Job? = if (environment.mode == UpdateMode.OFF) {
        null
    } else {
        scope.launch {
            withContext(ioDispatcher) {
                quietly { source.discard() }
                quietly { system.abandonAll() }
            }
        }
    }

    private val busy: Boolean
        get() = job?.isActive == true ||
            state.value is InstallState.Downloading ||
            state.value is InstallState.Installing

    /** **Download APK to update**: the steps for [update]; ignored while one runs. */
    fun download(update: FoundUpdate) = start { runRelease(update) }

    /** Test mode only: **Reinstall this build (test)**. */
    fun reinstallThisBuild() {
        if (environment.testMode) start { runOwnBuild() }
    }

    /** **Cancel**: stops the download, or gives up the session that waits for Android. */
    fun cancel() {
        val current = state.value
        job?.cancel()
        job = null
        val id = sessionId
        sessionId = null
        if (id != null) scope.launch { withContext(ioDispatcher) { system.abandon(id) } }
        mutableState.value = if (current is InstallState.Installing) {
            InstallState.Cancelled(current.test)
        } else {
            InstallState.Idle
        }
    }

    /** YFT started Android's confirmation for [sessionId]: not again. */
    fun confirmStarted(sessionId: Int) = mutableState.update { current ->
        if (current is InstallState.Installing && current.confirm?.sessionId == sessionId) {
            current.copy(confirm = null)
        } else {
            current
        }
    }

    /** Android's confirmation could not open: the session is given up. */
    fun confirmFailed(sessionId: Int) {
        if (sessionId != this.sessionId) return
        this.sessionId = null
        scope.launch { withContext(ioDispatcher) { system.abandon(sessionId) } }
        mutableState.value = InstallState.Failed(state.value.test, InstallFailure.NoInstaller)
    }

    /** Android's answer about [sessionId] ([InstallStatusReceiver]). */
    fun onStatus(sessionId: Int, status: Int, confirm: Intent?) {
        scope.launch {
            if (sessionId != this@UpdateInstallFlow.sessionId) {
                // An earlier run's session, or one given up: nothing waits for it.
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    withContext(ioDispatcher) { system.abandon(sessionId) }
                }
                return@launch
            }
            val test = state.value.test
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && confirm != null) {
                mutableState.value = InstallState.Installing(
                    test = test,
                    confirm = InstallConfirmation(sessionId, confirm),
                )
                return@launch
            }
            this@UpdateInstallFlow.sessionId = null
            mutableState.value = when (status) {
                // Android replaces YFT; this process ends with it.
                PackageInstaller.STATUS_SUCCESS -> InstallState.Idle
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Cancelled(test)
                else -> InstallState.Failed(test, InstallFailure.Android(status))
            }
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                withContext(ioDispatcher) { system.abandon(sessionId) }
            }
        }
    }

    private fun start(block: suspend () -> Unit) {
        if (environment.mode == UpdateMode.OFF || busy) return
        job = scope.launch {
            cleanup?.join()
            block()
        }
    }

    private suspend fun runRelease(update: FoundUpdate) {
        if (!system.canInstall()) return show(InstallState.NotAllowed(test = false))
        val free = withContext(ioDispatcher) { system.freeBytes() }
        if (free < SPACE_FACTOR * update.apkSize + SPARE_BYTES) {
            return fail(test = false, InstallFailure.NoSpace)
        }
        show(InstallState.Downloading(percent = 0))
        val sha256 = when (val sums = source.checksum(update)) {
            is SumsResult.Found -> sums.sha256
            SumsResult.Missing -> return fail(test = false, InstallFailure.NoChecksum)
            SumsResult.Offline -> return fail(test = false, InstallFailure.DownloadStopped)
            SumsResult.Failed -> return fail(test = false, InstallFailure.DownloadFailed)
        }
        currentCoroutineContext().ensureActive()
        val download = source.download(update, sha256) { percent ->
            mutableState.update { if (it is InstallState.Downloading) it.copy(percent) else it }
        }
        val file = when (download) {
            is DownloadResult.Done -> download.file
            DownloadResult.Damaged -> return fail(test = false, InstallFailure.ChecksumMismatch)
            DownloadResult.Offline -> return fail(test = false, InstallFailure.DownloadStopped)
            DownloadResult.Failed -> return fail(test = false, InstallFailure.DownloadFailed)
        }
        currentCoroutineContext().ensureActive()
        val verdict = withContext(ioDispatcher) {
            ApkCheck.release(reader.read(file), system.installed(), update, environment.testMode)
        }
        if (verdict != ApkVerdict.Passed) {
            withContext(ioDispatcher) { source.discard() }
            return fail(test = false, InstallFailure.Check(verdict))
        }
        install(file, test = false, cached = true)
    }

    private suspend fun runOwnBuild() {
        if (!system.canInstall()) return show(InstallState.NotAllowed(test = true))
        val file = system.ownApk()
        val label = "v${environment.installedName}"
        val verdict = withContext(ioDispatcher) {
            ApkCheck.ownBuild(reader.read(file), system.installed(), label)
        }
        if (verdict != ApkVerdict.Passed) return fail(test = true, InstallFailure.Check(verdict))
        install(file, test = true, cached = false)
    }

    /** Step 7: the session; its answer comes to [onStatus]. A [cached] file goes once written. */
    private suspend fun install(file: File, test: Boolean, cached: Boolean) {
        show(InstallState.Installing(test))
        try {
            val id = withContext(ioDispatcher) { system.createSession(file.length()) }
            sessionId = id
            withContext(ioDispatcher) { system.commit(id, file) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val id = sessionId
            sessionId = null
            if (id != null) withContext(ioDispatcher) { quietly { system.abandon(id) } }
            fail(test, InstallFailure.Android(PackageInstaller.STATUS_FAILURE))
        } finally {
            // The session holds its own copy.
            if (cached) withContext(NonCancellable + ioDispatcher) { source.discard() }
        }
    }

    private fun show(next: InstallState) {
        mutableState.value = next
    }

    private fun fail(test: Boolean, failure: InstallFailure) {
        mutableState.value = InstallState.Failed(test, failure)
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (failure: Exception) {
            // Best effort: a left-over is removed at the next start.
        }
    }

    companion object {
        /** Step 2: twice the APK (the file and the session's copy) and a margin. */
        const val SPACE_FACTOR = 2L
        const val SPARE_BYTES = 20L * 1024 * 1024
    }
}
