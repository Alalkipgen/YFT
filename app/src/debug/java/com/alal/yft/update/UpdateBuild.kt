package com.alal.yft.update

/**
 * Debug build: test mode (§4.1) — prereleases are offered, the installed versionCode counts as 0
 * and Check automatically starts off. The release's copy says false (decision 30 (v)).
 */
internal object UpdateBuild {
    const val TEST_MODE = true
}
