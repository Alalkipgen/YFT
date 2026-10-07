package com.alal.yft.feature.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.AdRedirectPolicy.Reason
import com.alal.yft.core.browser.webview.BlockedNavigation
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BrowserBlockedNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aBlockedRedirectNamesItsSiteAndOpenGoesThereAfterAll() {
        val opened = mutableListOf<String>()
        show(REDIRECT, onOpen = { opened += REDIRECT.url })

        composeRule.onNodeWithTag("browser-blocked-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-blocked-text", useUnmergedTree = true)
            .assertTextEquals("Blocked a redirect to win.other.test")
        composeRule.onNodeWithTag("browser-blocked-open").performClick()

        assertEquals(listOf(REDIRECT.url), opened)
    }

    @Test
    fun aBlockedWindowIsAPopUpAndTheNoticeGoesAfterFourSeconds() {
        composeRule.mainClock.autoAdvance = false
        show(WINDOW)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("browser-blocked-text", useUnmergedTree = true)
            .assertTextEquals("Pop-up blocked")
        composeRule.mainClock.advanceTimeBy(BLOCKED_NOTICE_MS - 500)
        composeRule.onNodeWithTag("browser-blocked-notice").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onAllNodesWithTag("browser-blocked-notice").assertCountEquals(0)
    }

    @Test
    fun theTextSaysPopUpForWindowsAndForRedirectsWithoutAHost() {
        assertEquals("Pop-up blocked", blockedNoticeText(WINDOW))
        assertEquals("Pop-up blocked", blockedNoticeText(REDIRECT.copy(host = "")))
        assertEquals("Blocked a redirect to win.other.test", blockedNoticeText(REDIRECT))
    }

    @Test
    fun theScreensSinkPassesEveryCallOnAndTellsTheNoticeOfBlockedPages() {
        val calls = mutableListOf<String>()
        val inner = object : BrowserObservationSink {
            override fun onPageStarted(url: String) {
                calls += "started $url"
            }
            override fun onPageFinished(url: String, title: String?) = Unit
            override fun onUrlChanged(url: String) = Unit
            override fun onProgressChanged(progress: Int) = Unit
            override fun onRequest(observation: RequestObservation) = Unit
            override fun onDownload(observation: DownloadObservation) = Unit
            override fun onDomProbeResult(pageUrl: String, result: String?) = Unit
            override fun onMainFrameError(url: String?, description: String) = Unit
            override fun onNavigationBlocked(blocked: BlockedNavigation) {
                calls += "blocked ${blocked.host}"
            }
        }
        val noticed = mutableListOf<BlockedNavigation>()
        val sink = BlockedNavigationSink(inner) { noticed += it }

        sink.onPageStarted("https://m.example.test/")
        sink.onNavigationBlocked(REDIRECT)

        assertEquals(listOf("started https://m.example.test/", "blocked win.other.test"), calls)
        assertEquals(listOf(REDIRECT), noticed)
    }

    private fun show(blocked: BlockedNavigation, onOpen: () -> Unit = {}) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                var shown by remember { mutableStateOf<BlockedNavigation?>(blocked) }
                shown?.let {
                    BrowserBlockedNotice(
                        blocked = it,
                        onOpen = onOpen,
                        onDismiss = { shown = null },
                    )
                }
            }
        }
    }

    private companion object {
        val REDIRECT = BlockedNavigation(
            url = "https://win.other.test/prize",
            host = "win.other.test",
            window = false,
            reason = Reason.NO_TAP,
        )
        val WINDOW = BlockedNavigation(
            url = "https://pop.other.test/",
            host = "pop.other.test",
            window = true,
            reason = Reason.NO_TAP,
        )
    }
}
