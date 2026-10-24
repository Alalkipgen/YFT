package com.alal.yft.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Android's side of the in-app update (§4.4 steps 1, 2, 7, 8 and 9); fakes in tests. */
interface InstallSystem {
    /** Step 1: may YFT ask Android to install? Before Android 8 its installer asks by itself. */
    fun canInstall(): Boolean

    /** Free bytes where the APK is kept (step 2). */
    fun freeBytes(): Long

    /** The installed app an APK must match (checks 2 and 3). */
    fun installed(): InstalledApp

    /** This build's own APK (the reinstall test). */
    fun ownApk(): File

    /** A new session for this app's update, [size] bytes; its id. */
    fun createSession(size: Long): Int

    /** Writes [file] into session [sessionId] and commits it; Android answers the receiver. */
    fun commit(sessionId: Int, file: File)

    fun abandon(sessionId: Int)

    /** YFT's own sessions an earlier run left. */
    fun abandonAll()
}

/**
 * [InstallSystem] on `PackageInstaller` (§4.4 step 7): a full-install session for this app's own
 * package, the user's reason, the user's confirmation required (Android 12+), the checked file
 * written into it and committed with a mutable PendingIntent to the unexported
 * [InstallStatusReceiver]. Android's installer then asks the user; YFT never installs silently.
 * [statusIntent] is where Android's answer goes: the receiver in the app, the test's own in the
 * emulator test.
 */
internal class PackageInstallSystem(
    private val context: Context,
    private val statusIntent: Intent = Intent(context, InstallStatusReceiver::class.java),
) : InstallSystem {
    private val packageManager: PackageManager = context.packageManager
    private val reader = PackageApkReader(packageManager)

    private val installer: PackageInstaller
        get() = packageManager.packageInstaller

    override fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    override fun freeBytes(): Long = context.cacheDir.usableSpace

    override fun installed(): InstalledApp = reader.installed(context.packageName)

    override fun ownApk(): File = File(context.applicationInfo.sourceDir)

    override fun createSession(size: Long): Int {
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        return installer.createSession(params)
    }

    override fun commit(sessionId: Int, file: File) {
        installer.openSession(sessionId).use { session ->
            session.openWrite(SESSION_APK_NAME, 0, file.length()).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            session.commit(statusIntent(sessionId).intentSender)
        }
    }

    // Mutable: Android adds the status and the confirmation to it. Explicit: only YFT's receiver.
    private fun statusIntent(sessionId: Int): PendingIntent {
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(statusIntent),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
    }

    override fun abandon(sessionId: Int) {
        try {
            installer.abandonSession(sessionId)
        } catch (gone: Exception) {
            // Already finished or given up: nothing to remove.
        }
    }

    override fun abandonAll() {
        installer.mySessions.forEach { abandon(it.sessionId) }
    }

    private companion object {
        const val SESSION_APK_NAME = "base.apk"
    }
}
