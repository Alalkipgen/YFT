package com.alal.yft.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Android's answer about an update session (§4.4 step 7), sent through the PendingIntent YFT gave
 * the session. Not exported: nothing else can reach it. `STATUS_PENDING_USER_ACTION` carries
 * Android's confirmation, which YFT starts only while it is in front ([UpdateAutoOpen]).
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val flow = EntryPointAccessors
            .fromApplication(context.applicationContext, InstallStatusEntryPoint::class.java)
            .installFlow()
        deliver(intent, flow)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface InstallStatusEntryPoint {
        fun installFlow(): UpdateInstallFlow
    }

    companion object {
        private const val NO_SESSION = -1

        /** Android's answer in [intent] — the session, its status, the confirmation — to [flow]. */
        fun deliver(intent: Intent, flow: UpdateInstallFlow) {
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, NO_SESSION)
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            val confirm = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                null
            }
            flow.onStatus(sessionId, status, confirm)
        }
    }
}
