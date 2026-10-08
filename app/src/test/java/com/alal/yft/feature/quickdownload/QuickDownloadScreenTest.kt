package com.alal.yft.feature.quickdownload

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import com.alal.yft.thumbnail.LocalRemoteThumbnails
import com.alal.yft.thumbnail.RemoteThumbnails
import com.alal.yft.thumbnail.testPicture
import com.alal.yft.ui.components.YFT_THUMBNAIL_IMAGE_TAG
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        val preferred = choices.video.single { it.rankHeight == 720 }
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
        composeRule.onNodeWithText("M4A").assertExists()
        composeRule.onNodeWithText("MP3 · 128 kbps").assertExists()
        composeRule.onAllNodesWithText("MP3 · 320 kbps").assertCountEquals(0)
        composeRule.onAllNodesWithText("1080p · Full HD").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-option-${preferred.id}").assertIsSelected()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-more-formats").assert(hasText("More formats · 4"))
            .performClick()
        listOf("MP3 · 320 kbps", "MP3 · 192 kbps", "MP3 · 128 kbps").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithTag("quick-option-${choices.audio.first().id}").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        listOf("1080p · Full HD", "720p · HD", "480p", "360p").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithText("1920 × 1080 · 30 fps · MP4").assertExists()
        // The expanded view is the full list once, never duplicated Music/Fast/High rows.
        listOf("Music", "M4A · Fast").forEach {
            composeRule.onAllNodesWithText(it).assertCountEquals(0)
        }
        // Expanding preserves the P9 default selection.
        composeRule.onNodeWithTag("quick-option-${preferred.id}").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
            .assert(hasText("Download · ~42 MB"))
        composeRule.onNodeWithTag("quick-option-${sd.id}").performScrollTo().assertIsNotSelected()
            .performClick()

        assertEquals(listOf(sd.id), selected)
        composeRule.onNodeWithTag("quick-option-${sd.id}").assertIsSelected()
        composeRule.onNodeWithTag("quick-option-${preferred.id}").assertIsNotSelected()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().assertIsEnabled()
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
        composeRule.onNodeWithTag("quick-more-formats").performClick()

        composeRule.onNodeWithTag("quick-option-${mp3.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick-option-${mp3.id}").assertIsSelected()
        composeRule.onAllNodesWithText("Made on the phone").assertCountEquals(3)
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
            .assert(hasText("Download · ${mp3.size}"))

        composeRule.onNodeWithTag("quick-details").assertIsDisplayed().performClick()
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
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().assert(hasText("Download"))
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

        composeRule.onNodeWithTag("quick-more-formats").performClick()
        // The row merges its texts; the warning is one of them, beside the title.
        composeRule.onNodeWithTag("quick-option-${row.id}")
            .performScrollTo()
            .assert(androidx.compose.ui.test.hasText("2160p · 4K"))
            .assert(androidx.compose.ui.test.hasText(QuickDownloadChoices.MAY_NOT_PLAY))
        composeRule.onAllNodesWithText(QuickDownloadChoices.MAY_NOT_PLAY).assertCountEquals(1)
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().assertIsEnabled()
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

        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().performClick()
        assertEquals(1, downloads)

        state = state.copy(downloadStatus = PreviewDownloadStatus.Enqueuing)
        composeRule.onNodeWithTag("quick-download").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick-option-${state.selectedId}")
            .assertIsNotEnabled()

        state = state.copy(downloadStatus = PreviewDownloadStatus.Queued("Ocean waves.mp4"))
        composeRule.onNodeWithTag("quick-download-status").assertIsDisplayed()
            .assert(hasText("Queued Ocean waves.mp4. Track progress on the Downloads screen."))
        composeRule.onNodeWithTag("quick-open-downloads").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun aWaitingSheetShowsTheLinkPlaceholderRowsAndGettingQualitiesThenItsRows() {
        // P16: opened from a pasted link before the lookup answered.
        var state by mutableStateOf(
            QuickDownloadUiState(
                header = SheetHeader(
                    title = "youtube.com/watch?v=fixture0001",
                    source = "youtube.com",
                    durationMillis = null,
                    audioOnly = false,
                ),
                loading = true,
                findingVideo = true,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(state = state, onSelect = {}, onDownload = {})
            }
        }

        composeRule.onNodeWithText("youtube.com/watch?v=fixture0001").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-loading").assertIsDisplayed()
        composeRule.onNodeWithText("Getting qualities…").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-placeholder-audio").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-placeholder-video").assertIsDisplayed()
        composeRule.onAllNodesWithTag("quick-placeholder-row").assertCountEquals(4)
        // Plan adapted (P18): Download is ready while the sheet waits; it queues the pick.
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().assertIsEnabled()
        composeRule.onAllNodesWithTag("quick-rows").assertCountEquals(0)

        // The lookup answered: the real rows take the placeholders' place.
        state = SAMPLE_QUICK_DOWNLOAD
        composeRule.onAllNodesWithTag("quick-placeholder-row").assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-loading").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-rows").assertExists()
        composeRule.onNodeWithTag("quick-download").assertIsEnabled()
    }

    @Test
    fun aWaitingSheetOffersM4AOrTheDefaultQualityAndDownloadStartsWhenReady() {
        // P18: the first row of each waiting section is a choice an early Download takes.
        var state by mutableStateOf(
            QuickDownloadUiState(
                header = SAMPLE_QUICK_DOWNLOAD.header,
                loading = true,
                findingVideo = true,
                defaultQuality = QualityPreference.UP_TO_1080P,
            ),
        )
        val picked = mutableListOf<OptionSection>()
        var downloads = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = { downloads += 1 },
                    onPickEarly = { picked += it },
                )
            }
        }

        val audio = composeRule.onNodeWithTag("quick-early-audio", useUnmergedTree = true)
        audio.assert(hasText("M4A"))
        composeRule.onNodeWithTag("quick-early-video", useUnmergedTree = true)
            .assert(hasText("1080p"))
        composeRule.onAllNodesWithTag("quick-placeholder-row").assertCountEquals(4)
        audio.performClick()
        assertEquals(listOf(OptionSection.AUDIO), picked)
        composeRule.onNodeWithTag("quick-download").assertIsEnabled().performClick()
        assertEquals(1, downloads)

        // Tapped: it waits for the rows, and the pick can no longer change.
        state = state.copy(startsWhenReady = true, earlySection = OptionSection.AUDIO)
        composeRule.onNodeWithTag("quick-download").assertIsNotEnabled()
            .assert(hasText(STARTS_WHEN_READY))
        composeRule.onNodeWithText("Starts when ready…").assertIsDisplayed()

        // The rows came and it started: the sheet says which quality it took.
        state = SAMPLE_QUICK_DOWNLOAD.copy(
            downloadStatus = PreviewDownloadStatus.Queued("Ocean waves.mp4"),
            startedNote = "Downloading 480p — 720p not available",
        )
        composeRule.onNodeWithTag("quick-download-note")
            .assert(hasText("Downloading 480p — 720p not available"))
        composeRule.onNodeWithTag("quick-download-status").assertIsDisplayed()
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
        // P16: Download stays in its place; P18: it can be tapped before the rows come.
        composeRule.onNodeWithTag("quick-download").assertIsEnabled()

        state = state.copy(loading = false, failure = "The media could not be reached.")
        composeRule.onNodeWithTag("quick-error").assert(hasText("The media could not be reached."))
        composeRule.onNodeWithTag("quick-retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun aPageLookupLoadsInTheSheetAProtectedVideoHasNoTryAgainAndOthersAreOneTapAway() {
        var state by mutableStateOf(
            QuickDownloadUiState(
                header = SAMPLE_QUICK_DOWNLOAD.header,
                loading = true,
                findingVideo = true,
            ),
        )
        var others = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = {},
                    onOpenOtherVideos = { others += 1 },
                )
            }
        }

        // P12: the sheet waits for the page's lookup instead of listing the page.
        composeRule.onNodeWithTag("quick-loading").assertExists()
        composeRule.onNodeWithText(WAITING_MESSAGE).assertExists()
        composeRule.onAllNodesWithTag("quick-other-videos").assertCountEquals(0)
        state = state.copy(loading = false, findingVideo = false, failure = "Protected")
        composeRule.onNodeWithTag("quick-error").assert(hasText("Protected"))
        composeRule.onNodeWithTag("quick-retry").assertExists()
        state = state.copy(canRetry = false)
        composeRule.onAllNodesWithTag("quick-retry").assertCountEquals(0)

        // A generic page's main video: the others are one row away.
        state = SAMPLE_QUICK_DOWNLOAD.copy(otherVideos = 3)
        composeRule.onNodeWithText("Other videos on this page (3)").assertExists()
        composeRule.onNodeWithTag("quick-other-videos").performClick()
        assertEquals(1, others)
    }

    @Test
    fun theSheetSaysItFindsThePagesVideoThenThatTheVideoMayBeAnAd() {
        // P28: the page states a far longer video than the ad its player shows first.
        var state by mutableStateOf(
            QuickDownloadUiState(
                header = SAMPLE_QUICK_DOWNLOAD.header,
                loading = true,
                findingVideo = true,
                findingPageVideo = true,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(state = state, onSelect = {}, onDownload = {})
            }
        }

        composeRule.onNodeWithText(FINDING_PAGE_VIDEO_MESSAGE).assertExists()
        composeRule.onAllNodesWithText(WAITING_MESSAGE).assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-maybe-ad").assertCountEquals(0)

        state = SAMPLE_QUICK_DOWNLOAD.copy(maybeAd = true, otherVideos = 2)
        composeRule.onNodeWithTag("quick-maybe-ad").assert(
            hasText("This may be an ad. Play the video for a moment, or see Other videos."),
        )
        composeRule.onNodeWithTag("quick-other-videos").assertExists()
    }

    @Test
    fun theSheetSaysItShowsTheNextVideoAndItsDetailsListBothAttempts() {
        // P29: the first video's file was gone; the qualities are the page's next video's.
        val details = listOf(
            "First video",
            "Step: file check",
            "Host: media.example.test",
            "Status: HTTP 410",
            "Next video",
            "Host: media.example.test",
            "Status: ready",
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = SAMPLE_QUICK_DOWNLOAD.copy(nextVideo = true, attemptDetails = details),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithTag("quick-next-video")
            .assert(hasText("The first file is gone — showing the next video"))
        composeRule.onAllNodesWithTag("quick-error").assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-next-video-detail-text").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-next-video-details").performClick()
        composeRule.onNodeWithTag("quick-next-video-detail-text")
            .assert(hasText(details.joinToString("\n")))
        composeRule.onNodeWithTag("quick-download").assertExists()
    }

    @Test
    fun theSheetSaysItUsesAFreshLinkAndItsDetailsListTheAttempts() {
        // P37: the first link was gone; the same video's fresh link is prepared.
        val details = listOf(
            "First video",
            "Status: HTTP 410",
            "Link from: page script",
            "Player's link",
            "Status: ready",
            "Link from: player request",
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = SAMPLE_QUICK_DOWNLOAD.copy(freshLink = true, attemptDetails = details),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithTag("quick-fresh-link")
            .assert(hasText("The first link is gone — using a fresh link"))
        composeRule.onAllNodesWithTag("quick-next-video").assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-fresh-link-detail-text").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-fresh-link-details").performClick()
        composeRule.onNodeWithTag("quick-fresh-link-detail-text")
            .assert(hasText(details.joinToString("\n")))
        composeRule.onAllNodesWithTag("quick-reload-retry").assertCountEquals(0)
    }

    @Test
    fun aGoneLinkOnTheBrowsersPageOffersReloadPageAndTryAgain() {
        // P37: nothing fresh came from the page: the error offers the browser's reload.
        var reloads = 0
        var retries = 0
        var state by mutableStateOf(
            QuickDownloadUiState(
                header = SAMPLE_QUICK_DOWNLOAD.header,
                failure = "The site no longer has this video (HTTP 410).",
                failureDetails = listOf("Status: HTTP 410"),
                canReload = true,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = {},
                    onDownload = {},
                    onRetry = { retries++ },
                    onReload = { reloads++ },
                )
            }
        }

        composeRule.onNodeWithTag("quick-retry").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-reload-retry")
            .assert(hasText("Reload page and try again"))
            .performClick()
        assertEquals(1, reloads)
        assertEquals(0, retries)

        state = state.copy(canReload = false)
        composeRule.onAllNodesWithTag("quick-reload-retry").assertCountEquals(0)
    }

    @Test
    fun aFailureShowsItsDetailsOnlyWhenAskedFor() {
        // P24: the sheet's Details name the step, the host and the status.
        val details = listOf(
            "Step: list of qualities (manifest)",
            "Host: stream.example.test",
            "Status: HTTP 403",
        )
        var state by mutableStateOf(
            QuickDownloadUiState(
                header = SAMPLE_QUICK_DOWNLOAD.header,
                failure = "The site refused this video (HTTP 403).",
                failureDetails = details,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(state = state, onSelect = {}, onDownload = {})
            }
        }

        composeRule.onNodeWithTag("quick-error")
            .assert(hasText("The site refused this video (HTTP 403)."))
        composeRule.onAllNodesWithTag("quick-error-detail-text").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-error-details").performClick()
        composeRule.onNodeWithTag("quick-error-detail-text")
            .assert(hasText(details.joinToString("\n")))
        composeRule.onNodeWithTag("quick-error-details").performClick()
        composeRule.onAllNodesWithTag("quick-error-detail-text").assertCountEquals(0)

        // A Download the sheet could not prepare has them under its message.
        state = SAMPLE_QUICK_DOWNLOAD.copy(
            downloadStatus = PreviewDownloadStatus.Rejected("The site no longer has this video."),
            downloadDetails = listOf("Step: file check", "Status: HTTP 404"),
        )
        composeRule.onNodeWithTag("quick-download-status")
            .assert(hasText("The site no longer has this video."))
        composeRule.onNodeWithTag("quick-error-details").performClick()
        composeRule.onNodeWithTag("quick-error-detail-text")
            .assert(hasText("Step: file check\nStatus: HTTP 404"))

        // Without Details nothing more is offered.
        state = state.copy(downloadDetails = emptyList())
        composeRule.onAllNodesWithTag("quick-error-details").assertCountEquals(0)
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

    @Test
    fun downloadStaysVisibleWithTwelveExpandedRowsWithoutScrolling() {
        val candidates = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144).map {
            QuickDownloadFixtures.video(it, QuickDownloadFixtures.MIB)
        } + QuickDownloadFixtures.audio(128, QuickDownloadFixtures.MIB)
        val choices = QuickDownloadFixtures.choices(candidates)!!
        assertEquals(12, choices.options.size)
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = SAMPLE_QUICK_DOWNLOAD.copy(
                        choices = choices,
                        selectedId = QuickDownloadChoices.preselect(
                            choices,
                            QualityPreference.UP_TO_720P,
                        )?.id,
                    ),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }
        // Old P3-FIX already shows all 12 rows and has no toggle: its button is offscreen.
        val toggles = composeRule.onAllNodesWithTag("quick-more-formats").fetchSemanticsNodes()
        if (toggles.isNotEmpty()) {
            composeRule.onNodeWithTag("quick-more-formats").performClick()
        }
        composeRule.onAllNodes(SemanticsMatcher("format row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("quick-option-") == true
        }).assertCountEquals(12)
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("quick-details").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-option-${choices.video.last().id}").performScrollTo()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyRowShowsItsOneLineDescriptionAndDownloadStaysVisible() {
        // P25: the line under each title says what the quality is for. Real text measuring
        // (native graphics), so the rows are as tall as on a phone.
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(state = SAMPLE_QUICK_DOWNLOAD, onSelect = {}, onDownload = {})
            }
        }
        val short = listOf(
            "Original sound, fastest",
            "Plays everywhere",
            "Clear view and quick play",
            "Normal quality for quick play",
        )
        val lines = composeRule.onAllNodesWithTag("quick-row-description", useUnmergedTree = true)
        lines.assertCountEquals(short.size)
        short.forEachIndexed { index, line ->
            lines[index].assertIsDisplayed().assertTextEquals(line)
        }
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()

        composeRule.onNodeWithTag("quick-more-formats").performClick()

        composeRule.onAllNodesWithTag("quick-row-description", useUnmergedTree = true)
            .assertCountEquals(SAMPLE_QUICK_DOWNLOAD.choices!!.options.size)
        composeRule.onNodeWithText("High details for full screen play", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun onASmallPhoneDownloadStaysVisibleWithTheDescriptions() {
        // P25: three lines a row still leave Details and Download pinned on a 568 dp screen.
        val choices = QuickDownloadFixtures.choices(QuickDownloadFixtures.youtubeLadder())!!
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = SAMPLE_QUICK_DOWNLOAD.copy(
                        choices = choices,
                        selectedId = QuickDownloadChoices.preselect(
                            choices,
                            QualityPreference.UP_TO_720P,
                        )?.id,
                    ),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("quick-more-formats").performClick()
        composeRule.onNodeWithTag("quick-option-${choices.video.last().id}").performScrollTo()
        composeRule.onNodeWithText("Low quality, smallest file", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-details").assertIsDisplayed()
    }

    @Test
    fun expandingAndCollapsingNeverChangesTheSelectedFormat() {
        var state by mutableStateOf(SAMPLE_QUICK_DOWNLOAD)
        val chosen = state.choices!!.video.first()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                QuickDownloadScreen(
                    state = state,
                    onSelect = { state = state.copy(selectedId = it) },
                    onDownload = {},
                )
            }
        }
        composeRule.onNodeWithTag("quick-more-formats").performClick()
        composeRule.onNodeWithTag("quick-option-${chosen.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick-fewer-formats").performClick()
        assertEquals(chosen.id, state.selectedId)
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
            .assert(hasText("Download · ${chosen.size}"))
        composeRule.onNodeWithTag("quick-more-formats").performClick()
        composeRule.onNodeWithTag("quick-option-${chosen.id}").performScrollTo().assertIsSelected()
    }

    @Test
    fun theHeaderShowsTheVideosPictureOnceItLoads() {
        // P19: the 16:9 header tile shows the placeholder, then the picture the loader gives.
        val asked = mutableListOf<String>()
        val loads = CompletableDeferred<ImageBitmap?>()
        val thumbnails = object : RemoteThumbnails {
            override fun cached(url: String): ImageBitmap? = null

            override suspend fun load(url: String): ImageBitmap? {
                asked += url
                return loads.await()
            }
        }
        val header = SAMPLE_QUICK_DOWNLOAD.header!!.copy(thumbnailUrl = PICTURE)
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalRemoteThumbnails provides thumbnails) {
                    QuickDownloadScreen(
                        state = SAMPLE_QUICK_DOWNLOAD.copy(header = header),
                        onSelect = {},
                        onDownload = {},
                    )
                }
            }
        }
        val picture = hasTestTag(YFT_THUMBNAIL_IMAGE_TAG) and
            hasAnyAncestor(hasTestTag("quick-thumbnail"))

        val tile = composeRule.onNodeWithTag("quick-thumbnail").assertIsDisplayed()
            .getBoundsInRoot()
        assertEquals(16f / 9f, (tile.right - tile.left) / (tile.bottom - tile.top), 0.05f)
        composeRule.onAllNodes(picture, useUnmergedTree = true).assertCountEquals(0)
        loads.complete(testPicture(480, 270))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(picture, useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
        composeRule.runOnIdle { assertEquals(listOf(PICTURE), asked) }
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
private const val PICTURE = "https://i.ytimg.com/vi/fixture0001/hqdefault.jpg"

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
        selectedId = QuickDownloadChoices.preselect(choices, QualityPreference.UP_TO_720P)?.id,
    )
}
