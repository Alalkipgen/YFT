package com.alal.yft.smoke

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.alal.yft.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * README screenshots: walks Home, the browser's Found on this page, the Download sheet,
 * Downloads, Library, Settings and Home in the dark theme, and saves one picture of each
 * ([WIDTH] px wide) for `docs/screenshots/`. Opt-in only (`-e yft.readmeScreenshots 1`), so the
 * emulator smoke test skips it; `scripts/readme-screenshots.sh` runs it on a clean emulator.
 * The sample is Big Buck Bunny (CC BY 3.0, Blender Foundation): the first of [SAMPLES] that
 * works. A screen that cannot be reached is named in the failure; the others are still saved.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ReadmeScreenshotsTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val failures = mutableListOf<String>()

    @Test
    fun captureReadmeScreens() {
        assumeTrue(
            "Opt-in only: -e $OPT_IN 1",
            InstrumentationRegistry.getArguments().getString(OPT_IN) == "1",
        )
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        shell("cmd uimode night no")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                composeRule.waitUntil(LAUNCH_MILLIS) { hasNode("home-open-browser") }
                settle()
                step("home") { capture("home") }
                step("browser-found") { browserFound() }
                step("download-sheet") { downloadSheet() }
                step("downloads") { downloads() }
                step("library") { library() }
                step("settings") { settings() }
                // Home again, now with the saved video under Recent.
                step("home") { homeWithRecent() }
                step("home-dark") { darkHome() }
            }
        } finally {
            shell("cmd uimode night no")
        }
        check(failures.isEmpty()) { "README screenshots not taken: ${failures.distinct()}" }
    }

    /** The first sample page whose video the browser finds. */
    private fun browserFound() {
        val found = SAMPLES.any { page ->
            goHome()
            click("home-open-browser")
            composeRule.waitUntil(UI_MILLIS) { hasNode("browser-address") }
            composeRule.onAllNodesWithTag("browser-address").onFirst()
                .performClick()
                .performTextReplacement(page)
            click("browser-go")
            val shown = runCatching {
                composeRule.waitUntil(FIND_MILLIS) { hasNode("media-found-button") }
            }.isSuccess
            if (!shown) Log.w(LOG_TAG, "The browser found no video on $page")
            shown
        }
        check(found) { "The browser found no video on any sample page" }
        settle(PAGE_SETTLE_MILLIS)
        click("media-found-button")
        composeRule.waitUntil(UI_MILLIS) { hasNode("found-sheet") }
        settle()
        capture("browser-found")
    }

    /** The first sample link whose Download sheet lists qualities. */
    private fun downloadSheet() {
        val listed = SAMPLES.any { link ->
            goHome()
            editHomeLink()
            composeRule.onAllNodesWithTag("home-link").onFirst()
                .performClick()
                .performTextReplacement(link)
            click("home-open-link")
            val rows = runCatching {
                composeRule.waitUntil(LOOKUP_MILLIS) {
                    hasNode("quick-sheet") || hasNode("home-view-media") ||
                        hasNode("home-not-found")
                }
                if (!hasNode("quick-sheet") && hasNode("home-view-media")) {
                    click("home-view-media")
                    composeRule.waitUntil(UI_MILLIS) { hasNode("quick-sheet") }
                }
                if (!hasNode("quick-sheet")) return@runCatching false
                composeRule.waitUntil(LOOKUP_MILLIS) {
                    hasNode("quick-error") || hasNode("quick-empty") || (
                        hasNode("quick-rows") && !hasNode("quick-loading") &&
                            !hasNode("quick-waiting-rows")
                        )
                }
                hasNode("quick-rows") && !hasNode("quick-error")
            }.getOrDefault(false)
            if (!rows) Log.w(LOG_TAG, "The Download sheet listed no qualities for $link")
            rows
        }
        check(listed) { "The Download sheet listed no qualities for any sample" }
        settle(SIZE_SETTLE_MILLIS)
        capture("download-sheet")
    }

    /** Downloads the sheet's last (smallest) row, so the run stays short. */
    private fun downloads() {
        check(hasNode("quick-sheet")) { "The Download sheet is not open" }
        val rows = composeRule.onAllNodes(OPTION_ROW)
        val count = rows.fetchSemanticsNodes().size
        check(count > 0) { "The Download sheet has no rows" }
        rows[count - 1].performScrollTo().performClick()
        click("quick-download")
        composeRule.waitUntil(UI_MILLIS) {
            hasNode("quick-open-downloads") || hasNode("metered-confirm")
        }
        if (hasNode("metered-confirm")) {
            click("metered-confirm")
            composeRule.waitUntil(UI_MILLIS) { hasNode("quick-open-downloads") }
        }
        click("quick-open-downloads")
        composeRule.waitUntil(UI_MILLIS) { hasNode("downloads-list") }
        settle(PROGRESS_SETTLE_MILLIS)
        capture("downloads")
    }

    private fun library() {
        closeSheets()
        val deadline = SystemClock.uptimeMillis() + DOWNLOAD_MILLIS
        click("nav-library")
        composeRule.waitForIdle()
        while (!hasNode("library-grid")) {
            check(SystemClock.uptimeMillis() < deadline) { "The download did not reach Library" }
            SystemClock.sleep(POLL_MILLIS)
            if (!hasNode("library-grid")) {
                // Coming back to the tab reads the library again.
                click("nav-downloads")
                composeRule.waitForIdle()
                click("nav-library")
                composeRule.waitForIdle()
            }
        }
        settle(THUMBNAIL_SETTLE_MILLIS)
        capture("library")
    }

    private fun settings() {
        closeSheets()
        click("nav-settings")
        composeRule.waitUntil(UI_MILLIS) { hasNode("settings-list") }
        settle()
        capture("settings")
    }

    private fun homeWithRecent() {
        goHome()
        settle(THUMBNAIL_SETTLE_MILLIS)
        capture("home")
    }

    private fun darkHome() {
        goHome()
        shell("cmd uimode night yes")
        SystemClock.sleep(THEME_SETTLE_MILLIS)
        composeRule.waitUntil(LAUNCH_MILLIS) { hasNode("home-open-browser") }
        settle(THUMBNAIL_SETTLE_MILLIS)
        capture("home-dark")
    }

    /** Back to Home from a sheet, the browser or another tab. */
    private fun goHome() {
        repeat(GO_HOME_TRIES) {
            when {
                hasSheet() -> device.pressBack()
                hasNode("browser-close") -> click("browser-close")
                hasNode("home-open-browser") -> return
                hasNode("nav-home") -> click("nav-home")
                else -> device.pressBack()
            }
            pause()
        }
        composeRule.waitUntil(UI_MILLIS) { hasNode("home-open-browser") && !hasSheet() }
    }

    private fun closeSheets() {
        repeat(GO_HOME_TRIES) {
            if (!hasSheet()) return
            device.pressBack()
            pause()
        }
    }

    /** A finished lookup shows its result in the link box; Edit link brings the box back. */
    private fun editHomeLink() {
        if (hasNode("home-link")) return
        if (hasNode("home-found")) click("home-found")
        if (hasNode("home-not-found")) click("home-not-found")
        composeRule.waitUntil(UI_MILLIS) { hasNode("home-link") }
    }

    private fun hasSheet(): Boolean = hasNode("found-sheet") || hasNode("quick-sheet")

    private fun pause() {
        composeRule.waitForIdle()
        SystemClock.sleep(STEP_PAUSE_MILLIS)
    }

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            failures += name
            Log.e(LOG_TAG, "$name: ${error.message}", error)
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        device.waitForIdle()
        val directory = checkNotNull(context.getExternalFilesDir(DIRECTORY))
        check(directory.exists() || directory.mkdirs())
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Android returned no screenshot"
        }
        val hardware = Build.VERSION.SDK_INT >= 26 && screen.config == Bitmap.Config.HARDWARE
        val software = if (hardware) {
            screen.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            screen
        }
        val picture = if (software.width > WIDTH) {
            val height = software.height * WIDTH / software.width
            Bitmap.createScaledBitmap(software, WIDTH, height, true)
        } else {
            software
        }
        try {
            File(directory, "$name.png").outputStream().use { output ->
                check(picture.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            setOf(picture, software, screen).forEach(Bitmap::recycle)
        }
        Log.i(LOG_TAG, "Saved $name.png")
    }

    private fun click(tag: String) {
        composeRule.waitUntil(UI_MILLIS) { hasNode(tag) }
        composeRule.onAllNodesWithTag(tag).onFirst().performClick()
    }

    private fun settle(millis: Long = SETTLE_MILLIS) {
        composeRule.waitForIdle()
        SystemClock.sleep(millis)
        composeRule.waitForIdle()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).use { it.readBytes() }
    }

    private fun hasNode(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val LOG_TAG = "YftReadmeScreens"
        const val OPT_IN = "yft.readmeScreenshots"
        const val DIRECTORY = "readme-screenshots"
        const val WIDTH = 720
        const val GO_HOME_TRIES = 5
        const val LAUNCH_MILLIS = 30_000L
        const val UI_MILLIS = 15_000L
        const val FIND_MILLIS = 45_000L
        const val LOOKUP_MILLIS = 90_000L
        const val DOWNLOAD_MILLIS = 300_000L
        const val POLL_MILLIS = 10_000L
        const val STEP_PAUSE_MILLIS = 500L
        const val SETTLE_MILLIS = 1_500L
        const val PAGE_SETTLE_MILLIS = 4_000L
        const val SIZE_SETTLE_MILLIS = 4_000L
        const val PROGRESS_SETTLE_MILLIS = 3_000L
        const val THUMBNAIL_SETTLE_MILLIS = 3_000L
        const val THEME_SETTLE_MILLIS = 3_000L
        /** Big Buck Bunny (CC BY 3.0, Blender Foundation) on public pages, best first. */
        val SAMPLES = listOf(
            "https://vimeo.com/1084537",
            "https://archive.org/details/BigBuckBunny_124",
            "https://commons.wikimedia.org/wiki/File:Big_Buck_Bunny_4K.webm",
        )
        val OPTION_ROW = SemanticsMatcher("a Download sheet row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("quick-option-") == true
        }
    }
}
