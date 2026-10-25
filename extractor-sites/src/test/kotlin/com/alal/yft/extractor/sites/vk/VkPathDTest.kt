package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.sites.vk.ScriptedVkHttp.Companion.json
import com.alal.yft.extractor.sites.vk.ScriptedVkHttp.Companion.page
import com.alal.yft.extractor.sites.vk.ScriptedVkHttp.Companion.status
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK5, path D (VK_PLAN §13): only after the embed's adult answer; v1's message when it fails. */
class VkPathDTest {
    @Test
    fun `the embed's adult answer opens path d, which gives every rendition`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = working(),
            delayMillis = { 100L },
        )

        val result = extractor(http).extract(request(ADULT_LINK)) as SiteExtractionResult.Success

        // B on vk.com, then path D: no B2 after the adult answer.
        assertEquals(listOf(COM, TOKEN_HOST, API_HOST), http.requests.map { it.host })
        val (_, token, api) = http.requests
        assertEquals("POST", token.method)
        assertEquals(VkWebClientProfile.TOKEN_URL, token.url)
        assertEquals(VkWebClientProfile.tokenBody, token.body)
        assertEquals(VkExtractor.MAX_TOKEN_BYTES, token.maxBodyBytes)
        assertEquals("GET", api.method)
        assertEquals(VkWebClientProfile.videoGetUrl(checkNotNull(VkUrls.linkOf(ADULT_LINK))), api.url)
        assertEquals(VkExtractor.MAX_API_BYTES, api.maxBodyBytes)
        assertEquals("Bearer $TOKEN", api.headers["Authorization"])
        assertFalse(TOKEN in api.url)
        listOf(token, api).forEach { sent ->
            assertEquals(USER_AGENT, sent.headers["User-Agent"])
            assertEquals("https://vkvideo.ru", sent.headers["Origin"])
            assertEquals("https://vkvideo.ru/", sent.headers["Referer"])
            assertEquals("*/*", sent.headers["Accept"])
            assertNull(sent.headers["Cookie"])
            assertNull(sent.headers["Accept-Language"])
        }
        assertNull(token.headers["Authorization"])

        assertEquals(
            listOf(
                "vk: B vk.com · 200 · 100 ms · adult content (embed)",
                "vk: D token login.vk.ru · 200 · 100 ms · new token",
                "vk: D video.get api.vkvideo.ru · 200 · 100 ms · " +
                    "video: 6 mp4 up to 1080p, 2 hls, 1 dash (table)",
            ),
            result.details,
        )
        val candidates = result.candidates
        assertEquals(
            List(6) { MediaKind.DIRECT } + List(2) { MediaKind.HLS } + MediaKind.DASH,
            candidates.map(MediaCandidate::kind),
        )
        assertEquals(
            listOf(1080, 720, 480, 360, 240, 144) + List(3) { null },
            candidates.map(MediaCandidate::height),
        )
        candidates.forEach { candidate ->
            assertTrue(candidate.mediaUrl.startsWith("https://vk6-4.vkuser.net/"))
            assertFalse(candidate.mediaUrl, "trailer" in candidate.mediaUrl)
            assertEquals(ADULT_CANONICAL, candidate.pageUrl)
            assertEquals(setOf(CandidateSource.MANIFEST), candidate.sources)
            assertEquals(CandidateConfidence.HIGH, candidate.confidence)
            assertEquals(EXPIRES_MS, candidate.expiresAtEpochMs)
            assertEquals("Video by Asianese", candidate.title)
            assertEquals(118_000L, candidate.durationMillis)
            // The files are bound to the user agent that asked: the media requests send it.
            assertEquals(
                BrowserRequestContext(ADULT_CANONICAL, USER_AGENT, null),
                candidate.requestContext,
            )
        }
    }

    @Test
    fun `the adult answer after a shell or on the other host opens path d too`() = runTest {
        val afterShell = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.shell()), page(VkFixtures.adult()))),
            api = working(),
        )
        assertTrue(extractor(afterShell).extract(request(ADULT_LINK)) is SiteExtractionResult.Success)
        assertEquals(listOf(COM, COM, TOKEN_HOST, API_HOST), afterShell.requests.map { it.host })

        val onRu = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(page(VkFixtures.notFound())),
                RU to listOf(page(VkFixtures.adultRussian())),
            ),
            api = working(),
        )
        val result = extractor(onRu).extract(request(ADULT_LINK)) as SiteExtractionResult.Success
        assertEquals(listOf(COM, RU, TOKEN_HOST, API_HOST), onRu.requests.map { it.host })
        assertEquals("vk: B2 vk.ru · 200 · 0 ms · adult content (embed)", result.details[1])
    }

    @Test
    fun `the page's cookie never goes to path d`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = working(),
        )
        val link = "https://vk.com/video-215889201_456243439"

        val result = extractor(http).extract(request(link, page = link, cookie = PAGE_COOKIE))

        assertTrue(result is SiteExtractionResult.Success)
        assertEquals(PAGE_COOKIE, http.embedRequests.single().headers["Cookie"])
        assertEquals(2, http.apiRequests.size)
        http.apiRequests.forEach { sent ->
            assertNull(sent.headers["Cookie"])
            assertFalse(sent.headers.values.any { "remixsid" in it })
            assertFalse("remixsid" in sent.url || "remixsid" in sent.body.orEmpty())
        }
    }

    @Test
    fun `every other embed answer stays v1's and never asks path d`() = runTest {
        // No api script: a request to login.vk.ru or api.vkvideo.ru would fail the test.
        listOf(
            mapOf(COM to listOf(page(VkFixtures.video()))),
            mapOf(COM to listOf(page(VkFixtures.notFound())), RU to listOf(page(VkFixtures.notFound()))),
            mapOf(COM to listOf(page(VkFixtures.shell())), RU to listOf(page(VkFixtures.shell()))),
            mapOf(COM to listOf(status(429)), RU to listOf(status(404))),
        ).forEach { embeds ->
            val http = ScriptedVkHttp(embeds = embeds)
            extractor(http).extract(request(VIDEO_LINK))
            assertTrue(http.apiRequests.isEmpty())
        }
    }

    @Test
    fun `a kept token serves later lookups until 60 s before vk's expiry`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = mapOf(
                TOKEN_HOST to listOf(json(VkApiPages.token(TOKEN)), json(VkApiPages.token(TOKEN_2))),
                API_HOST to listOf(json(VkFixtures.apiAdult())),
            ),
        )
        val extractor = extractor(http)

        assertTrue(extractor.extract(request(ADULT_LINK)) is SiteExtractionResult.Success)
        val later = extractor.extract(request(ADULT_LINK, now = NOW_MS + 3_600_000L))
            as SiteExtractionResult.Success
        assertEquals(
            "vk: D video.get api.vkvideo.ru (token in memory) · 200 · 0 ms · " +
                "video: 6 mp4 up to 1080p, 2 hls, 1 dash (table)",
            later.details.last(),
        )
        // 59 s before the expiry the kept token is not used: a new one is asked for.
        extractor.extract(request(ADULT_LINK, now = NOW_MS + 86_400_000L - 59_000L))

        assertEquals(
            listOf("POST", "GET", "GET", "POST", "GET"),
            http.apiRequests.map(ScriptedVkHttp.Request::method),
        )
        assertEquals(
            listOf("Bearer $TOKEN", "Bearer $TOKEN", "Bearer $TOKEN_2"),
            http.apiRequests.mapNotNull { it.headers["Authorization"] },
        )
    }

    @Test
    fun `vk refusing a kept token earns one new token and one more ask`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = mapOf(
                TOKEN_HOST to listOf(json(VkApiPages.token(TOKEN)), json(VkApiPages.token(TOKEN_2))),
                API_HOST to listOf(
                    json(VkFixtures.apiAdult()),
                    json(VkFixtures.apiAuthError()),
                    json(VkFixtures.apiAdult()),
                ),
            ),
        )
        val extractor = extractor(http)
        extractor.extract(request(ADULT_LINK))

        val result = extractor.extract(request(ADULT_LINK)) as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "vk: B vk.com · 200 · 0 ms · adult content (embed)",
                "vk: D video.get api.vkvideo.ru (token in memory) · 200 · 0 ms · API error 5",
                "vk: D token login.vk.ru · 200 · 0 ms · new token",
                "vk: D video.get api.vkvideo.ru · 200 · 0 ms · " +
                    "video: 6 mp4 up to 1080p, 2 hls, 1 dash (table)",
            ),
            result.details,
        )
        assertEquals(
            listOf("Bearer $TOKEN", "Bearer $TOKEN", "Bearer $TOKEN_2"),
            http.apiRequests.mapNotNull { it.headers["Authorization"] },
        )
    }

    @Test
    fun `a new token vk refuses ends as v1 does, and is not kept`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = mapOf(
                TOKEN_HOST to listOf(json(VkApiPages.token(TOKEN)), json(VkApiPages.token(TOKEN_2))),
                API_HOST to listOf(json(VkFixtures.apiAuthError()), json(VkFixtures.apiAdult())),
            ),
        )
        val extractor = extractor(http)

        val failure = extractor.extract(request(ADULT_LINK)) as SiteExtractionResult.Failure

        assertV1Message(failure)
        assertEquals(listOf("POST", "GET"), http.apiRequests.map(ScriptedVkHttp.Request::method))
        assertEquals(
            "vk: D video.get api.vkvideo.ru · 200 · 0 ms · API error 5",
            failure.details.last(),
        )
        // The refused token is gone: the next lookup asks for a new one.
        assertTrue(extractor.extract(request(ADULT_LINK)) is SiteExtractionResult.Success)
        assertEquals(
            listOf("POST", "GET", "POST", "GET"),
            http.apiRequests.map(ScriptedVkHttp.Request::method),
        )
    }

    @Test
    fun `no token from login_vk_ru ends as v1 does, without asking the api`() = runTest {
        mapOf(
            json("{\"type\":\"error\",\"error\":\"invalid_client\"}") to
                "vk: D token login.vk.ru · 200 · 0 ms · no token",
            json("<html>challenge</html>") to "vk: D token login.vk.ru · 200 · 0 ms · no token",
            status(403) to "vk: D token login.vk.ru · HTTP 403 · 0 ms · LOGIN_REQUIRED",
            status(503) to "vk: D token login.vk.ru · HTTP 503 · 0 ms · HTTP_STATUS",
        ).forEach { (answer, line) ->
            val http = ScriptedVkHttp(
                embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
                api = mapOf(TOKEN_HOST to listOf(answer)),
            )

            val failure = extractor(http).extract(request(ADULT_LINK)) as SiteExtractionResult.Failure

            assertV1Message(failure)
            assertEquals(line, failure.details.last())
            assertEquals(listOf(TOKEN_HOST), http.apiRequests.map { it.host })
        }
    }

    @Test
    fun `the api's other answers end as v1 does`() = runTest {
        val live = VkApiPages.videoGet(
            VkPages.item(
                files = "\"hls_live\":\"https://vk6-4.vkuser.net/hls_live/F\"",
                ownerId = ADULT_OWNER,
                id = ADULT_ID,
                more = ",\"live_status\":\"started\"",
            ),
        )
        mapOf(
            json(VkApiPages.error(14)) to "API error 14",
            json(VkFixtures.apiDeleted()) to "restricted",
            json(VkApiPages.videoGet(VkPages.item(files = null, ownerId = ADULT_OWNER, id = ADULT_ID))) to
                "no files",
            json(VkApiPages.videoGet(VkPages.item(files = VkPages.files("mp4_720")))) to
                "no item for the video",
            json("<html>challenge</html>") to "not JSON",
            status(500) to "HTTP_STATUS",
        ).forEach { (answer, ending) ->
            val link = if (answer.isDeleted()) "https://vkvideo.ru/video-215889201_1" else ADULT_LINK
            val http = ScriptedVkHttp(
                embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
                api = mapOf(TOKEN_HOST to listOf(json(VkApiPages.token())), API_HOST to listOf(answer)),
            )

            val failure = extractor(http).extract(request(link)) as SiteExtractionResult.Failure

            assertV1Message(failure)
            assertTrue(failure.details.last(), failure.details.last().endsWith(ending))
            assertEquals(3, http.requests.size)
        }

        // A live video from path D stops with v1's live message.
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = mapOf(TOKEN_HOST to listOf(json(VkApiPages.token())), API_HOST to listOf(json(live))),
        )
        val failure = extractor(http).extract(request(ADULT_LINK)) as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertEquals(VkExtractor.LIVE_MESSAGE, failure.message)
    }

    @Test
    fun `path d fits the same 15 s deadline in virtual time`() = runTest {
        val slow = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = working(),
            delayMillis = { number -> if (number == 1) 7_000L else 5_000L },
        )
        val startedAt = currentTime

        val failure = extractor(slow).extract(request(ADULT_LINK)) as SiteExtractionResult.Failure

        assertEquals(VkExtractor.DEADLINE_MS, currentTime - startedAt)
        assertV1Message(failure)
        assertEquals(
            listOf(
                "vk: B vk.com · 200 · 7000 ms · adult content (embed)",
                "vk: D token login.vk.ru · 200 · 5000 ms · new token",
                "vk: D video.get api.vkvideo.ru · timeout · 3000 ms",
            ),
            failure.details,
        )

        // A slow shell, then the adult answer with under 250 ms left: path D is not asked at all.
        val late = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.shell()), page(VkFixtures.adult()))),
            api = working(),
            delayMillis = { number -> if (number == 1) 7_900L else 6_900L },
        )
        val lateFailure = extractor(late).extract(request(ADULT_LINK)) as SiteExtractionResult.Failure
        assertV1Message(lateFailure)
        assertTrue(late.apiRequests.isEmpty())
        assertEquals("vk: D token login.vk.ru · not asked · out of time", lateFailure.details.last())
    }

    @Test
    fun `cancelling the lookup during path d sends no further request`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.adult()))),
            api = working(),
            delayMillis = { 5_000L },
        )

        val lookup = launch { extractor(http).extract(request(ADULT_LINK)) }
        advanceTimeBy(6_000)
        lookup.cancel()
        advanceUntilIdle()

        assertEquals(listOf(COM, TOKEN_HOST), http.requests.map { it.host })
        assertTrue(lookup.isCancelled)
    }

    @Test
    fun `path d's details never hold an address, the token or a body`() = runTest {
        val scripts = listOf(
            working(),
            mapOf(TOKEN_HOST to listOf(json(VkApiPages.token())), API_HOST to listOf(json(VkFixtures.apiAuthError()))),
            mapOf(TOKEN_HOST to listOf(json("{\"type\":\"error\"}"))),
            mapOf(TOKEN_HOST to listOf(json(VkApiPages.token())), API_HOST to listOf(json("<html>x</html>"))),
        )

        scripts.forEach { api ->
            val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(page(VkFixtures.adult()))), api = api)
            val result = extractor(http).extract(request(ADULT_LINK, page = ADULT_LINK, cookie = PAGE_COOKIE))
            val details = when (result) {
                is SiteExtractionResult.Success -> result.details
                is SiteExtractionResult.Failure -> result.details
            }
            assertTrue(details.size >= 2)
            details.forEach { line ->
                assertTrue(line, line.startsWith("vk: "))
                listOf(
                    "://", "vkuser", "okcdn", "Bearer", "anonym", "FIXTURE", "<", "{", "=",
                    "remixsid", VkWebClientProfile.CLIENT_SECRET,
                ).forEach { forbidden -> assertFalse(line, forbidden in line) }
            }
        }
    }

    private fun TestScope.extractor(http: ScriptedVkHttp) =
        VkExtractor(http, clockMillis = { testScheduler.currentTime })

    private fun request(
        link: String,
        page: String? = link,
        cookie: String? = null,
        now: Long = NOW_MS,
    ) = SiteExtractionRequest(
        identity = checkNotNull(VkUrls.identify(link)),
        requestContext = BrowserRequestContext(pageUrl = page, userAgent = USER_AGENT, cookie = cookie),
        nowEpochMs = now,
    )

    private fun working(): Map<String, List<ExtractorHttpResult>> = mapOf(
        TOKEN_HOST to listOf(json(VkApiPages.token())),
        API_HOST to listOf(json(VkFixtures.apiAdult())),
    )

    private fun assertV1Message(failure: SiteExtractionResult.Failure) {
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertEquals(VkExtractor.NOT_FOUND_MESSAGE, failure.message)
        assertNull(failure.httpStatusCode)
    }

    private fun ExtractorHttpResult.isDeleted(): Boolean =
        this is ExtractorHttpResult.Success && "Video deleted" in body

    private companion object {
        const val COM = "vk.com"
        const val RU = "vk.ru"
    }
}
