package com.alal.yft.download.policy

import com.alal.yft.core.model.settings.DownloadPreferences

/** What the transfer policy needs to know about the device's default network. */
data class NetworkSnapshot(
    val connected: Boolean,
    /** The system confirmed the network actually reaches the internet. */
    val validated: Boolean,
    /** Wi-Fi or Ethernet without a data cap, as opposed to mobile data or a metered hotspot. */
    val unmetered: Boolean,
) {
    val metered: Boolean get() = connected && !unmetered

    companion object {
        val OFFLINE = NetworkSnapshot(connected = false, validated = false, unmetered = false)
    }
}

/** Why transfers may or may not run right now; shown to the user as-is. */
enum class TransferNetworkState {
    ALLOWED,
    OFFLINE,
    WAITING_FOR_UNMETERED,
}

object DownloadNetworkPolicy {
    fun stateFor(network: NetworkSnapshot, preferences: DownloadPreferences): TransferNetworkState =
        when {
            !network.connected || !network.validated -> TransferNetworkState.OFFLINE
            preferences.unmeteredOnly && !network.unmetered ->
                TransferNetworkState.WAITING_FOR_UNMETERED
            else -> TransferNetworkState.ALLOWED
        }

    /** Whether starting a download now deserves a mobile-data confirmation. */
    fun needsMeteredConfirmation(
        network: NetworkSnapshot,
        preferences: DownloadPreferences,
    ): Boolean = preferences.confirmOnMeteredNetwork &&
        !preferences.unmeteredOnly &&
        network.connected &&
        network.metered
}
