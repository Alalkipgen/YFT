package com.alal.yft.download.policy

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.model.settings.DownloadPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Applies the user's network preferences before any work is queued. */
fun interface DownloadPolicyGate {
    suspend fun ensureApplied()

    companion object {
        val None = DownloadPolicyGate { }
    }
}

/**
 * Keeps the download queue in line with the network and the user's download preferences.
 *
 * The first [ensureApplied] call pushes the current policy synchronously, so work queued right
 * after it can never start on a network the user excluded; afterwards the policy follows every
 * network and preference change for the life of the process.
 */
class DownloadPolicyController(
    private val queue: DownloadQueue,
    private val network: NetworkStatusSource,
    private val preferences: DownloadPreferencesRepository,
    private val scope: CoroutineScope,
) : DownloadPolicyGate {
    private val startLock = Mutex()
    private var started = false
    private val mutableState = MutableStateFlow(TransferNetworkState.ALLOWED)

    /** The policy's current verdict, for the downloads screen. */
    val state: StateFlow<TransferNetworkState> = mutableState.asStateFlow()

    override suspend fun ensureApplied() {
        startLock.withLock {
            if (started) return
            val current = preferences.preferences.first()
            apply(network.snapshot.value, current)
            queue.setMaxConcurrentDownloads(current.maxConcurrentDownloads)
            started = true
        }
        scope.launch {
            combine(network.snapshot, preferences.preferences, ::Pair)
                .collect { (snapshot, prefs) -> apply(snapshot, prefs) }
        }
        scope.launch {
            preferences.preferences
                .map { prefs -> prefs.maxConcurrentDownloads }
                .distinctUntilChanged()
                .collect { limit -> queue.setMaxConcurrentDownloads(limit) }
        }
    }

    private suspend fun apply(snapshot: NetworkSnapshot, prefs: DownloadPreferences) {
        val verdict = DownloadNetworkPolicy.stateFor(snapshot, prefs)
        mutableState.value = verdict
        queue.setNetworkAvailable(verdict == TransferNetworkState.ALLOWED)
    }
}
