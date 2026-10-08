package com.alal.yft.download

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What YFT knows about running in the background (P34): whether a download was ever started,
 * the last time the phone froze YFT, and the cards the user put away. Nothing here names a
 * video, a site or an address.
 */
data class BackgroundHealth(
    val downloadStarted: Boolean = false,
    /** How many freezes the download service has seen. */
    val freezeCount: Int = 0,
    /** Length of the last freeze, or null before the first one. */
    val lastFreezeLostMs: Long? = null,
    /** "Not now" on the battery card: hidden until the next freeze. */
    val batteryCardHidden: Boolean = false,
    /** The notifications card was dismissed. */
    val notificationsCardHidden: Boolean = false,
)

/** Keeps [BackgroundHealth] for the download service, the Downloads screen and Settings. */
interface BackgroundHealthStore {
    val health: StateFlow<BackgroundHealth>

    fun markDownloadStarted()

    /** A freeze of [lostMs]: counted, and the battery card comes back even if it was hidden. */
    fun recordFreeze(lostMs: Long)

    fun hideBatteryCard()

    fun hideNotificationsCard()

    /** Notifications work again: the card may come back if they are turned off later. */
    fun resetNotificationsCard()
}

/** In memory only, for previews and tests. */
open class InMemoryBackgroundHealthStore(
    initial: BackgroundHealth = BackgroundHealth(),
) : BackgroundHealthStore {
    private val state = MutableStateFlow(initial)

    override val health: StateFlow<BackgroundHealth> = state.asStateFlow()

    override fun markDownloadStarted() = change { it.copy(downloadStarted = true) }

    override fun recordFreeze(lostMs: Long) = change {
        it.copy(
            freezeCount = it.freezeCount + 1,
            lastFreezeLostMs = lostMs,
            batteryCardHidden = false,
        )
    }

    override fun hideBatteryCard() = change { it.copy(batteryCardHidden = true) }

    override fun hideNotificationsCard() = change { it.copy(notificationsCardHidden = true) }

    override fun resetNotificationsCard() = change { it.copy(notificationsCardHidden = false) }

    /** Applies [transform]; returns without saving when nothing changed. */
    protected open fun change(transform: (BackgroundHealth) -> BackgroundHealth) {
        state.update(transform)
    }
}

/** [BackgroundHealth] kept in the app's own preferences, so a freeze outlives the process. */
class SharedPreferencesBackgroundHealthStore private constructor(
    private val preferences: SharedPreferences,
) : InMemoryBackgroundHealthStore(read(preferences)) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    override fun change(transform: (BackgroundHealth) -> BackgroundHealth) {
        val before = health.value
        super.change(transform)
        val after = health.value
        if (after == before) return
        preferences.edit()
            .putBoolean(KEY_STARTED, after.downloadStarted)
            .putInt(KEY_FREEZES, after.freezeCount)
            .putLong(KEY_LAST_FREEZE, after.lastFreezeLostMs ?: NONE)
            .putBoolean(KEY_BATTERY_HIDDEN, after.batteryCardHidden)
            .putBoolean(KEY_NOTIFICATIONS_HIDDEN, after.notificationsCardHidden)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "yft_background"
        const val KEY_STARTED = "download_started"
        const val KEY_FREEZES = "freeze_count"
        const val KEY_LAST_FREEZE = "last_freeze_lost_ms"
        const val KEY_BATTERY_HIDDEN = "battery_card_hidden"
        const val KEY_NOTIFICATIONS_HIDDEN = "notifications_card_hidden"
        const val NONE = -1L

        fun read(preferences: SharedPreferences) = BackgroundHealth(
            downloadStarted = preferences.getBoolean(KEY_STARTED, false),
            freezeCount = preferences.getInt(KEY_FREEZES, 0),
            lastFreezeLostMs = preferences.getLong(KEY_LAST_FREEZE, NONE).takeIf { it >= 0L },
            batteryCardHidden = preferences.getBoolean(KEY_BATTERY_HIDDEN, false),
            notificationsCardHidden = preferences.getBoolean(KEY_NOTIFICATIONS_HIDDEN, false),
        )
    }
}
