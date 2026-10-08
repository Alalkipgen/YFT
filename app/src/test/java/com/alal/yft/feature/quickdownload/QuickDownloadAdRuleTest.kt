package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.AdRule
import com.alal.yft.core.model.media.AdSign
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.detection.PageReread
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.detectedmedia.PageReload
import com.alal.yft.feature.detectedmedia.PageVideoLookup
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
import kotlinx.coroutines.launch
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
 * P43: what the sheet says when it skipped an ad ("That was an ad — showing the page's video"),
 * the ads it never counts as other videos, the reload that looks for the page's video and not
 * the ad, and G8's two rules (STRICT, the default; LENIENT keeps the browser's first choice).
 */
class QuickDownloadAdRuleTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val harness = AdSheetHarness()
    private val facts = PageVideoFacts(PAGE_LENGTH, TITLE, null)
    private val ad = requested("https://cdn.clipnet.test/c/30s.mp4")

    private fun QuickDownloadUiState.offered(): List<String> =
        choices?.options.orEmpty().map { it.source.candidate.mediaUrl }.distinct()

    /** The sheet opens while the browser still looks for the page's video (P28). */
    private fun waitingSheet(vararg reads: PageReread): QuickDownloadViewModel {
        harness.store.awaitPageVideo()
        harness.store.showLookup(
            PageVideoLookup(key = "generic:page-video:43", pageUrl = PAGE, title = TITLE),
        )
        return harness.sheet(QueuedPageReader(*reads))
    }

    @Test
    fun aSkippedAdSaysSoAboveThePagesVideoWithoutAskingForTheAd() = runTest {
        val networkAd = ad.copy(
            mediaUrl = "https://cdn.adtng.test/c/30s.mp4",
            adSign = AdSign.AD_HOST,
        )
        val main = named("https://media.example.test/v43/720.mp4?hash=fresh")
        harness.resolver.answers[main.mediaUrl] = read(main, MAIN_LENGTH)
        harness.store.publish(PAGE, TITLE, listOf(networkAd, main), facts = facts)
        harness.store.select(MediaGroups.of(listOf(networkAd)).single(), otherVideos = 1)

        val sheet = harness.sheet()
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertTrue(state.adSkipped)
        assertFalse(state.nextVideo)
        assertFalse(state.freshLink)
        assertEquals(listOf(main.mediaUrl), state.offered())
        assertEquals(0, state.otherVideos)
        assertEquals(listOf("First video", "skipped: ad (ad host)"), state.attemptDetails.take(2))
        assertTrue(harness.resolver.requested.none { it.mediaUrl == networkAd.mediaUrl })
    }

    @Test
    fun provenAdsAreNotCountedAmongThePagesOtherVideos() = runTest {
        val main = named("https://media.example.test/v43/720.mp4?hash=fresh")
            .copy(durationMillis = MAIN_LENGTH)
        val networkAd = ad.copy(adSign = AdSign.AD_HOST)
        val inBreak = requested("https://cdn.other.test/b.mp4").copy(adSign = AdSign.AD_BREAK)
        harness.store.publish(PAGE, TITLE, listOf(main, networkAd, inBreak), facts = facts)
        harness.store.select(MediaGroups.of(listOf(main)).single(), otherVideos = 2)

        val state = harness.sheet().uiState.value

        assertEquals(0, state.otherVideos)
        assertFalse(state.adSkipped)
    }

    @Test
    fun reloadAfterOnlyAnAdLooksForThePagesVideoByItsLength() = runTest {
        harness.resolver.answers[ad.mediaUrl] = read(ad, AD_LENGTH)
        harness.store.publish(PAGE, TITLE, listOf(ad), facts = facts)
        harness.store.select(MediaGroups.of(listOf(ad)).single())
        val reloads = mutableListOf<PageReload>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            harness.store.pageReloads.collect { reloads += it }
        }
        val sheet = harness.sheet()
        advanceUntilIdle()
        assertEquals(QuickDownloadViewModel.ONLY_AD_FOUND, sheet.uiState.value.failure)
        assertFalse(sheet.uiState.value.adSkipped)

        assertTrue(sheet.reloadPage())
        advanceUntilIdle()

        val reload = reloads.single()
        assertEquals(PAGE, reload.pageUrl)
        assertEquals(PAGE_LENGTH, reload.video.durationMillis)
        assertTrue(reload.video.candidates.single().mediaUrl.endsWith("#yft-page-video"))
        assertTrue(ad.mediaUrl in reload.deadLinks)
    }

    @Test
    fun tryAgainAfterOnlyAnAdLooksAgainAndFindsThePagesVideo() = runTest {
        val main = named("https://media.example.test/v43/720.mp4?hash=again")
        harness.resolver.answers += mapOf(
            ad.mediaUrl to read(ad, AD_LENGTH),
            main.mediaUrl to read(main, MAIN_LENGTH),
        )
        harness.store.publish(PAGE, TITLE, listOf(ad), facts = facts)
        harness.store.select(MediaGroups.of(listOf(ad)).single())
        val sheet = harness.sheet()
        advanceUntilIdle()
        assertEquals(QuickDownloadViewModel.ONLY_AD_FOUND, sheet.uiState.value.failure)

        // The page now has its video (the player started it): Try again offers it.
        harness.store.publish(PAGE, TITLE, listOf(ad, main), facts = facts)
        sheet.retry()
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertNull(state.failure)
        assertTrue(state.adSkipped)
        assertEquals(listOf(main.mediaUrl), state.offered())
        assertEquals(MAIN_LENGTH, state.header?.durationMillis)
    }

    @Test
    fun strictIsTheDefaultRule() {
        harness.store.publish(PAGE, TITLE, listOf(ad), facts = facts)
        harness.store.select(MediaGroups.of(listOf(ad)).single())

        assertEquals(AdRule.STRICT, harness.sheet().adRule)
    }

    @Test
    fun lenientKeepsTheBrowsersFirstChoiceAsBefore() = runTest {
        harness.resolver.answers[ad.mediaUrl] = read(ad, AD_LENGTH)
        harness.store.publish(PAGE, TITLE, listOf(ad), facts = facts)
        val sheet = waitingSheet()
        sheet.adRule = AdRule.LENIENT

        harness.store.select(MediaGroups.of(listOf(ad)).single(), maybeAd = true)
        harness.store.clearLookup(LookupOwner.BROWSER)
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertEquals(listOf(ad.mediaUrl), state.offered())
        assertTrue(state.maybeAd)
        assertFalse(state.adSkipped)
        assertNull(state.failure)
    }

    @Test
    fun lenientStillSkipsAMeasuredShortFileInTheFreshLinkChain() = runTest {
        val mp4 = named("https://media.example.test/v43/720.mp4?hash=script")
        val fresh = mp4.copy(mediaUrl = "https://media.example.test/v43/720.mp4?hash=again")
        harness.resolver.answers += mapOf(
            mp4.mediaUrl to gone(410),
            ad.mediaUrl to read(ad, AD_LENGTH),
            fresh.mediaUrl to read(fresh, MAIN_LENGTH),
        )
        harness.store.publish(PAGE, TITLE, listOf(mp4, ad), facts = facts)
        val sheet = waitingSheet(PageReread.Found(listOf(fresh), facts))
        sheet.adRule = AdRule.LENIENT

        harness.store.select(MediaGroups.of(listOf(mp4)).single(), otherVideos = 1)
        harness.store.clearLookup(LookupOwner.BROWSER)
        advanceUntilIdle()

        val state = sheet.uiState.value
        assertEquals(listOf(fresh.mediaUrl), state.offered())
        assertTrue(state.freshLink)
        assertTrue("skipped: 0:30 ad (short)" in state.attemptDetails)
    }
}