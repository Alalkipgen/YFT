package com.alal.yft.minified

import android.util.Log
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.util.regex.Pattern
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * The release commit (decision 30, R8): the shrunk builds run, not only compiled. The #12 normal
 * preview (`com.alal.yft.preview`, the release's flags and look) and the #12 trial
 * (`com.alal.yft.trial`, Master answers every lookup) are driven through their screens only — no
 * class of theirs is read, so what runs is the minified code exactly as the phone gets it — on the
 * CI emulator (`.github/workflows/minified-check.yml`): YouTube's route B, one social ladder (X,
 * then Facebook) and the capture ("Play the video…": the page's video played, then Download again).
 * Opt-in only (`-e yft.minified <package>`); the emulator smoke skips it. Live sites answer a data
 * centre as they choose, so a check never fails on a site's answer: each logs one `RESULT` line
 * (tag [TAG]: what the screens said, Master's lines and the sheet's rows; no address) that the
 * workflow turns into an annotation. The job fails on a crash or an R8-type error in the logs.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MinifiedBuildCheckTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val target: String = InstrumentationRegistry.getArguments().getString(ARG).orEmpty()
    private val trial get() = target.endsWith(".trial")
    private val shots by lazy {
        File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "minified-check")
            .apply { mkdirs() }
    }

    @Before
    fun optIn() {
        assumeTrue("Opt-in only: -e $ARG <package>", target.isNotEmpty())
    }

    /** YouTube: the trial's notice names route B's answer (`master: route B after …`). */
    @Test
    fun youtubeRouteB() {
        check("youtube-route-b", YOUTUBE, if (trial) ROUTE_B else null)
    }

    /** X's ladder (the trial's notice `master: x …`); Facebook's when X gives no Master line. */
    @Test
    fun socialLadder() {
        val x = check("social-x", X_POST, if (trial) "master: x" else null)
        if (trial && !x) check("social-facebook", FACEBOOK_VIDEO, "master: facebook")
    }

    /** The capture: a Facebook video; when the sheet asks for playback, play it and look again. */
    @Test
    fun capture() {
        check("capture-facebook", FACEBOOK_VIDEO, null, playWhenAsked = true)
    }

    /**
     * Opens [url] in the app's browser, waits for its lookup (the notice line [expected] in the
     * trial), opens the Download sheet and reads it. True when [expected] showed (or none expected
     * and the sheet had rows).
     */
    private fun check(
        name: String,
        url: String,
        expected: String?,
        playWhenAsked: Boolean = false,
    ): Boolean {
        val log = mutableListOf<String>()
        var seen = false
        try {
            openInBrowser(url)
            if (expected != null) {
                seen = awaitLines(NOTICE_MS) { lines -> lines.any { it.startsWith(expected) } }
                log += "notice " + (if (seen) "seen" else "missing") + ": " +
                    masterLines().joinToString(" / ").ifEmpty { summary(yftLines()) }
            } else {
                // The page's own lookup first (the sheet waits on it otherwise).
                device.waitForIdle()
                Thread.sleep(LOOKUP_SETTLE_MS)
            }
            shot(name, "page")
            var sheet = openSheet(name)
            log += "sheet: $sheet"
            if (playWhenAsked && sheet.startsWith("asks playback")) {
                device.pressBack()
                playPageVideo()
                shot(name, "played")
                sheet = openSheet("$name-again", retry = true)
                log += "after playback: $sheet"
            }
            if (expected == null) seen = sheet.startsWith("rows")
            details()?.let { log += "details: $it" }
        } catch (failure: Throwable) {
            log += "error: ${failure.javaClass.simpleName}: ${failure.message?.take(200)}"
            shot(name, "error")
        }
        Log.i(TAG, "RESULT $target $name ${if (seen) "PASS" else "NOT-SEEN"} :: " + clean(log.joinToString(" || ")))
        return seen
    }

    private fun openInBrowser(url: String) {
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $target/com.alal.yft.MainActivity")
        val open = device.wait(Until.findObject(By.pkg(target).text("Open browser")), LAUNCH_MS)
            ?: error("Home's Open browser did not show")
        open.click()
        // Home's link field is an EditText too: the browser's own control first.
        if (device.wait(Until.hasObject(By.pkg(target).desc("Close browser")), LAUNCH_MS) != true) {
            error("The browser did not open")
        }
        val address = device.wait(
            Until.findObject(By.pkg(target).clazz("android.widget.EditText")), LAUNCH_MS,
        ) ?: error("The browser's address field did not show")
        address.click()
        address.text = url
        val go = device.wait(Until.findObject(By.pkg(target).desc("Go")), 5_000)
        if (go != null) go.click() else device.pressEnter()
    }

    /**
     * Taps the page's Download and waits for the sheet's answer: `rows N (labels)`, `asks
     * playback: …`, `check: …`, `failed: …` or `waiting`. [retry]: a sheet that still asks (the
     * trial's sheet keeps its answer; a quiet build's Download looks up again) gets one Try again.
     */
    private fun openSheet(name: String, retry: Boolean = false): String {
        val download = device.wait(Until.findObject(By.pkg(target).desc(DOWNLOAD)), BUTTON_MS)
            ?: return "no Download button (" + summary(yftLines()) + ")"
        download.click()
        val deadline = System.currentTimeMillis() + SHEET_MS
        var answer = "waiting"
        var retried = !retry
        while (System.currentTimeMillis() < deadline) {
            val texts = yftLines()
            val rows = texts.filter { QUALITY.matcher(it).matches() }
            val waiting = texts.any { it == WAITING || it == FINDING || it.startsWith(LOOKING_UP) }
            answer = when {
                rows.isNotEmpty() && !waiting -> "rows ${rows.size} (${rows.distinct().joinToString(",")})"
                texts.any { it.startsWith("Play the video") } -> "asks playback: " + summary(texts)
                texts.any { it == "Show check" } -> "check: " + summary(texts)
                !waiting && texts.any { it in FAILED } -> "failed: " + summary(texts)
                else -> "waiting"
            }
            val again = if (!retried && answer.startsWith("asks playback")) {
                device.findObject(By.pkg(target).text("Try again"))
            } else {
                null
            }
            if (again != null) {
                retried = true
                again.click()
                Thread.sleep(POLL_MS)
                continue
            }
            if (answer != "waiting") break
            Thread.sleep(POLL_MS)
        }
        if (answer == "waiting") answer = "waiting: " + summary(yftLines())
        shot(name, "sheet")
        return answer
    }

    /** The trial's Details (and a normal build's, when shown): Master's lines only. */
    private fun details(): String? {
        val button = device.findObject(By.pkg(target).text("Details")) ?: return null
        button.click()
        device.waitForIdle()
        Thread.sleep(1_000)
        val lines = masterLines()
        device.pressBack()
        return lines.joinToString(" / ").takeIf { it.isNotEmpty() }
    }

    /** Plays the page's video as a user does: a tap on the video near the page's top. */
    private fun playPageVideo() {
        val page = device.wait(Until.findObject(By.pkg(target).clazz("android.webkit.WebView")), BUTTON_MS)
            ?: return
        val bounds = page.visibleBounds
        device.click(bounds.centerX(), bounds.top + bounds.height() / 4)
        Thread.sleep(PLAY_MS)
    }

    /** The sheet's words without the browser's own controls, each once. */
    private fun summary(texts: List<String>): String =
        texts.distinct().filterNot { it in CONTROLS }.take(SUMMARY_LINES).joinToString(" / ")

    private fun awaitLines(timeoutMs: Long, done: (List<String>) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (done(masterLines())) return true
            Thread.sleep(POLL_MS)
        }
        return false
    }

    private fun masterLines(): List<String> =
        yftLines().flatMap { it.lines() }.map(String::trim).filter { it.startsWith("master:") }.distinct()

    /** The app's own texts and descriptions on screen; the web page's (inside its WebView) never. */
    private fun yftLines(): List<String> {
        val dump = ByteArrayOutputStream()
        runCatching { device.dumpWindowHierarchy(dump) }.onFailure { return emptyList() }
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(dump.toString("UTF-8")))
        val texts = mutableListOf<String>()
        var depth = 0
        var webDepth = -1
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "node") {
                        val type = parser.getAttributeValue(null, "class").orEmpty()
                        if (webDepth < 0 && type == "android.webkit.WebView") webDepth = depth
                        if (webDepth < 0 && parser.getAttributeValue(null, "package") == target) {
                            listOf("text", "content-desc").forEach { attribute ->
                                parser.getAttributeValue(null, attribute)
                                    ?.takeIf(String::isNotBlank)?.let(texts::add)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (depth == webDepth) webDepth = -1
                    depth--
                }
                XmlPullParser.END_DOCUMENT -> return texts
            }
        }
    }

    private fun shot(name: String, step: String) {
        runCatching { device.takeScreenshot(File(shots, "${target.substringAfterLast('.')}-$name-$step.png")) }
    }

    /** No address in the log: the page's and any media server's stay on the emulator. */
    private fun clean(text: String): String =
        text.replace(Regex("https?://\\S+"), "<address>").replace(Regex("\\s+"), " ").take(3_000)

    private companion object {
        const val TAG = "YftMinifiedCheck"
        const val ARG = "yft.minified"
        const val ROUTE_B = "master: route B after"
        const val YOUTUBE = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        const val X_POST = "https://x.com/NASA/status/2042316964362698815"
        const val FACEBOOK_VIDEO =
            "https://www.facebook.com/NASA/videos/seeing-earth-as-only-nasa-can/1414737229683186/"
        const val WAITING = "Getting qualities…"
        const val FINDING = "Finding the page's video…"
        val DOWNLOAD: Pattern = Pattern.compile("Download (this video|video, \\d+ found|the video on screen)")
        val QUALITY: Pattern = Pattern.compile("(?i)(\\d{3,4}p\\d{0,3}|hd|sd)( .*)?")
        const val LOOKING_UP = "Looking up"
        val FAILED = setOf("Try again", "Retry", "Couldn't get this video.")
        val CONTROLS = setOf(
            "Go", "Close browser", "Previous page", "Next page", "YFT Home", "Browser menu",
            "Download this video", "Download", "Close",
        )
        const val SUMMARY_LINES = 14
        const val LAUNCH_MS = 30_000L
        const val NOTICE_MS = 150_000L
        const val LOOKUP_SETTLE_MS = 15_000L
        const val BUTTON_MS = 60_000L
        const val SHEET_MS = 120_000L
        const val PLAY_MS = 12_000L
        const val POLL_MS = 3_000L
    }
}
