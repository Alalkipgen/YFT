package com.alal.yft.update

import com.alal.yft.update.Installs.OTHER_KEY
import com.alal.yft.update.Installs.RELEASE_KEY
import com.alal.yft.update.Updates.found
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkCheckTest {
    private val installed = Installs.release(code = 1_000_000)
    private val update = found("v1.0.1")

    private fun release(facts: ApkFacts?, testMode: Boolean = false, to: FoundUpdate = update) =
        ApkCheck.release(facts, installed, to, testMode)

    @Test
    fun theReleasesOwnApkPasses() {
        assertEquals(ApkVerdict.Passed, release(Installs.releaseApk(code = 1_000_100)))
    }

    @Test
    fun theSignerSetMustBeTheInstalledOnes() {
        val apk = Installs.releaseApk()
        val mismatch = ApkVerdict.SignatureMismatch

        assertEquals(mismatch, release(apk.copy(signers = setOf(OTHER_KEY))))
        assertEquals(mismatch, release(apk.copy(signers = emptySet())))
        assertEquals(mismatch, release(apk.copy(signers = setOf(RELEASE_KEY, OTHER_KEY))))
        assertEquals(mismatch, release(null))
        assertEquals(
            mismatch,
            ApkCheck.release(apk, installed.copy(signers = emptySet()), update, testMode = false),
        )
        // Sets: the order Android lists them in does not matter.
        val two = installed.copy(signers = setOf(OTHER_KEY, RELEASE_KEY))
        assertEquals(
            ApkVerdict.Passed,
            ApkCheck.release(apk.copy(signers = setOf(RELEASE_KEY, OTHER_KEY)), two, update, false),
        )
    }

    @Test
    fun theSignatureIsCheckedBeforeThePackage() {
        val foreign = ApkFacts("com.example.other", 1_000_100, setOf(OTHER_KEY))
        assertEquals(ApkVerdict.SignatureMismatch, release(foreign))

        val sameKey = foreign.copy(signers = setOf(RELEASE_KEY))
        assertEquals(ApkVerdict.OtherApp, release(sameKey))
    }

    @Test
    fun aStableTagsApkHasExactlyItsCodeAboveTheInstalledOne() {
        assertEquals(
            ApkVerdict.NotThatVersion("v1.0.1"),
            release(Installs.releaseApk(code = 1_000_101)),
        )
        assertEquals(
            ApkVerdict.NotThatVersion("v1.0.1"),
            release(Installs.releaseApk(code = 1_000_000)),
        )
        val same = found("v1.0.0")
        assertEquals(ApkVerdict.NotNewer, release(Installs.releaseApk(1_000_000), to = same))
    }

    @Test
    fun aPrereleaseOnlyInTestModeAndNotBelowTheInstalledOne() {
        val beta = found("v1.0.1-beta.1")
        val apk = Installs.releaseApk(code = 1_000_001)

        assertEquals(ApkVerdict.NotThatVersion("v1.0.1-beta.1"), release(apk, to = beta))
        assertEquals(ApkVerdict.Passed, release(apk, testMode = true, to = beta))
        assertEquals(
            ApkVerdict.Passed,
            release(apk.copy(versionCode = 1_000_000), testMode = true, to = beta),
        )
        assertEquals(
            ApkVerdict.NotNewer,
            release(apk.copy(versionCode = 999_901), testMode = true, to = beta),
        )
    }

    @Test
    fun theReinstallTestNeedsTheInstalledCodeExactly() {
        val debug = Installs.debug(code = 3)
        val own = ApkFacts("com.alal.yft.debug", 3, setOf(OTHER_KEY))
        val label = "v1.0.0-beta.3-debug"

        assertEquals(ApkVerdict.Passed, ApkCheck.ownBuild(own, debug, label))
        assertEquals(
            ApkVerdict.NotThatVersion(label),
            ApkCheck.ownBuild(own.copy(versionCode = 4), debug, label),
        )
        assertEquals(
            ApkVerdict.SignatureMismatch,
            ApkCheck.ownBuild(own.copy(signers = setOf(RELEASE_KEY)), debug, label),
        )
        assertEquals(
            ApkVerdict.OtherApp,
            ApkCheck.ownBuild(own.copy(packageName = "com.alal.yft.debug.test"), debug, label),
        )
    }
}
