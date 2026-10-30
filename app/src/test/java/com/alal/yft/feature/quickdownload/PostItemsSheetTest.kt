package com.alal.yft.feature.quickdownload

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.PostVideoItem
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.SiteLookupKey
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.DownloadOrigin
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.detectedmedia.PageVideoLookup
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * v1.0.2 item 51: a post with several video items (an Instagram carousel, an X post with more
 * videos) shows every item in the sheet, the linked one first and selected; each item is read,
 * named and downloaded as a video of its own.
 */
class PostItemsSheetTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val store = DetectedMediaStore()
    private val selection = PreviewSelectionStore()
    private val resolver = AnsweringResolver()
    private val starter = RecordingStarter()
    private val lookups = SiteLookupCache()

    @Test
    fun theLinkedItemComesFirstAndEachItemIsReadAndDownloadedOnItsOwn() = runTest {
        open(post())
        val sheet = sheet(merges = MergeSupport { it.audioCompanion == null })

        val first = sheet.uiState.value
        assertEquals(
            listOf("Video 3 of 4" to 3, "Video 1 of 4" to 1),
            first.postItems.map { it.label to it.number },
        )
        assertEquals("$KEY_PREFIX$POST", first.postItemKey)
        assertEquals("$TITLE (Video 3 of 4)", first.header?.title)
        assertEquals(listOf(640), first.choices!!.video.map { it.variant.height })

        sheet.selectPostItem("$KEY_PREFIX$POST:1")
        advanceUntilIdle()

        val other = sheet.uiState.value
        assertEquals("$KEY_PREFIX$POST:1", other.postItemKey)
        assertEquals("$TITLE (Video 1 of 4)", other.header?.title)
        assertEquals("https://cdn.example.test/pic-1.jpg", other.header?.thumbnailUrl)
        // Its merge this phone cannot make is not offered; its whole file is.
        assertEquals(listOf(1280), other.choices!!.video.map { it.variant.height })
        assertEquals(PreviewDownloadStatus.Idle, other.downloadStatus)

        sheet.download()
        advanceUntilIdle()
        sheet.selectPostItem("$KEY_PREFIX$POST")
        advanceUntilIdle()
        assertEquals(listOf(640), sheet.uiState.value.choices!!.video.map { it.variant.height })
        sheet.download()
        advanceUntilIdle()

        assertEquals(
            listOf("$TITLE (Video 1 of 4)", "$TITLE (Video 3 of 4)"),
            starter.assets.map { it.title },
        )
        assertEquals(
            listOf("$POST:1", POST),
            starter.origins.map { it?.candidate?.videoId?.removePrefix("instagram:") },
        )
        assertTrue(starter.origins.all { it?.candidate?.pageUrl == PAGE })
    }

    @Test
    fun aBrokenDownloadOfAnotherItemForgetsThePostsLookup() = runTest {
        val key = SiteLookupKey("instagram", POST, session = false)
        lookups.put(key, SiteAdapterOutcome.Detected("instagram", post()), nowEpochMs = 1L)
        open(post())
        val sheet = sheet()

        sheet.selectPostItem("$KEY_PREFIX$POST:1")
        sheet.download()
        advanceUntilIdle()
        assertNotNull(lookups.get(key, 2L))
        lookups.forgetDownload("task-1")

        assertNull("the lookup that found the item goes with its links", lookups.get(key, 2L))
    }

    @Test
    fun downloadAsNamesThePostsItemAsTheSheetDoes() = runTest {
        open(post())
        val sheet = sheet()

        sheet.selectPostItem("$KEY_PREFIX$POST:1")
        assertTrue(sheet.openDetails())

        assertEquals("$TITLE (Video 1 of 4)", selection.selection.value?.title)
        assertEquals("instagram:$POST:1", selection.selection.value?.videoId)
    }

    @Test
    fun theItemStaysWhileADownloadAsksAboutMobileData() = runTest {
        open(post())
        val sheet = sheet(
            preferences = DownloadPreferences(confirmOnMeteredNetwork = true),
            network = NetworkSnapshot(connected = true, validated = true, unmetered = false),
        )

        sheet.download()
        advanceUntilIdle()
        assertEquals(PreviewDownloadStatus.ConfirmMetered, sheet.uiState.value.downloadStatus)
        sheet.selectPostItem("$KEY_PREFIX$POST:1")

        assertEquals("$KEY_PREFIX$POST", sheet.uiState.value.postItemKey)
        assertEquals("$TITLE (Video 3 of 4)", sheet.uiState.value.header?.title)
    }

    @Test
    fun aSheetThatWaitedOnTheLookupOffersThePostsItemsOnceItAnswers() = runTest {
        store.awaitPageVideo()
        store.showLookup(PageVideoLookup("instagram:$POST", PAGE, title = null, owner = LookupOwner.HOME))
        val sheet = sheet()
        assertTrue(sheet.uiState.value.postItems.isEmpty())

        open(post())
        store.clearLookup(LookupOwner.HOME)
        advanceUntilIdle()

        assertEquals(
            listOf("Video 3 of 4", "Video 1 of 4", "Video 4 of 4"),
            sheet.uiState.value.postItems.map { it.label },
        )
        assertEquals("$KEY_PREFIX$POST", sheet.uiState.value.postItemKey)
    }

    @Test
    fun aVideoOfItsOwnShowsNoItems() = runTest {
        open(listOf(row("https://cdn.example.test/solo.mp4", 720, POST, item = null)))

        val state = sheet().uiState.value

        assertTrue(state.postItems.isEmpty())
        assertNull(state.postItemKey)
        assertEquals(TITLE, state.header?.title)
    }

    /** The lookup's answer: item 3 of 4 linked; item 1 (a file, a merge), item 4 (a merge). */
    private fun post(): List<MediaCandidate> {
        val others = listOf(
            row("https://cdn.example.test/v1_1280.mp4", 1280, "$POST:1", PostVideoItem(1, 4), CANONICAL),
            row("https://cdn.example.test/v1_720.mp4", 720, "$POST:1", PostVideoItem(1, 4), CANONICAL, merged = true),
            row("https://cdn.example.test/v4_480.mp4", 480, "$POST:5", PostVideoItem(4, 4), CANONICAL, merged = true),
        )
        return listOf(row("https://cdn.example.test/v3_640.mp4", 640, POST, PostVideoItem(3, 4, others)))
    }

    private fun row(
        url: String,
        height: Int,
        id: String,
        item: PostVideoItem?,
        page: String = PAGE,
        merged: Boolean = false,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        title = TITLE,
        thumbnailUrl = "https://cdn.example.test/pic-${id.substringAfter(':', "0")}.jpg",
        durationMillis = 5_000L,
        contentLengthBytes = 3L * 1024 * 1024,
        drmHint = false,
        codecs = if (merged) listOf("avc1.4d401f") else emptyList(),
        audioCompanion = if (merged) QuickDownloadFixtures.companion() else null,
        videoId = "instagram:$id",
        width = height,
        height = height,
        postItem = item,
    )

    /** The lookup answered: its rows are the page's, their video the sheet's. */
    private fun open(rows: List<MediaCandidate>) {
        store.publish(PAGE, TITLE, rows, adapterSite = true, owner = LookupOwner.HOME)
        store.select(MediaGroups.of(rows).single())
    }

    private fun sheet(
        merges: MergeSupport = MergeSupport { true },
        preferences: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        network: NetworkSnapshot = NetworkSnapshot(connected = true, validated = true, unmetered = true),
    ) = QuickDownloadViewModel(
        store = store,
        selectionStore = selection,
        resolver = resolver,
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
        lookups = lookups,
        merges = merges,
    )

    private class RecordingStarter : PreviewDownloadStarter {
        val assets = mutableListOf<MediaAsset>()
        val origins = mutableListOf<DownloadOrigin?>()

        override suspend fun enqueue(
            asset: MediaAsset,
            variant: MediaVariant,
            origin: DownloadOrigin?,
        ): EnqueueResult {
            origins += origin
            return enqueue(asset, variant)
        }

        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
            assets += asset
            return EnqueueResult.Started("task-${assets.size}", "${asset.title}.mp4")
        }
    }

    private companion object {
        const val POST = "C9fixtMIXD1"
        const val PAGE = "https://www.instagram.com/p/C9fixtMIXD1/?img_index=4"
        const val CANONICAL = "https://www.instagram.com/p/C9fixtMIXD1/"
        const val TITLE = "Four moments from the beach day"
        const val KEY_PREFIX = "id:instagram:"
    }
}
