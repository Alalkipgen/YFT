package com.alal.yft.update

import com.alal.yft.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The version code rule (§4.2) on this build's own `yft.versionName` / `yft.versionCode`: a
 * stable name has exactly the rule's code (1.0.0 → 1000000), a prerelease a code below its
 * version's — so the updater's tag codes and Android's versionCode agree. CI and
 * `scripts/release-prep.sh` run it.
 */
class VersionCodeRuleTest {
    @Test
    fun theVersionCodeFollowsTheRule() {
        // Unit tests run on the debug variant, whose versionName carries the "-debug" suffix.
        val name = BuildConfig.VERSION_NAME.removeSuffix("-debug")
        val version = ReleaseVersion.parse(name)
        assertNotNull("yft.versionName $name does not follow the scheme", version)
        val code = BuildConfig.VERSION_CODE.toLong()
        val ruleCode = checkNotNull(version).ruleCode
        if (ruleCode != null) {
            assertEquals("yft.versionCode of the stable $name must be the rule's", ruleCode, code)
        } else {
            assertTrue(
                "yft.versionCode $code of the prerelease $name must be below ${version.baseCode}",
                code < version.baseCode,
            )
        }
        assertTrue(code in 1..Int.MAX_VALUE.toLong())
    }

    @Test
    fun theRuleItself() {
        assertEquals(1_000_000L, ReleaseVersion(1, 0, 0).ruleCode)
        assertEquals(1_000_100L, ReleaseVersion(1, 0, 1).ruleCode)
        assertEquals(1_010_000L, ReleaseVersion(1, 1, 0).ruleCode)
        // v1.0.0 sits above every 1.0.0 beta (1, 2, 3, …) and above 1.0.0-rc.1's 999901.
        assertTrue(ReleaseVersion(1, 0, 0).baseCode > 999_901L)
    }
}
