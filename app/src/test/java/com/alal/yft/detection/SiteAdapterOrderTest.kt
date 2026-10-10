package com.alal.yft.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.api.SitePageIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P40 step 5: the order of a lookup's steps. Browser: the tab's own data → the page read with
 * the tab's cookies → the hidden page (only when the tab has no data for the post). Home: the
 * page read → the hidden page. Details list every step and its result.
 */
class SiteAdapterOrderTest {
    @Test
    fun `the browser's lookup uses the tab's data first and nothing else`() = runTest {
        val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.RESPONSE_CHANGED))
        val hidden = FakeHiddenPage(found())
        val tab = FakeTab(tabData(SitePageDataSource.TAB_API_ANSWER, cookie = "tab=fresh"))

        val outcome = coordinator(extractor, hidden).inspect(PAGE, tabContext(), NOW, tab = tab)

        assertTrue(outcome is SiteAdapterOutcome.Detected)
        assertEquals(listOf(SitePageDataSource.TAB_API_ANSWER), extractor.sources())
        // The rows carry the cookies the tab gave with its data.
        assertEquals("tab=fresh", extractor.requests.single().requestContext.cookie)
        assertEquals(listOf(POST_ID), tab.asked)
        assertEquals(emptyList<String>(), hidden.asked)
    }

    @Test
    fun `without the tab's data the page read comes next, then the hidden page`() = runTest {
        val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.RESPONSE_CHANGED))
        val hidden = FakeHiddenPage(found())
        val tab = FakeTab(noTabData(), noTabData())

        val outcome = coordinator(extractor, hidden).inspect(PAGE, tabContext(), NOW, tab = tab)

        assertTrue(outcome is SiteAdapterOutcome.Detected)
        assertEquals(listOf(null, SitePageDataSource.HIDDEN_PAGE), extractor.sources())
        // The tab is read again before the hidden page: a loading page may hold the post now.
        assertEquals(listOf(POST_ID, POST_ID), tab.asked)
        assertEquals(listOf(CANONICAL to POST_ID), hidden.asked)
        val fromHidden = extractor.requests.last().requestContext
        assertEquals("hidden-desktop-agent", fromHidden.userAgent)
        assertEquals("hidden=cookie", fromHidden.cookie)
        assertEquals(CANONICAL, fromHidden.pageUrl)
        assertEquals("tab=cookie", extractor.requests.first().requestContext.cookie)
    }

    @Test
    fun `a tab that holds the post by its second read is used instead of the hidden page`() =
        runTest {
            val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.HTTP_STATUS))
            val hidden = FakeHiddenPage(found())
            val tab = FakeTab(noTabData(), tabData(SitePageDataSource.TAB_SCRIPT))

            val outcome = coordinator(extractor, hidden).inspect(PAGE, tabContext(), NOW, tab = tab)

            assertTrue(outcome is SiteAdapterOutcome.Detected)
            assertEquals(listOf(null, SitePageDataSource.TAB_SCRIPT), extractor.sources())
            assertEquals(emptyList<String>(), hidden.asked)
        }

    @Test
    fun `when the tab's data fails no hidden page follows`() = runTest {
        val extractor = OrderExtractor(
            pageRead = failure(SiteExtractionFailure.NO_MEDIA_FOUND),
            fromData = failure(SiteExtractionFailure.NO_MEDIA_FOUND, "data: no file opened"),
        )
        val hidden = FakeHiddenPage(found())
        val tab = FakeTab(tabData(SitePageDataSource.TAB_SCRIPT))

        val outcome = coordinator(extractor, hidden).inspect(PAGE, tabContext(), NOW, tab = tab)
            as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, outcome.reason)
        assertEquals(emptyList<String>(), hidden.asked)
        assertEquals(
            listOf("adapter tiktok: NO_MEDIA_FOUND", "tab data: tab · page script · found"),
            outcome.details.take(2),
        )
    }

    @Test
    fun `Home's lookup reads the page, then the hidden page`() = runTest {
        val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.RESPONSE_CHANGED))
        val hidden = FakeHiddenPage(found())

        val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)

        assertTrue(outcome is SiteAdapterOutcome.Detected)
        assertEquals(listOf(null, SitePageDataSource.HIDDEN_PAGE), extractor.sources())
        assertEquals(listOf(CANONICAL to POST_ID), hidden.asked)
    }

    @Test
    fun `a short link opens in the hidden page and its rows take the post it landed on`() =
        runTest {
            val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.NETWORK))
            val hidden = FakeHiddenPage(found(finalUrl = "$CANONICAL?is_from_webapp=1"))

            val outcome = coordinator(extractor, hidden).inspect(SHORT, homeContext(), NOW)

            assertTrue(outcome is SiteAdapterOutcome.Detected)
            assertEquals(listOf(SHORT to null), hidden.asked)
            val last = extractor.requests.last()
            assertEquals(POST_ID, last.identity.contentId)
            assertEquals(false, last.identity.requiresCanonicalResolution)
        }

    @Test
    fun `the hidden page's timeout leaves the page read's failure, with every step`() =
        runTest {
            val extractor = OrderExtractor(
                pageRead = failure(
                    SiteExtractionFailure.RESPONSE_CHANGED,
                    "page: phone · HTTP 200",
                ),
            )
            val hidden = FakeHiddenPage(
                HiddenPageResult.NotFound(null, null, listOf("hidden page: no data after 15.0 s")),
            )

            val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)
                as SiteAdapterOutcome.Failed

            assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, outcome.reason)
            assertEquals(
                listOf(
                    "adapter tiktok: RESPONSE_CHANGED",
                    "page read: RESPONSE_CHANGED",
                    "page: phone · HTTP 200",
                    "hidden page: no data after 15.0 s",
                ),
                outcome.details,
            )
            // Home's generic scan still runs after a changed page.
            assertTrue(outcome.allowsGenericFallback)
        }

    @Test
    fun `a check on the hidden page asks the user to open the video in the browser`() =
        runTest {
            val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.NO_MEDIA_FOUND))
            val hidden = FakeHiddenPage(
                HiddenPageResult.NotFound(
                    SiteExtractionFailure.BOT_CHECK,
                    CHECK_TEXT,
                    listOf("hidden page: a check stays on the page · 3.0 s"),
                ),
            )

            val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)
                as SiteAdapterOutcome.Failed

            assertEquals(SiteExtractionFailure.BOT_CHECK, outcome.reason)
            assertEquals(CHECK_TEXT, outcome.message)
            assertTrue(outcome.canRetry)
        }

    @Test
    fun `the page data's status alone that a post is private still asks the hidden page`() =
        runTest {
            val extractor = OrderExtractor(
                pageRead = failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE),
            )
            val hidden = FakeHiddenPage(found())

            val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)

            // P46: TikTok's pages call some public posts private; its own player gets them.
            assertEquals(1, hidden.asked.size)
            assertTrue(outcome.toString(), outcome is SiteAdapterOutcome.Detected)
        }

    @Test
    fun `a private answer with the adapter's own words or an HTTP status stands`() = runTest {
        listOf(
            SiteExtractionResult.Failure(
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                message = "This TikTok link does not open a video.",
            ),
            SiteExtractionResult.Failure(
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                httpStatusCode = 404,
            ),
        ).forEach { read ->
            val hidden = FakeHiddenPage(found())

            coordinator(OrderExtractor(pageRead = read), hidden).inspect(PAGE, homeContext(), NOW)

            assertEquals(read.toString(), emptyList<String>(), hidden.asked)
        }
    }

    @Test
    fun `the hidden page never follows the site's own answer about the post`() = runTest {
        val answers = listOf(
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.DRM_PROTECTED,
            SiteExtractionFailure.BOT_CHECK,
            SiteExtractionFailure.RATE_LIMITED,
        )
        answers.forEach { reason ->
            val extractor = OrderExtractor(pageRead = failure(reason))
            val hidden = FakeHiddenPage(found())

            val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)
                as SiteAdapterOutcome.Failed

            assertEquals(reason, outcome.reason)
            assertEquals("$reason", emptyList<String>(), hidden.asked)
        }
    }

    @Test
    fun `with the hidden page off the page read's failure stands and Details say so`() =
        runTest {
            val extractor = OrderExtractor(pageRead = failure(SiteExtractionFailure.HTTP_STATUS))
            val hidden = FakeHiddenPage(HiddenPageResult.Off)

            val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)
                as SiteAdapterOutcome.Failed

            assertEquals(SiteExtractionFailure.HTTP_STATUS, outcome.reason)
            assertEquals(1, hidden.asked.size)
            assertTrue(outcome.details.contains("hidden page: turned off"))
            assertEquals(listOf<SitePageDataSource?>(null), extractor.sources())
        }

    @Test
    fun `a failure of the hidden page's data is the last step`() = runTest {
        val extractor = OrderExtractor(
            pageRead = failure(SiteExtractionFailure.RESPONSE_CHANGED),
            fromData = failure(SiteExtractionFailure.NO_MEDIA_FOUND, "data: no file opened"),
        )
        val hidden = FakeHiddenPage(found())

        val outcome = coordinator(extractor, hidden).inspect(PAGE, homeContext(), NOW)
            as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, outcome.reason)
        assertEquals(
            listOf(
                "adapter tiktok: NO_MEDIA_FOUND",
                "page read: RESPONSE_CHANGED",
                "hidden page: found in 1.2 s · page script · API answers kept: 0",
                "hidden page's data: NO_MEDIA_FOUND",
                "data: no file opened",
            ),
            outcome.details,
        )
    }

    @Test
    fun `a site without a hidden page keeps its old Details`() = runTest {
        val extractor = OrderExtractor(
            pageRead = failure(SiteExtractionFailure.RESPONSE_CHANGED, "page: changed"),
        )
        val outcome = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(extractor), SiteAdapterFlags { true }),
        ).inspect(PAGE, homeContext(), NOW) as SiteAdapterOutcome.Failed

        assertEquals(listOf("adapter tiktok: RESPONSE_CHANGED", "page: changed"), outcome.details)
    }

    @Test
    fun `the coordinator names the site of a page`() {
        val coordinator = coordinator(OrderExtractor(pageRead = null), FakeHiddenPage(found()))

        assertEquals("tiktok", coordinator.siteId(PAGE))
        assertNull(coordinator.siteId("https://example.test/video/1"))
    }

    private fun coordinator(extractor: SiteExtractor, hidden: HiddenPageReader) =
        SiteAdapterCoordinator(
            registry = SiteExtractorRegistry(listOf(extractor), SiteAdapterFlags { true }),
            hiddenPages = hidden,
        )

    private fun failure(reason: SiteExtractionFailure, vararg details: String) =
        SiteExtractionResult.Failure(reason, details = details.toList())

    private fun tabData(source: SitePageDataSource, cookie: String? = null) = TabData(
        pageData = SitePageData(ITEM, source),
        details = listOf("tab data: ${source.label} · found"),
        cookie = cookie,
    )

    private fun noTabData() = TabData(null, listOf("tab data: no data for this post"))

    private fun found(finalUrl: String = CANONICAL) = HiddenPageResult.Found(
        data = SitePageData(ITEM, SitePageDataSource.HIDDEN_PAGE),
        finalUrl = finalUrl,
        userAgent = "hidden-desktop-agent",
        cookie = "hidden=cookie",
        details = listOf("hidden page: found in 1.2 s · page script · API answers kept: 0"),
    )

    private fun tabContext() = BrowserRequestContext(PAGE, "tab-agent", "tab=cookie")

    private fun homeContext() = BrowserRequestContext(PAGE, "home-agent", null)

    /** A TikTok-like adapter: [pageRead] without page data, [fromData] with it. */
    private class OrderExtractor(
        private val pageRead: SiteExtractionResult?,
        private val fromData: SiteExtractionResult? = null,
    ) : SiteExtractor {
        override val id: String = "tiktok"
        override val displayName: String = "TikTok"
        val requests = mutableListOf<SiteExtractionRequest>()

        fun sources(): List<SitePageDataSource?> = requests.map { it.pageData?.source }

        override fun identify(pageUrl: String): SitePageIdentity? = when {
            pageUrl.startsWith(SHORT) ->
                SitePageIdentity("tiktok", "ZMshort", SHORT, requiresCanonicalResolution = true)
            pageUrl.startsWith(CANONICAL) -> SitePageIdentity("tiktok", POST_ID, CANONICAL)
            else -> null
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean = false

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            val answer = if (request.pageData == null) pageRead else fromData
            return answer ?: SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = request.identity.canonicalPageUrl,
                        mediaUrl = "https://media.fixture.test/${request.identity.contentId}.mp4",
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                        requestContext = request.requestContext,
                    ),
                ),
            )
        }
    }

    private class FakeTab(vararg answers: TabData) : TabDataSource {
        private val queue = ArrayDeque(answers.toList())
        val asked = mutableListOf<String>()

        override suspend fun read(siteId: String, postId: String): TabData? {
            asked += postId
            return queue.removeFirstOrNull()
        }
    }

    private class FakeHiddenPage(private val result: HiddenPageResult) : HiddenPageReader {
        val asked = mutableListOf<Pair<String, String?>>()

        override fun handles(siteId: String): Boolean = siteId == "tiktok"

        override suspend fun read(link: String, postId: String?): HiddenPageResult {
            asked += link to postId
            return result
        }
    }

    private companion object {
        const val POST_ID = "7311234567890123456"
        const val CANONICAL = "https://www.tiktok.com/@fixture_user/video/$POST_ID"
        const val PAGE = "$CANONICAL?is_from_webapp=1"
        const val SHORT = "https://vt.tiktok.com/ZMshort/"
        const val NOW = 1_700_000_000_000L
        const val ITEM = "{\"id\":\"$POST_ID\"}"
        const val CHECK_TEXT =
            "TikTok wants a check. Open the video in YFT's browser and tap Show check."
    }
}