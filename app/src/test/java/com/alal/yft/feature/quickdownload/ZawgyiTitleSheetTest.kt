package com.alal.yft.feature.quickdownload

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.UNICODE_TITLE
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.ZAWGYI_TITLE
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * v1.0.2 item 25: a video whose title is written in Zawgyi. The sheet shows the title in Unicode
 * and a Download saves it so: the file's name, an MP3's title tag, the Downloads row named after
 * the file. "Use original title" switches the next Download to the title the video gave, and
 * back again.
 */
class ZawgyiTitleSheetTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val store = DetectedMediaStore()
    private val selection = PreviewSelectionStore()
    private val starter = PlanningStarter()

    @Test
    fun theSheetShowsAndSavesAZawgyiTitleInUnicode() = runTest {
        open(ZAWGYI_TITLE)
        val sheet = sheet()

        val state = sheet.uiState.value
        assertEquals(UNICODE_TITLE, state.header?.title)
        assertEquals(UNICODE_TITLE, state.shownTitle)
        assertTrue(state.offersOriginalTitle)
        assertFalse(state.useOriginalTitle)
        assertEquals(
            state.lookupDetails + "Title converted from Zawgyi (p=1.0000)",
            state.lookupDetailLines,
        )

        download(sheet, VIDEO_ROW)
        download(sheet, MP3_ROW)

        assertEquals(listOf(UNICODE_TITLE, UNICODE_TITLE), starter.assets.map { it.title })
        assertEquals(
            listOf("$UNICODE_TITLE 720p.mp4", "$UNICODE_TITLE MP3 192 kbps.mp3"),
            starter.requests.map { it.fileName },
        )
        assertEquals(listOf(UNICODE_TITLE), starter.mp3Titles())
        assertEquals(
            PreviewDownloadStatus.Queued("$UNICODE_TITLE MP3 192 kbps.mp3"),
            sheet.uiState.value.downloadStatus,
        )
    }

    @Test
    fun useOriginalTitleSwitchesTheFileNameAndTheTagsBackAndAgain() = runTest {
        open(ZAWGYI_TITLE)
        val sheet = sheet()
        download(sheet, MP3_ROW)

        sheet.toggleOriginalTitle()
        val original = sheet.uiState.value
        assertTrue(original.useOriginalTitle)
        assertEquals(ZAWGYI_TITLE, original.shownTitle)
        assertEquals(ZAWGYI_TITLE, original.downloadTitle)
        // Like another row, it clears the last Download's line; that download keeps its name.
        assertEquals(PreviewDownloadStatus.Idle, original.downloadStatus)
        download(sheet, MP3_ROW)
        download(sheet, VIDEO_ROW)

        sheet.toggleOriginalTitle()
        assertFalse(sheet.uiState.value.useOriginalTitle)
        assertEquals(UNICODE_TITLE, sheet.uiState.value.shownTitle)
        download(sheet, MP3_ROW)

        assertEquals(
            listOf(UNICODE_TITLE, ZAWGYI_TITLE, ZAWGYI_TITLE, UNICODE_TITLE),
            starter.assets.map { it.title },
        )
        assertEquals(
            listOf(
                "$UNICODE_TITLE MP3 192 kbps.mp3",
                "$ZAWGYI_TITLE MP3 192 kbps.mp3",
                "$ZAWGYI_TITLE 720p.mp4",
                "$UNICODE_TITLE MP3 192 kbps.mp3",
            ),
            starter.requests.map { it.fileName },
        )
        assertEquals(
            listOf(UNICODE_TITLE, ZAWGYI_TITLE, UNICODE_TITLE),
            starter.mp3Titles(),
        )
    }

    @Test
    fun downloadAsTakesTheTitleTheSheetShows() = runTest {
        open(ZAWGYI_TITLE)
        val sheet = sheet()

        assertTrue(sheet.openDetails())
        assertEquals(UNICODE_TITLE, selection.selection.value?.title)

        sheet.toggleOriginalTitle()
        assertTrue(sheet.openDetails())
        assertEquals(ZAWGYI_TITLE, selection.selection.value?.title)
    }

    @Test
    fun aUnicodeTitleOffersNoSwitchAndStaysAsItIs() = runTest {
        open(UNICODE_TITLE)
        val sheet = sheet()

        val state = sheet.uiState.value
        assertEquals(UNICODE_TITLE, state.shownTitle)
        assertFalse(state.offersOriginalTitle)
        assertEquals(state.lookupDetails, state.lookupDetailLines)
        sheet.toggleOriginalTitle()
        assertFalse(sheet.uiState.value.useOriginalTitle)
        download(sheet, VIDEO_ROW)

        assertEquals(listOf("$UNICODE_TITLE 720p.mp4"), starter.requests.map { it.fileName })
        // Download as opens the page's row as before.
        assertTrue(sheet.openDetails())
        assertEquals("$UNICODE_TITLE — 720p", selection.selection.value?.title)
    }

    @Test
    fun theTitleStaysWhileADownloadAsksAboutMobileData() = runTest {
        open(ZAWGYI_TITLE)
        val sheet = sheet(
            preferences = DownloadPreferences(confirmOnMeteredNetwork = true),
            network = NetworkSnapshot(connected = true, validated = true, unmetered = false),
        )

        download(sheet, VIDEO_ROW)
        assertEquals(PreviewDownloadStatus.ConfirmMetered, sheet.uiState.value.downloadStatus)
        sheet.toggleOriginalTitle()

        assertFalse(sheet.uiState.value.useOriginalTitle)
        assertEquals(UNICODE_TITLE, sheet.uiState.value.shownTitle)
    }

    /** The lookup answered: YouTube's rows of a video named [title], the sheet's video. */
    private fun open(title: String) {
        val rows = QuickDownloadFixtures.youtubeTitled(title)
        store.publish(QuickDownloadFixtures.PAGE, title, rows)
        store.select(MediaGroups.of(rows).single())
    }

    private fun TestScope.download(sheet: QuickDownloadViewModel, row: String) {
        sheet.select(sheet.uiState.value.choices!!.options.single { it.title == row }.id)
        sheet.download()
        advanceUntilIdle()
    }

    private fun sheet(
        preferences: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        network: NetworkSnapshot = WIFI,
    ) = QuickDownloadViewModel(
        store = store,
        selectionStore = selection,
        resolver = WholeFileResolver,
        downloadStarter = starter,
        downloadPreferences = object : DownloadPreferencesRepository {
            override val preferences: Flow<DownloadPreferences> = MutableStateFlow(preferences)

            override suspend fun update(
                transform: (DownloadPreferences) -> DownloadPreferences,
            ) = Unit
        },
        network = object : NetworkStatusSource {
            override val snapshot: StateFlow<NetworkSnapshot> = MutableStateFlow(network)
        },
        playback = VideoPlaybackSupport.ANY,
    )

    /** Each row's file as the resolver reads a whole file: an M4A stays an AAC sound track. */
    private object WholeFileResolver : VariantResolver {
        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult =
            VariantResolutionResult.Success(QuickDownloadFixtures.resolvedAsset(candidate))
    }

    /** Plans each download as the queue does: the file's name, an MP3's title tag. */
    private class PlanningStarter : PreviewDownloadStarter {
        val assets = mutableListOf<MediaAsset>()
        val requests = mutableListOf<DownloadRequest>()

        fun mp3Titles(): List<String?> = requests.filterIsInstance<DownloadRequest.Direct>()
            .mapNotNull { it.plan.mp3 }
            .map { it.title }

        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
            assets += asset
            val taskId = "task-${assets.size}"
            val planned = DownloadPlanFactory.create(
                asset = asset,
                variant = variant,
                taskId = taskId,
                nowEpochMs = 1L,
                sdkInt = 34,
                av1Enabled = false,
            )
            val request = (planned as DownloadPlanResult.Ready).request
            requests += request
            return EnqueueResult.Started(taskId, request.fileName)
        }
    }

    private companion object {
        const val VIDEO_ROW = "720p · HD"
        const val MP3_ROW = "MP3 · 192 kbps"
        val WIFI = NetworkSnapshot(connected = true, validated = true, unmetered = true)
    }
}