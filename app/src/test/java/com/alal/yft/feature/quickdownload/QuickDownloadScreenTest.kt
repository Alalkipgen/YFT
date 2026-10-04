package com.alal.yft.feature.quickdownload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun showsTitleSiteLengthMusicAndVideoRowsAndSelectsOne() {
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

        composeRule.onNodeWithText(SHEET_TITLE).assertExists()
        composeRule.onNodeWithText("Ocean waves").assertExists()
        composeRule.onNodeWithTag("quick-source").assert(hasText("youtube.com · "))
        composeRule.onNodeWithTag("quick-length").assert(hasText("4:12"))
        composeRule.onNodeWithText("M4A · Fast").assertExists()
        composeRule.onNodeWithText("128 kbps · 4 MB").assertExists()
        composeRule.onNodeWithTag("quick-row-mp3").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithText("480p · 30 fps · ~18 MB").assertExists()
        composeRule.onNodeWithText("720p · 30 fps · ~42 MB").assertExists()
        composeRule.onNodeWithTag("quick-row-high").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag("quick-download").performScrollTo()
            .assert(hasText("Download · ~42 MB"))
        composeRule.onNodeWithTag("quick-row-fast").performScrollTo().assertIsNotSelected()
            .performClick()

        assertEquals(listOf("fast"), selected)
        composeRule.onNodeWithTag("quick-row-fast").assertIsSelected()
        composeRule.onNodeWithTag("quick-download").performScrollTo().assertIsEnabled()
            .assert(hasText("Download · ~18 MB"))
    }

    @Test
    fun moreFormatsOpensInsideTheSheetWithEveryFormatAndDetails() {
        var state by mutableStateOf(SAMPLE_QUICK_DOWNLOAD)
        val selected = mutableListOf<String>()
        var details = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = { id ->
                        selected += id
                        state = state.copy(selectedId = id)
                    },
                    onDownload = {},
                    onToggleMoreFormats = {
                        state = state.copy(moreFormatsExpanded = !state.moreFormatsExpanded)
                    },
                    onOpenDetails = { details += 1 },
                )
            }
        }
        val choices = SAMPLE_QUICK_DOWNLOAD.choices!!
        val tallest = choices.more.first()
        val mp3 = choices.more.first { it.title == "MP3 · 320 kbps" }

        composeRule.onAllNodesWithTag("quick-more-list").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-more-formats").performScrollTo().performClick()

        // Still the same sheet: the rows stay and the list opens under them.
        composeRule.onNodeWithTag("quick-row-high").assertExists()
        composeRule.onNodeWithTag("quick-more-list").assertExists()
        composeRule.onNodeWithTag("quick-option-${tallest.id}").performScrollTo()
            .assertIsNotSelected()
        composeRule.onNodeWithText("1080p · Full HD").assertExists()
        composeRule.onNodeWithText("1920 × 1080 · 30 fps · MP4").assertExists()
        // The 720p option is the High row's file, so it reads as chosen too.
        val high = choices.video.last().option
        composeRule.onNodeWithTag("quick-option-${high.id}").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("quick-option-${mp3.id}").performScrollTo().performClick()
        assertEquals(listOf(QuickChoices.moreId(mp3)), selected)
        composeRule.onNodeWithTag("quick-option-${mp3.id}").assertIsSelected()
        composeRule.onNodeWithTag("quick-download").performScrollTo()
            .assert(hasText("Download · ${mp3.size}"))

        composeRule.onNodeWithTag("quick-details").performScrollTo().performClick()
        assertEquals(1, details)
        composeRule.onNodeWithTag("quick-more-formats").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("quick-more-list").assertCountEquals(0)
    }

    @Test
    fun downloadAndQueuedStatusCallBack() {
        var state by mutableStateOf(SAMPLE_QUICK_DOWNLOAD)
        var downloads = 0
        var opened = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.DARK) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = { downloads += 1 },
                    onOpenDownloads = { opened += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("quick-download").performScrollTo().performClick()
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
    fun loadingThenAFailureOffersTryAgain() {
        var state by mutableStateOf(
            QuickDownloadUiState(header = SAMPLE_QUICK_DOWNLOAD.header, loading = true),
        )
        var retries = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = {},
                    onRetry = { retries += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("quick-header").assertExists()
        composeRule.onNodeWithTag("quick-loading").assertExists()
        composeRule.onAllNodesWithTag("quick-download").assertCountEquals(0)

        state = state.copy(loading = false, failure = "The media could not be reached.")
        composeRule.onNodeWithTag("quick-error").assert(hasText("The media could not be reached."))
        composeRule.onNodeWithTag("quick-retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun meteredDownloadsAskFirstAndNoVideoOffersClose() {
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

/** The sheet a YouTube lookup gives, with High preselected; shared with the design renders. */
internal val SAMPLE_QUICK_DOWNLOAD: QuickDownloadUiState = run {
    val choices = QuickDownloadFixtures.choices(QuickDownloadFixtures.youtube())!!
    QuickDownloadUiState(
        header = SheetHeader(
            title = choices.title,
            source = choices.source,
            durationMillis = choices.durationMillis,
            audioOnly = choices.isAudioOnly,
        ),
        choices = choices,
        selectedId = "high",
    )
}
