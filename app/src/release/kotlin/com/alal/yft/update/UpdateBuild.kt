package com.alal.yft.update

/**
 * Release build: no test mode (decision 30 (v)) — stable releases only, and only when the
 * release key signed this app. Kept in `src/release/kotlin`, not `src/release/java`: the preview
 * build compiles `src/release/java` as well and has its own copy.
 */
internal object UpdateBuild {
    const val TEST_MODE = false
}
