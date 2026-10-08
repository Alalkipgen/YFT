package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.detection.PageReread
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.AD_LENGTH
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.MAIN_LENGTH
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.PAGE
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.PAGE_LENGTH
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.TITLE
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.gone
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.named
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.read
import com.alal.yft.feature.quickdownload.AdSheetHarness.Companion.requested
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * P43 (FIX_ADD_PLAN item 7): on a site without an adapter the sheet never offers the 0:30
 * pre-roll as the page's video. The page states 10:05; its player setup names an HLS and an MP4
 * whose links answer HTTP 410; the player fetched a 30-second MP4 from a host no list knows,
 * of a length nothing stated. Before P43 the fresh-link chain took that request as the
 * "player's link" and offered 0:30. These tests use only what the sheet had before P43, so
 * they also run against the code before it (the regression proof).
 */
class QuickDownloadPrerollTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val harness = AdSheetHarness()
    private val facts = PageVideoFacts(PAGE_LENGTH, TITLE, null)
    private val hls = named("https://media.example.test/v43/master.m3u8?hash=script", MediaKind.HLS)
    private val mp4 = named("https://media.example.test/v43/720.mp4?hash=script")
    private val ad = requested("https://cdn.clipnet.test/c/30s.mp4")

    private fun again(file: MediaCandidate) = file.copy(
        mediaUrl = file.mediaUrl.replace("hash=script", "hash=again"),
        sources = setOf(CandidateSource.PAGE_REREAD),
    )

    private fun QuickDownloadUiState.offered(): List<String> =
        choices?.options.orEmpty().map { it.source.candidate.mediaUrl }

    @Test
    fun theItem7PrerollIsSkippedAndThePageReadAgainGivesThePagesVideo() = runTest {
        val freshHls = again(hls)
        val freshMp4 = again(mp4)
        harness.resolver.answers += mapOf(
            hls.mediaUrl to gone(410),
            mp4.mediaUrl to gone(410),
            ad.mediaUrl to read(ad, AD_LENGTH),
            freshHls.mediaUrl to read(freshHls, MAIN_LENGTH),
            freshMp4.mediaUrl to read(freshMp4, MAIN_LENGTH),
        )
        harness.store.publish(PAGE, TITLE, listOf(hls, mp4, ad), facts = facts)
        harness.store.select(MediaGroups.of(listOf(hls, mp4)).single(), otherVideos = 1)
        val reader = QueuedPageReader(PageReread.Found(listOf(freshHls, freshMp4), facts))

        val sheet = harness.sheet(reader)
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertNull(state.failure)
        // Never the 0:30 pre-roll: the page's video from the links the page read again has.
        assertTrue(state.offered().toString(), state.offered().isNotEmpty())
        assertTrue(state.offered().toString(), state.offered().all { it.endsWith("hash=again") })
        assertEquals(MAIN_LENGTH, state.header?.durationMillis)
        assertTrue(state.freshLink)
        val details = state.attemptDetails
        assertTrue(details.toString(), "skipped: 0:30 ad (short)" in details)
        assertTrue(details.toString(), "chosen: named by the page's player" in details)
        assertTrue(details.toString(), "length 10:03 matches the page (10:05)" in details)
        assertEquals(
            listOf("Player's link", "skipped: 0:30 ad (short)", "Page read again"),
            details.dropWhile { it != "Player's link" }.take(3),
        )
        assertEquals(1, reader.asked.size)

        sheet.download()
        advanceUntilIdle()
        assertTrue(harness.starter.variants.single().playbackUrl.endsWith("hash=again"))
    }

    @Test
    fun aLoneFileOfUnknownLengthIsMeasuredAndAShortOneIsNeverOffered() = runTest {
        harness.resolver.answers[ad.mediaUrl] = read(ad, AD_LENGTH)
        harness.store.publish(PAGE, TITLE, listOf(ad), facts = facts)
        harness.store.select(MediaGroups.of(listOf(ad)).single())

        val sheet = harness.sheet()
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertNull(state.choices)
        assertEquals("Only an ad was found, not the page's video.", state.failure)
        assertEquals(listOf("skipped: 0:30 ad (short)"), state.failureDetails)
        assertTrue(state.canReload)
        assertFalse(state.loading)
        assertEquals(TITLE, state.header?.title)
        assertNull(state.header?.durationMillis)
        assertFalse(state.canDownload)
    }

    @Test
    fun aPrerollShownFirstGivesWayToThePagesNamedVideo() = runTest {
        val main = named("https://media.example.test/v43/720.mp4?hash=fresh")
        harness.resolver.answers += mapOf(
            ad.mediaUrl to read(ad, AD_LENGTH),
            main.mediaUrl to read(main, MAIN_LENGTH),
        )
        harness.store.publish(PAGE, TITLE, listOf(ad, main), facts = facts)
        // The browser selected what its player played: the pre-roll, maybe an ad (P28).
        harness.store.select(MediaGroups.of(listOf(ad)).single(), otherVideos = 1, maybeAd = true)

        val sheet = harness.sheet()
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertNull(state.failure)
        assertEquals(listOf(main.mediaUrl), state.offered().distinct())
        assertEquals(MAIN_LENGTH, state.header?.durationMillis)
        assertEquals(TITLE, state.header?.title)
        assertFalse(state.maybeAd)
        assertFalse(state.nextVideo)
        assertFalse(state.freshLink)
        assertEquals(0, state.otherVideos)
        assertEquals(
            listOf("First video", "skipped: 0:30 ad (short)", "Next video"),
            state.attemptDetails.take(3),
        )
        assertTrue(
            state.attemptDetails.toString(),
            "length 10:03 matches the page (10:05)" in state.attemptDetails,
        )
    }

    @Test
    fun thePlayersNewestRequestIsMeasuredAndItsPrerollSkippedForItsOwnVideo() = runTest {
        // The script's link is gone; the player asked for its video, then for the pre-roll.
        val player = requested("https://media.example.test/v43/hd.mp4?hash=player", at = 9)
        val preroll = ad.copy(observedAtEpochMs = 12)
        harness.resolver.answers += mapOf(
            mp4.mediaUrl to gone(410),
            preroll.mediaUrl to read(preroll, AD_LENGTH),
            player.mediaUrl to read(player, MAIN_LENGTH),
        )
        harness.store.publish(PAGE, TITLE, listOf(mp4, preroll, player), facts = facts)
        harness.store.select(MediaGroups.of(listOf(mp4)).single())

        val sheet = harness.sheet()
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertNull(state.failure)
        assertEquals(listOf(player.mediaUrl), state.offered().distinct())
        assertEquals(MAIN_LENGTH, state.header?.durationMillis)
        assertTrue(state.freshLink)
        assertEquals(
            listOf("Player's link", "skipped: 0:30 ad (short)", "Player's link"),
            state.attemptDetails.dropWhile { it != "Player's link" }.take(3),
        )
    }

    @Test
    fun aPageWithoutAdSignsShowsItsVideoAsBefore() = runTest {
        val main = named("https://media.example.test/v43/720.mp4?hash=fresh")
            .copy(durationMillis = MAIN_LENGTH)
        harness.store.publish(PAGE, TITLE, listOf(main), facts = facts)
        harness.store.select(MediaGroups.of(listOf(main)).single())

        val sheet = harness.sheet()
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertEquals(listOf(main.mediaUrl), state.offered().distinct())
        assertTrue(state.attemptDetails.isEmpty())
        assertFalse(state.freshLink)
        assertFalse(state.nextVideo)
        assertNull(state.failure)
    }
}
