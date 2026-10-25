package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.vk.ScriptedVkHttp.Companion.page
import com.alal.yft.extractor.sites.vk.ScriptedVkHttp.Companion.status
import com.alal.yft.extractor.sites.vk.ScriptedVkHttp.Companion.text
import com.alal.yft.extractor.sites.testing.Fixtures
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

class VkExtractorTest {
    @Test
    fun `a public video comes from vk_com's embed with every rendition`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.video()))),
            delayMillis = { 120L },
        )

        val result = extractor(http).extract(request(VIDEO_LINK, page = VIDEO_LINK, PAGE_COOKIE))
            as SiteExtractionResult.Success

        val embed = http.requests.single()
        assertEquals("https://vk.com/video_ext.php?oid=-50883936&id=456244102", embed.url)
        assertEquals(PAGE_COOKIE, embed.headers["Cookie"])
        assertEquals(USER_AGENT, embed.headers["User-Agent"])
        assertEquals(PageNavigationHeaders.ACCEPT, embed.headers["Accept"])
        assertEquals(PageNavigationHeaders.FETCH_MODE, embed.headers["Sec-Fetch-Mode"])
        assertEquals(VkExtractor.MAX_EMBED_BYTES, embed.maxBodyBytes)
        assertEquals(
            listOf(
                "vk: B vk.com (page session) · 200 · 120 ms · " +
                    "video: 8 mp4 up to 2160p, 2 hls, 3 dash (table)",
            ),
            result.details,
        )

        val candidates = result.candidates
        assertEquals(13, candidates.size)
        assertTrue(candidates[0].mediaUrl.startsWith("https://vkvd741.okcdn.ru/mp4_2160/"))
        assertEquals(
            List(8) { MediaKind.DIRECT } + List(2) { MediaKind.HLS } + List(3) { MediaKind.DASH },
            candidates.map(MediaCandidate::kind),
        )
        assertEquals(
            listOf(2160, 1440, 1080, 720, 480, 360, 240, 144) + List(5) { null },
            candidates.map(MediaCandidate::height),
        )
        assertEquals(
            List(8) { "video/mp4" } + List(2) { "application/x-mpegURL" } +
                List(3) { "application/dash+xml" },
            candidates.map(MediaCandidate::mimeType),
        )
        candidates.forEach { candidate ->
            assertEquals(VIDEO_LINK, candidate.pageUrl)
            assertEquals(setOf(CandidateSource.MANIFEST), candidate.sources)
            assertEquals(CandidateConfidence.HIGH, candidate.confidence)
            assertEquals(false, candidate.drmHint)
            assertEquals(NOW_MS, candidate.observedAtEpochMs)
            assertEquals(EXPIRES_MS, candidate.expiresAtEpochMs)
            assertEquals("Добивание Украины // Техник в коме // МОЯ ЗЛОСТЬ №140", candidate.title)
            assertEquals(4_651_000L, candidate.durationMillis)
            assertTrue(checkNotNull(candidate.thumbnailUrl).startsWith("https://sun9-70."))
            // Media requests name the page and the browser, never the page's cookie.
            assertEquals(
                BrowserRequestContext(VIDEO_LINK, USER_AGENT, null),
                candidate.requestContext,
            )
        }
    }

    @Test
    fun `a clip opened on vkvideo_ru asks vk_com without any cookie`() = runTest {
        val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(page(VkFixtures.clip()))))
        val link = "https://vkvideo.ru/clip30014565_456240946"

        val result = extractor(http).extract(request(link, page = link, PAGE_COOKIE))
            as SiteExtractionResult.Success

        val embed = http.requests.single()
        assertEquals("https://vk.com/video_ext.php?oid=30014565&id=456240946", embed.url)
        assertNull(embed.headers["Cookie"])
        assertEquals(10, result.candidates.size)
        assertTrue(result.candidates.all { it.pageUrl == CLIP_LINK })
        assertEquals("Clip by @diamondoly", result.candidates.first().title)
        assertEquals(
            listOf("vk: B vk.com · 200 · 0 ms · video: 6 mp4 up to 1080p, 2 hls, 2 dash (table)"),
            result.details,
        )
    }

    @Test
    fun `a vk_ru page's cookie goes to vk_ru only, and 429 goes straight to vk_com`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(RU to listOf(status(429)), COM to listOf(page(VkFixtures.video()))),
        )
        val link = "https://m.vk.ru/video-50883936_456244102"

        val result = extractor(http).extract(request(link, page = link, PAGE_COOKIE))

        assertTrue(result is SiteExtractionResult.Success)
        assertEquals(listOf(RU, COM), http.requests.map { it.host })
        assertEquals(PAGE_COOKIE, http.requests[0].headers["Cookie"])
        assertNull(http.requests[1].headers["Cookie"])
        assertEquals(
            listOf(
                "vk: B vk.ru (page session) · HTTP 429 · 0 ms · RATE_LIMITED",
                "vk: B2 vk.com · 200 · 0 ms · video: 8 mp4 up to 2160p, 2 hls, 3 dash (table)",
            ),
            (result as SiteExtractionResult.Success).details,
        )
    }

    @Test
    fun `a vk_com page's cookie goes to vk_com only, and 5xx goes straight to vk_ru`() = runTest {
        listOf(500, 502, 503).forEach { code ->
            val http = ScriptedVkHttp(
                embeds = mapOf(COM to listOf(status(code)), RU to listOf(page(VkFixtures.video()))),
            )

            val result = extractor(http)
                .extract(request(VIDEO_LINK, page = "https://vk.com/feed", PAGE_COOKIE))

            assertTrue("$code", result is SiteExtractionResult.Success)
            assertEquals("$code", listOf(COM, RU), http.requests.map { it.host })
            assertEquals("$code", PAGE_COOKIE, http.requests[0].headers["Cookie"])
            assertNull("$code", http.requests[1].headers["Cookie"])
        }
    }

    @Test
    fun `vkvideo_ru pages and home lookups send no cookie to either host`() = runTest {
        listOf("https://vkvideo.ru/video-50883936_456244102", null).forEach { pageUrl ->
            val http = ScriptedVkHttp(embeds = mapOf(COM to shells(), RU to shells()))

            extractor(http).extract(request(VIDEO_LINK, page = pageUrl, PAGE_COOKIE))

            assertEquals("$pageUrl", 3, http.requests.size)
            assertTrue("$pageUrl", http.requests.none { "Cookie" in it.headers })
        }
    }

    @Test
    fun `vk's shell earns one more ask on the same host, then the other host`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to shells(), RU to listOf(page(VkFixtures.video()))),
        )

        val result = extractor(http).extract(request(VIDEO_LINK, page = VIDEO_LINK, PAGE_COOKIE))
            as SiteExtractionResult.Success

        assertEquals(listOf(COM, COM, RU), http.requests.map { it.host })
        // The second ask keeps the session, so a signed-in video is not lost to the other host.
        assertEquals(PAGE_COOKIE, http.requests[1].headers["Cookie"])
        assertNull(http.requests[2].headers["Cookie"])
        assertEquals(
            listOf(
                "vk: B vk.com (page session) · 200 · 0 ms · shell",
                "vk: B again vk.com (page session) · 200 · 0 ms · shell",
                "vk: B2 vk.ru · 200 · 0 ms · video: 8 mp4 up to 2160p, 2 hls, 3 dash (table)",
            ),
            result.details,
        )

        val second = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(VkFixtures.shell()), page(VkFixtures.video()))),
        )
        assertTrue(
            extractor(second).extract(request(VIDEO_LINK)) is SiteExtractionResult.Success,
        )
        assertEquals(listOf(COM, COM), second.requests.map { it.host })
    }

    @Test
    fun `shells on every try are a failure that lets the generic detector look`() = runTest {
        val http = ScriptedVkHttp(embeds = mapOf(COM to shells(), RU to shells()))

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, result.reason)
        assertTrue(result.allowsGenericFallback)
        assertNull(result.message)
        assertEquals(3, http.requests.size)
        assertEquals(listOf(COM, COM, RU), http.requests.map { it.host })
    }

    @Test
    fun `vk's not-found text is vk's answer, never a private or sign-in verdict`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(page(VkFixtures.notFound())),
                RU to listOf(page(VkFixtures.notFound())),
            ),
        )

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
        assertEquals(VkExtractor.NOT_FOUND_MESSAGE, result.message)
        assertNull(result.httpStatusCode)
        assertEquals(listOf(COM, RU), http.requests.map { it.host })
        assertEquals(
            listOf(
                "vk: B vk.com · 200 · 0 ms · not found",
                "vk: B2 vk.ru · 200 · 0 ms · not found",
            ),
            result.details,
        )

        // The other host's video still wins over the first host's text.
        val mixed = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(page(VkFixtures.notFound())),
                RU to listOf(page(VkFixtures.video())),
            ),
        )
        assertTrue(extractor(mixed).extract(request(VIDEO_LINK)) is SiteExtractionResult.Success)

        // VK's text outranks a refused request.
        val refused = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(status(429)), RU to listOf(page(VkFixtures.notFound()))),
        )
        val failure =
            extractor(refused).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertEquals(VkExtractor.NOT_FOUND_MESSAGE, failure.message)
    }

    @Test
    fun `an endpoint's http status is not vk's verdict on the video`() = runTest {
        val http =
            ScriptedVkHttp(embeds = mapOf(COM to listOf(status(404)), RU to listOf(status(403))))

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.HTTP_STATUS, result.reason)
        assertEquals(403, result.httpStatusCode)
        assertTrue(result.allowsGenericFallback)
        assertEquals(
            listOf(
                "vk: B vk.com · HTTP 404 · 0 ms · PRIVATE_OR_UNAVAILABLE",
                "vk: B2 vk.ru · HTTP 403 · 0 ms · LOGIN_REQUIRED",
            ),
            result.details,
        )

        val limited =
            ScriptedVkHttp(embeds = mapOf(COM to listOf(status(429)), RU to listOf(status(429))))
        val rateLimited = extractor(limited).extract(request(VIDEO_LINK))
            as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.RATE_LIMITED, rateLimited.reason)
        assertEquals(429, rateLimited.httpStatusCode)

        val offline = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(networkError()),
                RU to listOf(networkError()),
            ),
        )
        val network =
            extractor(offline).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.NETWORK, network.reason)
        assertNull(network.httpStatusCode)
        assertEquals(
            "vk: B vk.com · error · 0 ms · NETWORK · error: SocketTimeoutException",
            network.details.first(),
        )
    }

    @Test
    fun `a broadcast that is not over stops the lookup with its own message`() = runTest {
        val live = VkFixtures.postlive()
            .replace("\"live_status\":\"postlive\"", "\"live_status\":\"started\"")
        val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(page(live))))

        val result = extractor(http).extract(request(POSTLIVE_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
        assertEquals(VkExtractor.LIVE_MESSAGE, result.message)
        assertEquals(1, http.requests.size)
        assertTrue(result.details.single().endsWith(" · live"))
    }

    @Test
    fun `a finished broadcast is saved like any video, its expiry read from path or query`() =
        runTest {
            val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(page(VkFixtures.postlive()))))

            val result = extractor(http).extract(request(POSTLIVE_LINK))
                as SiteExtractionResult.Success

            // An MP4 is there, so no manifest is read.
            assertEquals(1, http.requests.size)
            assertEquals(
                listOf(MediaKind.DIRECT, MediaKind.HLS, MediaKind.DASH),
                result.candidates.map(MediaCandidate::kind),
            )
            assertEquals(listOf(720, null, null), result.candidates.map(MediaCandidate::height))
            assertTrue(result.candidates.all { it.expiresAtEpochMs == EXPIRES_MS })
            assertEquals(
                "vk: B vk.com · 200 · 0 ms · video: 1 mp4 up to 720p, 1 hls, 1 dash (table)",
                result.details.single(),
            )
        }

    @Test
    fun `without mp4 the first manifest that shows video decides`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(page(manifestsOnly()))),
            manifests = mapOf(HLS_URL to text(Fixtures.read("vk/hls-master-video.m3u8"))),
        )

        val result = extractor(http).extract(request(VIDEO_LINK, page = VIDEO_LINK, PAGE_COOKIE))
            as SiteExtractionResult.Success

        assertEquals(listOf(EMBED_COM, HLS_URL), http.requests.map { it.url })
        val manifest = http.requests[1]
        assertEquals(USER_AGENT, manifest.headers["User-Agent"])
        assertEquals(VIDEO_LINK, manifest.headers["Referer"])
        assertNull(manifest.headers["Cookie"])
        assertEquals(VkExtractor.MAX_MANIFEST_BYTES, manifest.maxBodyBytes)
        assertEquals(
            listOf(
                "vk: B vk.com (page session) · 200 · 0 ms · manifests: 1 hls, 1 dash (table)",
                "vk: manifest hls · 200 · 0 ms · video",
            ),
            result.details,
        )
        assertEquals(
            listOf(MediaKind.HLS, MediaKind.DASH),
            result.candidates.map(MediaCandidate::kind),
        )
        assertEquals(
            listOf("Synthetic fixture", "Synthetic fixture"),
            result.candidates.map(MediaCandidate::title),
        )
    }

    @Test
    fun `audio-only manifests everywhere are a partial success`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(page(manifestsOnly())),
                RU to listOf(page(manifestsOnly())),
            ),
            manifests = mapOf(
                HLS_URL to text(VkJudgeTest.audioOnlyMaster()),
                DASH_URL to text(VkJudgeTest.audioOnlyMpd()),
            ),
        )

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Success

        assertEquals(
            listOf(EMBED_COM, HLS_URL, DASH_URL, EMBED_RU, HLS_URL, DASH_URL),
            http.requests.map { it.url },
        )
        assertEquals("vk: audio-only (partial)", result.details.last())
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun `manifests that cannot be judged stay video`() = runTest {
        val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(page(manifestsOnly()))))

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Success

        assertEquals(listOf(EMBED_COM, HLS_URL, DASH_URL), http.requests.map { it.url })
        assertEquals("vk: manifests not judged", result.details.last())
        assertEquals(
            "vk: manifest hls · HTTP 404 · 0 ms · PRIVATE_OR_UNAVAILABLE",
            result.details[1],
        )
    }

    @Test
    fun `files on hosts that are not vk's make a changed response`() = runTest {
        val foreign = page(
            VkPages.embed(
                VkPages.item(files = "\"mp4_720\":\"https://cdn.example.org/mp4_720/F\""),
            ),
        )
        val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(foreign), RU to listOf(foreign)))

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, result.reason)
        assertEquals(
            "vk: B vk.com · 200 · 0 ms · files on unknown hosts: 1",
            result.details.first(),
        )
        assertTrue(http.requests.all { it.isEmbed })
    }

    @Test
    fun `another video's embed never yields its files`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(page(VkFixtures.clip())),
                RU to listOf(page(VkFixtures.clip())),
            ),
        )

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, result.reason)
        assertEquals("vk: B vk.com · 200 · 0 ms · no item for the video", result.details.first())
    }

    @Test
    fun `a video without files is no media`() = runTest {
        val empty =
            page(VkPages.embed(VkPages.item(files = "\"failover_host\":\"vkvd1.okcdn.ru\"")))
        val http = ScriptedVkHttp(embeds = mapOf(COM to listOf(empty), RU to listOf(empty)))

        val result = extractor(http).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
        assertNull(result.message)
        assertEquals("vk: B vk.com · 200 · 0 ms · no files", result.details.first())
    }

    @Test
    fun `every request fits the 15 s deadline in virtual time`() = runTest {
        val slow = ScriptedVkHttp(
            embeds = mapOf(
                COM to listOf(page(VkFixtures.video())),
                RU to listOf(page(VkFixtures.video())),
            ),
            delayMillis = { 10_000L },
        )
        val startedAt = currentTime

        val result = extractor(slow).extract(request(VIDEO_LINK)) as SiteExtractionResult.Failure

        assertEquals(VkExtractor.DEADLINE_MS, currentTime - startedAt)
        assertEquals(SiteExtractionFailure.NETWORK, result.reason)
        assertEquals(
            listOf(
                "vk: B vk.com · timeout · 8000 ms",
                "vk: B2 vk.ru · timeout · 7000 ms",
                "vk: out of time",
            ),
            result.details,
        )

        // Two slow shells leave too little time to ask the other host at all.
        val shellsAt = ScriptedVkHttp(
            embeds = mapOf(COM to shells(), RU to listOf(page(VkFixtures.video()))),
            delayMillis = { number -> if (number == 1) 7_900L else 7_000L },
        )
        val changed = extractor(shellsAt).extract(request(VIDEO_LINK))
            as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, changed.reason)
        assertEquals(2, shellsAt.requests.size)
        assertEquals("vk: B2 vk.ru · not asked · out of time", changed.details.last())
    }

    @Test
    fun `cancelling the lookup sends no further request`() = runTest {
        val http = ScriptedVkHttp(
            embeds = mapOf(COM to listOf(status(503)), RU to listOf(page(VkFixtures.video()))),
            delayMillis = { 5_000L },
        )

        val lookup = launch { extractor(http).extract(request(VIDEO_LINK)) }
        advanceTimeBy(1_000)
        lookup.cancel()
        advanceUntilIdle()

        assertEquals(1, http.requests.size)
        assertTrue(lookup.isCancelled)
    }

    @Test
    fun `details never hold an address, a cookie or a body`() = runTest {
        val scripts = listOf(
            ScriptedVkHttp(embeds = mapOf(COM to listOf(page(VkFixtures.video())))),
            ScriptedVkHttp(
                embeds = mapOf(COM to shells(), RU to listOf(page(VkFixtures.notFound()))),
            ),
            ScriptedVkHttp(
                embeds = mapOf(COM to listOf(page(manifestsOnly()))),
                manifests = mapOf(HLS_URL to text(Fixtures.read("vk/hls-master-video.m3u8"))),
            ),
            ScriptedVkHttp(
                embeds = mapOf(COM to listOf(status(429)), RU to listOf(networkError())),
            ),
        )

        scripts.forEach { http ->
            val result =
                extractor(http).extract(request(VIDEO_LINK, page = VIDEO_LINK, PAGE_COOKIE))
            val details = when (result) {
                is SiteExtractionResult.Success -> result.details
                is SiteExtractionResult.Failure -> result.details
            }
            assertTrue(details.isNotEmpty())
            details.forEach { line ->
                assertTrue(line, line.startsWith("vk: "))
                listOf("://", "okcdn", "userapi", "remixsid", "fixture-session", "FIXTURE", "<")
                    .forEach { forbidden -> assertFalse(line, forbidden in line) }
            }
        }
    }

    @Test
    fun `an identity whose address is not a vk video is refused offline`() = runTest {
        val http = ScriptedVkHttp(embeds = emptyMap())
        val identity = SitePageIdentity("vk", "feed", "https://vk.com/feed")

        val result = extractor(http).extract(
            SiteExtractionRequest(identity, BrowserRequestContext(null, USER_AGENT, null), NOW_MS),
        ) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.UNSUPPORTED_URL, result.reason)
        assertTrue(http.requests.isEmpty())
        assertEquals("vk", extractor(http).id)
        assertEquals("VK", extractor(http).displayName)
    }

    private fun TestScope.extractor(http: ScriptedVkHttp) =
        VkExtractor(http, clockMillis = { testScheduler.currentTime })

    private fun request(
        link: String,
        page: String? = link,
        cookie: String? = null,
    ) = SiteExtractionRequest(
        identity = checkNotNull(VkUrls.identify(link)),
        requestContext =
            BrowserRequestContext(pageUrl = page, userAgent = USER_AGENT, cookie = cookie),
        nowEpochMs = NOW_MS,
    )

    private fun shells() = listOf(page(VkFixtures.shell()))

    private fun manifestsOnly(): String =
        VkPages.embed(VkPages.item(files = VkPages.files("hls", "dash_sep")))

    private fun networkError() = ExtractorHttpResult.Failure(
        SiteExtractionFailure.NETWORK,
        details = listOf("error: SocketTimeoutException"),
    )

    private companion object {
        const val COM = "vk.com"
        const val RU = "vk.ru"
        const val EMBED_COM = "https://vk.com/video_ext.php?oid=-50883936&id=456244102"
        const val EMBED_RU = "https://vk.ru/video_ext.php?oid=-50883936&id=456244102"
        val HLS_URL = VkPages.fileUrl("hls")
        val DASH_URL = VkPages.fileUrl("dash_sep")
    }
}
