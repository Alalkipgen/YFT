package com.alal.yft.update

/**
 * Preview build: test mode, as in the debug build. The preview also compiles
 * `src/release/java`, so the release's copy lives in `src/release/kotlin` instead.
 */
internal object UpdateBuild {
    const val TEST_MODE = true
}
