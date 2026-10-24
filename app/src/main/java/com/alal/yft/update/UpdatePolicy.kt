package com.alal.yft.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** Which builds ask GitHub (§4.1). */
enum class UpdateMode {
    /** A release signed with another key (CI's, a fork's): never asks; no row, no screen. */
    OFF,

    /** The release with the release key: stable releases with a higher versionCode. */
    RELEASE,

    /** Debug and preview builds: prereleases too, the installed code counted as 0. */
    TEST,
}

object UpdatePolicy {
    /**
     * SHA-256 of the release signing certificate, the repository variable
     * `YFT_RELEASE_CERT_SHA256` that every release's notes carry (public).
     */
    const val RELEASE_CERT_SHA256 =
        "3AEB306491E2DD6FF76DC5A868E6FCC9D330BB9985BF4D15B34A6704EC78988F"

    /** Test mode comes from the build type; a release asks only when the release key signed it. */
    fun mode(testMode: Boolean, installedSigners: Set<String>): UpdateMode = when {
        testMode -> UpdateMode.TEST
        installedSigners == setOf(RELEASE_CERT_SHA256) -> UpdateMode.RELEASE
        else -> UpdateMode.OFF
    }

    /** This build's mode: [UpdateBuild.TEST_MODE], else the installed app's signer. */
    fun mode(context: Context): UpdateMode =
        if (UpdateBuild.TEST_MODE) UpdateMode.TEST else mode(false, installedSigners(context))

    /**
     * The SHA-256 of the installed app's current signing certificates (uppercase hex); empty when
     * Android gives none, so the check stays off.
     */
    fun installedSigners(context: Context): Set<String> = try {
        currentSigners(context.packageManager, context.packageName)
    } catch (failure: Exception) {
        emptySet()
    }

    @Suppress("DEPRECATION")
    private fun currentSigners(packageManager: PackageManager, packageName: String): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        return signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Uppercase hex, two digits a byte. */
    fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
