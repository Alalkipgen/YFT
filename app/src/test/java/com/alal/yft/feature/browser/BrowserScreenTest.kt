package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.ui.theme.YftTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediaButtonOnlyAppearsWhenCandidatesExistAndOpensHonestSheet() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserScreen(
                    uiState = BrowserUiState(
                        currentUrl = "https://example.test/watch",
                        candidates = listOf(candidate()),
                    ),
                    canGoBack = false,
                    canGoForward = false,
                    onAddressChanged = {},
                    onGo = {},
                    onBrowserBack = {},
                    onBrowserForward = {},
                    onReload = {},
                    onStop = {},
                    onNavigateBack = {},
                    browserSurface = { Box(modifier = it) },
                )
            }
        }

        composeRule.onNodeWithTag("media-found-button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Detected media").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Detection only. Preview and download actions are added in later phases.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Fixture stream").assertIsDisplayed()
        composeRule.onNodeWithText("DRM: Unknown").fetchSemanticsNode()
    }

    @Test
    fun emptyCandidateStateHasNoMediaButton() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserScreen(
                    uiState = BrowserUiState(),
                    canGoBack = false,
                    canGoForward = false,
                    onAddressChanged = {},
                    onGo = {},
                    onBrowserBack = {},
                    onBrowserForward = {},
                    onReload = {},
                    onStop = {},
                    onNavigateBack = {},
                    browserSurface = { Box(modifier = it) },
                )
            }
        }

        composeRule.onAllNodesWithTag("media-found-button").assertCountEquals(0)
    }

    private fun candidate() = MediaCandidate(
        pageUrl = "https://example.test/watch",
        mediaUrl = "https://cdn.test/master.m3u8?token=test-only",
        sources = setOf(CandidateSource.REQUEST, CandidateSource.MANIFEST),
        kind = MediaKind.HLS,
        mimeType = "application/vnd.apple.mpegurl",
        title = "Fixture stream",
        confidence = CandidateConfidence.HIGH,
        observedAtEpochMs = 1,
    )
}