package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.JsonText
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P40 step 2: a TikTok lookup in the browser first asks the tab for TikTok's own data for the
 * post (at most 1 s), and its rows carry the tab's cookies; other sites never ask the tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserTikTokTabDataTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aTikTokPagesLookupUsesTheTabsOwnDataFirst() = runTest {
        val extractor = TikTokLike()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        val reads = collectReads(viewModel)

        viewModel.onPageStarted(PAGE)
        viewModel.onPageFinished(PAGE, "Clip")
        runCurrent()

        val read = reads.single()
        assertEquals(POST_ID, read.postId)
        assertTrue("The lookup waits for the tab", extractor.requests.isEmpty())
        read.answer(evaluated(itemAnswer()), cookie = "ttwid=tab")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        val request = extractor.requests.single()
        assertEquals(SitePageDataSource.TAB_SCRIPT, request.pageData?.source)
        assertEquals("ttwid=tab", request.requestContext.cookie)
        assertEquals(listOf(MEDIA), viewModel.uiState.value.candidates.map { it.mediaUrl })
    }

    @Test
    fun aTabThatDoesNotAnswerWithinOneSecondDoesNotHoldTheLookup() = runTest {
        val extractor = TikTokLike()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        val reads = collectReads(viewModel)

        viewModel.onPageStarted(PAGE)
        viewModel.onPageFinished(PAGE, "Clip")
        runCurrent()
        advanceTimeBy(TAB_DATA_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(extractor.requests.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        // The page read runs without the tab's data, and a late answer is not waited for.
        assertNull(extractor.requests.single().pageData)
        assertFalse(reads.single().isWaiting)
    }

    @Test
    fun downloadAfterAFailedLookupReadsTheTabAgain() = runTest {
        val extractor = TikTokLike()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        val reads = collectReads(viewModel)
        viewModel.onPageStarted(PAGE)
        viewModel.onPageFinished(PAGE, "Clip")
        runCurrent()
        // The tab's page has not got the post yet: the page read fails.
        reads.single().answer(evaluated(NO_DATA), cookie = null)
        runCurrent()
        assertNull(extractor.requests.single().pageData)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())

        assertTrue(viewModel.openPageVideo())
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(2, reads.size)
        reads.last().answer(evaluated(itemAnswer(from = "api")), cookie = "ttwid=tab")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(SitePageDataSource.TAB_API_ANSWER, extractor.requests.last().pageData?.source)
        assertEquals(listOf(MEDIA), viewModel.uiState.value.candidates.map { it.mediaUrl })
    }

    @Test
    fun withoutTheBrowserScreenThereIsNoTabToWaitFor() = runTest {
        val extractor = TikTokLike()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))

        viewModel.onPageStarted(PAGE)
        viewModel.onPageFinished(PAGE, "Clip")
        runCurrent()

        assertNull(extractor.requests.single().pageData)
    }

    @Test
    fun otherSitesNeverAskTheTab() = runTest {
        val extractor = TikTokLike(siteId = "fixture")
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(extractor))
        val reads = collectReads(viewModel)

        viewModel.onPageStarted(PAGE)
        viewModel.onPageFinished(PAGE, "Clip")
        runCurrent()

        assertEquals(emptyList<TabDataRequest>(), reads)
        assertNull(extractor.requests.single().pageData)
    }

    private fun TestScope.collectReads(viewModel: BrowserViewModel): List<TabDataRequest> {
        val reads = mutableListOf<TabDataRequest>()
        backgroundScope.launch { viewModel.tabDataReads.collect { reads += it } }
        // The screen collects the reads before any page loads.
        runCurrent()
        return reads
    }

    private fun adapters(extractor: SiteExtractor): SiteAdapterCoordinator =
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)))

    /** TikTok's adapter in short: rows from the tab's data, else the page read fails. */
    private class TikTokLike(private val siteId: String = "tiktok") : SiteExtractor {
        override val id: String = siteId
        override val displayName: String = "TikTok"
        val requests = mutableListOf<SiteExtractionRequest>()

        override fun identify(pageUrl: String): SitePageIdentity? {
            if (!pageUrl.startsWith(PREFIX)) return null
            val id = pageUrl.removePrefix(PREFIX).substringBefore('?')
            return SitePageIdentity(siteId, id, "$PREFIX$id")
        }

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            if (request.pageData == null && siteId == "tiktok") {
                return SiteExtractionResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
            }
            return SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = request.identity.canonicalPageUrl,
                        mediaUrl = MEDIA,
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                        title = "Clip",
                    ),
                ),
            )
        }
    }

    private companion object {
        const val POST_ID = "7311234567890123456"
        const val PREFIX = "https://www.tiktok.com/@scout/video/"
        const val PAGE = "$PREFIX$POST_ID"
        const val MEDIA = "https://cdn.tiktok.test/1080.mp4"
        const val NO_DATA = """{"v":1,"none":true,"answers":0}"""

        /** BrowserViewModel's wait for the tab's answer. */
        const val TAB_DATA_TIMEOUT_MS = 1_000L

        fun itemAnswer(from: String = "script"): String =
            StringBuilder("""{"v":1,"from":"$from","id":"$POST_ID","item":""")
                .also { JsonText.appendString(it, """{"id":"$POST_ID"}""") }
                .append(",\"answers\":0}")
                .toString()

        fun evaluated(text: String): String =
            StringBuilder().also { JsonText.appendString(it, text) }.toString()
    }
}
