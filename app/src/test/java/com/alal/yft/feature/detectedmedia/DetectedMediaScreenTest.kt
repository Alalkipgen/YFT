package com.alal.yft.feature.detectedmedia

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetectedMediaScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun withoutAPageTheScreenPointsToTheBrowser() {
        var openedBrowser = false
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                DetectedMediaScreen(
                    page = null,
                    onNavigateBack = {},
                    onOpenBrowser = { openedBrowser = true },
                )
            }
        }

        composeRule.onNodeWithTag("detected-empty").assertIsDisplayed()
        composeRule.onAllNodesWithTag("detected-clear").assertCountEquals(0)
        composeRule.onNodeWithTag("detected-open-browser").performClick()
        assertTrue(openedBrowser)
    }

    @Test
    fun listsThePageByHostOnlyAndPreviewsTheTappedCandidate() {
        val playable = candidate(0, "Fixture clip")
        val drmProtected = candidate(1, "Protected clip").copy(drmHint = true)
        var previewed: MediaCandidate? = null
        var cleared = false
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.DARK) {
                DetectedMediaScreen(
                    page = DetectedPage(
                        pageUrl = "https://video.example.test/watch?token=secret-value",
                        pageTitle = "Fixture page",
                        candidates = listOf(playable, drmProtected),
                    ),
                    onNavigateBack = {},
                    onPreview = { previewed = it },
                    onClear = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithText("Found on this page").assertIsDisplayed()
        composeRule.onNodeWithText("Fixture page").assertIsDisplayed()
        composeRule.onNodeWithText("video.example.test · 1 media item found").assertIsDisplayed()
        composeRule.onAllNodesWithText("secret-value", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("MP4").assertIsDisplayed()

        composeRule.onNodeWithTag("detected-preview-0").performClick()
        assertEquals(playable, previewed)
        // DRM-protected media is never offered, only counted in a note.
        composeRule.onAllNodesWithText("Protected clip").assertCountEquals(0)
        composeRule.onAllNodesWithTag("detected-preview-1").assertCountEquals(0)
        composeRule.onNodeWithTag("detected-list")
            .performScrollToNode(hasTestTag("detected-protected-note"))
        composeRule.onNodeWithTag("detected-protected-note").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Clear list").performClick()
        assertTrue(cleared)
    }

    @Test
    fun aPageWithOnlyProtectedMediaOffersTheBrowserInstead() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                DetectedMediaScreen(
                    page = DetectedPage(
                        pageUrl = "https://example.test/film",
                        pageTitle = "Film",
                        candidates = listOf(candidate(0, "Protected film").copy(drmHint = true)),
                    ),
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("example.test · No media found").assertIsDisplayed()
        composeRule.onNodeWithText("protected item is not listed", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Protected film").assertCountEquals(0)
        composeRule.onNodeWithTag("detected-open-browser").assertIsDisplayed()
    }

    @Test
    fun aPageWithoutMediaExplainsWhatToTry() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                DetectedMediaScreen(
                    page = DetectedPage(
                        pageUrl = "https://example.test/article",
                        pageTitle = null,
                        candidates = emptyList(),
                    ),
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Untitled page").assertIsDisplayed()
        composeRule.onNodeWithText("example.test · No media found").assertIsDisplayed()
        composeRule.onNodeWithTag("detected-none").assertIsDisplayed()
        composeRule.onNodeWithTag("detected-open-browser").assertIsDisplayed()
    }

    private fun candidate(index: Int, title: String) = MediaCandidate(
        pageUrl = "https://video.example.test/watch?token=secret-value",
        mediaUrl = "https://cdn.example.test/video-$index.mp4",
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        title = title,
        observedAtEpochMs = index.toLong(),
    )
}
