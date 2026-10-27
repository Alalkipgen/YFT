package com.alal.yft.feature.downloads

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alal.yft.BuildConfig
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.feature.library.LocalMediaDetailsSource
import com.alal.yft.feature.library.MediaDetailsSource
import com.alal.yft.thumbnail.DownloadThumbnails
import com.alal.yft.thumbnail.LocalDownloadThumbnails
import com.alal.yft.ui.LocalQuietRelease
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Track E (E-D9): "Break this download's link" is the trial's alone, on a running or paused
 * download; every other build (the release, a preview, a plain debug) and a quiet one drop it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BreakLinkMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyTheTrialsRunningAndPausedDownloadsOfferTheBreak() {
        val broken = mutableListOf<String>()
        setScreen(quiet = false, onBreakLink = { broken += it })

        // Each row's menu opens with one of its own controls, which closes it again.
        for ((id, control) in listOf("r" to "cancel", "p" to "resume", "f" to "retry")) {
            composeRule.onNodeWithTag("download-$id").performClick()
            composeRule.onNodeWithTag("download-menu-$control-$id").assertIsDisplayed()
            val offered = BuildConfig.SOCIAL_TRIAL && id != "f"
            composeRule.onAllNodesWithTag("download-menu-break-link-$id")
                .assertCountEquals(if (offered) 1 else 0)
            val close = if (offered) "break-link" else control
            composeRule.onNodeWithTag("download-menu-$close-$id").performClick()
        }

        assertEquals(if (BuildConfig.SOCIAL_TRIAL) listOf("r", "p") else emptyList(), broken)
    }

    @Test
    fun aQuietBuildDropsTheBreak() {
        setScreen(quiet = true)

        composeRule.onNodeWithTag("download-r").performClick()

        composeRule.onNodeWithTag("download-menu-cancel-r").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download-menu-break-link-r").assertCountEquals(0)
    }

    private fun setScreen(quiet: Boolean, onBreakLink: (String) -> Unit = {}) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalMediaDetailsSource provides MediaDetailsSource.None,
                    LocalDownloadThumbnails provides DownloadThumbnails.None,
                    LocalQuietRelease provides quiet,
                ) {
                    DownloadsScreen(
                        uiState = DownloadsUiState(
                            rows = listOf(
                                row("r", DownloadTaskStatus.RUNNING),
                                row("p", DownloadTaskStatus.PAUSED),
                                row("f", DownloadTaskStatus.FAILED),
                            ),
                        ),
                        onAction = { _, _ -> },
                        onPauseAll = {},
                        todayStartEpochMs = 0,
                        onBreakLink = onBreakLink,
                    )
                }
            }
        }
    }

    private fun row(id: String, status: DownloadTaskStatus) = DownloadRowUiState(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        downloadedBytes = 50,
        totalBytes = 100,
        progressPercent = 50,
        requiresLinkRefresh = false,
        failureReason = null,
        updatedAtEpochMs = 1,
        bytesPerSecond = null,
    )
}
