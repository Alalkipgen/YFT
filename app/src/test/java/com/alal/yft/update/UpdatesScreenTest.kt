package com.alal.yft.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.update.Updates.DAY
import com.alal.yft.update.Updates.HOUR
import com.alal.yft.update.Updates.START
import com.alal.yft.update.Updates.found
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class UpdatesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val times = UpdateTimes(
        now = START + 9 * HOUR + 35 * 60_000,
        is24Hour = true,
        zone = TimeZone.getTimeZone("UTC"),
    )
    private val shown = mutableStateOf(
        UpdateState(mode = UpdateMode.RELEASE, installedName = "1.0.0", autoCheck = true),
    )
    private val install = mutableStateOf<InstallState>(InstallState.Idle)
    private var checks = 0
    private val switched = mutableListOf<Boolean>()
    private var backs = 0
    private val downloads = mutableListOf<String>()
    private var cancels = 0
    private var reinstalls = 0

    /** [onOpenLink] null: the screen's own link opener, as in the app. */
    private fun setContent(onOpenLink: ((FoundUpdate) -> Boolean)? = null) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                if (onOpenLink == null) {
                    UpdatesScreen(
                        state = shown.value,
                        onNavigateBack = { backs += 1 },
                        onCheckNow = { checks += 1 },
                        onAutoCheckChanged = { switched += it },
                        times = times,
                        install = install.value,
                        onDownload = { downloads += it.version.name },
                        onCancel = { cancels += 1 },
                        onReinstall = { reinstalls += 1 },
                    )
                } else {
                    UpdatesScreen(
                        state = shown.value,
                        onNavigateBack = { backs += 1 },
                        onCheckNow = { checks += 1 },
                        onAutoCheckChanged = { switched += it },
                        times = times,
                        install = install.value,
                        onDownload = { downloads += it.version.name },
                        onCancel = { cancels += 1 },
                        onReinstall = { reinstalls += 1 },
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }

    @Test
    fun showsTheInstalledVersion() {
        setContent()

        composeRule.onNodeWithTag("updates-installed").assert(hasText("YFT 1.0.0"))
        composeRule.onNodeWithText("Updates").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate-back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun anUpdateShowsItsTitleNotesButtonAndSecurityLine() {
        val notes = "## Fixes\n" +
            (1..80).joinToString("\n") { "- **Fix $it** in [a list](https://example.com)" }
        shown.value = shown.value.copy(found = found("v1.0.1", notes = notes))
        setContent()

        composeRule.onNodeWithTag("updates-new-version").assert(hasText("New version v1.0.1"))
        // Markdown rendered: no mark shows, the link only its text, a heading and bold as such.
        composeRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("](", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("#", substring = true).assertCountEquals(0)
        composeRule.onNode(hasText("Fixes") and isHeading()).assertExists()
        composeRule.onAllNodesWithText("•").assertCountEquals(80)
        val item = composeRule.onNodeWithText("Fix 1 in a list").fetchSemanticsNode()
        val bold = item.config[SemanticsProperties.Text].single().spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(0 until 5, bold.start until bold.end)
        composeRule.onNodeWithTag("updates-notes")
            .assert(hasScrollAction())
            .assert(hasAnyDescendant(hasText("Fix 80 in a list")))
            .performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("updates-download").assert(hasText("Download APK to update"))
        composeRule.onNodeWithTag("updates-security")
            .assert(hasText("Signed by YFT · checked before install"))
        // Nothing says "verified" before the checks passed; no Check now while an update waits.
        composeRule.onAllNodesWithText("Verified", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("updates-check-now").assertCountEquals(0)
        composeRule.onAllNodesWithText("(test)", substring = true).assertCountEquals(0)
        // The release has no reinstall test.
        composeRule.onAllNodesWithTag("updates-test").assertCountEquals(0)

        // The button runs the in-app update; nothing leaves YFT.
        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()
        assertEquals(listOf("1.0.1"), downloads)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun downloadingShowsTheProgressAndCancel() {
        shown.value = shown.value.copy(found = found("v1.0.1"))
        install.value = InstallState.Downloading(percent = 42)
        setContent()

        composeRule.onNodeWithTag("updates-progress").assert(hasText("Downloading… 42%"))
        composeRule.onAllNodesWithTag("updates-download").assertCountEquals(0)
        composeRule.onAllNodesWithText("Verified", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("updates-cancel").performScrollTo().performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun verifiedOnlyOnceEveryCheckPassed() {
        shown.value = shown.value.copy(found = found("v1.0.1"))
        install.value = InstallState.Installing(test = false)
        setContent()

        composeRule.onNodeWithTag("updates-security")
            .assert(hasText("Verified: checksum and signature match"))
        composeRule.onAllNodesWithTag("updates-download").assertCountEquals(0)
        // Cancel also while Android's installer waits.
        composeRule.onNodeWithTag("updates-cancel").performScrollTo().performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun eachFailureSaysWhichStepThenNothingWasInstalled() {
        shown.value = shown.value.copy(found = found("v1.0.1"))
        install.value = InstallState.Failed(false, InstallFailure.NoSpace)
        setContent()

        val failures = listOf(
            InstallFailure.NoSpace to "Not enough free space for the update.",
            InstallFailure.NoChecksum to
                "This release has no checksum for its APK, so YFT can't check it.",
            InstallFailure.DownloadStopped to "The download stopped. Check the connection.",
            InstallFailure.DownloadFailed to "Couldn't download the update.",
            InstallFailure.ChecksumMismatch to
                "Checksum doesn't match — the download was damaged or changed.",
            InstallFailure.Check(ApkVerdict.SignatureMismatch) to
                "Signature doesn't match your YFT.",
            InstallFailure.Check(ApkVerdict.OtherApp) to "This file is a different app.",
            InstallFailure.Check(ApkVerdict.NotThatVersion("v1.0.1")) to
                "This file isn't YFT v1.0.1.",
            InstallFailure.Check(ApkVerdict.NotNewer) to "This file isn't newer than your YFT.",
            InstallFailure.Android(PackageInstaller.STATUS_FAILURE_CONFLICT) to
                "Android couldn't install the update (conflict).",
            InstallFailure.NoInstaller to "Android's installer didn't open.",
        )
        failures.forEach { (failure, text) ->
            install.value = InstallState.Failed(test = false, failure = failure)
            composeRule.onNodeWithTag("updates-problem-text").assert(hasText(text))
            composeRule.onNodeWithTag("updates-nothing-installed")
                .assert(hasText("Nothing was installed."))
            composeRule.onNodeWithTag("updates-browser").assert(hasText("Download in browser"))
            // The button stays: the user may try again.
            composeRule.onNodeWithTag("updates-download").assertIsEnabled()
            composeRule.onAllNodesWithText("Verified", substring = true, ignoreCase = true)
                .assertCountEquals(0)
        }
    }

    @Test
    fun androidsStatusesInWords() {
        val names = mapOf(
            PackageInstaller.STATUS_FAILURE to "failed",
            PackageInstaller.STATUS_FAILURE_BLOCKED to "blocked",
            PackageInstaller.STATUS_FAILURE_INVALID to "invalid APK",
            PackageInstaller.STATUS_FAILURE_CONFLICT to "conflict",
            PackageInstaller.STATUS_FAILURE_STORAGE to "not enough storage",
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE to "incompatible",
            8 to "timed out",
            -1 to "status -1",
        )
        names.forEach { (status, name) -> assertEquals(name, installStatusName(status)) }
    }

    @Test
    fun downloadInBrowserOpensTheExactApkLink() {
        shown.value = shown.value.copy(found = found("v1.0.1"))
        install.value = InstallState.Failed(false, InstallFailure.DownloadStopped)
        setContent()

        composeRule.onNodeWithTag("updates-browser").performScrollTo().performClick()

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            "https://github.com/Alalkipgen/YFT/releases/download/v1.0.1/video-downloader-1.0.1.apk",
            intent.dataString,
        )
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(intent.component)
        assertNull(shadowOf(context).nextStartedActivity)
        composeRule.onAllNodesWithTag("updates-link-failed").assertCountEquals(0)
    }

    @Test
    fun cancelledSaysSoAndNoBrowserSaysSo() {
        val opened = mutableListOf<FoundUpdate>()
        shown.value = shown.value.copy(found = found("v1.0.1"))
        install.value = InstallState.Cancelled(test = false)
        setContent(onOpenLink = { opened += it; false })

        composeRule.onNodeWithTag("updates-problem-text").assert(hasText("Update cancelled."))
        composeRule.onNodeWithTag("updates-nothing-installed")
            .assert(hasText("Nothing was installed."))
        composeRule.onNodeWithTag("updates-browser").performScrollTo().performClick()

        composeRule.onNodeWithTag("updates-link-failed").performScrollTo()
            .assert(hasText("No app can open this link."))
        assertEquals(listOf("1.0.1"), opened.map { it.version.name })
    }

    @Test
    fun notAllowedSendsToAndroidsSettingsForYft() {
        shown.value = shown.value.copy(found = found("v1.0.1"))
        install.value = InstallState.NotAllowed(test = false)
        setContent()

        composeRule.onNodeWithTag("updates-not-allowed")
            .assert(hasText("Allow YFT to install updates, then tap Download APK to update again."))
        // Nothing was downloaded, so nothing to say about an install.
        composeRule.onAllNodesWithTag("updates-problem").assertCountEquals(0)
        composeRule.onNodeWithTag("updates-open-settings").performScrollTo().performClick()

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun testModeHasTheReinstallTest() {
        shown.value = shown.value.copy(
            mode = UpdateMode.TEST,
            installedName = "1.0.0-beta.3-debug",
            found = found("v1.0.0-beta.4"),
        )
        setContent()

        composeRule.onNodeWithTag("updates-test").performScrollTo()
            .assert(
                hasAnyDescendant(hasText("Tries Android's installer with this build's own APK.")),
            )
        composeRule.onNodeWithTag("updates-reinstall")
            .assert(hasText("Reinstall this build (test)"))
            .performClick()
        assertEquals(1, reinstalls)

        install.value = InstallState.Installing(test = true)
        composeRule.onNodeWithText("Test: this build's own APK, signature matches")
            .assertExists()
        // The release's card claims nothing for the test, and waits.
        composeRule.onAllNodesWithText("Verified", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("updates-download").assertIsNotEnabled()
        composeRule.onNodeWithTag("updates-cancel").performScrollTo().performClick()
        assertEquals(1, cancels)

        install.value =
            InstallState.Failed(true, InstallFailure.Check(ApkVerdict.SignatureMismatch))
        composeRule.onNodeWithTag("updates-problem-text")
            .assert(hasText("Signature doesn't match your YFT."))
        // No release, so no link.
        composeRule.onAllNodesWithTag("updates-browser").assertCountEquals(0)
        composeRule.onNodeWithTag("updates-download").assertIsEnabled()
    }

    @Test
    fun testModeMarksTheVersion() {
        shown.value = shown.value.copy(mode = UpdateMode.TEST, found = found("v1.0.0-beta.1"))
        setContent()

        composeRule.onNodeWithTag("updates-new-version")
            .assert(hasText("New version v1.0.0-beta.1 (test)"))
    }

    @Test
    fun withoutAnUpdateTheLatestLineAndLastChecked() {
        shown.value = shown.value.copy(lastGoodAt = times.now - HOUR)
        setContent()

        composeRule.onNodeWithTag("updates-latest-title")
            .assert(hasText("You have the latest version"))
        composeRule.onNodeWithTag("updates-last-checked")
            .assert(hasText("Last checked today, 20:35"))
        composeRule.onAllNodesWithTag("updates-not-checked").assertCountEquals(0)
        composeRule.onAllNodesWithTag("updates-download").assertCountEquals(0)

        shown.value = shown.value.copy(lastGoodAt = times.now - DAY)
        composeRule.onNodeWithTag("updates-last-checked")
            .assert(hasText("Last checked yesterday, 21:35"))
    }

    @Test
    fun beforeAGoodAnswerNotCheckedYetAndCheckNowWithItsLines() {
        setContent()

        composeRule.onNodeWithTag("updates-not-checked").assert(hasText("Not checked yet"))
        composeRule.onAllNodesWithText("You have the latest version").assertCountEquals(0)
        composeRule.onAllNodesWithTag("updates-check-line").assertCountEquals(0)
        composeRule.onNodeWithTag("updates-check-now").assertIsEnabled().performClick()
        assertEquals(1, checks)

        shown.value = shown.value.copy(checking = true)
        composeRule.onNodeWithTag("updates-check-line").assert(hasText("Checking…"))
        composeRule.onNodeWithTag("updates-check-now").assertIsNotEnabled()

        val lines = listOf(
            CheckLine.NoConnection to "No connection.",
            CheckLine.Busy(START + 10 * HOUR + 10 * 60_000) to
                "GitHub is busy. Try again after 22:10.",
            CheckLine.CouldNotCheck to "Couldn't check. Try again later.",
        )
        lines.forEach { (line, text) ->
            shown.value = shown.value.copy(checking = false, line = line)
            composeRule.onNodeWithTag("updates-check-line").assert(hasText(text))
            composeRule.onNodeWithTag("updates-check-now").assertIsEnabled()
        }
    }

    @Test
    fun theSwitchAndItsText() {
        setContent()

        composeRule.onNodeWithTag("updates-auto-check").performScrollTo()
            .assertIsOn()
            .assert(hasText("Check automatically"))
            .assert(hasText("Once a day when YFT opens. Asks GitHub (api.github.com)."))
            .performClick()
        assertEquals(listOf(false), switched)

        shown.value = shown.value.copy(autoCheck = false)
        composeRule.onNodeWithTag("updates-auto-check").assertIsOff().performClick()
        assertEquals(listOf(false, true), switched)
    }
}
