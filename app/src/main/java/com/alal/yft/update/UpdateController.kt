package com.alal.yft.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The installed build, as the updater sees it. */
data class UpdateEnvironment(
    val mode: UpdateMode,
    /** `BuildConfig.VERSION_CODE`. */
    val installedCode: Long,
    /** `BuildConfig.VERSION_NAME`, such as "1.0.0" or "1.0.0-beta.3-debug". */
    val installedName: String,
) {
    val testMode: Boolean
        get() = mode == UpdateMode.TEST

    /** The code releases must beat: in test mode 0, so every release qualifies (§4.1). */
    val comparedCode: Long
        get() = if (testMode) 0 else installedCode

    /** Check automatically starts on in the release and off in test mode. */
    val defaultAutoCheck: Boolean
        get() = mode == UpdateMode.RELEASE
}

/** The line Check now leaves when it could not tell; a good answer leaves none. */
sealed interface CheckLine {
    data object NoConnection : CheckLine

    data class Busy(val until: Long) : CheckLine

    data object CouldNotCheck : CheckLine
}

/** What the Settings row, the Updates screen and the auto-open read. */
data class UpdateState(
    val mode: UpdateMode,
    val installedName: String,
    val autoCheck: Boolean,
    val found: FoundUpdate? = null,
    val lastGoodAt: Long? = null,
    val checking: Boolean = false,
    val line: CheckLine? = null,
    /** A found version the Updates screen should open for by itself (§4.5); null when none. */
    val autoOpen: FoundUpdate? = null,
) {
    val testMode: Boolean
        get() = mode == UpdateMode.TEST
}

/**
 * The updater's state and its rules (§4.3, §4.5). [onAppOpened] (MainActivity.onStart) asks
 * GitHub only when the build checks, Check automatically is on, no check is running, no rate
 * limit is pending and GitHub's last answer is 24 h old or more (or dated in the future: the
 * clock went back). Every answer from GitHub starts the next 24 h; no connection is not counted.
 * [checkNow] asks at once, with the switch on or off; only a pending rate limit stops it. No
 * background job, no notification. A found update is kept until the installed versionCode
 * reaches it; the screen opens by itself once per found version while the switch is on.
 */
class UpdateController(
    private val environment: UpdateEnvironment,
    private val checker: ReleaseCheck,
    private val store: UpdateStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var record: UpdateRecord? = null
    private var checking = false
    private var manualJoined = false
    private var line: CheckLine? = null
    private var autoOpenDeferred = false

    private val mutableState = MutableStateFlow(
        UpdateState(
            mode = environment.mode,
            installedName = environment.installedName,
            autoCheck = environment.defaultAutoCheck,
        ),
    )
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    init {
        if (environment.mode != UpdateMode.OFF) {
            scope.launch {
                mutex.withLock {
                    loaded()
                    publish()
                }
            }
        }
    }

    /** The app came to the front: the daily check when it is due; the auto-open may run again. */
    fun onAppOpened() {
        if (environment.mode == UpdateMode.OFF) return
        scope.launch {
            val start = mutex.withLock {
                autoOpenDeferred = false
                val current = loaded()
                val now = clock()
                val due = autoCheckOf(current) && !checking &&
                    !rateLimited(current, now) && answerDue(current, now)
                if (due) checking = true
                publish()
                due
            }
            if (start) check(manual = false)
        }
    }

    /** The screen's Check now: asks at once unless a rate limit is pending. */
    fun checkNow() {
        if (environment.mode == UpdateMode.OFF) return
        scope.launch {
            val start = mutex.withLock {
                val current = loaded()
                val until = current.rateLimitUntil
                var start = false
                when {
                    // The running check answers this tap too.
                    checking -> manualJoined = true
                    until != null && rateLimited(current, clock()) -> line = CheckLine.Busy(until)
                    else -> {
                        checking = true
                        line = null
                        start = true
                    }
                }
                publish()
                start
            }
            if (start) check(manual = true)
        }
    }

    fun setAutoCheck(on: Boolean) = edit { it.copy(autoCheck = on) }

    /** The screen opened for [version] (by itself or after Check now): not again for it. */
    fun markOpened(version: String) = edit { it.copy(openedVersion = version) }

    /** Another screen is in front: the auto-open waits for the next app open. */
    fun deferAutoOpen() {
        scope.launch {
            mutex.withLock {
                autoOpenDeferred = true
                publish()
            }
        }
    }

    private fun edit(change: (UpdateRecord) -> UpdateRecord) {
        if (environment.mode == UpdateMode.OFF) return
        scope.launch {
            mutex.withLock {
                save(change(loaded()))
                publish()
            }
        }
    }

    private suspend fun check(manual: Boolean) {
        val result = try {
            checker.check(environment.comparedCode, environment.testMode)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    checking = false
                    manualJoined = false
                    publish()
                }
            }
            throw cancelled
        } catch (failure: Exception) {
            CheckResult.Failed
        }
        mutex.withLock {
            val now = clock()
            val current = loaded()
            val asked = manual || manualJoined
            save(
                when (result) {
                    CheckResult.UpToDate -> current.copy(
                        lastAnswerAt = now,
                        lastGoodAt = now,
                        found = null,
                        rateLimitUntil = null,
                    )
                    is CheckResult.Available -> current.copy(
                        lastAnswerAt = now,
                        lastGoodAt = now,
                        found = result.update,
                        rateLimitUntil = null,
                        // Found on Check now: the user is on the screen already.
                        openedVersion = if (asked) {
                            result.update.version.name
                        } else {
                            current.openedVersion
                        },
                    )
                    CheckResult.Offline -> current
                    is CheckResult.RateLimited -> current.copy(
                        lastAnswerAt = now,
                        rateLimitUntil = result.until,
                    )
                    CheckResult.Failed -> current.copy(lastAnswerAt = now)
                },
            )
            line = when (result) {
                CheckResult.UpToDate, is CheckResult.Available -> null
                CheckResult.Offline -> if (asked) CheckLine.NoConnection else line
                is CheckResult.RateLimited -> if (asked) CheckLine.Busy(result.until) else line
                CheckResult.Failed -> if (asked) CheckLine.CouldNotCheck else line
            }
            checking = false
            manualJoined = false
            publish()
        }
    }

    /** The record, read once; a found update the installed build has reached is dropped. */
    private suspend fun loaded(): UpdateRecord {
        val current = record ?: withContext(ioDispatcher) { store.read() }
        val found = current.found
        val kept = if (found != null && !offerable(found)) current.copy(found = null) else current
        if (kept != current || record == null) {
            record = kept
            if (kept != current) store.write(kept)
        }
        return kept
    }

    private fun offerable(found: FoundUpdate): Boolean {
        if (environment.testMode) return true
        val code = found.code ?: return false
        return !found.prerelease && code > environment.installedCode
    }

    private fun save(next: UpdateRecord) {
        if (next == record) return
        record = next
        store.write(next)
    }

    private fun autoCheckOf(current: UpdateRecord): Boolean =
        current.autoCheck ?: environment.defaultAutoCheck

    private fun rateLimited(current: UpdateRecord, now: Long): Boolean {
        val until = current.rateLimitUntil ?: return false
        // A limit further ahead than GitHub ever sets means the clock went back: ignore it.
        return now < until && until - now <= UpdateChecker.MAX_WAIT_MS
    }

    private fun answerDue(current: UpdateRecord, now: Long): Boolean {
        val last = current.lastAnswerAt ?: return true
        return now < last || now - last >= DAY_MS
    }

    private fun publish() {
        val current = record ?: UpdateRecord()
        val autoCheck = autoCheckOf(current)
        mutableState.value = UpdateState(
            mode = environment.mode,
            installedName = environment.installedName,
            autoCheck = autoCheck,
            found = current.found,
            lastGoodAt = current.lastGoodAt,
            checking = checking,
            line = line,
            autoOpen = current.found?.takeIf {
                autoCheck && !autoOpenDeferred && it.version.name != current.openedVersion
            },
        )
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
