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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun showsTitleSiteLengthThenAudioAndVideoSectionsAndSelectsOne() {
        // P3-FIX (owner's phone): two sections only, Audio then Video, one row per resolution.
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
        val choices = SAMPLE_QUICK_DOWNLOAD.choices!!
        val tallest = choices.video.first()
        val sd = choices.video.single { it.title == "480p" }

        composeRule.onNodeWithText(SHEET_TITLE).assertExists()
        composeRule.onNodeWithText("Ocean waves").assertExists()
        composeRule.onNodeWithTag("quick-source").assert(hasText("youtube.com · "))
        composeRule.onNodeWithTag("quick-length").assert(hasText("4:12"))
        val audioTop = composeRule.onNodeWithTag("quick-section-audio").assert(hasText("Audio"))
            .fetchSemanticsNode().positionInRoot.y
        val videoTop = composeRule.onNodeWithTag("quick-section-video").assert(hasText("Video"))
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(audioTop < videoTop)
        composeRule.onNodeWithText("M4A · 128 kbps").assertExists()
        listOf("MP3 · 320 kbps", "MP3 · 192 kbps", "MP3 · 128 kbps").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithTag("quick-option-${choices.audio.first().id}").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        listOf("1080p · Full HD", "720p · HD", "480p", "360p").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithText("1920 × 1080 · 30 fps · MP4").assertExists()
        // Nothing is listed twice: no Music/Fast/High rows and no More formats list.
        listOf("Music", "M4A · Fast", "More formats").forEach {
            composeRule.onAllNodesWithText(it).assertCountEquals(0)
        }
        // Highest, the default quality, preselects the tallest row with sound.
        composeRule.onNodeWithTag("quick-option-${tallest.id}").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag("quick-download").performScrollTo()
            .assert(hasText("Download · ~80 MB"))
        composeRule.onNodeWithTag("quick-option-${sd.id}").performScrollTo().assertIsNotSelected()
            .performClick()

        assertEquals(listOf(sd.id), selected)
        composeRule.onNodeWithTag("quick-option-${sd.id}").assertIsSelected()
        composeRule.onNodeWithTag("quick-option-${tallest.id}").assertIsNotSelected()
        composeRule.onNodeWithTag("quick-download").performScrollTo().assertIsEnabled()
            .assert(hasText("Download · ~18 MB"))
    }

    @Test
    fun anMp3RowIsChosenLikeAnyOtherAndDetailsCallsBack() {
        var state by mutableStateOf(SAMPLE_QUICK_DOWNLOAD)
        var details = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = { id -> state = state.copy(selectedId = id) },
                    onDownload = {},
                    onOpenDetails = { details += 1 },
                )
            }
        }
        val mp3 = SAMPLE_QUICK_DOWNLOAD.choices!!.audio.single { it.title == "MP3 · 320 kbps" }

        composeRule.onNodeWithTag("quick-option-${mp3.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick-option-${mp3.id}").assertIsSelected()
        composeRule.onAllNodesWithText("Made on the phone").assertCountEquals(3)
        composeRule.onNodeWithTag("quick-download").performScrollTo()
            .assert(hasText("Download · ${mp3.size}"))

        composeRule.onNodeWithTag("quick-details").performScrollTo().performClick()
        assertEquals(1, details)
    }

    @Test
    fun aSilentFileOfUnknownSizeSaysSoAndKeepsItsRealPicture() {
        // The owner's Facebook reel: a 848 × 478 picture with no sound and no stated size.
        val silent = QuickDownloadFixtures.video(478, width = 848, index = 1)
        val choices = QuickDownloadChoices.of(
            QuickDownloadFixtures.group(listOf(silent)),
            listOf(
                SheetSource(
                    silent,
                    QuickDownloadFixtures.resolvedAsset(silent, silent = true),
                    resolved = true,
                ),
            ),
        )!!
        val row = choices.video.single()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = QuickDownloadUiState(
                        header = SheetHeader(
                            title = choices.title,
                            source = choices.source,
                            durationMillis = null,
                            audioOnly = false,
                        ),
                        choices = choices,
                        selectedId = row.id,
                    ),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("480p").assertExists()
        composeRule.onNodeWithText("848 × 478 · 30 fps · MP4").assertExists()
        composeRule.onNodeWithText(QuickDownloadChoices.NO_SOUND).assertExists()
        composeRule.onNodeWithText("Size unknown").assertExists()
        composeRule.onAllNodesWithTag("quick-section-audio").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-download").performScrollTo().assert(hasText("Download"))
    }

    @Test
    fun aFourKRowThePhoneMayNotPlayShowsTheWarningOnItsOwnRow() {
        // P6: a 4K VP9 WebM row on a phone without a VP9 decoder that large.
        val fourK = QuickDownloadFixtures.video(2160, 300 * QuickDownloadFixtures.MIB, true)
            .copy(mimeType = "video/webm", codecs = listOf("vp9"))
        val candidates = listOf(fourK) + QuickDownloadFixtures.youtube()
        val choices = QuickDownloadChoices.of(
            QuickDownloadFixtures.group(candidates),
            QuickDownloadFixtures.sources(candidates),
            playback = { "vp9" !in it.codecs },
        )!!
        val row = choices.video.first()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = QuickDownloadUiState(
                        header = SheetHeader(
                            title = choices.title,
                            source = choices.source,
                            durationMillis = null,
                            audioOnly = false,
                        ),
                        choices = choices,
                        selectedId = row.id,
                    ),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }

        // The row merges its texts; the warning is one of them, beside the title.
        composeRule.onNodeWithTag("quick-option-${row.id}")
            .performScrollTo()
            .assert(androidx.compose.ui.test.hasText("2160p · 4K"))
            .assert(androidx.compose.ui.test.hasText(QuickDownloadChoices.MAY_NOT_PLAY))
        composeRule.onAllNodesWithText(QuickDownloadChoices.MAY_NOT_PLAY).assertCountEquals(1)
        composeRule.onNodeWithTag("quick-download").performScrollTo().assertIsEnabled()
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
        composeRule.onNodeWithTag("quick-option-${state.choices!!.video.last().id}")
            .assertIsNotEnabled()

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

/**
 * The sheet a YouTube lookup gives, with the default quality's row preselected; shared with the
 * design renders.
 */
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
        selectedId = QuickDownloadChoices.preselect(choices, QualityPreference.HIGHEST)?.id,
    )
}
