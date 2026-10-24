package com.alal.yft.update

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/** What Android reads from an APK file: its package, its versionCode, its signers' SHA-256. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Set<String>)

/** The installed app an update must match. */
data class InstalledApp(val packageName: String, val versionCode: Long, val signers: Set<String>)

/** Reads [ApkFacts] from a file; null when Android can't read it. */
fun interface ApkReader {
    fun read(file: File): ApkFacts?
}

/** Checks 2 and 3 of §4.4: the first that fails, or [Passed]. */
sealed interface ApkVerdict {
    data object Passed : ApkVerdict

    /** "Signature doesn't match your YFT." — another signer, or none read. */
    data object SignatureMismatch : ApkVerdict

    /** "This file is a different app." */
    data object OtherApp : ApkVerdict

    /** "This file isn't YFT v1.0.1." — another versionCode than the tag's. */
    data class NotThatVersion(val label: String) : ApkVerdict

    /** "This file isn't newer than your YFT." */
    data object NotNewer : ApkVerdict
}

object ApkCheck {
    /**
     * A release's APK: check 2, the signature — the SHA-256 set of its current signers equals the
     * installed app's (neither empty); then check 3, the package — this app's own — and the
     * version: a stable tag's APK has exactly the tag's code (§4.2), above the installed one; a
     * prerelease's (test mode only; no rule code) is not below the installed one.
     */
    fun release(
        facts: ApkFacts?,
        installed: InstalledApp,
        update: FoundUpdate,
        testMode: Boolean,
    ): ApkVerdict {
        signerAndPackage(facts, installed)?.let { return it }
        val code = checkNotNull(facts).versionCode
        val tagCode = update.code
        return when {
            tagCode != null && code != tagCode -> ApkVerdict.NotThatVersion(update.version.label)
            tagCode != null && code <= installed.versionCode -> ApkVerdict.NotNewer
            tagCode == null && !testMode -> ApkVerdict.NotThatVersion(update.version.label)
            code < installed.versionCode -> ApkVerdict.NotNewer
            else -> ApkVerdict.Passed
        }
    }

    /**
     * The reinstall test (§4.4 step 9): this build's own APK — the same signer, the same package,
     * exactly the installed versionCode.
     */
    fun ownBuild(facts: ApkFacts?, installed: InstalledApp, installedLabel: String): ApkVerdict {
        signerAndPackage(facts, installed)?.let { return it }
        return if (checkNotNull(facts).versionCode == installed.versionCode) {
            ApkVerdict.Passed
        } else {
            ApkVerdict.NotThatVersion(installedLabel)
        }
    }

    private fun signerAndPackage(facts: ApkFacts?, installed: InstalledApp): ApkVerdict? = when {
        facts == null || facts.signers.isEmpty() || facts.signers != installed.signers ->
            ApkVerdict.SignatureMismatch
        facts.packageName != installed.packageName -> ApkVerdict.OtherApp
        else -> null
    }
}

/**
 * Asks Android about an APK file (`getPackageArchiveInfo`) and about the installed app, the same
 * way: the SHA-256 of the current signing certificates (`GET_SIGNING_CERTIFICATES` on 28+,
 * `GET_SIGNATURES` before; Android 9 reads an archive's certificates only with the latter, so
 * 28+ asks for both).
 */
internal class PackageApkReader(private val packageManager: PackageManager) : ApkReader {
    override fun read(file: File): ApkFacts? = try {
        packageManager.getPackageArchiveInfo(file.path, FLAGS)?.let(::facts)
    } catch (failure: Exception) {
        null
    }

    fun installed(packageName: String): InstalledApp = try {
        val facts = facts(packageManager.getPackageInfo(packageName, FLAGS))
        InstalledApp(packageName, facts.versionCode, facts.signers)
    } catch (failure: Exception) {
        InstalledApp(packageName, versionCode = -1, signers = emptySet())
    }

    @Suppress("DEPRECATION")
    private fun facts(info: PackageInfo): ApkFacts {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signatures
        } else {
            info.signatures
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return ApkFacts(
            packageName = info.packageName,
            versionCode = code,
            signers = signatures.orEmpty().map { UpdatePolicy.sha256Hex(it.toByteArray()) }.toSet(),
        )
    }

    private companion object {
        @SuppressLint("PackageManagerGetSignatures")
        @Suppress("DEPRECATION")
        val FLAGS: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }
}
