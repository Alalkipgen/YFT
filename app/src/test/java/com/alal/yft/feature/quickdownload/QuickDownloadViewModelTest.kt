package com.alal.yft.feature.quickdownload

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Conversion
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.SiteLookupKey
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.detectedmedia.PageVideoLookup
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.video
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.thumbnail.DownloadThumbnails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickDownloadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val store = DetectedMediaStore()
    private val selection = PreviewSelectionStore()
    private val resolver = FakeResolver()
    private val starter = FakeStarter()
    private val lookups = SiteLookupCache()
    private val saved = mutableListOf<Pair<String, String>>()
    private val thumbnails = object : DownloadThumbnails by DownloadThumbnails.None {
        override fun saveFrom(downloadId: String, thumbnailUrl: String) {
            saved += downloadId to thumbnailUrl
        }
    }

    @Test
    fun theDefaultQualityPreselectsARowWithoutARequestForStatedFiles() = runTest {
        select(QuickDownloadFixtures.youtube())

        val state = viewModel().uiState.value
        // P9: 720p is the unset preference; an explicitly saved Highest still stays.
        assertEquals(QualityPreference.UP_TO_720P, state.defaultQuality)
        assertEquals("720p · HD", state.selectedOption?.title)
        assertEquals(
            "1080p · Full HD",
            viewModel(DownloadPreferences(defaultQuality = QualityPreference.HIGHEST))
                .uiState.value.selectedOption?.title,
        )
        assertEquals("Ocean waves", state.header?.title)
        assertEquals("youtube.com", state.header?.source)
        assertFalse(state.loading)
        // Every file stated its type, size and height: nothing was requested to show them.
        assertTrue(resolver.requested.isEmpty())
        assertEquals(
            "480p",
            viewModel(DownloadPreferences(defaultQuality = QualityPreference.UP_TO_480P))
                .uiState.value.selectedOption?.title,
        )
    }

    @Test
    fun aMergedYoutubeRowIsQueuedWithItsAudioCompanionAndNamedAfterItsQuality() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        viewModel.download()
        advanceUntilIdle()

        val resolved = resolver.requested.single()
        assertNotNull(resolved.audioCompanion)
        val queued = starter.variants.single()
        assertNotNull(queued.audioCompanion)
        assertEquals("720p", queued.label)
        assertEquals(720, queued.height)
        assertEquals("Ocean waves", starter.assets.single().title)
        assertEquals(
            PreviewDownloadStatus.Queued("Ocean waves.mp4"),
            viewModel.uiState.value.downloadStatus,
        )
    }

    @Test
    fun aFourKRowThePhoneMayNotPlayIsWarnedAboutAndStillDownloads() = runTest {
        // P6: the phone's decoder list decides; the file itself is fine, so it stays offered.
        val fourK = video(2160, 300 * MIB, merged = true).copy(
            mediaUrl = "https://media.example.test/video-2160.webm",
            mimeType = "video/webm",
            codecs = listOf("vp9"),
        )
        select(listOf(fourK) + QuickDownloadFixtures.youtube())
        val viewModel = viewModel(playback = { variant -> "vp9" !in variant.codecs })

        val rows = viewModel.uiState.value.choices!!.video
        assertEquals("2160p · 4K", rows.first().title)
        assertEquals(listOf(QuickDownloadChoices.MAY_NOT_PLAY), rows.first().chips)
        assertTrue(rows.drop(1).all { it.chips.isEmpty() })
        assertEquals("720p · HD", viewModel.uiState.value.selectedOption?.title)
        viewModel.select(rows.first().id)
        viewModel.download()
        advanceUntilIdle()
        assertEquals("2160p", starter.variants.single().label)
        assertEquals(listOf("vp9"), starter.variants.single().codecs)
    }

    @Test
    fun theMp3RowQueuesTheM4aConvertedToMp3() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        val mp3 = viewModel.uiState.value.choices!!.audio.single { it.title == "MP3 · 192 kbps" }
        viewModel.select(mp3.id)
        viewModel.download()
        advanceUntilIdle()

        assertEquals(
            "https://media.example.test/audio-128.m4a",
            resolver.requested.single().mediaUrl,
        )
        val queued = starter.variants.single()
        assertEquals(Mp3Conversion(192, "direct-0"), queued.mp3)
        assertEquals("audio/mpeg", queued.mimeType)
        assertEquals("https://media.example.test/audio-128.m4a", queued.playbackUrl)
    }

    @Test
    fun musicOfAnMp4OnlyVideoQueuesTheVideosSoundKeptAsM4a() = runTest {
        val file = video(360, 11 * MIB, videoId = "facebook:1")
        select(listOf(file))
        val viewModel = viewModel()

        val m4a = viewModel.uiState.value.choices!!.audio.first()
        assertEquals("The video's own sound", m4a.detail)
        viewModel.select(m4a.id)
        viewModel.download()
        advanceUntilIdle()

        val queued = starter.variants.single()
        assertTrue(queued.audioFromVideo)
        assertEquals(MediaTrackType.AUDIO, queued.trackType)
        assertEquals("audio/mp4", queued.mimeType)
        assertEquals(file.mediaUrl, queued.playbackUrl)
        assertNull(queued.mp3)
    }

    @Test
    fun aGenericVideoWithoutAHeightIsMeasuredBeforeFormatsAreShown() = runTest {
        val hd = video(null, 25 * MIB, label = null, videoId = null, index = 1)
        resolver.heights[hd.mediaUrl] = 720
        select(listOf(hd))

        val state = viewModel().uiState.value

        assertEquals(listOf(hd), resolver.requested)
        val hdRow = state.choices!!.video.single()
        assertEquals("720p · HD", hdRow.title)
        assertEquals("720p · MP4", hdRow.detail)
        assertEquals("25 MB", hdRow.size)
        assertEquals(hdRow.id, state.selectedId)
    }

    @Test
    fun aSitesHdAndSdFilesAreRenamedInPlaceOnceMeasuredAndOfferTheirSound() = runTest {
        // P25: Facebook's HD/SD files state no height, codec or size. Their size check reads
        // the pictures: the rows take their heights' names without moving, and their sound is
        // offered as M4A and MP3 although no codec was stated.
        val files = QuickDownloadFixtures.facebookFiles()
        resolver.heights[files[0].mediaUrl] = 720
        resolver.heights[files[1].mediaUrl] = 360
        resolver.sizes[files[0].mediaUrl] = 25 * MIB
        resolver.sizes[files[1].mediaUrl] = 9 * MIB
        select(files)
        val stated = QuickDownloadChoices.of(
            QuickDownloadFixtures.group(files),
            files.map { SheetSource(it, QuickDownloadViewModel.stated(it), resolved = false) },
        )!!

        val state = viewModel().uiState.value

        val choices = state.choices!!
        assertEquals(listOf("HD", "SD"), stated.video.map { it.title })
        assertEquals(listOf("720p · HD", "360p"), choices.video.map { it.title })
        assertEquals(stated.options.map { it.id }, choices.options.map { it.id })
        assertEquals(listOf("25 MB", "9 MB"), choices.video.map { it.size })
        assertEquals(
            listOf("M4A", "MP3 · 320 kbps", "MP3 · 192 kbps", "MP3 · 128 kbps"),
            choices.audio.map { it.title },
        )
        assertEquals(stated.video.first().id, state.selectedId)
        assertEquals(files.toSet(), resolver.requested.toSet())
    }

    @Test
    fun aListOfQualitiesThatCannotBeReadIsNotANetworkProblem() = runTest {
        // P24: an exception from reading a page's manifest used to say "could not be reached".
        val stream = video(null, 25 * MIB, label = null, videoId = null, index = 1)
        resolver.thrown = NumberFormatException("For input string: \"fixture\"")
        select(listOf(stream))
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.choices)
        assertEquals(
            "The site's list of qualities could not be read.",
            viewModel.uiState.value.failure,
        )

        // A connection problem is still one.
        resolver.thrown = java.net.SocketTimeoutException("fixture timeout")
        viewModel.retry()
        assertEquals(
            "The media could not be reached. Check the connection and try again.",
            viewModel.uiState.value.failure,
        )
    }

    @Test
    fun aRefusedVideoSaysSoAndItsDetailsNameTheStepTheHostAndTheStatus() = runTest {
        // P24: the sheet that could not read the qualities.
        val stream = video(null, 25 * MIB, label = null, videoId = null, index = 1)
        resolver.answer = {
            VariantResolutionResult.Failure(
                VariantResolutionFailure.HTTP_STATUS,
                httpStatusCode = 403,
                step = ResolutionStep.MANIFEST,
                host = "stream.example.test",
            )
        }
        select(listOf(stream))
        val viewModel = viewModel()
        assertEquals("The site refused this video (HTTP 403).", viewModel.uiState.value.failure)
        assertEquals(
            listOf(
                "Step: list of qualities (manifest)",
                "Host: stream.example.test",
                "Status: HTTP 403",
            ),
            viewModel.uiState.value.failureDetails,
        )

        // A page-built address is not fetched at all.
        resolver.answer = null
        val requests = resolver.requested.size
        select(listOf(stream.copy(mediaUrl = "blob:https://videos.example.test/5b1c")))
        val blob = viewModel()
        assertEquals(requests, resolver.requested.size)
        assertEquals(
            "This video's address can't be downloaded. Try another video on the page.",
            blob.uiState.value.failure,
        )
        assertEquals(
            listOf("Step: video address", "Status: address not supported"),
            blob.uiState.value.failureDetails,
        )

        // At Download, another site's video says why, with its Details; a site's video still
        // offers its other qualities.
        val hd = video(720, 25 * MIB, videoId = null, index = 2)
        select(listOf(hd))
        val sheet = viewModel()
        assertTrue(sheet.uiState.value.failureDetails.isEmpty())
        resolver.answer = {
            VariantResolutionResult.Failure(
                VariantResolutionFailure.HTTP_STATUS,
                httpStatusCode = 404,
                step = ResolutionStep.FILE_CHECK,
                host = "media.example.test",
            )
        }
        sheet.download()
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Rejected("The site no longer has this video (HTTP 404)."),
            sheet.uiState.value.downloadStatus,
        )
        assertEquals(
            listOf("Step: file check", "Host: media.example.test", "Status: HTTP 404"),
            sheet.uiState.value.downloadDetails,
        )
        assertTrue(starter.variants.isEmpty())
    }

    @Test
    fun aFailedLookupSaysWhyAndTryAgainReadsTheFormatsAgain() = runTest {
        val hd = video(null, 25 * MIB, label = null, videoId = null, index = 1)
        resolver.failure = VariantResolutionFailure.NETWORK
        select(listOf(hd))
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.choices)
        assertEquals(
            "The media could not be reached. Check the connection and try again.",
            viewModel.uiState.value.failure,
        )
        assertEquals("Ocean waves", viewModel.uiState.value.header?.title)

        resolver.failure = null
        viewModel.retry()

        assertNotNull(viewModel.uiState.value.choices)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun anyRowOfEitherSectionCanBeChosenAndAnUnknownIdIsIgnored() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()
        val choices = viewModel.uiState.value.choices!!
        val lowest = choices.video.last()
        val mp3 = choices.audio.last()

        viewModel.select(lowest.id)
        assertSame(lowest, viewModel.uiState.value.selectedOption)
        assertEquals("360p", lowest.title)
        viewModel.select("missing")
        assertSame(lowest, viewModel.uiState.value.selectedOption)
        viewModel.select(mp3.id)
        assertSame(mp3, viewModel.uiState.value.selectedOption)
        assertEquals("MP3 · 128 kbps", mp3.title)
    }

    @Test
    fun detailsHandsTheChosenFormatsFileToDownloadAs() = runTest {
        val candidates = QuickDownloadFixtures.youtube()
        select(candidates)
        val viewModel = viewModel()

        viewModel.select(viewModel.uiState.value.choices!!.video.single { it.title == "480p" }.id)

        assertTrue(viewModel.openDetails())
        assertSame(candidates[2], selection.selection.value)
    }

    @Test
    fun mobileDataAsksFirstAndWifiOnlyWaits() = runTest {
        select(QuickDownloadFixtures.youtube())
        val asking = viewModel(DownloadPreferences(confirmOnMeteredNetwork = true), MOBILE)

        val choices = asking.uiState.value.choices!!
        val m4a = choices.audio.first()
        asking.select(m4a.id)
        asking.download()
        advanceUntilIdle()
        assertEquals(PreviewDownloadStatus.ConfirmMetered, asking.uiState.value.downloadStatus)
        assertTrue(starter.variants.isEmpty())
        asking.select(choices.video.last().id)
        assertEquals(m4a.id, asking.uiState.value.selectedId)

        asking.confirmMeteredDownload()
        advanceUntilIdle()
        assertEquals(
            "https://media.example.test/audio-128.m4a",
            resolver.requested.single().mediaUrl,
        )

        val waiting = viewModel(
            DownloadPreferences(confirmOnMeteredNetwork = false, unmeteredOnly = true),
            MOBILE,
        )
        waiting.download()
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Queued("Ocean waves.mp4", waitingForUnmetered = true),
            waiting.uiState.value.downloadStatus,
        )
    }

    @Test
    fun aResolverFailureOnDownloadIsShownAndNothingIsQueued() = runTest {
        select(QuickDownloadFixtures.youtube())
        resolver.failure = VariantResolutionFailure.EXPIRED_URL
        val viewModel = viewModel()

        viewModel.download()
        advanceUntilIdle()

        assertEquals(
            PreviewDownloadStatus.Rejected("This quality is not available now — choose another"),
            viewModel.uiState.value.downloadStatus,
        )
        assertTrue(starter.variants.isEmpty())
    }

    @Test
    fun theChosenVideoWinsOverThePageAndSeveralUnchosenVideosShowNothing() = runTest {
        val one = video(720, 42 * MIB, videoId = "youtube:one", title = "One", index = 1)
        val two = video(720, 40 * MIB, videoId = "youtube:two", title = "Two", index = 2)
        store.publish(QuickDownloadFixtures.PAGE, "Page", listOf(one, two))

        val nothing = viewModel()
        assertNull(nothing.uiState.value.header)
        nothing.download()
        assertEquals(PreviewDownloadStatus.Idle, nothing.uiState.value.downloadStatus)

        store.select(MediaGroups.of(listOf(one, two)).last())
        assertEquals("Two", viewModel().uiState.value.header?.title)
    }

    @Test
    fun aSheetOpenedDuringThePageLookupWaitsForThatLookupAndShowsItsVideo() = runTest {
        // P12: the Download button was tapped on a watch page before its lookup ended.
        store.publish(QuickDownloadFixtures.PAGE, null, emptyList(), adapterSite = true)
        val lookup = PageVideoLookup(KEY, QuickDownloadFixtures.PAGE, title = null)
        store.showLookup(lookup)
        store.awaitPageVideo()

        val viewModel = viewModel()
        val waiting = viewModel.uiState.value
        assertTrue(waiting.loading)
        assertTrue(waiting.findingVideo)
        assertEquals("youtube.com", waiting.header?.source)
        assertNull(waiting.choices)

        // The same lookup ends: the browser selects its video and the sheet reads it.
        val found = QuickDownloadFixtures.youtube()
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", found, adapterSite = true)
        store.select(MediaGroups.of(found).single())
        store.showLookup(null)
        val shown = viewModel.uiState.value
        assertFalse(shown.findingVideo)
        assertEquals("Ocean waves", shown.header?.title)
        assertEquals("720p · HD", shown.selectedOption?.title)
    }

    @Test
    fun aSheetOpenedOnALinkShowsTheLinkThenItsRowsAndClosingItEarlyStopsTheLookup() = runTest {
        // P16: the browser's last page has a video of its own; Home's link is another video.
        val feed = video(720, 42 * MIB, title = "Feed clip", videoId = null, index = 9)
        store.publish("https://m.youtube.com/", "YouTube", listOf(feed))
        store.awaitPageVideo()
        val lookup = PageVideoLookup(
            KEY,
            QuickDownloadFixtures.PAGE,
            title = null,
            owner = LookupOwner.HOME,
        )
        store.showLookup(lookup)
        val closes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.lookupCloses.collect { closes += it }
        }

        val sheets = ViewModelStore()
        val viewModel = sheet(sheets)
        val waiting = viewModel.uiState.value
        assertEquals("youtube.com/watch?v=fixture0001", waiting.header?.title)
        assertEquals("youtube.com", waiting.header?.source)
        assertEquals(YOUTUBE_PICTURE, waiting.header?.thumbnailUrl)
        assertTrue(waiting.loading)
        assertNull(waiting.choices)
        // Plan adapted (P18): Download is ready while it waits; it queues the Default quality.
        assertTrue(waiting.canDownload)

        // The lookup answers: the same sheet fills in with the video's rows.
        val found = QuickDownloadFixtures.youtube()
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", found, adapterSite = true)
        store.select(MediaGroups.of(found).single())
        store.clearLookup(LookupOwner.HOME)
        assertEquals("Ocean waves", viewModel.uiState.value.header?.title)
        assertEquals("720p · HD", viewModel.uiState.value.selectedOption?.title)
        sheets.clear()
        assertTrue(closes.isEmpty())

        // Closed before its video came: the lookup it waited on is stopped.
        store.awaitPageVideo()
        store.showLookup(lookup)
        val early = ViewModelStore()
        assertTrue(sheet(early).uiState.value.loading)
        early.clear()
        assertEquals(listOf(KEY), closes)
    }

    @Test
    fun aFailedPageLookupShowsItsMessageWithTryAgainInTheSheet() = runTest {
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", emptyList(), adapterSite = true)
        store.showLookup(
            PageVideoLookup(
                KEY,
                QuickDownloadFixtures.PAGE,
                title = "Ocean waves",
                failure = "The site did not answer. Try again.",
                canRetry = true,
            ),
        )
        val retries = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.lookupRetries.collect { retries += it }
        }

        val viewModel = viewModel()
        val failed = viewModel.uiState.value
        assertFalse(failed.loading)
        assertEquals("The site did not answer. Try again.", failed.failure)
        assertTrue(failed.canRetry)
        assertEquals("Ocean waves", failed.header?.title)

        // Try again asks the browser for the same video; the sheet waits for it again.
        viewModel.retry()
        assertEquals(listOf(KEY), retries)
        assertTrue(viewModel.uiState.value.loading)
        store.showLookup(PageVideoLookup(KEY, QuickDownloadFixtures.PAGE, "Ocean waves"))
        assertTrue(viewModel.uiState.value.findingVideo)

        // A protected video: the message, and no Try again.
        store.showLookup(
            PageVideoLookup(KEY, QuickDownloadFixtures.PAGE, null, failure = "Protected"),
        )
        assertFalse(viewModel.uiState.value.canRetry)
        viewModel.retry()
        assertEquals(listOf(KEY), retries)
    }

    @Test
    fun anAdapterSiteNeverOffersItsPlayersFilesAndTheMainVideoCountsTheOthers() = runTest {
        // P12: unnamed files on a site's page are its player's, not the page's video.
        val file = video(720, 42 * MIB, title = "Player part", videoId = null, index = 1)
        store.publish(QuickDownloadFixtures.PAGE, "Page", listOf(file), adapterSite = true)
        assertNull(viewModel().uiState.value.header)
        store.publish(QuickDownloadFixtures.PAGE, "Page", listOf(file))
        assertNotNull(viewModel().uiState.value.header)

        // A generic page's main video with three more: the row and the list request.
        store.select(MediaGroups.of(listOf(file)).single(), otherVideos = 3)
        val main = viewModel()
        assertEquals(3, main.uiState.value.otherVideos)
        var lists = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.foundList.collect { lists++ }
        }
        assertTrue(main.openOtherVideos())
        assertEquals(1, lists)
    }

    @Test
    fun everyStatedRowIsVisibleBeforeSizeChecksFinishAndOnlyTwoRunAtOnce() = runTest {
        val candidates = listOf(1080, 720, 480, 360).map {
            video(it, videoId = "facebook:fixture")
        }
        val gates = candidates.associate { it.mediaUrl to CompletableDeferred<Unit>() }
        var active = 0
        var maximum = 0
        resolver.beforeResolve = { candidate ->
            active++
            maximum = maxOf(maximum, active)
            try { gates.getValue(candidate.mediaUrl).await() } finally { active-- }
        }
        candidates.forEach { resolver.sizes[it.mediaUrl] = 5 * MIB }
        select(candidates)
        val viewModel = viewModel()
        val first = viewModel.uiState.value.choices!!
        assertEquals(listOf(1080, 720, 480, 360), first.video.map { it.rankHeight })
        assertEquals(2, resolver.requested.size)
        assertFalse(viewModel.uiState.value.loading)
        val selected = viewModel.uiState.value.selectedId
        gates.values.forEach { it.complete(Unit) }
        advanceUntilIdle()
        val after = viewModel.uiState.value.choices!!
        assertEquals(first.options.map { it.id }, after.options.map { it.id })
        assertEquals(first.options.map { it.title }, after.options.map { it.title })
        assertEquals(selected, viewModel.uiState.value.selectedId)
        assertTrue(after.video.all { it.size == "5 MB" })
        assertEquals(2, maximum)
    }

    @Test
    fun aFailedSizeCheckKeepsTheQualityAndADownloadOnADeadLinkQueuesNothing() = runTest {
        val candidate = video(720, videoId = "facebook:fixture")
        resolver.failure = VariantResolutionFailure.HTTP_STATUS
        resolver.failureStatus = 404
        select(listOf(candidate))
        val viewModel = viewModel()
        assertEquals("720p · HD", viewModel.uiState.value.choices!!.video.single().title)
        assertNull(viewModel.uiState.value.choices!!.video.single().size)
        assertTrue(viewModel.uiState.value.canDownload)
        viewModel.download()
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Rejected("This quality is not available now — choose another"),
            viewModel.uiState.value.downloadStatus,
        )
        assertTrue(starter.variants.isEmpty())
        // One background HEAD, one final check; a 404 is never retried.
        assertEquals(2, resolver.requested.size)
        assertEquals(1, viewModel.uiState.value.choices!!.video.size)
    }

    @Test
    fun aFinalTransientFailureRetriesButAHealthyRowIsNeverReplaced() = runTest {
        select(QuickDownloadFixtures.youtube())
        resolver.beforeResolve = {
            resolver.failure = if (resolver.requested.size == 1) VariantResolutionFailure.NETWORK
                else null
        }
        val viewModel = viewModel()
        val selected = viewModel.uiState.value.selectedId
        viewModel.download()
        advanceUntilIdle()
        assertEquals(2, resolver.requested.size)
        assertEquals(1, starter.variants.size)
        assertEquals(selected, viewModel.uiState.value.selectedId)
        assertEquals("720p", starter.variants.single().label)
    }

    @Test
    fun theHeaderNamesTheVideosPictureAndAStartedDownloadSavesIt() = runTest {
        // P19: a YouTube picture is known from the video's ID while the page lookup runs.
        store.publish(QuickDownloadFixtures.PAGE, null, emptyList(), adapterSite = true)
        store.showLookup(PageVideoLookup(KEY, QuickDownloadFixtures.PAGE, title = null))
        val waiting = viewModel()
        assertEquals(YOUTUBE_PICTURE, waiting.uiState.value.header?.thumbnailUrl)
        store.showLookup(null)

        // Other sites: the picture the lookup found, HTTPS only.
        val reel = QuickDownloadFixtures.youtube().map {
            it.copy(videoId = "facebook:1", thumbnailUrl = "https://scontent.example.test/t.jpg")
        }
        select(reel)
        assertEquals(
            "https://scontent.example.test/t.jpg",
            viewModel().uiState.value.header?.thumbnailUrl,
        )
        select(reel.map { it.copy(thumbnailUrl = "http://scontent.example.test/t.jpg") })
        assertNull(viewModel().uiState.value.header?.thumbnailUrl)

        // Download: the started task keeps the video's picture for Downloads.
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()
        assertEquals(YOUTUBE_PICTURE, viewModel.uiState.value.header?.thumbnailUrl)
        viewModel.download()
        advanceUntilIdle()
        assertEquals(listOf("task-1" to YOUTUBE_PICTURE), saved)
    }

    @Test
    fun linksThatStoppedWorkingDropTheVideosRememberedLookup() = runTest {
        // P17: after a 403 or 410 the next lookup of this video asks the site again.
        val candidate = video(720, videoId = "facebook:fixture")
        val key = SiteLookupKey("facebook", "fixture", session = false)
        val answer = SiteAdapterOutcome.Detected("facebook", listOf(candidate))
        lookups.put(key, answer, LOOKED_UP_AT)
        resolver.failure = VariantResolutionFailure.HTTP_STATUS
        resolver.failureStatus = 404
        select(listOf(candidate))
        viewModel().download()
        advanceUntilIdle()
        // A missing quality is not the video's links expiring: the answer stays.
        assertNotNull(lookups.get(key, LOOKED_UP_AT))

        resolver.failureStatus = 403
        viewModel().download()
        advanceUntilIdle()
        assertNull(lookups.get(key, LOOKED_UP_AT))

        // The download engine refusing the link does the same.
        resolver.failure = null
        lookups.put(key, answer, LOOKED_UP_AT)
        starter.rejection = EnqueueResult.Rejected(DownloadFailureReason.GONE, "Link expired")
        viewModel().download()
        advanceUntilIdle()
        assertNull(lookups.get(key, LOOKED_UP_AT))

        // A started download is remembered, so its later 403 drops the answer too.
        starter.rejection = null
        lookups.put(key, answer, LOOKED_UP_AT)
        viewModel().download()
        advanceUntilIdle()
        assertNotNull(lookups.get(key, LOOKED_UP_AT))
        lookups.forgetDownload("task-${starter.variants.size}")
        assertNull(lookups.get(key, LOOKED_UP_AT))
    }

    @Test
    fun aDownloadTappedWhileTheSheetWaitsStartsWithTheDefaultQualityOnceTheRowsCome() = runTest {
        // P18: Download works while the sheet still says "Getting qualities…" (was disabled).
        val viewModel = waitingSheet()
        val waiting = viewModel.uiState.value
        assertTrue(waiting.waitsForQualities)
        assertTrue(waiting.canDownload)
        assertEquals(OptionSection.VIDEO, waiting.earlySection)
        viewModel.download()
        val queued = viewModel.uiState.value
        assertTrue(queued.startsWhenReady)
        assertFalse(queued.canDownload)
        assertEquals(PreviewDownloadStatus.Idle, queued.downloadStatus)
        assertTrue(starter.variants.isEmpty())

        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        val started = viewModel.uiState.value
        assertEquals("720p", starter.variants.single().label)
        assertEquals("720p · HD", started.selectedOption?.title)
        assertEquals("Downloading 720p", started.startedNote)
        assertEquals(PreviewDownloadStatus.Queued("Ocean waves.mp4"), started.downloadStatus)
        assertFalse(started.startsWhenReady)
    }

    @Test
    fun anEarlyDownloadTakesTheNearestLowerElseTheNearestHigherQualityOrTheAudio() = runTest {
        val lower = waitingSheet()
        lower.download()
        answer(listOf(video(1080, 80 * MIB), video(480, 18 * MIB), video(360, 11 * MIB)))
        advanceUntilIdle()
        assertEquals("480p", starter.variants.last().label)
        assertEquals("Downloading 480p — 720p not available", lower.uiState.value.startedNote)

        val higher = waitingSheet()
        higher.download()
        answer(listOf(video(1080, 80 * MIB)))
        advanceUntilIdle()
        assertEquals("1080p", starter.variants.last().label)
        assertEquals("Downloading 1080p — 720p not available", higher.uiState.value.startedNote)

        // The user's own Default quality, there exactly.
        val own = waitingSheet(
            DownloadPreferences(
                defaultQuality = QualityPreference.UP_TO_480P,
                confirmOnMeteredNetwork = false,
            ),
        )
        assertEquals(QualityPreference.UP_TO_480P, own.uiState.value.defaultQuality)
        own.download()
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals("480p", starter.variants.last().label)
        assertEquals("Downloading 480p", own.uiState.value.startedNote)

        // The Audio placeholder picked: the M4A.
        val audio = waitingSheet()
        audio.pickEarly(OptionSection.AUDIO)
        assertEquals(OptionSection.AUDIO, audio.uiState.value.earlySection)
        audio.download()
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals(MediaTrackType.AUDIO, starter.variants.last().trackType)
        assertEquals(OptionSection.AUDIO, audio.uiState.value.selectedOption?.section)
        assertEquals(4, starter.variants.size)
    }

    @Test
    fun aFailedLookupDropsTheEarlyDownloadAndClosingTheSheetCancelsIt() = runTest {
        val viewModel = waitingSheet()
        viewModel.download()
        assertTrue(viewModel.uiState.value.startsWhenReady)
        val failure = "The site did not answer. Try again."
        store.showLookup(LOOKUP.copy(failure = failure, canRetry = true))
        val failed = viewModel.uiState.value
        assertEquals(failure, failed.failure)
        assertFalse(failed.startsWhenReady)
        assertFalse(failed.canDownload)

        // Try again: the rows come, but nothing starts without a new tap.
        viewModel.retry()
        store.showLookup(LOOKUP)
        assertTrue(viewModel.uiState.value.canDownload)
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertTrue(starter.variants.isEmpty())
        assertEquals(PreviewDownloadStatus.Idle, viewModel.uiState.value.downloadStatus)
        assertNull(viewModel.uiState.value.startedNote)

        // Closing the sheet before the rows came cancels the queued Download.
        val sheets = ViewModelStore()
        val closing = waitingSheet(owner = sheets)
        closing.download()
        assertTrue(closing.uiState.value.startsWhenReady)
        sheets.clear()
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertTrue(starter.variants.isEmpty())
    }

    @Test
    fun anEarlyDownloadStillPassesTheMobileDataWiFiOnlyAndStorageChecks() = runTest {
        // Mobile data is asked about at the tap: yes queues it and the rows start it.
        val ask = DownloadPreferences(confirmOnMeteredNetwork = true)
        val mobile = waitingSheet(ask, MutableStateFlow(MOBILE))
        mobile.download()
        assertEquals(PreviewDownloadStatus.ConfirmMetered, mobile.uiState.value.downloadStatus)
        mobile.confirmMeteredDownload()
        assertTrue(mobile.uiState.value.startsWhenReady)
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals(1, starter.variants.size)
        assertTrue(mobile.uiState.value.downloadStatus is PreviewDownloadStatus.Queued)

        // No drops it.
        val declined = waitingSheet(ask, MutableStateFlow(MOBILE))
        declined.download()
        declined.dismissMeteredDownload()
        assertFalse(declined.uiState.value.startsWhenReady)
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals(1, starter.variants.size)

        // Wi-Fi went between the tap and the rows: asked then.
        val network = MutableStateFlow(WIFI)
        val moved = waitingSheet(ask, network)
        moved.download()
        assertEquals(PreviewDownloadStatus.Idle, moved.uiState.value.downloadStatus)
        network.value = MOBILE
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals(PreviewDownloadStatus.ConfirmMetered, moved.uiState.value.downloadStatus)
        assertEquals(1, starter.variants.size)
        moved.confirmMeteredDownload()
        advanceUntilIdle()
        assertEquals(2, starter.variants.size)

        // Wi-Fi only waits for Wi-Fi, and the download engine's storage check still refuses.
        val wifiOnly = waitingSheet(
            DownloadPreferences(unmeteredOnly = true),
            MutableStateFlow(MOBILE),
        )
        wifiOnly.download()
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Queued("Ocean waves.mp4", waitingForUnmetered = true),
            wifiOnly.uiState.value.downloadStatus,
        )
        starter.rejection = EnqueueResult.Rejected(
            DownloadFailureReason.INSUFFICIENT_STORAGE,
            "Not enough free space",
        )
        val full = waitingSheet()
        full.download()
        answer(QuickDownloadFixtures.youtube())
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Rejected("Not enough free space"),
            full.uiState.value.downloadStatus,
        )
    }

    @Test
    fun theSheetWaitingForThePagesVideoShowsThePagesTitleAndPictureThenItsLine() = runTest {
        // P28: the browser waits a few seconds for the page's video after what may be its ad.
        val page = "https://tube.example.test/watch/77"
        val poster = "https://img.example.test/v77/poster.jpg"
        val facts = PageVideoFacts(984_000, "Harbour lights at dusk", poster)
        val ad = video(1080, videoId = null, title = null, label = null, page = page)
            .copy(durationMillis = 30_000)
        store.publish(page, "Harbour lights at dusk - Example Tube", listOf(ad), facts = facts)
        store.awaitPageVideo()
        store.showLookup(
            PageVideoLookup(
                key = "generic:page-video:1",
                pageUrl = page,
                title = facts.title,
                thumbnailUrl = poster,
                findingPageVideo = true,
            ),
        )

        val sheet = viewModel()
        val waiting = sheet.uiState.value
        assertTrue(waiting.findingPageVideo)
        assertTrue(waiting.loading)
        assertEquals("Harbour lights at dusk", waiting.header?.title)
        assertEquals(poster, waiting.header?.thumbnailUrl)
        assertFalse(waiting.maybeAd)

        // Nothing else came: the ad, with the page's title and picture and its line.
        store.select(MediaGroups.of(listOf(ad)).single(), maybeAd = true)
        store.clearLookup(LookupOwner.BROWSER)
        advanceUntilIdle()
        val shown = sheet.uiState.value
        assertTrue(shown.maybeAd)
        assertFalse(shown.findingPageVideo)
        assertEquals("Harbour lights at dusk", shown.header?.title)
        assertEquals(poster, shown.header?.thumbnailUrl)
        assertNotNull(shown.choices)
    }

    @Test
    fun aVideoWithoutTitleOrPictureTakesThePagesOwn() = runTest {
        // P28 step 6 / P29 step 3: every entry; the found list selects the bare group.
        val page = "https://tube.example.test/watch/77"
        val poster = "https://img.example.test/v77/poster.jpg"
        val stream = video(720, videoId = null, title = null, label = null, page = page)
        store.publish(
            page,
            null,
            listOf(stream),
            facts = PageVideoFacts(984_000, "Harbour lights at dusk", poster),
        )
        store.select(MediaGroups.of(listOf(stream)).single())

        val state = viewModel().uiState.value

        assertEquals("Harbour lights at dusk", state.header?.title)
        assertEquals(poster, state.header?.thumbnailUrl)
        assertFalse(state.maybeAd)
    }

    /** P16/P18: a sheet opened on Home's lookup of a link, before the video came. */
    private fun waitingSheet(
        preferences: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        network: StateFlow<NetworkSnapshot> = MutableStateFlow(WIFI),
        owner: ViewModelStore? = null,
    ): QuickDownloadViewModel {
        store.awaitPageVideo()
        store.showLookup(LOOKUP)
        return owner?.let(::sheet) ?: viewModel(preferences, snapshot = network)
    }

    /** The lookup answers with [found]: the video is selected and its lookup ends. */
    private fun answer(found: List<MediaCandidate>) {
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", found, adapterSite = true)
        store.select(MediaGroups.of(found).single())
        store.clearLookup(LookupOwner.HOME)
    }

    private fun select(candidates: List<MediaCandidate>) {
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", candidates)
        store.select(MediaGroups.of(candidates).single())
    }

    private fun viewModel(
        preferences: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        network: NetworkSnapshot = WIFI,
        playback: VideoPlaybackSupport = VideoPlaybackSupport.ANY,
        snapshot: StateFlow<NetworkSnapshot> = MutableStateFlow(network),
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
            override val snapshot: StateFlow<NetworkSnapshot> = snapshot
        },
        playback = playback,
        downloadThumbnails = thumbnails,
        lookups = lookups,
    )

    /** A sheet whose [ViewModelStore] the test clears, as closing the sheet does. */
    private fun sheet(owner: ViewModelStore): QuickDownloadViewModel = ViewModelProvider(
        owner,
        viewModelFactory { initializer { viewModel() } },
    )[QuickDownloadViewModel::class.java]

    /**
     * Resolves like the real resolver for a whole file: one variant keeping the companion and
     * the codecs, with the height its MP4 header would give ([heights]).
     */
    private class FakeResolver : VariantResolver {
        val requested = mutableListOf<MediaCandidate>()
        val heights = mutableMapOf<String, Int>()
        var failure: VariantResolutionFailure? = null
        var failureStatus: Int? = null

        /** P24: what the resolver throws instead of answering, like a parser that failed. */
        var thrown: Exception? = null

        /** P24: a whole answer for a candidate, when the test needs more than a reason. */
        var answer: ((MediaCandidate) -> VariantResolutionResult?)? = null
        val sizes = mutableMapOf<String, Long>()
        var beforeResolve: suspend (MediaCandidate) -> Unit = {}

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            requested += candidate
            beforeResolve(candidate)
            thrown?.let { throw it }
            answer?.invoke(candidate)?.let { return it }
            failure?.let {
                return VariantResolutionResult.Failure(it, httpStatusCode = failureStatus)
            }
            val variant = MediaVariant(
                id = "direct-0",
                playbackUrl = candidate.mediaUrl,
                kind = MediaKind.DIRECT,
                trackType = if (candidate.mimeType?.startsWith("audio/") == true) {
                    MediaTrackType.AUDIO
                } else {
                    MediaTrackType.AUDIO_VIDEO
                },
                requestContext = BrowserRequestContext(candidate.pageUrl, null, null),
                mimeType = candidate.mimeType,
                codecs = candidate.codecs,
                height = heights[candidate.mediaUrl],
                sizeBytes = sizes[candidate.mediaUrl] ?: candidate.contentLengthBytes,
                sizeAccuracy = (sizes[candidate.mediaUrl] ?: candidate.contentLengthBytes)?.let {
                    MediaSizeAccuracy.EXACT
                },
                audioCompanion = candidate.audioCompanion,
            )
            return VariantResolutionResult.Success(
                MediaAsset(
                    sourcePageUrl = candidate.pageUrl,
                    title = candidate.title,
                    thumbnailUrl = null,
                    durationMillis = candidate.durationMillis,
                    variants = listOf(variant),
                    resolvedAtEpochMs = 1,
                ),
            )
        }
    }

    private class FakeStarter : PreviewDownloadStarter {
        val variants = mutableListOf<MediaVariant>()
        val assets = mutableListOf<MediaAsset>()
        var rejection: EnqueueResult.Rejected? = null

        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
            assets += asset
            variants += variant
            rejection?.let { return it }
            return EnqueueResult.Started("task-${variants.size}", "Ocean waves.mp4")
        }
    }

    private companion object {
        val WIFI = NetworkSnapshot(connected = true, validated = true, unmetered = true)
        val MOBILE = NetworkSnapshot(connected = true, validated = true, unmetered = false)
        const val KEY = "youtube:fixture0001"
        const val LOOKED_UP_AT = 1L
        val LOOKUP = PageVideoLookup(
            KEY,
            QuickDownloadFixtures.PAGE,
            title = null,
            owner = LookupOwner.HOME,
        )
        const val YOUTUBE_PICTURE = "https://i.ytimg.com/vi/fixture0001/hqdefault.jpg"
    }
}
