package com.alal.yft.download

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** What Android and the phone allow YFT in the background right now (P34). */
data class BackgroundSystemState(
    /** YFT may run without battery limits (`isIgnoringBatteryOptimizations`). */
    val unrestricted: Boolean = true,
    /** Download progress can be seen outside YFT: permission, app switch and channel are on. */
    val notificationsVisible: Boolean = true,
    /** A Xiaomi, Redmi or POCO phone: HyperOS has its own battery saver and Autostart. */
    val xiaomi: Boolean = false,
) {
    companion object {
        /** Nothing limited; for previews and tests. */
        val Unlimited = BackgroundSystemState()
    }
}

/** Reads [BackgroundSystemState]; read again whenever YFT comes back to the screen. */
fun interface BackgroundSystemStatus {
    fun read(): BackgroundSystemState

    companion object {
        val Unlimited = BackgroundSystemStatus { BackgroundSystemState.Unlimited }
    }
}

/** The phone's real state. */
class AndroidBackgroundSystemStatus(context: Context) : BackgroundSystemStatus {
    private val context = context.applicationContext

    override fun read(): BackgroundSystemState = BackgroundSystemState(
        unrestricted = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
        notificationsVisible = notificationsVisible(),
        xiaomi = isXiaomi(Build.MANUFACTURER),
    )

    private fun notificationsVisible(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        if (!permitted || !manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannelCompat(DownloadNotificationFactory.CHANNEL_ID)
        return channel == null || channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }
}

/** Xiaomi, Redmi and POCO phones (HyperOS / MIUI). */
internal fun isXiaomi(manufacturer: String?): Boolean =
    manufacturer?.lowercase(Locale.US)?.trim() in XIAOMI_MANUFACTURERS

private val XIAOMI_MANUFACTURERS = setOf("xiaomi", "redmi", "poco")

/** The screens the background cards open (P34). Each falls back when a phone lacks one. */
object BackgroundSettingsIntents {
    /**
     * Asks Android to let YFT run without battery limits, or opens the closest screen. YFT is
     * handed out on GitHub, not Google Play, so Play's limits on the direct request do not apply.
     */
    @SuppressLint("BatteryLife")
    fun allowUnrestricted(context: Context) {
        val app = Uri.parse("package:${context.packageName}")
        startFirst(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
        )
    }

    /** YFT's own page in Settings, where HyperOS keeps Battery saver and Autostart. */
    fun appDetails(context: Context) {
        startFirst(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    /** YFT's notification settings. */
    fun notifications(context: Context) {
        val app = Uri.parse("package:${context.packageName}")
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        startFirst(context, notifications, details)
    }

    private fun startFirst(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // The next screen is the fallback.
            } catch (_: SecurityException) {
                // Some phones guard a settings screen; the next one still helps.
            }
        }
    }
}
