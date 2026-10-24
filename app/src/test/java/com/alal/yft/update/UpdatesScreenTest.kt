package com.alal.yft.update

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
    private var checks = 0
    private val switched = mutableListOf<Boolean>()
    private var backs = 0

    private fun setContent(onDownload: ((FoundUpdate) -> Boolean)? = null) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                if (onDownload == null) {
                    UpdatesScreen(
                        state = shown.value,
                        onNavigateBack = { backs += 1 },
                        onCheckNow = { checks += 1 },
                        onAutoCheckChanged = { switched += it },
                        times = times,
                    )
                } else {
                    UpdatesScreen(
                        state = shown.value,
                        onNavigateBack = { backs += 1 },
                        onCheckNow = { checks += 1 },
                        onAutoCheckChanged = { switched += it },
                        times = times,
                        onDownload = onDownload,
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
        val notes = (1..80).joinToString("\n") { "- **Fix $it** in [a list](https://example.com)" }
        shown.value = shown.value.copy(found = found("v1.0.1", notes = notes))
        setContent()

        composeRule.onNodeWithTag("updates-new-version").assert(hasText("New version v1.0.1"))
        // Plain text: Markdown signs stay as they are.
        composeRule.onNodeWithTag("updates-notes")
            .assert(hasScrollAction())
            .assert(
                hasAnyDescendant(
                    hasText("- **Fix 1** in [a list](https://example.com)", substring = true),
                ),
            )
            .performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("updates-download").assert(hasText("Download APK to update"))
        composeRule.onNodeWithTag("updates-security")
            .assert(hasText("Signed by YFT · checked before install"))
        // U1 never says "verified", and has no Check now while an update waits.
        composeRule.onAllNodesWithText("Verified", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("updates-check-now").assertCountEquals(0)
        composeRule.onAllNodesWithText("(test)", substring = true).assertCountEquals(0)
    }

    @Test
    fun theButtonOpensTheExactApkLinkInTheBrowser() {
        val update = found("v1.0.1")
        shown.value = shown.value.copy(found = update)
        setContent()

        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()

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
    fun noBrowserSaysSo() {
        val opened = mutableListOf<FoundUpdate>()
        shown.value = shown.value.copy(found = found("v1.0.1"))
        setContent(onDownload = { opened += it; false })

        composeRule.onNodeWithTag("updates-download").performScrollTo().performClick()

        composeRule.onNodeWithTag("updates-link-failed").performScrollTo()
            .assert(hasText("No app can open this link."))
        assertEquals(listOf("1.0.1"), opened.map { it.version.name })
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
