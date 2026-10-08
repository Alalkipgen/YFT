package com.alal.yft.feature.downloads

import com.alal.yft.download.BackgroundHealth
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.download.freezeMessage

/** BATTERY_CARD (G4) default: the battery card in Downloads; OFF keeps Settings and freezes. */
internal const val BATTERY_CARD = true

/** What the Downloads screen says about running in the background (P34). */
data class BackgroundCardsUiState(
    /** "Turn on notifications to see download progress outside YFT". */
    val notificationsCard: Boolean = false,
    val batteryCard: BatteryCardUiState? = null,
) {
    companion object {
        val None = BackgroundCardsUiState()
    }
}

/** The battery card: why downloads may stop and what to allow. */
data class BatteryCardUiState(
    /** "Your phone paused YFT in the background for 1 min 40 s.", after a freeze. */
    val freezeMessage: String?,
    /** Android still limits YFT's battery: the card offers Allow. */
    val canAllow: Boolean,
    /** HyperOS's own steps on Xiaomi, Redmi and POCO phones. */
    val xiaomiSteps: Boolean,
)

internal const val NOTIFICATIONS_CARD_TEXT =
    "Turn on notifications to see download progress outside YFT"

internal const val BATTERY_CARD_TEXT =
    "Downloads and merges may stop when YFT is in the background. " +
        "Allow YFT to run without battery limits."

internal const val XIAOMI_STEPS =
    "Settings \u203a Apps \u203a Manage apps \u203a YFT \u203a Battery saver \u203a " +
        "No restrictions; turn on Autostart; in Recents, hold YFT's card and tap the lock."

/**
 * The cards for [system] and [health]: notifications when they are off and not dismissed; the
 * battery card when Android limits YFT and a download was started, or after a freeze (shown
 * again then even if "Not now" hid it). With [batteryCard] off only a freeze shows it.
 */
internal fun backgroundCards(
    system: BackgroundSystemState,
    health: BackgroundHealth,
    batteryCard: Boolean = BATTERY_CARD,
): BackgroundCardsUiState {
    val froze = health.lastFreezeLostMs != null
    val limited = !system.unrestricted && health.downloadStarted && batteryCard
    val showBattery = !health.batteryCardHidden && (limited || froze)
    return BackgroundCardsUiState(
        notificationsCard = !system.notificationsVisible && !health.notificationsCardHidden,
        batteryCard = BatteryCardUiState(
            freezeMessage = health.lastFreezeLostMs?.let(::freezeMessage),
            canAllow = !system.unrestricted,
            xiaomiSteps = system.xiaomi,
        ).takeIf { showBattery },
    )
}

/** Settings › Downloads › Background downloads: "Allowed" or "Limited" (P34). */
internal fun backgroundDownloadsValue(system: BackgroundSystemState): String =
    if (system.unrestricted) "Allowed" else "Limited"

/** What the background cards' buttons do; the Downloads route opens Android's settings. */
data class BackgroundCardActions(
    val onTurnOnNotifications: () -> Unit = {},
    val onDismissNotifications: () -> Unit = {},
    val onAllow: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onNotNow: () -> Unit = {},
) {
    companion object {
        val None = BackgroundCardActions()
    }
}
