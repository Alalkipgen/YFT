package com.alal.yft.feature.quickdownload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class QuickDownloadScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTitleLengthMusicAndVideoRowsAndSelectsOne() {
        var state by mutableStateOf(SAMPLE_QUICK_DOWNLOAD)
        val selected = mutableListOf<String>()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = { id ->
                        selected += id
                        state = state.copy(selectedId = id)
                    },
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("Video you copied").assertExists()
        composeRule.onNodeWithText("Ocean waves").assertExists()
        composeRule.onNodeWithTag("quick-length").assert(hasText("4:12"))
        composeRule.onNodeWithText("M4A · Fast").assertExists()
        composeRule.onNodeWithTag("quick-row-mp3").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithText("MP3").assertExists()
        composeRule.onNodeWithText("480p · 18 MB").assertExists()
        composeRule.onNodeWithText("720p · 42 MB").assertExists()
        composeRule.onNodeWithTag("quick-row-high").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag("quick-row-fast").assertIsNotSelected().performClick()

        assertEquals(listOf("fast"), selected)
        composeRule.onNodeWithTag("quick-row-fast").assertIsSelected()
        composeRule.onNodeWithTag("quick-download").performScrollTo().assertIsEnabled()
    }

    @Test
    fun downloadMoreFormatsAndQueuedStatusCallBack() {
        var state by mutableStateOf(SAMPLE_QUICK_DOWNLOAD)
        var downloads = 0
        var more = 0
        var opened = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.DARK) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = { downloads += 1 },
                    onMoreFormats = { more += 1 },
                    onOpenDownloads = { opened += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("quick-more-formats").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick-download").performScrollTo().performClick()
        assertEquals(1, more)
        assertEquals(1, downloads)

        state = state.copy(downloadStatus = PreviewDownloadStatus.Enqueuing)
        composeRule.onNodeWithTag("quick-download").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick-row-fast").assertIsNotEnabled()

        state = state.copy(downloadStatus = PreviewDownloadStatus.Queued("Ocean waves.mp4"))
        composeRule.onNodeWithTag("quick-download-status").performScrollTo()
            .assert(hasText("Queued Ocean waves.mp4. Track progress on the Downloads screen."))
        composeRule.onNodeWithTag("quick-open-downloads").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun meteredDownloadsAskFirstAndAnEmptyStoreOffersClose() {
        var state by mutableStateOf(
            SAMPLE_QUICK_DOWNLOAD.copy(downloadStatus = PreviewDownloadStatus.ConfirmMetered),
        )
        var confirmed = 0
        var closed = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = {},
                    onConfirmMetered = { confirmed += 1 },
                    onClose = { closed += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("metered-confirm").performClick()
        assertEquals(1, confirmed)

        state = QuickDownloadUiState()
        composeRule.onNodeWithTag("quick-empty").assertExists()
        composeRule.onNodeWithTag("navigate-back").performClick()
        assertEquals(1, closed)
    }

    private fun hasText(text: String) = SemanticsMatcher.expectValue(
        SemanticsProperties.Text,
        listOf(androidx.compose.ui.text.AnnotatedString(text)),
    )
}

/** The rows a YouTube lookup gives, with High preselected; shared with the design renders. */
internal val SAMPLE_QUICK_DOWNLOAD = QuickDownloadUiState(
    choices = QuickDownloadChoices.of(QuickDownloadFixtures.youtube()),
    selectedId = "high",
)
