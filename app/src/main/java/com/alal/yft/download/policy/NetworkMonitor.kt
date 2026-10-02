package com.alal.yft.download.policy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Source of the current default-network snapshot. */
interface NetworkStatusSource {
    val snapshot: StateFlow<NetworkSnapshot>
}

/**
 * Tracks the default network for the lifetime of the process.
 *
 * One callback is registered lazily, the first time anyone reads [snapshot], and it is never
 * unregistered: the process-wide download policy needs it for as long as the process lives, and a
 * single default-network callback costs nothing while the network is idle.
 */
class ConnectivityNetworkMonitor(
    context: Context,
) : NetworkStatusSource {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(currentSnapshot())
    private var registered = false

    override val snapshot: StateFlow<NetworkSnapshot>
        get() {
            ensureRegistered()
            return state.asStateFlow()
        }

    @Synchronized
    private fun ensureRegistered() {
        if (registered || connectivity == null) return
        registered = runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            true
        }.getOrDefault(false)
        state.value = currentSnapshot()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            state.value = capabilities.toSnapshot()
        }

        override fun onLost(network: Network) {
            state.value = currentSnapshot()
        }

        override fun onAvailable(network: Network) {
            state.value = currentSnapshot()
        }
    }

    private fun currentSnapshot(): NetworkSnapshot {
        val manager = connectivity ?: return NetworkSnapshot.OFFLINE
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }
            .getOrNull()
            ?: return NetworkSnapshot.OFFLINE
        return capabilities.toSnapshot()
    }

    private fun NetworkCapabilities.toSnapshot(): NetworkSnapshot {
        val internet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return NetworkSnapshot(
            connected = internet,
            validated = internet && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }
}
